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
    private var live:LivePlayback?=null
    private var bound=false
    private var foreground=false
    val uiReady get()=::surface.isInitialized
    private var uiChange:(()->Unit)?=null
    private val connection=object:android.content.ServiceConnection{
        override fun onServiceConnected(name:android.content.ComponentName,binder:android.os.IBinder){
            if(destroyed)return
            live=(binder as PlaybackService.LocalBinder).service.live
            session=live!!.session;audio=live!!.audio;repository=live!!.repository
            bundledAlbum=live!!.bundledAlbum;importedTracks=live!!.importedTracks
            if(BuildConfig.DEBUG&&intent.hasExtra("scenario"))applyScenario(intent.getStringExtra("scenario")!!)
            if(intent.getBooleanExtra("show-player",false))session.page=1
            attachLiveUi();createInterface()
        }
        override fun onServiceDisconnected(name:android.content.ComponentName){audio=null;live=null}
    }

    private var inkSettings:InkSettingsView?=null
    private var feedback:FeedbackClient?=null
    private var musicDialog:MusicLibraryDialog?=null
    private var musicSourceDialog:MusicSourceDialog?=null
    private var bundledAlbum:List<LocalTrack> = emptyList()
    private var importedTracks:List<LocalTrack> = emptyList()
    private val musicWorker=java.util.concurrent.Executors.newSingleThreadExecutor()
    private var systemBack:Any?=null
    private var destroyed=false
    private var repository:RadioRepository?=null
    private var updater:Updates?=null
    private var searchField:EditText?=null
    private val main=Handler(Looper.getMainLooper())
    private val debugTick=object:Runnable{override fun run(){if(BuildConfig.DEBUG&&::surface.isInitialized)reportState();main.postDelayed(this,1000)}}
    private val deviceTick=object:Runnable{override fun run(){if(::session.isInitialized&&!session.reference&&session.powered){runCatching{DeviceReadings.read(this@MainActivity)}.getOrNull()?.let{if(it!=session.deviceReadings){session.deviceReadings=it;session.changed()}}};main.postDelayed(this,15000)}}
    private var pendingSearch:Runnable?=null
    private var pendingInput:Runnable?=null
    private var directoryQuery:String?=null
    private var directoryInterrupted=false
    private fun savePreferences(){live?.savePreferences()}
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        if(android.os.Build.VERSION.SDK_INT>=33){val back=android.window.OnBackInvokedCallback{navigateBack()};systemBack=back;onBackInvokedDispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,back)}
        window.setDecorFitsSystemWindows(false)
        window.attributes=window.attributes.apply{layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES}
        if(BuildConfig.DEBUG&&intent.getStringExtra("mode")=="reference"){
            session=Session(true);applyScenario(intent.getStringExtra("scenario")?:"modern-ink");createInterface()
        }else{
            setContentView(android.widget.TextView(this).apply{text="漫频";textSize=26f;gravity=Gravity.CENTER;setTextColor(Color.LTGRAY);setBackgroundColor(Color.rgb(8,10,12))})
            bound=bindService(Intent(this,PlaybackService::class.java).setAction(PlaybackService.LOCAL_BIND),connection,BIND_AUTO_CREATE)
        }
    }
    private fun attachLiveUi(){
        session.onPower={if(!it){cancelDirectory();updater?.cancel()}else{if(directoryInterrupted||session.catalog.isEmpty())searchDirectory(session.query,false);updater?.let{u->session.updateUi=u.ui}}}
        updater=Updates(applicationContext){status->if(!destroyed&&session.powered){session.updateUi=status;session.changed()}}
        session.onUpdateAction={action->when(action){"check"->updater?.check();"download"->updater?.download();"install"->updater?.install(this);"cancel"->updater?.cancel()}}
    }
    private fun createInterface(){
        surface=AetherView(this,session)
        if(!session.reference)feedback=FeedbackClient(applicationContext){if(!destroyed)session.changed()}
        surface.musicSources={showMusicSources()}
        surface.languageChoices={inkSettings?.openLanguageMenu()}
        surface.onVisualFrame={inkSettings?.alignWithSurface()}
        session.onSpectrum={surface.invalidate()}
        surface.touchTrace=BuildConfig.DEBUG&&intent.getBooleanExtra("touch_trace",false)
        if(BuildConfig.DEBUG&&intent.hasExtra("preset_font"))surface.painter.presetFont=intent.getStringExtra("preset_font")?.takeUnless{it=="Arimo"}
        surface.onFirstFrame={filesDir.resolve("frame-ready.txt").writeText(intent.getStringExtra("scenario")?:"modern-ink");reportState()}
        surface.onResourcesReady={if(!session.reference)MusicWidget.publishPreview(applicationContext);surface.beginRefresh();sampleControls();inkSettings?.place();inkSettings?.refresh();reportState()}
        val redraw=session.onChange
        uiChange={if(foreground){redraw();inkSettings?.refresh();inkSettings?.place()};savePreferences();if(foreground&&!session.reference&&session.powered&&directoryQuery!=session.query)searchDirectory(session.query,true);if(!session.enabled){searchField?.clearFocus();searchField?.windowToken?.let{(getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(it,0)}};reportState()}
        session.onChange=uiChange!!
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
        feedback?.let{client->inkSettings=InkSettingsView(this,session,surface,client).also{view->layout.addView(view,FrameLayout.LayoutParams(1,1));view.refresh()}}
        surface.addOnLayoutChangeListener{_,_,_,_,_,_,_,_,_->inkSettings?.place()}
        setContentView(layout)
        surface.post {window.insetsController?.let{it.hide(WindowInsets.Type.systemBars());it.systemBarsBehavior=WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE}}
        session.uiVisible=foreground
        if(foreground){surface.resume();main.post(deviceTick);if(BuildConfig.DEBUG)main.postDelayed(debugTick,1000)}
        if(!session.reference)searchDirectory(session.query,false)
    }
    private fun showMusicLibrary(selected:LocalAlbum?=live?.albums()?.firstOrNull()){
        if(session.reference||!session.enabled||isFinishing||destroyed)return
        musicDialog?.dismiss()
        val album=selected?:return
        val owner=live?:return
        musicDialog=MusicLibraryDialog(this,session,album.tracks,{audio?.samplePosition()},album,{owner.isStarred(album.key)},{owner.toggleAlbumStar(album.key)},{owner.deleteAlbum(album);musicDialog?.dismiss();showMusicSources()}).also{it.show()}
    }
    private fun showMusicSources(){
        if(session.reference||!session.enabled||destroyed)return
        importedTracks=live?.importedTracks?:importedTracks
        musicSourceDialog?.dismiss()
        val owner=live?:return
        musicSourceDialog=MusicSourceDialog(this,session,owner.albums(),owner::isStarred,{showMusicLibrary(it)},{chooseAudioFiles()},if(owner.albums().none{it.bundled})({owner.restoreBundledAlbum();showMusicSources()})else null).also{it.show()}
    }
    private fun chooseAudioFiles(){
        musicDialog?.dismiss();session.musicError=""
        runCatching{startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type="audio/*";putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)},71)}.onFailure{session.musicError=if(session.language=="zh")"没有可用的文件选择器。"else"No audio document picker available.";showMusicSources()}
    }
    @Deprecated("Activity document result is retained for this platform Activity")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode!=71)return
        if(resultCode!=RESULT_OK||data==null){showMusicSources();return}
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
                    live?.addImported(loaded);importedTracks=live?.importedTracks?:loaded
                    session.selectLocal(loaded,0)
                }
                if(failures>0){session.musicError=if(session.language=="zh")"$failures 个文件无法读取，请重新选择音频文件。"else"$failures audio documents could not be opened.";showMusicSources()}
                if(loaded.isEmpty())showMusicSources()
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
        if(!BuildConfig.DEBUG||!uiReady)return
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
    private fun navigateBack(){
        if(::session.isInitialized){
            if(session.menu!=null){session.menu=null;session.changed();return}
            if(session.view=="settings"&&session.updateOpen){session.updateOpen=false;session.changed();return}
            if(session.view=="settings"&&session.settingsPage!="menu"){session.settingsPage="menu";session.changed();return}
            if(session.view=="settings"){session.showView(session.lastBrowseView);return}
            if(session.page==1){session.page=0;session.changed();return}
        }
        finish()
    }
    override fun onNewIntent(intent:Intent){super.onNewIntent(intent);if(intent.getBooleanExtra("show-player",false)&&::session.isInitialized){session.page=1;session.changed()};if(BuildConfig.DEBUG&&(intent.hasExtra("scenario")||intent.hasExtra("mode"))){setIntent(intent);recreate()}}
    override fun onWindowFocusChanged(hasFocus:Boolean){super.onWindowFocusChanged(hasFocus);if(hasFocus)window.insetsController?.hide(WindowInsets.Type.systemBars())}
    override fun onResume(){super.onResume();foreground=true;if(::session.isInitialized)session.uiVisible=true;if(::surface.isInitialized){surface.resume();session.changed();main.post(deviceTick);if(BuildConfig.DEBUG)main.postDelayed(debugTick,1000)};updater?.returnedToApp(this);if(::session.isInitialized&&!session.reference&&session.powered&&directoryInterrupted)searchDirectory(session.query,false)}
    override fun onStop(){foreground=false;if(::session.isInitialized)session.uiVisible=false;main.removeCallbacks(deviceTick);main.removeCallbacks(debugTick);if(::surface.isInitialized)surface.pause();live?.savePosition();cancelDirectory();super.onStop()}
    override fun onDestroy(){if(android.os.Build.VERSION.SDK_INT>=33)(systemBack as? android.window.OnBackInvokedCallback)?.let{onBackInvokedDispatcher.unregisterOnBackInvokedCallback(it)};destroyed=true;musicDialog?.dismiss();musicSourceDialog?.dismiss();musicWorker.shutdownNow();pendingInput?.let{main.removeCallbacks(it)};pendingSearch?.let{main.removeCallbacks(it)};if(::surface.isInitialized)surface.close();if(::session.isInitialized&&session.onChange===uiChange){session.onChange={};session.onSpectrum={};session.onPower={};session.onUpdateAction={}};updater?.close();feedback?.close();if(bound){unbindService(connection);bound=false};super.onDestroy()}
}
