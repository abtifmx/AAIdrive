package me.hufman.androidautoidrive.phoneui.fragments

import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import me.hufman.androidautoidrive.AppSettings
import me.hufman.androidautoidrive.BooleanLiveSetting
import me.hufman.androidautoidrive.CarInformation
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.carapp.carinfo.*
import me.hufman.androidautoidrive.phoneui.TelemetryUi

/** Lets the user choose the engine profile, change the warning thresholds of the dashboard and name the car */
class DashboardSettingsFragment: Fragment() {
	private val tempKeys = listOf("scaleMin" to R.string.ds_scale_min, "cold" to R.string.ds_cold_below, "high" to R.string.ds_high_from,
			"critical" to R.string.ds_critical_from, "scaleMax" to R.string.ds_scale_max)
	private val rpmKeys = listOf("coldLimit" to R.string.ds_gentle_from, "high" to R.string.ds_high_from,
			"limit" to R.string.ds_limit_from, "scaleMax" to R.string.ds_scale_max)

	private var editing = EngineProfile.DIESEL
	private var updating = false
	private var preview: ImageView? = null
	private val fields = HashMap<String, HashMap<String, EditText>>()
	private var diesel: RadioButton? = null
	private var petrol: RadioButton? = null

	private fun setting(key: AppSettings.KEYS) = AppSettings[key]
	private fun save(key: AppSettings.KEYS, value: String) = AppSettings.saveSetting(requireContext().applicationContext, key, value)

	/** The profile the car uses now, from the setting or the last engine that the car reported */
	private fun activeProfile() = DashboardConfig.resolveProfile(EngineProfile.fromKey(setting(AppSettings.KEYS.DASHBOARD_ENGINE_PROFILE)), null, setting(AppSettings.KEYS.DASHBOARD_LAST_FUEL))

	private fun EditText.onText(block: (String) -> Unit) = addTextChangedListener(object: TextWatcher {
		override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
		override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
		override fun afterTextChanged(s: Editable?) { if (!updating) block(s?.toString().orEmpty()) }
	})

	private fun TempThresholds.get(key: String) = when (key) {
		"scaleMin" -> scaleMin; "scaleMax" -> scaleMax; "cold" -> cold; "high" -> high; else -> critical
	}
	private fun RpmThresholds.get(key: String) = when (key) {
		"scaleMax" -> scaleMax; "coldLimit" -> coldLimit; "high" -> high; else -> limit
	}

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
		val context = requireContext()
		val root = TelemetryUi.column(context, 16)
		val resources = context.resources

		// engine profile
		val profileCard = TelemetryUi.card(context, root, getString(R.string.ds_profile))
		val profileGroup = RadioGroup(context)
		val profiles = listOf(EngineProfile.AUTO to R.string.ds_auto, EngineProfile.DIESEL to R.string.ds_diesel, EngineProfile.PETROL to R.string.ds_petrol)
		val selected = EngineProfile.fromKey(setting(AppSettings.KEYS.DASHBOARD_ENGINE_PROFILE))
		profiles.forEach { (profile, label) ->
			profileGroup.addView(RadioButton(context).apply { id = View.generateViewId(); setText(label); isChecked = profile == selected; tag = profile })
		}
		profileGroup.setOnCheckedChangeListener { group, checkedId ->
			val profile = group.findViewById<View>(checkedId)?.tag as? EngineProfile ?: return@setOnCheckedChangeListener
			save(AppSettings.KEYS.DASHBOARD_ENGINE_PROFILE, profile.key)
			updateProfileLabels()
		}
		profileCard.addView(profileGroup)
		val lastFuel = EngineProfile.fromKey(setting(AppSettings.KEYS.DASHBOARD_LAST_FUEL))
		val detected = when (lastFuel) {
			EngineProfile.DIESEL -> getString(R.string.ds_diesel)
			EngineProfile.PETROL -> getString(R.string.ds_petrol)
			else -> getString(R.string.ds_not_connected)
		}
		profileCard.addView(TelemetryUi.text(context, getString(R.string.ds_detected, detected), 13f))

		// thresholds
		val editCard = TelemetryUi.card(context, root, getString(R.string.ds_edit))
		val editGroup = RadioGroup(context).apply { orientation = RadioGroup.HORIZONTAL }
		diesel = RadioButton(context).apply { id = View.generateViewId(); isChecked = true }
		petrol = RadioButton(context).apply { id = View.generateViewId() }
		editGroup.addView(diesel); editGroup.addView(petrol)
		editing = activeProfile()
		(if (editing == EngineProfile.PETROL) petrol else diesel)?.isChecked = true
		editGroup.setOnCheckedChangeListener { _, checkedId ->
			editing = if (checkedId == petrol?.id) EngineProfile.PETROL else EngineProfile.DIESEL
			populate()
		}
		editCard.addView(editGroup)
		preview = ImageView(context).apply {
			adjustViewBounds = true
			layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = TelemetryUi.dp(context, 8) }
		}
		editCard.addView(preview)

		fun section(name: String, title: Int, keys: List<Pair<String, Int>>, validate: () -> String?) {
			val card = TelemetryUi.card(context, root, getString(title))
			fields[name] = HashMap()
			keys.forEach { (key, label) ->
				val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
				row.addView(TextView(context).apply { setText(label); textSize = 14f }, LinearLayout.LayoutParams(0, -2, 1f))
				val input = EditText(context).apply {
					inputType = InputType.TYPE_CLASS_NUMBER; setSingleLine(); minimumWidth = TelemetryUi.dp(context, 96); gravity = android.view.Gravity.END
				}
				row.addView(input, LinearLayout.LayoutParams(-2, -2))
				card.addView(row)
				fields[name]!![key] = input
				input.onText { onChanged(name) }
			}
		}
		section("oil", R.string.ds_section_oil, tempKeys) { null }
		section("coolant", R.string.ds_section_engine, tempKeys) { null }
		section("rpm", R.string.ds_section_rpm, rpmKeys) { null }

		val resetCard = TelemetryUi.card(context, root)
		resetCard.addView(Button(context).apply {
			setText(R.string.ds_reset)
			setOnClickListener {
				save(AppSettings.KEYS.DASHBOARD_THRESHOLDS, DashboardConfig.withoutProfile(setting(AppSettings.KEYS.DASHBOARD_THRESHOLDS), editing))
				populate()
			}
		})

		// model name
		val nameCard = TelemetryUi.card(context, root, getString(R.string.ds_model_name))
		val chassis = CarInformation().capabilities["vehicle.type"]
		val automatic = VehicleNames.resolve(null, null, chassis, when (lastFuel) { EngineProfile.DIESEL -> 6; EngineProfile.PETROL -> 4; else -> null })
		nameCard.addView(EditText(context).apply {
			setSingleLine(); hint = getString(R.string.ds_model_hint, automatic)
			setText(setting(AppSettings.KEYS.DASHBOARD_MODEL_NAME))
			onText { save(AppSettings.KEYS.DASHBOARD_MODEL_NAME, it.trim()) }
		})

		// refresh time, only for advanced users
		val refreshCard = TelemetryUi.card(context, root, getString(R.string.ds_refresh))
		refreshCard.addView(TelemetryUi.text(context, getString(R.string.ds_refresh_hint), 13f))
		refreshCard.addView(EditText(context).apply {
			inputType = InputType.TYPE_CLASS_NUMBER; setSingleLine()
			setText(DashboardConfig.refreshMs(setting(AppSettings.KEYS.DASHBOARD_REFRESH_MS)).toString())
			onText {
				val value = it.toIntOrNull()
				if (value == null || value !in DashboardConfig.MIN_REFRESH_MS..DashboardConfig.MAX_REFRESH_MS) error = getString(R.string.ds_error_refresh)
				else save(AppSettings.KEYS.DASHBOARD_REFRESH_MS, value.toString())
			}
		})
		BooleanLiveSetting(context.applicationContext, AppSettings.KEYS.SHOW_ADVANCED_SETTINGS).observe(viewLifecycleOwner, Observer {
			refreshCard.visibility = if (it) View.VISIBLE else View.GONE
		})

		updateProfileLabels()
		populate()
		return ScrollView(context).apply { addView(root) }
	}

	private fun updateProfileLabels() {
		val active = activeProfile()
		val activeText = getString(R.string.ds_active)
		diesel?.text = getString(R.string.ds_diesel) + if (active == EngineProfile.DIESEL) " · $activeText" else ""
		petrol?.text = getString(R.string.ds_petrol) + if (active == EngineProfile.PETROL) " · $activeText" else ""
	}

	private fun thresholds() = DashboardConfig.thresholds(editing, setting(AppSettings.KEYS.DASHBOARD_THRESHOLDS))

	/** Fill the fields with the thresholds of the profile that is being edited */
	private fun populate() {
		val t = thresholds()
		updating = true
		tempKeys.forEach { (key, _) ->
			fields["oil"]?.get(key)?.apply { setText(t.oil.get(key).toString()); error = null }
			fields["coolant"]?.get(key)?.apply { setText(t.coolant.get(key).toString()); error = null }
		}
		rpmKeys.forEach { (key, _) -> fields["rpm"]?.get(key)?.apply { setText(t.rpm.get(key).toString()); error = null } }
		updating = false
		refreshPreview()
	}

	private fun onChanged(section: String) {
		val inputs = fields[section] ?: return
		val values = inputs.mapValues { it.value.text.toString().trim().toIntOrNull() }
		val missing = values.filterValues { it == null }.keys
		inputs.forEach { (key, input) -> input.error = if (key in missing) getString(R.string.ds_error_number) else null }
		if (missing.isNotEmpty()) return
		val defaults = DashboardDefaults.forProfile(editing)
		val valid: Boolean
		val message: String
		val defaultFor: (String) -> Int
		if (section == "rpm") {
			val v = values.mapValues { it.value!! }
			valid = RpmThresholds(v["scaleMax"]!!, v["coldLimit"]!!, v["high"]!!, v["limit"]!!).isValid()
			message = getString(R.string.ds_error_rpm)
			defaultFor = { defaults.rpm.get(it) }
		} else {
			val v = values.mapValues { it.value!! }
			valid = TempThresholds(v["scaleMin"]!!, v["scaleMax"]!!, v["cold"]!!, v["high"]!!, v["critical"]!!).isValid()
			message = getString(R.string.ds_error_temp)
			defaultFor = { defaults.let { d -> (if (section == "oil") d.oil else d.coolant).get(it) } }
		}
		if (!valid) {
			inputs.values.first().error = message
			return
		}
		var json = setting(AppSettings.KEYS.DASHBOARD_THRESHOLDS)
		values.forEach { (key, value) -> json = DashboardConfig.withOverride(json, editing, section, key, value!!, defaultFor(key)) }
		save(AppSettings.KEYS.DASHBOARD_THRESHOLDS, json)
		refreshPreview()
	}

	private fun refreshPreview() {
		val frame = GaugeFrames.preview(thresholds(), DashboardLabels.fromResources(resources))
		preview?.setImageBitmap(DashboardRenderer.gaugeBitmap(frame))
	}

	override fun onDestroyView() {
		preview = null; diesel = null; petrol = null; fields.clear()
		super.onDestroyView()
	}
}
