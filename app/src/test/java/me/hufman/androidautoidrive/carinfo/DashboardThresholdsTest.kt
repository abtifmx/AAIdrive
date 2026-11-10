package me.hufman.androidautoidrive.carinfo

import me.hufman.androidautoidrive.carapp.carinfo.*
import org.junit.Assert.*
import org.junit.Test

class DashboardThresholdsTest {
	@Test fun defaultsDifferByProfile() {
		val diesel = DashboardConfig.thresholds(EngineProfile.DIESEL, "{}")
		val petrol = DashboardConfig.thresholds(EngineProfile.PETROL, "{}")
		assertEquals(TempThresholds(40, 150, 70, 125, 135), diesel.oil)
		assertEquals(RpmThresholds(5500, 3000, 4200, 4800), diesel.rpm)
		assertEquals(TempThresholds(40, 160, 80, 130, 140), petrol.oil)
		assertEquals(TempThresholds(40, 130, 60, 115, 122), petrol.coolant)
		assertEquals(RpmThresholds(7500, 4000, 6000, 6500), petrol.rpm)
		assertTrue(listOf(diesel.oil, diesel.coolant, petrol.oil, petrol.coolant).all { it.isValid() })
		assertTrue(diesel.rpm.isValid() && petrol.rpm.isValid())
	}

	@Test fun overridesApplyPerProfile() {
		val json = """{"diesel":{"oil":{"high":120},"rpm":{"limit":5000}},"petrol":{}}"""
		val diesel = DashboardConfig.thresholds(EngineProfile.DIESEL, json)
		assertEquals(120, diesel.oil.high)
		assertEquals(135, diesel.oil.critical)
		assertEquals(5000, diesel.rpm.limit)
		assertEquals(DashboardDefaults.petrol, DashboardConfig.thresholds(EngineProfile.PETROL, json))
	}

	@Test fun malformedOrInvalidOverridesFallBackToDefaults() {
		assertEquals(DashboardDefaults.diesel, DashboardConfig.thresholds(EngineProfile.DIESEL, "not json"))
		assertEquals(DashboardDefaults.diesel, DashboardConfig.thresholds(EngineProfile.DIESEL, "[]"))
		assertEquals(DashboardDefaults.diesel, DashboardConfig.thresholds(EngineProfile.DIESEL, """{"diesel":{"oil":{"high":"abc"}}}"""))
		// the high limit above the critical limit is invalid, only that section falls back
		val invalid = DashboardConfig.thresholds(EngineProfile.DIESEL, """{"diesel":{"oil":{"high":140},"coolant":{"high":105}}}""")
		assertEquals(DashboardDefaults.diesel.oil, invalid.oil)
		assertEquals(105, invalid.coolant.high)
	}

	@Test fun validationRules() {
		assertTrue(TempThresholds(40, 150, 70, 125, 135).isValid())
		assertFalse(TempThresholds(40, 150, 40, 125, 135).isValid())     // cold must be above the scale start
		assertFalse(TempThresholds(40, 150, 70, 70, 135).isValid())
		assertFalse(TempThresholds(40, 130, 70, 125, 135).isValid())     // critical beyond the scale
		assertTrue(TempThresholds(40, 135, 70, 125, 135).isValid())
		assertTrue(RpmThresholds(5500, 3000, 4200, 4800).isValid())
		assertFalse(RpmThresholds(5500, 4200, 4200, 4800).isValid())
		assertFalse(RpmThresholds(4500, 3000, 4200, 4800).isValid())
	}

	@Test fun overrideJsonIsBuiltAndPruned() {
		var json = "{}"
		json = DashboardConfig.withOverride(json, EngineProfile.DIESEL, "oil", "high", 120, 125)
		assertEquals(120, DashboardConfig.thresholds(EngineProfile.DIESEL, json).oil.high)
		// setting the default again removes the override
		json = DashboardConfig.withOverride(json, EngineProfile.DIESEL, "oil", "high", 125, 125)
		assertEquals("{\"diesel\":{}}", json)
		json = DashboardConfig.withOverride(json, EngineProfile.PETROL, "rpm", "limit", 6800, 6500)
		assertEquals(6800, DashboardConfig.thresholds(EngineProfile.PETROL, json).rpm.limit)
		assertEquals(DashboardDefaults.petrol, DashboardConfig.thresholds(EngineProfile.PETROL, DashboardConfig.withoutProfile(json, EngineProfile.PETROL)))
	}

	@Test fun temperatureStatesUseHysteresis() {
		val t = DashboardDefaults.diesel.coolant       // cold 60, high 110, critical 118
		val tracker = TempStateTracker()
		assertEquals(TempState.NORMAL, tracker.update(109.0, t))
		assertEquals(TempState.HIGH, tracker.update(110.0, t))
		assertEquals(TempState.HIGH, tracker.update(108.5, t))      // not 2 below the limit yet
		assertEquals(TempState.NORMAL, tracker.update(107.9, t))
		assertEquals(TempState.CRITICAL, tracker.update(118.0, t))
		assertEquals(TempState.CRITICAL, tracker.update(116.5, t))
		assertEquals(TempState.HIGH, tracker.update(115.0, t))
		assertEquals(TempState.COLD, tracker.update(59.0, t))
		assertEquals(TempState.COLD, tracker.update(61.0, t))       // not 2 above the limit yet
		assertEquals(TempState.NORMAL, tracker.update(62.0, t))
		assertEquals(TempState.NORMAL, tracker.update(null, t))
	}

	@Test fun rpmStatesUseHysteresisAndOilTemperature() {
		val t = DashboardDefaults.diesel.rpm       // gentle 3000, high 4200, limit 4800
		val tracker = RpmStateTracker()
		assertEquals(RpmState.NORMAL, tracker.update(3500.0, false, t))
		assertEquals(RpmState.GENTLE, tracker.update(3500.0, true, t))
		assertEquals(RpmState.HIGH, tracker.update(4300.0, true, t))     // the higher state wins
		assertEquals(RpmState.HIGH, tracker.update(4100.0, true, t))
		assertEquals(RpmState.GENTLE, tracker.update(4000.0, true, t))
		assertEquals(RpmState.NORMAL, tracker.update(4000.0, false, t))
		assertEquals(RpmState.LIMIT, tracker.update(4800.0, false, t))
		assertEquals(RpmState.LIMIT, tracker.update(4700.0, false, t))
		assertEquals(RpmState.HIGH, tracker.update(4600.0, false, t))
	}

	@Test fun profileIsChosenFromSettingOrFuelType() {
		fun resolve(setting: EngineProfile, fuel: Int?, last: String? = "") = DashboardConfig.resolveProfile(setting, fuel, last)
		listOf(4, 5).forEach { assertEquals(EngineProfile.PETROL, resolve(EngineProfile.AUTO, it)) }
		listOf(6, 7).forEach { assertEquals(EngineProfile.DIESEL, resolve(EngineProfile.AUTO, it)) }
		// unknown fuel types use the last detected one, or diesel
		listOf(0, 15, null).forEach {
			assertEquals(EngineProfile.DIESEL, resolve(EngineProfile.AUTO, it))
			assertEquals(EngineProfile.PETROL, resolve(EngineProfile.AUTO, it, "petrol"))
			assertEquals(EngineProfile.DIESEL, resolve(EngineProfile.AUTO, it, "diesel"))
		}
		// a forced profile wins over the car
		assertEquals(EngineProfile.PETROL, resolve(EngineProfile.PETROL, 6))
		assertEquals(EngineProfile.DIESEL, resolve(EngineProfile.DIESEL, 4))
	}

	@Test fun refreshIntervalIsClamped() {
		assertEquals(1000, DashboardConfig.refreshMs("1000"))
		assertEquals(500, DashboardConfig.refreshMs("100"))
		assertEquals(4000, DashboardConfig.refreshMs("99999"))
		assertEquals(1000, DashboardConfig.refreshMs("abc"))
		assertEquals(1000, DashboardConfig.refreshMs(null))
	}
}
