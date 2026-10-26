package com.aether.radio

import android.app.Instrumentation
import android.content.Intent
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.*

class WidgetEntrySuite(private val test:Instrumentation,private val activity:MainActivity){
    private fun <T> main(block:()->T):T{val r=AtomicReference<Result<T>>();test.runOnMainSync{r.set(runCatching(block))};return r.get().getOrThrow()}
    private fun waitFor(block:()->Boolean){val end=SystemClock.uptimeMillis()+20000;while(SystemClock.uptimeMillis()<end){if(main(block))return;Thread.sleep(100)};error("Entry/navigation state timed out")}
    private fun gesture(points:List<Pair<Float,Float>>){
        val pixels=main{val v=activity.surface;val scale=minOf(v.width/1704f,v.height/1136f);val xy=IntArray(2);v.getLocationOnScreen(xy);points.map{(x,y)->xy[0]+(v.width-1704*scale)/2+x*scale to xy[1]+(v.height-1136*scale)/2+y*scale}}
        val down=SystemClock.uptimeMillis();pixels.forEachIndexed{i,(x,y)->val event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),if(i==0)0 else if(i==pixels.lastIndex)1 else 2,x,y,0).apply{source=InputDevice.SOURCE_TOUCHSCREEN};check(test.uiAutomation.injectInputEvent(event,true));event.recycle();Thread.sleep(25)};Thread.sleep(650)
    }
    private fun line(x:Float,y:Float,dx:Float)=gesture((0..12).map{x+dx*it/12f to y})
    fun run(baseline:Boolean=false):JSONObject{
        val checks=JSONArray();val s=activity.session
        main{s.powered=true;s.locked=false;s.menu=null;s.selectLocal(LocalMusic.bundled(test.targetContext),0)};waitFor{s.playing&&s.seekable&&!activity.surface.refreshing}
        val player=main{PlaybackService.current!!.live.audio.player};val id=main{s.activeId}
        fun tapAction(action:String){val r=main{activity.surface.painter.scene(activity.surface.painter.name(s.light)).flattened().first{it.action==action}.r};gesture(listOf(r.centerX() to r.centerY(),r.centerX() to r.centerY()))}
        fun entry(){main{activity.startActivity(Intent(activity,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("show-player",true))};waitFor{s.page==1};Thread.sleep(500)}
        entry();line(568f,568f,330f)
        val changed=main{s.page==0};checks.put(JSONObject().put("name","Center-of-CD horizontal swipe works after a warm widget entry").put("passed",changed))
        if(baseline)return JSONObject().put("passed",changed).put("checks",checks).put("baseline",true)
        check(changed);check(main{s.activeId==id&&player===PlaybackService.current!!.live.audio.player})
        repeat(4){
            line(1064f,574f,-230f);waitFor{s.page==1}
            line(568f,568f,330f);waitFor{s.page==0}
        }
        checks.put(JSONObject().put("name","Eight actual page swipes keep the same local source and player").put("passed",true))
        tapAction("device-lock");check(main{s.locked});entry();line(568f,568f,330f);waitFor{s.page==0}
        check(main{s.locked&&s.activeId==id});tapAction("device-lock");check(main{!s.locked})
        checks.put(JSONObject().put("name","Locked controls do not trap widget entry: page navigation reaches the physical unlock key without unlocking audio controls").put("passed",true))
        main{s.showView("settings");s.settingsPage="menu";s.changed()};Thread.sleep(500)
        val ink=main{MainActivity::class.java.getDeclaredField("inkSettings").apply{isAccessible=true}.get(activity) as InkSettingsView}
        main{ink.openLanguageMenu()};waitFor{ink.languageMenuView!=null};Thread.sleep(200);entry()
        check(main{s.view!="settings"&&s.menu==null&&ink.languageMenuView==null&&activity.surface.committedOffset==0f})
        line(568f,568f,330f);waitFor{s.page==0};checks.put(JSONObject().put("name","Entry while settings is retained clears transient navigation state and remains swipeable").put("passed",true))
        entry();val cx=ImmersivePlayback.DISC.centerX();val cy=ImmersivePlayback.DISC.centerY()
        gesture(listOf(cx to cy,cx to cy));waitFor{!s.playing};gesture(listOf(cx to cy,cx to cy));waitFor{s.playing}
        val before=main{player.currentPosition}
        gesture((0..20).map{i->val a=(-90+i*60.0/20)*PI/180;(cx+cos(a)*425).toFloat() to (cy+sin(a)*425).toFloat()})
        waitFor{player.currentPosition>before+7000};check(main{s.page==1&&s.activeId==id})
        checks.put(JSONObject().put("name","CD taps pause/resume and outer-rim rotary seek remains functional beside page navigation").put("passed",true))
        main{activity.startActivity(Intent(activity,MainActivity::class.java).setAction(Intent.ACTION_MAIN).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP))};waitFor{s.page==0}
        check(main{s.isLocal&&s.activeId==id});checks.put(JSONObject().put("name","Ordinary launcher entry opens the device page while retaining the local source").put("passed",true))
        tapAction("device-power");waitFor{!s.powered&&!activity.surface.refreshing}
        main{activity.startActivity(Intent(activity,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("show-player",true))};Thread.sleep(600)
        check(main{s.page==0&&!s.powered});tapAction("device-power");waitFor{s.powered&&!activity.surface.refreshing}
        checks.put(JSONObject().put("name","A powered-off widget entry opens the device page so the physical power key stays reachable").put("passed",true))
        return JSONObject().put("passed",true).put("checks",checks)
    }
    fun persistence(operation:String):JSONObject{
        val s=activity.session;val checks=JSONArray();val local=operation.endsWith("local");val seed=operation.startsWith("seed-")
        if(seed){
            main{if(local)s.selectLocal(LocalMusic.bundled(test.targetContext),2)else{val station=Station("restore:soma","SomaFM Groove Salad",stream="https://ice6.somafm.com/groovesalad-128-aac");s.savedStations[station.id]=station;s.select(station.id)}}
            waitFor{s.playing};if(local){main{s.seekLocal(32000)};waitFor{PlaybackService.current!!.live.audio.player.currentPosition>=31500}}
            main{PlaybackService.current!!.live.savePosition()};Thread.sleep(450)
            checks.put(JSONObject().put("name","Persisted real $local source before force-stop").put("passed",true))
        }else{
            check(main{s.page==0&&s.isLocal==local})
            if(local){check(main{s.localIndex==2&&s.localTrack?.title=="Where Water Meets Stone"&&s.displayPositionMs>=30000})}else check(main{s.station?.id=="restore:soma"&&s.localTrack==null})
            line(1064f,574f,-230f);waitFor{s.page==1}
            if(local)waitFor{activity.surface.immersivePlayback.coverLoaded}else{Thread.sleep(600);check(main{!activity.surface.immersivePlayback.coverLoaded&&activity.surface.immersivePlayback.needsFallback})}
            checks.put(JSONObject().put("name","After actual process restart, main page and immersive artwork share the restored ${if(local)"local track/position"else"radio source"}").put("passed",true))
            line(568f,568f,330f);waitFor{s.page==0};checks.put(JSONObject().put("name","Navigation remains enabled after cold source restoration").put("passed",true))
        }
        return JSONObject().put("passed",true).put("checks",checks).put("source",if(s.isLocal)"local"else"radio").put("title",s.localTrack?.title?:s.station?.name).put("positionMs",s.displayPositionMs)
    }
}
