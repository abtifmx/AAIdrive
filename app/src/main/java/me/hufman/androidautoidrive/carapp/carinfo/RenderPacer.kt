package me.hufman.androidautoidrive.carapp.carinfo

import android.util.Log
import kotlinx.coroutines.delay
import me.hufman.androidautoidrive.AppSettings

/**
 * Keeps the images for the car at a pace that the car can handle.
 * The minimum time between two images of a row is the configured refresh time, or three times the average
 * time that the car needed to receive an image. After a slow send, the time doubles for a while
 */
class RenderPacer(private val settings: AppSettings, private val clock: () -> Long = { System.nanoTime() / 1_000_000 }) {
	companion object {
		private const val TAG = "DashboardRender"
		const val SLOW_SEND_MS = 1500L
		const val PENALTY_MS = 30_000L
		const val SUMMARY_MS = 30_000L
		const val MAX_INTERVAL_MS = 10_000L
	}

	private val lastTurn = LongArray(4) { Long.MIN_VALUE }
	private var averageSendMs = 0.0
	private var penaltyUntil = Long.MIN_VALUE
	private var frames = 0
	private var bytes = 0L
	private var drawMs = 0L
	private var sends = 0
	private var sendMs = 0L
	private var lastSummary = clock()

	@Synchronized fun interval(): Long {
		val base = DashboardConfig.refreshMs(settings[AppSettings.KEYS.DASHBOARD_REFRESH_MS]).toLong()
		val adaptive = maxOf(base, (averageSendMs * 3).toLong())
		return (if (clock() < penaltyUntil) adaptive * 2 else adaptive).coerceAtMost(MAX_INTERVAL_MS)
	}

	/** Waits until the row may send its next image */
	suspend fun awaitTurn(row: Int) {
		val wait = synchronized(this) {
			val now = clock()
			val due = if (lastTurn[row] == Long.MIN_VALUE) now else lastTurn[row] + interval()
			val waitMs = (due - now).coerceAtLeast(0)
			lastTurn[row] = now + waitMs
			waitMs
		}
		if (wait > 0) delay(wait)
	}

	@Synchronized fun recordFrame(row: Int, size: Int, drawMillis: Long) {
		frames++; bytes += size; drawMs += drawMillis
		log("row=$row bytes=$size draw=${drawMillis}ms")
		summarize()
	}

	@Synchronized fun recordSend(millis: Long) {
		sends++; sendMs += millis
		averageSendMs = if (averageSendMs == 0.0) millis.toDouble() else averageSendMs * 0.7 + millis * 0.3
		if (millis > SLOW_SEND_MS) penaltyUntil = clock() + PENALTY_MS
		log("send=${millis}ms interval=${interval()}ms")
	}

	private fun summarize() {
		val now = clock()
		if (now - lastSummary < SUMMARY_MS) return
		log("summary frames=$frames avgBytes=${if (frames > 0) bytes / frames else 0} avgDraw=${if (frames > 0) drawMs / frames else 0}ms " +
				"sends=$sends avgSend=${if (sends > 0) sendMs / sends else 0}ms interval=${interval()}ms")
		frames = 0; bytes = 0; drawMs = 0; sends = 0; sendMs = 0; lastSummary = now
	}

	private fun log(message: String) {
		try { Log.d(TAG, message) } catch (_: RuntimeException) {}
	}
}
