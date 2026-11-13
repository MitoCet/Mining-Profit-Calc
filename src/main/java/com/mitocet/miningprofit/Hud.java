package com.mitocet.miningprofit;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The box: two tabs (Session / Toplam) and a [+]/[−] button for the big or small box.
 * Small: earned coins and coins per hour. Big: above those, coins per category (Gemstone,
 * Dwarven Metal…); clicking a category lists what it was earned from.
 * Shown while a session runs (when stopped only after /kazanc goster). While the chat is open (T), the box can be dragged, resized
 * from its bottom right corner, and a tab, the [+]/[−] button or a category clicked.
 */
public final class Hud {
	private static final int PAD = 4;
	private static final int LINE = 10;
	private static final int BG = 0x90000000;
	private static final int TAB_ON = 0xFFFFD24A;
	private static final int TAB_OFF = 0xFF8A8A8A;
	private static final int TEXT = 0xFFFFFFFF;
	private static final int GOOD = 0xFF6BE36B;
	private static final int MUTED = 0xFFAAAAAA;
	private static final int WARN = 0xFFFFAA33;
	private static final int BAD = 0xFFFF6B6B;
	private static final int HEAD = 0xFFFFD24A;
	/** The collector runs every 15 minutes; values older than this are called out. */
	private static final long STALE_MS = 60 * 60_000;
	private static final int HANDLE = 5;
	private static final int HANDLE_COLOR = 0xFFFFFFFF;
	private static final float MIN_SCALE = 0.5f;
	private static final float MAX_SCALE = 3f;

	private final MiningProfitClient mod;
	private int width = 120;
	private int height = 4 * LINE + 2 * PAD;
	private int sessionTabEnd;
	private int toggleX;
	/** Category of each line under the tab row (null when the line is not a heading). */
	private List<String> lineCats = new ArrayList<>();
	private boolean dragging;
	private boolean resizing;
	private boolean visible;
	/** Set by the chat screen's open and close events. */
	public boolean chatOpen;
	private double grabX;
	private double grabY;

	public Hud(MiningProfitClient mod) {
		this.mod = mod;
	}

	/** One text line of the box; cat is set on a category heading (a click opens or closes it). */
	private record Line(String text, int color, String cat) {}

	public void render(GuiGraphicsExtractor g, DeltaTracker delta) {
		Config cfg = mod.config;
		Tracker t = mod.tracker;
		// Shown while a session runs; when stopped only if asked for (/kazanc goster).
		if (!t.running() && !cfg.showStopped) {
			visible = false;
			return;
		}
		visible = true;
		Minecraft mc = Minecraft.getInstance();
		Font font = mc.font;
		long now = System.currentTimeMillis();

		boolean session = cfg.sessionTab;
		Breakdown raw = session ? t.sessionBreakdown : t.totalBreakdown;
		double coins = Tracker.net(session ? t.sessionCoins : t.totalCoins, raw, cfg.keyCost);
		// Keyed corpse loot only shows (under Corpse Loot) while key costs are on.
		Breakdown bd = raw.view(cfg.keyCost);
		long ms = session ? t.sessionMs(now) : t.totalMs(now);
		String tabA = "Session";
		String tabB = "Toplam";
		String toggle = cfg.big ? "[-]" : "[+]";

		List<Line> lines = new ArrayList<>();
		// Big box: categories with their coins, most first; an opened one lists what was earned.
		if (cfg.big) {
			List<String> cats = bd.categories();
			if (cats.isEmpty()) lines.add(new Line("Henüz kazanç yok", MUTED, null));
			for (String cat : cats) {
				boolean open = cfg.open.contains(cat);
				// With key costs on, Corpse Loot shows what is left after the keys.
				boolean keys = cfg.keyCost && cat.equals(Breakdown.CORPSE) && bd.keyText() != null;
				double total = bd.total(cat) - (keys ? bd.keyCoins : 0);
				lines.add(new Line((open ? "▼ " : "▶ ") + cat + " · " + Format.coins(total), HEAD, cat));
				if (open) {
					for (Map.Entry<String, Breakdown.Row> r : bd.rows(cat)) {
						lines.add(new Line("  " + Breakdown.rowText(r.getKey(), r.getValue()), TEXT, null));
					}
					if (keys) lines.add(new Line("  " + bd.keyText(), BAD, null));
				}
			}
		}
		lines.add(new Line("Kazanç: " + Format.coins(coins), GOOD, null));
		lines.add(new Line("Saatlik: " + Format.coins(Tracker.perHour(coins, ms)) + "/sa", TEXT, null));
		if (!mod.prices().loaded()) lines.add(new Line("Fiyatlar yükleniyor…", MUTED, null));
		else if (t.idle(now)) lines.add(new Line("⏸ Boşta, süre durdu · " + Format.time(ms), MUTED, null));
		else lines.add(new Line((t.running() ? "● " : "■ Durdu · ") + Format.time(ms), t.running() ? TEXT : MUTED, null));

		// Saved values from an earlier game, or values the collector hasn't renewed for a while.
		Prices p = mod.prices();
		long age = p.loaded() && p.timestamp > 0 ? now - p.timestamp * 1000 : 0;
		if (p.loaded() && (!mod.pricesFresh || age > STALE_MS)) {
			lines.add(new Line("⚠ Fiyatlar güncel değil" + (age > 0 ? " (" + Format.time(age) + " önce)" : ""), WARN, null));
		}

		// Something could not be read or looked wrong: the total may be off.
		if (!t.warnings.isEmpty()) {
			lines.add(new Line("⚠ Emin değilim: " + t.warnings.size() + " uyarı (/kazanc uyari)", WARN, null));
		}
		if (cfg.debugLog) lines.add(new Line("● Kayıt açık", MUTED, null));

		sessionTabEnd = font.width(tabA) + 6;
		int w = sessionTabEnd + font.width(tabB) + 8 + font.width(toggle);
		for (Line l : lines) w = Math.max(w, font.width(l.text));
		width = w + 2 * PAD;
		height = (lines.size() + 1) * LINE + 2 * PAD;
		toggleX = width - PAD - font.width(toggle);
		lineCats = new ArrayList<>();
		for (Line l : lines) lineCats.add(l.cat);
		clamp(mc);

		// Drawn at size 1 and scaled, so the whole box (text too) grows or shrinks.
		float scale = cfg.scale;
		g.pose().pushMatrix();
		g.pose().translate(cfg.x, cfg.y);
		g.pose().scale(scale, scale);
		g.fill(0, 0, width, height, BG);
		g.text(font, tabA, PAD, PAD, session ? TAB_ON : TAB_OFF, true);
		g.text(font, tabB, PAD + sessionTabEnd, PAD, session ? TAB_OFF : TAB_ON, true);
		g.text(font, toggle, toggleX, PAD, TAB_OFF, true);
		for (int i = 0; i < lines.size(); i++) g.text(font, lines.get(i).text, PAD, PAD + (i + 1) * LINE, lines.get(i).color, true);
		// Resize handle in the bottom right corner, only while the chat is open.
		if (chatOpen) {
			for (int i = 0; i < HANDLE; i++) g.fill(width - 1 - i, height - HANDLE + i, width, height - HANDLE + i + 1, HANDLE_COLOR);
		}
		g.pose().popMatrix();
	}

	private int scaledW() {
		return Math.round(width * mod.config.scale);
	}

	private int scaledH() {
		return Math.round(height * mod.config.scale);
	}

	private void clamp(Minecraft mc) {
		Config cfg = mod.config;
		if (!(cfg.scale >= MIN_SCALE && cfg.scale <= MAX_SCALE)) cfg.scale = 1f;
		int sw = mc.getWindow().getGuiScaledWidth();
		int sh = mc.getWindow().getGuiScaledHeight();
		cfg.x = Math.max(0, Math.min(cfg.x, sw - scaledW()));
		cfg.y = Math.max(0, Math.min(cfg.y, sh - scaledH()));
	}

	private boolean inside(double mx, double my) {
		Config cfg = mod.config;
		return visible && mx >= cfg.x && mx < cfg.x + scaledW() && my >= cfg.y && my < cfg.y + scaledH();
	}

	/** Mouse down in the chat screen. Returns true when the box took the click. */
	public boolean click(double mx, double my, int button) {
		if (button != 0 || !inside(mx, my)) return false;
		Config cfg = mod.config;
		double lx = (mx - cfg.x) / cfg.scale;
		double ly = (my - cfg.y) / cfg.scale;
		// The bottom right corner resizes.
		if (lx >= width - HANDLE - 2 && ly >= height - HANDLE - 2) {
			resizing = true;
			return true;
		}
		// [+]/[−] switches between the big and the small box.
		if (ly < PAD + LINE && lx >= toggleX - 2) {
			cfg.big = !cfg.big;
			mod.save();
			return true;
		}
		// A category heading opens or closes.
		int idx = (int) Math.floor((ly - PAD) / LINE) - 1;
		if (idx >= 0 && idx < lineCats.size() && lineCats.get(idx) != null) {
			String cat = lineCats.get(idx);
			if (!cfg.open.remove(cat)) cfg.open.add(cat);
			mod.save();
			return true;
		}
		// A click on a tab label switches tabs; anywhere else starts a drag.
		if (ly < PAD + LINE) {
			boolean onSession = lx < PAD + sessionTabEnd;
			if (onSession != cfg.sessionTab) {
				cfg.sessionTab = onSession;
				mod.save();
			}
		}
		dragging = true;
		grabX = mx - cfg.x;
		grabY = my - cfg.y;
		return true;
	}

	/** Called every frame while the chat screen is open. */
	public void drag(double mx, double my) {
		Config cfg = mod.config;
		if (resizing) {
			float s = (float) Math.max((mx - cfg.x) / width, (my - cfg.y) / height);
			cfg.scale = Math.round(Math.max(MIN_SCALE, Math.min(MAX_SCALE, s)) * 20) / 20f;
		} else if (dragging) {
			cfg.x = (int) Math.round(mx - grabX);
			cfg.y = (int) Math.round(my - grabY);
		}
	}

	public boolean release() {
		if (!dragging && !resizing) return false;
		dragging = false;
		resizing = false;
		mod.save();
		return true;
	}
}
