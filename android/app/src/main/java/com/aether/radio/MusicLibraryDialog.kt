package com.aether.radio

import android.app.Dialog
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.*

/** Editorial album sheet: original artwork, authored notes, real media controls. */
class MusicLibraryDialog(context:Context,private val state:Session,private val album:List<LocalTrack>,private val position:()->Unit):Dialog(context) {
    private val paper=Color.rgb(247,247,245);private val ink=Color.rgb(24,24,24)
    private val muted=Color.rgb(119,119,119);private val accent=Color.rgb(24,24,24);private val rule=Color.rgb(220,220,218)
    private val main=Handler(Looper.getMainLooper());private val rows=mutableListOf<Pair<LocalTrack,View>>()
    private lateinit var albumPlay:Button
    private var notesDialog:Dialog?=null
    private var renderedTrackId:String?="initial"
    private val cn get()=state.language=="zh"
    private fun dp(v:Int)=(v*context.resources.displayMetrics.density).toInt()
    private fun text(value:String,size:Float=16f,color:Int=ink)=TextView(context).apply{text=value;textSize=size;setTextColor(color);setLineSpacing(dp(3).toFloat(),1f);includeFontPadding=true}
    private fun shape(color:Int,radius:Int=10)=GradientDrawable().apply{setColor(color);cornerRadius=dp(radius).toFloat()}
    private fun button(label:String,id:Int,primary:Boolean=false,action:()->Unit)=Button(context).apply{
        text=label;this.id=id;textSize=16f;isAllCaps=false;setTextColor(if(primary)paper else accent);minHeight=dp(48)
        background=shape(if(primary)accent else Color.TRANSPARENT,26);setPadding(dp(18),dp(5),dp(18),dp(5));stateListAnimator=null
        setOnClickListener{if(state.enabled)action()}
    }
    private fun LinearLayout.space(size:Int){addView(Space(context),LinearLayout.LayoutParams(dp(size),dp(size)))}
    private fun LinearLayout.add(view:View,w:Int=-1,h:Int=-2,weight:Float=0f){addView(view,LinearLayout.LayoutParams(if(w>0)dp(w) else w,if(h>0)dp(h) else h,weight))}
    private fun line()=View(context).apply{setBackgroundColor(rule)}
    init {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        val root=LinearLayout(context).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(40),dp(24),dp(40),dp(22));background=shape(paper,18)}
        val header=LinearLayout(context).apply{gravity=Gravity.CENTER_VERTICAL}
        header.add(text(if(cn)"AETHER   /   私人唱片" else "AETHER   /   PRIVATE RECORDS",13f,muted).apply{letterSpacing=.13f},0,-2,1f)
        header.add(button("×",CLOSE){dismiss()}.apply{textSize=42f;contentDescription=if(cn)"关闭唱片，返回沉浸" else "Close album and return to listening"},68,68)
        root.add(header);root.space(22)
        val body=LinearLayout(context).apply{orientation=LinearLayout.HORIZONTAL}
        val left=LinearLayout(context).apply{orientation=LinearLayout.VERTICAL}
        val image=ImageView(context).apply{scaleType=ImageView.ScaleType.FIT_CENTER;contentDescription="Before the Rise — Dai Pan (潘岱), album cover";setImageBitmap(context.assets.open("${LocalMusic.ROOT}/cover.png").use{BitmapFactory.decodeStream(it)})}
        left.add(image,-1,430)
        left.add(Space(context),-1,0,1f)
        left.space(26)
        left.add(text("Before the Rise",42f).apply{typeface=Typeface.create("serif",Typeface.NORMAL);letterSpacing=-.025f})
        left.add(text("幻昼 · 万象归寂录",20f).apply{letterSpacing=.04f});left.space(7)
        left.add(text("Dai Pan (潘岱)",19f,accent));left.space(8)
        left.add(text(if(cn)"11 首器乐作品 · 30 分 39 秒 · 离线唱片" else "11 instrumentals · 30 min 39 sec · Offline album",13f,muted))
        left.space(18)
        albumPlay=button(if(cn)"▶  播放专辑" else "▶  Play album",ALBUM_PLAY,true){if(state.isLocal&&state.localQueue==album)state.togglePlay()else state.selectLocal(album,0);update()}
        val heroActions=LinearLayout(context).apply{gravity=Gravity.CENTER_VERTICAL}
        heroActions.add(albumPlay,166,50);heroActions.space(14)
        heroActions.add(button(if(cn)"专辑手记 ↗" else "Album notes ↗",NOTES){showNotes()},166,50)
        left.add(heroActions);left.space(16)
        left.add(text(if(cn)"由梦入真，由幻归寂。" else "Through illusion, returning to the silence of dawn.",16f,muted).apply{typeface=Typeface.create("serif",Typeface.ITALIC)})
        body.add(left,430,-1);body.space(58)
        val right=LinearLayout(context).apply{orientation=LinearLayout.VERTICAL}
        right.add(text(if(cn)"梦境的十一种形态" else "Eleven forms of a dream",29f).apply{typeface=Typeface.create("serif",Typeface.NORMAL)})
        right.space(6)
        right.add(text("PHANTASMAGORIA: THE ABSOLUTE DISSOLUTION",11f,muted).apply{letterSpacing=.1f});right.space(18)
        val scroll=ScrollView(context).apply{id=TRACK_LIST;isFillViewport=false;isVerticalScrollBarEnabled=false}
        val list=LinearLayout(context).apply{orientation=LinearLayout.VERTICAL}
        fun addTracks(queue:List<LocalTrack>){queue.forEachIndexed{i,t->
            val row=LinearLayout(context).apply{
                id=TRACK_BASE+i
                gravity=Gravity.CENTER_VERTICAL;setPadding(dp(10),dp(10),dp(10),dp(10));minimumHeight=dp(68)
                isClickable=true;isFocusable=true;contentDescription="${i+1}. ${t.title}, ${LocalMusic.time(t.durationMs)}"
                setOnClickListener{if(state.enabled){state.selectLocal(queue,i);update()}}
            }
            row.add(text((i+1).toString().padStart(2,'0'),14f,muted),38)
            val identity=LinearLayout(context).apply{orientation=LinearLayout.VERTICAL}
            identity.add(text(t.title,18f).apply{maxLines=2})
            val subtitle=t.instruments.ifBlank{t.artist.ifBlank{t.album}}
            if(subtitle.isNotBlank())identity.add(text(subtitle,12f,muted).apply{maxLines=2})
            row.add(identity,0,-2,1f);row.space(10)
            row.add(text(LocalMusic.time(t.durationMs),14f,muted).apply{gravity=Gravity.END},48)
            list.add(row);rows.add(t to row);list.add(line(),-1,1)
        }}
        addTracks(album)
        scroll.addView(list);right.add(scroll,-1,0,1f);body.add(right,0,-1,1f)
        root.add(body,-1,0,1f)
        setContentView(root)
        setOnDismissListener{main.removeCallbacks(tick);notesDialog?.dismiss();image.setImageDrawable(null)}
    }
    private fun showNotes(){
        val content=LinearLayout(context).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(36),dp(28),dp(36),dp(28));setBackgroundColor(paper)}
        content.add(text("Before the Rise",34f).apply{typeface=Typeface.create("serif",Typeface.NORMAL)});content.add(text("Dai Pan (潘岱)",18f,accent));content.space(18)
        val manifest=LocalMusic.manifest(context)
        content.add(text(if(cn)"幻昼·万象归寂录" else manifest.getString("conceptTitle"),24f));content.space(12)
        content.add(text(manifest.optString(if(cn)"linerNotesZh" else "linerNotesEn",manifest.getString(if(cn)"conceptZh" else "conceptEn")),17f))
        album.forEachIndexed{i,t->content.space(24);content.add(text("${i+1}. ${t.title}",21f));content.add(text(t.instruments,14f,accent));content.space(6);content.add(text(t.description,17f))}
        content.space(24);content.add(text("使用 Google AI 辅助生成 / Created with Google AI assistance",13f,muted))
        val scroll=ScrollView(context).apply{addView(content)}
        notesDialog=android.app.AlertDialog.Builder(android.view.ContextThemeWrapper(context,R.style.MusicSheetTheme)).setView(scroll).setPositiveButton(if(cn)"完成" else "Done"){d,_->d.dismiss()}.create().also{it.show();val d=context.resources.displayMetrics;it.window?.setLayout(minOf(dp(940),(d.widthPixels*.8f).toInt()),(d.heightPixels*.84f).toInt())}
    }
    private val tick=object:Runnable{override fun run(){if(isShowing){update();main.postDelayed(this,500)}}}
    private fun update(){
        position();val t=state.localTrack
        if(t?.id!=renderedTrackId){renderedTrackId=t?.id;rows.forEach{(track,row)->row.background=shape(if(t?.id==track.id)Color.rgb(230,230,228)else Color.TRANSPARENT,6);row.isSelected=t?.id==track.id}}
        albumPlay.text=if(state.isLocal&&state.localQueue==album&&state.playing)if(cn)"Ⅱ  暂停" else "Ⅱ  Pause" else if(state.isLocal&&state.localQueue==album)if(cn)"▶  继续播放" else "▶  Resume" else if(cn)"▶  播放专辑" else "▶  Play album"
        albumPlay.isEnabled=state.enabled
    }
    override fun show(){super.show();window?.apply{setBackgroundDrawableResource(android.R.color.transparent);addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);attributes=attributes.apply{dimAmount=.72f};val d=context.resources.displayMetrics;setLayout(minOf(dp(1480),(d.widthPixels*.94f).toInt()),(d.heightPixels*.94f).toInt());decorView.windowInsetsController?.hide(android.view.WindowInsets.Type.systemBars())};update();main.post(tick)}
    companion object {const val CLOSE=0x7100;const val TRACK_LIST=0x7107;const val NOTES=0x7108;const val ALBUM_PLAY=0x7109;const val TRACK_BASE=0x7200}
}
