package com.santiya.localaihub.worker

import android.content.Context
import android.net.Uri
import com.santiya.localaihub.models.enums.PathType
import com.santiya.localaihub.models.enums.ProviderType
import java.io.File
import java.util.Locale

enum class ImportedModelKind {
    GGUF,
    ONNX,
    DIFFUSION_PACKAGE,
    TTS_SUPERTONIC,
    TTS_PIPER,
    RAW_ASSET
}

data class ImportAnalysisResult(
    val kind: ImportedModelKind,
    val providerType: ProviderType,
    val pathType: PathType,
    val runnableNow: Boolean,
    val reason: String? = null
)

class ModelImportAnalyzer {

    fun analyzeUri(context: Context, uri: Uri, suggestedProviderType: ProviderType? = null): ImportAnalysisResult {
        val name = runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && index >= 0) cursor.getString(index) else null
            }
        }.getOrNull().orEmpty()
        return analyzeName(name, suggestedProviderType)
    }

    fun analyzePath(file: File, suggestedProviderType: ProviderType? = null): ImportAnalysisResult {
        if (file.isDirectory) {
            val names = file.list()?.map { it.lowercase(Locale.US) }.orEmpty()
            if (names.contains("tokenizer.json") && names.any { it == "unet.bin" || it == "unet.mnn" }) {
                return ImportAnalysisResult(
                    kind = ImportedModelKind.DIFFUSION_PACKAGE,
                    providerType = ProviderType.DIFFUSION,
                    pathType = PathType.DIRECTORY,
                    runnableNow = true
                )
            }
            if (names.any { it.endsWith(".onnx") }) {
                return ImportAnalysisResult(
                    kind = if (names.any { it.contains("piper") || it == "model.onnx" } && names.any { it.endsWith(".json") }) {
                        ImportedModelKind.TTS_PIPER
                    } else {
                        ImportedModelKind.ONNX
                    },
                    providerType = if (names.any { it.contains("piper") || it == "model.onnx" } && names.any { it.endsWith(".json") }) {
                        ProviderType.TTS_PIPER
                    } else {
                        ProviderType.ONNX
                    },
                    pathType = PathType.DIRECTORY,
                    runnableNow = names.any { it.contains("ultraface") || it.contains("yolo") || it.contains("retinaface") || it.contains("arcface") || it.contains("mobilefacenet") },
                    reason = if (names.any { it.contains("ultraface") || it.contains("yolo") || it.contains("retinaface") || it.contains("arcface") || it.contains("mobilefacenet") }) null
                    else "Архив или папка с ONNX-файлами сохранится как импортированная модель, но локальный runtime для этого набора не подтверждён."
                )
            }
            return ImportAnalysisResult(
                kind = ImportedModelKind.RAW_ASSET,
                providerType = ProviderType.RAW_ASSET,
                pathType = PathType.DIRECTORY,
                runnableNow = false,
                reason = "Папка импортирована как raw asset. Локальный запуск этой структуры не подтверждён."
            )
        }
        return analyzeName(file.name, suggestedProviderType)
    }

    private fun analyzeName(name: String, suggestedProviderType: ProviderType? = null): ImportAnalysisResult {
        val normalized = name.lowercase(Locale.US)
        val explicit = suggestedProviderType
        return when {
            explicit == ProviderType.DIFFUSION -> ImportAnalysisResult(
                kind = ImportedModelKind.DIFFUSION_PACKAGE,
                providerType = ProviderType.DIFFUSION,
                pathType = PathType.FILE,
                runnableNow = normalized.endsWith(".zip"),
                reason = if (normalized.endsWith(".zip")) null else "Для diffusion-модели ожидается zip-архив или готовая папка с весами."
            )
            explicit == ProviderType.TTS_PIPER || normalized.contains("piper") -> ImportAnalysisResult(
                kind = ImportedModelKind.TTS_PIPER,
                providerType = ProviderType.TTS_PIPER,
                pathType = PathType.FILE,
                runnableNow = normalized.endsWith(".zip"),
                reason = if (normalized.endsWith(".zip")) null else "Piper voice pack лучше импортировать zip-архивом или готовой папкой."
            )
            normalized.endsWith(".gguf") -> ImportAnalysisResult(
                kind = ImportedModelKind.GGUF,
                providerType = ProviderType.GGUF,
                pathType = PathType.FILE,
                runnableNow = true
            )
            normalized.endsWith(".onnx") -> ImportAnalysisResult(
                kind = ImportedModelKind.ONNX,
                providerType = ProviderType.ONNX,
                pathType = PathType.FILE,
                runnableNow = normalized.contains("ultraface") ||
                    normalized.contains("yolo") ||
                    normalized.contains("retinaface") ||
                    normalized.contains("arcface") ||
                    normalized.contains("mobilefacenet"),
                reason = "ONNX-файл будет импортирован. Для запуска нужны совместимый adapter и поддержанная архитектура."
            )
            normalized.endsWith(".zip") && (normalized.contains("sd") || normalized.contains("diffusion") || normalized.contains("stable")) -> ImportAnalysisResult(
                kind = ImportedModelKind.DIFFUSION_PACKAGE,
                providerType = ProviderType.DIFFUSION,
                pathType = PathType.FILE,
                runnableNow = true
            )
            normalized.endsWith(".zip") -> ImportAnalysisResult(
                kind = ImportedModelKind.RAW_ASSET,
                providerType = ProviderType.RAW_ASSET,
                pathType = PathType.FILE,
                runnableNow = false,
                reason = "Архив будет сохранён как raw asset. Высокий шанс, что он не запустится на этом устройстве без дополнительного runtime."
            )
            else -> ImportAnalysisResult(
                kind = ImportedModelKind.RAW_ASSET,
                providerType = ProviderType.RAW_ASSET,
                pathType = PathType.FILE,
                runnableNow = false,
                reason = "Формат не распознан как готовая локально запускаемая модель. Он будет сохранён как raw asset."
            )
        }
    }
}
