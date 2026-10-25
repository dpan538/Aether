package com.aether.radio

import java.util.Locale

/** Display labels only; directory/filter identity always retains its original source string. */
object UiWords{
    private val zh=Locale.SIMPLIFIED_CHINESE
    private val countries by lazy{Locale.getISOCountries().associate{val l=Locale.Builder().setRegion(it).build();l.getDisplayCountry(Locale.ENGLISH).lowercase() to l.getDisplayCountry(zh)}}
    private val languages by lazy{Locale.getISOLanguages().associate{val l=Locale.forLanguageTag(it);l.getDisplayLanguage(Locale.ENGLISH).lowercase() to l.getDisplayLanguage(zh)}}
    private val aliases=mapOf("the united states of america" to "美国","united states" to "美国","uk" to "英国","united kingdom" to "英国","american english" to "美式英语","british english" to "英式英语","español argentina" to "阿根廷西班牙语","español" to "西班牙语","deutsch" to "德语","mandarin" to "普通话","cantonese" to "粤语","chinese" to "中文")
    private val genres=mapOf("blasmusik" to "管乐","classic hits" to "经典金曲","adult contemporary" to "成人当代","classical" to "古典","classic" to "经典","pop" to "流行","rock" to "摇滚","jazz" to "爵士","news" to "新闻","talk" to "谈话","music" to "音乐","ambient" to "氛围","electronic" to "电子","alternative" to "另类","experimental" to "实验","dance" to "舞曲","blues" to "蓝调","country" to "乡村","folk" to "民谣","easy listening" to "轻音乐","oldies" to "怀旧金曲","soul" to "灵魂乐","hip hop" to "嘻哈","hip-hop" to "嘻哈","metal" to "金属","world" to "世界音乐","christian" to "基督教","religious" to "宗教","instrumental" to "器乐","soundtrack" to "原声","chillout" to "驰放","new music" to "新音乐","80s" to "80 年代","90s" to "90 年代","00s" to "00 年代","70s" to "70 年代","60s" to "60 年代","classic rock" to "经典摇滚","classical music" to "古典音乐","comedy" to "喜剧","sports" to "体育","smooth jazz" to "柔和爵士","lounge" to "沙发音乐","reggae" to "雷鬼","schlager" to "德语流行","regional" to "地方节目")
    fun option(key:String,value:String,cn:Boolean):String{
        if(key=="ui-language")return if(value=="zh")"中文"else"English"
        if(value.isBlank())return if(cn)when(key){"region"->"全部地区";"genre"->"全部类型";else->"全部语言"}else when(key){"region"->"ALL REGIONS";"genre"->"ALL GENRES";else->"ALL LANGUAGES"}
        if(!cn)return value.uppercase(Locale.ROOT)
        return value.split(',').joinToString("、"){part->val v=part.trim();val k=v.lowercase(Locale.ROOT);aliases[k]?:when(key){"region"->countries[k];"genre"->genres[k];else->languages[k]}?:v}
    }
    fun value(key:String,value:String,cn:Boolean)=if(value.isBlank())""else option(key,value,cn)
    fun connection(value:Connection,cn:Boolean)=if(!cn)value.name.replace('_',' ')else when(value){Connection.CONNECTED->"播放中";Connection.PAUSED->"已暂停";Connection.CONNECTING->"连接中";Connection.BUFFERING->"缓冲中";Connection.DISCONNECTED->"未连接";Connection.ERROR->"暂不可用"}
    fun screen(value:String,cn:Boolean):String{
        if(!cn)return value
        return mapOf("NET" to "电台","FILE" to "文件","PRESET" to "预设","STATIONS" to "个结果","SEARCH" to "搜索","STREAM" to "音频信息","LOCAL TIME" to "时长","DEVICE TIME" to "设备时间","DEVICE TIME · LIVE" to "设备时间","10 BAND / PCM" to "10 频段 / PCM","STEREO FREQUENCY MONITOR" to "立体声音频监测","CONNECTED" to "播放中","DISCONNECTED" to "未连接","PAUSED" to "已暂停","LIVE" to "直播","LOCAL AUDIO" to "本地音乐","DURATION" to "时长","Stereo" to "立体声","Mono" to "单声道")[value]?:value
    }
}
