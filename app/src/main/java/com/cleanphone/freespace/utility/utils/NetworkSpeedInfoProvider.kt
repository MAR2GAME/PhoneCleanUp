package com.cleanphone.freespace.utility.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import com.cleanphone.freespace.utility.R
import com.cleanphone.freespace.utility.bean.NetworkInfoSnapshot

import java.net.Inet4Address

object NetworkSpeedInfoProvider {
    fun getSnapshot(context: Context): NetworkInfoSnapshot {
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        val linkProperties = connectivityManager.getLinkProperties(activeNetwork)
        val wifiManager = context.applicationContext.getSystemService(WifiManager::class.java)

        val isWifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val networkType = when {
            isWifi -> context.getString(R.string.network_speed_type_wifi)
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true ->
                context.getString(R.string.network_speed_type_mobile)
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true ->
                context.getString(R.string.network_speed_type_ethernet)
            else -> context.getString(R.string.network_speed_unknown)
        }

        @Suppress("DEPRECATION")
        val wifiName = if (isWifi) {
            NetworkScanFormatter.cleanSsid(wifiManager.connectionInfo?.ssid).let { ssid ->
                if (NetworkScanFormatter.isUnknownSsid(ssid)) {
                    context.getString(R.string.network_scan_unknown_ssid)
                } else {
                    ssid
                }
            }
        } else {
            context.getString(R.string.network_speed_not_wifi)
        }

        val ipAddress = linkProperties?.linkAddresses
            ?.firstOrNull { it.address is Inet4Address && !it.address.isLoopbackAddress }
            ?.address
            ?.hostAddress
            ?: context.getString(R.string.network_speed_unknown)

        return NetworkInfoSnapshot(
            networkType = networkType,
            wifiName = wifiName,
            ipAddress = ipAddress
        )
    }
}
