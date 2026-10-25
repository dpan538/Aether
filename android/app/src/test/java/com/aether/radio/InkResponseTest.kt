package com.aether.radio

import org.junit.Assert.*
import org.junit.Test

class InkResponseTest {
    @Test fun partialRefreshRetainsPreviousActualFrameAndSettles(){
        val ink=InkResponse<String>()
        ink.offer("page A",1,0);ink.offer("page B",2,10)
        assertEquals("page A",ink.displayed)
        ink.offer("page B",2,129);assertEquals("page A",ink.displayed)
        ink.offer("page B",2,130);assertEquals("page B",ink.displayed);assertEquals("page A",ink.previous)
        assertTrue(ink.residual(130)>ink.residual(300))
        ink.offer("page B",2,530);assertNull(ink.previous);assertFalse(ink.animating(530))
    }
    @Test fun RapidChangesCoalesceWithoutIndefinitePostponement(){
        val ink=InkResponse<String>();ink.offer("A",0,0)
        for(i in 1..12)ink.offer("$i",i,i*10L)
        assertEquals("A",ink.displayed)
        ink.offer("latest",13,130);assertEquals("latest",ink.displayed);assertEquals("A",ink.previous)
        ink.offer("instant",14,131,true);assertEquals("instant",ink.displayed);assertNull(ink.previous);assertFalse(ink.animating(131))
    }
}
