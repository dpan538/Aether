package io.github.dpan538.widgethost;

import android.app.Activity;
import android.appwidget.*;
import android.content.*;
import android.os.*;
import android.util.SizeF;
import android.view.*;
import android.widget.FrameLayout;
import android.widget.TextView;
import org.json.*;
import java.util.*;

/** A different UID from the player: real RemoteViews IPC and system-dispatched touches. */
public class HostActivity extends Activity {
    static AppWidgetHost sharedHost;static int hostUsers=0;static int maxHostUsers=0;
    boolean ownsHost=false;
    final Handler handler=new Handler(Looper.getMainLooper());
    AppWidgetHost host; AppWidgetHostView widget; int id=-1; boolean resumed=false;
    final String player="io.github.dpan538.aether";
    int width,height; String style; String caseTag=""; boolean exact;
    final Runnable snapshot=new Runnable(){public void run(){dump();handler.postDelayed(this,200);}};
    public void onCreate(Bundle saved){
        if("probe".equals(getIntent().getStringExtra("command"))){
            super.onCreate(saved);
            try{
                JSONObject probe=new JSONObject().put("uid",android.os.Process.myUid()).put("time",SystemClock.uptimeMillis());
                try{sendBroadcast(new Intent("com.aether.radio.WIDGET.toggle").setComponent(new ComponentName(player,"com.aether.radio.WidgetPlaybackReceiver")));probe.put("requestReturned",true);}catch(SecurityException e){probe.put("rejected",e.toString());}
                try(java.io.FileOutputStream out=openFileOutput("probe.json",MODE_PRIVATE)){out.write(probe.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));}
            }catch(Exception e){throw new RuntimeException(e);}
            finish();return;
        }
        super.onCreate(saved);if("reset".equals(getIntent().getStringExtra("command"))){new AppWidgetHost(this,9500).deleteHost();finish();return;}if("finish".equals(getIntent().getStringExtra("command"))){finish();return;}getWindow().setDecorFitsSystemWindows(false);
        caseTag=getIntent().getStringExtra("case");if(caseTag==null)caseTag="";style=getIntent().getStringExtra("style");if(style==null)style="card";
        width=getIntent().getIntExtra("width",600);height=getIntent().getIntExtra("height",300);exact=getIntent().getBooleanExtra("exact",true);
        if(sharedHost==null)sharedHost=new AppWidgetHost(getApplicationContext(),9500);
        host=sharedHost;ownsHost=true;hostUsers++;maxHostUsers=Math.max(maxHostUsers,hostUsers);host.startListening();id=host.allocateAppWidgetId();
        ComponentName component=new ComponentName(player,"com.aether.radio."+(style.equals("compact")?"CompactMusicWidget":style.equals("strip")?"StripMusicWidget":"MusicWidget"));
        Bundle options=options();
        if(AppWidgetManager.getInstance(this).bindAppWidgetIdIfAllowed(id,component,options))show();
        else startActivityForResult(new Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id).putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER,component).putExtra(AppWidgetManager.EXTRA_APPWIDGET_OPTIONS,options),1);
    }
    Bundle options(){Bundle b=new Bundle();b.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,width);b.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH,width);b.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,height);b.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,height);b.putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES,exact?new ArrayList<>(Arrays.asList(new SizeF(width,height))):new ArrayList<SizeF>());return b;}
    protected void onNewIntent(Intent intent){
        super.onNewIntent(intent);
        if("finish".equals(intent.getStringExtra("command")))finish();
        else if("foreground".equals(intent.getStringExtra("command"))){}
        else if("capture".equals(intent.getStringExtra("command"))&&widget!=null){
            String name=intent.getStringExtra("name");final String file=(name==null?"widget":name.replaceAll("[^a-zA-Z0-9_-]",""))+".png";
            int rootId=0;try{rootId=getPackageManager().getResourcesForApplication(player).getIdentifier("widget_root","id",player);}catch(Exception e){return;}
            View root=widget.findViewById(rootId);int[] xy=new int[2];root.getLocationInWindow(xy);
            android.graphics.Bitmap b=android.graphics.Bitmap.createBitmap(root.getWidth(),root.getHeight(),android.graphics.Bitmap.Config.ARGB_8888);
            PixelCopy.request(getWindow(),new android.graphics.Rect(xy[0],xy[1],xy[0]+root.getWidth(),xy[1]+root.getHeight()),b,result->{try{if(result==PixelCopy.SUCCESS){try(java.io.FileOutputStream out=openFileOutput(file,MODE_PRIVATE)){b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}}}catch(Exception e){}finally{b.recycle();}},handler);
        }else if("resize".equals(intent.getStringExtra("command"))&&widget!=null){
            caseTag=intent.getStringExtra("case");width=intent.getIntExtra("width",width);height=intent.getIntExtra("height",height);exact=intent.getBooleanExtra("exact",false);
            float d=getResources().getDisplayMetrics().density;ViewGroup.LayoutParams p=widget.getLayoutParams();p.width=Math.round(width*d);p.height=Math.round(height*d);widget.setLayoutParams(p);
            widget.post(()->{if(exact)widget.updateAppWidgetSize(options(),Arrays.asList(new SizeF(width,height)));else widget.updateAppWidgetSize(options(),width,height,width,height);dump();});
        }else{setIntent(intent);recreate();}
    }
    protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(result==RESULT_OK)show();}
    void show(){
        widget=host.createView(this,id,AppWidgetManager.getInstance(this).getAppWidgetInfo(id));
        FrameLayout frame=new FrameLayout(this);frame.setBackgroundColor(0xffe6e6e2);
        float d=getResources().getDisplayMetrics().density;
        frame.addView(widget,new FrameLayout.LayoutParams(Math.round(width*d),Math.round(height*d),Gravity.CENTER));setContentView(frame);
        widget.post(()->{if(exact)widget.updateAppWidgetSize(options(),Arrays.asList(new SizeF(width,height)));else widget.updateAppWidgetSize(options(),width,height,width,height);getWindow().getInsetsController().hide(WindowInsets.Type.systemBars());});handler.post(snapshot);
    }
    JSONObject bounds(View v)throws Exception{
        int[] xy=new int[2];v.getLocationOnScreen(xy);
        JSONObject result=new JSONObject().put("x",xy[0]).put("y",xy[1]).put("w",v.getWidth()).put("h",v.getHeight()).put("enabled",v.isEnabled()).put("clickable",v.isClickable()).put("description",v.getContentDescription()).put("text",v instanceof TextView?((TextView)v).getText():"");
        if(v instanceof TextView){
            TextView text=(TextView)v;result.put("gravity",text.getGravity()).put("fontPadding",text.getIncludeFontPadding());
            if(text.getLayout()!=null&&text.getLineCount()>0){android.graphics.Paint.FontMetricsInt fm=text.getPaint().getFontMetricsInt();result.put("fontTop",text.getBaseline()+fm.top).put("fontBottom",text.getTotalPaddingTop()+text.getLayout().getLineBaseline(text.getLineCount()-1)+fm.bottom);}
        }
        if(v instanceof android.widget.ImageView){
            android.widget.ImageView image=(android.widget.ImageView)v;
            if(image.getDrawable()!=null){android.graphics.RectF icon=new android.graphics.RectF(image.getDrawable().getBounds());image.getImageMatrix().mapRect(icon);result.put("iconWidth",icon.width()).put("iconHeight",icon.height());}
        }
        return result;
    }
    void dump(){try{
        JSONObject j=new JSONObject().put("case",caseTag).put("resumed",resumed).put("style",style).put("widthDp",width).put("heightDp",height).put("uid",android.os.Process.myUid()).put("hostUsers",hostUsers).put("maxHostUsers",maxHostUsers);
        if(widget!=null){Bundle configured=AppWidgetManager.getInstance(this).getAppWidgetOptions(id);JSONObject configuredJson=new JSONObject();for(String key:configured.keySet())configuredJson.put(key,String.valueOf(configured.get(key)));j.put("options",configuredJson);j.put("host",bounds(widget));j.put("padding",new JSONArray(new int[]{widget.getPaddingLeft(),widget.getPaddingTop(),widget.getPaddingRight(),widget.getPaddingBottom()}));for(String n:new String[]{"widget_root","widget_art_open","widget_cover","widget_title","widget_album","widget_previous_cell","widget_play_cell","widget_next_cell","widget_time","widget_progress","widget_previous","widget_play","widget_next"}){int rid=getPackageManager().getResourcesForApplication(player).getIdentifier(n,"id",player);View v=widget.findViewById(rid);if(v!=null)j.put(n,bounds(v));}}
        android.util.AtomicFile state=new android.util.AtomicFile(new java.io.File(getFilesDir(),"state.json"));
        java.io.FileOutputStream out=state.startWrite();
        try{out.write(j.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));state.finishWrite(out);}catch(Exception e){state.failWrite(out);throw e;}
    }catch(Exception e){android.util.Log.e("WidgetHost",e.toString());}}
    protected void onResume(){super.onResume();resumed=true;dump();}
    protected void onPause(){resumed=false;dump();super.onPause();}
    protected void onDestroy(){handler.removeCallbacksAndMessages(null);if(host!=null&&ownsHost){if(id!=-1)host.deleteAppWidgetId(id);ownsHost=false;hostUsers--;if(hostUsers==0)host.stopListening();}super.onDestroy();}
}
