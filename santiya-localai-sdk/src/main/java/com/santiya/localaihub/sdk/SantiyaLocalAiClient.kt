package com.santiya.localaihub.sdk

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.santiya.localaihub.api.SantiyaLocalAiActions
import com.santiya.localaihub.service.ILLMService
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class SantiyaLocalAiClient private constructor(
    private val context: Context,
    private val service: ILLMService,
    private val connection: ServiceConnection,
) : AutoCloseable {

    fun getRuntimeCapabilitiesJson(): String = service.getRuntimeCapabilitiesJson()

    fun listModelsJson(): String = service.listModelsJson()

    fun listModelsJson(locale: String): String = service.listModelsJsonForLocale(locale)

    fun searchCatalogJson(requestJson: String): String = service.searchCatalogJson(requestJson)

    fun registerClientJson(requestJson: String): String = service.registerClientJson(requestJson)

    fun getModelManifestSchemaJson(): String = service.getModelManifestSchemaJson()

    fun importModelManifestJson(manifestJson: String): String =
        service.importModelManifestJson(manifestJson)

    fun downloadModelJson(requestJson: String): String =
        service.downloadModelJson(requestJson)

    fun cancelDownloadJson(modelId: String): String =
        service.cancelDownloadJson(modelId)

    fun getDownloadStatusJson(modelId: String): String =
        service.getDownloadStatusJson(modelId)

    fun preparePreferredModelJson(capability: String): String =
        service.preparePreferredModelJson(capability)

    fun runVisionJson(requestJson: String): String =
        service.runVisionJson(requestJson)

    fun runImageJson(requestJson: String): String =
        service.runImageJson(requestJson)

    fun runWithModeJson(requestJson: String): String =
        service.runWithModeJson(requestJson)

    fun getHttpApiStateJson(): String = service.getHttpApiStateJson()

    fun getPreferredModelsJson(): String = service.getPreferredModelsJson()

    fun setPreferredModelJson(capability: String, modelId: String?): String =
        service.setPreferredModelJson(capability, modelId ?: "")

    fun getExternalAccessPolicyJson(): String = service.getExternalAccessPolicyJson()

    fun approveClientJson(packageName: String): String = service.approveClientJson(packageName)

    fun revokeClientJson(packageName: String): String = service.revokeClientJson(packageName)

    fun listLanNodesJson(): String = service.listLanNodesJson()

    fun getDistributedGgufPlanJson(modelId: String): String =
        service.getDistributedGgufPlanJson(modelId)

    fun getOrchestraConfigJson(): String = service.getOrchestraConfigJson()

    fun setOrchestraConfigJson(configJson: String): String = service.setOrchestraConfigJson(configJson)

    fun rawService(): ILLMService = service

    override fun close() {
        context.applicationContext.unbindService(connection)
    }

    companion object {
        suspend fun bind(context: Context): SantiyaLocalAiClient =
            suspendCancellableCoroutine { continuation ->
                val appContext = context.applicationContext
                lateinit var connection: ServiceConnection
                connection = object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                        val service = ILLMService.Stub.asInterface(binder)
                        if (continuation.isActive) {
                            continuation.resume(SantiyaLocalAiClient(appContext, service, connection))
                        }
                    }

                    override fun onServiceDisconnected(name: ComponentName?) = Unit
                }

                val intent = Intent().apply {
                    component = ComponentName(
                        "com.santiya.localaihub",
                        "com.santiya.localaihub.service.LLMService",
                    )
                }

                val bound = appContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)
                if (!bound) {
                    continuation.resumeWith(
                        Result.failure(IllegalStateException("SantiyaLocalAiHub service is not available.")),
                    )
                    return@suspendCancellableCoroutine
                }

                continuation.invokeOnCancellation {
                    runCatching { appContext.unbindService(connection) }
                }
            }

        fun pickModelIntent(): Intent = Intent(SantiyaLocalAiActions.ACTION_PICK_MODEL).apply {
            setPackage("com.santiya.localaihub")
        }
    }
}
