package com.aether.radio

import android.content.Context
import android.net.Uri
import androidx.media3.extractor.metadata.id3.ApicFrame
import androidx.media3.extractor.metadata.id3.Id3Decoder
import androidx.media3.extractor.metadata.id3.TextInformationFrame

/** Bounded Media3 fallback for valid ID3 tags the platform retriever does not expose. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
object Id3Artwork{
    data class Tags(val title:String="",val album:String="",val artist:String="",val picture:ByteArray?=null)
    fun read(context:Context,uri:Uri):Tags=runCatching{
        context.contentResolver.openInputStream(uri)?.use{source->
            val input=java.io.DataInputStream(source);val head=ByteArray(10);input.readFully(head)
            if(head[0]!=73.toByte()||head[1]!=68.toByte()||head[2]!=51.toByte())return Tags()
            if((6..9).any{head[it].toInt() and 0x80!=0})return Tags()
            val size=(6..9).fold(0){sum,i->(sum shl 7) or (head[i].toInt() and 127)}
            if(size !in 1..20*1024*1024)return Tags()
            val bytes=ByteArray(size+10);head.copyInto(bytes);input.readFully(bytes,10,size)
            val data=Id3Decoder().decode(bytes,bytes.size)?:return Tags()
            var title="";var album="";var artist="";var art:ByteArray?=null
            for(i in 0 until data.length())when(val frame=data[i]){
                is TextInformationFrame->when(frame.id){"TIT2","TT2"->title=frame.values.firstOrNull().orEmpty();"TALB","TAL"->album=frame.values.firstOrNull().orEmpty();"TPE1","TP1"->artist=frame.values.firstOrNull().orEmpty()}
                is ApicFrame->if(art==null||frame.pictureType==3)art=frame.pictureData
            }
            Tags(title,album,artist,art)
        }?:Tags()
    }.getOrDefault(Tags())
}
