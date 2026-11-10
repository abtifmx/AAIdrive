package me.hufman.androidautoidrive.carapp.carinfo

import android.content.res.Resources
import de.bmw.idrive.BMWRemoting
import io.bimmergestalt.idriveconnectkit.CDS
import io.bimmergestalt.idriveconnectkit.CDSProperty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import me.hufman.androidautoidrive.AppSettings
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.cds.CDSMetrics
import me.hufman.androidautoidrive.cds.flow
import me.hufman.androidautoidrive.utils.Utils
import java.util.Locale
import kotlin.math.roundToInt

/** The texts in the gauge row, in the language of the phone */
data class DashboardLabels(val oil: String, val engine: String, val rpm: String, val speed: String,
                           val cold: String, val high: String, val critical: String, val gentle: String, val limit: String) {
	companion object {
		val DEFAULT = DashboardLabels("ÖL", "MOTOR", "DREHZAHL", "TEMPO", "KALT", "HOCH", "KRITISCH", "SCHONEN", "LIMIT")

		fun fromResources(resources: Resources): DashboardLabels = try {
			fun text(id: Int) = resources.getString(id).uppercase(Locale.getDefault())
			DashboardLabels(text(R.string.dash_label_oil), text(R.string.dash_label_engine), text(R.string.dash_label_rpm), text(R.string.dash_label_speed),
					text(R.string.dash_state_cold), text(R.string.dash_state_high), text(R.string.dash_state_critical), text(R.string.dash_state_gentle), text(R.string.dash_state_limit))
		} catch (e: Exception) { DEFAULT }
	}
}

/** Builds what is drawn for each gauge, without any Android classes so that it can be tested */
object GaugeFrames {
	private fun fraction(value: Int, min: Int, max: Int) = ((value - min).toFloat() / (max - min)).coerceIn(0f, 1f)
	private fun arc(value: Double?, min: Int, max: Int) =
		if (value == null) 0 else (fraction(value.roundToInt(), min, max) * GaugeFrame.ARC_STEPS).roundToInt()
	private fun zone(from: Int, to: Int, min: Int, max: Int, color: Int, alpha: Int) =
		GaugeZone(fraction(from, min, max), fraction(to, min, max), color, alpha)
	private fun whole(value: Double?) = value?.roundToInt()?.toString() ?: "--"
	private fun thousands(value: Double?) = value?.roundToInt()?.let { String.format(Locale.GERMAN, "%,d", it) } ?: "--"

	fun temperature(label: String, value: Double?, t: TempThresholds, state: TempState, labels: DashboardLabels): GaugeCard {
		val color = when (state) {
			TempState.COLD -> DashboardColors.COLD
			TempState.NORMAL -> DashboardColors.NORMAL
			TempState.HIGH -> DashboardColors.HIGH
			TempState.CRITICAL -> DashboardColors.CRITICAL
		}
		val text = when (state) {
			TempState.COLD -> labels.cold
			TempState.HIGH -> labels.high
			TempState.CRITICAL -> labels.critical
			TempState.NORMAL -> ""
		}
		val zones = listOf(
				zone(t.high, t.critical, t.scaleMin, t.scaleMax, DashboardColors.HIGH, 102),
				zone(t.critical, t.scaleMax, t.scaleMin, t.scaleMax, DashboardColors.CRITICAL, 128))
		return GaugeCard(label, whole(value), "°C", arc(value, t.scaleMin, t.scaleMax), color, zones, text, state == TempState.COLD)
	}

	fun rpm(label: String, value: Double?, t: RpmThresholds, state: RpmState, oilCold: Boolean, labels: DashboardLabels): GaugeCard {
		val color = when (state) {
			RpmState.NORMAL -> DashboardColors.NORMAL
			RpmState.GENTLE, RpmState.HIGH -> DashboardColors.HIGH
			RpmState.LIMIT -> DashboardColors.CRITICAL
		}
		val text = when (state) {
			RpmState.GENTLE -> labels.gentle
			RpmState.HIGH -> labels.high
			RpmState.LIMIT -> labels.limit
			RpmState.NORMAL -> ""
		}
		val zones = listOfNotNull(
				if (oilCold) zone(t.coldLimit, t.high, 0, t.scaleMax, DashboardColors.HIGH, 102) else null,
				zone(t.high, t.limit, 0, t.scaleMax, DashboardColors.HIGH, 102),
				zone(t.limit, t.scaleMax, 0, t.scaleMax, DashboardColors.CRITICAL, 128))
		return GaugeCard(label, thousands(value), "U/min", arc(value, 0, t.scaleMax), color, zones, text, false)
	}

	fun speed(label: String, value: Double?): GaugeCard =
		GaugeCard(label, whole(value), "km/h", arc(value, 0, 260), DashboardColors.NORMAL, emptyList(), "", false)

	/** An example frame for the preview in the settings, with the values in the middle of the scales */
	fun preview(t: DashboardThresholds, labels: DashboardLabels): GaugeFrame = GaugeFrame(listOf(
			temperature(labels.oil, (t.oil.scaleMin + t.oil.scaleMax) / 2.0, t.oil, TempState.NORMAL, labels),
			temperature(labels.engine, (t.coolant.scaleMin + t.coolant.scaleMax) / 2.0, t.coolant, TempState.NORMAL, labels),
			rpm(labels.rpm, t.rpm.scaleMax / 2.0, t.rpm, RpmState.NORMAL, false, labels),
			speed(labels.speed, 130.0)))
}

/** The first row of the dashboard page: oil, coolant, rpm and speed as gauges in one image */
class DashboardGauges(metrics: CDSMetrics, private val settings: AppSettings, private val pacer: RenderPacer, private val labels: DashboardLabels,
                      private val persistSetting: (AppSettings.KEYS, String) -> Unit) {
	companion object {
		private const val TAG = "DashboardRender"
	}
	private val data = metrics.carInfo.cdsData

	private fun number(property: CDSProperty, vararg path: String, valid: ClosedFloatingPointRange<Double>): Flow<Double?> =
		data.flow[property].map { json -> LiveDashboard.number(json, *path)?.takeIf { it in valid } }.onStart { emit(null) }

	private val oil = number(CDS.ENGINE.TEMPERATURE, "temperature", "oil", valid = -50.0..254.0)
	private val coolant = number(CDS.ENGINE.TEMPERATURE, "temperature", "engine", valid = -50.0..254.0)
	private val rpm = number(CDS.ENGINE.RPMSPEED, "RPMSpeed", valid = 0.0..10000.0)
	private val speed = number(CDS.DRIVING.SPEEDACTUAL, "speedActual", valid = 0.0..400.0)
	private val fuelType = metrics.fuelType.onStart { emit(null) }

	private var cachedKey: Pair<EngineProfile, String>? = null
	private var cachedThresholds: DashboardThresholds? = null

	private fun thresholds(profile: EngineProfile): DashboardThresholds {
		val key = profile to settings[AppSettings.KEYS.DASHBOARD_THRESHOLDS]
		if (key != cachedKey) {
			cachedThresholds = DashboardConfig.thresholds(profile, key.second)
			cachedKey = key
		}
		return cachedThresholds!!
	}

	private fun rememberFuel(fuelType: Int?) {
		val detected = DashboardConfig.detectedFuel(fuelType) ?: return
		if (settings[AppSettings.KEYS.DASHBOARD_LAST_FUEL] != detected.key) {
			persistSetting(AppSettings.KEYS.DASHBOARD_LAST_FUEL, detected.key)
		}
	}

	/** The display values of the gauges. Equal frames are not emitted twice */
	fun frames(): Flow<GaugeFrame> = flow {
		val oilState = TempStateTracker()
		val coolantState = TempStateTracker()
		val rpmState = RpmStateTracker()
		emitAll(combine(oil, coolant, rpm, speed, fuelType) { oil, coolant, rpm, speed, fuel ->
			rememberFuel(fuel)
			val profile = DashboardConfig.resolveProfile(EngineProfile.fromKey(settings[AppSettings.KEYS.DASHBOARD_ENGINE_PROFILE]), fuel, settings[AppSettings.KEYS.DASHBOARD_LAST_FUEL])
			val t = thresholds(profile)
			val oilTemp = oilState.update(oil, t.oil)
			val oilCold = oilTemp == TempState.COLD
			GaugeFrame(listOf(
					GaugeFrames.temperature(labels.oil, oil, t.oil, oilTemp, labels),
					GaugeFrames.temperature(labels.engine, coolant, t.coolant, coolantState.update(coolant, t.coolant), labels),
					GaugeFrames.rpm(labels.rpm, rpm, t.rpm, rpmState.update(rpm, oilCold, t.rpm), oilCold, labels),
					GaugeFrames.speed(labels.speed, speed)))
		})
	}.distinctUntilChanged()

	/** Draws and compresses off the car's thread, and always only sends the newest image */
	val row: Flow<Any> = frames()
		.conflate()
		.onEach { pacer.awaitTurn(0) }
		.map { frame ->
			val started = System.nanoTime()
			val bitmap = DashboardRenderer.gaugeBitmap(frame)
			val bytes = Utils.compressBitmapPng(bitmap)
			bitmap.recycle()
			pacer.recordFrame(0, bytes.size, (System.nanoTime() - started) / 1_000_000)
			BMWRemoting.RHMIResourceData(BMWRemoting.RHMIResourceType.IMAGEDATA, bytes) as Any
		}
		.flowOn(Dispatchers.Default)
		.conflate()
}
