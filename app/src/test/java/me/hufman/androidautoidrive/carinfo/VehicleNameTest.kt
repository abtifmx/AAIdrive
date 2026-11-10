package me.hufman.androidautoidrive.carinfo

import me.hufman.androidautoidrive.carapp.carinfo.VehicleNames
import org.junit.Assert.assertEquals
import org.junit.Test

class VehicleNameTest {
	@Test fun customNameWinsOverEverything() {
		assertEquals("Mein Auto", VehicleNames.resolve("  Mein Auto ", "WBA3Q51234567890X", "F36", 6))
	}

	@Test fun blankCustomNameMeansAutomatic() {
		assertEquals("4er Gran Coupé d", VehicleNames.resolve("  ", null, "F36", 6))
		assertEquals("4er Gran Coupé d", VehicleNames.resolve("", null, "F36", 6))
	}

	@Test fun shortOrUnknownVinFallsThroughToTheChassisCode() {
		// the car often only reports 7 characters, and the table has no verified entries yet
		assertEquals("4er Coupé i", VehicleNames.resolve(null, "BP34061", "F32", 4))
		assertEquals("4er Coupé i", VehicleNames.resolve(null, "WBA3Q51234567890X", "F32", 5))
	}

	@Test fun chassisCodeAndFuelTypeGiveTheName() {
		assertEquals("4er Coupé d", VehicleNames.resolve(null, null, "F32", 7))
		assertEquals("4er Cabrio", VehicleNames.resolve(null, null, "F33", null))
		assertEquals("4er Gran Coupé d", VehicleNames.resolve(null, null, "F36", 6))
		assertEquals("4er Coupé i", VehicleNames.resolve(null, null, "G22", 4))
		assertEquals("4er Cabrio", VehicleNames.resolve(null, null, "G23", 0))
		assertEquals("4er Gran Coupé", VehicleNames.resolve(null, null, "G26", 15))
		assertEquals("4er Coupé d", VehicleNames.resolve(null, null, " f32 ", 6))
	}

	@Test fun otherSeriesAndModelsUseTheChassisName() {
		assertEquals("X1", VehicleNames.resolve(null, null, "F48", 6))
	}

	@Test fun unknownCarsFallBackToTheBrand() {
		assertEquals("BMW", VehicleNames.resolve(null, null, null, 6))
		assertEquals("BMW", VehicleNames.resolve(null, null, "Unknown", 6))
		assertEquals("BMW", VehicleNames.resolve(null, null, "R56", 6))       // a MINI chassis
	}
}
