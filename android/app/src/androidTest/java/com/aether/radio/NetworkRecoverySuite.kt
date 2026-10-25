package com.aether.radio

import android.app.Instrumentation
import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicReference

class NetworkRecoverySuite(private val test:Instrumentation,private val activity:MainActivity){
    private fun <T> main(block:()->T):T{val out=AtomicReference<Result<T>>();test.runOnMainSync{out.set(runCatching(block))};return out.get().getOrThrow()}
    private val s get()=activity.session
    private fun audio()=MainActivity::class.java.getDeclaredField("audio").apply{isAccessible=true}.get(activity) as AudioSession
    private fun retry()=AudioSession::class.java.getDeclaredField("reconnect").apply{isAccessible=true}.get(audio())
    private fun waitFor(block:()->Boolean){val end=SystemClock.uptimeMillis()+45000;while(SystemClock.uptimeMillis()<end){if(main(block))return;Thread.sleep(40)};error("Network test timed out: ${s.issue}")}
    fun run():JSONObject{
        val checks=JSONArray();fun record(name:String){checks.put(JSONObject().put("name",name).put("passed",true))}
        val unreachable=Station("verification:closed-port","Unreachable test endpoint",stream="https://127.0.0.1:9/unavailable")
        main{s.savedStations[unreachable.id]=unreachable;s.select(unreachable.id)}
        waitFor{retry()!=null}
        check(main{!s.playing&&s.playIntent});record("A real HTTPS connection failure schedules bounded recovery without claiming audio is playing")
        val future=main{androidx.media3.session.MediaController.Builder(test.targetContext,androidx.media3.session.SessionToken(test.targetContext,android.content.ComponentName(test.targetContext,PlaybackService::class.java))).buildAsync()}
        val controller=future.get(15,java.util.concurrent.TimeUnit.SECONDS)
        try{main{controller.pause()};waitFor{!s.playIntent&&retry()==null};Thread.sleep(2200);check(main{!s.playing&&retry()==null});record("A real media-session Pause cancels pending recovery and does not unexpectedly restart playback")}
        finally{main{controller.release()}}
        main{s.select(unreachable.id)};waitFor{retry()!=null}
        main{s.selectLocal(LocalMusic.bundled(test.targetContext),0)};waitFor{s.playing&&s.spectrumFrame!=null}
        Thread.sleep(2500);check(main{s.isLocal&&s.localTrack?.id=="before-the-rise:1"&&s.playing&&retry()==null})
        record("Switching to local music cancels the failed radio retry; real PCM and the current queue stay intact")
        return JSONObject().put("passed",true).put("checks",checks)
    }
}
