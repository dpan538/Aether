package com.aether.radio

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.MotionEvent
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicReference

/** Separate test APK. Exercises real native touch/IME paths and observes release state. */
class NativeEvidence:Instrumentation() {
    private var arguments=Bundle()
    private lateinit var activity:MainActivity
    private val securityChecks=JSONArray()
    override fun onCreate(arguments:Bundle?){super.onCreate(arguments);this.arguments=arguments?:Bundle();start()}
    private fun <T> main(block:()->T):T{val value=AtomicReference<T>();runOnMainSync{value.set(block())};return value.get()}
    private fun waitFor(seconds:Int=45,predicate:()->Boolean){val end=SystemClock.uptimeMillis()+seconds*1000;while(SystemClock.uptimeMillis()<end){if(main{activity.uiReady&&predicate()})return;Thread.sleep(150)};error(snapshot().toString())}
    private fun tap(action:String,fractionX:Float=.5f){
        val point=main {
            val surface=activity.surface
            if(!activity.session.reference&&activity.session.view=="settings"){
                fun descendants(v:android.view.View):List<android.view.View> = listOf(v)+(if(v is android.view.ViewGroup)(0 until v.childCount).flatMap{descendants(v.getChildAt(it))}else emptyList())
                val root=descendants(activity.window.decorView)
                val popup=root.filterIsInstance<InkSettingsView>().firstOrNull()?.languageMenuView
                val key=when(action){"check-updates"->"settings:menu:1";"update:back"->"settings:back";"menu:ui-language"->"settings:menu:0";"option:0"->if(popup!=null)"settings:language:en"else action;"option:1"->if(popup!=null)"settings:language:zh"else action;else->action}
                val native=(root+if(popup!=null)descendants(popup)else emptyList()).firstOrNull{it.tag==key&&it.isShown}
                if(native!=null){val position=IntArray(2);native.getLocationOnScreen(position);return@main (position[0]+native.width*fractionX) to (position[1]+native.height*.5f)}
            }
            val nodes=if(action.startsWith("update:"))surface.painter.updatePanel.nodes()else surface.painter.scene(if(activity.session.page==1)"immersive" else surface.painter.name(activity.session.light)).flattened()
            val r=nodes.first{it.action==action||it.id==action}.r
            val scale=minOf(surface.width/1704f,surface.height/1136f)
            val location=IntArray(2);surface.getLocationOnScreen(location)
            (location[0]+(surface.width-1704*scale)/2+(r.left+r.width()*fractionX)*scale) to (location[1]+(surface.height-1136*scale)/2+r.centerY()*scale)
        }
        val now=SystemClock.uptimeMillis()
        sendPointerSync(MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,point.first,point.second,0))
        sendPointerSync(MotionEvent.obtain(now,now+90,MotionEvent.ACTION_UP,point.first,point.second,0))
        Thread.sleep(250)
    }
    private fun updater()=MainActivity::class.java.getDeclaredField("updater").apply{isAccessible=true}.get(activity) as? Updates
    private fun snapshot():JSONObject=main {
        val s=activity.session;val info=targetContext.packageManager.getPackageInfo(targetContext.packageName,0)
        JSONObject().put("iconClass",targetContext.packageManager.getApplicationIcon(targetContext.packageName).javaClass.name).put("package",targetContext.packageName).put("versionCode",info.longVersionCode).put("versionName",info.versionName).put("reference",s.reference).put("query",s.query).put("filteredCount",s.filtered().size).put("station",s.station?.name).put("selectedId",s.station?.id).put("favorites",JSONArray(s.favorites)).put("volume",s.volume).put("tempo",s.tempo).put("language",s.language).put("spectrumEnabled",s.spectrumEnabled).put("spectrumSamples",s.spectrumFrame?.samples?:0).put("updatePhase",s.updateUi.phase.name).put("cached",s.updateUi.cached).put("downloadRequests",updater()?.downloadRequests?:0).put("reusedPackages",updater()?.reusedPackages?:0)
    }
    override fun onStart(){
        val result=Bundle()
        try {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(uiAutomation.executeShellCommand("cmd statusbar collapse")).use{it.readBytes()}
            val launch=Intent(targetContext,MainActivity::class.java).setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            if(arguments.getString("reference")=="true")launch.putExtra("mode","reference")
            if(arguments.getString("operation")=="seed")launch.putExtra("mode","live").putExtra("stream","https://ice6.somafm.com/groovesalad-128-aac").putExtra("query","SomaFM")
            arguments.putLong("launchStart",SystemClock.elapsedRealtime())
            activity=startActivitySync(launch) as MainActivity
            waitFor{activity.surface.assetsReady&&activity.surface.width>0&&!activity.surface.refreshing};Thread.sleep(700)
            result.putString("operation",arguments.getString("operation","state"))
            result.putInt("targetVersionCode",targetContext.packageManager.getPackageInfo(targetContext.packageName,0).longVersionCode.toInt())
            result.putString("targetVersionName",targetContext.packageManager.getPackageInfo(targetContext.packageName,0).versionName)
            when(arguments.getString("operation","state")){
                "state"->{}
                "ink-typography"->{result.putString("aetherResult",InkTypographySuite(this,activity).run().toString());finish(Activity.RESULT_OK,result);return}
                "widget-variants"->{result.putString("aetherResult",WidgetVariantsSuite(this,activity).run().toString());finish(Activity.RESULT_OK,result);return}
                "render-recovery"->{result.putString("aetherResult",RenderRecoverySuite(this,activity).run().toString());finish(Activity.RESULT_OK,result);return}
                "ime"->{
                    tap("station-search")
                    main {
                        val field=activity.currentFocus as EditText
                        val connection=field.onCreateInputConnection(EditorInfo())!!
                        check(connection.commitText("no-such-aether-station",1))
                    }
                    waitFor{activity.session.query=="no-such-aether-station"&&activity.session.filtered().isEmpty()}
                }
                "download"->{
                    tap("device-settings");tap("check-updates")
                    waitFor{activity.session.updateUi.phase in listOf(UpdatePhase.AVAILABLE,UpdatePhase.READY)}
                    if(main{activity.session.updateUi.phase==UpdatePhase.AVAILABLE})tap("update:download")
                    waitFor(180){activity.session.updateUi.phase==UpdatePhase.READY}
                    val before=main{updater()!!.downloadRequests}
                    tap("update:back");tap("check-updates")
                    waitFor{activity.session.updateUi.phase==UpdatePhase.READY}
                    check(main{updater()!!.downloadRequests}==before){"Repeated check downloaded the same APK again"}
                }
                "star-controls","star-controls-restore"->{result.putString("aetherResult",StarControlsSuite(this,activity).run(arguments.getString("operation")=="star-controls-restore").toString());finish(Activity.RESULT_OK,result);return}
                "navigation-performance"->{result.putString("aetherResult",NavigationPerformanceSuite(this,activity).run().toString());finish(Activity.RESULT_OK,result);return}
                "hardware-refinement"->{result.putString("aetherResult",HardwareRefinementSuite(this,activity).run().toString());finish(Activity.RESULT_OK,result);return}
                "render-regression"->{
                    main {
                        val v=activity.surface;val painter=v.painter
                        fun draw():android.graphics.Bitmap {
                            val bitmap=android.graphics.Bitmap.createBitmap(3408,2272,android.graphics.Bitmap.Config.ARGB_8888)
                            val canvas=android.graphics.Canvas(bitmap);canvas.scale(2f,2f);painter.draw(canvas,false,false);return bitmap
                        }
                        val field=ScenePainter::class.java.getDeclaredField("p").apply{isAccessible=true}
                        val shared=field.get(painter) as android.graphics.Paint
                        shared.textAlign=android.graphics.Paint.Align.LEFT;shared.textScaleX=1f
                        val baseline=draw();val pixels=IntArray(3408*2272);baseline.getPixels(pixels,0,3408,0,0,3408,2272)
                        for(alignment in listOf(android.graphics.Paint.Align.CENTER,android.graphics.Paint.Align.RIGHT)){
                            shared.textAlign=alignment;shared.textScaleX=.35f;shared.textSkewX=.4f;shared.letterSpacing=.5f
                            val next=draw();val actual=IntArray(pixels.size);next.getPixels(actual,0,3408,0,0,3408,2272)
                            val differences=pixels.indices.count{pixels[it]!=actual[it]};check(differences==0){"Text paint leaked: $alignment changed $differences pixels"}
                            securityChecks.put(JSONObject().put("check","Full 3408x2272 render independent of $alignment / scale / skew / tracking").put("changedPixels",differences).put("passed",true));next.recycle()
                        }
                        baseline.recycle()
                    }
                }
                "paint"->{
                    main {
                        val paint=android.graphics.Paint()
                        for(align in listOf(android.graphics.Paint.Align.CENTER,android.graphics.Paint.Align.RIGHT)){
                            paint.textAlign=align;paint.reset()
                            securityChecks.put(JSONObject().put("before",align.name).put("afterReset",paint.textAlign.name))
                        }
                        val v=activity.surface;val bitmap=android.graphics.Bitmap.createBitmap(3408,2272,android.graphics.Bitmap.Config.ARGB_8888)
                        val canvas=android.graphics.Canvas(bitmap);canvas.scale(2f,2f)
                        v.painter.draw(canvas,false,false)
                        val folder=targetContext.getExternalFilesDir("evidence")!!.apply{mkdirs()}
                        java.io.File(folder,"software-scene.png").outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
                    }
                }
                "current"->{
                    tap("device-settings");tap("check-updates")
                    waitFor{activity.session.updateUi.phase==UpdatePhase.CURRENT}
                    check(main{activity.session.updateUi.message=="No newer version"})
                }
                "processor"->{
                    val processor=SpectrumProcessor({false},{})
                    processor.configure(androidx.media3.common.audio.AudioProcessor.AudioFormat(44100,2,androidx.media3.common.C.ENCODING_PCM_16BIT));processor.flush()
                    processor.queueInput(androidx.media3.common.audio.AudioProcessor.EMPTY_BUFFER)
                    processor.queueInput(java.nio.ByteBuffer.allocateDirect(0))
                    val bytes=ByteArray(4096){(it%127).toByte()}
                    val input=java.nio.ByteBuffer.allocateDirect(bytes.size).put(bytes).apply{flip()}
                    processor.queueInput(input)
                    val actual=ByteArray(bytes.size);processor.output.get(actual);check(actual.contentEquals(bytes))
                    processor.queueInput(androidx.media3.common.audio.AudioProcessor.EMPTY_BUFFER)
                    processor.queueEndOfStream();check(processor.isEnded);processor.reset()
                    fun tone(floating:Boolean,opposite:Boolean,silent:Boolean=false):SpectrumFrame {
                        val frames=mutableListOf<SpectrumFrame>();val p=SpectrumProcessor({true}){frames.add(it)}
                        p.configure(androidx.media3.common.audio.AudioProcessor.AudioFormat(48000,2,if(floating)androidx.media3.common.C.ENCODING_PCM_FLOAT else androidx.media3.common.C.ENCODING_PCM_16BIT));p.flush()
                        val buffer=java.nio.ByteBuffer.allocateDirect(48000*2*if(floating)4 else 2).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                        for(i in 0 until 48000){val a=if(silent)0.0 else .5*kotlin.math.sin(2*Math.PI*1000*i/48000);val b=if(opposite)-a else if(silent)0.0 else .5*kotlin.math.sin(2*Math.PI*4000*i/48000);for(sample in listOf(a,b))if(floating)buffer.putFloat(sample.toFloat())else buffer.putShort((sample*32767).toInt().toShort())}
                        buffer.flip();val original=ByteArray(buffer.remaining());buffer.duplicate().get(original);p.queueInput(buffer)
                        val output=ByteArray(original.size);p.output.get(output);check(output.contentEquals(original)){"Analyzer altered audible PCM"}
                        check(frames.size in 1..20);val frame=frames.last();check(frame.levels.all{it.isFinite()})
                        if(silent){check(frame.rms==0f);check(frame.levels.max()<=-89f)}else{
                            check(frame.rms>.3f);check(frame.leftLevels.indices.maxBy{frame.leftLevels[it]}==5)
                            check(frame.rightLevels.indices.maxBy{frame.rightLevels[it]}==if(opposite)5 else 7)
                            check(frame.levels[5]>-15f){"Opposite-phase stereo cancelled"}
                        }
                        p.reset();return frame
                    }
                    tone(false,true);tone(false,false);tone(true,true);tone(true,false);tone(false,false,true);tone(true,false,true)
                    securityChecks.put(JSONObject().put("check","16-bit / float, anti-phase stereo, isolated channel frequencies, silence, 20 Hz ceiling and bit-exact pass-through").put("passed",true))
                    securityChecks.put(JSONObject().put("check","PCM pass-through and empty/end-of-stream buffers").put("passed",true))
                }
                "album-presentation"->{val data=AlbumPresentationSuite(this,activity).run();result.putString("aetherResult",data.toString());finish(Activity.RESULT_OK,result);return}
                "network-recovery"->{val data=NetworkRecoverySuite(this,activity).run();result.putString("aetherResult",data.toString());finish(Activity.RESULT_OK,result);return}
                "ink-motion"->{val data=InkMotionSuite(this,activity).run();result.putString("aetherResult",data.toString());finish(Activity.RESULT_OK,result);return}
                "library-management"->{val data=LibraryManagementSuite(this,activity).run();result.putString("aetherResult",data.toString());finish(Activity.RESULT_OK,result);return}
                "feedback-live"->{val data=ControlsSettingsSuite(this,activity).runFeedbackLive();result.putString("aetherResult",data.toString());finish(Activity.RESULT_OK,result);return}
                "media-notification"->{val data=NotificationSuite(this,activity).run();result.putString("aetherResult",data.toString());finish(Activity.RESULT_OK,result);return}
                "controls-settings"->{val data=ControlsSettingsSuite(this,activity).run();result.putString("aetherResult",data.toString());finish(Activity.RESULT_OK,result);return}
                "station-audit"->{val data=StationAudit(this,activity).run();result.putString("aetherResult",data.toString());finish(Activity.RESULT_OK,result);return}
                "radio-background"->{val data=ImmersiveSuite(this,activity).runRadio();result.putString("aetherResult",data.toString());finish(Activity.RESULT_OK,result);return}
                "embedded-artwork"->{val data=ImmersiveSuite(this,activity).runArtwork();result.putString("aetherResult",data.toString());finish(Activity.RESULT_OK,result);return}
                "music-widget"->{val data=WidgetSuite(this,activity).run();result.putString("aetherResult",data.toString());finish(Activity.RESULT_OK,result);return}
                "immersive-background"->{val data=ImmersiveSuite(this,activity).run();result.putString("aetherResult",data.toString());finish(Activity.RESULT_OK,result);return}
                "icon"->{
                    main {
                        val icon=targetContext.packageManager.getApplicationIcon(targetContext.packageName)
                        check(icon is android.graphics.drawable.AdaptiveIconDrawable){"Launcher did not resolve adaptive resource"}
                        val bitmap=android.graphics.Bitmap.createBitmap(512,512,android.graphics.Bitmap.Config.ARGB_8888)
                        icon.setBounds(0,0,512,512);icon.draw(android.graphics.Canvas(bitmap))
                        var left=512;var top=512;var right=0;var bottom=0
                        for(y in 0 until 512)for(x in 0 until 512){val c=bitmap.getPixel(x,y);if(android.graphics.Color.alpha(c)>240&&kotlin.math.abs(android.graphics.Color.red(c)-11)+kotlin.math.abs(android.graphics.Color.green(c)-13)+kotlin.math.abs(android.graphics.Color.blue(c)-18)>35){left=minOf(left,x);top=minOf(top,y);right=maxOf(right,x);bottom=maxOf(bottom,y)}}
                        check(kotlin.math.abs(left-(511-right))<=2&&kotlin.math.abs(top-(511-bottom))<=2){"Logo is off centre: $left,$top,$right,$bottom"}
                        check(left>=20&&top>=20&&right<=491&&bottom<=491){"Outer artwork reaches mask edge"}
                        val folder=targetContext.getExternalFilesDir("evidence")!!.apply{mkdirs()}
                        java.io.File(folder,"launcher-drawable.png").outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
                        securityChecks.put(JSONObject().put("check","Selected 99-ring adaptive icon resolves, native 512px art centred with full outer-ring margins").put("artBounds",JSONArray(listOf(left,top,right,bottom))).put("passed",true))
                    }
                }
                "album-layout"->{val data=AlbumSuite(this,activity).runLayout();result.putString("aetherResult",data.toString());finish(Activity.RESULT_OK,result);return}
                "album-import"->{val data=AlbumSuite(this,activity).runImport();result.putString("aetherResult",data.toString());finish(Activity.RESULT_OK,result);return}
                "album-restore"->{val data=AlbumSuite(this,activity).runRestore();result.putString("aetherResult",data.toString());finish(Activity.RESULT_OK,result);return}
                "album"->{val data=AlbumSuite(this,activity).run();result.putString("aetherResult",data.toString());finish(Activity.RESULT_OK,result);return}
                "soak"->{
                    val data=ExperienceSuite(this,activity,arguments).run()
                    result.putString("aetherResult",data.toString());finish(Activity.RESULT_OK,result);return
                }
                "security"->{
                    val updater=main{updater()!!}
                    val apk=java.io.File(arguments.getString("apk")?:error("APK path required"))
                    val info=targetContext.packageManager.getPackageArchiveInfo(apk.path,android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES)!!
                    val cert=Updates.digest(info.signingInfo!!.apkContentsSigners.single().toByteArray())
                    val good=UpdatePackage(info.longVersionCode,info.versionName!!,Updates.sha(apk),cert,apk.length(),"https://github.com/dpan538/Aether/releases/download/v0.1.0-preview.1/Aether-0.1.0-preview.1.apk","")
                    val verify=Updates::class.java.getDeclaredMethod("verify",UpdatePackage::class.java,java.io.File::class.java).apply{isAccessible=true}
                    val validate=Updates::class.java.getDeclaredMethod("validate",UpdatePackage::class.java).apply{isAccessible=true}
                    fun accept(label:String,run:()->Unit){run();securityChecks.put(JSONObject().put("check",label).put("passed",true))}
                    fun reject(label:String,run:()->Unit){check(runCatching(run).isFailure){"Unsafe update accepted: $label"};securityChecks.put(JSONObject().put("check",label).put("passed",true))}
                    accept("valid same-signer release"){verify.invoke(updater,good,apk)}
                    reject("wrong signer"){verify.invoke(updater,good.copy(certificate="0".repeat(64)),apk)}
                    reject("wrong version"){verify.invoke(updater,good.copy(versionName="forged"),apk)}
                    reject("downgrade"){verify.invoke(updater,good.copy(versionCode=1),apk)}
                    reject("truncated size"){verify.invoke(updater,good.copy(bytes=apk.length()-1),apk)}
                    reject("wrong hash"){verify.invoke(updater,good.copy(sha256="0".repeat(64)),apk)}
                    for(url in listOf("http://github.com/dpan538/Aether/releases/download/x/a.apk","https://github.com/other/repo/releases/download/x/a.apk","https://github.com.evil.test/dpan538/Aether/releases/download/x/a.apk","https://user@github.com/dpan538/Aether/releases/download/x/a.apk"))reject("untrusted URL $url"){validate.invoke(updater,good.copy(url=url))}
                    val changed=java.io.File(targetContext.cacheDir,"tampered.apk")
                    try{
                        apk.copyTo(changed,true)
                        java.io.RandomAccessFile(changed,"rw").use{it.seek(100);val b=it.read();it.seek(100);it.write(b xor 1)}
                        reject("tampered bytes with matching advertised hash"){verify.invoke(updater,good.copy(sha256=Updates.sha(changed)),changed)}
                    }finally{changed.delete()}
                }
                "seed"->{
                    waitFor{activity.session.station?.stream?.startsWith("https://")==true}
                    if(!main{activity.session.playing})tap("play-toggle")
                    waitFor{activity.session.playing}
                    if(main{activity.session.station?.id !in activity.session.favorites})tap("favorite-toggle")
                    tap("segmented-volume",17f/30);tap("tempo",.4f)
                    tap("device-settings");tap("menu:ui-language");tap("option:1");tap("device-settings")
                    waitFor{activity.session.volume==17&&activity.session.tempo==90&&activity.session.language=="zh"}
                }
                else->error("Unknown verification operation: ${arguments.getString("operation")}")
            }
            Thread.sleep(450)
            val operation=arguments.getString("operation","state").replace(Regex("[^A-Za-z0-9-]"),"").take(32)
            val folder=targetContext.getExternalFilesDir("evidence")!!.apply{mkdirs()}
            uiAutomation.takeScreenshot()?.let{image->java.io.File(folder,"$operation.png").outputStream().use{image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};image.recycle()}
            val data=snapshot();data.put("passed",true);if(securityChecks.length()>0)data.put("securityChecks",securityChecks)
            val cache=java.io.File(targetContext.filesDir,"updates")
            data.put("cachedFiles",JSONArray(cache.listFiles()?.filter{it.extension=="apk"}?.map{JSONObject().put("name",it.name).put("bytes",it.length()).put("modified",it.lastModified()).put("sha256",Updates.sha(it))}?:emptyList<JSONObject>()))
            result.putString("aetherResult",data.toString());finish(Activity.RESULT_OK,result)
        }catch(error:Throwable){result.putString("aetherError",error.stackTraceToString());finish(Activity.RESULT_CANCELED,result)}
    }
}
