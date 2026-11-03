package me.hufman.androidautoidrive.phoneui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.webkit.*
import com.google.gson.Gson
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.logging.RoutePoint

@SuppressLint("SetJavaScriptEnabled")
class RouteMapView(context: Context, segments: List<List<RoutePoint>>) : WebView(context) {
    init {
        settings.javaScriptEnabled=true
        settings.allowFileAccess=false; settings.allowContentAccess=false
        settings.cacheMode=WebSettings.LOAD_DEFAULT
        settings.userAgentString="AAIdrive-430d/1.4.4 (https://github.com/abtifmx/AAIdrive)"
        webViewClient=object: WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest): Boolean {
                if(request.url.scheme=="https") runCatching { context.startActivity(Intent(Intent.ACTION_VIEW,request.url)) }
                return true
            }
        }
        setOnTouchListener { _, event ->
            parent?.requestDisallowInterceptTouchEvent(event.action != android.view.MotionEvent.ACTION_UP)
            false
        }
        val js=context.assets.open("route-map/leaflet.js").bufferedReader().use { it.readText() }
        val css=context.assets.open("route-map/leaflet.css").bufferedReader().use { it.readText() }
        val points=Gson().toJson(segments.map { part -> part.map { listOf(it.lat,it.lon,it.speedKmh) } })
        val speedLabel=Gson().toJson(context.getString(R.string.tl_speed))
        val missingSpeed=Gson().toJson(context.getString(R.string.tl_route_speed_missing))
        val zoomIn=Gson().toJson(context.getString(R.string.tl_zoom_in))
        val zoomOut=Gson().toJson(context.getString(R.string.tl_zoom_out))
        contentDescription=context.getString(R.string.tl_route)
        val html="""<!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1">
            <style>$css html,body{height:100%;margin:0;background:#e3eceb}body{display:flex;flex-direction:column}
            #map{flex:1;min-height:0}.leaflet-control-attribution{font-size:10px}
            #speed-legend{padding:7px 10px;color:#152634;background:#f7fafc;font:12px sans-serif}
            #speed-scale{height:7px;margin:4px 0;border-radius:4px;background:linear-gradient(90deg,#0474d0,#0a9e80,#ddab03,#eb701d,#cd274b)}
            #speed-ticks{display:flex;justify-content:space-between}.missing{color:#475569;font-size:11px}
            </style></head><body><div id="map"></div><div id="speed-legend" hidden></div><script>$js</script><script>
            var parts=$points, flat=parts.flat();
            var legend=document.getElementById('speed-legend');
            legend.textContent=$speedLabel+' (km/h)';
            var hasSpeed=parts.some(p=>p.some((v,i)=>i>0 && v[2]!=null && p[i-1][2]!=null));
            if(hasSpeed){
              legend.insertAdjacentHTML('beforeend','<div id="speed-scale"></div><div id="speed-ticks"><span>0</span><span>40</span><span>80</span><span>120</span><span>160+</span></div>');
            }
            if(!hasSpeed || flat.some(p=>p[2]==null)){
              var missing=document.createElement('div');missing.className='missing';missing.textContent=$missingSpeed;legend.appendChild(missing);
            }
            legend.hidden=false;
            var map=L.map('map',{preferCanvas:true,zoomControl:false});
            // Initialize the view before adding paths, so the renderer has a sized pane.
            map.fitBounds(L.latLngBounds(flat),{padding:[24,24],maxZoom:17});
            L.control.zoom({zoomInTitle:$zoomIn,zoomOutTitle:$zoomOut}).addTo(map);
            L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:19,attribution:'&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'}).addTo(map);
            var renderer=L.canvas({padding:.25});
            function speedColor(a,b){
              if(a==null || b==null)return '#64748b';
              var stops=[[4,116,208],[10,158,128],[221,171,3],[235,112,29],[205,39,75]];
              var band=Math.min(160,Math.round((a+b)/8)*4)/40;
              var low=Math.min(3,Math.floor(band)),f=band-low;
              return 'rgb('+stops[low].map((c,i)=>Math.round(c+(stops[low+1][i]-c)*f)).join(',')+')';
            }
            function stroke(p,color,weight){L.polyline(p,{renderer:renderer,color:color,weight:weight,interactive:false}).addTo(map);}
            parts.forEach(p=>{if(p.length>1)stroke(p,'#fff',8)});
            parts.forEach(function(p){
              if(p.length>1){
                var first=0,color=speedColor(p[0][2],p[1][2]);
                for(var i=2;i<p.length;i++){
                  var next=speedColor(p[i-1][2],p[i][2]);
                  if(next!==color){stroke(p.slice(first,i),color,5);first=i-1;color=next;}
                }
                stroke(p.slice(first),color,5);
              }else if(p.length==1)L.circleMarker(p[0],{renderer:renderer,radius:4,color:'#087f8c',fillOpacity:1}).addTo(map)});
            if(flat.length){
              L.circleMarker(flat[0],{renderer:renderer,radius:7,color:'#ffffff',weight:3,fillColor:'#16865b',fillOpacity:1}).addTo(map);
              L.circleMarker(flat[flat.length-1],{renderer:renderer,radius:7,color:'#ffffff',weight:3,fillColor:'#e38d42',fillOpacity:1}).addTo(map);
            }
            new ResizeObserver(function(){map.invalidateSize({pan:false});}).observe(document.getElementById('map'));
            </script></body></html>""".trimIndent()
        loadDataWithBaseURL("https://appassets.androidplatform.net/",html,"text/html","UTF-8",null)
    }
}
