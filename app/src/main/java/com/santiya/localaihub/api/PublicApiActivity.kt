package com.santiya.localaihub.api

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.santiya.localaihub.hub.ExternalAccessManager
import com.santiya.localaihub.runtime.ModelRegistry
import com.santiya.localaihub.service.LLMService

class PublicApiActivity : Activity() {
    private val registry = ModelRegistry()
    private lateinit var externalAccessManager: ExternalAccessManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        externalAccessManager = ExternalAccessManager(applicationContext)
        val result = Intent()
        val callingPackageName = callingPackage ?: intent?.`package`.orEmpty()

        if (callingPackageName.isNotBlank() && !externalAccessManager.isPackageAllowed(callingPackageName)) {
            if (callingPackageName != packageName) {
                kotlinx.coroutines.runBlocking {
                    externalAccessManager.recordPending(callingPackageName)
                }
            }
            result.putExtra(
                SantiyaLocalAiActions.EXTRA_ERROR,
                "Доступ к AI из других приложений выключен или приложение ещё не одобрено."
            )
            setResult(RESULT_CANCELED, result)
            finish()
            return
        }

        when (intent?.action) {
            SantiyaLocalAiActions.ACTION_PICK_MODEL -> {
                val catalogJson = LLMService.instance?.let { service ->
                    runCatching { service.publicCatalogJson("ru") }.getOrNull()
                } ?: registry.defaultCatalogJson()
                result.putExtra(SantiyaLocalAiActions.EXTRA_RESULT_JSON, catalogJson)
                setResult(RESULT_OK, result)
            }
            SantiyaLocalAiActions.ACTION_WAKE_HUB -> {
                val wakeIntent = Intent(applicationContext, LLMService::class.java).apply {
                    action = LLMService.ACTION_WAKE_HUB
                }
                androidx.core.content.ContextCompat.startForegroundService(applicationContext, wakeIntent)
                result.putExtra(
                    SantiyaLocalAiActions.EXTRA_RESULT_JSON,
                    """{"ok":true,"status":"waking","message":"AI Hub wake request sent."}"""
                )
                setResult(RESULT_OK, result)
            }
            SantiyaLocalAiActions.ACTION_RUN_TEXT,
            SantiyaLocalAiActions.ACTION_RUN_IMAGE,
            SantiyaLocalAiActions.ACTION_RUN_VISION -> {
                result.putExtra(
                    SantiyaLocalAiActions.EXTRA_ERROR,
                    "Используйте AIDL SDK для выполнения. Intent API оставлен для коротких пользовательских сценариев."
                )
                setResult(RESULT_CANCELED, result)
            }
            else -> {
                result.putExtra(SantiyaLocalAiActions.EXTRA_ERROR, "Неподдерживаемое действие SantiyaLocalAiHub.")
                setResult(RESULT_CANCELED, result)
            }
        }

        finish()
    }
}
