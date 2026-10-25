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
class MusicLibraryDialog(context:Context,private val state:Session,private val album:List<LocalTrack>,private val position:()->Unit,private val record:LocalAlbum=LocalAlbum(LocalAlbums.BUNDLED,"Before the Rise","Dai Pan (潘岱)",album,true),private val starred:()->Boolean={false},private val toggleStar:()->Unit={},private val deleteAlbum:()->Unit={}):Dialog(context) {
    private val paper=Color.rgb(247,247,245);private val ink=Color.rgb(24,24,24)
    private val muted=Color.rgb(119,119,119);private val accent=Color.rgb(24,24,24);private val rule=Color.rgb(220,220,218)
    private val main=Handler(Looper.getMainLooper());private val rows=mutableListOf<Pair<LocalTrack,View>>()
    private lateinit var albumPlay:Button
    private var notesDialog:Dialog?=null
    private var optionsDialog:Dialog?=null
    private var renderedTrackId:String?="initial"
    private val cn get()=state.language=="zh"
    private fun dp(v:Int)=(v*context.resources.displayMetrics.density).toInt()
    private fun text(value:String,size:Float=16f,color:Int=ink)=TextView(context).apply{text=value;textSize=size;setTextColor(color);setLineSpacing(dp(3).toFloat(),1f);includeFontPadding=true}
    private fun shape(color:Int,radius:Int=10)=GradientDrawable().apply{setColor(color);cornerRadius=dp(radius).toFloat()}
    private fun button(label:String,id:Int,primary:Boolean=false,action:()->Unit)=Button(context).apply{
        text=label;this.id=id;textSize=18f;isAllCaps=false;setTextColor(if(primary)paper else accent);minHeight=dp(48)
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
        header.add(text(if(cn)"漫频   /   私人唱片" else "漫频   /   PRIVATE RECORDS",13f,muted).apply{letterSpacing=.13f},0,-2,1f)
        header.add(button("×",CLOSE){dismiss()}.apply{textSize=42f;contentDescription=if(cn)"关闭唱片，返回沉浸" else "Close album and return to listening"},68,68)
        root.add(header);root.space(22)
        val body=LinearLayout(context).apply{orientation=LinearLayout.HORIZONTAL}
        val left=LinearLayout(context).apply{orientation=LinearLayout.VERTICAL}
        val image=ImageView(context).apply{scaleType=ImageView.ScaleType.FIT_CENTER;contentDescription="${record.title}, album cover";setImageBitmap(ArtworkStore.load(context,record.coverUri,1600))}
        left.add(image,-1,430)
        left.space(28)
        left.add(AlbumTitleView(context).apply{text=record.title;setTextColor(ink)})
        left.space(32)
        val credits=LinearLayout(context).apply{orientation=LinearLayout.VERTICAL}
        if(record.artist.isNotBlank())credits.add(text(record.artist,23f,accent));credits.space(14)
        credits.add(text(if(record.bundled)if(cn)"11 首器乐作品 · 30 分 39 秒 · 离线唱片" else "11 instrumentals · 30 min 39 sec · Offline album" else "${album.size} ${if(cn)"首曲目"else"tracks"} · ${LocalMusic.time(album.sumOf{it.durationMs})}",14f,muted))
        left.add(credits)
        left.space(38)
        albumPlay=button(if(cn)"▶  播放专辑" else "▶  Play album",ALBUM_PLAY,true){if(state.isLocal&&state.localQueue==album)state.togglePlay()else state.selectLocal(album,0);update()}
        val heroActions=LinearLayout(context).apply{gravity=Gravity.CENTER_VERTICAL}
        heroActions.add(albumPlay,190,60);heroActions.space(14)
        if(record.bundled)heroActions.add(button(if(cn)"专辑手记 ↗" else "Album notes ↗",NOTES){showNotes()},180,60)
        left.add(heroActions);left.space(30)
        if(record.bundled)left.add(text(if(cn)"由梦入真，由幻归寂。" else "Through illusion, returning to the silence of dawn.",22f,muted).apply{typeface=Typeface.create("serif",Typeface.ITALIC)})
        val leftScroll=ScrollView(context).apply{isFillViewport=true;isVerticalScrollBarEnabled=false;addView(left)}
        body.add(leftScroll,430,-1);body.space(58)
        val right=LinearLayout(context).apply{orientation=LinearLayout.VERTICAL}
        right.add(text(if(record.bundled)if(cn)"梦境的十一种形态" else "Eleven forms of a dream" else if(cn)"曲目"else"Tracks",29f).apply{typeface=Typeface.create("serif",Typeface.NORMAL)})
        right.space(6)
        if(record.bundled)right.add(text("PHANTASMAGORIA: THE ABSOLUTE DISSOLUTION",11f,muted).apply{letterSpacing=.1f});right.space(18)
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
        scroll.addView(list);right.add(scroll,-1,0,1f)
        val footer=LinearLayout(context).apply{gravity=Gravity.END or Gravity.CENTER_VERTICAL;setPadding(0,dp(12),0,0)}
        footer.add(button("•••",MORE){showOptions()}.apply{textSize=24f;contentDescription=if(cn)"专辑选项"else"Album options"},64,64)
        right.add(footer);body.add(right,0,-1,1f)
        root.add(body,-1,0,1f)
        setContentView(root)
        setOnDismissListener{main.removeCallbacks(tick);notesDialog?.dismiss();optionsDialog?.dismiss();image.setImageDrawable(null)}
    }
    private fun showOptions(confirm:Boolean=false){
        optionsDialog?.dismiss()
        val sheet=LinearLayout(context).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(30),dp(20),dp(30),dp(28));background=shape(paper,18)}
        val header=LinearLayout(context).apply{gravity=Gravity.CENTER_VERTICAL}
        header.add(text(if(confirm)if(cn)"删除专辑？"else"Delete album?"else record.title,24f).apply{maxLines=2},0,-2,1f)
        header.add(button("×",OPTIONS_CLOSE){optionsDialog?.dismiss()}.apply{textSize=34f},64,64);sheet.add(header);sheet.space(16)
        if(confirm){
            val explanation=LinearLayout(context).apply{orientation=LinearLayout.VERTICAL}
            explanation.add(text(if(cn)"此专辑将从漫频音乐库中移除。\n设备中的原始音乐文件会保留。"else"This album will be removed from your 漫频 library.\nThe original music files on your device will be kept.",16f,muted).apply{id=DELETE_EXPLANATION;setLineSpacing(dp(7).toFloat(),1f);maxWidth=dp(390)})
            if(record.bundled){
                explanation.space(12)
                explanation.add(text(if(cn)"随附专辑可随时在音乐菜单中恢复。"else"You can restore the included album from the Music menu.",14f,muted).apply{id=DELETE_RESTORE;setLineSpacing(dp(5).toFloat(),1f);maxWidth=dp(390)})
            }
            sheet.add(explanation,390);sheet.space(26)
            sheet.add(button(if(cn)"删除专辑"else"Delete album",CONFIRM_DELETE,true){optionsDialog?.dismiss();deleteAlbum()},-1,56)
            sheet.add(button(if(cn)"取消"else"Cancel",CANCEL_DELETE){optionsDialog?.dismiss()},-1,56)
        }else{
            sheet.add(button(if(starred())if(cn)"☆   取消星标"else"☆   Unstar album"else if(cn)"★   星标专辑"else"★   Star album",STAR){toggleStar();optionsDialog?.dismiss()},-1,64)
            sheet.add(line(),-1,1)
            sheet.add(button(if(cn)"删除专辑"else"Delete album",DELETE){showOptions(true)},-1,64)
        }
        optionsDialog=Dialog(context).apply{requestWindowFeature(Window.FEATURE_NO_TITLE);setContentView(sheet);show();window?.apply{setBackgroundDrawableResource(android.R.color.transparent);attributes=attributes.apply{gravity=Gravity.BOTTOM or Gravity.END;dimAmount=.45f;x=dp(70);y=dp(52)};setLayout(minOf(dp(510),(context.resources.displayMetrics.widthPixels*.5f).toInt()),-2);decorView.windowInsetsController?.hide(android.view.WindowInsets.Type.systemBars())}}
    }
    private fun showNotes(){
        val content=LinearLayout(context).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(36),dp(28),dp(36),dp(28));setBackgroundColor(paper)}
        content.add(text("Before the Rise",34f).apply{typeface=Typeface.create("serif",Typeface.NORMAL)});content.add(text("Dai Pan (潘岱)",18f,accent));content.space(18)
        val manifest=LocalMusic.manifest(context)
        content.add(text(if(cn)"幻昼·万象归寂录" else manifest.getString("conceptTitle"),24f));content.space(12)
        val notes=manifest.optString(if(cn)"linerNotesZh" else "linerNotesEn",manifest.getString(if(cn)"conceptZh" else "conceptEn"))
        val heading=if(cn)"幻昼·万象归寂录" else manifest.getString("conceptTitle")
        content.add(text(if(notes.startsWith(heading))notes.substringAfter("\n\n",notes)else notes,17f))
        album.forEachIndexed{i,t->content.space(24);content.add(text("${i+1}. ${t.title}",21f));content.add(text(t.instruments,14f,accent));content.space(6);content.add(text(t.description,17f))}
        content.space(24);content.add(text("使用 Google AI 辅助生成 / Created with Google AI assistance",13f,muted))
        val scroll=ScrollView(context).apply{addView(content)}
        val sheet=LinearLayout(context).apply{orientation=LinearLayout.VERTICAL;background=shape(paper,18)}
        val header=LinearLayout(context).apply{gravity=Gravity.CENTER_VERTICAL;setPadding(dp(32),dp(12),dp(20),dp(8))}
        header.addView(text(if(cn)"专辑手记"else"Album notes",24f),LinearLayout.LayoutParams(0,-2,1f))
        header.addView(Button(context).apply{text="×";textSize=32f;isAllCaps=false;setTextColor(ink);background=shape(Color.TRANSPARENT,0);contentDescription=if(cn)"关闭专辑手记"else"Close album notes";setOnClickListener{notesDialog?.dismiss()}},LinearLayout.LayoutParams(dp(64),dp(64)))
        sheet.addView(header);sheet.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        notesDialog=Dialog(context).apply{requestWindowFeature(Window.FEATURE_NO_TITLE);setContentView(sheet);show();window?.setBackgroundDrawableResource(android.R.color.transparent);val d=context.resources.displayMetrics;window?.setLayout(minOf(dp(940),(d.widthPixels*.8f).toInt()),(d.heightPixels*.84f).toInt());window?.decorView?.windowInsetsController?.hide(android.view.WindowInsets.Type.systemBars())}
    }
    private val tick=object:Runnable{override fun run(){if(isShowing){update();main.postDelayed(this,500)}}}
    private fun update(){
        position();val t=state.localTrack
        if(t?.id!=renderedTrackId){renderedTrackId=t?.id;rows.forEach{(track,row)->row.background=shape(if(t?.id==track.id)Color.rgb(230,230,228)else Color.TRANSPARENT,6);row.isSelected=t?.id==track.id}}
        albumPlay.text=if(state.isLocal&&state.localQueue==album&&state.playing)if(cn)"Ⅱ  暂停" else "Ⅱ  Pause" else if(state.isLocal&&state.localQueue==album)if(cn)"▶  继续播放" else "▶  Resume" else if(cn)"▶  播放专辑" else "▶  Play album"
        albumPlay.isEnabled=state.enabled
    }
    override fun show(){super.show();window?.apply{setBackgroundDrawableResource(android.R.color.transparent);addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);attributes=attributes.apply{dimAmount=.72f};val d=context.resources.displayMetrics;setLayout(minOf(dp(1480),(d.widthPixels*.94f).toInt()),(d.heightPixels*.94f).toInt());decorView.windowInsetsController?.hide(android.view.WindowInsets.Type.systemBars())};update();main.post(tick)}
    companion object {const val DELETE_EXPLANATION=0x7116;const val DELETE_RESTORE=0x7117;const val MORE=0x7110;const val STAR=0x7111;const val DELETE=0x7112;const val CONFIRM_DELETE=0x7113;const val CANCEL_DELETE=0x7114;const val OPTIONS_CLOSE=0x7115;const val CLOSE=0x7100;const val TRACK_LIST=0x7107;const val NOTES=0x7108;const val ALBUM_PLAY=0x7109;const val TRACK_BASE=0x7200}
}
