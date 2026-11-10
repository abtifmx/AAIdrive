package me.hufman.androidautoidrive.phoneui.fragments

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import me.hufman.androidautoidrive.AppSettings
import me.hufman.androidautoidrive.BooleanLiveSetting
import me.hufman.androidautoidrive.R

/** Secondary pages that don't fit in the bottom bar, with the rarely needed ones behind the advanced setting */
class MoreFragment: Fragment() {
	private class Entry(val destination: Int, val icon: Int, val title: Int, val advanced: Boolean = false)

	private val entries = listOf(
			Entry(R.id.nav_music, R.drawable.ic_music_feather, R.string.menu_music),
			Entry(R.id.nav_notifications, R.drawable.ic_bell_feather, R.string.menu_notifications),
			Entry(R.id.nav_calendar, R.drawable.ic_calendar_feather, R.string.menu_calendar),
			Entry(R.id.nav_navigation, R.drawable.ic_navigation_feather, R.string.menu_navigation),
			Entry(R.id.nav_maps, R.drawable.ic_map_feather, R.string.menu_maps),
			Entry(R.id.nav_dashboard_settings, R.drawable.ic_baseline_directions_car_24, R.string.ds_title),
			Entry(R.id.nav_settings, R.drawable.ic_settings_feather, R.string.menu_settings),
			Entry(R.id.nav_connection, R.drawable.ic_radio_feather, R.string.menu_connection, true),
			Entry(R.id.nav_assistants, R.drawable.ic_mic_feather, R.string.menu_assistants, true),
			Entry(R.id.nav_addons, R.drawable.ic_outline_extension_24, R.string.menu_addons, true),
			Entry(R.id.nav_support, R.drawable.ic_heart_feather, R.string.menu_support, true)
	)

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
		val context = requireContext()
		val density = resources.displayMetrics.density
		fun dp(value: Int) = (value * density).toInt()

		val themeAttrs = context.obtainStyledAttributes(R.style.optionMapVisible, arrayOf(android.R.attr.visibility).toIntArray())
		val mapVisible = themeAttrs.getInt(0, 0) == View.VISIBLE
		themeAttrs.recycle()

		val list = LinearLayout(context).apply {
			orientation = LinearLayout.VERTICAL
			setPadding(dp(16), dp(16), dp(16), dp(16))
		}
		val rows = entries.map { entry ->
			val row = LinearLayout(context).apply {
				orientation = LinearLayout.HORIZONTAL
				gravity = Gravity.CENTER_VERTICAL
				minimumHeight = dp(64)
				setPadding(dp(20), 0, dp(20), 0)
				background = ContextCompat.getDrawable(context, R.drawable.telemetry_card)
				isClickable = true
				isFocusable = true
				setOnClickListener { findNavController().navigate(entry.destination) }
			}
			row.addView(ImageView(context).apply {
				setImageResource(entry.icon)
				setColorFilter(ContextCompat.getColor(context, R.color.colorAccent))
			}, LinearLayout.LayoutParams(dp(24), dp(24)))
			row.addView(TextView(context).apply {
				setText(entry.title)
				textSize = 17f
				setTextColor(ContextCompat.getColor(context, R.color.telemetryText))
			}, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(20) })
			if (entry.destination == R.id.nav_maps && !mapVisible) {
				row.visibility = View.GONE
			}
			list.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
				bottomMargin = dp(10)
			})
			entry to row
		}

		BooleanLiveSetting(context, AppSettings.KEYS.SHOW_ADVANCED_SETTINGS).observe(viewLifecycleOwner, Observer { showAdvanced ->
			rows.filter { it.first.advanced }.forEach { it.second.visibility = if (showAdvanced) View.VISIBLE else View.GONE }
		})

		return ScrollView(context).apply { addView(list) }
	}
}
