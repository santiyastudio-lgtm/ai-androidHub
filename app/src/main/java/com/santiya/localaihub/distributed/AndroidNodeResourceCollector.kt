package com.santiya.localaihub.distributed

import android.content.Context
import android.os.Build
import com.santiya.localaihub.global.HardwareScanner
import kotlin.math.roundToInt

class AndroidNodeResourceCollector(private val context: Context) {

    fun snapshot(nodeId: String = defaultNodeId()): NodeResourceSnapshot {
        val hardware = HardwareScanner.scan(context)
        val acceleratorSummary = inferAcceleratorSummary()
        val acceleratorScore = inferAcceleratorScore(acceleratorSummary)
        val computeScore = (
            hardware.availableRamMB / 1024.0 +
                hardware.cpuCores * 0.75 +
                acceleratorScore * 1.25
            )
        val storageMb = (context.filesDir.usableSpace / (1024L * 1024L)).toInt()

        return NodeResourceSnapshot(
            nodeId = nodeId,
            displayName = hardware.deviceModel,
            platform = DistributedNodePlatform.ANDROID,
            totalRamMb = hardware.totalRamMB,
            freeRamMb = hardware.availableRamMB,
            cpuCores = hardware.cpuCores,
            cpuArch = hardware.cpuArch,
            acceleratorSummary = acceleratorSummary,
            acceleratorScore = acceleratorScore,
            computeScore = (computeScore * 100.0).roundToInt() / 100.0,
            availableStorageMb = storageMb,
            supportsSequentialOffload = true,
            supportsPipelineWorker = !hardware.isLowRamDevice,
            heavySlotAvailable = hardware.availableRamMB >= 2048,
            transportProtocols = listOf(
                DistributedTransportProtocol.NSD_LIBP2P,
                DistributedTransportProtocol.NSD_HTTP
            ),
            lastSeenEpochMs = System.currentTimeMillis(),
            notes = buildList {
                if (hardware.isLowRamDevice) add("Low RAM device: pipeline worker mode should be conservative.")
                add("Node snapshot is local-only until NSD/libp2p discovery is wired to a live transport.")
            }
        )
    }

    private fun defaultNodeId(): String {
        val model = Build.MODEL.lowercase().replace(" ", "-")
        return "android-$model"
    }

    private fun inferAcceleratorSummary(): String {
        val hardware = "${Build.HARDWARE} ${Build.BOARD} ${Build.PRODUCT}".lowercase()
        return when {
            "adreno" in hardware || "snapdragon" in hardware || "qcom" in hardware -> "adreno/qualcomm"
            "mali" in hardware -> "mali"
            "tensor" in hardware -> "tensor"
            "mediatek" in hardware || "mt" in hardware -> "mediatek"
            else -> "unknown"
        }
    }

    private fun inferAcceleratorScore(summary: String): Double {
        return when {
            "adreno" in summary || "qualcomm" in summary -> 3.5
            "tensor" in summary -> 3.0
            "mali" in summary -> 2.5
            "mediatek" in summary -> 2.0
            else -> 1.0
        }
    }
}
