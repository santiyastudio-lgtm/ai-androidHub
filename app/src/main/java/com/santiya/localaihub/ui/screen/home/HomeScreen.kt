package com.santiya.localaihub.ui.screen.home

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.santiya.localaihub.data.AppSettingsDataStore
import com.santiya.localaihub.ui.components.LocalCodeHighlightEnabled
import com.santiya.localaihub.viewmodel.ChatViewModel
import com.santiya.localaihub.viewmodel.LLMModelViewModel
import kotlinx.coroutines.launch

// в”Ђв”Ђ HomeScreen в”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђ

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun HomeScreen(
    onStoreButtonClicked: () -> Unit,
    onFilesClick: () -> Unit,
    onLiveClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onDeveloperClick: () -> Unit,
    onVaultManagerClick: () -> Unit,
    onImageGenSetupNeeded: () -> Unit,
    chatViewModel: ChatViewModel,
    llmModelViewModel: LLMModelViewModel
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val appSettings = remember { AppSettingsDataStore(context) }
    val codeHighlightEnabled by appSettings.codeHighlightEnabled
        .collectAsStateWithLifecycle(initialValue = true)
    val toolCallingEnabled by appSettings.toolCallingEnabled
        .collectAsStateWithLifecycle(initialValue = true)
    val chatUiState by chatViewModel.chatUiState.collectAsStateWithLifecycle()
    val chatConfigState by chatViewModel.chatConfigState.collectAsStateWithLifecycle()
    val installedModels by llmModelViewModel.installedModels.collectAsStateWithLifecycle(initialValue = emptyList())
    val currentModelId by llmModelViewModel.currentModelID.collectAsStateWithLifecycle()
    val currentModelName = installedModels.firstOrNull { it.id == currentModelId }?.modelName

    // Navigate to QNN setup when a diffusion model needs it
    val needsQnnSetup by llmModelViewModel.needsQnnSetup.collectAsStateWithLifecycle()
    LaunchedEffect(needsQnnSetup) {
        if (needsQnnSetup) onImageGenSetupNeeded()
    }

    // Offer to reload the last loaded model on startup
    val lastModelOffer by llmModelViewModel.lastModelOffer.collectAsStateWithLifecycle()
    lastModelOffer?.let { model ->
        ReloadModelDialog(
            modelName = model.modelName,
            modelType = model.providerType,
            onConfirm = { llmModelViewModel.acceptLastModelOffer() },
            onDismiss = { llmModelViewModel.dismissLastModelOffer() }
        )
    }

    CompositionLocalProvider(LocalCodeHighlightEnabled provides codeHighlightEnabled) {
    ModalNavigationDrawer(
        drawerState = drawerState, drawerContent = {
            ModalDrawerSheet {
                HomeDrawerScreen(
                    onVaultManagerClick = onVaultManagerClick,
                    onFilesClick = onFilesClick,
                    onChatSelected = {
                        chatViewModel.loadChat(it)
                        scope.launch {
                            drawerState.close()
                        }
                    },
                    chatViewModel = chatViewModel
                )
            }
        }) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopBar(
                    onStoreButtonClicked = onStoreButtonClicked,
                    onMenuClick = { scope.launch { drawerState.open() } },
                    onSettingsClick = onSettingsClick,
                    onDeveloperClick = onDeveloperClick,
                    showDynamicWindow = { chatViewModel.showDynamicWindow() },
                    currentModelName = currentModelName,
                    isGenerating = chatUiState.isGenerating,
                    isDynamicWindowVisible = chatConfigState.showDynamicWindow
                )
            },
            bottomBar = {
                BottomBar(
                    chatViewModel = chatViewModel,
                    llmModelViewModel = llmModelViewModel,
                    toolCallingEnabled = toolCallingEnabled
                )
            }) { paddingValues ->
            BodyContent(
                paddingValues = paddingValues,
                chatViewModel = chatViewModel,
                llmModelViewModel = llmModelViewModel,
                onStoreClick = onStoreButtonClicked,
                onFilesClick = onFilesClick,
                onLiveClick = onLiveClick
            )
        }
    }
    } // CompositionLocalProvider
}
