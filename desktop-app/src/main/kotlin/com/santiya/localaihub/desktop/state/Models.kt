package com.santiya.localaihub.desktop.state

enum class ThemePreset {
    SYSTEM,
    MIDNIGHT_VIOLET,
    OBSIDIAN_MONO,
    MARBLE_LILAC
}

enum class AppLocale {
    SYSTEM,
    RU,
    EN
}

enum class ShellRoute {
    GUIDE,
    TERMS,
    SETUP,
    HOME,
    STORE,
    LIVE,
    FILES,
    SETTINGS
}

enum class UtilityWindowType {
    NODES,
    SESSIONS,
    LOGS,
    DEVELOPER,
    RUNTIME
}

enum class StoreTab {
    CATALOG,
    INSTALLED,
    SOURCES
}

enum class ComposerMode {
    NORMAL,
    THINKING,
    ORCHESTRA
}

enum class BackendMode {
    CORE,
    HUB,
    OFFLINE
}

data class BackendAvailability(
    val mode: BackendMode = BackendMode.OFFLINE,
    val baseUrl: String = "",
    val message: String = "Disconnected",
    val advancedFeaturesAvailable: Boolean = false
)

data class StatusSummary(
    val appName: String = "SantiyaLocalAiHub",
    val version: String = "",
    val pairingTokenConfigured: Boolean = false,
    val modelCount: Int = 0,
    val runtimeCount: Int = 0,
    val pluginCount: Int = 0,
    val toolCount: Int = 0,
    val ragCount: Int = 0,
    val lanEnabled: Boolean = false,
    val orchestraEnabled: Boolean = false,
    val toolCallingEnabled: Boolean = false
)

data class ModelSummary(
    val id: String,
    val name: String,
    val path: String,
    val sizeMb: Int,
    val runtime: String,
    val modelType: String,
    val capabilities: List<String>
)

data class CatalogWarning(
    val title: String,
    val message: String,
    val severity: String
)

data class CatalogEntry(
    val id: String,
    val title: String,
    val description: String,
    val taskLabel: String,
    val thumbnailUrl: String,
    val previewImages: List<String>,
    val ramEstimateMb: Int?,
    val supportStatus: String,
    val downloadability: String,
    val warnings: List<CatalogWarning>,
    val sourceLabel: String,
    val assistantEligible: Boolean,
    val liveEligible: Boolean,
    val tags: List<String>
)

data class RuntimeBadgeState(
    val name: String,
    val status: String,
    val summary: String
)

data class ToolDefinition(
    val pluginName: String,
    val toolName: String,
    val description: String,
    val parameters: List<String>
)

data class PluginSummary(
    val name: String,
    val description: String,
    val version: String,
    val enabled: Boolean
)

data class ToolState(
    val enabledPlugins: Set<String> = emptySet(),
    val webSearchEnabled: Boolean = false,
    val grammarMode: Boolean = false,
    val multiTurn: Boolean = true,
    val modelSupportGating: Boolean = true,
    val bypass: Boolean = false
)

data class PreferredModels(
    val chatModelId: String = "",
    val visionModelId: String = "",
    val imageGenerationModelId: String = "",
    val ttsModelId: String = "",
    val assistantLiveModelId: String = "",
    val filesModelId: String = ""
)

data class ExternalAccessPolicy(
    val enabled: Boolean = false,
    val requireApproval: Boolean = true,
    val description: String = ""
)

data class OrchestraConfig(
    val enabled: Boolean = false,
    val autoAssign: Boolean = true,
    val allowLanSpillover: Boolean = true
)

data class LanNodeCardState(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val status: String,
    val platform: String,
    val notes: String,
    val capabilities: List<String>,
    val installedModelCount: Int,
    val isLocal: Boolean,
    val transportSummary: String
)

data class SetupRecommendation(
    val title: String,
    val description: String,
    val sizeLabel: String,
    val installActionLabel: String,
    val projectorTitle: String? = null,
    val projectorDescription: String? = null,
    val projectorActionLabel: String? = null,
    val warning: String? = null,
    val status: String = "manual_import_only"
)

data class ComposerState(
    val prompt: String = "",
    val systemPrompt: String = "",
    val mode: ComposerMode = ComposerMode.NORMAL,
    val selectedNodeId: String = "",
    val generating: Boolean = false,
    val lastResult: String = ""
)

data class HomeState(
    val activeModelName: String,
    val backendAvailability: BackendAvailability,
    val statusSummary: StatusSummary,
    val runtimeBadges: List<RuntimeBadgeState>,
    val quickActions: List<String>,
    val composerState: ComposerState,
    val lanNodes: List<LanNodeCardState>,
    val timeline: List<String>
)

data class StoreCardState(
    val entry: CatalogEntry,
    val expanded: Boolean = false
)

data class ModelDetailState(
    val installedModels: List<ModelSummary>,
    val catalogCards: List<StoreCardState>,
    val currentTab: StoreTab
)

data class StoreState(
    val summary: String,
    val detailState: ModelDetailState
)

data class LiveState(
    val availability: BackendAvailability,
    val activeModelName: String,
    val readiness: String,
    val notes: String
)

data class FilesState(
    val workspacePath: String,
    val ragCount: Int,
    val notes: List<String>
)

data class SettingsState(
    val themePreset: ThemePreset,
    val appLocale: AppLocale,
    val preferredModels: PreferredModels,
    val toolState: ToolState,
    val externalAccessPolicy: ExternalAccessPolicy,
    val orchestraConfig: OrchestraConfig,
    val backendAvailability: BackendAvailability,
    val coreUrl: String,
    val hubUrl: String
)

data class UtilityWindowState(
    val title: String,
    val subtitle: String
)
