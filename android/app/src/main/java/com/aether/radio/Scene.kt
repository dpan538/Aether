package com.aether.radio

import android.content.Context
import android.graphics.*
import android.util.LruCache
import com.caverock.androidsvg.SVG
import com.caverock.androidsvg.SVGExternalFileResolver
import org.json.JSONArray
import org.json.JSONObject
import android.text.TextPaint
import android.text.TextUtils
import kotlin.math.*

internal fun JSONArray.f(i:Int)=optDouble(i,0.0).toFloat()
internal fun JSONObject.f(k:String,d:Double=0.0)=optDouble(k,d).toFloat()
internal fun JSONArray.box()=RectF(f(0),f(1),f(0)+f(2),f(1)+f(3))

class SceneNode(val json:JSONObject) {
    val id=json.optString("id");val cls=json.optString("cls");val r=json.getJSONArray("r").box()
    val action=json.optString("action");val label=json.optString("label")
    val children=(json.optJSONArray("children")?:JSONArray()).let{a->(0 until a.length()).map{SceneNode(a.getJSONObject(it))}}
    fun find(id:String):SceneNode?=if(this.id==id)this else children.firstNotNullOfOrNull{it.find(id)}
    fun flattened():List<SceneNode> = listOf(this)+children.flatMap{it.flattened()}
}

/** Native display list. Skins contain only empty individual material surfaces. */
class ScenePainter(val context:Context,val state:Session) {
    private val scenes=java.util.concurrent.ConcurrentHashMap<String,SceneNode>()
    private val preparation=java.util.concurrent.Executors.newSingleThreadExecutor()
    @Volatile private var closed=false
    private val skinMeta=JSONObject(context.assets.open("skins/index.json").bufferedReader().readText())
    // All exported 2x material surfaces total 257 MiB. Keep the shared two-page
    // working set: a smaller cache repeatedly decoded both pages during a drag.
    private val bitmaps=object:LruCache<String,Bitmap>(288*1024*1024){override fun sizeOf(k:String,v:Bitmap)=v.allocationByteCount}
    private val pictures=object:LruCache<String,Picture>(200){}
    private val p=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val arimo=Typeface.createFromAsset(context.assets,"fonts/Arimo.ttf")
    private val cousine=Typeface.createFromAsset(context.assets,"fonts/Cousine.ttf")
    private val fonts=java.util.concurrent.ConcurrentHashMap<Pair<Boolean,Int>,Typeface>()
    var mainDisc:Bitmap?=null;var smallDisc:Bitmap?=null
    var smallDiscPhase=Float.NaN
    var tuningAngle=-125f;var modeAngle=-60f;var volumeAngle=45f
    var phase=0f
    var pressed:String?=null
    var pressProgress=0f
    var recordStatic=false
    var presetFont:String?="sans-serif-condensed"
    private var menuOverlay=false
    var ghostMode=0
    var gaugeRefresh:FloatArray?=null
    val updatePanel by lazy{UpdatePanel(this,state)}
    init {
        SVG.registerExternalFileResolver(object:SVGExternalFileResolver(){override fun resolveFont(fontFamily:String,fontWeight:Int,fontStyle:String):Typeface = font(fontFamily.contains("mono",true)||fontFamily.contains("Consolas")||fontFamily.contains("SFMono"),fontWeight)})
    }
    fun font(mono:Boolean,weight:Int)=fonts.getOrPut(mono to weight){
        val actual=if(weight>=600)700 else 400
        if(mono)Typeface.create(cousine,if(actual==700)Typeface.BOLD else Typeface.NORMAL)
        else Typeface.Builder(context.assets,"fonts/Arimo.ttf").setFontVariationSettings("'wght' $actual").setWeight(actual).build()
    }
    fun scene(name:String)=scenes.getOrPut(name){SceneNode(JSONObject(context.assets.open("scenes/$name.json").bufferedReader().readText()).getJSONObject("root"))}
    fun prepare(done:(Throwable?)->Unit){
        preparation.execute {
            try {
                // Decode once away from the UI thread. All 2x surfaces fit the
                // bounded cache; window changes never redo this work in onDraw.
                for(key in skinMeta.keys()){
                    if(closed)return@execute
                    val bmp=context.assets.open("skins/$key.png").use{BitmapFactory.decodeStream(it)}
                    if(closed){bmp.recycle();return@execute};bitmaps.put(key,bmp)
                }
                for(file in context.assets.list("scenes").orEmpty()){
                    if(closed)return@execute
                    val root=scene(file.removeSuffix(".json"))
                    for(node in root.flattened())if(node.json.has("svg"))picture(node.json.getString("svg"),node.r)
                }
                if(!closed)done(null)
            }catch(error:Throwable){if(!closed)done(error)}
        }
    }
    fun name(lit:Boolean):String {
        val suffix=if(lit)"lit" else "ink"
        return when {
            !state.powered&&!lit->"off"
            state.menu!=null->state.menu+"-"+suffix
            state.view=="settings"->"settings-$suffix"
            state.volume==0->"mute-$suffix"
            state.selected==3 && state.reference->"004-$suffix"
            else->"modern-$suffix"
        }
    }
    fun draw(canvas:Canvas,lit:Boolean,immersive:Boolean=false) {
        val root=scene(if(immersive)"immersive" else name(lit))
        drawNode(canvas,root,lit)
        if(!immersive&&!state.reference&&state.updateOpen&&state.view=="settings")updatePanel.draw(canvas,lit)
        if(!immersive&&state.menu!=null){
            canvas.save();root.flattened().find{it.cls=="directory-screen"}?.r?.let{canvas.clipRect(it)}
            menuOverlay=true
            try{root.flattened().filter{it.cls=="filter-menu"}.forEach{drawNode(canvas,it,lit)}}finally{menuOverlay=false;canvas.restore()}
        }
    }
    fun drawNode(canvas:Canvas,n:SceneNode,lit:Boolean,parentId:String="",station:Station?=null,slot:Int=-1) {
        if(canvas.quickReject(RectF(n.r).apply{inset(-32f,-32f)}))return
        if(n.cls=="filter-menu"&&!menuOverlay)return
        if(n.cls.contains("pause-glyph")&&!state.playing)return
        val id=n.id.ifBlank{n.action.ifBlank{parentId}}
        val drawingStation=station ?: if(n.cls.split(' ').contains("favorite-station"))state.catalog.getOrNull(n.action.substringAfter(':').toIntOrNull()?:-1) else null
        if(n.cls=="dial-index"||n.cls=="volume-ticks")return
        if(n.json.optString("tag")=="I"&&n.cls.isBlank()&&parentId=="tuning-mode")return
        var node=n
        if(n.id=="device-light")node=scene(if(state.light)"modern-lit" else "modern-ink").find(n.id)?:n
        if(n.id=="device-lock"&&state.locked)node=scene("locked").find(n.id)?:n
        if(n.id=="device-power"&&!state.powered)node=scene("off").find(n.id)?:n
        if(n.id=="stereo-button"&&!state.powered)node=scene("off").find(n.id)?:n
        if(n.cls=="micro-led"&&!state.powered)node=scene("off").flattened().firstOrNull{it.cls=="micro-led"}?:n
        val r=node.r
        canvas.save()
        val powerColor=node.id=="device-power"&&state.powered
        if(powerColor){val material=Paint();material.colorFilter=ColorMatrixColorFilter(ColorMatrix(floatArrayOf(.84f,.16f,0f,0f,0f,.12f,.88f,0f,0f,0f,.06f,.12f,.82f,0f,0f,0f,0f,0f,1f,0f)));canvas.saveLayer(RectF(r).apply{inset(-12f,-12f)},material)}
        val alpha=if(node.json.has("svg"))1f else node.json.f("alpha",1.0)
        if(alpha<.999f)canvas.saveLayerAlpha(null,(alpha*255).toInt())
        val cap=node.cls in listOf("key-cap","transport-cap","equalizer-cap","preset-cap")
        val pausedCap=node.cls=="transport-cap"&&parentId=="play-toggle"&&!state.playing
        val unpressedCap=if(pausedCap)scene("modern-${if(lit)"lit" else "ink"}").flattened().firstOrNull{it.cls=="transport-cap"} else null
        if(pausedCap)canvas.translate(0f,-5.7936f)
        if(cap && pressed!=null && (parentId==pressed || n.action==pressed))canvas.translate(0f,3.4f*pressProgress)
        val lamp=if(node.json.optString("tag")=="I")when(parentId){"link-lamp"->if(state.connection==Connection.ERROR)"warning" else if(state.connection==Connection.CONNECTED)"connected" else "disconnected";"audio-lamp"->if(!state.reference&&state.playing)"connected" else "disconnected";"visual-lamp"->if(state.motion||state.spectrumEnabled)"connected" else "disconnected";else->null}else null
        if(lamp!=null&&(!state.reference||state.motion||!state.powered))drawLamp(canvas,r,if(state.powered)lamp else "off") else drawSkin(canvas,(unpressedCap?:node).json.optString("skin"),r)
        val before=node.json.optJSONObject("before");before?.let{val skin=unpressedCap?.json?.optJSONObject("before")?.optString("skin");val shape=if(skin!=null)JSONObject(it.toString()).put("skin",skin) else it;drawPseudo(canvas,shape)}
        val clip=node.json.optJSONArray("clip")
        if(clip!=null){val cr=clip.box();val path=Path();val radii=node.json.optJSONArray("clipRadii");if(radii!=null)path.addRoundRect(cr,FloatArray(8){radii.f(it)},Path.Direction.CW)else path.addRoundRect(cr,clip.f(4),clip.f(4),Path.Direction.CW);canvas.clipPath(path)}
        if(node.id=="settings-view")canvas.translate(0f,-state.settingsScroll)
        if(node.json.has("svg")){
            if(id=="play-toggle"&&!state.playing)canvas.translate(9.4f,0f)
            if(!state.reference&&id=="spectrum"){
                if(!recordStatic)drawLiveSpectrum(canvas,lit)
            }else {
                val source=svg(node,id,lit,drawingStation,slot)
                drawSvg(canvas,source,r,node.json.f("rotation"))
            }
        } else if(node.json.has("disc")) {
            val print=node.json.optString("disc")=="record"
            (if(print)mainDisc else smallDisc)?.let{b->
                p.reset();p.isAntiAlias=true;p.isFilterBitmap=true
                if(print){val shadow=Path().apply{fillType=Path.FillType.EVEN_ODD;addCircle(r.centerX(),r.centerY()+12.5f,r.width()/2,Path.Direction.CW);addCircle(r.centerX(),r.centerY()+12.5f,r.width()/16,Path.Direction.CW)};p.color=0x99000000.toInt();p.maskFilter=BlurMaskFilter(17f,BlurMaskFilter.Blur.NORMAL);canvas.drawPath(shadow,p);p.maskFilter=null;p.alpha=255}
                if(!print)canvas.rotate(-90f,r.centerX(),r.centerY())
                canvas.drawBitmap(b,null,r,p)
            }
        }else if(node.id=="station-list")drawStations(canvas,node,lit)
        else if(node.id=="favorite-stations")drawPresets(canvas,node,lit)
        else if(node.cls=="filter-menu")drawMenu(canvas,node,lit)
        else if(node.cls=="volume-segments"){
            val templates=scene("modern-${if(lit)"lit" else "ink"}").flattened().first{it.cls=="volume-segments"}.children
            node.children.forEachIndexed{i,child->drawSkin(canvas,templates[if(i<state.volume)0 else 20].json.optString("skin"),child.r)}
        }
        else {
            val texts=node.json.optJSONArray("text")?:JSONArray()
            for(i in 0 until texts.length()){
                val t=texts.getJSONObject(i);val original=t.getString("text")
                var text=boundText(node,id,original,i,drawingStation,slot)
                var target=t
                if(id=="now-title"||id=="now-station")text=fit(text,t,node.r.width())
                if(id=="station-name"&&text!=original){
                    val maximum=scene("immersive").flattened().first{it.cls=="station-selector__copy"}.r.width()
                    text=fit(text,t,maximum);target=JSONObject(t.toString())
                    val measure=TextPaint().apply{typeface=font(t.optBoolean("mono"),t.optInt("weight",400));textSize=t.f("size")}
                    target.put("x",node.r.right-measure.measureText(text)-t.f("spacing")*text.length)
                }
                val changed=text!=original
                if(node.id=="preset-number")t.put("preset",true)
                drawText(canvas,target,text,if(changed)0f else t.f("w"))
            }
            for(child in node.children)drawNode(canvas,child,lit,id,drawingStation,slot)
        }
        if(node.id=="tempo")drawTempo(canvas,r)
        if(node.id=="settings-view")drawSettingsDetails(canvas,node,lit)
        if(!recordStatic){
            if(node.id=="tuning-dial")drawPointer(canvas,RectF(r).apply{inset(r.width()*.123f,r.height()*.123f)},tuningAngle,3.07f,13.06f,1.7f,Color.rgb(30,50,51))
            if(node.id=="tuning-mode")drawPointer(canvas,r,modeAngle,3.64f,9.09f,5.68f,Color.rgb(223,231,218))
            if(node.id=="volume")drawPointer(canvas,r,volumeAngle,2.84f,9.09f,11.36f,Color.rgb(229,237,225))
        }
        if(node.id!="volume")node.json.optJSONObject("after")?.let{drawPseudo(canvas,it)}
        if(alpha<.999f)canvas.restore()
        if(powerColor)canvas.restore()
        canvas.restore()
    }
    private fun drawSkin(c:Canvas,key:String,r:RectF){
        if(key.isBlank()||key=="null"||!skinMeta.has(key))return
        val info=skinMeta.getJSONObject(key)
        val bmp=bitmaps.get(key)?:context.assets.open("skins/$key.png").use{BitmapFactory.decodeStream(it)}.also{bitmaps.put(key,it)}
        val pad=info.f("pad")
        p.reset();p.isFilterBitmap=true;p.isAntiAlias=true
        c.drawBitmap(bmp,null,RectF(r.left-pad,r.top-pad,r.left-pad+bmp.width/2f,r.top-pad+bmp.height/2f),p)
    }
    private fun drawPseudo(c:Canvas,o:JSONObject){
        val r=o.getJSONArray("r").box();c.save()
        val rotation=o.f("rotation");if(rotation!=0f)c.rotate(rotation,r.centerX(),r.centerY())
        val alpha=o.f("alpha",1.0);if(alpha<1)c.saveLayerAlpha(null,(alpha*255).toInt())
        drawSkin(c,o.optString("skin"),r)
        if(alpha<1)c.restore();c.restore()
    }
    private fun drawLamp(c:Canvas,r:RectF,status:String){
        val colors=when(status){"connected"->intArrayOf(0xffb6f5c3.toInt(),0xff55b976.toInt());"warning"->intArrayOf(0xfffff2a4.toInt(),0xffd0b936.toInt());"off"->intArrayOf(0xff879083.toInt(),0xff879083.toInt());else->intArrayOf(0xffefbd7d.toInt(),0xffc07c36.toInt())}
        p.reset();p.isAntiAlias=true;p.shader=RadialGradient(r.left+r.width()*.38f,r.top+r.height()*.3f,r.width()*.7f,colors,null,Shader.TileMode.CLAMP);c.drawOval(r,p)
        p.shader=null;p.style=Paint.Style.STROKE;p.strokeWidth=.8f;p.color=0xff7a8579.toInt();c.drawOval(r,p)
    }
    private fun drawText(c:Canvas,t:JSONObject,text:String,width:Float=t.f("w")){
        p.reset();p.isAntiAlias=true;p.isSubpixelText=true
        p.typeface=font(t.optBoolean("mono"),t.optInt("weight",400));p.textSize=t.f("size");p.color=color(t.optString("color"))
        if(t.optBoolean("preset")&&presetFont!=null){val spec=presetFont!!.split(':');p.typeface=Typeface.create(Typeface.create(spec[0],Typeface.NORMAL),spec.getOrNull(1)?.toIntOrNull()?:700,false)}
        val actual=p.measureText(text)
        val spacing=t.f("spacing")
        val ink=p.color
        fun ghosts(draw:()->Unit){
            if(ghostMode>0){p.color=if(ghostMode==1)0x38142014 else 0x20142014;c.save();c.translate(if(ghostMode==1)1.3632f else .4544f,0f);draw();c.restore();if(ghostMode==1){p.color=0x1a142014;c.save();c.translate(-.6816f,.7952f);draw();c.restore()};p.color=ink}
            draw()
        }
        if(abs(spacing)>.01f&&text.isNotEmpty()){
            val advances=FloatArray(text.length+1){i->p.getRunAdvance(text,0,text.length,0,text.length,false,i)}
            val scale=if(width>0&&actual>0)(width-spacing*text.length)/actual else 1f
            p.textScaleX=scale
            ghosts {for(i in text.indices)c.drawText(text,i,i+1,t.f("x")+advances[i]*scale+spacing*i,t.f("y"),p)}
        }else{
            if(width>0&&actual>0)p.textScaleX=width/actual
            ghosts{c.drawText(text,t.f("x"),t.f("y"),p)}
        }
    }
    private fun fit(text:String,t:JSONObject,width:Float):String {
        val measure=TextPaint(Paint.ANTI_ALIAS_FLAG).apply{typeface=font(t.optBoolean("mono"),t.optInt("weight",400));textSize=t.f("size")}
        return TextUtils.ellipsize(text,measure,(width-abs(t.f("spacing"))*text.length).coerceAtLeast(8f),TextUtils.TruncateAt.END).toString()
    }
    private fun twoLines(text:String,t:JSONObject,width:Float):List<String> {
        val measure=TextPaint().apply{typeface=font(t.optBoolean("mono"),t.optInt("weight",400));textSize=t.f("size")}
        if(measure.measureText(text)<=width)return listOf(text)
        var cut=measure.breakText(text,true,width,null).coerceAtLeast(1)
        val space=text.lastIndexOf(' ',cut);if(space>cut/2)cut=space
        return listOf(text.take(cut).trim(),fit(text.drop(cut).trim(),t,width))
    }
    private fun picture(source:String,r:RectF):Picture {
        val key="${r.width()}x${r.height()}:$source"
        pictures.get(key)?.let{return it}
        val match=Regex("<svg[^>]*>").find(source)
        val sized=if(match==null)source else source.replaceRange(match.range,match.value.replace(Regex("\\bwidth=\"[^\"]*\""),"width=\"${r.width()}\"").replace(Regex("\\bheight=\"[^\"]*\""),"height=\"${r.height()}\""))
        val normalized=normalizeSvg(sized)
        return SVG.getFromString(normalized).renderToPicture().also{pictures.put(key,it)}
    }
    private fun drawSvg(c:Canvas,source:String,r:RectF,rotation:Float=0f){
        val ready=try{picture(source,r)}catch(e:Exception){android.util.Log.e("AetherSVG",e.message?:"svg");return}
        c.save();if(rotation!=0f)c.rotate(rotation,r.centerX(),r.centerY());c.drawPicture(ready,r);c.restore()
    }
    private fun drawPointer(c:Canvas,r:RectF,angle:Float,width:Float,height:Float,top:Float,color:Int){
        c.save();c.rotate(angle,r.centerX(),r.centerY());p.reset();p.isAntiAlias=true;p.color=color
        c.drawRoundRect(r.centerX()-width/2,r.top+top,r.centerX()+width/2,r.top+top+height,1f,1f,p);c.restore()
    }
    fun drawPointers(c:Canvas){
        val root=scene(name(false))
        root.find("tuning-dial")?.r?.let{drawPointer(c,RectF(it).apply{inset(it.width()*.123f,it.height()*.123f)},tuningAngle,3.07f,13.06f,1.7f,Color.rgb(30,50,51))}
        root.find("tuning-mode")?.r?.let{drawPointer(c,it,modeAngle,3.64f,9.09f,5.68f,Color.rgb(223,231,218))}
        root.find("volume")?.r?.let{drawPointer(c,it,volumeAngle,2.84f,9.09f,11.36f,Color.rgb(229,237,225))}
    }
    fun drawPressOverlay(c:Canvas,lit:Boolean,immersive:Boolean){
        val action=pressed?:return
        val root=scene(if(immersive)"immersive" else name(lit))
        val node=if(action.startsWith("preset:"))root.find("favorite-stations")?.children?.getOrNull((action.substringAfter(':').toIntOrNull()?:0)-state.presetOffset) else root.flattened().find{it.id==action||it.action==action}
        val r=node?.r?:return
        c.save();c.clipRect(RectF(r).apply{inset(-11f,-11f)});recordStatic=true
        try{drawNode(c,root,lit)}finally{recordStatic=false;c.restore()}
    }
    private fun drawTempo(c:Canvas,r:RectF){
        p.reset();p.isAntiAlias=true;p.color=Color.rgb(8,18,24)
        c.drawRoundRect(r.left,r.centerY()-3.7f,r.right,r.centerY()+3.7f,3f,3f,p)
        p.color=Color.rgb(79,104,115);c.drawLine(r.left,r.centerY()+3.7f,r.right,r.centerY()+3.7f,p)
        val radius=21.58f;val cx=r.left+radius+(r.width()-radius*2)*(state.tempo-50)/100f;val cy=r.centerY()-1.4f
        p.color=Color.rgb(5,13,18);c.drawCircle(cx,cy+4,radius+1,p)
        p.shader=SweepGradient(cx,cy,intArrayOf(0xff27333b.toInt(),0xff78848a.toInt(),0xff27333b.toInt()),floatArrayOf(0f,.025f,.045f));p.shader?.setLocalMatrix(Matrix())
        // Fine radial knurling uses individual evenly spaced flutes.
        p.shader=null;p.color=0xff38434b.toInt();c.drawCircle(cx,cy,radius,p)
        for(i in 0 until 72){val a=i*PI/36; p.color=if(i%2==0)0xff78848a.toInt() else 0xff27333b.toInt();p.strokeWidth=1.5f;c.drawLine(cx+cos(a).toFloat()*(radius-3),cy+sin(a).toFloat()*(radius-3),cx+cos(a).toFloat()*radius,cy+sin(a).toFloat()*radius,p)}
        p.color=0xff252f36.toInt();c.drawCircle(cx,cy,radius*.88f,p)
        p.color=0xff848e94.toInt();c.drawCircle(cx,cy,radius*.82f,p)
        p.color=0xff252f36.toInt();c.drawCircle(cx,cy,radius*.78f,p)
        p.color=0xff454d52.toInt();c.drawCircle(cx,cy,radius*.72f,p)
    }
    private fun boundText(n:SceneNode,id:String,original:String,line:Int,station:Station?,slot:Int):String {
        val retained=if(!state.powered)state.retainedInk else null
        val s=station?:retained?.station?:state.station
        val metadata=if(retained!=null)retained.metadata else state.metadata
        val format=if(retained!=null)retained.format else state.decodedFormat
        val connection=retained?.connection?:state.connection
        val playing=retained?.playing?:state.playing
        val serial=if(state.catalog.isEmpty())"—" else (state.selected+1).toString().padStart(3,'0')
        val time=s?.localTime?.substringBefore(' ')?:"—"
        val mock=if(state.reference)"MOCK" else "LIVE"
        val cn=state.language=="zh"
        if(!state.reference&&original.contains("WIFI"))return state.deviceReadings?.network?:"NETWORK · —"
        val control=n.json.optString("control")
        if(control.isNotBlank()&&n.json.optString("tag")=="SPAN") {
            val key=control.removeSuffix("-filter");val value=state.filterValue(key)
            return if(key=="ui-language")if(state.language=="zh")"中文" else "English" else if(value.isNotBlank())value.uppercase().replace("UK","UNITED KINGDOM") else when(key){"region"->if(cn)"全部地区" else "ALL REGIONS";"genre"->if(cn)"全部类型" else "ALL GENRES";else->if(cn)"全部语言" else "ALL LANGUAGES"}
        }
        val translation=n.json.optString("i18n")
        if(cn&&translation.isNotBlank())return translations[translation]?:original
        return when(id){
            "preset-number","station-serial"->serial
            "preset-badge"->if(s==null)"—" else (state.selected+1).toString()
            "now-title"->if(state.reference)s?.programme?.replace('\n',' ')?:"—" else metadata?:"—"
            "now-station","station-name"->s?.name?:"—"
            "local-time","station-time"->if(id=="station-time")s?.localTime?:"—" else time
            "clock-location"->if(state.reference)"${s?.city} · ${s?.region}" else "DEVICE · ${state.deviceReadings?.zone?:"—"}"
            "selected-readout"->"SELECTED $serial / ${s?.name?:"—"}"
            "result-counter"->if(state.view=="settings")"03 ${if(cn)"设置" else "OPTIONS"} · $mock" else "${state.filtered().size.toString().padStart(2,'0')} ${if(cn)"条目" else "ENTRIES"} · ${if(state.directoryIssue!=null)"CACHE" else mock}"
            "screen-title"->if(state.view=="settings")"AETHER / SYSTEM" else "AETHER / STATION DIRECTORY"
            "clear-filters"->if(cn)"重置 ↗" else "RESET ↗"
            "list-title"->if(state.view=="favorites")if(cn)"我的频率" else "YOUR FREQUENCIES" else if(cn)"全球频率" else "GLOBAL FREQUENCIES"
            "station-search"->state.query.ifBlank{if(state.reference)if(cn)"电台名称、城市或呼号" else "Station name, city or callsign" else if(cn)"搜索电台名称" else "Search station names"}
            "tuning-mode-readout"->"TUNE / ${listOf("STATION","REGION","GENRE","LANGUAGE")[state.mode]}"
            "volume-readout"->"${state.volume.toString().padStart(2,'0')} / 30"
            "stream-format","stream-quality"->if(state.reference)s?.quality?:"—" else format?:"—"
            "stream-connection"->if(state.reference)if(cn)"未连接 · MOCK" else "NOT CONNECTED · MOCK" else connection.name.replace('_',' ')
            "stream-profile"->listOf(s?.language?.uppercase(),s?.genre?.uppercase()).filterNot{it.isNullOrBlank()}.joinToString(" / ").ifBlank{"—"}
            "stream-output"->if(state.volume==0)"OUTPUT / MUTE" else "VOL / ${state.volume.toString().padStart(2,'0')} · 30"
            "favorite-count"->" · ${state.favorites.size}"
            "scope-filters"->listOf(state.region,state.genre.uppercase(),state.stationLanguage.uppercase(),if(state.query.isBlank())"" else "“${state.query}”").filter{it.isNotBlank()}.joinToString(" / ").ifBlank{if(cn)"全球 / 全部电台" else "GLOBAL / ALL FREQUENCIES"}
            "scope-count"->if(n.id=="scope-count")state.filtered().size.toString().padStart(2,'0') else if(cn)"电台" else "STATIONS"
            "tempo-value"->"${state.tempo}%"
            "station-subtitle"->s?.let{listOf(it.city,it.region).filter{t->t.isNotBlank()}.joinToString(" / ") }?.ifBlank{"—"}?:"—"
            "station-position"->"${(state.selected+1).toString().padStart(2,'0')} / ${state.catalog.size.toString().padStart(2,'0')}"
            "programme"->if(state.reference)s?.programme?.split('\n')?.getOrNull(line)?:"" else twoLines(metadata?:"—",n.json.getJSONArray("text").getJSONObject(0),n.r.width()).getOrNull(line)?:""
            "programme-description"->if(state.reference)s?.description?:"—" else s?.genre?.ifBlank{"—"}?:"—"
            "now-playing"->if(state.reference)s?.track?:"—" else "—"
            "disc-edition"->"PHASE ${(state.selected+1).toString().padStart(2,'0')} · FREQUENCY"
            else->when {
                n.cls.contains("preset-title")->s?.name?:original
                n.cls.contains("playing-kicker")->if(state.reference)if(playing)"NOW PLAYING · MOCK" else "PAUSED · MOCK" else connection.name
                n.cls=="calendar"&&!state.reference->when(line){0->state.deviceReadings?.weekday?:"DEVICE TIME";1->state.deviceReadings?.date?:"—";else->state.deviceReadings?.year?:""}
                n.cls=="signal-note__live"&&!state.reference->connection.name
                n.cls.contains("station-mark")&&station!=null->station.mark
                n.cls.contains("station-genre")&&station!=null->station.genre.uppercase()
                n.cls=="station-number"&&station!=null->(state.catalog.indexOf(station)+1).toString().padStart(2,'0')
                n.json.optString("tag")=="STRONG"&&parentClass(n)=="station-identity"&&station!=null->station.name
                else->if(state.reference)original else original.replace("MOCK","—")
            }
        }
    }
    private fun parentClass(n:SceneNode)="" // Row identity is handled by drawStations below.
    private fun drawStations(c:Canvas,n:SceneNode,lit:Boolean){
        val pool=state.filtered();val template=n.children
        if(pool.isEmpty()){
            val r=n.r;p.reset();p.isAntiAlias=true;p.typeface=font(false,400);p.textSize=13f;p.color=0xff657467.toInt()
            val msg=when{state.directoryIssue!=null->if(state.language=="zh")"目录暂不可用，请重试搜索。" else "Directory unavailable. Retry search.";state.view=="favorites"->if(state.language=="zh")"还没有收藏电台。" else "No favorite stations yet.";else->if(state.language=="zh")"没有匹配的电台，清除筛选后再试。" else "No matching stations. Reset filters to try again."}
            c.drawText(msg,r.left+6,r.centerY(),p);return
        }
        val visible=pool.drop(state.listOffset).take(4);val height=n.r.height()/visible.size.coerceAtLeast(1)
        visible.forEachIndexed {i,s->
            val original=template.getOrNull(if(state.reference)state.catalog.indexOf(s) else i)?:template.firstOrNull()?:return@forEachIndexed
            val targetTop=n.r.top+i*height;val dy=targetTop+(height-original.r.height())/2-original.r.top
            if(s.id==state.station?.id){p.reset();p.color=0x1c526650;c.drawRect(n.r.left,targetTop,n.r.right,targetTop+height,p);p.color=0xff4f6553.toInt();c.drawRect(n.r.left,targetTop+height*.22f,n.r.left+2,targetTop+height*.78f,p)}
            c.save();c.translate(0f,dy)
            for(child in original.children)drawRowPart(c,child,lit,s)
            c.restore()
            p.reset();p.color=0x6694a093;p.strokeWidth=.8f;c.drawLine(n.r.left,targetTop+height,n.r.right,targetTop+height,p)
        }
    }
    private fun drawRowPart(c:Canvas,n:SceneNode,lit:Boolean,s:Station,identity:Boolean=false){
        val isIdentity=identity||n.cls=="station-identity"
        if(n.cls=="station-flag"){
            p.reset();p.isAntiAlias=true;p.style=if(s.id==state.station?.id)Paint.Style.FILL else Paint.Style.STROKE;p.strokeWidth=.8f;p.color=0xff657e67.toInt();c.drawOval(n.r,p);return
        }
        if(n.json.getJSONArray("text").length()>0&&(isIdentity||n.cls=="station-mark"||n.cls=="station-genre"||n.cls=="station-number")){
            drawSkin(c,n.json.optString("skin"),n.r)
            val a=n.json.getJSONArray("text")
            for(i in 0 until a.length()){
                val t=a.getJSONObject(i);val text=when {n.cls=="station-mark"->s.mark;n.cls=="station-genre"->s.genre.uppercase();n.cls=="station-number"->(state.catalog.indexOf(s)+1).toString().padStart(2,'0');n.json.optString("tag")=="STRONG"->s.name;else->listOf(s.city,s.region).filter{it.isNotBlank()}.joinToString(" / ").ifBlank{"—"}}
                c.save();c.clipRect(n.r);val fitted=if(n.cls=="station-number")text else fit(text,t,n.r.width());drawText(c,t,fitted,if(text==t.optString("text"))t.f("w") else if(n.cls=="station-number")n.r.width() else 0f);c.restore()
            }
        }else if(n.children.isEmpty())drawNode(c,n,lit,station=s)
        for(child in n.children)drawRowPart(c,child,lit,s,isIdentity)
    }
    private fun svg(n:SceneNode,id:String,lit:Boolean,s:Station?,slot:Int):String {
        var source=n.json.optString("svg")
        if(id in listOf("device-power","device-lock","device-light","device-settings")){
            val enabled=when(id){"device-power"->!state.locked;"device-lock","device-light"->state.powered;else->state.enabled}
            source=source.replaceFirst(Regex("opacity=\"[^\"]*\""),"opacity=\"${if(enabled)1 else .48}\"")
        }
        if(id=="digital-clock")return clock(if(state.reference)state.station?.localTime?.substringBefore(' ') else state.deviceReadings?.time,if(lit)"#353932" else "#34483a")
        if(!state.reference&&n.cls=="gauge"){
            val temperature=source.contains("data-gauge=\"temperature\"")
            val value=if(temperature)state.deviceReadings?.batteryCelsius else state.deviceReadings?.battery?.toFloat()
            val label=value?.let{if(temperature)"BATT ${it.roundToInt()}°" else "BAT ${it.roundToInt()}"}?:"—"
            source=source.replace(Regex("TEMP 22°|BAT　66"),label)
            source=Regex("<line[^>]*stroke-width=\"[36](?:px)?\"[^>]*/>").replace(source){match->
                if(value==null)"" else {
                    val angle=(value/(if(temperature)50 else 100)*180-180).coerceIn(-180f,0f)*PI/180
                    var line=match.value
                    for((key,number) in listOf("x1" to 140-cos(angle)*18,"y1" to 90-sin(angle)*18,"x2" to 140+cos(angle)*60,"y2" to 90+sin(angle)*60))line=line.replace(Regex("$key=\"[^\"]*\""),"$key=\"$number\"")
                    line
                }
            }
        }
        if(n.cls=="gauge"&&gaugeRefresh!=null){
            val matrix=gaugeRefresh!!
            source=Regex("<circle[^>]*\\br=\"71\"[^>]*/>").replace(source){circle->Regex("(fill|stroke)=\"([^\"]+)\"").replace(circle.value){attr->
                val original=color(attr.groupValues[2]);val rgb=floatArrayOf(Color.red(original).toFloat(),Color.green(original).toFloat(),Color.blue(original).toFloat())
                val channels=(0..2).map{i->(matrix[i*5]*rgb[0]+matrix[i*5+1]*rgb[1]+matrix[i*5+2]*rgb[2]+matrix[i*5+4]).roundToInt().coerceIn(0,255)}
                "${attr.groupValues[1]}=\"#%02x%02x%02x\"".format(channels[0],channels[1],channels[2])
            }}
        }
        if(!state.reference&&id=="spectrum")return "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"800\" height=\"430\" viewBox=\"0 0 800 430\"><text x=\"400\" y=\"225\" text-anchor=\"middle\" fill=\"#657467\" font-size=\"28\">—</text></svg>"
        if(state.reference&&state.motion&&id=="spectrum")return animatedSpectrum(lit)
        return source
    }
    private fun animatedSpectrum(lit:Boolean):String {
        val initial=listOf(21,22,19,12,16,15,17,10,12,7)
        val labels=listOf("31","62","125","250","500","1K","2K","4K","8K","16K")
        val ink=if(lit)"#a5eec0" else "#50624f"
        val b=StringBuilder("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"800\" height=\"430\" viewBox=\"0 0 800 430\">")
        for(i in 0..9){
            val x=75+i*65
            val level=(initial[i]+(sin(phase*1.9+i*1.7)*4+cos(phase*.7+i)*2).roundToInt()).coerceIn(3,23)
            for(j in 0..23){val active=j<level;val color=if(active)if(lit)if(j<9)"#c4ffd2" else if(j<14)"#7affaa" else "#19eb6c" else "#50644f" else if(lit)"#16462f" else "#8e9b84";b.append("<rect x=\"$x\" y=\"${340-j*12.7}\" width=\"38\" height=\"8.8\" rx=\".7\" fill=\"$color\" opacity=\"${if(active)1 else .23}\"/>")}
            b.append("<text x=\"${x+19}\" y=\"392\" text-anchor=\"middle\" fill=\"$ink\" font-family=\"monospace\" font-size=\"23\" font-weight=\"600\">${labels[i]}</text>")
        }
        for(i in 0..8)b.append("<circle cx=\"58\" cy=\"${60+i*35}\" r=\"2.4\" fill=\"$ink\"/><circle cx=\"752\" cy=\"${60+i*35}\" r=\"2.4\" fill=\"$ink\"/>")
        b.append("<g fill=\"$ink\" font-family=\"monospace\" font-size=\"23\"><text x=\"12\" y=\"66\">10</text><text x=\"24\" y=\"207\">0</text><text x=\"12\" y=\"347\">10</text><text x=\"763\" y=\"66\">10</text><text x=\"763\" y=\"207\">5</text><text x=\"763\" y=\"347\">0</text><text x=\"7\" y=\"392\">[Fq.]</text><text x=\"746\" y=\"392\">[Hz]</text></g></svg>")
        return b.toString()
    }
    fun drawLiveSpectrum(c:Canvas,lit:Boolean){
        val r=scene(name(lit)).find("spectrum")?.r?:return
        c.save();c.clipRect(r);c.translate(r.left,r.top);c.scale(r.width()/800,r.height()/430)
        val frame=state.spectrumFrame
        p.reset();p.isAntiAlias=true;p.typeface=font(true,400);p.textSize=23f
        val ink=if(lit)Color.rgb(165,238,192) else Color.rgb(80,98,79)
        if(!state.spectrumEnabled||frame==null){p.color=ink;p.textAlign=Paint.Align.CENTER;c.drawText("—",400f,220f,p);c.restore();return}
        val labels=listOf("31","62","125","250","500","1K","2K","4K","8K","16K")
        for(i in 0..9){
            val x=75f+i*65;val level=((frame.levels[i]+72)/72*24).roundToInt().coerceIn(0,24)
            for(j in 0..23){
                val on=j<level
                p.color=if(on)if(lit)if(j<9)0xffc4ffd2.toInt() else if(j<14)0xff7affaa.toInt() else 0xff19eb6c.toInt() else 0xff50644f.toInt() else if(lit)0xff16462f.toInt() else 0xff8e9b84.toInt()
                p.alpha=if(on)255 else 59;c.drawRoundRect(x,340-j*12.7f,x+38,348.8f-j*12.7f,.7f,.7f,p)
            }
            p.color=ink;p.textAlign=Paint.Align.CENTER;c.drawText(labels[i],x+19,392f,p)
        }
        p.color=ink;p.textSize=20f;p.textAlign=Paint.Align.LEFT;c.drawText("0",5f,63f,p);c.drawText("−36",0f,209f,p);c.drawText("−72",0f,349f,p);c.drawText("dBFS · PCM",20f,424f,p)
        p.textAlign=Paint.Align.RIGHT;c.drawText("Hz",784f,424f,p)
        c.restore()
    }
    private fun drawPresets(c:Canvas,n:SceneNode,lit:Boolean){
        val root=scene("modern-${if(lit)"lit" else "ink"}")
        val base=root.find("favorite-stations")?:return
        val off=scene("off").find("favorite-stations")
        val selected=base.children[0]
        val unselected=base.children[1]
        n.children.take(4).forEachIndexed {i,target->
            val station=state.lookup(state.favorites.getOrNull(i+state.presetOffset))
            val template=if(!state.powered)off?.children?.getOrNull(i)?:unselected else if(station?.id==state.station?.id)selected else unselected
            c.save();c.translate(target.r.left-template.r.left,0f)
            drawPresetPart(c,template,lit,station,i+1)
            c.restore()
        }
    }
    private fun drawPresetPart(c:Canvas,n:SceneNode,lit:Boolean,s:Station?,digit:Int){
        if(n.json.has("svg")){
            val r=n.r;p.reset();p.isAntiAlias=true
            // The seven bars remain vectors. Only the corner slot number changes.
            val shapes=listOf("4,1 16,1 18,3 16,5 4,5 2,3","18,4 20,6 20,14 18,16 16,14 16,6","18,17 20,19 20,27 18,29 16,27 16,19","4,28 16,28 18,30 16,32 4,32 2,30","2,17 4,19 4,27 2,29 0,27 0,19","2,4 4,6 4,14 2,16 0,14 0,6","4,14 16,14 18,16 16,18 4,18 2,16")
            val on=when(digit){1->listOf(1,2);2->listOf(0,1,3,4,6);3->listOf(0,1,2,3,6);else->listOf(1,2,5,6)}
            val bright=state.powered&&s?.id==state.station?.id
            val fill=if(!state.powered)"#30473f" else if(bright)"#edfffc" else "#7e9b90"
            val source="<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"24\" height=\"35\" viewBox=\"-2 -1 24 35\">"+shapes.mapIndexed{j,points->"<polygon points=\"$points\" fill=\"${if(j in on)fill else "#27443e"}\"/>"}.joinToString("")+"</svg>"
            if(bright){p.color=0x55a6f4f7;p.maskFilter=BlurMaskFilter(2f,BlurMaskFilter.Blur.NORMAL);c.drawRoundRect(r,2f,2f,p)}
            drawSvg(c,source,r);return
        }
        drawSkin(c,n.json.optString("skin"),n.r)
        n.json.optJSONObject("before")?.let{drawPseudo(c,it)}
        if(n.cls=="preset-title"){
            val a=n.json.optJSONArray("text")?:JSONArray()
            if(a.length()>0){val t=JSONObject(a.getJSONObject(0).toString());val title=fit(s?.name?:"—",t,n.r.width()-4);p.reset();p.typeface=font(false,600);p.textSize=t.f("size");t.put("x",n.r.centerX()-p.measureText(title)/2);c.save();c.clipRect(n.r);drawText(c,t,title,0f);c.restore()}
        }
        for(child in n.children){c.save();if(child.cls=="preset-cap"&&pressed=="preset:${digit-1+state.presetOffset}")c.translate(0f,3.4f*pressProgress);drawPresetPart(c,child,lit,s,digit);c.restore()}
        n.json.optJSONObject("after")?.let{drawPseudo(c,it)}
    }
    private fun drawMenu(c:Canvas,n:SceneNode,lit:Boolean){
        val key=state.menu?:return
        val options=state.options(key)
        val optionTemplate=n.children.firstOrNull()?:return
        val height=optionTemplate.r.height()
        options.drop(state.menuOffset).take(n.children.size).forEachIndexed{i,value->
            val selected=value==state.filterValue(key)
            val r=RectF(optionTemplate.r);r.offset(0f,i*height)
            val source=n.children.getOrNull(if(selected)0 else 1)?:optionTemplate
            drawSkin(c,source.json.optString("skin"),r)
            val t=JSONObject(optionTemplate.json.getJSONArray("text").getJSONObject(0).toString())
            t.put("y",t.f("y")+i*height);t.put("color",if(selected)if(lit)"#f5f0db" else "#d0d2c5" else if(lit)"#353932" else "#34483a")
            val label=if(key=="ui-language")if(value=="zh")"中文" else "English" else if(value.isBlank())when(key){"region"->"ALL REGIONS";"genre"->"ALL GENRES";else->"ALL LANGUAGES"} else value.uppercase().replace("UK","UNITED KINGDOM")
            drawText(c,t,label,if(label==optionTemplate.json.getJSONArray("text").getJSONObject(0).optString("text"))t.f("w") else 0f)
            if(selected){p.reset();p.isAntiAlias=true;p.style=Paint.Style.STROKE;p.strokeWidth=1.7f;p.color=if(lit)0xfff5f0db.toInt() else 0xffd0d2c5.toInt();val x=r.right-14;val y=r.centerY();val path=Path();path.moveTo(x-4,y);path.lineTo(x-1,y+3);path.lineTo(x+5,y-3);c.drawPath(path,p)}
        }
    }
    private fun drawSettingsDetails(c:Canvas,n:SceneNode,lit:Boolean){
        if(!state.updateMessage&&!state.about)return
        p.reset();p.isAntiAlias=true;p.typeface=font(false,400);p.textSize=13.6f;p.color=if(lit)0xff353932.toInt() else 0xff34483a.toInt()
        var y=n.r.top+231f
        if(state.updateMessage){c.drawText(if(state.language=="zh")"更新服务暂不可用。" else "Update service is currently unavailable.",n.r.left+36,y,p);y+=26}
        if(state.about){p.typeface=font(false,700);c.drawText("漫频 Aether",n.r.left+36,y,p);p.typeface=font(false,400);c.drawText("不设边界，漫游频率。",n.r.left+36,y+23,p);c.drawText("No boundaries. Just frequencies.",n.r.left+36,y+44,p);p.textSize=11.9f;c.drawText(if(state.reference)"Design preview · 0.0.0" else "Android preview · ${BuildConfig.VERSION_NAME}",n.r.left+36,y+65,p)}
    }
    fun close(){closed=true;preparation.shutdownNow();bitmaps.evictAll();pictures.evictAll();mainDisc?.recycle();smallDisc?.recycle()}
    companion object {
        val translations=mapOf("settings" to "设置","uiLanguage" to "界面语言","checkUpdates" to "检查更新","about" to "关于","region" to "地区","genre" to "类型","language" to "语言","directory" to "目录","favorites" to "收藏","random" to "随机","favoriteStations" to "收藏电台","automaticSearch" to "直达搜索","audioFormat" to "音频规格")
        fun color(s:String):Int {
            if(s.startsWith("#"))return runCatching{Color.parseColor(s)}.getOrDefault(Color.BLACK)
            val v=Regex("[0-9.]+").findAll(s.substringAfter('(').substringBefore(')')).map{it.value.toFloat()}.toList()
            if(v.size<3)return Color.BLACK
            val unit=if(s.startsWith("color("))255f else 1f
            return Color.argb(((v.getOrNull(3)?:1f)*255).roundToInt().coerceIn(0,255),(v[0]*unit).roundToInt().coerceIn(0,255),(v[1]*unit).roundToInt().coerceIn(0,255),(v[2]*unit).roundToInt().coerceIn(0,255))
        }
        fun normalizeSvg(source:String):String {
            if(!source.contains("rgb"))return source
            var result=Regex("color\\(srgb ([^)]+)\\)").replace(source){m->val c=color(m.value);if(Color.alpha(c)==0)"none" else "#%06x".format(c and 0xffffff)}
            result=Regex("rgba?\\([^)]+\\)").replace(result){m->val c=color(m.value);if(Color.alpha(c)==0)"none" else "#%06x".format(c and 0xffffff)}
            return result
        }
        val segmentShapes=mapOf('a' to "10,0 50,0 58,8 50,14 10,14 2,7",'b' to "52,16 60,8 60,44 52,50 46,44 46,22",'c' to "52,54 60,60 60,94 52,101 46,93 46,61",'d' to "10,89 50,89 57,96 50,103 10,103 2,96",'e' to "0,60 8,53 14,61 14,85 7,93 0,86",'f' to "0,16 8,8 14,17 14,43 7,50 0,43",'g' to "10,45 49,45 57,52 49,59 10,59 3,52")
        val digitSegments=listOf("abcdef","bc","abged","abgcd","fgbc","afgcd","afgecd","abc","abcdefg","abfgcd")
        fun clock(value:String?,ink:String):String {
            val digits=value?.filter{it.isDigit()}?.take(4)
            val content=(0..3).joinToString(""){i->val d=digits?.getOrNull(i)?.digitToIntOrNull();"<g transform=\"translate(${i*72+(if(i>1)21 else 0)+10},0)\">"+segmentShapes.map{(name,points)->"<polygon points=\"$points\" opacity=\"${if(d!=null&&digitSegments[d].contains(name))1 else if(d==null&&name=='g')1 else .035}\"/>"}.joinToString("")+"</g>"}
            return "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"355\" height=\"102\" viewBox=\"0 0 355 102\" fill=\"$ink\">$content<rect x=\"151\" y=\"25\" width=\"9\" height=\"10\"/><rect x=\"151\" y=\"68\" width=\"9\" height=\"10\"/></svg>"
        }
    }
}
