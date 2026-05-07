package com.santiya.localaihub.windows;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class WindowsHubService {
    private static final int HTTP_TIMEOUT_MS = 350;

    private final int port;
    private final Path modelsDir;
    private final PeerRegistry peerRegistry;
    private final WindowsNodeResourceCollector resourceCollector = new WindowsNodeResourceCollector();
    private final GgufPlanner planner = new GgufPlanner();
    private final String pairingToken;
    private final int androidNodePort;
    private final boolean lanEnabled;
    private final boolean advertiseLocalNode;
    private final String coreBaseUrl;

    private volatile HttpServer server;
    private volatile List<ModelEntry> cachedModels = List.of();
    private volatile List<PeerNodeConfig> cachedPeers = List.of();
    private volatile List<DiscoveredLanNode> cachedAndroidNodes = List.of();

    WindowsHubService(int port, Path modelsDir, Path peersFile, String pairingToken, int androidNodePort) {
        this(port, modelsDir, peersFile, pairingToken, androidNodePort, "", true, true);
    }

    WindowsHubService(int port, Path modelsDir, Path peersFile, String pairingToken, int androidNodePort, String coreBaseUrl) {
        this(port, modelsDir, peersFile, pairingToken, androidNodePort, coreBaseUrl, true, true);
    }

    WindowsHubService(
        int port,
        Path modelsDir,
        Path peersFile,
        String pairingToken,
        int androidNodePort,
        String coreBaseUrl,
        boolean lanEnabled,
        boolean advertiseLocalNode
    ) {
        this.port = port;
        this.modelsDir = modelsDir;
        this.peerRegistry = new PeerRegistry(peersFile);
        this.pairingToken = pairingToken == null ? "" : pairingToken.trim();
        this.androidNodePort = androidNodePort;
        this.coreBaseUrl = normalizeCoreBaseUrl(coreBaseUrl);
        this.lanEnabled = lanEnabled;
        this.advertiseLocalNode = advertiseLocalNode;
    }

    void start() throws IOException {
        Files.createDirectories(modelsDir);
        refreshState();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.createContext("/", this::handleDashboard);
        server.createContext("/health", exchange -> writeJson(exchange, 200, Map.of(
            "ok", true,
            "app", "SantiyaLocalAiHub Windows Hub",
            "state", "running",
            "timestamp", Instant.now().toString()
        )));
        server.createContext("/api/status", exchange -> {
            if (proxyIfConfigured(exchange, "/api/status")) {
                return;
            }
            writeJson(exchange, 200, statusPayload());
        });
        server.createContext("/api/models", exchange -> {
            if (proxyIfConfigured(exchange, "/api/models")) {
                return;
            }
            writeJson(exchange, 200, modelsPayload());
        });
        server.createContext("/api/nodes", exchange -> {
            if (proxyIfConfigured(exchange, "/api/lan/nodes")) {
                return;
            }
            writeJson(exchange, 200, nodesPayload());
        });
        server.createContext("/api/lan/nodes", exchange -> {
            if (proxyIfConfigured(exchange, "/api/lan/nodes")) {
                return;
            }
            writeJson(exchange, 200, nodesPayload());
        });
        server.createContext("/api/runtimes", exchange -> proxyRequired(exchange, "/api/runtimes"));
        server.createContext("/api/catalog", exchange -> proxyRequired(exchange, "/api/catalog"));
        server.createContext("/api/setup/recommendation", exchange -> proxyRequired(exchange, "/api/setup/recommendation"));
        server.createContext("/api/plugins", exchange -> proxyRequired(exchange, "/api/plugins"));
        server.createContext("/api/tools", exchange -> proxyRequired(exchange, "/api/tools"));
        server.createContext("/api/tool-state", exchange -> proxyRequired(exchange, "/api/tool-state"));
        server.createContext("/api/preferred-models", exchange -> proxyRequired(exchange, "/api/preferred-models"));
        server.createContext("/api/external-access", exchange -> proxyRequired(exchange, "/api/external-access"));
        server.createContext("/api/orchestra", exchange -> proxyRequired(exchange, "/api/orchestra"));
        server.createContext("/api/chat/generate", exchange -> proxyRequired(exchange, "/api/chat/generate"));
        server.createContext("/api/rag/install", exchange -> proxyRequired(exchange, "/api/rag/install"));
        server.createContext("/api/rag/query", exchange -> proxyRequired(exchange, "/api/rag/query"));
        server.createContext("/api/tts/speak", exchange -> proxyRequired(exchange, "/api/tts/speak"));
        server.createContext("/api/image/generate", exchange -> proxyRequired(exchange, "/api/image/generate"));
        server.createContext("/api/reload", exchange -> {
            if (proxyIfConfigured(exchange, "/api/reload")) {
                return;
            }
            refreshState();
            writeJson(exchange, 200, Map.of(
                "ok", true,
                "modelCount", cachedModels.size(),
                "peerCount", cachedPeers.size(),
                "discoveredLanNodes", cachedAndroidNodes.size(),
                "message", "State reloaded."
            ));
        });
        server.createContext("/api/plan", exchange -> {
            if (proxyIfConfigured(exchange, appendQuery("/api/plan", exchange.getRequestURI()))) {
                return;
            }
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
        server.createContext("/api/lan/execute", this::handleLanExecute);
        server.start();
    }

    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    String statusLine() {
        return "Windows Hub running on http://127.0.0.1:" + port
            + " | models=" + cachedModels.size()
            + " | manualPeers=" + cachedPeers.size()
            + " | androidNodes=" + cachedAndroidNodes.size()
            + " | core=" + (coreBaseUrl.isBlank() ? "local-java" : coreBaseUrl);
    }

    private void handleDashboard(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestURI().getPath().equals("/")) {
            writeJson(exchange, 404, Map.of("ok", false, "error", "not_found"));
            return;
        }
        writeHtml(exchange, 200, dashboardHtml());
    }

    private void handleLanExecute(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
            writeJson(exchange, 405, Map.of("ok", false, "error", "method_not_allowed"));
            return;
        }
        if (!lanEnabled) {
            writeJson(exchange, 400, Map.of(
                "ok", false,
                "error", "lan_disabled",
                "message", "Enable LAN Hub in desktop settings before forwarding Android execution jobs."
            ));
            return;
        }
        if (pairingToken.isBlank()) {
            writeJson(exchange, 400, Map.of(
                "ok", false,
                "error", "pairing_token_missing",
                "message", "Restart Windows Hub with --pairing-token=<shared token> to execute Android LAN jobs."
            ));
            return;
        }

        Map<String, String> form = parseForm(readBody(exchange));
        String host = form.getOrDefault("host", "").trim();
        String capability = form.getOrDefault("capability", "chat").trim();
        String prompt = form.getOrDefault("prompt", "").trim();
        String systemPrompt = form.getOrDefault("systemPrompt", "").trim();
        String mode = form.getOrDefault("orchestrationMode", "single_model").trim();
        int remotePort = safeParseInt(form.getOrDefault("remotePort", ""), androidNodePort);

        if (host.isBlank() || prompt.isBlank()) {
            writeJson(exchange, 400, Map.of(
                "ok", false,
                "error", "invalid_request",
                "message", "Both host and prompt are required."
            ));
            return;
        }

        LinkedHashMap<String, Object> inputPayload = new LinkedHashMap<>();
        inputPayload.put("prompt", prompt);

        LinkedHashMap<String, Object> executionPayload = new LinkedHashMap<>();
        executionPayload.put("capability", capability);
        executionPayload.put("modelId", blankToNull(form.get("modelId")));
        executionPayload.put("systemPrompt", blankToNull(systemPrompt));
        executionPayload.put("inputJson", JsonUtil.stringify(inputPayload));
        executionPayload.put("orchestrationMode", mode.isBlank() ? "single_model" : mode);

        ResponseEnvelope response = postJson(
            "http://" + host + ":" + remotePort + "/hub/lan/execute",
            JsonUtil.stringify(executionPayload),
            pairingToken
        );
        writeJsonString(exchange, response.statusCode() >= 200 && response.statusCode() < 300 ? 200 : response.statusCode(), response.body());
    }

    private void refreshState() {
        cachedModels = scanModels();
        cachedPeers = peerRegistry.load();
        cachedAndroidNodes = scanAndroidNodes();
    }

    private boolean proxyIfConfigured(HttpExchange exchange, String remotePath) throws IOException {
        if (coreBaseUrl.isBlank()) {
            return false;
        }
        proxyRequest(exchange, remotePath);
        return true;
    }

    private void proxyRequired(HttpExchange exchange, String remotePath) throws IOException {
        if (coreBaseUrl.isBlank()) {
            writeJson(exchange, 501, Map.of(
                "ok", false,
                "error", "native_core_required",
                "message", "Restart Windows Hub with --core-url=http://127.0.0.1:17861 after launching windows-core."
            ));
            return;
        }
        proxyRequest(exchange, remotePath);
    }

    private void proxyRequest(HttpExchange exchange, String remotePath) throws IOException {
        String body = readBody(exchange);
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        ResponseEnvelope response = request(
            coreBaseUrl + remotePath,
            exchange.getRequestMethod(),
            contentType,
            body.isBlank() ? null : body,
            null
        );
        writeJsonString(
            exchange,
            response.statusCode() >= 200 && response.statusCode() < 300 ? 200 : response.statusCode(),
            response.body()
        );
    }

    private List<ModelEntry> scanModels() {
        if (!Files.exists(modelsDir)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.walk(modelsDir)) {
            return paths
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".gguf"))
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

    private List<DiscoveredLanNode> scanAndroidNodes() {
        if (!lanEnabled) {
            return List.of();
        }
        List<String> candidates = collectCandidateHosts();
        Set<String> manualHosts = cachedPeers.stream().map(PeerNodeConfig::host).collect(Collectors.toSet());
        var executor = Executors.newFixedThreadPool(24);
        try {
            List<Future<DiscoveredLanNode>> futures = new ArrayList<>();
            for (String host : candidates) {
                if (manualHosts.contains(host)) {
                    continue;
                }
                futures.add(executor.submit(new ProbeTask(host)));
            }
            List<DiscoveredLanNode> nodes = new ArrayList<>();
            for (Future<DiscoveredLanNode> future : futures) {
                try {
                    DiscoveredLanNode node = future.get();
                    if (node != null) {
                        nodes.add(node);
                    }
                } catch (Exception ignored) {
                }
            }
            return nodes.stream()
                .sorted(Comparator.comparing(item -> item.snapshot().displayName().toLowerCase(Locale.ROOT)))
                .toList();
        } finally {
            executor.shutdownNow();
        }
    }

    private List<String> collectCandidateHosts() {
        LinkedHashSet<String> hosts = new LinkedHashSet<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (!iface.isUp() || iface.isLoopback() || iface.isVirtual()) {
                    continue;
                }
                for (var address : iface.getInterfaceAddresses()) {
                    if (!(address.getAddress() instanceof Inet4Address ipv4)) {
                        continue;
                    }
                    if (!ipv4.isSiteLocalAddress()) {
                        continue;
                    }
                    String[] octets = ipv4.getHostAddress().split("\\.");
                    if (octets.length != 4) {
                        continue;
                    }
                    int localHost = safeParseInt(octets[3], -1);
                    String prefix = octets[0] + "." + octets[1] + "." + octets[2] + ".";
                    for (int index = 1; index <= 254; index++) {
                        if (index == localHost) {
                            continue;
                        }
                        hosts.add(prefix + index);
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return new ArrayList<>(hosts);
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
        for (DiscoveredLanNode node : cachedAndroidNodes) {
            nodes.add(node.snapshot());
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
        payload.put("discoveredLanNodes", cachedAndroidNodes.size());
        payload.put("androidNodePort", androidNodePort);
        payload.put("coreBaseUrl", coreBaseUrl);
        payload.put("nativeCoreProxyEnabled", !coreBaseUrl.isBlank());
        payload.put("lanEnabled", lanEnabled);
        payload.put("advertiseLocalNode", advertiseLocalNode);
        payload.put("pairingTokenConfigured", !pairingToken.isBlank());
        payload.put("localNode", nodePayload(localNode(), "local", "127.0.0.1", port, List.of()));
        payload.put("message", lanEnabled
            ? "Windows hub scans the LAN for Android nodes and can forward authenticated execution requests."
            : "LAN hub is disabled in desktop settings. Local planning remains available.");
        return payload;
    }

    String baseUrl() {
        return "http://127.0.0.1:" + port;
    }

    void reloadFromDesktop() {
        refreshState();
    }

    Map<String, Object> desktopStatusPayload() {
        return statusPayload();
    }

    List<Map<String, Object>> desktopModelsPayload() {
        return modelsPayload();
    }

    List<Map<String, Object>> desktopNodesPayload() {
        return nodesPayload();
    }

    Map<String, Object> desktopPlanPayload(String modelId) {
        ModelEntry model = modelId == null || modelId.isBlank()
            ? cachedModels.stream().findFirst().orElse(null)
            : cachedModels.stream().filter(item -> item.id().equals(modelId)).findFirst().orElse(null);
        if (model == null) {
            return Map.of(
                "ok", false,
                "error", "model_not_found",
                "message", "No GGUF model is available for planning."
            );
        }
        GgufModelProfile profile = planner.estimate(model);
        DistributedPartitionPlan plan = planner.planDistributed(profile, localNode(), peerSnapshots());
        return Map.of(
            "ok", true,
            "modelId", model.id(),
            "plan", planPayload(plan)
        );
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
        nodes.add(nodePayload(localNode(), "local", "127.0.0.1", port, List.of()));
        for (PeerNodeConfig peer : cachedPeers) {
            NodeResourceSnapshot snapshot = new NodeResourceSnapshot(
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
                List.of(DistributedTransportProtocol.MANUAL_HTTP, DistributedTransportProtocol.LIBP2P_PLANNED),
                Instant.now().toEpochMilli(),
                List.of("Manual Windows peer registry entry.")
            );
            nodes.add(nodePayload(snapshot, "manual_peer", peer.host(), peer.port(), List.of()));
        }
        for (DiscoveredLanNode discovered : cachedAndroidNodes) {
            nodes.add(nodePayload(
                discovered.snapshot(),
                "discovered_lan",
                discovered.host(),
                discovered.port(),
                discovered.supportedModels()
            ));
        }
        return nodes;
    }

    private Map<String, Object> planPayload(DistributedPartitionPlan plan) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("strategy", plan.strategy().name().toLowerCase(Locale.ROOT));
        payload.put("modelProfile", profilePayload(plan.modelProfile()));
        payload.put("localNode", nodePayload(plan.localNode(), "local", "127.0.0.1", port, List.of()));
        payload.put("peerNodes", plan.peerNodes().stream().map(node -> nodePayload(node, "peer", null, null, List.of())).toList());
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
                "compression", plan.hiddenStateTransferPlan().compression().name().toLowerCase(Locale.ROOT),
                "rawBytesPerToken", plan.hiddenStateTransferPlan().rawBytesPerToken(),
                "compressedBytesPerToken", plan.hiddenStateTransferPlan().compressedBytesPerToken(),
                "preferredTransport", plan.hiddenStateTransferPlan().preferredTransport().name().toLowerCase(Locale.ROOT),
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

    private Map<String, Object> nodePayload(
        NodeResourceSnapshot snapshot,
        String source,
        String host,
        Integer nodePort,
        List<String> supportedModels
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("nodeId", snapshot.nodeId());
        payload.put("displayName", snapshot.displayName());
        payload.put("platform", snapshot.platform().name().toLowerCase(Locale.ROOT));
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
        payload.put("transportProtocols", snapshot.transportProtocols().stream().map(item -> item.name().toLowerCase(Locale.ROOT)).toList());
        payload.put("lastSeenEpochMs", snapshot.lastSeenEpochMs());
        payload.put("notes", snapshot.notes());
        payload.put("source", source);
        payload.put("host", host);
        payload.put("port", nodePort);
        payload.put("supportedModels", supportedModels);
        return payload;
    }

    private Map<String, Object> sequentialPayload(SequentialOffloadPlan plan) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("enabled", plan.enabled());
        payload.put("residentLayerWindow", plan.residentLayerWindow());
        payload.put("estimatedPeakRamMb", plan.estimatedPeakRamMb());
        payload.put("estimatedKvCacheMb", plan.estimatedKvCacheMb());
        payload.put("estimatedFlashTrafficMbPerToken", plan.estimatedFlashTrafficMbPerToken());
        payload.put("kvCacheCompression", plan.kvCacheCompression().name().toLowerCase(Locale.ROOT));
        payload.put("windows", plan.windows().stream().map(window -> Map.of(
            "phaseIndex", window.phaseIndex(),
            "targetDevice", window.targetDevice(),
            "startLayerInclusive", window.layerRange().startLayerInclusive(),
            "endLayerExclusive", window.layerRange().endLayerExclusive()
        )).toList());
        payload.put("notes", plan.notes());
        return payload;
    }

    private DiscoveredLanNode probeAndroidNode(String host) {
        ResponseEnvelope response = getJson("http://" + host + ":" + androidNodePort + "/hub/distributed/status");
        if (response.statusCode() != 200 || !response.body().contains("\"ok\":true")) {
            return null;
        }
        NodeResourceSnapshot snapshot = parseAndroidStatus(response.body(), host, androidNodePort);
        if (snapshot == null) {
            return null;
        }
        List<String> supportedModels = extractStringArray(response.body(), "supportedModels");
        boolean pairingRequired = extractBoolean(response.body(), "pairingRequired", true);
        return new DiscoveredLanNode(host, androidNodePort, snapshot, supportedModels, pairingRequired);
    }

    private NodeResourceSnapshot parseAndroidStatus(String json, String host, int nodePort) {
        String nodeId = extractString(json, "nodeId", "android-" + host.replace('.', '-'));
        String displayName = extractString(json, "displayName", "Android Node");
        String platform = extractString(json, "platform", "android");
        List<DistributedTransportProtocol> protocols = parseProtocols(extractStringArray(json, "transportProtocols"));
        List<String> notes = new ArrayList<>(extractStringArray(json, "notes"));
        notes.add("Discovered over LAN HTTP from " + host + ":" + nodePort + ".");

        return new NodeResourceSnapshot(
            nodeId,
            displayName,
            switch (platform.toLowerCase(Locale.ROOT)) {
                case "windows" -> DistributedNodePlatform.WINDOWS;
                case "linux" -> DistributedNodePlatform.LINUX;
                case "macos" -> DistributedNodePlatform.MACOS;
                default -> DistributedNodePlatform.ANDROID;
            },
            extractInt(json, "totalRamMb", 0),
            extractInt(json, "freeRamMb", 0),
            extractInt(json, "cpuCores", 0),
            extractString(json, "cpuArch", "unknown"),
            extractString(json, "acceleratorSummary", "unknown"),
            extractDouble(json, "acceleratorScore", 0.0),
            extractDouble(json, "computeScore", 0.0),
            extractInt(json, "availableStorageMb", 0),
            extractBoolean(json, "supportsSequentialOffload", false),
            extractBoolean(json, "supportsPipelineWorker", false),
            extractBoolean(json, "heavySlotAvailable", false),
            protocols.isEmpty() ? List.of(DistributedTransportProtocol.NSD_HTTP) : protocols,
            extractLong(json, "lastSeenEpochMs", System.currentTimeMillis()),
            notes
        );
    }

    private List<DistributedTransportProtocol> parseProtocols(List<String> rawProtocols) {
        List<DistributedTransportProtocol> parsed = new ArrayList<>();
        for (String raw : rawProtocols) {
            switch (raw.toLowerCase(Locale.ROOT)) {
                case "manual_http" -> parsed.add(DistributedTransportProtocol.MANUAL_HTTP);
                case "nsd_http" -> parsed.add(DistributedTransportProtocol.NSD_HTTP);
                case "nsd_libp2p" -> parsed.add(DistributedTransportProtocol.NSD_LIBP2P);
                case "libp2p", "libp2p_planned" -> parsed.add(DistributedTransportProtocol.LIBP2P_PLANNED);
                default -> parsed.add(DistributedTransportProtocol.NSD_PLANNED);
            }
        }
        return parsed;
    }

    private ResponseEnvelope getJson(String url) {
        return request(url, "GET", null, null, null);
    }

    private ResponseEnvelope postJson(String url, String body, String bearerToken) {
        return request(url, "POST", "application/json; charset=utf-8", body, bearerToken);
    }

    private ResponseEnvelope request(String rawUrl, String method, String contentType, String body, String bearerToken) {
        HttpURLConnection connection = null;
        try {
            URL url = URI.create(rawUrl).toURL();
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(HTTP_TIMEOUT_MS);
            connection.setReadTimeout(HTTP_TIMEOUT_MS);
            connection.setRequestMethod(method);
            connection.setRequestProperty("Accept", "application/json");
            if (bearerToken != null && !bearerToken.isBlank()) {
                connection.setRequestProperty("Authorization", "Bearer " + bearerToken);
            }
            if (body != null) {
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", contentType);
                byte[] payload = body.getBytes(StandardCharsets.UTF_8);
                try (OutputStream output = connection.getOutputStream()) {
                    output.write(payload);
                }
            }

            int statusCode = connection.getResponseCode();
            InputStream stream = statusCode >= 400 ? connection.getErrorStream() : connection.getInputStream();
            String responseBody = stream == null ? "" : new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            return new ResponseEnvelope(statusCode, responseBody.isBlank() ? "{\"ok\":false,\"message\":\"empty response\"}" : responseBody);
        } catch (Exception error) {
            return new ResponseEnvelope(502, JsonUtil.stringify(Map.of(
                "ok", false,
                "error", "remote_request_failed",
                "message", error.getMessage() == null ? "Remote request failed." : error.getMessage()
            )));
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void writeJson(HttpExchange exchange, int status, Object payload) throws IOException {
        writeJsonString(exchange, status, JsonUtil.stringify(payload));
    }

    private void writeJsonString(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(bytes);
        }
    }

    private void writeHtml(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(bytes);
        }
    }

    private String readBody(HttpExchange exchange) throws IOException {
        try (InputStream input = exchange.getRequestBody()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String normalizeCoreBaseUrl(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private String appendQuery(String basePath, URI uri) {
        String query = uri.getRawQuery();
        if (query == null || query.isBlank()) {
            return basePath;
        }
        return basePath + "?" + query;
    }

    private Map<String, String> parseForm(String raw) {
        Map<String, String> values = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) {
            return values;
        }
        for (String part : raw.split("&")) {
            if (part.isBlank()) {
                continue;
            }
            String[] pieces = part.split("=", 2);
            String key = decode(pieces[0]);
            String value = pieces.length > 1 ? decode(pieces[1]) : "";
            values.put(key, value);
        }
        return values;
    }

    private Map<String, String> parseQuery(URI uri) {
        return parseForm(uri.getRawQuery());
    }

    private String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private String stableId(Path path) {
        return UUID.nameUUIDFromBytes(path.toAbsolutePath().toString().getBytes(StandardCharsets.UTF_8)).toString();
    }

    private String extractString(String json, String field, String fallback) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*\"(.*?)\"", Pattern.DOTALL).matcher(json);
        if (!matcher.find()) {
            return fallback;
        }
        return matcher.group(1)
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\");
    }

    private int extractInt(String json, String field, int fallback) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*(-?\\d+)").matcher(json);
        return matcher.find() ? safeParseInt(matcher.group(1), fallback) : fallback;
    }

    private long extractLong(String json, String field, long fallback) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*(-?\\d+)").matcher(json);
        if (!matcher.find()) {
            return fallback;
        }
        try {
            return Long.parseLong(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private double extractDouble(String json, String field, double fallback) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)").matcher(json);
        if (!matcher.find()) {
            return fallback;
        }
        try {
            return Double.parseDouble(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private boolean extractBoolean(String json, String field, boolean fallback) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*(true|false)", Pattern.CASE_INSENSITIVE).matcher(json);
        return matcher.find() ? Boolean.parseBoolean(matcher.group(1)) : fallback;
    }

    private List<String> extractStringArray(String json, String field) {
        Matcher outer = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*\\[(.*?)\\]", Pattern.DOTALL).matcher(json);
        if (!outer.find()) {
            return List.of();
        }
        Matcher inner = Pattern.compile("\"(.*?)\"", Pattern.DOTALL).matcher(outer.group(1));
        List<String> values = new ArrayList<>();
        while (inner.find()) {
            values.add(inner.group(1).replace("\\\"", "\"").replace("\\\\", "\\"));
        }
        return values;
    }

    private int safeParseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String dashboardHtml() {
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
              <meta charset="UTF-8">
              <meta name="viewport" content="width=device-width, initial-scale=1.0">
              <title>Windows Hub</title>
              <style>
                :root {
                  --bg: #f3ede5;
                  --card: rgba(255,255,255,0.82);
                  --ink: #18312e;
                  --muted: #5e736f;
                  --line: rgba(24,49,46,0.12);
                  --accent: #0f8572;
                  --accent-soft: rgba(15,133,114,0.14);
                  --shadow: 0 24px 48px rgba(24,49,46,0.12);
                }
                * { box-sizing: border-box; }
                body {
                  margin: 0;
                  font-family: "Segoe UI", sans-serif;
                  color: var(--ink);
                  background:
                    radial-gradient(circle at top right, rgba(15,133,114,0.16), transparent 24%),
                    radial-gradient(circle at bottom left, rgba(216,170,89,0.14), transparent 22%),
                    linear-gradient(135deg, #f6f1e8 0%, #ede5d9 50%, #f4efe9 100%);
                }
                main { width: min(1180px, calc(100% - 24px)); margin: 24px auto 32px; }
                .card {
                  background: var(--card);
                  border: 1px solid rgba(255,255,255,0.78);
                  border-radius: 24px;
                  box-shadow: var(--shadow);
                  backdrop-filter: blur(18px) saturate(140%);
                  padding: 18px;
                }
                .hero {
                  display: grid;
                  grid-template-columns: 1.5fr 1fr;
                  gap: 16px;
                  margin-bottom: 16px;
                }
                .grid {
                  display: grid;
                  grid-template-columns: repeat(2, minmax(0, 1fr));
                  gap: 16px;
                }
                h1, h2, p { margin: 0; }
                .eyebrow { text-transform: uppercase; letter-spacing: .16em; font-size: .76rem; color: var(--accent); margin-bottom: .5rem; }
                .lede { color: var(--muted); margin-top: .8rem; line-height: 1.5; }
                .metric { padding: 14px; border-radius: 18px; background: rgba(255,255,255,0.72); border: 1px solid var(--line); margin-bottom: 12px; }
                .metric strong { display: block; font-size: 1.45rem; margin-top: 4px; }
                .row { display: flex; justify-content: space-between; align-items: center; gap: 12px; margin-bottom: 12px; }
                .pill { display: inline-flex; align-items: center; gap: 8px; padding: 8px 12px; border-radius: 999px; background: var(--accent-soft); color: var(--accent); font-weight: 700; }
                .stack { display: grid; gap: 12px; }
                .item {
                  padding: 14px;
                  border-radius: 18px;
                  background: rgba(255,255,255,0.76);
                  border: 1px solid var(--line);
                }
                .meta { margin-top: 8px; color: var(--muted); font-size: .92rem; display: flex; flex-wrap: wrap; gap: 8px 14px; }
                button, select, input, textarea {
                  font: inherit;
                  width: 100%;
                  border-radius: 16px;
                  border: 1px solid var(--line);
                  background: rgba(255,255,255,0.86);
                  padding: 12px 14px;
                  color: var(--ink);
                }
                button {
                  background: linear-gradient(135deg, var(--accent), #16a28d);
                  color: white;
                  border: 0;
                  cursor: pointer;
                  font-weight: 700;
                }
                textarea { min-height: 120px; resize: vertical; }
                pre {
                  margin: 0;
                  white-space: pre-wrap;
                  word-break: break-word;
                  font-family: Consolas, monospace;
                  color: #203634;
                }
                @media (max-width: 900px) {
                  .hero, .grid { grid-template-columns: 1fr; }
                }
              </style>
            </head>
            <body>
              <main>
                <section class="hero">
                  <article class="card">
                    <p class="eyebrow">Windows orchestration</p>
                    <h1>SantiyaLocalAiHub Windows Hub</h1>
                    <p class="lede">Local Windows control plane for GGUF planning, Android phone discovery on LAN, and prompt forwarding into the mobile runtime.</p>
                  </article>
                  <article class="card">
                    <div class="metric"><span>Hub port</span><strong id="hub-port">-</strong></div>
                    <div class="metric"><span>Android nodes</span><strong id="node-count">0</strong></div>
                    <div class="metric"><span>Pairing token</span><strong id="token-status">missing</strong></div>
                    <button id="refresh-button" type="button">Refresh LAN State</button>
                  </article>
                </section>

                <section class="grid">
                  <article class="card">
                    <div class="row">
                      <h2>Discovered Nodes</h2>
                      <span class="pill" id="discovery-pill">scan idle</span>
                    </div>
                    <div class="stack" id="nodes-list"><div class="item">No nodes loaded yet.</div></div>
                  </article>

                  <article class="card">
                    <div class="row">
                      <h2>Forward Prompt to Android</h2>
                      <span class="pill">LAN execute</span>
                    </div>
                    <form id="execute-form" class="stack">
                      <select id="node-select" name="host"></select>
                      <input type="hidden" name="remotePort" id="remote-port">
                      <input name="capability" value="chat" placeholder="capability">
                      <input name="modelId" placeholder="optional model id">
                      <input name="systemPrompt" placeholder="optional system prompt">
                      <textarea name="prompt" placeholder="Type a prompt for the Android node"></textarea>
                      <button type="submit">Send to Phone Node</button>
                    </form>
                  </article>

                  <article class="card">
                    <div class="row">
                      <h2>Installed Models</h2>
                      <span class="pill" id="model-count">0</span>
                    </div>
                    <div class="stack" id="models-list"><div class="item">No GGUF models found.</div></div>
                  </article>

                  <article class="card">
                    <div class="row">
                      <h2>Execution Result</h2>
                      <span class="pill">JSON relay</span>
                    </div>
                    <pre id="result-box">Awaiting request.</pre>
                  </article>
                </section>
              </main>
              <script>
                const state = { nodes: [] };

                async function fetchJson(url, options) {
                  const response = await fetch(url, options);
                  const text = await response.text();
                  try { return JSON.parse(text); } catch { return { ok: false, raw: text, status: response.status }; }
                }

                function renderStatus(status) {
                  document.getElementById('hub-port').textContent = status.port;
                  document.getElementById('token-status').textContent = status.pairingTokenConfigured ? 'configured' : 'missing';
                  document.getElementById('node-count').textContent = status.discoveredLanNodes;
                }

                function renderNodes(nodes) {
                  state.nodes = nodes.filter(node => node.platform === 'android' || node.source === 'discovered_lan');
                  document.getElementById('discovery-pill').textContent = state.nodes.length ? `${state.nodes.length} active` : 'none found';
                  const list = document.getElementById('nodes-list');
                  const select = document.getElementById('node-select');
                  const remotePort = document.getElementById('remote-port');
                  if (!state.nodes.length) {
                    list.innerHTML = '<div class="item">No Android LAN nodes discovered yet. Open the Android app, enable LAN mode, then refresh.</div>';
                    select.innerHTML = '<option value="">No nodes available</option>';
                    remotePort.value = '';
                    return;
                  }

                  list.innerHTML = state.nodes.map(node => `
                    <article class="item">
                      <strong>${escapeHtml(node.displayName)}</strong>
                      <div class="meta">
                        <span>${escapeHtml(node.host || 'unknown host')}</span>
                        <span>${escapeHtml(String(node.port || ''))}</span>
                        <span>RAM ${escapeHtml(String(node.freeRamMb))}/${escapeHtml(String(node.totalRamMb))} MB</span>
                        <span>${escapeHtml(node.acceleratorSummary || 'unknown accelerator')}</span>
                      </div>
                    </article>
                  `).join('');

                  select.innerHTML = state.nodes.map((node, index) => `
                    <option value="${escapeHtml(node.host || '')}" data-port="${escapeHtml(String(node.port || ''))}" ${index === 0 ? 'selected' : ''}>
                      ${escapeHtml(node.displayName)} (${escapeHtml(node.host || 'unknown')})
                    </option>
                  `).join('');
                  remotePort.value = String(state.nodes[0].port || '');
                }

                function renderModels(models) {
                  document.getElementById('model-count').textContent = models.length;
                  const list = document.getElementById('models-list');
                  if (!models.length) {
                    list.innerHTML = '<div class="item">No GGUF models found in the configured models directory.</div>';
                    return;
                  }
                  list.innerHTML = models.map(model => `
                    <article class="item">
                      <strong>${escapeHtml(model.name)}</strong>
                      <div class="meta">
                        <span>${escapeHtml(model.architecture || 'unknown')}</span>
                        <span>${escapeHtml(String(model.sizeMb))} MB</span>
                        <span>${escapeHtml(String(model.estimatedLayers || '-'))} layers</span>
                      </div>
                    </article>
                  `).join('');
                }

                async function refreshAll() {
                  const [status, nodes, models] = await Promise.all([
                    fetchJson('/api/status'),
                    fetchJson('/api/nodes'),
                    fetchJson('/api/models')
                  ]);
                  renderStatus(status);
                  renderNodes(nodes);
                  renderModels(models);
                }

                document.getElementById('refresh-button').addEventListener('click', async () => {
                  await fetch('/api/reload');
                  await refreshAll();
                });

                document.getElementById('node-select').addEventListener('change', event => {
                  const option = event.target.selectedOptions[0];
                  document.getElementById('remote-port').value = option?.dataset.port || '';
                });

                document.getElementById('execute-form').addEventListener('submit', async event => {
                  event.preventDefault();
                  const formData = new URLSearchParams(new FormData(event.target));
                  const response = await fetch('/api/lan/execute', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                    body: formData
                  });
                  const text = await response.text();
                  document.getElementById('result-box').textContent = text;
                });

                function escapeHtml(value) {
                  return String(value)
                    .replaceAll('&', '&amp;')
                    .replaceAll('<', '&lt;')
                    .replaceAll('>', '&gt;')
                    .replaceAll('"', '&quot;')
                    .replaceAll("'", '&#39;');
                }

                refreshAll().catch(error => {
                  document.getElementById('result-box').textContent = String(error);
                });
              </script>
            </body>
            </html>
            """;
    }

    private record ResponseEnvelope(int statusCode, String body) {
    }

    private final class ProbeTask implements Callable<DiscoveredLanNode> {
        private final String host;

        private ProbeTask(String host) {
            this.host = host;
        }

        @Override
        public DiscoveredLanNode call() {
            return probeAndroidNode(host);
        }
    }
}
