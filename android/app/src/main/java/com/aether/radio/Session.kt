package com.aether.radio

import kotlin.math.*
import java.text.Normalizer

data class Station(
    val id: String, val name: String, val region: String = "", val city: String = "",
    val language: String = "", val genre: String = "", val stream: String? = null,
    val programme: String? = null, val description: String? = null, val track: String? = null,
    val quality: String? = null, val localTime: String? = null, val mark: String = "—"
)

object ReferenceStations {
    val all = listOf(
        Station("ref:0", "FM 802", "JAPAN", "OSAKA", "Japanese", "Alternative", programme="NIGHT\nWINDOWS", description="夜间漫游 · Alternative / Electronic",track="Haru Aoki — Last Train Home",quality="128 kbps · AAC",localTime="22:14 JST",mark="802"),
        Station("ref:1", "BBC RADIO 1", "UK", "LONDON", "English", "New Music",programme="AFTER\nTHE CITY",description="城市余响 · Indie / New Music",track="Maya North — Slow Motion",quality="192 kbps · AAC",localTime="14:14 BST",mark="BBC 1"),
        Station("ref:2", "NTS RADIO", "UK", "LONDON", "English", "Experimental",programme="OPEN\nCIRCUIT",description="开放回路 · Ambient / Experimental",track="Mono Field — Soft Systems",quality="256 kbps · MP3",localTime="14:14 BST",mark="NTS"),
        Station("ref:3", "RADIO NOVA", "FRANCE", "PARIS", "French", "Soul",programme="LATE\nRENDEZVOUS",description="午夜相遇 · Soul / Global Sounds",track="Lucie Morel — À demi-mot",quality="128 kbps · AAC",localTime="15:14 CEST",mark="NOVA")
    )
}

enum class Connection { DISCONNECTED, CONNECTING, BUFFERING, CONNECTED, PAUSED, ERROR }
data class RetainedInk(val station:Station?,val metadata:String?,val format:String?,val connection:Connection,val playing:Boolean)

/** One state and one player, shared by both native scenes. Reference never opens audio. */
class Session(val reference: Boolean) {
    var catalog = if(reference) ReferenceStations.all else emptyList()
    var localQueue:List<LocalTrack> = emptyList()
    var localIndex=0
    var isLocal=false
    val localTrack get()=if(isLocal)localQueue.getOrNull(localIndex) else null
    val activeId get()=if(isLocal)localTrack?.id else station?.id
    var localPositionMs=0L
    var localDurationMs=0L
    var onLocalSelect:(List<LocalTrack>,Int)->Unit={_,_->}
    var onLocalSeek:(Long)->Unit={}
    fun selectLocal(queue:List<LocalTrack>,index:Int){
        if(!enabled||index !in queue.indices)return
        localQueue=queue.toList();localIndex=index;isLocal=true;localPositionMs=0;localDurationMs=queue[index].durationMs
        metadata=null;decodedFormat=null;issue=null;spectrumFrame=null;connection=Connection.CONNECTING;playIntent=true
        onLocalSelect(localQueue,index);changed()
    }
    fun seekLocal(position:Long){if(enabled&&isLocal)onLocalSeek(position.coerceIn(0,localDurationMs.coerceAtLeast(0)))}
    var selected = 0
    private var selectedStation:Station?=null
    val savedStations=mutableMapOf<String,Station>()
    val station get() = selectedStation ?: catalog.getOrNull(selected)
    fun restoreStation(value:Station){selectedStation=value;savedStations[value.id]=value}
    fun lookup(id:String?)=catalog.find{it.id==id}?:savedStations[id]
    @Volatile var powered = true
    var locked = false
    var light = false
    var volume = 20
    var unmutedVolume = 20
    var playing = reference
    var playIntent=reference
    var connection = Connection.DISCONNECTED
    var metadata: String? = null
    var decodedFormat: String? = null
    var issue: String? = null
    var directoryIssue:String?=null
    var retainedInk:RetainedInk?=null
    fun freezeInk(){retainedInk=RetainedInk(station,metadata,decodedFormat,connection,playing)}
    val favorites = if(reference) mutableListOf("ref:0", "ref:2", "ref:1", "ref:3") else mutableListOf()
    var view = "directory"
    var lastBrowseView="directory"
    var page = 0
    var menu: String? = null
    var language = "en"
    var query = ""
    var region = ""
    var genre = ""
    var stationLanguage = ""
    var mode = 0
    var tempo = 100
    var motion = false
    @Volatile var spectrumEnabled=!reference
    var spectrumFrame:SpectrumFrame?=null
    var deviceReadings:DeviceReadings?=null
    var onSpectrum:()->Unit={}
    var onTempo:(Int)->Unit={}
    var about = false
    var updateMessage = false
    var updateOpen=false
    var updateUi=UpdateUi()
    var onUpdateAction:(String)->Unit={}
    var tuningAngle = -125f
    var listOffset=0
    var presetOffset=0
    var menuOffset=0
    var settingsScroll=0f
    var onChange: () -> Unit = {}
    var onSelect: (Station) -> Unit = {}
    var onVolume: (Int) -> Unit = {}
    var onPlay: (Boolean) -> Unit = {}
    var onFavorites: () -> Unit = {}
    var onPower:(Boolean)->Unit={}
    val enabled get() = powered && !locked
    fun changed() = onChange()
    private val browseView get()=if(view=="settings")lastBrowseView else view
    fun filtered(): List<Station> = (if(browseView=="favorites")favorites.mapNotNull{lookup(it)} else catalog).filter {
        (query.isBlank() || normal(it.name+it.city+it.region).contains(normal(query))) &&
        (region.isBlank() || it.region==region) && (genre.isBlank() || it.genre==genre) &&
        (stationLanguage.isBlank() || it.language==stationLanguage) && (browseView!="favorites" || it.id in favorites)
    }
    fun showView(value:String){
        if(value=="settings"){if(view!="settings")lastBrowseView=view;view="settings"}
        else{view=value;lastBrowseView=value;listOffset=0}
        menu=null;changed()
    }
    fun tuningPool():List<Station> = filtered().ifEmpty{if(browseView=="favorites"&&favorites.isEmpty())catalog else emptyList()}
    fun canUse(action:String):Boolean=when{
        action=="device-power"->!locked
        action in listOf("device-lock","device-light")->powered
        !enabled->false
        action=="empty-preset"->false
        action.startsWith("preset:")->presetPool().getOrNull(action.substringAfter(':').toIntOrNull()?:-1)!=null
        action.startsWith("favorite-step:")->if(isLocal)localQueue.isNotEmpty() else favorites.any{lookup(it)!=null}
        action in listOf("previous","next")||action.startsWith("step:")->if(isLocal)localQueue.isNotEmpty() else tuningPool().isNotEmpty()
        action=="play-toggle"->if(isLocal)localTrack!=null else station!=null
        action=="favorite-toggle"->!isLocal&&station!=null
        action=="random-station"->filtered().isNotEmpty()
        else->true
    }
    val displayOrdinal:Int? get(){if(isLocal)return localIndex+1;val i=tuningPool().indexOfFirst{it.id==station?.id};return if(i<0)null else i+1}
    fun presetPool():List<Station> {
        val saved=favorites.mapNotNull{lookup(it)}
        return if(reference)saved else (saved+catalog).distinctBy{it.id}
    }
    val presetStart get()=presetOffset.coerceIn(0,(presetPool().size-4).coerceAtLeast(0))
    fun select(id: String) {
        if(!enabled)return
        val chosen=lookup(id)?:return
        val index=catalog.indexOfFirst {it.id==id}
        if(index>=0)selected=index
        isLocal=false;selectedStation=chosen;metadata=null;decodedFormat=null;issue=null;spectrumFrame=null
        if(!reference) {connection=Connection.CONNECTING;playIntent=true;onSelect(chosen)}
        changed()
    }
    fun step(direction: Int, favorite: Boolean=false) {
        if(!enabled)return
        if(isLocal){if(localQueue.isNotEmpty())selectLocal(localQueue,if(favorite)if(direction<0)0 else localQueue.lastIndex else Math.floorMod(localIndex+direction,localQueue.size));return}
        stepRadio(direction,favorite)
    }
    private fun stepRadio(direction:Int,favorite:Boolean=false){
        val pool=if(favorite) favorites.mapNotNull { id->lookup(id) } else tuningPool()
        if(pool.isEmpty())return
        val i=pool.indexOfFirst {it.id==station?.id}
        select(pool[if(i<0)if(direction>0)0 else pool.lastIndex else Math.floorMod(i+direction,pool.size)].id)
    }
    fun changeVolume(v: Int) {if(!enabled)return;val next=v.coerceIn(0,30);if(next==volume)return;volume=next;onVolume(volume);changed()}
    fun toggleMute() { if(volume>0){unmutedVolume=volume;changeVolume(0)}else changeVolume(unmutedVolume) }
    fun toggleFavorite() {if(!enabled||isLocal)return;station?.let {if(!favorites.remove(it.id)){favorites.add(it.id);savedStations[it.id]=it}else savedStations.remove(it.id)};presetOffset=presetOffset.coerceAtMost((favorites.size-4).coerceAtLeast(0));listOffset=0;onFavorites();changed()}
    fun togglePlay() {if(!enabled)return;playIntent=if(!reference&&connection in listOf(Connection.CONNECTING,Connection.BUFFERING))false else !playing;if(reference)playing=playIntent;onPlay(playIntent);changed()}
    fun changeTempo(value:Int){if(!enabled)return;val next=(value/5.0).roundToInt().times(5).coerceIn(50,150);if(next==tempo)return;tempo=next;onTempo(next);changed()}
    fun options(key:String):List<String> = when(key) {
        "region" -> listOf("")+catalog.map {it.region}.filter{it.isNotBlank()}.distinct()
        "genre" -> listOf("")+catalog.map {it.genre}.filter{it.isNotBlank()}.distinct()
        "language" -> listOf("")+catalog.map {it.language}.filter{it.isNotBlank()}.distinct()
        else -> listOf("en","zh")
    }
    fun filterValue(key:String)=when(key){"region"->region;"genre"->genre;"language"->stationLanguage;else->language}
    fun filter(key:String,value:String) {when(key){"region"->region=value;"genre"->genre=value;"language"->stationLanguage=value;else->language=value};menu=null;listOffset=0;menuOffset=0;changed()}
    fun tune(direction:Int) {
        if(!enabled)return
        view="directory";tuningAngle+=24*direction
        if(mode==0)stepRadio(direction)else {
            val key=listOf("station","region","genre","language")[mode]
            val opts=options(key);filter(key,opts[Math.floorMod(opts.indexOf(filterValue(key))+direction,opts.size)])
        }
        changed()
    }
    fun resetFilters(){query="";region="";genre="";stationLanguage="";listOffset=0;changed()}
    companion object {fun normal(s:String)=Normalizer.normalize(s,Normalizer.Form.NFKD).replace(Regex("\\p{M}"),"").lowercase().replace(Regex("[^\\p{L}\\p{N}]"),"")}
}

/** Fresh travel only. Reversal/idle/cancel discard residual; no delayed step queue. */
class DetentGate(val threshold:Double,val interval:Long,val maxDelta:Double) {
    private var travel=0.0; private var lastStep=Long.MIN_VALUE/2;private var lastInput=Long.MIN_VALUE/2
    fun push(delta:Double,now:Long):Int {
        if(!delta.isFinite())return 0
        if(now-lastInput>240 || sign(delta)!=sign(travel))travel=0.0
        lastInput=now;travel+=delta.coerceIn(-maxDelta,maxDelta)
        if(abs(travel)<threshold)return 0
        val direction=sign(travel).toInt();travel=0.0
        if(now-lastStep<interval)return 0
        lastStep=now;return direction
    }
    fun reset(){travel=0.0;lastInput=Long.MIN_VALUE/2}
}
