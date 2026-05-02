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
import com.santiya.localaihub.models.ModelType
import com.santiya.localaihub.ui.components.ActionButton
import com.santiya.localaihub.ui.components.ActionProgressButton
import com.santiya.localaihub.ui.components.MemoryOverlayBottomSheet
import com.santiya.localaihub.ui.components.ModeToggleSwitch
import com.santiya.localaihub.ui.components.PluginOverlayBottomSheet
import com.santiya.localaihub.ui.icons.TnIcons
import com.santiya.localaihub.viewmodel.ChatViewModel
import com.santiya.localaihub.viewmodel.LLMModelViewModel
import com.santiya.localaihub.viewmodel.MemoryViewModel
import com.santiya.localaihub.viewmodel.PluginViewModel
import com.santiya.localaihub.viewmodel.RagViewModel
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader

private enum class ChatComposerMode {
    NORMAL,
    THINKING,
    ORCHESTRA
}

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
    var chatMode by remember { mutableStateOf(ChatComposerMode.NORMAL) }

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
    val isModelLoaded = currentModelID.isNotBlank()

    val chatState by chatViewModel.chatUiState.collectAsStateWithLifecycle()
    val isTextModelLoaded by chatViewModel.isTextModelLoaded.collectAsStateWithLifecycle()
    val isImageModelLoaded by chatViewModel.isImageModelLoaded.collectAsStateWithLifecycle()

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

    fun selectChatMode(mode: ChatComposerMode) {
        chatMode = mode
        val shouldThink = mode != ChatComposerMode.NORMAL
        if (isTextModelLoaded && chatState.thinkingEnabled != shouldThink) {
            chatViewModel.toggleThinkingMode()
        }
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
                "Файл: $label\n$text"
            }
        }
        val promptCore = input.trim().ifBlank {
            if (imageAttachments.isNotEmpty()) "Опиши, что изображено на фото." else ""
        }
        val withModePrefix = when (chatMode) {
            ChatComposerMode.NORMAL -> promptCore
            ChatComposerMode.THINKING -> "Режим размышления.\n$promptCore".trim()
            ChatComposerMode.ORCHESTRA -> "Режим оркестра. Разбей задачу на роли и дай единый согласованный ответ.\n$promptCore".trim()
        }
        return buildString {
            append(withModePrefix)
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
                            prompt.ifBlank { "Опиши, что изображено на фото." },
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
                if (showModelRequiredHint || currentModelID.isBlank()) {
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
                            text = "Выберите AI-модель в верхней AI-панели перед запуском.",
                            style = MaterialTheme.typography.bodySmall,
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
                                            ModelType.TEXT_GENERATION -> "Напишите запрос, добавьте фото или файл..."
                                            ModelType.IMAGE_GENERATION -> "Опишите изображение..."
                                            ModelType.AUDIO_GENERATION -> "Напишите запрос..."
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

                            if (toolCallingEnabled) {
                                ComposerIconAction(
                                    icon = TnIcons.World,
                                    selected = isWebSearchEnabled,
                                    enabled = isToolCallingModelLoaded,
                                    onClick = { pluginViewModel.toggleWebSearch(!isWebSearchEnabled) }
                                )
                            }

                            if (isTextModelLoaded) {
                                ComposerModeChip(
                                    label = "Обычный",
                                    selected = chatMode == ChatComposerMode.NORMAL,
                                    onClick = { selectChatMode(ChatComposerMode.NORMAL) }
                                )
                                ComposerModeChip(
                                    label = "Размышление",
                                    selected = chatMode == ChatComposerMode.THINKING,
                                    onClick = { selectChatMode(ChatComposerMode.THINKING) }
                                )
                                ComposerModeChip(
                                    label = "Оркестр",
                                    selected = chatMode == ChatComposerMode.ORCHESTRA,
                                    onClick = { selectChatMode(ChatComposerMode.ORCHESTRA) }
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
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
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
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
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
        uri.lastPathSegment?.substringAfterLast('/') ?: "Файл"
    }.getOrDefault("Файл")
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
