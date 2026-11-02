package me.hufman.androidautoidrive.carapp.carinfo

import android.graphics.Bitmap
import com.google.gson.JsonObject
import de.bmw.idrive.BMWRemoting
import io.bimmergestalt.idriveconnectkit.CDS
import io.bimmergestalt.idriveconnectkit.CDSProperty
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
		const val TITLE = "430d Charts"
		const val WINDOW_MS = 120_000L
		const val REFRESH_MS = 1500L
		// bitmap sizes in px. They are scaled by the car to the table column
		const val LINE_WIDTH = 340
		const val LINE_HEIGHT = 130
		const val GAUGE_SIZE = 150

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
	private val torque = series(CDS.ENGINE.TORQUE, "Drehmoment", "Nm", 0.0, 700.0, 0, 0.0..2000.0, "torque")
	private val pedal = series(CDS.DRIVING.ACCELERATORPEDAL, "Gaspedal", "%", 0.0, 100.0, 0, 0.0..100.0, "acceleratorPedal", "position")

	private fun png(bitmap: Bitmap): BMWRemoting.RHMIResourceData {
		val bytes = Utils.compressBitmapPng(bitmap)
		bitmap.recycle()
		return BMWRemoting.RHMIResourceData(BMWRemoting.RHMIResourceType.IMAGEDATA, bytes)
	}

	/** records every update, but renders at most once per REFRESH_MS */
	private fun <T> Series.rendered(render: (Double?) -> T): Flow<T> =
		source.onEach { it?.let(history::add) }
			.conflate()
			.map { render(history.latest()) }
			.onEach { delay(REFRESH_MS) }
			.flowOn(Dispatchers.Default)

	private fun gauge(s: Series): Flow<Any> = s.rendered { value ->
		png(TelemetryChartRenderer.renderGaugeBitmap(GAUGE_SIZE, GAUGE_SIZE, value, s.min, s.max, s.unit, s.text(value), STYLE))
	}

	private fun line(s: Series): Flow<Any> = s.rendered {
		val now = System.currentTimeMillis()
		png(TelemetryChartRenderer.renderBitmap(LINE_WIDTH, LINE_HEIGHT, s.history.snapshot(), now - WINDOW_MS, now, STYLE,
			formatTime = { elapsed -> "${(elapsed - WINDOW_MS) / 1000}s" },
			formatValue = { String.format(Locale.GERMAN, "%.0f", it) }))
	}

	private fun label(s: Series): Flow<Any> = s.source.map { "${s.title}: ${s.text(it)} ${s.unit}" as Any }
		.onStart { emit("${s.title}: -- ${s.unit}") }.distinctUntilChanged()

	/** Cells are laid out two per row: gauges first, then one (label, line chart) row per value */
	val fields: List<Flow<Any>> = listOf(
		gauge(oil), gauge(engine),
		gauge(rpm), gauge(speed)
	) + listOf(oil, engine, rpm, speed, torque, pedal).flatMap { listOf(label(it), line(it)) }
}
