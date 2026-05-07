package com.santiya.localaihub.ui.screen.offlinecity

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.santiya.localaihub.global.AppLanguage
import com.santiya.localaihub.global.AppLanguageManager
import com.santiya.localaihub.global.Standards
import com.santiya.localaihub.global.formatBytes
import com.santiya.localaihub.global.localizedText
import com.santiya.localaihub.offlinecity.OfflineCityCatalogEntry
import com.santiya.localaihub.offlinecity.OfflineCityDataset
import com.santiya.localaihub.ui.components.ActionButton
import com.santiya.localaihub.ui.components.StandardCard
import com.santiya.localaihub.ui.icons.TnIcons
import com.santiya.localaihub.viewmodel.OfflineCityImportState
import com.santiya.localaihub.viewmodel.OfflineCityViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun OfflineCityScreen(
    onNavigateBack: () -> Unit,
    viewModel: OfflineCityViewModel = viewModel(),
) {
    val context = LocalContext.current
    val language = remember { AppLanguageManager.readPersistedLanguage(context) }
    val dataset = viewModel.dataset.collectAsStateWithLifecycle().value
    val query = viewModel.query.collectAsStateWithLifecycle().value
    val answer = viewModel.answer.collectAsStateWithLifecycle().value
    val location = viewModel.location.collectAsStateWithLifecycle().value
    val importState = viewModel.importState.collectAsStateWithLifecycle().value
    val catalogQuery = viewModel.catalogQuery.collectAsStateWithLifecycle().value
    val catalogResults = viewModel.catalogResults.collectAsStateWithLifecycle().value

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importDataset(uri)
    }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { viewModel.refreshLocation() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(localizedText(language, "Офлайн-город", "Offline City Agent")) },
                navigationIcon = {
                    ActionButton(
                        onClickListener = onNavigateBack,
                        icon = TnIcons.ArrowLeft,
                        contentDescription = localizedText(language, "Назад", "Back"),
                    )
                },
                actions = {
                    ActionButton(
                        onClickListener = { importLauncher.launch(arrayOf("*/*")) },
                        icon = TnIcons.FileUpload,
                        contentDescription = localizedText(language, "Импорт файла", "Import file"),
                    )
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(Standards.SpacingMd),
            verticalArrangement = Arrangement.spacedBy(Standards.SpacingSm),
        ) {
            item {
                StandardCard(
                    title = localizedText(language, "Офлайн-карта и городской агент", "Offline map and city agent"),
                    description = localizedText(
                        language,
                        "Можно импортировать city-pack.json, GeoJSON или GTFS zip, либо выбрать готовый пакет по названию города. Ответы работают офлайн и могут открыть карту, остановки, места и примерный путь.",
                        "You can import a city-pack.json, GeoJSON, or GTFS zip, or choose a ready-made package by city name. Answers work offline and can show a map, stops, places, and an approximate path."
                    ),
                    icon = TnIcons.World,
                )
            }

            importStateCard(language, importState, viewModel::dismissImportMessage)

            item {
                CityCatalogCard(
                    language = language,
                    query = catalogQuery,
                    results = catalogResults,
                    onQueryChange = viewModel::updateCatalogQuery,
                    onInstallClick = viewModel::installCatalogEntry,
                )
            }

            item {
                DatasetSummaryCard(
                    language = language,
                    dataset = dataset,
                    onImportClick = { importLauncher.launch(arrayOf("*/*")) },
                    onClearClick = viewModel::clearImportedDataset,
                )
            }

            item {
                StandardCard(
                    title = localizedText(language, "Геопозиция", "Location"),
                    description = localizedText(
                        language,
                        "Нужна для запросов вроде «где я», «как пройти» и «что рядом».",
                        "Used for questions like \"where am I\", \"how do I get there\", and \"what is nearby\"."
                    ),
                    icon = TnIcons.Gauge,
                ) {
                    Text(
                        text = location?.let {
                            localizedText(
                                language,
                                "Широта ${"%.5f".format(it.latitude)}, долгота ${"%.5f".format(it.longitude)}",
                                "Lat ${"%.5f".format(it.latitude)}, lon ${"%.5f".format(it.longitude)}"
                            )
                        } ?: localizedText(language, "Геопозиция пока недоступна", "Location is not available yet"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = false,
                            onClick = {
                                if (viewModel.canUseLocation()) {
                                    viewModel.refreshLocation()
                                } else {
                                    locationPermissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION,
                                        )
                                    )
                                }
                            },
                            label = { Text(localizedText(language, "Обновить геопозицию", "Refresh location")) },
                            leadingIcon = { androidx.compose.material3.Icon(TnIcons.Refresh, null) },
                        )
                        FilterChip(
                            selected = false,
                            onClick = {
                                locationPermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION,
                                    )
                                )
                            },
                            label = { Text(localizedText(language, "Разрешение", "Permission")) },
                            leadingIcon = { androidx.compose.material3.Icon(TnIcons.ShieldLock, null) },
                        )
                    }
                }
            }

            item {
                StandardCard(
                    title = localizedText(language, "Быстрые запросы", "Quick prompts"),
                    description = localizedText(
                        language,
                        "Работает на локальном городском пакете. В ответ можно получить не только текст, но и карту с точкой, остановками или примерным маршрутом.",
                        "Runs on the local city package. Answers can include not only text but also a map with a point, stops, or an approximate route."
                    ),
                    icon = TnIcons.BrainCircuit,
                ) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        quickPrompts(language).forEach { prompt ->
                            AssistChip(
                                onClick = {
                                    viewModel.updateQuery(prompt)
                                    viewModel.ask(prompt)
                                },
                                label = { Text(prompt) },
                            )
                        }
                    }
                }
            }

            item {
                StandardCard(
                    title = localizedText(language, "Спросить офлайн-агента", "Ask the offline agent"),
                    description = localizedText(
                        language,
                        "Например: где я, где поесть, как пройти до музея, расписание автобусов рядом с центром.",
                        "For example: where am I, where to eat, how to get to the museum, or bus schedules near the center."
                    ),
                    icon = TnIcons.MessageCircle,
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = viewModel::updateQuery,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(localizedText(language, "Ваш вопрос", "Your question")) },
                        minLines = 3,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = false,
                            onClick = { viewModel.ask() },
                            label = { Text(localizedText(language, "Ответить офлайн", "Answer offline")) },
                            leadingIcon = { androidx.compose.material3.Icon(TnIcons.Send, null) },
                        )
                        FilterChip(
                            selected = false,
                            onClick = {
                                val prompt = localizedText(language, "Ближайшая остановка", "Nearest stop")
                                viewModel.updateQuery(prompt)
                                viewModel.ask(prompt)
                            },
                            label = { Text(localizedText(language, "Ближайшая остановка", "Nearest stop")) },
                            leadingIcon = { androidx.compose.material3.Icon(TnIcons.CalendarTime, null) },
                        )
                    }
                }
            }

            answer?.let { cityAnswer ->
                item {
                    OfflineCityAnswerCard(
                        answer = cityAnswer,
                        onSuggestionClick = { suggestion ->
                            viewModel.updateQuery(suggestion)
                            viewModel.ask(suggestion)
                        }
                    )
                }
            }
        }
    }
}

private fun LazyListScope.importStateCard(
    language: AppLanguage,
    state: OfflineCityImportState,
    onDismiss: () -> Unit,
) {
    when (state) {
        OfflineCityImportState.Idle -> Unit
        OfflineCityImportState.Importing -> item {
            StandardCard(
                title = localizedText(language, "Импортирую данные города", "Importing city data"),
                description = localizedText(language, "Читаю городской пакет и обновляю офлайн-индекс.", "Reading the city package and refreshing the offline index."),
                icon = TnIcons.Database,
            )
        }
        is OfflineCityImportState.Error -> item {
            StandardCard(
                title = localizedText(language, "Ошибка импорта", "Import error"),
                description = state.message,
                icon = TnIcons.AlertTriangle,
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                onClick = onDismiss,
            )
        }
        is OfflineCityImportState.Success -> item {
            StandardCard(
                title = localizedText(language, "Городской пакет готов", "City pack ready"),
                description = state.message,
                icon = TnIcons.CircleCheck,
                containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f),
                onClick = onDismiss,
            )
        }
    }
}

@Composable
private fun CityCatalogCard(
    language: AppLanguage,
    query: String,
    results: List<OfflineCityCatalogEntry>,
    onQueryChange: (String) -> Unit,
    onInstallClick: (OfflineCityCatalogEntry) -> Unit,
) {
    StandardCard(
        title = localizedText(language, "Загрузить карту города", "Download city map"),
        description = localizedText(
            language,
            "Введите город и выберите готовый офлайн-пакет. Перед установкой виден размер и покрытие данных.",
            "Type a city and choose a ready-made offline pack. Size and data coverage are shown before installation."
        ),
        icon = TnIcons.Download,
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(localizedText(language, "Город", "City")) },
            supportingText = {
                Text(
                    localizedText(
                        language,
                        "Пока доступен встроенный каталог готовых тестовых пакетов. Внешний OSM build-catalog можно подключить позже.",
                        "A built-in catalog of ready-made test packs is available now. An external OSM build catalog can be connected later."
                    )
                )
            }
        )
        Spacer(Modifier.height(8.dp))
        if (results.isEmpty()) {
            Text(
                text = localizedText(language, "Ничего не найдено", "No matching city packs"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        results.forEach { entry ->
            StandardCard(
                title = entry.cityName,
                description = localizedText(
                    language,
                    "${entry.regionCountry} • ${entry.sourceCoverage} • ${formatBytes(entry.packageSizeBytes)}",
                    "${entry.regionCountry} • ${entry.sourceCoverage} • ${formatBytes(entry.packageSizeBytes)}"
                ),
                icon = TnIcons.World,
                onClick = { onInstallClick(entry) },
            ) {
                entry.tileDisplayName?.let { tile ->
                    Text(
                        text = localizedText(language, "Источник карты: $tile", "Map source: $tile"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    text = localizedText(language, "Нажмите, чтобы установить пакет", "Tap to install this pack"),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}

@Composable
private fun DatasetSummaryCard(
    language: AppLanguage,
    dataset: OfflineCityDataset?,
    onImportClick: () -> Unit,
    onClearClick: () -> Unit,
) {
    StandardCard(
        title = localizedText(language, "Текущий пакет города", "Current city pack"),
        description = dataset?.summary()?.let { summary ->
            localizedText(
                language,
                "${summary.name} • мест ${summary.placeCount}, остановок ${summary.stopCount}, маршрутов ${summary.routeCount}",
                "${summary.name} • ${summary.placeCount} places, ${summary.stopCount} stops, ${summary.routeCount} routes"
            )
        } ?: localizedText(language, "Пакет ещё не загружен", "No city pack installed yet"),
        icon = TnIcons.Database,
    ) {
        dataset?.tileSource?.let { tileSource ->
            Text(
                text = localizedText(language, "Источник карты: ${tileSource.displayName}", "Map source: ${tileSource.displayName}"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        dataset?.summary()?.sourceFiles?.takeIf { it.isNotEmpty() }?.let { files ->
            Text(
                text = files.joinToString("\n"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = false,
                onClick = onImportClick,
                label = { Text(localizedText(language, "Импорт файла", "Import file")) },
                leadingIcon = { androidx.compose.material3.Icon(TnIcons.FileUpload, null) },
            )
            if (dataset != null) {
                FilterChip(
                    selected = false,
                    onClick = onClearClick,
                    label = { Text(localizedText(language, "Очистить", "Clear")) },
                    leadingIcon = { androidx.compose.material3.Icon(TnIcons.TrashX, null) },
                )
            }
        }
    }
}

private fun quickPrompts(language: AppLanguage): List<String> {
    return if (language == AppLanguage.RUSSIAN) {
        listOf("Где я", "Где поесть рядом", "Ближайшая остановка", "Расписание автобусов", "Как пройти до музея")
    } else {
        listOf("Where am I", "Where to eat nearby", "Nearest stop", "Bus schedule", "How to get to the museum")
    }
}
