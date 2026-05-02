package com.santiya.localaihub.windows;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class WindowsHubService {
    private final int port;
    private final Path modelsDir;
    private final PeerRegistry peerRegistry;
    private final WindowsNodeResourceCollector resourceCollector = new WindowsNodeResourceCollector();
    private final GgufPlanner planner = new GgufPlanner();
    private volatile HttpServer server;
    private volatile List<ModelEntry> cachedModels = List.of();
    private volatile List<PeerNodeConfig> cachedPeers = List.of();

    WindowsHubService(int port, Path modelsDir, Path peersFile) {
        this.port = port;
        this.modelsDir = modelsDir;
        this.peerRegistry = new PeerRegistry(peersFile);
    }

    void start() throws IOException {
        Files.createDirectories(modelsDir);
        refreshState();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.createContext("/health", exchange -> writeJson(exchange, 200, Map.of(
            "ok", true,
            "app", "SantiyaLocalAiHub Windows Hub",
            "state", "running",
            "timestamp", Instant.now().toString()
        )));
        server.createContext("/api/status", exchange -> writeJson(exchange, 200, statusPayload()));
        server.createContext("/api/models", exchange -> writeJson(exchange, 200, modelsPayload()));
        server.createContext("/api/nodes", exchange -> writeJson(exchange, 200, nodesPayload()));
        server.createContext("/api/reload", exchange -> {
            refreshState();
            writeJson(exchange, 200, Map.of(
                "ok", true,
                "modelCount", cachedModels.size(),
                "peerCount", cachedPeers.size(),
                "message", "State reloaded."
            ));
        });
        server.createContext("/api/plan", exchange -> {
            Map<String, String> query = parseQuery(exchange.getRequestURI());
            String modelId = query.get("modelId");
            ModelEntry model = modelId == null
                ? cachedModels.stream().findFirst().orElse(null)
                : cachedModels.stream().filter(item -> item.id().equals(modelId)).findFirst().orElse(null);
            if (model == null) {
                writeJson(exchange, 404, Map.of(
                    "ok", false,
                    "error", "model_not_found",
                    "message", "No GGUF model is available for planning."
                ));
                return;
            }
            GgufModelProfile profile = planner.estimate(model);
            DistributedPartitionPlan plan = planner.planDistributed(profile, localNode(), peerSnapshots());
            writeJson(exchange, 200, Map.of(
                "ok", true,
                "modelId", model.id(),
                "plan", planPayload(plan)
            ));
        });
        server.start();
    }

    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    String statusLine() {
        return "Windows Hub running on http://127.0.0.1:" + port + " | models=" + cachedModels.size() + " | peers=" + cachedPeers.size();
    }

    private void refreshState() {
        cachedModels = scanModels();
        cachedPeers = peerRegistry.load();
    }

    private List<ModelEntry> scanModels() {
        if (!Files.exists(modelsDir)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.walk(modelsDir)) {
            return paths
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".gguf"))
                .sorted(Comparator.comparing(Path::toString))
                .map(path -> {
                    long sizeBytes;
                    try {
                        sizeBytes = Files.size(path);
                    } catch (IOException ignored) {
                        sizeBytes = 0L;
                    }
                    int sizeMb = (int) Math.max(1L, sizeBytes / (1024L * 1024L));
                    return new ModelEntry(
                        stableId(path),
                        path.getFileName().toString().replaceFirst("(?i)\\.gguf$", ""),
                        path,
                        sizeBytes,
                        sizeMb
                    );
                })
                .collect(Collectors.toList());
        } catch (IOException ignored) {
            return List.of();
        }
    }

    private String stableId(Path path) {
        return UUID.nameUUIDFromBytes(path.toAbsolutePath().toString().getBytes(StandardCharsets.UTF_8)).toString();
    }

    private NodeResourceSnapshot localNode() {
        return resourceCollector.snapshot(port);
    }

    private List<NodeResourceSnapshot> peerSnapshots() {
        List<NodeResourceSnapshot> nodes = new ArrayList<>();
        for (PeerNodeConfig peer : cachedPeers) {
            nodes.add(new NodeResourceSnapshot(
                "peer-" + peer.host() + "-" + peer.port(),
                peer.name(),
                DistributedNodePlatform.WINDOWS,
                peer.totalRamMb(),
                peer.freeRamMb(),
                peer.cpuCores(),
                "unknown",
                peer.acceleratorSummary(),
                1.0,
                peer.computeScore(),
                1024,
                true,
                true,
                peer.freeRamMb() > 2048,
                List.of(
                    DistributedTransportProtocol.MANUAL_HTTP,
                    DistributedTransportProtocol.LIBP2P_PLANNED
                ),
                Instant.now().toEpochMilli(),
                List.of("Manual Windows peer registry entry.")
            ));
        }
        return nodes;
    }

    private Map<String, Object> statusPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ok", true);
        payload.put("app", "SantiyaLocalAiHub Windows Hub");
        payload.put("port", port);
        payload.put("baseUrl", "http://127.0.0.1:" + port);
        payload.put("modelsDir", modelsDir.toAbsolutePath().toString());
        payload.put("modelCount", cachedModels.size());
        payload.put("peerCount", cachedPeers.size());
        payload.put("localNode", nodePayload(localNode()));
        payload.put("message", "Windows module exposes planning, model scan, and local HTTP integration.");
        return payload;
    }

    private List<Map<String, Object>> modelsPayload() {
        return cachedModels.stream().map(model -> {
            GgufModelProfile profile = planner.estimate(model);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", model.id());
            item.put("name", model.modelName());
            item.put("path", model.modelPath().toAbsolutePath().toString());
            item.put("sizeMb", model.fileSizeMb());
            item.put("architecture", profile.architecture());
            item.put("estimatedLayers", profile.layerCount());
            item.put("estimatedHiddenSize", profile.hiddenSize());
            item.put("estimatedContext", profile.contextLength());
            item.put("notes", profile.notes());
            return item;
        }).toList();
    }

    private List<Map<String, Object>> nodesPayload() {
        List<Map<String, Object>> nodes = new ArrayList<>();
        nodes.add(nodePayload(localNode()));
        for (NodeResourceSnapshot peer : peerSnapshots()) {
            nodes.add(nodePayload(peer));
        }
        return nodes;
    }

    private Map<String, Object> planPayload(DistributedPartitionPlan plan) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("strategy", plan.strategy().name().toLowerCase());
        payload.put("modelProfile", profilePayload(plan.modelProfile()));
        payload.put("localNode", nodePayload(plan.localNode()));
        payload.put("peerNodes", plan.peerNodes().stream().map(this::nodePayload).toList());
        payload.put("assignments", plan.assignments().stream().map(assignment -> Map.of(
            "nodeId", assignment.nodeId(),
            "nodeName", assignment.nodeName(),
            "role", assignment.role(),
            "startLayerInclusive", assignment.layerRange().startLayerInclusive(),
            "endLayerExclusive", assignment.layerRange().endLayerExclusive(),
            "estimatedResidentMb", assignment.estimatedResidentMb()
        )).toList());
        if (plan.sequentialOffloadPlan() != null) {
            payload.put("sequentialOffloadPlan", sequentialPayload(plan.sequentialOffloadPlan()));
        }
        if (plan.hiddenStateTransferPlan() != null) {
            payload.put("hiddenStateTransferPlan", Map.of(
                "compression", plan.hiddenStateTransferPlan().compression().name().toLowerCase(),
                "rawBytesPerToken", plan.hiddenStateTransferPlan().rawBytesPerToken(),
                "compressedBytesPerToken", plan.hiddenStateTransferPlan().compressedBytesPerToken(),
                "preferredTransport", plan.hiddenStateTransferPlan().preferredTransport().name().toLowerCase(),
                "notes", plan.hiddenStateTransferPlan().notes()
            ));
        }
        payload.put("nativeExecutionRequired", plan.nativeExecutionRequired());
        payload.put("requiredEngineHooks", plan.requiredEngineHooks());
        payload.put("warnings", plan.warnings());
        payload.put("notes", plan.notes());
        return payload;
    }

    private Map<String, Object> profilePayload(GgufModelProfile profile) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("modelId", profile.modelId());
        payload.put("modelName", profile.modelName());
        payload.put("architecture", profile.architecture());
        payload.put("estimatedParameterScaleB", profile.estimatedParameterScaleB());
        payload.put("layerCount", profile.layerCount());
        payload.put("hiddenSize", profile.hiddenSize());
        payload.put("attentionHeads", profile.attentionHeads());
        payload.put("kvHeads", profile.kvHeads());
        payload.put("contextLength", profile.contextLength());
        payload.put("modelSizeMb", profile.modelSizeMb());
        payload.put("estimatedLayerFootprintMb", profile.estimatedLayerFootprintMb());
        payload.put("estimatedHiddenStateBytesPerToken", profile.estimatedHiddenStateBytesPerToken());
        payload.put("heuristicProfile", profile.heuristicProfile());
        payload.put("notes", profile.notes());
        return payload;
    }

    private Map<String, Object> nodePayload(NodeResourceSnapshot snapshot) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("nodeId", snapshot.nodeId());
        payload.put("displayName", snapshot.displayName());
        payload.put("platform", snapshot.platform().name().toLowerCase());
        payload.put("totalRamMb", snapshot.totalRamMb());
        payload.put("freeRamMb", snapshot.freeRamMb());
        payload.put("cpuCores", snapshot.cpuCores());
        payload.put("cpuArch", snapshot.cpuArch());
        payload.put("acceleratorSummary", snapshot.acceleratorSummary());
        payload.put("acceleratorScore", snapshot.acceleratorScore());
        payload.put("computeScore", snapshot.computeScore());
        payload.put("availableStorageMb", snapshot.availableStorageMb());
        payload.put("supportsSequentialOffload", snapshot.supportsSequentialOffload());
        payload.put("supportsPipelineWorker", snapshot.supportsPipelineWorker());
        payload.put("heavySlotAvailable", snapshot.heavySlotAvailable());
        payload.put("transportProtocols", snapshot.transportProtocols().stream().map(item -> item.name().toLowerCase()).toList());
        payload.put("lastSeenEpochMs", snapshot.lastSeenEpochMs());
        payload.put("notes", snapshot.notes());
        return payload;
    }

    private Map<String, Object> sequentialPayload(SequentialOffloadPlan plan) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("enabled", plan.enabled());
        payload.put("residentLayerWindow", plan.residentLayerWindow());
        payload.put("estimatedPeakRamMb", plan.estimatedPeakRamMb());
        payload.put("estimatedKvCacheMb", plan.estimatedKvCacheMb());
        payload.put("estimatedFlashTrafficMbPerToken", plan.estimatedFlashTrafficMbPerToken());
        payload.put("kvCacheCompression", plan.kvCacheCompression().name().toLowerCase());
        payload.put("windows", plan.windows().stream().map(window -> Map.of(
            "phaseIndex", window.phaseIndex(),
            "targetDevice", window.targetDevice(),
            "startLayerInclusive", window.layerRange().startLayerInclusive(),
            "endLayerExclusive", window.layerRange().endLayerExclusive()
        )).toList());
        payload.put("notes", plan.notes());
        return payload;
    }

    private void writeJson(HttpExchange exchange, int status, Object payload) throws IOException {
        String body = JsonUtil.stringify(payload);
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(bytes);
        }
    }

    private Map<String, String> parseQuery(URI uri) {
        Map<String, String> query = new HashMap<>();
        String rawQuery = uri.getRawQuery();
        if (rawQuery == null || rawQuery.isBlank()) {
            return query;
        }
        for (String part : rawQuery.split("&")) {
            if (part.isBlank()) continue;
            String[] pieces = part.split("=", 2);
            String key = decode(pieces[0]);
            String value = pieces.length > 1 ? decode(pieces[1]) : "";
            query.put(key, value);
        }
        return query;
    }

    private String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
