package com.aether.radio

/** Starts a standalone background test without keeping an Instrumentation process active. */
class SoakFixture:android.app.Activity(){
    private var bound=false
    private val connection=object:android.content.ServiceConnection{
        override fun onServiceConnected(name:android.content.ComponentName,binder:android.os.IBinder){
            val service=(binder as PlaybackService.LocalBinder).service
            BackgroundSoak.start(applicationContext,service)
            startActivity(android.content.Intent(this@SoakFixture,MainActivity::class.java))
            finish()
        }
        override fun onServiceDisconnected(name:android.content.ComponentName){}
    }
    override fun onCreate(state:android.os.Bundle?){super.onCreate(state);bound=bindService(android.content.Intent(this,PlaybackService::class.java).setAction(PlaybackService.LOCAL_BIND),connection,BIND_AUTO_CREATE)}
    override fun onDestroy(){if(bound)unbindService(connection);super.onDestroy()}
}

private object BackgroundSoak{
    private val main=android.os.Handler(android.os.Looper.getMainLooper())
    fun start(context:android.content.Context,service:PlaybackService){
        main.removeCallbacksAndMessages(null)
        val file=java.io.File(context.getExternalFilesDir("evidence"),"standalone-background-soak.jsonl");file.writeText("")
        val live=service.live;val s=live.session
        val station=Station("verification:soak-radio","SomaFM Groove Salad",stream="https://ice6.somafm.com/groovesalad-128-aac")
        s.savedStations[station.id]=station;s.select(station.id)
        val start=android.os.SystemClock.elapsedRealtime();var switched=false
        val tick=object:Runnable{override fun run(){
            val elapsed=android.os.SystemClock.elapsedRealtime()-start
            if(elapsed>=300000&&!switched){switched=true;s.selectLocal(LocalMusic.bundled(context),0)}
            live.audio.samplePosition()
            val power=context.getSystemService(android.os.PowerManager::class.java)
            file.appendText(org.json.JSONObject().put("elapsedMs",elapsed).put("pid",android.os.Process.myPid()).put("playing",live.audio.player.isPlaying).put("positionMs",live.audio.player.currentPosition).put("source",if(s.isLocal)"local"else"radio").put("id",s.activeId).put("visible",s.uiVisible).put("interactive",power.isInteractive).put("connection",s.connection.name).put("issue",s.issue).put("done",elapsed>=600000).toString()+"\n")
            if(elapsed<600000)main.postDelayed(this,10000)
        }}
        main.post(tick)
    }
}
