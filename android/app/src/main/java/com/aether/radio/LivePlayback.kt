package com.aether.radio

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Owned by PlaybackService; no Activity, window or view is retained here. */
class LivePlayback(val context:Context) {
    val session=Session(false)
    val preferences=context.getSharedPreferences("aether-live",Context.MODE_PRIVATE)
    private var savedPreferenceState=""
    private var savedQueueStamp=""
    var bundledAlbum:List<LocalTrack> = emptyList();private set
    var importedTracks:List<LocalTrack> = emptyList();private set
    private val starred=preferences.getStringSet("starred-albums",emptySet()).orEmpty().toMutableSet()
    private var bundledHidden=preferences.getBoolean("bundled-hidden",false)
    fun albums():List<LocalAlbum> = (if(bundledHidden)emptyList()else listOf(LocalAlbum(LocalAlbums.BUNDLED,"Before the Rise","Dai Pan (潘岱)",bundledAlbum,true)))
        .plus(LocalAlbums.imported(importedTracks)).sortedByDescending{it.key in starred}
    fun isStarred(key:String)=key in starred
    fun toggleAlbumStar(key:String){if(!starred.remove(key))starred.add(key);preferences.edit().putStringSet("starred-albums",starred.toSet()).apply();session.changed()}
    fun deleteAlbum(album:LocalAlbum){
        val ids=album.tracks.map{it.id}.toSet()
        // Only app-library entries change. Never delete the source document.
        if(session.isLocal&&session.localTrack?.id in ids){
            audio.pause();session.playIntent=false;audio.player.clearMediaItems()
            session.localQueue=emptyList();session.localIndex=0;session.isLocal=false;session.localPositionMs=0;session.localDurationMs=0
            session.playbackPositionMs=0;session.spectrumFrame=null;session.metadata=null;session.decodedFormat=null
        }else if(session.localQueue.any{it.id in ids}){
            val current=session.localTrack?.id
            val removed=session.localQueue.indices.filter{session.localQueue[it].id in ids}
            session.localQueue=session.localQueue.filter{it.id !in ids}
            session.localIndex=session.localQueue.indexOfFirst{it.id==current}.coerceAtLeast(0)
            if(session.isLocal)removed.asReversed().forEach{audio.player.removeMediaItem(it)}
        }
        if(album.bundled){bundledHidden=true;preferences.edit().putBoolean("bundled-hidden",true).apply()}
        else{importedTracks=importedTracks.filter{it.id !in ids};persistImported()}
        starred.remove(album.key);preferences.edit().putStringSet("starred-albums",starred.toSet()).apply();session.changed();savePosition()
    }
    fun restoreBundledAlbum(){bundledHidden=false;preferences.edit().putBoolean("bundled-hidden",false).apply();session.changed()}
    private fun persistImported(){preferences.edit().putString("imported-tracks",JSONArray(importedTracks.map{it.json()}).toString()).apply()}
    val repository=RadioRepository(context)
    val audio:AudioSession
    private fun stationJson(s:Station)=JSONObject().put("id",s.id).put("name",s.name).put("region",s.region).put("language",s.language).put("genre",s.genre).put("stream",s.stream)
    private fun stationFromJson(s:JSONObject)=Station(s.getString("id"),s.getString("name"),s.optString("region"),language=s.optString("language"),genre=s.optString("genre"),stream=s.optString("stream").takeIf{it.startsWith("https://")})
    fun savePreferences(){
        if(session.reference)return
        val station=session.station?.let{stationJson(it).toString()}.orEmpty()
        val queueStamp="${session.isLocal}:${session.localIndex}:"+session.localQueue.joinToString("|"){it.id+it.coverUri}
        if(queueStamp!=savedQueueStamp){savedQueueStamp=queueStamp;preferences.edit().putBoolean("local-source",session.isLocal).putInt("local-index",session.localIndex).putString("local-queue",JSONArray(session.localQueue.map{it.json()}).toString()).putLong("local-position",session.localPositionMs).apply()}
        val stamp=listOf(session.volume,session.unmutedVolume,session.tempo,session.language,session.spectrumEnabled,station).joinToString("|")
        if(stamp==savedPreferenceState)return;savedPreferenceState=stamp
        preferences.edit().putInt("volume",session.volume).putInt("unmuted",session.unmutedVolume).putInt("tempo",session.tempo).putString("language",session.language).putString("last-station",station).putBoolean("spectrum-enabled",session.spectrumEnabled).apply()
    }
    init {
            session.volume=preferences.getInt("volume",20).coerceIn(0,30);session.unmutedVolume=preferences.getInt("unmuted",20).coerceIn(1,30)
            session.spectrumEnabled=preferences.getBoolean("spectrum-enabled",true)
            session.tempo=preferences.getInt("tempo",100).coerceIn(50,150);session.language=preferences.getString("language","en")?.takeIf{it in listOf("en","zh")}?:"en"
            runCatching{preferences.getString("last-station",null)?.takeIf{it.isNotBlank()}?.let{session.restoreStation(stationFromJson(JSONObject(it)))}}
            session.favorites.clear();runCatching{JSONArray(preferences.getString("favorites","[]")).let{a->for(i in 0 until a.length())session.favorites.add(a.getString(i))}}
            runCatching{JSONArray(preferences.getString("favorite-stations","[]")).let{a->for(i in 0 until a.length()){val s=a.getJSONObject(i);val station=Station(s.getString("id"),s.getString("name"),s.optString("region"),language=s.optString("language"),genre=s.optString("genre"),stream=s.optString("stream"));session.savedStations[station.id]=station}}}
            session.onFavorites={
                val saved=JSONArray(session.favorites.mapNotNull{session.lookup(it)}.map{s->JSONObject().put("id",s.id).put("name",s.name).put("region",s.region).put("language",s.language).put("genre",s.genre).put("stream",s.stream)})
                preferences.edit().putString("favorites",JSONArray(session.favorites).toString()).putString("favorite-stations",saved.toString()).apply()
            }
            bundledAlbum=LocalMusic.bundled(context)
            importedTracks=runCatching{LocalMusic.restoreQueue(context,preferences.getString("imported-tracks","[]")?:"[]",bundledAlbum)}.getOrDefault(emptyList())
            session.localQueue=runCatching{LocalMusic.restoreQueue(context,preferences.getString("local-queue","[]")?:"[]",bundledAlbum)}.getOrDefault(emptyList())
            session.localIndex=preferences.getInt("local-index",0).coerceIn(0,(session.localQueue.size-1).coerceAtLeast(0))
            session.isLocal=preferences.getBoolean("local-source",false)&&session.localQueue.isNotEmpty()
            session.localDurationMs=session.localTrack?.durationMs?:0

        session.catalog=repository.cached()
        audio=AudioSession(context,session)
        audio.restoreSelection(preferences.getLong("local-position",0));audio.samplePosition()
        session.observe{savePreferences()}
    }
    fun addImported(tracks:List<LocalTrack>){
        importedTracks=(importedTracks.filter{old->tracks.none{it.id==old.id}}+tracks).distinctBy{it.id}.takeLast(100)
        persistImported()
    }
    fun savePosition(){audio.samplePosition();preferences.edit().putLong("local-position",session.localPositionMs).apply()}
    fun close(){savePosition();session.onChange={};session.onSpectrum={};audio.close();repository.close()}
}
