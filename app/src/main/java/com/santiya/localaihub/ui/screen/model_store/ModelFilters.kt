package com.santiya.localaihub.ui.screen.model_store

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.santiya.localaihub.catalog.CatalogSource
import com.santiya.localaihub.global.Standards
import com.santiya.localaihub.hub.ModelSupportStatus
import com.santiya.localaihub.ui.components.ActionSwitch
import com.santiya.localaihub.viewmodel.ModelStoreViewModel
import com.santiya.localaihub.viewmodel.SortOption

@Composable
fun SearchAppBar(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onCloseSearch: () -> Unit,
) = Unit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelDiscoveryBottomSheet(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onClearSearch: () -> Unit,
    viewModel: ModelStoreViewModel,
) {
    val sortBy by viewModel.sortBy.collectAsStateWithLifecycle()
    val selectedSources by viewModel.selectedSources.collectAsStateWithLifecycle()
    val selectedCapabilities by viewModel.selectedCapabilities.collectAsStateWithLifecycle()
    val selectedSupportStatuses by viewModel.selectedSupportStatuses.collectAsStateWithLifecycle()
    val selectedFamilyTags by viewModel.selectedFamilyTags.collectAsStateWithLifecycle()
    val showNsfw by viewModel.showNsfw.collectAsStateWithLifecycle()
    val executionTarget by viewModel.executionTarget.collectAsStateWithLifecycle()
    val models by viewModel.models.collectAsStateWithLifecycle()
    val availableCapabilities = remember(models) { viewModel.getAvailableCapabilities() }
    val availableFamilyTags = remember(models) { viewModel.getAvailableFamilyTags() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Standards.SpacingLg, vertical = Standards.SpacingSm),
            verticalArrangement = Arrangement.spacedBy(Standards.SpacingMd)
        ) {
            Text(
                text = "Поиск и фильтры",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                label = { Text("Название, описание, семейство") }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Сбросить все фильтры",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = onClearSearch) {
                    Text("Сбросить")
                }
            }

            FilterSection(
                title = "Источники",
                chips = CatalogSource.entries.map { source ->
                    FilterChipSpec(
                        label = source.displayName,
                        selected = source in selectedSources,
                        onClick = { viewModel.toggleSourceFilter(source) }
                    )
                }
            )

            if (availableCapabilities.isNotEmpty()) {
                FilterSection(
                    title = "Задачи",
                    chips = availableCapabilities.map { capability ->
                        FilterChipSpec(
                            label = capabilityLabel(capability),
                            selected = capability in selectedCapabilities,
                            onClick = { viewModel.toggleCapabilityFilter(capability) }
                        )
                    }
                )
            }

            FilterSection(
                title = "Статус",
                chips = ModelSupportStatus.entries.map { status ->
                    FilterChipSpec(
                        label = when (status) {
                            ModelSupportStatus.LOCAL -> "Локально"
                            ModelSupportStatus.LAN -> "LAN"
                            ModelSupportStatus.EXPERIMENTAL -> "Экспериментально"
                            ModelSupportStatus.CATALOG_ONLY -> "Raw asset"
                        },
                        selected = status in selectedSupportStatuses,
                        onClick = { viewModel.toggleSupportStatusFilter(status) }
                    )
                }
            )

            FilterSection(
                title = "Запуск",
                chips = listOf("CPU", "NPU", "LAN").map { target ->
                    FilterChipSpec(
                        label = target,
                        selected = executionTarget == target,
                        onClick = { viewModel.setExecutionTarget(if (executionTarget == target) null else target) }
                    )
                }
            )

            if (availableFamilyTags.isNotEmpty()) {
                FilterSection(
                    title = "Семейства",
                    chips = availableFamilyTags.map { tag ->
                        FilterChipSpec(
                            label = tag,
                            selected = tag in selectedFamilyTags,
                            onClick = { viewModel.toggleFamilyTagFilter(tag) }
                        )
                    }
                )
            }

            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Показывать NSFW",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Отключайте, если нужен более чистый каталог.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    ActionSwitch(
                        checked = showNsfw,
                        onCheckedChange = { viewModel.setShowNsfw(it) }
                    )
                }
            }

            FilterSection(
                title = "Сортировка",
                chips = listOf(
                    FilterChipSpec(
                        label = "Название",
                        selected = sortBy == SortOption.NAME,
                        onClick = { viewModel.setSortOption(SortOption.NAME) }
                    ),
                    FilterChipSpec(
                        label = "Размер",
                        selected = sortBy == SortOption.SIZE,
                        onClick = { viewModel.setSortOption(SortOption.SIZE) }
                    ),
                    FilterChipSpec(
                        label = "Новые",
                        selected = sortBy == SortOption.RECENTLY_ADDED,
                        onClick = { viewModel.setSortOption(SortOption.RECENTLY_ADDED) }
                    )
                )
            )
        }
    }
}

@Composable
fun ModelFiltersSection(viewModel: ModelStoreViewModel) = Unit

private data class FilterChipSpec(
    val label: String,
    val selected: Boolean,
    val onClick: () -> Unit,
)

@Composable
private fun FilterSection(
    title: String,
    chips: List<FilterChipSpec>,
) {
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.26f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Standards.SpacingSm)
            ) {
                chips.forEach { chip ->
                    StoreFilterChip(chip = chip)
                }
            }
        }
    }
}

@Composable
private fun StoreFilterChip(
    chip: FilterChipSpec
) {
    val accent = if (chip.selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        onClick = chip.onClick,
        shape = RoundedCornerShape(999.dp),
        color = if (chip.selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        }
    ) {
        Text(
            text = chip.label,
            style = MaterialTheme.typography.labelMedium,
            color = if (chip.selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                accent
            },
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            modifier = Modifier
                .background(androidx.compose.ui.graphics.Color.Transparent)
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .basicMarquee()
        )
    }
}

private fun capabilityLabel(capability: String): String = when (capability) {
    "chat" -> "Чат"
    "object_detection" -> "Объекты"
    "face_detection" -> "Лица"
    "face_recognition" -> "Распознавание лиц"
    "image_generation" -> "Изображения"
    "video_generation" -> "Видео"
    "tts" -> "Озвучка"
    "files" -> "Файлы"
    "assistant_live" -> "Live"
    else -> capability.replace('_', ' ')
}
