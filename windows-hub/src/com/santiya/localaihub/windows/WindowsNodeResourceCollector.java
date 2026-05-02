package com.santiya.localaihub.windows;

import com.sun.management.OperatingSystemMXBean;
import java.io.File;
import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.util.List;

final class WindowsNodeResourceCollector {

    NodeResourceSnapshot snapshot(int port) {
        OperatingSystemMXBean os = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        long totalRamBytes = os.getTotalMemorySize();
        long freeRamBytes = os.getFreeMemorySize();
        int totalRamMb = (int) Math.max(1L, totalRamBytes / (1024L * 1024L));
        int freeRamMb = (int) Math.max(1L, freeRamBytes / (1024L * 1024L));
        int cpuCores = Runtime.getRuntime().availableProcessors();
        String arch = System.getProperty("os.arch", "unknown");
        double acceleratorScore = arch.contains("64") ? 1.0 : 0.4;
        double computeScore = cpuCores + (freeRamMb / 1024.0) + acceleratorScore;
        File root = new File(System.getProperty("user.home", "C:\\"));
        int storageMb = (int) Math.max(1L, root.getUsableSpace() / (1024L * 1024L));

        return new NodeResourceSnapshot(
            "windows-" + System.getenv().getOrDefault("COMPUTERNAME", "node").toLowerCase(),
            "Windows Hub @" + port,
            DistributedNodePlatform.WINDOWS,
            totalRamMb,
            freeRamMb,
            cpuCores,
            arch,
            "CPU only on Windows module; GPU/NPU hooks are planned.",
            acceleratorScore,
            computeScore,
            storageMb,
            true,
            true,
            freeRamMb > 2048,
            List.of(
                DistributedTransportProtocol.MANUAL_HTTP,
                DistributedTransportProtocol.LIBP2P_PLANNED
            ),
            Instant.now().toEpochMilli(),
            List.of("Windows module currently exposes control-plane planning and HTTP integration.")
        );
    }
}
