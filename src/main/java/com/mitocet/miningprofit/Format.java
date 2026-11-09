package com.mitocet.miningprofit;

import java.util.Locale;

/** Turkish style numbers: 1,25M, 340,5k, 1sa 05dk. */
public final class Format {
	private Format() {}

	public static String coins(double v) {
		double a = Math.abs(v);
		String s;
		if (a >= 1e9) s = String.format(Locale.ROOT, "%.2fB", v / 1e9);
		else if (a >= 1e6) s = String.format(Locale.ROOT, "%.2fM", v / 1e6);
		else if (a >= 1e3) s = String.format(Locale.ROOT, "%.1fk", v / 1e3);
		else s = String.format(Locale.ROOT, "%.0f", v);
		return s.replace('.', ',');
	}

	public static String time(long ms) {
		long min = ms / 60_000;
		return min >= 60 ? String.format(Locale.ROOT, "%dsa %02ddk", min / 60, min % 60) : min + "dk";
	}
}
