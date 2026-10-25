package com.aether.radio

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.TextPaint
import android.text.TextUtils
import java.util.concurrent.Executors

/** Dynamic surfaces are outside the cached scene: real cover, rotation and real playback time. */
class ImmersivePlayback(private val context:Context,private val state:Session,private val invalidate:()->Unit) {
    private val worker=Executors.newSingleThreadExecutor();private val main=Handler(Looper.getMainLooper())
    private var closed=false;private var artworkKey="initial";private var artwork:Bitmap?=null
    private var coverPending=false
    val needsFallback get()=artwork==null&&!coverPending
    var rotation=0f
    var dragging=false
    var previewMs:Long?=null
    val coverLoaded get()=artwork!=null
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val text=TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val path=Path()
    private val white=Color.rgb(238,241,242)
    fun syncArtwork(){
        val key=state.localTrack?.coverUri.orEmpty()
        if(key==artworkKey)return
        artworkKey=key;artwork=null;coverPending=key.isNotBlank()
        if(key.isBlank())return
        worker.execute{val result=ArtworkStore.load(context.applicationContext,key,2048);main.post{if(!closed&&artworkKey==key){coverPending=false;artwork=result;invalidate()}else result?.recycle()}}
    }
    fun advance(seconds:Float){if(!dragging)rotation=(rotation+seconds*18f)%360f}
    fun drawDisc(c:Canvas,fallback:Bitmap?){
        syncArtwork();val bitmap=artwork?:fallback?:return
        c.save();c.rotate(rotation,DISC.centerX(),DISC.centerY())
        path.reset();path.fillType=Path.FillType.EVEN_ODD;path.addCircle(DISC.centerX(),DISC.centerY(),DISC.width()/2,Path.Direction.CW);path.addCircle(DISC.centerX(),DISC.centerY(),DISC.width()/16,Path.Direction.CW)
        c.clipPath(path)
        paint.reset();paint.isAntiAlias=true;paint.isFilterBitmap=true
        val scale=maxOf(DISC.width()/bitmap.width,DISC.height()/bitmap.height)
        val dst=RectF(DISC.centerX()-bitmap.width*scale/2,DISC.centerY()-bitmap.height*scale/2,DISC.centerX()+bitmap.width*scale/2,DISC.centerY()+bitmap.height*scale/2)
        c.drawBitmap(bitmap,null,dst,paint)
        if(artwork==null){label(c,"漫频",273.44f,797f,17f,163f);label(c,if(state.isLocal)"TRACK ${(state.localIndex+1).toString().padStart(2,'0')}"else"FM ${state.displayOrdinal?.toString()?.padStart(3,'0')?:"—"}",273.44f,821f,11f,163f)}
        c.restore()
        if(artwork!=null){paint.reset();paint.isAntiAlias=true;paint.style=Paint.Style.STROKE;paint.strokeWidth=2f;paint.color=0xff8f9a9d.toInt();c.drawCircle(DISC.centerX(),DISC.centerY(),DISC.width()/2-1,paint);paint.strokeWidth=7f;c.drawCircle(DISC.centerX(),DISC.centerY(),DISC.width()/16+4,paint)}
        previewMs?.let{ms->paint.reset();paint.color=0xdd0a0c0e.toInt();c.drawRoundRect(RectF(DISC.centerX()-92,DISC.centerY()+90,DISC.centerX()+92,DISC.centerY()+145),12f,12f,paint);label(c,LocalMusic.time(ms),DISC.centerX()-72,DISC.centerY()+126,25f,144f)}
    }
    private fun label(c:Canvas,value:String,x:Float,baseline:Float,size:Float,width:Float,color:Int=white){
        text.reset();text.isAntiAlias=true;text.isSubpixelText=true;text.color=color;text.typeface=Typeface.create("sans-serif",Typeface.NORMAL);text.textSize=size
        c.drawText(TextUtils.ellipsize(value,text,width,TextUtils.TruncateAt.END).toString(),x,baseline,text)
    }
    fun drawSelector(c:Canvas){
        val local=state.localTrack;val x=1498f;val w=147f
        if(local!=null){label(c,local.album.ifBlank{if(state.language=="zh")"设备音乐"else"Device music"},x,346f,16f,w);label(c,"${(state.localIndex+1).toString().padStart(2,'0')} / ${state.localQueue.size.toString().padStart(2,'0')}",x,387f,28f,w)}
        else{label(c,"FM",x,346f,16f,w);label(c,state.displayOrdinal?.toString()?.padStart(3,'0')?:"—",x,387f,36f,w)}
        paint.reset();paint.isAntiAlias=true;paint.strokeCap=Paint.Cap.ROUND;paint.strokeWidth=2.4f;paint.color=0x554f656f;c.drawLine(PROGRESS.left,PROGRESS.centerY(),PROGRESS.right,PROGRESS.centerY(),paint)
        val position=previewMs?:state.displayPositionMs
        if(local!=null&&state.localDurationMs>0){
            val end=PROGRESS.left+PROGRESS.width()*(position.toFloat()/state.localDurationMs).coerceIn(0f,1f);paint.color=white;c.drawLine(PROGRESS.left,PROGRESS.centerY(),end,PROGRESS.centerY(),paint)
            paint.style=Paint.Style.FILL;c.drawCircle(end,PROGRESS.centerY(),3.5f,paint)
            label(c,"${LocalMusic.time(position)} / ${LocalMusic.time(state.localDurationMs)}",x,449f,12f,w)
        }else label(c,if(state.playing)"${if(state.language=="zh")"直播"else"LIVE"}  ${LocalMusic.time(state.playbackPositionMs)}"else if(state.language=="zh")"直播 · ${if(state.connection==Connection.ERROR)"不可用"else"已暂停"}"else"LIVE · ${state.connection.name}",x,449f,12f,w)
        paint.reset();paint.isAntiAlias=true;paint.color=white;for(i in 0..2)c.drawCircle(1631f+i*6,67f,1.7f,paint)
    }
    fun close(){closed=true;worker.shutdownNow();artwork=null}
    companion object {
        val DISC=RectF(92.9219f,92.9219f,1043.0625f,1043.0625f)
        val SELECTOR=RectF(1082.75f,42.5938f,1661.4063f,471.4219f)
        val PROGRESS=RectF(1498f,401f,1645f,435f)
        val MORE=RectF(1609f,45f,1657f,89f)
    }
}
