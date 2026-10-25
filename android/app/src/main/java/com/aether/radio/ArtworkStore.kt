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
        var sample=1;while(maxOf(bounds.outWidth,bounds.outHeight)/sample>4096)sample*=2
        var bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.size,BitmapFactory.Options().apply{inSampleSize=sample})?:return ""
        val largest=maxOf(bitmap.width,bitmap.height)
        if(largest>2560){val scaled=Bitmap.createScaledBitmap(bitmap,bitmap.width*2560/largest,bitmap.height*2560/largest,true);if(scaled!==bitmap)bitmap.recycle();bitmap=scaled}
        val folder=File(context.filesDir,"artwork").apply{mkdirs()}
        val digest=MessageDigest.getInstance("SHA-256").apply{update(source.toString().toByteArray());update(bytes)}
        val key=digest.digest().joinToString(""){"%02x".format(it)}
        val target=File(folder,"$key.jpg");val temp=File(folder,"$key.tmp")
        try{temp.outputStream().use{bitmap.compress(Bitmap.CompressFormat.JPEG,96,it)};check(temp.renameTo(target))}finally{bitmap.recycle();temp.delete()}
        Uri.fromFile(target).toString()
    }.getOrDefault("")
    fun load(context:Context,value:String,size:Int):Bitmap?=runCatching{
        val uri=Uri.parse(value)
        fun input()=when(uri.scheme){
            "asset"->context.assets.open(uri.path!!.removePrefix("/"))
            "file"->{val file=File(uri.path!!).canonicalFile;require(file.path.startsWith(File(context.filesDir,"artwork").canonicalPath+File.separator));file.inputStream()}
            else->error("Unsupported cover source")
        }
        val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true};input().use{BitmapFactory.decodeStream(it,null,bounds)}
        require(bounds.outWidth in 1..40000&&bounds.outHeight in 1..40000)
        var sample=1;while(maxOf(bounds.outWidth,bounds.outHeight)/sample>size*2)sample*=2
        input().use{BitmapFactory.decodeStream(it,null,BitmapFactory.Options().apply{inSampleSize=sample})}
    }.getOrNull()
}
