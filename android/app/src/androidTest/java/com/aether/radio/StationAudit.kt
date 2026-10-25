package com.aether.radio

import android.app.Instrumentation
import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicReference

/** Every candidate in the actual loaded directory is attempted through the shipping player. */
class StationAudit(private val test:Instrumentation,private val activity:MainActivity){
    private fun <T> main(block:()->T):T{val r=AtomicReference<Result<T>>();test.runOnMainSync{r.set(runCatching{block()})};return r.get().getOrThrow()}
    fun run():JSONObject{
        val s=activity.session
        val stations=main{s.catalog.toList()};require(stations.isNotEmpty())
        val rows=JSONArray();val file=java.io.File(test.targetContext.getExternalFilesDir("evidence"),"station-audit.json")
        val audio=main{MainActivity::class.java.getDeclaredField("audio").apply{isAccessible=true}.get(activity) as AudioSession}
        main{s.page=0;s.view="directory";s.query="";s.region="";s.genre="";s.stationLanguage="";s.spectrumEnabled=true;s.changed()}
        var playable=0
        for(station in stations){
            val start=SystemClock.elapsedRealtime();main{s.select(station.id)}
            var decoded=false
            while(SystemClock.elapsedRealtime()-start<10000){
                val status=main{Triple(s.playing&&s.spectrumFrame!=null&&(s.spectrumFrame?.samples?:0)>8192,s.connection,s.activeId)}
                if(status.third==station.id&&status.first){decoded=true;break}
                if(status.second==Connection.ERROR)break
                Thread.sleep(100)
            }
            if(decoded)playable++
            rows.put(main{JSONObject().put("id",station.id).put("name",station.name).put("url",station.stream).put("decoded",decoded).put("format",s.decodedFormat).put("samples",s.spectrumFrame?.samples?:0).put("rms",s.spectrumFrame?.rms?:0).put("error",s.issue).put("positionMs",audio.player.currentPosition).put("elapsedMs",SystemClock.elapsedRealtime()-start)})
            file.writeText(JSONObject().put("completed",rows.length()).put("candidates",stations.size).put("decoded",playable).put("environment","API36 ARM64 emulator, 3408×2272; current network; not physical Xiaomi").put("stations",rows).toString(2))
        }
        main{s.selectLocal(LocalMusic.bundled(test.targetContext),0)}
        return JSONObject().put("auditCompleted",true).put("candidates",stations.size).put("decoded",playable).put("notDecoded",stations.size-playable).put("records","station-audit.json")
    }
}
