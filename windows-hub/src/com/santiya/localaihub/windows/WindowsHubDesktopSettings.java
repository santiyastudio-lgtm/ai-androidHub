package com.santiya.localaihub.windows;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

final class WindowsHubDesktopSettings {
    enum ThemePreset {
        SYSTEM,
        MIDNIGHT_VIOLET,
        OBSIDIAN_MONO,
        MARBLE_LILAC
    }

    enum AppLocale {
        SYSTEM,
        RU,
        EN
    }

    enum PerformanceMode {
        PERFORMANCE,
        BALANCED,
        POWER_SAVING
    }

    enum AccelerationMode {
        AUTO,
        GPU,
        CPU
    }

    int port = 17860;
    int androidNodePort = 17888;
    String pairingToken = "";
    String coreBaseUrl = "";
    Path modelsDir = WindowsHubApp.Arguments.defaultAppHome().resolve("models").toAbsolutePath();
    Path peersFile = WindowsHubApp.Arguments.defaultAppHome().resolve("peers.csv").toAbsolutePath();

    boolean streamingEnabled = true;
    boolean chatMemoryEnabled = true;
    boolean toolCallingEnabled = true;
    boolean toolCallingBypassEnabled = false;
    boolean imageBlurEnabled = true;
    boolean loadTtsOnStart = true;
    boolean codeHighlightEnabled = true;
    boolean aiMemoryEnabled = true;
    boolean askModelReloadDialog = true;

    boolean hardwareTuningEnabled = true;
    PerformanceMode performanceMode = PerformanceMode.BALANCED;
    AccelerationMode accelerationMode = AccelerationMode.AUTO;

    ThemePreset themePreset = ThemePreset.SYSTEM;
    AppLocale appLocale = AppLocale.SYSTEM;

    String preferredChatModelId = "";
    String preferredLiveModelId = "";
    String preferredImageModelId = "";

    boolean externalAccessEnabled = false;
    boolean lanEnabled = true;
    boolean advertiseLocalNode = true;
    boolean orchestraEnabled = false;
    boolean orchestraAutoAssign = true;
    boolean orchestraAllowLanSpillover = true;

    static WindowsHubDesktopSettings load(Path file, WindowsHubApp.Arguments startup) {
        WindowsHubDesktopSettings settings = new WindowsHubDesktopSettings();
        settings.applyStartupDefaults(startup);
        if (!Files.exists(file)) {
            return settings;
        }

        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(file)) {
            properties.load(input);
        } catch (IOException ignored) {
            return settings;
        }

        settings.port = parseInt(properties.getProperty("port"), settings.port);
        settings.androidNodePort = parseInt(properties.getProperty("androidNodePort"), settings.androidNodePort);
        settings.pairingToken = properties.getProperty("pairingToken", settings.pairingToken).trim();
        settings.coreBaseUrl = properties.getProperty("coreBaseUrl", settings.coreBaseUrl).trim();
        settings.modelsDir = parsePath(properties.getProperty("modelsDir"), settings.modelsDir);
        settings.peersFile = parsePath(properties.getProperty("peersFile"), settings.peersFile);

        settings.streamingEnabled = parseBoolean(properties.getProperty("streamingEnabled"), settings.streamingEnabled);
        settings.chatMemoryEnabled = parseBoolean(properties.getProperty("chatMemoryEnabled"), settings.chatMemoryEnabled);
        settings.toolCallingEnabled = parseBoolean(properties.getProperty("toolCallingEnabled"), settings.toolCallingEnabled);
        settings.toolCallingBypassEnabled = parseBoolean(properties.getProperty("toolCallingBypassEnabled"), settings.toolCallingBypassEnabled);
        settings.imageBlurEnabled = parseBoolean(properties.getProperty("imageBlurEnabled"), settings.imageBlurEnabled);
        settings.loadTtsOnStart = parseBoolean(properties.getProperty("loadTtsOnStart"), settings.loadTtsOnStart);
        settings.codeHighlightEnabled = parseBoolean(properties.getProperty("codeHighlightEnabled"), settings.codeHighlightEnabled);
        settings.aiMemoryEnabled = parseBoolean(properties.getProperty("aiMemoryEnabled"), settings.aiMemoryEnabled);
        settings.askModelReloadDialog = parseBoolean(properties.getProperty("askModelReloadDialog"), settings.askModelReloadDialog);

        settings.hardwareTuningEnabled = parseBoolean(properties.getProperty("hardwareTuningEnabled"), settings.hardwareTuningEnabled);
        settings.performanceMode = parseEnum(properties.getProperty("performanceMode"), PerformanceMode.BALANCED, PerformanceMode.class);
        settings.accelerationMode = parseEnum(properties.getProperty("accelerationMode"), AccelerationMode.AUTO, AccelerationMode.class);
        settings.themePreset = parseEnum(properties.getProperty("themePreset"), ThemePreset.SYSTEM, ThemePreset.class);
        settings.appLocale = parseEnum(properties.getProperty("appLocale"), AppLocale.SYSTEM, AppLocale.class);

        settings.preferredChatModelId = properties.getProperty("preferredChatModelId", settings.preferredChatModelId);
        settings.preferredLiveModelId = properties.getProperty("preferredLiveModelId", settings.preferredLiveModelId);
        settings.preferredImageModelId = properties.getProperty("preferredImageModelId", settings.preferredImageModelId);

        settings.externalAccessEnabled = parseBoolean(properties.getProperty("externalAccessEnabled"), settings.externalAccessEnabled);
        settings.lanEnabled = parseBoolean(properties.getProperty("lanEnabled"), settings.lanEnabled);
        settings.advertiseLocalNode = parseBoolean(properties.getProperty("advertiseLocalNode"), settings.advertiseLocalNode);
        settings.orchestraEnabled = parseBoolean(properties.getProperty("orchestraEnabled"), settings.orchestraEnabled);
        settings.orchestraAutoAssign = parseBoolean(properties.getProperty("orchestraAutoAssign"), settings.orchestraAutoAssign);
        settings.orchestraAllowLanSpillover = parseBoolean(properties.getProperty("orchestraAllowLanSpillover"), settings.orchestraAllowLanSpillover);
        settings.applyStartupOverrides(startup);
        return settings;
    }

    static void save(Path file, WindowsHubDesktopSettings settings) throws IOException {
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        Properties properties = new Properties();
        properties.setProperty("port", String.valueOf(settings.port));
        properties.setProperty("androidNodePort", String.valueOf(settings.androidNodePort));
        properties.setProperty("pairingToken", settings.pairingToken);
        properties.setProperty("coreBaseUrl", settings.coreBaseUrl);
        properties.setProperty("modelsDir", settings.modelsDir.toString());
        properties.setProperty("peersFile", settings.peersFile.toString());

        properties.setProperty("streamingEnabled", String.valueOf(settings.streamingEnabled));
        properties.setProperty("chatMemoryEnabled", String.valueOf(settings.chatMemoryEnabled));
        properties.setProperty("toolCallingEnabled", String.valueOf(settings.toolCallingEnabled));
        properties.setProperty("toolCallingBypassEnabled", String.valueOf(settings.toolCallingBypassEnabled));
        properties.setProperty("imageBlurEnabled", String.valueOf(settings.imageBlurEnabled));
        properties.setProperty("loadTtsOnStart", String.valueOf(settings.loadTtsOnStart));
        properties.setProperty("codeHighlightEnabled", String.valueOf(settings.codeHighlightEnabled));
        properties.setProperty("aiMemoryEnabled", String.valueOf(settings.aiMemoryEnabled));
        properties.setProperty("askModelReloadDialog", String.valueOf(settings.askModelReloadDialog));

        properties.setProperty("hardwareTuningEnabled", String.valueOf(settings.hardwareTuningEnabled));
        properties.setProperty("performanceMode", settings.performanceMode.name());
        properties.setProperty("accelerationMode", settings.accelerationMode.name());
        properties.setProperty("themePreset", settings.themePreset.name());
        properties.setProperty("appLocale", settings.appLocale.name());

        properties.setProperty("preferredChatModelId", settings.preferredChatModelId);
        properties.setProperty("preferredLiveModelId", settings.preferredLiveModelId);
        properties.setProperty("preferredImageModelId", settings.preferredImageModelId);

        properties.setProperty("externalAccessEnabled", String.valueOf(settings.externalAccessEnabled));
        properties.setProperty("lanEnabled", String.valueOf(settings.lanEnabled));
        properties.setProperty("advertiseLocalNode", String.valueOf(settings.advertiseLocalNode));
        properties.setProperty("orchestraEnabled", String.valueOf(settings.orchestraEnabled));
        properties.setProperty("orchestraAutoAssign", String.valueOf(settings.orchestraAutoAssign));
        properties.setProperty("orchestraAllowLanSpillover", String.valueOf(settings.orchestraAllowLanSpillover));

        try (OutputStream output = Files.newOutputStream(file)) {
            properties.store(output, "SantiyaLocalAiHub Windows Hub");
        }
    }

    private void applyStartupDefaults(WindowsHubApp.Arguments startup) {
        port = startup.port();
        androidNodePort = startup.androidNodePort();
        if (!startup.pairingToken().isBlank()) {
            pairingToken = startup.pairingToken();
        }
        if (!startup.coreBaseUrl().isBlank()) {
            coreBaseUrl = startup.coreBaseUrl();
        }
        modelsDir = startup.modelsDir().toAbsolutePath();
        peersFile = startup.peersFile().toAbsolutePath();
    }

    private void applyStartupOverrides(WindowsHubApp.Arguments startup) {
        Path defaultModelsDir = WindowsHubApp.Arguments.defaultAppHome().resolve("models").toAbsolutePath();
        Path defaultPeersFile = WindowsHubApp.Arguments.defaultAppHome().resolve("peers.csv").toAbsolutePath();
        if (startup.port() != 17860) {
            port = startup.port();
        }
        if (startup.androidNodePort() != 17888) {
            androidNodePort = startup.androidNodePort();
        }
        if (!startup.pairingToken().isBlank()) {
            pairingToken = startup.pairingToken();
        }
        if (!startup.coreBaseUrl().isBlank()) {
            coreBaseUrl = startup.coreBaseUrl();
        }
        if (!startup.modelsDir().toAbsolutePath().equals(defaultModelsDir)) {
            modelsDir = startup.modelsDir().toAbsolutePath();
        }
        if (!startup.peersFile().toAbsolutePath().equals(defaultPeersFile)) {
            peersFile = startup.peersFile().toAbsolutePath();
        }
    }

    private static boolean parseBoolean(String raw, boolean fallback) {
        return raw == null ? fallback : Boolean.parseBoolean(raw);
    }

    private static int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(raw);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static Path parsePath(String raw, Path fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Path.of(raw).toAbsolutePath();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static <T extends Enum<T>> T parseEnum(String raw, T fallback, Class<T> type) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, raw.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
