package com.santiya.localaihub.hub

import android.content.Context
import android.os.Build
import com.santiya.localaihub.distributed.AndroidNodeResourceCollector
import com.santiya.localaihub.distributed.DistributedPartitionPlanner
import com.santiya.localaihub.distributed.DistributedPlanEnvelope
import com.santiya.localaihub.distributed.GgufModelProfileEstimator
import com.santiya.localaihub.distributed.NsdLanAdvertiser
import com.santiya.localaihub.distributed.NsdLanDiscoveryService
import com.santiya.localaihub.distributed.NodeResourceSnapshot
import com.santiya.localaihub.models.enums.ProviderType
import com.santiya.localaihub.models.table_schema.Model
import com.santiya.localaihub.runtime.ModelRegistry
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.security.SecureRandom

class LanCoordinator(private val context: Context) {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
    private val modelRegistry = ModelRegistry()
    private val resourceCollector = AndroidNodeResourceCollector(context)
    private val profileEstimator = GgufModelProfileEstimator()
    private val partitionPlanner = DistributedPartitionPlanner()
    private val peerDiscovery = NsdLanDiscoveryService(context)
    private val advertiser = NsdLanAdvertiser(context)

    companion object {
        const val LAN_SERVER_PORT = NsdLanAdvertiser.DEFAULT_PORT
    }

    fun ensurePairingToken(config: LanHubConfig): LanHubConfig {
        return if (config.pairingToken.isBlank()) {
            config.copy(pairingToken = randomToken())
        } else {
            config
        }
    }

    fun localNode(models: List<Model>, config: LanHubConfig): LanNodeInfo {
        val safeConfig = ensurePairingToken(config)
        val capabilities = modelRegistry.capabilities().map { it.capability.name.lowercase() }
        val snapshot = resourceCollector.snapshot()
        return LanNodeInfo(
            id = "local-${Build.MODEL.lowercase().replace(" ", "-")}",
            name = "${Build.MANUFACTURER} ${Build.MODEL}",
            isLocal = true,
            status = if (safeConfig.enabled) "ready" else "disabled",
            capabilities = capabilities,
            installedModelCount = models.size,
            notes = if (safeConfig.enabled) {
                "LAN planning enabled. Native distributed GGUF execution still requires JNI layer-range hooks and live transport."
            } else {
                "LAN mode disabled."
            },
            totalRamMb = snapshot.totalRamMb,
            freeRamMb = snapshot.freeRamMb,
            cpuCores = snapshot.cpuCores,
            acceleratorSummary = snapshot.acceleratorSummary,
            computeScore = snapshot.computeScore,
            transportProtocols = snapshot.transportProtocols.map { it.name.lowercase() },
            heavySlotAvailable = snapshot.heavySlotAvailable,
            supportsSequentialOffload = snapshot.supportsSequentialOffload,
            supportsPipelineWorker = snapshot.supportsPipelineWorker,
            lastSeenEpochMs = snapshot.lastSeenEpochMs
        )
    }

    fun applyLanRuntime(models: List<Model>, config: LanHubConfig, httpPort: Int = LAN_SERVER_PORT) {
        val safeConfig = ensurePairingToken(config)
        if (!safeConfig.enabled) {
            stopLanRuntime()
            return
        }
        if (safeConfig.advertiseLocalNode) {
            advertiser.start(resourceCollector.snapshot(), httpPort)
        } else {
            advertiser.stop()
        }
        peerDiscovery.start()
    }

    fun stopLanRuntime() {
        advertiser.stop()
        peerDiscovery.stop()
    }

    fun statusJson(models: List<Model>, config: LanHubConfig, httpPort: Int = LAN_SERVER_PORT): String {
        val safeConfig = ensurePairingToken(config)
        val snapshot = resourceCollector.snapshot()
        val supportedModels = models
            .filter { it.providerType == ProviderType.GGUF }
            .map { it.id }

        return buildJsonObject {
            put("ok", true)
            put("nodeId", snapshot.nodeId)
            put("displayName", snapshot.displayName)
            put("platform", snapshot.platform.name.lowercase())
            put("totalRamMb", snapshot.totalRamMb)
            put("freeRamMb", snapshot.freeRamMb)
            put("cpuCores", snapshot.cpuCores)
            put("cpuArch", snapshot.cpuArch)
            put("acceleratorSummary", snapshot.acceleratorSummary)
            put("acceleratorScore", snapshot.acceleratorScore)
            put("computeScore", snapshot.computeScore)
            put("availableStorageMb", snapshot.availableStorageMb)
            put("supportsSequentialOffload", snapshot.supportsSequentialOffload)
            put("supportsPipelineWorker", snapshot.supportsPipelineWorker)
            put("heavySlotAvailable", snapshot.heavySlotAvailable)
            put("lastSeenEpochMs", snapshot.lastSeenEpochMs)
            put("installedModelCount", models.size)
            put("httpPort", httpPort)
            put("pairingRequired", true)
            putJsonArray("transportProtocols") {
                snapshot.transportProtocols.forEach { add(JsonPrimitive(it.name.lowercase())) }
            }
            putJsonArray("supportedModels") {
                supportedModels.forEach { add(JsonPrimitive(it)) }
            }
            putJsonArray("notes") {
                snapshot.notes.forEach { add(JsonPrimitive(it)) }
                add(JsonPrimitive(
                    if (safeConfig.enabled) {
                        "LAN node API is live on port $httpPort."
                    } else {
                        "LAN node API is disabled."
                    }
                ))
            }
        }.toString()
    }

    fun nodesJson(models: List<Model>, config: LanHubConfig): String {
        val nodes = if (config.enabled) {
            applyLanRuntime(models, config)
            listOf(localNode(models, config)) + peerDiscovery.snapshot().map { it.toLanNodeInfo() }
        } else {
            stopLanRuntime()
            emptyList()
        }
        return json.encodeToString(nodes)
    }

    fun distributedPlanJson(models: List<Model>, modelId: String, config: LanHubConfig): String {
        val safeConfig = ensurePairingToken(config)
        if (!safeConfig.enabled) {
            return json.encodeToString(
                DistributedPlanEnvelope(
                    ok = false,
                    modelId = modelId,
                    error = "lan_disabled",
                    message = "Enable LAN mode before requesting distributed GGUF planning."
                )
            )
        }

        val model = models.firstOrNull { it.id == modelId && it.providerType == ProviderType.GGUF }
            ?: return json.encodeToString(
                DistributedPlanEnvelope(
                    ok = false,
                    modelId = modelId,
                    error = "model_not_found",
                    message = "GGUF model was not found among installed models."
                )
            )

        applyLanRuntime(models, safeConfig)
        val plan = partitionPlanner.plan(
            model = profileEstimator.estimate(model),
            localNode = resourceCollector.snapshot(),
            peerNodes = peerDiscovery.snapshot()
        )

        return json.encodeToString(
            DistributedPlanEnvelope(
                ok = true,
                modelId = modelId,
                plan = plan,
                message = "Planner result generated. Peer discovery and remote execution require the next transport/runtime pass."
            )
        )
    }

    private fun randomToken(): String {
        val bytes = ByteArray(6)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun NodeResourceSnapshot.toLanNodeInfo(): LanNodeInfo {
        val noteText = notes.joinToString(separator = " ")
        return LanNodeInfo(
            id = nodeId,
            name = displayName,
            isLocal = false,
            status = if (heavySlotAvailable) "ready" else "busy",
            capabilities = emptyList(),
            installedModelCount = 0,
            notes = noteText.takeIf { it.isNotBlank() },
            platform = platform.name.lowercase(),
            totalRamMb = totalRamMb,
            freeRamMb = freeRamMb,
            cpuCores = cpuCores,
            acceleratorSummary = acceleratorSummary,
            computeScore = computeScore,
            transportProtocols = transportProtocols.map { it.name.lowercase() },
            heavySlotAvailable = heavySlotAvailable,
            supportsSequentialOffload = supportsSequentialOffload,
            supportsPipelineWorker = supportsPipelineWorker,
            lastSeenEpochMs = lastSeenEpochMs
        )
    }
}
