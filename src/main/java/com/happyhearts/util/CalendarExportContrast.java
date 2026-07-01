package com.happyhearts.util;

/**
 * WCAG-style relative luminance for export UIs: pick readable text on arbitrary hex backgrounds.
 */
public final class CalendarExportContrast {

    private CalendarExportContrast() {
    }

    /**
     * @return true if white (#FFFFFF) text is more readable than black on this RGB background.
     */
    public static boolean shouldUseWhiteText(byte[] rgb) {
        if (rgb == null || rgb.length < 3) {
            return false;
        }
        int r = rgb[0] & 0xFF;
        int g = rgb[1] & 0xFF;
        int b = rgb[2] & 0xFF;
        double l = relativeLuminance(srgbToLinear(r), srgbToLinear(g), srgbToLinear(b));
        // Strict: only very dark backgrounds get white text (fixes white-on-magenta / white-on-pastels).
        return l < 0.16;
    }

    private static double srgbToLinear(int channel) {
        double c = channel / 255.0;
        return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    private static double relativeLuminance(double r, double g, double b) {
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }
}
