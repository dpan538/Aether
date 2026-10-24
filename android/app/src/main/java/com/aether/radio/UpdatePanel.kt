package com.aether.radio

import android.graphics.*
import org.json.JSONArray
import org.json.JSONObject

/** An ink panel inside the modern display; it adds no third page. */
class UpdatePanel(private val painter:ScenePainter,private val state:Session) {
    private val p=Paint(Paint.ANTI_ALIAS_FLAG)
    fun bounds():RectF = RectF(painter.scene(painter.name(state.light)).flattened().first{it.cls=="directory-screen"}.r).apply{inset(14f,14f)}
    fun nodes():List<SceneNode>{
        val r=bounds();val phase=state.updateUi.phase
        val action=when(phase){UpdatePhase.DOWNLOADING,UpdatePhase.CHECKING,UpdatePhase.VERIFYING->"cancel";UpdatePhase.AVAILABLE->"download";UpdatePhase.READY->"install";else->"check"}
        val items=listOf("back" to RectF(r.left+10,r.bottom-44,r.left+110,r.bottom-10),action to RectF(r.right-205,r.bottom-44,r.right-10,r.bottom-10))
        return items.map{(id,box)->SceneNode(JSONObject().put("id","update:$id").put("action","update:$id").put("label",label(id)).put("r",JSONArray(listOf(box.left,box.top,box.width(),box.height()))))}
    }
    private fun label(id:String)=if(state.language=="zh")when(id){"back"->"返回";"download"->"下载更新";"install"->"安装已验证包";"cancel"->"暂停";else->"检查更新"}else when(id){"back"->"Back";"download"->"Download update";"install"->"Install verified APK";"cancel"->"Pause";else->"Check again"}
    fun draw(c:Canvas,lit:Boolean){
        val r=bounds();val ui=state.updateUi;val zh=state.language=="zh"
        c.save();c.clipRect(r)
        p.color=if(lit)0xffe6e7df.toInt() else 0xffc2c8b8.toInt();c.drawRoundRect(r,5f,5f,p)
        p.color=0xff465b4b.toInt();p.typeface=painter.font(false,700);p.textSize=15f
        c.drawText(if(zh)"漫频 Aether · 仓库更新" else "AETHER · REPOSITORY UPDATES",r.left+16,r.top+25,p)
        p.strokeWidth=1f;c.drawLine(r.left+16,r.top+38,r.right-16,r.top+38,p)
        p.textSize=23f
        val title=if(zh)when(ui.phase){UpdatePhase.CHECKING->"正在检查仓库版本";UpdatePhase.DOWNLOADING->"正在下载更新";UpdatePhase.VERIFYING->"正在校验安装包与签名";UpdatePhase.AVAILABLE->"发现新版本";UpdatePhase.READY->"已验证 · 可安装";UpdatePhase.CURRENT->if(ui.message.startsWith("No APK"))"仓库尚无 APK 版本" else "暂无更高版本";UpdatePhase.ERROR->"更新暂不可用";else->"应用内更新"}else ui.message.ifBlank{"In-app updates"}
        text(c,title,r.left+16,r.top+74,r.width()-32,2,27f)
        p.textSize=14f;p.typeface=painter.font(false,400)
        val detail=ui.detail.ifBlank{if(zh)"首次安装后，后续版本可在这里下载并覆盖更新。" else "Future APK versions are downloaded and installed here."}
        text(c,detail,r.left+16,r.top+132,r.width()-32,3,20f)
        if(ui.phase==UpdatePhase.DOWNLOADING){
            p.color=0xff8f9d8c.toInt();c.drawRect(r.left+16,r.top+192,r.right-16,r.top+199,p)
            p.color=0xff46624c.toInt();c.drawRect(r.left+16,r.top+192,r.left+16+(r.width()-32)*ui.progress/100,r.top+199,p)
            p.textSize=13f;c.drawText("${ui.progress}%",r.left+16,r.top+218,p)
        }else {
            p.textSize=12f;p.color=0xff657461.toInt()
            text(c,if(zh)"同一已验证安装包会保留并复用。安装由 Android 系统确认，收藏和设置保留。" else "Verified APKs are retained and reused. Android confirms installation; favorites and settings are preserved.",r.left+16,r.top+203,r.width()-32,2,17f)
        }
        nodes().forEach{n->p.style=Paint.Style.STROKE;p.strokeWidth=1f;p.color=0xff5e725e.toInt();c.drawRoundRect(n.r,3f,3f,p);p.style=Paint.Style.FILL;p.textSize=14f;p.typeface=painter.font(false,700);p.textAlign=Paint.Align.CENTER;c.drawText(n.label,n.r.centerX(),n.r.centerY()+5,p);p.textAlign=Paint.Align.LEFT}
        c.restore()
    }
    private fun text(c:Canvas,value:String,x:Float,y:Float,width:Float,lines:Int,spacing:Float){
        var remaining=value.replace('\n',' ').take(1000);var yy=y
        repeat(lines){
            if(remaining.isBlank())return
            var end=p.breakText(remaining,true,width,null).coerceAtLeast(1)
            if(end<remaining.length&&state.language!="zh"){val space=remaining.lastIndexOf(' ',end);if(space>end/2)end=space}
            val tail=if(it==lines-1&&end<remaining.length)"…" else ""
            c.drawText(remaining.take(end).trim()+tail,x,yy,p);remaining=remaining.drop(end).trim();yy+=spacing
        }
    }
}
