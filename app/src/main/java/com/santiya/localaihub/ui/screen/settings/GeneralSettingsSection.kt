package com.santiya.localaihub.ui.screen.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.santiya.localaihub.global.AccelerationMode
import com.santiya.localaihub.global.HardwareProfile
import com.santiya.localaihub.global.PerformanceMode
import com.santiya.localaihub.global.Standards
import com.santiya.localaihub.hub.ExternalAccessPolicy
import com.santiya.localaihub.hub.LanHubConfig
import com.santiya.localaihub.hub.LanNodeInfo
import com.santiya.localaihub.hub.OrchestraCapabilityState
import com.santiya.localaihub.hub.OrchestraConfig
import com.santiya.localaihub.hub.PreferredModelMap
import com.santiya.localaihub.hub.ThemePreset
import com.santiya.localaihub.models.enums.ProviderType
import com.santiya.localaihub.models.table_schema.Model
import com.santiya.localaihub.service.ModelDownloadService
import com.santiya.localaihub.ui.components.ActionTextButton
import com.santiya.localaihub.ui.components.ActionToggleGroup
import com.santiya.localaihub.ui.components.BodyLabel
import com.santiya.localaihub.ui.components.SectionDivider
import com.santiya.localaihub.ui.components.SectionHeader
import com.santiya.localaihub.ui.components.StandardCard
import com.santiya.localaihub.ui.components.SwitchRow
import com.santiya.localaihub.ui.icons.TnIcons
import com.santiya.localaihub.viewmodel.SettingsViewModel
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

// РІвЂќР‚РІвЂќР‚ General Settings Section РІвЂќР‚РІвЂќР‚

internal fun LazyListScope.generalSettingsSection(
    toolCallingEnabled: Boolean,
    toolCallingBypassEnabled: Boolean,
    hasToolCallingModel: Boolean,
    toolCallingDownloadState: ModelDownloadService.DownloadState?,
    viewModel: SettingsViewModel
) {
    // ==================== General ====================
    item { SectionHeader(title = "Общие") }

    item {
        val canEnableToolCalling = hasToolCallingModel || toolCallingBypassEnabled
        SwitchRow(
            title = "Вызов инструментов",
            description = when {
                toolCallingBypassEnabled -> "Обход включён — инструменты доступны для всех моделей"
                hasToolCallingModel -> "Любая модель с шаблоном чата может вызывать инструменты"
                else -> "Установите GGUF-модель, чтобы включить инструменты"
            },
            checked = toolCallingEnabled && canEnableToolCalling,
            onCheckedChange = { viewModel.setToolCallingEnabled(it) },
            enabled = canEnableToolCalling
        )
    }

    // Download recommended tool calling model card
    if (!hasToolCallingModel) {
        item {
            ModelDownloadCard(
                title = "Рекомендуемая модель для инструментов",
                description = "Ruvltra Claude Code 0.5B • ~400 MB\nКомпактная модель для вызова инструментов",
                downloadState = toolCallingDownloadState,
                onDownload = { viewModel.downloadToolCallingModel() }
            )
        }
    }

    // Bypass tool calling model check РІР‚вЂќ red warning card
    item {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Standards.CardCornerRadius),
            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Standards.SpacingMd),
                verticalArrangement = Arrangement.spacedBy(Standards.SpacingSm)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Standards.SpacingSm)
                ) {
                    Icon(
                        TnIcons.AlertTriangle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Обход проверки модели",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Text(
                    text = "Принудительно включает инструменты для моделей без шаблона чата. Может давать ошибки или странные ответы.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                SwitchRow(
                    title = "Включить обход",
                    description = if (toolCallingBypassEnabled) "Инструменты принудительно включены для всех моделей" else "Инструменты доступны только для моделей с шаблоном чата",
                    checked = toolCallingBypassEnabled,
                    onCheckedChange = { viewModel.setToolCallingBypassEnabled(it) },
                    titleColor = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

// РІвЂќР‚РІвЂќР‚ LLM Settings Section РІвЂќР‚РІвЂќР‚

internal fun LazyListScope.llmSettingsSection(
    streamingEnabled: Boolean,
    chatMemoryEnabled: Boolean,
    askModelReloadDialog: Boolean,
    viewModel: SettingsViewModel
) {
    item { Spacer(Modifier.height(Standards.SpacingSm)) }
    item { SectionDivider() }
    item { SectionHeader(title = "Чат-модель") }

    item {
        SwitchRow(
            title = "Потоковый ответ",
            description = "Показывать токены по мере генерации",
            checked = streamingEnabled,
            onCheckedChange = { viewModel.setStreamingEnabled(it) }
        )
    }

    item {
        SwitchRow(
            title = "Память чата",
            description = "Помнить предыдущие сообщения в диалоге",
            checked = chatMemoryEnabled,
            onCheckedChange = { viewModel.setChatMemoryEnabled(it) }
        )
    }

    item {
        SwitchRow(
            title = "Спрашивать о перезагрузке модели",
            description = "Показывать диалог при запуске. Если выключено, последняя модель загрузится автоматически.",
            checked = askModelReloadDialog,
            onCheckedChange = { viewModel.setAskModelReloadDialog(it) }
        )
    }
}

// РІвЂќР‚РІвЂќР‚ Chat Settings Section РІвЂќР‚РІвЂќР‚

internal fun LazyListScope.chatSettingsSection(
    codeHighlightEnabled: Boolean,
    viewModel: SettingsViewModel
) {
    item { Spacer(Modifier.height(Standards.SpacingSm)) }
    item { SectionDivider() }
    item { SectionHeader(title = "Чат") }

    item {
        SwitchRow(
            title = "Подсветка кода",
            description = "Подсвечивать блоки кода по языку",
            checked = codeHighlightEnabled,
            onCheckedChange = { viewModel.setCodeHighlightEnabled(it) }
        )
    }
}

// РІвЂќР‚РІвЂќР‚ Hardware Tuning Section РІвЂќР‚РІвЂќР‚

internal fun LazyListScope.hardwareTuningSection(
    hardwareTuningEnabled: Boolean,
    performanceMode: PerformanceMode,
    accelerationMode: AccelerationMode,
    hardwareProfile: HardwareProfile?,
    viewModel: SettingsViewModel
) {
    item { Spacer(Modifier.height(Standards.SpacingSm)) }
    item { SectionDivider() }
    item { SectionHeader(title = "Аппаратная настройка") }

    item {
        SwitchRow(
            title = "Автонастройка по железу",
            description = "Автоматически подбирать параметры по возможностям устройства. Выключите, если хотите настраивать вручную.",
            checked = hardwareTuningEnabled,
            onCheckedChange = { viewModel.setHardwareTuningEnabled(it) }
        )
    }

    // РІвЂќР‚РІвЂќР‚ Performance Mode РІвЂќР‚РІвЂќР‚
    item {
        Column {
            Text(
                text = "Режим производительности",
                style = MaterialTheme.typography.titleSmall,
                color = if (hardwareTuningEnabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(Standards.SpacingSm))
            ActionToggleGroup(
                items = PerformanceMode.entries.toList(),
                selectedItem = performanceMode,
                onItemSelected = { viewModel.setPerformanceMode(it) },
                itemLabel = { mode ->
                    when (mode) {
                        PerformanceMode.PERFORMANCE -> "Максимум"
                        PerformanceMode.BALANCED -> "Баланс"
                        PerformanceMode.POWER_SAVING -> "Экономия"
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = hardwareTuningEnabled
            )
            Spacer(Modifier.height(Standards.SpacingXs))
            Text(
                text = if (!hardwareTuningEnabled) {
                    "Включите автонастройку, чтобы использовать пресеты производительности"
                } else when (performanceMode) {
                    PerformanceMode.PERFORMANCE -> "Использует максимум быстрых ядер. Лучше скорость, выше расход батареи."
                    PerformanceMode.BALANCED -> "Использует производительные ядра без перегруза. Хороший баланс."
                    PerformanceMode.POWER_SAVING -> "Минимум потоков и памяти. Лучший режим для экономии батареи."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    item {
        StandardCard(
            title = "Ускорение",
            description = "Выберите, как запускать image- и speech-runtime. GGUF-чаты в этой сборке всё ещё CPU-only."
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Standards.SpacingSm)) {
                ActionToggleGroup(
                    items = AccelerationMode.entries.toList(),
                    selectedItem = accelerationMode,
                    onItemSelected = { viewModel.setAccelerationMode(it) },
                    itemLabel = { mode ->
                        when (mode) {
                            AccelerationMode.AUTO -> "Авто"
                            AccelerationMode.GPU -> "GPU/NPU"
                            AccelerationMode.CPU -> "CPU"
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = when (accelerationMode) {
                        AccelerationMode.AUTO -> "Использовать ускорение, когда его поддерживают устройство и набор модели."
                        AccelerationMode.GPU -> "Предпочитать GPU/NPU для поддерживаемых image- и speech-runtime."
                        AccelerationMode.CPU -> "Принудительно запускать поддерживаемые runtime на CPU."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    hardwareProfile?.let { profile ->
        item {
            val topo = profile.cpuTopology
            val coreInfo = if (topo.scanSucceeded) {
                buildString {
                    if (topo.primeCoreCount > 0) append("${topo.primeCoreCount}P")
                    if (topo.performanceCoreCount > 0) {
                        if (isNotEmpty()) append("+")
                        append("${topo.performanceCoreCount}P")
                    }
                    if (topo.efficiencyCoreCount > 0) {
                        if (isNotEmpty()) append("+")
                        append("${topo.efficiencyCoreCount}E")
                    }
                    append(" cores")
                }
            } else {
                "${profile.cpuCores} cores"
            }

            StandardCard(
                title = "${profile.totalRamMB} MB RAM • $coreInfo • ${profile.cpuArch}",
                description = profile.deviceModel
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    ActionTextButton(
                        onClickListener = { viewModel.rescanHardware() },
                        icon = TnIcons.Refresh,
                        text = "Пересканировать",
                        shape = RoundedCornerShape(Standards.CardSmallCornerRadius)
                    )
                }
            }
        }
    }
}

// РІвЂќР‚РІвЂќР‚ Model Configuration Section РІвЂќР‚РІвЂќР‚

internal fun LazyListScope.modelConfigurationSection(
    hardwareTuningEnabled: Boolean,
    installedModels: List<Model>,
    onModelEditor: () -> Unit
) {
    item { Spacer(Modifier.height(Standards.SpacingSm)) }
    item { SectionDivider() }
    item {
        SectionHeader(title = "Конфигурация модели") {
            ActionTextButton(
                onClickListener = onModelEditor,
                icon = TnIcons.Sparkles,
                text = "Настроить",
                shape = RoundedCornerShape(Standards.CardSmallCornerRadius),
                enabled = !hardwareTuningEnabled
            )
        }
    }

    if (hardwareTuningEnabled) {
        item {
            Text(
                text = "Параметры модели сейчас управляются движком производительности. Отключите аппаратную настройку, чтобы редактировать вручную.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
            )
        }
    }

    if (installedModels.isEmpty()) {
        item {
            StandardCard(
                description = "Модели не установлены. Скачайте их из магазина."
            )
        }
    } else {
        items(installedModels.size, key = { installedModels[it].id }) { index ->
            val model = installedModels[index]
            StandardCard(
                title = model.modelName,
                description = model.providerType.name,
                icon = TnIcons.Sparkles,
                onClick = if (!hardwareTuningEnabled) onModelEditor else ({})
            )
        }
    }
}

// РІвЂќР‚РІвЂќР‚ AI Memory Section РІвЂќР‚РІвЂќР‚

internal fun LazyListScope.aiMemorySection(
    aiMemoryEnabled: Boolean,
    onAiMemoryClick: () -> Unit,
    viewModel: SettingsViewModel
) {
    item { Spacer(Modifier.height(Standards.SpacingSm)) }
    item { SectionDivider() }
    item { SectionHeader(title = "Память AI") }

    item {
        SwitchRow(
            title = "Память AI",
            description = "Запоминать факты о вас между диалогами",
            checked = aiMemoryEnabled,
            onCheckedChange = { viewModel.setAiMemoryEnabled(it) }
        )
    }

    item {
        Surface(
            onClick = onAiMemoryClick,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Standards.RadiusMd),
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(modifier = Modifier.padding(Standards.SpacingLg)) {
                Text(
                    "Открыть память",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "Просматривайте, ищите и очищайте то, что AI запомнил о вас",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// РІвЂќР‚РІвЂќР‚ Image Generation Section РІвЂќР‚РІвЂќР‚

internal fun LazyListScope.imageGenerationSection(
    imageBlurEnabled: Boolean,
    viewModel: SettingsViewModel
) {
    item { Spacer(Modifier.height(Standards.SpacingSm)) }
    item { SectionDivider() }
    item { SectionHeader(title = "Генерация изображений") }

    item {
        SwitchRow(
            title = "Размывать сгенерированные изображения",
            description = "Размывать изображения по умолчанию, нажмите чтобы показать",
            checked = imageBlurEnabled,
            onCheckedChange = { viewModel.setImageBlurEnabled(it) }
        )
    }
}

// РІвЂќР‚РІвЂќР‚ About Section РІвЂќР‚РІвЂќР‚

internal fun LazyListScope.aboutSection(appVersion: String) {
    item { Spacer(Modifier.height(Standards.SpacingSm)) }
    item { SectionDivider() }
    item { SectionHeader(title = "О приложении") }

    item {
        StandardCard(
            title = "SantiyaLocalAiHub",
            description = "Локальный AI: LLM, генерация изображений и озвучка"
        ) {
            BodyLabel(
                text = "Версия $appVersion",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

internal fun LazyListScope.themeSettingsSection(
    themePreset: ThemePreset,
    viewModel: SettingsViewModel
) {
    item { Spacer(Modifier.height(Standards.SpacingSm)) }
    item { SectionDivider() }
    item { SectionHeader(title = "Оформление") }

    item {
        StandardCard(
            title = "Тема приложения",
            description = "Системная, полночная фиолетовая, чёрно-белая или светлая мраморная"
        ) {
            ActionToggleGroup(
                items = ThemePreset.entries.toList(),
                selectedItem = themePreset,
                onItemSelected = { viewModel.setThemePreset(it) },
                itemLabel = {
                    when (it) {
                        ThemePreset.SYSTEM -> "Система"
                        ThemePreset.MIDNIGHT_VIOLET -> "Midnight"
                        ThemePreset.OBSIDIAN_MONO -> "Mono"
                        ThemePreset.MARBLE_LILAC -> "Marble"
                    }
                }
            )
        }
    }
}

private data class PreferenceOption(
    val id: String?,
    val label: String
)

internal fun LazyListScope.preferredModelsSection(
    installedModels: List<Model>,
    preferredModels: PreferredModelMap,
    viewModel: SettingsViewModel
) {
    item { Spacer(Modifier.height(Standards.SpacingSm)) }
    item { SectionDivider() }
    item { SectionHeader(title = "Модели по умолчанию") }

    val noneOption = PreferenceOption(id = null, label = "Не выбрана")
    val chatOptions = listOf(noneOption) + installedModels
        .filter { it.providerType == ProviderType.GGUF || it.providerType == ProviderType.GOOGLE_LOCAL }
        .map { PreferenceOption(it.id, it.modelName) }
    val imageOptions = listOf(noneOption) + installedModels
        .filter { it.providerType == ProviderType.DIFFUSION }
        .map { PreferenceOption(it.id, it.modelName) }
    val ttsOptions = listOf(noneOption) + installedModels
        .filter { it.providerType == ProviderType.TTS || it.providerType == ProviderType.TTS_PIPER }
        .map { PreferenceOption(it.id, it.modelName) }

    item {
        StandardCard(
            title = "Чат по умолчанию",
            description = "Эта модель будет открываться первой для текстового режима."
        ) {
            ActionToggleGroup(
                items = chatOptions,
                selectedItem = chatOptions.firstOrNull { it.id == preferredModels.chatModelId } ?: noneOption,
                onItemSelected = { viewModel.setPreferredModel("chat", it.id) },
                itemLabel = { it.label }
            )
        }
    }

    item {
        StandardCard(
            title = "Live/ассистент",
            description = "Модель для режима реального времени и сценариев ассистента."
        ) {
            ActionToggleGroup(
                items = chatOptions,
                selectedItem = chatOptions.firstOrNull { it.id == preferredModels.assistantLiveModelId } ?: noneOption,
                onItemSelected = { viewModel.setPreferredModel("assistant_live", it.id) },
                itemLabel = { it.label }
            )
        }
    }

    item {
        StandardCard(
            title = "Генерация изображений",
            description = "Модель для режима создания изображений."
        ) {
            ActionToggleGroup(
                items = imageOptions,
                selectedItem = imageOptions.firstOrNull { it.id == preferredModels.imageGenerationModelId } ?: noneOption,
                onItemSelected = { viewModel.setPreferredModel("image_generation", it.id) },
                itemLabel = { it.label }
            )
        }
    }

    item {
        StandardCard(
            title = "Голос",
            description = "Модель озвучки по умолчанию."
        ) {
            ActionToggleGroup(
                items = ttsOptions,
                selectedItem = ttsOptions.firstOrNull { it.id == preferredModels.ttsModelId } ?: noneOption,
                onItemSelected = { viewModel.setPreferredModel("tts", it.id) },
                itemLabel = { it.label }
            )
        }
    }
}

internal fun LazyListScope.externalAccessSection(
    externalAccessPolicy: ExternalAccessPolicy,
    viewModel: SettingsViewModel
) {
    item { Spacer(Modifier.height(Standards.SpacingSm)) }
    item { SectionDivider() }
    item { SectionHeader(title = "Доступ для приложений") }

    item {
        SwitchRow(
            title = "Разрешить доступ к AI",
            description = "Когда выключено, AIDL, Intent API и локальный HTTP блокируются для других приложений.",
            checked = externalAccessPolicy.enabled,
            onCheckedChange = { viewModel.setExternalAccessEnabled(it) }
        )
    }

    if (externalAccessPolicy.pendingPackages.isNotEmpty()) {
        item {
            StandardCard(
                title = "Ожидают подтверждения",
                description = "Новые приложения сначала попадают в список ожидания."
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(Standards.SpacingSm)) {
                    externalAccessPolicy.pendingPackages.forEach { packageName ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BodyLabel(
                                text = packageName,
                                modifier = Modifier.weight(1f)
                            )
                            ActionTextButton(
                                onClickListener = { viewModel.approveClient(packageName) },
                                icon = TnIcons.CircleCheck,
                                text = "Разрешить",
                                shape = RoundedCornerShape(Standards.RadiusFull)
                            )
                        }
                    }
                }
            }
        }
    }

    if (externalAccessPolicy.approvedApps.isNotEmpty()) {
        item {
            StandardCard(
                title = "Разрешённые приложения",
                description = "Можно отозвать доступ в любой момент."
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(Standards.SpacingSm)) {
                    externalAccessPolicy.approvedApps.forEach { app ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = app.appLabel,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = app.packageName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            ActionTextButton(
                                onClickListener = { viewModel.revokeClient(app.packageName) },
                                icon = TnIcons.X,
                                text = "Запретить",
                                shape = RoundedCornerShape(Standards.RadiusFull)
                            )
                        }
                    }
                }
            }
        }
    }
}

internal fun LazyListScope.lanSection(
    lanHubConfig: LanHubConfig,
    lanNodesJson: String,
    viewModel: SettingsViewModel
) {
    val nodes = runCatching {
        Json { ignoreUnknownKeys = true }.decodeFromString<List<LanNodeInfo>>(lanNodesJson)
    }.getOrDefault(emptyList())

    item { Spacer(Modifier.height(Standards.SpacingSm)) }
    item { SectionDivider() }
    item { SectionHeader(title = "LAN-пул устройств") }

    item {
        SwitchRow(
            title = "Включить LAN-пул",
            description = "Распределяет целые задачи по устройствам в локальной сети.",
            checked = lanHubConfig.enabled,
            onCheckedChange = { viewModel.setLanEnabled(it) }
        )
    }

    item {
        SwitchRow(
            title = "Показывать это устройство",
            description = "Делает текущее устройство доступным как локальный AI-узел.",
            checked = lanHubConfig.advertiseLocalNode,
            onCheckedChange = { viewModel.setAdvertiseLocalNode(it) },
            enabled = lanHubConfig.enabled
        )
    }

    item {
        StandardCard(
            title = "Токен сопряжения",
            description = if (lanHubConfig.pairingToken.isBlank()) "Токен будет создан автоматически." else lanHubConfig.pairingToken
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                ActionTextButton(
                    onClickListener = { viewModel.regenerateLanToken() },
                    icon = TnIcons.Refresh,
                    text = "Обновить",
                    shape = RoundedCornerShape(Standards.RadiusFull)
                )
            }
        }
    }

    item {
        StandardCard(
            title = "Узлы",
            description = if (nodes.isEmpty()) "Пока обнаружен только локальный узел." else "Доступно узлов: ${nodes.size}"
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Standards.SpacingSm)) {
                nodes.forEach { node ->
                    Text(
                        text = "${node.name}: ${node.status} • ${node.installedModelCount} моделей",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

internal fun LazyListScope.orchestraSection(
    orchestraConfig: OrchestraConfig,
    orchestraCapabilityState: OrchestraCapabilityState,
    viewModel: SettingsViewModel
) {
    item { Spacer(Modifier.height(Standards.SpacingSm)) }
    item { SectionDivider() }
    item { SectionHeader(title = "Оркестр маленьких моделей") }

    item {
        SwitchRow(
            title = "Включить оркестр",
            description = "Роутер + специалисты для чата, файлов и суммаризации.",
            checked = orchestraConfig.enabled,
            onCheckedChange = { viewModel.setOrchestraEnabled(it) }
        )
    }

    item {
        SwitchRow(
            title = "Автоназначение ролей",
            description = "Hub сам подбирает маленькие модели под роли оркестра.",
            checked = orchestraConfig.autoAssign,
            onCheckedChange = { viewModel.setOrchestraAutoAssign(it) },
            enabled = orchestraConfig.enabled
        )
    }

    item {
        SwitchRow(
            title = "Разрешить spillover в LAN",
            description = "Если памяти не хватает, отдельные задачи можно отдавать соседнему устройству.",
            checked = orchestraConfig.allowLanSpillover,
            onCheckedChange = { viewModel.setOrchestraLanSpillover(it) },
            enabled = orchestraConfig.enabled
        )
    }

    item {
        StandardCard(
            title = "Состояние оркестра",
            description = orchestraCapabilityState.reason
        ) {
            Text(
                text = "Подходящих маленьких моделей: ${orchestraCapabilityState.eligibleModelIds.size}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    item {
        StandardCard(
            title = "Роли",
            description = "Router, chat, files, vision, code, summary/TTS"
        ) {
            Text(
                text = "В этой версии роли назначаются автоматически и работают как управляемый роутер, а не массовый ансамбль.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
