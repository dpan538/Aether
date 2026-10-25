package com.aether.radio

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.Window
import android.widget.*

/** Designed library sheet: existing music first, one explicit device-import action. */
class MusicSourceDialog(context:Context,private val state:Session,private val albums:List<LocalAlbum>,private val starred:(String)->Boolean,private val openAlbum:(LocalAlbum)->Unit,private val importFiles:()->Unit,private val restoreBundled:(()->Unit)?=null):Dialog(context){
    private val ink=Color.rgb(25,25,25);private val gray=Color.rgb(120,120,120)
    private fun dp(v:Int)=(v*context.resources.displayMetrics.density).toInt()
    private fun label(value:String,size:Float,color:Int=ink)=TextView(context).apply{text=value;textSize=size;setTextColor(color);setLineSpacing(dp(3).toFloat(),1f)}
    private fun shape(color:Int,radius:Int)=GradientDrawable().apply{setColor(color);cornerRadius=dp(radius).toFloat()}
    init{
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        val root=LinearLayout(context).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(32),dp(22),dp(32),dp(28));background=shape(Color.rgb(247,247,245),18)}
        val header=LinearLayout(context).apply{gravity=Gravity.CENTER_VERTICAL}
        header.addView(label(if(state.language=="zh")"音乐"else"Music",30f).apply{typeface=Typeface.create("serif",Typeface.NORMAL)},LinearLayout.LayoutParams(0,-2,1f))
        header.addView(Button(context).apply{text="×";textSize=36f;setTextColor(ink);isAllCaps=false;contentDescription="关闭音乐菜单";background=shape(Color.TRANSPARENT,0);setOnClickListener{dismiss()}},LinearLayout.LayoutParams(dp(64),dp(64)))
        root.addView(header)
        if(state.musicError.isNotBlank())root.addView(label(state.musicError,14f,gray).apply{setPadding(0,dp(10),0,dp(14))})
        val scroll=ScrollView(context).apply{isVerticalScrollBarEnabled=false}
        val list=LinearLayout(context).apply{orientation=LinearLayout.VERTICAL;setPadding(0,dp(18),0,dp(22))}
        albums.forEach{album->
            val row=LinearLayout(context).apply{gravity=Gravity.CENTER_VERTICAL;minimumHeight=dp(132);setPadding(0,dp(12),0,dp(24));isClickable=true;isFocusable=true;contentDescription=album.title;setOnClickListener{dismiss();openAlbum(album)}}
            val cover=ArtworkStore.load(context,album.coverUri,256)
            row.addView(ImageView(context).apply{scaleType=ImageView.ScaleType.CENTER_CROP;setImageBitmap(cover);background=shape(Color.rgb(35,35,35),4);contentDescription=if(state.language=="zh")"专辑封面"else"Album cover"},LinearLayout.LayoutParams(dp(104),dp(104)))
            val info=LinearLayout(context).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(24),0,0,0)}
            info.addView(label((if(starred(album.key))"★  "else"")+album.title,25f).apply{typeface=Typeface.create("serif",Typeface.NORMAL);maxLines=2})
            if(album.artist.isNotBlank())info.addView(label(album.artist,16f).apply{setPadding(0,dp(10),0,0)})
            val count=album.tracks.size;val duration=LocalMusic.time(album.tracks.sumOf{it.durationMs})
            info.addView(label(if(state.language=="zh")"$count 首 · $duration"else"$count tracks · $duration",13f,gray).apply{setPadding(0,dp(12),0,0)})
            row.addView(info,LinearLayout.LayoutParams(0,-2,1f));list.addView(row)
        }
        if(albums.isEmpty())list.addView(label(if(state.language=="zh")"音乐库为空，导入音乐开始聆听。"else"Your library is empty. Import music to start listening.",17f,gray).apply{setPadding(0,dp(24),0,dp(28))})
        if(restoreBundled!=null)list.addView(Button(context).apply{text=if(state.language=="zh")"恢复随附专辑"else"Restore included album";textSize=14f;isAllCaps=false;setTextColor(gray);background=shape(Color.TRANSPARENT,0);gravity=Gravity.START or Gravity.CENTER_VERTICAL;setOnClickListener{dismiss();restoreBundled.invoke()}})
        scroll.addView(list);root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        root.addView(View(context).apply{setBackgroundColor(Color.rgb(220,220,218))},LinearLayout.LayoutParams(-1,dp(1)))
        root.addView(Button(context).apply{text="+   Import music from the device";textSize=17f;isAllCaps=false;setTextColor(ink);gravity=Gravity.START or Gravity.CENTER_VERTICAL;background=shape(Color.TRANSPARENT,0);setPadding(0,dp(20),0,0);minimumHeight=dp(76);setOnClickListener{dismiss();importFiles()}},LinearLayout.LayoutParams(-1,dp(80)))
        setContentView(root)
    }
    override fun show(){super.show();window?.apply{setBackgroundDrawableResource(android.R.color.transparent);attributes=attributes.apply{gravity=Gravity.TOP or Gravity.END;dimAmount=.6f;x=dp(34);y=dp(48)};val d=context.resources.displayMetrics;setLayout(minOf(dp(700),(d.widthPixels*.56f).toInt()),minOf(dp(260+albums.size*132),(d.heightPixels*.79f).toInt()));decorView.windowInsetsController?.hide(android.view.WindowInsets.Type.systemBars())}}
}
