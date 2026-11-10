package me.hufman.androidautoidrive.carapp.carinfo

import android.graphics.*
import com.google.gson.JsonObject
import de.bmw.idrive.BMWRemoting
import io.bimmergestalt.idriveconnectkit.CDS
import io.bimmergestalt.idriveconnectkit.CDSProperty
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import me.hufman.androidautoidrive.cds.CDSMetrics
import me.hufman.androidautoidrive.cds.flow
import me.hufman.androidautoidrive.phoneui.ChartStyle
import me.hufman.androidautoidrive.phoneui.TelemetryChartRenderer
import me.hufman.androidautoidrive.utils.Utils
import java.util.Locale

/**
 * EXPERIMENT: renders live gauges and line charts as bitmaps and shows them as image cells
 * in the two-column table of the car's detail list. Whether (and how large) the car draws
 * image cells in this list has to be checked on a real iDrive; tune the size constants below.
 */
class LiveCharts(metrics: CDSMetrics) {
	companion object {
		const val TITLE = "430d Dashboard"
		const val WINDOW_MS = 120_000L
		const val REFRESH_MS = 4000L
		// bitmap sizes in px. They are scaled by the car to the table column
		const val LINE_WIDTH = 760
		const val GAUGE_SIZE = 190

		// dark colors for the car display
		val STYLE = ChartStyle(
			line = 0xFF4FD1C5.toInt(), grid = 0xFF3A4650.toInt(), label = 0xFFDDE6EC.toInt(),
			surface = 0xFF10171E.toInt(), density = 1.25f)
	}

	private class Series(val title: String, val unit: String, val min: Double, val max: Double,
	                     val decimals: Int, val source: Flow<Double?>) {
		val history = ChartHistory(WINDOW_MS)
		fun text(value: Double?) = value?.let { String.format(Locale.GERMAN, "%.${decimals}f", it) } ?: "--"
	}

	private val data = metrics.carInfo.cdsData

	private fun series(property: CDSProperty, title: String, unit: String, min: Double, max: Double, decimals: Int,
	                   valid: ClosedFloatingPointRange<Double>, vararg path: String) =
		Series(title, unit, min, max, decimals, data.flow[property].map { json: JsonObject ->
			LiveDashboard.number(json, *path)?.takeIf { it in valid }
		})

	private val oil = series(CDS.ENGINE.TEMPERATURE, "Öl", "°C", 40.0, 140.0, 0, -50.0..254.0, "temperature", "oil")
	private val engine = series(CDS.ENGINE.TEMPERATURE, "Motor", "°C", 40.0, 130.0, 0, -50.0..254.0, "temperature", "engine")
	private val rpm = series(CDS.ENGINE.RPMSPEED, "Drehzahl", "U/min", 0.0, 5500.0, 0, 0.0..10000.0, "RPMSpeed")
	private val speed = series(CDS.DRIVING.SPEEDACTUAL, "Tempo", "km/h", 0.0, 260.0, 0, 0.0..400.0, "speedActual")

	private fun png(bitmap: Bitmap): BMWRemoting.RHMIResourceData {
		val bytes = Utils.compressBitmapPng(bitmap)
		bitmap.recycle()
		return BMWRemoting.RHMIResourceData(BMWRemoting.RHMIResourceType.IMAGEDATA, bytes)
	}


    private fun Series.frames(): Flow<Double?> = flow {
        kotlinx.coroutines.coroutineScope {
            var current: Double? = null
            val reader = launch { source.collect { value -> current = value; value?.let(history::add) } }
            try {
                while (true) { emit(current); delay(REFRESH_MS) }
            } finally { reader.cancel() }
        }
    }.flowOn(Dispatchers.Default)

    private fun dashboardRow(series: List<Series>): Flow<Any> =
        combine(series.map { it.frames() }) { values -> values.toList() }
        // only send a new image when a displayed (rounded) value changed
        .distinctUntilChanged { old, new -> series.indices.all { series[it].text(old[it]) == series[it].text(new[it]) } }
        .map { values ->
            val bitmap = Bitmap.createBitmap(LINE_WIDTH, 220, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(STYLE.surface)
            series.forEachIndexed { index, s ->
                canvas.save()
                canvas.translate((index * GAUGE_SIZE).toFloat(), 0f)
                TelemetryChartRenderer.drawGauge(canvas, GAUGE_SIZE, 185, values[index], s.min, s.max,
                    s.unit, s.text(values[index]), STYLE)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = STYLE.label; textSize = 22f; textAlign = Paint.Align.CENTER
                }
                canvas.drawText(s.title, GAUGE_SIZE / 2f, 211f, paint)
                canvas.restore()
            }
            png(bitmap) as Any
        }

    val fields: List<Flow<Any>> = listOf(dashboardRow(listOf(oil, engine, rpm, speed)))
}
