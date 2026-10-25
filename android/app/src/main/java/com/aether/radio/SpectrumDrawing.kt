package com.aether.radio

import android.graphics.*

/** Two views of the same decoded PCM frame. No synthetic signal or time-driven curve. */
class SpectrumDrawing(private val face:Typeface) {
    private fun text(size:Float,color:Int,align:Paint.Align=Paint.Align.LEFT)=Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface=face;textSize=size;this.color=color;textAlign=align;textScaleX=1f;letterSpacing=0f
    }
    fun bars(c:Canvas,r:RectF,frame:SpectrumFrame?,lit:Boolean,enabled:Boolean,caption:String){
        c.save();c.clipRect(r);c.translate(r.left,r.top);c.scale(r.width()/800f,r.height()/430f)
        val ink=if(lit)0xffa5eec0.toInt() else 0xff50624f.toInt()
        val label=text(20f,ink);val shape=Paint(Paint.ANTI_ALIAS_FLAG)
        c.drawText(caption,30f,28f,label)
        val frequencies=listOf("31","62","125","250","500","1K","2K","4K","8K","16K")
        for(i in 0..9){
            val level=if(enabled&&frame!=null)(((frame.levels[i]+72)/72f)*24).toInt().coerceIn(0,24)else 0
            val x=77f+i*65f
            for(j in 0..23){
                val on=j<level
                shape.color=if(on)if(lit)if(j<9)0xffc4ffd2.toInt()else if(j<14)0xff7affaa.toInt()else 0xff19eb6c.toInt()else 0xff50644f.toInt()else if(lit)0xff16462f.toInt()else 0xff8e9b84.toInt()
                shape.alpha=if(on)255 else 65;c.drawRoundRect(x,340-j*12.7f,x+38,348.8f-j*12.7f,.7f,.7f,shape)
            }
            label.textAlign=Paint.Align.CENTER;c.drawText(frequencies[i],x+19,392f,label)
        }
        label.textAlign=Paint.Align.LEFT;c.drawText("0",3f,63f,label);c.drawText("−36",0f,209f,label);c.drawText("−72",0f,349f,label);c.drawText("dBFS · PCM",20f,424f,label)
        label.textAlign=Paint.Align.RIGHT;c.drawText("Hz",785f,424f,label)
        c.restore()
    }
    fun plot(c:Canvas,r:RectF,frame:SpectrumFrame?,enabled:Boolean,caption:String,cn:Boolean=false){
        c.save();c.clipRect(r);c.translate(r.left,r.top)
        val w=r.width();val h=r.height();val top=28f;val bottom=h-23f;val graphHeight=(bottom-top).coerceAtLeast(1f)
        val grid=Paint(Paint.ANTI_ALIAS_FLAG).apply{strokeWidth=.45f;color=0x4243545d}
        for(i in 0..80){val x=i*w/80;c.drawLine(x,top,x,bottom,grid)}
        for(i in 0..28){val y=top+i*graphHeight/28;c.drawLine(0f,y,w,y,grid)}
        grid.color=0xff485963.toInt();grid.strokeWidth=.8f;c.drawRect(0f,top,w,bottom,Paint(grid).apply{style=Paint.Style.STROKE})
        val label=text(9f,0xff35454e.toInt());c.drawText(if(cn)"PCM 频谱 · dBFS"else"PCM SPECTRUM · dBFS",1f,11f,label)
        label.textAlign=Paint.Align.RIGHT;c.drawText(caption,w-1f,11f,label)
        val values=listOf("31","62","125","250","500","1K","2K","4K","8K","16K")
        label.textAlign=Paint.Align.CENTER
        for(i in 0..9)c.drawText(values[i],(i/9f*(w-18f)+9f),h-6f,label)
        if(enabled&&frame!=null){
            fun trace(levels:FloatArray,color:Int){
                val path=Path()
                val points=(0..9).map{i->PointF(i/9f*(w-2)+1,top+(1-((levels[i]+72)/72f).coerceIn(0f,1f))*graphHeight)}
                path.moveTo(points[0].x,points[0].y)
                for(i in 1..9){val a=points[i-1];val b=points[i];val middle=(a.x+b.x)/2;path.cubicTo(middle,a.y,middle,b.y,b.x,b.y)}
                c.drawPath(path,Paint(Paint.ANTI_ALIAS_FLAG).apply{style=Paint.Style.STROKE;strokeWidth=1.3f;this.color=color})
            }
            trace(frame.leftLevels,0xff344d60.toInt());trace(frame.rightLevels,0xb05f6b69.toInt())
        }
        c.restore()
    }
}
