package com.aether.radio

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF

/** Playback time is sampled from the player, independently of cached ink materials. */
class MainPlaybackPanel(private val state:Session,private val painter:ScenePainter){
    var previewMs:Long?=null
    private val p=Paint(Paint.ANTI_ALIAS_FLAG)
    private var retainedPosition=0L
    val position get()=previewMs?:if(state.powered)state.displayPositionMs.also{retainedPosition=it}else retainedPosition
    fun seekPosition(x:Float)=((x-PROGRESS.left)/PROGRESS.width()).coerceIn(0f,1f).times(state.localDurationMs).toLong()
    fun draw(c:Canvas,lit:Boolean){
        val root=painter.scene(painter.name(lit));val timer=root.find("local-time")?:return
        p.reset();p.isAntiAlias=true;p.typeface=painter.font(true,400);p.color=if(lit)0xff253f49.toInt()else 0xff34483a.toInt()
        val elapsed=LocalMusic.time(position);p.textSize=21f
        val maxWidth=timer.r.right-704f
        if(p.measureText(elapsed)>maxWidth)p.textSize*=maxWidth/p.measureText(elapsed)
        p.textAlign=Paint.Align.RIGHT;c.drawText(elapsed,timer.r.right,timer.r.bottom-6f,p)
        if(!state.isLocal||state.localDurationMs<=0)return
        p.strokeWidth=2f;p.strokeCap=Paint.Cap.ROUND;p.alpha=55;c.drawLine(PROGRESS.left,766f,PROGRESS.right,766f,p)
        p.alpha=255;val end=PROGRESS.left+PROGRESS.width()*(position.toDouble()/state.localDurationMs).coerceIn(0.0,1.0).toFloat()
        c.drawLine(PROGRESS.left,766f,end,766f,p);c.drawCircle(end,766f,3f,p)
        p.textSize=12.5f;p.alpha=180;c.drawText(LocalMusic.time(state.localDurationMs),PROGRESS.right,787f,p)
    }
    companion object {val PROGRESS=RectF(426.75f,748f,787.25f,789f)}
}
