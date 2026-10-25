package com.aether.radio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.security.MessageDigest

/** Read actual embedded covers. Invalid/missing covers deliberately return the default-disc state. */
object ArtworkStore {
    fun saveEmbedded(context:Context,source:Uri,bytes:ByteArray?):String=runCatching{
        if(bytes==null||bytes.size>20*1024*1024)return ""
        val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
        require(bounds.outWidth in 1..40000&&bounds.outHeight in 1..40000)
        val folder=File(context.filesDir,"artwork").apply{mkdirs()}
        val digest=MessageDigest.getInstance("SHA-256").apply{update(source.toString().toByteArray());update(bytes)}
        val key=digest.digest().joinToString(""){String.format("%02x",it)}
        val extension=when(bounds.outMimeType){"image/png"->"png";"image/webp"->"webp";"image/jpeg"->"jpg";else->"img"}
        val target=File(folder,"$key.$extension");val temp=File(folder,"$key.tmp")
        // Keep the original embedded artwork bytes. Thumbnail sizing is a separate decode operation.
        try{temp.outputStream().use{it.write(bytes)};check(temp.renameTo(target))}finally{temp.delete()}
        Uri.fromFile(target).toString()
    }.getOrDefault("")
    fun load(context:Context,value:String,size:Int):Bitmap?=runCatching{
        require(size>0)
        val uri=Uri.parse(value)
        fun input()=when(uri.scheme){
            "asset"->context.assets.open(uri.path!!.removePrefix("/"))
            "file"->{val file=File(uri.path!!).canonicalFile;require(file.path.startsWith(File(context.filesDir,"artwork").canonicalPath+File.separator));file.inputStream()}
            else->error("Unsupported cover source")
        }
        val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true};input().use{BitmapFactory.decodeStream(it,null,bounds)}
        require(bounds.outWidth in 1..40000&&bounds.outHeight in 1..40000)
        var sample=1;while(minOf(bounds.outWidth,bounds.outHeight)/(sample*2)>=size)sample*=2
        input().use{BitmapFactory.decodeStream(it,null,BitmapFactory.Options().apply{inSampleSize=sample;inScaled=false;inPreferredConfig=Bitmap.Config.ARGB_8888})}
    }.getOrNull()
}
