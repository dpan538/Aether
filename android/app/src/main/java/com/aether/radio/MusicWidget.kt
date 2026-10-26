package com.aether.radio

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken

/** The single cover widget shares playback with the foreground media service. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
open class MusicWidget:AppWidgetProvider() {
    override fun onUpdate(context:Context,manager:AppWidgetManager,ids:IntArray){
        publishPreview(context)
        PlaybackService.current?.let{updateAll(context,it.live);return}
        val p=context.getSharedPreferences("aether-widget",Context.MODE_PRIVATE)
        ids.forEach{id->manager.updateAppWidget(id,responsive(context,id,p.getString("title","漫频")!!,p.getString("album","选择音乐，开始聆听")!!,p.getString("cover","")!!,false,p.getLong("position",0),p.getLong("duration",0),p.getBoolean("local",false),p.getBoolean("can-play",false),p.getBoolean("can-step",false)))}
    }
    override fun onAppWidgetOptionsChanged(context:Context,manager:AppWidgetManager,id:Int,options:Bundle){onUpdate(context,manager,intArrayOf(id))}
    companion object {
        private fun component(context:Context)=ComponentName(context,CompactMusicWidget::class.java)
        fun publishPreview(context:Context){
            if(android.os.Build.VERSION.SDK_INT<35)return
            val preferences=context.getSharedPreferences("aether-widget-preview",Context.MODE_PRIVATE)
            if(preferences.getInt("static-preview:compact",-1)==BuildConfig.VERSION_CODE)return
            if(runCatching{AppWidgetManager.getInstance(context).removeWidgetPreview(component(context),AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN)}.isSuccess)
                preferences.edit().putInt("static-preview:compact",BuildConfig.VERSION_CODE).apply()
        }
        private fun responsive(context:Context,id:Int,title:String,album:String,art:String,playing:Boolean,position:Long,duration:Long,local:Boolean,canPlay:Boolean,canStep:Boolean):RemoteViews{
            val options=AppWidgetManager.getInstance(context).getAppWidgetOptions(id)
            @Suppress("DEPRECATION") val exact=options.getParcelableArrayList<android.util.SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES).orEmpty().filter{it.width.isFinite()&&it.height.isFinite()&&it.width>0&&it.height>0}.distinct().take(8)
            fun sized(width:Float,height:Float):RemoteViews=views(context,title,album,art,playing,position,duration,local,canPlay,canStep).also{geometry(context,it,width.coerceAtLeast(1f),height.coerceAtLeast(1f))}
            if(exact.size==1){val size=exact.first();return sized(size.width,size.height)}
            if(exact.size>1)return RemoteViews(exact.associateWith{sized(it.width,it.height)})
            fun dimension(key:String,fallback:Float)=options.getInt(key).takeIf{it>0}?.toFloat()?:fallback
            val minW=dimension(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,248f)
            val maxW=dimension(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH,minW).coerceAtLeast(minW)
            val minH=dimension(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,248f)
            val maxH=dimension(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,minH).coerceAtLeast(minH)
            if(minW==maxW&&minH==maxH)return sized(minW,minH)
            return RemoteViews(sized(maxW,minH),sized(minW,maxH))
        }
        private fun geometry(context:Context,view:RemoteViews,w:Float,h:Float){
            val scale=minOf(w/248f,h/248f).coerceIn(.05f,3f)
            val unit=android.util.TypedValue.COMPLEX_UNIT_DIP
            fun size(id:Int,width:Float?=null,height:Float?=null){if(width!=null)view.setViewLayoutWidth(id,width,unit);if(height!=null)view.setViewLayoutHeight(id,height,unit)}
            fun text(id:Int,sp:Float){view.setTextViewTextSize(id,android.util.TypedValue.COMPLEX_UNIT_SP,sp*scale)}
            val pad=(12*scale*context.resources.displayMetrics.density).toInt()
            view.setViewPadding(R.id.widget_metadata,pad,pad,pad,pad)
            text(R.id.widget_title,14f);text(R.id.widget_album,11f);text(R.id.widget_time,11f)
            view.setViewLayoutMargin(R.id.widget_album,RemoteViews.MARGIN_TOP,4f*scale,unit)
            view.setViewLayoutMargin(R.id.widget_controls,RemoteViews.MARGIN_TOP,8f*scale,unit)
            size(R.id.widget_controls,height=48f*scale);size(R.id.widget_footer,height=19f*scale);size(R.id.widget_time,height=16f*scale);size(R.id.widget_progress,height=3f*scale)
            size(R.id.widget_art_open,height=(h-133f*scale).coerceAtLeast(h*.30f))
            for(id in listOf(R.id.widget_previous,R.id.widget_play,R.id.widget_next)){
                size(id,48f*scale,48f*scale)
                val inset=(14*scale*context.resources.displayMetrics.density).toInt();view.setViewPadding(id,inset,inset,inset,inset)
            }
        }
        internal const val PREFIX="com.aether.radio.WIDGET."
        private var coverKey="";private var cover:android.graphics.Bitmap?=null
        private fun command(context:Context,name:String)=PendingIntent.getBroadcast(context,name.hashCode(),Intent(context,WidgetPlaybackReceiver::class.java).setAction(PREFIX+name),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        private fun views(context:Context,title:String,album:String,art:String,playing:Boolean,position:Long,duration:Long,local:Boolean,canPlay:Boolean,canStep:Boolean):RemoteViews {
            val view=RemoteViews(context.packageName,R.layout.widget_live_square)
            view.setTextViewText(R.id.widget_title,title);view.setTextViewText(R.id.widget_album,album)
            if(art!=coverKey){coverKey=art;cover=ArtworkStore.load(context,art,1024)?.asShared()}
            cover?.let{view.setImageViewBitmap(R.id.widget_cover,it)}?:view.setImageViewResource(R.id.widget_cover,R.drawable.icon_canvas_99)
            view.setImageViewResource(R.id.widget_play,if(playing)R.drawable.widget_pause else R.drawable.widget_play)
            view.setContentDescription(R.id.widget_play,if(playing)"暂停"else"播放")
            view.setBoolean(R.id.widget_play,"setEnabled",canPlay)
            view.setBoolean(R.id.widget_previous,"setEnabled",canStep);view.setBoolean(R.id.widget_next,"setEnabled",canStep)
            view.setOnClickPendingIntent(R.id.widget_play,command(context,"toggle"));view.setOnClickPendingIntent(R.id.widget_previous,command(context,"previous"));view.setOnClickPendingIntent(R.id.widget_next,command(context,"next"))
            val open=PendingIntent.getActivity(context,11,Intent(context,MainActivity::class.java).putExtra("show-player",true),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            for(id in listOf(R.id.widget_art_open,R.id.widget_title,R.id.widget_album))view.setOnClickPendingIntent(id,open)
            view.setTextViewText(R.id.widget_time,if(local)"${LocalMusic.time(position)} / ${LocalMusic.time(duration)}"else"FM · ${LocalMusic.time(position)}")
            view.setProgressBar(R.id.widget_progress,1000,if(local&&duration>0)(position*1000/duration).toInt().coerceIn(0,1000)else 0,false)
            return view
        }
        private fun ids(context:Context)=AppWidgetManager.getInstance(context).getAppWidgetIds(component(context))
        private var progressSecond=-1L
        private var progressSource:String?=null
        private var widgetPollSecond=-1L
        private var progressLayout=""
        private var widgetRenderedSecond=-1L
        fun updateAll(context:Context,live:LivePlayback)=render(context,live,true)
        private fun render(context:Context,live:LivePlayback,persist:Boolean){
            val s=live.session;val t=s.localTrack;val title=t?.title?:s.station?.name?:"漫频";val album=t?.album?.ifBlank{t.artist}?:"FM ${s.displayOrdinal?.toString()?.padStart(3,'0')?:"—"}"
            val art=t?.coverUri.orEmpty()
            if(persist)context.getSharedPreferences("aether-widget",Context.MODE_PRIVATE).edit().putString("title",title).putString("album",album).putString("cover",art).putLong("position",s.displayPositionMs).putLong("duration",s.localDurationMs).putBoolean("local",s.isLocal).putBoolean("can-play",s.canUse("play-toggle")).putBoolean("can-step",s.canUse("next")).apply()
            ids(context).forEach{id->AppWidgetManager.getInstance(context).updateAppWidget(id,responsive(context,id,title,album,art,s.playing,s.displayPositionMs,s.localDurationMs,s.isLocal,s.canUse("play-toggle"),s.canUse("next")))}
        }
        fun updateProgress(context:Context,live:LivePlayback){
            val tick=android.os.SystemClock.elapsedRealtime()/1000
            if(tick==widgetPollSecond)return
            widgetPollSecond=tick
            val s=live.session
            val currentWidgets=ids(context)
            val manager=AppWidgetManager.getInstance(context)
            @Suppress("DEPRECATION")
            val layout=currentWidgets.joinToString("|"){id->
                val options=manager.getAppWidgetOptions(id)
                listOf(id,options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH),options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT),options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH),options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT),options.getParcelableArrayList<android.util.SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)).joinToString(":")
            }
            val layoutChanged=layout!=progressLayout
            progressLayout=layout
            if(currentWidgets.isEmpty())return
            val second=s.displayPositionMs/1000
            val pausedRefresh=!s.playing&&tick-widgetRenderedSecond>=5
            if(!layoutChanged&&!pausedRefresh&&(!s.playing||second==progressSecond&&s.activeId==progressSource))return
            progressSecond=second;progressSource=s.activeId
            // One bounded complete tree retains click handlers and shared artwork;
            // a progress tick never writes preferences or replaces the player.
            render(context,live,false)
            widgetRenderedSecond=tick
        }
    }
}

/** Preserve the installed 2x2 component identity across upgrades. */
class CompactMusicWidget:MusicWidget()

/** Only widget-created PendingIntents may reach transport commands. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class WidgetPlaybackReceiver:android.content.BroadcastReceiver(){
    override fun onReceive(context:Context,intent:Intent){
        val action=intent.action?.removePrefix(MusicWidget.PREFIX)?:return
        if(intent.action?.startsWith(MusicWidget.PREFIX)!=true||action !in listOf("toggle","previous","next"))return
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
}
