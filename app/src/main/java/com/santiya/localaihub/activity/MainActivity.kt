package com.santiya.localaihub.activity

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.santiya.localaihub.data.AppSettingsDataStore
import com.santiya.localaihub.data.SetupDataStore
import com.santiya.localaihub.data.TermsDataStore
import com.santiya.localaihub.data.VaultManager
import com.santiya.localaihub.di.AppContainer
import com.santiya.localaihub.hub.ThemePreset
import com.santiya.localaihub.models.enums.ProviderType
import com.santiya.localaihub.ui.screen.gate.VaultGateScreen
import com.santiya.localaihub.ui.screen.developer.DeveloperApiScreen
import com.santiya.localaihub.ui.screen.files.FilesWorkspaceScreen
import com.santiya.localaihub.ui.screen.guide.GuideScreen
import com.santiya.localaihub.ui.screen.guide.TermsAndConditionsScreen
import com.santiya.localaihub.ui.screen.home.HomeScreen
import com.santiya.localaihub.ui.screen.live.LiveAiScreen
import com.santiya.localaihub.ui.screen.memory.AiMemoryScreen
import com.santiya.localaihub.ui.screen.memory.VaultDashboard
import com.santiya.localaihub.ui.screen.model_config.ModelConfigEditorScreen
import com.santiya.localaihub.ui.screen.model_store.ModelStoreScreen
import com.santiya.localaihub.ui.screen.settings.SettingsScreen
import com.santiya.localaihub.ui.screen.setup.ImageGenSetupScreen
import com.santiya.localaihub.ui.screen.setup.SetupScreen
import com.santiya.localaihub.viewModel.VaultGateViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.santiya.localaihub.ui.theme.SantiyaLocalAiHubTheme
import com.santiya.localaihub.viewmodel.ChatViewModel
import com.santiya.localaihub.viewmodel.LLMModelViewModel
import com.santiya.localaihub.worker.LlmModelWorker
import com.santiya.localaihub.worker.NotificationPermissionHelper
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.santiya.localaihub.global.AppPaths
import java.io.File

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var ragRepository: com.santiya.localaihub.repo.RagRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Bind LLM service after activity is created (Android 14+ requirement)
        LlmModelWorker.bindService(applicationContext)

        if (!NotificationPermissionHelper.hasNotificationPermission(this)) {
            NotificationPermissionHelper.requestNotificationPermission(this) {
                if (it) {
                    Toast.makeText(this, "Notification permission granted", Toast.LENGTH_SHORT)
                        .show()
                } else {
                    Toast.makeText(this, "Notification permission denied", Toast.LENGTH_SHORT)
                        .show()
                }
            }
        }

        setContent {
            val context = this@MainActivity
            val themePreset by remember { AppSettingsDataStore(context).themePreset }
                .collectAsState(initial = ThemePreset.SYSTEM)
            SantiyaLocalAiHubTheme(themePreset = themePreset) {

                // Compute start destination from onboarding state + installed models
                var startDestination by remember { mutableStateOf<String?>(null) }
                var hasModelsInstalled by remember { mutableStateOf(false) }
                var needsMigration by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    withContext(Dispatchers.IO) {
                        // Parallelize DataStore reads вЂ” each opens a separate file
                        val (termsAccepted, setupDone, guideSeen) = coroutineScope {
                            val t = async { TermsDataStore(context).hasAcceptedTerms.first() }
                            val s = async { SetupDataStore(context).isSetupDone.first() }
                            val g = async { AppSettingsDataStore(context).guideSeen.first() }
                            Triple(t.await(), s.await(), g.await())
                        }

                        // Auto-init vault for returning users (exists on disk but not yet opened)
                        if (!VaultManager.isReady.value && VaultManager.exists(context)) {
                            VaultManager.initPlaintext(context)
                            AppContainer.ensureVaultInitialized()
                        }

                        val vaultReady = VaultManager.isReady.value

                        // Check for legacy data that needs migration
                        val roomDb = context.getDatabasePath("llm_models_database").exists()
                        val vault = AppPaths.vaultFile(context).exists()
                        needsMigration = roomDb || vault

                        // Only check models if vault is ready
                        val hasModel = if (vaultReady) {
                            try {
                                val modelRepository = AppContainer.getModelRepository()
                                val models = modelRepository.getAllModels().first()
                                models.any {
                                    it.providerType == ProviderType.GGUF || it.providerType == ProviderType.DIFFUSION
                                }
                            } catch (_: Exception) { false }
                        } else false
                        hasModelsInstalled = hasModel

                        startDestination = when {
                            // Returning user: vault ready + terms accepted + (setup done or has model)
                            vaultReady && termsAccepted && (setupDone || hasModel) -> Screen.Chat.route

                            // Vault ready but terms not accepted
                            vaultReady && !termsAccepted -> Screen.Terms.route

                            // Vault ready but setup not done
                            vaultReady && !setupDone && !hasModel -> Screen.OnboardingSetup.route

                            // First launch: show intro
                            !guideSeen -> Screen.Intro.route

                            // Needs migration and vault not ready: go to migration
                            needsMigration && !vaultReady -> Screen.Migration.route

                            // Guide seen but terms not accepted
                            !termsAccepted -> Screen.Terms.route

                            // Fallback: go to setup (which handles vault init if needed)
                            else -> Screen.OnboardingSetup.route
                        }
                    }
                }

                val dest = startDestination ?: return@SantiyaLocalAiHubTheme

                AppNavigation(
                    startDestination = dest,
                    hasModelsInstalled = hasModelsInstalled,
                    needsMigration = needsMigration
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clear password cache when app terminates
        ragRepository.clearPasswordCache()
        LlmModelWorker.unbindService()
        AppContainer.shutdown()
    }
}

sealed class Screen(val route: String) {
    // Onboarding (flat routes so any can be used as startDestination)
    object Intro : Screen("intro")
    object Guide : Screen("guide")
    object Migration : Screen("migration")
    object Terms : Screen("terms")
    object OnboardingSetup : Screen("setup")

    // Main app
    object Chat : Screen("chat")
    object Store : Screen("store")
    object Editor : Screen("editor")
    object Settings : Screen("settings")
    object Files : Screen("files")
    object Live : Screen("live")
    object DeveloperApi : Screen("developer_api")
    object VaultManager : Screen("vault_manager")
    object AiMemory : Screen("ai_memory")
    object ImageGenSetup : Screen("image_gen_setup")
}

@Composable
fun AppNavigation(
    startDestination: String,
    hasModelsInstalled: Boolean,
    needsMigration: Boolean
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val navController = rememberNavController()

    // Activity-scoped ViewModels for shared state between Chat and Personas
    val chatViewModel: ChatViewModel = hiltViewModel()
    val llmModelViewModel: LLMModelViewModel = hiltViewModel()

    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = tween(300)
            ) + fadeIn(animationSpec = tween(300))
        },
        exitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = tween(300)
            ) + fadeOut(animationSpec = tween(300))
        },
        popEnterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(300)
            ) + fadeIn(animationSpec = tween(300))
        },
        popExitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(300)
            ) + fadeOut(animationSpec = tween(300))
        }
    ) {

        // ============ ONBOARDING SCREENS ============
        composable(Screen.Intro.route) {
            // IntroScreen was removed вЂ” skip straight to Guide or Migration
            LaunchedEffect(Unit) {
                val nextRoute = if (needsMigration) Screen.Migration.route else Screen.Guide.route
                navController.navigate(nextRoute) {
                    popUpTo(Screen.Intro.route) { inclusive = true }
                }
            }
        }

        composable(Screen.Migration.route) {
            val vaultGateViewModel: VaultGateViewModel = viewModel()
            VaultGateScreen(
                viewModel = vaultGateViewModel,
                onComplete = {
                    navController.navigate(Screen.Guide.route) {
                        popUpTo(Screen.Migration.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Guide.route) {
            val appSettings = remember { AppSettingsDataStore(context) }
            GuideScreen(onContinue = {
                scope.launch { appSettings.saveGuideSeen(true) }
                navController.navigate(Screen.Terms.route) {
                    popUpTo(Screen.Guide.route) { inclusive = true }
                }
            })
        }

        composable(Screen.Terms.route) {
            val termsDataStore = remember { TermsDataStore(context) }
            TermsAndConditionsScreen(
                onAccept = {
                    scope.launch {
                        termsDataStore.acceptTerms()
                    }
                    if (hasModelsInstalled) {
                        // Returning user: skip setup, go to chat
                        navController.navigate(Screen.Chat.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    } else {
                        // New user: proceed to setup
                        navController.navigate(Screen.OnboardingSetup.route) {
                            popUpTo(Screen.Terms.route) { inclusive = true }
                        }
                    }
                }
            )
        }

        composable(Screen.OnboardingSetup.route) {
            SetupScreen(
                onSetupComplete = {
                    navController.navigate(Screen.Chat.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        // ============ MAIN APP ROUTES ============
        composable(Screen.Chat.route) { _ ->
            HomeScreen(
                onSettingsClick = {
                    navController.navigate(Screen.Settings.route)
                },
                onDeveloperClick = {
                    navController.navigate(Screen.DeveloperApi.route)
                },
                onStoreButtonClicked = {
                    navController.navigate(Screen.Store.route)
                },
                onFilesClick = {
                    navController.navigate(Screen.Files.route)
                },
                onLiveClick = {
                    navController.navigate(Screen.Live.route)
                },
                onVaultManagerClick = {
                    navController.navigate(Screen.VaultManager.route)
                },
                onImageGenSetupNeeded = {
                    navController.navigate(Screen.ImageGenSetup.route)
                },
                chatViewModel = chatViewModel,
                llmModelViewModel = llmModelViewModel
            )
        }

        composable(Screen.Editor.route) {
            ModelConfigEditorScreen(onBackClick = {
                navController.popBackStack()
            })
        }

        composable(Screen.Store.route) {
            ModelStoreScreen(onNavigateBack = {
                navController.popBackStack()
            })
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onModelEditor = { navController.navigate(Screen.Editor.route) },
                onAiMemoryClick = { navController.navigate(Screen.AiMemory.route) }
            )
        }

        composable(Screen.Files.route) {
            FilesWorkspaceScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(Screen.Live.route) {
            LiveAiScreen(
                onNavigateBack = { navController.popBackStack() },
                chatViewModel = chatViewModel,
                llmModelViewModel = llmModelViewModel
            )
        }

        composable(Screen.DeveloperApi.route) {
            DeveloperApiScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(Screen.VaultManager.route) {
            VaultDashboard(onNavigateBack = { navController.popBackStack() })
        }

        composable(Screen.AiMemory.route) {
            AiMemoryScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.ImageGenSetup.route) {
            ImageGenSetupScreen(
                onComplete = {
                    llmModelViewModel.onQnnSetupComplete()
                    navController.popBackStack()
                },
                onSkip = {
                    llmModelViewModel.onQnnSetupDismissed()
                    navController.popBackStack()
                },
                onBack = {
                    llmModelViewModel.onQnnSetupDismissed()
                    navController.popBackStack()
                }
            )
        }
    }
}
