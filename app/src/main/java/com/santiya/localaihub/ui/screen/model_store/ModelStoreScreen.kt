package com.santiya.localaihub.ui.screen.model_store

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.santiya.localaihub.ui.components.ActionButton
import com.santiya.localaihub.ui.icons.TnIcons
import com.santiya.localaihub.ui.theme.Motion
import com.santiya.localaihub.viewmodel.ModelStoreViewModel

enum class StoreTab {
    MODELS, INSTALLED, SETTINGS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelStoreScreen(
    onNavigateBack: () -> Unit,
    viewModel: ModelStoreViewModel = hiltViewModel(),
) {
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val models by viewModel.filteredModels.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val downloadStates by viewModel.downloadStates.collectAsStateWithLifecycle()
    val installedModels by viewModel.installedModels.collectAsStateWithLifecycle()
    val deviceInfo by viewModel.deviceInfo.collectAsStateWithLifecycle()
    val deleteInProgress by viewModel.deleteInProgress.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var showDiscoverySheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Магазин моделей") },
                navigationIcon = {
                    ActionButton(
                        onClickListener = onNavigateBack,
                        icon = TnIcons.ArrowLeft,
                        contentDescription = "Назад"
                    )
                },
                actions = {
                    if (selectedTab == StoreTab.MODELS) {
                        ActionButton(
                            onClickListener = { showDiscoverySheet = true },
                            icon = TnIcons.Adjustments,
                            contentDescription = "Поиск и фильтры"
                        )
                        ActionButton(
                            onClickListener = { viewModel.refreshModels() },
                            icon = TnIcons.Refresh,
                            contentDescription = "Обновить"
                        )
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            SecondaryTabRow(
                selectedTabIndex = selectedTab.ordinal,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                listOf(
                    StoreTab.MODELS to "Каталог",
                    StoreTab.INSTALLED to "Установлено",
                    StoreTab.SETTINGS to "Источники"
                ).forEach { (tab, label) ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { viewModel.selectTab(tab) },
                        text = {
                            Text(
                                text = label,
                                fontWeight = if (selectedTab == tab) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = { fadeIn(Motion.state()) togetherWith fadeOut(Motion.state()) },
                label = "store_tab_content"
            ) { tab ->
                when (tab) {
                    StoreTab.MODELS -> ModelsTab(
                        models = models,
                        isLoading = isLoading,
                        error = error,
                        downloadStates = downloadStates,
                        installedModelIds = installedModels.map { it.id }.toSet(),
                        deviceInfo = deviceInfo,
                        viewModel = viewModel,
                        onDownload = { viewModel.downloadModel(it) },
                        onCancelDownload = { modelId -> viewModel.cancelDownload(modelId) },
                        onRetry = { viewModel.loadModels() }
                    )

                    StoreTab.INSTALLED -> InstalledModelsTab(
                        models = installedModels,
                        deleteInProgress = deleteInProgress,
                        onDelete = { viewModel.deleteModel(it) },
                        viewModel = viewModel
                    )

                    StoreTab.SETTINGS -> SettingsTab(
                        deviceInfo = deviceInfo,
                        viewModel = viewModel
                    )
                }
            }
        }

        if (selectedTab == StoreTab.MODELS && showDiscoverySheet) {
            ModelDiscoveryBottomSheet(
                searchQuery = searchQuery,
                onSearchQueryChange = {
                    searchQuery = it
                    viewModel.filterModels(it)
                },
                onDismiss = { showDiscoverySheet = false },
                onClearSearch = {
                    searchQuery = ""
                    viewModel.clearAllFilters()
                },
                viewModel = viewModel
            )
        }
    }
}
