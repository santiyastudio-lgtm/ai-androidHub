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
import java.security.SecureRandom

class LanCoordinator(private val context: Context) {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
    private val modelRegistry = ModelRegistry()
    private val resourceCollector = AndroidNodeResourceCollector(context)
    private val profileEstimator = GgufModelProfileEstimator()
    private val partitionPlanner = DistributedPartitionPlanner()
    private val peerDiscovery = NsdLanDiscoveryService(context)
    private val advertiser = NsdLanAdvertiser(context)

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

    fun nodesJson(models: List<Model>, config: LanHubConfig): String {
        val nodes = if (config.enabled) {
            advertiser.start(resourceCollector.snapshot())
            peerDiscovery.start()
            listOf(localNode(models, config)) + peerDiscovery.snapshot().map { it.toLanNodeInfo() }
        } else {
            advertiser.stop()
            peerDiscovery.stop()
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

        advertiser.start(resourceCollector.snapshot())
        peerDiscovery.start()
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
