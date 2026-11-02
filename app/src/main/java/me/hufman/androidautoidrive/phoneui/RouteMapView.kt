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
        val points=Gson().toJson(segments.map { part -> part.map { listOf(it.lat,it.lon) } })
        val zoomIn=Gson().toJson(context.getString(R.string.tl_zoom_in))
        val zoomOut=Gson().toJson(context.getString(R.string.tl_zoom_out))
        contentDescription=context.getString(R.string.tl_route)
        val html="""<!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1">
            <style>$css html,body,#map{height:100%;margin:0;background:#e3eceb}.leaflet-overlay-pane svg{overflow:visible}.leaflet-control-attribution{font-size:10px}</style>
            </head><body><div id="map"></div><script>$js</script><script>
            var map=L.map('map',{preferCanvas:false,zoomControl:false});
            L.control.zoom({zoomInTitle:$zoomIn,zoomOutTitle:$zoomOut}).addTo(map);
            L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:19,attribution:'&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'}).addTo(map);
            var parts=$points;var lines=L.featureGroup();var flat=parts.flat();
            if(flat.length>1)L.polyline(flat,{color:'#087f8c',weight:3,opacity:.45,dashArray:'6 8'}).addTo(lines);
            parts.forEach(function(p){
              if(p.length>1)L.polyline(p,{color:'#087f8c',weight:5,opacity:.95,lineCap:'round',lineJoin:'round'}).addTo(lines);
              else if(p.length==1)L.circleMarker(p[0],{radius:4,color:'#087f8c',fillOpacity:1}).addTo(lines)});
            lines.addTo(map);
            if(flat.length){
            L.circleMarker(flat[0],{radius:7,color:'#ffffff',weight:3,fillColor:'#16865b',fillOpacity:1}).addTo(map);
            L.circleMarker(flat[flat.length-1],{radius:7,color:'#ffffff',weight:3,fillColor:'#e38d42',fillOpacity:1}).addTo(map);
            function fit(){map.invalidateSize();map.fitBounds(lines.getBounds(),{padding:[24,24],maxZoom:17});}
            fit();setTimeout(fit,300);window.addEventListener('resize',fit);}
            </script></body></html>""".trimIndent()
        loadDataWithBaseURL("https://appassets.androidplatform.net/",html,"text/html","UTF-8",null)
    }
}
