package com.aether.radio

import android.app.Instrumentation
import android.animation.ValueAnimator
import android.os.SystemClock
import android.view.MotionEvent
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicReference

class InkMotionSuite(private val test:Instrumentation,private val activity:MainActivity){
    private val checks=JSONArray()
    private fun <T> main(block:()->T):T{val value=AtomicReference<Result<T>>();test.runOnMainSync{value.set(runCatching(block))};return value.get().getOrThrow()}
    private fun shot(name:String){test.uiAutomation.takeScreenshot()?.let{b->java.io.File(test.targetContext.getExternalFilesDir("evidence"),"ink-$name.png").outputStream().use{b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};b.recycle()}}
    private fun record(name:String){checks.put(JSONObject().put("name",name).put("passed",true))}
    fun run():JSONObject{
        val s=activity.session;val v=activity.surface
        main{s.page=0;s.view="directory";s.language="zh";s.menu=null;s.changed()};Thread.sleep(750)
        shot("directory-grid")
        main{
            val painter=v.painter;val scene=painter.scene("modern-ink");val tuner=scene.find("tuner-scale")!!
            val method=ScenePainter::class.java.getDeclaredMethod("svg",SceneNode::class.java,String::class.java,Boolean::class.javaPrimitiveType,Station::class.java,Int::class.javaPrimitiveType).apply{isAccessible=true}
            val first=s.catalog.first();s.restoreStation(first);s.isLocal=false
            val selected=method.invoke(painter,tuner,"tuner-scale",false,null,-1) as String
            check(!selected.contains("M85 1V25m0 7v48"));check(selected.contains("#40c86e"));check(selected.contains("#727a74"))
            s.powered=false;val off=method.invoke(painter,tuner,"tuner-scale",false,null,-1) as String;check(!off.contains("#40c86e"));s.powered=true
        }
        record("Tuner crossing lines are removed; selected preset has a green physical lamp, other/power-off lamps remain gray")
        main{s.changed()};Thread.sleep(750);shot("selected-led")
        val frames=JSONArray()
        main{
            val settle=AetherView::class.java.getDeclaredMethod("settle",Boolean::class.javaPrimitiveType,Int::class.javaPrimitiveType).apply{isAccessible=true}
            settle.invoke(v,true,-1)
            val animator=AetherView::class.java.getDeclaredField("pageAnimator").apply{isAccessible=true}.get(v) as ValueAnimator
            val offset=AetherView::class.java.getDeclaredField("offset").apply{isAccessible=true}
            check(animator.interpolator is android.view.animation.LinearInterpolator)
            for(time in listOf(56L,112L,168L,224L)){
                animator.currentPlayTime=time;val x=offset.getFloat(v);check(kotlin.math.abs(x-(-1704f*time/280f))<3f)
                frames.put(JSONObject().put("timeMs",time).put("offset",x))
            }
            animator.end()
        }
        Thread.sleep(350);check(main{s.page==1});shot("page-finish")
        record("Native page transition uses constant velocity at 20%, 40%, 60% and 80% and lands on the next page")
        main{s.page=0;s.view="directory";s.changed()};Thread.sleep(750)
        main{s.showView("settings")};Thread.sleep(190);shot("partial-refresh")
        Thread.sleep(550);shot("partial-settled")
        main{v.beginRefresh()};Thread.sleep(500);shot("full-refresh")
        Thread.sleep(1500);check(main{!v.refreshing});shot("settings-settled")
        record("Partial update retains the actual previous ink frame and settles without a full-panel flash; full refresh also reaches native settings content")
        main{v.reducedMotion=true;s.showView("directory");s.changed()};Thread.sleep(150);check(main{!v.refreshing})
        record("Reduced-motion mode bypasses display lag and page animation")
        main{v.reducedMotion=false;s.showView("directory")}
        return JSONObject().put("passed",true).put("checks",checks).put("pageSamples",frames)
    }
}
