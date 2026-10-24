package com.aether.radio

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.content.FileProvider
import com.android.apksig.ApkVerifier
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.HttpsURLConnection

enum class UpdatePhase { IDLE, CHECKING, AVAILABLE, DOWNLOADING, VERIFYING, READY, CURRENT, ERROR }
data class UpdateUi(val phase:UpdatePhase=UpdatePhase.IDLE,val message:String="",val detail:String="",val progress:Int=0,val cached:Boolean=false)

data class UpdatePackage(val versionCode:Long,val versionName:String,val sha256:String,val certificate:String,val bytes:Long,val url:String,val notes:String) {
    fun json()=JSONObject().put("versionCode",versionCode).put("versionName",versionName).put("sha256",sha256).put("certificate",certificate).put("bytes",bytes).put("url",url).put("notes",notes)
    companion object {
        fun read(j:JSONObject)=UpdatePackage(j.getLong("versionCode"),j.getString("versionName"),j.getString("sha256"),j.getString("certificate"),j.getLong("bytes"),j.getString("url"),j.optString("notes"))
    }
}

/** Public GitHub release channel, private persistent downloads, same-signer installs. */
class Updates(private val context:Context,private val changed:(UpdateUi)->Unit) {
    private val main=Handler(Looper.getMainLooper())
    private val worker=Executors.newSingleThreadExecutor()
    private val busy=AtomicBoolean(false)
    private val cancelled=AtomicBoolean(false)
    @Volatile private var connection:HttpsURLConnection?=null
    @Volatile private var closed=false
    private val folder=File(context.filesDir,"updates").apply{mkdirs()}
    private val pending=File(folder,"pending.json")
    @Volatile var candidate:UpdatePackage?=null;private set
    var ui=UpdateUi();private set
    var downloadRequests=0;private set
    var reusedPackages=0;private set
    private var awaitingPermission=false

    init { task {
        if(pending.exists())runCatching{
            val item=UpdatePackage.read(JSONObject(pending.readText()))
            validate(item)
            if(item.versionCode<=BuildConfig.VERSION_CODE){pending.delete();file(item).delete()}
            else {candidate=item;if(file(item).exists()){try{verify(item,file(item))}catch(error:Exception){file(item).delete();throw error};reusedPackages++;publish(UpdateUi(UpdatePhase.READY,"Verified package retained",item.versionName,cached=true))}else publish(UpdateUi(UpdatePhase.AVAILABLE,"Update available",item.versionName))}
        }.onFailure{pending.delete();publish(UpdateUi(UpdatePhase.ERROR,"Saved update could not be verified",it.message.orEmpty()))}
    }}

    private fun publish(value:UpdateUi){main.post{if(!closed){ui=value;changed(value)}}}
    private fun task(work:()->Unit){
        if(!busy.compareAndSet(false,true))return
        cancelled.set(false)
        worker.execute{try{work()}catch(error:Exception){if(!closed){
            val saved=candidate
            if(saved!=null&&file(saved).exists()&&runCatching{verify(saved,file(saved))}.isSuccess){
                publish(UpdateUi(UpdatePhase.READY,"Verified package retained","Check failed: ${error.message.orEmpty()}. The retained APK can still be installed.",cached=true))
            }else if(saved!=null&&File(file(saved).path+".part").exists()){
                publish(UpdateUi(UpdatePhase.AVAILABLE,"Download paused","${error.message.orEmpty()}. Downloaded bytes are retained; retry resumes where supported."))
            }else publish(UpdateUi(UpdatePhase.ERROR,if(cancelled.get())"Update paused" else "Update unavailable",error.message.orEmpty()))
        }}finally{busy.set(false)}}
    }

    fun check(){task {
        publish(UpdateUi(UpdatePhase.CHECKING,"Checking GitHub Releases"))
        val releases=JSONArray(readText("https://api.github.com/repos/dpan538/Aether/releases?per_page=30",512*1024))
        val choices=mutableListOf<UpdatePackage>()
        for(i in 0 until releases.length()){
            val release=releases.getJSONObject(i);if(release.optBoolean("draft"))continue
            val assets=release.optJSONArray("assets")?:continue
            val list=(0 until assets.length()).map{assets.getJSONObject(it)}
            val manifest=list.firstOrNull{it.optString("name")=="aether-update.json"}?:continue
            val manifestUrl=manifest.getString("browser_download_url");requireRepoAsset(manifestUrl)
            val j=JSONObject(readText(manifestUrl,64*1024))
            require(j.getInt("schemaVersion")==1&&j.getString("packageName")==BuildConfig.APPLICATION_ID){"Unsupported update identity"}
            val apkName=j.getString("apk");require(apkName.matches(Regex("[A-Za-z0-9._-]+\\.apk"))){"Invalid APK name"}
            val apk=list.firstOrNull{it.getString("name")==apkName}?:continue
            val item=UpdatePackage(j.getLong("versionCode"),j.getString("versionName"),j.getString("sha256").lowercase(),j.getString("certificateSha256").lowercase(),apk.getLong("size"),apk.getString("browser_download_url"),release.optString("body").take(2000))
            validate(item);choices.add(item)
        }
        val best=choices.filter{it.versionCode>BuildConfig.VERSION_CODE}.maxByOrNull{it.versionCode}
        if(best==null){publish(UpdateUi(UpdatePhase.CURRENT,if(choices.isEmpty())"No APK release is published yet" else "No newer version",BuildConfig.VERSION_NAME));return@task}
        candidate=best;persist(best)
        if(file(best).exists()){
            if(runCatching{verify(best,file(best))}.isSuccess){reusedPackages++;publish(UpdateUi(UpdatePhase.READY,"Verified package retained",best.versionName,cached=true))}
            else {file(best).delete();publish(UpdateUi(UpdatePhase.AVAILABLE,"Cached package was invalid; download again",best.versionName))}
        }else publish(UpdateUi(UpdatePhase.AVAILABLE,"Update available",best.versionName+" · "+"%.1f MB".format(best.bytes/1e6)))
    }}

    fun download(){val item=candidate?:return;task {
        validate(item);persist(item)
        val target=file(item)
        if(target.exists()){
            if(runCatching{verify(item,target)}.isSuccess){reusedPackages++;publish(UpdateUi(UpdatePhase.READY,"Verified package retained",item.versionName,cached=true));return@task}
            target.delete()
        }
        val part=File(target.path+".part")
        if(part.length()>item.bytes)part.delete()
        var offset=part.length()
        publish(UpdateUi(UpdatePhase.DOWNLOADING,"Downloading update",item.versionName,(offset*100/item.bytes).toInt()))
        if(offset<item.bytes){
            downloadRequests++
            val response=open(item.url,offset)
            try {
                if(response.responseCode==206){require(response.getHeaderField("Content-Range")?.startsWith("bytes $offset-")==true){"Invalid resumed range"}}
                else {require(response.responseCode==200){"Download HTTP ${response.responseCode}"};offset=0}
                val append=offset>0
                response.inputStream.use{input->java.io.FileOutputStream(part,append).use{output->
                    val buffer=ByteArray(64*1024);var received=offset;var lastNotice=0L
                    while(true){
                        if(cancelled.get()||closed)throw java.io.IOException("Cancelled")
                        val count=input.read(buffer);if(count<0)break
                        received+=count;require(received<=item.bytes){"APK exceeds release size"};output.write(buffer,0,count)
                        val now=android.os.SystemClock.uptimeMillis()
                        if(now-lastNotice>250){lastNotice=now;publish(UpdateUi(UpdatePhase.DOWNLOADING,"Downloading update",item.versionName,(received*100/item.bytes).toInt()))}
                    }
                }}
            }finally{response.disconnect();connection=null}
        }
        publish(UpdateUi(UpdatePhase.VERIFYING,"Verifying package and signature",item.versionName))
        try{verify(item,part)}catch(error:Exception){part.delete();throw error}
        check(part.renameTo(target)){"Could not retain verified package"}
        publish(UpdateUi(UpdatePhase.READY,"Ready to install",item.versionName,cached=true))
    }}

    fun cancel(){cancelled.set(true);connection?.disconnect()}
    fun install(activity:Activity){val item=candidate?:return;task {
        publish(UpdateUi(UpdatePhase.VERIFYING,"Rechecking retained package",item.versionName,cached=true))
        verify(item,file(item))
        main.post{if(!closed){
            publish(UpdateUi(UpdatePhase.READY,"Ready to install",item.versionName,cached=true))
            if(!context.packageManager.canRequestPackageInstalls()){
                awaitingPermission=true
                activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:${context.packageName}")))
            }else launchInstaller(activity,item)
        }}
    }}
    fun returnedToApp(activity:Activity){
        if(!awaitingPermission)return
        awaitingPermission=false
        if(context.packageManager.canRequestPackageInstalls())install(activity)
        else publish(UpdateUi(UpdatePhase.READY,"Installation permission was not granted","The verified APK remains available. Tap Install to retry.",cached=true))
    }
    @Suppress("DEPRECATION")
    private fun launchInstaller(activity:Activity,item:UpdatePackage){
        val uri=FileProvider.getUriForFile(context,context.packageName+".updates",file(item))
        runCatching{activity.startActivity(Intent(Intent.ACTION_INSTALL_PACKAGE).setDataAndType(uri,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))}
            .onFailure{publish(UpdateUi(UpdatePhase.READY,"Installer could not open",it.message.orEmpty(),cached=true))}
    }

    private fun file(item:UpdatePackage)=File(folder,"${item.versionCode}-${item.sha256.take(16)}.apk")
    private fun persist(item:UpdatePackage){val tmp=File(folder,"pending.tmp");tmp.writeText(item.json().toString());check(tmp.renameTo(pending)){"Could not save update state"}}
    private fun validate(item:UpdatePackage){
        require(item.versionCode>=0&&item.bytes in 1..MAX_APK&&item.sha256.matches(HEX)&&item.certificate.matches(HEX)){"Invalid update manifest"}
        requireRepoAsset(item.url)
        require(item.certificate==installedCertificate()){ "Update signer differs from this installation" }
    }
    private fun verify(item:UpdatePackage,apk:File){
        validate(item);require(item.versionCode>BuildConfig.VERSION_CODE){"Update version is not newer"}
        require(apk.length()==item.bytes){"Incomplete APK"}
        require(sha(apk)==item.sha256){"APK SHA-256 mismatch"}
        val verified=ApkVerifier.Builder(apk).setMinCheckedPlatformVersion(31).setMaxCheckedPlatformVersion(Build.VERSION.SDK_INT).build().verify()
        require(verified.isVerified&&verified.signerCertificates.size==1){"APK cryptographic signature is invalid"}
        require(digest(verified.signerCertificates.single().encoded)==item.certificate){"APK signing certificate mismatch"}
        @Suppress("DEPRECATION") val info=context.packageManager.getPackageArchiveInfo(apk.path,PackageManager.GET_SIGNING_CERTIFICATES)
        require(info?.packageName==BuildConfig.APPLICATION_ID&&info.longVersionCode==item.versionCode&&info.versionName==item.versionName){"APK package or version mismatch"}
        require((info.applicationInfo?.flags?:0) and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE==0){"Release APK must not be debuggable"}
    }
    @Suppress("DEPRECATION")
    private fun installedCertificate():String{
        val signers=context.packageManager.getPackageInfo(context.packageName,PackageManager.GET_SIGNING_CERTIFICATES).signingInfo!!.apkContentsSigners
        require(signers.size==1){"Unsupported installed signing identity"};return digest(signers.single().toByteArray())
    }
    private fun readText(url:String,limit:Int):String{
        val response=open(url)
        try {
            require(response.responseCode==200){"GitHub HTTP ${response.responseCode}"}
            return response.inputStream.use{stream->val output=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192);while(true){val count=stream.read(buffer);if(count<0)break;require(output.size()+count<=limit){"Release response is too large"};output.write(buffer,0,count)};output.toString("UTF-8")}
        }finally{response.disconnect();connection=null}
    }
    private fun open(url:String,offset:Long=0):HttpsURLConnection{
        var next=URL(url)
        repeat(8){
            require(allowed(next)){"Untrusted update download host"}
            if(cancelled.get()||closed)throw java.io.IOException("Cancelled")
            val c=(next.openConnection() as HttpsURLConnection).apply{
                instanceFollowRedirects=false;connectTimeout=8000;readTimeout=15000
                setRequestProperty("User-Agent","Aether-Android/${BuildConfig.VERSION_NAME}")
                setRequestProperty("Accept","application/vnd.github+json")
                if(offset>0)setRequestProperty("Range","bytes=$offset-")
            };connection=c
            if(c.responseCode in listOf(301,302,303,307,308)){
                val location=c.getHeaderField("Location")?:throw java.io.IOException("Missing redirect")
                next=URL(next,location);c.disconnect()
            }else return c
        }
        throw java.io.IOException("Too many download redirects")
    }
    fun close(){closed=true;cancel();worker.shutdown()}
    companion object {
        const val MAX_APK=150L*1024*1024
        private val HEX=Regex("[a-f0-9]{64}")
        fun allowed(url:URL)=url.protocol=="https"&&url.userInfo==null&&(url.port==-1||url.port==443)&&url.host.lowercase() in setOf("api.github.com","github.com","objects.githubusercontent.com","release-assets.githubusercontent.com")
        fun requireRepoAsset(url:String){val u=URL(url);require(allowed(u)&&u.host=="github.com"&&u.path.startsWith("/dpan538/Aether/releases/download/")){"Update asset is outside the Aether repository"}}
        fun digest(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}
        fun sha(file:File):String{val d=MessageDigest.getInstance("SHA-256");file.inputStream().use{input->val b=ByteArray(64*1024);while(true){val n=input.read(b);if(n<0)break;d.update(b,0,n)}};return d.digest().joinToString(""){"%02x".format(it)}}
    }
}
