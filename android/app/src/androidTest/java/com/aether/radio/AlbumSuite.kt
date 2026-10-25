package com.aether.radio

import android.app.Instrumentation
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicReference

/** Real native popup/touch paths and real Media3 decoding of each bundled file. */
class AlbumSuite(private val test:Instrumentation,private val activity:MainActivity) {
    private val checks=JSONArray()
    private val s get()=activity.session
    private fun <T> main(block:()->T):T{val out=AtomicReference<T>();test.runOnMainSync{out.set(block())};return out.get()}
    private fun waitFor(seconds:Int=30,block:()->Boolean){val until=SystemClock.uptimeMillis()+seconds*1000;while(SystemClock.uptimeMillis()<until){if(main(block))return;Thread.sleep(100)};error("Timed out: ${s.activeId}, ${s.connection}, ${s.issue}")}
    private fun audio()=MainActivity::class.java.getDeclaredField("audio").apply{isAccessible=true}.get(activity) as AudioSession
    private fun dialog()=MainActivity::class.java.getDeclaredField("musicDialog").apply{isAccessible=true}.get(activity) as? MusicLibraryDialog
    private fun record(name:String){checks.put(JSONObject().put("name",name).put("passed",true).put("track",main{s.localTrack?.title}).put("durationMs",main{audio().player.duration}).put("samples",main{s.spectrumFrame?.samples?:0}).put("rms",main{s.spectrumFrame?.rms?:0}).put("timeMs",SystemClock.elapsedRealtime()))}
    private fun touch(x:Float,y:Float){val t=SystemClock.uptimeMillis();test.sendPointerSync(MotionEvent.obtain(t,t,MotionEvent.ACTION_DOWN,x,y,0));test.sendPointerSync(MotionEvent.obtain(t,t+70,MotionEvent.ACTION_UP,x,y,0));Thread.sleep(180)}
    private fun tapView(id:Int){
        val point=main{val v=dialog()!!.findViewById<View>(id);v.requestRectangleOnScreen(android.graphics.Rect(0,0,v.width,v.height),true);v}
        Thread.sleep(250)
        val xy=main{val r=android.graphics.Rect();check(point.getGlobalVisibleRect(r)&&r.height()>0){"Offscreen button $id"};val location=IntArray(2);point.getLocationOnScreen(location);(location[0]+point.width*.5f) to (location[1]+point.height*.5f)}
        touch(xy.first,xy.second)
    }
    private fun tapSource(index:Int){
        val v=main{val d=MainActivity::class.java.getDeclaredField("musicSourceDialog").apply{isAccessible=true}.get(activity) as android.app.AlertDialog;d.listView.getChildAt(index)}
        val xy=main{val location=IntArray(2);v.getLocationOnScreen(location);(location[0]+v.width*.5f) to (location[1]+v.height*.5f)}
        touch(xy.first,xy.second)
    }
    private fun openAlbum(){tapScene("station-selector__registration");Thread.sleep(250);tapSource(0);waitFor{dialog()?.isShowing==true}}
    private fun tapScene(action:String){
        val point=main{val v=activity.surface;val n=v.painter.scene(if(s.page==1)"immersive" else v.painter.name(s.light)).flattened().first{it.action==action||it.cls==action};val scale=minOf(v.width/1704f,v.height/1136f);val loc=IntArray(2);v.getLocationOnScreen(loc);(loc[0]+(v.width-1704*scale)/2+n.r.centerX()*scale) to (loc[1]+(v.height-1136*scale)/2+n.r.centerY()*scale)}
        touch(point.first,point.second)
    }
    private fun screenshot(name:String){Thread.sleep(400);val dir=test.targetContext.getExternalFilesDir("evidence")!!.apply{mkdirs()};test.uiAutomation.takeScreenshot()?.let{b->java.io.File(dir,"album-$name.png").outputStream().use{b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};b.recycle()}}
    private fun clickSystemText(value:String):Boolean {
        val root=test.uiAutomation.rootInActiveWindow?:return false
        fun find(n:android.view.accessibility.AccessibilityNodeInfo):android.view.accessibility.AccessibilityNodeInfo? {
            if(n.text?.toString()?.contains(value,true)==true||n.contentDescription?.toString()?.equals(value,true)==true)return n
            for(i in 0 until n.childCount){n.getChild(i)?.let{find(it)?.let{return it}}};return null
        }
        var n=find(root)?:return false
        while(!n.isClickable){n=n.parent?:return false}
        return n.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
    }
    fun runImport():JSONObject {
        main{s.page=1;s.language="en";s.changed()};Thread.sleep(350);tapScene("station-selector__registration");Thread.sleep(200);tapSource(1);Thread.sleep(200)
        val existing=main{(MainActivity::class.java.getDeclaredField("importedTracks").apply{isAccessible=true}.get(activity) as List<*>).size}
        tapSource(existing);Thread.sleep(1000);screenshot("picker")
        var clicked=false
        for(i in 0 until 12){
            if(clickSystemText("Aether-local-import")){clicked=true;break}
            if(i==0)clickSystemText("Show roots")
            if(i==2)clickSystemText("Downloads")
            Thread.sleep(400)
        }
        check(clicked){"Fixture not visible in Android document picker"}
        waitFor{s.isLocal&&s.localTrack?.id?.startsWith("document:")==true&&s.playing&&s.spectrumFrame!=null}
        check(main{activity.contentResolver.persistedUriPermissions.any{it.uri.toString()==s.localTrack?.uri&&it.isReadPermission}})
        check(main{s.localQueue.size}==1);check(main{audio().player.mediaItemCount}==1);screenshot("imported")
        record("System document picker returns an audio URI with persisted read permission and real MP3/PCM playback")
        return JSONObject().put("passed",true).put("checks",checks)
    }
    fun runRestore():JSONObject {
        check(main{s.isLocal&&s.localTrack?.id?.startsWith("document:")==true&&!s.playing&&!s.playIntent})
        val id=main{s.activeId};main{s.togglePlay()};waitFor{s.playing&&s.spectrumFrame!=null};check(main{s.activeId}==id)
        record("Cold launch restores the imported document selection without autoplay; play reopens the persisted URI")
        main{s.selectLocal(listOf(LocalTrack("invalid","Unavailable","content://com.android.providers.downloads.documents/document/aether-missing","",1000)),0)}
        waitFor{s.connection==Connection.ERROR&&!s.playing};check(main{s.issue}!=null);record("Missing document reports a real playback error without crashing or showing fake PCM")
        main{s.selectLocal(LocalMusic.bundled(activity),0)};waitFor{s.playing&&s.spectrumFrame!=null};record("Recovery from unreadable document to bundled audio succeeds")
        return JSONObject().put("passed",true).put("checks",checks)
    }
    fun runLayout():JSONObject {
        main{s.page=1;s.language="zh";s.changed()};Thread.sleep(300);openAlbum()
        main{
            fun texts(v:View):List<TextView> = if(v is TextView)listOf(v)else if(v is android.view.ViewGroup)(0 until v.childCount).flatMap{texts(v.getChildAt(it))}else emptyList()
            val visibleTexts=texts(dialog()!!.window!!.decorView)
            check(visibleTexts.none{it.text.toString().contains("幻昼")})
            check(visibleTexts.first{it.text.toString()=="由梦入真，由幻归寂。"}.textSize>=44f)
            val title=visibleTexts.first{it is AlbumTitleView}
            check(title.text.toString()=="Before the Rise"&&title.textSize<=128.1f&&title.textSize>=68f)
            for(sample in listOf("Before the Rise","Music for the Quiet Hours: A Journey Through Memory and the First Light of Morning","The Complete Recordings of the Nocturnal Garden, Volume One: Visions, Echoes and the Dissolution of Dreams")){
                val v=AlbumTitleView(activity).apply{text=sample}
                v.measure(View.MeasureSpec.makeMeasureSpec(860,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));v.layout(0,0,v.measuredWidth,v.measuredHeight)
                check(v.text.toString()==sample&&v.textSize in 68f..128.1f)
                check((0 until v.lineCount).all{v.layout.getEllipsisCount(it)==0})
            }
            fun rect(v:View)=android.graphics.Rect().also{check(v.getGlobalVisibleRect(it))}
            val artist=visibleTexts.first{it.text.toString()=="Dai Pan (潘岱)"}
            val metadata=visibleTexts.first{it.text.toString().startsWith("11 首器乐作品")}
            val phrase=visibleTexts.first{it.text.toString()=="由梦入真，由幻归寂。"}
            val button=dialog()!!.findViewById<View>(MusicLibraryDialog.ALBUM_PLAY)
            check(rect(artist).top-rect(title).bottom>=63)
            check(rect(button).top-rect(metadata).bottom>=75)
            check(rect(phrase).top-rect(button).bottom>=59)
            check(rect(phrase).height()>=phrase.height-1){"Gray phrase is clipped"}
            val root=android.graphics.Rect();dialog()!!.window!!.decorView.getGlobalVisibleRect(root)
            for(id in listOf(MusicLibraryDialog.ALBUM_PLAY,MusicLibraryDialog.NOTES,MusicLibraryDialog.CLOSE)){
                val v=dialog()!!.findViewById<View>(id);val r=android.graphics.Rect();check(v.getGlobalVisibleRect(r));check(r.height()>=v.height-1){"Clipped album action $id"}
            }
        }
        record("Independent title/credit/action/phrase spacing, adaptive complete titles and booklet-only concept heading fit the 3.4K sheet")
        screenshot("layout")
        tapView(MusicLibraryDialog.ALBUM_PLAY);waitFor{s.playing&&s.spectrumFrame!=null};record("Enlarged album play action decodes real audio")
        tapView(MusicLibraryDialog.NOTES);Thread.sleep(350);screenshot("layout-notes");test.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);Thread.sleep(200);record("Enlarged notes action opens readable booklet")
        tapView(MusicLibraryDialog.CLOSE);waitFor{dialog()?.isShowing!=true};record("Large close returns to immersive playback")
        return JSONObject().put("passed",true).put("checks",checks).put("width",activity.surface.width).put("height",activity.surface.height)
    }
    fun run():JSONObject {
        main{s.page=1;s.language="zh";s.tempo=100;s.spectrumEnabled=true;s.changed()};Thread.sleep(500)
        openAlbum();record("Physical ellipsis source menu opens monochrome album sheet");screenshot("library")
        check(main{dialog()!!.findViewById<View>(MusicLibraryDialog.CLOSE).width}>=136)
        tapView(MusicLibraryDialog.NOTES);Thread.sleep(300);screenshot("notes");test.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);Thread.sleep(200);record("Large close target and readable album booklet")
        val album=LocalMusic.bundled(test.targetContext)
        check(album.size==11);check(album.first().title=="Before the First Light"&&album.last().title=="Morning’s Sharp Truth");check(album.sumOf{it.durationMs}==1838994L);record("Exactly 11 supplied tracks, ordered night to daylight; 30:39 total")
        for(i in album.indices){
            tapView(MusicLibraryDialog.TRACK_BASE+i)
            waitFor{s.localIndex==i&&s.playing&&s.spectrumFrame?.samples?:0>0&&audio().player.currentMediaItem?.mediaId==album[i].id}
            check(main{audio().player.mediaItemCount}==11);check(main{s.decodedFormat?.contains("MP3")==true});check(kotlin.math.abs(main{audio().player.duration}-album[i].durationMs)<1000)
            record("Track ${i+1}: native selection, MP3 decoder, real duration and PCM")
        }
        tapView(MusicLibraryDialog.ALBUM_PLAY);waitFor{!s.playing};val paused=main{audio().player.currentPosition};Thread.sleep(650);check(kotlin.math.abs(main{audio().player.currentPosition}-paused)<100);tapView(MusicLibraryDialog.ALBUM_PLAY);waitFor{s.playing};record("Album pause freezes the real position and resumes")
        main{s.changeTempo(50)};check(main{audio().player.playbackParameters.speed}==.5f&&main{audio().player.playbackParameters.pitch}==1f)
        main{s.changeTempo(150)};check(main{audio().player.playbackParameters.speed}==1.5f&&main{audio().player.playbackParameters.pitch}==1f);main{s.changeTempo(100)};record("Local audio obeys 50–150% tempo with pitch 1.0")
        main{s.selectLocal(album,0)};waitFor{s.playing};main{s.seekLocal(s.localDurationMs-500)};waitFor{s.localIndex==1&&s.playing};record("Media3 automatically advances to next track without another player")
        main{s.selectLocal(album,10)};waitFor{s.playing};main{s.seekLocal(s.localDurationMs-400)};waitFor{audio().player.playbackState==androidx.media3.common.Player.STATE_ENDED&&!s.playing};check(main{!s.playIntent});tapView(MusicLibraryDialog.ALBUM_PLAY);waitFor{s.playing&&audio().player.currentPosition<10000};record("Last track ends cleanly; play restarts it")
        tapView(MusicLibraryDialog.CLOSE);tapScene("previous");waitFor{s.localIndex==9&&s.playing};record("Immersive triangle controls the local queue");screenshot("immersive")
        main{s.page=0;s.changed()};Thread.sleep(500);check(main{s.isLocal&&s.playing});screenshot("modern");record("Both pages share local metadata, audio and PCM")
        // Explicit radio selection must leave local queue intact and use the same player.
        main{s.savedStations["verification:radio"]=Station("verification:radio","SomaFM verification",stream="https://ice6.somafm.com/groovesalad-128-aac");s.select("verification:radio")}
        waitFor(50){!s.isLocal&&s.playing&&s.spectrumFrame!=null};check(main{audio().player.mediaItemCount}==1);record("Explicit radio selection returns the single player to a real HTTPS stream")
        main{s.selectLocal(album,4)};waitFor{s.playing};record("Radio to local switch keeps decoder and spectra functional")
        main{s.page=1;s.changed()};Thread.sleep(350);openAlbum()
        screenshot("selected")
        return JSONObject().put("passed",true).put("checks",checks).put("width",activity.surface.width).put("height",activity.surface.height).put("playerCount",1)
    }
}
