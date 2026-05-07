package com.santiya.localaihub.ui.screen.home

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.santiya.localaihub.activity.RagActivity
import com.santiya.localaihub.global.Standards
import com.santiya.localaihub.global.localizedText
import com.santiya.localaihub.models.ModelType
import com.santiya.localaihub.ui.components.ActionButton
import com.santiya.localaihub.ui.components.ActionProgressButton
import com.santiya.localaihub.ui.components.MemoryOverlayBottomSheet
import com.santiya.localaihub.ui.components.ModeToggleSwitch
import com.santiya.localaihub.ui.components.PluginOverlayBottomSheet
import com.santiya.localaihub.ui.icons.TnIcons
import com.santiya.localaihub.viewmodel.ChatViewModel
import com.santiya.localaihub.viewmodel.OpenClawMode
import com.santiya.localaihub.viewmodel.LLMModelViewModel
import com.santiya.localaihub.viewmodel.MemoryViewModel
import com.santiya.localaihub.viewmodel.PluginViewModel
import com.santiya.localaihub.viewmodel.RagViewModel
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun BottomBar(
    chatViewModel: ChatViewModel = hiltViewModel(),
    llmModelViewModel: LLMModelViewModel = hiltViewModel(),
    ragViewModel: RagViewModel = hiltViewModel(),
    pluginViewModel: PluginViewModel = hiltViewModel(),
    memoryViewModel: MemoryViewModel = hiltViewModel(),
    toolCallingEnabled: Boolean = true
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var value by remember { mutableStateOf("") }
    var showMoreOptions by remember { mutableStateOf(false) }
    var showModelRequiredHint by remember { mutableStateOf(false) }
    var imageAttachments by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var fileAttachments by remember { mutableStateOf<List<Uri>>(emptyList()) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 3)
    ) { uris ->
        if (!uris.isNullOrEmpty()) {
            imageAttachments = (imageAttachments + uris).distinct()
        }
    }
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (!uris.isNullOrEmpty()) {
            fileAttachments = (fileAttachments + uris).distinct()
        }
    }

    val currentModelID by llmModelViewModel.currentModelID.collectAsStateWithLifecycle()
    val chatState by chatViewModel.chatUiState.collectAsStateWithLifecycle()
    val isTextModelLoaded by chatViewModel.isTextModelLoaded.collectAsStateWithLifecycle()
    val isImageModelLoaded by chatViewModel.isImageModelLoaded.collectAsStateWithLifecycle()
    val isModelLoaded = currentModelID.isNotBlank() || isTextModelLoaded

    val loadedRags by ragViewModel.loadedRags.collectAsStateWithLifecycle()
    val isRagEnabledForChat by ragViewModel.isRagEnabledForChat.collectAsStateWithLifecycle()

    val showPluginOverlay by pluginViewModel.showPluginOverlay.collectAsStateWithLifecycle()
    val enabledPluginNames by pluginViewModel.enabledPluginNames.collectAsStateWithLifecycle()
    val expandedPluginIds by pluginViewModel.expandedPluginIds.collectAsStateWithLifecycle()
    val multiTurnEnabled by pluginViewModel.multiTurnEnabled.collectAsStateWithLifecycle()
    val toolCallingConfig by pluginViewModel.toolCallingConfig.collectAsStateWithLifecycle()
    val isToolCallingModelLoaded by pluginViewModel.isToolCallingModelLoaded.collectAsStateWithLifecycle()
    val isWebSearchEnabled by pluginViewModel.isWebSearchEnabled.collectAsStateWithLifecycle()
    val nonWebSearchPlugins by pluginViewModel.nonWebSearchPlugins.collectAsStateWithLifecycle()

    val showMemoryOverlay by memoryViewModel.showMemoryOverlay.collectAsStateWithLifecycle()
    val isMemoryEnabled by memoryViewModel.isMemoryEnabled.collectAsStateWithLifecycle()
    val memoryResults by memoryViewModel.memoryResults.collectAsStateWithLifecycle()
    val vaultStats by memoryViewModel.vaultStats.collectAsStateWithLifecycle()
    val memoryEntryCount by memoryViewModel.memoryEntryCount.collectAsStateWithLifecycle()

    PluginOverlayBottomSheet(
        show = showPluginOverlay,
        plugins = nonWebSearchPlugins,
        enabledPluginNames = enabledPluginNames,
        expandedPluginIds = expandedPluginIds,
        multiTurnEnabled = multiTurnEnabled,
        toolCallingConfig = toolCallingConfig,
        onDismiss = { pluginViewModel.hidePluginOverlay() },
        onPluginToggle = { name, enabled -> pluginViewModel.togglePluginEnabled(name, enabled) },
        onPluginExpand = { name -> pluginViewModel.togglePluginExpanded(name) },
        onMultiTurnToggle = { pluginViewModel.setMultiTurnEnabled(it) },
        onMaxRoundsChange = { pluginViewModel.setMaxRounds(it) }
    )

    MemoryOverlayBottomSheet(
        show = showMemoryOverlay,
        isMemoryEnabled = isMemoryEnabled,
        vaultStats = vaultStats,
        memoryResults = memoryResults,
        memoryEntryCount = memoryEntryCount,
        onDismiss = { memoryViewModel.dismissMemoryOverlay() },
        onMemoryEnabledChange = { memoryViewModel.setMemoryEnabled(it) },
        onRefreshStats = { memoryViewModel.refreshStats() }
    )

    fun selectChatMode(mode: OpenClawMode) {
        chatViewModel.setOpenClawMode(mode)
    }

    fun openAiPanelWithHint() {
        showModelRequiredHint = true
        chatViewModel.hideModelList()
        chatViewModel.showDynamicWindow()
    }

    fun clearComposer() {
        value = ""
        imageAttachments = emptyList()
        fileAttachments = emptyList()
    }

    fun buildPromptWithAttachments(input: String): String {
        val fileSections = fileAttachments.mapNotNull { uri ->
            readTextAttachment(context, uri)?.let { text ->
                val label = resolveAttachmentLabel(context, uri)
                "${localizedText(context, "\u0424\u0430\u0439\u043b", "File")}: $label\n$text"
            }
        }
        val promptCore = input.trim().ifBlank {
            if (imageAttachments.isNotEmpty()) {
                localizedText(context, "\u041e\u043f\u0438\u0448\u0438, \u0447\u0442\u043e \u0438\u0437\u043e\u0431\u0440\u0430\u0436\u0435\u043d\u043e \u043d\u0430 \u0444\u043e\u0442\u043e.", "Describe what is shown in the image.")
            } else {
                ""
            }
        }
        return buildString {
            append(promptCore)
            if (fileSections.isNotEmpty()) {
                if (isNotBlank()) append("\n\n")
                append(fileSections.joinToString("\n\n"))
            }
        }.trim()
    }

    fun sendCurrentMessage() {
        if (value.isBlank() && imageAttachments.isEmpty() && fileAttachments.isEmpty()) return
        if (!isModelLoaded) {
            openAiPanelWithHint()
            return
        }

        showMoreOptions = false
        showModelRequiredHint = false
        val prompt = buildPromptWithAttachments(value)

        when (chatState.generationType) {
            ModelType.TEXT_GENERATION -> {
                if (imageAttachments.isNotEmpty()) {
                    val imageBytes = imageAttachments.mapNotNull { uri -> readBinaryAttachment(context, uri) }
                    if (imageBytes.isNotEmpty()) {
                        chatViewModel.sendChatWithImages(
                            prompt.ifBlank { "Р В Р’В Р РЋРІР‚С”Р В Р’В Р РЋРІР‚вЂќР В Р’В Р РЋРІР‚ВР В Р Р‹Р Р†РІР‚С™Р’В¬Р В Р’В Р РЋРІР‚В, Р В Р Р‹Р Р†Р вЂљР Р‹Р В Р Р‹Р Р†Р вЂљРЎв„ўР В Р’В Р РЋРІР‚Сћ Р В Р’В Р РЋРІР‚ВР В Р’В Р вЂ™Р’В·Р В Р’В Р РЋРІР‚СћР В Р’В Р вЂ™Р’В±Р В Р Р‹Р В РІР‚С™Р В Р’В Р вЂ™Р’В°Р В Р’В Р вЂ™Р’В¶Р В Р’В Р вЂ™Р’ВµР В Р’В Р В РІР‚В¦Р В Р’В Р РЋРІР‚Сћ Р В Р’В Р В РІР‚В¦Р В Р’В Р вЂ™Р’В° Р В Р Р‹Р Р†Р вЂљРЎвЂєР В Р’В Р РЋРІР‚СћР В Р Р‹Р Р†Р вЂљРЎв„ўР В Р’В Р РЋРІР‚Сћ." },
                            imageBytes
                        )
                        clearComposer()
                        return
                    }
                }

                val textPrompt = prompt.ifBlank { value.trim() }
                if (textPrompt.isBlank()) return
                val hasRags = loadedRags.isNotEmpty() && isRagEnabledForChat
                if (hasRags) {
                    val userQuery = textPrompt
                    clearComposer()
                    scope.launch {
                        val ragContext = ragViewModel.queryAndStoreResults(userQuery)
                        chatViewModel.setRagContext(
                            ragContext.ifBlank { null },
                            ragViewModel.lastRagResults.value
                        )
                        chatViewModel.sendTextMessage(userQuery)
                    }
                } else {
                    chatViewModel.clearRagContext()
                    chatViewModel.sendTextMessage(textPrompt)
                    clearComposer()
                }
            }

            ModelType.IMAGE_GENERATION -> {
                chatViewModel.sendImageRequest(prompt.ifBlank { value.trim() })
                clearComposer()
            }

            ModelType.AUDIO_GENERATION -> Unit
        }
    }

    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.primary.copy(0.04f)
                        .compositeOver(MaterialTheme.colorScheme.background)
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = Standards.SpacingSm)
                    .padding(top = Standards.SpacingSm, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(Standards.SpacingSm)
            ) {
                if (showModelRequiredHint || !isModelLoaded) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.75f),
                                shape = RoundedCornerShape(Standards.RadiusLg)
                            )
                            .padding(horizontal = Standards.SpacingMd, vertical = Standards.SpacingSm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Standards.SpacingSm)
                    ) {
                        Icon(
                            imageVector = TnIcons.AlertTriangle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = localizedText("\u0412\u044b\u0431\u0435\u0440\u0438\u0442\u0435 AI-\u043c\u043e\u0434\u0435\u043b\u044c \u0432 \u0432\u0435\u0440\u0445\u043d\u0435\u0439 AI-\u043f\u0430\u043d\u0435\u043b\u0438 \u043f\u0435\u0440\u0435\u0434 \u0437\u0430\u043f\u0443\u0441\u043a\u043e\u043c.", "Pick an AI model from the top AI panel before sending."),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }

                QuickLookChipRow(
                    loadedRagCount = loadedRags.size,
                    isMemoryEnabled = isMemoryEnabled,
                    isWebSearchEnabled = isWebSearchEnabled,
                    isRagEnabled = isRagEnabledForChat,
                    activePluginName = enabledPluginNames.firstOrNull { it != "Web Search" },
                    onRagChipClick = { context.startActivity(Intent(context, RagActivity::class.java)) },
                    onToolChipClick = { pluginViewModel.showPluginOverlay() },
                    onMemoryChipClick = { memoryViewModel.toggleMemoryOverlay() },
                    onWebSearchChipClick = { pluginViewModel.toggleWebSearch(false) }
                )

                MoreOptionsOverlay(
                    show = showMoreOptions,
                    loadedRagCount = loadedRags.size,
                    isRagEnabled = isRagEnabledForChat,
                    onRagToggle = { ragViewModel.toggleRagForChat(it) },
                    onRagManage = {
                        showMoreOptions = false
                        context.startActivity(Intent(context, RagActivity::class.java))
                    },
                    nonWebSearchPlugins = nonWebSearchPlugins,
                    enabledPluginNames = enabledPluginNames,
                    isToolCallingModelLoaded = isToolCallingModelLoaded,
                    toolCallingEnabled = toolCallingEnabled,
                    onPluginToggle = { name, enabled -> pluginViewModel.togglePluginEnabled(name, enabled) },
                    onManagePlugins = {
                        showMoreOptions = false
                        pluginViewModel.showPluginOverlay()
                    }
                )

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Standards.SpacingMd, vertical = Standards.SpacingSm),
                        verticalArrangement = Arrangement.spacedBy(Standards.SpacingSm)
                    ) {
                        val textModeEnabled = chatState.generationType == ModelType.TEXT_GENERATION
                        val openClawActive = textModeEnabled && chatState.openClawEnabled
                        val orchestraModeEnabled = openClawActive && toolCallingEnabled && isToolCallingModelLoaded
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(Standards.SpacingXs),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ComposerModeChip(
                                label = localizedText("\u041e\u0431\u044b\u0447\u043d\u044b\u0439", "Normal"),
                                selected = openClawActive && chatState.openClawMode == OpenClawMode.NORMAL,
                                enabled = openClawActive,
                                onClick = { selectChatMode(OpenClawMode.NORMAL) }
                            )
                            ComposerModeChip(
                                label = localizedText("\u0414\u0443\u043c\u0430\u044e\u0449\u0438\u0439", "Thinking"),
                                selected = openClawActive && chatState.openClawMode == OpenClawMode.THINKING,
                                enabled = openClawActive,
                                onClick = { selectChatMode(OpenClawMode.THINKING) }
                            )
                            ComposerModeChip(
                                label = localizedText("\u041e\u0440\u043a\u0435\u0441\u0442\u0440", "Orchestra"),
                                selected = openClawActive && chatState.openClawMode == OpenClawMode.ORCHESTRA,
                                enabled = orchestraModeEnabled,
                                onClick = { selectChatMode(OpenClawMode.ORCHESTRA) }
                            )
                        }
                        if (textModeEnabled && !openClawActive) {
                            Text(
                                text = localizedText(
                                    "Включите кнопку OpenClaw внизу, если хотите отправлять сообщение через локального агента, а не через обычный чат.",
                                    "Enable the OpenClaw button below to send through the local agent instead of ordinary chat."
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        } else if (textModeEnabled && !orchestraModeEnabled) {
                            Text(
                                text = localizedText(
                                    "Оркестр включится, когда будет загружена локальная GGUF-модель с поддержкой инструментов и активированы локальные инструменты.",
                                    "Orchestra becomes available after a local GGUF tool-calling model is loaded and local tools are enabled."
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        if (imageAttachments.isNotEmpty() || fileAttachments.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(Standards.SpacingXs)
                            ) {
                                imageAttachments.forEach { uri ->
                                    AttachmentChip(
                                        label = resolveAttachmentLabel(context, uri),
                                        icon = TnIcons.Photo,
                                        onRemove = { imageAttachments = imageAttachments - uri }
                                    )
                                }
                                fileAttachments.forEach { uri ->
                                    AttachmentChip(
                                        label = resolveAttachmentLabel(context, uri),
                                        icon = TnIcons.FileText,
                                        onRemove = { fileAttachments = fileAttachments - uri }
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(Standards.SpacingSm)
                        ) {
                            TextField(
                                value = value,
                                onValueChange = { value = it },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 72.dp, max = 160.dp),
                                placeholder = {
                                    Text(
                                        text = when (chatState.generationType) {
                                            ModelType.TEXT_GENERATION -> localizedText(
                                                "\u041d\u0430\u043f\u0438\u0448\u0438\u0442\u0435 \u0437\u0430\u043f\u0440\u043e\u0441, \u0434\u043e\u0431\u0430\u0432\u044c\u0442\u0435 \u0444\u043e\u0442\u043e \u0438\u043b\u0438 \u0444\u0430\u0439\u043b...",
                                                "Write a prompt, add an image or file..."
                                            )
                                            ModelType.IMAGE_GENERATION -> localizedText(
                                                "\u041e\u043f\u0438\u0448\u0438\u0442\u0435 \u0438\u0437\u043e\u0431\u0440\u0430\u0436\u0435\u043d\u0438\u0435...",
                                                "Describe the image..."
                                            )
                                            ModelType.AUDIO_GENERATION -> localizedText(
                                                "\u041d\u0430\u043f\u0438\u0448\u0438\u0442\u0435 \u0437\u0430\u043f\u0440\u043e\u0441...",
                                                "Write a prompt..."
                                            )
                                        }
                                    )
                                },
                                maxLines = 6,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    disabledContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    cursorColor = MaterialTheme.colorScheme.primary
                                )
                            )

                            if (chatState.isGenerating) {
                                ActionProgressButton(
                                    onClickListener = { chatViewModel.stop() },
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.primary.copy(0.18f),
                                        contentColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                            } else {
                                ActionButton(
                                    onClickListener = { sendCurrentMessage() },
                                    icon = TnIcons.Send,
                                    shape = MaterialShapes.Ghostish.toShape(),
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.primary.copy(0.18f),
                                        contentColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(Standards.SpacingXs),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ModeToggleSwitch(
                                isImageMode = chatState.generationType == ModelType.IMAGE_GENERATION,
                                onModeChange = { isImageMode ->
                                    if (isImageMode) chatViewModel.switchToImageGeneration()
                                    else chatViewModel.switchToTextGeneration()
                                },
                                textModelLoaded = isTextModelLoaded,
                                imageModelLoaded = isImageModelLoaded
                            )

                            ComposerIconAction(
                                icon = TnIcons.Photo,
                                selected = imageAttachments.isNotEmpty(),
                                enabled = chatState.generationType == ModelType.TEXT_GENERATION,
                                onClick = {
                                    photoPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                            )

                            ComposerIconAction(
                                icon = TnIcons.FileUpload,
                                selected = fileAttachments.isNotEmpty(),
                                onClick = {
                                    filePicker.launch(arrayOf("text/plain", "text/*", "application/json", "text/csv"))
                                }
                            )

                            ComposerIconAction(
                                icon = TnIcons.Adjustments,
                                selected = showMoreOptions,
                                onClick = { showMoreOptions = !showMoreOptions }
                            )

                            ComposerIconAction(
                                icon = TnIcons.BrainCircuit,
                                selected = chatState.openClawEnabled,
                                enabled = chatState.generationType == ModelType.TEXT_GENERATION,
                                onClick = { chatViewModel.toggleOpenClawEnabled() }
                            )

                            if (toolCallingEnabled) {
                                ComposerIconAction(
                                    icon = TnIcons.World,
                                    selected = isWebSearchEnabled,
                                    enabled = isToolCallingModelLoaded,
                                    onClick = { pluginViewModel.toggleWebSearch(!isWebSearchEnabled) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ComposerIconAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(Standards.RadiusFull),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
        }
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ComposerModeChip(
    label: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(Standards.RadiusFull),
        color = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
        }
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = when {
                !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                selected -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        )
    }
}

@Composable
private fun AttachmentChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onRemove: () -> Unit,
) {
    Surface(
        onClick = onRemove,
        shape = RoundedCornerShape(Standards.RadiusFull),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.82f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Standards.SpacingXs)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Icon(
                imageVector = TnIcons.X,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

private fun resolveAttachmentLabel(context: Context, uri: Uri): String {
    return runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val columnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (columnIndex >= 0) {
                    return@runCatching cursor.getString(columnIndex)
                }
            }
        }
        uri.lastPathSegment?.substringAfterLast('/') ?: localizedText(context, "\u0424\u0430\u0439\u043b", "File")
    }.getOrDefault(localizedText(context, "\u0424\u0430\u0439\u043b", "File"))
}

private fun readBinaryAttachment(context: Context, uri: Uri): ByteArray? {
    return runCatching {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            inputStream.readBytes()
        }
    }.getOrNull()
}

private fun readTextAttachment(context: Context, uri: Uri, maxChars: Int = 6000): String? {
    return runCatching {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                val text = buildString {
                    val buffer = CharArray(1024)
                    var read = reader.read(buffer)
                    while (read > 0 && length < maxChars) {
                        append(buffer, 0, read)
                        read = reader.read(buffer)
                    }
                }
                text.trim().takeIf { it.isNotBlank() }?.take(maxChars)
            }
        }
    }.getOrNull()
}
