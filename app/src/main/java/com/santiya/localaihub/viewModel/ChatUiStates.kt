package com.santiya.localaihub.viewmodel

import android.graphics.Bitmap
import com.santiya.localaihub.models.ModelType
import com.santiya.localaihub.models.messages.ToolChainStepData

// в•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђ
//  CHAT UI STATE GROUPS
//  Grouped by update frequency for optimal recomposition.
// в•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђ

// в”Ђв”Ђ HOT вЂ” updates per token (~50ms during generation) в”Ђв”Ђ

data class StreamingState(
    val userMessage: String? = null,
    val assistantMessage: String = "",
    val image: Bitmap? = null,
    val imageProgress: Float = 0f,
    val imageStep: String = ""
)

// в”Ђв”Ђ WARM вЂ” updates per user action / phase change в”Ђв”Ђ

data class ChatUiState(
    val isGenerating: Boolean = false,
    val currentChatId: String? = null,
    val error: String? = null,
    val generationType: ModelType = ModelType.TEXT_GENERATION,
    val openClawEnabled: Boolean = false,
    val thinkingEnabled: Boolean = false,
    val modelSupportsThinking: Boolean = false,
    val openClawMode: OpenClawMode = OpenClawMode.NORMAL
)

enum class OpenClawMode {
    NORMAL,
    THINKING,
    ORCHESTRA
}

data class AgentState(
    val phase: AgentPhase = AgentPhase.Idle,
    val plan: String? = null,
    val summary: String? = null,
    val toolChainSteps: List<ToolChainStepData> = emptyList(),
    val currentRound: Int = 0
)

data class RagState(
    val context: String? = null,
    val results: List<RagQueryDisplayResult> = emptyList()
)

// в”Ђв”Ђ COLD вЂ” updates rarely / per session в”Ђв”Ђ

data class ChatConfigState(
    val streamingEnabled: Boolean = true,
    val chatMemoryEnabled: Boolean = true,
    val showDynamicWindow: Boolean = false,
    val showModelList: Boolean = false
)
