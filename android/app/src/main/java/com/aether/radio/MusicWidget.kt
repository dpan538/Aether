package com.aether.radio

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken

/** Real RemoteViews widget; its commands are acknowledged by the same media-session service. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class MusicWidget:AppWidgetProvider() {
    override fun onUpdate(context:Context,manager:AppWidgetManager,ids:IntArray){
        PlaybackService.current?.let{updateAll(context,it.live);return}
        val p=context.getSharedPreferences("aether-widget",Context.MODE_PRIVATE)
        ids.forEach{manager.updateAppWidget(it,views(context,p.getString("title","漫频")!!,p.getString("album","选择音乐，开始聆听")!!,p.getString("cover","")!!,false,p.getLong("position",0),p.getLong("duration",0),p.getBoolean("local",false),p.getBoolean("can-play",false),p.getBoolean("can-step",false)))}
    }
    override fun onReceive(context:Context,intent:Intent){
        super.onReceive(context,intent)
        val action=intent.action?.removePrefix(PREFIX)?:return
        if(intent.action?.startsWith(PREFIX)!=true||action !in listOf("toggle","previous","next"))return
        val pending=goAsync()
        val app=context.applicationContext
        val future=runCatching{MediaController.Builder(app,SessionToken(app,ComponentName(app,PlaybackService::class.java))).buildAsync()}.getOrElse{pending.finish();return}
        future.addListener({
            var controller:MediaController?=null
            runCatching{
                controller=future.get()
                val connected=controller!!
                val ack=connected.sendCustomCommand(SessionCommand(PlaybackService.WIDGET_COMMAND,Bundle.EMPTY),Bundle().apply{putString("action",action)})
                ack.addListener({connected.release();pending.finish()},context.mainExecutor)
            }.onFailure{controller?.release();pending.finish()}
        },context.mainExecutor)
    }
    companion object {
        private const val PREFIX="com.aether.radio.WIDGET."
        private var coverKey="";private var cover:android.graphics.Bitmap?=null
        private fun command(context:Context,name:String)=PendingIntent.getBroadcast(context,name.hashCode(),Intent(context,MusicWidget::class.java).setAction(PREFIX+name),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        private fun views(context:Context,title:String,album:String,art:String,playing:Boolean,position:Long,duration:Long,local:Boolean,canPlay:Boolean,canStep:Boolean):RemoteViews {
            val view=RemoteViews(context.packageName,R.layout.music_widget)
            view.setTextViewText(R.id.widget_title,title);view.setTextViewText(R.id.widget_album,album)
            if(art!=coverKey){coverKey=art;cover=ArtworkStore.load(context,art,192)}
            cover?.let{view.setImageViewBitmap(R.id.widget_cover,it)}?:view.setImageViewResource(R.id.widget_cover,R.drawable.icon_canvas_99)
            view.setImageViewResource(R.id.widget_play,if(playing)R.drawable.widget_pause else R.drawable.widget_play)
            view.setContentDescription(R.id.widget_play,if(playing)"暂停"else"播放")
            view.setBoolean(R.id.widget_play,"setEnabled",canPlay)
            view.setBoolean(R.id.widget_previous,"setEnabled",canStep);view.setBoolean(R.id.widget_next,"setEnabled",canStep)
            view.setOnClickPendingIntent(R.id.widget_play,command(context,"toggle"));view.setOnClickPendingIntent(R.id.widget_previous,command(context,"previous"));view.setOnClickPendingIntent(R.id.widget_next,command(context,"next"))
            view.setOnClickPendingIntent(R.id.widget_root,PendingIntent.getActivity(context,11,Intent(context,MainActivity::class.java).putExtra("show-player",true),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            progress(view,position,duration,local);return view
        }
        private fun progress(view:RemoteViews,position:Long,duration:Long,local:Boolean){
            view.setTextViewText(R.id.widget_time,if(local)"${LocalMusic.time(position)} / ${LocalMusic.time(duration)}" else "FM · 直播")
            view.setProgressBar(R.id.widget_progress,1000,if(local&&duration>0)(position*1000/duration).toInt().coerceIn(0,1000)else 0,false)
        }
        private fun ids(context:Context)=AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context,MusicWidget::class.java))
        fun updateAll(context:Context,live:LivePlayback){
            val s=live.session;val t=s.localTrack;val title=t?.title?:s.station?.name?:"漫频";val album=t?.album?.ifBlank{t.artist}?:"FM ${s.displayOrdinal?.toString()?.padStart(3,'0')?:"—"}"
            val art=t?.coverUri.orEmpty()
            context.getSharedPreferences("aether-widget",Context.MODE_PRIVATE).edit().putString("title",title).putString("album",album).putString("cover",art).putLong("position",s.localPositionMs).putLong("duration",s.localDurationMs).putBoolean("local",s.isLocal).putBoolean("can-play",s.canUse("play-toggle")).putBoolean("can-step",s.canUse("next")).apply()
            val ids=ids(context);if(ids.isEmpty())return
            AppWidgetManager.getInstance(context).updateAppWidget(ids,views(context,title,album,art,s.playing,s.localPositionMs,s.localDurationMs,s.isLocal,s.canUse("play-toggle"),s.canUse("next")))
        }
        fun updateProgress(context:Context,live:LivePlayback){
            val s=live.session;if(!s.playing||!s.isLocal)return
            val ids=ids(context);if(ids.isEmpty())return
            val view=RemoteViews(context.packageName,R.layout.music_widget);progress(view,s.localPositionMs,s.localDurationMs,true);AppWidgetManager.getInstance(context).partiallyUpdateAppWidget(ids,view)
        }
    }
}
