package com.aether.radio

import org.junit.Assert.*
import org.junit.Test

class RelativeEncoderTest{
    @Test fun pressingAndSubDetentJitterNeverJump(){val e=RelativeEncoder();e.begin(14);assertEquals(14,e.push(0.0));repeat(30){assertEquals(14,e.push(.12));assertEquals(14,e.push(-.12))}}
    @Test fun slowAndFastSamplesKeepTheSameActualTravel(){val a=RelativeEncoder();val b=RelativeEncoder();a.begin(10);b.begin(10);repeat(100){a.push(.04)};assertEquals(a.push(0.0),b.push(4.0));assertEquals(14,a.push(0.0))}
    @Test fun reversalDoesNotReplayQueuedTravel(){val e=RelativeEncoder();e.begin(10);assertEquals(15,e.push(5.0));assertEquals(14,e.push(-1.0));assertEquals(13,e.push(-1.0))}
    @Test fun crossingADetentDoesNotReverseOnFingerTremor(){val e=RelativeEncoder();e.begin(10);assertEquals(11,e.push(.85));repeat(20){assertEquals(11,e.push(-.12));assertEquals(11,e.push(.12))};assertEquals(10,e.push(-.7))}
    @Test fun endStopsDiscardOverscrollAndReverseImmediately(){val e=RelativeEncoder();e.begin(29);assertEquals(30,e.push(50.0));assertEquals(29,e.push(-1.0));e.begin(1);assertEquals(0,e.push(-50.0));assertEquals(1,e.push(1.0))}
    @Test fun newGestureAndInvalidSamplesDoNotKeepResidualMotion(){val e=RelativeEncoder();e.begin(20);e.push(.5);e.begin(5);assertEquals(5,e.push(.2));assertEquals(5,e.push(Double.NaN));assertEquals(5,e.push(Double.POSITIVE_INFINITY))}
}
