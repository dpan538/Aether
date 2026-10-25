package com.aether.radio

import org.junit.Assert.*
import org.junit.Test

class LocalQueueTest {
    private val queue=(1..3).map{LocalTrack("local:$it","Track $it","asset:///albums/before-the-rise/$it.mp3","Album",60000)}
    @Test fun explicitSourceSwitchesDoNotRewriteRadioPresets(){
        val s=Session(false);s.catalog=listOf(Station("radio","Radio",stream="https://example.test/audio"));s.select("radio")
        val presets=s.presetPool();s.selectLocal(queue,1)
        assertTrue(s.isLocal);assertEquals("local:2",s.activeId);assertEquals(2,s.displayOrdinal);assertEquals(presets,s.presetPool())
        assertFalse(s.canUse("favorite-toggle"));s.toggleFavorite();assertTrue(s.favorites.isEmpty())
        s.select("radio");assertFalse(s.isLocal);assertEquals("radio",s.activeId);assertEquals(queue,s.localQueue)
    }
    @Test fun localNavigationRespectsLockAndHasExplicitFirstLastActions(){
        val s=Session(false);s.selectLocal(queue,0);s.step(-1);assertEquals(2,s.localIndex)
        s.step(-1,true);assertEquals(0,s.localIndex);s.step(1,true);assertEquals(2,s.localIndex)
        s.locked=true;s.step(-1);s.selectLocal(queue,0);assertEquals(2,s.localIndex)
        s.locked=false;s.powered=false;s.selectLocal(queue,0);assertEquals(2,s.localIndex)
    }
    @Test fun tuningWheelExplicitlyReturnsToRadioAndSeekIsClamped(){
        val s=Session(false);s.catalog=listOf(Station("radio","Radio",stream="https://example.test/audio"));s.selectLocal(queue,0)
        var seek=-1L;s.onLocalSeek={seek=it};s.seekLocal(-100);assertEquals(0,seek);s.seekLocal(70000);assertEquals(60000,seek)
        s.tune(1);assertFalse(s.isLocal);assertEquals("radio",s.activeId)
    }
}
