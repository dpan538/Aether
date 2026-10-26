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
    override fun dispatchTouchEvent(event:android.view.MotionEvent):Boolean{
        if(event.actionMasked==android.view.MotionEvent.ACTION_DOWN)surface.externalInputActive=true
        return try{super.dispatchTouchEvent(event)}finally{if(event.actionMasked in listOf(android.view.MotionEvent.ACTION_UP,android.view.MotionEvent.ACTION_CANCEL))surface.externalInputActive=false}
    }
    override fun onDetachedFromWindow(){dismissLanguageMenu();surface.externalInputActive=false;main.removeCallbacksAndMessages(null);retained=null;super.onDetachedFromWindow()}
    private var editor:EditText?=null
    private var status:TextView?=null
    private var count:TextView?=null
    private var send:Button?=null
    private var updateProgress:ProgressBar?=null
    private var updatePercent:TextView?=null
    private var diagnostic:TextView?=null
    private var languagePopup:PopupWindow?=null
    val languageMenuView:View? get()=languagePopup?.contentView
    private var languageValue:TextView?=null
    fun openLanguageMenu(){
        if(!state.enabled||surface.refreshing||state.view!="settings"||state.updateOpen)return
        if(languagePopup!=null){dismissLanguageMenu();return}
        val anchor=findViewWithTag<View>("settings:menu:0")?:return
        val content=LinearLayout(context).apply{orientation=VERTICAL;setPadding(dp(4),dp(4),dp(4),dp(4));background=background(if(surface.frontlight>.5f)0xffeeecd8.toInt()else 0xffc7cabb.toInt(),true)}
        val popup=PopupWindow(content,dp(220),LayoutParams.WRAP_CONTENT,true).apply{elevation=dp(3).toFloat();animationStyle=0;setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));isOutsideTouchable=true}
        listOf("zh" to "中文","en" to "English").forEach{(code,label)->
            val selected=state.language==code
            val option=LinearLayout(context).apply{tag="settings:language:$code";gravity=Gravity.CENTER_VERTICAL;setPadding(dp(12),0,dp(12),0);isClickable=true;isFocusable=true;contentDescription=label;isSelected=selected;if(selected)background=background(0xff34483a.toInt())
                setOnClickListener{if(state.enabled&&!surface.refreshing){surface.haptics.confirm();popup.dismiss();state.language=code;state.menu=null;state.changed()}}}
            val color=if(selected)0xffeeecd8.toInt()else ink
            option.addView(text(label,15f,color),LayoutParams(0,-2,1f));if(selected)option.addView(text("✓",17f,color))
            content.addView(option,LayoutParams(-1,dp(48)))
        }
        languagePopup=popup
        popup.setOnDismissListener{if(languagePopup===popup){languagePopup=null;surface.externalInputActive=false;languageValue?.text=words("中文  ⌄","English  ⌄")}}
        main.postDelayed({if(languagePopup===popup&&isAttachedToWindow&&visibility==View.VISIBLE&&state.view=="settings"&&state.settingsPage=="menu"&&!state.updateOpen){languageValue?.text=words("中文  ⌃","English  ⌃");surface.externalInputActive=true;popup.showAsDropDown(anchor,0,dp(3),Gravity.END)}},if(surface.reducedMotion)0 else 120)
    }
    private fun dismissLanguageMenu(){
        val popup=languagePopup;languagePopup=null;popup?.dismiss();surface.externalInputActive=false;languageValue?.text=words("中文  ⌄","English  ⌄")
    }
    private val zh get()=state.language=="zh"
    private val ink=Color.rgb(43,56,46)
    private val secondary=0xff687561.toInt()
    private var inkScale=1f
    private var geometryReady=false
    private fun dp(n:Int)=kotlin.math.round(n*inkScale).toInt().coerceAtLeast(if(n>0)1 else 0)
    private fun TextView.inkSize(size:Float){setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,size*inkScale)}
    private fun words(cn:String,en:String)=if(zh)cn else en
    private fun text(value:String,size:Float,color:Int=ink)=TextView(context).apply{
        text=value;inkSize(size);typeface=surface.painter.font(false,400);setTextColor(color)
        includeFontPadding=false;setLineSpacing(dp(4).toFloat(),1f)
    }
    private fun background(fill:Int,stroke:Boolean=false)=GradientDrawable().apply{setColor(fill);cornerRadius=dp(4).toFloat();if(stroke)setStroke(dp(1),0xff7d8978.toInt())}
    private fun button(value:String,key:String="",click:()->Unit)=Button(context).apply{
        text=value;tag=key;inkSize(14f);typeface=surface.painter.font(false,600);isAllCaps=false
        setSingleLine(true);ellipsize=android.text.TextUtils.TruncateAt.END;contentDescription=value;setTextColor(ink);background=background(Color.TRANSPARENT,true);setPadding(dp(12),0,dp(12),0)
        setOnClickListener{if(state.enabled&&!surface.refreshing){surface.haptics.press();click()}}
    }
    init{orientation=VERTICAL;importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_YES}
    fun alignWithSurface(){
        visibility=if(!state.reference&&surface.modernVisible&&state.view=="settings")View.VISIBLE else View.GONE
        translationX=surface.modernOffsetPixels
        if(visibility!=View.VISIBLE||translationX!=0f||!state.enabled)dismissLanguageMenu()
    }
    fun refresh(){
        if(state.settingsPage=="widget")state.settingsPage="about"
        // The dropdown and updater retain this native ink renderer.
        alignWithSurface()
        if(visibility!=View.VISIBLE){
            main.removeCallbacks(applyPage);pendingSignature=signature
            if(editor?.hasFocus()==true){clearFocus();(context.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).hideSoftInputFromWindow(windowToken,0)}
            return
        }
        val update=if(state.updateOpen)"${state.updateUi.phase}:${state.updateUi.message}:${state.updateUi.detail}"else ""
        val next="${state.settingsPage}:${state.language}:${state.updateOpen}:$update"
        if(next==signature&&pendingSignature!=next){main.removeCallbacks(applyPage);pendingSignature=next}
        if(next!=signature){
            if(signature.isBlank()||surface.reducedMotion){signature=next;pendingSignature=next;build()}
            else if(next!=pendingSignature){pendingSignature=next;main.removeCallbacks(applyPage);main.postDelayed(applyPage,120)}
        }
        editor?.let{it.isEnabled=state.enabled&&!feedback.busy;if(it.text.toString()!=feedback.draft)it.setText(feedback.draft)}
        count?.text="${feedback.draft.codePointCount(0,feedback.draft.length)} / 1000"
        send?.apply{isEnabled=state.enabled&&!feedback.busy&&feedback.draft.isNotBlank();text=words(if(feedback.busy)"正在提交…"else"提交反馈",if(feedback.busy)"Sending…"else"Send feedback");alpha=if(isEnabled)1f else .45f}
        status?.text=when(feedback.status){"received"->words("反馈已收到，谢谢。","Feedback received. Thank you.");"sending"->words("正在发送，请稍候。","Sending your feedback…");"service_unconfigured"->words("反馈服务暂未启用，草稿已保留。","Feedback is not configured yet. Your draft is saved.");"rate_limited"->words("提交较频繁，请稍后重试；草稿已保留。","Please retry later. Your draft is saved.");""->"";else->words("暂时无法提交，草稿已保留，请重试。","Could not send. Your draft is saved; please retry.")}
        updateProgress?.progress=state.updateUi.progress
        updatePercent?.text="${state.updateUi.progress}%"
        fun enabled(v:View){if(v!==editor&&v!==send)v.isEnabled=state.enabled;if(v is android.view.ViewGroup)for(i in 0 until v.childCount)enabled(v.getChildAt(i))}
        enabled(this)
    }
    fun place(){
        if(!surface.assetsReady||surface.width==0)return
        val r=surface.painter.scene(surface.painter.name(state.light)).flattened().first{it.cls=="directory-screen"}.r
        val scale=minOf(surface.width/1704f,surface.height/1136f);val ox=(surface.width-1704*scale)/2;val oy=(surface.height-1136*scale)/2
        if(!geometryReady||kotlin.math.abs(inkScale-scale)>.001f){geometryReady=true;inkScale=scale;signature="";pendingSignature="";retained=null;setPadding(dp(20),dp(14),dp(20),dp(14));build()}
        val width=(r.width()*scale).toInt();val height=(r.height()*scale).toInt();val left=(ox+r.left*scale).toInt();val top=(oy+r.top*scale).toInt()
        val old=layoutParams as? FrameLayout.LayoutParams
        if(old?.width==width&&old.height==height&&old.leftMargin==left&&old.topMargin==top)return
        layoutParams=FrameLayout.LayoutParams(width,height).apply{leftMargin=left;topMargin=top}
    }
    private fun page(value:String){dismissLanguageMenu();state.menu=null;state.settingsPage=value;state.changed()}
    private fun scrollBody():LinearLayout{
        val body=LinearLayout(context).apply{orientation=VERTICAL}
        addView(ScrollView(context).apply{isFillViewport=true;isVerticalScrollBarEnabled=false;addView(body)},LayoutParams(-1,0,1f))
        return body
    }
    private fun LinearLayout.paragraph(value:String,size:Float=14f,muted:Boolean=false,top:Int=14){addView(text(value,size,if(muted)secondary else ink),LayoutParams(-1,-2).apply{topMargin=dp(top)})}
    private fun row(label:String,value:String,key:String,selected:Boolean=false,click:()->Unit){
        val row=LinearLayout(context).apply{
            tag=key;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(6),0,dp(6),0);isClickable=true;isFocusable=true;contentDescription=label
            if(selected)background=background(0x16565e4f)
            setOnClickListener{if(state.enabled&&!surface.refreshing){surface.haptics.press();click()}}
        }
        if(key.startsWith("settings:menu:"))row.addView(text("0${key.substringAfterLast(':').toInt()+1}",12f,secondary),LayoutParams(dp(38),-2))
        row.addView(text(label,15f),LayoutParams(0,-2,1f));val tail=text(value,15f);if(key=="settings:menu:0")languageValue=tail;row.addView(tail)
        addView(row,LayoutParams(-1,0,1f));addView(View(context).apply{setBackgroundColor(0x33929d89)},LayoutParams(-1,dp(1)))
    }
    private fun build(){
        dismissLanguageMenu();languageValue=null;removeAllViews();editor=null;status=null;count=null;send=null;updateProgress=null;updatePercent=null;diagnostic=null
        // The Canvas beneath us owns the original ink material and its continuous frontlight.
        setBackgroundColor(Color.TRANSPARENT)
        val current=if(state.updateOpen)"update"else state.settingsPage
        val header=LinearLayout(context).apply{gravity=Gravity.CENTER_VERTICAL}
        if(current!="menu")header.addView(button("‹","settings:back"){
            if(state.updateOpen){state.updateOpen=false;state.changed()}else page(if(current=="haptics")"about"else"menu")
        }.apply{contentDescription=words("返回","Back")},LayoutParams(dp(48),dp(38)).apply{marginEnd=dp(14)})
        val title=when(current){"about"->words("关于漫频","About 漫频");"feedback"->words("意见反馈","Feedback");"update"->words("检查更新","Check updates");"haptics"->words("触感检测","Haptic feedback");else->words("漫频 / 设置","漫频 / SETTINGS")}
        header.addView(text(title,16f).apply{typeface=surface.painter.font(false,600)},LayoutParams(0,-2,1f))
        if(current in listOf("about","update"))header.addView(text(BuildConfig.VERSION_NAME,12f,secondary))
        addView(header,LayoutParams(-1,dp(38)));addView(View(context).apply{setBackgroundColor(0xff929d89.toInt())},LayoutParams(-1,dp(1)).apply{topMargin=dp(8);bottomMargin=dp(10)})
        when(current){
            "update"->buildUpdate()
            "about"->{
                val body=scrollBody()
                body.paragraph(words("不设边界，漫游频率。","No boundaries. Just frequencies."),17f,top=8)
                body.paragraph(words("为小米 Pad 9 Pro Max 打造的原生聆听体验。\n在电台与本地音乐之间，保留声音，也留出安静的画面。","A native listening experience for Xiaomi Pad 9 Pro Max.\nRadio and local music, with space to simply listen."))
                body.paragraph(words("无需账户 · 无广告 · 无第三方遥测\n电台来自 Radio Browser；可播放状态随音源与网络变化。","No account · No ads · No third-party telemetry\nRadio directory: Radio Browser. Availability varies by source and network."),13f,true)
                diagnostic=text("",13f,secondary);body.addView(diagnostic,LayoutParams(-1,-2))
                val actions=LinearLayout(context)
                actions.addView(button(words("触感检测","Haptic feedback"),"settings:haptics"){page("haptics")},LayoutParams(0,dp(44),1f).apply{marginEnd=dp(12)})
                actions.addView(button(words("GitHub 仓库","GitHub repository"),"settings:github"){
                    val intent=android.content.Intent(android.content.Intent.ACTION_VIEW,android.net.Uri.parse("https://github.com/dpan538/Aether")).addCategory(android.content.Intent.CATEGORY_BROWSABLE).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching{context.startActivity(intent)}.onFailure{diagnostic?.text=words("无法打开浏览器：github.com/dpan538/Aether","Unable to open browser: github.com/dpan538/Aether")}
                },LayoutParams(0,dp(44),1f))
                addView(actions,LayoutParams(-1,dp(44)).apply{topMargin=dp(12)})
            }
            "haptics"->{
                val body=scrollBody()
                body.paragraph(words("旋钮每跨过一档，触发一次马达与机械音效。","Each encoder detent requests one motor pulse and mechanical click."),17f,top=8)
                diagnostic=text(surface.haptics.description(zh),14f);body.addView(diagnostic,LayoutParams(-1,-2).apply{topMargin=dp(18)})
                body.paragraph(words("请同时开启系统的触感反馈与触摸提示音。若设备没有振动马达，应用无法用软件产生真实震动。","Enable system haptic feedback and touch sounds. A device without a vibration motor cannot produce physical haptics."),13f,true)
                addView(button(words("测试触感","Test haptics"),"settings:test-haptics"){surface.haptics.testPulse();diagnostic?.text=surface.haptics.description(zh)},LayoutParams(dp(180),dp(44)).apply{gravity=Gravity.END;topMargin=dp(12)})
            }
            "feedback"->{
                editor=EditText(context).apply{setText(feedback.draft);inkSize(14f);typeface=surface.painter.font(false,400);setTextColor(ink);setHintTextColor(0xff6e7b67.toInt());hint=words("描述问题、发生步骤或修改建议…","Describe the issue, steps or suggestion…");gravity=Gravity.TOP;setPadding(dp(12),dp(10),dp(12),dp(10));background=background(0x16565e4f,true);inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES;filters=arrayOf(android.text.InputFilter.LengthFilter(1000));isSingleLine=false;contentDescription=words("反馈内容","Feedback message");addTextChangedListener(object:TextWatcher{override fun beforeTextChanged(s:CharSequence?,a:Int,c:Int,f:Int){};override fun onTextChanged(s:CharSequence?,a:Int,b:Int,c:Int){feedback.edit(s.toString())};override fun afterTextChanged(e:Editable?){}})}
                addView(editor,LayoutParams(-1,0,1f))
                val row=LinearLayout(context).apply{gravity=Gravity.CENTER_VERTICAL};row.addView(text(words("反馈将公开到 GitHub，请勿填写隐私信息。","Posted publicly to GitHub. Please omit private information."),12f),LayoutParams(0,-2,1f));count=text("",12f);row.addView(count);addView(row,LayoutParams(-1,dp(30)))
                status=text("",14f).apply{maxLines=2};addView(status,LayoutParams(-1,dp(35)))
                send=button(words("提交反馈","Send feedback"),"settings:send-feedback"){editor?.clearFocus();(context.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).hideSoftInputFromWindow(windowToken,0);feedback.submit()};addView(send,LayoutParams(dp(180),dp(44)).apply{gravity=Gravity.END})
            }
            else->{
                listOf(words("界面语言","Interface language"),words("检查更新","Check updates"),words("关于","About"),words("意见反馈","Feedback")).forEachIndexed{i,label->
                    row(label,if(i==0)words("中文  ⌄","English  ⌄")else "›","settings:menu:$i"){
                        when(i){0->openLanguageMenu();1->surface.activate("check-updates");2->page("about");else->page("feedback")}
                    }
                }
            }
        }
    }
    private fun buildUpdate(){
        val ui=state.updateUi
        val title=if(zh)when(ui.phase){UpdatePhase.CHECKING->"正在检查仓库版本";UpdatePhase.DOWNLOADING->"正在下载更新";UpdatePhase.VERIFYING->"正在校验安装包与签名";UpdatePhase.AVAILABLE->"发现新版本";UpdatePhase.READY->"已验证 · 可安装";UpdatePhase.CURRENT->if(ui.message.startsWith("No APK"))"仓库尚无 APK 版本"else"暂无更高版本";UpdatePhase.ERROR->"更新暂不可用";else->"应用内更新"}else when(ui.phase){UpdatePhase.CHECKING->"Checking repository";UpdatePhase.DOWNLOADING->"Downloading update";UpdatePhase.VERIFYING->"Verifying package and signature";UpdatePhase.AVAILABLE->"Update available";UpdatePhase.READY->"Verified · Ready to install";UpdatePhase.CURRENT->if(ui.message.startsWith("No APK"))"No APK release yet"else"No newer version";UpdatePhase.ERROR->"Update unavailable";else->"In-app updates"}
        val detail=if(!zh)ui.detail else when{
            ui.message.startsWith("Installation permission")->"尚未获得系统安装权限。已校验文件仍保留，可再次尝试安装。"
            ui.message.startsWith("Installer could")->"未能打开系统安装器。已校验文件仍保留。"
            ui.detail.startsWith("Check failed")->"暂时无法连接仓库。已保留的安装包仍可安装。"
            ui.message=="Download paused"->"下载已暂停，已有数据会保留。服务器支持时可从断点继续。"
            ui.phase==UpdatePhase.ERROR->when{ui.detail.contains("sign",true)->"签名校验未通过，已停止安装。";ui.detail.contains("digest",true)||ui.detail.contains("sha256",true)->"文件完整性校验未通过，请重新下载。";else->"无法完成更新请求，请检查网络后重试。"}
            else->ui.detail
        }
        val body=scrollBody();body.paragraph(title,17f,top=10)
        if(detail.isNotBlank())body.paragraph(detail,14f,top=18)
        if(ui.phase==UpdatePhase.DOWNLOADING){
            updateProgress=ProgressBar(context,null,android.R.attr.progressBarStyleHorizontal).apply{max=100;progress=ui.progress;progressTintList=android.content.res.ColorStateList.valueOf(ink);progressBackgroundTintList=android.content.res.ColorStateList.valueOf(0x33738169)}
            body.addView(updateProgress,LayoutParams(-1,dp(6)).apply{topMargin=dp(24)})
            updatePercent=text("${ui.progress}%",14f,secondary);body.addView(updatePercent,LayoutParams(-1,-2).apply{topMargin=dp(8)})
        }
        body.paragraph(words("同一已验证安装包会保留并复用。安装由 Android 系统确认，收藏与设置保留。","Verified packages are retained and reused. Android confirms installation; favorites and settings are preserved."),14f,true,20)
        val action=when(ui.phase){UpdatePhase.CHECKING,UpdatePhase.DOWNLOADING,UpdatePhase.VERIFYING->"cancel";UpdatePhase.AVAILABLE->"download";UpdatePhase.READY->"install";else->"check"}
        val label=when(action){"cancel"->words("暂停","Pause");"download"->words("下载更新","Download update");"install"->words("安装已验证包","Install verified APK");else->words("再次检查","Check again")}
        addView(button(label,"update:$action"){state.onUpdateAction(action)},LayoutParams(dp(220),dp(44)).apply{gravity=Gravity.END;topMargin=dp(12)})
    }
}
