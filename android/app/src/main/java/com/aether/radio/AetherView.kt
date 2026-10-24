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
    var frontlight=if(state.light)1f else 0f
    var refreshing=false;private set
    var reducedMotion=false
    var search:()->Unit={}
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
    private var ignoreGesture=false
    private val displayLists=android.util.LruCache<String,Picture>(4)
    private class SceneLayer(val node:RenderNode,var source:Picture?=null,var width:Int=0,var height:Int=0)
    private val nativeLayers=mutableMapOf<String,SceneLayer>()
    private var revision=0L
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
    private var action="";private var moved=false;private var verticalVolume=false
    private var focusAction="volume"
    private var settingsStartScroll=0f
    private val rotaryGate=DetentGate(.57,180,.2)
    private val modeGate=DetentGate(.66,180,.2)
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
                    if(assetsReady){onResourcesReady();postDelayed({if(!released&&active)disc.render(true,3801)},250)}
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
            if(assetsReady&&(!firstFrame||BuildConfig.DEBUG)){
                val refreshAtDraw=refreshing;val lightAtDraw=frontlight;val pageAtDraw=state.page
                val first=!firstFrame;firstFrame=true
                viewTreeObserver.registerFrameCommitCallback {post{
                    if(!released){committedRefresh=refreshAtDraw;committedFrontlight=lightAtDraw;committedPage=pageAtDraw;if(first)onFirstFrame()}
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
            if(!animate||reducedMotion){set(target);return}
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
            canvas.drawText("漫频 Aether",width/2f,height/2f,p)
            p.textSize=12f*sx;p.color=0xff84958e.toInt()
            canvas.drawText(if(preparationError==null)"Preparing controls…" else "Interface resources could not load",width/2f,height/2f+28*sx,p)
            return
        }
        val frameTime=SystemClock.uptimeMillis()
        if(state.motion&&active&&state.powered){phaseMillis+=(frameTime-phaseTick).coerceAtLeast(0);painter.phase=(testTime?:phaseMillis)/1000f}
        phaseTick=frameTime
        if((state.page==1||offset!=0f)&&painter.mainDisc==null)disc.render(true,3801)
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
        if(!immersive&&!state.reference)painter.drawLiveSpectrum(c,lit)
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
        painter.ghostMode=if(f in .44f..<.8f)1 else if(f in .8f..<1f)2 else 0
        for(name in listOf("directory-screen","mint-display","spectrum-glass","now-display","scope-display","area-display","indicator-strip")){
            val n=root.flattened().find{it.cls.split(' ').contains(name)}?:continue
            c.saveLayer(n.r,p);c.clipRect(n.r);painter.drawNode(c,n,false);c.restore()
        }
        painter.ghostMode=0;painter.gaugeRefresh=values
        try{root.flattened().filter{it.cls=="gauge"}.forEach{painter.drawNode(c,it,false)}}finally{painter.gaugeRefresh=null}
    }
    fun pause(){active=false;pausedAt=SystemClock.uptimeMillis();cancelInput();lightAnimator?.end();pageAnimator?.end();pressAnimator?.end();pointerAnimators.values.forEach{it.end()}}
    fun resume(){active=true;phaseTick=SystemClock.uptimeMillis();pausedAt=0;reducedMotion=Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f;if(reducedMotion){refreshing=false;state.motion=false};invalidate()}
    fun close(){released=true;assetsReady=false;pause();displayLists.evictAll();nativeLayers.values.forEach{it.node.discardDisplayList()};nativeLayers.clear();disc.close();painter.close()}
    private fun nodes():List<SceneNode>{
        if(!assetsReady)return emptyList()
        val all=painter.scene(if(state.page==1)"immersive" else painter.name(frontlight>.5f)).flattened()
        if(state.page!=0||!state.updateOpen||state.view!="settings")return all
        val panel=painter.updatePanel.bounds()
        return all.filter{!panel.contains(it.r.centerX(),it.r.centerY())}+painter.updatePanel.nodes()
    }
    private fun hit(x:Float,y:Float):SceneNode?=nodes().lastOrNull{it.action.isNotBlank()&&it.r.contains(x,y)}
    private fun hitAction(x:Float,y:Float):String {
        val raw=hit(x,y)?.action?:""
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
                val i=((x-presets.r.left)/presets.r.width()*4).toInt().coerceIn(0,3)+state.presetOffset
                return if(i<state.favorites.size)"preset:$i" else "empty-preset"
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
                downX=x;downY=y;lastY=y;moved=false;action=hitAction(x,y)
                settingsStartScroll=state.settingsScroll
                if(!(state.enabled||action in listOf("device-lock","device-light")&&state.powered||action=="device-power"&&!state.locked)){action="";ignoreGesture=true;return true}
                if(state.menu!=null&&action.isBlank()){state.menu=null;state.changed();return true}
                focusAction=action.ifBlank{focusAction}
                if(physical(action)){press(action,true);haptics.press()}
                invalidate()
                if(action!="station-search")requestFocus()
                listOf(rotaryGate,modeGate,volumeGate,verticalGate).forEach{it.reset()}
                lastAngle=angle(action,x,y)
                val r=nodeFor(action)?.r
                verticalVolume=action=="volume"&&r!=null&&hypot(x-r.centerX(),y-r.centerY())<=r.width()*.27f
                if(action=="tempo")tempo(x)
                if(action=="segmented-volume")strip(x)
                parent.requestDisallowInterceptTouchEvent(true);return true
            }
            MotionEvent.ACTION_POINTER_DOWN->{ignoreGesture=true;cancelInput();return true}
            MotionEvent.ACTION_MOVE->{
                if(hypot(x-downX,y-downY)>7)moved=true
                if(!state.enabled)return true
                val now=SystemClock.uptimeMillis()
                if(state.view=="settings"&&state.menu==null&&painter.scene(painter.name(false)).find("settings-view")?.r?.contains(downX,downY)==true&&abs(y-downY)>12){state.settingsScroll=(settingsStartScroll+downY-y).coerceIn(0f,if(state.about)100f else 0f);state.changed()}
                if(action.startsWith("choose:")&&abs(y-lastY)>=28){state.listOffset=(state.listOffset+if(y<lastY)1 else -1).coerceIn(0,(state.filtered().size-4).coerceAtLeast(0));lastY=y;state.changed()}
                if(action.startsWith("preset:")&&abs(x-downX)>=40){state.presetOffset=(state.presetOffset+if(x<downX)1 else -1).coerceIn(0,(state.favorites.size-4).coerceAtLeast(0));downX=x;state.changed()}
                if(state.menu!=null&&abs(y-lastY)>=28){val count=state.options(state.menu!!).size;state.menuOffset=(state.menuOffset+if(y<lastY)1 else -1).coerceIn(0,(count-4).coerceAtLeast(0));lastY=y;state.changed()}
                if(action=="tempo")tempo(x)
                else if(action=="segmented-volume")strip(x)
                else if(action=="volume"&&verticalVolume){val d=verticalGate.push((lastY-y).toDouble(),now);lastY=y;if(d!=0)stepControl("volume",d)}
                else if(action in listOf("volume","tuning-dial","tuning-mode")){
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
                if(state.enabled&&action=="tempo")tempo(x)
                if(state.enabled&&action=="segmented-volume")strip(x)
                if(action.isBlank()&&state.enabled){
                    val dx=x-downX;val dy=y-downY
                    if(abs(dx)>=72&&abs(dx)>abs(dy)*1.3f)settle(true,if(dx>0)1 else -1)
                    else {settle(false,if(dx>0)1 else -1);if(state.page==1&&abs(dy)>=40&&abs(dy)>abs(dx)){val before=state.station?.id;state.step(if(dy<0)1 else -1);if(before!=state.station?.id)haptics.tick()}}
                } else if(!moved){
                    if(action=="tuning-dial"||action=="tuning-mode"){val r=nodeFor(action)?.r;if(r!=null&&angle(action,x,y)!=null)stepControl(action,if(x<r.centerX())-1 else 1)}
                    else if(action!in listOf("volume","tempo","segmented-volume","empty-preset","update:body")){if(action.isNotBlank()&&!physical(action))haptics.press();activate(action)}
                }
                cancelInput(false);performClick();return true
            }
            MotionEvent.ACTION_CANCEL->{settle(false,if(offset>0)1 else -1);cancelInput(false);return true}
        }
        return true
    }
    private fun physical(a:String)=a.startsWith("device-")||a.startsWith("preset:")||a.startsWith("step:")||a.startsWith("favorite-step:")||a in listOf("play-toggle","stereo-button","equalizer-preview")
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
        pageAnimator=ValueAnimator.ofFloat(offset,target).apply {duration=if(change)280 else 220;interpolator=physical;addUpdateListener{offset=it.animatedValue as Float;invalidate()};addListener(object:android.animation.AnimatorListenerAdapter(){var cancelled=false;override fun onAnimationCancel(a:android.animation.Animator){cancelled=true};override fun onAnimationEnd(a:android.animation.Animator){if(!cancelled){state.page=destination;offset=0f;state.changed()};if(pageAnimator===a)pageAnimator=null}});start()}
    }
    fun cancelInput(resetPage:Boolean=true){action="";press(null,false);lastAngle=null;listOf(rotaryGate,modeGate,volumeGate,verticalGate,wheelGate,volumeWheelGate).forEach{it.reset()};if(resetPage)offset=0f;invalidate()}
    fun activate(a:String){
        if(refreshing)return
        if(a=="device-lock"&&state.powered){state.locked=!state.locked;if(state.locked)state.menu=null;state.changed();return}
        if(a=="device-light"&&state.powered){setLight(!state.light);return}
        if(!state.enabled&&a!="device-power")return
        when {
            a.startsWith("update:")->{if(a=="update:back"){state.updateOpen=false;state.changed()}else state.onUpdateAction(a.substringAfter(':'))}
            a=="device-power"->power()
            a=="device-settings"->{state.view=if(state.view=="settings")"directory" else "settings";state.menu=null;state.changed()}
            a.startsWith("view:")->{state.view=a.substringAfter(':');state.menu=null;state.listOffset=0;state.changed()}
            a.startsWith("menu:")->{val key=a.substringAfter(':').removeSuffix("-filter");state.menu=if(state.menu==key)null else key;state.settingsScroll=0f;state.changed()}
            a.startsWith("option:")->{val key=state.menu?:return;val i=a.substringAfter(':').toIntOrNull()?:return;state.options(key).getOrNull(i)?.let{state.filter(key,it)}}
            a.startsWith("station:")->{val i=a.substringAfter(':').toIntOrNull()?:return;state.catalog.getOrNull(i)?.let{state.select(it.id)}}
            a.startsWith("choose:")->state.select(a.substringAfter(':'))
            a.startsWith("preset:")->state.favorites.getOrNull(a.substringAfter(':').toIntOrNull()?:-1)?.let{state.select(it)}
            a.startsWith("step:")->state.step(a.substringAfter(':').toInt())
            a.startsWith("favorite-step:")->state.step(a.substringAfter(':').toInt(),true)
            a=="previous"->state.step(-1)
            a=="next"->state.step(1)
            a=="favorite-toggle"->state.toggleFavorite()
            a=="play-toggle"->state.togglePlay()
            a=="stereo-button"->state.toggleMute()
            a in listOf("monitor-toggle","equalizer-preview")->{
                if(state.reference){if(!reducedMotion)state.motion=!state.motion}
                else if(a=="monitor-toggle"){state.motion=!state.motion&&!reducedMotion;state.spectrumEnabled=if(reducedMotion)!state.spectrumEnabled else state.motion}
                else state.spectrumEnabled=!state.spectrumEnabled
                state.changed()
            }
            a=="clear-filters"->state.resetFilters()
            a=="random-station"->state.filtered().randomOrNull()?.let{state.select(it.id)}
            a in listOf("station-search","automatic-search")->{state.view="directory";state.changed();search()}
            a=="check-updates"->{if(state.reference)state.updateMessage=true else {state.updateOpen=true;state.onUpdateAction("check")};state.changed()}
            a=="about-toggle"->{state.about=!state.about;state.settingsScroll=if(state.about)70f else 0f;state.changed()}
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
        if(keyCode==KeyEvent.KEYCODE_ESCAPE||keyCode==KeyEvent.KEYCODE_BACK){if(state.menu!=null){state.menu=null;state.changed();return true};if(state.view=="settings"){state.view="directory";state.changed();return true};if(state.page==1){state.page=0;invalidate();return true}}
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
                contentDescription=when{actual.startsWith("choose:")->state.lookup(actual.substringAfter(':'))?.name?:"—";actual.startsWith("preset:")->state.lookup(state.favorites.getOrNull(actual.substringAfter(':').toIntOrNull()?:-1))?.name?:"Empty preset";actual.startsWith("option:")->state.options(state.menu?:"").getOrNull(actual.substringAfter(':').toIntOrNull()?:-1)?:"—";else->item.label.ifBlank{item.action}}
                if(actual=="empty-preset")contentDescription="Empty preset"
                if(item.action=="tempo")contentDescription="Tempo ${state.tempo} percent"
                if(item.action=="tuning-mode")contentDescription="Tuning dimension: ${listOf("station","region","genre","language")[state.mode]}"
                if(item.action=="play-toggle")contentDescription=if(state.playing)"Pause" else "Play"
                if(item.action in listOf("monitor-toggle","equalizer-preview")&&!state.reference)contentDescription="Live PCM spectrum ${if(state.spectrumEnabled)"on" else "off"}"
                isEnabled=(state.enabled||item.action in listOf("device-lock","device-light","device-power"))&&actual!="empty-preset";isClickable=true;isFocusable=true;isVisibleToUser=true
                addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK)
                val r=item.r;val b=Rect((ox+r.left*sx).toInt(),(oy+r.top*sx).toInt(),(ox+r.right*sx).toInt(),(oy+r.bottom*sx).toInt());setBoundsInParent(b)
                val loc=IntArray(2);getLocationOnScreen(loc);b.offset(loc[0],loc[1]);setBoundsInScreen(b)
                if(item.action=="volume"){rangeInfo=AccessibilityNodeInfo.RangeInfo.obtain(AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_INT,0f,30f,state.volume.toFloat());contentDescription="Volume ${state.volume} / 30";addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)}
            }
        }
        override fun performAction(virtualViewId:Int,action:Int,arguments:android.os.Bundle?):Boolean {
            val item=nodes().filter{it.action.isNotBlank()}.getOrNull(virtualViewId)?:return false
            if(action==AccessibilityNodeInfo.ACTION_CLICK){activate(hitAction(item.r.centerX(),item.r.centerY()));return true}
            if(action==AccessibilityNodeInfo.ACTION_SCROLL_FORWARD||action==AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD){stepControl(item.action,if(action==AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)1 else -1);return true}
            return false
        }
    }
}
