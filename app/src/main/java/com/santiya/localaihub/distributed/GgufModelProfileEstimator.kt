package com.santiya.localaihub.distributed

import com.santiya.localaihub.models.table_schema.Model
import kotlin.math.max

class GgufModelProfileEstimator {

    fun estimate(model: Model): GgufModelProfile {
        val sizeMb = ((model.fileSize ?: 0L) / (1024 * 1024)).toInt().coerceAtLeast(1)
        val scaleB = inferParameterScaleB(model.modelName)
        val layerCount = inferLayerCount(scaleB)
        val hiddenSize = inferHiddenSize(scaleB)
        val heads = inferAttentionHeads(hiddenSize)
        val kvHeads = inferKvHeads(scaleB, heads)
        val layerFootprint = sizeMb.toDouble() / (layerCount + 2)
        val hiddenStateBytesPerToken = hiddenSize * 2
        val notes = buildList {
            add("Profile is heuristic until JNI exposes per-layer GGUF metadata.")
            scaleB?.let { add("Estimated parameter scale: ${"%.1f".format(it)}B") }
        }

        return GgufModelProfile(
            modelId = model.id,
            modelName = model.modelName,
            architecture = inferArchitecture(model.modelName),
            estimatedParameterScaleB = scaleB,
            layerCount = layerCount,
            hiddenSize = hiddenSize,
            attentionHeads = heads,
            kvHeads = kvHeads,
            contextLength = inferContextLength(model.modelName),
            modelSizeMb = sizeMb,
            estimatedLayerFootprintMb = layerFootprint,
            estimatedHiddenStateBytesPerToken = hiddenStateBytesPerToken,
            heuristicProfile = true,
            notes = notes
        )
    }

    private fun inferArchitecture(modelName: String): String {
        val lower = modelName.lowercase()
        return when {
            "qwen" in lower -> "qwen"
            "llama" in lower -> "llama"
            "mistral" in lower -> "mistral"
            "gemma" in lower -> "gemma"
            else -> "gguf"
        }
    }

    private fun inferParameterScaleB(modelName: String): Double? {
        val match = Regex("""(\d+(?:\.\d+)?)\s*([bm])""", RegexOption.IGNORE_CASE).find(modelName)
            ?: return null
        val value = match.groupValues[1].toDoubleOrNull() ?: return null
        return when (match.groupValues[2].lowercase()) {
            "b" -> value
            "m" -> value / 1000.0
            else -> null
        }
    }

    private fun inferLayerCount(scaleB: Double?): Int {
        if (scaleB == null) return 32
        return when {
            scaleB <= 1.0 -> 16
            scaleB <= 2.0 -> 24
            scaleB <= 4.0 -> 28
            scaleB <= 9.0 -> 32
            scaleB <= 14.0 -> 40
            scaleB <= 35.0 -> 48
            scaleB <= 72.0 -> 80
            else -> 120
        }
    }

    private fun inferHiddenSize(scaleB: Double?): Int {
        if (scaleB == null) return 4096
        return when {
            scaleB <= 1.0 -> 2048
            scaleB <= 2.0 -> 2048
            scaleB <= 4.0 -> 2560
            scaleB <= 9.0 -> 4096
            scaleB <= 14.0 -> 5120
            scaleB <= 35.0 -> 6656
            scaleB <= 72.0 -> 8192
            else -> 12288
        }
    }

    private fun inferAttentionHeads(hiddenSize: Int): Int {
        return max(8, hiddenSize / 128)
    }

    private fun inferKvHeads(scaleB: Double?, attentionHeads: Int): Int {
        if (scaleB == null) return max(8, attentionHeads / 4)
        return when {
            scaleB <= 4.0 -> max(8, attentionHeads / 2)
            scaleB <= 14.0 -> max(8, attentionHeads / 4)
            else -> max(8, attentionHeads / 8)
        }
    }

    private fun inferContextLength(modelName: String): Int {
        val lower = modelName.lowercase()
        return when {
            "128k" in lower -> 131072
            "64k" in lower -> 65536
            "32k" in lower -> 32768
            "16k" in lower -> 16384
            else -> 8192
        }
    }
}
