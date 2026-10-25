package com.aether.radio

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.URL
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.Executors
import javax.net.ssl.HttpsURLConnection

/** Only user-entered text, stable request identity and app version leave the device. */
class FeedbackClient(context:Context,private val changed:()->Unit){
    private val prefs=context.applicationContext.getSharedPreferences("manpin-feedback",Context.MODE_PRIVATE)
    private val worker=Executors.newSingleThreadExecutor();private val main=Handler(Looper.getMainLooper())
    private var closed=false
    var draft=prefs.getString("draft","").orEmpty();private set
    private var requestId=prefs.getString("request-id","").orEmpty()
    private var version=prefs.getString("version",BuildConfig.VERSION_NAME).orEmpty()
    var busy=false;private set
    var status="";private set
    var receipt="";private set
    fun edit(value:String){
        if(busy||draft==value)return
        draft=value;requestId=UUID.randomUUID().toString();version=BuildConfig.VERSION_NAME;status="";receipt=""
        persist();changed()
    }
    private fun persist(){prefs.edit().putString("draft",draft).putString("request-id",requestId).putString("version",version).apply()}
    private fun request(url:String,body:JSONObject?=null):Pair<Int,JSONObject>{
        val c=URL(url).openConnection() as HttpsURLConnection
        c.instanceFollowRedirects=false;c.connectTimeout=12000;c.readTimeout=12000;c.setRequestProperty("User-Agent","Manpin/${BuildConfig.VERSION_NAME}");c.setRequestProperty("Accept","application/json");c.setRequestProperty("Cache-Control","no-cache")
        try{
            if(body!=null){c.requestMethod="POST";c.doOutput=true;c.setRequestProperty("Content-Type","application/json");c.outputStream.use{it.write(body.toString().toByteArray(Charsets.UTF_8))}}
            val code=c.responseCode;val input=if(code in 200..299)c.inputStream else c.errorStream
            val bytes=input?.use{val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(4096);while(true){val n=it.read(buffer);if(n<0)break;require(out.size()+n<=65536);out.write(buffer,0,n)};out.toByteArray()}?:byteArrayOf()
            return code to JSONObject(bytes.toString(Charsets.UTF_8))
        }finally{c.disconnect()}
    }
    fun submit(){
        if(busy||draft.isBlank())return
        if(requestId.isBlank())requestId=UUID.randomUUID().toString()
        persist();val text=draft;val id=requestId;val originalVersion=version
        busy=true;status="sending";changed()
        worker.execute{
            var issue=""
            val result=runCatching{
                val (code,data)=request(ENDPOINT,JSONObject().put("body",text).put("requestId",id).put("version",originalVersion))
                if(code!=200)return@runCatching data.optString("error","unavailable")
                require(data.optBoolean("accepted")&&data.optString("requestId")==id)
                issue=data.getString("receipt")
                val number=Regex("https://github\\.com/dpan538/Aether/issues/([1-9][0-9]*)").matchEntire(issue)?.groupValues?.get(1)?:error("Invalid receipt")
                val digest=MessageDigest.getInstance("SHA-256").digest((text+'\u0000'+originalVersion).toByteArray(Charsets.UTF_8)).joinToString(""){"%02x".format(it)}
                val (verified,record)=request("https://api.github.com/repos/dpan538/Aether/issues/$number")
                require(verified==200&&!record.has("pull_request")&&record.optString("html_url")==issue&&record.optString("body").contains("<!-- MANPIN:$id:$digest -->"))
                "received"
            }.getOrDefault("unavailable")
            main.post{if(!closed){busy=false;status=result;if(result=="received"&&requestId==id&&draft==text){receipt=issue;draft="";requestId="";persist()};changed()}}
        }
    }
    fun close(){closed=true;worker.shutdownNow()}
    companion object{const val ENDPOINT="https://manpin-feedback.vercel.app/v1/feedback"}
}
