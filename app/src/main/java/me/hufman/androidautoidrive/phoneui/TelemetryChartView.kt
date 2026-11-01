package me.hufman.androidautoidrive.phoneui

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.logging.*
import java.util.Locale
import kotlin.math.abs

class TelemetryChartView(context: Context, private val metric: Metric, private val points: List<Reading>,
                         private val start: Long, private val end: Long) : View(context) {
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG)
    private val accent = ContextCompat.getColor(context, R.color.colorAccent)
    private val muted = ContextCompat.getColor(context, R.color.telemetryMuted)
    private val finite = points.filter { it.value.isFinite() }
    private fun px(v: Int) = TelemetryUi.dp(context, v).toFloat()
    private fun x(time: Long) = px(44) + ((time-start).toDouble() / maxOf(1L, end-start)).coerceIn(0.0,1.0).toFloat() * (width-px(56))
    fun stateLabel(value: Double): String {
        val code = value.toInt()
        return when (metric.id) {
            "gear" -> when (code) { 1 -> "N"; 2 -> "R"; 3 -> "P"; in 5..16 -> "D${code-4}"; else -> context.getString(R.string.tl_code, code) }
            "mode" -> when(code) { 2,3 -> "Comfort"; 4 -> "Sport"; 5 -> "Sport+"; 7 -> "Eco Pro"; else -> context.getString(R.string.tl_code, code) }
            "dsc" -> when(code) { 0 -> context.getString(R.string.tl_off); 1 -> context.getString(R.string.tl_on); else -> context.getString(R.string.tl_code, code) }
            else -> context.getString(R.string.tl_code, code)
        }
    }
    private fun stateColor(value: Double) = Color.HSVToColor(floatArrayOf(((value*67+170)%360).toFloat(), .52f, .82f))
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (finite.isEmpty()) return
        val top = px(14); val bottom = height-px(46)
        ink.textSize = px(11); ink.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        ink.color = muted; ink.strokeWidth = px(1); ink.style = Paint.Style.FILL
        listOf(0.0, .5, 1.0).forEach { fraction ->
            val time = start + ((end-start)*fraction).toLong()
            val label = TelemetryUi.duration(time-start)
            val tx = x(time).coerceIn(0f, maxOf(0f, width-ink.measureText(label)))
            canvas.drawText(label, tx, height-px(9), ink)
        }
        if (metric.kind == MetricKind.STATE) {
            points.forEachIndexed { index, reading ->
                if (reading.value.isFinite()) {
                    val next = points.getOrNull(index+1)?.time ?: end
                    ink.color = stateColor(reading.value)
                    canvas.drawRoundRect(x(reading.time), top, maxOf(x(reading.time)+px(1),x(next)), top+px(32),px(3),px(3),ink)
                }
            }
            finite.map { it.value }.distinct().take(8).forEachIndexed { index, value ->
                val tx = px(8) + (index%3)*(width/3f)
                val ty = top+px(62)+(index/3)*px(25)
                ink.color=stateColor(value); canvas.drawCircle(tx+px(4),ty-px(4),px(4),ink)
                ink.color=muted; canvas.drawText(stateLabel(value),tx+px(14),ty,ink)
            }
            contentDescription = context.getString(metric.title) + ": " + finite.map { stateLabel(it.value) }.distinct().joinToString()
            return
        }
        var minimum = finite.minOf { it.value }; var maximum = finite.maxOf { it.value }
        if (minimum == maximum) { minimum -= .5; maximum += .5 }
        fun y(value: Double) = bottom - ((value-minimum)/(maximum-minimum)).toFloat()*(bottom-top)
        for (i in 0..2) {
            val value = minimum+(maximum-minimum)*i/2
            ink.color=ContextCompat.getColor(context,R.color.telemetryBorder)
            canvas.drawLine(px(44),y(value),width-px(12),y(value),ink)
            ink.color=muted
            canvas.drawText(String.format(Locale.getDefault(),"%.1f",value),0f,y(value)+px(4),ink)
        }
        val path = Path(); var previous: Reading? = null
        points.forEach { point ->
            if (!point.value.isFinite()) previous=null
            else {
                if (previous == null || point.time-previous!!.time > 10000) path.moveTo(x(point.time),y(point.value))
                else path.lineTo(x(point.time),y(point.value))
                previous=point
            }
        }
        ink.color=accent; ink.strokeWidth=px(2); ink.style=Paint.Style.STROKE
        ink.strokeJoin=Paint.Join.ROUND; canvas.drawPath(path,ink); ink.style=Paint.Style.FILL
        finite.forEach { canvas.drawCircle(x(it.time),y(it.value),px(1),ink) }
        contentDescription=context.getString(metric.title)+" ${finite.minOf { it.value }} – ${finite.maxOf { it.value }} ${metric.unit}"
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) return true
        if (event.action == MotionEvent.ACTION_UP) {
            performClick()
            finite.minByOrNull { abs(x(it.time)-event.x) }?.let {
                val value=if(metric.kind==MetricKind.STATE) stateLabel(it.value) else String.format(Locale.getDefault(),"%.1f %s",it.value,metric.unit)
                Toast.makeText(context,"${TelemetryUi.duration(it.time-start)} · $value",Toast.LENGTH_SHORT).show()
            }
            return true
        }
        return super.onTouchEvent(event)
    }
    override fun performClick(): Boolean { super.performClick(); return true }
}
