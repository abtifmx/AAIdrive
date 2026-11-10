package me.hufman.androidautoidrive.carinfo

import me.hufman.androidautoidrive.AppSettings
import me.hufman.androidautoidrive.carapp.carinfo.*
import org.junit.Assert.*
import org.junit.Test

class DashboardGaugesTest {
	private val labels = DashboardLabels.DEFAULT
	private val diesel = DashboardDefaults.diesel

	@Test fun temperatureCardShowsRoundedValueArcAndZones() {
		val card = GaugeFrames.temperature("ÖL", 92.4, diesel.oil, TempState.NORMAL, labels)
		assertEquals("92", card.value)
		assertEquals("°C", card.unit)
		assertEquals(57, card.arc)                  // (92 - 40) / 110 of 120 steps
		assertEquals(DashboardColors.NORMAL, card.arcColor)
		assertEquals("", card.stateText)
		assertFalse(card.cold)
		assertEquals(2, card.zones.size)
		assertEquals(DashboardColors.HIGH, card.zones[0].color)
		assertEquals(102, card.zones[0].alpha)
		assertEquals((125 - 40) / 110f, card.zones[0].from, 0.001f)
		assertEquals(DashboardColors.CRITICAL, card.zones[1].color)
		assertEquals(128, card.zones[1].alpha)
		assertEquals(1f, card.zones[1].to, 0.001f)
	}

	@Test fun temperatureStatesChangeColorAndText() {
		assertEquals("KALT", GaugeFrames.temperature("ÖL", 50.0, diesel.oil, TempState.COLD, labels).stateText)
		assertTrue(GaugeFrames.temperature("ÖL", 50.0, diesel.oil, TempState.COLD, labels).cold)
		assertEquals(DashboardColors.COLD, GaugeFrames.temperature("ÖL", 50.0, diesel.oil, TempState.COLD, labels).arcColor)
		assertEquals("HOCH", GaugeFrames.temperature("ÖL", 126.0, diesel.oil, TempState.HIGH, labels).stateText)
		val critical = GaugeFrames.temperature("ÖL", 140.0, diesel.oil, TempState.CRITICAL, labels)
		assertEquals("KRITISCH", critical.stateText)
		assertEquals(DashboardColors.CRITICAL, critical.arcColor)
	}

	@Test fun missingValuesAreDashesWithoutArc() {
		val card = GaugeFrames.temperature("ÖL", null, diesel.oil, TempState.NORMAL, labels)
		assertEquals("--", card.value)
		assertEquals(0, card.arc)
		assertEquals("--", GaugeFrames.rpm("DREHZAHL", null, diesel.rpm, RpmState.NORMAL, false, labels).value)
		assertEquals("--", GaugeFrames.speed("TEMPO", null).value)
	}

	@Test fun valuesBeyondTheScaleAreClamped() {
		assertEquals(GaugeFrame.ARC_STEPS, GaugeFrames.temperature("ÖL", 300.0, diesel.oil, TempState.CRITICAL, labels).arc)
		assertEquals(0, GaugeFrames.temperature("ÖL", 10.0, diesel.oil, TempState.COLD, labels).arc)
	}

	@Test fun rpmUsesThousandsSeparatorAndOptionalColdZone() {
		val warm = GaugeFrames.rpm("DREHZAHL", 3500.0, diesel.rpm, RpmState.NORMAL, false, labels)
		assertEquals("3.500", warm.value)
		assertEquals("U/min", warm.unit)
		assertEquals(76, warm.arc)
		assertEquals(2, warm.zones.size)
		val cold = GaugeFrames.rpm("DREHZAHL", 3500.0, diesel.rpm, RpmState.GENTLE, true, labels)
		assertEquals(3, cold.zones.size)
		assertEquals(3000 / 5500f, cold.zones[0].from, 0.001f)
		assertEquals("SCHONEN", cold.stateText)
		assertEquals("LIMIT", GaugeFrames.rpm("DREHZAHL", 4900.0, diesel.rpm, RpmState.LIMIT, false, labels).stateText)
	}

	@Test fun speedHasNoZonesAndNoStateText() {
		val card = GaugeFrames.speed("TEMPO", 130.0)
		assertEquals("130", card.value)
		assertEquals(60, card.arc)
		assertTrue(card.zones.isEmpty())
		assertEquals("", card.stateText)
	}

	@Test fun framesOnlyDifferWhenSomethingVisibleChanged() {
		fun frame(oil: Double) = GaugeFrame(listOf(GaugeFrames.temperature("ÖL", oil, diesel.oil, TempState.NORMAL, labels)))
		assertEquals(frame(92.0), frame(92.4))      // rounds to the same text and bow
		assertNotEquals(frame(92.0), frame(92.6))
	}

	@Test fun previewHasFourCards() {
		assertEquals(4, GaugeFrames.preview(diesel, labels).cards.size)
	}

	private class FakeSettings(var refresh: String = "1000"): AppSettings {
		override fun get(key: AppSettings.KEYS) = if (key == AppSettings.KEYS.DASHBOARD_REFRESH_MS) refresh else key.default
	}

	@Test fun pacerUsesTheConfiguredInterval() {
		var now = 0L
		val settings = FakeSettings("2000")
		val pacer = RenderPacer(settings) { now }
		assertEquals(2000, pacer.interval())
		settings.refresh = "100"
		assertEquals(500, pacer.interval())
		settings.refresh = "99999"
		assertEquals(4000, pacer.interval())
	}

	@Test fun pacerSlowsDownWhenTheCarIsSlow() {
		var now = 0L
		val pacer = RenderPacer(FakeSettings()) { now }
		pacer.recordSend(100)
		assertEquals(1000, pacer.interval())            // three times 100 ms is below the configured interval
		pacer.recordSend(600); pacer.recordSend(600)
		assertTrue(pacer.interval() > 1000)
	}

	@Test fun pacerDoublesTheIntervalAfterASlowSendForAWhile() {
		var now = 0L
		val pacer = RenderPacer(FakeSettings()) { now }
		pacer.recordSend(2000)
		assertEquals(RenderPacer.MAX_INTERVAL_MS, pacer.interval())   // 3 * 2000 doubled, capped
		now += RenderPacer.PENALTY_MS + 1
		assertEquals(6000, pacer.interval())
	}
}
