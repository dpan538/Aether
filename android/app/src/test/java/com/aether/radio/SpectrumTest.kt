package com.aether.radio

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class SpectrumTest {
    @Test fun oneKilohertzPcmPeaksInOneKilohertzBand(){
        val analyzer=SpectrumAnalyzer(48000);var frame:SpectrumFrame?=null
        for(i in 0 until 2048)frame=analyzer.accept(.5*sin(2*PI*1000*i/48000))?:frame
        val result=frame!!
        assertEquals(5,result.levels.indices.maxBy{result.levels[it]})
        assertTrue(result.levels[5] in -8f..-5f)
        assertEquals(.3535,result.rms.toDouble(),.005)
        assertEquals(2048L,result.samples)
    }
    @Test fun silenceHasNoSynthesizedActivity(){
        val analyzer=SpectrumAnalyzer(44100);var frame:SpectrumFrame?=null
        repeat(2048){frame=analyzer.accept(0.0)?:frame}
        assertTrue(frame!!.levels.all{it==-90f});assertEquals(0f,frame!!.rms)
    }
}
