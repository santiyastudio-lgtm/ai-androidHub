package com.santiya.localaihub

import android.app.Application
import android.util.Log
import com.santiya.localaihub.data.AppSettingsDataStore
import com.santiya.localaihub.data.VaultManager
import com.santiya.localaihub.di.AppContainer
import com.santiya.localaihub.plugins.CalculatorPlugin
import com.santiya.localaihub.plugins.BrowserPlugin
import com.santiya.localaihub.plugins.DateTimePlugin
import com.santiya.localaihub.plugins.DevUtilsPlugin
import com.santiya.localaihub.plugins.FileManagerPlugin
import com.santiya.localaihub.plugins.LocationControlPlugin
import com.santiya.localaihub.plugins.NotePadPlugin
import com.santiya.localaihub.plugins.PluginManager
import com.santiya.localaihub.plugins.ScriptAutomationPlugin
import com.santiya.localaihub.plugins.SystemInfoPlugin
import com.santiya.localaihub.plugins.WebSearchPlugin
import com.santiya.localaihub.repo.RagRepository
import com.santiya.localaihub.tts.TTSDataStore
import com.santiya.localaihub.tts.TTSManager
import com.santiya.localaihub.worker.DataIntegrityManager
import com.santiya.localaihub.worker.LlmModelWorker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@HiltAndroidApp
class NVApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private const val TAG = "NVApplication"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Application onCreate")

        // Initialize app container first
        AppContainer.init(applicationContext, this)

        // Register plugins
        PluginManager.registerPlugin(WebSearchPlugin())
        PluginManager.registerPlugin(BrowserPlugin())
        PluginManager.registerPlugin(CalculatorPlugin())
        PluginManager.registerPlugin(DateTimePlugin())
        PluginManager.registerPlugin(DevUtilsPlugin())
        PluginManager.registerPlugin(FileManagerPlugin(applicationContext))
        PluginManager.registerPlugin(ScriptAutomationPlugin(com.santiya.localaihub.global.AppPaths.workspaceFiles(applicationContext)))
        PluginManager.registerPlugin(NotePadPlugin())
        PluginManager.registerPlugin(SystemInfoPlugin(applicationContext))
        PluginManager.registerPlugin(LocationControlPlugin(applicationContext))
        Log.d(TAG, "Plugins registered: ${PluginManager.registeredPlugins.value.size} plugins")

        // Initialize TTS Manager without auto-loading (loading controlled by settings)
        TTSManager.init(applicationContext, autoLoad = false)
        Log.d(TAG, "TTSManager initialized")

        // Run data integrity check after UMS is ready (deferred to let UI render first)
        appScope.launch {
            delay(2000) // Let Activity.onCreate + first frame complete before scanning
            try {
                if (!VaultManager.isReady.value) {
                    Log.w(TAG, "UMS not ready, skipping integrity check")
                } else {
                    val db = AppContainer.getDatabase()
                    val ragRepository = RagRepository(
                        ragDao = db.ragDao(),
                        context = applicationContext
                    )
                    val manager = DataIntegrityManager(
                        context = applicationContext,
                        modelRepo = VaultManager.modelRepo!!,
                        personaRepo = VaultManager.personaRepo!!,
                        ragDao = db.ragDao(),
                        memoryRepo = VaultManager.memoryRepo!!,
                        ragRepository = ragRepository,
                        appSettings = AppSettingsDataStore(applicationContext)
                    )
                    val report = manager.runFullCheck()
                    Log.i(TAG, "Integrity check: ${report.totalFixes} fixes applied")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Data integrity check failed", e)
            }
        }

        // Conditionally load TTS model based on user setting
        appScope.launch {
            try {
                val settings = AppSettingsDataStore(applicationContext)
                val loadOnStart = settings.loadTTSOnStart.first()
                if (loadOnStart) {
                    val modelDir = TTSManager.getModelDirectory()
                    if (modelDir != null) {
                        val useNNAPI = TTSDataStore(applicationContext).settings.first().useNNAPI
                        val success = TTSManager.loadModel(modelDir, useNNAPI)
                        Log.d(TAG, "TTS model auto-loaded on start: $success")
                    }
                } else {
                    Log.d(TAG, "TTS auto-load disabled by user setting")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking TTS auto-load setting", e)
            }
        }

        // Note: Service binding moved to MainActivity to comply with Android 14+ foreground service restrictions
    }
}
