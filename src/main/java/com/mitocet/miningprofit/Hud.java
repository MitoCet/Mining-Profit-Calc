package com.mitocet.miningprofit;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The small box: two tabs (Session / Toplam), earned coins and coins per hour.
 * While the chat is open (T), the box can be dragged with the mouse and a tab clicked.
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
	/** The collector runs every 15 minutes; values older than this are called out. */
	private static final long STALE_MS = 60 * 60_000;

	private final MiningProfitClient mod;
	private int width = 120;
	private int height = 4 * LINE + 2 * PAD;
	private int sessionTabEnd;
	private boolean dragging;
	private double grabX;
	private double grabY;

	public Hud(MiningProfitClient mod) {
		this.mod = mod;
	}

	public void render(GuiGraphicsExtractor g, DeltaTracker delta) {
		Config cfg = mod.config;
		if (cfg.hidden) return;
		Minecraft mc = Minecraft.getInstance();
		Font font = mc.font;
		Tracker t = mod.tracker;
		long now = System.currentTimeMillis();

		boolean session = cfg.sessionTab;
		double coins = session ? t.sessionCoins : t.totalCoins;
		long ms = session ? t.sessionMs(now) : t.totalMs(now);
		String tabA = "Session";
		String tabB = "Toplam";
		String l1 = "Kazanç: " + Format.coins(coins);
		String l2 = "Saatlik: " + Format.coins(Tracker.perHour(coins, ms)) + "/sa";
		String l3 = !mod.prices().loaded() ? "Fiyatlar yükleniyor…"
			: (t.running() ? "● " : "■ Durdu · ") + Format.time(ms);

		// Saved values from an earlier game, or values the collector hasn't renewed for a while.
		Prices p = mod.prices();
		long age = p.loaded() && p.timestamp > 0 ? now - p.timestamp * 1000 : 0;
		String l4 = p.loaded() && (!mod.pricesFresh || age > STALE_MS)
			? "⚠ Fiyatlar güncel değil" + (age > 0 ? " (" + Format.time(age) + " önce)" : "") : null;

		sessionTabEnd = font.width(tabA) + 6;
		int w = Math.max(sessionTabEnd + font.width(tabB), Math.max(font.width(l1), Math.max(font.width(l2), font.width(l3))));
		if (l4 != null) w = Math.max(w, font.width(l4));
		height = (l4 == null ? 4 : 5) * LINE + 2 * PAD;
		width = w + 2 * PAD;
		clamp(mc);
		int x = cfg.x;
		int y = cfg.y;
		g.fill(x, y, x + width, y + height, BG);
		g.text(font, tabA, x + PAD, y + PAD, session ? TAB_ON : TAB_OFF, true);
		g.text(font, tabB, x + PAD + sessionTabEnd, y + PAD, session ? TAB_OFF : TAB_ON, true);
		g.text(font, l1, x + PAD, y + PAD + LINE, GOOD, true);
		g.text(font, l2, x + PAD, y + PAD + 2 * LINE, TEXT, true);
		g.text(font, l3, x + PAD, y + PAD + 3 * LINE, t.running() ? TEXT : MUTED, true);
		if (l4 != null) g.text(font, l4, x + PAD, y + PAD + 4 * LINE, WARN, true);
	}

	private void clamp(Minecraft mc) {
		Config cfg = mod.config;
		int sw = mc.getWindow().getGuiScaledWidth();
		int sh = mc.getWindow().getGuiScaledHeight();
		cfg.x = Math.max(0, Math.min(cfg.x, sw - width));
		cfg.y = Math.max(0, Math.min(cfg.y, sh - height));
	}

	private boolean inside(double mx, double my) {
		Config cfg = mod.config;
		return !cfg.hidden && mx >= cfg.x && mx < cfg.x + width && my >= cfg.y && my < cfg.y + height;
	}

	/** Mouse down in the chat screen. Returns true when the box took the click. */
	public boolean click(double mx, double my, int button) {
		if (button != 0 || !inside(mx, my)) return false;
		Config cfg = mod.config;
		// A click on a tab label switches tabs; anywhere else starts a drag.
		if (my < cfg.y + PAD + LINE) {
			boolean onSession = mx < cfg.x + PAD + sessionTabEnd;
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
		if (!dragging) return;
		mod.config.x = (int) Math.round(mx - grabX);
		mod.config.y = (int) Math.round(my - grabY);
	}

	public boolean release() {
		if (!dragging) return false;
		dragging = false;
		mod.save();
		return true;
	}
}
