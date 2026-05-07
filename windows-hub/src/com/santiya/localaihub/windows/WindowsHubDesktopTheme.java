package com.santiya.localaihub.windows;

import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

final class WindowsHubDesktopTheme {
    record Palette(
        Color background,
        Color surface,
        Color surfaceVariant,
        Color primary,
        Color onPrimary,
        Color secondary,
        Color tertiary,
        Color primaryContainer,
        Color secondaryContainer,
        Color tertiaryContainer,
        Color text,
        Color textMuted,
        Color outline,
        Color success,
        Color warning,
        Color error
    ) {}

    record FontSet(Font display, Font body, Font mono) {}

    private WindowsHubDesktopTheme() {
    }

    static Palette resolve(WindowsHubDesktopSettings.ThemePreset preset) {
        return switch (preset) {
            case MIDNIGHT_VIOLET -> new Palette(
                color(0x09070F),
                color(0x110E1B),
                color(0x1B1729),
                color(0xC4B0FF),
                color(0x24104C),
                color(0xD7C9FF),
                color(0xFFA0C5),
                color(0x352265),
                color(0x312547),
                color(0x53253E),
                color(0xF5F0FF),
                color(0xC2B9DD),
                color(0x736C90),
                color(0x5FD2BA),
                color(0xFFBE72),
                color(0xFF8DA5)
            );
            case OBSIDIAN_MONO -> new Palette(
                color(0x050505),
                color(0x101010),
                color(0x1A1A1A),
                color(0xF6F4FF),
                color(0x111111),
                color(0xD6D6DE),
                color(0xBEBECE),
                color(0x2B2B2B),
                color(0x242424),
                color(0x202020),
                color(0xF5F5F7),
                color(0xB5B5BC),
                color(0x7C7C7C),
                color(0x99E2D0),
                color(0xFFD39E),
                color(0xFF8C8C)
            );
            case MARBLE_LILAC -> new Palette(
                color(0xF6F2F8),
                color(0xFFFCFF),
                color(0xF0EAF4),
                color(0x6A4BE8),
                Color.WHITE,
                color(0x8565D8),
                color(0xAC6A97),
                color(0xE9DEFF),
                color(0xF0E8FF),
                color(0xFADAE7),
                color(0x211A2F),
                color(0x6E6481),
                color(0x82788F),
                color(0x0E8B73),
                color(0xA16609),
                color(0xB3261E)
            );
            case SYSTEM -> systemPalette();
        };
    }

    static FontSet loadFonts(Path distributionRoot) {
        Font display = loadFont(distributionRoot.resolve("assets").resolve("fonts").resolve("manrope.ttf"), 18f);
        Font body = loadFont(distributionRoot.resolve("assets").resolve("fonts").resolve("manrope.ttf"), 14f);
        Font mono = loadFont(distributionRoot.resolve("assets").resolve("fonts").resolve("maple_mono.ttf"), 13f);
        return new FontSet(
            display != null ? display : new Font("Segoe UI", Font.BOLD, 18),
            body != null ? body : new Font("Segoe UI", Font.PLAIN, 14),
            mono != null ? mono : new Font(Font.MONOSPACED, Font.PLAIN, 13)
        );
    }

    private static Palette systemPalette() {
        boolean dark = false;
        Color systemPanel = javax.swing.UIManager.getColor("Panel.background");
        if (systemPanel != null) {
            dark = (systemPanel.getRed() + systemPanel.getGreen() + systemPanel.getBlue()) / 3 < 128;
        }
        if (dark) {
            return new Palette(
                color(0x0B0B12),
                color(0x11121B),
                color(0x1B1A26),
                color(0xB7A8FF),
                color(0x20114C),
                color(0xC9B6FF),
                color(0xF496B7),
                color(0x2E2361),
                color(0x302545),
                color(0x4A2235),
                color(0xF7F2FF),
                color(0xBFB8D2),
                color(0x786D97),
                color(0x5FD2BA),
                color(0xFFBE72),
                color(0xFF8DA5)
            );
        }
        return new Palette(
            color(0xF8F4FC),
            color(0xFFFBFF),
            color(0xEEE7F6),
            color(0x6D48D8),
            Color.WHITE,
            color(0x8B6FD7),
            color(0xB85F87),
            color(0xE8DEFF),
            color(0xEEE6FF),
            color(0xFFD7E6),
            color(0x211A2F),
            color(0x6E6481),
            color(0x82788F),
            color(0x0E8B73),
            color(0xA16609),
            color(0xB3261E)
        );
    }

    private static Font loadFont(Path file, float size) {
        try {
            if (!Files.exists(file)) {
                return null;
            }
            try (InputStream input = Files.newInputStream(file)) {
                Font font = Font.createFont(Font.TRUETYPE_FONT, input).deriveFont(size);
                GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font);
                return font;
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Color color(int hex) {
        return new Color(hex);
    }
}
