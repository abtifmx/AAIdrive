package me.hufman.androidautoidrive.carapp.carinfo

import android.util.Log
import com.google.gson.JsonObject
import de.bmw.idrive.BMWRemoting
import io.bimmergestalt.idriveconnectkit.CDS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import me.hufman.androidautoidrive.cds.CDSMetrics
import me.hufman.androidautoidrive.cds.CDSVehicleUnits
import me.hufman.androidautoidrive.cds.flow
import me.hufman.androidautoidrive.utils.Utils
import kotlin.math.abs
import kotlin.math.roundToInt

/** One side of the climate setting. Special settings such as LO or HI are not known yet, so [special] stays empty */
data class ClimateSide(val temperature: Double?, val special: String? = null)

/** The desired temperature in the display unit and the interior temperature, rounded to what is visible */
data class ClimateFrame(val left: ClimateSide, val right: ClimateSide, val indoor: Double?, val fahrenheit: Boolean, val rightHandDrive: Boolean)

object ClimateParser {
	private const val TAG = "DashboardClimate"

	/**
	 * Reads the desired temperature from the climate.driverSettings or climate.passengerSettings value, for example
	 * {"driverSettings":{"desiredTemperature":21.5,"program":1,"automaticBlower":1}}
	 * An unknown structure gives an empty side and is logged once per value
	 */
	fun parse(json: JsonObject?, key: String): ClimateSide {
		val temperature = try {
			json?.getAsJsonObject(key)?.get("desiredTemperature")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asDouble?.takeIf { it.isFinite() }
		} catch (e: Exception) { null }
		if (temperature == null) {
			try { Log.w(TAG, "Unknown $key value: $json") } catch (_: RuntimeException) {}
		}
		return ClimateSide(temperature)
	}
}

/** The measurements and texts of the climate image, without Android classes so that they can be tested */
object ClimateLayout {
	const val SCALE_LEFT = 40f
	const val SCALE_RIGHT = 720f
	const val MIN_DOT_DISTANCE = 26f
	const val DEVIATION_LIMIT_C = 0.3
	const val DEVIATION_LIMIT_F = 0.5

	fun scale(fahrenheit: Boolean): Pair<Int, Int> = if (fahrenheit) 61 to 82 else 16 to 28

	/** Rounds a desired temperature to half a degree and the interior temperature to a tenth */
	fun quantize(value: Double?, step: Double): Double? = value?.let { (it / step).roundToInt() * step }

	/** Where a value is on the scale, clamped to the ends */
	fun fraction(value: Double, fahrenheit: Boolean): Float {
		val (min, max) = scale(fahrenheit)
		return ((value - min) / (max - min)).toFloat().coerceIn(0f, 1f)
	}

	fun x(value: Double, fahrenheit: Boolean): Float = SCALE_LEFT + fraction(value, fahrenheit) * (SCALE_RIGHT - SCALE_LEFT)

	/** Moves two dots symmetrically apart if they would overlap */
	fun spread(left: Float, right: Float): Pair<Float, Float> {
		if (abs(right - left) >= MIN_DOT_DISTANCE) return left to right
		val middle = (left + right) / 2
		return if (left <= right) (middle - MIN_DOT_DISTANCE / 2) to (middle + MIN_DOT_DISTANCE / 2)
		else (middle + MIN_DOT_DISTANCE / 2) to (middle - MIN_DOT_DISTANCE / 2)
	}

	fun format(value: Double?, fahrenheit: Boolean): String = when {
		value == null -> "--"
		fahrenheit -> "${value.roundToInt()}°"
		else -> String.format(java.util.Locale.GERMAN, "%.1f°", value)
	}

	enum class Deviation { ABOVE, BELOW, ON_TARGET }

	/** How far the interior temperature is from the average desired temperature, if all values are known */
	fun deviation(indoor: Double?, left: Double?, right: Double?, fahrenheit: Boolean): Pair<Deviation, Double>? {
		if (indoor == null || left == null || right == null) return null
		val difference = indoor - (left + right) / 2
		val limit = if (fahrenheit) DEVIATION_LIMIT_F else DEVIATION_LIMIT_C
		return when {
			difference > limit -> Deviation.ABOVE to difference
			difference < -limit -> Deviation.BELOW to -difference
			else -> Deviation.ON_TARGET to 0.0
		}
	}

	fun deviationText(deviation: Pair<Deviation, Double>): String = when (deviation.first) {
		Deviation.ABOVE -> String.format(java.util.Locale.GERMAN, "%.1f° über Soll (Ø L/R)", deviation.second)
		Deviation.BELOW -> String.format(java.util.Locale.GERMAN, "%.1f° unter Soll (Ø L/R)", deviation.second)
		Deviation.ON_TARGET -> "auf Soll"
	}

	fun headers(rightHandDrive: Boolean): Pair<String, String> =
		if (rightHandDrive) "LINKS · BEIFAHRER" to "RECHTS · FAHRER" else "LINKS · FAHRER" to "RECHTS · BEIFAHRER"
}

/** The second row of the dashboard page: the desired temperatures on one scale */
class ClimateRow(metrics: CDSMetrics, private val rightHandDrive: Boolean, private val pacer: RenderPacer) {
	private val data = metrics.carInfo.cdsData

	private fun side(property: io.bimmergestalt.idriveconnectkit.CDSProperty, key: String) =
		data.flow[property].map { ClimateParser.parse(it, key) }.onStart { emit(ClimateSide(null)) }

	private val driver = side(CDS.CLIMATE.DRIVERSETTINGS, "driverSettings")
	private val passenger = side(CDS.CLIMATE.PASSENGERSETTINGS, "passengerSettings")
	private val indoor = data.flow[CDS.SENSORS.TEMPERATUREINTERIOR].map { LiveDashboard.number(it, "temperatureInterior") }.onStart { emit(null) }
	private val temperatureUnits = metrics.units.map { it.temperatureUnits }.onStart { emit(CDSVehicleUnits.Temperature.CELCIUS) }

	fun frames(): Flow<ClimateFrame> = combine(driver, passenger, indoor, temperatureUnits) { driver, passenger, indoor, units ->
		fun show(side: ClimateSide) = ClimateSide(ClimateLayout.quantize(side.temperature?.let { units.fromCarUnit(it) }, 0.5), side.special)
		val (left, right) = if (rightHandDrive) show(passenger) to show(driver) else show(driver) to show(passenger)
		ClimateFrame(left, right, ClimateLayout.quantize(indoor?.let { units.fromCarUnit(it) }, 0.1),
				units == CDSVehicleUnits.Temperature.FAHRENHEIT, rightHandDrive)
	}.distinctUntilChanged()

	val row: Flow<Any> = frames()
		.conflate()
		.onEach { pacer.awaitTurn(1) }
		.map { frame ->
			val started = System.nanoTime()
			val bitmap = ClimateRenderer.bitmap(frame)
			val bytes = Utils.compressBitmapPng(bitmap)
			bitmap.recycle()
			pacer.recordFrame(1, bytes.size, (System.nanoTime() - started) / 1_000_000)
			BMWRemoting.RHMIResourceData(BMWRemoting.RHMIResourceType.IMAGEDATA, bytes) as Any
		}
		.flowOn(Dispatchers.Default)
		.conflate()
}
