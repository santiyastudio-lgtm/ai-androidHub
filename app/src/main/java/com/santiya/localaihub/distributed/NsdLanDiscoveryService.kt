package com.santiya.localaihub.distributed

import android.annotation.SuppressLint
import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import java.util.concurrent.ConcurrentHashMap

@SuppressLint("MissingPermission")
class NsdLanDiscoveryService(
    context: Context
) : PeerDiscoveryService {

    companion object {
        const val SERVICE_TYPE = "_santiyalocalaihub._tcp."
    }

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val discovered = ConcurrentHashMap<String, NodeResourceSnapshot>()
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    override fun start() {
        if (discoveryListener != null) return
        val listener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                discovered.remove(serviceInfo.serviceName)
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (serviceInfo.serviceType != SERVICE_TYPE) return
                resolve(serviceInfo)
            }
        }
        discoveryListener = listener
        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    override fun stop() {
        val listener = discoveryListener ?: return
        runCatching { nsdManager.stopServiceDiscovery(listener) }
        discoveryListener = null
        discovered.clear()
    }

    override fun snapshot(): List<NodeResourceSnapshot> =
        discovered.values.sortedBy { it.displayName }

    private fun resolve(serviceInfo: NsdServiceInfo) {
        nsdManager.resolveService(
            serviceInfo,
            object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit

                override fun onServiceResolved(resolved: NsdServiceInfo) {
                    val hostAddress = resolved.host?.hostAddress ?: "unknown"
                    val attributes = resolved.attributes
                    discovered[resolved.serviceName] = NodeResourceSnapshot(
                        nodeId = resolved.serviceName,
                        displayName = resolved.serviceName,
                        platform = DistributedNodePlatform.ANDROID,
                        totalRamMb = attributes["totalRamMb"]?.decodeInt() ?: 0,
                        freeRamMb = attributes["freeRamMb"]?.decodeInt() ?: 0,
                        cpuCores = attributes["cpuCores"]?.decodeInt() ?: 0,
                        cpuArch = attributes["cpuArch"]?.decodeString() ?: "unknown",
                        acceleratorSummary = attributes["accelerator"]?.decodeString() ?: "unknown",
                        acceleratorScore = attributes["acceleratorScore"]?.decodeDouble() ?: 0.0,
                        computeScore = attributes["computeScore"]?.decodeDouble() ?: 0.0,
                        availableStorageMb = attributes["storageMb"]?.decodeInt() ?: 0,
                        supportsSequentialOffload = attributes["sequentialOffload"]?.decodeBoolean() ?: false,
                        supportsPipelineWorker = attributes["pipelineWorker"]?.decodeBoolean() ?: false,
                        heavySlotAvailable = attributes["heavySlotAvailable"]?.decodeBoolean() ?: false,
                        transportProtocols = decodeProtocols(attributes),
                        lastSeenEpochMs = System.currentTimeMillis(),
                        notes = listOf("Resolved over NSD from $hostAddress:${resolved.port}")
                    )
                }
            }
        )
    }

    private fun decodeProtocols(attributes: Map<String, ByteArray>): List<DistributedTransportProtocol> {
        val raw = attributes["protocols"]?.decodeString().orEmpty()
        val parsed = raw.split(",").mapNotNull { token ->
            when (token.trim().lowercase()) {
                "nsd_http" -> DistributedTransportProtocol.NSD_HTTP
                "nsd_libp2p" -> DistributedTransportProtocol.NSD_LIBP2P
                "grpc" -> DistributedTransportProtocol.GRPC
                "libp2p" -> DistributedTransportProtocol.LIBP2P
                else -> null
            }
        }
        return if (parsed.isEmpty()) listOf(DistributedTransportProtocol.NSD_HTTP) else parsed
    }

    private fun ByteArray.decodeString(): String = toString(Charsets.UTF_8)
    private fun ByteArray.decodeInt(): Int = decodeString().toIntOrNull() ?: 0
    private fun ByteArray.decodeDouble(): Double = decodeString().toDoubleOrNull() ?: 0.0
    private fun ByteArray.decodeBoolean(): Boolean = decodeString().equals("true", ignoreCase = true)
}
