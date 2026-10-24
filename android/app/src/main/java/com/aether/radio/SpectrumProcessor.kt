package com.aether.radio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder

@androidx.annotation.OptIn(UnstableApi::class)
class SpectrumProcessor(private val enabled:()->Boolean,private val delivered:(SpectrumFrame)->Unit):BaseAudioProcessor() {
    private var analyzer:SpectrumAnalyzer?=null
    private var lastDelivery=0L
    override fun onConfigure(input:AudioProcessor.AudioFormat):AudioProcessor.AudioFormat {
        analyzer=SpectrumAnalyzer(input.sampleRate)
        return if(input.encoding==C.ENCODING_PCM_16BIT)input else AudioProcessor.AudioFormat.NOT_SET
    }
    override fun onFlush(){analyzer=SpectrumAnalyzer(inputAudioFormat.sampleRate);lastDelivery=0}
    override fun queueInput(input:ByteBuffer){
        if(!input.hasRemaining())return
        if(enabled()){
            val data=input.duplicate().order(ByteOrder.LITTLE_ENDIAN)
            val channels=inputAudioFormat.channelCount.coerceAtLeast(1)
            while(data.remaining()>=channels*2){
                var mono=0.0;repeat(channels){mono+=data.short/32768.0}
                analyzer?.accept(mono/channels)?.let{frame->
                    if(frame.samples-lastDelivery>=frame.sampleRate/10){lastDelivery=frame.samples;delivered(frame)}
                }
            }
        }
        replaceOutputBuffer(input.remaining()).put(input).flip()
    }
}
