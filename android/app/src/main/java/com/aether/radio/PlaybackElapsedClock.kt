package com.aether.radio

/** Listening time for unseekable radio: independent of an HLS window's source position. */
internal class PlaybackElapsedClock{
    private var elapsed=0L
    private var sampledAt=0L
    private var running=false
    fun reset(now:Long){elapsed=0;sampledAt=now;running=false}
    fun sample(now:Long,playing:Boolean):Long{
        if(running)elapsed+=(now-sampledAt).coerceAtLeast(0)
        sampledAt=now;running=playing
        return elapsed
    }
}
