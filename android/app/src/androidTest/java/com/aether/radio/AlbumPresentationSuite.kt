package com.aether.radio

import android.app.Dialog
import android.app.Instrumentation
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.ScrollView
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicReference

class AlbumPresentationSuite(private val test:Instrumentation,private val activity:MainActivity){
    private val checks=JSONArray()
    private fun <T> main(block:()->T):T{val out=AtomicReference<Result<T>>();test.runOnMainSync{out.set(runCatching(block))};return out.get().getOrThrow()}
    private val s get()=activity.session
    private fun dialog()=MainActivity::class.java.getDeclaredField("musicDialog").apply{isAccessible=true}.get(activity) as MusicLibraryDialog
    private fun record(name:String){checks.put(JSONObject().put("name",name).put("passed",true))}
    private fun waitFor(block:()->Boolean){val end=SystemClock.uptimeMillis()+30000;while(SystemClock.uptimeMillis()<end){if(main(block))return;Thread.sleep(100)};error("Album presentation condition timed out")}
    private fun tap(view:View){val p=main{val at=IntArray(2);view.getLocationOnScreen(at);at[0]+view.width*.5f to at[1]+view.height*.5f};val t=SystemClock.uptimeMillis();test.sendPointerSync(MotionEvent.obtain(t,t,MotionEvent.ACTION_DOWN,p.first,p.second,0));test.sendPointerSync(MotionEvent.obtain(t,t+80,MotionEvent.ACTION_UP,p.first,p.second,0));Thread.sleep(220)}
    private fun shot(name:String){test.uiAutomation.takeScreenshot()?.let{b->java.io.File(test.targetContext.getExternalFilesDir("evidence"),"presentation-$name-${activity.resources.configuration.fontScale}.png").outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()}}
    fun run():JSONObject{
        main{s.page=1;s.language="zh";s.selectLocal(LocalMusic.bundled(test.targetContext),0);s.changed()}
        waitFor{s.playing&&activity.surface.immersivePlayback.coverLoaded}
        val dimensions=main{
            val v=activity.surface;val width=ImmersivePlayback.DISC.width()*minOf(v.width/1704f,v.height/1136f)
            check(v.immersivePlayback.coverPixelWidth>=width){"Album artwork below native CD diameter"}
            JSONObject().put("nativeDiscDiameter",width).put("decodedCoverWidth",v.immersivePlayback.coverPixelWidth)
        }
        shot("native-disc");record("Album CD uses an independent decoded texture with at least the native display pixel diameter")
        main{val owner=MainActivity::class.java.getDeclaredField("live").apply{isAccessible=true}.get(activity) as LivePlayback;owner.restoreBundledAlbum();MainActivity::class.java.getDeclaredMethod("showMusicLibrary",LocalAlbum::class.java).apply{isAccessible=true}.invoke(activity,owner.albums().first{it.bundled})};Thread.sleep(450)
        val more=main{dialog().findViewById<View>(MusicLibraryDialog.MORE)}
        main{
            check(more is AlbumMoreButton)
            val rect=Rect();check(more.getGlobalVisibleRect(rect)&&rect.width()==more.width&&rect.height()==more.height)
            check(more.width==more.height&&more.width>=48*activity.resources.displayMetrics.density)
            val scroll=dialog().findViewById<ScrollView>(MusicLibraryDialog.TRACK_LIST)
            val a=IntArray(2);val b=IntArray(2);scroll.getLocationOnScreen(a);more.getLocationOnScreen(b)
            check(kotlin.math.abs((a[1]+scroll.height)-(b[1]+more.height))<=2){"More action still consumes track-list height"}
            val bitmap=Bitmap.createBitmap(more.width,more.height,Bitmap.Config.ARGB_8888);more.draw(Canvas(bitmap))
            val visited=BooleanArray(bitmap.width*bitmap.height);val centers=mutableListOf<Pair<Float,Float>>()
            for(y in 0 until bitmap.height)for(x in 0 until bitmap.width){
                val index=y*bitmap.width+x;if(visited[index]||Color.alpha(bitmap.getPixel(x,y))<30)continue
                val queue=java.util.ArrayDeque<Int>();queue.add(index);visited[index]=true;var sx=0;var sy=0;var count=0
                while(queue.isNotEmpty()){val i=queue.removeFirst();val px=i%bitmap.width;val py=i/bitmap.width;sx+=px;sy+=py;count++
                    for((nx,ny)in listOf(px-1 to py,px+1 to py,px to py-1,px to py+1))if(nx in 0 until bitmap.width&&ny in 0 until bitmap.height){val j=ny*bitmap.width+nx;if(!visited[j]&&Color.alpha(bitmap.getPixel(nx,ny))>=30){visited[j]=true;queue.add(j)}}
                };centers.add(sx.toFloat()/count to sy.toFloat()/count)
            }
            bitmap.recycle();check(centers.size==3&&centers.maxOf{it.second}-centers.minOf{it.second}<1f){"Dots are not horizontal"}
        }
        record("At current system font scale the three dots stay horizontal, remain fully visible and do not reduce the track-list viewport")
        shot("library");tap(more)
        val popup=main{MusicLibraryDialog::class.java.getDeclaredField("optionsDialog").apply{isAccessible=true}.get(dialog()) as Dialog};check(main{popup.isShowing});test.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);Thread.sleep(200)
        record("48 dp horizontal menu control opens the designed album options with native touch")
        val last=main{dialog().findViewById<View>(MusicLibraryDialog.TRACK_BASE+10).also{it.requestRectangleOnScreen(Rect(0,0,it.width,it.height),true)}};Thread.sleep(250);tap(last);waitFor{s.localIndex==10&&s.playing}
        shot("last-track");record("Last album track remains reachable and plays real audio while the menu stays outside the list")
        main{dialog().dismiss()}
        return JSONObject().put("passed",true).put("checks",checks).put("fontScale",activity.resources.configuration.fontScale).put("resolution",dimensions)
    }
}
