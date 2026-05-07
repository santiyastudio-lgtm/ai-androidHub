package com.santiya.localaihub.windows;

import java.nio.file.Path;
import java.util.List;

enum DistributedNodePlatform {
    ANDROID,
    MACOS,
    WINDOWS,
    LINUX,
    UNKNOWN
}

enum DistributedTransportProtocol {
    MANUAL_HTTP,
    NSD_HTTP,
    NSD_LIBP2P,
    LIBP2P_PLANNED,
    NSD_PLANNED
}

enum DistributedExecutionStrategy {
    LOCAL_ONLY,
    SEQUENTIAL_OFFLOAD,
    PIPELINE_PARALLEL_LAN
}

enum KvCacheCompressionMode {
    NONE,
    Q8,
    Q6,
    Q4
}

record GgufLayerRange(int startLayerInclusive, int endLayerExclusive) {
    int layerCount() {
        return Math.max(0, endLayerExclusive - startLayerInclusive);
    }
}

record ModelEntry(
    String id,
    String modelName,
    Path modelPath,
    long fileSizeBytes,
    int fileSizeMb
) {}

record GgufModelProfile(
    String modelId,
    String modelName,
    String architecture,
    Double estimatedParameterScaleB,
    int layerCount,
    int hiddenSize,
    int attentionHeads,
    int kvHeads,
    int contextLength,
    int modelSizeMb,
    double estimatedLayerFootprintMb,
    int estimatedHiddenStateBytesPerToken,
    boolean heuristicProfile,
    List<String> notes
) {}

record NodeResourceSnapshot(
    String nodeId,
    String displayName,
    DistributedNodePlatform platform,
    int totalRamMb,
    int freeRamMb,
    int cpuCores,
    String cpuArch,
    String acceleratorSummary,
    double acceleratorScore,
    double computeScore,
    int availableStorageMb,
    boolean supportsSequentialOffload,
    boolean supportsPipelineWorker,
    boolean heavySlotAvailable,
    List<DistributedTransportProtocol> transportProtocols,
    long lastSeenEpochMs,
    List<String> notes
) {}

record SequentialOffloadWindow(
    int phaseIndex,
    GgufLayerRange layerRange,
    String targetDevice
) {}

record SequentialOffloadPlan(
    boolean enabled,
    int residentLayerWindow,
    int estimatedPeakRamMb,
    int estimatedKvCacheMb,
    double estimatedFlashTrafficMbPerToken,
    KvCacheCompressionMode kvCacheCompression,
    List<SequentialOffloadWindow> windows,
    List<String> notes
) {}

record HiddenStateTransferPlan(
    KvCacheCompressionMode compression,
    int rawBytesPerToken,
    int compressedBytesPerToken,
    DistributedTransportProtocol preferredTransport,
    List<String> notes
) {}

record PipelineSegmentAssignment(
    String nodeId,
    String nodeName,
    GgufLayerRange layerRange,
    String role,
    int estimatedResidentMb
) {}

record DistributedPartitionPlan(
    DistributedExecutionStrategy strategy,
    GgufModelProfile modelProfile,
    NodeResourceSnapshot localNode,
    List<NodeResourceSnapshot> peerNodes,
    List<PipelineSegmentAssignment> assignments,
    SequentialOffloadPlan sequentialOffloadPlan,
    HiddenStateTransferPlan hiddenStateTransferPlan,
    boolean nativeExecutionRequired,
    List<String> requiredEngineHooks,
    List<String> warnings,
    List<String> notes
) {}

record PeerNodeConfig(
    String name,
    String host,
    int port,
    int freeRamMb,
    int totalRamMb,
    int cpuCores,
    double computeScore,
    String acceleratorSummary
) {}

record DiscoveredLanNode(
    String host,
    int port,
    NodeResourceSnapshot snapshot,
    List<String> supportedModels,
    boolean pairingRequired
) {}
