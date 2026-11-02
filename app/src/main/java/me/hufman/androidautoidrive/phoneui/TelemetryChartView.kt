package me.hufman.androidautoidrive.phoneui

import android.content.Context
import android.graphics.Canvas
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.logging.*
import java.util.Locale

/** Interactive chart: drag a finger across it to scrub through the trip. Drawing lives in TelemetryChartRenderer */
class TelemetryChartView(context: Context, private val metric: Metric, private val points: List<Reading>,
                         private val start: Long, private val end: Long) : View(context) {
    private val style = ChartStyle(
        line = ContextCompat.getColor(context, R.color.colorAccent),
        grid = ContextCompat.getColor(context, R.color.telemetryBorder),
        label = ContextCompat.getColor(context, R.color.telemetryMuted),
        surface = ContextCompat.getColor(context, R.color.telemetrySurface),
        density = context.resources.displayMetrics.density
    )
    private val finite = points.filter { it.value.isFinite() }
    private var selected: Reading? = null

    fun stateLabel(value: Double): String {
        val code = value.toInt()
        return when (metric.id) {
            "gear" -> when (code) { 1 -> "N"; 2 -> "R"; 3 -> "P"; in 5..16 -> "D${code-4}"; else -> context.getString(R.string.tl_code, code) }
            "mode" -> when(code) { 2,3 -> "Comfort"; 4 -> "Sport"; 5 -> "Sport+"; 7 -> "Eco Pro"; else -> context.getString(R.string.tl_code, code) }
            "dsc" -> when(code) { 0 -> context.getString(R.string.tl_off); 1 -> context.getString(R.string.tl_on); else -> context.getString(R.string.tl_code, code) }
            else -> context.getString(R.string.tl_code, code)
        }
    }

    private fun value(v: Double) = String.format(Locale.getDefault(), "%.1f%s", v, if (metric.unit.isEmpty()) "" else " ${metric.unit}")

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (finite.isEmpty()) return
        if (metric.kind == MetricKind.STATE) {
            TelemetryChartRenderer.drawStates(canvas, width, height, points, start, end, style, TelemetryUi::duration, ::stateLabel)
            contentDescription = context.getString(metric.title) + ": " + finite.map { stateLabel(it.value) }.distinct().joinToString()
        } else {
            TelemetryChartRenderer.drawCurve(canvas, width, height, points, start, end, style, selected, TelemetryUi::duration, ::value)
            contentDescription = context.getString(metric.title) + " ${finite.minOf { it.value }} – ${finite.maxOf { it.value }} ${metric.unit}"
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (metric.kind == MetricKind.STATE || finite.isEmpty()) return super.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)   // keep the page from scrolling while scrubbing
                selected = TelemetryChartRenderer.nearest(finite, width, style.density, start, end, event.x)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> { performClick(); parent?.requestDisallowInterceptTouchEvent(false); return true }
            MotionEvent.ACTION_CANCEL -> { selected = null; invalidate(); parent?.requestDisallowInterceptTouchEvent(false); return true }
        }
        return super.onTouchEvent(event)
    }
    override fun performClick(): Boolean { super.performClick(); return true }
}
