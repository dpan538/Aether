package com.aether.radio

import android.app.Instrumentation
import android.app.NotificationManager
import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicReference

class NotificationSuite(private val test:Instrumentation,private val activity:MainActivity){
    private fun <T> main(block:()->T):T{val r=AtomicReference<Result<T>>();test.runOnMainSync{r.set(runCatching{block()})};return r.get().getOrThrow()}
    private fun waitFor(block:()->Boolean){val end=SystemClock.uptimeMillis()+25000;while(SystemClock.uptimeMillis()<end){if(main(block))return;Thread.sleep(100)};error("Media notification timed out")}
    fun run():JSONObject{
        val s=activity.session;val checks=JSONArray();val manager=test.targetContext.getSystemService(NotificationManager::class.java)
        main{s.selectLocal(LocalMusic.bundled(test.targetContext),0)};waitFor{s.playing}
        fun notification()=manager.activeNotifications.firstOrNull{it.notification.channelId=="manpin.playback"}?.notification
        waitFor{notification()?.extras?.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString()=="Before the First Light"&&notification()?.getLargeIcon()!=null}
        check(main{notification()!!.actions.size>=3&&manager.getNotificationChannel("manpin.playback").name.toString()=="漫频 · 音乐播放"})
        checks.put(JSONObject().put("name","MediaStyle notification contains actual title, album artwork, three transport actions and the named 漫频 playback channel").put("passed",true))
        test.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_HOME);Thread.sleep(1200)
        android.os.ParcelFileDescriptor.AutoCloseInputStream(test.uiAutomation.executeShellCommand("cmd statusbar expand-settings")).use{it.readBytes()};Thread.sleep(1000)
        val image=test.uiAutomation.takeScreenshot()!!;java.io.File(test.targetContext.getExternalFilesDir("evidence"),"media-notification.png").outputStream().use{image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};image.recycle()
        java.io.File(test.targetContext.getExternalFilesDir("evidence"),"notification-actions.txt").writeText(main{notification()!!.actions.joinToString("\n"){it.title.toString()}})
        waitFor{notification()?.actions?.any{it.title.toString().lowercase() in listOf("pause","暂停")}==true}
        main{notification()!!.actions.first{it.title.toString().lowercase() in listOf("pause","暂停")}.actionIntent.send()};waitFor{!s.playing&&!s.playIntent}
        waitFor{notification()?.actions?.any{it.title.toString().lowercase() in listOf("play","播放")}==true}
        main{notification()!!.actions.first{it.title.toString().lowercase() in listOf("play","播放")}.actionIntent.send()};waitFor{s.playing&&s.playIntent}
        checks.put(JSONObject().put("name","Real notification PendingIntents pause/resume the same service player while the app is in background").put("passed",true))
        android.os.ParcelFileDescriptor.AutoCloseInputStream(test.uiAutomation.executeShellCommand("cmd statusbar collapse")).use{it.readBytes()};main{notification()!!.contentIntent.send()};waitFor{s.uiVisible&&s.page==1}
        checks.put(JSONObject().put("name","Tapping the notification returns to the immersive page and the continuing session").put("passed",true))
        return JSONObject().put("passed",true).put("checks",checks)
    }
}
