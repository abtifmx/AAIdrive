package me.hufman.androidautoidrive.carinfo

import com.google.gson.JsonParser
import me.hufman.androidautoidrive.carapp.carinfo.ClimateLayout
import me.hufman.androidautoidrive.carapp.carinfo.ClimateParser
import org.junit.Assert.*
import org.junit.Test

class ClimateRowTest {
	private fun json(value: String) = JsonParser.parseString(value).asJsonObject

	@Test fun parsesTheValuesOfTheCar() {
		// recorded from a real car
		val driver = json("""{"driverSettings":{"desiredTemperature":21.5,"program":1,"automaticBlower":1}}""")
		val passenger = json("""{"passengerSettings":{"desiredTemperature":21.5,"program":63,"automaticBlower":0}}""")
		assertEquals(21.5, ClimateParser.parse(driver, "driverSettings").temperature!!, 0.0)
		assertEquals(21.5, ClimateParser.parse(passenger, "passengerSettings").temperature!!, 0.0)
		assertNull(ClimateParser.parse(driver, "driverSettings").special)
	}

	@Test fun unknownStructuresGiveNoValueWithoutCrashing() {
		assertNull(ClimateParser.parse(null, "driverSettings").temperature)
		assertNull(ClimateParser.parse(json("{}"), "driverSettings").temperature)
		assertNull(ClimateParser.parse(json("""{"error":401}"""), "driverSettings").temperature)
		assertNull(ClimateParser.parse(json("""{"driverSettings":null}"""), "driverSettings").temperature)
		assertNull(ClimateParser.parse(json("""{"driverSettings":{"program":1}}"""), "driverSettings").temperature)
		assertNull(ClimateParser.parse(json("""{"driverSettings":{"desiredTemperature":"LO"}}"""), "driverSettings").temperature)
		assertNull(ClimateParser.parse(json("""{"passengerSettings":{"desiredTemperature":21.5}}"""), "driverSettings").temperature)
	}

	@Test fun positionsOnTheScaleAreClamped() {
		assertEquals(40f, ClimateLayout.x(16.0, false), 0.01f)
		assertEquals(720f, ClimateLayout.x(28.0, false), 0.01f)
		assertEquals(380f, ClimateLayout.x(22.0, false), 0.01f)
		assertEquals(40f, ClimateLayout.x(10.0, false), 0.01f)
		assertEquals(720f, ClimateLayout.x(40.0, false), 0.01f)
		assertEquals(61 to 82, ClimateLayout.scale(true))
		assertEquals(40f, ClimateLayout.x(61.0, true), 0.01f)
		assertEquals(720f, ClimateLayout.x(82.0, true), 0.01f)
	}

	@Test fun closeDotsAreSpreadToTheMinimumDistance() {
		val (left, right) = ClimateLayout.spread(300f, 310f)
		assertEquals(26f, right - left, 0.01f)
		assertEquals(305f, (left + right) / 2, 0.01f)
		val (a, b) = ClimateLayout.spread(300f, 300f)
		assertEquals(26f, b - a, 0.01f)
		val (c, d) = ClimateLayout.spread(310f, 300f)       // keeps the order
		assertEquals(26f, c - d, 0.01f)
		assertEquals(100f to 200f, ClimateLayout.spread(100f, 200f))
	}

	@Test fun valuesAreRoundedToWhatIsVisible() {
		assertEquals(21.5, ClimateLayout.quantize(21.26, 0.5)!!, 0.0001)
		assertEquals(21.0, ClimateLayout.quantize(21.24, 0.5)!!, 0.0001)
		assertEquals(22.0, ClimateLayout.quantize(22.04, 0.1)!!, 0.0001)
		assertEquals(22.1, ClimateLayout.quantize(22.06, 0.1)!!, 0.0001)
		assertNull(ClimateLayout.quantize(null, 0.5))
	}

	@Test fun deviationFromTheAverageDesiredTemperature() {
		fun text(indoor: Double?, left: Double?, right: Double?, f: Boolean = false) =
			ClimateLayout.deviation(indoor, left, right, f)?.let { ClimateLayout.deviationText(it) }
		assertEquals("1,6° über Soll (Ø L/R)", text(23.1, 21.5, 21.5))
		assertEquals("1,5° unter Soll (Ø L/R)", text(20.0, 21.5, 21.5))
		assertEquals("auf Soll", text(21.7, 21.5, 21.5))
		assertEquals("auf Soll", text(21.79, 21.5, 21.5))            // just below the 0.3 limit
		assertEquals("0,5° über Soll (Ø L/R)", text(22.0, 21.0, 22.0))    // the average of both sides
		assertNull(text(null, 21.5, 21.5))
		assertNull(text(21.0, null, 21.5))
		assertNull(text(21.0, 21.5, null))
		assertEquals("auf Soll", text(70.8, 70.5, 70.5, f = true))        // Fahrenheit uses 0.5
	}

	@Test fun formatsInTheUnitOfTheCar() {
		assertEquals("21,5°", ClimateLayout.format(21.5, false))
		assertEquals("21,0°", ClimateLayout.format(21.0, false))
		assertEquals("71°", ClimateLayout.format(70.7, true))
		assertEquals("--", ClimateLayout.format(null, false))
	}

	@Test fun headersSwapDriverAndPassengerForRightHandDrive() {
		assertEquals("LINKS · FAHRER" to "RECHTS · BEIFAHRER", ClimateLayout.headers(false))
		assertEquals("LINKS · BEIFAHRER" to "RECHTS · FAHRER", ClimateLayout.headers(true))
	}
}
