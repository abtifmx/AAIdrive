package me.hufman.androidautoidrive.phoneui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.databinding.CarInfoPageBinding
import me.hufman.androidautoidrive.phoneui.viewmodels.CarSummaryModel

class CarInfoPageFragment: Fragment() {
	val viewModel by activityViewModels<CarSummaryModel> { CarSummaryModel.Factory(requireContext().applicationContext) }

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
		val binding = CarInfoPageBinding.inflate(inflater, container, false)
		binding.lifecycleOwner = viewLifecycleOwner
		binding.viewModel = viewModel
		binding.root.findViewById<View>(R.id.buttonDashboardSettings).setOnClickListener { findNavController().navigate(R.id.nav_dashboard_settings) }
		return binding.root
	}
}
