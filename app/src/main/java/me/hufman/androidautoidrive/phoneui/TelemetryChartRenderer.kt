package me.hufman.androidautoidrive.phoneui

import android.graphics.*
import me.hufman.androidautoidrive.logging.Reading
import java.util.Locale
import kotlin.math.abs

/** Colors and density for a rendered chart; independent of any Context so the exact same code can draw
 *  on the phone (TelemetryChartView) and into an off-screen Bitmap (e.g. to push to the car's display). */
data class ChartStyle(
	val line: Int, val grid: Int, val label: Int, val surface: Int,
	val density: Float = 1f,
	val fill: Boolean = true,
	val stateColors: (Double) -> Int = { Color.HSVToColor(floatArrayOf(((it * 67 + 170) % 360).toFloat(), .52f, .82f)) }
)

/** Draws curves and state timelines. All geometry is derived from the canvas size. */
object TelemetryChartRenderer {
	private const val GAP_MILLIS = 10_000L

	private class Frame(val left: Float, val top: Float, val right: Float, val bottom: Float,
	                    val start: Long, val end: Long) {
		fun x(time: Long) = left + ((time - start).toDouble() / maxOf(1L, end - start)).coerceIn(0.0, 1.0).toFloat() * (right - left)
	}

	/** Converts a touch x coordinate back into a data point for the scrubber */
	fun nearest(points: List<Reading>, width: Int, density: Float, start: Long, end: Long, touchX: Float): Reading? {
		val frame = Frame(44 * density, 0f, width - 12 * density, 0f, start, end)
		return points.filter { it.value.isFinite() }.minByOrNull { abs(frame.x(it.time) - touchX) }
	}

	fun drawCurve(canvas: Canvas, width: Int, height: Int, points: List<Reading>, start: Long, end: Long,
	              style: ChartStyle, selected: Reading? = null, formatTime: (Long) -> String, formatValue: (Double) -> String = ::plain, lowerBound: Double? = null) {
		val finite = points.filter { it.value.isFinite() }
		if (finite.isEmpty()) return
		val d = style.density
		val paint = Paint(Paint.ANTI_ALIAS_FLAG)
		val frame = Frame(44 * d, 10 * d, width - 12 * d, height - 34 * d, start, end)
		var minimum = finite.minOf { it.value }
		var maximum = finite.maxOf { it.value }
		if (minimum == maximum) { minimum -= .5; maximum += .5 }
		val pad = (maximum - minimum) * .08
		minimum -= pad; maximum += pad
		if (lowerBound != null) minimum = maxOf(lowerBound, minimum)
		fun y(value: Double) = frame.bottom - ((value - minimum) / (maximum - minimum)).toFloat() * (frame.bottom - frame.top)

		paint.textSize = 11 * d
		paint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
		// horizontal grid + y labels
		for (i in 0..3) {
			val value = minimum + (maximum - minimum) * i / 3
			paint.color = style.grid; paint.strokeWidth = d; paint.style = Paint.Style.STROKE
			paint.pathEffect = DashPathEffect(floatArrayOf(4 * d, 5 * d), 0f)
			canvas.drawLine(frame.left, y(value), frame.right, y(value), paint)
			paint.pathEffect = null
			paint.style = Paint.Style.FILL; paint.color = style.label
			canvas.drawText(formatValue(value), 0f, y(value) + 4 * d, paint)
		}
		// time labels
		paint.color = style.label; paint.style = Paint.Style.FILL
		listOf(0.0, .5, 1.0).forEach { fraction ->
			val time = start + ((end - start) * fraction).toLong()
			val text = formatTime(time - start)
			val tx = (frame.x(time) - paint.measureText(text) * fraction.toFloat()).coerceAtLeast(0f)
			canvas.drawText(text, tx, height - 9 * d, paint)
		}

		// split into continuous segments, each drawn as a smooth curve
		val segments = mutableListOf<MutableList<Reading>>()
		var previous: Reading? = null
		points.forEach { point ->
			if (!point.value.isFinite()) { previous = null; return@forEach }
			if (previous == null || point.time - previous!!.time > GAP_MILLIS) segments.add(mutableListOf())
			segments.last().add(point)
			previous = point
		}
		val gradient = LinearGradient(0f, frame.top, 0f, frame.bottom,
			withAlpha(style.line, 90), withAlpha(style.line, 0), Shader.TileMode.CLAMP)
		segments.forEach { segment ->
			val line = Path()
			val xs = FloatArray(segment.size) { frame.x(segment[it].time) }
			val ys = FloatArray(segment.size) { y(segment[it].value) }
			line.moveTo(xs[0], ys[0])
			for (i in 1 until segment.size) {
				val mid = (xs[i - 1] + xs[i]) / 2
				line.cubicTo(mid, ys[i - 1], mid, ys[i], xs[i], ys[i])   // monotone-looking smoothing, never overshoots
			}
			if (style.fill && segment.size > 1) {
				val area = Path(line)
				area.lineTo(xs.last(), frame.bottom); area.lineTo(xs[0], frame.bottom); area.close()
				paint.shader = gradient; paint.style = Paint.Style.FILL
				canvas.drawPath(area, paint); paint.shader = null
			}
			paint.color = style.line; paint.style = Paint.Style.STROKE; paint.strokeWidth = 2.5f * d
			paint.strokeCap = Paint.Cap.ROUND; paint.strokeJoin = Paint.Join.ROUND
			canvas.drawPath(line, paint)
			if (segment.size == 1) {
				paint.style = Paint.Style.FILL; canvas.drawCircle(xs[0], ys[0], 3 * d, paint)
			}
		}

		// highlighted point: crosshair + halo, defaulting to the latest value
		val focus = selected ?: finite.last()
		val fx = frame.x(focus.time); val fy = y(focus.value)
		if (selected != null) {
			paint.color = style.label; paint.strokeWidth = d; paint.style = Paint.Style.STROKE
			canvas.drawLine(fx, frame.top, fx, frame.bottom, paint)
		}
		paint.style = Paint.Style.FILL
		paint.color = withAlpha(style.line, 60); canvas.drawCircle(fx, fy, 9 * d, paint)
		paint.color = style.surface; canvas.drawCircle(fx, fy, 5.5f * d, paint)
		paint.color = style.line; canvas.drawCircle(fx, fy, 3.5f * d, paint)

		if (selected != null) {
			val text = "${formatTime(selected.time - start)}  ·  ${formatValue(selected.value)}"
			paint.textSize = 12 * d; paint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
			val w = paint.measureText(text) + 20 * d; val h = 28 * d
			val bx = (fx - w / 2).coerceIn(frame.left, maxOf(frame.left, width - w))
			val by = if (fy - h - 14 * d < 0) fy + 14 * d else fy - h - 14 * d
			paint.color = style.label
			canvas.drawRoundRect(bx, by, bx + w, by + h, 10 * d, 10 * d, paint)
			paint.color = style.surface
			canvas.drawText(text, bx + 10 * d, by + h / 2 + 4.5f * d, paint)
		}
	}

	fun drawStates(canvas: Canvas, width: Int, height: Int, points: List<Reading>, start: Long, end: Long,
	               style: ChartStyle, formatTime: (Long) -> String, stateLabel: (Double) -> String) {
		val finite = points.filter { it.value.isFinite() }
		if (finite.isEmpty()) return
		val d = style.density
		val paint = Paint(Paint.ANTI_ALIAS_FLAG)
		val frame = Frame(8 * d, 8 * d, width - 8 * d, 0f, start, end)
		points.forEachIndexed { index, reading ->
			if (!reading.value.isFinite()) return@forEachIndexed
			val next = points.getOrNull(index + 1)?.time ?: end
			paint.color = style.stateColors(reading.value)
			canvas.drawRoundRect(frame.x(reading.time), frame.top, maxOf(frame.x(reading.time) + d, frame.x(next)),
				frame.top + 36 * d, 6 * d, 6 * d, paint)
		}
		paint.textSize = 11 * d; paint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
		paint.color = style.label
		listOf(0.0, .5, 1.0).forEach { fraction ->
			val time = start + ((end - start) * fraction).toLong()
			val text = formatTime(time - start)
			canvas.drawText(text, (frame.x(time) - paint.measureText(text) * fraction.toFloat()).coerceAtLeast(0f), frame.top + 54 * d, paint)
		}
		finite.map { it.value }.distinct().take(8).forEachIndexed { index, value ->
			val tx = 8 * d + (index % 3) * (width / 3f)
			val ty = frame.top + 84 * d + (index / 3) * 26 * d
			paint.color = style.stateColors(value); canvas.drawCircle(tx + 5 * d, ty - 4 * d, 5 * d, paint)
			paint.color = style.label; canvas.drawText(stateLabel(value), tx + 16 * d, ty, paint)
		}
	}

	/** Round gauge with a 240° arc; the arc turns amber/red near the top of the range */
	fun drawGauge(canvas: Canvas, width: Int, height: Int, value: Double?, min: Double, max: Double,
	              title: String, valueText: String, style: ChartStyle) {
		val d = style.density
		val paint = Paint(Paint.ANTI_ALIAS_FLAG)
		val stroke = 9 * d
		val size = minOf(width.toFloat(), height - 14 * d) - stroke
		val cx = width / 2f; val cy = stroke / 2 + size / 2 + 2 * d
		val rect = RectF(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2)
		paint.style = Paint.Style.STROKE; paint.strokeWidth = stroke; paint.strokeCap = Paint.Cap.ROUND
		paint.color = style.grid
		canvas.drawArc(rect, 150f, 240f, false, paint)
		val fraction = if (value == null || !value.isFinite()) 0.0 else ((value - min) / (max - min)).coerceIn(0.0, 1.0)
		if (fraction > 0.0) {
			paint.color = when { fraction >= .9 -> 0xFFE5484D.toInt(); fraction >= .75 -> 0xFFF5A623.toInt(); else -> style.line }
			canvas.drawArc(rect, 150f, (240 * fraction).toFloat(), false, paint)
		}
		paint.style = Paint.Style.FILL
		paint.textAlign = Paint.Align.CENTER
		paint.color = style.label
		paint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
		paint.textSize = size * .26f
		canvas.drawText(valueText, cx, cy + size * .10f, paint)
		paint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
		paint.textSize = size * .15f
		canvas.drawText(title, cx, cy + size * .42f, paint)
	}

	/** Renders a gauge into a standalone bitmap */
	fun renderGaugeBitmap(width: Int, height: Int, value: Double?, min: Double, max: Double,
	                      title: String, valueText: String, style: ChartStyle): Bitmap {
		val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
		val canvas = Canvas(bitmap)
		canvas.drawColor(style.surface)
		drawGauge(canvas, width, height, value, min, max, title, valueText, style)
		return bitmap
	}

	/** Renders a curve into a standalone bitmap, e.g. to send as image to the car (RHMI RaImageModel) */
	fun renderBitmap(width: Int, height: Int, points: List<Reading>, start: Long, end: Long, style: ChartStyle,
	                 formatTime: (Long) -> String, formatValue: (Double) -> String = ::plain): Bitmap {
		val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
		val canvas = Canvas(bitmap)
		canvas.drawColor(style.surface)
		drawCurve(canvas, width, height, points, start, end, style, null, formatTime, formatValue)
		return bitmap
	}

	private fun plain(value: Double) = String.format(Locale.getDefault(), "%.1f", value)
	private fun withAlpha(color: Int, alpha: Int) = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
}
