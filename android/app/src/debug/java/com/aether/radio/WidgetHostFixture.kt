package com.aether.radio

/** Internal framework widget host, never included in a production variant. */
class WidgetHostFixture:android.app.Activity(){
    lateinit var host:android.appwidget.AppWidgetHost;private set
    lateinit var widget:android.appwidget.AppWidgetHostView;private set
    override fun onCreate(savedInstanceState:android.os.Bundle?){
        super.onCreate(savedInstanceState);window.setDecorFitsSystemWindows(false)
        val id=intent.getIntExtra("widget-id",-1);host=android.appwidget.AppWidgetHost(this,9001);host.startListening()
        val info=android.appwidget.AppWidgetManager.getInstance(this).getAppWidgetInfo(id)!!;widget=host.createView(this,id,info)
        val frame=android.widget.FrameLayout(this).apply{setBackgroundColor(android.graphics.Color.rgb(230,230,226))}
        val density=resources.displayMetrics.density
        frame.addView(widget,android.widget.FrameLayout.LayoutParams((440*density).toInt(),(240*density).toInt(),android.view.Gravity.CENTER))
        setContentView(frame);frame.post{window.insetsController?.hide(android.view.WindowInsets.Type.systemBars())}
    }
    override fun onDestroy(){if(::host.isInitialized)host.stopListening();super.onDestroy()}
}
