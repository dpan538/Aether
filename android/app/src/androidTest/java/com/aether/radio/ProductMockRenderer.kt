package com.aether.radio

import android.app.Instrumentation
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicReference

/** Export the current native product views, without system UI or screenshot resampling. */
class ProductMockRenderer(private val test:Instrumentation,private val activity:MainActivity){
    private fun <T> main(block:()->T):T{val result=AtomicReference<Result<T>>();test.runOnMainSync{result.set(runCatching(block))};return result.get().getOrThrow()}
    private fun waitFor(block:()->Boolean){val end=SystemClock.uptimeMillis()+30000;while(SystemClock.uptimeMillis()<end){if(main(block))return;Thread.sleep(100)};error("Product mock did not reach a settled native frame")}
    fun render():JSONObject{
        val state=activity.session
        main{
            check(!state.reference)
            state.powered=true;state.locked=false;state.language="zh";state.page=0
            state.query="";state.region="";state.genre="";state.stationLanguage="";state.listOffset=0;state.presetOffset=0
            state.favorites.clear();state.favorites.addAll(state.catalog.distinctBy{it.id}.take(4).map{it.id})
            state.spectrumEnabled=true;state.changeVolume(20);state.changeTempo(100)
            state.showView("directory");activity.surface.setLight(true)
            state.selectLocal(LocalMusic.bundled(test.targetContext),0)
        }
        waitFor{state.playing&&state.spectrumFrame!=null&&activity.surface.immersivePlayback.coverLoaded&&!activity.surface.refreshing}
        main{state.seekLocal(21000)}
        waitFor{state.playing&&state.displayPositionMs>=21000}
        Thread.sleep(2400)
        val files=JSONArray()
        for((page,name) in listOf(0 to "modern-mock-hd.png",1 to "immersive-mock-hd.png")){
            main{activity.surface.cancelInput();state.page=page;state.changed()}
            waitFor{activity.surface.committedPage==page&&!activity.surface.refreshing&&state.playing}
            Thread.sleep(1600)
            val bitmap=main{
                val view=activity.surface
                check(view.width==3408&&view.height==2272)
                Bitmap.createBitmap(5112,3408,Bitmap.Config.ARGB_8888).also{image->
                    val canvas=Canvas(image);canvas.scale(1.5f,1.5f);view.draw(canvas)
                }
            }
            val file=java.io.File(test.targetContext.getExternalFilesDir("evidence"),name)
            file.outputStream().use{check(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))}
            files.put(JSONObject().put("file",name).put("width",bitmap.width).put("height",bitmap.height).put("page",page))
            bitmap.recycle()
        }
        return JSONObject().put("passed",true).put("version",BuildConfig.VERSION_NAME).put("versionCode",BuildConfig.VERSION_CODE)
            .put("source","Current native view directly drawn to a high-resolution Canvas; actual decoded album audio; no system status bar or personal screenshot")
            .put("files",files)
    }
}
