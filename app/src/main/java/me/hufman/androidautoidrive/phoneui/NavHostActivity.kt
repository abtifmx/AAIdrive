package me.hufman.androidautoidrive.phoneui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.children
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import me.hufman.androidautoidrive.*

class NavHostActivity: AppCompatActivity() {

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		AppSettings.loadSettings(this)
		if (AppSettings[AppSettings.KEYS.ENABLED_ANALYTICS].toBoolean()) {
			Analytics.init(this)
		}
		CarInformation.loadCache(MutableAppSettingsReceiver(applicationContext))

		if (!AppSettings[AppSettings.KEYS.FIRST_START_DONE].toBoolean()) {
			val intent = Intent(this, WelcomeActivity::class.java)
			startActivity(intent)
			finish()
			return
		}

		setContentView(R.layout.activity_navhost)
		val navToolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.nav_toolbar)
		val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_nav)
		setSupportActionBar(navToolbar)

		// the bottom bar entries are the top level destinations, everything else gets an up button
		val navController = findNavController(R.id.nav_host_fragment)
		val appBarConfig = AppBarConfiguration(setOf(R.id.nav_overview, R.id.nav_triplogs, R.id.nav_cds_live, R.id.nav_carinfo, R.id.nav_more))
		setupActionBarWithNavController(navController, appBarConfig)        // title updater
		navToolbar.setupWithNavController(navController, appBarConfig)     // up button click handler
		bottomNav.setupWithNavController(navController)                    // tab click handler

		// pages opened from the More page keep that tab highlighted
		val bottomIds = bottomNav.menu.children.map { it.itemId }.toSet()
		navController.addOnDestinationChangedListener { _, destination, _ ->
			if (destination.id !in bottomIds) {
				bottomNav.menu.findItem(R.id.nav_more).isChecked = true
			}
		}
	}

	override fun onResume() {
		super.onResume()

		startService()
	}

	fun startService() {
		try {
			if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
				// this is a clear signal of car connection, we can confidently startForeground
				this.startForegroundService(Intent(this, MainService::class.java).setAction(MainService.ACTION_START))
			} else {
				this.startService(Intent(this, MainService::class.java).setAction(MainService.ACTION_START))
			}
		} catch (e: IllegalStateException) {
			// Android Oreo strenuously objects to starting the service if the activity isn't visible
			// for example, when Android Studio tries to start the Activity with the screen off
		}
	}
}
