package com.aether.radio

import android.app.Instrumentation
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RenderNode
import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicReference

/** Faults are injected only from the separate test APK, never a production control. */
class RenderRecoverySuite(private val test:Instrumentation,private val activity:MainActivity){
    private val checks=JSONArray()
    private val surface get()=activity.surface
    private fun <T> main(block:()->T):T{val r=AtomicReference<Result<T>>();test.runOnMainSync{r.set(runCatching(block))};return r.get().getOrThrow()}
    private fun waitFor(seconds:Int=25,block:()->Boolean){val end=SystemClock.uptimeMillis()+seconds*1000;while(SystemClock.uptimeMillis()<end){if(main(block))return;Thread.sleep(150)};error("Render recovery timed out")}
    @Suppress("UNCHECKED_CAST") private fun nodes():List<RenderNode>{
        val layers=AetherView::class.java.getDeclaredField("nativeLayers").apply{isAccessible=true}.get(surface) as Map<String,Any>
        return layers.values.map{it.javaClass.getDeclaredField("node").apply{isAccessible=true}.get(it) as RenderNode}
    }
    private fun capture(name:String):Bitmap{
        val b=test.uiAutomation.takeScreenshot()!!
        java.io.File(test.targetContext.getExternalFilesDir("evidence"),"recovery-$name.png").outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)}
        return b
    }
    private fun paperVisible(b:Bitmap):Boolean{
        val scale=minOf(b.width/1704f,b.height/1136f);val ox=(b.width-1704*scale)/2;val oy=(b.height-1136*scale)/2
        val (x,y)=if(activity.session.page==0)820f to 100f else 1144f to 600f
        val c=b.getPixel((ox+x*scale).toInt(),(oy+y*scale).toInt());return Color.red(c)+Color.green(c)+Color.blue(c)>210
    }
    private fun record(name:String){checks.put(JSONObject().put("name",name).put("passed",true))}
    fun run():JSONObject{
        val s=activity.session
        main{s.page=0;s.showView("directory");s.menu=null;s.selectLocal(LocalMusic.bundled(test.targetContext),0);s.changed()}
        waitFor{!surface.refreshing&&s.playing&&surface.committedOffset==0f};Thread.sleep(1500)
        val player=main{PlaybackService.current!!.live.audio.player};val before=main{player.currentPosition}
        capture("baseline").also{check(paperVisible(it));it.recycle()}
        main{check(nodes().isNotEmpty());nodes().forEach{it.discardDisplayList()};surface.invalidate()}
        Thread.sleep(1100)
        main{check(nodes().any{it.hasDisplayList()}){"Visible scene was not re-recorded"};check(surface.renderRecoveries==0){"Unexpected watchdog recovery ${surface.renderRecoveries}"}}
        capture("discarded-list-restored").also{check(paperVisible(it));it.recycle()}
        record("Discarded native display lists re-record even when source identity and dimensions are unchanged; actual paper pixels return")
        // Suspend only the test's periodic device-value updates, so they cannot mask this fault.
        main{val h=MainActivity::class.java.getDeclaredField("main").apply{isAccessible=true}.get(activity) as android.os.Handler;val tick=MainActivity::class.java.getDeclaredField("deviceTick").apply{isAccessible=true}.get(activity) as Runnable;h.removeCallbacks(tick)}
        for(page in listOf(1,0)){
            main{s.page=page;s.changed()};Thread.sleep(1800)
            val count=main{surface.renderRecoveries}
            // Freeze the watchdog only while proving the injected fault. Screenshot
            // latency must not race a successful automatic recovery on a busy emulator.
            main{surface.renderHealth.stop();nodes().forEach{it.beginRecording(it.width,it.height);it.endRecording()};surface.invalidate()}
            Thread.sleep(350)
            capture("blank-valid-node-page-$page").also{check(!paperVisible(it)){"Injection did not remove the static material"};it.recycle()}
            main{surface.renderHealth.start()}
            waitFor{surface.renderRecoveries>count};Thread.sleep(800)
            capture("recovered-page-$page").also{check(paperVisible(it)){"Recovery did not restore visible pixels"};it.recycle()}
            record("Page $page: a valid but empty static GPU layer is detected from two stable window samples and restored automatically")
        }
        main{check(surface.directRendering);check(player===PlaybackService.current!!.live.audio.player);check(player.isPlaying);check(player.currentPosition>before+5000)}
        record("Repeated loss selects direct native-resolution drawing, with the same playing Media3 instance and advancing audio position")
        val count=main{surface.renderRecoveries}
        main{surface.beginRefresh()};Thread.sleep(2500)
        repeat(3){main{surface.pause();surface.resume();surface.invalidate()};Thread.sleep(700)}
        Thread.sleep(4500)
        main{check(surface.renderRecoveries==count);check(surface.directRendering);check(player.isPlaying)}
        capture("stable-safe-path").also{check(paperVisible(it));it.recycle()}
        record("Ink refresh and repeated pause/resume cause no false recovery, preserve safe drawing and keep playback alive")
        return JSONObject().put("passed",true).put("checks",checks).put("recoveries",main{surface.renderRecoveries}).put("resolution","3408×2272; original CD texture retained").put("limit","Fault-injection coverage; the original physical Xiaomi GPU eviction trigger is not independently reproduced")
    }
}
