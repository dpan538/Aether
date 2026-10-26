package com.aether.radio

import android.app.Instrumentation
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicReference

/** Runs in the player while a separately installed, different-UID host receives real touches. */
class ExternalWidgetSuite(private val test:Instrumentation,private val activity:MainActivity){
    private fun <T> main(block:()->T):T{val r=AtomicReference<Result<T>>();test.runOnMainSync{r.set(runCatching(block))};return r.get().getOrThrow()}
    private fun shell(command:String)=android.os.ParcelFileDescriptor.AutoCloseInputStream(test.uiAutomation.executeShellCommand(command)).bufferedReader().use{it.readText()}
    private fun host():JSONObject?=runCatching{JSONObject(shell("run-as io.github.dpan538.widgethost cat files/state.json"))}.getOrNull()
    private fun waitFor(block:()->Boolean){val end=SystemClock.uptimeMillis()+18000;while(SystemClock.uptimeMillis()<end){if(block())return;Thread.sleep(150)};error("External widget timed out; host="+host())}
    private fun tap(name:String,fx:Float=.5f,fy:Float=.5f){val b=host()!!.getJSONObject(name);val x=b.getInt("x")+b.getInt("w")*fx;val y=b.getInt("y")+b.getInt("h")*fy;val t=SystemClock.uptimeMillis();for(a in listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP)){val event=MotionEvent.obtain(t,SystemClock.uptimeMillis(),a,x,y,0).apply{source=InputDevice.SOURCE_TOUCHSCREEN};check(test.uiAutomation.injectInputEvent(event,true));event.recycle();Thread.sleep(75)};Thread.sleep(450)}
    private fun capture(name:String){
        val capture="frame-${SystemClock.uptimeMillis()}"
        shell("am start -W -n io.github.dpan538.widgethost/.HostActivity --es command capture --es name $capture")
        var frame:JSONObject?=null
        waitFor{frame=runCatching{JSONObject(shell("run-as io.github.dpan538.widgethost cat files/capture-state.json"))}.getOrNull();frame?.optString("capture")=="$capture.png"&&frame?.optBoolean("frameCommitted")==true}
        val b=android.os.ParcelFileDescriptor.AutoCloseInputStream(test.uiAutomation.executeShellCommand("run-as io.github.dpan538.widgethost cat files/$capture.png")).use{android.graphics.BitmapFactory.decodeStream(it)}!!
        java.io.File(test.targetContext.getExternalFilesDir("evidence"),"external-widget-$name.png").outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)}
        if(name.endsWith("baseline-false")){
            val bar=frame!!.getJSONObject("widget_progress");val root=frame!!.getJSONObject("widget_root")
            val y=bar.getInt("y")-root.getInt("y")+bar.getInt("h")/2
            check(listOf(.25,.5,.75).any{f->
                val c=b.getPixel(bar.getInt("x")-root.getInt("x")+(bar.getInt("w")*f).toInt(),y)
                android.graphics.Color.red(c)+android.graphics.Color.green(c)+android.graphics.Color.blue(c)>110
            }){"Progress track is not actually visible in the native screenshot: $bar"}
        }
        b.recycle()
    }
    fun run(baseline:Boolean=false,pausedOnly:Boolean=false):JSONObject{
        val checks=JSONArray();val s=activity.session;var hostUid=-1
        fun record(item:JSONObject){checks.put(item);java.io.File(test.targetContext.getExternalFilesDir("evidence"),"external-widget-checks.json").writeText(checks.toString(2))}
        main{s.powered=true;s.locked=false;s.selectLocal(LocalMusic.bundled(test.targetContext),0)};waitFor{main{s.playing}}
        val player=main{PlaybackService.current!!.live.audio.player}
        if(!baseline){
            val pm=test.targetContext.packageManager
            for(type in listOf(CompactMusicWidget::class.java)){
                val info=pm.getReceiverInfo(android.content.ComponentName(test.targetContext,type),0)
                check(info.permission==android.Manifest.permission.BIND_APPWIDGET)
            }
            check(!pm.getReceiverInfo(android.content.ComponentName(test.targetContext,WidgetPlaybackReceiver::class.java),0).exported)
            val started=SystemClock.uptimeMillis()
            shell("am start -W -n io.github.dpan538.widgethost/.HostActivity --es command probe");Thread.sleep(700)
            val probe=JSONObject(shell("run-as io.github.dpan538.widgethost cat files/probe.json"))
            check(probe.getInt("uid")!=android.os.Process.myUid()&&probe.getLong("time")>=started)
            check(main{player.isPlaying}){"Untrusted raw broadcast changed playback"}
            record(JSONObject().put("name","System widget updates require BIND_APPWIDGET; a different-UID app's raw transport broadcast cannot change playback").put("passed",true).put("probe",probe))
        }
        shell("am start -W -n io.github.dpan538.widgethost/.HostActivity --es command reset");Thread.sleep(300)
        fun awaitHost(caseTag:String){
            val end=SystemClock.uptimeMillis()+20000
            while((host()?.optBoolean("resumed")!=true||host()?.optInt("widthDp")!=900||host()?.optString("case")!=caseTag)&&SystemClock.uptimeMillis()<end){
                fun walk(n:android.view.accessibility.AccessibilityNodeInfo):List<android.view.accessibility.AccessibilityNodeInfo> = listOf(n)+(0 until n.childCount).flatMap{n.getChild(it)?.let{walk(it)}?:emptyList()}
                test.uiAutomation.rootInActiveWindow?.let{root->walk(root).firstOrNull{it.viewIdResourceName=="android:id/button1"&&it.isClickable}?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)};Thread.sleep(150)
            }
            waitFor{host()?.optJSONObject("widget_play")?.optString("description") in listOf("播放","暂停")&&host()?.optBoolean("resumed")==true&&host()?.optInt("widthDp")==900&&host()?.optString("case")==caseTag};Thread.sleep(1000)
        }
        val styles=if(pausedOnly)emptyList()else listOf("compact")
        for(style in styles){
            val caseTag="${style}-${SystemClock.uptimeMillis()}"
            shell("am start -W -n io.github.dpan538.widgethost/.HostActivity --es case $caseTag --es style $style --ei width 900 --ei height 400 --ez exact false")
            awaitHost(caseTag)
            for((width,height,exact) in if(baseline)listOf(Triple(900,400,false))else listOf(Triple(900,400,false),if(style=="compact")Triple(280,280,true)else if(style=="strip")Triple(600,200,true)else Triple(600,400,true),Triple(900,400,true),Triple(600,300,false))){
            val resizedCase="$caseTag-$width-$height"
            if(width!=900||height!=400||exact){shell("am start -W -n io.github.dpan538.widgethost/.HostActivity --es command resize --es case $resizedCase --ei width $width --ei height $height --ez exact $exact");waitFor{host()?.optInt("widthDp")==width&&host()?.optString("case")==resizedCase};Thread.sleep(1000)}
            val h=host()!!;val content=h.getJSONObject("widget_root");val bounds=h.getJSONObject("host")
            hostUid=h.getInt("uid");check(hostUid!=android.os.Process.myUid())
            if(!baseline)for(key in listOf("widget_cover","widget_time","widget_progress","widget_previous","widget_play","widget_next")){
                val child=h.getJSONObject(key)
                check(child.getInt("x")>=content.getInt("x")&&child.getInt("y")>=content.getInt("y")&&child.getInt("x")+child.getInt("w")<=content.getInt("x")+content.getInt("w")+1&&child.getInt("y")+child.getInt("h")<=content.getInt("y")+content.getInt("h")+1){"Clipped widget content: $key $h"}
            }
            val padding=h.getJSONArray("padding");val filled=kotlin.math.abs(content.getInt("w")-(bounds.getInt("w")-padding.getInt(0)-padding.getInt(2)))<=2&&kotlin.math.abs(content.getInt("h")-(bounds.getInt("h")-padding.getInt(1)-padding.getInt(3)))<=2
            record(JSONObject().put("name","$style $width × $height fills the external allocation (exact sizes: $exact)").put("passed",filled).put("host",bounds).put("content",content));if(!baseline)check(filled){"Not filled: $h"}
            capture("$style-$width-$height-$exact-baseline-$baseline")
            shell("am start -W -n io.github.dpan538.widgethost/.HostActivity --es command capture --es name sample-$style-$width-$height")
            tap("widget_play");Thread.sleep(500)
            val paused=main{!player.isPlaying};val stayed=host()?.optBoolean("resumed")==true
            record(JSONObject().put("name","$style physical-coordinate pause stays in the external host").put("passed",paused&&stayed).put("paused",paused).put("stayedInHost",stayed));if(!baseline)check(paused&&stayed)
            if(stayed){
                if(paused)tap("widget_play",.85f,.85f);waitFor{main{player.isPlaying}}
                tap("widget_next",.90f,.15f);waitFor{main{s.localIndex==1}};check(host()?.optBoolean("resumed")==true)
                tap("widget_previous",.10f,.85f);waitFor{main{s.localIndex==0}};check(host()?.optBoolean("resumed")==true)
                record(JSONObject().put("name","$style coordinate resume/next/previous use the same player without opening the app").put("passed",true))
            }
            if(!baseline){
                val before=host()!!.getJSONObject("widget_time").getString("text");waitFor{host()!!.getJSONObject("widget_time").getString("text")!=before}
                main{s.locked=true;s.changed()};Thread.sleep(350);tap("widget_play");check(host()?.optBoolean("resumed")==true);main{s.locked=false;s.changed()};Thread.sleep(350)
                record(JSONObject().put("name","$style $width × $height progress reapply preserves controls; disabled control never launches the app").put("passed",true))
                if(host()!!.has("widget_art_open")){
                    tap("widget_art_open");waitFor{main{activity.hasWindowFocus()&&s.page==1}}
                    shell("am start -W -n io.github.dpan538.widgethost/.HostActivity --es command foreground");waitFor{host()?.optBoolean("resumed")==true}
                    record(JSONObject().put("name","Square artwork entry is independent of transport hit regions").put("passed",true))
                }
            }
            }
            if(!baseline){
                tap(if(host()!!.has("widget_art_open"))"widget_art_open"else"widget_cover");waitFor{main{activity.hasWindowFocus()&&s.page==1}};check(main{player===PlaybackService.current!!.live.audio.player&&s.isLocal})
                record(JSONObject().put("name","$style artwork opens the existing player Activity independently of transport controls").put("passed",true))
            }
            shell("am start -W -f 0x20000 -n io.github.dpan538.widgethost/.HostActivity --es command finish")
            Thread.sleep(350)
        }
        if(!baseline&&!pausedOnly){
            val catalog=main{s.catalog}
            val stations=listOf(Station("widget:soma-groove","SomaFM Groove Salad",stream="https://ice6.somafm.com/groovesalad-128-aac"),Station("widget:soma-drone","SomaFM Drone Zone",stream="https://ice6.somafm.com/dronezone-128-aac"))
            main{s.catalog=stations;s.select(stations[0].id)};waitFor{main{s.playing&&!s.isLocal}}
            val caseTag="radio-${SystemClock.uptimeMillis()}"
            shell("am start -W -n io.github.dpan538.widgethost/.HostActivity --es case $caseTag --es style compact --ei width 900 --ei height 400 --ez exact false");awaitHost(caseTag)
            tap("widget_play");waitFor{main{!player.isPlaying}};tap("widget_play",.85f,.85f);waitFor{main{player.isPlaying}}
            record(JSONObject().put("name","Real HTTPS radio pauses/resumes from the external widget without opening the player").put("passed",true))
            tap("widget_next",.90f,.15f);waitFor{main{s.station?.id==stations[1].id&&s.playing}};check(host()?.optBoolean("resumed")==true)
            tap("widget_previous",.10f,.85f);waitFor{main{s.station?.id==stations[0].id&&s.playing}};check(host()?.optBoolean("resumed")==true)
            record(JSONObject().put("name","Real radio next/previous change decoded HTTPS streams and retain background controls").put("passed",true))
            val before=host()!!.getJSONObject("widget_time").getString("text");waitFor{host()!!.getJSONObject("widget_time").getString("text")!=before}
            record(JSONObject().put("name","Radio elapsed time advances in the widget after source changes and full refreshes").put("passed",true))
            shell("am start -W -n io.github.dpan538.widgethost/.HostActivity --es command finish")
            main{s.catalog=catalog;s.selectLocal(LocalMusic.bundled(test.targetContext),0)};waitFor{main{s.playing}}
        }
        if(!baseline){
            main{s.togglePlay()};waitFor{main{!player.isPlaying}}
            val pausedCase="paused-${SystemClock.uptimeMillis()}"
            shell("am start -W -n io.github.dpan538.widgethost/.HostActivity --es case $pausedCase --es style compact --ei width 900 --ei height 400 --ez exact false");awaitHost(pausedCase)
            check(host()!!.getJSONObject("widget_title").getString("text")==main{s.localTrack!!.title})
            tap("widget_play");waitFor{main{player.isPlaying}};check(host()?.optBoolean("resumed")==true)
            record(JSONObject().put("name","A newly added widget while paused receives real metadata and active controls rather than remaining a placeholder").put("passed",true))
            shell("am start -W -n io.github.dpan538.widgethost/.HostActivity --es command finish")
            if(pausedOnly)return JSONObject().put("passed",true).put("checks",checks)
            val monitor=test.addMonitor(MainActivity::class.java.name,null,false)
            try{
                main{activity.finishAndRemoveTask()};waitFor{main{activity.isDestroyed&&player.isPlaying}}
                record(JSONObject().put("name","Removing the actual player task leaves the same foreground-service player running").put("passed",true))
                val caseTag="cold-${SystemClock.uptimeMillis()}"
                shell("am start -W -n io.github.dpan538.widgethost/.HostActivity --es case $caseTag --es style compact --ei width 900 --ei height 400 --ez exact false");awaitHost(caseTag)
                tap("widget_play");waitFor{main{!player.isPlaying}};tap("widget_play");waitFor{main{player.isPlaying}}
                tap("widget_next",.90f,.15f);waitFor{main{s.localIndex==1}};tap("widget_previous",.10f,.85f);waitFor{main{s.localIndex==0}}
                check(host()?.optBoolean("resumed")==true&&monitor.hits==0)
                record(JSONObject().put("name","After task removal, external widget pause/resume/next/previous remain background controls").put("passed",true))
                tap("widget_art_open")
                val fresh=monitor.waitForActivityWithTimeout(20000) as? MainActivity?:error("Artwork did not open a new player Activity")
                waitFor{main{fresh.uiReady&&fresh.surface.assetsReady&&fresh.session.page==1&&fresh.hasWindowFocus()}}
                check(main{fresh.session.isLocal&&PlaybackService.current!!.live.audio.player===player})
                record(JSONObject().put("name","Artwork recreates only the Activity, restoring immersive local music without replacing the player").put("passed",true))
                shell("am start -W -n io.github.dpan538.widgethost/.HostActivity --es command finish")
            }finally{test.removeMonitor(monitor)}
        }
        return JSONObject().put("passed",!baseline||checks.let{a->(0 until a.length()).all{a.getJSONObject(it).getBoolean("passed")}}).put("checks",checks).put("baseline",baseline).put("playerUid",android.os.Process.myUid()).put("hostUid",hostUid).put("hostPackage","io.github.dpan538.widgethost")
    }
}
