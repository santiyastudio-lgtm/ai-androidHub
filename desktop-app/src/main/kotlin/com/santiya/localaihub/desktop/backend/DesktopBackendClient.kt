package com.santiya.localaihub.desktop.backend

import com.santiya.localaihub.desktop.state.CatalogEntry
import com.santiya.localaihub.desktop.state.ComposerMode
import com.santiya.localaihub.desktop.state.ExternalAccessPolicy
import com.santiya.localaihub.desktop.state.LanNodeCardState
import com.santiya.localaihub.desktop.state.ModelSummary
import com.santiya.localaihub.desktop.state.OrchestraConfig
import com.santiya.localaihub.desktop.state.PluginSummary
import com.santiya.localaihub.desktop.state.PreferredModels
import com.santiya.localaihub.desktop.state.RuntimeBadgeState
import com.santiya.localaihub.desktop.state.SetupRecommendation
import com.santiya.localaihub.desktop.state.StatusSummary
import com.santiya.localaihub.desktop.state.ToolDefinition
import com.santiya.localaihub.desktop.state.ToolState

interface DesktopBackendClient {
    val baseUrl: String

    suspend fun ping(): Boolean
    suspend fun status(): StatusSummary
    suspend fun models(): List<ModelSummary>
    suspend fun catalog(): List<CatalogEntry>
    suspend fun runtimes(): List<RuntimeBadgeState>
    suspend fun plugins(): List<PluginSummary>
    suspend fun tools(): List<ToolDefinition>
    suspend fun toolState(): ToolState
    suspend fun updateToolState(state: ToolState)
    suspend fun preferredModels(): PreferredModels
    suspend fun updatePreferredModels(models: PreferredModels)
    suspend fun externalAccess(): ExternalAccessPolicy
    suspend fun updateExternalAccess(policy: ExternalAccessPolicy)
    suspend fun orchestra(): OrchestraConfig
    suspend fun updateOrchestra(config: OrchestraConfig)
    suspend fun nodes(): List<LanNodeCardState>
    suspend fun setupRecommendation(): SetupRecommendation?
    suspend fun chatGenerate(prompt: String, systemPrompt: String, mode: ComposerMode, modelId: String): String
    suspend fun lanExecute(prompt: String, hostHint: String, remotePort: Int, systemPrompt: String, mode: ComposerMode): String
}

class BackendUnavailableException(message: String) : RuntimeException(message)
