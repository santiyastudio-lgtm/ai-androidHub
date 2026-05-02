package com.santiya.localaihub.distributed

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

class SequentialOffloadPlanner {

    fun plan(
        model: GgufModelProfile,
        node: NodeResourceSnapshot,
        requestedContextLength: Int = model.contextLength.coerceAtMost(8192)
    ): SequentialOffloadPlan {
        val kvCompression = chooseCompression(node)
        val kvBytesPerToken = when (kvCompression) {
            KvCacheCompressionMode.NONE -> model.estimatedHiddenStateBytesPerToken
            KvCacheCompressionMode.Q8 -> model.hiddenSize
            KvCacheCompressionMode.Q6 -> (model.hiddenSize * 0.75).toInt()
            KvCacheCompressionMode.Q4 -> (model.hiddenSize * 0.5).toInt()
        }
        val estimatedKvCacheMb = ((requestedContextLength.toLong() * kvBytesPerToken) / (1024.0 * 1024.0)).toInt()
        val reservedSystemMb = max(768, (node.totalRamMb * 0.20).toInt())
        val availableForResidentLayersMb = (node.freeRamMb - reservedSystemMb - estimatedKvCacheMb).coerceAtLeast(256)
        val residentLayerWindow = floor(availableForResidentLayersMb / model.estimatedLayerFootprintMb)
            .toInt()
            .coerceIn(1, model.layerCount)
        val phases = ceil(model.layerCount / residentLayerWindow.toDouble()).toInt()

        val windows = (0 until phases).map { index ->
            val start = index * residentLayerWindow
            val end = (start + residentLayerWindow).coerceAtMost(model.layerCount)
            SequentialOffloadWindow(
                phaseIndex = index,
                layerRange = GgufLayerRange(start, end)
            )
        }

        val estimatedPeakRamMb = (reservedSystemMb + estimatedKvCacheMb + residentLayerWindow * model.estimatedLayerFootprintMb)
            .toInt()
        val flashTrafficMbPerToken = if (phases <= 1) {
            0.0
        } else {
            (model.modelSizeMb.toDouble() / phases).coerceAtLeast(model.estimatedLayerFootprintMb)
        }

        return SequentialOffloadPlan(
            enabled = phases > 1,
            residentLayerWindow = residentLayerWindow,
            estimatedPeakRamMb = estimatedPeakRamMb,
            estimatedKvCacheMb = estimatedKvCacheMb,
            estimatedFlashTrafficMbPerToken = flashTrafficMbPerToken,
            kvCacheCompression = kvCompression,
            windows = windows,
            notes = buildList {
                if (phases > 1) {
                    add("Model exceeds resident memory budget and should be paged layer-window by layer-window.")
                } else {
                    add("Model can stay resident locally without sequential layer paging.")
                }
                add("This plan still requires JNI support for per-range GGUF load/unload.")
            }
        )
    }

    private fun chooseCompression(node: NodeResourceSnapshot): KvCacheCompressionMode {
        return when {
            node.freeRamMb < 4096 -> KvCacheCompressionMode.Q4
            node.freeRamMb < 6144 -> KvCacheCompressionMode.Q6
            node.freeRamMb < 8192 -> KvCacheCompressionMode.Q8
            else -> KvCacheCompressionMode.NONE
        }
    }
}
