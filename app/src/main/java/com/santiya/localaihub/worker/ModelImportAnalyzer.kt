package com.santiya.localaihub.worker

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
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
    val detectedFormat: String,
    val compatibility: ImportCompatibility,
    val reason: String? = null,
    val conversionSuggestion: String? = null,
)

class ModelImportAnalyzer {

    fun analyzeUri(context: Context, uri: Uri, suggestedProviderType: ProviderType? = null): ImportAnalysisResult {
        val name = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && index >= 0) cursor.getString(index) else null
            }
        }.getOrNull().orEmpty()
        detectBinaryKind(context, uri)?.let { detected ->
            return detected
        }
        return analyzeName(name, suggestedProviderType)
    }

    fun analyzePath(file: File, suggestedProviderType: ProviderType? = null): ImportAnalysisResult {
        if (file.isDirectory) {
            val names = file.list()?.map { it.lowercase(Locale.US) }.orEmpty()
            val hasPiperShape = names.any { it.endsWith(".onnx") } && names.any { it.endsWith(".json") }
            val hasDiffusionShape = names.contains("tokenizer.json") && names.any { it == "unet.bin" || it == "unet.mnn" }
            return when {
                hasDiffusionShape -> ImportAnalysisResult(
                    kind = ImportedModelKind.DIFFUSION_PACKAGE,
                    providerType = ProviderType.DIFFUSION,
                    pathType = PathType.DIRECTORY,
                    runnableNow = true,
                    detectedFormat = "diffusion_directory",
                    compatibility = ImportCompatibility.RUNNABLE,
                )

                hasPiperShape -> ImportAnalysisResult(
                    kind = ImportedModelKind.TTS_PIPER,
                    providerType = ProviderType.TTS_PIPER,
                    pathType = PathType.DIRECTORY,
                    runnableNow = true,
                    detectedFormat = "piper_directory",
                    compatibility = ImportCompatibility.RUNNABLE,
                )

                names.any { it.endsWith(".onnx") } -> ImportAnalysisResult(
                    kind = ImportedModelKind.ONNX,
                    providerType = ProviderType.ONNX,
                    pathType = PathType.DIRECTORY,
                    runnableNow = false,
                    detectedFormat = "onnx_directory",
                    compatibility = ImportCompatibility.IMPORTABLE_WITH_LIMITS,
                    reason = "Папку с ONNX-файлами можно сохранить в хабе, но generic ONNX LLM не запускается в текущем Android runtime.",
                    conversionSuggestion = "Если это чат-модель, конвертируйте её на компьютере в GGUF. Если это voice pack, добавьте полный Piper layout с .onnx и .json."
                )

                else -> ImportAnalysisResult(
                    kind = ImportedModelKind.RAW_ASSET,
                    providerType = ProviderType.RAW_ASSET,
                    pathType = PathType.DIRECTORY,
                    runnableNow = false,
                    detectedFormat = "directory",
                    compatibility = ImportCompatibility.UNSUPPORTED,
                    reason = "Структура папки не распознана как поддерживаемый runtime package.",
                    conversionSuggestion = "Подготовьте city-pack, GGUF, diffusion package или Piper voice pack в поддерживаемой структуре."
                )
            }
        }
        detectBinaryKind(file)?.let { detected ->
            return detected
        }
        return analyzeName(file.name, suggestedProviderType)
    }

    private fun detectBinaryKind(context: Context, uri: Uri): ImportAnalysisResult? {
        return runCatching {
            ContentUriIO.openInputStream(context, uri).use { input ->
                val header = ByteArray(4)
                val read = input.read(header)
                if (read == 4 && header.decodeToString() == "GGUF") {
                    runnableGguf()
                } else {
                    null
                }
            }
        }.getOrNull()
    }

    private fun detectBinaryKind(file: File): ImportAnalysisResult? {
        return runCatching {
            file.inputStream().use { input ->
                val header = ByteArray(4)
                val read = input.read(header)
                if (read == 4 && header.decodeToString() == "GGUF") {
                    runnableGguf()
                } else {
                    null
                }
            }
        }.getOrNull()
    }

    private fun runnableGguf(): ImportAnalysisResult =
        ImportAnalysisResult(
            kind = ImportedModelKind.GGUF,
            providerType = ProviderType.GGUF,
            pathType = PathType.FILE,
            runnableNow = true,
            detectedFormat = "gguf",
            compatibility = ImportCompatibility.RUNNABLE,
        )

    private fun analyzeName(name: String, suggestedProviderType: ProviderType? = null): ImportAnalysisResult {
        val normalized = name.lowercase(Locale.US)
        return when {
            normalized.endsWith(".gguf") -> ImportAnalysisResult(
                kind = ImportedModelKind.GGUF,
                providerType = ProviderType.GGUF,
                pathType = PathType.FILE,
                runnableNow = true,
                detectedFormat = "gguf",
                compatibility = ImportCompatibility.RUNNABLE,
            )

            normalized.endsWith(".litertlm") || normalized.endsWith(".task") -> ImportAnalysisResult(
                kind = ImportedModelKind.RAW_ASSET,
                providerType = ProviderType.RAW_ASSET,
                pathType = PathType.FILE,
                runnableNow = false,
                detectedFormat = if (normalized.endsWith(".task")) "task" else "litertlm",
                compatibility = ImportCompatibility.IMPORTABLE_WITH_LIMITS,
                reason = "Файл относится к Google Local runtime, а не к обычному GGUF chat path.",
                conversionSuggestion = "Импортируйте его как Google Local asset или используйте встроенный Google Local backend."
            )

            suggestedProviderType == ProviderType.DIFFUSION ||
                (normalized.endsWith(".zip") && (normalized.contains("sd") || normalized.contains("diffusion") || normalized.contains("stable"))) -> ImportAnalysisResult(
                kind = ImportedModelKind.DIFFUSION_PACKAGE,
                providerType = ProviderType.DIFFUSION,
                pathType = PathType.FILE,
                runnableNow = true,
                detectedFormat = "diffusion_zip",
                compatibility = ImportCompatibility.RUNNABLE,
            )

            suggestedProviderType == ProviderType.TTS_PIPER || normalized.contains("piper") -> ImportAnalysisResult(
                kind = ImportedModelKind.TTS_PIPER,
                providerType = ProviderType.TTS_PIPER,
                pathType = PathType.FILE,
                runnableNow = normalized.endsWith(".zip"),
                detectedFormat = if (normalized.endsWith(".zip")) "piper_zip" else "piper_asset",
                compatibility = if (normalized.endsWith(".zip")) ImportCompatibility.RUNNABLE else ImportCompatibility.NEEDS_CONVERSION,
                reason = if (normalized.endsWith(".zip")) null else "Piper voice pack лучше импортировать zip-архивом или готовой папкой с .onnx и .json.",
                conversionSuggestion = if (normalized.endsWith(".zip")) null else "Соберите voice pack в структуру Piper: model.onnx + config.json, затем импортируйте папку или zip."
            )

            normalized.endsWith(".onnx") -> ImportAnalysisResult(
                kind = ImportedModelKind.ONNX,
                providerType = ProviderType.ONNX,
                pathType = PathType.FILE,
                runnableNow = false,
                detectedFormat = "onnx",
                compatibility = ImportCompatibility.NEEDS_CONVERSION,
                reason = "Generic ONNX chat model не поддерживается текущим Android runtime как локальная LLM.",
                conversionSuggestion = "Если это LLM-веса, конвертируйте их на компьютере в GGUF. Если это vision/voice model, используйте совместимый adapter path."
            )

            normalized.endsWith(".safetensors") || normalized.endsWith(".bin") -> ImportAnalysisResult(
                kind = ImportedModelKind.RAW_ASSET,
                providerType = ProviderType.RAW_ASSET,
                pathType = PathType.FILE,
                runnableNow = false,
                detectedFormat = if (normalized.endsWith(".safetensors")) "safetensors" else "bin",
                compatibility = ImportCompatibility.NEEDS_CONVERSION,
                reason = "Transformer weights из Hugging Face не запускаются напрямую в мобильном GGUF runtime.",
                conversionSuggestion = "Конвертируйте модель на компьютере в GGUF и импортируйте готовый .gguf-файл."
            )

            normalized.endsWith(".zip") -> ImportAnalysisResult(
                kind = ImportedModelKind.RAW_ASSET,
                providerType = ProviderType.RAW_ASSET,
                pathType = PathType.FILE,
                runnableNow = false,
                detectedFormat = "zip",
                compatibility = ImportCompatibility.IMPORTABLE_WITH_LIMITS,
                reason = "Архив можно сохранить в хабе, но в нём не найдена поддерживаемая runtime signature.",
                conversionSuggestion = "Подготовьте diffusion package, Piper zip или распакуйте архив и проверьте структуру вручную."
            )

            else -> ImportAnalysisResult(
                kind = ImportedModelKind.RAW_ASSET,
                providerType = ProviderType.RAW_ASSET,
                pathType = PathType.FILE,
                runnableNow = false,
                detectedFormat = normalized.substringAfterLast('.', "unknown"),
                compatibility = ImportCompatibility.UNSUPPORTED,
                reason = "Формат не распознан как локально запускаемая модель для этого runtime.",
                conversionSuggestion = "Используйте GGUF для локального чата, Piper для TTS, diffusion package для изображений или city-pack/GeoJSON/GTFS для офлайн-карт."
            )
        }
    }
}
