package com.aether.radio

import kotlin.math.*

data class SpectrumFrame(val levels:FloatArray,val rms:Float,val sampleRate:Int,val samples:Long)

/** Hann-windowed FFT of decoded PCM, in dBFS. No synthesized input or microphone. */
class SpectrumAnalyzer(private val rate:Int,private val size:Int=2048) {
    private val samples=DoubleArray(size)
    private var cursor=0
    private var received=0L
    private val centers=doubleArrayOf(31.25,62.5,125.0,250.0,500.0,1000.0,2000.0,4000.0,8000.0,16000.0)
    fun accept(sample:Double):SpectrumFrame? {
        samples[cursor++]=sample;received++
        if(cursor<size)return null
        cursor=0
        val real=DoubleArray(size){samples[it]*(.5-.5*cos(2*PI*it/(size-1)))}
        val imag=DoubleArray(size)
        var j=0
        for(i in 1 until size){var bit=size shr 1;while(j and bit!=0){j=j xor bit;bit=bit shr 1};j=j xor bit;if(i<j){val t=real[i];real[i]=real[j];real[j]=t}}
        var length=2
        while(length<=size){
            val angle=-2*PI/length;val wr=cos(angle);val wi=sin(angle)
            for(start in 0 until size step length){var ur=1.0;var ui=0.0
                for(k in 0 until length/2){
                    val a=start+k;val b=a+length/2
                    val tr=real[b]*ur-imag[b]*ui;val ti=real[b]*ui+imag[b]*ur
                    real[b]=real[a]-tr;imag[b]=imag[a]-ti;real[a]+=tr;imag[a]+=ti
                    val next=ur*wr-ui*wi;ui=ur*wi+ui*wr;ur=next
                }
            };length=length shl 1
        }
        val levels=FloatArray(10){band->
            val low=max(1,(centers[band]/sqrt(2.0)*size/rate).toInt())
            val high=min(size/2,ceil(centers[band]*sqrt(2.0)*size/rate).toInt())
            var peak=0.0
            for(bin in low..high)peak=max(peak,hypot(real[bin],imag[bin])*4/size)
            (20*log10(max(peak,1e-5))).coerceIn(-90.0,0.0).toFloat()
        }
        return SpectrumFrame(levels,sqrt(samples.sumOf{it*it}/size).toFloat(),rate,received)
    }
}
