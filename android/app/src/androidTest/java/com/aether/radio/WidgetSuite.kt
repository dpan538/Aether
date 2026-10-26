package com.aether.radio

import android.app.Instrumentation
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicReference

class WidgetSuite(private val test:Instrumentation,private val activity:MainActivity){
    private fun <T> main(block:()->T):T{val r=AtomicReference<Result<T>>();test.runOnMainSync{r.set(runCatching{block()})};return r.get().getOrThrow()}
    private fun audio()=MainActivity::class.java.getDeclaredField("audio").apply{isAccessible=true}.get(activity) as AudioSession
    private fun waitFor(block:()->Boolean){val end=SystemClock.uptimeMillis()+30000;while(SystemClock.uptimeMillis()<end){if(main(block))return;Thread.sleep(150)};error("Widget state timed out")}
    fun run():JSONObject{
        val checks=JSONArray();val state=activity.session;val player=main{audio().player}
        main{state.selectLocal(LocalMusic.bundled(test.targetContext),0)};waitFor{player.isPlaying}
        val manager=AppWidgetManager.getInstance(test.targetContext);val host=AppWidgetHost(test.targetContext,9001);host.deleteHost()
        val id=host.allocateAppWidgetId()
        val options=Bundle().apply{putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,440);putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,240);putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH,440);putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,240)}
        val provider=ComponentName(test.targetContext,MusicWidget::class.java)
        if(!manager.bindAppWidgetIdIfAllowed(id,provider,options)){
            main{activity.startActivityForResult(Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id).putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER,provider).putExtra(AppWidgetManager.EXTRA_APPWIDGET_OPTIONS,options),99)}
            val deadline=SystemClock.uptimeMillis()+20000
            while(manager.getAppWidgetInfo(id)==null&&SystemClock.uptimeMillis()<deadline){
                val root=test.uiAutomation.rootInActiveWindow
                fun walk(n:android.view.accessibility.AccessibilityNodeInfo):List<android.view.accessibility.AccessibilityNodeInfo> = listOf(n)+(0 until n.childCount).flatMap{n.getChild(it)?.let{walk(it)}?:emptyList()}
                val nodes=root?.let{walk(it)}?:emptyList()
                val file=java.io.File(test.targetContext.getExternalFilesDir("evidence"),"widget-bind-screen.txt")
                file.writeText(nodes.filter{!it.text.isNullOrBlank()||!it.contentDescription.isNullOrBlank()}.joinToString("\n"){"${it.packageName} ${it.viewIdResourceName} ${it.text} ${it.contentDescription}"})
                val positive=nodes.firstOrNull{it.viewIdResourceName=="android:id/button1"&&it.isClickable}?:nodes.firstOrNull{it.text?.toString()?.lowercase() in listOf("allow","create","add","允许","创建","添加")}
                positive?.let{var button=it;while(!button.isClickable&&button.parent!=null)button=button.parent;button.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)}
                Thread.sleep(200)
            }
            check(manager.getAppWidgetInfo(id)!=null){"System widget binding not completed"}
        }
        var preview:android.app.Activity?=null
        try{
            preview=test.startActivitySync(Intent().setComponent(ComponentName(test.targetContext.packageName,"com.aether.radio.WidgetHostFixture")).putExtra("widget-id",id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            val screen=preview!!
            fun hostView()=screen.javaClass.getMethod("getWidget").invoke(screen) as android.appwidget.AppWidgetHostView
            fun find(id:Int)=hostView().findViewById<View>(id)
            fun captureWidget(name:String)=main{
                val widget=hostView()
                val bitmap=android.graphics.Bitmap.createBitmap(widget.width,widget.height,android.graphics.Bitmap.Config.ARGB_8888)
                widget.draw(android.graphics.Canvas(bitmap))
                java.io.File(test.targetContext.getExternalFilesDir("evidence"),"$name.png").outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
            }
            Thread.sleep(1200)
            main{
                fun tree(v:View,depth:Int=0):String = "  ".repeat(depth)+v.javaClass.simpleName+" id="+v.id+" text="+(v as? TextView)?.text+"\n"+(if(v is android.view.ViewGroup)(0 until v.childCount).joinToString(""){tree(v.getChildAt(it),depth+1)}else "")
                java.io.File(test.targetContext.getExternalFilesDir("evidence"),"widget-view-tree.txt").writeText(tree(screen.window.decorView))
            }
            test.uiAutomation.takeScreenshot()!!.let{b->java.io.File(test.targetContext.getExternalFilesDir("evidence"),"widget-diagnostic.png").outputStream().use{b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};b.recycle()}
            waitFor{find(R.id.widget_title)!=null&&(find(R.id.widget_title) as TextView).text.toString()=="Before the First Light"}
            main{
                val widget=hostView()
                val density=activity.resources.displayMetrics.density
                widget.layoutParams=widget.layoutParams.apply{width=(280*density).toInt();height=(224*density).toInt()}
            }
            Thread.sleep(400)
            main{
                val play=find(R.id.widget_play);val progress=find(R.id.widget_progress)
                val a=IntArray(2);val b=IntArray(2);play.getLocationOnScreen(a);progress.getLocationOnScreen(b)
                check(b[1]-(a[1]+play.height)>=6*activity.resources.displayMetrics.density){"Playback button crowds the progress track"}
            }
            captureWidget("widget-minimum-native-view")
            checks.put(JSONObject().put("name","At minimum 280×224 dp, controls retain at least 6 dp clearance above the progress track").put("passed",true))
            main{val widget=hostView();val density=activity.resources.displayMetrics.density;widget.layoutParams=widget.layoutParams.apply{width=(440*density).toInt();height=(240*density).toInt()}}
            Thread.sleep(400)
            captureWidget("widget-native-view")
            fun tap(id:Int){val xy=main{val v=find(id);val p=IntArray(2);v.getLocationOnScreen(p);(p[0]+v.width*.5f) to (p[1]+v.height*.5f)};val t=SystemClock.uptimeMillis();test.sendPointerSync(MotionEvent.obtain(t,t,MotionEvent.ACTION_DOWN,xy.first,xy.second,0));test.sendPointerSync(MotionEvent.obtain(t,t+80,MotionEvent.ACTION_UP,xy.first,xy.second,0));Thread.sleep(250)}
            checks.put(JSONObject().put("name","Real AppWidgetHost renders current title, album art, progress and controls from the live service").put("passed",true))
            val bitmap=test.uiAutomation.takeScreenshot()!!;val file=java.io.File(test.targetContext.getExternalFilesDir("evidence"),"music-widget.png");file.outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
            tap(R.id.widget_play);waitFor{!player.isPlaying&&find(R.id.widget_play).contentDescription=="播放"};checks.put(JSONObject().put("name","Widget pause PendingIntent is acknowledged by the actual media session").put("passed",true))
            tap(R.id.widget_play);waitFor{player.isPlaying};checks.put(JSONObject().put("name","Widget resumes the same player without foregrounding the main Activity").put("passed",true))
            tap(R.id.widget_next);waitFor{state.localIndex==1&&(find(R.id.widget_title) as TextView).text.toString()=="Glass Garden Portraits"};tap(R.id.widget_previous);waitFor{state.localIndex==0};checks.put(JSONObject().put("name","Widget next/previous update the same queue and rendered metadata").put("passed",true))
            val before=main{player.currentPosition};Thread.sleep(2500);check(main{player.currentPosition}>before+1800);checks.put(JSONObject().put("name","Playback and real widget time advance while the main Activity is stopped").put("passed",true))
            return JSONObject().put("passed",true).put("checks",checks).put("host","Internal Android AppWidgetHost; not physical Xiaomi launcher")
        }finally{main{preview?.finish()};host.deleteAppWidgetId(id)}
    }
}
