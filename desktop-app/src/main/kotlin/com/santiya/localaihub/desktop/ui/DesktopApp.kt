package com.santiya.localaihub.desktop.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.santiya.localaihub.desktop.design.HubIcons
import com.santiya.localaihub.desktop.design.MapleMonoFontFamily
import com.santiya.localaihub.desktop.design.Motion
import com.santiya.localaihub.desktop.design.SantiyaDesktopTheme
import com.santiya.localaihub.desktop.design.Standards
import com.santiya.localaihub.desktop.state.AppLocale
import com.santiya.localaihub.desktop.state.ComposerMode
import com.santiya.localaihub.desktop.state.DesktopAppStore
import com.santiya.localaihub.desktop.state.ShellRoute
import com.santiya.localaihub.desktop.state.StoreTab
import com.santiya.localaihub.desktop.state.ThemePreset
import com.santiya.localaihub.desktop.state.UtilityWindowType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun DesktopApp(store: DesktopAppStore) {
    SantiyaDesktopTheme(themePreset = store.themePreset) {
        LaunchedEffect(store.coreUrl, store.hubUrl) {
            while (true) {
                store.refreshAll()
                delay(3_000)
            }
        }

        val strings = store.resolvedStrings()
        val scrollState = rememberScrollState()
        AppBackdrop {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 30.dp, vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                ProductCanvas(
                    modifier = Modifier
                        .width(1120.dp)
                        .fillMaxHeight()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(horizontal = 28.dp, vertical = 26.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        val shellVisible = store.route.ordinal >= ShellRoute.HOME.ordinal
                        if (shellVisible) {
                            AppTopShell(store = store, onToggleAi = {
                                store.openUtility(UtilityWindowType.RUNTIME)
                            })
                            PrimaryNavigation(store = store, strings = strings)
                        }
                        AnimatedContent(
                            targetState = store.route,
                            transitionSpec = { fadeIn(Motion.entrance()) togetherWith fadeOut(Motion.exit()) },
                            label = "route_transition"
                        ) { route ->
                            when (route) {
                                ShellRoute.GUIDE -> GuideScreen(strings = strings, onContinue = store::completeGuide)
                                ShellRoute.TERMS -> TermsScreen(
                                    strings = strings,
                                    scrolledToEnd = store.termsScrolledToEnd,
                                    onScrolled = store::updateTermsScrolledToEnd,
                                    onAccept = store::acceptTerms
                                )
                                ShellRoute.SETUP -> SetupScreen(store = store, strings = strings)
                                ShellRoute.HOME -> HomeScreen(store = store)
                                ShellRoute.STORE -> StoreScreen(store = store, strings = strings)
                                ShellRoute.LIVE -> LiveScreen(store = store, strings = strings)
                                ShellRoute.FILES -> FilesScreen(store = store, strings = strings)
                                ShellRoute.SETTINGS -> SettingsScreen(store = store, strings = strings)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppTopShell(
    store: DesktopAppStore,
    onToggleAi: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Standards.SpacingSm), verticalAlignment = Alignment.CenterVertically) {
            ActionIconButton(HubIcons.Menu, "Menu") { store.openUtility(UtilityWindowType.SESSIONS) }
            ActionIconButton(HubIcons.Settings, "Settings") { store.navigate(ShellRoute.SETTINGS) }
            ActionIconButton(HubIcons.Code, "Developer") { store.openUtility(UtilityWindowType.DEVELOPER) }
        }
        DynamicIslandPill(
            currentModelName = store.activeModelName().ifBlank { null },
            isGenerating = store.composerState.generating,
            isExpanded = store.isUtilityWindowOpen(UtilityWindowType.RUNTIME),
            onClick = onToggleAi
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Standards.SpacingSm), verticalAlignment = Alignment.CenterVertically) {
            ActionIconButton(HubIcons.Download, "Store") { store.navigate(ShellRoute.STORE) }
            ActionIconButton(HubIcons.Upload, "Import") { store.openUtility(UtilityWindowType.NODES) }
            ActionIconButton(HubIcons.Refresh, "Refresh") {}
        }
    }
}

@Composable
private fun PrimaryNavigation(
    store: DesktopAppStore,
    strings: com.santiya.localaihub.desktop.state.DesktopStrings
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        listOf(
            ShellRoute.HOME to strings.homeTitle,
            ShellRoute.STORE to strings.storeTitle,
            ShellRoute.LIVE to strings.liveTitle,
            ShellRoute.FILES to strings.filesTitle,
            ShellRoute.SETTINGS to strings.settingsTitle
        ).forEach { (route, label) ->
            NavigationPill(
                label = label,
                selected = store.route == route,
                onClick = { store.navigate(route) }
            )
        }
    }
}

@Composable
private fun GuideScreen(
    strings: com.santiya.localaihub.desktop.state.DesktopStrings,
    onContinue: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 70.dp, bottom = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        Box(
            modifier = Modifier
                .size(92.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(HubIcons.Sparkles, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(42.dp))
        }
        HeroCard(
            title = strings.guideTitle,
            body = strings.guideBody,
            badge = "ANDROID-FIRST WINDOWS SHELL",
            modifier = Modifier.fillMaxWidth(0.82f)
        )
        PrimaryCtaButton(
            label = strings.continueLabel,
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth(0.42f)
        )
    }
}

@Composable
private fun TermsScreen(
    strings: com.santiya.localaihub.desktop.state.DesktopStrings,
    scrolledToEnd: Boolean,
    onScrolled: (Boolean) -> Unit,
    onAccept: () -> Unit
) {
    val scrollState = rememberScrollState()
    LaunchedEffect(scrollState.value, scrollState.maxValue) {
        onScrolled(scrollState.value >= scrollState.maxValue - 80 || scrollState.maxValue == 0)
    }
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        SectionTitle(strings.termsTitle, strings.termsSubtitle)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(520.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            listOf(
                "1. Local control" to "Все модели, LAN-узлы, tool calling и внешние интеграции должны быть понятны и управляемы из интерфейса.",
                "2. Honest state" to "Если runtime или capability отсутствует, приложение обязано показывать `Requires windows-core`, `Manual import only` или `Planned / provider-needed`.",
                "3. Critical review" to "Медицинские, юридические, финансовые и security-решения нельзя принимать без отдельной проверки.",
                "4. External access" to "Внешний доступ, web search и orchestration остаются явными toggles, а не скрытыми фоновыми возможностями.",
                "5. Model ownership" to "Main model и projector asset всегда отображаются раздельно: это важный UX-контракт Android-приложения."
            ).forEach { (title, body) ->
                GlassCard {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        PrimaryCtaButton(
            label = if (scrolledToEnd) strings.acceptLabel else strings.scrollToEndLabel,
            onClick = onAccept,
            enabled = scrolledToEnd,
            modifier = Modifier.fillMaxWidth(0.44f)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SetupScreen(
    store: DesktopAppStore,
    strings: com.santiya.localaihub.desktop.state.DesktopStrings
) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        HeroCard(
            title = strings.setupTitle,
            body = strings.setupBody,
            badge = "SETUP / RECOMMENDATION / PERFORMANCE",
            modifier = Modifier.fillMaxWidth()
        )
        GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Gemma recommendation", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(store.setupRecommendation.description, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusChip(label = store.setupRecommendation.sizeLabel, icon = HubIcons.Download)
                    StatusChip(label = store.setupRecommendation.status.replace('_', ' '), icon = HubIcons.AlertTriangle)
                    store.setupRecommendation.warning?.let {
                        StatusChip(label = it, icon = HubIcons.AlertTriangle, container = MaterialTheme.colorScheme.errorContainer, content = MaterialTheme.colorScheme.error)
                    }
                }
                GlassCard(accent = MaterialTheme.colorScheme.secondary) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(store.setupRecommendation.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(store.setupRecommendation.installActionLabel, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                        store.setupRecommendation.projectorTitle?.let { projectorTitle ->
                            Spacer(Modifier.height(4.dp))
                            Text(projectorTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                store.setupRecommendation.projectorDescription.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                store.setupRecommendation.projectorActionLabel.orEmpty(),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }
        }
        SectionTitle("Setup scenarios", "??????? option cards ????????? Android flow, ? ?? desktop radio-form.")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SetupOptionCard(HubIcons.Sparkles, "??????? ???", "Starter local model | ????? 200 MB")
            SetupOptionCard(HubIcons.Volume, "??? + ???????", "Base model ? Piper-compatible voice")
            SetupOptionCard(HubIcons.Photo, "????????? ???????????", "Image runtime / QNN or diffusion provider")
            SetupOptionCard(HubIcons.Bolt, "??????? ?????", "Open the shell without downloads")
        }
        GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Performance mode", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf("maximum", "balanced", "economy").forEach { mode ->
                        NavigationPill(
                            label = mode.replaceFirstChar(Char::uppercase),
                            selected = store.setupPerformanceMode == mode,
                            onClick = { store.chooseSetupMode(mode) }
                        )
                    }
                }
                Text("Detected hardware profile is routed into the same setup language as Android: explicit speed, balance and battery trade-offs.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Restore from backup", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Migration and backup restore stay visible in setup instead of disappearing into Settings.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                PrimaryCtaButton("Continue to shell", onClick = store::finishSetup, modifier = Modifier.fillMaxWidth(0.34f))
            }
        }
    }
}

@Composable
private fun SetupOptionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    GlassCard(modifier = Modifier.width(248.dp), accent = MaterialTheme.colorScheme.primary) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            }
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HomeScreen(store: DesktopAppStore) {
    val state = store.homeState()
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        HeroCard(
            title = state.activeModelName.ifBlank { "???????? ???????? ??????" },
            body = if (state.activeModelName.isBlank()) {
                "Home ?????? ???? ????? ? Setup ??? Store, ???? preferred model ??? ?? ?????????."
            } else {
                "Windows shell ?????? ??? ?? Android-first ???????: model pill, quick actions, runtime strip ? unified composer."
            },
            badge = state.backendAvailability.message.ifBlank { "LOCAL CONTROL PLANE" },
            modifier = Modifier.fillMaxWidth()
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard("Models", state.statusSummary.modelCount.toString(), HubIcons.Download)
            MetricCard("Nodes", state.lanNodes.size.toString(), HubIcons.Link)
            MetricCard("Tools", state.statusSummary.toolCount.toString(), HubIcons.Code)
            MetricCard("RAG", state.statusSummary.ragCount.toString(), HubIcons.Books)
        }
        GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Runtime strip", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusChip(label = if (state.statusSummary.pairingTokenConfigured) "pairing token configured" else "pairing token missing", icon = HubIcons.Shield)
                    StatusChip(label = if (state.statusSummary.lanEnabled) "LAN enabled" else "LAN disabled", icon = HubIcons.Link)
                    StatusChip(label = if (state.statusSummary.orchestraEnabled) "orchestra ready" else "orchestra off", icon = HubIcons.Brain)
                    StatusChip(label = if (state.statusSummary.toolCallingEnabled) "tool calling on" else "tool calling off", icon = HubIcons.Code)
                }
                if (state.runtimeBadges.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.runtimeBadges.take(6).forEach { badge ->
                            StatusChip(label = "${badge.name}: ${badge.status}", icon = HubIcons.Cpu)
                        }
                    }
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            QuickActionCard("Store", "Open model inventory and sources", HubIcons.Download) { store.navigate(ShellRoute.STORE) }
            QuickActionCard("Live AI", "Camera and live routing states", HubIcons.Photo) { store.navigate(ShellRoute.LIVE) }
            QuickActionCard("Files", "RAG, memory and workspace paths", HubIcons.Folder) { store.navigate(ShellRoute.FILES) }
            QuickActionCard("Settings", "Themes, orchestration and access policy", HubIcons.Settings) { store.navigate(ShellRoute.SETTINGS) }
        }
        ComposerCard(store)
        if (state.timeline.isNotEmpty()) {
            GlassCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Session timeline", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    state.timeline.take(6).forEach { entry ->
                        Text(entry, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricCard(title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    GlassCard(modifier = Modifier.width(180.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            }
            Column {
                Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun QuickActionCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    GlassCard(
        modifier = Modifier
            .width(248.dp)
            .clickable(onClick = onClick)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ComposerCard(store: DesktopAppStore) {
    val composer = store.composerState
    val nodes = store.lanNodes.toList()
    val scope = rememberCoroutineScope()
    GlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Unified composer", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Text, mode chips, system prompt and LAN target live in one rounded block like on Android.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    ComposerMode.NORMAL to "???????",
                    ComposerMode.THINKING to "???????????",
                    ComposerMode.ORCHESTRA to "???????"
                ).forEach { (mode, label) ->
                    NavigationPill(label = label, selected = composer.mode == mode, onClick = { store.updateComposerMode(mode) })
                }
            }
            if (nodes.isNotEmpty()) {
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                    OutlinedTextField(
                        value = nodes.firstOrNull { it.id == composer.selectedNodeId }?.name
                            ?: nodes.firstOrNull()?.name.orEmpty(),
                        onValueChange = {},
                        label = { Text("LAN node") },
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        nodes.forEach { node ->
                            DropdownMenuItem(
                                text = { Text("${node.name} | ${node.status}") },
                                onClick = {
                                    store.chooseNode(node.id)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
            OutlinedTextField(
                value = composer.systemPrompt,
                onValueChange = store::updateComposerSystemPrompt,
                label = { Text("System prompt") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = composer.prompt,
                onValueChange = store::updateComposerPrompt,
                label = { Text("Prompt") },
                modifier = Modifier.fillMaxWidth().height(140.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                PrimaryCtaButton(
                    label = if (composer.generating) "Running..." else "Send",
                    enabled = !composer.generating,
                    modifier = Modifier.fillMaxWidth(0.26f),
                    onClick = { scope.launch { store.sendPrompt() } }
                )
                ActionIconButton(HubIcons.Photo, "Attachment") {}
                ActionIconButton(HubIcons.Upload, "File") {}
                ActionIconButton(HubIcons.Books, "RAG") { store.navigate(ShellRoute.FILES) }
            }
            if (composer.lastResult.isNotBlank()) {
                GlassCard(accent = MaterialTheme.colorScheme.secondary) {
                    Text(composer.lastResult, style = MaterialTheme.typography.bodyMedium, fontFamily = com.santiya.localaihub.desktop.design.MapleMonoFontFamily)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StoreScreen(
    store: DesktopAppStore,
    strings: com.santiya.localaihub.desktop.state.DesktopStrings
) {
    val state = store.storeState()
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            ActionIconButton(HubIcons.ArrowLeft, "Back") { store.navigate(ShellRoute.HOME) }
            SectionTitle(strings.storeTitle, state.summary, modifier = Modifier.weight(1f))
            ActionIconButton(HubIcons.Adjustments, "Filters") {}
            ActionIconButton(HubIcons.Refresh, "Refresh") {}
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(StoreTab.CATALOG to "???????", StoreTab.INSTALLED to "???????????", StoreTab.SOURCES to "?????????").forEach { (tab, label) ->
                NavigationPill(label = label, selected = store.storeTab == tab, onClick = { store.selectStoreTab(tab) })
            }
        }
        when (store.storeTab) {
            StoreTab.CATALOG -> {
                if (state.detailState.catalogCards.isEmpty()) {
                    EmptyStateCard(
                        title = "Catalog is empty",
                        body = "Current runtime does not provide a remote catalog yet. The UI stays honest and keeps the Android store structure instead of faking download sources.",
                        actionLabel = "Open Settings",
                        onAction = { store.navigate(ShellRoute.SETTINGS) }
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        state.detailState.catalogCards.forEach { card ->
                            CatalogCard(card = card, onToggle = { store.toggleCatalogCard(card.entry.id) })
                        }
                    }
                }
            }

            StoreTab.INSTALLED -> {
                if (state.detailState.installedModels.isEmpty()) {
                    EmptyStateCard(
                        title = "No installed models",
                        body = "Import GGUF into the models directory or connect windows-core with a catalog source.",
                        actionLabel = "Go to Setup",
                        onAction = { store.navigate(ShellRoute.SETUP) }
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        state.detailState.installedModels.forEach { model ->
                            GlassCard {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(model.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    Text("${model.modelType} | ${model.runtime} | ${model.sizeMb} MB", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        model.capabilities.forEach { cap -> StatusChip(label = cap, icon = HubIcons.Cpu) }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            StoreTab.SOURCES -> {
                GlassCard {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Sources", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("Installed, downloads and sources stay visually inside the same store system. Right now this runtime exposes local models and provider-needed catalog slots.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        StatusChip(label = store.backendAvailability.message, icon = HubIcons.World)
                        StatusChip(label = if (store.backendAvailability.advancedFeaturesAvailable) "advanced api online" else "manual import only", icon = HubIcons.Download)
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogCard(
    card: com.santiya.localaihub.desktop.state.StoreCardState,
    onToggle: () -> Unit
) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(
                    modifier = Modifier
                        .size(140.dp, 116.dp)
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.09f)
                                )
                            ),
                            RoundedCornerShape(24.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(HubIcons.Brain, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(42.dp))
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusChip(label = card.entry.taskLabel, icon = HubIcons.Sparkles)
                        StatusChip(label = card.entry.sourceLabel, icon = HubIcons.World)
                        card.entry.ramEstimateMb?.let { StatusChip(label = "${it} MB", icon = HubIcons.Database) }
                    }
                    Text(card.entry.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(card.entry.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (card.expanded) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusChip(label = card.entry.supportStatus.replace('_', ' '), icon = HubIcons.Shield)
                    StatusChip(label = card.entry.downloadability.replace('_', ' '), icon = HubIcons.Download)
                    card.entry.tags.forEach { tag -> StatusChip(label = tag, icon = HubIcons.Cpu) }
                }
                card.entry.warnings.forEach { warning ->
                    GlassCard(accent = MaterialTheme.colorScheme.error) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(warning.title, fontWeight = FontWeight.SemiBold)
                            Text(warning.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveScreen(
    store: DesktopAppStore,
    strings: com.santiya.localaihub.desktop.state.DesktopStrings
) {
    val state = store.liveState()
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        SectionTitle(strings.liveTitle, "Camera/live states are first-class, not hidden behind developer utilities.")
        HeroCard(
            title = state.activeModelName.ifBlank { "Live AI needs a model or projector asset" },
            body = state.notes,
            badge = state.readiness,
            modifier = Modifier.fillMaxWidth()
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            GlassCard(modifier = Modifier.width(320.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Mode selector", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NavigationPill("Camera", true) {}
                        NavigationPill("Photo Q&A", false) {}
                        NavigationPill("LAN Relay", false) {}
                    }
                }
            }
            GlassCard(modifier = Modifier.weight(1f, fill = false).width(620.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.56f), RoundedCornerShape(28.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(HubIcons.Photo, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(38.dp))
                            Text("Camera preview placeholder", fontWeight = FontWeight.SemiBold)
                            Text("The screen stays honest until a live-capable runtime is wired.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilesScreen(
    store: DesktopAppStore,
    strings: com.santiya.localaihub.desktop.state.DesktopStrings
) {
    val state = store.filesState()
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        SectionTitle(strings.filesTitle, "RAG, memory and attachments stay inside the same rounded operational grammar.")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            GlassCard(modifier = Modifier.width(320.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("RAG state", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Installed knowledge packs: ${state.ragCount}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    StatusChip(label = if (state.ragCount > 0) "ready" else "manual import only", icon = HubIcons.Books)
                }
            }
            GlassCard(modifier = Modifier.width(320.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Workspace paths", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(state.workspacePath, fontFamily = com.santiya.localaihub.desktop.design.MapleMonoFontFamily, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            GlassCard(modifier = Modifier.width(320.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Recent notes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (state.notes.isEmpty()) {
                        Text("No RAG or session notes yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        state.notes.forEach { note -> Text(note, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    store: DesktopAppStore,
    strings: com.santiya.localaihub.desktop.state.DesktopStrings
) {
    val state = store.settingsState()
    val scope = rememberCoroutineScope()
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        SectionTitle(strings.settingsTitle, "Theme, locale, access policy and orchestration stay product-styled, not plain forms.")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            GlassCard(modifier = Modifier.width(320.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Appearance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    EnumDropdown("Theme", state.themePreset, ThemePreset.entries.toList(), store::updateTheme)
                    EnumDropdown("Locale", state.appLocale, AppLocale.entries.toList(), store::updateLocale)
                }
            }
            GlassCard(modifier = Modifier.width(320.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Backends", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(value = state.coreUrl, onValueChange = store::updateCoreUrl, label = { Text("windows-core URL") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = state.hubUrl, onValueChange = store::updateHubUrl, label = { Text("windows-hub URL") }, modifier = Modifier.fillMaxWidth())
                    Text(state.backendAvailability.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            GlassCard(modifier = Modifier.width(320.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Preferred chat model", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                        OutlinedTextField(
                            value = store.models.firstOrNull { it.id == state.preferredModels.chatModelId }?.name
                                ?: store.activeModelName(),
                            onValueChange = {},
                            label = { Text("Chat model") },
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            store.models.forEach { model ->
                                DropdownMenuItem(
                                    text = { Text(model.name) },
                                    onClick = {
                                        expanded = false
                                        scope.launch { store.chooseChatModel(model.id) }
                                    }
                                )
                            }
                        }
                    }
                    if (store.models.isNotEmpty()) {
                        PrimaryCtaButton(
                            label = "Use first installed model",
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                val first = store.models.firstOrNull()?.id ?: return@PrimaryCtaButton
                                scope.launch { store.chooseChatModel(first) }
                            }
                        )
                    }
                }
            }
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ToggleCard(
                title = "External access",
                body = "Loopback by default, explicit enable and approval policy for remote actions.",
                checked = state.externalAccessPolicy.enabled,
                onCheckedChange = { scope.launch { store.toggleExternalAccess(it) } }
            )
            ToggleCard(
                title = "Require approval",
                body = "Keep high-risk network/tool actions explicit.",
                checked = state.externalAccessPolicy.requireApproval,
                onCheckedChange = { scope.launch { store.toggleRequireApproval(it) } }
            )
            ToggleCard(
                title = "Orchestra",
                body = "Enable LAN spillover and distributed routing surfaces.",
                checked = state.orchestraConfig.enabled,
                onCheckedChange = { scope.launch { store.toggleOrchestra(it) } }
            )
            ToggleCard(
                title = "LAN spillover",
                body = "Allow chat execution to route into mobile nodes.",
                checked = state.orchestraConfig.allowLanSpillover,
                onCheckedChange = { scope.launch { store.toggleLanSpillover(it) } }
            )
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ToggleCard(
                title = "Web search",
                body = "Built-in plugin with explicit toggle.",
                checked = state.toolState.webSearchEnabled,
                onCheckedChange = { scope.launch { store.toggleWebSearch(it) } }
            )
            state.toolState.enabledPlugins.sorted().forEach { pluginName ->
                ToggleCard(
                    title = pluginName,
                    body = "Built-in plugin available through windows-core tool routing.",
                    checked = true,
                    onCheckedChange = { enabled -> scope.launch { store.togglePlugin(pluginName, enabled) } }
                )
            }
        }

        GlassCard {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryCtaButton("Nodes") { store.openUtility(UtilityWindowType.NODES) }
                PrimaryCtaButton("Sessions") { store.openUtility(UtilityWindowType.SESSIONS) }
                PrimaryCtaButton("Logs") { store.openUtility(UtilityWindowType.LOGS) }
                PrimaryCtaButton("Developer/API") { store.openUtility(UtilityWindowType.DEVELOPER) }
                PrimaryCtaButton("Runtime") { store.openUtility(UtilityWindowType.RUNTIME) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T : Enum<T>> EnumDropdown(
    label: String,
    value: T,
    options: List<T>,
    onChange: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = value.name.replace('_', ' '),
            onValueChange = {},
            label = { Text(label) },
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.name.replace('_', ' ')) },
                    onClick = {
                        onChange(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun ToggleCard(
    title: String,
    body: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    GlassCard(modifier = Modifier.width(254.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Switch(checked = checked, onCheckedChange = onCheckedChange)
            }
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun UtilityWindowContent(
    type: UtilityWindowType,
    store: DesktopAppStore
) {
    SantiyaDesktopTheme(themePreset = store.themePreset) {
        AppBackdrop {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (type) {
                    UtilityWindowType.NODES -> UtilityNodesWindow(store)
                    UtilityWindowType.SESSIONS -> UtilitySessionsWindow(store)
                    UtilityWindowType.LOGS -> UtilityLogsWindow(store)
                    UtilityWindowType.DEVELOPER -> UtilityDeveloperWindow(store)
                    UtilityWindowType.RUNTIME -> UtilityRuntimeWindow(store)
                }
            }
        }
    }
}

@Composable
private fun UtilityNodesWindow(store: DesktopAppStore) {
    SectionTitle("Node Farm", "Desktop-dense peer monitoring with the same rounded/glow grammar.")
    if (store.lanNodes.isEmpty()) {
        EmptyStateCard("No LAN nodes yet", "Turn on LAN mode on the phone and use the same pairing token.", "Open Settings") {
            store.navigate(ShellRoute.SETTINGS)
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            store.lanNodes.forEach { node ->
                GlassCard {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(node.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("${node.platform} • ${node.status} • ${node.transportSummary}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            node.capabilities.forEach { cap -> StatusChip(label = cap, icon = HubIcons.Cpu) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UtilitySessionsWindow(store: DesktopAppStore) {
    SectionTitle("Sessions", "Operational activity timeline and shell actions.")
    GlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (store.sessionTimeline.isEmpty()) {
                Text("No sessions yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                store.sessionTimeline.forEach { entry ->
                    Text(entry, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun UtilityLogsWindow(store: DesktopAppStore) {
    SectionTitle("Logs", "Runtime and UI feedback in a mono technical surface.")
    GlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Backend: ${store.backendAvailability.baseUrl.ifBlank { "offline" }}", fontFamily = MapleMonoFontFamily)
            Text("Last error: ${store.lastError.ifBlank { "none" }}", fontFamily = MapleMonoFontFamily)
            Text("Composer mode: ${store.composerState.mode}", fontFamily = MapleMonoFontFamily)
            Text("Timeline entries: ${store.sessionTimeline.size}", fontFamily = MapleMonoFontFamily)
        }
    }
}

@Composable
private fun UtilityDeveloperWindow(store: DesktopAppStore) {
    SectionTitle("Developer/API", "Explicit backend surfaces, no hidden diagnostics.")
    GlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                "/api/status",
                "/api/models",
                "/api/catalog",
                "/api/runtimes",
                "/api/plugins",
                "/api/tools",
                "/api/tool-state",
                "/api/preferred-models",
                "/api/external-access",
                "/api/orchestra",
                "/api/lan/nodes",
                "/api/chat/generate",
                "/api/lan/execute"
            ).forEach { endpoint ->
                Text("${store.backendAvailability.baseUrl}$endpoint", fontFamily = MapleMonoFontFamily)
            }
        }
    }
}

@Composable
private fun UtilityRuntimeWindow(store: DesktopAppStore) {
    SectionTitle("Runtime Monitor", "Runtimes, plugins and capability gaps.")
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (store.runtimes.isNotEmpty()) {
            GlassCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Runtimes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    store.runtimes.forEach { runtime ->
                        Text("${runtime.name}: ${runtime.status} • ${runtime.summary}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (store.plugins.isNotEmpty()) {
            GlassCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Plugins", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    store.plugins.forEach { plugin ->
                        Text("${plugin.name} • ${plugin.version} • ${if (plugin.enabled) "enabled" else "disabled"}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (store.tools.isNotEmpty()) {
            GlassCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Tools", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    store.tools.take(12).forEach { tool ->
                        Text("${tool.pluginName} / ${tool.toolName}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
