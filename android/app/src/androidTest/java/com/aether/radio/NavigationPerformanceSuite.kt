package com.aether.radio

import android.app.Instrumentation
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.view.FrameMetrics
import android.view.MotionEvent
import android.view.Window
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.*

/** Actual window frame timing during native drag/release, at the full display resolution. */
class NavigationPerformanceSuite(private val test:Instrumentation,private val activity:MainActivity){
    private fun <T> main(block:()->T):T{val r=AtomicReference<Result<T>>();test.runOnMainSync{r.set(runCatching{block()})};return r.get().getOrThrow()}
    private fun waitFor(block:()->Boolean){val end=SystemClock.uptimeMillis()+35000;while(SystemClock.uptimeMillis()<end){if(main(block))return;Thread.sleep(100)};error("Navigation condition timed out")}
    fun run():JSONObject{
        val state=activity.session;main{state.page=0;state.language="zh";state.showView("directory");activity.surface.setLight(true);state.selectLocal(LocalMusic.bundled(test.targetContext),0)}
        waitFor{state.playing&&activity.surface.immersivePlayback.coverLoaded&&!activity.surface.refreshing};Thread.sleep(1600)
        val active=AtomicBoolean(false);val samples=mutableListOf<Long>();val thread=HandlerThread("Navigation frame metrics").apply{start()}
        val listener=Window.OnFrameMetricsAvailableListener{_,frame,_->if(active.get()){val total=frame.getMetric(FrameMetrics.TOTAL_DURATION);if(total>0)synchronized(samples){samples.add(total)}}}
        main{activity.window.addOnFrameMetricsAvailableListener(listener,Handler(thread.looper))}
        val transitions=JSONArray()
        try{
            for(i in 0 until 16){
                val from=main{state.page};val direction=if(from==0)-1 else 1
                val points=main{val v=activity.surface;val scale=minOf(v.width/1704f,v.height/1136f);val loc=IntArray(2);v.getLocationOnScreen(loc);(0..10).map{j->(loc[0]+(v.width-1704*scale)/2+(1064+direction*210*j/10f)*scale) to (loc[1]+(v.height-1136*scale)/2+574*scale)}}
                val index=synchronized(samples){samples.size};active.set(true);val down=SystemClock.uptimeMillis()
                points.forEachIndexed{j,p->test.sendPointerSync(MotionEvent.obtain(down,SystemClock.uptimeMillis(),if(j==0)MotionEvent.ACTION_DOWN else if(j==points.lastIndex)MotionEvent.ACTION_UP else MotionEvent.ACTION_MOVE,p.first,p.second,0));Thread.sleep(16)}
                Thread.sleep(340);active.set(false);check(main{state.page}==1-from)
                val frames=synchronized(samples){samples.drop(index).map{it/1e6}.sorted()}
                transitions.put(JSONObject().put("index",i).put("from",from).put("to",1-from).put("frames",frames.size).put("p95Ms",if(frames.isEmpty())0.0 else frames[((frames.size-1)*.95).toInt()]).put("maxMs",frames.lastOrNull()?:0.0))
                Thread.sleep(100)
            }
        }finally{active.set(false);main{activity.window.removeOnFrameMetricsAvailableListener(listener)};thread.quitSafely()}
        val values=synchronized(samples){samples.map{it/1e6}.sorted()};check(values.isNotEmpty())
        val memory=android.os.Debug.MemoryInfo();android.os.Debug.getMemoryInfo(memory)
        val info=test.targetContext.packageManager.getPackageInfo(test.targetContext.packageName,0)
        return JSONObject().put("passed",true).put("version",info.versionName).put("versionCode",info.longVersionCode).put("width",activity.surface.width).put("height",activity.surface.height).put("transitions",transitions).put("frameCount",values.size).put("medianMs",values[values.size/2]).put("p95Ms",values[((values.size-1)*.95).toInt()]).put("maxMs",values.last()).put("over16_7Ms",values.count{it>16.7}).put("pssKb",memory.totalPss).put("environment","API 36 ARM64 emulator, 3408×2272; physical Xiaomi frame rate is not established")
    }
}
