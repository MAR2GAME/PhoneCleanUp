package com.cleanphone.freespace.utility.utils

import android.annotation.SuppressLint
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.cleanphone.freespace.utility.R
import com.cleanphone.freespace.utility.bean.NetworkDeviceInfo
import com.cleanphone.freespace.utility.bean.NetworkScanDetail
import com.cleanphone.freespace.utility.bean.NetworkScanSnapshot

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLException

object NetworkScanAnalyzer {
    private const val PROBE_TIMEOUT_MS = 150
    private const val HTTPS_TIMEOUT_MS = 1800
    private const val MAX_HOSTS_TO_PROBE = 64
    private const val PROBE_PORT = 80

    suspend fun scan(context: Context): NetworkScanSnapshot = withContext(Dispatchers.IO) {
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
        val wifiManager = context.applicationContext.getSystemService(WifiManager::class.java)

        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        val linkProperties = connectivityManager.getLinkProperties(activeNetwork)

        val isConnectedToWifi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        } else {
            @Suppress("DEPRECATION")
            connectivityManager.activeNetworkInfo?.type == ConnectivityManager.TYPE_WIFI
        }

        @Suppress("DEPRECATION")
        val wifiInfo = wifiManager.connectionInfo
        val ssid = NetworkScanFormatter.cleanSsid(wifiInfo?.ssid)
        val securityLabel = resolveSecurityLabel(context, wifiManager, ssid)
        val gatewayIp = resolveGatewayIp(linkProperties)
        val localIp = resolveLocalIpv4(linkProperties)
        val dnsServers = linkProperties?.dnsServers
            ?.filterIsInstance<Inet4Address>()
            ?.mapNotNull { it.hostAddress }
            .orEmpty()

        val arpEntries = readArpTable()
        val discoveredDevices = if (isConnectedToWifi) {
            discoverDevices(
                context = context,
                localIp = localIp,
                gatewayIp = gatewayIp,
                arpEntries = arpEntries
            )
        } else {
            emptyList()
        }

        val httpCheck = if (isConnectedToWifi) performHttpCheck(useHttps = false) else ProbeStatus.UNKNOWN
        val httpsCheck = if (isConnectedToWifi) performHttpCheck(useHttps = true) else ProbeStatus.UNKNOWN

        val details = listOf(
            buildWifiAuthDetail(context, isConnectedToWifi, securityLabel),
            buildArpSpoofingDetail(context, isConnectedToWifi, gatewayIp, arpEntries),
            buildSslStrippingDetail(context, capabilities, httpCheck),
            buildSslSplittingDetail(context, capabilities, httpsCheck),
            buildDnsSpoofingDetail(context, isConnectedToWifi, dnsServers),
            buildDevicesDetail(context, isConnectedToWifi, discoveredDevices)
        )

        val lastScanTime = System.currentTimeMillis()
        val snapshot = NetworkScanSnapshot(
            ssid = if (isConnectedToWifi && !NetworkScanFormatter.isUnknownSsid(ssid)) {
                ssid
            } else {
                context.getString(R.string.network_scan_unknown_ssid)
            },
            lastScanTime = lastScanTime,
            details = details,
            isConnectedToWifi = isConnectedToWifi,
            devices = discoveredDevices
        )
        NetworkScanStore.saveSnapshot(snapshot)
        snapshot
    }

    private fun buildWifiAuthDetail(
        context: Context,
        isConnectedToWifi: Boolean,
        securityLabel: String
    ): NetworkScanDetail {
        return when {
            !isConnectedToWifi -> warningDetail(
                context,
                R.string.network_scan_wifi_auth,
                context.getString(R.string.network_scan_not_connected)
            )
            securityLabel == "Unknown" -> unknownDetail(context, R.string.network_scan_wifi_auth)
            else -> safeDetail(
                context,
                R.string.network_scan_wifi_auth,
                if (securityLabel == "Open") {
                    context.getString(R.string.network_scan_security_open)
                } else {
                    securityLabel
                }
            )
        }
    }

    private fun buildArpSpoofingDetail(
        context: Context,
        isConnectedToWifi: Boolean,
        gatewayIp: String?,
        arpEntries: List<ArpEntry>
    ): NetworkScanDetail {
        if (!isConnectedToWifi) {
            return warningDetail(
                context,
                R.string.network_scan_arp_spoofing,
                context.getString(R.string.network_scan_not_connected)
            )
        }
        if (gatewayIp.isNullOrBlank()) {
            return unknownDetail(context, R.string.network_scan_arp_spoofing)
        }

        val gatewayMacs = arpEntries
            .filter { it.ipAddress == gatewayIp }
            .map { it.macAddress.lowercase() }
            .distinct()

        return when {
            gatewayMacs.isEmpty() -> unknownDetail(context, R.string.network_scan_arp_spoofing)
            gatewayMacs.size > 1 -> warningDetail(
                context,
                R.string.network_scan_arp_spoofing,
                context.getString(R.string.network_scan_gateway_conflict)
            )
            gatewayMacs.first().startsWith("00:00") || gatewayMacs.first().startsWith("ff:ff") -> warningDetail(
                context,
                R.string.network_scan_arp_spoofing,
                context.getString(R.string.network_scan_gateway_conflict)
            )
            else -> safeDetail(
                context,
                R.string.network_scan_arp_spoofing,
                context.getString(R.string.network_scan_no_conflict_detected)
            )
        }
    }

    private fun buildSslStrippingDetail(
        context: Context,
        capabilities: NetworkCapabilities?,
        probeStatus: ProbeStatus
    ): NetworkScanDetail {
        return when {
            capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL) == true ->
                warningDetail(
                    context,
                    R.string.network_scan_ssl_stripping,
                    context.getString(R.string.network_scan_redirect_detected)
                )
            probeStatus == ProbeStatus.SAFE -> safeDetail(
                context,
                R.string.network_scan_ssl_stripping,
                context.getString(R.string.network_scan_no_redirect_detected)
            )
            probeStatus == ProbeStatus.WARNING -> warningDetail(
                context,
                R.string.network_scan_ssl_stripping,
                context.getString(R.string.network_scan_redirect_detected)
            )
            else -> unknownDetail(context, R.string.network_scan_ssl_stripping)
        }
    }

    private fun buildSslSplittingDetail(
        context: Context,
        capabilities: NetworkCapabilities?,
        probeStatus: ProbeStatus
    ): NetworkScanDetail {
        if (capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) != true &&
            probeStatus == ProbeStatus.UNKNOWN
        ) {
            return unknownDetail(context, R.string.network_scan_ssl_splitting)
        }

        return when (probeStatus) {
            ProbeStatus.SAFE -> safeDetail(
                context,
                R.string.network_scan_ssl_splitting,
                context.getString(R.string.network_scan_https_verified)
            )
            ProbeStatus.WARNING -> warningDetail(
                context,
                R.string.network_scan_ssl_splitting,
                context.getString(R.string.network_scan_https_check_failed)
            )
            ProbeStatus.UNKNOWN -> unknownDetail(context, R.string.network_scan_ssl_splitting)
        }
    }

    private fun buildDnsSpoofingDetail(
        context: Context,
        isConnectedToWifi: Boolean,
        dnsServers: List<String>
    ): NetworkScanDetail {
        if (!isConnectedToWifi) {
            return warningDetail(
                context,
                R.string.network_scan_dns_spoofing,
                context.getString(R.string.network_scan_not_connected)
            )
        }
        if (dnsServers.isEmpty()) {
            return unknownDetail(context, R.string.network_scan_dns_spoofing)
        }

        val hasSuspiciousDns = dnsServers.any { server ->
            server == "0.0.0.0" || server.startsWith("127.") || server.startsWith("224.")
        }

        return if (hasSuspiciousDns) {
            warningDetail(
                context,
                R.string.network_scan_dns_spoofing,
                context.getString(R.string.network_scan_dns_risk_detected)
            )
        } else {
            safeDetail(
                context,
                R.string.network_scan_dns_spoofing,
                context.resources.getQuantityString(
                    R.plurals.network_scan_dns_servers_configured,
                    dnsServers.size,
                    dnsServers.size
                )
            )
        }
    }

    private fun buildDevicesDetail(
        context: Context,
        isConnectedToWifi: Boolean,
        devices: List<NetworkDeviceInfo>
    ): NetworkScanDetail {
        if (!isConnectedToWifi) {
            return warningDetail(
                context,
                R.string.network_scan_devices,
                context.getString(R.string.network_scan_not_connected)
            )
        }
        return if (devices.isEmpty()) {
            unknownDetail(context, R.string.network_scan_devices)
        } else {
            safeDetail(
                context,
                R.string.network_scan_devices,
                context.resources.getQuantityString(
                    R.plurals.network_scan_devices_detected,
                    devices.size,
                    devices.size
                )
            )
        }
    }

    private suspend fun discoverDevices(
        context: Context,
        localIp: String?,
        gatewayIp: String?,
        arpEntries: List<ArpEntry>
    ): List<NetworkDeviceInfo> {
        val initialIps = arpEntries.map { it.ipAddress }.toMutableSet()
        localIp?.let(initialIps::add)
        gatewayIp?.let(initialIps::add)

        val candidates = buildProbeCandidates(localIp, gatewayIp)
        if (candidates.isEmpty()) {
            return buildDeviceInfoList(
                context = context,
                allIps = initialIps,
                responsiveIps = emptySet(),
                localIp = localIp,
                gatewayIp = gatewayIp,
                arpEntries = arpEntries
            )
        }

        val responsiveIps = mutableSetOf<String>()
        val semaphore = Semaphore(24)

        coroutineScope {
            candidates.map { ip ->
                async {
                    semaphore.withPermit {
                        if (probeHost(ip)) {
                            responsiveIps.add(ip)
                        }
                    }
                }
            }.awaitAll()
        }

        val latestArpEntries = readArpTable()
        val allIps = initialIps + responsiveIps + latestArpEntries.map { it.ipAddress }
        return buildDeviceInfoList(
            context = context,
            allIps = allIps,
            responsiveIps = responsiveIps,
            localIp = localIp,
            gatewayIp = gatewayIp,
            arpEntries = latestArpEntries.ifEmpty { arpEntries }
        )
    }

    private fun buildDeviceInfoList(
        context: Context,
        allIps: Set<String>,
        responsiveIps: Set<String>,
        localIp: String?,
        gatewayIp: String?,
        arpEntries: List<ArpEntry>
    ): List<NetworkDeviceInfo> {
        val arpEntriesByIp = arpEntries.associateBy { it.ipAddress }
        return allIps
            .filter { it.isNotBlank() }
            .distinct()
            .map { ipAddress ->
                val arpEntry = arpEntriesByIp[ipAddress]
                NetworkDeviceInfo(
                    ipAddress = ipAddress,
                    macAddress = arpEntry?.macAddress
                        ?: context.getString(R.string.network_scan_unknown),
                    interfaceName = arpEntry?.device
                        ?: context.getString(R.string.network_scan_unknown),
                    role = when (ipAddress) {
                        gatewayIp -> context.getString(R.string.network_scan_role_gateway)
                        localIp -> context.getString(R.string.network_scan_role_this_device)
                        else -> if (responsiveIps.contains(ipAddress)) {
                            context.getString(R.string.network_scan_role_active_device)
                        } else {
                            context.getString(R.string.network_scan_role_cached_device)
                        }
                    },
                    isReachable = responsiveIps.contains(ipAddress) || ipAddress == localIp || ipAddress == gatewayIp
                )
            }
            .sortedWith(
                compareBy<NetworkDeviceInfo> {
                    when (it.ipAddress) {
                        gatewayIp -> 0
                        localIp -> 1
                        else -> if (it.isReachable) 2 else 3
                    }
                }.thenBy { it.ipAddress }
            )
    }

    private fun buildProbeCandidates(localIp: String?, gatewayIp: String?): List<String> {
        val baseIp = localIp ?: return emptyList()
        val lastDotIndex = baseIp.lastIndexOf('.')
        if (lastDotIndex <= 0) return emptyList()

        val prefix = baseIp.substring(0, lastDotIndex)
        val localHost = baseIp.substring(lastDotIndex + 1).toIntOrNull() ?: return emptyList()
        val gatewayHost = gatewayIp?.substringAfterLast('.')?.toIntOrNull()

        val hostOrder = mutableListOf<Int>()
        gatewayHost?.takeIf { it in 1..254 && it != localHost }?.let(hostOrder::add)

        for (offset in 1..127) {
            val lower = localHost - offset
            val upper = localHost + offset
            if (lower in 1..254 && lower != gatewayHost) hostOrder.add(lower)
            if (upper in 1..254 && upper != gatewayHost) hostOrder.add(upper)
            if (hostOrder.size >= MAX_HOSTS_TO_PROBE) break
        }

        return hostOrder
            .distinct()
            .take(MAX_HOSTS_TO_PROBE)
            .map { "$prefix.$it" }
    }

    private fun probeHost(ipAddress: String): Boolean {
        val inetAddress = runCatching { InetAddress.getByName(ipAddress) }.getOrNull() ?: return false

        runCatching {
            if (inetAddress.isReachable(PROBE_TIMEOUT_MS)) {
                return true
            }
        }

        return runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ipAddress, PROBE_PORT), PROBE_TIMEOUT_MS)
                true
            }
        }.recoverCatching { error ->
            error is ConnectException
        }.getOrDefault(false)
    }

    private fun performHttpCheck(useHttps: Boolean): ProbeStatus {
        val url = if (useHttps) {
            "https://connectivitycheck.gstatic.com/generate_204"
        } else {
            "http://connectivitycheck.gstatic.com/generate_204"
        }

        return runCatching {
            val connection = URL(url).openConnection()
            when (connection) {
                is HttpsURLConnection -> {
                    connection.instanceFollowRedirects = false
                    connection.connectTimeout = HTTPS_TIMEOUT_MS
                    connection.readTimeout = HTTPS_TIMEOUT_MS
                    connection.useCaches = false
                    connection.requestMethod = "GET"
                    connection.connect()
                    if (connection.responseCode in listOf(200, 204)) ProbeStatus.SAFE else ProbeStatus.WARNING
                }
                is HttpURLConnection -> {
                    connection.instanceFollowRedirects = false
                    connection.connectTimeout = HTTPS_TIMEOUT_MS
                    connection.readTimeout = HTTPS_TIMEOUT_MS
                    connection.useCaches = false
                    connection.requestMethod = "GET"
                    connection.connect()
                    when {
                        connection.responseCode == 204 -> ProbeStatus.SAFE
                        connection.responseCode in 300..399 || connection.responseCode == 200 -> ProbeStatus.WARNING
                        else -> ProbeStatus.UNKNOWN
                    }
                }
                else -> ProbeStatus.UNKNOWN
            }
        }.recoverCatching { throwable ->
            if (throwable is SSLException) {
                ProbeStatus.WARNING
            } else {
                ProbeStatus.UNKNOWN
            }
        }.getOrDefault(ProbeStatus.UNKNOWN)
    }

    private fun readArpTable(): List<ArpEntry> {
        val arpFile = File("/proc/net/arp")
        if (!arpFile.exists()) return emptyList()

        return runCatching {
            arpFile.useLines { lines ->
                lines.drop(1)
                    .mapNotNull { line ->
                        val parts = line.trim().split(Regex("\\s+"))
                        if (parts.size < 6) return@mapNotNull null
                        val ipAddress = parts[0]
                        val macAddress = parts[3]
                        val device = parts[5]
                        if (!macAddress.matches(Regex("..:..:..:..:..:.."))) {
                            return@mapNotNull null
                        }
                        ArpEntry(ipAddress = ipAddress, macAddress = macAddress, device = device)
                    }
                    .toList()
            }
        }.getOrDefault(emptyList())
    }

    private fun resolveGatewayIp(linkProperties: LinkProperties?): String? {
        return linkProperties?.routes
            ?.firstOrNull { it.isDefaultRoute && it.gateway is Inet4Address }
            ?.gateway
            ?.hostAddress
    }

    private fun resolveLocalIpv4(linkProperties: LinkProperties?): String? {
        return linkProperties?.linkAddresses
            ?.firstOrNull { it.address is Inet4Address && !it.address.isLoopbackAddress }
            ?.address
            ?.hostAddress
    }

    @SuppressLint("MissingPermission")
    private fun resolveSecurityLabel(context: Context, wifiManager: WifiManager, currentSsid: String): String {
        if (NetworkScanFormatter.isUnknownSsid(currentSsid) || !hasWifiScanPermission(context)) {
            return "Unknown"
        }
        val matchedScan = runCatching {
            wifiManager.scanResults.firstOrNull {
                NetworkScanFormatter.cleanSsid(it.SSID) == currentSsid
            }
        }.getOrNull()
        return NetworkScanFormatter.resolveSecurityLabel(matchedScan?.capabilities)
    }

    private fun hasWifiScanPermission(context: Context): Boolean {
        val fineLocationGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val nearbyWifiGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.NEARBY_WIFI_DEVICES
            ) == PackageManager.PERMISSION_GRANTED
        return fineLocationGranted && nearbyWifiGranted
    }

    private fun safeDetail(context: Context, titleResId: Int, value: String): NetworkScanDetail {
        return NetworkScanDetail(
            title = context.getString(titleResId),
            value = value,
            statusIconResId = R.mipmap.ic_check
        )
    }

    private fun warningDetail(context: Context, titleResId: Int, value: String): NetworkScanDetail {
        return NetworkScanDetail(
            title = context.getString(titleResId),
            value = value,
            statusIconResId = R.mipmap.ic_network_scan_unknown
        )
    }

    private fun unknownDetail(context: Context, titleResId: Int): NetworkScanDetail {
        return NetworkScanDetail(
            title = context.getString(titleResId),
            value = context.getString(R.string.network_scan_unknown),
            statusIconResId = R.mipmap.ic_network_scan_unknown
        )
    }

    private data class ArpEntry(
        val ipAddress: String,
        val macAddress: String,
        val device: String
    )

    private enum class ProbeStatus {
        SAFE,
        WARNING,
        UNKNOWN
    }
}
