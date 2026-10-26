package com.aether.radio

import android.app.Instrumentation
import android.appwidget.AppWidgetManager
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicReference

/** Checks the installed provider contract and real, differently-owned host allocations. */
class WidgetSizingSuite(private val test:Instrumentation,private val activity:MainActivity){
    private fun <T> main(block:()->T):T{val r=AtomicReference<Result<T>>();test.runOnMainSync{r.set(runCatching(block))};return r.get().getOrThrow()}
    private fun shell(command:String)=android.os.ParcelFileDescriptor.AutoCloseInputStream(test.uiAutomation.executeShellCommand(command)).bufferedReader().use{it.readText()}
    private fun host():JSONObject?=runCatching{JSONObject(shell("run-as io.github.dpan538.widgethost cat files/state.json"))}.getOrNull()
    private fun waitFor(block:()->Boolean){val end=SystemClock.uptimeMillis()+20000;while(SystemClock.uptimeMillis()<end){if(block())return;Thread.sleep(150)};error("Widget sizing timed out: "+host())}
    fun run():JSONObject{
        val checks=JSONArray();val allocations=JSONArray();val providers=JSONArray()
        fun record(name:String){checks.put(JSONObject().put("name",name).put("passed",true))}
        val manager=AppWidgetManager.getInstance(test.targetContext)
        main{MusicWidget.publishPreview(test.targetContext)}
        val density=test.targetContext.resources.displayMetrics.density
        for((name,expected) in listOf("CompactMusicWidget" to listOf(110,110,2,2),"MusicWidget" to listOf(180,110,3,2),"StripMusicWidget" to listOf(180,40,3,1))){
            val p=manager.installedProviders.single{it.provider.packageName==test.targetContext.packageName&&it.provider.className.endsWith(".$name")}
            val w=kotlin.math.round(p.minWidth/density).toInt();val h=kotlin.math.round(p.minHeight/density).toInt()
            check(w==expected[0]&&h==expected[1]){"Installed legacy sizing is $w x $h, expected $expected for $name"}
            check(p.targetCellWidth==expected[2]&&p.targetCellHeight==expected[3])
            check(p.minResizeWidth<=p.minWidth&&p.minResizeHeight<=p.minHeight)
            // The documented legacy 70n-30 estimate. This is not a Xiaomi launcher execution.
            check(kotlin.math.ceil((w+30)/70.0).toInt()==expected[2]&&kotlin.math.ceil((h+30)/70.0).toInt()==expected[3])
            if(android.os.Build.VERSION.SDK_INT>=35)check(p.generatedPreviewCategories==0)
            main{
                val v=RemoteViews(test.targetContext.packageName,p.previewLayout).apply(test.targetContext,null)
                v.measure(View.MeasureSpec.makeMeasureSpec(p.minWidth,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(p.minHeight,View.MeasureSpec.EXACTLY));v.layout(0,0,v.measuredWidth,v.measuredHeight)
                val image=v as android.widget.ImageView
                val rect=android.graphics.RectF(image.drawable.bounds);image.imageMatrix.mapRect(rect)
                check(rect.width()>0&&rect.height()>0&&rect.left>=-1&&rect.top>=-1&&rect.right<=v.width+1&&rect.bottom<=v.height+1)
            }
            providers.put(JSONObject().put("provider",name).put("minWidthDp",w).put("minHeightDp",h).put("targetColumns",p.targetCellWidth).put("targetRows",p.targetCellHeight))
            record("$name: installed dp hints, resize bounds, grid targets and bounded picker preview agree")
        }
        val s=activity.session
        main{s.powered=true;s.locked=false;s.selectLocal(LocalMusic.bundled(test.targetContext),6)}
        waitFor{main{s.playing}};main{s.togglePlay()};waitFor{main{!s.playing}}
        shell("am start -W -n io.github.dpan538.widgethost/.HostActivity --es command reset");Thread.sleep(250)
        val cases=mapOf("compact" to listOf(110 to 110,180 to 180,280 to 280,600 to 300),"card" to listOf(180 to 110,320 to 180,600 to 300,900 to 400),"strip" to listOf(180 to 40,320 to 72,480 to 104,600 to 200))
        for((style,sizes) in cases)for(exact in listOf(false,true))for((w,h) in sizes){
            val tag="$style-$w-$h-$exact-${SystemClock.uptimeMillis()}"
            shell("am start -W -n io.github.dpan538.widgethost/.HostActivity --es case $tag --es style $style --ei width $w --ei height $h --ez exact $exact")
            val end=SystemClock.uptimeMillis()+20000
            while((host()?.optString("case")!=tag||host()?.optBoolean("resumed")!=true)&&SystemClock.uptimeMillis()<end){
                fun walk(n:android.view.accessibility.AccessibilityNodeInfo):List<android.view.accessibility.AccessibilityNodeInfo> = listOf(n)+(0 until n.childCount).flatMap{n.getChild(it)?.let{walk(it)}?:emptyList()}
                test.uiAutomation.rootInActiveWindow?.let{root->walk(root).firstOrNull{it.viewIdResourceName=="android:id/button1"&&it.isClickable}?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)};Thread.sleep(150)
            }
            waitFor{host()?.optString("case")==tag&&host()?.optJSONObject("widget_play")?.optString("description")=="播放"&&host()?.optJSONObject("widget_title")?.optString("text")==main{s.localTrack!!.title}}
            Thread.sleep(700)
            val state=host()!!;val root=state.getJSONObject("widget_root");val outer=state.getJSONObject("host");val padding=state.getJSONArray("padding")
            check(state.getInt("uid")!=android.os.Process.myUid())
            check(root.getInt("w")==outer.getInt("w")-padding.getInt(0)-padding.getInt(2)&&root.getInt("h")==outer.getInt("h")-padding.getInt(1)-padding.getInt(3))
            check(state.has("widget_art_open")== (style=="compact")) {"Cover provider changed presentation: $state"}
            check(state.has("widget_play_cell")== (style=="card")) {"Card provider changed presentation: $state"}
            for(key in listOf("widget_title","widget_album","widget_cover","widget_time","widget_progress","widget_previous","widget_play","widget_next")){
                val child=state.getJSONObject(key)
                check(child.getInt("w")>0&&child.getInt("h")>0&&child.getInt("x")>=root.getInt("x")-1&&child.getInt("y")>=root.getInt("y")-1&&child.getInt("x")+child.getInt("w")<=root.getInt("x")+root.getInt("w")+1&&child.getInt("y")+child.getInt("h")<=root.getInt("y")+root.getInt("h")+1){"Clipped $style $w x $h: $key $state"}
                if(key in listOf("widget_title","widget_album","widget_time"))check(child.getInt("fontTop")>=-1&&child.getInt("fontBottom")<=child.getInt("h")+1){"Clipped glyphs: $key $state"}
            }
            val clock=state.getJSONObject("widget_time");val bar=state.getJSONObject("widget_progress")
            check(clock.getInt("y")+clock.getInt("h")<=bar.getInt("y")+1)
            val b=test.uiAutomation.takeScreenshot()!!
            check(listOf(.25,.5,.75).any{f->val c=b.getPixel(bar.getInt("x")+(bar.getInt("w")*f).toInt(),bar.getInt("y")+bar.getInt("h")/2);android.graphics.Color.red(c)+android.graphics.Color.green(c)+android.graphics.Color.blue(c)>110}){"Invisible progress track: $state"}
            java.io.File(test.targetContext.getExternalFilesDir("evidence"),"sizing-$style-$w-$h-$exact.png").outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()
            allocations.put(state);java.io.File(test.targetContext.getExternalFilesDir("evidence"),"widget-sizing-allocations.json").writeText(allocations.toString(2))
            record("$style $w x $h dp (exact=$exact): identity, full allocation, long-title glyphs, time, track and controls stay inside bounds")
            shell("am start -W -n io.github.dpan538.widgethost/.HostActivity --es command finish")
        }
        return JSONObject().put("passed",true).put("checks",checks).put("providers",providers).put("allocations",allocations).put("physicalXiaomiLauncherTested",false)
    }
}
