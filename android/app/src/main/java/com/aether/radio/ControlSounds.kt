package com.aether.radio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.SystemClock
import android.provider.Settings

/** Short original mechanical detent, on the system sonification stream and setting. */
class ControlSounds(private val context:Context){
    private val pool=SoundPool.Builder().setMaxStreams(2).setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()
    var played=0;private set
    private var ready=false
    private val sound:Int
    private var lastAt=0L
    init{pool.setOnLoadCompleteListener{_,_,status->ready=status==0};sound=pool.load(context,R.raw.encoder_tick,1)}
    fun tick(boundary:Boolean=false){
        val now=SystemClock.uptimeMillis()
        if(!ready||now-lastAt<45||Settings.System.getInt(context.contentResolver,Settings.System.SOUND_EFFECTS_ENABLED,1)==0)return
        lastAt=now;if(pool.play(sound,.20f,.20f,1,0,if(boundary).82f else 1f)!=0)played++
    }
    fun close(){pool.release()}
}
