package com.santiya.localaihub.hub

import com.santiya.localaihub.models.data.HuggingFaceModel
import com.santiya.localaihub.models.data.ModelType

object ModelCatalogPresentation {

    fun presentationFor(model: HuggingFaceModel): CatalogPresentationEntry {
        val repo = model.repositoryUrl.ifBlank { model.fileUri.substringBefore("/resolve/main/") }
        val taskLabel = when {
            model.tags.any { it.contains("tts", ignoreCase = true) } -> "Голос и озвучка"
            model.tags.any { it.contains("vision", ignoreCase = true) } -> "Зрение и анализ"
            model.tags.any { it.contains("video", ignoreCase = true) } -> "Генерация видео"
            model.tags.any { it.contains("3d", ignoreCase = true) } -> "3D и сцены"
            model.tags.any { it.contains("tool", ignoreCase = true) } -> "Чат и инструменты"
            model.modelType == ModelType.SD -> "Генерация изображений"
            model.modelType == ModelType.GGUF -> "Локальный чат"
            else -> "Локальная модель"
        }

        val titleRu = when {
            !model.displayNameRu.isNullOrBlank() -> model.displayNameRu
            model.modelType == ModelType.TTS -> "Голосовая модель ${model.name}"
            model.modelType == ModelType.SD -> "Модель изображений ${model.name}"
            else -> model.name
        } ?: model.name

        val descriptionRu = when {
            !model.descriptionRu.isNullOrBlank() -> model.descriptionRu
            model.modelType == ModelType.GGUF ->
                "Локальная языковая модель для чата, инструкций и инструментов. Работает на устройстве без облака."
            model.modelType == ModelType.SD ->
                "Локальная модель генерации изображений. Подходит для арта, референсов и визуальных прототипов прямо на устройстве."
            model.modelType == ModelType.TTS ->
                "Локальная голосовая модель для озвучки ответов и live-ассистента."
            else -> model.description
        }

        return CatalogPresentationEntry(
            id = model.id,
            titleRu = titleRu,
            descriptionRu = descriptionRu,
            taskLabelRu = taskLabel,
            thumbnailUrl = model.thumbnailUrl ?: repo.takeIf { it.isNotBlank() }?.let {
                "https://huggingface.co/$it/resolve/main/thumbnail.png"
            },
            previewImages = model.previewImages,
            ramEstimateMb = model.ramEstimateMb ?: estimateRam(model),
            supportStatus = when {
                model.experimental -> ModelSupportStatus.EXPERIMENTAL
                model.supportStatus != null -> model.supportStatus
                model.tags.any { it.contains("video", ignoreCase = true) || it.contains("3d", ignoreCase = true) } ->
                    ModelSupportStatus.CATALOG_ONLY
                else -> ModelSupportStatus.LOCAL
            },
            downloadability = model.downloadability,
            warnings = model.warnings,
            sourceLabel = model.sourceLabel,
            assistantEligible = model.assistantEligible
                ?: (model.modelType == ModelType.GGUF || model.modelType == ModelType.TTS),
            liveEligible = model.liveEligible
                ?: model.tags.any { it.contains("vision", ignoreCase = true) || it.contains("camera", ignoreCase = true) },
            tagsRu = buildRussianTags(model, taskLabel)
        )
    }

    private fun estimateRam(model: HuggingFaceModel): Int? {
        val number = Regex("(\\d+)").find(model.approximateSize)?.groupValues?.getOrNull(1)?.toIntOrNull()
        return when {
            number == null -> null
            model.approximateSize.contains("GB", ignoreCase = true) -> number * 2048
            else -> number + 1024
        }
    }

    private fun buildRussianTags(model: HuggingFaceModel, taskLabel: String): List<String> {
        val translated = model.tags.mapNotNull { tag ->
            when {
                tag.equals("GGUF", ignoreCase = true) -> "GGUF"
                tag.equals("CPU", ignoreCase = true) -> "CPU"
                tag.equals("NPU", ignoreCase = true) -> "NPU"
                tag.equals("Multilingual", ignoreCase = true) -> "Мультиязычная"
                tag.equals("Tool Calling", ignoreCase = true) -> "Инструменты"
                tag.equals("vision", ignoreCase = true) -> "Камера"
                tag.equals("NSFW", ignoreCase = true) -> "18+"
                else -> null
            }
        }
        val sourceTag = when (model.source.lowercase()) {
            "civitai" -> "Civitai"
            "modelscope" -> "ModelScope"
            "github" -> "GitHub"
            else -> "Hugging Face"
        }
        return (listOf(taskLabel, sourceTag) + translated + model.familyTags).distinct()
    }
}
