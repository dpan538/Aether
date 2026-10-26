package com.aether.radio

import android.app.Instrumentation
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicReference

class DeviceTimeCaptionSuite(private val test:Instrumentation,private val activity:MainActivity){
    private fun <T> main(block:()->T):T{val r=AtomicReference<Result<T>>();test.runOnMainSync{r.set(runCatching(block))};return r.get().getOrThrow()}
    fun run():JSONObject{
        val checks=JSONArray();val s=activity.session
        main{s.language="zh"}
        for(lit in listOf(false,true)){
            val result=main{
                val painter=activity.surface.painter
                val node=painter.scene(if(lit)"modern-lit"else"modern-ink").flattened().single{n->n.json.optJSONArray("text")?.let{a->(0 until a.length()).any{a.getJSONObject(it).optString("text").startsWith("LOCAL TIME")}}==true}
                val text=node.json.getJSONArray("text").getJSONObject(0);val scale=8
                val bitmap=Bitmap.createBitmap(((node.r.width()+8)*scale).toInt(),24*scale,Bitmap.Config.ARGB_8888)
                val face=painter.font(text.getBoolean("mono"),text.getInt("weight"));var glyphs=0
                val canvas=object:Canvas(bitmap){override fun drawText(value:String,x:Float,y:Float,paint:android.graphics.Paint){
                    check(paint.typeface===face&&kotlin.math.abs(paint.textSize-text.f("size"))<.0001f){"Clock caption changed its original typeface, weight or size"}
                    glyphs++;super.drawText(value,x,y,paint)
                }}
                canvas.scale(scale.toFloat(),scale.toFloat());canvas.translate(4-node.r.left,8-node.r.top);painter.drawNode(canvas,node,lit)
                check(glyphs==4)
                var left=bitmap.width;var right=-1;val occupied=BooleanArray(bitmap.width)
                for(x in 0 until bitmap.width)for(y in 0 until bitmap.height)if(Color.alpha(bitmap.getPixel(x,y))>64){left=minOf(left,x);right=maxOf(right,x);occupied[x]=true}
                val width=(right-left+1)/scale.toFloat();val end=node.r.left-4+(right+1)/scale.toFloat()
                val gaps=mutableListOf<Int>();var gap=0
                for(x in left..right){if(!occupied[x])gap++ else if(gap>0){gaps.add(gap);gap=0}}
                java.io.File(test.targetContext.getExternalFilesDir("evidence"),"device-time-caption-$lit.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
                check(width>=text.f("size")*3.5f&&gaps.sortedDescending().take(3).let{it.size==3&&it.all{g->g>=scale*.75f}}){"Crowded Chinese glyphs: width=$width gaps=$gaps"}
                check(kotlin.math.abs(end-node.r.right)<2f){"Caption is outside the header's right grid: $end vs ${node.r.right}"}
                bitmap.recycle()
                JSONObject().put("inkWidth",width).put("rightInset",node.r.right-end).put("originalTypefaceWeightAndSize",true)
            }
            checks.put(JSONObject().put("name","Chinese device-time caption has separated glyphs and follows the header right grid (front light=$lit)").put("passed",true).put("pixels",result))
        }
        return JSONObject().put("passed",true).put("checks",checks)
    }
}
