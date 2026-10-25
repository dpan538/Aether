package com.aether.radio

/** Library identity is independent of the currently playing queue. */
data class LocalAlbum(val key:String,val title:String,val artist:String,val tracks:List<LocalTrack>,val bundled:Boolean=false) {
    val coverUri get()=tracks.firstOrNull{it.coverUri.isNotBlank()}?.coverUri.orEmpty()
}

object LocalAlbums {
    const val BUNDLED="bundle:before-the-rise"
    fun imported(tracks:List<LocalTrack>):List<LocalAlbum> = tracks.groupBy {
        if(it.album.isBlank())"track:${it.id}" else "album:${it.album.trim()}\u0000${it.artist.trim()}"
    }.map{(key,items)->LocalAlbum(key,items.first().album.ifBlank{items.first().title},items.first().artist,items)}
}
