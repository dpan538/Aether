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

/** Real RemoteViews widget; its commands are acknowledged by the same media-session service. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
open class MusicWidget:AppWidgetProvider() {
    override fun onUpdate(context:Context,manager:AppWidgetManager,ids:IntArray){
        publishPreview(context)
        PlaybackService.current?.let{updateAll(context,it.live);return}
        val p=context.getSharedPreferences("aether-widget",Context.MODE_PRIVATE)
        ids.forEach{id->manager.updateAppWidget(id,responsive(context,id,p.getString("title","漫频")!!,p.getString("album","选择音乐，开始聆听")!!,p.getString("cover","")!!,false,p.getLong("position",0),p.getLong("duration",0),p.getBoolean("local",false),p.getBoolean("can-play",false),p.getBoolean("can-step",false)))}
    }
    override fun onAppWidgetOptionsChanged(context:Context,manager:AppWidgetManager,id:Int,options:Bundle){onUpdate(context,manager,intArrayOf(id))}
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
        fun requestPin(context:Context,style:String="card"):Boolean{
            val manager=AppWidgetManager.getInstance(context)
            if(!manager.isRequestPinAppWidgetSupported)return false
            publishPreview(context)
            return runCatching{manager.requestPinAppWidget(component(context,style),null,null)}.getOrDefault(false)
        }
        fun publishPreview(context:Context){
            if(android.os.Build.VERSION.SDK_INT<35)return
            val preferences=context.getSharedPreferences("aether-widget-preview",Context.MODE_PRIVATE)
            val manager=AppWidgetManager.getInstance(context)
            val now=System.currentTimeMillis()
            val sample=LocalMusic.bundled(context).firstOrNull()
            for(style in listOf("compact","strip","card")){
                val info=manager.getInstalledProvidersForPackage(context.packageName,null).firstOrNull{it.provider==component(context,style)}
                if(preferences.getInt("version:$style",-1)==BuildConfig.VERSION_CODE&&info!=null&&info.generatedPreviewCategories and AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN!=0)continue
                if(now-preferences.getLong("attempt:$style",0) in 0..1_800_000)continue
                preferences.edit().putLong("attempt:$style",now).apply()
                val preview=views(context,style,sample?.title?:"漫频",sample?.album?:"选择音乐，开始聆听",sample?.coverUri.orEmpty(),false,0,sample?.durationMs?:0,sample!=null,true,true)
                if(runCatching{manager.setWidgetPreview(component(context,style),AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN,preview)}.getOrDefault(false))preferences.edit().putInt("version:$style",BuildConfig.VERSION_CODE).apply()
            }
        }
        private fun component(context:Context,style:String)=ComponentName(context,when(style){"compact"->CompactMusicWidget::class.java;"strip"->StripMusicWidget::class.java;else->MusicWidget::class.java})
        private fun style(context:Context,id:Int)=when(AppWidgetManager.getInstance(context).getAppWidgetInfo(id)?.provider?.className){CompactMusicWidget::class.java.name->"compact";StripMusicWidget::class.java.name->"strip";else->"card"}
        private fun layout(style:String)=when(style){"compact"->R.layout.music_widget_compact;"strip"->R.layout.music_widget_strip;else->R.layout.music_widget}
        private fun responsive(context:Context,id:Int,title:String,album:String,art:String,playing:Boolean,position:Long,duration:Long,local:Boolean,canPlay:Boolean,canStep:Boolean,progressOnly:Boolean=false):RemoteViews{
            val style=style(context,id)
            val sizes=when(style){"compact"->listOf(android.util.SizeF(224f,144f),android.util.SizeF(248f,144f),android.util.SizeF(280f,152f));"strip"->listOf(android.util.SizeF(320f,104f),android.util.SizeF(480f,104f),android.util.SizeF(560f,112f));else->listOf(android.util.SizeF(280f,224f),android.util.SizeF(440f,224f),android.util.SizeF(480f,240f))}
            val options=AppWidgetManager.getInstance(context).getAppWidgetOptions(id)
            @Suppress("DEPRECATION") val exact=options.getParcelableArrayList<android.util.SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES).orEmpty().filter{it.width>0&&it.height>0}.take(8)
            return RemoteViews((if(exact.isEmpty())sizes else exact).associateWith{size->
                val presentation=if(style=="card"&&size.width<360f)"compact"else style
                (if(progressOnly)RemoteViews(context.packageName,layout(presentation)).also{progress(it,position,duration,local,presentation)}else views(context,presentation,title,album,art,playing,position,duration,local,canPlay,canStep)).apply{
                    // Some tablet launchers allocate much larger cells than requested. Bound the
                    // actual card too, so the artwork and controls never drift into distant corners.
                    val max=sizes.last();setViewLayoutWidth(R.id.widget_root,minOf(size.width,max.width),android.util.TypedValue.COMPLEX_UNIT_DIP)
                    setViewLayoutHeight(R.id.widget_root,minOf(size.height,max.height),android.util.TypedValue.COMPLEX_UNIT_DIP)
                    if(presentation=="card"){
                        val artwork=minOf(size.width*.40f,minOf(size.height,max.height)-51f,188f)
                        setViewLayoutWidth(R.id.widget_cover,artwork,android.util.TypedValue.COMPLEX_UNIT_DIP)
                        setViewLayoutHeight(R.id.widget_cover,artwork,android.util.TypedValue.COMPLEX_UNIT_DIP)
                        setViewLayoutHeight(R.id.widget_main,artwork,android.util.TypedValue.COMPLEX_UNIT_DIP)
                    }
                }
            })
        }
        private const val PREFIX="com.aether.radio.WIDGET."
        private var coverKey="";private var cover:android.graphics.Bitmap?=null
        private fun command(context:Context,name:String)=PendingIntent.getBroadcast(context,name.hashCode(),Intent(context,MusicWidget::class.java).setAction(PREFIX+name),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        private fun views(context:Context,style:String,title:String,album:String,art:String,playing:Boolean,position:Long,duration:Long,local:Boolean,canPlay:Boolean,canStep:Boolean):RemoteViews {
            val view=RemoteViews(context.packageName,layout(style))
            view.setTextViewText(R.id.widget_title,title);view.setTextViewText(R.id.widget_album,album)
            if(art!=coverKey){coverKey=art;cover=ArtworkStore.load(context,art,768)}
            cover?.let{view.setImageViewBitmap(R.id.widget_cover,it)}?:view.setImageViewResource(R.id.widget_cover,R.drawable.icon_canvas_99)
            view.setImageViewResource(R.id.widget_play,if(playing)R.drawable.widget_pause else R.drawable.widget_play)
            view.setContentDescription(R.id.widget_play,if(playing)"暂停"else"播放")
            view.setBoolean(R.id.widget_play,"setEnabled",canPlay)
            view.setBoolean(R.id.widget_previous,"setEnabled",canStep);view.setBoolean(R.id.widget_next,"setEnabled",canStep)
            view.setOnClickPendingIntent(R.id.widget_play,command(context,"toggle"));view.setOnClickPendingIntent(R.id.widget_previous,command(context,"previous"));view.setOnClickPendingIntent(R.id.widget_next,command(context,"next"))
            view.setOnClickPendingIntent(R.id.widget_root,PendingIntent.getActivity(context,11,Intent(context,MainActivity::class.java).putExtra("show-player",true),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            progress(view,position,duration,local,style);return view
        }
        private fun progress(view:RemoteViews,position:Long,duration:Long,local:Boolean,style:String){
            view.setTextViewText(R.id.widget_time,if(local&&style!="compact")"${LocalMusic.time(position)} / ${LocalMusic.time(duration)}" else if(local)LocalMusic.time(position)else "FM · ${LocalMusic.time(position)}")
            view.setProgressBar(R.id.widget_progress,1000,if(local&&duration>0)(position*1000/duration).toInt().coerceIn(0,1000)else 0,false)
        }
        private fun ids(context:Context)=listOf("card","compact","strip").flatMap{AppWidgetManager.getInstance(context).getAppWidgetIds(component(context,it)).toList()}.toIntArray()
        fun updateAll(context:Context,live:LivePlayback){
            val s=live.session;val t=s.localTrack;val title=t?.title?:s.station?.name?:"漫频";val album=t?.album?.ifBlank{t.artist}?:"FM ${s.displayOrdinal?.toString()?.padStart(3,'0')?:"—"}"
            val art=t?.coverUri.orEmpty()
            context.getSharedPreferences("aether-widget",Context.MODE_PRIVATE).edit().putString("title",title).putString("album",album).putString("cover",art).putLong("position",s.displayPositionMs).putLong("duration",s.localDurationMs).putBoolean("local",s.isLocal).putBoolean("can-play",s.canUse("play-toggle")).putBoolean("can-step",s.canUse("next")).apply()
            val ids=ids(context);if(ids.isEmpty())return
            ids.forEach{id->AppWidgetManager.getInstance(context).updateAppWidget(id,responsive(context,id,title,album,art,s.playing,s.displayPositionMs,s.localDurationMs,s.isLocal,s.canUse("play-toggle"),s.canUse("next")))}
        }
        fun updateProgress(context:Context,live:LivePlayback){
            val s=live.session;if(!s.playing)return
            val ids=ids(context);if(ids.isEmpty())return
            val t=s.localTrack
            ids.forEach{id->AppWidgetManager.getInstance(context).partiallyUpdateAppWidget(id,responsive(context,id,t?.title?:s.station?.name?:"漫频",t?.album.orEmpty(),t?.coverUri.orEmpty(),true,s.displayPositionMs,s.localDurationMs,s.isLocal,s.canUse("play-toggle"),s.canUse("next"),true))}
        }
    }
}

class CompactMusicWidget:MusicWidget()
class StripMusicWidget:MusicWidget()
