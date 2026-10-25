package com.aether.radio

import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View

/** Native functional haptics, respecting the device and user's system setting. */
class ControlHaptics(private val view:View) {
    private val sounds=ControlSounds(view.context.applicationContext)
    val soundPlays get()=sounds.played
    val available=view.context.getSystemService(VibratorManager::class.java)?.defaultVibrator?.hasVibrator()==true
    var attempts=0;private set
    var accepted=0;private set
    var suppressed=0;private set
    var ticks=0;private set
    var presses=0;private set
    var stops=0;private set
    var last="none";private set
    private var lastAt=-1000L
    private fun emit(kind:Int,label:String,gap:Long=40){
        val now=SystemClock.uptimeMillis();if(now-lastAt<gap)return
        lastAt=now;attempts++;last=label
        @Suppress("DEPRECATION")
        val enabled=Settings.System.getInt(view.context.contentResolver,Settings.System.HAPTIC_FEEDBACK_ENABLED,1)!=0
        if(!enabled||!view.isHapticFeedbackEnabled){suppressed++;return}
        val motor=view.context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        val tactile=label in listOf("volume-detent","detent","end-stop")&&available
        val delivered=if(tactile)runCatching{motor!!.vibrate(android.os.VibrationEffect.createPredefined(if(label=="end-stop")android.os.VibrationEffect.EFFECT_HEAVY_CLICK else android.os.VibrationEffect.EFFECT_TICK),android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).build());true}.getOrDefault(false)else false
        if(delivered||view.performHapticFeedback(kind))accepted++
    }
    fun press(){val before=attempts;emit(HapticFeedbackConstants.VIRTUAL_KEY,"key");if(attempts>before)presses++}
    fun tick(frequent:Boolean=false){
        val before=attempts
        val kind=if(Build.VERSION.SDK_INT>=34){HapticFeedbackConstants.SEGMENT_TICK}else HapticFeedbackConstants.CLOCK_TICK
        emit(kind,if(frequent)"volume-detent" else "detent")
        if(attempts>before){ticks++;sounds.tick()}
    }
    fun boundary(){val before=attempts;emit(HapticFeedbackConstants.REJECT,"end-stop",180);if(attempts>before){stops++;sounds.tick(true)}}
    fun close(){sounds.close()}
    fun confirm(){emit(HapticFeedbackConstants.CONFIRM,"selection")}
}
