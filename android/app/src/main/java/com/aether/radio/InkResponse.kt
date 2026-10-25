package com.aether.radio

/** A bounded partial-refresh presentation queue. Control/audio state never waits for the display. */
internal class InkResponse<T>(private val latencyMs:Long=120,private val settleMs:Long=400) {
    var displayed:T?=null;private set
    var previous:T?=null;private set
    private var shownKey:Any?=null
    private var targetKey:Any?=null
    private var pending:T?=null
    private var due=0L
    private var changedAt=Long.MIN_VALUE
    fun offer(value:T,key:Any,now:Long,instant:Boolean=false){
        if(instant||displayed==null){displayed=value;shownKey=key;targetKey=key;pending=null;previous=null;return}
        if(key!=targetKey){if(pending==null)due=now+latencyMs;pending=value;targetKey=key}
        if(pending!=null&&now>=due){previous=displayed;displayed=pending;shownKey=targetKey;pending=null;changedAt=now}
        if(now-changedAt>=settleMs&&changedAt!=Long.MIN_VALUE)previous=null
    }
    fun residual(now:Long):Float=if(previous==null)0f else .16f*(1f-((now-changedAt).toFloat()/settleMs).coerceIn(0f,1f))
    fun animating(now:Long)=pending!=null||residual(now)>0f
    fun clear(){displayed=null;previous=null;pending=null;shownKey=null;targetKey=null}
}
