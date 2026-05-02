package com.santiya.localaihub.worker

import com.santiya.localaihub.global.AccelerationMode
import java.io.File

data class DiffusionBackendSelection(
    val runOnCpu: Boolean,
    val useCpuClip: Boolean,
    val effectiveMode: AccelerationMode,
    val hardwareBackendAvailable: Boolean
)

object DiffusionBackendSelector {

    fun resolve(
        mode: AccelerationMode,
        isQualcommDevice: Boolean,
        modelDir: File?
    ): DiffusionBackendSelection {
        val hasQnnBackend = modelDir?.let {
            File(it, "unet.bin").exists() && File(it, "vae_decoder.bin").exists()
        } ?: false
        val hasCpuBackend = modelDir?.let {
            File(it, "unet.mnn").exists() && File(it, "vae_decoder.mnn").exists()
        } ?: false
        val hasNpuClip = modelDir?.let { File(it, "clip.bin").exists() } ?: false
        val hasCpuClip = modelDir?.let { File(it, "clip.mnn").exists() } ?: false

        return resolve(
            mode = mode,
            isQualcommDevice = isQualcommDevice,
            hasQnnBackend = hasQnnBackend,
            hasCpuBackend = hasCpuBackend,
            hasNpuClip = hasNpuClip,
            hasCpuClip = hasCpuClip
        )
    }

    internal fun resolve(
        mode: AccelerationMode,
        isQualcommDevice: Boolean,
        hasQnnBackend: Boolean,
        hasCpuBackend: Boolean,
        hasNpuClip: Boolean,
        hasCpuClip: Boolean
    ): DiffusionBackendSelection {
        val hardwareBackendAvailable = isQualcommDevice && hasQnnBackend

        fun hardwareSelection() = DiffusionBackendSelection(
            runOnCpu = false,
            useCpuClip = !hasNpuClip && hasCpuClip,
            effectiveMode = AccelerationMode.GPU,
            hardwareBackendAvailable = true
        )

        fun cpuSelection() = DiffusionBackendSelection(
            runOnCpu = true,
            useCpuClip = true,
            effectiveMode = AccelerationMode.CPU,
            hardwareBackendAvailable = hardwareBackendAvailable
        )

        return when (mode) {
            AccelerationMode.GPU -> {
                if (hardwareBackendAvailable) hardwareSelection() else cpuSelection()
            }

            AccelerationMode.CPU -> {
                if (hasCpuBackend || !hardwareBackendAvailable) cpuSelection() else hardwareSelection()
            }

            AccelerationMode.AUTO -> {
                if (hardwareBackendAvailable) hardwareSelection() else cpuSelection()
            }
        }
    }
}
