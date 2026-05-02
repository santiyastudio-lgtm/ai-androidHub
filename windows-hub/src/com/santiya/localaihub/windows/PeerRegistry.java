package com.santiya.localaihub.windows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class PeerRegistry {
    private final Path peersFile;

    PeerRegistry(Path peersFile) {
        this.peersFile = peersFile;
    }

    List<PeerNodeConfig> load() {
        if (!Files.exists(peersFile)) {
            return List.of();
        }
        List<PeerNodeConfig> peers = new ArrayList<>();
        try {
            for (String line : Files.readAllLines(peersFile, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (trimmed.isBlank() || trimmed.startsWith("#")) {
                    continue;
                }
                String[] parts = trimmed.split(",", -1);
                if (parts.length < 8) {
                    continue;
                }
                peers.add(new PeerNodeConfig(
                    parts[0].trim(),
                    parts[1].trim(),
                    parseInt(parts[2], 17860),
                    parseInt(parts[3], 4096),
                    parseInt(parts[4], 8192),
                    parseInt(parts[5], 8),
                    parseDouble(parts[6], 8.0),
                    parts[7].trim()
                ));
            }
        } catch (IOException ignored) {
            return List.of();
        }
        return peers;
    }

    void save(List<PeerNodeConfig> peers) throws IOException {
        Files.createDirectories(peersFile.getParent());
        List<String> lines = new ArrayList<>();
        lines.add("# name,host,port,freeRamMb,totalRamMb,cpuCores,computeScore,acceleratorSummary");
        for (PeerNodeConfig peer : peers) {
            lines.add(String.join(",",
                peer.name(),
                peer.host(),
                String.valueOf(peer.port()),
                String.valueOf(peer.freeRamMb()),
                String.valueOf(peer.totalRamMb()),
                String.valueOf(peer.cpuCores()),
                String.valueOf(peer.computeScore()),
                peer.acceleratorSummary()
            ));
        }
        Files.write(peersFile, lines, StandardCharsets.UTF_8);
    }

    private int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private double parseDouble(String raw, double fallback) {
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
