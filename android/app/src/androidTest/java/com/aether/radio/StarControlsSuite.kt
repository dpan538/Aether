package com.aether.radio

import android.app.Instrumentation
import android.os.SystemClock
import android.view.MotionEvent
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicReference

class StarControlsSuite(private val test:Instrumentation,private val activity:MainActivity){
    private val s get()=activity.session
    private val checks=JSONArray()
    private fun <T> main(block:()->T):T{val r=AtomicReference<Result<T>>();test.runOnMainSync{r.set(runCatching{block()})};return r.get().getOrThrow()}
    private fun waitFor(block:()->Boolean){val end=SystemClock.uptimeMillis()+30000;while(SystemClock.uptimeMillis()<end){if(main(block))return;Thread.sleep(100)};error("Star controls timed out")}
    private fun tap(action:String){val p=main{val v=activity.surface;val scene=v.painter.scene(v.painter.name(s.light));val r=if(action.startsWith("preset:"))scene.find("favorite-stations")!!.children[action.substringAfter(':').toInt()].r else scene.flattened().first{it.id==action||it.action==action}.r;val z=minOf(v.width/1704f,v.height/1136f);val loc=IntArray(2);v.getLocationOnScreen(loc);(loc[0]+(v.width-1704*z)/2+r.centerX()*z) to (loc[1]+(v.height-1136*z)/2+r.centerY()*z)};val now=SystemClock.uptimeMillis();test.sendPointerSync(MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,p.first,p.second,0));Thread.sleep(70);test.sendPointerSync(MotionEvent.obtain(now,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,p.first,p.second,0));Thread.sleep(550)}
    private fun audio()=MainActivity::class.java.getDeclaredField("audio").apply{isAccessible=true}.get(activity) as AudioSession
    private fun record(name:String){checks.put(JSONObject().put("passed",true).put("name",name))}
    fun run(restore:Boolean=false):JSONObject{
        val album=LocalMusic.bundled(test.targetContext);val track=album.first()
        main{s.page=0;s.showView("directory");s.selectLocal(album,0)};waitFor{s.playing&&!activity.surface.refreshing}
        if(restore){check(main{track.id in s.starredTracks});record("A fresh process restores the exact previously starred track from device preferences");tap("favorite-toggle");check(main{track.id !in s.starredTracks});record("Tapping the active star removes the persisted track star");return JSONObject().put("passed",true).put("checks",checks)}
        main{s.starredTracks.remove(track.id);s.changed()};Thread.sleep(400)
        val previous=main{s.favorites.toList()}
        tap("favorite-toggle");check(main{track.id in s.starredTracks&&s.favorites==previous})
        record("Actual left-star touch stars the playing song and leaves radio presets intact")
        tap("monitor-toggle");waitFor{s.page==1};check(main{s.activeId==track.id&&s.playing});record("Actual right-waveform touch opens immersive playback without replacing or pausing the player")
        main{s.page=0;s.changed()};Thread.sleep(500)
        val candidates=main{s.catalog.filter{it.stream?.startsWith("https://")==true}.distinctBy{it.id}.take(6)};check(candidates.size==6)
        main{s.favorites.clear();s.favorites.addAll(candidates.take(5).map{it.id});s.presetOffset=2;s.select(candidates.last().id);s.changed()};Thread.sleep(500)
        tap("favorite-toggle");check(main{s.favorites.size==6&&s.presetStart==0&&s.presetPool().take(4).any{it.id==candidates.last().id}})
        record("Starring a sixth real station puts it into the visible four-key bank immediately, preserving all previous favorites")
        main{s.select(candidates.first().id)};Thread.sleep(400);tap("preset:0")
        check(main{s.station?.id==candidates.last().id&&audio().player.currentMediaItem?.mediaId==candidates.last().id})
        record("The newly populated preset key actually selects that station")
        tap("favorite-toggle");check(main{candidates.last().id !in s.favorites});record("Unstarring the station removes its favorite entry and updates the bank")
        main{s.favorites.clear();s.favorites.addAll(previous);s.presetOffset=0;s.onFavorites();s.selectLocal(album,0)}
        return JSONObject().put("passed",true).put("checks",checks).put("starredTrack",track.id)
    }
}
