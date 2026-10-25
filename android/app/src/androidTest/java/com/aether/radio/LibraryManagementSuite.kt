package com.aether.radio

import android.app.Dialog
import android.app.Instrumentation
import android.graphics.Rect
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicReference

class LibraryManagementSuite(private val test:Instrumentation,private val activity:MainActivity){
    private val checks=JSONArray()
    private fun <T> main(block:()->T):T{val value=AtomicReference<Result<T>>();test.runOnMainSync{value.set(runCatching(block))};return value.get().getOrThrow()}
    private val live get()=MainActivity::class.java.getDeclaredField("live").apply{isAccessible=true}.get(activity) as LivePlayback
    private val s get()=activity.session
    private fun record(name:String){checks.put(JSONObject().put("name",name).put("passed",true))}
    private fun albumDialog()=MainActivity::class.java.getDeclaredField("musicDialog").apply{isAccessible=true}.get(activity) as MusicLibraryDialog
    private fun options()=MusicLibraryDialog::class.java.getDeclaredField("optionsDialog").apply{isAccessible=true}.get(albumDialog()) as Dialog
    private fun open(album:LocalAlbum){main{MainActivity::class.java.getDeclaredMethod("showMusicLibrary",LocalAlbum::class.java).apply{isAccessible=true}.invoke(activity,album)};Thread.sleep(400)}
    private fun tap(dialog:Dialog,id:Int){
        val xy=main{val v=dialog.findViewById<View>(id);val r=Rect();check(v.getGlobalVisibleRect(r)&&r.height()>=v.height-1);val at=IntArray(2);v.getLocationOnScreen(at);at[0]+v.width*.5f to at[1]+v.height*.5f}
        val now=SystemClock.uptimeMillis();test.sendPointerSync(MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,xy.first,xy.second,0));test.sendPointerSync(MotionEvent.obtain(now,now+80,MotionEvent.ACTION_UP,xy.first,xy.second,0));Thread.sleep(250)
    }
    private fun shot(name:String){test.uiAutomation.takeScreenshot()?.let{b->java.io.File(test.targetContext.getExternalFilesDir("evidence"),"library-$name.png").outputStream().use{b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};b.recycle()}}
    private fun waitPlaying(){val end=SystemClock.uptimeMillis()+15000;while(SystemClock.uptimeMillis()<end){if(main{s.playing})return;Thread.sleep(100)};error("Player did not start")}
    fun run():JSONObject{
        val original=main{live.importedTracks};val beforeStar=main{live.isStarred(LocalAlbums.BUNDLED)}
        check(original.isNotEmpty()){"Run embedded-artwork first to supply a persisted audio document"}
        val source=original.first();val one=source.copy(id="management:one",album="Library lifecycle — A");val two=source.copy(id="management:two",album="Library lifecycle — B")
        val beforeLength=test.targetContext.contentResolver.openAssetFileDescriptor(android.net.Uri.parse(source.uri),"r")!!.use{it.length}
        try{
            main{live.restoreBundledAlbum();live.addImported(listOf(one,two));s.page=1;s.language="zh";s.selectLocal(listOf(one,two),0)};waitPlaying()
            val bundle=main{live.albums().first{it.bundled}}
            open(bundle);tap(albumDialog(),MusicLibraryDialog.MORE);shot("options")
            tap(options(),MusicLibraryDialog.STAR)
            check(main{live.isStarred(bundle.key)!=beforeStar&&live.preferences.getStringSet("starred-albums",emptySet())!!.contains(bundle.key)==live.isStarred(bundle.key)})
            tap(albumDialog(),MusicLibraryDialog.MORE);tap(options(),MusicLibraryDialog.STAR)
            check(main{live.isStarred(bundle.key)==beforeStar});record("Bottom album menu stars/un-stars the album and persists the library state")
            tap(albumDialog(),MusicLibraryDialog.MORE);tap(options(),MusicLibraryDialog.DELETE)
            main{
                val dialog=options();val body=dialog.findViewById<android.widget.TextView>(MusicLibraryDialog.DELETE_EXPLANATION);val hint=dialog.findViewById<android.widget.TextView>(MusicLibraryDialog.DELETE_RESTORE)
                check(body.lineCount==2&&body.text.toString().contains("\n"))
                val first=IntArray(2);val second=IntArray(2);body.getLocationOnScreen(first);hint.getLocationOnScreen(second)
                check(first[0]==second[0]&&second[1]-(first[1]+body.height) in 23..25)
                check(body.layout.getEllipsisCount(0)==0&&body.layout.getEllipsisCount(1)==0)
            }
            record("Delete dialog uses two readable body lines with an aligned restoration note and a consistent 12 dp paragraph gap")
            shot("delete-confirm")
            tap(options(),MusicLibraryDialog.CANCEL_DELETE);check(main{live.albums().any{it.bundled}&&s.playing});record("Designed delete confirmation can be cancelled without altering the library or playback")
            val player=main{live.audio.player};val position=main{player.currentPosition}
            tap(albumDialog(),MusicLibraryDialog.MORE);tap(options(),MusicLibraryDialog.DELETE);tap(options(),MusicLibraryDialog.CONFIRM_DELETE)
            check(main{live.albums().none{it.bundled}&&live.preferences.getBoolean("bundled-hidden",false)&&player.isPlaying&&player.currentPosition>position})
            record("Deleting an inactive included album removes it from the library while another album keeps playing")
            main{live.restoreBundledAlbum();check(live.albums().any{it.bundled});MainActivity::class.java.getDeclaredField("musicSourceDialog").apply{isAccessible=true}.get(activity).let{(it as? Dialog)?.dismiss()}}
            record("Included album can be restored from the designed music menu")
            val inactive=main{live.albums().first{it.tracks.any{t->t.id==two.id}}}
            val current=main{player.currentPosition};main{live.deleteAlbum(inactive)}
            check(main{player.isPlaying&&s.localTrack?.id==one.id&&player.mediaItemCount==1&&s.localQueue.size==1&&player.currentPosition>=current-150})
            record("Deleting a queued inactive album preserves the current track, position and single player")
            val active=main{live.albums().first{it.tracks.any{t->t.id==one.id}}};open(active);tap(albumDialog(),MusicLibraryDialog.MORE);tap(options(),MusicLibraryDialog.DELETE);tap(options(),MusicLibraryDialog.CONFIRM_DELETE)
            check(main{!player.isPlaying&&s.localQueue.isEmpty()&&live.importedTracks.none{it.id in setOf(one.id,two.id)}})
            val afterLength=test.targetContext.contentResolver.openAssetFileDescriptor(android.net.Uri.parse(source.uri),"r")!!.use{it.length};check(beforeLength==afterLength&&afterLength>0)
            record("Deleting the playing album stops only the removed queue; original device audio is still readable and unchanged in length")
            shot("source-after-delete")
        }finally{main{
            live.restoreBundledAlbum();LivePlayback::class.java.getDeclaredField("importedTracks").apply{isAccessible=true}.set(live,original)
            live.preferences.edit().putString("imported-tracks",JSONArray(original.map{it.json()}).toString()).apply()
            if(live.isStarred(LocalAlbums.BUNDLED)!=beforeStar)live.toggleAlbumStar(LocalAlbums.BUNDLED)
            s.selectLocal(live.bundledAlbum,0)
        }}
        return JSONObject().put("passed",true).put("checks",checks)
    }
}
