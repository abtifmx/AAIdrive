package me.hufman.androidautoidrive.phoneui.fragments

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.widget.*
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import io.bimmergestalt.idriveconnectkit.CDSProperty
import me.hufman.androidautoidrive.CarInformation
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.cds.CDSEventHandler
import me.hufman.androidautoidrive.phoneui.TelemetryUi
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class CdsLiveFragment : Fragment() {
    private data class Value(val json: String,val timestamp:Long,val cached:Boolean,val error:Boolean)
    private val properties=CDSProperty.values().sortedBy{it.ident}
    private var filtered=properties
    private var showAll=false
    private var query=""
    private fun applyFilter(){
        filtered=properties.filter{property->
            val matches=query.isEmpty()||property.propertyName.contains(query,true)||property.ident.toString()==query
            val value=values[property]
            matches&&(showAll||(value!=null&&!value.error))
        }
    }
    private val values=ConcurrentHashMap<CDSProperty,Value>()
    private val dirty=AtomicBoolean(true)
    private var lastRender=0L
    private val handler=Handler(Looper.getMainLooper())
    private var listener:CDSEventHandler?=null
    private var adapter:BaseAdapter?=null
    private var status:TextView?=null
    private val ticker=object:Runnable {
        override fun run() {
            val message=if(CarInformation.isConnected)getString(R.string.tl_live_count,values.values.count{!it.error},properties.size)
                else getString(R.string.tl_disconnected)
            if(status?.text?.toString()!=message)status?.text=message
            val now=System.currentTimeMillis()
            if(dirty.getAndSet(false)||(values.isNotEmpty()&&now-lastRender>=5000)) {
                applyFilter()
                adapter?.notifyDataSetChanged();lastRender=now
            }
            handler.postDelayed(this,1000)
        }
    }
    override fun onCreateView(inflater:LayoutInflater,container:ViewGroup?,state:Bundle?):View {
        val root=TelemetryUi.column(requireContext(),16)
        root.layoutParams=ViewGroup.LayoutParams(-1,-1)
        val header=TelemetryUi.card(requireContext(),root,getString(R.string.tl_live))
        header.addView(TelemetryUi.text(requireContext(),getString(R.string.tl_live_info),13f))
        status=TelemetryUi.text(requireContext(),getString(R.string.tl_waiting),14f,true).also{header.addView(it)}
        val search=EditText(requireContext()).apply {
            hint=getString(R.string.tl_search); isSingleLine=true; textSize=16f
        }
        header.addView(search)
        header.addView(SwitchCompat(requireContext()).apply {
            text=getString(R.string.tl_live_show_all); minHeight=TelemetryUi.dp(context,48)
            setOnCheckedChangeListener{_,checked->showAll=checked;applyFilter();adapter?.notifyDataSetChanged()}
        })
        val list=ListView(requireContext()).apply {
            dividerHeight=TelemetryUi.dp(context,8); divider=null; clipToPadding=false
            layoutParams=LinearLayout.LayoutParams(-1,0,1f)
        }
        adapter=object:BaseAdapter() {
            override fun getCount()=filtered.size
            override fun getItem(position:Int)=filtered[position]
            override fun getItemId(position:Int)=filtered[position].ident.toLong()
            override fun hasStableIds()=true
            override fun getView(position:Int,convertView:View?,parent:ViewGroup):View {
                val row=convertView as? LinearLayout?:TelemetryUi.column(requireContext(),14).apply {
                    addView(TelemetryUi.text(context,"",15f,true))
                    addView(TelemetryUi.text(context,"",13f))
                    addView(TelemetryUi.text(context,"",11f))
                    setBackgroundResource(R.drawable.telemetry_card)
                }
                val property=filtered[position]; val value=values[property]
                (row.getChildAt(0) as TextView).text="${property.ident} · ${property.propertyName}"
                (row.getChildAt(1) as TextView).text=value?.json?:getString(R.string.tl_waiting)
                (row.getChildAt(2) as TextView).text=if(value==null)"" else if(value.cached)getString(R.string.tl_cached)
                    else getString(R.string.tl_ago,maxOf(0,(System.currentTimeMillis()-value.timestamp)/1000))
                return row
            }
        }
        list.adapter=adapter; root.addView(list)
        search.addTextChangedListener(object:TextWatcher {
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){
                query=s?.toString().orEmpty()
                applyFilter()
                adapter?.notifyDataSetChanged()
            }
            override fun afterTextChanged(s:Editable?){}
        })
        return root
    }
    override fun onStart(){
        super.onStart(); values.clear();applyFilter();dirty.set(true)
        val registering=AtomicBoolean(true)
        val callback=CDSEventHandler{property,json->
            values[property]=Value(json.toString(),System.currentTimeMillis(),registering.get(),json.has("error"))
            dirty.set(true)
        }
        listener=callback
        properties.forEach{CarInformation.cdsData.addEventHandler(it,1000,callback)}
        registering.set(false); handler.post(ticker)
    }
    override fun onStop(){
        handler.removeCallbacks(ticker)
        listener?.let{callback->properties.forEach{CarInformation.cdsData.removeEventHandler(it,callback)}}
        listener=null;super.onStop()
    }
    override fun onDestroyView(){adapter=null;status=null;super.onDestroyView()}
}
