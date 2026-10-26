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
                val preview=views(context,if(style=="compact")"square"else style,sample?.title?:"漫频",sample?.album?:"选择音乐，开始聆听",sample?.coverUri.orEmpty(),false,0,sample?.durationMs?:0,sample!=null,true,true)
                if(runCatching{manager.setWidgetPreview(component(context,style),AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN,preview)}.getOrDefault(false))preferences.edit().putInt("version:$style",BuildConfig.VERSION_CODE).apply()
            }
        }
        private fun component(context:Context,style:String)=ComponentName(context,when(style){"compact"->CompactMusicWidget::class.java;"strip"->StripMusicWidget::class.java;else->MusicWidget::class.java})
        private fun style(context:Context,id:Int)=when(AppWidgetManager.getInstance(context).getAppWidgetInfo(id)?.provider?.className){CompactMusicWidget::class.java.name->"compact";StripMusicWidget::class.java.name->"strip";else->"card"}
        private fun layout(style:String)=when(style){"square"->R.layout.widget_live_square;"compact"->R.layout.widget_live_compact;"strip"->R.layout.widget_live_strip;else->R.layout.widget_live_card}
        private fun responsive(context:Context,id:Int,title:String,album:String,art:String,playing:Boolean,position:Long,duration:Long,local:Boolean,canPlay:Boolean,canStep:Boolean):RemoteViews{
            val style=style(context,id)
            val manager=AppWidgetManager.getInstance(context)
            val options=manager.getAppWidgetOptions(id)
            @Suppress("DEPRECATION") val exact=options.getParcelableArrayList<android.util.SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES).orEmpty().filter{it.width>0&&it.height>0}.distinct().take(8)
            fun sized(width:Float,height:Float):RemoteViews{
                val w=width.coerceAtLeast(180f);val h=height.coerceAtLeast(96f)
                val presentation=when{w/h<1.45f&&h>=170->"square";w>=320&&w/h>=2.7f->"strip";w>=320&&h>=180->"card";else->"compact"}
                return views(context,presentation,title,album,art,playing,position,duration,local,canPlay,canStep).also{geometry(context,it,presentation,w,h)}
            }
            if(exact.size==1){val size=exact.first();return sized(size.width,size.height)}
            if(exact.size>1)return RemoteViews(exact.associateWith{sized(it.width,it.height)})
            val fallback=when(style){"compact"->248f to 248f;"strip"->480f to 104f;else->440f to 224f}
            val minW=options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,fallback.first.toInt()).toFloat()
            val maxW=options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH,minW.toInt()).toFloat()
            val minH=options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,fallback.second.toInt()).toFloat()
            val maxH=options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,minH.toInt()).toFloat()
            // Legacy launchers may omit OPTION_APPWIDGET_SIZES. Their orientation-specific
            // ranges still describe the real allocation; no fixed card-size ceiling is applied.
            val landscape=context.resources.configuration.orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE
            val width=if(landscape)maxW else minW;val height=if(landscape)minH else maxH
            return sized(width,height)
        }
        private fun geometry(context:Context,view:RemoteViews,style:String,w:Float,h:Float){
            val scale=minOf(w/(if(style=="square")248f else if(style=="card")440f else if(style=="strip")480f else 248f),h/(if(style=="square")248f else if(style=="card")224f else if(style=="strip")104f else 144f)).coerceIn(.75f,3f)
            val unit=android.util.TypedValue.COMPLEX_UNIT_DIP
            fun size(id:Int,width:Float?=null,height:Float?=null){if(width!=null)view.setViewLayoutWidth(id,width,unit);if(height!=null)view.setViewLayoutHeight(id,height,unit)}
            fun text(id:Int,sp:Float){view.setTextViewTextSize(id,android.util.TypedValue.COMPLEX_UNIT_SP,sp*scale)}
            val pad=(if(style=="card")16 else 12)*scale
            val px=(pad*context.resources.displayMetrics.density).toInt()
            if(style=="square"){
                view.setViewPadding(R.id.widget_metadata,px,px,px,px)
                text(R.id.widget_title,14f);text(R.id.widget_album,11f);text(R.id.widget_time,11f)
                size(R.id.widget_controls,height=48f*scale);size(R.id.widget_footer,height=19f*scale);size(R.id.widget_time,height=16f*scale);size(R.id.widget_progress,height=3f*scale)
                size(R.id.widget_art_open,height=(h-133f*scale).coerceAtLeast(h*.30f))
                for(id in listOf(R.id.widget_previous,R.id.widget_play,R.id.widget_next)){size(id,48f*scale,48f*scale);val inset=(14*scale*context.resources.displayMetrics.density).toInt();view.setViewPadding(id,inset,inset,inset,inset)}
                return
            }
            view.setViewPadding(R.id.widget_root,px,px,px,px)
            val gap=if(style=="card")16f else 12f
            view.setViewLayoutMargin(R.id.widget_metadata,RemoteViews.MARGIN_START,gap*scale,unit)
            text(R.id.widget_title,if(style=="card")19f else if(style=="strip")16f else 14f);text(R.id.widget_album,if(style=="card")12f else 11f);text(R.id.widget_time,11f)
            size(R.id.widget_progress,height=3f*scale)
            view.setViewLayoutMargin(R.id.widget_album,RemoteViews.MARGIN_TOP,(if(style=="card")7f else if(style=="strip")5f else 4f)*scale,unit)
            if(style!="card")view.setViewLayoutMargin(R.id.widget_progress,RemoteViews.MARGIN_TOP,(if(style=="compact")10f else 8f)*scale,unit)
            val cardArt=if(style=="card")minOf((w-pad*2-16f*scale)*.52f,h-pad*2-19f*scale).coerceAtLeast(80f)else 0f
            // Keep large transport symbols and hit regions centred in the metadata column.
            val button=if(style=="card")minOf(60f*scale,(w-pad*2-16f*scale-cardArt)/3f)else 48f*scale
            for(id in listOf(R.id.widget_previous,R.id.widget_play,R.id.widget_next)){
                if(style=="card"&&id!=R.id.widget_play)size(id,height=button)
                else if(style!="compact")size(id,button,button)else size(id,height=button)
                val inset=((if(style=="card")button*.20f else 14*scale)*context.resources.displayMetrics.density).toInt();view.setViewPadding(id,inset,inset,inset,inset)
            }
            if(style=="card"){
                val footer=19f*scale;size(R.id.widget_footer,height=footer)
                size(R.id.widget_time,height=16f*scale)
                size(R.id.widget_controls,height=button)
                view.setViewLayoutMargin(R.id.widget_controls,RemoteViews.MARGIN_TOP,8f*scale,unit)
                view.setViewLayoutMargin(R.id.widget_controls,RemoteViews.MARGIN_START,0f,unit)
                view.setViewLayoutMargin(R.id.widget_controls,RemoteViews.MARGIN_BOTTOM,0f,unit)
                val art=cardArt
                size(R.id.widget_cover,art,art);size(R.id.widget_main,height=art)
            }else if(style=="compact"){
                size(R.id.widget_controls,height=button)
                view.setViewLayoutMargin(R.id.widget_controls,RemoteViews.MARGIN_TOP,8f*scale,unit)
                val top=h-pad*2-button-21f*scale
                val art=minOf(w*.32f,top*.90f).coerceAtLeast(38f*scale)
                size(R.id.widget_cover,art,art);size(R.id.widget_time,width=art)
                view.setViewLayoutMargin(R.id.widget_time,RemoteViews.MARGIN_END,12f*scale,unit)
            }else {
                val available=h-pad*2-31f*scale
                val art=maxOf(52f*scale,available*.85f).coerceAtMost(available.coerceAtLeast(40f))
                size(R.id.widget_cover,art,art)
            }
        }
        internal const val PREFIX="com.aether.radio.WIDGET."
        private var coverKey="";private var cover:android.graphics.Bitmap?=null
        private fun command(context:Context,name:String)=PendingIntent.getBroadcast(context,name.hashCode(),Intent(context,WidgetPlaybackReceiver::class.java).setAction(PREFIX+name),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        private fun views(context:Context,style:String,title:String,album:String,art:String,playing:Boolean,position:Long,duration:Long,local:Boolean,canPlay:Boolean,canStep:Boolean):RemoteViews {
            val view=RemoteViews(context.packageName,layout(style))
            view.setTextViewText(R.id.widget_title,title);view.setTextViewText(R.id.widget_album,album)
            if(art!=coverKey){coverKey=art;cover=ArtworkStore.load(context,art,1024)?.asShared()}
            cover?.let{view.setImageViewBitmap(R.id.widget_cover,it)}?:view.setImageViewResource(R.id.widget_cover,R.drawable.icon_canvas_99)
            view.setImageViewResource(R.id.widget_play,if(playing)R.drawable.widget_pause else R.drawable.widget_play)
            view.setContentDescription(R.id.widget_play,if(playing)"暂停"else"播放")
            view.setBoolean(R.id.widget_play,"setEnabled",canPlay)
            view.setBoolean(R.id.widget_previous,"setEnabled",canStep);view.setBoolean(R.id.widget_next,"setEnabled",canStep)
            view.setOnClickPendingIntent(R.id.widget_play,command(context,"toggle"));view.setOnClickPendingIntent(R.id.widget_previous,command(context,"previous"));view.setOnClickPendingIntent(R.id.widget_next,command(context,"next"))
            val open=PendingIntent.getActivity(context,11,Intent(context,MainActivity::class.java).putExtra("show-player",true),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            for(id in listOf(if(style=="square")R.id.widget_art_open else R.id.widget_cover,R.id.widget_title,R.id.widget_album))view.setOnClickPendingIntent(id,open)
            progress(view,position,duration,local,style);return view
        }
        private fun progress(view:RemoteViews,position:Long,duration:Long,local:Boolean,style:String){
            view.setTextViewText(R.id.widget_time,if(local&&style!="compact")"${LocalMusic.time(position)} / ${LocalMusic.time(duration)}" else if(local)LocalMusic.time(position)else "FM · ${LocalMusic.time(position)}")
            view.setProgressBar(R.id.widget_progress,1000,if(local&&duration>0)(position*1000/duration).toInt().coerceIn(0,1000)else 0,false)
        }
        private fun ids(context:Context)=listOf("card","compact","strip").flatMap{AppWidgetManager.getInstance(context).getAppWidgetIds(component(context,it)).toList()}.toIntArray()
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
            val ids=ids(context);if(ids.isEmpty())return
            ids.forEach{id->AppWidgetManager.getInstance(context).updateAppWidget(id,responsive(context,id,title,album,art,s.playing,s.displayPositionMs,s.localDurationMs,s.isLocal,s.canUse("play-toggle"),s.canUse("next")))}
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
            // Reapply one complete, bounded action tree per visible second. This
            // retains child click handlers even on hosts that mishandle partial updates.
            // New or resized widgets also update while paused if host callbacks are delayed.
            // The unchanged cover is immutable shared memory; no preferences are written.
            render(context,live,false)
            widgetRenderedSecond=tick
        }
    }
}

class CompactMusicWidget:MusicWidget()
class StripMusicWidget:MusicWidget()

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
