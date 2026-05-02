package com.santiya.localaihub.ui.screen.home

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.santiya.localaihub.models.ModelType
import com.santiya.localaihub.models.messages.ContentType
import com.santiya.localaihub.models.messages.Role
import com.santiya.localaihub.ui.components.lazyMarkdownItems
import com.santiya.localaihub.ui.theme.Motion
import com.santiya.localaihub.viewmodel.ChatViewModel
import com.santiya.localaihub.viewmodel.LLMModelViewModel
import com.santiya.localaihub.global.Standards
import com.santiya.localaihub.viewmodel.StreamingState
import com.santiya.localaihub.viewmodel.ChatUiState
import com.santiya.localaihub.viewmodel.AgentState
import com.santiya.localaihub.viewmodel.RagState
import com.santiya.localaihub.viewmodel.ChatConfigState

// в”Ђв”Ђ Pre-compiled regex (avoid allocation in composition) в”Ђв”Ђ

internal val THINK_TAG_REGEX = Regex(
    "<think>(.*?)</think>|\\[THINK](.*?)\\[/THINK]|<reasoning>(.*?)</reasoning>",
    RegexOption.DOT_MATCHES_ALL
)
private val THINK_OPEN_TAGS = listOf("<think>", "[THINK]", "<reasoning>")

data class ParsedMessage(
    val thinkingContent: String?,
    val actualContent: String,
    val isThinkingInProgress: Boolean = false
)

fun parseThinkingTags(content: String): ParsedMessage {
    // Fast path: no think tags at all
    val openTag = THINK_OPEN_TAGS.firstOrNull { content.contains(it, ignoreCase = true) }
        ?: return ParsedMessage(null, content.trim())

    // Completed thinking: matched pair present
    val thinkingMatch = THINK_TAG_REGEX.find(content)
    if (thinkingMatch != null) {
        // Group 1 = <think>, Group 2 = [THINK], Group 3 = <reasoning>
        val thinkingContent = thinkingMatch.groupValues.drop(1).firstOrNull { it.isNotEmpty() }?.trim() ?: ""
        val actualContent = content.replace(THINK_TAG_REGEX, "").trim()
        return ParsedMessage(
            thinkingContent = thinkingContent.ifEmpty { null },
            actualContent = actualContent
        )
    }

    // In-progress thinking: open tag without close tag (streaming)
    val openIdx = content.indexOf(openTag, ignoreCase = true)
    val thinkingContent = content.substring(openIdx + openTag.length).trim()
    val beforeThink = content.substring(0, openIdx).trim()
    return ParsedMessage(
        thinkingContent = thinkingContent.ifEmpty { null },
        actualContent = beforeThink,
        isThinkingInProgress = true
    )
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun BodyContent(
    paddingValues: PaddingValues,
    chatViewModel: ChatViewModel,
    llmModelViewModel: LLMModelViewModel,
    onStoreClick: () -> Unit,
    onFilesClick: () -> Unit,
    onLiveClick: () -> Unit,
) {
    val messages = chatViewModel.messages
    val streaming by chatViewModel.streamingState.collectAsStateWithLifecycle()
    val chatState by chatViewModel.chatUiState.collectAsStateWithLifecycle()
    val agent by chatViewModel.agentState.collectAsStateWithLifecycle()
    val rag by chatViewModel.ragState.collectAsStateWithLifecycle()
    val config by chatViewModel.chatConfigState.collectAsStateWithLifecycle()
    val appState by com.santiya.localaihub.state.AppStateManager.appState.collectAsStateWithLifecycle()
    val ttsPlayingMsgId by chatViewModel.ttsPlayingMsgId.collectAsStateWithLifecycle()
    val ttsIsPlaying by chatViewModel.ttsIsPlaying.collectAsStateWithLifecycle()
    val ttsSynthesizing by chatViewModel.ttsSynthesizing.collectAsStateWithLifecycle()
    val ttsModelLoaded by chatViewModel.ttsModelLoaded.collectAsStateWithLifecycle()
    val installedModels by llmModelViewModel.installedModels.collectAsStateWithLifecycle(emptyList())
    val currentModelId by llmModelViewModel.currentModelID.collectAsStateWithLifecycle()
    val currentModelName = installedModels.firstOrNull { it.id == currentModelId }?.modelName

    // Image blur setting вЂ” collected once, passed down to avoid per-message DataStore creation
    val context = androidx.compose.ui.platform.LocalContext.current
    val imageBlurEnabled by remember { com.santiya.localaihub.data.AppSettingsDataStore(context).imageBlurEnabled }
        .collectAsStateWithLifecycle(initialValue = true)

    val listState = rememberLazyListState()
    val statusStripHeight = 64.dp

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty() && !chatState.isGenerating) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(paddingValues)
    ) {
        ModelStatusStrip(
            currentModelName = currentModelName,
            onClick = { chatViewModel.showDynamicWindow() },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(horizontal = Standards.SpacingMd, vertical = Standards.SpacingSm)
        )

        if (messages.isEmpty() && !chatState.isGenerating) {
            HomeLandingState(
                modifier = Modifier.padding(top = statusStripHeight),
                llmModelViewModel = llmModelViewModel,
                onStoreClick = onStoreClick,
                onFilesClick = onFilesClick,
                onLiveClick = onLiveClick,
                onShowModelPicker = { chatViewModel.showDynamicWindow() }
            )
        } else {
            if (chatState.isGenerating && streaming.userMessage != null) {
                Box(modifier = Modifier.padding(top = statusStripHeight)) {
                    StreamingView(
                    userMessage = streaming.userMessage!!,
                    assistantMessage = streaming.assistantMessage,
                    streamingImage = streaming.image,
                    imageProgress = streaming.imageProgress,
                    imageStep = streaming.imageStep,
                    isImageGeneration = chatState.generationType == ModelType.IMAGE_GENERATION,
                    ragResults = rag.results,
                    appState = appState,
                    messages = messages,
                    toolChainSteps = agent.toolChainSteps,
                    currentToolChainRound = agent.currentRound,
                    agentPhase = agent.phase,
                    agentPlan = agent.plan,
                    agentSummary = agent.summary,
                    thinkingEnabled = chatState.thinkingEnabled
                    )
                }
            } else {
                val deduped = remember(messages.size) { messages.distinctBy { it.msgId } }
                val lastAssistantIndex = remember(deduped.size) { deduped.indexOfLast { it.role == Role.Assistant } }

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = statusStripHeight),
                    contentPadding = PaddingValues(vertical = Standards.SpacingSm),
                    verticalArrangement = Arrangement.spacedBy(Standards.SpacingXs)
                ) {

                    deduped.forEachIndexed { index, message ->
                        when (message.role) {
                            Role.User -> {
                                item(key = "${message.msgId}-user") {
                                    UserMessageBubble(message)
                                }
                            }
                            else -> {
                                val isLastAssistant = index == lastAssistantIndex
                                // Header: RAG, tool chain, thinking, image/plugin
                                item(key = "${message.msgId}-header") {
                                    AssistantMessageHeader(message, imageBlurEnabled)
                                }
                                // Markdown content вЂ” each element is a lazy item
                                if (message.content.contentType == ContentType.Text) {
                                    val raw = message.content.content
                                    val parsedText = if (THINK_TAG_REGEX.containsMatchIn(raw)) {
                                        raw.replace(THINK_TAG_REGEX, "").trim()
                                    } else raw
                                    if (parsedText.isNotEmpty()) {
                                        lazyMarkdownItems(
                                            text = parsedText,
                                            keyPrefix = message.msgId,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = Standards.SpacingMd)
                                        )
                                    }
                                }
                                // Footer: metrics + action row
                                item(key = "${message.msgId}-footer") {
                                    AssistantMessageFooter(
                                        message = message,
                                        ttsPlayingMsgId = ttsPlayingMsgId,
                                        ttsIsPlaying = ttsIsPlaying,
                                        ttsSynthesizing = ttsSynthesizing,
                                        ttsModelLoaded = ttsModelLoaded,
                                        onSpeak = { chatViewModel.speakMessage(it) },
                                        onStopTTS = { chatViewModel.stopTTS() },
                                        onRegenerate = if (isLastAssistant) {
                                            { chatViewModel.regenerateLastMessage() }
                                        } else null,
                                        isRegenerateEnabled = !chatState.isGenerating
                                    )
                                }
                            }
                        }
                    }
                    item {
                        Spacer(modifier = Modifier.height(Standards.SpacingLg))
                    }
                }
            }
        }

        // Scrim + Dynamic Action Window вЂ” single AnimatedVisibility to avoid double state reads
        AnimatedVisibility(
            visible = config.showDynamicWindow,
            enter = fadeIn(Motion.entrance()),
            exit = fadeOut(Motion.exit())
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Scrim background
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) {
                            chatViewModel.hideDynamicWindow()
                        }
                )

                // Window content with spring animation
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Standards.SpacingLg, vertical = Standards.SpacingLg),
                    contentAlignment = Alignment.TopCenter
                ) {
                    val ragCount by com.santiya.localaihub.plugins.PluginManager.enabledPluginNames.collectAsStateWithLifecycle()
                    val ttsLoaded by com.santiya.localaihub.tts.TTSManager.isModelLoaded.collectAsStateWithLifecycle()

                    DynamicActionWindow(
                        chatViewModel = chatViewModel,
                        modelViewModel = llmModelViewModel,
                        enabledToolCount = ragCount.size,
                        ttsModelLoaded = ttsLoaded
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelStatusStrip(
    currentModelName: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasModel = !currentModelName.isNullOrBlank()
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Standards.RadiusLg),
        color = if (hasModel) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
        } else {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f)
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Standards.SpacingMd, vertical = Standards.SpacingSm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Standards.SpacingSm)
        ) {
            Icon(
                imageVector = if (hasModel) com.santiya.localaihub.ui.icons.TnIcons.CircleCheck else com.santiya.localaihub.ui.icons.TnIcons.AlertTriangle,
                contentDescription = null,
                tint = if (hasModel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onErrorContainer
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (hasModel) "Активная AI-модель" else "Выберите AI-модель",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (hasModel) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = currentModelName ?: "Откройте AI-панель вверху и выберите модель перед запуском чата.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (hasModel) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}
