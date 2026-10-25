package com.aether.radio

import android.content.Context
import android.graphics.Typeface
import android.graphics.text.LineBreaker
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.TypedValue
import android.view.View
import android.widget.TextView

/** Natural balanced wrapping, bounded type size, and complete text for long album names. */
class AlbumTitleView(context:Context):TextView(context) {
    private val sizingPaint=TextPaint()
    private var measuredText=""
    private var measuredWidth=-1
    private var measuredFontScale=-1f
    private var measuredDensity=-1f
    private var chosenSize=64f
    init {typeface=Typeface.create("serif",Typeface.NORMAL);letterSpacing=-.025f;includeFontPadding=true;breakStrategy=LineBreaker.BREAK_STRATEGY_BALANCED}
    override fun onMeasure(widthMeasureSpec:Int,heightMeasureSpec:Int){
        val available=(View.MeasureSpec.getSize(widthMeasureSpec)-paddingLeft-paddingRight).coerceAtLeast(1)
        val metrics=resources.displayMetrics
        val fontScale=resources.configuration.fontScale
        fun px(sp:Float)=TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,sp,metrics)
        val value=text.toString()
        if(value!=measuredText||available!=measuredWidth||fontScale!=measuredFontScale||metrics.density!=measuredDensity){
            sizingPaint.set(paint)
            fun fits(sp:Int):Boolean {
                sizingPaint.textSize=px(sp.toFloat())
                return StaticLayout.Builder.obtain(text,0,text.length,sizingPaint,available).setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(true).setBreakStrategy(LineBreaker.BREAK_STRATEGY_BALANCED).build().height<=164*metrics.density
            }
            var low=34;var high=64;var best=34
            while(low<=high){val size=(low+high)/2;if(fits(size)){best=size;low=size+1}else high=size-1}
            chosenSize=best.toFloat();measuredText=value;measuredWidth=available;measuredFontScale=fontScale;measuredDensity=metrics.density
        }
        if(kotlin.math.abs(textSize-px(chosenSize))>.1f)setTextSize(TypedValue.COMPLEX_UNIT_SP,chosenSize)
        // At minimum size, unusually long titles retain their height; the left column scrolls.
        super.onMeasure(widthMeasureSpec,heightMeasureSpec)
    }
}
