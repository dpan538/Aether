package com.aether.radio

import android.content.Context
import android.net.Uri
import android.media.MediaMetadataRetriever
import android.provider.OpenableColumns
import org.json.JSONArray
import org.json.JSONObject

/** Real finite media; radio directory identity remains independent of this queue. */
data class LocalTrack(val id:String,val title:String,val uri:String,val album:String,val durationMs:Long,
    val artist:String="",val instruments:String="",val description:String="") {
    fun json()=JSONObject().put("id",id).put("title",title).put("uri",uri).put("album",album).put("durationMs",durationMs).put("artist",artist).put("instruments",instruments).put("description",description)
    companion object {
        fun fromJson(j:JSONObject)=LocalTrack(j.getString("id"),j.getString("title"),j.getString("uri"),j.optString("album"),j.optLong("durationMs"),j.optString("artist"),j.optString("instruments"),j.optString("description"))
    }
}
object LocalMusic {
    const val ROOT="albums/before-the-rise"
    fun manifest(context:Context)=JSONObject(context.assets.open("$ROOT/manifest.json").bufferedReader().use{it.readText()})
    fun bundled(context:Context):List<LocalTrack>{
        val album=manifest(context);val tracks=album.getJSONArray("tracks")
        return (0 until tracks.length()).map{i->val t=tracks.getJSONObject(i)
            LocalTrack("before-the-rise:${i+1}",t.getString("title"),"asset:///$ROOT/${t.getString("file")}",album.getString("title"),t.getLong("durationMs"),album.getString("artist"),t.getString("instruments"),t.getString("description"))}
    }
    fun imported(context:Context,uri:Uri):LocalTrack {
        require(uri.scheme=="content"){"Choose an audio document"}
        val name=context.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use{if(it.moveToFirst())it.getString(0)else null}.orEmpty()
        val reader=MediaMetadataRetriever()
        try {
            reader.setDataSource(context,uri)
            val duration=reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?:0L
            require(duration>0){"Unreadable audio file"}
            return LocalTrack("document:${uri}",reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf{it.isNotBlank()}?.take(400)?:name.substringBeforeLast('.').ifBlank{"Audio"}.take(400),uri.toString(),reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM).orEmpty().take(400),duration,reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST).orEmpty().take(400))
        }finally{reader.release()}
    }
    fun time(ms:Long):String {val seconds=ms.coerceAtLeast(0)/1000;return "%d:%02d".format(java.util.Locale.ROOT,seconds/60,seconds%60)}
    fun restoreQueue(context:Context,json:String,bundled:List<LocalTrack>):List<LocalTrack>{
        val grants=context.contentResolver.persistedUriPermissions.filter{it.isReadPermission}.map{it.uri.toString()}.toSet()
        val a=JSONArray(json)
        return (0 until a.length()).take(100).mapNotNull{i->runCatching{val t=LocalTrack.fromJson(a.getJSONObject(i));if(t.id.startsWith("before-the-rise:"))bundled.find{it.id==t.id}else t.takeIf{Uri.parse(it.uri).scheme=="content"&&it.uri in grants}}.getOrNull()}
    }
}
