package com.santiya.localaihub.distributed

import kotlin.math.roundToInt

class DistributedPartitionPlanner(
    private val sequentialOffloadPlanner: SequentialOffloadPlanner = SequentialOffloadPlanner()
) {

    fun plan(
        model: GgufModelProfile,
        localNode: NodeResourceSnapshot,
        peerNodes: List<NodeResourceSnapshot>
    ): DistributedPartitionPlan {
        val eligiblePeers = peerNodes.filter { it.supportsPipelineWorker && it.heavySlotAvailable }
        if (eligiblePeers.isEmpty()) {
            return DistributedPartitionPlan(
                strategy = DistributedExecutionStrategy.SEQUENTIAL_OFFLOAD,
                modelProfile = model,
                localNode = localNode,
                peerNodes = emptyList(),
                assignments = listOf(
                    PipelineSegmentAssignment(
                        nodeId = localNode.nodeId,
                        nodeName = localNode.displayName,
                        layerRange = GgufLayerRange(0, model.layerCount),
                        role = "root",
                        estimatedResidentMb = model.modelSizeMb
                    )
                ),
                sequentialOffloadPlan = sequentialOffloadPlanner.plan(model, localNode),
                hiddenStateTransferPlan = null,
                nativeExecutionRequired = true,
                requiredEngineHooks = requiredHooks(singleNode = true),
                warnings = listOf("No eligible LAN workers were available. Planner fell back to local sequential offload."),
                notes = listOf("Sequential offload is the only honest execution strategy until remote workers advertise compatible GGUF runtime hooks.")
            )
        }

        val allNodes = listOf(localNode) + eligiblePeers
        val capacities = allNodes.map { it.nodeId to effectiveCapacity(it) }
        val totalCapacity = capacities.sumOf { it.second }.coerceAtLeast(1.0)
        val baseAllocations = capacities.map { (_, capacity) ->
            ((model.layerCount * (capacity / totalCapacity)).roundToInt()).coerceAtLeast(1)
        }.toMutableList()
        normalizeAllocations(baseAllocations, model.layerCount)

        var cursor = 0
        val assignments = allNodes.mapIndexed { index, node ->
            val layersForNode = baseAllocations[index]
            val end = (cursor + layersForNode).coerceAtMost(model.layerCount)
            val range = GgufLayerRange(cursor, end)
            cursor = end
            PipelineSegmentAssignment(
                nodeId = node.nodeId,
                nodeName = node.displayName,
                layerRange = range,
                role = if (index == 0) "root" else "worker",
                estimatedResidentMb = (range.layerCount * model.estimatedLayerFootprintMb).roundToInt()
            )
        }.filter { it.layerRange.layerCount > 0 }

        val compression = chooseNetworkCompression(model)
        val compressedBytesPerToken = when (compression) {
            KvCacheCompressionMode.NONE -> model.estimatedHiddenStateBytesPerToken
            KvCacheCompressionMode.Q8 -> model.hiddenSize
            KvCacheCompressionMode.Q6 -> (model.hiddenSize * 0.75).toInt()
            KvCacheCompressionMode.Q4 -> (model.hiddenSize * 0.5).toInt()
        }

        return DistributedPartitionPlan(
            strategy = DistributedExecutionStrategy.PIPELINE_PARALLEL_LAN,
            modelProfile = model,
            localNode = localNode,
            peerNodes = eligiblePeers,
            assignments = assignments,
            sequentialOffloadPlan = null,
            hiddenStateTransferPlan = HiddenStateTransferPlan(
                compression = compression,
                rawBytesPerToken = model.estimatedHiddenStateBytesPerToken,
                compressedBytesPerToken = compressedBytesPerToken,
                preferredTransport = DistributedTransportProtocol.NSD_LIBP2P,
                notes = listOf(
                    "libp2p is the preferred future transport because it can be shared across Android, macOS and Windows.",
                    "Compression estimate targets hidden-state handoff; JNI still needs tensor serialization hooks."
                )
            ),
            nativeExecutionRequired = true,
            requiredEngineHooks = requiredHooks(singleNode = false),
            warnings = buildList {
                add("Planner output is executable only after GGUF JNI exposes layer-range forward and hidden-state serialization.")
                if (eligiblePeers.any { DistributedTransportProtocol.NSD_LIBP2P !in it.transportProtocols }) {
                    add("One or more nodes do not advertise libp2p yet. HTTP fallback would be slower and less portable.")
                }
            },
            notes = listOf(
                "Assignments are contiguous transformer layer spans to keep pipeline semantics simple.",
                "Root keeps the first segment and coordinates token loop plus KV-cache policy."
            )
        )
    }

    private fun effectiveCapacity(node: NodeResourceSnapshot): Double {
        val ramFactor = node.freeRamMb / 1024.0
        val cpuFactor = node.cpuCores * 0.8
        val accelFactor = node.acceleratorScore * 1.2
        return ramFactor + cpuFactor + accelFactor
    }

    private fun normalizeAllocations(allocations: MutableList<Int>, targetTotal: Int) {
        var diff = targetTotal - allocations.sum()
        while (diff != 0 && allocations.isNotEmpty()) {
            for (i in allocations.indices) {
                if (diff == 0) break
                if (diff > 0) {
                    allocations[i] += 1
                    diff -= 1
                } else if (allocations[i] > 1) {
                    allocations[i] -= 1
                    diff += 1
                }
            }
            if (allocations.sum() == targetTotal) break
        }
    }

    private fun chooseNetworkCompression(model: GgufModelProfile): KvCacheCompressionMode {
        return when {
            model.hiddenSize >= 8192 -> KvCacheCompressionMode.Q6
            model.hiddenSize >= 4096 -> KvCacheCompressionMode.Q8
            else -> KvCacheCompressionMode.NONE
        }
    }

    private fun requiredHooks(singleNode: Boolean): List<String> {
        return buildList {
            add("gguf_execute_layer_range(startLayer, endLayer, hiddenStateHandle, kvCacheHandle)")
            add("gguf_serialize_hidden_state() / gguf_deserialize_hidden_state()")
            add("gguf_reserve_kv_cache(compressionMode)")
            if (singleNode) {
                add("gguf_load_layer_window(startLayer, endLayer)")
                add("gguf_evict_layer_window(startLayer, endLayer)")
            } else {
                add("remote_segment_execute(requestEnvelope)")
                add("pipeline_session_resume(sessionId, stepId)")
            }
        }
    }
}
