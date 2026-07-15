package com.psdk.util;

import java.util.Locale;

/**
 * Formatação de números da economia. Abrevia valores grandes:
 * 1000 → 1k, 1500 → 1.5k, 1_000_000 → 1M, 2_500_000 → 2.5M, etc.
 */
public final class NumberUtil {

    private NumberUtil() {}

    private static final String[] SUF = {"k", "M", "B", "T"};

    /** Abrevia o valor (abaixo de 1000 mostra o número inteiro). */
    public static String abbrev(double value) {
        if (Double.isNaN(value)) return "0";
        if (value < 0) return "-" + abbrev(-value);
        if (value < 1000) return String.valueOf((long) value);

        double v = value;
        int i = -1;
        while (v >= 1000 && i < SUF.length - 1) { v /= 1000.0; i++; }

        String num = String.format(Locale.US, "%.1f", v);
        if (num.endsWith(".0")) num = num.substring(0, num.length() - 2);   // 1.0k -> 1k
        return num + SUF[i];
    }
}
