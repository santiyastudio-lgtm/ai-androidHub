package com.santiya.localaihub.distributed

import android.annotation.SuppressLint
import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo

@SuppressLint("MissingPermission")
class NsdLanAdvertiser(
    context: Context
) {

    companion object {
        const val DEFAULT_PORT = 17888
    }

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var registeredListener: NsdManager.RegistrationListener? = null
    private var registeredName: String? = null

    fun start(snapshot: NodeResourceSnapshot, port: Int = DEFAULT_PORT) {
        if (registeredListener != null && registeredName == snapshot.nodeId) return
        stop()

        val serviceInfo = NsdServiceInfo().apply {
            serviceName = snapshot.nodeId
            serviceType = NsdLanDiscoveryService.SERVICE_TYPE
            setPort(port)
            setAttribute("httpPort", port.toString())
            setAttribute("pairingRequired", "true")
            setAttribute("totalRamMb", snapshot.totalRamMb.toString())
            setAttribute("freeRamMb", snapshot.freeRamMb.toString())
            setAttribute("cpuCores", snapshot.cpuCores.toString())
            setAttribute("cpuArch", snapshot.cpuArch)
            setAttribute("accelerator", snapshot.acceleratorSummary)
            setAttribute("acceleratorScore", snapshot.acceleratorScore.toString())
            setAttribute("computeScore", snapshot.computeScore.toString())
            setAttribute("storageMb", snapshot.availableStorageMb.toString())
            setAttribute("sequentialOffload", snapshot.supportsSequentialOffload.toString())
            setAttribute("pipelineWorker", snapshot.supportsPipelineWorker.toString())
            setAttribute("heavySlotAvailable", snapshot.heavySlotAvailable.toString())
            setAttribute("protocols", snapshot.transportProtocols.joinToString(",") { it.name.lowercase() })
        }

        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {
                registeredName = info.serviceName
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
        }

        registeredListener = listener
        nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    fun stop() {
        val listener = registeredListener ?: return
        runCatching { nsdManager.unregisterService(listener) }
        registeredListener = null
        registeredName = null
    }
}
