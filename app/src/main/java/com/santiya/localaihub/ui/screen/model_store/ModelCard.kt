package com.santiya.localaihub.ui.screen.model_store

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.santiya.localaihub.global.Standards
import com.santiya.localaihub.global.formatBytes
import com.santiya.localaihub.hub.Downloadability
import com.santiya.localaihub.hub.ModelCatalogPresentation
import com.santiya.localaihub.hub.ModelSupportStatus
import com.santiya.localaihub.models.data.HuggingFaceModel
import com.santiya.localaihub.models.data.ModelType
import com.santiya.localaihub.service.ModelDownloadService
import com.santiya.localaihub.ui.components.ActionButton
import com.santiya.localaihub.ui.components.ActionProgressButton
import com.santiya.localaihub.ui.icons.TnIcons
import com.santiya.localaihub.ui.theme.Motion

@Composable
private fun SupportBadge(status: ModelSupportStatus) {
    val (label, color) = when (status) {
        ModelSupportStatus.LOCAL -> "Локально" to MaterialTheme.colorScheme.primary
        ModelSupportStatus.LAN -> "Через LAN" to MaterialTheme.colorScheme.secondary
        ModelSupportStatus.EXPERIMENTAL -> "Экспериментально" to MaterialTheme.colorScheme.tertiary
        ModelSupportStatus.CATALOG_ONLY -> "Только каталог" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    StoreInfoChip(text = label, accent = color)
}

@Composable
fun ModelCard(
    model: HuggingFaceModel,
    isInstalled: Boolean,
    downloadState: ModelDownloadService.DownloadState?,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit
) {
    val presentation = remember(model) { ModelCatalogPresentation.presentationFor(model) }
    val previewCandidates = remember(presentation) {
        buildList {
            presentation.thumbnailUrl?.takeIf { it.isNotBlank() }?.let(::add)
            addAll(presentation.previewImages.filter { it.isNotBlank() })
        }.distinct()
    }

    var expanded by rememberSaveable(model.id) { mutableStateOf(false) }
    var previewIndex by remember(model.id) { mutableIntStateOf(0) }
    var previewFailed by remember(model.id) { mutableStateOf(false) }

    val isDownloading = downloadState is ModelDownloadService.DownloadState.Downloading
    val isExtracting = downloadState is ModelDownloadService.DownloadState.Extracting
    val isProcessing = downloadState is ModelDownloadService.DownloadState.Processing
    val canStartDownload = presentation.downloadability == Downloadability.LOCAL_RUNNABLE ||
        presentation.downloadability == Downloadability.RAW_ASSET_DOWNLOAD

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable { expanded = !expanded },
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(Standards.CardPadding),
            verticalArrangement = Arrangement.spacedBy(Standards.SpacingSm)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.88f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.44f))
                    .clickable { expanded = !expanded },
                contentAlignment = Alignment.Center
            ) {
                val previewUrl = previewCandidates.getOrNull(previewIndex)
                if (!previewFailed && !previewUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = previewUrl,
                        contentDescription = presentation.titleRu,
                        modifier = Modifier.fillMaxWidth(),
                        contentScale = ContentScale.Crop,
                        onError = {
                            if (previewIndex < previewCandidates.lastIndex) {
                                previewIndex += 1
                            } else {
                                previewFailed = true
                            }
                        }
                    )
                } else {
                    PreviewFallback(modelType = model.modelType)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StoreInfoChip(presentation.taskLabelRu, MaterialTheme.colorScheme.primary)
                StoreInfoChip(presentation.sourceLabel, MaterialTheme.colorScheme.secondary)
                SupportBadge(presentation.supportStatus)
                when (presentation.downloadability) {
                    Downloadability.LOCAL_RUNNABLE ->
                        StoreInfoChip("Можно скачать", MaterialTheme.colorScheme.primary)

                    Downloadability.RAW_ASSET_DOWNLOAD ->
                        StoreInfoChip("Скачать как asset", MaterialTheme.colorScheme.tertiary)

                    Downloadability.TOKEN_REQUIRED ->
                        StoreInfoChip("Нужен токен", MaterialTheme.colorScheme.error)

                    Downloadability.UNRESOLVED ->
                        StoreInfoChip("Ссылка не найдена", MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { expanded = !expanded },
                    verticalArrangement = Arrangement.spacedBy(Standards.SpacingXs)
                ) {
                    Text(
                        text = presentation.titleRu,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = if (expanded) 4 else 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = presentation.descriptionRu,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (expanded) 8 else 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                when {
                    isInstalled -> {
                        Icon(
                            imageVector = TnIcons.CircleCheck,
                            contentDescription = "Установлено",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    isDownloading || isExtracting || isProcessing -> {
                        ActionProgressButton(
                            onClickListener = onCancelDownload,
                            icon = TnIcons.PlayerStop,
                            contentDescription = "Отменить"
                        )
                    }

                    canStartDownload -> {
                        ActionButton(
                            onClickListener = onDownload,
                            icon = TnIcons.Download,
                            contentDescription = "Скачать модель"
                        )
                    }

                    else -> {
                        Icon(
                            imageVector = if (presentation.downloadability == Downloadability.TOKEN_REQUIRED) {
                                TnIcons.AlertTriangle
                            } else {
                                TnIcons.Download
                            },
                            contentDescription = "Скачивание недоступно",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StoreInfoChip(model.approximateSize, MaterialTheme.colorScheme.primary)
                presentation.ramEstimateMb?.let {
                    StoreInfoChip("${it} MB RAM", MaterialTheme.colorScheme.secondary)
                }
                presentation.tagsRu.take(if (expanded) 6 else 3).forEach { tag ->
                    StoreInfoChip(tag, MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            presentation.warnings.firstOrNull()?.let { warning ->
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = when (presentation.downloadability) {
                        Downloadability.LOCAL_RUNNABLE -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f)
                        Downloadability.RAW_ASSET_DOWNLOAD,
                        Downloadability.TOKEN_REQUIRED,
                        Downloadability.UNRESOLVED -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.62f)
                    }
                ) {
                    Text(
                        text = warning.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = when (presentation.downloadability) {
                            Downloadability.LOCAL_RUNNABLE -> MaterialTheme.colorScheme.onSurfaceVariant
                            Downloadability.RAW_ASSET_DOWNLOAD,
                            Downloadability.TOKEN_REQUIRED,
                            Downloadability.UNRESOLVED -> MaterialTheme.colorScheme.onTertiaryContainer
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = Motion.Enter,
                exit = Motion.Exit
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(Standards.SpacingSm)) {
                    ModelDetailRow("Файл", model.resolvedFileName ?: "Будет выбран при загрузке")
                    ModelDetailRow("Источник", model.pageUrl ?: presentation.sourceLabel)
                    ModelDetailRow(
                        "Запуск",
                        when (presentation.supportStatus) {
                            ModelSupportStatus.LOCAL -> "Локально на устройстве"
                            ModelSupportStatus.LAN -> "Через LAN-узел"
                            ModelSupportStatus.EXPERIMENTAL -> "Экспериментальный запуск или raw asset"
                            ModelSupportStatus.CATALOG_ONLY -> "Только каталог или asset"
                        }
                    )
                }
            }

            AnimatedVisibility(
                visible = isDownloading || isExtracting || isProcessing,
                enter = Motion.Enter,
                exit = Motion.Exit
            ) {
                Column(modifier = Modifier.padding(top = Standards.SpacingXs)) {
                    val progress = if (downloadState is ModelDownloadService.DownloadState.Downloading) {
                        downloadState.progress
                    } else {
                        0f
                    }

                    val statusText = when {
                        isProcessing -> "Подготовка модели..."
                        isExtracting -> {
                            val state = downloadState as ModelDownloadService.DownloadState.Extracting
                            if (state.currentFile.isNotEmpty()) {
                                "Распаковка ${state.currentFile} (${state.extractedCount + 1}/${state.totalFiles})"
                            } else {
                                "Распаковка..."
                            }
                        }

                        isDownloading -> {
                            val state = downloadState as ModelDownloadService.DownloadState.Downloading
                            "${formatBytes(state.downloadedBytes)} / ${formatBytes(state.totalBytes)} (${(progress * 100).toInt()}%)"
                        }

                        else -> ""
                    }

                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )

                    if (downloadState is ModelDownloadService.DownloadState.Downloading) {
                        val speedText = buildString {
                            append(formatDownloadSpeed(downloadState.speedBytesPerSec))
                            val etaText = formatEta(downloadState.etaSeconds)
                            if (etaText != null) {
                                append(" • ")
                                append(etaText)
                            }
                        }
                        Text(
                            text = speedText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(Standards.SpacingXs))

                    LinearProgressIndicator(
                        progress = {
                            when (downloadState) {
                                is ModelDownloadService.DownloadState.Downloading -> downloadState.progress
                                is ModelDownloadService.DownloadState.Extracting -> {
                                    if (downloadState.totalFiles > 0) {
                                        downloadState.extractedCount.toFloat() / downloadState.totalFiles
                                    } else {
                                        0f
                                    }
                                }

                                else -> 0f
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(Standards.RadiusFull)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewFallback(modelType: ModelType) {
    Icon(
        imageVector = when (modelType) {
            ModelType.GGUF -> TnIcons.Messages
            ModelType.SD -> TnIcons.Photo
            ModelType.TTS -> TnIcons.Volume
        },
        contentDescription = null,
        modifier = Modifier.size(44.dp),
        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.82f)
    )
}

@Composable
private fun ModelDetailRow(
    title: String,
    value: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun StoreInfoChip(
    text: String,
    accent: Color
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = accent,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
        modifier = Modifier
            .clip(RoundedCornerShape(Standards.RadiusFull))
            .background(accent.copy(alpha = 0.11f))
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .basicMarquee()
    )
}

private fun formatDownloadSpeed(speedBytesPerSec: Long): String {
    if (speedBytesPerSec <= 0L) return "Скорость считается..."
    return "${formatBytes(speedBytesPerSec)}/s"
}

private fun formatEta(etaSeconds: Long): String? {
    if (etaSeconds < 0L) return null
    val minutes = etaSeconds / 60
    val seconds = etaSeconds % 60
    return if (minutes > 0) {
        "Осталось ${minutes}м ${seconds}с"
    } else {
        "Осталось ${seconds}с"
    }
}
