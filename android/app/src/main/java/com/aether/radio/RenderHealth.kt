package com.aether.radio

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy

/** Checks only this app's small, transient window sample. No storage, telemetry or audio changes. */
class RenderHealth(private val view:AetherView,private val stable:()->Boolean,private val page:()->Int,private val recover:()->Unit){
    private val handler=Handler(Looper.getMainLooper())
    private var running=false
    private var generation=0
    private var failures=0
    private var pending=false
    private val tick=object:Runnable{override fun run(){if(!running)return;probe();handler.postDelayed(this,4000)}}
    fun start(){if(running)return;running=true;generation++;handler.postDelayed(tick,2500)}
    fun stop(){running=false;generation++;failures=0;handler.removeCallbacks(tick)}
    fun probe(){
        if(pending||!stable()){failures=0;return}
        val activity=view.context as? Activity?:return
        val epoch=generation;val current=page();val revision=view.contentRevision
        val position=IntArray(2);view.getLocationInWindow(position)
        val sample=Bitmap.createBitmap(170,114,Bitmap.Config.ARGB_8888)
        pending=true
        try{PixelCopy.request(activity.window,Rect(position[0],position[1],position[0]+view.width,position[1]+view.height),sample,{result->
            pending=false
            try{
                if(epoch!=generation||!running||!stable()||current!=page()||revision!=view.contentRevision)return@request
                if(result!=PixelCopy.SUCCESS){failures=0;return@request}
                val scale=minOf(view.width/1704f,view.height/1136f)
                val ox=(view.width-1704*scale)/2;val oy=(view.height-1136*scale)/2
                // These anchors are always opaque metal/paper, independent of programme, cover and PCM.
                val anchors=if(current==0)listOf(820f to 100f,1540f to 100f,820f to 420f,112f to 900f,1580f to 730f)else listOf(1144f to 600f,1650f to 600f,1144f to 1000f,1650f to 1000f)
                val bright=anchors.count{(x,y)->
                    val px=((ox+x*scale)/view.width*sample.width).toInt().coerceIn(0,sample.width-1)
                    val py=((oy+y*scale)/view.height*sample.height).toInt().coerceIn(0,sample.height-1)
                    val color=sample.getPixel(px,py);Color.red(color)+Color.green(color)+Color.blue(color)>210
                }
                if(bright==0){failures++;if(failures>=2){failures=0;recover()}else handler.postDelayed({if(running)probe()},700)}else failures=0
            }finally{sample.recycle()}
        },handler)}catch(_:IllegalArgumentException){pending=false;failures=0;sample.recycle()}
    }
}
