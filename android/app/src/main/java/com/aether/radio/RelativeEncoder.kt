package com.aether.radio

import kotlin.math.abs
import kotlin.math.roundToInt

/** Relative travel with a small detent hysteresis; no timer or discarded motion samples. */
class RelativeEncoder(private val minimum:Int=0,private val maximum:Int=30){
    private var travel=0.0
    private var value=0
    fun begin(value:Int){this.value=value.coerceIn(minimum,maximum);travel=this.value.toDouble()}
    fun push(units:Double):Int{
        if(!units.isFinite())return value
        travel=(travel+units).coerceIn(minimum.toDouble(),maximum.toDouble())
        if(abs(travel-value)>=.62)value=travel.roundToInt().coerceIn(minimum,maximum)
        return value
    }
}
