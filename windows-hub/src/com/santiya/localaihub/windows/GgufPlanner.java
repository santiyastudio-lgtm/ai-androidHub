package com.santiya.localaihub.windows;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class GgufPlanner {

    GgufModelProfile estimate(ModelEntry model) {
        int sizeMb = Math.max(1, model.fileSizeMb());
        Double scaleB = inferParameterScaleB(model.modelName());
        int layerCount = inferLayerCount(scaleB);
        int hiddenSize = inferHiddenSize(scaleB);
        int heads = inferAttentionHeads(hiddenSize);
        int kvHeads = inferKvHeads(scaleB, heads);
        double layerFootprint = sizeMb / (double) (layerCount + 2);
        int hiddenStateBytesPerToken = hiddenSize * 2;
        List<String> notes = new ArrayList<>();
        notes.add("Profile is heuristic until native GGUF metadata is exposed on Windows.");
        if (scaleB != null) {
            notes.add(String.format(Locale.US, "Estimated parameter scale: %.1fB", scaleB));
        }
        return new GgufModelProfile(
            model.id(),
            model.modelName(),
            inferArchitecture(model.modelName()),
            scaleB,
            layerCount,
            hiddenSize,
            heads,
            kvHeads,
            inferContextLength(model.modelName()),
            sizeMb,
            layerFootprint,
            hiddenStateBytesPerToken,
            true,
            notes
        );
    }

    SequentialOffloadPlan planSequentialOffload(GgufModelProfile model, NodeResourceSnapshot node) {
        return planSequentialOffload(model, node, Math.min(model.contextLength(), 8192));
    }

    SequentialOffloadPlan planSequentialOffload(
        GgufModelProfile model,
        NodeResourceSnapshot node,
        int requestedContextLength
    ) {
        KvCacheCompressionMode compression = chooseCompression(node);
        int kvBytesPerToken = switch (compression) {
            case NONE -> model.estimatedHiddenStateBytesPerToken();
            case Q8 -> model.hiddenSize();
            case Q6 -> (int) (model.hiddenSize() * 0.75);
            case Q4 -> (int) (model.hiddenSize() * 0.5);
        };
        int estimatedKvCacheMb = (int) ((requestedContextLength * (long) kvBytesPerToken) / (1024.0 * 1024.0));
        int reservedSystemMb = Math.max(768, (int) (node.totalRamMb() * 0.20));
        int availableForLayersMb = Math.max(256, node.freeRamMb() - reservedSystemMb - estimatedKvCacheMb);
        int residentLayerWindow = (int) Math.floor(availableForLayersMb / model.estimatedLayerFootprintMb());
        residentLayerWindow = Math.max(1, Math.min(model.layerCount(), residentLayerWindow));
        int phases = (int) Math.ceil(model.layerCount() / (double) residentLayerWindow);

        List<SequentialOffloadWindow> windows = new ArrayList<>();
        for (int index = 0; index < phases; index++) {
            int start = index * residentLayerWindow;
            int end = Math.min(model.layerCount(), start + residentLayerWindow);
            windows.add(new SequentialOffloadWindow(index, new GgufLayerRange(start, end), "local"));
        }

        int estimatedPeakRamMb = (int) (reservedSystemMb + estimatedKvCacheMb + residentLayerWindow * model.estimatedLayerFootprintMb());
        double flashTrafficMbPerToken = phases <= 1
            ? 0.0
            : Math.max(model.estimatedLayerFootprintMb(), model.modelSizeMb() / (double) phases);

        List<String> notes = new ArrayList<>();
        if (phases > 1) {
            notes.add("Model exceeds resident memory budget and should be paged layer-window by layer-window.");
        } else {
            notes.add("Model can stay resident locally without sequential layer paging.");
        }
        notes.add("This plan still requires native Windows GGUF hooks for per-range load and evict.");

        return new SequentialOffloadPlan(
            phases > 1,
            residentLayerWindow,
            estimatedPeakRamMb,
            estimatedKvCacheMb,
            flashTrafficMbPerToken,
            compression,
            windows,
            notes
        );
    }

    DistributedPartitionPlan planDistributed(
        GgufModelProfile model,
        NodeResourceSnapshot localNode,
        List<NodeResourceSnapshot> peerNodes
    ) {
        List<NodeResourceSnapshot> eligiblePeers = peerNodes.stream()
            .filter(NodeResourceSnapshot::supportsPipelineWorker)
            .filter(NodeResourceSnapshot::heavySlotAvailable)
            .toList();
        if (eligiblePeers.isEmpty()) {
            return new DistributedPartitionPlan(
                DistributedExecutionStrategy.SEQUENTIAL_OFFLOAD,
                model,
                localNode,
                List.of(),
                List.of(new PipelineSegmentAssignment(
                    localNode.nodeId(),
                    localNode.displayName(),
                    new GgufLayerRange(0, model.layerCount()),
                    "root",
                    model.modelSizeMb()
                )),
                planSequentialOffload(model, localNode),
                null,
                true,
                requiredHooks(true),
                List.of("No eligible LAN workers were available. Planner fell back to local sequential offload."),
                List.of("Sequential offload is the honest local fallback until remote workers expose compatible execution hooks.")
            );
        }

        List<NodeResourceSnapshot> allNodes = new ArrayList<>();
        allNodes.add(localNode);
        allNodes.addAll(eligiblePeers);

        List<Integer> allocations = computeAllocations(model.layerCount(), allNodes);
        int cursor = 0;
        List<PipelineSegmentAssignment> assignments = new ArrayList<>();
        for (int i = 0; i < allNodes.size(); i++) {
            NodeResourceSnapshot node = allNodes.get(i);
            int layersForNode = allocations.get(i);
            int end = Math.min(model.layerCount(), cursor + layersForNode);
            GgufLayerRange range = new GgufLayerRange(cursor, end);
            cursor = end;
            if (range.layerCount() > 0) {
                assignments.add(new PipelineSegmentAssignment(
                    node.nodeId(),
                    node.displayName(),
                    range,
                    i == 0 ? "root" : "worker",
                    (int) Math.round(range.layerCount() * model.estimatedLayerFootprintMb())
                ));
            }
        }

        KvCacheCompressionMode compression = chooseNetworkCompression(model);
        int compressedBytesPerToken = switch (compression) {
            case NONE -> model.estimatedHiddenStateBytesPerToken();
            case Q8 -> model.hiddenSize();
            case Q6 -> (int) (model.hiddenSize() * 0.75);
            case Q4 -> (int) (model.hiddenSize() * 0.5);
        };

        List<String> warnings = new ArrayList<>();
        warnings.add("Planner output becomes executable only after native GGUF hooks expose layer-range forward and hidden-state serialization.");
        boolean missingLibp2p = eligiblePeers.stream()
            .anyMatch(node -> !node.transportProtocols().contains(DistributedTransportProtocol.LIBP2P_PLANNED));
        if (missingLibp2p) {
            warnings.add("One or more nodes do not advertise libp2p yet. Manual HTTP fallback is slower and less portable.");
        }

        return new DistributedPartitionPlan(
            DistributedExecutionStrategy.PIPELINE_PARALLEL_LAN,
            model,
            localNode,
            eligiblePeers,
            assignments,
            null,
            new HiddenStateTransferPlan(
                compression,
                model.estimatedHiddenStateBytesPerToken(),
                compressedBytesPerToken,
                DistributedTransportProtocol.LIBP2P_PLANNED,
                List.of(
                    "libp2p remains the preferred future transport because it can be shared across Android, macOS, and Windows.",
                    "Compression estimate targets hidden-state handoff; native serialization hooks are still required."
                )
            ),
            true,
            requiredHooks(false),
            warnings,
            List.of(
                "Assignments are contiguous transformer layer spans to keep pipeline semantics simple.",
                "Root keeps the first segment and coordinates token loop plus KV-cache policy."
            )
        );
    }

    private List<Integer> computeAllocations(int layerCount, List<NodeResourceSnapshot> nodes) {
        List<Double> capacities = nodes.stream().map(this::effectiveCapacity).toList();
        double total = capacities.stream().mapToDouble(Double::doubleValue).sum();
        if (total <= 0.0) {
            total = nodes.size();
        }
        List<Integer> allocations = new ArrayList<>();
        for (double capacity : capacities) {
            allocations.add(Math.max(1, (int) Math.round(layerCount * (capacity / total))));
        }
        normalizeAllocations(allocations, layerCount);
        return allocations;
    }

    private void normalizeAllocations(List<Integer> allocations, int targetTotal) {
        int diff = targetTotal - allocations.stream().mapToInt(Integer::intValue).sum();
        while (diff != 0 && !allocations.isEmpty()) {
            for (int i = 0; i < allocations.size() && diff != 0; i++) {
                int value = allocations.get(i);
                if (diff > 0) {
                    allocations.set(i, value + 1);
                    diff--;
                } else if (value > 1) {
                    allocations.set(i, value - 1);
                    diff++;
                }
            }
        }
    }

    private double effectiveCapacity(NodeResourceSnapshot node) {
        double ramFactor = node.freeRamMb() / 1024.0;
        double cpuFactor = node.cpuCores() * 0.8;
        double accelFactor = node.acceleratorScore() * 1.2;
        return ramFactor + cpuFactor + accelFactor;
    }

    private KvCacheCompressionMode chooseCompression(NodeResourceSnapshot node) {
        if (node.freeRamMb() < 4096) {
            return KvCacheCompressionMode.Q4;
        }
        if (node.freeRamMb() < 6144) {
            return KvCacheCompressionMode.Q6;
        }
        if (node.freeRamMb() < 8192) {
            return KvCacheCompressionMode.Q8;
        }
        return KvCacheCompressionMode.NONE;
    }

    private KvCacheCompressionMode chooseNetworkCompression(GgufModelProfile model) {
        if (model.hiddenSize() >= 8192) {
            return KvCacheCompressionMode.Q6;
        }
        if (model.hiddenSize() >= 4096) {
            return KvCacheCompressionMode.Q8;
        }
        return KvCacheCompressionMode.NONE;
    }

    private List<String> requiredHooks(boolean singleNode) {
        List<String> hooks = new ArrayList<>();
        hooks.add("gguf_execute_layer_range(startLayer, endLayer, hiddenStateHandle, kvCacheHandle)");
        hooks.add("gguf_serialize_hidden_state() / gguf_deserialize_hidden_state()");
        hooks.add("gguf_reserve_kv_cache(compressionMode)");
        if (singleNode) {
            hooks.add("gguf_load_layer_window(startLayer, endLayer)");
            hooks.add("gguf_evict_layer_window(startLayer, endLayer)");
        } else {
            hooks.add("remote_segment_execute(requestEnvelope)");
            hooks.add("pipeline_session_resume(sessionId, stepId)");
        }
        return hooks;
    }

    private String inferArchitecture(String modelName) {
        String lower = modelName.toLowerCase(Locale.ROOT);
        if (lower.contains("qwen")) return "qwen";
        if (lower.contains("llama")) return "llama";
        if (lower.contains("mistral")) return "mistral";
        if (lower.contains("gemma")) return "gemma";
        return "gguf";
    }

    private Double inferParameterScaleB(String modelName) {
        var match = java.util.regex.Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*([bm])", java.util.regex.Pattern.CASE_INSENSITIVE)
            .matcher(modelName);
        if (!match.find()) {
            return null;
        }
        double value = Double.parseDouble(match.group(1));
        String suffix = match.group(2).toLowerCase(Locale.ROOT);
        return switch (suffix) {
            case "b" -> value;
            case "m" -> value / 1000.0;
            default -> null;
        };
    }

    private int inferLayerCount(Double scaleB) {
        if (scaleB == null) return 32;
        if (scaleB <= 1.0) return 16;
        if (scaleB <= 2.0) return 24;
        if (scaleB <= 4.0) return 28;
        if (scaleB <= 9.0) return 32;
        if (scaleB <= 14.0) return 40;
        if (scaleB <= 35.0) return 48;
        if (scaleB <= 72.0) return 80;
        return 120;
    }

    private int inferHiddenSize(Double scaleB) {
        if (scaleB == null) return 4096;
        if (scaleB <= 2.0) return 2048;
        if (scaleB <= 4.0) return 2560;
        if (scaleB <= 9.0) return 4096;
        if (scaleB <= 14.0) return 5120;
        if (scaleB <= 35.0) return 6656;
        if (scaleB <= 72.0) return 8192;
        return 12288;
    }

    private int inferAttentionHeads(int hiddenSize) {
        return Math.max(8, hiddenSize / 128);
    }

    private int inferKvHeads(Double scaleB, int attentionHeads) {
        if (scaleB == null) return Math.max(8, attentionHeads / 4);
        if (scaleB <= 4.0) return Math.max(8, attentionHeads / 2);
        if (scaleB <= 14.0) return Math.max(8, attentionHeads / 4);
        return Math.max(8, attentionHeads / 8);
    }

    private int inferContextLength(String modelName) {
        String lower = modelName.toLowerCase(Locale.ROOT);
        if (lower.contains("128k")) return 131072;
        if (lower.contains("64k")) return 65536;
        if (lower.contains("32k")) return 32768;
        if (lower.contains("16k")) return 16384;
        return 8192;
    }
}
