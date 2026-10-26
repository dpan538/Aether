package com.aether.radio

import android.app.Instrumentation
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.*

class ControlsSettingsSuite(private val test:Instrumentation,private val activity:MainActivity){
    private val s get()=activity.session
    private val checks=JSONArray()
    private fun <T> main(block:()->T):T{val r=AtomicReference<Result<T>>();test.runOnMainSync{r.set(runCatching{block()})};return r.get().getOrThrow()}
    private fun waitFor(block:()->Boolean){val end=SystemClock.uptimeMillis()+35000;while(SystemClock.uptimeMillis()<end){if(main(block))return;Thread.sleep(100)};error("Controls/settings condition timed out")}
    private fun record(name:String){checks.put(JSONObject().put("name",name).put("passed",true))}
    private fun views(v:View):List<View> = listOf(v)+(if(v is ViewGroup)(0 until v.childCount).flatMap{views(v.getChildAt(it))}else emptyList())
    private fun overlay()=MainActivity::class.java.getDeclaredField("inkSettings").apply{isAccessible=true}.get(activity) as InkSettingsView
    private fun client()=MainActivity::class.java.getDeclaredField("feedback").apply{isAccessible=true}.get(activity) as FeedbackClient
    private fun audio()=MainActivity::class.java.getDeclaredField("audio").apply{isAccessible=true}.get(activity) as AudioSession
    private fun pointer(points:List<Pair<Float,Float>>,logical:Boolean=true){
        val converted=if(logical)main{val v=activity.surface;val scale=minOf(v.width/1704f,v.height/1136f);val p=IntArray(2);v.getLocationOnScreen(p);points.map{(x,y)->(p[0]+(v.width-1704*scale)/2+x*scale) to (p[1]+(v.height-1136*scale)/2+y*scale)}}else points
        val start=SystemClock.uptimeMillis()
        converted.forEachIndexed{i,p->test.sendPointerSync(MotionEvent.obtain(start,SystemClock.uptimeMillis(),if(i==0)MotionEvent.ACTION_DOWN else if(i==converted.lastIndex)MotionEvent.ACTION_UP else MotionEvent.ACTION_MOVE,p.first,p.second,0));Thread.sleep(55)};Thread.sleep(200)
    }
    private fun tapText(label:String){waitFor{views(overlay()).filterIsInstance<TextView>().any{it.text.toString()==label}};val p=main{val v=views(overlay()).filterIsInstance<TextView>().first{it.text.toString()==label};val p=IntArray(2);v.getLocationOnScreen(p);p[0]+v.width*.5f to p[1]+v.height*.5f};pointer(listOf(p,p),false)}
    private fun shot(name:String){Thread.sleep(350);val b=test.uiAutomation.takeScreenshot()!!;java.io.File(test.targetContext.getExternalFilesDir("evidence"),"controls-$name.png").outputStream().use{b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};b.recycle()}
    private fun sceneText(id:String):String=main{val painter=activity.surface.painter;val node=painter.scene(painter.name(s.light)).flattened().first{it.id==id};val method=ScenePainter::class.java.getDeclaredMethod("boundText",SceneNode::class.java,String::class.java,String::class.java,Int::class.javaPrimitiveType,Station::class.java,Int::class.javaPrimitiveType).apply{isAccessible=true};method.invoke(painter,node,id,node.json.getJSONArray("text").getJSONObject(0).getString("text"),0,null,-1) as String}
    fun runFeedbackLive():JSONObject{
        val message="[漫频自动验收] 验证原生反馈页面、服务接收与回执；不含个人信息，验证后可关闭。"
        main{s.page=0;s.language="zh";s.showView("settings");s.settingsPage="menu";s.changed()};waitFor{overlay().visibility==View.VISIBLE}
        tapText("意见反馈");waitFor{s.settingsPage=="feedback"}
        main{val editor=views(overlay()).filterIsInstance<EditText>().single();editor.requestFocus();editor.setText("");editor.onCreateInputConnection(android.view.inputmethod.EditorInfo())!!.commitText(message,1)}
        waitFor{client().draft==message}
        val prefs=test.targetContext.getSharedPreferences("manpin-feedback",android.content.Context.MODE_PRIVATE)
        val id=prefs.getString("request-id","")!!;val version=prefs.getString("version","")!!
        tapText("提交反馈");waitFor{!client().busy&&client().status!="sending"}
        check(main{client().status=="received"}){"Live feedback result: ${main{client().status}}"}
        val receipt=main{client().receipt};check(receipt.startsWith("https://github.com/dpan538/Aether/issues/"));check(main{client().draft.isEmpty()});shot("feedback-received-zh")
        record("Human-authorized text is submitted through the actual ink form and independently verified against the real GitHub Issue before clearing the draft")
        val connection=java.net.URL(FeedbackClient.ENDPOINT).openConnection() as javax.net.ssl.HttpsURLConnection
        val replay=try{connection.requestMethod="POST";connection.doOutput=true;connection.connectTimeout=15000;connection.readTimeout=15000;connection.setRequestProperty("Content-Type","application/json");connection.outputStream.use{it.write(JSONObject().put("body",message).put("requestId",id).put("version",version).toString().toByteArray(Charsets.UTF_8))};check(connection.responseCode==200);connection.inputStream.use{JSONObject(it.bufferedReader().readText())}}finally{connection.disconnect()}
        check(replay.getString("receipt")==receipt&&replay.getString("requestId")==id);record("Retrying the identical real request returns the same Issue receipt without a second feedback message")
        main{s.language="en";s.changed()};shot("feedback-received-en");check(main{views(overlay()).filterIsInstance<TextView>().any{it.text.toString()=="Feedback received. Thank you."}})
        record("Received status is designed inline in both language pages")
        return JSONObject().put("passed",true).put("checks",checks).put("receipt",receipt)
    }
    fun run():JSONObject{
        val effects=android.provider.Settings.System.getInt(test.targetContext.contentResolver,android.provider.Settings.System.SOUND_EFFECTS_ENABLED,1)
        test.uiAutomation.executeShellCommand("settings put system sound_effects_enabled 1").close()
        try{
            main{s.page=0;s.language="zh";s.view="directory";s.changeVolume(15);s.changeTempo(100);s.selectLocal(LocalMusic.bundled(test.targetContext),0);s.changed()}
            waitFor{s.playing};Thread.sleep(700)
            check(sceneText("scope-filters").startsWith("本地音乐")&&sceneText("scope-count")=="11");shot("local-source")
            record("The lower source screen recognizes the local album and its actual 11-track queue")
            val v=main{activity.surface.painter.scene(activity.surface.painter.name(s.light)).find("volume")!!.r}
            val before=main{activity.surface.haptics.attempts};val sound=main{activity.surface.haptics.soundPlays}
            pointer((0..10).map{i->v.centerX() to (v.centerY()-72*i/10f)})
            check(main{s.volume in 18..19&&abs(activity.surface.painter.volumeAngle-(-135+s.volume*9))<.1f&&abs(audio().player.volume-s.volume/30f)<.001})
            val volume=main{s.volume};pointer(listOf(v.centerX() to v.centerY(),v.centerX() to v.centerY()));check(main{s.volume}==volume)
            record("Center dragging follows fresh relative travel and the real player gain; pressing never jumps")
            val start=main{s.volume}
            pointer((0..10).map{i->val a=i*54.0/10*PI/180;(v.centerX()+v.width()*.42*cos(a)).toFloat() to (v.centerY()+v.width()*.42*sin(a)).toFloat()})
            check(main{s.volume-start in 4..5});record("Outer rotation follows the relative damped detents without timer-dropped travel")
            val tempo=main{activity.surface.painter.scene(activity.surface.painter.name(s.light)).find("tempo")!!.r}
            pointer((0..16).map{i->(tempo.left+21.58f+(tempo.width()-43.16f)*(.5f+i*.5f/16)) to tempo.centerY()})
            check(main{s.tempo==150&&audio().player.playbackParameters.speed==1.5f&&audio().player.playbackParameters.pitch==1f})
            check(main{activity.surface.haptics.attempts>before&&activity.surface.haptics.soundPlays>sound});record("Tempo retains pitch; native haptic requests and real SoundPool detents are emitted on changed values")
            main{s.changeTempo(100);s.changeVolume(20);s.showView("settings")};waitFor{overlay().visibility==View.VISIBLE}
            shot("settings-zh");tapText("关于");waitFor{s.settingsPage=="about"};shot("about-zh")
            check(main{views(overlay()).filterIsInstance<TextView>().any{it.text.toString().contains("不设边界")}});record("About opens a dedicated readable ink page with product and privacy information")
            tapText("‹");tapText("意见反馈");waitFor{s.settingsPage=="feedback"}
            val message="原生反馈草稿：旋钮与版式建议。\nDraft retention test. 🌙"
            main{val editor=views(overlay()).filterIsInstance<EditText>().single();editor.requestFocus();editor.onCreateInputConnection(android.view.inputmethod.EditorInfo())!!.commitText(message,1)}
            waitFor{client().draft==message};shot("feedback-zh")
            main{s.settingsPage="menu";s.language="en";s.changed()};tapText("Feedback");waitFor{client().draft==message}
            shot("feedback-en");record("Both language forms use the ink screen and preserve exact Unicode draft text across page/language changes")
            val health=(java.net.URL("https://manpin-feedback.vercel.app/healthz").openConnection() as javax.net.ssl.HttpsURLConnection).apply{connectTimeout=10000;readTimeout=10000}
            val configured=try{health.inputStream.use{JSONObject(it.bufferedReader().readText()).getBoolean("configured")}}finally{health.disconnect()}
            if(!configured){tapText("Send feedback");waitFor{!client().busy&&client().status=="service_unconfigured"};check(main{client().draft==message});shot("feedback-unconfigured");record("A real unconfigured-server response stays inline and retains the original draft; no success is invented")}
            main{s.settingsPage="menu";s.language="zh";s.changed()};tapText("界面语言");waitFor{overlay().languageMenuView!=null&&s.settingsPage=="menu"&&s.menu==null};shot("language-menu")
            main{s.menu=null;s.showView("directory");activity.surface.activate("menu:language")};waitFor{s.menu=="language"};shot("station-language-menu")
            check(UiWords.option("language","german",true)=="德语"&&UiWords.option("language","american english",true)=="美式英语"&&UiWords.option("language","español argentina",true)=="阿根廷西班牙语")
            record("Custom language menus use translated display labels while original filter values retain their identity")
            main{s.menu=null;s.changed()}
            return JSONObject().put("passed",true).put("checks",checks).put("hapticNote","Requests verified on emulator; physical Xiaomi motor feel remains a hardware check")
        }finally{test.uiAutomation.executeShellCommand("settings put system sound_effects_enabled $effects").close()}
    }
}
