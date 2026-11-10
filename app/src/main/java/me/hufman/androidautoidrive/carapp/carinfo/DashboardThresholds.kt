package me.hufman.androidautoidrive.carapp.carinfo

import android.util.Log
import com.google.gson.JsonObject
import com.google.gson.JsonParser

enum class EngineProfile(val key: String) {
	AUTO("auto"), DIESEL("diesel"), PETROL("petrol");
	companion object {
		fun fromKey(key: String?) = values().firstOrNull { it.key == key } ?: AUTO
	}
}

data class TempThresholds(val scaleMin: Int, val scaleMax: Int, val cold: Int, val high: Int, val critical: Int) {
	fun isValid() = scaleMin < cold && cold < high && high < critical && critical <= scaleMax
}

data class RpmThresholds(val scaleMax: Int, val coldLimit: Int, val high: Int, val limit: Int) {
	fun isValid() = coldLimit < high && high < limit && limit <= scaleMax
}

data class DashboardThresholds(val oil: TempThresholds, val coolant: TempThresholds, val rpm: RpmThresholds)

enum class TempState { COLD, NORMAL, HIGH, CRITICAL }
enum class RpmState { NORMAL, GENTLE, HIGH, LIMIT }

object DashboardDefaults {
	val diesel = DashboardThresholds(
			oil = TempThresholds(40, 150, 70, 125, 135),
			coolant = TempThresholds(40, 130, 60, 110, 118),
			rpm = RpmThresholds(5500, 3000, 4200, 4800))
	val petrol = DashboardThresholds(
			oil = TempThresholds(40, 160, 80, 130, 140),
			coolant = TempThresholds(40, 130, 60, 115, 122),
			rpm = RpmThresholds(7500, 4000, 6000, 6500))

	fun forProfile(profile: EngineProfile) = if (profile == EngineProfile.PETROL) petrol else diesel
}

object DashboardConfig {
	private const val TAG = "DashboardConfig"
	const val TEMP_HYSTERESIS = 2.0
	const val RPM_HYSTERESIS = 150.0
	const val MIN_REFRESH_MS = 500
	const val MAX_REFRESH_MS = 4000
	const val DEFAULT_REFRESH_MS = 1000

	fun detectedFuel(fuelType: Int?): EngineProfile? = when (fuelType) {
		4, 5 -> EngineProfile.PETROL
		6, 7 -> EngineProfile.DIESEL
		else -> null
	}

	/** The concrete profile to use: forced, else detected from the car, else the last detected one, else diesel */
	fun resolveProfile(setting: EngineProfile, fuelType: Int?, lastFuel: String?): EngineProfile {
		if (setting != EngineProfile.AUTO) return setting
		return detectedFuel(fuelType) ?: EngineProfile.fromKey(lastFuel).takeIf { it != EngineProfile.AUTO } ?: EngineProfile.DIESEL
	}

	fun refreshMs(setting: String?): Int = (setting?.trim()?.toIntOrNull() ?: DEFAULT_REFRESH_MS).coerceIn(MIN_REFRESH_MS, MAX_REFRESH_MS)

	private fun warn(message: String) {
		try { Log.w(TAG, message) } catch (_: RuntimeException) {}
	}

	private fun parse(json: String?): JsonObject = try {
		JsonParser.parseString(json?.takeIf { it.isNotBlank() } ?: "{}").asJsonObject
	} catch (e: Exception) {
		warn("Ignoring malformed dashboard thresholds: $json")
		JsonObject()
	}

	private fun JsonObject.int(key: String, default: Int): Int = try {
		get(key)?.takeIf { it.isJsonPrimitive }?.asInt ?: default
	} catch (e: Exception) { default }

	private fun JsonObject.obj(key: String): JsonObject = try {
		getAsJsonObject(key) ?: JsonObject()
	} catch (e: Exception) { JsonObject() }

	/** Thresholds for a concrete profile: the defaults with any valid overrides from the settings JSON applied */
	fun thresholds(profile: EngineProfile, overridesJson: String?): DashboardThresholds {
		val defaults = DashboardDefaults.forProfile(profile)
		val overrides = parse(overridesJson).obj(profile.key)
		fun temp(name: String, d: TempThresholds): TempThresholds {
			val o = overrides.obj(name)
			val t = TempThresholds(o.int("scaleMin", d.scaleMin), o.int("scaleMax", d.scaleMax), o.int("cold", d.cold), o.int("high", d.high), o.int("critical", d.critical))
			return if (t.isValid()) t else { warn("Invalid $name thresholds for ${profile.key}, using defaults"); d }
		}
		val r = overrides.obj("rpm")
		val rpm = RpmThresholds(r.int("scaleMax", defaults.rpm.scaleMax), r.int("coldLimit", defaults.rpm.coldLimit),
				r.int("high", defaults.rpm.high), r.int("limit", defaults.rpm.limit))
		return DashboardThresholds(temp("oil", defaults.oil), temp("coolant", defaults.coolant),
				if (rpm.isValid()) rpm else { warn("Invalid rpm thresholds for ${profile.key}, using defaults"); defaults.rpm })
	}

	/** Returns the overrides JSON with one value of one profile changed. A value equal to the default removes the override */
	fun withOverride(overridesJson: String?, profile: EngineProfile, section: String, key: String, value: Int, default: Int): String {
		val root = parse(overridesJson)
		val profileObj = root.obj(profile.key).also { root.add(profile.key, it) }
		val sectionObj = profileObj.obj(section).also { profileObj.add(section, it) }
		if (value == default) sectionObj.remove(key) else sectionObj.addProperty(key, value)
		if (sectionObj.size() == 0) profileObj.remove(section)
		return root.toString()
	}

	/** Returns the overrides JSON without any overrides for this profile */
	fun withoutProfile(overridesJson: String?, profile: EngineProfile): String {
		val root = parse(overridesJson)
		root.remove(profile.key)
		return root.toString()
	}
}

/** Remembers the last state so that a state is only left after the value moved past the limit by the hysteresis */
class TempStateTracker {
	private var state = TempState.NORMAL
	fun update(value: Double?, t: TempThresholds, hysteresis: Double = DashboardConfig.TEMP_HYSTERESIS): TempState {
		if (value == null) { state = TempState.NORMAL; return state }
		var s = when {
			value >= t.critical -> TempState.CRITICAL
			value >= t.high -> TempState.HIGH
			value < t.cold -> TempState.COLD
			else -> TempState.NORMAL
		}
		if (state == TempState.CRITICAL && s < TempState.CRITICAL && value >= t.critical - hysteresis) s = TempState.CRITICAL
		if (s < TempState.HIGH && (state == TempState.HIGH || state == TempState.CRITICAL) && value >= t.high - hysteresis) s = TempState.HIGH
		if (state == TempState.COLD && s == TempState.NORMAL && value < t.cold + hysteresis) s = TempState.COLD
		state = s
		return s
	}
}

class RpmStateTracker {
	private var state = RpmState.NORMAL
	fun update(rpm: Double?, oilCold: Boolean, t: RpmThresholds, hysteresis: Double = DashboardConfig.RPM_HYSTERESIS): RpmState {
		if (rpm == null) { state = RpmState.NORMAL; return state }
		var s = when {
			rpm >= t.limit -> RpmState.LIMIT
			rpm >= t.high -> RpmState.HIGH
			oilCold && rpm >= t.coldLimit -> RpmState.GENTLE
			else -> RpmState.NORMAL
		}
		if (state == RpmState.LIMIT && s < RpmState.LIMIT && rpm >= t.limit - hysteresis) s = RpmState.LIMIT
		if (s < RpmState.HIGH && (state == RpmState.HIGH || state == RpmState.LIMIT) && rpm >= t.high - hysteresis) s = RpmState.HIGH
		if (s < RpmState.GENTLE && oilCold && state == RpmState.GENTLE && rpm >= t.coldLimit - hysteresis) s = RpmState.GENTLE
		state = s
		return s
	}
}
