package me.hufman.androidautoidrive.carapp.carinfo

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface

/** Draws the climate row: both desired temperatures and the interior temperature on one scale */
object ClimateRenderer {
	const val WIDTH = 760
	const val HEIGHT = 190

	private val COLD = 0xFF4C9BFF.toInt()
	private val NEUTRAL = 0xFFD6DEE6.toInt()
	private val WARM = 0xFFFF8A3D.toInt()
	private val UNKNOWN = 0xFF5A6773.toInt()
	private val ABOVE = 0xFFFFAA70.toInt()
	private val BELOW = 0xFF7DB6FF.toInt()

	private val medium = Typeface.create("sans-serif-medium", Typeface.NORMAL)
	private val regular = Typeface.create("sans-serif", Typeface.NORMAL)
	private val bold = Typeface.create("sans-serif", Typeface.BOLD)

	fun bitmap(frame: ClimateFrame): Bitmap {
		val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
		draw(Canvas(bitmap), frame)
		return bitmap
	}

	private fun lerp(from: Int, to: Int, amount: Float): Int = Color.rgb(
			(Color.red(from) + (Color.red(to) - Color.red(from)) * amount).toInt(),
			(Color.green(from) + (Color.green(to) - Color.green(from)) * amount).toInt(),
			(Color.blue(from) + (Color.blue(to) - Color.blue(from)) * amount).toInt())

	/** The color of the scale at a position from 0 to 1 */
	fun scaleColor(fraction: Float): Int =
		if (fraction < 0.5f) lerp(COLD, NEUTRAL, fraction / 0.5f) else lerp(NEUTRAL, WARM, (fraction - 0.5f) / 0.5f)

	private fun Paint.text(size: Float, color: Int, face: Typeface, align: Paint.Align, spacing: Float = 0f) {
		style = Paint.Style.FILL; this.color = color; textSize = size; typeface = face; textAlign = align; letterSpacing = spacing / size
	}

	fun draw(canvas: Canvas, frame: ClimateFrame) {
		canvas.drawColor(DashboardColors.BACKGROUND)
		val paint = Paint(Paint.ANTI_ALIAS_FLAG)
		paint.style = Paint.Style.FILL
		paint.color = DashboardColors.CARD
		canvas.drawRoundRect(RectF(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat()), 14f, 14f, paint)

		val f = frame.fahrenheit
		// header
		val (leftHeader, rightHeader) = ClimateLayout.headers(frame.rightHandDrive)
		paint.text(13f, DashboardColors.MUTED, medium, Paint.Align.LEFT, 1.5f)
		canvas.drawText(leftHeader, 28f, 32f, paint)
		paint.textAlign = Paint.Align.CENTER
		canvas.drawText("INNEN · IST", 380f, 32f, paint)
		paint.textAlign = Paint.Align.RIGHT
		canvas.drawText(rightHeader, 732f, 32f, paint)

		// values with a dot in the color of the temperature
		fun dotColor(side: ClimateSide) = side.temperature?.let { scaleColor(ClimateLayout.fraction(it, f)) } ?: UNKNOWN
		paint.style = Paint.Style.FILL
		paint.color = dotColor(frame.left); canvas.drawCircle(36f, 61f, 7f, paint)
		paint.color = dotColor(frame.right); canvas.drawCircle(724f, 61f, 7f, paint)
		paint.text(36f, DashboardColors.TEXT, medium, Paint.Align.LEFT)
		canvas.drawText(ClimateLayout.format(frame.left.temperature, f), 52f, 74f, paint)
		paint.textAlign = Paint.Align.CENTER
		canvas.drawText(ClimateLayout.format(frame.indoor, f), 380f, 74f, paint)
		paint.textAlign = Paint.Align.RIGHT
		canvas.drawText(ClimateLayout.format(frame.right.temperature, f), 708f, 74f, paint)

		// the deviation from the average desired temperature
		val deviation = ClimateLayout.deviation(frame.indoor, frame.left.temperature, frame.right.temperature, f)
		if (deviation != null) {
			val color = when (deviation.first) {
				ClimateLayout.Deviation.ABOVE -> ABOVE
				ClimateLayout.Deviation.BELOW -> BELOW
				ClimateLayout.Deviation.ON_TARGET -> DashboardColors.MUTED
			}
			paint.text(13f, color, regular, Paint.Align.CENTER)
			canvas.drawText(ClimateLayout.deviationText(deviation), 380f, 96f, paint)
		}

		// the scale bar
		val (min, max) = ClimateLayout.scale(f)
		paint.style = Paint.Style.FILL
		paint.shader = LinearGradient(ClimateLayout.SCALE_LEFT, 0f, ClimateLayout.SCALE_RIGHT, 0f, intArrayOf(COLD, NEUTRAL, WARM), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
		canvas.drawRoundRect(RectF(ClimateLayout.SCALE_LEFT, 129f, ClimateLayout.SCALE_RIGHT, 139f), 5f, 5f, paint)
		paint.shader = null
		// a tick for each degree and a label for every second one
		paint.style = Paint.Style.STROKE; paint.strokeWidth = 2f; paint.color = DashboardColors.TICK
		for (degree in min..max) {
			val x = ClimateLayout.x(degree.toDouble(), f)
			canvas.drawLine(x, 147f, x, if ((degree - min) % 2 == 0) 156f else 152f, paint)
		}
		paint.text(13f, DashboardColors.MUTED, regular, Paint.Align.CENTER)
		for (degree in min..max step 2) canvas.drawText("$degree°", ClimateLayout.x(degree.toDouble(), f), 176f, paint)

		// the marker of the interior temperature
		frame.indoor?.let { indoor ->
			val x = ClimateLayout.x(indoor, f)
			paint.style = Paint.Style.FILL; paint.color = Color.WHITE
			canvas.drawRoundRect(RectF(x - 2f, 116f, x + 2f, 152f), 2f, 2f, paint)
			paint.text(11f, Color.WHITE, bold, Paint.Align.CENTER)
			canvas.drawText("IST", x, 111f, paint)
		}

		// the desired temperatures on the scale, pushed apart when they would overlap
		val leftX = frame.left.temperature?.let { ClimateLayout.x(it, f) }
		val rightX = frame.right.temperature?.let { ClimateLayout.x(it, f) }
		val (drawLeft, drawRight) = if (leftX != null && rightX != null) ClimateLayout.spread(leftX, rightX) else leftX to rightX
		fun dot(x: Float?, side: ClimateSide, letter: String) {
			if (x == null) return
			paint.style = Paint.Style.FILL
			paint.color = DashboardColors.CARD; canvas.drawCircle(x, 134f, 15f, paint)
			paint.color = dotColor(side); canvas.drawCircle(x, 134f, 12f, paint)
			paint.text(12f, DashboardColors.BACKGROUND, bold, Paint.Align.CENTER)
			canvas.drawText(letter, x, 138.5f, paint)
		}
		dot(drawLeft, frame.left, "L")
		dot(drawRight, frame.right, "R")
	}
}
