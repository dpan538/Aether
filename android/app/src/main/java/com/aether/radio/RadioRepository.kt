package com.aether.radio

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.HttpsURLConnection

/** Radio Browser data only; no invented city, callsign, time zone, programme or artwork. */
class RadioRepository(private val context:Context) {
    private val worker=Executors.newSingleThreadExecutor()
    private val main=Handler(Looper.getMainLooper())
    private val generation=AtomicInteger()
    @Volatile private var connection:HttpsURLConnection?=null
    private val cache=context.filesDir.resolve("directory.json")
    fun cached():List<Station> = runCatching{decode(JSONArray(cache.readText()))}.getOrDefault(emptyList())
    fun search(query:String,done:(List<Station>,String?)->Unit) {
        val token=generation.incrementAndGet();connection?.disconnect()
        worker.execute {
            var stations=emptyList<Station>();var failure:String?=null
            for(host in listOf("de1.api.radio-browser.info","nl1.api.radio-browser.info")) {
                if(token!=generation.get())return@execute
                try {
                    val q=URLEncoder.encode(query,"UTF-8")
                    val c=URL("https://$host/json/stations/search?hidebroken=true&limit=100&name=$q").openConnection() as HttpsURLConnection
                    connection=c;c.connectTimeout=8000;c.readTimeout=8000;c.setRequestProperty("User-Agent","Aether/${BuildConfig.VERSION_NAME} (Android; native visual radio)");c.instanceFollowRedirects=true
                    val text=try{c.inputStream.use{input->
                        val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
                        while(true){val count=input.read(buffer);if(count<0)break;require(out.size()+count<=2*1024*1024){"Directory response too large"};out.write(buffer,0,count)}
                        out.toString("UTF-8")
                    }}finally{c.disconnect();if(connection===c)connection=null}
                    val array=JSONArray(text);stations=decode(array)
                    if(query.isBlank())cache.writeText(text)
                    failure=null;break
                }catch(e:Exception){failure="Directory unavailable · ${e.javaClass.simpleName}"}
            }
            if(token==generation.get())main.post{if(token==generation.get())done(stations,failure)}
        }
    }
    private fun decode(a:JSONArray):List<Station> = (0 until a.length()).mapNotNull {i->
        val o=a.getJSONObject(i);val url=o.optString("url_resolved").ifBlank{o.optString("url")}
        if(!url.startsWith("https://",true))return@mapNotNull null
        Station(o.optString("stationuuid"),o.optString("name").trim().ifBlank{"—"},o.optString("country"),
            city="",language=o.optString("language"),genre=o.optString("tags").substringBefore(','),stream=url,
            quality=if(o.optInt("bitrate")>0)"DIRECTORY · ${o.optInt("bitrate")} kbps · ${o.optString("codec")}" else null,
            mark=o.optString("countrycode").ifBlank{"—"})
    }
    fun cancel(){generation.incrementAndGet();connection?.disconnect()}
    fun close(){cancel();worker.shutdownNow()}
}
