package com.aether.radio

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import org.json.JSONArray
import org.json.JSONObject

class MainActivity:Activity() {
    lateinit var session:Session;private set
    lateinit var surface:AetherView;private set
    private var audio:AudioSession?=null
    private var musicDialog:MusicLibraryDialog?=null
    private var musicSourceDialog:android.app.AlertDialog?=null
    private var bundledAlbum:List<LocalTrack> = emptyList()
    private var importedTracks:List<LocalTrack> = emptyList()
    private val musicWorker=java.util.concurrent.Executors.newSingleThreadExecutor()
    private var destroyed=false
    private var savedQueueStamp=""
    private var repository:RadioRepository?=null
    private var updater:Updates?=null
    private var searchField:EditText?=null
    private val main=Handler(Looper.getMainLooper())
    private val debugTick=object:Runnable{override fun run(){if(BuildConfig.DEBUG&&::surface.isInitialized)reportState();main.postDelayed(this,1000)}}
    private val deviceTick=object:Runnable{override fun run(){if(!session.reference&&session.powered){runCatching{DeviceReadings.read(this@MainActivity)}.getOrNull()?.let{if(it!=session.deviceReadings){session.deviceReadings=it;session.changed()}}};main.postDelayed(this,15000)}}
    private var pendingSearch:Runnable?=null
    private var pendingInput:Runnable?=null
    private var directoryQuery:String?=null
    private var directoryInterrupted=false
    private val preferences by lazy {getSharedPreferences("aether-live",MODE_PRIVATE)}
    private var savedPreferenceState=""
    private fun stationJson(s:Station)=JSONObject().put("id",s.id).put("name",s.name).put("region",s.region).put("language",s.language).put("genre",s.genre).put("stream",s.stream)
    private fun stationFromJson(s:JSONObject)=Station(s.getString("id"),s.getString("name"),s.optString("region"),language=s.optString("language"),genre=s.optString("genre"),stream=s.optString("stream").takeIf{it.startsWith("https://")})
    private fun savePreferences(){
        if(session.reference)return
        val station=session.station?.let{stationJson(it).toString()}.orEmpty()
        val queueStamp="${session.isLocal}:${session.localIndex}:"+session.localQueue.joinToString("|"){it.id}
        if(queueStamp!=savedQueueStamp){savedQueueStamp=queueStamp;preferences.edit().putBoolean("local-source",session.isLocal).putInt("local-index",session.localIndex).putString("local-queue",JSONArray(session.localQueue.map{it.json()}).toString()).apply()}
        val stamp=listOf(session.volume,session.unmutedVolume,session.tempo,session.language,session.spectrumEnabled,station).joinToString("|")
        if(stamp==savedPreferenceState)return;savedPreferenceState=stamp
        preferences.edit().putInt("volume",session.volume).putInt("unmuted",session.unmutedVolume).putInt("tempo",session.tempo).putString("language",session.language).putString("last-station",station).putBoolean("spectrum-enabled",session.spectrumEnabled).apply()
    }
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        window.setDecorFitsSystemWindows(false)
        window.attributes=window.attributes.apply{layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES}
        session=Session(BuildConfig.DEBUG&&intent.getStringExtra("mode")=="reference")
        if(BuildConfig.DEBUG)applyScenario(intent.getStringExtra("scenario")?:"modern-ink")
        if(!session.reference){
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
            bundledAlbum=LocalMusic.bundled(this)
            importedTracks=runCatching{LocalMusic.restoreQueue(this,preferences.getString("imported-tracks","[]")?:"[]",bundledAlbum)}.getOrDefault(emptyList())
            session.localQueue=runCatching{LocalMusic.restoreQueue(this,preferences.getString("local-queue","[]")?:"[]",bundledAlbum)}.getOrDefault(emptyList())
            session.localIndex=preferences.getInt("local-index",0).coerceIn(0,(session.localQueue.size-1).coerceAtLeast(0))
            session.isLocal=preferences.getBoolean("local-source",false)&&session.localQueue.isNotEmpty()
            session.localDurationMs=session.localTrack?.durationMs?:0
            repository=RadioRepository(this)
            session.onPower={if(!it){cancelDirectory();updater?.cancel()}else{if(directoryInterrupted||session.catalog.isEmpty())searchDirectory(session.query,false);updater?.let{u->session.updateUi=u.ui}}}
            session.catalog=repository!!.cached()
            audio=AudioSession(this,session)
            updater=Updates(applicationContext){status->if(session.powered){session.updateUi=status;session.changed()}}
            session.onUpdateAction={action->when(action){"check"->updater?.check();"download"->updater?.download();"install"->updater?.install(this);"cancel"->updater?.cancel()}}
        }
        surface=AetherView(this,session)
        surface.musicSources={showMusicSources()}
        session.onSpectrum={surface.invalidate()}
        surface.touchTrace=BuildConfig.DEBUG&&intent.getBooleanExtra("touch_trace",false)
        if(BuildConfig.DEBUG&&intent.hasExtra("preset_font"))surface.painter.presetFont=intent.getStringExtra("preset_font")?.takeUnless{it=="Arimo"}
        surface.onFirstFrame={filesDir.resolve("frame-ready.txt").writeText(intent.getStringExtra("scenario")?:"modern-ink");reportState()}
        surface.onResourcesReady={surface.beginRefresh();sampleControls();reportState()}
        val redraw=session.onChange
        session.onChange={redraw();savePreferences();if(!session.reference&&session.powered&&directoryQuery!=session.query)searchDirectory(session.query,true);if(!session.enabled){searchField?.clearFocus();searchField?.windowToken?.let{(getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(it,0)}};reportState()}
        val layout=FrameLayout(this).apply{setBackgroundColor(Color.rgb(8,10,12))}
        layout.addView(surface,FrameLayout.LayoutParams(-1,-1))
        searchField=EditText(this).apply{
            alpha=0f;setSingleLine(true);inputType=android.text.InputType.TYPE_CLASS_TEXT;imeOptions=android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
            setText(session.query)
            addTextChangedListener(object:TextWatcher {
                override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}
                override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){
                    if(!session.enabled)return
                    session.query=s.toString();session.listOffset=0
                    pendingInput?.let{main.removeCallbacks(it)}
                    pendingInput=Runnable{session.changed()}.also{main.postDelayed(it,90)}
                }
                override fun afterTextChanged(s:Editable?){}
            })
            setOnEditorActionListener{_,_,_->surface.requestFocus();window.insetsController?.hide(WindowInsets.Type.ime() or WindowInsets.Type.systemBars());true}
        }
        layout.addView(searchField,FrameLayout.LayoutParams(1,1))
        surface.search={if(searchField?.text?.toString()!=session.query)searchField?.setText(session.query);searchField?.requestFocus();(getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(searchField,InputMethodManager.SHOW_IMPLICIT)}
        setContentView(layout)
        surface.post {window.insetsController?.let{it.hide(WindowInsets.Type.systemBars());it.systemBarsBehavior=WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE}}
        if(!session.reference)searchDirectory(session.query,false)
    }
    private fun showMusicLibrary(){
        if(session.reference||!session.enabled||isFinishing||destroyed)return
        musicDialog?.dismiss()
        musicDialog=MusicLibraryDialog(this,session,bundledAlbum,{audio?.samplePosition()}).also{it.show()}
    }
    private fun showMusicSources(){
        if(session.reference||!session.enabled||destroyed)return
        val cn=session.language=="zh"
        val options=mutableListOf("Before the Rise — Dai Pan",if(cn)"设备上的音乐" else "Music on this device")
        if(session.isLocal&&session.station!=null)options.add(if(cn)"返回电台" else "Return to radio")
        musicSourceDialog=android.app.AlertDialog.Builder(android.view.ContextThemeWrapper(this,R.style.MusicSheetTheme)).setItems(options.toTypedArray()){_,which->when(which){0->showMusicLibrary();1->showDeviceMusic();2->session.station?.let{session.select(it.id)}}}.create().also{it.show()}
    }
    private fun showDeviceMusic(){
        if(!session.enabled)return
        val options=importedTracks.map{it.title}+if(session.language=="zh")"选择音频文件…" else "Choose audio files…"
        musicSourceDialog=android.app.AlertDialog.Builder(android.view.ContextThemeWrapper(this,R.style.MusicSheetTheme)).setItems(options.toTypedArray()){_,which->if(which<importedTracks.size)session.selectLocal(importedTracks,which)else chooseAudioFiles()}.create().also{it.show()}
    }
    private fun chooseAudioFiles(){
        musicDialog?.dismiss()
        runCatching{startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type="audio/*";putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)},71)}.onFailure{android.widget.Toast.makeText(this,if(session.language=="zh")"没有可用的文件选择器" else "No audio document picker available",android.widget.Toast.LENGTH_LONG).show()}
    }
    @Deprecated("Activity document result is retained for this platform Activity")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode!=71)return
        if(resultCode!=RESULT_OK||data==null){showDeviceMusic();return}
        val uris=(data.clipData?.let{clip->(0 until clip.itemCount).map{clip.getItemAt(it).uri}}?:listOfNotNull(data.data)).distinct().take(100)
        val permission=data.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION
        musicWorker.execute{
            val loaded=mutableListOf<LocalTrack>();var failures=0
            uris.forEach{uri->runCatching{
                require(uri.scheme=="content")
                require(permission!=0){"Audio document has no read grant"}
                contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)
                LocalMusic.imported(this,uri)
            }.onSuccess{loaded.add(it)}.onFailure{failures++}}
            main.post{if(!destroyed){
                if(loaded.isNotEmpty()){
                    importedTracks=(importedTracks+loaded).distinctBy{it.id}.takeLast(100)
                    preferences.edit().putString("imported-tracks",JSONArray(importedTracks.map{it.json()}).toString()).apply()
                    session.selectLocal(loaded,0)
                }
                if(failures>0)android.widget.Toast.makeText(this,if(session.language=="zh")"$failures 个文件无法读取，请重新选择音频文件。" else "$failures audio documents could not be opened.",android.widget.Toast.LENGTH_LONG).show()
                if(loaded.isEmpty())showDeviceMusic()
            }}
        }
    }
    private fun applyScenario(name:String){
        session.light=name in listOf("modern-lit","modern-settings","modern-language-menu","modern-genre-menu","modern-mute","preset004")
        if(name=="immersive")session.page=1
        if(name=="modern-settings"||name=="modern-language-menu")session.view="settings"
        if(name=="modern-language-menu")session.menu="ui-language"
        if(name=="modern-genre-menu")session.menu="genre"
        if(name=="modern-mute")session.volume=0
        if(name=="modern-off")session.powered=false
        if(name=="preset004")session.selected=3
        if(name=="locked")session.locked=true
        if(name=="empty-results")session.query="no-such-aether-station"
        if(name=="tempo-min")session.tempo=50
        if(name=="tempo-max")session.tempo=150
        if(name=="volume-max")session.volume=30
        intent.getStringExtra("query")?.let{session.query=it}
    }
    private fun sampleControls(){
        if(!BuildConfig.DEBUG)return
        val lightTime=intent.getIntExtra("light_time",-1)
        if(lightTime>=0){session.light=true;surface.frontlight=android.view.animation.PathInterpolator(.22f,.08f,.24f,1f).getInterpolation((lightTime/1050f).coerceIn(0f,1f));surface.invalidate()}
        if(intent.getBooleanExtra("animate_light",false))surface.setLight(true)
        if(intent.getBooleanExtra("animate_power",false))surface.power()
        if(intent.getBooleanExtra("motion",false)&&!surface.reducedMotion){session.motion=true;surface.invalidate()}
        val phase=intent.getLongExtra("phase_time",-1);if(phase>=0)surface.testTime=phase
        val refresh=intent.getLongExtra("refresh_time",-1);if(refresh>=0)surface.sampleRefresh(refresh)
        intent.getStringExtra("stream")?.takeIf{!session.reference&&it.startsWith("https://")}?.let{url->
            val station=Station("verification:stream","Verification stream",stream=url)
            session.savedStations[station.id]=station;session.select(station.id)
        }
    }
    private fun cancelDirectory(){
        pendingSearch?.let{main.removeCallbacks(it)};repository?.cancel();directoryInterrupted=true
    }
    private fun searchDirectory(query:String,debounce:Boolean){
        directoryQuery=query;directoryInterrupted=false
        pendingSearch?.let{main.removeCallbacks(it)}
        val work=Runnable{repository?.search(query){stations,error->
            val previous=session.station?.id
            if(stations.isNotEmpty()||error==null){session.catalog=stations;session.selected=stations.indexOfFirst{it.id==previous}.coerceAtLeast(0)}
            session.directoryIssue=error;session.changed();reportState()
        }}
        pendingSearch=work;main.postDelayed(work,if(debounce)350 else 0)
    }
    private fun reportState(){
        if(!BuildConfig.DEBUG)return
        val d=resources.displayMetrics
        val payload=JSONObject().put("mode",if(session.reference)"reference" else "live").put("width",d.widthPixels).put("height",d.heightPixels).put("density",d.density).put("densityDpi",d.densityDpi).put("fontScale",resources.configuration.fontScale).put("api",android.os.Build.VERSION.SDK_INT).put("model",android.os.Build.MODEL).put("catalogCount",session.catalog.size).put("connection",session.connection.name).put("issue",session.issue)
            .put("viewWidth",surface.width).put("viewHeight",surface.height).put("directoryIssue",session.directoryIssue)
            .put("committedRefresh",surface.committedRefresh).put("committedFrontlight",surface.committedFrontlight).put("committedPage",surface.committedPage).put("contentRevision",surface.contentRevision).put("committedRevision",surface.committedRevision).put("committedView",surface.committedView).put("committedOffset",surface.committedOffset)
            .put("assetsReady",surface.assetsReady).put("preparationMillis",surface.preparationMillis)
            .put("hapticAvailable",surface.haptics.available).put("hapticAttempts",surface.haptics.attempts).put("hapticAccepted",surface.haptics.accepted).put("hapticSuppressed",surface.haptics.suppressed).put("hapticTicks",surface.haptics.ticks).put("hapticPresses",surface.haptics.presses).put("hapticStops",surface.haptics.stops).put("lastHaptic",surface.haptics.last)
            .put("selected",session.selected).put("station",session.station?.name).put("volume",session.volume).put("powered",session.powered).put("locked",session.locked).put("light",session.light).put("frontlight",surface.frontlight).put("page",session.page).put("view",session.view).put("menu",session.menu).put("tempo",session.tempo).put("favorites",JSONArray(session.favorites)).put("filteredCount",session.filtered().size).put("query",session.query).put("metadata",session.metadata).put("decodedFormat",session.decodedFormat)
            .put("audioSource",if(session.isLocal)"local" else "radio").put("localTitle",session.localTrack?.title).put("localIndex",session.localIndex).put("localQueueCount",session.localQueue.size).put("musicDialogOpen",musicDialog?.isShowing?:false)
            .put("playerCount",if(audio==null)0 else 1).put("isPlaying",audio?.player?.isPlaying?:false).put("positionMs",audio?.player?.currentPosition?:0).put("playIntent",session.playIntent).put("playbackSpeed",audio?.player?.playbackParameters?.speed?:1f).put("playbackPitch",audio?.player?.playbackParameters?.pitch?:1f)
            .put("language",session.language).put("about",session.about).put("updateMessage",session.updateMessage).put("settingsScroll",session.settingsScroll).put("motion",session.motion).put("tuningMode",session.mode).put("region",session.region).put("genre",session.genre).put("stationLanguage",session.stationLanguage).put("refreshing",surface.refreshing).put("phaseSeconds",surface.painter.phase)
            .put("presentationPlaying",session.playing)
            .put("spectrumEnabled",session.spectrumEnabled).put("spectrumSamples",session.spectrumFrame?.samples?:0).put("spectrumRms",session.spectrumFrame?.rms?:0).put("spectrumLevels",JSONArray(session.spectrumFrame?.levels?.toList()?:emptyList<Float>()))
            .put("updateOpen",session.updateOpen).put("updatePhase",session.updateUi.phase.name).put("updateCached",session.updateUi.cached).put("updateRequests",updater?.downloadRequests?:0).put("updateReuse",updater?.reusedPackages?:0)
            .put("presetIds",JSONArray(session.presetPool().drop(session.presetStart).take(4).map{it.id})).put("presetNames",JSONArray(session.presetPool().drop(session.presetStart).take(4).map{it.name})).put("presetStart",session.presetStart).put("selectedId",session.station?.id).put("mediaId",audio?.player?.currentMediaItem?.mediaId).put("visible",JSONArray(session.filtered().drop(session.listOffset).take(4).map{it.name})).put("artworkReady",surface.painter.mainDisc!=null&&surface.painter.smallDisc!=null&&(surface.testTime==null||!session.motion||kotlin.math.abs(surface.painter.smallDiscPhase-surface.painter.phase)<.0001f)).put("reducedMotion",surface.reducedMotion)
        surface.rootWindowInsets?.let{val i=it.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout());payload.put("insets",JSONArray(listOf(i.left,i.top,i.right,i.bottom)))}
        filesDir.resolve("runtime-state.json").writeText(payload.toString(2));android.util.Log.i("AetherState",payload.toString())
    }
    override fun onNewIntent(intent:Intent){super.onNewIntent(intent);if(BuildConfig.DEBUG&&(intent.hasExtra("scenario")||intent.hasExtra("mode"))){setIntent(intent);recreate()}}
    override fun onWindowFocusChanged(hasFocus:Boolean){super.onWindowFocusChanged(hasFocus);if(hasFocus)window.insetsController?.hide(WindowInsets.Type.systemBars())}
    override fun onResume(){super.onResume();if(::surface.isInitialized)surface.resume();updater?.returnedToApp(this);if(!session.reference&&session.powered&&directoryInterrupted)searchDirectory(session.query,false);main.post(deviceTick);if(BuildConfig.DEBUG)main.postDelayed(debugTick,1000)}
    override fun onStop(){main.removeCallbacks(deviceTick);main.removeCallbacks(debugTick);if(::surface.isInitialized)surface.pause();audio?.pause();cancelDirectory();super.onStop()}
    override fun onDestroy(){destroyed=true;musicDialog?.dismiss();musicSourceDialog?.dismiss();musicWorker.shutdownNow();pendingInput?.let{main.removeCallbacks(it)};pendingSearch?.let{main.removeCallbacks(it)};surface.close();audio?.close();repository?.close();updater?.close();super.onDestroy()}
}
