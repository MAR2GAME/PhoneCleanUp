package com.cleanphone.freespace.utility.ui

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.databinding.DataBindingUtil.setContentView
import androidx.recyclerview.widget.LinearLayoutManager
import com.cleanphone.freespace.utility.R
import com.cleanphone.freespace.utility.adapter.NetworkDevicesAdapter
import com.cleanphone.freespace.utility.base.activity.BaseViewActivity
import com.cleanphone.freespace.utility.base.command.safeClick
import com.cleanphone.freespace.utility.databinding.ActivityNetworkDevicesBinding
import com.cleanphone.freespace.utility.utils.NetworkScanStore

class NetworkDevicesActivity : BaseViewActivity<ActivityNetworkDevicesBinding>(){
    private lateinit var devicesAdapter: NetworkDevicesAdapter
    override fun initializeActivity() {
        super.initializeActivity()
        setupRecyclerView()
        binding.llBack.safeClick { finish() }
        val snapshot = NetworkScanStore.getLastSnapshot()
        val devices = snapshot?.devices.orEmpty()
        binding.tvDevices.text=getString(R.string.network_scan_devices)+" (${devices.size})"
        devicesAdapter.submitList(devices)
    }

    private fun setupRecyclerView() {
        devicesAdapter = NetworkDevicesAdapter()
        binding.rvDevices.apply {
            adapter = devicesAdapter
            layoutManager = LinearLayoutManager(this@NetworkDevicesActivity)
            setHasFixedSize(true)
        }
    }

}