package com.santiya.localaihub.windows;

import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;

public final class WindowsHubApp {
    private WindowsHubApp() {
    }

    public static void main(String[] args) throws Exception {
        Arguments options = Arguments.parse(args);
        if (options.help()) {
            System.out.println(Arguments.usage());
            return;
        }
        if (!options.headless()) {
            WindowsHubDesktopApp.launch(options);
            return;
        }
        WindowsHubService service = new WindowsHubService(
            options.port(),
            options.modelsDir(),
            options.peersFile(),
            options.pairingToken(),
            options.androidNodePort(),
            options.coreBaseUrl()
        );
        service.start();
        System.out.println(service.statusLine());
        System.out.println("Models directory: " + options.modelsDir().toAbsolutePath());
        System.out.println("Peers file: " + options.peersFile().toAbsolutePath());
        System.out.println("Android node port: " + options.androidNodePort());
        System.out.println("Pairing token configured: " + (!options.pairingToken().isBlank()));
        System.out.println("Core base URL: " + (options.coreBaseUrl().isBlank() ? "(local Java mode)" : options.coreBaseUrl()));
        System.out.println("Press Ctrl+C to stop.");
        Runtime.getRuntime().addShutdownHook(new Thread(service::stop));
        new CountDownLatch(1).await();
    }

    record Arguments(
        int port,
        Path modelsDir,
        Path peersFile,
        boolean headless,
        boolean help,
        String pairingToken,
        int androidNodePort,
        String coreBaseUrl
    ) {
        static Arguments parse(String[] args) {
            int port = 17860;
            int androidNodePort = 17888;
            boolean headless = false;
            boolean help = false;
            String pairingToken = "";
            String coreBaseUrl = System.getenv("WINDOWS_CORE_URL");
            Path appHome = defaultAppHome();
            Path modelsDir = appHome.resolve("models");
            Path peersFile = appHome.resolve("peers.csv");
            for (String arg : args) {
                String lower = arg.toLowerCase(Locale.ROOT);
                if (lower.equals("--headless")) {
                    headless = true;
                } else if (lower.equals("--help") || lower.equals("-h") || lower.equals("/?")) {
                    help = true;
                } else if (lower.startsWith("--port=")) {
                    port = safeParseInt(arg.substring("--port=".length()), port);
                } else if (lower.startsWith("--models-dir=")) {
                    modelsDir = Path.of(arg.substring("--models-dir=".length())).toAbsolutePath();
                } else if (lower.startsWith("--peers-file=")) {
                    peersFile = Path.of(arg.substring("--peers-file=".length())).toAbsolutePath();
                } else if (lower.startsWith("--pairing-token=")) {
                    pairingToken = arg.substring("--pairing-token=".length()).trim();
                } else if (lower.startsWith("--android-node-port=")) {
                    androidNodePort = safeParseInt(arg.substring("--android-node-port=".length()), androidNodePort);
                } else if (lower.startsWith("--core-url=")) {
                    coreBaseUrl = arg.substring("--core-url=".length()).trim();
                }
            }
            if (coreBaseUrl == null) {
                coreBaseUrl = "";
            }
            return new Arguments(port, modelsDir, peersFile, headless, help, pairingToken, androidNodePort, coreBaseUrl);
        }

        static String usage() {
            String lineSeparator = System.lineSeparator();
            return String.join(lineSeparator,
                "SantiyaLocalAiHub Windows Hub",
                "",
                "Flags:",
                "  --headless",
                "  --port=17860",
                "  --models-dir=C:\\path\\to\\models",
                "  --peers-file=C:\\path\\to\\peers.csv",
                "  --pairing-token=shared-lan-token",
                "  --android-node-port=17888",
                "  --core-url=http://127.0.0.1:17861",
                "  --help"
            );
        }

        private static int safeParseInt(String value, int fallback) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }

        static Path defaultAppHome() {
            String localAppData = System.getenv("LOCALAPPDATA");
            if (localAppData != null && !localAppData.isBlank()) {
                return Path.of(localAppData, "SantiyaLocalAiHub", "windows-hub");
            }
            return Path.of(System.getProperty("user.home"), "AppData", "Local", "SantiyaLocalAiHub", "windows-hub");
        }
    }
}
