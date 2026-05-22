package com.santiya.localaihub.api

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.santiya.localaihub.hub.ExternalAccessManager
import com.santiya.localaihub.runtime.ModelRegistry
import com.santiya.localaihub.service.LLMService
import kotlinx.coroutines.runBlocking

class PublicApiActivity : Activity() {
    private val registry = ModelRegistry()
    private lateinit var externalAccessManager: ExternalAccessManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        externalAccessManager = ExternalAccessManager(applicationContext)
        val result = Intent()
        val caller = callingPackage

        if (!isCallerAllowed(caller)) {
            caller?.takeIf { it != packageName }?.let { packageName ->
                runBlocking { externalAccessManager.recordPending(packageName) }
            }
            result.putExtra(
                SantiyaLocalAiActions.EXTRA_ERROR,
                "Доступ к AI Hub из внешнего приложения выключен или приложение ещё не одобрено."
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
                    "Используйте AIDL SDK с авторизацией. Intent API оставлен только для коротких пользовательских сценариев."
                )
                setResult(RESULT_CANCELED, result)
            }

            else -> {
                result.putExtra(
                    SantiyaLocalAiActions.EXTRA_ERROR,
                    "Неподдерживаемое действие SantiyaLocalAiHub."
                )
                setResult(RESULT_CANCELED, result)
            }
        }

        finish()
    }

    private fun isCallerAllowed(caller: String?): Boolean {
        if (caller == packageName) return true
        if (caller.isNullOrBlank()) return false
        return externalAccessManager.isPackageAllowed(caller)
    }
}
