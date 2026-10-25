package com.aether.radio

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class DeviceReadings(val time:String,val weekday:String,val date:String,val year:String,val zone:String,val battery:Int?,val batteryCelsius:Float?,val network:String,val weekdayZh:String="",val dateZh:String="") {
    companion object {
        fun read(context:Context):DeviceReadings {
            val now=ZonedDateTime.now()
            val battery=context.registerReceiver(null,IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level=battery?.getIntExtra(BatteryManager.EXTRA_LEVEL,-1)?:-1
            val scale=battery?.getIntExtra(BatteryManager.EXTRA_SCALE,-1)?:-1
            val celsius=if(battery?.hasExtra(BatteryManager.EXTRA_TEMPERATURE)==true)battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE,0)/10f else null
            val manager=context.getSystemService(ConnectivityManager::class.java)
            val net=manager.getNetworkCapabilities(manager.activeNetwork)
            val network=when{
                net==null->"OFFLINE"
                net.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)->"WI-FI"
                net.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)->"CELLULAR"
                net.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)->"ETHERNET"
                else->"NETWORK"
            }
            fun format(pattern:String)=now.format(DateTimeFormatter.ofPattern(pattern,Locale.ENGLISH)).uppercase()
            return DeviceReadings(format("HH:mm"),format("EEEE"),format("MMMM d"),format("yyyy"),format("z"),if(level>=0&&scale>0)level*100/scale else null,celsius,network,now.format(DateTimeFormatter.ofPattern("EEEE",Locale.SIMPLIFIED_CHINESE)),now.format(DateTimeFormatter.ofPattern("M月d日",Locale.SIMPLIFIED_CHINESE)))
        }
    }
}
