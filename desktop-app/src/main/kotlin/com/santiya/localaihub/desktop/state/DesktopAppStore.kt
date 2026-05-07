package com.santiya.localaihub.desktop.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.santiya.localaihub.desktop.backend.BackendUnavailableException
import com.santiya.localaihub.desktop.backend.CoreBackendClient
import com.santiya.localaihub.desktop.backend.DesktopBackendClient
import com.santiya.localaihub.desktop.backend.HubBackendClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.prefs.Preferences

class DesktopAppStore {
    private val preferences = Preferences.userRoot().node("com/santiya/localaihub/desktop-app")

    var themePreset by mutableStateOf(loadThemePreset())
    var locale by mutableStateOf(loadLocale())
    var route by mutableStateOf(if (preferences.getBoolean(KEY_ONBOARDING_COMPLETE, false)) ShellRoute.HOME else ShellRoute.GUIDE)
    var storeTab by mutableStateOf(StoreTab.CATALOG)
    var backendAvailability by mutableStateOf(BackendAvailability())
    var statusSummary by mutableStateOf(StatusSummary())
    var setupRecommendation by mutableStateOf(defaultSetupRecommendation())
    var externalAccessPolicy by mutableStateOf(ExternalAccessPolicy())
    var orchestraConfig by mutableStateOf(OrchestraConfig())
    var toolState by mutableStateOf(ToolState())
    var preferredModels by mutableStateOf(PreferredModels())
    var composerState by mutableStateOf(ComposerState())
    var coreUrl by mutableStateOf(preferences.get(KEY_CORE_URL, "http://127.0.0.1:17861"))
    var hubUrl by mutableStateOf(preferences.get(KEY_HUB_URL, "http://127.0.0.1:17860"))
    var setupPerformanceMode by mutableStateOf("balanced")
    var lastError by mutableStateOf("")
    var termsScrolledToEnd by mutableStateOf(false)

    val models = mutableStateListOf<ModelSummary>()
    val catalog = mutableStateListOf<CatalogEntry>()
    val runtimes = mutableStateListOf<RuntimeBadgeState>()
    val plugins = mutableStateListOf<PluginSummary>()
    val tools = mutableStateListOf<ToolDefinition>()
    val lanNodes = mutableStateListOf<LanNodeCardState>()
    val sessionTimeline = mutableStateListOf<String>()
    val expandedCatalogCards = mutableStateMapOf<String, Boolean>()
    val utilityWindows = mutableStateMapOf<UtilityWindowType, Boolean>()
    private val optionalEndpointAvailability = mutableStateMapOf<String, Boolean>()

    suspend fun initialize() {
        bootstrapLocalBackendIfNeeded()
        refreshAll()
    }

    suspend fun refreshAll() {
        val client = detectBackend()
        if (client == null) {
            backendAvailability = BackendAvailability(
                mode = BackendMode.OFFLINE,
                baseUrl = "",
                message = "Локальный runtime не найден. Запустите windows-core или windows-hub.",
                advancedFeaturesAvailable = false
            )
            statusSummary = StatusSummary()
            if (preferences.getBoolean(KEY_ONBOARDING_COMPLETE, false)) {
                route = ShellRoute.SETUP
            }
            return
        }

        try {
            backendAvailability = BackendAvailability(
                mode = if (client is CoreBackendClient) BackendMode.CORE else BackendMode.HUB,
                baseUrl = client.baseUrl,
                message = "Подключено к ${client.baseUrl}",
                advancedFeaturesAvailable = false
            )
            statusSummary = client.status()
            models.replaceWith(client.models())
            catalog.replaceWith(optionalCall("catalog") { client.catalog() }.orEmpty())
            runtimes.replaceWith(optionalCall("runtimes") { client.runtimes() }.orEmpty())
            plugins.replaceWith(optionalCall("plugins") { client.plugins() }.orEmpty())
            tools.replaceWith(optionalCall("tools") { client.tools() }.orEmpty())
            toolState = optionalCall("tool state") { client.toolState() } ?: ToolState()
            preferredModels = optionalCall("preferred models") { client.preferredModels() } ?: PreferredModels()
            externalAccessPolicy = optionalCall("external access") { client.externalAccess() } ?: ExternalAccessPolicy()
            orchestraConfig = optionalCall("orchestra") { client.orchestra() } ?: OrchestraConfig()
            lanNodes.replaceWith(optionalCall("LAN nodes") { client.nodes() }.orEmpty())
            setupRecommendation = optionalCall("setup recommendation") { client.setupRecommendation() } ?: deriveSetupRecommendation()
            backendAvailability = backendAvailability.copy(
                advancedFeaturesAvailable = client is CoreBackendClient || catalog.isNotEmpty() || runtimes.isNotEmpty() || plugins.isNotEmpty() || tools.isNotEmpty()
            )
            if (preferences.getBoolean(KEY_ONBOARDING_COMPLETE, false) && route.ordinal < ShellRoute.HOME.ordinal) {
                route = if (hasPrimaryModel()) ShellRoute.HOME else ShellRoute.SETUP
            }
            lastError = ""
        } catch (error: Exception) {
            lastError = error.message.orEmpty()
            sessionTimeline.add(0, "Не удалось обновить backend: ${error.message.orEmpty()}")
            backendAvailability = BackendAvailability(
                mode = backendAvailability.mode,
                baseUrl = client.baseUrl,
                message = error.message.orEmpty().ifBlank { "Runtime refresh failed" },
                advancedFeaturesAvailable = false
            )
            setupRecommendation = deriveSetupRecommendation()
        }
    }

    fun resolvedStrings(): DesktopStrings = stringsFor(resolveLocale(locale))

    fun activeModelName(): String {
        val preferredId = preferredModels.chatModelId.ifBlank { models.firstOrNull()?.id.orEmpty() }
        return models.firstOrNull { it.id == preferredId }?.name
            ?: models.firstOrNull()?.name
            ?: ""
    }

    fun isUtilityWindowOpen(type: UtilityWindowType): Boolean = utilityWindows[type] == true

    fun openUtility(type: UtilityWindowType) {
        utilityWindows[type] = true
    }

    fun closeUtility(type: UtilityWindowType) {
        utilityWindows[type] = false
    }

    fun utilityTitle(type: UtilityWindowType): String = when (type) {
        UtilityWindowType.NODES -> "Node Farm"
        UtilityWindowType.SESSIONS -> "Sessions"
        UtilityWindowType.LOGS -> "Logs"
        UtilityWindowType.DEVELOPER -> "Developer/API"
        UtilityWindowType.RUNTIME -> "Runtime Monitor"
    }

    fun completeGuide() {
        route = ShellRoute.TERMS
    }

    fun updateTermsScrolledToEnd(scrolled: Boolean) {
        termsScrolledToEnd = scrolled
    }

    fun acceptTerms() {
        if (!termsScrolledToEnd) {
            return
        }
        route = ShellRoute.SETUP
        preferences.putBoolean(KEY_TERMS_ACCEPTED, true)
    }

    fun finishSetup() {
        preferences.putBoolean(KEY_ONBOARDING_COMPLETE, true)
        route = if (hasPrimaryModel()) ShellRoute.HOME else ShellRoute.STORE
    }

    fun navigate(route: ShellRoute) {
        this.route = route
    }

    fun selectStoreTab(tab: StoreTab) {
        storeTab = tab
    }

    fun toggleCatalogCard(id: String) {
        expandedCatalogCards[id] = !(expandedCatalogCards[id] ?: false)
    }

    fun updateTheme(theme: ThemePreset) {
        themePreset = theme
        preferences.put(KEY_THEME, theme.name)
    }

    fun updateLocale(appLocale: AppLocale) {
        locale = appLocale
        preferences.put(KEY_LOCALE, appLocale.name)
    }

    fun updateCoreUrl(value: String) {
        coreUrl = value
        preferences.put(KEY_CORE_URL, value)
    }

    fun updateHubUrl(value: String) {
        hubUrl = value
        preferences.put(KEY_HUB_URL, value)
    }

    suspend fun toggleWebSearch(enabled: Boolean) {
        mutateToolState { copy(webSearchEnabled = enabled) }
    }

    suspend fun togglePlugin(pluginName: String, enabled: Boolean) {
        mutateToolState {
            val updated = enabledPlugins.toMutableSet()
            if (enabled) updated.add(pluginName) else updated.remove(pluginName)
            copy(enabledPlugins = updated)
        }
    }

    suspend fun toggleExternalAccess(enabled: Boolean) {
        mutateExternalAccess(externalAccessPolicy.copy(enabled = enabled))
    }

    suspend fun toggleRequireApproval(enabled: Boolean) {
        mutateExternalAccess(externalAccessPolicy.copy(requireApproval = enabled))
    }

    suspend fun toggleOrchestra(enabled: Boolean) {
        mutateOrchestra(orchestraConfig.copy(enabled = enabled))
    }

    suspend fun toggleLanSpillover(enabled: Boolean) {
        mutateOrchestra(orchestraConfig.copy(allowLanSpillover = enabled))
    }

    suspend fun toggleAutoAssign(enabled: Boolean) {
        mutateOrchestra(orchestraConfig.copy(autoAssign = enabled))
    }

    suspend fun chooseChatModel(modelId: String) {
        val client = detectBackend() ?: return
        preferredModels = preferredModels.copy(chatModelId = modelId)
        client.updatePreferredModels(preferredModels)
        sessionTimeline.add(0, "Preferred chat model updated: ${models.firstOrNull { it.id == modelId }?.name ?: modelId}")
    }

    fun updateComposerPrompt(value: String) {
        composerState = composerState.copy(prompt = value)
    }

    fun updateComposerSystemPrompt(value: String) {
        composerState = composerState.copy(systemPrompt = value)
    }

    fun updateComposerMode(mode: ComposerMode) {
        composerState = composerState.copy(mode = mode)
    }

    fun chooseNode(nodeId: String) {
        composerState = composerState.copy(selectedNodeId = nodeId)
    }

    fun chooseSetupMode(mode: String) {
        setupPerformanceMode = mode
    }

    suspend fun sendPrompt() {
        if (composerState.prompt.isBlank()) return
        val client = detectBackend()
        if (client == null) {
            composerState = composerState.copy(lastResult = "Backend unavailable", generating = false)
            return
        }
        composerState = composerState.copy(generating = true)
        try {
            val selectedNode = lanNodes.firstOrNull { it.id == composerState.selectedNodeId } ?: lanNodes.firstOrNull()
            val result = if (composerState.mode == ComposerMode.ORCHESTRA && selectedNode != null) {
                client.lanExecute(
                    prompt = composerState.prompt,
                    hostHint = selectedNode.host,
                    remotePort = selectedNode.port,
                    systemPrompt = composerState.systemPrompt,
                    mode = composerState.mode
                )
            } else {
                client.chatGenerate(
                    prompt = composerState.prompt,
                    systemPrompt = composerState.systemPrompt,
                    mode = composerState.mode,
                    modelId = preferredModels.chatModelId
                )
            }
            composerState = composerState.copy(lastResult = result, generating = false, prompt = "")
            sessionTimeline.add(0, "Prompt executed in ${composerState.mode.name.lowercase()} mode")
        } catch (error: Exception) {
            composerState = composerState.copy(
                lastResult = error.message ?: "Execution failed",
                generating = false
            )
            sessionTimeline.add(0, "Execution failed: ${error.message.orEmpty()}")
        }
    }

    fun homeState(): HomeState {
        return HomeState(
            activeModelName = activeModelName(),
            backendAvailability = backendAvailability,
            statusSummary = statusSummary,
            runtimeBadges = runtimes.toList(),
            quickActions = listOf("Store", "Live AI", "Files", "Settings"),
            composerState = composerState,
            lanNodes = lanNodes.toList(),
            timeline = sessionTimeline.toList()
        )
    }

    fun storeState(): StoreState {
        val summary = when {
            catalog.isNotEmpty() -> "???????: ${catalog.size} | ???????????: ${models.size}"
            models.isNotEmpty() -> "????????? ??????: ${models.size}"
            else -> "???? ??? ???????. ???????? GGUF ??? ?????????? windows-core catalog."
        }
        return StoreState(
            summary = summary,
            detailState = ModelDetailState(
                installedModels = models.toList(),
                catalogCards = catalog.map { StoreCardState(it, expandedCatalogCards[it.id] == true) },
                currentTab = storeTab
            )
        )
    }

    fun liveState(): LiveState {
        return LiveState(
            availability = backendAvailability,
            activeModelName = activeModelName(),
            readiness = if (statusSummary.pairingTokenConfigured) "?????? ? live-routing" else "?? ????? pairing token",
            notes = if (catalog.any { it.liveEligible }) {
                "Live AI ?????????? ?? ?? ???????? ? ?????? ?????????? ??????????? runtime-???????????."
            } else {
                "??? Live AI ????? ?????? ? live-????????????? ??? projector asset."
            }
        )
    }

    fun filesState(): FilesState {
        return FilesState(
            workspacePath = backendAvailability.baseUrl.ifBlank { "????????? workspace / RAG path ???? ?? ???????????" },
            ragCount = statusSummary.ragCount,
            notes = sessionTimeline.take(6)
        )
    }

    fun settingsState(): SettingsState {
        return SettingsState(
            themePreset = themePreset,
            appLocale = locale,
            preferredModels = preferredModels,
            toolState = toolState,
            externalAccessPolicy = externalAccessPolicy,
            orchestraConfig = orchestraConfig,
            backendAvailability = backendAvailability,
            coreUrl = coreUrl,
            hubUrl = hubUrl
        )
    }

    private suspend fun mutateToolState(transform: ToolState.() -> ToolState) {
        val client = detectBackend() ?: return
        val updated = toolState.transform()
        client.updateToolState(updated)
        toolState = updated
    }

    private suspend fun mutateExternalAccess(policy: ExternalAccessPolicy) {
        val client = detectBackend() ?: return
        client.updateExternalAccess(policy)
        externalAccessPolicy = policy
    }

    private suspend fun mutateOrchestra(config: OrchestraConfig) {
        val client = detectBackend() ?: return
        client.updateOrchestra(config)
        orchestraConfig = config
    }

    private suspend fun <T> optionalCall(label: String, block: suspend () -> T): T? {
        return try {
            val result = block()
            if (optionalEndpointAvailability[label] == false) {
                sessionTimeline.add(0, "???????????? endpoint ${label} ????? ????????.")
            }
            optionalEndpointAvailability[label] = true
            result
        } catch (error: Exception) {
            if (optionalEndpointAvailability[label] != false) {
                sessionTimeline.add(0, "???????????? endpoint ${label} ??????????: ${error.message.orEmpty()}")
            }
            optionalEndpointAvailability[label] = false
            null
        }
    }

    private suspend fun detectBackend(): DesktopBackendClient? {
        val preferredCore = coreUrl.trim()
        val preferredHub = hubUrl.trim()
        val candidates = listOf(
            if (preferredCore.isNotBlank()) CoreBackendClient(preferredCore) else null,
            if (preferredHub.isNotBlank()) HubBackendClient(preferredHub) else null,
            if (preferredCore != "http://127.0.0.1:17861") CoreBackendClient("http://127.0.0.1:17861") else null,
            if (preferredHub != "http://127.0.0.1:17860") HubBackendClient("http://127.0.0.1:17860") else null
        ).filterNotNull()

        for (client in candidates.distinctBy { it.baseUrl + clientKind(it) }) {
            if (client.ping()) {
                return client
            }
        }
        return null
    }

    private suspend fun bootstrapLocalBackendIfNeeded() {
        if (detectBackend() != null) {
            return
        }

        repeat(6) {
            delay(1_000)
            if (detectBackend() != null) {
                sessionTimeline.add(0, "????????? backend ???????? ?? ????? ?????????? ????????.")
                return
            }
        }

        val launcher = backendLaunchCandidates().firstOrNull() ?: return
        val command = if (launcher.extension.equals("bat", ignoreCase = true)) {
            listOf("cmd.exe", "/c", launcher.absolutePath)
        } else {
            listOf(launcher.absolutePath)
        }

        try {
            ProcessBuilder(command)
                .directory(launcher.parentFile ?: File(System.getProperty("user.dir")))
                .start()
            sessionTimeline.add(0, "???????? ????????? backend: ${launcher.name}")
        } catch (error: Exception) {
            sessionTimeline.add(0, "Backend bootstrap failed: ${error.message.orEmpty()}")
            return
        }

        repeat(12) {
            delay(1_000)
            if (detectBackend() != null) {
                sessionTimeline.add(0, "????????? backend ???????? ????? bootstrap.")
                return
            }
        }
    }

    private fun backendLaunchCandidates(): List<File> {
        val envPaths = listOf(
            System.getenv("SANTIYA_STACK_LAUNCHER"),
            System.getenv("WINDOWS_CORE_EXE"),
            System.getenv("WINDOWS_HUB_EXE")
        ).mapNotNull { raw ->
            raw?.takeIf { it.isNotBlank() }?.let(::File)
        }

        val workingDir = File(System.getProperty("user.dir"))
        val bases = generateSequence(workingDir) { current ->
            current.parentFile?.takeIf { it != current }
        }.take(10).toList()

        val repoCandidates = bases.flatMap { base ->
            listOf(
                File(base, "windows-hub/run-windows-stack.bat"),
                File(base, "windows-core/dist/santiya-localai-core.exe"),
                File(base, "windows-hub/dist/native/SantiyaLocalAiHub Windows Hub/SantiyaLocalAiHub Windows Hub.exe")
            )
        }

        return (envPaths + repoCandidates)
            .distinctBy { it.absolutePath.lowercase(Locale.getDefault()) }
            .filter { it.isFile }
    }

    private fun hasPrimaryModel(): Boolean {
        return preferredModels.chatModelId.isNotBlank() || models.isNotEmpty()
    }

    private fun deriveSetupRecommendation(): SetupRecommendation {
        val existingChatModel = models.firstOrNull { it.capabilities.any { cap -> cap == "chat" } }
        if (existingChatModel != null) {
            return SetupRecommendation(
                title = existingChatModel.name,
                description = "?????? ??? ???????? ????????. ????? ????? ??????? ?? ??????? ????? ? ???????? projector asset ?????.",
                sizeLabel = "${existingChatModel.sizeMb} MB",
                installActionLabel = "??????? ??????? ?????",
                projectorTitle = "Projector / mmproj",
                projectorDescription = "????????? photo Q&A ? live vision ????? ????????? ????????? GGUF.",
                projectorActionLabel = "???????? ?????",
                warning = "???????? ?????? ??? ???????????.",
                status = "installed"
            )
        }
        return defaultSetupRecommendation()
    }

    private fun loadThemePreset(): ThemePreset =
        ThemePreset.entries.firstOrNull { it.name == preferences.get(KEY_THEME, ThemePreset.MARBLE_LILAC.name) }
            ?: ThemePreset.MARBLE_LILAC

    private fun loadLocale(): AppLocale =
        AppLocale.entries.firstOrNull { it.name == preferences.get(KEY_LOCALE, AppLocale.SYSTEM.name) }
            ?: AppLocale.SYSTEM

    private fun clientKind(client: DesktopBackendClient): String =
        when (client) {
            is CoreBackendClient -> "core"
            is HubBackendClient -> "hub"
            else -> "unknown"
        }

    companion object {
        private const val KEY_THEME = "themePreset"
        private const val KEY_LOCALE = "appLocale"
        private const val KEY_ONBOARDING_COMPLETE = "onboardingComplete"
        private const val KEY_TERMS_ACCEPTED = "termsAccepted"
        private const val KEY_CORE_URL = "coreUrl"
        private const val KEY_HUB_URL = "hubUrl"
    }
}

private fun <T> MutableList<T>.replaceWith(values: List<T>) {
    clear()
    addAll(values)
}

private fun defaultSetupRecommendation(): SetupRecommendation {
    return SetupRecommendation(
        title = "Gemma starter GGUF",
        description = "????????????? ????????? ??????? ??? Windows chat setup. ???????? ?????? ???????? ??????, projector asset ??????????? ????? ???.",
        sizeLabel = "~200 MB",
        installActionLabel = "?????? ?????? ??????",
        projectorTitle = "Gemma projector / mmproj",
        projectorDescription = "???? photo Q&A, VLM ? live camera surfaces ????? ????????? ????????? GGUF.",
        projectorActionLabel = "??????? ????? ???????? ??????",
        warning = "???????? Windows runtime ??? ??? ????? ????????? GGUF ??? ??????????? catalog source.",
        status = "manual_import_only"
    )
}
