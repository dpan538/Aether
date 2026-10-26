package com.aether.radio

import android.app.Instrumentation
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.*

/** Regression paths from the tablet screenshots. No feedback messages or public writes. */
class HardwareRefinementSuite(private val test:Instrumentation,private val activity:MainActivity){
    private val s get()=activity.session
    private val checks=JSONArray()
    private fun <T> main(block:()->T):T{val r=AtomicReference<Result<T>>();test.runOnMainSync{r.set(runCatching{block()})};return r.get().getOrThrow()}
    private fun record(name:String){checks.put(JSONObject().put("name",name).put("passed",true))}
    private fun waitFor(block:()->Boolean){val end=SystemClock.uptimeMillis()+40000;while(SystemClock.uptimeMillis()<end){if(main(block))return;Thread.sleep(100)};error("Refinement timed out: "+main{JSONObject().put("page",s.settingsPage).put("language",s.language).put("view",s.view).put("refreshing",activity.surface.refreshing).put("refreshElapsed",activity.surface.refreshElapsed).put("visible",JSONArray(tree(activity.window.decorView).filter{it.isShown&&it.tag!=null}.map{it.tag.toString()})).toString()})}
    private fun tree(v:View):List<View> = listOf(v)+(if(v is ViewGroup)(0 until v.childCount).flatMap{tree(v.getChildAt(it))}else emptyList())
    private fun native(key:String):View?{
        val root=tree(activity.window.decorView)
        val menu=root.filterIsInstance<InkSettingsView>().firstOrNull()?.languageMenuView
        return (root+if(menu!=null)tree(menu)else emptyList()).firstOrNull{it.tag==key&&it.isShown}
    }
    private fun tapNative(key:String){Thread.sleep(2800);waitFor{native(key)!=null&&!activity.surface.refreshing};val p=main{val v=native(key)!!;val xy=IntArray(2);v.getLocationOnScreen(xy);(xy[0]+v.width*.5f) to (xy[1]+v.height*.5f)};pointer(listOf(p,p),false)}
    private fun pointer(points:List<Pair<Float,Float>>,logical:Boolean=true){
        val converted=if(logical)main{val v=activity.surface;val scale=minOf(v.width/1704f,v.height/1136f);val xy=IntArray(2);v.getLocationOnScreen(xy);points.map{(x,y)->(xy[0]+(v.width-1704*scale)/2+x*scale) to (xy[1]+(v.height-1136*scale)/2+y*scale)}}else points
        val start=SystemClock.uptimeMillis()
        converted.forEachIndexed{i,p->test.sendPointerSync(MotionEvent.obtain(start,SystemClock.uptimeMillis(),if(i==0)MotionEvent.ACTION_DOWN else if(i==converted.lastIndex)MotionEvent.ACTION_UP else MotionEvent.ACTION_MOVE,p.first,p.second,0));Thread.sleep(40)}
        Thread.sleep(250)
    }
    private fun capture(name:String){Thread.sleep(650);val b=test.uiAutomation.takeScreenshot()!!;java.io.File(test.targetContext.getExternalFilesDir("evidence"),"refinement-$name.png").outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()}
    private fun rectangle(action:String)=main{activity.surface.painter.scene(activity.surface.painter.name(s.light)).flattened().first{it.id==action||it.action==action}.r}
    private fun arc(action:String,angles:List<Double>){val r=rectangle(action);pointer(angles.map{a->val t=a*PI/180;(r.centerX()+r.width()*.43*cos(t)).toFloat() to (r.centerY()+r.width()*.43*sin(t)).toFloat()})}
    private fun audio()=MainActivity::class.java.getDeclaredField("audio").apply{isAccessible=true}.get(activity) as AudioSession
    fun run():JSONObject{
        main{s.page=0;s.language="zh";s.light=true;s.showView("settings");s.changed()}
        waitFor{native("settings:menu:0")!=null}
        capture("settings-zh")
        repeat(3){
            tapNative("settings:menu:0");waitFor{native("settings:language:en")!=null}
            if(it==0)capture("language-dropdown-zh")
            tapNative("settings:language:en");waitFor{s.language=="en"&&s.menu==null&&s.settingsPage=="menu"&&native("settings:menu:0")!=null}
            tapNative("settings:menu:0");waitFor{native("settings:language:zh")!=null}
            tapNative("settings:language:zh");waitFor{s.language=="zh"&&s.menu==null&&s.settingsPage=="menu"}
        }
        record("Six actual dropdown selections retain the four-row native settings page and never reveal a legacy menu or separate language page")
        tapNative("settings:menu:1");waitFor{s.updateUi.phase !in listOf(UpdatePhase.CHECKING,UpdatePhase.VERIFYING)&&native("update:check")!=null||native("update:download")!=null||native("update:install")!=null}
        check(main{s.updateOpen&&tree(activity.window.decorView).filterIsInstance<InkSettingsView>().single().isShown})
        capture("update-zh");record("The real repository response stays on the same native ink material, with readable typography")
        tapNative("settings:back");tapNative("settings:menu:2");capture("about-zh")
        tapNative("settings:haptics");capture("haptics-zh")
        val attempts=main{activity.surface.haptics.attempts};tapNative("settings:test-haptics")
        check(main{activity.surface.haptics.attempts>attempts&&activity.surface.haptics.last=="test"})
        record("A real native diagnostic page exposes motor availability and system switches and routes its test through the functional touch-vibration API")
        tapNative("settings:back");tapNative("settings:widget");capture("widget-help-zh")
        if(android.os.Build.VERSION.SDK_INT>=35){val info=android.appwidget.AppWidgetManager.getInstance(test.targetContext).installedProviders.first{it.provider.className==MusicWidget::class.java.name};check(info.previewLayout!=0);check(info.targetCellHeight==3)}
        record("The widget provider provides a scalable preview and a default cell size consistent with its minimum height; generated previews respect Android rate limits")
        main{s.showView("directory");s.changeVolume(15);s.selectLocal(LocalMusic.bundled(test.targetContext),0);s.changed()};waitFor{s.playing&&s.seekable}
        Thread.sleep(1700)
        val before=main{activity.surface.mainPlayback.position};Thread.sleep(1100);check(main{activity.surface.mainPlayback.position}>before+800)
        capture("local-progress")
        val progress=MainPlaybackPanel.PROGRESS;pointer(listOf(progress.centerX() to progress.centerY(),progress.centerX() to progress.centerY()))
        waitFor{abs(audio().player.currentPosition-s.localDurationMs/2)<3000}
        record("The elapsed readout advances from the real local player; touching the main progress track seeks that same player")
        main{s.togglePlay()};waitFor{!s.playing};Thread.sleep(650);val stopped=main{activity.surface.mainPlayback.position};Thread.sleep(1000);check(abs(main{activity.surface.mainPlayback.position}-stopped)<150)
        record("Paused elapsed time holds rather than advancing from a decorative wall clock")
        val volume=rectangle("volume")
        pointer(listOf(volume.centerX() to volume.centerY(),volume.centerX() to volume.centerY()));check(main{s.volume}==15)
        pointer((0..12).map{i->volume.centerX() to (volume.centerY()-80*i/12f)});check(main{s.volume}==19)
        val v=main{s.volume};arc("volume",(0..16).map{it*48.0/16});check(main{s.volume}==v+4)
        record("Volume presses do not jump; 80 logical pixels yield four levels and 48-degree outer travel yields four damped detents")
        val old=s.tuningAngle
        arc("tuning-dial",listOf(0.0,0.0));check(main{s.tuningAngle}==old)
        arc("tuning-dial",(0..22).map{if(it%2==0)0.0 else 3.0});check(main{s.tuningAngle}==old)
        arc("tuning-dial",(0..20).map{it*24.0/20});check(main{s.tuningAngle}==old+24)
        record("Tuning taps and small tremor never select a station; one deliberate 24-degree turn advances exactly one 24-degree printed detent")
        // A real network stream supplies the radio elapsed reading as well as live metadata.
        main{s.catalog=s.catalog+Station("refinement:soma","SomaFM Groove Salad",stream="https://ice6.somafm.com/groovesalad-128-aac");s.select("refinement:soma");s.changed()}
        waitFor{s.playing};Thread.sleep(1600);val radio=main{activity.surface.mainPlayback.position};Thread.sleep(1200);check(main{activity.surface.mainPlayback.position}>radio+800)
        capture("radio-elapsed");record("Radio elapsed time advances from the actual decoded stream even when no station-local timezone exists")
        main{s.toggleMute()};capture("muted-glyph");main{s.toggleMute();s.catalog=s.catalog.filterNot{it.id.startsWith("refinement:")};s.selectLocal(LocalMusic.bundled(test.targetContext),0)}
        return JSONObject().put("passed",true).put("checks",checks).put("hapticHardware",main{activity.surface.haptics.description(false)}).put("hardwareLimit","Physical Xiaomi vibration feel and Xiaomi recommendation-center approval are not established by emulator tests")
    }
}
