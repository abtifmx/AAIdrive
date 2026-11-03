package me.hufman.androidautoidrive.carapp.carinfo

import android.graphics.*
import de.bmw.idrive.BMWRemoting
import io.bimmergestalt.idriveconnectkit.CDS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import me.hufman.androidautoidrive.cds.CDSMetrics
import me.hufman.androidautoidrive.cds.flow
import me.hufman.androidautoidrive.utils.Utils

/** Distances in vehicle order; unknown/out-of-range codes never become obstacle distances. */
class ParkingSensors(metrics: CDSMetrics) {
    companion object {
        const val TITLE = "430d PDC"
        const val MIN_RENDER_MS = 600L
        internal fun distance(value: Double?): Int? = value?.takeIf { it in 0.0..252.0 }?.toInt()
        private val keys = listOf("outLeft", "left", "middleLeft", "middleRight", "right", "outRight")
    }
    private val data = metrics.carInfo.cdsData
    private fun values(front: Boolean) = data.flow[if (front) CDS.SENSORS.PDCRANGEFRONT else CDS.SENSORS.PDCRANGEREAR]
        .map { json -> keys.map { distance(LiveDashboard.number(json, if (front) "PDCRangeFront" else "PDCRangeRear", it)) } }
        .onStart { emit(List(6) { null }) }

    // the sensors change quickly while parking, so only render when a distance changed and at most every MIN_RENDER_MS
    val fields: List<Flow<Any>> = listOf(values(true).combine(values(false)) { front, rear -> front to rear }
        .distinctUntilChanged().conflate().onEach { delay(MIN_RENDER_MS) }.map { (front, rear) ->
        val bitmap = Bitmap.createBitmap(760, 300, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(LiveCharts.STYLE.surface)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
        fun text(value: String, x: Float, y: Float, size: Float = 21f) {
            paint.color = LiveCharts.STYLE.label; paint.textSize = size; paint.style = Paint.Style.FILL
            canvas.drawText(value, x, y, paint)
        }
        text("Parksensoren · cm · links → rechts", 380f, 28f, 24f)
        paint.color = LiveCharts.STYLE.grid
        canvas.drawRoundRect(225f, 115f, 535f, 198f, 25f, 25f, paint)
        text("FAHRZEUG · VORN ↑", 380f, 165f)
        listOf(front, rear).forEachIndexed { row, distances ->
            val y = if (row == 0) 80f else 245f
            distances.forEachIndexed { index, value ->
                val x = 85f + index * 118f
                paint.color = when { value == null -> LiveCharts.STYLE.grid; value < 40 -> Color.rgb(229,72,77)
                    value < 80 -> Color.rgb(245,166,35); else -> LiveCharts.STYLE.line }
                canvas.drawRoundRect(x-46, y-24, x+46, y+17, 9f, 9f, paint)
                paint.color = Color.WHITE; paint.textSize = 24f
                canvas.drawText(value?.toString() ?: "--", x, y+5, paint)
            }
        }
        text("-- = kein gültiger Abstand / außerhalb Messbereich", 380f, 286f, 18f)
        val bytes = Utils.compressBitmapPng(bitmap); bitmap.recycle()
        BMWRemoting.RHMIResourceData(BMWRemoting.RHMIResourceType.IMAGEDATA, bytes) as Any
    }.flowOn(Dispatchers.Default))
}
