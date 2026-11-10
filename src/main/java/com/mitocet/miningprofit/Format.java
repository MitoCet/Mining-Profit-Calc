package com.mitocet.miningprofit;

import java.util.Locale;

/** Turkish style numbers: 1,25M, 340,5k, 1sa 05dk 07sn. */
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

	/** Whole count with dots: 1.200. */
	public static String count(double v) {
		return String.format(Locale.ROOT, "%,d", Math.round(v)).replace(',', '.');
	}

	/** 1sa 05dk 07sn, 5dk 07sn, 42sn. */
	public static String time(long ms) {
		long sec = Math.max(0, ms) / 1000;
		long min = sec / 60;
		if (min >= 60) return String.format(Locale.ROOT, "%dsa %02ddk %02dsn", min / 60, min % 60, sec % 60);
		if (min > 0) return String.format(Locale.ROOT, "%ddk %02dsn", min, sec % 60);
		return sec + "sn";
	}
}
