package com.aether.radio

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import android.widget.Button

/** Geometry, rather than text shaping, keeps the three dots horizontal at every font scale. */
class AlbumMoreButton(context:Context):View(context){
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val density=resources.displayMetrics.density
    init{isClickable=true;isFocusable=true;importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_YES;minimumWidth=(48*density).toInt();minimumHeight=(48*density).toInt()}
    override fun getAccessibilityClassName():CharSequence=Button::class.java.name
    override fun onDraw(canvas:Canvas){
        super.onDraw(canvas)
        paint.color=Color.rgb(24,24,24)
        if(isPressed){paint.alpha=12;canvas.drawCircle(width/2f,height/2f,20*density,paint)}
        paint.alpha=if(isEnabled)255 else 100
        for(i in -1..1)canvas.drawCircle(width/2f+i*7*density,height/2f,2*density,paint)
    }
    override fun drawableStateChanged(){super.drawableStateChanged();invalidate()}
}
