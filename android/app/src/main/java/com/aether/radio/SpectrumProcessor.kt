package com.aether.radio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

@androidx.annotation.OptIn(UnstableApi::class)
class SpectrumProcessor(private val enabled:()->Boolean,private val delivered:(SpectrumFrame)->Unit):BaseAudioProcessor() {
    private var left:SpectrumAnalyzer?=null
    private var right:SpectrumAnalyzer?=null
    private var lastDelivery=0L
    private fun restart(rate:Int){left=if(rate>0)SpectrumAnalyzer(rate)else null;right=if(rate>0)SpectrumAnalyzer(rate)else null;lastDelivery=0}
    override fun onConfigure(input:AudioProcessor.AudioFormat):AudioProcessor.AudioFormat {
        restart(input.sampleRate)
        return if(input.encoding in listOf(C.ENCODING_PCM_16BIT,C.ENCODING_PCM_FLOAT))input else AudioProcessor.AudioFormat.NOT_SET
    }
    override fun onFlush(){restart(inputAudioFormat.sampleRate)}
    override fun queueInput(input:ByteBuffer){
        if(!input.hasRemaining())return
        if(enabled()){
            val data=input.duplicate().order(ByteOrder.LITTLE_ENDIAN)
            val channels=inputAudioFormat.channelCount.coerceAtLeast(1)
            val floating=inputAudioFormat.encoding==C.ENCODING_PCM_FLOAT
            val bytes=if(floating)4 else 2
            while(data.remaining()>=channels*bytes){
                var l=0.0;var r=0.0
                repeat(channels){i->val raw=if(floating)data.float.toDouble()else data.short/32768.0;val sample=if(raw.isFinite())raw.coerceIn(-1.0,1.0)else 0.0;if(i==0)l=sample;if(i==1)r=sample}
                if(channels==1)r=l
                val a=left?.accept(l);val b=right?.accept(r)
                if(a!=null&&b!=null&&a.samples-lastDelivery>=a.sampleRate/20){
                    lastDelivery=a.samples
                    // Combine spectral power, rather than cancelling opposite-phase stereo.
                    val bands=FloatArray(10){i->(10*log10((10.0.pow(a.levels[i]/10.0)+10.0.pow(b.levels[i]/10.0))/2)).toFloat()}
                    delivered(SpectrumFrame(bands,sqrt((a.rms*a.rms+b.rms*b.rms)/2),a.sampleRate,a.samples,a.levels,b.levels))
                }
            }
        }
        replaceOutputBuffer(input.remaining()).put(input).flip()
    }
}
