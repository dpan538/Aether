package com.aether.radio

import android.app.Instrumentation
import android.appwidget.*
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.util.SizeF
import android.view.View
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicReference

class WidgetVariantsSuite(private val test:Instrumentation,private val activity:MainActivity){
    private fun <T> main(block:()->T):T{val r=AtomicReference<Result<T>>();test.runOnMainSync{r.set(runCatching(block))};return r.get().getOrThrow()}
    private fun waitFor(block:()->Boolean){val end=SystemClock.uptimeMillis()+15000;while(SystemClock.uptimeMillis()<end){if(main(block))return;Thread.sleep(150)};error("Widget variant timed out")}
    fun run():JSONObject{
        val checks=JSONArray();val manager=AppWidgetManager.getInstance(test.targetContext);val host=AppWidgetHost(test.targetContext,9001)
        main{activity.session.selectLocal(LocalMusic.bundled(test.targetContext),0)};waitFor{activity.session.playing}
        val player=main{PlaybackService.current!!.live.audio.player}
        for((style,cls)in listOf("compact" to CompactMusicWidget::class.java,"strip" to StripMusicWidget::class.java,"card" to MusicWidget::class.java)){
            val id=host.allocateAppWidgetId();val provider=ComponentName(test.targetContext,cls)
            val min=when(style){"compact"->224 to 144;"strip"->320 to 104;else->360 to 224}
            val max=when(style){"compact"->280 to 152;"strip"->560 to 112;else->480 to 240}
            if(!manager.bindAppWidgetIdIfAllowed(id,provider,Bundle())){
                main{activity.startActivityForResult(Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id).putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER,provider),99)}
                val end=SystemClock.uptimeMillis()+15000
                while(manager.getAppWidgetInfo(id)==null&&SystemClock.uptimeMillis()<end){
                    fun walk(n:android.view.accessibility.AccessibilityNodeInfo):List<android.view.accessibility.AccessibilityNodeInfo> = listOf(n)+(0 until n.childCount).flatMap{n.getChild(it)?.let{walk(it)}?:emptyList()}
                    test.uiAutomation.rootInActiveWindow?.let{root->walk(root).firstOrNull{it.viewIdResourceName=="android:id/button1"&&it.isClickable}?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)};Thread.sleep(150)
                }
                check(manager.getAppWidgetInfo(id)!=null)
            }
            val screen=test.startActivitySync(Intent().setComponent(ComponentName(test.targetContext.packageName,"com.aether.radio.WidgetHostFixture")).putExtra("widget-id",id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            try{
                fun widget()=screen.javaClass.getMethod("getWidget").invoke(screen) as AppWidgetHostView
                Thread.sleep(800)
                for((w,h)in (listOf(min,max,900 to 400)+if(style=="card")listOf(280 to 224)else emptyList())){
                    val sizes=arrayListOf(SizeF(w.toFloat(),h.toFloat()))
                    manager.updateAppWidgetOptions(id,Bundle().apply{putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES,sizes);putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,w);putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,h);putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH,w);putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,h)})
                    main{val v=widget();val d=screen.resources.displayMetrics.density;v.layoutParams=v.layoutParams.apply{width=(w*d).toInt();height=(h*d).toInt()};MusicWidget.updateAll(screen,PlaybackService.current!!.live)}
                    Thread.sleep(650)
                    main{
                        val root=widget().findViewById<View>(R.id.widget_root);val d=screen.resources.displayMetrics.density
                        check(root.width<=max.first*d+1&&root.height<=max.second*d+1){"$style stretched to ${root.width/d} × ${root.height/d}"}
                        val play=widget().findViewById<View>(R.id.widget_play);val progress=widget().findViewById<View>(R.id.widget_progress)
                        val a=IntArray(2);val b=IntArray(2);play.getLocationOnScreen(a);progress.getLocationOnScreen(b)
                        check(b[1]>=a[1]+play.height+6*d){"$style play overlaps progress"}
                        if(style=="card"&&w>=360){val cover=widget().findViewById<View>(R.id.widget_cover);check(cover.height>=root.height*.60f){"Large card cover too small"}}
                        if(style=="card"&&w>=360){
                            val time=widget().findViewById<View>(R.id.widget_time);val tp=IntArray(2);time.getLocationOnScreen(tp)
                            check(tp[1]+time.height<=b[1]){"Time overlaps progress"}
                            check(kotlin.math.abs((tp[0]+time.width)-(b[0]+progress.width))<=1){"Time and progress right edges are not aligned"}
                            check(tp[1]>=a[1]+play.height){"Time still floats between metadata and controls"}
                        }
                        val title=widget().findViewById<TextView>(R.id.widget_title);check(title.width>36*d)
                        val bitmap=android.graphics.Bitmap.createBitmap(root.width,root.height,android.graphics.Bitmap.Config.ARGB_8888);root.draw(android.graphics.Canvas(bitmap))
                        java.io.File(test.targetContext.getExternalFilesDir("evidence"),"widget-$style-$w-$h.png").outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
                    }
                    checks.put(JSONObject().put("name","$style at $w × $h dp stays bounded, with visible title and separated controls/progress").put("passed",true))
                }
                val elapsed=main{widget().findViewById<TextView>(R.id.widget_time).text.toString()};Thread.sleep(2200)
                main{check(widget().findViewById<TextView>(R.id.widget_time).text.toString()!=elapsed){"Responsive progress actions did not advance time"}}
                main{widget().findViewById<View>(R.id.widget_play).performClick()};waitFor{!player.isPlaying};main{widget().findViewById<View>(R.id.widget_play).performClick()};waitFor{player.isPlaying}
                main{widget().findViewById<View>(R.id.widget_next).performClick()};waitFor{activity.session.localIndex==1};main{widget().findViewById<View>(R.id.widget_previous).performClick()};waitFor{activity.session.localIndex==0}
                main{check(player===PlaybackService.current!!.live.audio.player)}
                checks.put(JSONObject().put("name","$style pause/resume/next/previous command the same background player").put("passed",true))
            }finally{main{screen.finish()};host.deleteAppWidgetId(id)}
        }
        return JSONObject().put("passed",true).put("checks",checks).put("host","Android AppWidgetHost at 3.4K; Xiaomi launcher behavior still needs physical acceptance")
    }
}
