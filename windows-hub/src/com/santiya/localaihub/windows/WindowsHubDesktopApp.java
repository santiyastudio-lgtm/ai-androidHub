package com.santiya.localaihub.windows;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.filechooser.FileNameExtensionFilter;

final class WindowsHubDesktopApp {
    private static final int REFRESH_MS = 3_000;
    private static final int FULL_SCAN_EVERY_TICKS = 5;

    private static final String PAGE_HOME = "home";
    private static final String PAGE_STORE = "store";
    private static final String PAGE_LIVE = "live";
    private static final String PAGE_FILES = "files";
    private static final String PAGE_NODES = "nodes";
    private static final String PAGE_MODELS = "models";
    private static final String PAGE_SETTINGS = "settings";

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss")
        .withZone(ZoneId.systemDefault());

    private final JFrame frame = new JFrame("SantiyaLocalAiHub Windows Hub");
    private final Timer refreshTimer = new Timer(REFRESH_MS, event -> scheduleRefresh(false));
    private final ExecutorService ioExecutor = Executors.newFixedThreadPool(2);
    private final AtomicBoolean refreshInFlight = new AtomicBoolean(false);
    private final StringBuilder sessionLog = new StringBuilder();

    private final Path settingsFile;
    private final Path distributionRoot;
    private final WindowsHubDesktopTheme.FontSet fonts;

    private WindowsHubDesktopTheme.Palette palette;
    private WindowsHubDesktopSettings currentSettings;
    private WindowsHubDesktopSettings runningSettings;

    private WindowsHubService service;
    private boolean suppressEvents;
    private int refreshTick;
    private String currentPage = PAGE_HOME;
    private String selectedModelId = "";
    private Path pendingAttachmentPath;
    private String pendingAttachmentKind = "";
    private String lastRelayResult = "Здесь появится ответ relay от Android-узла.";

    private JLabel shellTitleLabel;
    private JLabel shellSubtitleLabel;
    private JLabel pillPrimaryLabel;
    private JLabel pillSecondaryLabel;
    private JLabel footerLabel;

    private JButton startButton;
    private JButton stopButton;
    private JButton restartButton;
    private JButton refreshButton;
    private JButton openDashboardButton;
    private JButton saveSettingsButton;
    private JButton saveAndRestartButton;

    private JPanel navigationPanel;
    private JPanel pageContainer;
    private java.awt.CardLayout pageLayout;
    private final Map<String, JToggleButton> navigationButtons = new LinkedHashMap<>();

    private JLabel heroModelValueLabel;
    private JLabel heroRuntimeValueLabel;
    private JLabel heroLanValueLabel;
    private JLabel metricInstalledValueLabel;
    private JLabel metricRamValueLabel;
    private JLabel metricNodesValueLabel;
    private JLabel statusStripTitleLabel;
    private JLabel statusStripBodyLabel;
    private JLabel attachmentSummaryLabel;
    private JComboBox<NodeChoice> relayHostCombo;
    private JTextField relayPortField;
    private JTextField relaySystemPromptField;
    private JComboBox<ModeOption> relayModeCombo;
    private JTextArea relayPromptArea;
    private JTextArea relayResultArea;
    private JButton relaySendButton;
    private JButton attachFileButton;
    private JButton attachPhotoButton;
    private JButton clearAttachmentButton;
    private JTextArea logArea;

    private JLabel nodesSummaryLabel;
    private JPanel nodesContainer;

    private JLabel storeSummaryLabel;
    private JTextField modelsDirField;
    private JTextField peersFileField;
    private JButton browseModelsButton;
    private JButton browsePeersButton;
    private JButton openModelsFolderButton;
    private JButton openAppHomeButton;
    private DefaultListModel<ModelListItem> modelListModel;
    private JList<ModelListItem> modelList;
    private JTextArea planArea;

    private JLabel liveModelLabel;
    private JLabel liveStatusLabel;
    private JLabel liveModesLabel;
    private JLabel filesSummaryLabel;
    private JLabel filesPathsLabel;

    private JTextField portField;
    private JTextField androidPortField;
    private JTextField coreUrlField;
    private JPasswordField tokenField;
    private JButton generateTokenButton;

    private JCheckBox streamingCheck;
    private JCheckBox chatMemoryCheck;
    private JCheckBox toolCallingCheck;
    private JCheckBox toolCallingBypassCheck;
    private JCheckBox imageBlurCheck;
    private JCheckBox loadTtsCheck;
    private JCheckBox codeHighlightCheck;
    private JCheckBox aiMemoryCheck;
    private JCheckBox askReloadCheck;
    private JCheckBox hardwareTuningCheck;
    private JCheckBox externalAccessCheck;
    private JCheckBox lanEnabledCheck;
    private JCheckBox advertiseLocalNodeCheck;
    private JCheckBox orchestraEnabledCheck;
    private JCheckBox orchestraAutoAssignCheck;
    private JCheckBox orchestraLanSpilloverCheck;

    private JComboBox<WindowsHubDesktopSettings.ThemePreset> themePresetCombo;
    private JComboBox<WindowsHubDesktopSettings.AppLocale> localeCombo;
    private JComboBox<WindowsHubDesktopSettings.PerformanceMode> performanceModeCombo;
    private JComboBox<WindowsHubDesktopSettings.AccelerationMode> accelerationModeCombo;
    private JComboBox<IdLabelOption> preferredChatModelCombo;
    private JComboBox<IdLabelOption> preferredLiveModelCombo;
    private JComboBox<IdLabelOption> preferredImageModelCombo;
    private JButton openNodeFarmButton;
    private JButton openRuntimeButton;
    private JButton openApiButton;
    private JButton openDownloadsButton;

    static void launch(WindowsHubApp.Arguments options) {
        SwingUtilities.invokeLater(() -> new WindowsHubDesktopApp(options).show());
    }

    private WindowsHubDesktopApp(WindowsHubApp.Arguments options) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }
        settingsFile = WindowsHubApp.Arguments.defaultAppHome().resolve("desktop.properties");
        distributionRoot = resolveDistributionRoot();
        currentSettings = WindowsHubDesktopSettings.load(settingsFile, options);
        fonts = WindowsHubDesktopTheme.loadFonts(distributionRoot);
        palette = WindowsHubDesktopTheme.resolve(currentSettings.themePreset);

        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.setMinimumSize(new Dimension(1320, 860));
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                shutdown();
            }
        });

        rebuildUi(currentSettings, PAGE_HOME);
        wireActions();
    }

    private static Path resolveDistributionRoot() {
        try {
            Path codeSource = Path.of(
                WindowsHubDesktopApp.class.getProtectionDomain().getCodeSource().getLocation().toURI()
            );
            if (Files.isRegularFile(codeSource)) {
                Path parent = codeSource.getParent();
                if (parent != null && "app".equalsIgnoreCase(parent.getFileName().toString())) {
                    Path nativeRoot = parent.getParent();
                    if (nativeRoot != null) {
                        return nativeRoot;
                    }
                }
                if (parent != null) {
                    return parent;
                }
            }
            return codeSource.toAbsolutePath();
        } catch (Exception ignored) {
            return Path.of(".").toAbsolutePath().normalize();
        }
    }

    private void show() {
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        startHub();
    }

    private boolean isRussianUi() {
        WindowsHubDesktopSettings.AppLocale locale = currentSettings == null
            ? WindowsHubDesktopSettings.AppLocale.SYSTEM
            : currentSettings.appLocale;
        if (locale == WindowsHubDesktopSettings.AppLocale.RU) {
            return true;
        }
        if (locale == WindowsHubDesktopSettings.AppLocale.EN) {
            return false;
        }
        return Locale.getDefault().getLanguage().toLowerCase(Locale.ROOT).startsWith("ru");
    }

    private String t(String ru, String en) {
        return isRussianUi() ? ru : en;
    }

    private String localizedThemeLabel(WindowsHubDesktopSettings.ThemePreset preset) {
        return switch (preset) {
            case SYSTEM -> t("Система", "System");
            case MIDNIGHT_VIOLET -> "Midnight Violet";
            case OBSIDIAN_MONO -> "Obsidian Mono";
            case MARBLE_LILAC -> "Marble Lilac";
        };
    }

    private String localizedLocaleLabel(WindowsHubDesktopSettings.AppLocale locale) {
        return switch (locale) {
            case SYSTEM -> t("Система", "System");
            case RU -> "Русский";
            case EN -> "English";
        };
    }

    private String localizedPerformanceLabel(WindowsHubDesktopSettings.PerformanceMode mode) {
        return switch (mode) {
            case PERFORMANCE -> t("Производительность", "Performance");
            case BALANCED -> t("Баланс", "Balanced");
            case POWER_SAVING -> t("Экономия", "Power saving");
        };
    }

    private String localizedAccelerationLabel(WindowsHubDesktopSettings.AccelerationMode mode) {
        return switch (mode) {
            case AUTO -> "Auto";
            case GPU -> "GPU";
            case CPU -> "CPU";
        };
    }

    private void rebuildUi(WindowsHubDesktopSettings settings, String pageToShow) {
        suppressEvents = true;
        currentSettings = settings;
        palette = WindowsHubDesktopTheme.resolve(settings.themePreset);

        JPanel root = new JPanel(new BorderLayout(18, 18));
        root.setBackground(palette.background());
        root.setBorder(new EmptyBorder(18, 18, 18, 18));

        root.add(buildTopBarV2(), BorderLayout.NORTH);
        root.add(buildBodyV2(settings), BorderLayout.CENTER);
        root.add(buildFooter(), BorderLayout.SOUTH);

        frame.setContentPane(root);
        frame.revalidate();
        frame.repaint();

        currentPage = pageToShow;
        switchPage(pageToShow);
        applyRunningState();
        suppressEvents = false;
    }

    private JPanel buildTopBar() {
        RoundedPanel topBar = new RoundedPanel(palette.surface(), palette.outline(), 30);
        topBar.setLayout(new BorderLayout(16, 16));
        topBar.setBorder(new EmptyBorder(18, 20, 18, 20));

        shellTitleLabel = label("SantiyaLocalAiHub", fonts.display().deriveFont(Font.BOLD, 24f), palette.text());
        shellSubtitleLabel = label(
            "Desktop shell in the same visual language as Android: home landing, settings matrix, models and LAN relay.",
            fonts.body().deriveFont(Font.PLAIN, 13f),
            palette.textMuted()
        );

        JPanel titleColumn = transparentPanel();
        titleColumn.setLayout(new BoxLayout(titleColumn, BoxLayout.Y_AXIS));
        titleColumn.add(shellTitleLabel);
        titleColumn.add(Box.createVerticalStrut(6));
        titleColumn.add(shellSubtitleLabel);

        RoundedPanel pill = new RoundedPanel(palette.primaryContainer(), palette.outline(), 28);
        pill.setLayout(new BoxLayout(pill, BoxLayout.Y_AXIS));
        pill.setBorder(new EmptyBorder(12, 16, 12, 16));
        pillPrimaryLabel = label("Модель не выбрана", fonts.body().deriveFont(Font.BOLD, 14f), palette.text());
        pillSecondaryLabel = label("Windows hub stopped", fonts.body().deriveFont(Font.PLAIN, 12f), palette.textMuted());
        pill.add(pillPrimaryLabel);
        pill.add(Box.createVerticalStrut(3));
        pill.add(pillSecondaryLabel);

        JPanel actions = transparentPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        refreshButton = actionButton("Обновить", true);
        openDashboardButton = actionButton("Dashboard", true);
        restartButton = actionButton("Перезапуск", true);
        stopButton = actionButton("Стоп", true);
        startButton = actionButton("Старт", false);
        actions.add(refreshButton);
        actions.add(openDashboardButton);
        actions.add(restartButton);
        actions.add(stopButton);
        actions.add(startButton);

        JPanel center = transparentPanel(new BorderLayout());
        center.add(pill, BorderLayout.CENTER);

        topBar.add(titleColumn, BorderLayout.WEST);
        topBar.add(center, BorderLayout.CENTER);
        topBar.add(actions, BorderLayout.EAST);
        return topBar;
    }

    private JPanel buildBody(WindowsHubDesktopSettings settings) {
        JPanel body = transparentPanel(new BorderLayout(18, 18));
        body.add(buildNavigation(), BorderLayout.WEST);
        body.add(buildPages(settings), BorderLayout.CENTER);
        return body;
    }

    private JPanel buildNavigation() {
        navigationPanel = new RoundedPanel(palette.surface(), palette.outline(), 28);
        navigationPanel.setLayout(new BoxLayout(navigationPanel, BoxLayout.Y_AXIS));
        navigationPanel.setBorder(new EmptyBorder(16, 14, 16, 14));
        navigationPanel.setPreferredSize(new Dimension(220, 0));

        navigationButtons.clear();
        navigationPanel.add(navButton(PAGE_HOME, "Домой"));
        navigationPanel.add(Box.createVerticalStrut(10));
        navigationPanel.add(navButton(PAGE_NODES, "LAN узлы"));
        navigationPanel.add(Box.createVerticalStrut(10));
        navigationPanel.add(navButton(PAGE_MODELS, "Модели"));
        navigationPanel.add(Box.createVerticalStrut(10));
        navigationPanel.add(navButton(PAGE_SETTINGS, "Настройки"));
        navigationPanel.add(Box.createVerticalGlue());

        RoundedPanel navHint = new RoundedPanel(palette.surfaceVariant(), palette.outline(), 24);
        navHint.setLayout(new BoxLayout(navHint, BoxLayout.Y_AXIS));
        navHint.setBorder(new EmptyBorder(14, 14, 14, 14));
        navHint.add(label("Android parity", fonts.body().deriveFont(Font.BOLD, 13f), palette.text()));
        navHint.add(Box.createVerticalStrut(6));
        navHint.add(label(
            "Home, theme presets and settings groups mirror the Android shell. Unsupported Windows runtime areas are marked explicitly.",
            fonts.body().deriveFont(Font.PLAIN, 12f),
            palette.textMuted()
        ));
        navigationPanel.add(navHint);
        return navigationPanel;
    }

    private JPanel buildPages(WindowsHubDesktopSettings settings) {
        pageLayout = new java.awt.CardLayout();
        pageContainer = transparentPanel(pageLayout);

        pageContainer.add(wrapPage(buildHomePage()), PAGE_HOME);
        pageContainer.add(wrapPage(buildNodesPage()), PAGE_NODES);
        pageContainer.add(wrapPage(buildModelsPage()), PAGE_MODELS);
        pageContainer.add(wrapPage(buildSettingsPage(settings)), PAGE_SETTINGS);
        return pageContainer;
    }

    private JPanel buildTopBarV2() {
        RoundedPanel topBar = new RoundedPanel(palette.surface(), palette.outline(), 30);
        topBar.setLayout(new BorderLayout(16, 16));
        topBar.setBorder(new EmptyBorder(18, 20, 18, 20));

        shellTitleLabel = label("SantiyaLocalAiHub", fonts.display().deriveFont(Font.BOLD, 24f), palette.text());
        shellSubtitleLabel = label(
            t(
                "Android-first оболочка для Windows: Home, Store, Live AI, Files и настройки в одном продуктовом стиле.",
                "Android-first shell for Windows: Home, Store, Live AI, Files and settings in one product style."
            ),
            fonts.body().deriveFont(Font.PLAIN, 13f),
            palette.textMuted()
        );

        JPanel titleColumn = transparentPanel();
        titleColumn.setLayout(new BoxLayout(titleColumn, BoxLayout.Y_AXIS));
        titleColumn.add(shellTitleLabel);
        titleColumn.add(Box.createVerticalStrut(6));
        titleColumn.add(shellSubtitleLabel);

        RoundedPanel pill = new RoundedPanel(palette.primaryContainer(), palette.outline(), 28);
        pill.setLayout(new BoxLayout(pill, BoxLayout.Y_AXIS));
        pill.setBorder(new EmptyBorder(12, 16, 12, 16));
        pillPrimaryLabel = label(t("Модель не выбрана", "No model selected"), fonts.body().deriveFont(Font.BOLD, 14f), palette.text());
        pillSecondaryLabel = label(t("Windows hub остановлен", "Windows hub stopped"), fonts.body().deriveFont(Font.PLAIN, 12f), palette.textMuted());
        pill.add(pillPrimaryLabel);
        pill.add(Box.createVerticalStrut(3));
        pill.add(pillSecondaryLabel);

        JPanel actions = transparentPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        refreshButton = actionButton(t("Обновить", "Refresh"), true);
        openDashboardButton = actionButton("Dashboard", true);
        restartButton = actionButton(t("Перезапуск", "Restart"), true);
        stopButton = actionButton(t("Стоп", "Stop"), true);
        startButton = actionButton(t("Старт", "Start"), false);
        actions.add(refreshButton);
        actions.add(openDashboardButton);
        actions.add(restartButton);
        actions.add(stopButton);
        actions.add(startButton);

        topBar.add(titleColumn, BorderLayout.WEST);
        topBar.add(pill, BorderLayout.CENTER);
        topBar.add(actions, BorderLayout.EAST);
        return topBar;
    }

    private JPanel buildBodyV2(WindowsHubDesktopSettings settings) {
        JPanel body = transparentPanel(new BorderLayout());
        JPanel centered = transparentPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));

        RoundedPanel canvas = new RoundedPanel(palette.surfaceVariant(), palette.outline(), 36);
        canvas.setLayout(new BorderLayout(0, 16));
        canvas.setBorder(new EmptyBorder(18, 18, 18, 18));
        canvas.setPreferredSize(new Dimension(980, 680));
        canvas.add(buildNavigationV2(), BorderLayout.NORTH);
        canvas.add(buildPagesV2(settings), BorderLayout.CENTER);

        centered.add(canvas);
        body.add(centered, BorderLayout.CENTER);
        return body;
    }

    private JPanel buildNavigationV2() {
        navigationPanel = new RoundedPanel(palette.surface(), palette.outline(), 30);
        navigationPanel.setLayout(new BoxLayout(navigationPanel, BoxLayout.Y_AXIS));
        navigationPanel.setBorder(new EmptyBorder(14, 14, 14, 14));

        JPanel tabs = transparentPanel(new GridLayout(1, 5, 10, 0));
        navigationButtons.clear();
        tabs.add(navButton(PAGE_HOME, t("Главная", "Home")));
        tabs.add(navButton(PAGE_STORE, t("Магазин", "Store")));
        tabs.add(navButton(PAGE_LIVE, "Live AI"));
        tabs.add(navButton(PAGE_FILES, t("Файлы", "Files")));
        tabs.add(navButton(PAGE_SETTINGS, t("Настройки", "Settings")));
        navigationPanel.add(tabs);
        navigationPanel.add(Box.createVerticalStrut(12));

        RoundedPanel navHint = new RoundedPanel(palette.primaryContainer(), palette.outline(), 24);
        navHint.setLayout(new BorderLayout());
        navHint.setBorder(new EmptyBorder(12, 14, 12, 14));
        navHint.add(label(
            t(
                "Основной маршрут как в Android: product shell в центре окна, а Node Farm / Runtime / API уходят в utilities.",
                "Primary flow mirrors Android: centered product shell, while Node Farm / Runtime / API move into utilities."
            ),
            fonts.body().deriveFont(Font.PLAIN, 12f),
            palette.textMuted()
        ), BorderLayout.CENTER);
        navigationPanel.add(navHint);
        return navigationPanel;
    }

    private JPanel buildPagesV2(WindowsHubDesktopSettings settings) {
        pageLayout = new java.awt.CardLayout();
        pageContainer = transparentPanel(pageLayout);

        pageContainer.add(wrapPage(buildHomePageV2()), PAGE_HOME);
        pageContainer.add(wrapPage(buildStorePage()), PAGE_STORE);
        pageContainer.add(wrapPage(buildLivePage()), PAGE_LIVE);
        pageContainer.add(wrapPage(buildFilesPage()), PAGE_FILES);
        pageContainer.add(wrapPage(buildNodesPageV2()), PAGE_NODES);
        pageContainer.add(wrapPage(buildSettingsPageV2(settings)), PAGE_SETTINGS);
        return pageContainer;
    }

    private JPanel buildHomePageV2() {
        JPanel page = pageColumn();

        RoundedPanel heroCard = new RoundedPanel(palette.primaryContainer(), palette.outline(), 30);
        heroCard.setLayout(new BoxLayout(heroCard, BoxLayout.Y_AXIS));
        heroCard.setBorder(new EmptyBorder(22, 22, 22, 22));
        heroCard.add(label("SantiyaLocalAiHub", fonts.display().deriveFont(Font.BOLD, 28f), palette.text()));
        heroCard.add(Box.createVerticalStrut(8));
        heroCard.add(label(
            t(
                "Главная поверхность как в Android: модель, быстрые действия, runtime-статус и единый AI composer для LAN-связи с телефоном.",
                "Android-like home surface: model, quick actions, runtime status and a unified AI composer for LAN connection with your phone."
            ),
            fonts.body().deriveFont(Font.PLAIN, 14f),
            palette.textMuted()
        ));
        heroCard.add(Box.createVerticalStrut(16));

        RoundedPanel modelStrip = subtleCard(palette.secondaryContainer(), 999);
        modelStrip.setLayout(new BoxLayout(modelStrip, BoxLayout.Y_AXIS));
        modelStrip.setBorder(new EmptyBorder(14, 16, 14, 16));
        heroModelValueLabel = label(t("Текущая модель: не выбрана", "Current model: none"), fonts.body().deriveFont(Font.BOLD, 14f), palette.text());
        heroRuntimeValueLabel = label(t("Runtime: остановлен", "Runtime: stopped"), fonts.body().deriveFont(Font.PLAIN, 12f), palette.textMuted());
        heroLanValueLabel = label("LAN: off", fonts.body().deriveFont(Font.PLAIN, 12f), palette.textMuted());
        modelStrip.add(heroModelValueLabel);
        modelStrip.add(Box.createVerticalStrut(4));
        modelStrip.add(heroRuntimeValueLabel);
        modelStrip.add(Box.createVerticalStrut(2));
        modelStrip.add(heroLanValueLabel);
        heroCard.add(modelStrip);
        page.add(heroCard);

        JPanel metricsRow = transparentPanel(new GridLayout(1, 3, 12, 12));
        metricInstalledValueLabel = metricCard(metricsRow, t("Установлено", "Installed"), "0");
        metricRamValueLabel = metricCard(metricsRow, "RAM", "0 GB");
        metricNodesValueLabel = metricCard(metricsRow, t("LAN узлы", "LAN nodes"), "0");
        page.add(metricsRow);

        JPanel quickActions = transparentPanel(new GridLayout(2, 2, 12, 12));
        quickActions.add(quickActionButton(t("Магазин", "Store"), t("Каталог моделей и планы", "Model catalog and plans"), () -> switchPage(PAGE_STORE)));
        quickActions.add(quickActionButton("Live AI", t("Камера, режимы и готовность", "Camera, modes and readiness"), () -> switchPage(PAGE_LIVE)));
        quickActions.add(quickActionButton(t("Файлы", "Files"), t("Модели, app home и peers", "Models, app home and peers"), () -> switchPage(PAGE_FILES)));
        quickActions.add(quickActionButton(t("Настройки", "Settings"), t("Тема, язык и orchestration", "Theme, language and orchestration"), () -> switchPage(PAGE_SETTINGS)));
        page.add(quickActions);

        RoundedPanel statusStrip = subtleCard(palette.surface(), 24);
        statusStrip.setLayout(new BoxLayout(statusStrip, BoxLayout.Y_AXIS));
        statusStrip.setBorder(new EmptyBorder(16, 18, 16, 18));
        statusStripTitleLabel = label(t("AI-панель", "AI panel"), fonts.body().deriveFont(Font.BOLD, 14f), palette.text());
        statusStripBodyLabel = label(
            t(
                "Если модель ещё не выбрана, откройте Магазин и укажите GGUF. После этого relay и orchestration станут полноценными.",
                "If no model is selected yet, open Store and choose a GGUF. Relay and orchestration become fully usable after that."
            ),
            fonts.body().deriveFont(Font.PLAIN, 12f),
            palette.textMuted()
        );
        statusStrip.add(statusStripTitleLabel);
        statusStrip.add(Box.createVerticalStrut(4));
        statusStrip.add(statusStripBodyLabel);
        page.add(statusStrip);

        RoundedPanel relayCard = cardPanel(30);
        relayCard.setLayout(new BoxLayout(relayCard, BoxLayout.Y_AXIS));
        relayCard.setBorder(new EmptyBorder(18, 18, 18, 18));
        relayCard.add(label(t("AI composer", "AI composer"), fonts.body().deriveFont(Font.BOLD, 16f), palette.text()));
        relayCard.add(Box.createVerticalStrut(6));
        relayCard.add(label(
            t(
                "Единый composer как в Android: prompt, mode chips, вложения и relay на Android-узел через локальную сеть.",
                "Unified composer like Android: prompt, mode chips, attachments and relay to an Android node over the local network."
            ),
            fonts.body().deriveFont(Font.PLAIN, 12f),
            palette.textMuted()
        ));
        relayCard.add(Box.createVerticalStrut(14));

        relayHostCombo = comboBox();
        relayHostCombo.setEditable(true);
        relayPortField = textField();
        relayPortField.setColumns(8);
        relayModeCombo = comboBox(
            new ModeOption("single_model", t("Обычный / Standard", "Standard / Normal")),
            new ModeOption("single_model", t("Размышление / Reason", "Reason / Think")),
            new ModeOption("small_model_orchestra", t("Оркестр / Orchestra", "Orchestra"))
        );
        relaySystemPromptField = textField();
        relayPromptArea = textArea(5, false);
        relayResultArea = textArea(8, true);
        relaySendButton = actionButton(t("Отправить в Android", "Send to Android"), false);
        attachFileButton = actionButton(t("Файл", "File"), true);
        attachPhotoButton = actionButton(t("Фото", "Photo"), true);
        clearAttachmentButton = actionButton(t("Очистить", "Clear"), true);
        attachmentSummaryLabel = label(t("Вложений нет", "No attachments"), fonts.body().deriveFont(Font.PLAIN, 12f), palette.textMuted());

        relayCard.add(labeledFieldRow(t("Узел / Node", "Node / Host"), t("Выберите обнаруженный Android-узел или введите host вручную.", "Pick a discovered Android node or enter a host manually."), relayHostCombo));
        relayCard.add(Box.createVerticalStrut(12));

        JPanel relayTopRow = transparentPanel(new GridLayout(1, 2, 12, 12));
        relayTopRow.add(labeledFieldPanel(t("Порт", "Remote port"), t("По умолчанию используется Android LAN port.", "The Android LAN port is used by default."), relayPortField));
        relayTopRow.add(labeledFieldPanel(t("Режим", "Mode"), t("Обычный, размышление или оркестрация.", "Normal, thinking or orchestration."), relayModeCombo));
        relayCard.add(relayTopRow);
        relayCard.add(Box.createVerticalStrut(12));

        relayCard.add(labeledFieldRow(t("System prompt", "System prompt"), t("Необязательная системная инструкция для Android runtime.", "Optional system instruction for the Android runtime."), relaySystemPromptField));
        relayCard.add(Box.createVerticalStrut(12));
        relayCard.add(labeledFieldRow(t("Сообщение", "Message"), t("Основной пользовательский prompt для телефона.", "Main user prompt for the phone."), scrollWrap(relayPromptArea)));
        relayCard.add(Box.createVerticalStrut(12));

        JPanel attachmentRow = transparentPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        attachmentRow.add(attachPhotoButton);
        attachmentRow.add(attachFileButton);
        attachmentRow.add(clearAttachmentButton);
        relayCard.add(attachmentRow);
        relayCard.add(Box.createVerticalStrut(6));
        relayCard.add(attachmentSummaryLabel);
        relayCard.add(Box.createVerticalStrut(12));
        relayCard.add(relaySendButton);
        relayCard.add(Box.createVerticalStrut(14));
        relayCard.add(labeledFieldRow("Execution result", t("JSON-ответ Android-узла или ошибка relay.", "Android node JSON response or relay error."), scrollWrap(relayResultArea)));
        page.add(relayCard);

        RoundedPanel logCard = cardPanel(28);
        logCard.setLayout(new BoxLayout(logCard, BoxLayout.Y_AXIS));
        logCard.setBorder(new EmptyBorder(18, 18, 18, 18));
        logCard.add(label(t("Журнал сессии", "Session log"), fonts.body().deriveFont(Font.BOLD, 16f), palette.text()));
        logCard.add(Box.createVerticalStrut(8));
        logArea = textArea(10, true);
        logArea.setText(sessionLog.toString());
        logCard.add(scrollWrap(logArea));
        page.add(logCard);
        return page;
    }

    private JPanel buildStorePage() {
        JPanel page = pageColumn();

        RoundedPanel intro = new RoundedPanel(palette.primaryContainer(), palette.outline(), 30);
        intro.setLayout(new BoxLayout(intro, BoxLayout.Y_AXIS));
        intro.setBorder(new EmptyBorder(20, 20, 20, 20));
        intro.add(label(t("Магазин моделей", "Model Store"), fonts.display().deriveFont(Font.BOLD, 24f), palette.text()));
        intro.add(Box.createVerticalStrut(8));
        storeSummaryLabel = label(
            t(
                "Visual store-поверхность для локальных GGUF и будущего каталога. Сейчас показывает установленные модели честно, без фальшивых превью.",
                "Visual store surface for local GGUF models and the future catalog. It currently shows installed models honestly, without fake previews."
            ),
            fonts.body().deriveFont(Font.PLAIN, 13f),
            palette.textMuted()
        );
        intro.add(storeSummaryLabel);
        page.add(intro);
        page.add(buildModelsPage());
        return page;
    }

    private JPanel buildLivePage() {
        JPanel page = pageColumn();

        RoundedPanel hero = new RoundedPanel(palette.tertiaryContainer(), palette.outline(), 30);
        hero.setLayout(new BoxLayout(hero, BoxLayout.Y_AXIS));
        hero.setBorder(new EmptyBorder(20, 20, 20, 20));
        hero.add(label("Live AI", fonts.display().deriveFont(Font.BOLD, 24f), palette.text()));
        hero.add(Box.createVerticalStrut(8));
        hero.add(label(
            t(
                "First-class Live AI экран в стиле Android. Desktop-камера и локальный live runtime ещё не доведены до полного parity, поэтому readiness показывается честно.",
                "First-class Live AI surface in the Android style. Desktop camera and local live runtime are not at full parity yet, so readiness is shown honestly."
            ),
            fonts.body().deriveFont(Font.PLAIN, 13f),
            palette.textMuted()
        ));
        page.add(hero);

        RoundedPanel readiness = cardPanel(28);
        readiness.setLayout(new BoxLayout(readiness, BoxLayout.Y_AXIS));
        readiness.setBorder(new EmptyBorder(18, 18, 18, 18));
        liveModelLabel = label(t("Модель Live AI: не выбрана", "Live AI model: none"), fonts.body().deriveFont(Font.BOLD, 15f), palette.text());
        liveStatusLabel = label(t("Статус: ожидает конфигурацию", "Status: waiting for configuration"), fonts.body().deriveFont(Font.PLAIN, 12f), palette.textMuted());
        liveModesLabel = label(
            t("Режимы: Объекты, Лица, Текст, Вопрос по кадру", "Modes: Objects, Faces, Text, Ask frame"),
            fonts.body().deriveFont(Font.PLAIN, 12f),
            palette.textMuted()
        );
        readiness.add(liveModelLabel);
        readiness.add(Box.createVerticalStrut(6));
        readiness.add(liveStatusLabel);
        readiness.add(Box.createVerticalStrut(6));
        readiness.add(liveModesLabel);
        page.add(readiness);

        JPanel actionRow = transparentPanel(new GridLayout(1, 2, 12, 12));
        actionRow.add(quickActionButton(t("Открыть Home", "Open Home"), t("Composer и relay на Android", "Composer and Android relay"), () -> switchPage(PAGE_HOME)));
        actionRow.add(quickActionButton(t("Настроить модель", "Configure model"), t("Выбрать preferred live model", "Pick preferred live model"), () -> switchPage(PAGE_SETTINGS)));
        page.add(actionRow);
        return page;
    }

    private JPanel buildFilesPage() {
        JPanel page = pageColumn();

        RoundedPanel hero = new RoundedPanel(palette.secondaryContainer(), palette.outline(), 30);
        hero.setLayout(new BoxLayout(hero, BoxLayout.Y_AXIS));
        hero.setBorder(new EmptyBorder(20, 20, 20, 20));
        hero.add(label(t("Файлы и рабочее пространство", "Files and workspace"), fonts.display().deriveFont(Font.BOLD, 24f), palette.text()));
        hero.add(Box.createVerticalStrut(8));
        filesSummaryLabel = label(
            t(
                "Rounded workspace для моделей, app home и peer registry. Это не сухая service-панель, а продуктовая рабочая поверхность.",
                "Rounded workspace for models, app home and the peer registry. This is a product workspace, not a dry service panel."
            ),
            fonts.body().deriveFont(Font.PLAIN, 13f),
            palette.textMuted()
        );
        hero.add(filesSummaryLabel);
        page.add(hero);

        RoundedPanel paths = cardPanel(28);
        paths.setLayout(new BoxLayout(paths, BoxLayout.Y_AXIS));
        paths.setBorder(new EmptyBorder(18, 18, 18, 18));
        filesPathsLabel = label("", fonts.body().deriveFont(Font.PLAIN, 13f), palette.text());
        paths.add(filesPathsLabel);
        paths.add(Box.createVerticalStrut(12));

        JPanel buttons = transparentPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        JButton openModels = actionButton(t("Открыть models", "Open models"), true);
        JButton openHome = actionButton(t("Открыть app home", "Open app home"), true);
        openModels.addActionListener(event -> openModelsFolder());
        openHome.addActionListener(event -> openAppHome());
        buttons.add(openModels);
        buttons.add(openHome);
        paths.add(buttons);
        page.add(paths);
        return page;
    }

    private JPanel buildNodesPageV2() {
        JPanel page = pageColumn();

        RoundedPanel hero = new RoundedPanel(palette.surface(), palette.outline(), 30);
        hero.setLayout(new BoxLayout(hero, BoxLayout.Y_AXIS));
        hero.setBorder(new EmptyBorder(18, 18, 18, 18));
        hero.add(label(t("Node Farm", "Node Farm"), fonts.display().deriveFont(Font.BOLD, 22f), palette.text()));
        hero.add(Box.createVerticalStrut(6));
        hero.add(label(
            t(
                "Вторичный utility-экран для LAN-узлов. Основной landing UX теперь идёт через Home, а не через инфраструктурный dashboard.",
                "Secondary utility screen for LAN nodes. The main landing UX now goes through Home instead of an infrastructure dashboard."
            ),
            fonts.body().deriveFont(Font.PLAIN, 12f),
            palette.textMuted()
        ));
        page.add(hero);
        page.add(buildNodesPage());
        return page;
    }

    private JPanel buildSettingsPageV2(WindowsHubDesktopSettings settings) {
        JPanel page = pageColumn();

        RoundedPanel hero = new RoundedPanel(palette.primaryContainer(), palette.outline(), 30);
        hero.setLayout(new BoxLayout(hero, BoxLayout.Y_AXIS));
        hero.setBorder(new EmptyBorder(20, 20, 20, 20));
        hero.add(label(t("Настройки", "Settings"), fonts.display().deriveFont(Font.BOLD, 24f), palette.text()));
        hero.add(Box.createVerticalStrut(8));
        hero.add(label(
            t(
                "Тот же product style, что и в Android: темы, язык, LAN/orchestra, preferred models и secondary utilities.",
                "The same product style as Android: themes, language, LAN/orchestra, preferred models and secondary utilities."
            ),
            fonts.body().deriveFont(Font.PLAIN, 13f),
            palette.textMuted()
        ));
        page.add(hero);

        localeCombo = comboBox(WindowsHubDesktopSettings.AppLocale.values());
        JPanel localeCard = settingsCard(t("Язык интерфейса", "Interface language"), t("RU/EN bilingual shell. System использует язык ОС.", "RU/EN bilingual shell. System follows the OS language."));
        addComboRow(localeCard, t("Язык", "Language"), t("Переключает product copy и screen labels.", "Switches product copy and screen labels."), localeCombo);
        page.add(localeCard);

        openNodeFarmButton = actionButton(t("Node Farm", "Node Farm"), true);
        openRuntimeButton = actionButton(t("Runtime Monitor", "Runtime Monitor"), true);
        openApiButton = actionButton(t("Developer / API", "Developer / API"), true);
        openDownloadsButton = actionButton(t("Downloads", "Downloads"), true);

        JPanel utilityCard = settingsCard(t("Utilities", "Utilities"), t("Вторичные desktop-инструменты, убранные из роли главного экрана.", "Secondary desktop tools removed from the role of the main screen."));
        JPanel utilityGrid = transparentPanel(new GridLayout(2, 2, 12, 12));
        utilityGrid.add(openNodeFarmButton);
        utilityGrid.add(openRuntimeButton);
        utilityGrid.add(openApiButton);
        utilityGrid.add(openDownloadsButton);
        utilityCard.add(utilityGrid);
        page.add(utilityCard);

        page.add(buildSettingsPage(settings));
        return page;
    }

    private JComponent wrapPage(JComponent page) {
        JScrollPane scrollPane = new JScrollPane(page);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getViewport().setBackground(palette.background());
        scrollPane.getVerticalScrollBar().setUnitIncrement(18);
        return scrollPane;
    }

    private JPanel buildHomePage() {
        JPanel page = pageColumn();

        RoundedPanel heroCard = cardPanel(28);
        heroCard.setLayout(new BoxLayout(heroCard, BoxLayout.Y_AXIS));
        heroCard.setBorder(new EmptyBorder(20, 20, 20, 20));
        heroCard.add(label("SantiyaLocalAiHub", fonts.display().deriveFont(Font.BOLD, 28f), palette.text()));
        heroCard.add(Box.createVerticalStrut(8));
        heroCard.add(label(
            "Локальный AI hub для моделей, orchestration и связи с Android-узлами в локальной сети.",
            fonts.body().deriveFont(Font.PLAIN, 14f),
            palette.textMuted()
        ));
        heroCard.add(Box.createVerticalStrut(16));

        RoundedPanel modelStrip = subtleCard(palette.primaryContainer(), 999);
        modelStrip.setLayout(new BoxLayout(modelStrip, BoxLayout.Y_AXIS));
        modelStrip.setBorder(new EmptyBorder(14, 16, 14, 16));
        heroModelValueLabel = label("Текущая модель: не выбрана", fonts.body().deriveFont(Font.BOLD, 14f), palette.text());
        heroRuntimeValueLabel = label("Runtime: stopped", fonts.body().deriveFont(Font.PLAIN, 12f), palette.textMuted());
        heroLanValueLabel = label("LAN: disabled", fonts.body().deriveFont(Font.PLAIN, 12f), palette.textMuted());
        modelStrip.add(heroModelValueLabel);
        modelStrip.add(Box.createVerticalStrut(4));
        modelStrip.add(heroRuntimeValueLabel);
        modelStrip.add(Box.createVerticalStrut(2));
        modelStrip.add(heroLanValueLabel);
        heroCard.add(modelStrip);
        page.add(heroCard);

        JPanel metricsRow = transparentPanel(new GridLayout(1, 3, 12, 12));
        metricInstalledValueLabel = metricCard(metricsRow, "Установлено", "0");
        metricRamValueLabel = metricCard(metricsRow, "RAM", "0 GB");
        metricNodesValueLabel = metricCard(metricsRow, "LAN узлы", "0");
        page.add(metricsRow);

        JPanel quickActions = transparentPanel(new GridLayout(2, 2, 12, 12));
        quickActions.add(quickActionButton("Live Beta", "Камера, лица и VLM", () ->
            showInfo("Live Beta", "Этот сценарий уже есть в Android-приложении. В Windows Hub runtime для Live пока не доведён.")
        ));
        quickActions.add(quickActionButton("Модели", "Выбор и планирование GGUF", () -> switchPage(PAGE_MODELS)));
        quickActions.add(quickActionButton("Магазин", "Desktop path для моделей", () -> {
            switchPage(PAGE_MODELS);
            openModelsFolder();
        }));
        quickActions.add(quickActionButton("Файлы", "App home и peers registry", this::openAppHome));
        page.add(quickActions);

        RoundedPanel statusStrip = subtleCard(palette.surfaceVariant(), 24);
        statusStrip.setLayout(new BoxLayout(statusStrip, BoxLayout.Y_AXIS));
        statusStrip.setBorder(new EmptyBorder(16, 18, 16, 18));
        statusStripTitleLabel = label("Активная AI-модель", fonts.body().deriveFont(Font.BOLD, 14f), palette.text());
        statusStripBodyLabel = label(
            "Откройте страницу моделей или Android relay ниже, чтобы начать рабочий сценарий.",
            fonts.body().deriveFont(Font.PLAIN, 12f),
            palette.textMuted()
        );
        statusStrip.add(statusStripTitleLabel);
        statusStrip.add(Box.createVerticalStrut(4));
        statusStrip.add(statusStripBodyLabel);
        page.add(statusStrip);

        RoundedPanel relayCard = cardPanel(28);
        relayCard.setLayout(new BoxLayout(relayCard, BoxLayout.Y_AXIS));
        relayCard.setBorder(new EmptyBorder(18, 18, 18, 18));
        relayCard.add(label("Forward Prompt to Android", fonts.body().deriveFont(Font.BOLD, 16f), palette.text()));
        relayCard.add(Box.createVerticalStrut(6));
        relayCard.add(label(
            "Встроенный desktop relay использует тот же LAN path, что и Android dashboard: host + prompt + orchestration mode.",
            fonts.body().deriveFont(Font.PLAIN, 12f),
            palette.textMuted()
        ));
        relayCard.add(Box.createVerticalStrut(14));

        relayHostCombo = comboBox();
        relayHostCombo.setEditable(true);
        relayPortField = textField();
        relayPortField.setColumns(8);
        relayModeCombo = comboBox(
            new ModeOption("single_model", "Single model"),
            new ModeOption("small_model_orchestra", "Small orchestra")
        );
        relaySystemPromptField = textField();
        relayPromptArea = textArea(5, false);
        relayResultArea = textArea(8, true);
        relaySendButton = actionButton("Отправить в Android", false);

        relayCard.add(labeledFieldRow("Host / node", "Выберите обнаруженный Android node или введите host вручную.", relayHostCombo));
        relayCard.add(Box.createVerticalStrut(12));

        JPanel relayTopRow = transparentPanel(new GridLayout(1, 2, 12, 12));
        relayTopRow.add(labeledFieldPanel("Remote port", "По умолчанию используется Android LAN port.", relayPortField));
        relayTopRow.add(labeledFieldPanel("Mode", "Обычный relay или orchestration hint.", relayModeCombo));
        relayCard.add(relayTopRow);
        relayCard.add(Box.createVerticalStrut(12));

        relayCard.add(labeledFieldRow("System prompt", "Необязательная system-инструкция для Android runtime.", relaySystemPromptField));
        relayCard.add(Box.createVerticalStrut(12));
        relayCard.add(labeledFieldRow("Prompt", "Основной пользовательский prompt для отправки на телефон.", scrollWrap(relayPromptArea)));
        relayCard.add(Box.createVerticalStrut(12));
        relayCard.add(relaySendButton);
        relayCard.add(Box.createVerticalStrut(14));
        relayCard.add(labeledFieldRow("Execution result", "JSON-ответ Android node или ошибка relay.", scrollWrap(relayResultArea)));
        page.add(relayCard);

        RoundedPanel logCard = cardPanel(28);
        logCard.setLayout(new BoxLayout(logCard, BoxLayout.Y_AXIS));
        logCard.setBorder(new EmptyBorder(18, 18, 18, 18));
        logCard.add(label("Session log", fonts.body().deriveFont(Font.BOLD, 16f), palette.text()));
        logCard.add(Box.createVerticalStrut(8));
        logArea = textArea(10, true);
        logArea.setText(sessionLog.toString());
        logCard.add(scrollWrap(logArea));
        page.add(logCard);

        return page;
    }

    private JPanel buildNodesPage() {
        JPanel page = pageColumn();

        RoundedPanel summaryCard = cardPanel(28);
        summaryCard.setLayout(new BoxLayout(summaryCard, BoxLayout.Y_AXIS));
        summaryCard.setBorder(new EmptyBorder(18, 18, 18, 18));
        summaryCard.add(label("LAN nodes", fonts.display().deriveFont(Font.BOLD, 22f), palette.text()));
        summaryCard.add(Box.createVerticalStrut(8));
        nodesSummaryLabel = label(
            "Windows hub scans Android peers on the local network and keeps manual peers from peers.csv.",
            fonts.body().deriveFont(Font.PLAIN, 13f),
            palette.textMuted()
        );
        summaryCard.add(nodesSummaryLabel);
        page.add(summaryCard);

        nodesContainer = transparentPanel();
        nodesContainer.setLayout(new BoxLayout(nodesContainer, BoxLayout.Y_AXIS));
        page.add(nodesContainer);
        return page;
    }

    private JPanel buildModelsPage() {
        JPanel page = pageColumn();

        RoundedPanel controlsCard = cardPanel(28);
        controlsCard.setLayout(new BoxLayout(controlsCard, BoxLayout.Y_AXIS));
        controlsCard.setBorder(new EmptyBorder(18, 18, 18, 18));
        controlsCard.add(label("GGUF models", fonts.display().deriveFont(Font.BOLD, 22f), palette.text()));
        controlsCard.add(Box.createVerticalStrut(8));
        controlsCard.add(label(
            "Эта страница повторяет Android flow выбора моделей: installed models, preferred targets и distributed plan preview.",
            fonts.body().deriveFont(Font.PLAIN, 13f),
            palette.textMuted()
        ));
        controlsCard.add(Box.createVerticalStrut(14));

        modelsDirField = textField();
        peersFileField = textField();
        browseModelsButton = actionButton("Выбрать папку", true);
        browsePeersButton = actionButton("Выбрать CSV", true);
        openModelsFolderButton = actionButton("Открыть models", false);
        openAppHomeButton = actionButton("Открыть app home", true);

        controlsCard.add(labeledFieldRow("Models folder", "Папка, которую hub сканирует на .gguf-файлы.", fieldWithButton(modelsDirField, browseModelsButton)));
        controlsCard.add(Box.createVerticalStrut(12));
        controlsCard.add(labeledFieldRow("Peers file", "Manual Windows peers registry для planning и LAN overlays.", fieldWithButton(peersFileField, browsePeersButton)));
        controlsCard.add(Box.createVerticalStrut(12));
        JPanel actions = transparentPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actions.add(openModelsFolderButton);
        actions.add(openAppHomeButton);
        controlsCard.add(actions);
        page.add(controlsCard);

        modelListModel = new DefaultListModel<>();
        modelList = new JList<>(modelListModel);
        modelList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        modelList.setCellRenderer(new ModelCellRenderer());
        modelList.setFont(fonts.body().deriveFont(Font.PLAIN, 14f));
        modelList.setBackground(palette.surface());
        modelList.setForeground(palette.text());
        modelList.setBorder(new EmptyBorder(8, 8, 8, 8));

        planArea = textArea(18, true);
        planArea.setText("Выберите модель, чтобы увидеть distributed plan.");

        JScrollPane listScroll = scrollWrap(modelList);
        JScrollPane planScroll = scrollWrap(planArea);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, listScroll, planScroll);
        splitPane.setResizeWeight(0.42);
        splitPane.setBorder(BorderFactory.createEmptyBorder());
        splitPane.setOpaque(false);
        splitPane.setDividerSize(10);
        page.add(splitPane);
        return page;
    }

    private JPanel buildSettingsPage(WindowsHubDesktopSettings settings) {
        JPanel page = pageColumn();

        RoundedPanel intro = cardPanel(28);
        intro.setLayout(new BoxLayout(intro, BoxLayout.Y_AXIS));
        intro.setBorder(new EmptyBorder(18, 18, 18, 18));
        intro.add(label("Настройки", fonts.display().deriveFont(Font.BOLD, 22f), palette.text()));
        intro.add(Box.createVerticalStrut(8));
        intro.add(label(
            "Структура повторяет Android Settings: theme presets, LAN/orchestra/external access, preferred models и runtime toggles.",
            fonts.body().deriveFont(Font.PLAIN, 13f),
            palette.textMuted()
        ));
        intro.add(Box.createVerticalStrut(14));
        saveSettingsButton = actionButton("Сохранить", false);
        saveAndRestartButton = actionButton("Сохранить и перезапустить hub", true);
        JPanel saveActions = transparentPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        saveActions.add(saveSettingsButton);
        saveActions.add(saveAndRestartButton);
        intro.add(saveActions);
        page.add(intro);

        page.add(sectionCard("Android parity", "Эти группы повторяют Android UX. На Windows часть флагов уже влияет на runtime, часть пока хранится как desktop contract и используется как future-ready configuration.",
            parityNote("Уже живые на Windows: theme preset, models folder, peers file, pairing token, LAN enable, Android port, relay composer."),
            parityNote("Паритетно сохранены, но пока не исполняются Windows runtime напрямую: tool calling, AI memory, TTS preload, orchestra role assignment, external app access.")
        ));

        portField = textField();
        androidPortField = textField();
        coreUrlField = textField();
        tokenField = passwordField();
        generateTokenButton = actionButton("Сгенерировать token", true);

        streamingCheck = toggle();
        chatMemoryCheck = toggle();
        toolCallingCheck = toggle();
        toolCallingBypassCheck = toggle();
        imageBlurCheck = toggle();
        loadTtsCheck = toggle();
        codeHighlightCheck = toggle();
        aiMemoryCheck = toggle();
        askReloadCheck = toggle();
        hardwareTuningCheck = toggle();
        externalAccessCheck = toggle();
        lanEnabledCheck = toggle();
        advertiseLocalNodeCheck = toggle();
        orchestraEnabledCheck = toggle();
        orchestraAutoAssignCheck = toggle();
        orchestraLanSpilloverCheck = toggle();

        themePresetCombo = comboBox(WindowsHubDesktopSettings.ThemePreset.values());
        performanceModeCombo = comboBox(WindowsHubDesktopSettings.PerformanceMode.values());
        accelerationModeCombo = comboBox(WindowsHubDesktopSettings.AccelerationMode.values());
        preferredChatModelCombo = comboBox();
        preferredLiveModelCombo = comboBox();
        preferredImageModelCombo = comboBox();

        JPanel connectionCard = settingsCard("Hub runtime", "Базовые desktop-параметры для Windows control plane.");
        addFieldRow(connectionCard, "Hub port", "Локальный HTTP-порт для dashboard и control plane.", portField);
        addFieldRow(connectionCard, "Android port", "LAN API port, который использует Android node.", androidPortField);
        addFieldRow(connectionCard, "Pairing token", "Общий секрет для relay между Windows и Android.", fieldWithButton(tokenField, generateTokenButton));
        addFieldRow(connectionCard, "Native core URL", "Optional compatibility bridge to windows-core, for example http://127.0.0.1:17861.", coreUrlField);
        page.add(connectionCard);

        JPanel themeCard = settingsCard("Theme", "Те же presets, что и в Android app: System, Midnight, Mono, Marble.");
        addComboRow(themeCard, "Theme preset", "Применяется к desktop shell сразу после выбора.", themePresetCombo);
        page.add(themeCard);

        JPanel generalCard = settingsCard("Общие", "Главные Android-style toggles для chat/tooling shell.");
        addToggleRow(generalCard, "Tool calling", "Разрешить инструменты в общем shell UX.", toolCallingCheck);
        addToggleRow(generalCard, "Bypass tool model check", "Хранит флаг Android compatibility для моделей без chat template.", toolCallingBypassCheck);
        addToggleRow(generalCard, "Streaming", "Потоковый ответ в сценариях генерации.", streamingCheck);
        addToggleRow(generalCard, "Chat memory", "Помнить историю диалога между сообщениями.", chatMemoryCheck);
        addToggleRow(generalCard, "Code highlight", "Подсветка кода в ответах.", codeHighlightCheck);
        addToggleRow(generalCard, "Ask reload dialog", "Спрашивать о перезагрузке последней модели.", askReloadCheck);
        page.add(generalCard);

        JPanel preferredCard = settingsCard("Preferred models", "Те же целевые роли, что и на Android: chat/live/image.");
        addComboRow(preferredCard, "Chat model", "Модель по умолчанию для chat flow.", preferredChatModelCombo);
        addComboRow(preferredCard, "Live / assistant", "Модель для live/assistant сценариев.", preferredLiveModelCombo);
        addComboRow(preferredCard, "Image generation", "Модель для генерации изображений.", preferredImageModelCombo);
        page.add(preferredCard);

        JPanel lanCard = settingsCard("LAN hub", "Android-style LAN settings для discovery и prompt relay.");
        addToggleRow(lanCard, "Enable LAN hub", "Влияет на LAN discovery и relay endpoint в Windows runtime.", lanEnabledCheck);
        addToggleRow(lanCard, "Advertise local node", "Сохраняется как parity flag для локального узла.", advertiseLocalNodeCheck);
        page.add(lanCard);

        JPanel orchestraCard = settingsCard("Orchestra", "Группа настроек для multi-model orchestration.");
        addToggleRow(orchestraCard, "Enable orchestra", "Router + specialist roles shell contract.", orchestraEnabledCheck);
        addToggleRow(orchestraCard, "Auto assign roles", "Автоназначение ролей под доступные модели.", orchestraAutoAssignCheck);
        addToggleRow(orchestraCard, "Allow LAN spillover", "Разрешить вынос отдельных задач на LAN peer.", orchestraLanSpilloverCheck);
        page.add(orchestraCard);

        JPanel hardwareCard = settingsCard("Hardware tuning", "Preset matrix для runtime policy.");
        addToggleRow(hardwareCard, "Auto hardware tuning", "Android-style performance preset switching.", hardwareTuningCheck);
        addComboRow(hardwareCard, "Performance mode", "Performance / Balanced / Power saving.", performanceModeCombo);
        addComboRow(hardwareCard, "Acceleration mode", "Auto / GPU / CPU policy flag.", accelerationModeCombo);
        page.add(hardwareCard);

        JPanel featuresCard = settingsCard("Additional features", "Паритетные флаги для памяти, TTS и image UX.");
        addToggleRow(featuresCard, "AI memory", "Remember facts across conversations.", aiMemoryCheck);
        addToggleRow(featuresCard, "Load TTS on start", "Автозагрузка TTS runtime.", loadTtsCheck);
        addToggleRow(featuresCard, "Blur generated images", "Blur-to-reveal для image outputs.", imageBlurCheck);
        addToggleRow(featuresCard, "External app access", "Разрешить доступ другим приложениям к AI layer.", externalAccessCheck);
        page.add(featuresCard);

        JPanel aboutCard = settingsCard("About", "Desktop shell aligned with the Android app language.");
        aboutCard.add(label("Version 1.0.0", fonts.body().deriveFont(Font.PLAIN, 13f), palette.textMuted()));
        aboutCard.add(Box.createVerticalStrut(4));
        aboutCard.add(label("Fonts: Manrope + Maple Mono when available from bundled assets.", fonts.body().deriveFont(Font.PLAIN, 12f), palette.textMuted()));
        page.add(aboutCard);

        applySettingsToControls(settings);
        return page;
    }

    private JPanel buildFooter() {
        RoundedPanel footer = new RoundedPanel(palette.surface(), palette.outline(), 24);
        footer.setLayout(new BorderLayout());
        footer.setBorder(new EmptyBorder(12, 16, 12, 16));
        footerLabel = label("Desktop shell ready.", fonts.body().deriveFont(Font.PLAIN, 12f), palette.textMuted());
        footer.add(footerLabel, BorderLayout.CENTER);
        return footer;
    }

    private void wireActions() {
        navigationButtons.forEach((pageId, button) ->
            button.addActionListener(event -> switchPage(pageId))
        );

        startButton.addActionListener(event -> startHub());
        stopButton.addActionListener(event -> stopHub());
        restartButton.addActionListener(event -> restartHub());
        refreshButton.addActionListener(event -> scheduleRefresh(true));
        openDashboardButton.addActionListener(event -> openDashboard());

        browseModelsButton.addActionListener(event -> chooseModelsFolder());
        browsePeersButton.addActionListener(event -> choosePeersFile());
        openModelsFolderButton.addActionListener(event -> openModelsFolder());
        openAppHomeButton.addActionListener(event -> openAppHome());
        if (attachFileButton != null) {
            attachFileButton.addActionListener(event -> chooseAttachment(false));
        }
        if (attachPhotoButton != null) {
            attachPhotoButton.addActionListener(event -> chooseAttachment(true));
        }
        if (clearAttachmentButton != null) {
            clearAttachmentButton.addActionListener(event -> clearAttachment());
        }
        if (openNodeFarmButton != null) {
            openNodeFarmButton.addActionListener(event -> switchPage(PAGE_NODES));
        }
        if (openRuntimeButton != null) {
            openRuntimeButton.addActionListener(event -> switchPage(PAGE_HOME));
        }
        if (openApiButton != null) {
            openApiButton.addActionListener(event -> openDashboard());
        }
        if (openDownloadsButton != null) {
            openDownloadsButton.addActionListener(event -> openModelsFolder());
        }

        relaySendButton.addActionListener(event -> sendRelayPrompt());
        modelList.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                ModelListItem item = modelList.getSelectedValue();
                selectedModelId = item == null ? "" : item.id();
                scheduleRefresh(false);
            }
        });

        saveSettingsButton.addActionListener(event -> saveSettings(false));
        saveAndRestartButton.addActionListener(event -> saveSettings(true));
        generateTokenButton.addActionListener(event -> tokenField.setText(generateToken()));

        Runnable rebuildSettingsShell = () -> {
            WindowsHubDesktopSettings snapshot = captureSettingsFromControls();
            currentSettings = snapshot;
            try {
                WindowsHubDesktopSettings.save(settingsFile, snapshot);
            } catch (IOException ignored) {
            }
            rebuildUi(snapshot, PAGE_SETTINGS);
            wireActions();
            setFooter(t("Обновлена тема: ", "Theme updated: ") + localizedThemeLabel(snapshot.themePreset) + ".");
            scheduleRefresh(false);
        };

        themePresetCombo.addActionListener(event -> {
            if (suppressEvents) {
                return;
            }
            rebuildSettingsShell.run();
        });
        if (localeCombo != null) {
            localeCombo.addActionListener(event -> {
                if (suppressEvents) {
                    return;
                }
                rebuildSettingsShell.run();
            });
        }
    }

    private void switchPage(String pageId) {
        currentPage = pageId;
        pageLayout.show(pageContainer, pageId);
        navigationButtons.forEach((id, button) -> applyNavSelection(button, Objects.equals(id, pageId)));
    }

    private void startHub() {
        if (service != null) {
            appendLog("Hub is already running.");
            setFooter("Windows hub already running.");
            return;
        }
        try {
            WindowsHubDesktopSettings settings = captureSettingsFromControls();
            Files.createDirectories(settings.modelsDir);
            if (settings.peersFile.getParent() != null) {
                Files.createDirectories(settings.peersFile.getParent());
            }
            WindowsHubDesktopSettings.save(settingsFile, settings);
            currentSettings = settings;
            service = new WindowsHubService(
                settings.port,
                settings.modelsDir,
                settings.peersFile,
                settings.pairingToken,
                settings.androidNodePort,
                settings.coreBaseUrl,
                settings.lanEnabled,
                settings.advertiseLocalNode
            );
            service.start();
            runningSettings = copyOf(settings);
            refreshTick = 0;
            refreshTimer.start();
            appendLog(service.statusLine());
            applyRunningState();
            setFooter("Windows hub started on " + service.baseUrl() + ".");
            scheduleRefresh(true);
        } catch (Exception error) {
            appendLog("Start failed: " + safeMessage(error));
            setFooter("Failed to start hub: " + safeMessage(error));
            writeDesktopErrorLog("startHub failed", error);
            applyRunningState();
            JOptionPane.showMessageDialog(
                frame,
                safeMessage(error),
                "Windows Hub failed to start",
                JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private void stopHub() {
        if (service == null) {
            return;
        }
        try {
            service.stop();
            appendLog("Hub stopped.");
        } finally {
            service = null;
            runningSettings = null;
            refreshTimer.stop();
            refreshInFlight.set(false);
            applyRunningState();
            clearRuntimeViews();
            setFooter("Windows hub stopped.");
        }
    }

    private void restartHub() {
        saveSettings(true);
    }

    private void saveSettings(boolean restartHub) {
        try {
            WindowsHubDesktopSettings settings = captureSettingsFromControls();
            WindowsHubDesktopSettings.save(settingsFile, settings);
            currentSettings = settings;
            appendLog("Settings saved.");
            if (restartHub) {
                stopHub();
                startHub();
                return;
            }
            if (requiresRestart(settings)) {
                setFooter("Settings saved. Restart hub to apply runtime-sensitive changes.");
            } else {
                setFooter("Settings saved.");
            }
        } catch (Exception error) {
            appendLog("Save failed: " + safeMessage(error));
            setFooter("Failed to save settings: " + safeMessage(error));
        }
    }

    private boolean requiresRestart(WindowsHubDesktopSettings snapshot) {
        if (runningSettings == null) {
            return false;
        }
        return snapshot.port != runningSettings.port
            || snapshot.androidNodePort != runningSettings.androidNodePort
            || !Objects.equals(snapshot.pairingToken, runningSettings.pairingToken)
            || !Objects.equals(snapshot.coreBaseUrl, runningSettings.coreBaseUrl)
            || !Objects.equals(snapshot.modelsDir, runningSettings.modelsDir)
            || !Objects.equals(snapshot.peersFile, runningSettings.peersFile)
            || snapshot.lanEnabled != runningSettings.lanEnabled
            || snapshot.advertiseLocalNode != runningSettings.advertiseLocalNode;
    }

    private void scheduleRefresh(boolean reloadState) {
        if (service == null || !refreshInFlight.compareAndSet(false, true)) {
            return;
        }
        WindowsHubService activeService = service;
        String planModelId = selectedModelId;
        ioExecutor.submit(() -> {
            try {
                if (reloadState) {
                    activeService.reloadFromDesktop();
                }
                Map<String, Object> status = activeService.desktopStatusPayload();
                List<Map<String, Object>> models = activeService.desktopModelsPayload();
                List<Map<String, Object>> nodes = activeService.desktopNodesPayload();
                Map<String, Object> plan = activeService.desktopPlanPayload(planModelId);
                SwingUtilities.invokeLater(() -> {
                    if (activeService == service) {
                        updateRuntimeViews(status, models, nodes, plan);
                    }
                    refreshInFlight.set(false);
                });
            } catch (Exception error) {
                SwingUtilities.invokeLater(() -> {
                    appendLog("Refresh failed: " + safeMessage(error));
                    setFooter("Refresh failed: " + safeMessage(error));
                    refreshInFlight.set(false);
                });
            }
        });
    }

    private void updateRuntimeViews(
        Map<String, Object> status,
        List<Map<String, Object>> models,
        List<Map<String, Object>> nodes,
        Map<String, Object> plan
    ) {
        refreshTick += 1;
        if (refreshTick % FULL_SCAN_EVERY_TICKS == 0) {
            ioExecutor.submit(() -> {
                if (service == null) {
                    return;
                }
                try {
                    service.reloadFromDesktop();
                } catch (Exception ignored) {
                }
            });
        }

        updateTopPillV2(status, models);
        updateHomeV2(status, models, nodes);
        updateNodesV2(status, nodes);
        updateStoreV2(status, models, plan);
        updateSecondarySurfaces(status, models, nodes);
        updateSettingsOptions(models);
        applyRunningState();
    }

    private void updateTopPill(Map<String, Object> status, List<Map<String, Object>> models) {
        String modelName = resolveCurrentModelName(models);
        if (modelName == null) {
            modelName = "Модель не выбрана";
        }
        boolean running = service != null;
        boolean lanEnabled = boolValue(status.get("lanEnabled"), false);
        int nodeCount = intValue(status.get("discoveredLanNodes"), 0);
        pillPrimaryLabel.setText(modelName);
        pillSecondaryLabel.setText((running ? "Running" : "Stopped") + " • LAN " + (lanEnabled ? "on" : "off") + " • nodes " + nodeCount);
    }

    private void updateHome(Map<String, Object> status, List<Map<String, Object>> models, List<Map<String, Object>> nodes) {
        String currentModel = resolveCurrentModelName(models);
        int modelCount = intValue(status.get("modelCount"), models.size());
        int nodeCount = Math.max(0, nodes.size() - 1);
        int freeRamMb = nestedInt(status, "localNode", "freeRamMb");
        int totalRamMb = nestedInt(status, "localNode", "totalRamMb");
        boolean lanEnabled = boolValue(status.get("lanEnabled"), false);

        heroModelValueLabel.setText("Текущая модель: " + (currentModel == null ? "не выбрана" : currentModel));
        heroRuntimeValueLabel.setText("Runtime: " + (service == null ? "stopped" : "running on " + service.baseUrl()));
        heroLanValueLabel.setText("LAN: " + (lanEnabled ? "enabled" : "disabled") + " • pairing " + (boolValue(status.get("pairingTokenConfigured"), false) ? "configured" : "missing"));

        metricInstalledValueLabel.setText(String.valueOf(modelCount));
        metricRamValueLabel.setText(totalRamMb > 0
            ? freeRamMb + " / " + totalRamMb + " MB"
            : "unknown");
        metricNodesValueLabel.setText(String.valueOf(nodeCount));

        if (currentModel == null) {
            statusStripTitleLabel.setText("Выберите AI-модель");
            statusStripBodyLabel.setText("Откройте страницу «Модели» и положите GGUF в папку models, чтобы planning и relay стали полноценными.");
        } else {
            statusStripTitleLabel.setText("Активная AI-модель");
            statusStripBodyLabel.setText(currentModel + " • planning available • Android relay " + (lanEnabled ? "ready" : "disabled by settings"));
        }

        updateRelayHostChoices(nodes);
        if (relayResultArea != null) {
            relayResultArea.setText(lastRelayResult);
        }
    }

    private void updateSecondarySurfaces(
        Map<String, Object> status,
        List<Map<String, Object>> models,
        List<Map<String, Object>> nodes
    ) {
        String currentModel = resolveCurrentModelName(models);
        int remoteNodes = Math.max(0, nodes.size() - 1);
        boolean lanEnabled = boolValue(status.get("lanEnabled"), false);
        if (storeSummaryLabel != null) {
            storeSummaryLabel.setText(
                t(
                    "Установлено моделей: ",
                    "Installed models: "
                )
                    + models.size()
                    + " • "
                    + t("обнаружено LAN-узлов: ", "discovered LAN nodes: ")
                    + remoteNodes
            );
        }
        if (liveModelLabel != null) {
            liveModelLabel.setText(
                t("Модель Live AI: ", "Live AI model: ")
                    + (currentModel == null ? t("не выбрана", "none") : currentModel)
            );
        }
        if (liveStatusLabel != null) {
            liveStatusLabel.setText(
                lanEnabled
                    ? t("Статус: relay и LAN готовы; desktop live-camera пока в roadmap.", "Status: relay and LAN are ready; desktop live camera is still on the roadmap.")
                    : t("Статус: включите LAN в настройках для relay и удалённых live-сценариев.", "Status: enable LAN in Settings for relay and remote live scenarios.")
            );
        }
        if (filesPathsLabel != null) {
            filesPathsLabel.setText(
                t("Models: ", "Models: ") + currentSettings.modelsDir
                    + "\nPeers: " + currentSettings.peersFile
                    + "\nApp home: " + WindowsHubApp.Arguments.defaultAppHome()
            );
        }
    }

    private void updateTopPillV2(Map<String, Object> status, List<Map<String, Object>> models) {
        String modelName = resolveCurrentModelName(models);
        if (modelName == null) {
            modelName = t("Модель не выбрана", "No model selected");
        }
        boolean running = service != null;
        boolean lanEnabled = boolValue(status.get("lanEnabled"), false);
        int nodeCount = intValue(status.get("discoveredLanNodes"), 0);
        pillPrimaryLabel.setText(modelName);
        pillSecondaryLabel.setText(
            (running ? t("Работает", "Running") : t("Остановлен", "Stopped"))
                + " • LAN "
                + (lanEnabled ? "on" : "off")
                + " • "
                + t("узлы", "nodes")
                + " "
                + nodeCount
        );
    }

    private void updateHomeV2(Map<String, Object> status, List<Map<String, Object>> models, List<Map<String, Object>> nodes) {
        String currentModel = resolveCurrentModelName(models);
        int modelCount = intValue(status.get("modelCount"), models.size());
        int nodeCount = Math.max(0, nodes.size() - 1);
        int freeRamMb = nestedInt(status, "localNode", "freeRamMb");
        int totalRamMb = nestedInt(status, "localNode", "totalRamMb");
        boolean lanEnabled = boolValue(status.get("lanEnabled"), false);

        heroModelValueLabel.setText(t("Текущая модель: ", "Current model: ") + (currentModel == null ? t("не выбрана", "none") : currentModel));
        heroRuntimeValueLabel.setText("Runtime: " + (service == null ? t("остановлен", "stopped") : t("работает на ", "running on ") + service.baseUrl()));
        heroLanValueLabel.setText("LAN: " + (lanEnabled ? t("включён", "enabled") : t("выключен", "disabled")) + " • pairing " + (boolValue(status.get("pairingTokenConfigured"), false) ? t("настроен", "configured") : t("отсутствует", "missing")));

        metricInstalledValueLabel.setText(String.valueOf(modelCount));
        metricRamValueLabel.setText(totalRamMb > 0
            ? freeRamMb + " / " + totalRamMb + " MB"
            : t("неизвестно", "unknown"));
        metricNodesValueLabel.setText(String.valueOf(nodeCount));

        if (currentModel == null) {
            statusStripTitleLabel.setText(t("Выберите AI-модель", "Choose an AI model"));
            statusStripBodyLabel.setText(
                t(
                    "Откройте Магазин и положите GGUF в папку models, чтобы planning и relay стали полноценными.",
                    "Open Store and place a GGUF into the models folder so planning and relay become fully usable."
                )
            );
        } else {
            statusStripTitleLabel.setText(t("Активная AI-модель", "Active AI model"));
            statusStripBodyLabel.setText(
                currentModel
                    + " • "
                    + t("planning доступен", "planning available")
                    + " • Android relay "
                    + (lanEnabled ? t("готов", "ready") : t("выключен в настройках", "disabled in settings"))
            );
        }

        updateRelayHostChoices(nodes);
        if (relayResultArea != null) {
            relayResultArea.setText(lastRelayResult);
        }
        if (attachmentSummaryLabel != null && pendingAttachmentPath == null) {
            attachmentSummaryLabel.setText(t("Вложений нет", "No attachments"));
        }
    }

    private void updateNodesV2(Map<String, Object> status, List<Map<String, Object>> nodes) {
        int remoteNodes = Math.max(0, nodes.size() - 1);
        nodesSummaryLabel.setText(
            t("Ручные peers: ", "Manual peers: ")
                + intValue(status.get("peerCount"), 0)
                + " • "
                + t("найдено Android-узлов: ", "discovered Android nodes: ")
                + remoteNodes
                + " • Android port: "
                + intValue(status.get("androidNodePort"), 17888)
        );

        nodesContainer.removeAll();
        for (Map<String, Object> node : nodes) {
            nodesContainer.add(nodeCard(node));
            nodesContainer.add(Box.createVerticalStrut(12));
        }
        nodesContainer.revalidate();
        nodesContainer.repaint();
    }

    private void updateStoreV2(Map<String, Object> status, List<Map<String, Object>> models, Map<String, Object> plan) {
        String previouslySelected = selectedModelId;
        modelListModel.clear();
        for (Map<String, Object> model : models) {
            ModelListItem item = new ModelListItem(
                stringValue(model.get("id")),
                stringValue(model.get("name")),
                intValue(model.get("sizeMb"), 0),
                stringValue(model.get("architecture"))
            );
            modelListModel.addElement(item);
            if (previouslySelected.isBlank()) {
                previouslySelected = item.id();
            }
        }

        if (modelListModel.isEmpty()) {
            selectedModelId = "";
            planArea.setText(
                t(
                    "GGUF-модели не найдены. Добавьте файлы в папку models, чтобы Store и planning стали рабочими.",
                    "No GGUF models were found. Add files to the models folder to activate Store and planning."
                )
            );
            return;
        }

        if (selectedModelId.isBlank()) {
            selectedModelId = previouslySelected;
        }
        selectModelInList(selectedModelId);
        planArea.setText(formatPlan(status, plan));
    }

    private void updateNodes(Map<String, Object> status, List<Map<String, Object>> nodes) {
        int remoteNodes = Math.max(0, nodes.size() - 1);
        nodesSummaryLabel.setText(
            "Manual peers: " + intValue(status.get("peerCount"), 0)
                + " • discovered Android nodes: " + remoteNodes
                + " • Android port: " + intValue(status.get("androidNodePort"), 17888)
        );

        nodesContainer.removeAll();
        for (Map<String, Object> node : nodes) {
            nodesContainer.add(nodeCard(node));
            nodesContainer.add(Box.createVerticalStrut(12));
        }
        nodesContainer.revalidate();
        nodesContainer.repaint();
    }

    private void updateModels(Map<String, Object> status, List<Map<String, Object>> models, Map<String, Object> plan) {
        String previouslySelected = selectedModelId;
        modelListModel.clear();
        for (Map<String, Object> model : models) {
            ModelListItem item = new ModelListItem(
                stringValue(model.get("id")),
                stringValue(model.get("name")),
                intValue(model.get("sizeMb"), 0),
                stringValue(model.get("architecture"))
            );
            modelListModel.addElement(item);
            if (previouslySelected.isBlank()) {
                previouslySelected = item.id();
            }
        }

        if (modelListModel.isEmpty()) {
            selectedModelId = "";
            planArea.setText("GGUF модели не найдены. Скопируйте файлы в папку models.");
            return;
        }

        if (selectedModelId.isBlank()) {
            selectedModelId = previouslySelected;
        }
        selectModelInList(selectedModelId);
        planArea.setText(formatPlan(status, plan));
    }

    private void updateSettingsOptions(List<Map<String, Object>> models) {
        List<IdLabelOption> options = new ArrayList<>();
        options.add(new IdLabelOption("", "Не выбрана"));
        for (Map<String, Object> model : models) {
            options.add(new IdLabelOption(stringValue(model.get("id")), stringValue(model.get("name"))));
        }
        refillOptionCombo(preferredChatModelCombo, options, selectedOptionId(preferredChatModelCombo, currentSettings.preferredChatModelId));
        refillOptionCombo(preferredLiveModelCombo, options, selectedOptionId(preferredLiveModelCombo, currentSettings.preferredLiveModelId));
        refillOptionCombo(preferredImageModelCombo, options, selectedOptionId(preferredImageModelCombo, currentSettings.preferredImageModelId));
    }

    private void updateRelayHostChoices(List<Map<String, Object>> nodes) {
        DefaultComboBoxModel<NodeChoice> model = new DefaultComboBoxModel<>();
        NodeChoice selected = null;
        String currentHost = editableComboValue(relayHostCombo);
        for (Map<String, Object> node : nodes) {
            if ("local".equals(stringValue(node.get("source")))) {
                continue;
            }
            NodeChoice choice = new NodeChoice(
                stringValue(node.get("host")),
                intValue(node.get("port"), currentSettings.androidNodePort),
                stringValue(node.get("displayName")) + " • " + stringValue(node.get("host"))
            );
            model.addElement(choice);
            if (!currentHost.isBlank() && currentHost.equals(choice.host())) {
                selected = choice;
            } else if (selected == null) {
                selected = choice;
            }
        }
        relayHostCombo.setModel(model);
        if (selected != null) {
            relayHostCombo.setSelectedItem(selected);
            if (relayPortField.getText().isBlank()) {
                relayPortField.setText(String.valueOf(selected.port()));
            }
        }
        if (currentHost != null && !currentHost.isBlank()) {
            relayHostCombo.getEditor().setItem(currentHost);
        }
    }

    private void clearRuntimeViews() {
        pillPrimaryLabel.setText(t("Модель не выбрана", "No model selected"));
        pillSecondaryLabel.setText(t("Windows hub остановлен", "Windows hub stopped"));
        heroModelValueLabel.setText(t("Текущая модель: не выбрана", "Current model: none"));
        heroRuntimeValueLabel.setText("Runtime: " + t("stopped", "stopped"));
        heroLanValueLabel.setText("LAN: " + t("disabled", "disabled"));
        metricInstalledValueLabel.setText("0");
        metricNodesValueLabel.setText("0");
        statusStripTitleLabel.setText(t("Hub остановлен", "Hub stopped"));
        statusStripBodyLabel.setText(t("Запустите Windows hub снова, чтобы получить live status, Store и LAN nodes.", "Start the Windows hub again to get live status, Store and LAN nodes."));
        nodesSummaryLabel.setText(t("Hub остановлен.", "Hub stopped."));
        nodesContainer.removeAll();
        nodesContainer.revalidate();
        nodesContainer.repaint();
        modelListModel.clear();
        planArea.setText(t("Hub остановлен.", "Hub stopped."));
        if (storeSummaryLabel != null) {
            storeSummaryLabel.setText(t("Store ждёт локальные модели и runtime.", "Store is waiting for local models and runtime."));
        }
        if (liveModelLabel != null) {
            liveModelLabel.setText(t("Модель Live AI: не выбрана", "Live AI model: none"));
        }
        if (liveStatusLabel != null) {
            liveStatusLabel.setText(t("Статус: hub остановлен", "Status: hub stopped"));
        }
        if (filesPathsLabel != null) {
            filesPathsLabel.setText(
                t("Models: ", "Models: ") + currentSettings.modelsDir
                    + "\nPeers: " + currentSettings.peersFile
                    + "\nApp home: " + WindowsHubApp.Arguments.defaultAppHome()
            );
        }
        clearAttachment();
    }

    private void sendRelayPrompt() {
        if (service == null) {
            showInfo("Hub stopped", "Сначала запустите Windows hub.");
            return;
        }
        String host = editableComboValue(relayHostCombo).trim();
        String prompt = relayPromptArea.getText().trim();
        String systemPrompt = relaySystemPromptField.getText().trim();
        String remotePort = relayPortField.getText().trim();
        ModeOption mode = (ModeOption) relayModeCombo.getSelectedItem();
        if (host.isBlank() || prompt.isBlank()) {
            showInfo("Relay", "Укажите host Android-узла и prompt.");
            return;
        }

        relaySendButton.setEnabled(false);
        setFooter("Sending prompt to Android node...");
        final String effectivePrompt = pendingAttachmentPath == null
            ? prompt
            : prompt
                + "\n\n[desktop_attachment kind="
                + pendingAttachmentKind
                + " path="
                + pendingAttachmentPath.toAbsolutePath()
                + "]";
        ioExecutor.submit(() -> {
            try {
                String response = postForm(
                    service.baseUrl() + "/api/lan/execute",
                    Map.of(
                        "host", host,
                        "remotePort", remotePort.isBlank() ? String.valueOf(currentSettings.androidNodePort) : remotePort,
                        "capability", "chat",
                        "prompt", effectivePrompt,
                        "systemPrompt", systemPrompt,
                        "orchestrationMode", mode == null ? "single_model" : mode.id(),
                        "modelId", currentSettings.preferredChatModelId
                    )
                );
                lastRelayResult = response;
                SwingUtilities.invokeLater(() -> {
                    relayResultArea.setText(response);
                    relaySendButton.setEnabled(true);
                    appendLog("Relay response received from " + host + ".");
                    setFooter("Relay completed.");
                });
            } catch (Exception error) {
                String message = safeMessage(error);
                lastRelayResult = "{\"ok\":false,\"error\":\"relay_failed\",\"message\":\"" + escapeJson(message) + "\"}";
                SwingUtilities.invokeLater(() -> {
                    relayResultArea.setText(lastRelayResult);
                    relaySendButton.setEnabled(true);
                    appendLog("Relay failed: " + message);
                    setFooter("Relay failed: " + message);
                });
            }
        });
    }

    private void applyRunningState() {
        boolean running = service != null;
        startButton.setEnabled(!running);
        stopButton.setEnabled(running);
        restartButton.setEnabled(running);
        openDashboardButton.setEnabled(running);
        refreshButton.setEnabled(running);

        Color accent = running ? palette.primaryContainer() : palette.tertiaryContainer();
        Color text = running ? palette.text() : palette.textMuted();
        startButton.setVisible(!running);
        stopButton.setVisible(running);
        restartButton.setVisible(running);
        pillPrimaryLabel.setForeground(text);
        pillSecondaryLabel.setForeground(palette.textMuted());
        shellSubtitleLabel.setText(running
            ? t(
                "Android-first shell активен и сейчас управляет Windows LAN hub.",
                "The Android-first shell is active and currently driving the Windows LAN hub."
            )
            : t(
                "Android-first shell готов. Запустите hub, чтобы включить planning и LAN relay.",
                "The Android-first shell is ready. Start the hub to enable planning and LAN relay."
            ));
        frame.repaint();
    }

    private void chooseModelsFolder() {
        JFileChooser chooser = new JFileChooser(modelsDirField.getText().trim());
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            modelsDirField.setText(chooser.getSelectedFile().toPath().toString());
        }
    }

    private void choosePeersFile() {
        JFileChooser chooser = new JFileChooser(peersFileField.getText().trim());
        chooser.setFileFilter(new FileNameExtensionFilter("CSV files", "csv"));
        if (chooser.showSaveDialog(frame) == JFileChooser.APPROVE_OPTION) {
            peersFileField.setText(chooser.getSelectedFile().toPath().toString());
        }
    }

    private void openDashboard() {
        if (service == null) {
            return;
        }
        openUri(service.baseUrl() + "/");
    }

    private void openModelsFolder() {
        try {
            Path modelsDir = Path.of(modelsDirField.getText().trim());
            Files.createDirectories(modelsDir);
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(modelsDir.toFile());
            }
        } catch (Exception error) {
            showError("Cannot open models folder", safeMessage(error));
        }
    }

    private void openAppHome() {
        try {
            Path appHome = WindowsHubApp.Arguments.defaultAppHome();
            Files.createDirectories(appHome);
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(appHome.toFile());
            }
        } catch (Exception error) {
            showError("Cannot open app home", safeMessage(error));
        }
    }

    private void chooseAttachment(boolean photoOnly) {
        JFileChooser chooser = new JFileChooser();
        if (photoOnly) {
            chooser.setFileFilter(new FileNameExtensionFilter("Images", "png", "jpg", "jpeg", "webp", "bmp"));
        }
        if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            pendingAttachmentPath = chooser.getSelectedFile().toPath();
            pendingAttachmentKind = photoOnly ? "photo" : "file";
            if (attachmentSummaryLabel != null) {
                attachmentSummaryLabel.setText(
                    t("Вложение: ", "Attachment: ")
                        + pendingAttachmentPath.getFileName()
                        + t(" (путь будет добавлен в prompt)", " (path will be appended to the prompt)")
                );
            }
        }
    }

    private void clearAttachment() {
        pendingAttachmentPath = null;
        pendingAttachmentKind = "";
        if (attachmentSummaryLabel != null) {
            attachmentSummaryLabel.setText(t("Вложений нет", "No attachments"));
        }
    }

    private void shutdown() {
        refreshTimer.stop();
        stopHub();
        ioExecutor.shutdownNow();
        frame.dispose();
    }

    private WindowsHubDesktopSettings captureSettingsFromControls() {
        WindowsHubDesktopSettings settings = copyOf(currentSettings);
        if (portField != null) {
            settings.port = parseInt(portField.getText().trim(), settings.port);
            settings.androidNodePort = parseInt(androidPortField.getText().trim(), settings.androidNodePort);
            settings.coreBaseUrl = coreUrlField.getText().trim();
            settings.pairingToken = new String(tokenField.getPassword()).trim();
            settings.modelsDir = Path.of(modelsDirField.getText().trim()).toAbsolutePath();
            settings.peersFile = Path.of(peersFileField.getText().trim()).toAbsolutePath();

            settings.streamingEnabled = streamingCheck.isSelected();
            settings.chatMemoryEnabled = chatMemoryCheck.isSelected();
            settings.toolCallingEnabled = toolCallingCheck.isSelected();
            settings.toolCallingBypassEnabled = toolCallingBypassCheck.isSelected();
            settings.imageBlurEnabled = imageBlurCheck.isSelected();
            settings.loadTtsOnStart = loadTtsCheck.isSelected();
            settings.codeHighlightEnabled = codeHighlightCheck.isSelected();
            settings.aiMemoryEnabled = aiMemoryCheck.isSelected();
            settings.askModelReloadDialog = askReloadCheck.isSelected();

            settings.hardwareTuningEnabled = hardwareTuningCheck.isSelected();
            settings.performanceMode = (WindowsHubDesktopSettings.PerformanceMode) performanceModeCombo.getSelectedItem();
            settings.accelerationMode = (WindowsHubDesktopSettings.AccelerationMode) accelerationModeCombo.getSelectedItem();
            settings.themePreset = (WindowsHubDesktopSettings.ThemePreset) themePresetCombo.getSelectedItem();
            settings.appLocale = localeCombo == null
                ? settings.appLocale
                : (WindowsHubDesktopSettings.AppLocale) localeCombo.getSelectedItem();

            settings.preferredChatModelId = optionId((IdLabelOption) preferredChatModelCombo.getSelectedItem());
            settings.preferredLiveModelId = optionId((IdLabelOption) preferredLiveModelCombo.getSelectedItem());
            settings.preferredImageModelId = optionId((IdLabelOption) preferredImageModelCombo.getSelectedItem());

            settings.externalAccessEnabled = externalAccessCheck.isSelected();
            settings.lanEnabled = lanEnabledCheck.isSelected();
            settings.advertiseLocalNode = advertiseLocalNodeCheck.isSelected();
            settings.orchestraEnabled = orchestraEnabledCheck.isSelected();
            settings.orchestraAutoAssign = orchestraAutoAssignCheck.isSelected();
            settings.orchestraAllowLanSpillover = orchestraLanSpilloverCheck.isSelected();
        }
        return settings;
    }

    private void applySettingsToControls(WindowsHubDesktopSettings settings) {
        portField.setText(String.valueOf(settings.port));
        androidPortField.setText(String.valueOf(settings.androidNodePort));
        coreUrlField.setText(settings.coreBaseUrl);
        tokenField.setText(settings.pairingToken.isBlank() ? generateToken() : settings.pairingToken);
        modelsDirField.setText(settings.modelsDir.toString());
        peersFileField.setText(settings.peersFile.toString());

        streamingCheck.setSelected(settings.streamingEnabled);
        chatMemoryCheck.setSelected(settings.chatMemoryEnabled);
        toolCallingCheck.setSelected(settings.toolCallingEnabled);
        toolCallingBypassCheck.setSelected(settings.toolCallingBypassEnabled);
        imageBlurCheck.setSelected(settings.imageBlurEnabled);
        loadTtsCheck.setSelected(settings.loadTtsOnStart);
        codeHighlightCheck.setSelected(settings.codeHighlightEnabled);
        aiMemoryCheck.setSelected(settings.aiMemoryEnabled);
        askReloadCheck.setSelected(settings.askModelReloadDialog);

        hardwareTuningCheck.setSelected(settings.hardwareTuningEnabled);
        performanceModeCombo.setSelectedItem(settings.performanceMode);
        accelerationModeCombo.setSelectedItem(settings.accelerationMode);
        themePresetCombo.setSelectedItem(settings.themePreset);
        if (localeCombo != null) {
            localeCombo.setSelectedItem(settings.appLocale);
        }

        externalAccessCheck.setSelected(settings.externalAccessEnabled);
        lanEnabledCheck.setSelected(settings.lanEnabled);
        advertiseLocalNodeCheck.setSelected(settings.advertiseLocalNode);
        orchestraEnabledCheck.setSelected(settings.orchestraEnabled);
        orchestraAutoAssignCheck.setSelected(settings.orchestraAutoAssign);
        orchestraLanSpilloverCheck.setSelected(settings.orchestraAllowLanSpillover);

        relayPortField.setText(String.valueOf(settings.androidNodePort));
        relaySystemPromptField.setText("");
        relayPromptArea.setText("");
        relayResultArea.setText(lastRelayResult);
        if (attachmentSummaryLabel != null) {
            attachmentSummaryLabel.setText(t("Вложений нет", "No attachments"));
        }
    }

    private String resolveCurrentModelName(List<Map<String, Object>> models) {
        String preferredId = currentSettings.preferredChatModelId;
        if (!preferredId.isBlank()) {
            for (Map<String, Object> model : models) {
                if (preferredId.equals(stringValue(model.get("id")))) {
                    return stringValue(model.get("name"));
                }
            }
        }
        return models.isEmpty() ? null : stringValue(models.get(0).get("name"));
    }

    private JPanel nodeCard(Map<String, Object> node) {
        RoundedPanel card = cardPanel(24);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(16, 16, 16, 16));

        String name = stringValue(node.get("displayName"));
        String source = stringValue(node.get("source"));
        String host = stringValue(node.get("host"));
        String platform = stringValue(node.get("platform"));
        String accelerators = stringValue(node.get("acceleratorSummary"));
        int freeRam = intValue(node.get("freeRamMb"), 0);
        int totalRam = intValue(node.get("totalRamMb"), 0);
        int cpuCores = intValue(node.get("cpuCores"), 0);
        boolean pipeline = boolValue(node.get("supportsPipelineWorker"), false);
        boolean sequential = boolValue(node.get("supportsSequentialOffload"), false);

        card.add(label(name, fonts.body().deriveFont(Font.BOLD, 15f), palette.text()));
        card.add(Box.createVerticalStrut(4));
        card.add(label(source + " • " + platform + " • " + host, fonts.body().deriveFont(Font.PLAIN, 12f), palette.textMuted()));
        card.add(Box.createVerticalStrut(10));
        card.add(label("RAM: " + freeRam + " / " + totalRam + " MB • CPU cores: " + cpuCores, fonts.body().deriveFont(Font.PLAIN, 12f), palette.text()));
        card.add(Box.createVerticalStrut(4));
        card.add(label("Accelerator: " + accelerators, fonts.body().deriveFont(Font.PLAIN, 12f), palette.textMuted()));
        card.add(Box.createVerticalStrut(4));
        card.add(label("Capabilities: sequential=" + sequential + " • pipeline=" + pipeline, fonts.body().deriveFont(Font.PLAIN, 12f), palette.textMuted()));
        Object lastSeen = node.get("lastSeenEpochMs");
        if (lastSeen instanceof Number number && number.longValue() > 0) {
            card.add(Box.createVerticalStrut(4));
            card.add(label("Last seen: " + TIME_FORMAT.format(Instant.ofEpochMilli(number.longValue())), fonts.body().deriveFont(Font.PLAIN, 12f), palette.textMuted()));
        }
        return card;
    }

    private String formatPlan(Map<String, Object> status, Map<String, Object> plan) {
        if (!boolValue(plan.get("ok"), false)) {
            return stringValue(plan.get("message"));
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> planBody = (Map<String, Object>) plan.get("plan");
        if (planBody == null) {
            return "Plan data is missing.";
        }

        StringBuilder builder = new StringBuilder();
        builder.append("Strategy: ").append(stringValue(planBody.get("strategy"))).append('\n');
        builder.append("LAN enabled: ").append(boolValue(status.get("lanEnabled"), false)).append('\n');
        builder.append("Native execution required: ").append(boolValue(planBody.get("nativeExecutionRequired"), false)).append("\n\n");

        @SuppressWarnings("unchecked")
        Map<String, Object> profile = (Map<String, Object>) planBody.get("modelProfile");
        if (profile != null) {
            builder.append("Model profile\n");
            builder.append("  name: ").append(stringValue(profile.get("modelName"))).append('\n');
            builder.append("  architecture: ").append(stringValue(profile.get("architecture"))).append('\n');
            builder.append("  estimated layers: ").append(intValue(profile.get("layerCount"), 0)).append('\n');
            builder.append("  hidden size: ").append(intValue(profile.get("hiddenSize"), 0)).append('\n');
            builder.append("  context length: ").append(intValue(profile.get("contextLength"), 0)).append("\n\n");
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> assignments = (List<Map<String, Object>>) planBody.get("assignments");
        builder.append("Assignments\n");
        if (assignments == null || assignments.isEmpty()) {
            builder.append("  No peer assignments.\n");
        } else {
            for (Map<String, Object> assignment : assignments) {
                builder.append("  - ")
                    .append(stringValue(assignment.get("nodeName")))
                    .append(" • ")
                    .append(stringValue(assignment.get("role")))
                    .append(" • layers ")
                    .append(intValue(assignment.get("startLayerInclusive"), 0))
                    .append("..")
                    .append(intValue(assignment.get("endLayerExclusive"), 0))
                    .append(" • resident ")
                    .append(intValue(assignment.get("estimatedResidentMb"), 0))
                    .append(" MB\n");
            }
        }

        @SuppressWarnings("unchecked")
        List<String> warnings = (List<String>) planBody.get("warnings");
        if (warnings != null && !warnings.isEmpty()) {
            builder.append("\nWarnings\n");
            for (String warning : warnings) {
                builder.append("  - ").append(warning).append('\n');
            }
        }
        return builder.toString();
    }

    private void selectModelInList(String modelId) {
        for (int index = 0; index < modelListModel.size(); index++) {
            if (modelListModel.get(index).id().equals(modelId)) {
                modelList.setSelectedIndex(index);
                modelList.ensureIndexIsVisible(index);
                return;
            }
        }
        if (!modelListModel.isEmpty() && modelList.getSelectedIndex() < 0) {
            modelList.setSelectedIndex(0);
            selectedModelId = modelListModel.get(0).id();
        }
    }

    private void refillOptionCombo(JComboBox<IdLabelOption> combo, List<IdLabelOption> options, String selectedId) {
        IdLabelOption previous = (IdLabelOption) combo.getSelectedItem();
        DefaultComboBoxModel<IdLabelOption> model = new DefaultComboBoxModel<>();
        for (IdLabelOption option : options) {
            model.addElement(option);
        }
        combo.setModel(model);
        String target = selectedId == null || selectedId.isBlank()
            ? optionId(previous)
            : selectedId;
        for (int index = 0; index < model.getSize(); index++) {
            IdLabelOption option = model.getElementAt(index);
            if (Objects.equals(option.id(), target)) {
                combo.setSelectedIndex(index);
                return;
            }
        }
        combo.setSelectedIndex(0);
    }

    private JToggleButton navButton(String pageId, String title) {
        JToggleButton button = new JToggleButton(title);
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setFont(fonts.body().deriveFont(Font.BOLD, 14f));
        button.setMargin(new Insets(12, 14, 12, 14));
        button.setBorder(new LineBorder(palette.outline(), 1, true));
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        applyNavSelection(button, false);
        navigationButtons.put(pageId, button);
        return button;
    }

    private void applyNavSelection(JToggleButton button, boolean selected) {
        button.setSelected(selected);
        button.setBackground(selected ? palette.primaryContainer() : palette.surfaceVariant());
        button.setForeground(selected ? palette.text() : palette.textMuted());
    }

    private JLabel metricCard(JPanel parent, String title, String value) {
        RoundedPanel card = cardPanel(24);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(16, 16, 16, 16));
        card.add(label(title, fonts.body().deriveFont(Font.PLAIN, 12f), palette.textMuted()));
        card.add(Box.createVerticalStrut(6));
        JLabel valueLabel = label(value, fonts.display().deriveFont(Font.BOLD, 22f), palette.text());
        card.add(valueLabel);
        parent.add(card);
        return valueLabel;
    }

    private JButton quickActionButton(String title, String subtitle, Runnable onClick) {
        JButton button = actionButton("<html><b>" + title + "</b><br><span style='font-size:10px'>" + subtitle + "</span></html>", true);
        button.setHorizontalAlignment(JButton.LEFT);
        button.addActionListener(event -> onClick.run());
        button.setPreferredSize(new Dimension(220, 96));
        return button;
    }

    private RoundedPanel cardPanel(int radius) {
        RoundedPanel panel = new RoundedPanel(palette.surface(), palette.outline(), radius);
        panel.setOpaque(false);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        return panel;
    }

    private RoundedPanel subtleCard(Color fill, int radius) {
        RoundedPanel panel = new RoundedPanel(fill, palette.outline(), radius);
        panel.setOpaque(false);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        return panel;
    }

    private JPanel sectionCard(String title, String description, Component... bodyParts) {
        JPanel card = settingsCard(title, description);
        for (Component part : bodyParts) {
            card.add(part);
            card.add(Box.createVerticalStrut(8));
        }
        return card;
    }

    private JLabel parityNote(String text) {
        return label("• " + text, fonts.body().deriveFont(Font.PLAIN, 12f), palette.textMuted());
    }

    private JPanel settingsCard(String title, String description) {
        RoundedPanel panel = cardPanel(24);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(18, 18, 18, 18));
        panel.add(label(title, fonts.body().deriveFont(Font.BOLD, 15f), palette.text()));
        panel.add(Box.createVerticalStrut(6));
        panel.add(label(description, fonts.body().deriveFont(Font.PLAIN, 12f), palette.textMuted()));
        panel.add(Box.createVerticalStrut(12));
        return panel;
    }

    private void addToggleRow(JPanel parent, String title, String description, JCheckBox checkBox) {
        JPanel row = transparentPanel(new BorderLayout(12, 12));
        JPanel textColumn = transparentPanel();
        textColumn.setLayout(new BoxLayout(textColumn, BoxLayout.Y_AXIS));
        textColumn.add(label(title, fonts.body().deriveFont(Font.BOLD, 13f), palette.text()));
        textColumn.add(Box.createVerticalStrut(4));
        textColumn.add(label(description, fonts.body().deriveFont(Font.PLAIN, 12f), palette.textMuted()));
        row.add(textColumn, BorderLayout.CENTER);
        row.add(checkBox, BorderLayout.EAST);
        parent.add(row);
        parent.add(Box.createVerticalStrut(10));
    }

    private void addComboRow(JPanel parent, String title, String description, JComboBox<?> combo) {
        parent.add(labeledFieldRow(title, description, combo));
        parent.add(Box.createVerticalStrut(10));
    }

    private void addFieldRow(JPanel parent, String title, String description, JComponent field) {
        parent.add(labeledFieldRow(title, description, field));
        parent.add(Box.createVerticalStrut(10));
    }

    private JPanel labeledFieldRow(String title, String description, JComponent field) {
        return labeledFieldPanel(title, description, field);
    }

    private JPanel labeledFieldPanel(String title, String description, JComponent field) {
        RoundedPanel panel = subtleCard(palette.surfaceVariant(), 20);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(14, 14, 14, 14));
        panel.add(label(title, fonts.body().deriveFont(Font.BOLD, 13f), palette.text()));
        panel.add(Box.createVerticalStrut(4));
        panel.add(label(description, fonts.body().deriveFont(Font.PLAIN, 12f), palette.textMuted()));
        panel.add(Box.createVerticalStrut(10));
        panel.add(field);
        return panel;
    }

    private JPanel fieldWithButton(JComponent field, JButton button) {
        JPanel panel = transparentPanel(new BorderLayout(8, 0));
        panel.add(field, BorderLayout.CENTER);
        panel.add(button, BorderLayout.EAST);
        return panel;
    }

    private JPanel pageColumn() {
        JPanel page = transparentPanel();
        page.setLayout(new BoxLayout(page, BoxLayout.Y_AXIS));
        page.setBorder(new EmptyBorder(0, 0, 8, 0));
        return page;
    }

    private JScrollPane scrollWrap(JComponent component) {
        JScrollPane scrollPane = new JScrollPane(component);
        scrollPane.setBorder(new LineBorder(palette.outline(), 1, true));
        scrollPane.getViewport().setBackground(palette.surface());
        return scrollPane;
    }

    private JButton actionButton(String text, boolean secondary) {
        JButton button = new JButton(text);
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setFont(fonts.body().deriveFont(Font.BOLD, 13f));
        button.setMargin(new Insets(10, 14, 10, 14));
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setBorder(new LineBorder(secondary ? palette.outline() : palette.primary(), 1, true));
        button.setBackground(secondary ? palette.surfaceVariant() : palette.primary());
        button.setForeground(secondary ? palette.text() : palette.onPrimary());
        return button;
    }

    private JLabel label(String text, Font font, Color color) {
        JLabel label = new JLabel("<html>" + escapeHtml(text).replace("\n", "<br>") + "</html>");
        label.setFont(font);
        label.setForeground(color);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private JTextField textField() {
        JTextField field = new JTextField();
        styleTextField(field);
        return field;
    }

    private JPasswordField passwordField() {
        JPasswordField field = new JPasswordField();
        styleTextField(field);
        return field;
    }

    private void styleTextField(JTextField field) {
        field.setFont(fonts.body().deriveFont(Font.PLAIN, 13f));
        field.setBackground(palette.surface());
        field.setForeground(palette.text());
        field.setCaretColor(palette.primary());
        field.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(palette.outline(), 1, true),
            new EmptyBorder(10, 12, 10, 12)
        ));
    }

    @SafeVarargs
    private final <T> JComboBox<T> comboBox(T... items) {
        JComboBox<T> combo = new JComboBox<>();
        for (T item : items) {
            combo.addItem(item);
        }
        styleComboBox(combo);
        return combo;
    }

    private <T> JComboBox<T> comboBox() {
        JComboBox<T> combo = new JComboBox<>();
        styleComboBox(combo);
        return combo;
    }

    private Object localizedComboValue(Object value) {
        if (value instanceof WindowsHubDesktopSettings.ThemePreset preset) {
            return localizedThemeLabel(preset);
        }
        if (value instanceof WindowsHubDesktopSettings.AppLocale locale) {
            return localizedLocaleLabel(locale);
        }
        if (value instanceof WindowsHubDesktopSettings.PerformanceMode mode) {
            return localizedPerformanceLabel(mode);
        }
        if (value instanceof WindowsHubDesktopSettings.AccelerationMode mode) {
            return localizedAccelerationLabel(mode);
        }
        return value;
    }

    private void styleComboBox(JComboBox<?> combo) {
        combo.setFont(fonts.body().deriveFont(Font.PLAIN, 13f));
        combo.setBackground(palette.surface());
        combo.setForeground(palette.text());
        combo.setBorder(new LineBorder(palette.outline(), 1, true));
        combo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(
                JList<?> list,
                Object value,
                int index,
                boolean isSelected,
                boolean cellHasFocus
            ) {
                Component component = super.getListCellRendererComponent(
                    list,
                    localizedComboValue(value),
                    index,
                    isSelected,
                    cellHasFocus
                );
                component.setFont(fonts.body().deriveFont(Font.PLAIN, 13f));
                component.setBackground(isSelected ? palette.primaryContainer() : palette.surface());
                component.setForeground(palette.text());
                return component;
            }
        });
    }

    private JTextArea textArea(int rows, boolean mono) {
        JTextArea area = new JTextArea(rows, 32);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setEditable(true);
        area.setFont((mono ? fonts.mono() : fonts.body()).deriveFont(Font.PLAIN, mono ? 12f : 13f));
        area.setBackground(palette.surface());
        area.setForeground(palette.text());
        area.setCaretColor(palette.primary());
        area.setBorder(new EmptyBorder(12, 12, 12, 12));
        if (mono) {
            area.setEditable(false);
        }
        return area;
    }

    private JCheckBox toggle() {
        JCheckBox checkBox = new JCheckBox();
        checkBox.setOpaque(false);
        checkBox.setSelected(false);
        checkBox.setBorder(new EmptyBorder(0, 0, 0, 0));
        checkBox.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return checkBox;
    }

    private JPanel transparentPanel() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        return panel;
    }

    private JPanel transparentPanel(java.awt.LayoutManager layout) {
        JPanel panel = new JPanel(layout);
        panel.setOpaque(false);
        return panel;
    }

    private void appendLog(String message) {
        sessionLog.append('[').append(TIME_FORMAT.format(Instant.now())).append("] ").append(message).append('\n');
        if (logArea != null) {
            logArea.setText(sessionLog.toString());
            logArea.setCaretPosition(logArea.getDocument().getLength());
        }
    }

    private void showInfo(String title, String message) {
        JOptionPane.showMessageDialog(frame, message, title, JOptionPane.INFORMATION_MESSAGE);
    }

    private void showError(String title, String message) {
        JOptionPane.showMessageDialog(frame, message, title, JOptionPane.ERROR_MESSAGE);
    }

    private void setFooter(String message) {
        footerLabel.setText(message);
    }

    private String generateToken() {
        return "lan-" + java.util.UUID.randomUUID().toString().replace("-", "");
    }

    private void openUri(String value) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(URI.create(value));
            }
        } catch (Exception error) {
            showError("Cannot open browser", safeMessage(error));
        }
    }

    private String postForm(String endpoint, Map<String, String> form) throws IOException {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(endpoint).openConnection();
            connection.setConnectTimeout(2_000);
            connection.setReadTimeout(8_000);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            String payload = encodeForm(form);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(payload.getBytes(StandardCharsets.UTF_8));
            }
            InputStream stream = connection.getResponseCode() >= 400
                ? connection.getErrorStream()
                : connection.getInputStream();
            if (stream == null) {
                return "{\"ok\":false,\"message\":\"empty_response\"}";
            }
            try (InputStream input = stream) {
                return new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String encodeForm(Map<String, String> form) {
        List<String> pairs = new ArrayList<>();
        for (Map.Entry<String, String> entry : form.entrySet()) {
            pairs.add(urlEncode(entry.getKey()) + "=" + urlEncode(entry.getValue() == null ? "" : entry.getValue()));
        }
        return String.join("&", pairs);
    }

    private String urlEncode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String editableComboValue(JComboBox<NodeChoice> comboBox) {
        Object value = comboBox.getEditor().getItem();
        if (value instanceof NodeChoice choice) {
            return choice.host();
        }
        return value == null ? "" : value.toString();
    }

    private String safeMessage(Throwable error) {
        return error.getMessage() == null || error.getMessage().isBlank()
            ? error.getClass().getSimpleName()
            : error.getMessage();
    }

    private String stackTrace(Throwable error) {
        StringWriter buffer = new StringWriter();
        try (PrintWriter writer = new PrintWriter(buffer)) {
            error.printStackTrace(writer);
        }
        return buffer.toString();
    }

    private void writeDesktopErrorLog(String title, Throwable error) {
        try {
            Path file = WindowsHubApp.Arguments.defaultAppHome().resolve("desktop-startup-error.log");
            Files.createDirectories(file.getParent());
            Files.writeString(
                file,
                "[" + Instant.now() + "] " + title + System.lineSeparator()
                    + stackTrace(error) + System.lineSeparator(),
                StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.APPEND
            );
        } catch (Exception ignored) {
        }
    }

    private int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static int intValue(Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private static boolean boolValue(Object value, boolean fallback) {
        return value instanceof Boolean bool ? bool : fallback;
    }

    @SuppressWarnings("unchecked")
    private static int nestedInt(Map<String, Object> map, String key, String nestedKey) {
        Object nested = map.get(key);
        if (nested instanceof Map<?, ?> nestedMap) {
            Object value = ((Map<String, Object>) nestedMap).get(nestedKey);
            return intValue(value, 0);
        }
        return 0;
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static WindowsHubDesktopSettings copyOf(WindowsHubDesktopSettings source) {
        WindowsHubDesktopSettings copy = new WindowsHubDesktopSettings();
        copy.port = source.port;
        copy.androidNodePort = source.androidNodePort;
        copy.pairingToken = source.pairingToken;
        copy.coreBaseUrl = source.coreBaseUrl;
        copy.modelsDir = source.modelsDir;
        copy.peersFile = source.peersFile;

        copy.streamingEnabled = source.streamingEnabled;
        copy.chatMemoryEnabled = source.chatMemoryEnabled;
        copy.toolCallingEnabled = source.toolCallingEnabled;
        copy.toolCallingBypassEnabled = source.toolCallingBypassEnabled;
        copy.imageBlurEnabled = source.imageBlurEnabled;
        copy.loadTtsOnStart = source.loadTtsOnStart;
        copy.codeHighlightEnabled = source.codeHighlightEnabled;
        copy.aiMemoryEnabled = source.aiMemoryEnabled;
        copy.askModelReloadDialog = source.askModelReloadDialog;

        copy.hardwareTuningEnabled = source.hardwareTuningEnabled;
        copy.performanceMode = source.performanceMode;
        copy.accelerationMode = source.accelerationMode;
        copy.themePreset = source.themePreset;
        copy.appLocale = source.appLocale;

        copy.preferredChatModelId = source.preferredChatModelId;
        copy.preferredLiveModelId = source.preferredLiveModelId;
        copy.preferredImageModelId = source.preferredImageModelId;

        copy.externalAccessEnabled = source.externalAccessEnabled;
        copy.lanEnabled = source.lanEnabled;
        copy.advertiseLocalNode = source.advertiseLocalNode;
        copy.orchestraEnabled = source.orchestraEnabled;
        copy.orchestraAutoAssign = source.orchestraAutoAssign;
        copy.orchestraAllowLanSpillover = source.orchestraAllowLanSpillover;
        return copy;
    }

    private static String optionId(IdLabelOption option) {
        return option == null ? "" : option.id();
    }

    private static String selectedOptionId(JComboBox<IdLabelOption> comboBox, String fallback) {
        if (comboBox == null) {
            return fallback;
        }
        IdLabelOption option = (IdLabelOption) comboBox.getSelectedItem();
        String id = optionId(option);
        return id.isBlank() ? fallback : id;
    }

    private static String themeLabel(WindowsHubDesktopSettings.ThemePreset preset) {
        return switch (preset) {
            case SYSTEM -> "System";
            case MIDNIGHT_VIOLET -> "Midnight";
            case OBSIDIAN_MONO -> "Mono";
            case MARBLE_LILAC -> "Marble";
        };
    }

    private static String escapeHtml(String value) {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;");
    }

    private static String escapeJson(String value) {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"");
    }

    private record ModelListItem(String id, String name, int sizeMb, String architecture) {
        @Override
        public String toString() {
            return name;
        }
    }

    private record IdLabelOption(String id, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    private record NodeChoice(String host, int port, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    private record ModeOption(String id, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    private final class ModelCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(
            JList<?> list,
            Object value,
            int index,
            boolean isSelected,
            boolean cellHasFocus
        ) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof ModelListItem item) {
                label.setText("<html><b>" + escapeHtml(item.name()) + "</b><br><span style='font-size:10px'>"
                    + item.sizeMb() + " MB • " + escapeHtml(item.architecture()) + "</span></html>");
            }
            label.setFont(fonts.body().deriveFont(Font.PLAIN, 13f));
            label.setBorder(new EmptyBorder(10, 10, 10, 10));
            label.setBackground(isSelected ? palette.primaryContainer() : palette.surface());
            label.setForeground(palette.text());
            return label;
        }
    }

    private static final class RoundedPanel extends JPanel {
        private final Color fill;
        private final Color stroke;
        private final int arc;

        private RoundedPanel(Color fill, Color stroke, int arc) {
            this.fill = fill;
            this.stroke = stroke;
            this.arc = arc;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g2 = (Graphics2D) graphics.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
            g2.setColor(stroke);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
            g2.dispose();
            super.paintComponent(graphics);
        }
    }
}
