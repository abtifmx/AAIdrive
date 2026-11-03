package me.hufman.androidautoidrive.phoneui.fragments

import android.os.Bundle
import android.view.*
import android.webkit.WebView
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.*
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.logging.*
import me.hufman.androidautoidrive.phoneui.*
import java.text.DateFormat
import java.util.Date
import java.util.Locale

class TripLogsFragment : Fragment() {
    private var content: LinearLayout? = null
    private var selected: String? = null
    private var more = false
    private var raw = false
    private var offset = 0
    private var loadJob: Job? = null
    private var exportId: String? = null
    private val maps = mutableListOf<WebView>()
    private var back: OnBackPressedCallback? = null
    private val export = registerForActivityResult(ActivityResultContracts.CreateDocument("application/x-ndjson")) { uri ->
        val id = exportId
        if (uri != null && id != null) {
            val context = requireContext().applicationContext
            viewLifecycleOwner.lifecycleScope.launch {
                val result = withContext(Dispatchers.IO) { runCatching {
                    context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                        TripLogger.store(context).events(id).forEach { writer.append(it.toString()).append('\n') }
                    } ?: error("output unavailable")
                } }
                toast(if(result.isSuccess) R.string.tl_exported else R.string.tl_failed)
            }
        }
    }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state); setHasOptionsMenu(true)
        selected=state?.getString("trip"); offset=state?.getInt("offset")?:0
        more=state?.getBoolean("more")?:false; raw=state?.getBoolean("raw")?:false
        exportId=state?.getString("export")
    }
    override fun onSaveInstanceState(state: Bundle) {
        super.onSaveInstanceState(state)
        state.putString("trip",selected); state.putInt("offset",offset)
        state.putBoolean("more",more); state.putBoolean("raw",raw); state.putString("export",exportId)
    }
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        val settings=menu.add(0,9001,0,R.string.tl_settings).setIcon(R.drawable.ic_settings_feather)
        settings.icon?.mutate()?.setTint(ContextCompat.getColor(requireContext(),R.color.telemetryText))
        settings.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        if(selected!=null && selected!=TripLogger.activeId)menu.add(0,9002,1,R.string.tl_delete).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
    }
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if(item.itemId==9001) { settings(); return true }
        if(item.itemId==9002) { deleteSelected(); return true }
        return super.onOptionsItemSelected(item)
    }
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View =
        ScrollView(requireContext()).apply {
            clipToPadding=false
            content=TelemetryUi.column(context)
            addView(content)
        }
    override fun onViewCreated(view: View, state: Bundle?) {
        back=object: OnBackPressedCallback(selected!=null) {
            override fun handleOnBackPressed() { selected=null; refresh() }
        }.also { requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner,it) }
    }
    override fun onResume() { super.onResume(); refresh() }
    override fun onDestroyView() {
        loadJob?.cancel(); maps.forEach { it.destroy() }; maps.clear()
        content=null; back=null; super.onDestroyView()
    }
    private fun toast(message: Int) { if(isAdded) Toast.makeText(requireContext(),message,Toast.LENGTH_LONG).show() }
    private fun date(time: Long)=DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(Date(time))
    private fun label(parent: LinearLayout, value: String, size: Float=15f, bold: Boolean=false) {
        parent.addView(TelemetryUi.text(requireContext(),value,size,bold))
    }
    private fun action(parent: LinearLayout, title: Int, block: ()->Unit) {
        parent.addView(TelemetryUi.button(requireContext(),getString(title),block))
    }
    private fun title(metric: Metric)=getString(metric.title)+(if(metric.suffix.isNotEmpty()) " · ${metric.suffix}" else "")
    private fun favorites()=TripLogger.preferences(requireContext()).getStringSet("dashboard",TripAnalytics.defaults)!!.toSet()
    private fun settings() {
        val covered=TripAnalytics.metrics.map{it.property}.toSet()
        val extra=TripLogger.properties.filter{it.propertyName !in covered}
        val ids=listOf("route")+TripAnalytics.metrics.map { it.id }+extra.map{"raw:${it.propertyName}"}
        val labels=(listOf(getString(R.string.tl_route))+TripAnalytics.metrics.map { title(it) }+extra.map{it.propertyName}).toTypedArray()
        val picked=favorites().toMutableSet()
        AlertDialog.Builder(requireContext()).setTitle(R.string.tl_settings)
            .setMultiChoiceItems(labels,ids.map { it in picked }.toBooleanArray()) { _,index,checked ->
                if(checked) picked.add(ids[index]) else picked.remove(ids[index])
            }.setNegativeButton(R.string.tl_cancel,null).setPositiveButton(R.string.tl_save) { _,_ ->
                TripLogger.preferences(requireContext()).edit().putStringSet("dashboard",picked).apply(); refresh()
            }.show()
    }
    private fun status(trip: TripLogStore.Trip)=getString(when(trip.state) {
        "läuft"->R.string.tl_running; "beendet"->R.string.tl_finished; else->R.string.tl_interrupted
    })
    private fun deleteSelected() {
        val id=selected?:return
        if(id==TripLogger.activeId)return
        val store=TripLogger.store(requireContext())
        AlertDialog.Builder(requireContext()).setMessage(R.string.tl_delete_confirm).setNegativeButton(R.string.tl_cancel,null)
            .setPositiveButton(R.string.tl_delete){_,_->viewLifecycleOwner.lifecycleScope.launch{
                val result=withContext(Dispatchers.IO){runCatching{store.delete(id)}}
                if(result.isSuccess){selected=null;refresh()}else toast(R.string.tl_failed)
            }}.show()
    }
    private fun metric(parent: LinearLayout, metric: Metric, analysis: TripAnalysis, start: Long, end: Long) {
        val card=TelemetryUi.card(requireContext(),parent,title(metric))
        val points=analysis.series[metric.id].orEmpty()
        val valid=points.filter { it.value.isFinite() }
        if(valid.isEmpty()) { label(card,getString(R.string.tl_no_data)); return }
        val chart=TelemetryChartView(requireContext(),metric,points,start,end)
        if(metric.kind==MetricKind.CURVE) {
            fun value(v:Double)=String.format(Locale.getDefault(),"%.1f %s",v,metric.unit)
            label(card,value(valid.last().value),28f,true)
            label(card,getString(R.string.tl_minmax,value(valid.minOf{it.value}),value(valid.maxOf{it.value})),13f)
        } else {
            label(card,chart.stateLabel(valid.last().value),24f,true)
            label(card,getString(R.string.tl_state),13f)
        }
        chart.layoutParams=LinearLayout.LayoutParams(-1,TelemetryUi.dp(requireContext(),if(metric.kind==MetricKind.STATE)180 else 210))
        card.addView(chart)
    }
    private fun route(parent: LinearLayout, analysis: TripAnalysis) {
        val card=TelemetryUi.card(requireContext(),parent,getString(R.string.tl_route))
        if(analysis.route.flatten().isEmpty()) { label(card,getString(R.string.tl_route_empty)); return }
        label(card,getString(R.string.tl_route_info),13f)
        val map=RouteMapView(requireContext(),analysis.route)
        map.layoutParams=LinearLayout.LayoutParams(-1,TelemetryUi.dp(requireContext(),340))
        maps.add(map); card.addView(map)
    }
    private fun refresh() {
        loadJob?.cancel()
        val context=requireContext().applicationContext
        val id=selected
        back?.isEnabled=id!=null
        requireActivity().invalidateOptionsMenu()
        loadJob=viewLifecycleOwner.lifecycleScope.launch {
            maps.forEach { it.destroy() }; maps.clear()
            content?.removeAllViews()
            val parent=content?:return@launch
            label(parent,getString(R.string.tl_loading))
            try {
                val store=TripLogger.store(context)
                val trips=withContext(Dispatchers.IO) { store.trips(TripLogger.activeId) }
                parent.removeAllViews()
                if(id==null) {
                    val prefs=TripLogger.preferences(context)
                    val statusText=getString(if(TripLogger.error!=null)R.string.tl_error else if(!prefs.getBoolean(TripLogger.ENABLED,true))R.string.tl_disabled
                        else if(TripLogger.activeId!=null)R.string.tl_recording else R.string.tl_ready)
                    val hero=TelemetryUi.hero(requireContext(),parent,getString(R.string.tl_trips),statusText,getString(R.string.tl_privacy),20f)
                    TelemetryUi.stats(requireContext(),hero,listOf(
                        getString(R.string.tl_stat_trips) to trips.size.toString(),
                        getString(R.string.tl_stat_time) to TelemetryUi.duration(trips.sumOf{it.durationMillis()}),
                        getString(R.string.tl_stat_size) to "${trips.sumOf{it.bytes}/1024} KB"),onHero=true)
                    val header=TelemetryUi.card(requireContext(),parent)
                    header.addView(SwitchMaterial(requireContext()).apply {
                        text=getString(R.string.tl_record); isChecked=prefs.getBoolean(TripLogger.ENABLED,true)
                        setOnCheckedChangeListener { _,enabled ->
                            prefs.edit().putBoolean(TripLogger.ENABLED,enabled).apply()
                            view?.postDelayed({if(content!=null) refresh()},500)
                        }
                    })
                    action(header,R.string.tl_refresh){refresh()}
                    if(trips.isEmpty()) label(parent,getString(R.string.tl_empty),18f)
                    trips.forEach { trip ->
                        val card=TelemetryUi.card(requireContext(),parent,date(trip.started))
                        label(card,TelemetryUi.duration(trip.durationMillis()),26f,true)
                        label(card,getString(R.string.tl_samples,trip.count,trip.bytes/1024),13f)
                        card.addView(TelemetryUi.pill(requireContext(),status(trip)))
                        card.isClickable=true; card.isFocusable=true
                        card.setOnClickListener {
                            selected=trip.id; more=false; raw=false; offset=0
                            (content?.parent as? ScrollView)?.scrollTo(0,0); refresh()
                        }
                    }
                } else {
                    val trip=trips.firstOrNull { it.id==id }
                    if(trip==null) { selected=null; refresh(); return@launch }
                    action(parent,R.string.tl_back){selected=null;refresh()}
                    val hero=TelemetryUi.hero(requireContext(),parent,status(trip),TelemetryUi.duration(trip.durationMillis()),date(trip.started))
                    TelemetryUi.stats(requireContext(),hero,listOf(
                        getString(R.string.tl_stat_readings) to trip.count.toString(),
                        getString(R.string.tl_stat_size) to "${trip.bytes/1024} KB"),onHero=true)
                    val actions=LinearLayout(requireContext()).apply{orientation=LinearLayout.HORIZONTAL
                        layoutParams=LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=TelemetryUi.dp(requireContext(),14)}}
                    listOf(R.string.tl_refresh,R.string.tl_export).forEach{title->
                        val button=TelemetryUi.button(requireContext(),getString(title)){
                            if(title==R.string.tl_refresh)refresh()else{exportId=id;export.launch("AAIdrive-${trip.started}.jsonl")}
                        }
                        button.layoutParams=LinearLayout.LayoutParams(0,-2,1f).apply{marginEnd=TelemetryUi.dp(requireContext(),4)}
                        actions.addView(button)
                    }
                    parent.addView(actions)
                    val end=trip.started+maxOf(1,trip.durationMillis())
                    val analysis=withContext(Dispatchers.IO){TripAnalytics.analyze(store.events(id),trip.started,end)}
                    val selectedMetrics=favorites()
                    label(parent,getString(R.string.tl_favorites),22f,true)
                    if(selectedMetrics.isEmpty()) label(parent,getString(R.string.tl_no_favorites))
                    if("route" in selectedMetrics) route(parent,analysis)
                    TripAnalytics.metrics.filter{it.id in selectedMetrics}.forEach{metric(parent,it,analysis,trip.started,end)}
                    selectedMetrics.filter{it.startsWith("raw:")}.sorted().forEach{key->
                        val property=key.removePrefix("raw:")
                        val card=TelemetryUi.card(requireContext(),parent,property)
                        val event=analysis.latest[property]
                        if(event==null)label(card,getString(R.string.tl_no_data))
                        else {
                            label(card,event.get("value").toString())
                            label(card,getString(if(event.get("cached")?.asBoolean==true)R.string.tl_cached else R.string.tl_last,date(event.get("timestamp").asLong)),12f)
                        }
                    }
                    action(parent,if(more)R.string.tl_less else R.string.tl_more){more=!more;refresh()}
                    if(more) {
                        if("route" !in selectedMetrics) route(parent,analysis)
                        TripAnalytics.metrics.filter{it.id !in selectedMetrics && analysis.series[it.id].orEmpty().any { p->p.value.isFinite() }}
                            .forEach{metric(parent,it,analysis,trip.started,end)}
                        action(parent,R.string.tl_raw){raw=!raw;refresh()}
                        if(raw) {
                            analysis.latest.toSortedMap().filterKeys{"raw:$it" !in selectedMetrics}.forEach{(property,event)->
                                val card=TelemetryUi.card(requireContext(),parent,property)
                                label(card,getString(if(event.get("cached")?.asBoolean==true)R.string.tl_cached else R.string.tl_last,date(event.get("timestamp").asLong)),12f)
                                label(card,event.get("value").toString(),13f)
                            }
                            val page=withContext(Dispatchers.IO){store.page(id,offset)}
                            if(offset>0)action(parent,R.string.tl_previous){offset=maxOf(0,offset-100);refresh()}
                            page.forEach{event->
                                val card=TelemetryUi.card(requireContext(),parent,"${date(event.get("timestamp").asLong)} · ${event.get("property").asString}")
                                label(card,event.get("value").toString(),13f)
                            }
                            if(offset+page.size<trip.count)action(parent,R.string.tl_next){offset+=100;refresh()}
                        }
                    }
                }
            } catch(e:CancellationException){throw e}
            catch(e:Exception){label(parent,getString(R.string.tl_failed))}
        }
    }
}
