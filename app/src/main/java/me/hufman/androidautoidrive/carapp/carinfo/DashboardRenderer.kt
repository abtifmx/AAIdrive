package me.hufman.androidautoidrive.carapp.carinfo

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.cos
import kotlin.math.sin

object DashboardColors {
	val BACKGROUND = 0xFF0A0F14.toInt()
	val CARD = 0xFF111920.toInt()
	val TRACK = 0xFF24303B.toInt()
	val TICK = 0xFF3A4856.toInt()
	val TEXT = 0xFFF2F6F9.toInt()
	val MUTED = 0xFF8C9AA8.toInt()
	val COLD = 0xFF4C9BFF.toInt()
	val COLD_TEXT = 0xFF7DB6FF.toInt()
	val NORMAL = 0xFF3ECFC0.toInt()
	val HIGH = 0xFFF5A623.toInt()
	val CRITICAL = 0xFFE5484D.toInt()
}

/** A colored part of the gauge track, as fractions of the scale */
data class GaugeZone(val from: Float, val to: Float, val color: Int, val alpha: Int)

/**
 * Everything that is drawn for one gauge. The values are already rounded to what is visible,
 * so that equal cards mean an equal image and no new image has to be sent
 */
data class GaugeCard(val label: String, val value: String, val unit: String, val arc: Int, val arcColor: Int,
                     val zones: List<GaugeZone>, val stateText: String, val cold: Boolean)

data class GaugeFrame(val cards: List<GaugeCard>) {
	companion object {
		/** The bow is drawn in 120 steps over the 240 degrees of the scale */
		const val ARC_STEPS = 120
	}
}

/** Draws the gauge row with Android Canvas, in bitmap pixels that the car scales to the column width */
object DashboardRenderer {
	const val GAUGE_WIDTH = 760
	const val GAUGE_HEIGHT = 200
	private const val CARD_WIDTH = 184f
	private const val CARD_STEP = 192f
	private const val START_ANGLE = 150f
	private const val SWEEP = 240f

	private val regular = Typeface.create("sans-serif", Typeface.NORMAL)
	private val medium = Typeface.create("sans-serif-medium", Typeface.NORMAL)
	private val bold = Typeface.create("sans-serif", Typeface.BOLD)

	fun gaugeBitmap(frame: GaugeFrame): Bitmap {
		val bitmap = Bitmap.createBitmap(GAUGE_WIDTH, GAUGE_HEIGHT, Bitmap.Config.ARGB_8888)
		drawGauges(Canvas(bitmap), frame)
		return bitmap
	}

	fun drawGauges(canvas: Canvas, frame: GaugeFrame) {
		canvas.drawColor(DashboardColors.BACKGROUND)
		val paint = Paint(Paint.ANTI_ALIAS_FLAG)
		frame.cards.forEachIndexed { index, card -> drawCard(canvas, paint, card, index * CARD_STEP) }
	}

	private fun Paint.reset(style: Paint.Style, color: Int, width: Float = 0f, cap: Paint.Cap = Paint.Cap.BUTT) {
		this.style = style; this.color = color; strokeWidth = width; strokeCap = cap
	}

	private fun drawCard(canvas: Canvas, paint: Paint, card: GaugeCard, x: Float) {
		paint.reset()
		paint.isAntiAlias = true
		paint.reset(Paint.Style.FILL, DashboardColors.CARD)
		canvas.drawRoundRect(RectF(x, 0f, x + CARD_WIDTH, GAUGE_HEIGHT.toFloat()), 14f, 14f, paint)

		val cx = x + 92f
		val cy = 98f
		val radius = 64f
		val oval = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
		// the track, then the warning zones with flat ends on top of it
		paint.reset(Paint.Style.STROKE, DashboardColors.TRACK, 12f, Paint.Cap.ROUND)
		canvas.drawArc(oval, START_ANGLE, SWEEP, false, paint)
		paint.strokeCap = Paint.Cap.BUTT
		card.zones.forEach { zone ->
			paint.color = zone.color
			paint.alpha = zone.alpha
			canvas.drawArc(oval, START_ANGLE + SWEEP * zone.from, SWEEP * (zone.to - zone.from), false, paint)
		}
		paint.alpha = 255
		// the scale ticks
		paint.reset(Paint.Style.STROKE, DashboardColors.TICK, 2f, Paint.Cap.ROUND)
		for (tick in 0..8) {
			val angle = Math.toRadians(START_ANGLE + 30.0 * tick)
			val outer = if (tick % 2 == 0) 84f else 81f
			canvas.drawLine(cx + 77f * cos(angle).toFloat(), cy + 77f * sin(angle).toFloat(),
					cx + outer * cos(angle).toFloat(), cy + outer * sin(angle).toFloat(), paint)
		}
		// the value bow with a dot at its end
		if (card.arc > 0) {
			val sweep = SWEEP * card.arc / GaugeFrame.ARC_STEPS
			paint.reset(Paint.Style.STROKE, card.arcColor, 12f, Paint.Cap.ROUND)
			canvas.drawArc(oval, START_ANGLE, sweep, false, paint)
			val angle = Math.toRadians(START_ANGLE + sweep.toDouble())
			paint.reset(Paint.Style.FILL, DashboardColors.BACKGROUND)
			canvas.drawCircle(cx + radius * cos(angle).toFloat(), cy + radius * sin(angle).toFloat(), 3.5f, paint)
		}

		paint.reset(Paint.Style.FILL, DashboardColors.TEXT)
		paint.textAlign = Paint.Align.CENTER
		paint.letterSpacing = 0f
		paint.typeface = medium
		paint.textSize = 40f
		canvas.drawText(card.value, cx, 106f, paint)
		paint.typeface = regular
		paint.textSize = 14f
		paint.color = DashboardColors.MUTED
		canvas.drawText(card.unit, cx, 129f, paint)
		if (card.stateText.isNotEmpty()) {
			paint.typeface = bold
			paint.textSize = 12f
			paint.letterSpacing = 1.5f / 12f
			paint.color = card.arcColor
			canvas.drawText(card.stateText, cx, 153f, paint)
		}
		paint.typeface = medium
		paint.textSize = 14f
		paint.letterSpacing = 2f / 14f
		paint.color = if (card.cold) DashboardColors.COLD_TEXT else DashboardColors.MUTED
		canvas.drawText(card.label, cx, 182f, paint)
		paint.letterSpacing = 0f
	}
}
