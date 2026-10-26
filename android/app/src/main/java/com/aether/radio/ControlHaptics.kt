package com.aether.radio

import android.media.AudioAttributes
import android.os.Build
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View

/** Functional touch feedback. A submitted request is never proof of physical motor movement. */
class ControlHaptics(private val view:View){
    private val sounds=ControlSounds(view.context.applicationContext)
    private val motor get()=view.context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    val available get()=motor?.hasVibrator()==true
    @Suppress("DEPRECATION")
    private val systemEnabled get()=Settings.System.getInt(view.context.contentResolver,Settings.System.HAPTIC_FEEDBACK_ENABLED,1)!=0
    val soundPlays get()=sounds.played
    var attempts=0;private set
    var accepted=0;private set
    var suppressed=0;private set
    var ticks=0;private set
    var presses=0;private set
    var stops=0;private set
    var last="none";private set
    private var lastAt=-1000L
    private fun pulse(boundary:Boolean):Boolean{
        val vibrator=motor?.takeIf{it.hasVibrator()}?:return false
        return runCatching{
            val primitive=VibrationEffect.Composition.PRIMITIVE_CLICK
            val effect=if(vibrator.areAllPrimitivesSupported(primitive))VibrationEffect.startComposition().addPrimitive(primitive,if(boundary)1f else .72f).compose()
                else VibrationEffect.createPredefined(if(boundary)VibrationEffect.EFFECT_HEAVY_CLICK else VibrationEffect.EFFECT_CLICK)
            if(Build.VERSION.SDK_INT>=33)vibrator.vibrate(effect,VibrationAttributes.Builder().setUsage(VibrationAttributes.USAGE_TOUCH).build())
            else vibrator.vibrate(effect,AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).build())
            true
        }.getOrDefault(false)
    }
    private fun emit(kind:Int,label:String,gap:Long=40,forceTest:Boolean=false){
        val now=SystemClock.uptimeMillis();if(!forceTest&&now-lastAt<gap)return
        lastAt=now;attempts++;last=label
        if(!systemEnabled||!view.isHapticFeedbackEnabled){suppressed++;return}
        val tactile=label in listOf("volume-detent","detent","end-stop","test")
        if((tactile&&pulse(label=="end-stop"))||view.performHapticFeedback(kind))accepted++
    }
    fun press(){val before=attempts;emit(HapticFeedbackConstants.VIRTUAL_KEY,"key");if(attempts>before)presses++}
    fun tick(frequent:Boolean=false){val before=attempts;emit(HapticFeedbackConstants.CLOCK_TICK,if(frequent)"volume-detent"else"detent");if(attempts>before){ticks++;sounds.tick()}}
    fun boundary(){val before=attempts;emit(HapticFeedbackConstants.REJECT,"end-stop",180);if(attempts>before){stops++;sounds.tick(true)}}
    fun testPulse(){emit(HapticFeedbackConstants.CONFIRM,"test",forceTest=true);sounds.tick()}
    fun description(zh:Boolean):String{
        val sound=Settings.System.getInt(view.context.contentResolver,Settings.System.SOUND_EFFECTS_ENABLED,1)!=0
        return if(zh)"振动马达：${if(available)"系统检测到"else"系统未检测到"}\n系统触感反馈：${if(systemEnabled)"开启"else"关闭"}\n触摸提示音：${if(sound)"开启"else"关闭"}\n已提交触感请求：$accepted"else "Vibration motor: ${if(available)"detected"else"not detected"}\nSystem haptics: ${if(systemEnabled)"on"else"off"}\nTouch sounds: ${if(sound)"on"else"off"}\nSubmitted haptic requests: $accepted"
    }
    fun confirm(){emit(HapticFeedbackConstants.CONFIRM,"selection")}
    fun close(){sounds.close()}
}
