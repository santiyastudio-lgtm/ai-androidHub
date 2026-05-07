package com.santiya.localaihub.worker

import android.content.Context
import com.santiya.localaihub.global.HardwareScanner
import java.io.File
import kotlin.math.roundToInt

data class GgufProbeVerdict(
    val canProbeNow: Boolean,
    val message: String? = null,
)

object GgufProbeGuard {
    private const val BALANCED_RAM_RATIO = 0.60
    private const val MIN_AVAILABLE_HEADROOM_RATIO = 1.10

    fun evaluate(context: Context, file: File): GgufProbeVerdict {
        if (!file.exists() || !file.isFile) {
            return GgufProbeVerdict(false, "Файл модели не найден.")
        }
        return evaluateForSize(context, (file.length() / (1024L * 1024L)).toInt().coerceAtLeast(1))
    }

    fun evaluateForSize(context: Context, modelSizeMb: Int): GgufProbeVerdict {
        val profile = HardwareScanner.scan(context)
        val safeBudgetMb = (profile.totalRamMB * BALANCED_RAM_RATIO).roundToInt()
        val availableNeedMb = (modelSizeMb * MIN_AVAILABLE_HEADROOM_RATIO).roundToInt()

        if (modelSizeMb > safeBudgetMb || profile.availableRamMB < availableNeedMb) {
            return GgufProbeVerdict(
                canProbeNow = false,
                message = buildString {
                    append("Файл GGUF прочитан, но пробный локальный запуск пропущен: модели нужно слишком много памяти для этого устройства.")
                    append("\n\n")
                    append("RAM устройства: ${profile.totalRamMB} MB")
                    append("\n")
                    append("Свободно сейчас: ${profile.availableRamMB} MB")
                    append("\n")
                    append("Размер модели: ${modelSizeMb} MB")
                    append("\n")
                    append("Безопасный бюджет под пробную загрузку: ${safeBudgetMb} MB")
                    append("\n\n")
                    append("Модель можно импортировать в хаб без потери данных. Для реального запуска нужен более лёгкий квант или устройство с большим запасом RAM.")
                }
            )
        }

        return GgufProbeVerdict(canProbeNow = true)
    }
}
