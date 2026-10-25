package com.aether.radio

import android.content.Context
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.extractor.metadata.icy.IcyInfo
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
    private val latestSpectrum=java.util.concurrent.atomic.AtomicReference<Pair<Int,SpectrumFrame>?>(null)
    private val spectrumPending=java.util.concurrent.atomic.AtomicBoolean(false)
    private val spectrum=SpectrumProcessor({state.spectrumEnabled&&state.powered}) {frame->
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
        .setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(DefaultHttpDataSource.Factory().setUserAgent("Aether/${BuildConfig.VERSION_NAME}").setConnectTimeoutMs(8000).setReadTimeoutMs(8000).setAllowCrossProtocolRedirects(false)))
        .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),true)
        .setHandleAudioBecomingNoisy(true).build()
    init {
        player.volume=state.volume/30f
        player.playbackParameters=PlaybackParameters(state.tempo/100f,1f)
        player.addListener(object:Player.Listener {
            override fun onPlaybackStateChanged(playbackState:Int){
                state.connection=when(playbackState){Player.STATE_BUFFERING->Connection.BUFFERING;Player.STATE_READY->if(player.isPlaying)Connection.CONNECTED else Connection.PAUSED;Player.STATE_ENDED->Connection.DISCONNECTED;else->if(state.issue!=null)Connection.ERROR else Connection.DISCONNECTED}
                state.decodedFormat=showFormat(player.audioFormat)
                state.changed()
            }
            override fun onIsPlayingChanged(isPlaying:Boolean){
                state.playing=isPlaying
                if(player.playbackState==Player.STATE_READY)state.connection=if(isPlaying)Connection.CONNECTED else Connection.PAUSED
                state.changed()
            }
            override fun onPlayerError(error:PlaybackException){state.connection=Connection.ERROR;state.playing=false;state.issue=error.errorCodeName;state.metadata=null;state.spectrumFrame=null;state.changed()}
        })
        player.addAnalyticsListener(object:AnalyticsListener {
            override fun onAudioInputFormatChanged(eventTime:AnalyticsListener.EventTime,format:Format,decoderReuseEvaluation:androidx.media3.exoplayer.DecoderReuseEvaluation?){
                if(eventTime.timeline.isEmpty)return
                val id=eventTime.timeline.getWindow(eventTime.windowIndex,Timeline.Window()).mediaItem.mediaId
                if(id==state.station?.id){state.decodedFormat=showFormat(format);state.changed()}
            }
            override fun onMetadata(eventTime:AnalyticsListener.EventTime,metadata:Metadata){
                val window=Timeline.Window()
                if(eventTime.timeline.isEmpty)return
                val id=eventTime.timeline.getWindow(eventTime.windowIndex,window).mediaItem.mediaId
                if(id!=state.station?.id)return
                for(i in 0 until metadata.length()){val entry=metadata[i];if(entry is IcyInfo)state.metadata=entry.title?.takeIf{it.isNotBlank()}}
                state.changed()
            }
        })
        state.onSelect={station->select(station)}
        state.onVolume={player.volume=it/30f}
        state.onTempo={player.playbackParameters=PlaybackParameters(it/100f,1f)}
        state.onPlay={play->if(play){if(player.mediaItemCount==0)state.station?.let{select(it)}else {if(player.playbackState==Player.STATE_IDLE){state.issue=null;player.prepare()};player.play()}}else player.pause()}
    }
    fun select(station:Station){
        val url=station.stream?:return
        if(!url.startsWith("https://",true)){state.issue="HTTPS stream required";state.connection=Connection.ERROR;state.changed();return}
        generation++;latestSpectrum.set(null);state.spectrumFrame=null;state.metadata=null;state.decodedFormat=null
        player.setMediaItem(MediaItem.Builder().setUri(url).setMediaId(station.id).setMediaMetadata(MediaMetadata.Builder().setTitle(station.name).build()).build())
        player.prepare();player.play()
    }
    fun pause(){player.pause()}
    fun close(){player.release()}
}
