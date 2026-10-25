package com.aether.radio

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.provider.Settings
import android.view.*
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeProvider
import android.view.animation.PathInterpolator
import kotlin.math.*

class AetherView(context:Context,val state:Session):View(context) {
    val painter=ScenePainter(context,state)
    val haptics=ControlHaptics(this)
    val immersivePlayback=ImmersivePlayback(context,state){invalidate()}
    private var selectorTouch=false
    private var seekBaseMs=0L
    private var seekRadians=0.0
    private var discStartRotation=0f
    private var discLastAngle:Double?=null
    var frontlight=if(state.light)1f else 0f
    var refreshing=false;private set
    var reducedMotion=false
    var search:()->Unit={}
    var musicSources:()->Unit={}
    var touchTrace=false
    var onFirstFrame:()->Unit={}
    var onResourcesReady:()->Unit={}
    var assetsReady=false;private set
    var preparationMillis=0L;private set
    private var preparationStarted=false
    private var preparationError:Throwable?=null
    private var released=false
    private var firstFrame=false
    var committedRefresh=true;private set
    var committedFrontlight=-1f;private set
    var committedPage=-1;private set
    var committedView="";private set
    var committedOffset=0f;private set
    private var ignoreGesture=false
    private val displayLists=android.util.LruCache<String,Picture>(4)
    private class SceneLayer(val node:RenderNode,var source:Picture?=null,var width:Int=0,var height:Int=0)
    private val nativeLayers=mutableMapOf<String,SceneLayer>()
    private val inkFrames=mutableMapOf<Boolean,InkResponse<Picture>>()
    private var inkRects:List<RectF>?=null
    private var inkSpectrum:SpectrumFrame?=null
    private var spectrumSampleAt=0L
    private var inkNavigation=""
    private var partialNavigations=0
    private var navigationChangedAt=0L
    private var revision=0L
    val contentRevision get()=revision
    var committedRevision=-1L;private set
    private var active=true
    private val physical=PathInterpolator(.16f,.72f,.2f,1f)
    private val lightCurve=PathInterpolator(.22f,.08f,.24f,1f)
    private var lightAnimator:ValueAnimator?=null
    private var pageAnimator:ValueAnimator?=null
    private var pressAnimator:ValueAnimator?=null
    private var pausedAt=0L
    private var refreshStart=0L
    private var refreshGeneration=0
    private var offset=0f
    private var downX=0f;private var downY=0f;private var lastY=0f;private var lastAngle:Double?=null
    private var action="";private var moved=false;private var consumedGesture=false;private var verticalVolume=false
    private var focusAction="volume"
    private var settingsStartScroll=0f
    private val rotaryGate=DetentGate(.57,180,.2)
    private val modeGate=DetentGate(.66,180,.2)
    private val liveVolume=RelativeEncoder()
    private val volumeGate=DetentGate(.3,140,.12)
    private val verticalGate=DetentGate(18.0,140,9.0)
    private val wheelGate=DetentGate(96.0,180,48.0)
    private val volumeWheelGate=DetentGate(120.0,160,48.0)
    private val disc=DiscRenderer(context){print,bmp,phase->post {if(released)bmp.recycle()else {if(print)painter.mainDisc=bmp else {painter.smallDisc=bmp;painter.smallDiscPhase=phase};displayLists.evictAll();invalidate();if(print)discSmall()}}}
    private var phaseMillis=0L
    private var phaseTick=SystemClock.uptimeMillis()
    private var previousMotion=state.motion
    var testTime:Long?=null
    private var lastSmallPhase=-1000L
    private val p=Paint(Paint.ANTI_ALIAS_FLAG)
    private val pointerAnimators=mutableMapOf<String,ValueAnimator>()
    private val sx get()=min(width/1704f,height/1136f)
    private val ox get()=(width-1704*sx)/2
    private val oy get()=(height-1136*sx)/2
    override fun onAttachedToWindow(){
        super.onAttachedToWindow()
        if(!preparationStarted){
            preparationStarted=true;val started=SystemClock.uptimeMillis()
            painter.prepare{error->post{
                if(!released){preparationMillis=SystemClock.uptimeMillis()-started;preparationError=error;assetsReady=error==null
                    if(assetsReady){onResourcesReady();immersivePlayback.syncArtwork();postDelayed({if(!released&&active){if(immersivePlayback.needsFallback)disc.render(true,3801)else discSmall()}},250)}
                    invalidate()
                }
            }}
        }
    }
    init {
        isFocusable=true;isFocusableInTouchMode=true;isHapticFeedbackEnabled=true;importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_YES
        state.onChange={if(state.motion&&!previousMotion)phaseTick=SystemClock.uptimeMillis();previousMotion=state.motion;revision++;displayLists.evictAll();sync();invalidate();sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)}
        sync(false)
        viewTreeObserver.addOnPreDrawListener {
            if(assetsReady){
                val refreshAtDraw=refreshing;val lightAtDraw=frontlight;val pageAtDraw=state.page;val viewAtDraw=state.view;val offsetAtDraw=offset;val revisionAtDraw=revision
                val first=!firstFrame;firstFrame=true
                viewTreeObserver.registerFrameCommitCallback {post{
                    if(!released){committedRefresh=refreshAtDraw;committedFrontlight=lightAtDraw;committedPage=pageAtDraw;committedView=viewAtDraw;committedOffset=offsetAtDraw;committedRevision=revisionAtDraw;if(first)onFirstFrame()}
                }}
            }
            true
        }
    }
    private fun discSmall(){disc.render(false,966,if(state.motion)painter.phase else 0f)}
    fun sync(animate:Boolean=true){
        fun pointer(key:String,target:Float,duration:Long,get:()->Float,set:(Float)->Unit){
            if(abs(target-get())<.01f)return
            pointerAnimators.remove(key)?.cancel()
            if(!animate||reducedMotion||(key=="volume"&&action=="volume"&&!state.reference)){set(target);return}
            pointerAnimators[key]=ValueAnimator.ofFloat(get(),target).apply{this.duration=duration;interpolator=physical;addUpdateListener{set(it.animatedValue as Float);invalidate()};start()}
        }
        pointer("tune",state.tuningAngle,230,{painter.tuningAngle},{painter.tuningAngle=it})
        pointer("mode",-60f+state.mode*40,230,{painter.modeAngle},{painter.modeAngle=it})
        pointer("volume",-135f+state.volume*9,180,{painter.volumeAngle},{painter.volumeAngle=it})
    }
    fun setLight(value:Boolean){
        state.light=value&&state.powered
        lightAnimator?.cancel()
        val target=if(state.light)1f else 0f
        if(abs(frontlight-target)<.0001f){frontlight=target;state.changed();return}
        if(reducedMotion){frontlight=target;state.changed();return}
        lightAnimator=ValueAnimator.ofFloat(frontlight,target).apply{duration=1050;interpolator=lightCurve;addUpdateListener{frontlight=it.animatedValue as Float;invalidate()};addListener(object:android.animation.AnimatorListenerAdapter(){override fun onAnimationEnd(a:android.animation.Animator){state.changed()}});start()}
        state.changed()
    }
    fun power(){
        if(state.locked||refreshing)return
        if(state.powered)state.freezeInk()
        state.powered=!state.powered
        phaseTick=SystemClock.uptimeMillis()
        state.onPower(state.powered)
        state.menu=null
        if(!state.powered){setLight(false);state.onPlay(false)}else if(state.playIntent)state.onPlay(true)
        beginRefresh()
        state.changed()
    }
    fun beginRefresh(){if(!reducedMotion){val generation=++refreshGeneration;refreshing=true;refreshStart=SystemClock.uptimeMillis();postDelayed({if(generation==refreshGeneration){refreshing=false;invalidate()}},1850)}}
    val refreshElapsed get()=testTime?:SystemClock.uptimeMillis()-refreshStart
    fun sampleRefresh(ms:Long){refreshGeneration++;refreshing=true;testTime=ms;invalidate()}
    private fun press(a:String?,down:Boolean){
        pressAnimator?.cancel()
        if(down)painter.pressed=a
        val target=if(down)1f else 0f
        if(reducedMotion){painter.pressProgress=target;if(!down)painter.pressed=null;invalidate();return}
        pressAnimator=ValueAnimator.ofFloat(painter.pressProgress,target).apply{duration=90;interpolator=physical;addUpdateListener{painter.pressProgress=it.animatedValue as Float;invalidate()};addListener(object:android.animation.AnimatorListenerAdapter(){override fun onAnimationEnd(a:android.animation.Animator){if(!down)painter.pressed=null}});start()}
    }
    override fun onDraw(canvas:Canvas){
        super.onDraw(canvas);canvas.drawColor(0xff080a0c.toInt())
        if(released)return
        if(sx<=0)return
        if(!assetsReady){
            p.reset();p.isAntiAlias=true;p.color=0xffc5d1ce.toInt();p.textAlign=Paint.Align.CENTER;p.textSize=22f*sx
            canvas.drawText("漫频",width/2f,height/2f,p)
            p.textSize=12f*sx;p.color=0xff84958e.toInt()
            canvas.drawText(if(preparationError==null)"Preparing controls…" else "Interface resources could not load",width/2f,height/2f+28*sx,p)
            return
        }
        val frameTime=SystemClock.uptimeMillis()
        if(!state.reference&&state.page==0&&!reducedMotion){
            val nav="${state.view}:${state.menu}:${state.language}:${state.settingsPage}:${state.listOffset}:${state.updateOpen}"
            if(nav!=inkNavigation){if(inkNavigation.isNotEmpty())partialNavigations++;inkNavigation=nav;navigationChangedAt=frameTime}
            if(partialNavigations>=12&&frameTime-navigationChangedAt>800&&action.isBlank()&&!refreshing){partialNavigations=0;beginRefresh()}
        }
        if(active&&state.powered&&state.playing&&!reducedMotion)immersivePlayback.advance((frameTime-phaseTick).coerceIn(0,100)/1000f)
        if(state.motion&&active&&state.powered){phaseMillis+=(frameTime-phaseTick).coerceAtLeast(0);painter.phase=(testTime?:phaseMillis)/1000f}
        phaseTick=frameTime
        if(!state.reference){immersivePlayback.setRenderScale(sx);immersivePlayback.syncArtwork()}
        if((state.page==1||offset!=0f)&&painter.mainDisc==null&&(state.reference||immersivePlayback.needsFallback))disc.render(true,3801)
        if(state.page==1&&testTime!=null&&state.motion&&(!painter.smallDiscPhase.isFinite()||abs(painter.smallDiscPhase-painter.phase)>.0001f))disc.render(false,966,painter.phase)
        canvas.save();canvas.translate(ox,oy);canvas.scale(sx,sx);canvas.clipRect(0f,0f,1704f,1136f)
        fun page(immersive:Boolean,x:Float){canvas.save();canvas.translate(x,0f)
            if(immersive)drawScene(canvas,false,true)
            else {
                drawScene(canvas,false,false)
                if(frontlight>0f){val a=(frontlight*255).toInt().coerceIn(0,255);canvas.saveLayerAlpha(0f,0f,1704f,1136f,a);drawScene(canvas,true,false);canvas.restore()}
                if(refreshing)drawRefresh(canvas)
            }
            canvas.restore()
        }
        page(state.page==1,offset)
        if(offset!=0f)page(state.page!=1,offset+if(offset>0)-1704f else 1704f)
        canvas.restore()
        if(active&&!state.reference&&state.page==1&&state.powered&&state.playing&&!reducedMotion)postInvalidateOnAnimation()
        if(active&&testTime==null&&(refreshing||state.motion&&state.powered&&!reducedMotion)){
            val now=testTime?:phaseMillis
            if(state.page==1&&now-lastSmallPhase>42){disc.render(false,966,painter.phase);lastSmallPhase=now}
            if(refreshing){
                val elapsed=SystemClock.uptimeMillis()-refreshStart
                val next=listOf(396L,792L,1152L,1440L,1800L,1850L).firstOrNull{it>elapsed}?:1850L
                postInvalidateDelayed((next-elapsed).coerceAtLeast(1))
            }else postInvalidateDelayed(42)
        }
    }
    private fun drawScene(c:Canvas,lit:Boolean,immersive:Boolean){
        val tick=if(state.reference&&state.motion)(painter.phase*10).toInt() else 0
        val key="$revision:$lit:$immersive:$tick"
        val list=displayLists.get(key)?:Picture().also {picture->
            val target=picture.beginRecording(1704,1136);painter.recordStatic=true
            val pressed=painter.pressProgress;painter.pressProgress=0f
            try{painter.draw(target,lit,immersive)}finally{painter.pressProgress=pressed;painter.recordStatic=false;picture.endRecording()}
            displayLists.put(key,picture)
        }
        if(c.isHardwareAccelerated){
            // Native display-list layers are rasterized at device pixel size,
            // including 3408x2272. Reusing them avoids repainting every material
            // during alpha/drag animation; no interface screenshot is shipped.
            val kind="$lit:$immersive"
            val layer=nativeLayers.getOrPut(kind){SceneLayer(RenderNode("Aether $kind"))}
            val pixelsW=ceil(1704*sx).toInt();val pixelsH=ceil(1136*sx).toInt()
            if(layer.source!==list||layer.width!=pixelsW||layer.height!=pixelsH){
                layer.node.setPosition(0,0,pixelsW,pixelsH);layer.node.setUseCompositingLayer(true,null)
                val target=layer.node.beginRecording(pixelsW,pixelsH)
                target.scale(sx,sx);target.drawPicture(list);layer.node.endRecording()
                layer.source=list;layer.width=pixelsW;layer.height=pixelsH
            }
            c.save();c.scale(1/sx,1/sx);c.drawRenderNode(layer.node);c.restore()
        }else c.drawPicture(list)
        if(!state.reference){if(immersive){immersivePlayback.drawDisc(c,painter.mainDisc);immersivePlayback.drawSelector(c);painter.drawImmersiveSpectrum(c)}else{
            val now=SystemClock.uptimeMillis()
            val ink=inkFrames.getOrPut(lit){InkResponse()}
            ink.offer(list,revision,now,reducedMotion||refreshing||!active)
            val rects=inkRects?:painter.scene("modern-ink").flattened().filter{it.cls in listOf("directory-screen","mint-display","now-display","scope-display","area-display","indicator-strip")}.map{RectF(it.r)}.also{inkRects=it}
            for(r in rects){
                c.save();c.clipRect(r)
                if(ink.displayed!==list)ink.displayed?.let{c.drawPicture(it)}
                val alpha=(ink.residual(now)*255).toInt()
                if(alpha>0){c.saveLayerAlpha(r,alpha);ink.previous?.let{c.drawPicture(it)};c.restore()}
                c.restore()
            }
            if(ink.animating(now)&&active)postInvalidateDelayed(24)
            if(now-spectrumSampleAt>=160||reducedMotion||inkSpectrum==null){inkSpectrum=state.spectrumFrame;spectrumSampleAt=now}
            painter.drawLiveSpectrum(c,lit,inkSpectrum)
        }}
        if(!immersive)painter.drawPointers(c)
        if(painter.pressProgress>0f)painter.drawPressOverlay(c,lit,immersive)
    }
    private fun drawRefresh(c:Canvas){
        val elapsed=testTime?:SystemClock.uptimeMillis()-refreshStart
        if(elapsed>=1800)return
        val f=(elapsed/1800f).coerceIn(0f,1f)
        val contrast=when{f<.22f->.7f;f<.44f->1.3f;f<.64f->.55f;f<.8f->.75f;f<1f->.7f;else->1f}
        val invert=f in .22f..<.44f||f in .64f..<.8f
        val brightness=if(f in .44f..<.64f)1.25f else 1f
        val k=if(invert)-contrast else contrast*brightness
        val bias=if(invert)255f*(1+contrast)/2 else 255*(1-contrast)/2
        val values=floatArrayOf(.2126f*k,.7152f*k,.0722f*k,0f,bias,.2126f*k,.7152f*k,.0722f*k,0f,bias,.2126f*k,.7152f*k,.0722f*k,0f,bias,0f,0f,0f,1f,0f)
        val cm=ColorMatrix(values)
        p.reset();p.colorFilter=ColorMatrixColorFilter(cm)
        val root=painter.scene(painter.name(false))
        painter.ghostMode=0
        for(name in listOf("directory-screen","mint-display","spectrum-glass","now-display","scope-display","area-display","indicator-strip")){
            val n=root.flattened().find{it.cls.split(' ').contains(name)}?:continue
            c.saveLayer(n.r,p);c.clipRect(n.r);painter.drawNode(c,n,false);c.restore()
        }
        painter.ghostMode=0;painter.gaugeRefresh=values
        try{root.flattened().filter{it.cls=="gauge"}.forEach{painter.drawNode(c,it,false)}}finally{painter.gaugeRefresh=null}
    }
    fun pause(){active=false;pausedAt=SystemClock.uptimeMillis();cancelInput();lightAnimator?.end();pageAnimator?.end();pressAnimator?.end();pointerAnimators.values.forEach{it.end()}}
    fun resume(){active=true;phaseTick=SystemClock.uptimeMillis();pausedAt=0;reducedMotion=Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f;if(reducedMotion){refreshing=false;state.motion=false};invalidate()}
    fun close(){released=true;assetsReady=false;pause();displayLists.evictAll();inkFrames.values.forEach{it.clear()};inkFrames.clear();nativeLayers.values.forEach{it.node.discardDisplayList()};nativeLayers.clear();disc.close();immersivePlayback.close();haptics.close();painter.close()}
    private fun nodes():List<SceneNode>{
        if(!assetsReady)return emptyList()
        val all=painter.scene(if(state.page==1)"immersive" else painter.name(frontlight>.5f)).flattened().map{
            if(state.page==1&&it.cls=="station-selector__registration"&&!state.reference)SceneNode(org.json.JSONObject(it.json.toString()).put("action","music-sources").put("label",if(state.language=="zh")"音乐来源与本地专辑" else "Music sources and local album"))else it
        }
        if(state.page==1&&!state.reference){
            fun node(action:String,r:RectF,label:String)=SceneNode(org.json.JSONObject().put("id",action).put("action",action).put("label",label).put("r",org.json.JSONArray(listOf(r.left,r.top,r.width(),r.height()))))
            return all.filter{it.action!="music-sources"}+node("disc-control",ImmersivePlayback.DISC,"唱片：点击播放或暂停，旋转调整进度")+node("playback-progress",ImmersivePlayback.PROGRESS,"播放进度")+node("music-sources",ImmersivePlayback.MORE,"音乐")
        }
        if(state.page!=0||!state.updateOpen||state.view!="settings")return all
        val panel=painter.updatePanel.bounds()
        return all.filter{!panel.contains(it.r.centerX(),it.r.centerY())}+painter.updatePanel.nodes()
    }
    private fun touchBounds(node:SceneNode):RectF=RectF(node.r).apply{
        if(node.r.height()<32f||node.r.width()<32f){
            val minimum=48f*resources.displayMetrics.density/sx
            inset(-((minimum-width()).coerceAtLeast(0f)/2),-((minimum-height()).coerceAtLeast(0f)/2))
        }
    }
    private fun hit(x:Float,y:Float):SceneNode?{
        val targets=nodes().filter{it.action.isNotBlank()}
        targets.lastOrNull{it.r.contains(x,y)}?.let{return it}
        return targets.filter{touchBounds(it).contains(x,y)}.minByOrNull{
            val r=touchBounds(it);val dx=(x-r.centerX())/r.width();val dy=(y-r.centerY())/r.height();dx*dx+dy*dy
        }
    }
    private fun hitAction(x:Float,y:Float):String {
        var raw=hit(x,y)?.action?:""
        if(state.page==1&&!state.reference){
            if(raw=="disc-control"&&hypot(x-ImmersivePlayback.DISC.centerX(),y-ImmersivePlayback.DISC.centerY())>ImmersivePlayback.DISC.width()/2)raw=""
            if(raw=="playback-progress"&&(!state.isLocal||!state.seekable))raw="selector-swipe"
            if(raw.isBlank()&&ImmersivePlayback.SELECTOR.contains(x,y))raw="selector-swipe"
        }
        if(state.page==0&&state.view=="settings"&&state.updateOpen&&painter.updatePanel.bounds().contains(x,y))return painter.updatePanel.nodes().lastOrNull{it.r.contains(x,y)}?.action?:"update:body"
        if(state.menu!=null){
            val root=painter.scene(painter.name(frontlight>.5f))
            val menu=root.flattened().find{it.cls=="filter-menu"}
            if(menu!=null&&menu.r.contains(x,y)){
                val first=menu.children.firstOrNull()
                if(first!=null){val i=((y-first.r.top)/first.r.height()).toInt()+state.menuOffset;return if(i in state.options(state.menu!!).indices)"option:$i" else ""}
            }
            return raw
        }
        if(state.page==0){
            val root=painter.scene(painter.name(frontlight>.5f))
            if(state.view=="settings"&&root.find("settings-view")?.r?.contains(x,y)==true)return hit(x,y+state.settingsScroll)?.action?:""
            val list=root.find("station-list")
            if(list!=null&&list.r.contains(x,y)&&state.view!="settings"){
                val visible=state.filtered().drop(state.listOffset).take(4)
                val i=((y-list.r.top)/list.r.height()*visible.size).toInt()
                return visible.getOrNull(i)?.let{"choose:"+it.id}?:""
            }
            val presets=root.find("favorite-stations")
            if(presets!=null&&presets.r.contains(x,y)){
                val i=((x-presets.r.left)/presets.r.width()*4).toInt().coerceIn(0,3)+state.presetStart
                return if(i<state.presetPool().size)"preset:$i" else "empty-preset"
            }
        }
        return raw
    }
    private fun coordinates(e:MotionEvent)=((e.x-ox)/sx) to ((e.y-oy)/sx)
    private fun nodeFor(a:String)=nodes().find{it.action==a||it.id==a}
    private fun angle(a:String,x:Float,y:Float):Double? {
        val r=nodeFor(a)?.r?:return null
        if(hypot(x-r.centerX(),y-r.centerY())<r.width()*.2f)return null
        return atan2((y-r.centerY()).toDouble(),(x-r.centerX()).toDouble())
    }
    override fun onSizeChanged(w:Int,h:Int,oldw:Int,oldh:Int){
        super.onSizeChanged(w,h,oldw,oldh)
        if(oldw>0&&oldh>0){ignoreGesture=true;pageAnimator?.cancel();pageAnimator=null;cancelInput()}
    }
    private fun trackDiscDrag(x:Float,y:Float){
        if(hypot(x-downX,y-downY)>ViewConfiguration.get(context).scaledTouchSlop/sx)moved=true
        val next=if(hypot(x-ImmersivePlayback.DISC.centerX(),y-ImmersivePlayback.DISC.centerY())>ImmersivePlayback.DISC.width()*.16f)atan2((y-ImmersivePlayback.DISC.centerY()).toDouble(),(x-ImmersivePlayback.DISC.centerX()).toDouble())else null
        val before=discLastAngle;discLastAngle=next
        if(moved&&next!=null&&before!=null){
            var d=next-before;if(d>PI)d-=2*PI;if(d< -PI)d+=2*PI
            seekRadians+=d;consumedGesture=consumedGesture||abs(seekRadians)>=.10
            if(consumedGesture&&state.isLocal&&state.seekable){
                immersivePlayback.previewMs=(seekBaseMs+seekRadians/(2*PI)*60000).toLong().coerceIn(0,state.localDurationMs)
                immersivePlayback.rotation=discStartRotation+(seekRadians*180/PI).toFloat()
            }
        }
    }
    override fun onTouchEvent(e:MotionEvent):Boolean {
        if(sx<=0)return false
        if(e.actionMasked==MotionEvent.ACTION_DOWN)ignoreGesture=!assetsReady||refreshing||pageAnimator?.isRunning==true
        if(ignoreGesture||!assetsReady||refreshing){
            if(e.actionMasked==MotionEvent.ACTION_UP||e.actionMasked==MotionEvent.ACTION_CANCEL){cancelInput();ignoreGesture=false}
            return true
        }
        val (x,y)=coordinates(e)
        if(BuildConfig.DEBUG&&touchTrace)android.util.Log.i("AetherInput","event=${e.actionMasked} point=$x,$y action=$action hit=${hitAction(x,y)} refresh=$refreshing vertical=$verticalVolume scale=$sx view=${width}x$height")
        when(e.actionMasked){
            MotionEvent.ACTION_DOWN->{
                if(refreshing)return true
                downX=x;downY=y;lastY=y;moved=false;consumedGesture=false;action=hitAction(x,y)
                selectorTouch=state.page==1&&!state.reference&&ImmersivePlayback.SELECTOR.contains(x,y)&&action !in listOf("music-sources","playback-progress")
                if(action=="disc-control"){
                    immersivePlayback.dragging=true;seekBaseMs=state.displayPositionMs;seekRadians=0.0;discStartRotation=immersivePlayback.rotation
                    discLastAngle=if(hypot(x-ImmersivePlayback.DISC.centerX(),y-ImmersivePlayback.DISC.centerY())>ImmersivePlayback.DISC.width()*.16f)atan2((y-ImmersivePlayback.DISC.centerY()).toDouble(),(x-ImmersivePlayback.DISC.centerX()).toDouble())else null
                }
                settingsStartScroll=state.settingsScroll
                if(!state.canUse(action)){action="";ignoreGesture=true;return true}
                if(state.menu!=null&&!action.startsWith("option:")&&!action.startsWith("menu:")){
                    state.menu=null;state.changed();ignoreGesture=true;return true
                }
                focusAction=action.ifBlank{focusAction}
                if(physical(action)){press(action,true);haptics.press()}
                invalidate()
                if(action!="station-search")requestFocus()
                listOf(rotaryGate,modeGate,volumeGate,verticalGate).forEach{it.reset()}
                lastAngle=angle(action,x,y)
                val r=nodeFor(action)?.r
                verticalVolume=action=="volume"&&r!=null&&hypot(x-r.centerX(),y-r.centerY())<=r.width()*.27f
                if(action=="volume")liveVolume.begin(state.volume)
                if(action=="tempo")tempo(x)
                if(action=="segmented-volume")strip(x)
                parent.requestDisallowInterceptTouchEvent(true);return true
            }
            MotionEvent.ACTION_POINTER_DOWN->{ignoreGesture=true;cancelInput();return true}
            MotionEvent.ACTION_MOVE->{
                if(hypot(x-downX,y-downY)>ViewConfiguration.get(context).scaledTouchSlop/sx)moved=true
                if(!state.enabled)return true
                val now=SystemClock.uptimeMillis()
                if(action=="disc-control"){
                    trackDiscDrag(x,y)
                    invalidate();return true
                }
                if(action=="playback-progress"){consumedGesture=true;immersivePlayback.previewMs=(((x-ImmersivePlayback.PROGRESS.left)/ImmersivePlayback.PROGRESS.width()).coerceIn(0f,1f)*state.localDurationMs).toLong();invalidate();return true}
                if(selectorTouch&&hypot(x-downX,y-downY)>64){consumedGesture=true;invalidate();return true}
                if(state.view=="settings"&&state.about&&!state.updateOpen&&state.menu==null&&painter.scene(painter.name(false)).find("settings-view")?.r?.contains(downX,downY)==true&&abs(y-downY)>12){consumedGesture=true;state.settingsScroll=(settingsStartScroll+downY-y).coerceIn(0f,if(state.about)100f else 0f);state.changed()}
                if(action.startsWith("choose:")&&abs(y-lastY)>=28){consumedGesture=true;state.listOffset=(state.listOffset+if(y<lastY)1 else -1).coerceIn(0,(state.filtered().size-4).coerceAtLeast(0));lastY=y;state.changed()}
                if(action.startsWith("preset:")&&abs(x-downX)>=40){consumedGesture=true;state.presetOffset=(state.presetOffset+if(x<downX)1 else -1).coerceIn(0,(state.presetPool().size-4).coerceAtLeast(0));downX=x;state.changed()}
                if(state.menu!=null&&abs(y-lastY)>=28){consumedGesture=true;val count=state.options(state.menu!!).size;state.menuOffset=(state.menuOffset+if(y<lastY)1 else -1).coerceIn(0,(count-4).coerceAtLeast(0));lastY=y;state.changed()}
                if(action=="tempo")tempo(x)
                else if(action=="segmented-volume")strip(x)
                else if(action=="volume"&&!state.reference){
                    trackLiveVolume(x,y)
                }
                else if(action=="volume"&&verticalVolume){val d=verticalGate.push((lastY-y).toDouble(),now);lastY=y;if(d!=0)stepControl("volume",d)}
                else if(action in listOf("volume","tuning-dial","tuning-mode")){
                    if(moved)consumedGesture=true
                    val next=angle(action,x,y);val prev=lastAngle;lastAngle=next
                    val gate=when(action){"volume"->volumeGate;"tuning-mode"->modeGate;else->rotaryGate}
                    if(next==null||prev==null)gate.reset()else{
                        var d=next-prev;if(d>PI)d-=2*PI;if(d< -PI)d+=2*PI
                        val direction=gate.push(d*(if(action=="volume").75 else .82),now)
                        if(direction!=0)stepControl(action,direction)
                    }
                } else if(action.isBlank()){
                    val dx=x-downX;val dy=y-downY
                    if(abs(dx)>abs(dy)*1.3f){offset=dx.coerceIn(-1704f,1704f);invalidate()}
                }
                return true
            }
            MotionEvent.ACTION_UP->{
                if(state.enabled&&action=="volume"&&!state.reference)trackLiveVolume(x,y)
                if(state.enabled&&action=="disc-control"){
                    trackDiscDrag(x,y)
                    if(consumedGesture)immersivePlayback.previewMs?.let{state.seekLocal(it);haptics.tick()}else if(!moved&&hypot(x-downX,y-downY)<18){state.togglePlay();haptics.press()}
                    immersivePlayback.previewMs=null;immersivePlayback.dragging=false;selectorTouch=false;cancelInput(false);invalidate();performClick();return true
                }
                if(state.enabled&&action=="playback-progress"){val target=immersivePlayback.previewMs?:(((x-ImmersivePlayback.PROGRESS.left)/ImmersivePlayback.PROGRESS.width()).coerceIn(0f,1f)*state.localDurationMs).toLong();state.seekLocal(target);immersivePlayback.previewMs=null;cancelInput(false);invalidate();return true}
                if(selectorTouch&&state.enabled&&maxOf(abs(x-downX),abs(y-downY))>=64){val dx=x-downX;val dy=y-downY;if(maxOf(abs(dx),abs(dy))>=64){state.step(if(abs(dx)>abs(dy))if(dx<0)1 else -1 else if(dy<0)1 else -1);haptics.tick()};selectorTouch=false;cancelInput(false);return true}
                if(state.enabled&&action=="tempo")tempo(x)
                if(state.enabled&&action=="segmented-volume")strip(x)
                if(action.isBlank()&&state.enabled){
                    val dx=x-downX;val dy=y-downY
                    if(abs(dx)>=72&&abs(dx)>abs(dy)*1.3f)settle(true,if(dx>0)1 else -1)
                    else settle(false,if(dx>0)1 else -1)
                } else if(!consumedGesture&&(!moved||state.page!=1)&&(hitAction(x,y)==action||nodeFor(action)?.let{touchBounds(it).contains(x,y)}==true)){
                    if(action=="tuning-dial"||action=="tuning-mode"){val r=nodeFor(action)?.r;if(r!=null&&angle(action,x,y)!=null)stepControl(action,if(x<r.centerX())-1 else 1)}
                    else if(action!in listOf("volume","tempo","segmented-volume","empty-preset","update:body","selector-swipe")){if(action.isNotBlank()&&!physical(action))haptics.press();activate(action)}
                }
                cancelInput(false);performClick();return true
            }
            MotionEvent.ACTION_CANCEL->{immersivePlayback.previewMs=null;immersivePlayback.dragging=false;if(action=="disc-control")immersivePlayback.rotation=discStartRotation;selectorTouch=false;settle(false,if(offset>0)1 else -1);cancelInput(false);return true}
        }
        return true
    }
    private fun physical(a:String)=a.startsWith("device-")||a.startsWith("preset:")||a.startsWith("step:")||a.startsWith("favorite-step:")||a in listOf("play-toggle","stereo-button","equalizer-preview")
    private fun trackLiveVolume(x:Float,y:Float){
        if(hypot(x-downX,y-downY)>ViewConfiguration.get(context).scaledTouchSlop/sx)moved=true
        if(moved)consumedGesture=true
        if(verticalVolume){changeLiveVolume((lastY-y)/12.0);lastY=y}
        else{val next=angle("volume",x,y);val previous=lastAngle;lastAngle=next;if(next!=null&&previous!=null){var delta=next-previous;if(delta>PI)delta-=2*PI;if(delta< -PI)delta+=2*PI;if(abs(delta)<1.0)changeLiveVolume(delta/(PI/20))}}
    }
    private fun changeLiveVolume(units:Double){
        val before=state.volume;val next=liveVolume.push(units)
        state.changeVolume(next)
        if(next!=before){if(next in listOf(0,30))haptics.boundary()else haptics.tick(true)}
    }
    private fun tempo(x:Float){nodeFor("tempo")?.r?.let{val previous=state.tempo;val radius=21.58f;val progress=((x-it.left-radius)/(it.width()-radius*2)).coerceIn(0f,1f);state.changeTempo(((50+progress*100)/5).roundToInt()*5);if(previous!=state.tempo){if(state.tempo in listOf(50,150))haptics.boundary()else haptics.tick()}}}
    private fun strip(x:Float){nodeFor("segmented-volume")?.r?.let{val previous=state.volume;state.changeVolume(((x-it.left)/it.width()*30).roundToInt());if(previous!=state.volume){if(state.volume in listOf(0,30))haptics.boundary()else haptics.tick(true)}}}
    private fun stepControl(control:String,direction:Int){when(control){
        "volume"->{val before=state.volume;state.changeVolume(state.volume+direction);if(before==state.volume)haptics.boundary()else haptics.tick(true)}
        "tempo"->{val before=state.tempo;state.changeTempo(state.tempo+5*direction);if(before==state.tempo)haptics.boundary()else haptics.tick()}
        "tuning-mode"->{state.mode=Math.floorMod(state.mode+direction,4);state.changed();haptics.tick()}
        else->{state.tune(direction);haptics.tick()}
    }}
    private fun settle(change:Boolean,direction:Int){
        pageAnimator?.cancel()
        val target=if(change)direction*1704f else 0f
        if(!change&&abs(offset)<.001f){pageAnimator=null;offset=0f;return}
        if(change)haptics.confirm()
        val destination=if(change)1-state.page else state.page
        if(reducedMotion){state.page=destination;offset=0f;invalidate();return}
        pageAnimator=ValueAnimator.ofFloat(offset,target).apply {duration=if(change)280 else 220;interpolator=android.view.animation.LinearInterpolator();addUpdateListener{offset=it.animatedValue as Float;invalidate()};addListener(object:android.animation.AnimatorListenerAdapter(){var cancelled=false;override fun onAnimationCancel(a:android.animation.Animator){cancelled=true};override fun onAnimationEnd(a:android.animation.Animator){if(!cancelled){state.page=destination;offset=0f;state.changed()};if(pageAnimator===a)pageAnimator=null}});start()}
    }
    fun cancelInput(resetPage:Boolean=true){immersivePlayback.previewMs=null;immersivePlayback.dragging=false;selectorTouch=false;action="";press(null,false);lastAngle=null;listOf(rotaryGate,modeGate,volumeGate,verticalGate,wheelGate,volumeWheelGate).forEach{it.reset()};if(resetPage)offset=0f;invalidate()}
    fun activate(a:String){
        if(refreshing||!state.canUse(a))return
        if(a=="device-lock"&&state.powered){state.locked=!state.locked;if(state.locked)state.menu=null;state.changed();return}
        if(a=="device-light"&&state.powered){setLight(!state.light);return}
        if(!state.enabled&&a!="device-power")return
        when {
            a.startsWith("update:")->{if(a=="update:back"){state.updateOpen=false;state.changed()}else state.onUpdateAction(a.substringAfter(':'))}
            a=="disc-control"->state.togglePlay()
            a=="music-sources"->musicSources()
            a=="device-power"->power()
            a=="device-settings"->state.showView(if(state.view=="settings")state.lastBrowseView else "settings")
            a.startsWith("view:")->state.showView(a.substringAfter(':'))
            a.startsWith("menu:")->{val key=a.substringAfter(':').removeSuffix("-filter");state.menu=if(state.menu==key)null else key;state.settingsScroll=0f;state.changed()}
            a.startsWith("option:")->{val key=state.menu?:return;val i=a.substringAfter(':').toIntOrNull()?:return;state.options(key).getOrNull(i)?.let{state.filter(key,it)}}
            a.startsWith("station:")->{val i=a.substringAfter(':').toIntOrNull()?:return;state.catalog.getOrNull(i)?.let{state.select(it.id)}}
            a.startsWith("choose:")->state.select(a.substringAfter(':'))
            a.startsWith("preset:")->state.presetPool().getOrNull(a.substringAfter(':').toIntOrNull()?:-1)?.let{state.select(it.id)}
            a.startsWith("step:")->state.step(a.substringAfter(':').toInt())
            a.startsWith("favorite-step:")->state.step(a.substringAfter(':').toInt(),true)
            a=="previous"->state.step(-1)
            a=="next"->state.step(1)
            a=="favorite-toggle"->state.toggleFavorite()
            a=="play-toggle"->state.togglePlay()
            a=="stereo-button"->state.toggleMute()
            a in listOf("monitor-toggle","equalizer-preview")->{
                if(state.reference){if(!reducedMotion)state.motion=!state.motion}
                else if(a=="monitor-toggle"){if(!reducedMotion)state.motion=!state.motion}
                else state.spectrumEnabled=!state.spectrumEnabled
                state.changed()
            }
            a=="clear-filters"->state.resetFilters()
            a=="random-station"->state.filtered().randomOrNull()?.let{state.select(it.id)}
            a in listOf("station-search","automatic-search")->{state.showView("directory");search()}
            a=="check-updates"->{if(state.reference)state.updateMessage=true else {state.updateOpen=true;state.onUpdateAction("check")};state.changed()}
            a=="about-toggle"->{if(state.reference){state.about=!state.about;state.settingsScroll=if(state.about)70f else 0f}else state.settingsPage="about";state.changed()}
        }
    }
    override fun performClick():Boolean {super.performClick();return true}
    override fun onGenericMotionEvent(event:MotionEvent):Boolean {
        if(!assetsReady)return super.onGenericMotionEvent(event)
        if(event.action==MotionEvent.ACTION_SCROLL&&state.enabled){
            val hardware=event.source and InputDevice.SOURCE_ROTARY_ENCODER==InputDevice.SOURCE_ROTARY_ENCODER
            val(x,y)=coordinates(event);val raw=if(hardware)focusAction else hit(x,y)?.action.orEmpty()
            val control=if(raw=="segmented-volume")"volume" else raw
            if(control !in listOf("volume","tempo","tuning-dial","tuning-mode"))return super.onGenericMotionEvent(event)
            val amount=event.getAxisValue(if(hardware)MotionEvent.AXIS_SCROLL else MotionEvent.AXIS_VSCROLL)
            val gate=if(control=="volume")volumeWheelGate else wheelGate
            val delta=amount*(if(control in listOf("volume","tempo"))48 else -48)
            val d=gate.push(delta.toDouble(),SystemClock.uptimeMillis());if(d!=0)stepControl(control,d);return true
        }
        return super.onGenericMotionEvent(event)
    }
    override fun onKeyDown(keyCode:Int,event:KeyEvent):Boolean {
        if(!assetsReady)return super.onKeyDown(keyCode,event)
        if(keyCode in listOf(KeyEvent.KEYCODE_ESCAPE,KeyEvent.KEYCODE_BACK)&&state.updateOpen){state.updateOpen=false;state.changed();return true}
        if(keyCode==KeyEvent.KEYCODE_ESCAPE||keyCode==KeyEvent.KEYCODE_BACK){if(state.menu!=null){state.menu=null;state.changed();return true};if(state.view=="settings"){if(state.settingsPage!="menu")state.settingsPage="menu" else state.showView(state.lastBrowseView);state.changed();return true};if(state.page==1){state.page=0;invalidate();return true}}
        if(state.enabled&&keyCode in listOf(KeyEvent.KEYCODE_DPAD_UP,KeyEvent.KEYCODE_DPAD_RIGHT,KeyEvent.KEYCODE_DPAD_DOWN,KeyEvent.KEYCODE_DPAD_LEFT,KeyEvent.KEYCODE_MOVE_HOME,KeyEvent.KEYCODE_MOVE_END)){
            if(focusAction=="volume"&&keyCode==KeyEvent.KEYCODE_MOVE_HOME){state.changeVolume(0);haptics.boundary()}
            else if(focusAction=="volume"&&keyCode==KeyEvent.KEYCODE_MOVE_END){state.changeVolume(30);haptics.boundary()}
            else stepControl(focusAction,if(keyCode==KeyEvent.KEYCODE_DPAD_UP||keyCode==KeyEvent.KEYCODE_DPAD_RIGHT)1 else -1)
            return true
        }
        return super.onKeyDown(keyCode,event)
    }
    override fun getAccessibilityNodeProvider():AccessibilityNodeProvider = object:AccessibilityNodeProvider(){
        override fun createAccessibilityNodeInfo(virtualViewId:Int):AccessibilityNodeInfo? {
            val all=nodes().filter{it.action.isNotBlank()}
            if(virtualViewId==View.NO_ID){val n=AccessibilityNodeInfo.obtain(this@AetherView);onInitializeAccessibilityNodeInfo(n);all.indices.forEach{n.addChild(this@AetherView,it)};return n}
            val item=all.getOrNull(virtualViewId)?:return null
            return AccessibilityNodeInfo.obtain().apply{
                setSource(this@AetherView,virtualViewId);setParent(this@AetherView);packageName=context.packageName
                className=if(item.action in listOf("volume","tuning-dial","tuning-mode","tempo"))"android.widget.SeekBar" else "android.widget.Button"
                val actual=hitAction(item.r.centerX(),item.r.centerY())
                contentDescription=when{actual.startsWith("choose:")->state.lookup(actual.substringAfter(':'))?.name?:"—";actual.startsWith("preset:")->state.presetPool().getOrNull(actual.substringAfter(':').toIntOrNull()?:-1)?.name?:"Empty preset";actual.startsWith("option:")->state.options(state.menu?:"").getOrNull(actual.substringAfter(':').toIntOrNull()?:-1)?:"—";state.isLocal&&actual in listOf("previous","next")->if(actual=="previous")"Previous track" else "Next track";state.isLocal&&actual.startsWith("favorite-step:")->if(actual.endsWith("-1"))"First track" else "Last track";else->item.label.ifBlank{item.action}}
                if(actual=="empty-preset")contentDescription="Empty preset"
                if(item.action=="tempo")contentDescription="Tempo ${state.tempo} percent"
                if(item.action=="tuning-mode")contentDescription="Tuning dimension: ${listOf("station","region","genre","language")[state.mode]}"
                if(item.action=="play-toggle")contentDescription=if(state.playing)"Pause" else "Play"
                if(item.action=="equalizer-preview"&&!state.reference)contentDescription="Live PCM spectrum ${if(state.spectrumEnabled)"on" else "off"}"
                if(item.action=="monitor-toggle"&&!state.reference)contentDescription="Artwork motion ${if(state.motion)"on" else "off"}"
                isEnabled=state.canUse(actual);isClickable=true;isFocusable=true;isVisibleToUser=true
                addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK)
                val r=touchBounds(item);val b=Rect((ox+r.left*sx).toInt(),(oy+r.top*sx).toInt(),(ox+r.right*sx).toInt(),(oy+r.bottom*sx).toInt());setBoundsInParent(b)
                val loc=IntArray(2);getLocationOnScreen(loc);b.offset(loc[0],loc[1]);setBoundsInScreen(b)
                if(item.action=="playback-progress"){rangeInfo=AccessibilityNodeInfo.RangeInfo.obtain(AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_FLOAT,0f,state.localDurationMs.toFloat(),state.displayPositionMs.toFloat());contentDescription="播放进度 ${LocalMusic.time(state.displayPositionMs)} / ${LocalMusic.time(state.localDurationMs)}";addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)}
                if(item.action=="volume"){rangeInfo=AccessibilityNodeInfo.RangeInfo.obtain(AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_INT,0f,30f,state.volume.toFloat());contentDescription="Volume ${state.volume} / 30";addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)}
            }
        }
        override fun performAction(virtualViewId:Int,action:Int,arguments:android.os.Bundle?):Boolean {
            val item=nodes().filter{it.action.isNotBlank()}.getOrNull(virtualViewId)?:return false
            if(action==AccessibilityNodeInfo.ACTION_CLICK){activate(hitAction(item.r.centerX(),item.r.centerY()));return true}
            if(action==AccessibilityNodeInfo.ACTION_SCROLL_FORWARD||action==AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD){if(item.action=="playback-progress")state.seekLocal(state.displayPositionMs+if(action==AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)5000 else -5000)else stepControl(item.action,if(action==AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)1 else -1);return true}
            return false
        }
    }
}
