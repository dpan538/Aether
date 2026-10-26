package com.aether.radio

import android.graphics.*
import android.util.LruCache
import kotlin.math.ceil

/** Same decoded PCM signal; static scales/grid stay in small native-resolution GPU layers. */
class SpectrumDrawing(private val face:Typeface){
    private val frequencies=listOf("31","62","125","250","500","1K","2K","4K","8K","16K")
    private val layers=object:LruCache<String,RenderNode>(6){override fun entryRemoved(evicted:Boolean,key:String,old:RenderNode,new:RenderNode?){if(old!==new)old.discardDisplayList()}}
    var directRendering=false
    private val shape=Paint(Paint.ANTI_ALIAS_FLAG)
    private val captionPaint=text(20f,0)
    private val plotCaption=text(9f,0xff35454e.toInt())
    private val traces=arrayOf(Path(),Path())
    private val strokes=arrayOf(0xff344d60.toInt(),0xb05f6b69.toInt()).map{color->Paint(Paint.ANTI_ALIAS_FLAG).apply{style=Paint.Style.STROKE;strokeWidth=1.3f;this.color=color}}
    private var traceFrame:SpectrumFrame?=null
    private var traceSize=""
    private fun text(size:Float,color:Int,align:Paint.Align=Paint.Align.LEFT)=Paint(Paint.ANTI_ALIAS_FLAG).apply{typeface=face;textSize=size;this.color=color;textAlign=align;textScaleX=1f;letterSpacing=0f}
    private fun fixed(c:Canvas,key:String,w:Float,h:Float,scaleX:Float,scaleY:Float,draw:(Canvas)->Unit){
        if(!c.isHardwareAccelerated||directRendering){draw(c);return}
        val pixelsW=ceil(w*scaleX).toInt().coerceAtLeast(1);val pixelsH=ceil(h*scaleY).toInt().coerceAtLeast(1)
        val id="$key:$pixelsW:$pixelsH"
        val cached=layers.get(id)
        val node=cached?.takeIf{it.hasDisplayList()}?:RenderNode("PCM $key").also{
            it.setPosition(0,0,pixelsW,pixelsH);it.setUseCompositingLayer(true,null);it.setClipToBounds(true)
            val target=it.beginRecording(pixelsW,pixelsH);target.scale(pixelsW/w,pixelsH/h);draw(target);it.endRecording();layers.put(id,it)
        }
        c.save();c.scale(w/pixelsW,h/pixelsH);c.drawRenderNode(node);c.restore()
    }
    fun bars(c:Canvas,r:RectF,frame:SpectrumFrame?,lit:Boolean,enabled:Boolean,caption:String,pixelScale:Float=1f){
        if(c.quickReject(r))return
        c.save();c.clipRect(r);c.translate(r.left,r.top);c.scale(r.width()/800f,r.height()/430f)
        val ink=if(lit)0xffa5eec0.toInt()else 0xff50624f.toInt()
        fixed(c,"bars:$lit",800f,430f,r.width()*pixelScale/800f,r.height()*pixelScale/430f){base->
            val label=text(20f,ink);val segment=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=if(lit)0xff16462f.toInt()else 0xff8e9b84.toInt();alpha=65}
            for(i in 0..9){val x=77f+i*65f;for(j in 0..23)base.drawRoundRect(x,340-j*12.7f,x+38,348.8f-j*12.7f,.7f,.7f,segment)
                label.textAlign=Paint.Align.CENTER;base.drawText(frequencies[i],x+19,392f,label)}
            label.textAlign=Paint.Align.LEFT;base.drawText("0",3f,63f,label);base.drawText("−36",0f,209f,label);base.drawText("−72",0f,349f,label);base.drawText("dBFS · PCM",20f,424f,label)
            label.textAlign=Paint.Align.RIGHT;base.drawText("Hz",785f,424f,label)
        }
        captionPaint.color=ink;c.drawText(caption,30f,28f,captionPaint)
        if(enabled&&frame!=null)for(i in 0..9){
            val level=(((frame.levels[i]+72)/72f)*24).toInt().coerceIn(0,24);val x=77f+i*65f
            for(j in 0 until level){shape.color=if(lit)if(j<9)0xffc4ffd2.toInt()else if(j<14)0xff7affaa.toInt()else 0xff19eb6c.toInt()else 0xff50644f.toInt();shape.alpha=255;c.drawRoundRect(x,340-j*12.7f,x+38,348.8f-j*12.7f,.7f,.7f,shape)}
        }
        c.restore()
    }
    fun plot(c:Canvas,r:RectF,frame:SpectrumFrame?,enabled:Boolean,caption:String,cn:Boolean=false,pixelScale:Float=1f){
        if(c.quickReject(r))return
        c.save();c.clipRect(r);c.translate(r.left,r.top)
        val w=r.width();val h=r.height();val top=28f;val bottom=h-23f;val graphHeight=(bottom-top).coerceAtLeast(1f)
        fixed(c,"plot:$cn",w,h,pixelScale,pixelScale){base->
            val grid=Paint(Paint.ANTI_ALIAS_FLAG).apply{strokeWidth=.45f;color=0x4243545d}
            for(i in 0..80){val x=i*w/80;base.drawLine(x,top,x,bottom,grid)}
            for(i in 0..28){val y=top+i*graphHeight/28;base.drawLine(0f,y,w,y,grid)}
            grid.color=0xff485963.toInt();grid.strokeWidth=.8f;grid.style=Paint.Style.STROKE;base.drawRect(0f,top,w,bottom,grid)
            val label=text(9f,0xff35454e.toInt());base.drawText(if(cn)"PCM 频谱 · dBFS"else"PCM SPECTRUM · dBFS",1f,11f,label)
            label.textAlign=Paint.Align.CENTER;for(i in 0..9)base.drawText(frequencies[i],i/9f*(w-18f)+9f,h-6f,label)
        }
        plotCaption.textAlign=Paint.Align.RIGHT;c.drawText(caption,w-1f,11f,plotCaption)
        if(enabled&&frame!=null){
            if(traceFrame!==frame||traceSize!="$w:$h"){
                traceFrame=frame;traceSize="$w:$h"
                arrayOf(frame.leftLevels,frame.rightLevels).forEachIndexed{index,levels->
                    val path=traces[index];path.reset()
                    var ax=1f;var ay=top+(1-((levels[0]+72)/72f).coerceIn(0f,1f))*graphHeight;path.moveTo(ax,ay)
                    for(i in 1..9){val bx=i/9f*(w-2)+1;val by=top+(1-((levels[i]+72)/72f).coerceIn(0f,1f))*graphHeight;val middle=(ax+bx)/2;path.cubicTo(middle,ay,middle,by,bx,by);ax=bx;ay=by}
                }
            }
            for(i in 0..1)c.drawPath(traces[i],strokes[i])
        }
        c.restore()
    }
    fun reset(direct:Boolean){directRendering=direct;layers.evictAll()}
    fun close(){layers.evictAll()}
}
