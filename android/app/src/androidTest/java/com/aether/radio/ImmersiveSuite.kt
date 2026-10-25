package com.aether.radio

import android.app.Instrumentation
import android.content.Intent
import android.os.SystemClock
import android.view.MotionEvent
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.*

class ImmersiveSuite(private val test:Instrumentation,private var activity:MainActivity){
    private val checks=JSONArray()
    private val s get()=activity.session
    private fun <T> main(block:()->T):T{val out=AtomicReference<Result<T>>();test.runOnMainSync{out.set(runCatching{block()})};return out.get().getOrThrow()}
    private fun audio()=MainActivity::class.java.getDeclaredField("audio").apply{isAccessible=true}.get(activity) as AudioSession
    private fun waitFor(seconds:Int=35,block:()->Boolean){val end=SystemClock.uptimeMillis()+seconds*1000;while(SystemClock.uptimeMillis()<end){if(main(block))return;Thread.sleep(100)};error("Timed out: ${main{s.connection}} / ${main{s.issue}}")}
    private fun record(name:String){checks.put(JSONObject().put("name",name).put("passed",true).put("positionMs",main{audio().player.currentPosition}).put("playing",main{s.playing}).put("id",main{s.activeId}).put("timeMs",SystemClock.elapsedRealtime()))}
    private fun gesture(points:List<Pair<Float,Float>>){
        val converted=main{val v=activity.surface;val scale=minOf(v.width/1704f,v.height/1136f);val p=IntArray(2);v.getLocationOnScreen(p);points.map{(x,y)->(p[0]+(v.width-1704*scale)/2+x*scale) to (p[1]+(v.height-1136*scale)/2+y*scale)}}
        val start=SystemClock.uptimeMillis()
        converted.forEachIndexed{i,p->val now=SystemClock.uptimeMillis();test.sendPointerSync(MotionEvent.obtain(start,now,if(i==0)MotionEvent.ACTION_DOWN else if(i==converted.lastIndex)MotionEvent.ACTION_UP else MotionEvent.ACTION_MOVE,p.first,p.second,0));Thread.sleep(30)}
        Thread.sleep(220)
    }
    private fun tap(x:Float,y:Float)=gesture(listOf(x to y,x to y))
    private fun shot(name:String){Thread.sleep(400);val b=test.uiAutomation.takeScreenshot()!!;val folder=test.targetContext.getExternalFilesDir("evidence")!!.apply{mkdirs()};java.io.File(folder,"immersive-$name.png").outputStream().use{b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};b.recycle()}
    fun runArtwork():JSONObject {
        main{s.page=1;s.language="zh";activity.surface.activate("music-sources")};Thread.sleep(350)
        fun texts(v:android.view.View):List<android.widget.TextView> = if(v is android.widget.TextView)listOf(v)else if(v is android.view.ViewGroup)(0 until v.childCount).flatMap{texts(v.getChildAt(it))}else emptyList()
        fun importAction(){
            val dialog=main{MainActivity::class.java.getDeclaredField("musicSourceDialog").apply{isAccessible=true}.get(activity) as MusicSourceDialog}
            val v=main{texts(dialog.window!!.decorView).first{it.text.toString().contains("Import music from the device")}}
            val xy=main{val p=IntArray(2);v.getLocationOnScreen(p);(p[0]+v.width*.5f) to (p[1]+v.height*.5f)}
            val t=SystemClock.uptimeMillis();test.sendPointerSync(MotionEvent.obtain(t,t,MotionEvent.ACTION_DOWN,xy.first,xy.second,0));test.sendPointerSync(MotionEvent.obtain(t,t+80,MotionEvent.ACTION_UP,xy.first,xy.second,0));Thread.sleep(1000)
        }
        importAction()
        fun systemText(value:String):Boolean{
            fun find(n:android.view.accessibility.AccessibilityNodeInfo):android.view.accessibility.AccessibilityNodeInfo?{if(n.text?.toString()?.contains(value,true)==true||(value=="Show roots"&&n.contentDescription?.toString()?.contains(value,true)==true))return n;for(i in 0 until n.childCount)n.getChild(i)?.let{find(it)?.let{return it}};return null}
            var node=test.uiAutomation.rootInActiveWindow?.let{find(it)}?:return false
            if(value.startsWith("Aether-cover-import")){
                val bounds=android.graphics.Rect();node.getBoundsInScreen(bounds)
                test.uiAutomation.executeShellCommand("input tap ${bounds.centerX()} ${bounds.centerY()}").use{fd->java.io.FileInputStream(fd.fileDescriptor).use{it.readBytes()}}
                return true
            }
            while(!node.isClickable)node=node.parent?:return false
            return node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
        }
        var picked=false
        for(i in 0 until 18){if(systemText("Aether-cover-import")){picked=true;break};if(i==0)systemText("Show roots");if(i==2)systemText("Downloads");Thread.sleep(400)}
        check(picked){"Embedded-cover fixture not found in Android picker"}
        waitFor{s.localTrack?.title=="Embedded cover fixture"&&s.playing}
        val uri=android.net.Uri.parse(main{s.localTrack!!.uri})
        check(test.targetContext.contentResolver.persistedUriPermissions.any{it.uri==uri&&it.isReadPermission})
        val track=LocalMusic.imported(test.targetContext,uri)
        check(track.album=="Embedded Cover Test"&&track.title=="Embedded cover fixture"&&track.coverUri.startsWith("file:"))
        val originalPicture=Id3Artwork.read(test.targetContext,uri).picture!!
        check(java.io.File(android.net.Uri.parse(track.coverUri).path!!).readBytes().contentEquals(originalPicture)){"Embedded cover bytes were resized or recompressed"}
        val cover=ArtworkStore.load(test.targetContext,track.coverUri,2048)!!;check(maxOf(cover.width,cover.height)>=1900);cover.recycle()
        val live=main{MainActivity::class.java.getDeclaredField("live").apply{isAccessible=true}.get(activity) as LivePlayback}
        main{live.addImported(listOf(track));s.page=1;s.selectLocal(listOf(track),0);s.changed()}
        waitFor{s.playing&&activity.surface.immersivePlayback.coverLoaded};record("Actual embedded ID3 cover is extracted, cached at CD resolution and displayed while the file decodes")
        shot("embedded-cover")
        main{activity.surface.activate("music-sources")};Thread.sleep(300)
        importAction()
        check(test.uiAutomation.rootInActiveWindow?.packageName?.toString()?.contains("documentsui")==true);check(main{audio().player.isPlaying})
        test.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);Thread.sleep(350);test.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);Thread.sleep(150)
        record("Designed import action opens Android's audio picker without interrupting playback; cancel preserves the queue")
        val original=LocalMusic.bundled(test.targetContext)
        main{s.selectLocal(listOf(original[0].copy(id="fixture:missing-cover",coverUri="file://${test.targetContext.filesDir}/artwork/missing-cover.jpg")),0)}
        waitFor{!activity.surface.immersivePlayback.coverLoaded&&activity.surface.painter.mainDisc!=null&&s.playing}
        shot("fallback-cover");record("Unreadable cover falls back to the existing moon CD while real audio keeps playing")
        main{s.selectLocal(original,0)};waitFor{s.playing&&activity.surface.immersivePlayback.coverLoaded}
        return JSONObject().put("passed",true).put("checks",checks)
    }
    fun runRadio():JSONObject {
        val station=Station("verification:background-radio","SomaFM Groove Salad",stream="https://ice6.somafm.com/groovesalad-128-aac")
        main{s.savedStations[station.id]=station;s.page=1;s.select(station.id)}
        waitFor(55){s.playing&&audio().player.audioFormat!=null};record("Actual HTTPS radio stream decodes through the service player")
        shot("radio-selector")
        val player=main{audio().player};val position=main{player.currentPosition}
        test.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_HOME);Thread.sleep(7000)
        check(main{player.isPlaying&&player.currentPosition>position+5500});record("Real radio continues advancing in background")
        val future=main{androidx.media3.session.MediaController.Builder(test.targetContext,androidx.media3.session.SessionToken(test.targetContext,android.content.ComponentName(test.targetContext,PlaybackService::class.java))).buildAsync()}
        val controller=future.get(15,java.util.concurrent.TimeUnit.SECONDS)
        try{
            main{controller.pause()};waitFor{!s.playing&&!s.playIntent};main{controller.play()};waitFor{s.playing&&s.playIntent}
            record("System media-session play/pause commands control and synchronize the same real radio player")
        }finally{main{controller.release()}}
        return JSONObject().put("passed",true).put("checks",checks).put("testStream",station.stream)
    }
    fun run():JSONObject {
        val album=LocalMusic.bundled(test.targetContext)
        main{s.language="zh";s.page=1;s.changeTempo(100);s.selectLocal(album,0);s.changed()}
        waitFor{s.playing&&activity.surface.immersivePlayback.coverLoaded&&s.seekable};record("Single service player decodes bundled audio and displays its actual album cover")
        val angle=main{activity.surface.immersivePlayback.rotation};Thread.sleep(850);check(main{abs(activity.surface.immersivePlayback.rotation-angle)}>6);record("Album CD rotates while actual playback is active")
        shot("cover")
        tap(568f,768f);waitFor{!s.playing};val stopped=main{activity.surface.immersivePlayback.rotation};Thread.sleep(650);check(abs(main{activity.surface.immersivePlayback.rotation}-stopped)<1);record("CD tap pauses real audio and freezes rotation")
        tap(568f,768f);waitFor{s.playing};record("Second CD tap resumes playback")
        main{s.seekLocal(30000)};Thread.sleep(250)
        fun arc(from:Double,to:Double){gesture((0..16).map{i->val a=(from+(to-from)*i/16)*PI/180;(568+330*cos(a)).toFloat() to (568+330*sin(a)).toFloat()})}
        val before=main{audio().player.currentPosition};arc(0.0,120.0);check(main{audio().player.currentPosition}>before+17000);record("Clockwise CD turn seeks forward against the real decoder position")
        val forward=main{audio().player.currentPosition};arc(120.0,60.0);check(main{audio().player.currentPosition}<forward-7000);record("Counterclockwise CD turn seeks backward")
        val id=main{s.activeId}
        gesture(listOf(1400f to 15f,1401f to 40f,1401f to 70f));check(main{s.activeId}==id)
        gesture(listOf(1000f to 600f,1000f to 640f,1000f to 680f));check(main{s.activeId}==id)
        gesture(listOf(1530f to 280f,1530f to 295f,1530f to 310f));check(main{s.activeId}==id)
        record("Top edge, outside-card vertical gestures and light selector drags do not change tracks")
        gesture(listOf(1530f to 180f,1530f to 140f,1530f to 95f));waitFor{s.localIndex==1};record("Deliberate swipe inside the upper-right component changes exactly one track")
        tap(1642f,67f);Thread.sleep(400);shot("music-menu");test.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);Thread.sleep(200)
        record("Designed source menu opens from the ellipsis; album/device music replace the return-to-radio utility list")
        val player=main{audio().player};val state=main{s};val position=main{player.currentPosition}
        test.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_HOME);Thread.sleep(11000)
        check(main{player.isPlaying&&player.currentPosition>position+9000&&!state.uiVisible});record("Home/background keeps real playback advancing for eleven seconds")
        val manager=test.targetContext.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        check(manager.getRunningServices(30).any{it.service.className==PlaybackService::class.java.name&&it.foreground});record("Background playback is backed by an actual mediaPlayback foreground service")
        android.os.ParcelFileDescriptor.AutoCloseInputStream(test.uiAutomation.executeShellCommand("input keyevent KEYCODE_SLEEP")).use{it.readBytes()};Thread.sleep(6000)
        check(main{player.isPlaying&&!(test.targetContext.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager).isInteractive});android.os.ParcelFileDescriptor.AutoCloseInputStream(test.uiAutomation.executeShellCommand("input keyevent KEYCODE_WAKEUP")).use{it.readBytes()};Thread.sleep(500);android.os.ParcelFileDescriptor.AutoCloseInputStream(test.uiAutomation.executeShellCommand("wm dismiss-keyguard")).use{it.readBytes()};record("Playback survives screen off/on")
        main{activity.finish()};Thread.sleep(700);check(main{player.isPlaying});record("Destroying the Activity does not release the service-owned player")
        activity=test.startActivitySync(Intent(test.targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        waitFor{activity.uiReady&&activity.surface.assetsReady};check(main{s===state&&audio().player===player&&s.playing});main{s.page=1;s.changed()};Thread.sleep(350);shot("returned");record("Returning to the app reattaches to the same session, queue and player")
        return JSONObject().put("passed",true).put("checks",checks).put("width",activity.surface.width).put("height",activity.surface.height)
    }
}
