package com.aether.radio

import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Debug
import android.os.SystemClock
import android.view.MotionEvent
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/** Actual launches, native pointer input and player observations; separate test APK only. */
class ExperienceSuite(private val test:Instrumentation,private val activity:MainActivity,private val arguments:Bundle) {
    private val output=File(test.targetContext.getExternalFilesDir("evidence"),"experience").apply{mkdirs()}
    private fun <T> ui(work:()->T):T{val value=AtomicReference<T>();test.runOnMainSync{value.set(work())};return value.get()}
    private fun waitFor(seconds:Int=25,predicate:()->Boolean){val end=SystemClock.elapsedRealtime()+seconds*1000;while(SystemClock.elapsedRealtime()<end){if(ui(predicate))return;Thread.sleep(80)};error("Timed out: "+snapshot())}
    private fun point(action:String,fraction:Float=.5f):Pair<Float,Float> = ui {
        val v=activity.surface;val s=activity.session
        val nodes=v.painter.scene(if(s.page==1)"immersive" else v.painter.name(s.light)).flattened()
        val r=nodes.first{it.action==action||it.id==action}.r
        val scale=minOf(v.width/1704f,v.height/1136f);val at=IntArray(2);v.getLocationOnScreen(at)
        (at[0]+(v.width-1704*scale)/2+(r.left+r.width()*fraction)*scale) to (at[1]+(v.height-1136*scale)/2+r.centerY()*scale)
    }
    private fun tap(action:String,fraction:Float=.5f){val p=point(action,fraction);val now=SystemClock.uptimeMillis();test.sendPointerSync(MotionEvent.obtain(now,now,0,p.first,p.second,0));test.sendPointerSync(MotionEvent.obtain(now,now+70,1,p.first,p.second,0));Thread.sleep(120)}
    private fun swipe(fromX:Float,toX:Float){
        val values=ui{val v=activity.surface;val scale=minOf(v.width/1704f,v.height/1136f);floatArrayOf(scale,(v.width-1704*scale)/2,(v.height-1136*scale)/2)}
        val scale=values[0];val y=values[2]+575*scale;val start=SystemClock.uptimeMillis()
        test.sendPointerSync(MotionEvent.obtain(start,start,0,values[1]+fromX*scale,y,0))
        for(i in 1..12){Thread.sleep(25);test.sendPointerSync(MotionEvent.obtain(start,SystemClock.uptimeMillis(),2,values[1]+(fromX+(toX-fromX)*i/12)*scale,y,0))}
        test.sendPointerSync(MotionEvent.obtain(start,SystemClock.uptimeMillis(),1,values[1]+toX*scale,y,0));Thread.sleep(400)
    }
    private fun audio():AudioSession? = MainActivity::class.java.getDeclaredField("audio").apply{isAccessible=true}.get(activity) as? AudioSession
    private fun snapshot():JSONObject = ui {
        val s=activity.session;val a=audio();val memory=Debug.MemoryInfo();Debug.getMemoryInfo(memory)
        JSONObject().put("station",s.station?.name).put("selectedId",s.station?.id).put("catalogCount",s.catalog.size).put("favorites",JSONArray(s.favorites)).put("volume",s.volume).put("tempo",s.tempo).put("language",s.language).put("page",s.page).put("powered",s.powered).put("locked",s.locked).put("light",s.light).put("reference",s.reference).put("connection",s.connection.name).put("playing",s.playing).put("decodedFormat",s.decodedFormat).put("playerIdentity",a?.let{System.identityHashCode(it.player)}).put("speed",a?.player?.playbackParameters?.speed).put("pitch",a?.player?.playbackParameters?.pitch).put("pssKiB",memory.totalPss).put("javaHeapBytes",Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory()).put("width",activity.surface.width).put("height",activity.surface.height).put("hapticAttempts",activity.surface.haptics.attempts).put("hapticAccepted",activity.surface.haptics.accepted)
    }
    private fun committedFrame(){
        val finished=java.util.concurrent.atomic.AtomicBoolean(false)
        ui{activity.surface.viewTreeObserver.registerFrameCommitCallback{finished.set(true)};activity.surface.invalidate()}
        waitFor{finished.get()}
    }
    private fun ready(){waitFor{activity.surface.assetsReady&&!activity.surface.refreshing&&activity.hasWindowFocus()};committedFrame()}
    private fun reset(){
        ui{
            val s=activity.session
            if(s.locked)s.locked=false
            if(!s.powered)activity.surface.power()
            s.page=0;s.view="directory";s.menu=null;s.updateOpen=false;s.motion=false;s.spectrumEnabled=false
            if(s.playIntent)s.togglePlay()
            s.resetFilters();s.changeVolume(20);s.changeTempo(100);activity.surface.setLight(false);s.changed()
        }
        ready()
    }
    fun run():JSONObject {
        check(!activity.session.reference){"Experience acceptance must use real release data"}
        val startRound=arguments.getString("startRound","1").toInt();val count=arguments.getString("rounds","10").toInt()
        val batch=JSONArray();val player=ui{System.identityHashCode(audio()!!.player)}
        for(offset in 0 until count){
            val number=startRound+offset;val scenario=(number-1)%10
            val row=JSONObject().put("round",number).put("scenario",listOf("frontlight","volume-mute","tempo-pitch","settings-language","real-directory-filter","favorites","shared-page-session","lock-protection","power-retained-ink","real-playback")[scenario]).put("launchKind",if(offset==0)"cold" else "warm").put("startedAtEpochMs",System.currentTimeMillis())
            var begin=if(offset==0)arguments.getLong("launchStart",SystemClock.elapsedRealtime())else SystemClock.elapsedRealtime()
            try {
                if(offset>0){
                    ui{activity.moveTaskToBack(true)};waitFor{!activity.hasWindowFocus()};Thread.sleep(150)
                    begin=SystemClock.elapsedRealtime();row.put("startedAtEpochMs",System.currentTimeMillis())
                    test.targetContext.startActivity(Intent(test.targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
                }
                ready();row.put("openMs",SystemClock.elapsedRealtime()-begin);reset()
                val actionStart=SystemClock.elapsedRealtime()
                val before=snapshot();row.put("before",before)
                when(scenario){
                    0->{tap("device-light");waitFor{activity.surface.frontlight>.999f};committedFrame();check(ui{activity.session.light})}
                    1->{tap("volume");check(ui{activity.session.volume==20});tap("segmented-volume",17f/30);waitFor{activity.session.volume==17};tap("stereo-button");waitFor{activity.session.volume==0};tap("stereo-button");waitFor{activity.session.volume==17}}
                    2->{tap("tempo",0f);waitFor{activity.session.tempo==50&&audio()!!.player.playbackParameters.speed==.5f};tap("tempo",1f);waitFor{activity.session.tempo==150&&audio()!!.player.playbackParameters.speed==1.5f};tap("tempo");waitFor{activity.session.tempo==100};check(ui{audio()!!.player.playbackParameters.pitch==1f})}
                    3->{tap("device-settings");waitFor{activity.session.view=="settings"};tap("menu:ui-language");tap("option:1");waitFor{activity.session.language=="zh"};tap("menu:ui-language");tap("option:0");waitFor{activity.session.language=="en"};tap("device-settings")}
                    4->{waitFor{activity.session.catalog.isNotEmpty()};val selected=ui{activity.session.station?.id};tap("menu:genre-filter");tap("option:1");waitFor{activity.session.menu==null&&activity.session.genre.isNotBlank()};check(ui{activity.session.filtered().isNotEmpty()&&activity.session.station?.id==selected});tap("clear-filters");waitFor{activity.session.genre.isEmpty()}}
                    5->{val favorites=ui{activity.session.favorites.toList()};check(ui{activity.session.station!=null});tap("favorite-toggle");tap("favorite-toggle");check(ui{activity.session.favorites.toSet()==favorites.toSet()});tap("view:favorites");waitFor{activity.session.view=="favorites"};tap("view:directory")}
                    6->{val selected=ui{activity.session.station?.id};val volume=ui{activity.session.volume};swipe(1250f,800f);waitFor{activity.session.page==1};check(ui{activity.session.station?.id==selected&&activity.session.volume==volume});committedFrame();swipe(450f,750f);waitFor{activity.session.page==0}}
                    7->{val volume=ui{activity.session.volume};tap("device-lock");waitFor{activity.session.locked};tap("segmented-volume",1f);check(ui{activity.session.volume==volume});tap("device-light");waitFor{activity.session.light};tap("device-lock");waitFor{!activity.session.locked}}
                    8->{val selected=ui{activity.session.station?.id};tap("device-power");waitFor{!activity.session.powered};waitFor{!activity.surface.refreshing};tap("segmented-volume",1f);check(ui{activity.session.station?.id==selected&&activity.session.volume==20&&!activity.session.powered});tap("device-power");waitFor{activity.session.powered&&!activity.surface.refreshing}}
                    9->{check(ui{activity.session.station?.stream?.startsWith("https://")==true});tap("play-toggle");waitFor(40){activity.session.playing};check(ui{audio()!!.player.isPlaying});Thread.sleep(500);tap("play-toggle");waitFor{!activity.session.playing};check(ui{audio()!!.player.audioFormat!=null})}
                }
                committedFrame();row.put("actionMs",SystemClock.elapsedRealtime()-actionStart)
                check(ui{System.identityHashCode(audio()!!.player)==player}){"Player recreated during the batch"}
                row.put("passed",true)
            }catch(error:Throwable){row.put("passed",false).put("error",error.stackTraceToString().take(6000))}
            row.put("totalMs",SystemClock.elapsedRealtime()-begin).put("after",snapshot())
            test.uiAutomation.takeScreenshot()?.let{full->
                val name="round-%03d".format(number)
                if(offset==0||!row.optBoolean("passed"))File(output,"$name-native.png").outputStream().use{full.compress(Bitmap.CompressFormat.PNG,100,it)}
                val small=Bitmap.createScaledBitmap(full,852,568,true);File(output,"$name-preview.png").outputStream().use{small.compress(Bitmap.CompressFormat.PNG,100,it)};small.recycle();full.recycle()
                row.put("preview","$name-preview.png")
            }
            batch.put(row);File(output,"batch-$startRound.json").writeText(batch.toString(2))
            android.util.Log.i("AetherExperience","round=$number passed=${row.optBoolean("passed")} openMs=${row.optLong("openMs")} actionMs=${row.optLong("actionMs")}")
            reset()
        }
        return JSONObject().put("batchStart",startRound).put("rounds",batch.length()).put("results",batch)
    }
}
