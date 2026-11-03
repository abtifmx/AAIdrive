package me.hufman.androidautoidrive.carapp.carinfo

import me.hufman.androidautoidrive.logging.Reading

/** A thread-safe rolling window of recent readings, used to draw live line charts */
class ChartHistory(val windowMillis: Long, private val clock: () -> Long = System::currentTimeMillis) {
	private val readings = ArrayDeque<Reading>()

	@Synchronized fun add(value: Double, time: Long = clock()) {
		if (!value.isFinite()) return
		readings.addLast(Reading(time, value))
		while (readings.isNotEmpty() && readings.first().time < time - windowMillis) readings.removeFirst()
	}

	@Synchronized fun snapshot(): List<Reading> = readings.toList()
	@Synchronized fun latest(): Double? = readings.lastOrNull()?.value
}
