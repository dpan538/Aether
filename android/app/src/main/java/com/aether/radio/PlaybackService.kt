package com.aether.radio

import android.app.PendingIntent
import android.content.Intent
import android.os.Binder
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.session.*
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/** The service, notification, Activity and widget all address this one live player. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlaybackService:MediaSessionService() {
    lateinit var live:LivePlayback;private set
    private var mediaSession:MediaSession?=null
    private val main=Handler(Looper.getMainLooper())
    private val binder=LocalBinder()
    inner class LocalBinder:Binder(){val service get()=this@PlaybackService}
    private var positionTicks=0
    private val tick=object:Runnable{override fun run(){
        live.audio.samplePosition()
        if(live.session.playing&&++positionTicks%20==0)live.savePosition()
        MusicWidget.updateProgress(this@PlaybackService,live)
        main.postDelayed(this,500)
    }}
    override fun onCreate(){
        super.onCreate();setMediaNotificationProvider(DefaultMediaNotificationProvider.Builder(this).setChannelId("manpin.playback").setChannelName(R.string.playback_channel).build());live=LivePlayback(applicationContext)
        live.audio.player.setWakeMode(androidx.media3.common.C.WAKE_MODE_NETWORK)
        val controls=object:ForwardingPlayer(live.audio.player){
            override fun play(){if(live.session.enabled){live.session.playIntent=true;live.session.onPlay(true);live.session.changed()}}
            override fun pause(){live.session.playIntent=false;live.session.onPlay(false);live.session.changed()}
            override fun seekToNext(){live.session.step(1)}
            override fun seekToPrevious(){live.session.step(-1)}
            override fun seekToNextMediaItem(){live.session.step(1)}
            override fun seekToPreviousMediaItem(){live.session.step(-1)}
            override fun getAvailableCommands():Player.Commands=super.getAvailableCommands().buildUpon().apply{
                if(live.session.canUse("next"))addAll(Player.COMMAND_SEEK_TO_NEXT,Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,Player.COMMAND_SEEK_TO_PREVIOUS,Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            }.build()
        }
        mediaSession=MediaSession.Builder(this,controls).setSessionActivity(PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java).putExtra("show-player",true),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)).setCallback(object:MediaSession.Callback{
            override fun onConnect(session:MediaSession,controller:MediaSession.ControllerInfo):MediaSession.ConnectionResult {
                if(controller.packageName!=packageName&&!controller.isTrusted)return MediaSession.ConnectionResult.reject()
                return MediaSession.ConnectionResult.AcceptedResultBuilder(session).setAvailableSessionCommands(MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon().add(SessionCommand(WIDGET_COMMAND,Bundle.EMPTY)).build()).build()
            }
            override fun onCustomCommand(session:MediaSession,controller:MediaSession.ControllerInfo,command:SessionCommand,args:Bundle):ListenableFuture<SessionResult>{
                if(controller.packageName!=packageName||command.customAction!=WIDGET_COMMAND)return Futures.immediateFuture(SessionResult(SessionError.ERROR_PERMISSION_DENIED))
                when(args.getString("action")){"toggle"->if(live.session.canUse("play-toggle"))live.session.togglePlay();"previous"->live.session.step(-1);"next"->live.session.step(1)}
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
        }).build()
        addSession(mediaSession!!)
        live.session.observe{MusicWidget.updateAll(this,live)}
        current=this;MusicWidget.updateAll(this,live);main.post(tick)
    }
    override fun onBind(intent:Intent?):IBinder?=if(intent?.action==LOCAL_BIND)binder else super.onBind(intent)
    override fun onGetSession(controllerInfo:MediaSession.ControllerInfo):MediaSession?=mediaSession
    override fun onTaskRemoved(rootIntent:Intent?){live.savePosition();super.onTaskRemoved(rootIntent)}
    override fun onDestroy(){if(current===this)current=null;live.session.playing=false;live.session.playIntent=false;MusicWidget.updateAll(this,live);main.removeCallbacksAndMessages(null);mediaSession?.release();mediaSession=null;live.close();super.onDestroy()}
    companion object {internal var current:PlaybackService?=null;private set
        const val LOCAL_BIND="com.aether.radio.LOCAL_PLAYBACK";const val WIDGET_COMMAND="com.aether.radio.WIDGET_CONTROL"}
}
