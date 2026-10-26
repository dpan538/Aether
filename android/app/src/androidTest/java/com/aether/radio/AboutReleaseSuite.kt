package com.aether.radio

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicReference

class AboutReleaseSuite(private val test:Instrumentation,private val activity:MainActivity){
    private fun <T> main(block:()->T):T{val r=AtomicReference<Result<T>>();test.runOnMainSync{r.set(runCatching(block))};return r.get().getOrThrow()}
    private fun tree(v:View):List<View> = listOf(v)+(if(v is ViewGroup)(0 until v.childCount).flatMap{tree(v.getChildAt(it))}else emptyList())
    private fun waitFor(block:()->Boolean){val end=SystemClock.uptimeMillis()+20000;while(SystemClock.uptimeMillis()<end){if(main(block))return;Thread.sleep(100)};error("About release page timed out")}
    private fun native(key:String)=tree(activity.window.decorView).firstOrNull{it.isShown&&it.tag==key}
    private fun tap(key:String){waitFor{native(key)!=null&&!activity.surface.refreshing};val point=main{val v=native(key)!!;val p=IntArray(2);v.getLocationOnScreen(p);p[0]+v.width/2f to p[1]+v.height/2f};val time=SystemClock.uptimeMillis();test.sendPointerSync(MotionEvent.obtain(time,time,MotionEvent.ACTION_DOWN,point.first,point.second,0));test.sendPointerSync(MotionEvent.obtain(time,time+70,MotionEvent.ACTION_UP,point.first,point.second,0));Thread.sleep(450)}
    fun run():JSONObject{
        val checks=JSONArray();fun record(name:String){checks.put(JSONObject().put("name",name).put("passed",true))}
        val s=activity.session
        main{s.powered=true;s.locked=false;s.page=0;s.selectLocal(LocalMusic.bundled(test.targetContext),0);s.showView("settings");s.settingsPage="about";s.changed()}
        waitFor{s.playing}
        for(language in listOf("zh","en")){
            main{s.language=language;s.settingsPage="about";s.changed()};waitFor{native("settings:github")!=null&&!activity.surface.refreshing};Thread.sleep(650)
            main{
                val buttons=tree(activity.window.decorView).filterIsInstance<Button>().filter{it.isShown&&it.tag.toString().startsWith("settings:")&&it.tag!="settings:back"}
                check(buttons.map{it.tag}.toSet()==setOf("settings:haptics","settings:github")&&buttons.size==2)
                check(kotlin.math.abs(buttons[0].width-buttons[1].width)<=1&&buttons[0].height==buttons[1].height)
                for(button in buttons){val rect=android.graphics.Rect();check(button.getGlobalVisibleRect(rect)&&rect.width()==button.width&&rect.height()==button.height)}
                check(tree(activity.window.decorView).none{it.isShown&&it.tag?.toString()?.let{tag->tag.startsWith("settings:pin")||tag=="settings:widget"}==true})
            }
            record("$language About has only two aligned, fully visible actions: haptics and GitHub; no widget promotion")
            val filter=IntentFilter(Intent.ACTION_VIEW).apply{addCategory(Intent.CATEGORY_BROWSABLE);addDataScheme("https");addDataAuthority("github.com",null);addDataPath("/dpan538/Aether",android.os.PatternMatcher.PATTERN_LITERAL)}
            val monitor=test.addMonitor(filter,Instrumentation.ActivityResult(Activity.RESULT_OK,null),true)
            try{tap("settings:github");check(monitor.hits==1);check(main{s.settingsPage=="about"&&s.playing})}finally{test.removeMonitor(monitor)}
            record("$language GitHub button dispatches the exact HTTPS repository link from native touch while playback continues")
            tap("settings:haptics");waitFor{s.settingsPage=="haptics"&&native("settings:test-haptics")!=null}
            val before=main{activity.surface.haptics.attempts};tap("settings:test-haptics");check(main{activity.surface.haptics.attempts>before&&activity.surface.haptics.last=="test"})
            tap("settings:back");waitFor{s.settingsPage=="about"&&native("settings:github")!=null}
            record("$language haptic detection invokes the real haptic API and returns to the same About page")
        }
        main{s.settingsPage="widget";s.changed()};waitFor{s.settingsPage=="about"&&native("settings:github")!=null}
        record("A retained old widget-help route closes into About instead of restoring a removed recommendation page")
        main{s.language="zh";s.showView("directory");s.changed()}
        return JSONObject().put("passed",true).put("checks",checks)
    }
}
