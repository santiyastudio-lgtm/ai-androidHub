package com.santiya.localaihub.distributed

import com.santiya.localaihub.models.enums.PathType
import com.santiya.localaihub.models.enums.ProviderType
import com.santiya.localaihub.models.table_schema.Model
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DistributedPartitionPlannerTest {

    private val estimator = GgufModelProfileEstimator()
    private val planner = DistributedPartitionPlanner()

    @Test
    fun `falls back to sequential offload when no peers`() {
        val model = estimator.estimate(
            Model(
                modelName = "Llama-3-8B-Instruct-Q4",
                modelPath = "/tmp/model.gguf",
                pathType = PathType.FILE,
                providerType = ProviderType.GGUF,
                fileSize = 6_700L * 1024L * 1024L
            )
        )
        val local = sampleNode("local", freeRamMb = 4096, acceleratorScore = 3.0)

        val plan = planner.plan(model, local, emptyList())

        assertEquals(DistributedExecutionStrategy.SEQUENTIAL_OFFLOAD, plan.strategy)
        assertTrue(plan.sequentialOffloadPlan != null)
        assertEquals(1, plan.assignments.size)
        assertEquals(model.layerCount, plan.assignments.first().layerRange.layerCount)
    }

    @Test
    fun `splits layers across local and peer nodes`() {
        val model = estimator.estimate(
            Model(
                modelName = "Llama-3-70B-Q4",
                modelPath = "/tmp/model.gguf",
                pathType = PathType.FILE,
                providerType = ProviderType.GGUF,
                fileSize = 40_000L * 1024L * 1024L
            )
        )
        val local = sampleNode("local", freeRamMb = 8192, acceleratorScore = 3.0)
        val peer = sampleNode("peer-1", freeRamMb = 12288, acceleratorScore = 4.0)

        val plan = planner.plan(model, local, listOf(peer))

        assertEquals(DistributedExecutionStrategy.PIPELINE_PARALLEL_LAN, plan.strategy)
        assertEquals(2, plan.assignments.size)
        assertEquals(
            model.layerCount,
            plan.assignments.sumOf { it.layerRange.layerCount }
        )
        assertTrue(plan.hiddenStateTransferPlan != null)
    }

    @Test
    fun `heuristic estimator marks profile as heuristic`() {
        val profile = estimator.estimate(
            Model(
                modelName = "Qwen3.5-30B-A3B-Uncensored",
                modelPath = "/tmp/model.gguf",
                pathType = PathType.FILE,
                providerType = ProviderType.GGUF,
                fileSize = 17_000L * 1024L * 1024L
            )
        )

        assertTrue(profile.heuristicProfile)
        assertTrue(profile.layerCount >= 32)
        assertTrue(profile.hiddenSize >= 4096)
    }

    private fun sampleNode(
        id: String,
        freeRamMb: Int,
        acceleratorScore: Double
    ): NodeResourceSnapshot {
        return NodeResourceSnapshot(
            nodeId = id,
            displayName = id,
            platform = DistributedNodePlatform.ANDROID,
            totalRamMb = 12288,
            freeRamMb = freeRamMb,
            cpuCores = 8,
            cpuArch = "arm64-v8a",
            acceleratorSummary = "adreno",
            acceleratorScore = acceleratorScore,
            computeScore = 10.0,
            availableStorageMb = 32000,
            supportsSequentialOffload = true,
            supportsPipelineWorker = true,
            heavySlotAvailable = true,
            transportProtocols = listOf(DistributedTransportProtocol.NSD_LIBP2P),
            lastSeenEpochMs = System.currentTimeMillis()
        )
    }
}
