package com.aether.radio

import android.app.Instrumentation
import org.json.JSONObject

/** All current widget variants are sizes of the same 2x2 provider. */
class WidgetVariantsSuite(private val test:Instrumentation,private val activity:MainActivity){
    fun run():JSONObject=ExternalWidgetSuite(test,activity).run()
}
