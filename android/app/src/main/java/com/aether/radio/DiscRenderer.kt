package com.aether.radio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES30 as GL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors
import java.io.File
import java.security.MessageDigest

/** Offscreen GLES port of the approved original shader. It renders artwork only. */
class DiscRenderer(context:Context,private val delivered:(Boolean,Bitmap,Float)->Unit) {
    private val context=context.applicationContext
    private val fragmentSource by lazy{this.context.assets.open("disc.frag").bufferedReader().readText()}
    private val sourceHash by lazy{MessageDigest.getInstance("SHA-256").digest(fragmentSource.toByteArray()).take(12).joinToString(""){"%02x".format(it)}}
    private val worker=Executors.newSingleThreadExecutor()
    @Volatile private var closed=false
    @Volatile private var busy=false
    @Volatile private var printComplete=false
    fun render(print:Boolean,size:Int,timeSeconds:Float=0f) {
        if(closed||busy||print&&printComplete)return
        busy=true
        worker.execute {
            var result:Bitmap?=null
            try {
                val cache=if(print||timeSeconds==0f)File(context.cacheDir,"aether-disc-v2-$sourceHash-${if(print)1 else 0}-$size.png")else null
                if(cache?.exists()==true)result=BitmapFactory.decodeFile(cache.path)?.takeIf{it.width==size&&it.height==size}
                if(result==null){
                    result=draw(print,size,timeSeconds)
                    if(result!=null&&cache!=null&&!closed){
                        val temporary=File.createTempFile("aether-disc-",".tmp",context.cacheDir)
                        try{temporary.outputStream().use{result!!.compress(Bitmap.CompressFormat.PNG,100,it)};temporary.renameTo(cache)}finally{temporary.delete()}
                    }
                }
            }
            catch(error:Exception){android.util.Log.e("AetherDisc","Offscreen context unavailable",error)}
            finally {if(print&&result!=null)printComplete=true;busy=false}
            result?.let {if(!closed)delivered(print,it,timeSeconds)else it.recycle()}
        }
    }
    private fun draw(print:Boolean,size:Int,time:Float):Bitmap? {
        val display=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        check(EGL14.eglInitialize(display,IntArray(2),0,IntArray(2),0))
        val configs=arrayOfNulls<EGLConfig>(1)
        check(EGL14.eglChooseConfig(display,intArrayOf(EGL14.EGL_RENDERABLE_TYPE,64,EGL14.EGL_SURFACE_TYPE,EGL14.EGL_PBUFFER_BIT,EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,EGL14.EGL_ALPHA_SIZE,8,EGL14.EGL_NONE),0,configs,0,1,IntArray(1),0))
        val config=configs[0]!!
        val eglContext=EGL14.eglCreateContext(display,config,EGL14.EGL_NO_CONTEXT,intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION,3,EGL14.EGL_NONE),0)
        val surface=EGL14.eglCreatePbufferSurface(display,config,intArrayOf(EGL14.EGL_WIDTH,size,EGL14.EGL_HEIGHT,size,EGL14.EGL_NONE),0)
        check(EGL14.eglMakeCurrent(display,surface,surface,eglContext))
        var program=0
        try {
            fun shader(type:Int,source:String):Int {
                val s=GL.glCreateShader(type);GL.glShaderSource(s,source);GL.glCompileShader(s)
                val status=IntArray(1);GL.glGetShaderiv(s,GL.GL_COMPILE_STATUS,status,0)
                check(status[0]!=0){GL.glGetShaderInfoLog(s)};return s
            }
            val vertex=shader(GL.GL_VERTEX_SHADER,"#version 300 es\nin vec2 position;void main(){gl_Position=vec4(position,0.,1.);}")
            val fragment=shader(GL.GL_FRAGMENT_SHADER,fragmentSource)
            program=GL.glCreateProgram();GL.glAttachShader(program,vertex);GL.glAttachShader(program,fragment);GL.glLinkProgram(program)
            val status=IntArray(1);GL.glGetProgramiv(program,GL.GL_LINK_STATUS,status,0);check(status[0]!=0){GL.glGetProgramInfoLog(program)}
            GL.glUseProgram(program)
            val vertices=ByteBuffer.allocateDirect(48).order(ByteOrder.nativeOrder()).asFloatBuffer().put(floatArrayOf(-1f,-1f,1f,-1f,-1f,1f,-1f,1f,1f,-1f,1f,1f));vertices.position(0)
            val attr=GL.glGetAttribLocation(program,"position");GL.glEnableVertexAttribArray(attr);GL.glVertexAttribPointer(attr,2,GL.GL_FLOAT,false,0,vertices)
            GL.glUniform2f(GL.glGetUniformLocation(program,"resolution"),size.toFloat(),size.toFloat())
            GL.glUniform1f(GL.glGetUniformLocation(program,"time"),time)
            GL.glUniform1f(GL.glGetUniformLocation(program,"material"),if(print)1f else 0f)
            GL.glViewport(0,0,size,size);GL.glClearColor(0f,0f,0f,0f);GL.glClear(GL.GL_COLOR_BUFFER_BIT);GL.glDrawArrays(GL.GL_TRIANGLES,0,6)
            val pixels=ByteBuffer.allocateDirect(size*size*4).order(ByteOrder.nativeOrder());GL.glReadPixels(0,0,size,size,GL.GL_RGBA,GL.GL_UNSIGNED_BYTE,pixels)
            val rgba=IntArray(size*size);pixels.asIntBuffer().get(rgba)
            fun argb(p:Int)=(p and -0x1000000) or ((p and 255) shl 16) or (p and 0xff00) or ((p ushr 16) and 255)
            // Flip and swizzle in place, avoiding another 55 MiB array for the
            // full-resolution printed disc while both pages are resident.
            for(y in 0 until (size+1)/2)for(x in 0 until size){
                val top=y*size+x;val bottom=(size-1-y)*size+x
                val a=rgba[top];val b=rgba[bottom]
                rgba[top]=argb(b);if(top!=bottom)rgba[bottom]=argb(a)
            }
            val bitmap=Bitmap.createBitmap(rgba,size,size,Bitmap.Config.ARGB_8888)
            GL.glDeleteShader(vertex);GL.glDeleteShader(fragment)
            return bitmap
        } catch(e:Exception) {android.util.Log.e("AetherDisc","GLES artwork failed",e);return null}
        finally {
            if(program!=0)GL.glDeleteProgram(program)
            EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(display,surface);EGL14.eglDestroyContext(display,eglContext);EGL14.eglTerminate(display)
        }
    }
    fun close(){closed=true;worker.shutdown()}
}
