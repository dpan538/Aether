package com.aether.radio

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.*

/** Dedicated native pages contained by the existing ink screen, including the real feedback editor. */
class InkSettingsView(context:Context,private val state:Session,private val surface:AetherView,private val feedback:FeedbackClient):LinearLayout(context){
    private var signature=""
    private var pendingSignature=""
    private val main=android.os.Handler(android.os.Looper.getMainLooper())
    private var retained:android.graphics.Picture?=null
    private var changedAt=0L
    private val applyPage=Runnable{
        if(visibility==View.VISIBLE){
            retained=null
            if(width>0&&height>0&&!surface.reducedMotion)retained=android.graphics.Picture().also{picture->super.draw(picture.beginRecording(width,height));picture.endRecording()}
            signature=pendingSignature;build();changedAt=android.os.SystemClock.uptimeMillis();refresh();invalidate()
        }
    }
    override fun draw(canvas:android.graphics.Canvas){
        if(surface.refreshing&&!surface.reducedMotion){
            val f=(surface.refreshElapsed/1800f).coerceIn(0f,1f)
            val contrast=when{f<.22f->.7f;f<.44f->1.3f;f<.64f->.55f;f<.8f->.75f;else->.7f}
            val invert=f in .22f..<.44f||f in .64f..<.8f
            val k=if(invert)-contrast else contrast*(if(f in .44f..<.64f)1.25f else 1f)
            val bias=if(invert)255f*(1+contrast)/2 else 255*(1-contrast)/2
            val paint=android.graphics.Paint().apply{colorFilter=android.graphics.ColorMatrixColorFilter(floatArrayOf(.2126f*k,.7152f*k,.0722f*k,0f,bias,.2126f*k,.7152f*k,.0722f*k,0f,bias,.2126f*k,.7152f*k,.0722f*k,0f,bias,0f,0f,0f,1f,0f))}
            canvas.saveLayer(0f,0f,width.toFloat(),height.toFloat(),paint);super.draw(canvas);canvas.restore();postInvalidateDelayed(32)
        }else super.draw(canvas)
    }
    override fun dispatchDraw(canvas:android.graphics.Canvas){
        super.dispatchDraw(canvas)
        retained?.let{picture->val elapsed=android.os.SystemClock.uptimeMillis()-changedAt
            if(elapsed<400&&!surface.reducedMotion){canvas.saveLayerAlpha(0f,0f,width.toFloat(),height.toFloat(),(40*(1f-elapsed/400f)).toInt().coerceIn(0,40));canvas.drawPicture(picture);canvas.restore();postInvalidateDelayed(24)}else retained=null
        }
    }
    override fun onDetachedFromWindow(){main.removeCallbacksAndMessages(null);retained=null;super.onDetachedFromWindow()}
    private var editor:EditText?=null;private var status:TextView?=null;private var count:TextView?=null;private var send:Button?=null
    private val zh get()=state.language=="zh"
    private val ink=Color.rgb(43,56,46)
    private fun dp(n:Int)=(n*resources.displayMetrics.density).toInt()
    private fun words(cn:String,en:String)=if(zh)cn else en
    private fun text(value:String,size:Float,color:Int=ink)=TextView(context).apply{text=value;textSize=size;setTextColor(color);includeFontPadding=false;setLineSpacing(dp(4).toFloat(),1f)}
    private fun background(fill:Int,stroke:Boolean=false)=GradientDrawable().apply{setColor(fill);cornerRadius=dp(4).toFloat();if(stroke)setStroke(dp(1),0xff7d8978.toInt())}
    private fun button(value:String,click:()->Unit)=Button(context).apply{text=value;textSize=16f;isAllCaps=false;setTextColor(ink);background=background(Color.TRANSPARENT,true);setPadding(dp(12),0,dp(12),0);setOnClickListener{if(state.enabled){surface.haptics.press();click()}}}
    init{orientation=VERTICAL;setPadding(dp(18),dp(14),dp(18),dp(14));importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_YES}
    fun refresh(){
        visibility=if(!state.reference&&state.page==0&&state.view=="settings"&&state.menu==null&&!state.updateOpen)View.VISIBLE else View.GONE
        if(visibility!=View.VISIBLE){if(editor?.hasFocus()==true){clearFocus();(context.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).hideSoftInputFromWindow(windowToken,0)};return}
        val next="${state.settingsPage}:${state.language}:${state.light}"
        if(next==signature&&pendingSignature!=next){main.removeCallbacks(applyPage);pendingSignature=next}
        if(next!=signature){
            if(signature.isBlank()||surface.reducedMotion){signature=next;pendingSignature=next;build()}
            else if(next!=pendingSignature){pendingSignature=next;main.removeCallbacks(applyPage);main.postDelayed(applyPage,120)}
        }
        editor?.let{it.isEnabled=state.enabled&&!feedback.busy;if(it.text.toString()!=feedback.draft)it.setText(feedback.draft)}
        count?.text="${feedback.draft.codePointCount(0,feedback.draft.length)} / 1000"
        send?.apply{isEnabled=state.enabled&&!feedback.busy&&feedback.draft.isNotBlank();text=words(if(feedback.busy)"正在提交…"else"提交反馈",if(feedback.busy)"Sending…"else"Send feedback");alpha=if(isEnabled)1f else .45f}
        status?.text=when(feedback.status){"received"->words("反馈已收到，谢谢。","Feedback received. Thank you.");"sending"->words("正在发送，请稍候。","Sending your feedback…");"service_unconfigured"->words("反馈服务暂未启用，草稿已保留。","Feedback is not configured yet. Your draft is saved.");"rate_limited"->words("提交较频繁，请稍后重试；草稿已保留。","Please retry later. Your draft is saved.");""->"";else->words("暂时无法提交，草稿已保留，请重试。","Could not send. Your draft is saved; please retry.")}
        fun enabled(v:View){if(v!==editor&&v!==send)v.isEnabled=state.enabled;if(v is android.view.ViewGroup)for(i in 0 until v.childCount)enabled(v.getChildAt(i))};enabled(this)
    }
    fun place(){
        if(!surface.assetsReady||surface.width==0)return
        val r=surface.painter.scene(surface.painter.name(state.light)).flattened().first{it.cls=="directory-screen"}.r
        val scale=minOf(surface.width/1704f,surface.height/1136f);val ox=(surface.width-1704*scale)/2;val oy=(surface.height-1136*scale)/2
        val width=(r.width()*scale).toInt();val height=(r.height()*scale).toInt();val left=(ox+r.left*scale).toInt();val top=(oy+r.top*scale).toInt()
        val old=layoutParams as? FrameLayout.LayoutParams
        if(old?.width==width&&old.height==height&&old.leftMargin==left&&old.topMargin==top)return
        layoutParams=FrameLayout.LayoutParams(width,height).apply{leftMargin=left;topMargin=top}
    }
    private fun build(){
        removeAllViews();editor=null;status=null;count=null;send=null
        background=background(if(state.light)0xffe6e7df.toInt()else 0xffc2c8b8.toInt())
        val header=LinearLayout(context).apply{gravity=Gravity.CENTER_VERTICAL}
        if(state.settingsPage!="menu")header.addView(button("‹"){state.settingsPage="menu";state.changed()},LayoutParams(dp(48),dp(38)).apply{marginEnd=dp(14)})
        val title=when(state.settingsPage){"about"->words("关于漫频","About 漫频");"feedback"->words("意见反馈","Feedback");else->words("漫频 / 设置","漫频 / SETTINGS")}
        header.addView(text(title,19f).apply{typeface=Typeface.create("sans-serif",Typeface.BOLD)},LayoutParams(0,-2,1f));header.addView(text(BuildConfig.VERSION_NAME,11f,0xff687561.toInt()))
        addView(header,LayoutParams(-1,dp(38)));addView(View(context).apply{setBackgroundColor(0xff929d89.toInt())},LayoutParams(-1,dp(1)).apply{topMargin=dp(8);bottomMargin=dp(10)})
        when(state.settingsPage){
            "about"->{
                addView(text(words("不设边界，漫游频率。","No boundaries. Just frequencies."),26f).apply{setPadding(0,dp(14),0,dp(22))})
                addView(text(words("为小米 Pad 9 Pro Max 打造的原生聆听体验。\n在电台与本地音乐之间，保留声音，也留出安静的画面。","A native listening experience for Xiaomi Pad 9 Pro Max.\nRadio and local music, with space to simply listen."),16f))
                addView(text(words("无需账户 · 无广告 · 无第三方遥测\n电台来自 Radio Browser；可播放状态随音源与网络变化。","No account · No ads · No third-party telemetry\nRadio directory: Radio Browser. Availability varies by source and network."),13f,0xff687561.toInt()).apply{setPadding(0,dp(24),0,0)})
            }
            "feedback"->{
                editor=EditText(context).apply{setText(feedback.draft);textSize=16f;setTextColor(ink);setHintTextColor(0xff6e7b67.toInt());hint=words("描述问题、发生步骤或修改建议…","Describe the issue, steps or suggestion…");gravity=Gravity.TOP;setPadding(dp(12),dp(10),dp(12),dp(10));background=background(0x16565e4f,true);inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES;filters=arrayOf(android.text.InputFilter.LengthFilter(1000));isSingleLine=false;contentDescription=words("反馈内容","Feedback message");addTextChangedListener(object:TextWatcher{override fun beforeTextChanged(s:CharSequence?,a:Int,c:Int,f:Int){};override fun onTextChanged(s:CharSequence?,a:Int,b:Int,c:Int){feedback.edit(s.toString())};override fun afterTextChanged(e:Editable?){}})}
                addView(editor,LayoutParams(-1,0,1f))
                val row=LinearLayout(context).apply{gravity=Gravity.CENTER_VERTICAL};row.addView(text(words("反馈将公开到 GitHub，请勿填写隐私信息。","Posted publicly to GitHub. Please omit private information."),11f),LayoutParams(0,-2,1f));count=text("",11f);row.addView(count);addView(row,LayoutParams(-1,dp(30)))
                status=text("",13f).apply{maxLines=2};addView(status,LayoutParams(-1,dp(35)))
                send=button(words("提交反馈","Send feedback")){editor?.clearFocus();(context.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).hideSoftInputFromWindow(windowToken,0);feedback.submit()};addView(send,LayoutParams(dp(180),dp(44)).apply{gravity=Gravity.END})
            }
            else->{
                listOf(words("界面语言","Interface language"),words("检查更新","Check updates"),words("关于","About"),words("意见反馈","Feedback")).forEachIndexed{i,label->
                    val row=LinearLayout(context).apply{gravity=Gravity.CENTER_VERTICAL;setPadding(dp(6),0,dp(6),0);isClickable=true;isFocusable=true;contentDescription=label;setOnClickListener{if(state.enabled){surface.haptics.press();when(i){0->surface.activate("menu:ui-language");1->surface.activate("check-updates");2->state.settingsPage="about";else->state.settingsPage="feedback"};state.changed()}}}
                    row.addView(text("0${i+1}",11f,0xff7e8a74.toInt()),LayoutParams(dp(38),-2));row.addView(text(label,17f),LayoutParams(0,-2,1f));row.addView(text(if(i==0)words("中文  ⌄","English  ⌄")else if(i==1)"↻" else "›",17f));addView(row,LayoutParams(-1,0,1f));if(i<3)addView(View(context).apply{setBackgroundColor(0x33929d89)},LayoutParams(-1,dp(1)))
                }
            }
        }
    }
}
