package com.aether.radio

import android.app.Instrumentation
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicReference

/** Seeds the previous signed APK, then verifies the same data after a real package upgrade. */
class ReleaseUpgradeSuite(private val test:Instrumentation,private val activity:MainActivity){
    private fun <T> main(block:()->T):T{val r=AtomicReference<Result<T>>();test.runOnMainSync{r.set(runCatching(block))};return r.get().getOrThrow()}
    private fun waitFor(block:()->Boolean){val end=SystemClock.uptimeMillis()+25000;while(SystemClock.uptimeMillis()<end){if(block())return;Thread.sleep(150)};error("Upgrade verification timed out")}
    private val file get()=java.io.File(test.targetContext.getExternalFilesDir("evidence"),"release-upgrade-seed.json")
    private val hostId=10000
    fun seed():JSONObject{
        check(test.targetContext.packageManager.getPackageInfo(test.targetContext.packageName,0).longVersionCode==12L)
        val s=activity.session
        main{s.powered=true;s.locked=false;s.selectLocal(LocalMusic.bundled(test.targetContext),2)};waitFor{main{s.playing&&s.seekable}}
        main{s.seekLocal(37000)};waitFor{main{s.displayPositionMs>=36500}}
        main{s.togglePlay();s.changeVolume(17);s.changeTempo(95);s.language="zh";if(!s.activeStarred)s.toggleFavorite();s.changed();PlaybackService.current!!.live.savePosition()}
        waitFor{main{!s.playing}}
        val manager=AppWidgetManager.getInstance(test.targetContext);val host=AppWidgetHost(test.targetContext,hostId);host.deleteHost()
        val entries=JSONArray()
        for(name in listOf("MusicWidget","StripMusicWidget","CompactMusicWidget")){
            val component=ComponentName(test.targetContext.packageName,"com.aether.radio.$name");val id=host.allocateAppWidgetId()
            val options=Bundle().apply{putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,280);putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,280)}
            if(!manager.bindAppWidgetIdIfAllowed(id,component,options)){
                main{activity.startActivityForResult(Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id).putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER,component).putExtra(AppWidgetManager.EXTRA_APPWIDGET_OPTIONS,options),99)}
                waitFor{
                    fun walk(n:android.view.accessibility.AccessibilityNodeInfo):List<android.view.accessibility.AccessibilityNodeInfo> = listOf(n)+(0 until n.childCount).flatMap{n.getChild(it)?.let{walk(it)}?:emptyList()}
                    test.uiAutomation.rootInActiveWindow?.let{root->walk(root).firstOrNull{it.viewIdResourceName=="android:id/button1"&&it.isClickable}?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)}
                    manager.getAppWidgetInfo(id)?.provider==component
                }
            }
            check(manager.getAppWidgetInfo(id)?.provider==component)
            entries.put(JSONObject().put("name",name).put("id",id))
        }
        val expected=main{
            PlaybackService.current!!.live.savePosition()
            JSONObject().put("widgetIds",entries).put("trackId",s.localTrack!!.id).put("queue",JSONArray(s.localQueue.map{it.id})).put("positionMs",s.displayPositionMs).put("volume",s.volume).put("tempo",s.tempo).put("language",s.language).put("favorites",JSONArray(s.favorites)).put("stars",JSONArray(s.starredTracks))
        }
        file.writeText(expected.toString(2))
        return JSONObject().put("passed",true).put("checks",JSONArray().put(JSONObject().put("name","Previous 9.6 APK has three bound providers and durable local playback, star and settings state").put("passed",true))).put("seed",expected)
    }
    fun verify():JSONObject{
        val checks=JSONArray();fun record(name:String){checks.put(JSONObject().put("name",name).put("passed",true))}
        val expected=JSONObject(file.readText());val s=activity.session
        main{
            check(s.page==0&&s.isLocal&&s.localTrack?.id==expected.getString("trackId"))
            check(s.localQueue.map{it.id}==(0 until expected.getJSONArray("queue").length()).map{expected.getJSONArray("queue").getString(it)})
            check(kotlin.math.abs(s.displayPositionMs-expected.getLong("positionMs"))<2500)
            check(s.volume==expected.getInt("volume")&&s.tempo==expected.getInt("tempo")&&s.language==expected.getString("language"))
            fun values(key:String)=expected.getJSONArray(key).let{a->(0 until a.length()).map{a.getString(it)}.toSet()}
            check(s.favorites.toSet()==values("favorites")&&s.starredTracks==values("stars"))
        }
        record("Actual 9.6 to 1.0.0 upgrade preserves source, queue, position, favorites, track stars, volume, tempo and language; normal launch opens the device page")
        val manager=AppWidgetManager.getInstance(test.targetContext)
        val providers=manager.installedProviders.filter{it.provider.packageName==test.targetContext.packageName}
        check(providers.size==1&&providers.single().provider.className==CompactMusicWidget::class.java.name)
        record("Android exposes exactly one 2x2 cover provider after upgrade")
        val entries=expected.getJSONArray("widgetIds")
        for(i in 0 until entries.length()){
            val entry=entries.getJSONObject(i);val info=manager.getAppWidgetInfo(entry.getInt("id"))
            if(entry.getString("name")=="CompactMusicWidget")check(info?.provider==providers.single().provider)else check(info==null){"Removed provider still bound: $entry"}
        }
        record("Existing 2x2 widget keeps its ID; old 1x3 and 2x3 bindings are removed by Android")
        val retained=(0 until entries.length()).map{entries.getJSONObject(it)}.single{it.getString("name")=="CompactMusicWidget"}.getInt("id")
        val host=AppWidgetHost(test.targetContext,hostId)
        val view=main{
            host.startListening()
            host.createView(activity,retained,manager.getAppWidgetInfo(retained)).also{v->
                val d=activity.resources.displayMetrics.density
                activity.addContentView(v,android.widget.FrameLayout.LayoutParams((280*d).toInt(),(280*d).toInt(),android.view.Gravity.CENTER))
                v.updateAppWidgetSize(Bundle(),280,280,280,280)
            }
        }
        try{
            waitFor{main{view.findViewById<android.view.View>(R.id.widget_play)?.contentDescription=="播放"}}
            fun tap(){
                val point=main{val b=view.findViewById<android.view.View>(R.id.widget_play);val p=IntArray(2);b.getLocationOnScreen(p);p[0]+b.width/2f to p[1]+b.height/2f}
                val time=SystemClock.uptimeMillis();test.sendPointerSync(android.view.MotionEvent.obtain(time,time,0,point.first,point.second,0));test.sendPointerSync(android.view.MotionEvent.obtain(time,time+70,1,point.first,point.second,0))
            }
            tap();waitFor{main{s.playing}};check(main{s.page==0})
            waitFor{main{view.findViewById<android.view.View>(R.id.widget_play)?.contentDescription=="暂停"}}
            tap();waitFor{main{!s.playing}};check(main{s.page==0})
            record("The retained widget ID renders after upgrade and real touch play/pause controls the restored player without navigating into the app")
        }finally{main{(view.parent as? android.view.ViewGroup)?.removeView(view);host.stopListening()}}
        host.deleteHost()
        return JSONObject().put("passed",true).put("checks",checks)
    }
}
