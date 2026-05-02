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
        WindowsHubService service = new WindowsHubService(options.port(), options.modelsDir(), options.peersFile());
        service.start();
        System.out.println(service.statusLine());
        System.out.println("Models directory: " + options.modelsDir().toAbsolutePath());
        System.out.println("Peers file: " + options.peersFile().toAbsolutePath());
        System.out.println("Press Ctrl+C to stop.");
        Runtime.getRuntime().addShutdownHook(new Thread(service::stop));

        if (options.headless()) {
            new CountDownLatch(1).await();
            return;
        }

        System.out.println("GUI mode is not implemented yet. The Windows Hub is running as a console service.");
        System.out.println("Use --headless for service mode or open the local API at http://127.0.0.1:" + options.port());
        new CountDownLatch(1).await();
    }

    record Arguments(int port, Path modelsDir, Path peersFile, boolean headless, boolean help) {
        static Arguments parse(String[] args) {
            int port = 17860;
            boolean headless = false;
            boolean help = false;
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
                }
            }
            return new Arguments(port, modelsDir, peersFile, headless, help);
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

        private static Path defaultAppHome() {
            String localAppData = System.getenv("LOCALAPPDATA");
            if (localAppData != null && !localAppData.isBlank()) {
                return Path.of(localAppData, "SantiyaLocalAiHub", "windows-hub");
            }
            return Path.of(System.getProperty("user.home"), "AppData", "Local", "SantiyaLocalAiHub", "windows-hub");
        }
    }
}
