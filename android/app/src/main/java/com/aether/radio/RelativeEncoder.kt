package com.aether.radio

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

/** Continuous relative travel, with Schmitt thresholds on both sides of each detent. */
class RelativeEncoder(private val minimum:Int=0,private val maximum:Int=30,private val threshold:Double=.8){
    private var travel=0.0
    private var value=0
    fun begin(value:Int){this.value=value.coerceIn(minimum,maximum);travel=this.value.toDouble()}
    fun push(units:Double):Int{
        if(!units.isFinite())return value
        travel=(travel+units).coerceIn(minimum.toDouble(),maximum.toDouble())
        val distance=travel-value
        if(abs(distance)>=threshold){
            value=(if(distance>0)floor(travel+1-threshold)else ceil(travel-1+threshold)).toInt().coerceIn(minimum,maximum)
        }
        return value
    }
}
