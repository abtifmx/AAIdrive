package me.hufman.androidautoidrive.carinfo

import me.hufman.androidautoidrive.carapp.carinfo.ChartHistory
import org.junit.Assert.*
import org.junit.Test

class ChartHistoryTest {
    @Test fun dropsOldReadingsAndIgnoresInvalidValues() {
        val history = ChartHistory(10_000) { 0L }
        history.add(1.0, 0)
        history.add(Double.NaN, 1000)
        history.add(2.0, 8_000)
        history.add(3.0, 20_000)
        assertEquals(listOf(3.0), history.snapshot().map { it.value })
        assertEquals(3.0, history.latest()!!, 0.0)
    }

    @Test fun keepsReadingsInsideTheWindow() {
        val history = ChartHistory(10_000) { 0L }
        listOf(0L, 4_000L, 9_000L).forEachIndexed { i, t -> history.add(i.toDouble(), t) }
        assertEquals(3, history.snapshot().size)
    }
}
