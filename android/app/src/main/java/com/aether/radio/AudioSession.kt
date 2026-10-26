package com.aether.radio

import android.content.Context
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.extractor.metadata.icy.IcyInfo
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import android.os.Handler
import android.os.Looper

/** Exactly one decoder/player. Playback state is exclusively driven by Media3 callbacks. */
@androidx.annotation.OptIn(UnstableApi::class)
class AudioSession(context:Context,private val state:Session) {
    private val main=Handler(Looper.getMainLooper())
    @Volatile private var generation=0
    private var reconnect:Runnable?=null
    private var retryCount=0
    private var closed=false
    private val radioElapsed=PlaybackElapsedClock()
    private val connectivity=context.applicationContext.getSystemService(android.net.ConnectivityManager::class.java)
    private val networkCallback=object:android.net.ConnectivityManager.NetworkCallback(){
        override fun onAvailable(network:android.net.Network){main.post{reconnect?.let{main.removeCallbacks(it);main.post(it)}}}
    }
    private fun cancelReconnect(){reconnect?.let{main.removeCallbacks(it)};reconnect=null;retryCount=0}
    private fun recoverNetwork(error:PlaybackException){
        if(state.isLocal||!state.playIntent||!state.powered||closed||retryCount>=5||error.errorCode !in setOf(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT))return
        reconnect?.let{main.removeCallbacks(it)}
        val expected=generation;val id=state.activeId;val delay=minOf(15000L,1000L shl retryCount++)
        reconnect=Runnable{
            reconnect=null
            if(!closed&&generation==expected&&state.activeId==id&&state.playIntent&&state.powered){state.issue=null;state.connection=Connection.CONNECTING;state.changed();player.prepare();player.play()}
        }.also{main.postDelayed(it,delay)}
    }
    private val latestSpectrum=java.util.concurrent.atomic.AtomicReference<Pair<Int,SpectrumFrame>?>(null)
    private val spectrumPending=java.util.concurrent.atomic.AtomicBoolean(false)
    private val spectrum=SpectrumProcessor({state.spectrumEnabled&&state.powered&&state.uiVisible}) {frame->
        latestSpectrum.set(generation to frame)
        if(spectrumPending.compareAndSet(false,true))main.post{
            val latest=latestSpectrum.getAndSet(null);spectrumPending.set(false)
            if(latest!=null&&latest.first==generation&&state.playing&&state.powered){state.spectrumFrame=latest.second;state.onSpectrum()}
        }
    }
    private fun showFormat(format:Format?):String?=format?.let{f->
        val codec=when(f.sampleMimeType){"audio/mp4a-latm"->"AAC";"audio/mpeg"->"MP3";"audio/opus"->"Opus";"audio/vorbis"->"Vorbis";"audio/flac"->"FLAC";else->f.codecs?.uppercase()?:"Audio"}
        buildList{
            if(f.averageBitrate>0)add("${f.averageBitrate/1000} kbps")
            add(codec)
            if(f.sampleRate>0)add("${f.sampleRate/1000f} kHz")
            if(f.channelCount>0)add(if(f.channelCount==1)"Mono"else if(f.channelCount==2)"Stereo"else "${f.channelCount} ch")
        }.joinToString(" · ")
    }
    private val renderers=object:DefaultRenderersFactory(context){
        override fun buildAudioSink(context:Context,enableFloatOutput:Boolean,enableAudioTrackPlaybackParams:Boolean):AudioSink = DefaultAudioSink.Builder(context).setEnableFloatOutput(false).setEnableAudioTrackPlaybackParams(false).setAudioProcessors(arrayOf(spectrum)).build()
    }
    val player:ExoPlayer=ExoPlayer.Builder(context,renderers)
        .setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(DefaultDataSource.Factory(context,DefaultHttpDataSource.Factory().setUserAgent("Aether/${BuildConfig.VERSION_NAME}").setConnectTimeoutMs(8000).setReadTimeoutMs(8000).setAllowCrossProtocolRedirects(false))))
        .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),true)
        .setHandleAudioBecomingNoisy(true).build()
    init {
        runCatching{connectivity.registerDefaultNetworkCallback(networkCallback)}
        player.volume=state.volume/30f
        player.playbackParameters=PlaybackParameters(state.tempo/100f,1f)
        player.addListener(object:Player.Listener {
            override fun onMediaItemTransition(mediaItem:MediaItem?,reason:Int){
                if(state.isLocal){val i=state.localQueue.indexOfFirst{it.id==mediaItem?.mediaId};if(i>=0){generation++;latestSpectrum.set(null);state.localIndex=i;state.localPositionMs=0;state.playbackPositionMs=0;state.positionSampleTime=playbackClockMs();state.localDurationMs=state.localQueue[i].durationMs;state.spectrumFrame=null;state.metadata=null;state.issue=null;state.changed()}}
            }
            override fun onPlaybackStateChanged(playbackState:Int){
                state.connection=when(playbackState){Player.STATE_BUFFERING->Connection.BUFFERING;Player.STATE_READY->if(player.isPlaying)Connection.CONNECTED else Connection.PAUSED;Player.STATE_ENDED->Connection.DISCONNECTED;else->if(state.issue!=null)Connection.ERROR else Connection.DISCONNECTED}
                if(playbackState==Player.STATE_ENDED){state.playIntent=false;state.spectrumFrame=null;cancelReconnect()}
                state.decodedFormat=showFormat(player.audioFormat)
                state.changed()
            }
            override fun onIsPlayingChanged(isPlaying:Boolean){
                state.playing=isPlaying;samplePosition()
                if(isPlaying)cancelReconnect()
                if(player.playbackState==Player.STATE_READY)state.connection=if(isPlaying)Connection.CONNECTED else Connection.PAUSED
                state.changed()
            }
            override fun onPlayerError(error:PlaybackException){state.connection=Connection.ERROR;state.playing=false;state.issue=error.errorCodeName;state.metadata=null;state.spectrumFrame=null;state.changed();recoverNetwork(error)}
        })
        player.addAnalyticsListener(object:AnalyticsListener {
            override fun onAudioInputFormatChanged(eventTime:AnalyticsListener.EventTime,format:Format,decoderReuseEvaluation:androidx.media3.exoplayer.DecoderReuseEvaluation?){
                if(eventTime.timeline.isEmpty)return
                val id=eventTime.timeline.getWindow(eventTime.windowIndex,Timeline.Window()).mediaItem.mediaId
                if(id==state.activeId){state.decodedFormat=showFormat(format);state.changed()}
            }
            override fun onMetadata(eventTime:AnalyticsListener.EventTime,metadata:Metadata){
                val window=Timeline.Window()
                if(eventTime.timeline.isEmpty)return
                val id=eventTime.timeline.getWindow(eventTime.windowIndex,window).mediaItem.mediaId
                if(state.isLocal||id!=state.activeId)return
                for(i in 0 until metadata.length()){val entry=metadata[i];if(entry is IcyInfo)state.metadata=entry.title?.takeIf{it.isNotBlank()}}
                state.changed()
            }
        })
        state.onSelect={station->select(station)}
        state.onLocalSelect={queue,index->selectLocal(queue,index)}
        state.onLocalSeek={player.seekTo(it);samplePosition()}
        state.onVolume={player.volume=it/30f}
        state.onTempo={player.playbackParameters=PlaybackParameters(it/100f,1f)}
        state.onPlay={play->if(play){if(player.mediaItemCount==0){if(state.isLocal)selectLocal(state.localQueue,state.localIndex)else state.station?.let{select(it)}}else {if(player.playbackState==Player.STATE_ENDED)player.seekTo(0);if(player.playbackState==Player.STATE_IDLE){state.issue=null;player.prepare()};player.play()}}else {cancelReconnect();player.pause()}}
    }
    fun select(station:Station){
        cancelReconnect();radioElapsed.reset(playbackClockMs())
        val url=station.stream?:return
        if(!url.startsWith("https://",true)){state.issue="HTTPS stream required";state.connection=Connection.ERROR;state.changed();return}
        generation++;latestSpectrum.set(null);state.spectrumFrame=null;state.metadata=null;state.decodedFormat=null
        player.setMediaItem(MediaItem.Builder().setUri(url).setMediaId(station.id).setMediaMetadata(MediaMetadata.Builder().setTitle(station.name).build()).build())
        player.prepare();player.play()
    }
    fun selectLocal(queue:List<LocalTrack>,index:Int){
        cancelReconnect();radioElapsed.reset(playbackClockMs())
        if(index !in queue.indices)return
        require(queue.all{it.uri.startsWith("asset:///albums/before-the-rise/")||android.net.Uri.parse(it.uri).scheme=="content"})
        generation++;latestSpectrum.set(null);state.spectrumFrame=null;state.metadata=null;state.decodedFormat=null
        val items=queue.map{item(it)}
        player.setMediaItems(items,index,0L);player.prepare();player.play()
    }
    private fun item(t:LocalTrack)=MediaItem.Builder().setUri(t.uri).setMediaId(t.id).setMediaMetadata(MediaMetadata.Builder().setTitle(t.title).setAlbumTitle(t.album).setArtist(t.artist.takeIf{it.isNotBlank()}).setArtworkUri(t.coverUri.takeIf{it.isNotBlank()}?.let{android.net.Uri.parse(it)}).build()).build()
    fun restoreSelection(position:Long){
        if(state.isLocal&&state.localQueue.isNotEmpty())player.setMediaItems(state.localQueue.map{item(it)},state.localIndex,position.coerceIn(0,state.localDurationMs))
        else state.station?.stream?.takeIf{it.startsWith("https://")}?.let{url->player.setMediaItem(MediaItem.Builder().setUri(url).setMediaId(state.station!!.id).setMediaMetadata(MediaMetadata.Builder().setTitle(state.station!!.name).build()).build())}
    }
    fun samplePosition(){
        val now=playbackClockMs()
        state.playbackPositionMs=if(state.isLocal)player.currentPosition.coerceAtLeast(0)else radioElapsed.sample(now,player.isPlaying)
        state.positionSampleTime=now;state.seekable=player.isCurrentMediaItemSeekable
        if(state.isLocal){state.localPositionMs=state.playbackPositionMs;if(player.duration!=C.TIME_UNSET&&player.duration>=0)state.localDurationMs=player.duration}
    }
    fun pause(){cancelReconnect();player.pause();samplePosition()}
    fun close(){closed=true;cancelReconnect();runCatching{connectivity.unregisterNetworkCallback(networkCallback)};player.release()}
}
