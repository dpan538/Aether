package com.aether.radio

import org.junit.Assert.*
import org.junit.Test

class PlaybackElapsedClockTest{
    @Test fun countsOnlyActualPlaybackAcrossPauseAndBuffering(){
        val clock=PlaybackElapsedClock();clock.reset(1000)
        assertEquals(0,clock.sample(9000,false));assertEquals(0,clock.sample(10000,true))
        assertEquals(1500,clock.sample(11500,false));assertEquals(1500,clock.sample(20000,false))
        assertEquals(1500,clock.sample(24000,true));assertEquals(3500,clock.sample(26000,true))
    }
    @Test fun selectingAnotherStationStartsFreshWithoutSourceWindowOffsets(){
        val clock=PlaybackElapsedClock();clock.reset(1000);clock.sample(1000,true);assertEquals(7000,clock.sample(8000,true))
        clock.reset(8000);assertEquals(0,clock.sample(10000,false));assertEquals(0,clock.sample(15000,true));assertEquals(1000,clock.sample(16000,true))
    }
}
