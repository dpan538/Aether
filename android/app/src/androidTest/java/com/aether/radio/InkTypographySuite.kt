package com.aether.radio

import android.app.Instrumentation
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicReference

class InkTypographySuite(private val test:Instrumentation,private val activity:MainActivity){
    private fun <T> main(block:()->T):T{val r=AtomicReference<Result<T>>();test.runOnMainSync{r.set(runCatching(block))};return r.get().getOrThrow()}
    private fun tree(v:View):List<View> = listOf(v)+(if(v is ViewGroup)(0 until v.childCount).flatMap{tree(v.getChildAt(it))}else emptyList())
    fun run():JSONObject{
        val checks=JSONArray();val sizes=JSONArray();val s=activity.session
        main{s.language="zh";s.page=0;s.showView("settings");s.settingsPage="menu";s.changed()};Thread.sleep(1700)
        val ink=main{tree(activity.window.decorView).filterIsInstance<InkSettingsView>().single()}
        for(page in listOf("menu","about","haptics","feedback","update")){
            main{s.updateOpen=page=="update";s.settingsPage=if(page=="update")"menu"else page;s.changed()};Thread.sleep(900)
            main{
                val scale=minOf(activity.surface.width/1704f,activity.surface.height/1136f)
                val texts=tree(ink).filterIsInstance<TextView>().filter{it.isShown}
                check(texts.isNotEmpty());check(texts.all{it.textSize/scale<=17.1f}){"Oversized ink text: "+texts.joinToString{"${it.text}: ${it.textSize/scale}"}}
                if(page=="about"){
                    val slogan=texts.single{it.text.toString()=="不设边界，漫游频率。"};check(kotlin.math.abs(slogan.textSize/scale-17)<.1)
                    val note=texts.single{it.text.toString().contains("无需账户")};val pos=IntArray(2);note.getLocationOnScreen(pos)
                    val scroll=note.parent.parent as android.widget.ScrollView;val loc=IntArray(2);scroll.getLocationOnScreen(loc)
                    check(pos[1]+note.height<=loc[1]+scroll.height){"About note clipped by action row"}
                }
                sizes.put(JSONObject().put("page",page).put("largestLogicalSize",texts.maxOf{it.textSize/scale}))
            }
            val b=test.uiAutomation.takeScreenshot()!!;java.io.File(test.targetContext.getExternalFilesDir("evidence"),"ink-type-$page.png").outputStream().use{b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};b.recycle()
            checks.put(JSONObject().put("name","$page uses the directory's logical typography, independent of density/sp scaling").put("passed",true))
        }
        main{s.updateOpen=false;s.settingsPage="menu";s.changed()};Thread.sleep(650);main{ink.openLanguageMenu()};Thread.sleep(450)
        main{val popup=ink.languageMenuView!!;val scale=minOf(activity.surface.width/1704f,activity.surface.height/1136f);check(tree(popup).filterIsInstance<TextView>().all{it.textSize/scale<=17.1f});check(s.settingsPage=="menu")}
        checks.put(JSONObject().put("name","Language remains an in-place dropdown with matching text scale").put("passed",true))
        main{ink.languageMenuView?.findViewWithTag<View>("settings:language:en")?.performClick()};Thread.sleep(600)
        main{check(s.language=="en");check(s.settingsPage=="menu");s.language="zh";s.showView("directory");s.changed()}
        return JSONObject().put("passed",true).put("checks",checks).put("sizes",sizes).put("density",activity.resources.displayMetrics.density).put("fontScale",activity.resources.configuration.fontScale)
    }
}
