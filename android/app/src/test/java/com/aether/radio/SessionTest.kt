package com.aether.radio

import org.junit.Assert.*
import org.junit.Test

class SessionTest {
    @Test fun referenceIsDeterministicAndOffline() {
        val s=Session(true)
        assertEquals("FM 802",s.station?.name)
        assertEquals(20,s.volume)
        assertFalse(s.light)
        assertEquals(listOf("ref:0","ref:2","ref:1","ref:3"),s.favorites)
        assertTrue(s.catalog.all{it.stream==null})
        assertEquals(Connection.DISCONNECTED,s.connection)
    }
    @Test fun combinedFiltersNeverReplaceCurrentStation() {
        val s=Session(true)
        s.filter("region","UK");s.filter("genre","Experimental")
        assertEquals(listOf("NTS RADIO"),s.filtered().map{it.name})
        assertEquals("FM 802",s.station?.name)
        s.query="unmatched";assertTrue(s.filtered().isEmpty())
        s.resetFilters();assertEquals(4,s.filtered().size)
    }
    @Test fun relativeVolumeMuteRestoresAndClamps() {
        val s=Session(true)
        s.changeVolume(21);s.toggleMute();assertEquals(0,s.volume)
        s.toggleMute();assertEquals(21,s.volume)
        s.changeVolume(31);assertEquals(30,s.volume)
        s.changeVolume(-1);assertEquals(0,s.volume)
        s.locked=true;s.changeVolume(15);assertEquals(0,s.volume)
    }
    @Test fun pageChangeSharesStationVolumeAndConnection() {
        val s=Session(true);s.select("ref:3");s.changeVolume(17);s.page=1
        assertEquals("RADIO NOVA",s.station?.name);assertEquals(17,s.volume)
        assertEquals(Connection.DISCONNECTED,s.connection)
    }
    @Test fun detentsDropFastBurstsRatherThanQueueThem() {
        val gate=DetentGate(.3,140,.12)
        assertEquals(0,gate.push(10.0,0));assertEquals(0,gate.push(10.0,20))
        assertEquals(1,gate.push(10.0,40))
        for(t in listOf(60L,80,100))assertEquals(0,gate.push(10.0,t))
        assertEquals(0,gate.push(0.0,500))
        assertEquals(0,gate.push(.1,550));assertEquals(0,gate.push(.1,600));assertEquals(1,gate.push(.11,650))
    }
    @Test fun cancellationAndReversalRequireFreshTravel() {
        val gate=DetentGate(.57,180,.2)
        gate.push(.2,0);gate.push(.2,20)
        assertEquals(0,gate.push(-.2,40));assertEquals(0,gate.push(-.2,60));assertEquals(-1,gate.push(-.2,80))
        gate.reset();assertEquals(0,gate.push(.2,400))
    }
    @Test fun liveDoesNotInheritMockMetadata() {
        val s=Session(false)
        assertTrue(s.catalog.isEmpty());assertNull(s.station);assertNull(s.metadata);assertNull(s.decodedFormat)
        assertFalse(s.playing);assertTrue(s.favorites.isEmpty())
    }
    @Test fun directoryRefreshCannotRenameThePlayingStream() {
        val s=Session(false);val playing=Station("a","Actual station",stream="https://example.test/a")
        s.catalog=listOf(playing);s.select(playing.id)
        s.catalog=listOf(Station("b","Different search result"));s.selected=0
        assertEquals("Actual station",s.station?.name)
        s.metadata="Actual ICY title";s.connection=Connection.CONNECTED;s.playing=true;s.freezeInk();s.powered=false
        s.metadata=null;s.connection=Connection.PAUSED;s.playing=false
        assertEquals("Actual ICY title",s.retainedInk?.metadata);assertEquals(Connection.CONNECTED,s.retainedInk?.connection)
    }
}
