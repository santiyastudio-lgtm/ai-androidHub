package com.santiya.localaihub.hub

import android.content.Context
import android.content.pm.PackageManager
import android.os.Binder
import com.santiya.localaihub.data.AppSettingsDataStore
import com.santiya.localaihub.service.ExternalClientRegistrationRequest
import com.santiya.localaihub.service.ExternalClientRegistrationResponse
import com.santiya.localaihub.service.HubAuthEnvelope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import java.security.MessageDigest
import java.security.SecureRandom

data class ExternalAuthResult(
    val ok: Boolean,
    val packageName: String? = null,
    val app: ApprovedClientApp? = null,
    val message: String = "",
)

class ExternalAccessManager(private val context: Context) {
    private val appSettings = AppSettingsDataStore(context)

    fun policyFlow(): Flow<ExternalAccessPolicy> = appSettings.externalAccessPolicy

    fun policySnapshot(): ExternalAccessPolicy = runBlocking {
        appSettings.externalAccessPolicySnapshot()
    }

    fun isCallerAllowed(): Boolean {
        val packageName = resolveCallingPackage() ?: return false
        return isPackageAllowed(packageName)
    }

    fun isPackageAllowed(packageName: String): Boolean {
        if (packageName == context.packageName) return true
        if (context.packageManager.checkSignatures(context.packageName, packageName) == PackageManager.SIGNATURE_MATCH) {
            return true
        }
        val policy = policySnapshot()
        if (!policy.enabled) return false
        return policy.approvedApps.any { it.packageName == packageName }
    }

    fun resolveCallingPackage(): String? {
        val packages = context.packageManager.getPackagesForUid(Binder.getCallingUid()).orEmpty()
        return packages.firstOrNull()
    }

    fun validateAuth(envelope: HubAuthEnvelope?): ExternalAuthResult {
        val callerPackage = resolveCallingPackage()
        if (callerPackage == context.packageName) {
            return ExternalAuthResult(ok = true, packageName = callerPackage, message = "internal")
        }
        if (callerPackage != null && context.packageManager.checkSignatures(context.packageName, callerPackage) == PackageManager.SIGNATURE_MATCH) {
            return ExternalAuthResult(ok = true, packageName = callerPackage, message = "trusted_signature")
        }
        val packageName = envelope?.packageName?.takeIf { it.isNotBlank() } ?: callerPackage
            ?: return ExternalAuthResult(ok = false, message = "package_not_resolved")
        if (callerPackage != null && packageName != callerPackage) {
            return ExternalAuthResult(ok = false, packageName = callerPackage, message = "package_mismatch")
        }
        val policy = policySnapshot()
        if (!policy.enabled) {
            return ExternalAuthResult(ok = false, packageName = packageName, message = "external_access_disabled")
        }
        val app = policy.approvedApps.firstOrNull { it.packageName == packageName }
            ?: return ExternalAuthResult(ok = false, packageName = packageName, message = "client_not_approved")
        if (app.clientId.isBlank() || app.apiKeyHash.isBlank()) {
            return ExternalAuthResult(ok = false, packageName = packageName, app = app, message = "client_credentials_missing")
        }
        if (envelope == null || envelope.clientId != app.clientId || hashApiKey(envelope.apiKey) != app.apiKeyHash) {
            return ExternalAuthResult(ok = false, packageName = packageName, app = app, message = "invalid_credentials")
        }
        return ExternalAuthResult(ok = true, packageName = packageName, app = app, message = "ok")
    }

    suspend fun setEnabled(enabled: Boolean) {
        val current = appSettings.externalAccessPolicySnapshot()
        appSettings.saveExternalAccessPolicy(current.copy(enabled = enabled))
    }

    suspend fun approve(packageName: String) {
        val current = appSettings.externalAccessPolicySnapshot()
        val existing = current.approvedApps.firstOrNull { it.packageName == packageName }
        val label = packageLabel(packageName)
        val approved = current.approvedApps
            .filterNot { it.packageName == packageName } +
            ApprovedClientApp(
                packageName = packageName,
                appLabel = label,
                approvedAtEpochMs = existing?.approvedAtEpochMs ?: System.currentTimeMillis(),
                clientId = existing?.clientId.orEmpty(),
                apiKeyPreview = existing?.apiKeyPreview.orEmpty(),
                apiKeyHash = existing?.apiKeyHash.orEmpty(),
                scopes = existing?.scopes ?: emptyList(),
            )
        appSettings.saveExternalAccessPolicy(
            current.copy(
                approvedApps = approved.sortedBy { it.appLabel.lowercase() },
                pendingPackages = current.pendingPackages.filterNot { it == packageName }
            )
        )
    }

    suspend fun revoke(packageName: String) {
        val current = appSettings.externalAccessPolicySnapshot()
        appSettings.saveExternalAccessPolicy(
            current.copy(
                approvedApps = current.approvedApps.filterNot { it.packageName == packageName }
            )
        )
    }

    suspend fun recordPending(packageName: String) {
        if (packageName.isBlank() || packageName == context.packageName) return
        val current = appSettings.externalAccessPolicySnapshot()
        if (current.pendingPackages.contains(packageName)) return
        appSettings.saveExternalAccessPolicy(
            current.copy(pendingPackages = (current.pendingPackages + packageName).sorted())
        )
    }

    suspend fun registerClient(request: ExternalClientRegistrationRequest): ExternalClientRegistrationResponse {
        val resolvedPackage = request.packageName.ifBlank { resolveCallingPackage().orEmpty() }
        if (resolvedPackage.isBlank()) {
            return ExternalClientRegistrationResponse(
                ok = false,
                packageName = "",
                message = "Не удалось определить package внешнего приложения."
            )
        }
        if (resolvedPackage != context.packageName) {
            recordPending(resolvedPackage)
        }
        val current = appSettings.externalAccessPolicySnapshot()
        val existing = current.approvedApps.firstOrNull { it.packageName == resolvedPackage }
        val apiKey = randomToken(18)
        val clientId = existing?.clientId?.takeIf { it.isNotBlank() } ?: "cli_${randomToken(8)}"
        val updatedApp = ApprovedClientApp(
            packageName = resolvedPackage,
            appLabel = request.appLabel?.takeIf { it.isNotBlank() } ?: packageLabel(resolvedPackage),
            approvedAtEpochMs = existing?.approvedAtEpochMs ?: System.currentTimeMillis(),
            clientId = clientId,
            apiKeyPreview = apiKey.take(6),
            apiKeyHash = hashApiKey(apiKey),
            scopes = request.scopes.ifEmpty { DEFAULT_SCOPES }
        )
        val updatedApproved = current.approvedApps.filterNot { it.packageName == resolvedPackage } + updatedApp
        appSettings.saveExternalAccessPolicy(
            current.copy(
                approvedApps = updatedApproved.sortedBy { it.appLabel.lowercase() },
                pendingPackages = (current.pendingPackages + resolvedPackage).distinct().sorted()
            )
        )
        val approved = current.enabled && current.approvedApps.any { it.packageName == resolvedPackage }
        return ExternalClientRegistrationResponse(
            ok = true,
            packageName = resolvedPackage,
            clientId = clientId,
            apiKey = apiKey,
            approved = approved,
            scopes = updatedApp.scopes,
            message = if (approved) {
                "Клиент обновлён. Вызовы доступны сразу."
            } else {
                "Клиент зарегистрирован. Подтвердите приложение в настройках Hub."
            }
        )
    }

    private fun packageLabel(packageName: String): String {
        return try {
            val pm = context.packageManager
            @Suppress("DEPRECATION")
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo)?.toString().orEmpty().ifBlank { packageName }
        } catch (_: Exception) {
            packageName
        }
    }

    private fun hashApiKey(value: String): String {
        if (value.isBlank()) return ""
        return MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private fun randomToken(bytes: Int): String {
        val data = ByteArray(bytes)
        SecureRandom().nextBytes(data)
        return data.joinToString("") { "%02x".format(it) }
    }

    companion object {
        val DEFAULT_SCOPES = listOf(
            "catalog/search",
            "download/install",
            "chat",
            "files",
            "object_detect",
            "face_detect",
            "face_recognize",
            "image_generate",
            "video_request",
            "tts",
            "live_session",
        )
    }
}
