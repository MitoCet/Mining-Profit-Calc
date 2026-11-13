package com.mitocet.miningprofit;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * /kazanc gecmis: every saved session on the left (newest first, the running one on top), the
 * chosen one's details on the right (what it earned from, the API sack check), and buttons to
 * add what the check found missing (Düzelt), check again, delete it or delete them all.
 */
public final class HistoryScreen extends Screen {
	private static final int ROW = 22;
	private static final int LINE = 11;
	private static final int PANEL = 0xC0000000;
	private static final int BORDER = 0xFF555555;
	private static final int SELECTED = 0xFF3D5A8A;

	private final MiningProfitClient mod;
	/** Chosen session: its start, or LIVE for the session running now. */
	private long chosen = Long.MIN_VALUE;
	private static final long LIVE = -1;
	private double listScroll;
	private double detailScroll;
	private Button fixButton;
	private Button checkButton;
	private Button deleteButton;
	private Button autoFixButton;

	// Layout, set in init().
	private int listX;
	private int listW;
	private int detailX;
	private int detailW;
	private int top;
	private int bottom;
	/** The list ends higher: the Oto düzelt button sits under it. */
	private int listBottom;

	public HistoryScreen(MiningProfitClient mod) {
		super(Component.literal("Kazanç geçmişi"));
		this.mod = mod;
	}

	/** Opens with the "delete all" question first (/kazanc sifirla). */
	public static Screen confirmAll(MiningProfitClient mod) {
		HistoryScreen s = new HistoryScreen(mod);
		return s.confirmAllScreen();
	}

	@Override
	protected void init() {
		listX = 10;
		listW = Math.max(150, Math.min(240, width * 38 / 100));
		detailX = listX + listW + 8;
		detailW = width - detailX - 10;
		top = 32;
		bottom = height - 32;
		listBottom = height - 56;
		int y = height - 26;
		autoFixButton = addRenderableWidget(Button.builder(autoFixText(), (b) -> {
			mod.menuToggleAutoFix();
			b.setMessage(autoFixText());
		}).pos(listX, height - 50).size(listW, 20).build());
		addRenderableWidget(Button.builder(Component.literal("§cHepsini sil (sıfırla)"),
			(b) -> Minecraft.getInstance().gui.setScreen(confirmAllScreen())).pos(listX, y).size(listW, 20).build());
		int gap = 4;
		int bw = (detailW - 3 * gap) / 4;
		fixButton = addRenderableWidget(Button.builder(Component.literal("Düzelt"), (b) -> {
			mod.menuFix(chosenSession());
		}).pos(detailX, y).size(bw, 20).build());
		checkButton = addRenderableWidget(Button.builder(Component.literal("Kontrol et"), (b) -> {
			mod.menuCheck();
		}).pos(detailX + (bw + gap), y).size(bw, 20).build());
		deleteButton = addRenderableWidget(Button.builder(Component.literal("§cSil"), (b) -> {
			History.Session s = chosenSession();
			if (s != null) Minecraft.getInstance().gui.setScreen(confirmDelete(s));
		}).pos(detailX + 2 * (bw + gap), y).size(bw, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Kapat"), (b) -> Minecraft.getInstance().gui.setScreen(null))
			.pos(detailX + 3 * (bw + gap), y).size(detailW - 3 * (bw + gap), 20).build());
	}

	private Component autoFixText() {
		return Component.literal("Oto düzelt: " + (mod.config.autoFix ? "§aAçık" : "Kapalı"));
	}

	private Screen confirmAllScreen() {
		long now = System.currentTimeMillis();
		int n = mod.history().sessions.size();
		double total = Tracker.net(mod.tracker.totalCoins, mod.tracker.totalBreakdown, mod.config.keyCost);
		return new ConfirmScreen((yes) -> {
			if (yes) mod.menuDeleteAll();
			Minecraft.getInstance().gui.setScreen(this);
		}, Component.literal("Bütün sessionlar silinsin mi?"),
			Component.literal(n + " session ve Toplam (" + Format.count(total) + ", " + Format.time(mod.tracker.totalMs(now))
				+ ") silinir" + (mod.hasLiveSession(now) ? "; şu anki session kalır" : "") + ". Geri alınamaz."));
	}

	private Screen confirmDelete(History.Session s) {
		double coins = Tracker.net(s.coins, s.breakdown, mod.config.keyCost);
		double total = Tracker.net(mod.tracker.totalCoins, mod.tracker.totalBreakdown, mod.config.keyCost);
		return new ConfirmScreen((yes) -> {
			if (yes) {
				mod.menuDelete(s);
				chosen = Long.MIN_VALUE;
			}
			Minecraft.getInstance().gui.setScreen(this);
		}, Component.literal("Bu session silinsin mi?"),
			Component.literal(History.date(s.start) + " · " + Format.time(s.ms) + " · " + Format.count(coins) + " coin. Toplam "
				+ Format.count(total) + " → " + Format.count(Math.max(0, total - coins)) + " olur. Geri alınamaz."));
	}

	/** The rows of the list: null = the session running now. */
	private List<History.Session> entries(long now) {
		List<History.Session> out = new ArrayList<>();
		if (mod.hasLiveSession(now)) out.add(null);
		out.addAll(mod.history().newestFirst());
		return out;
	}

	/** The chosen saved session (null = the live one or none). */
	private History.Session chosenSession() {
		return chosen == LIVE || chosen == Long.MIN_VALUE ? null : mod.history().find(chosen);
	}

	private static long id(History.Session s) {
		return s == null ? LIVE : s.start;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		long now = System.currentTimeMillis();
		List<History.Session> rows = entries(now);
		// Nothing chosen yet (or it was deleted): the newest.
		boolean found = false;
		for (History.Session s : rows) if (id(s) == chosen) found = true;
		if (!found) chosen = rows.isEmpty() ? Long.MIN_VALUE : id(rows.get(0));
		boolean live = chosen == LIVE;
		History.Session sel = live ? mod.liveRecord(now) : chosenSession();

		double missing = sel != null && sel.check != null ? sel.check.missingCoins() : 0;
		fixButton.active = missing > 0;
		fixButton.setMessage(Component.literal(missing > 0 ? "Düzelt: +" + Format.coins(missing) : "Düzelt"));
		checkButton.active = mod.canCheck(live ? null : sel);
		deleteButton.active = !live && sel != null;

		super.extractRenderState(g, mouseX, mouseY, a);

		double total = Tracker.net(mod.tracker.totalCoins, mod.tracker.totalBreakdown, mod.config.keyCost);
		g.centeredText(font, title, width / 2, 8, History.HEAD);
		g.centeredText(font, Component.literal("Toplam: " + Format.count(total) + " coin · " + Format.time(mod.tracker.totalMs(now))
			+ " · " + mod.history().sessions.size() + " session"), width / 2, 19, History.MUTED);

		// List.
		box(g, listX, top, listW, listBottom - top);
		int visible = (listBottom - top - 4) / ROW;
		listScroll = Math.max(0, Math.min(listScroll, Math.max(0, rows.size() - visible)));
		int first = (int) listScroll;
		if (rows.isEmpty()) g.text(font, "Kayıtlı session yok.", listX + 6, top + 6, History.MUTED, true);
		for (int i = first; i < rows.size() && i < first + visible; i++) {
			History.Session s = rows.get(i);
			int y = top + 2 + (i - first) * ROW;
			if (id(s) == chosen) g.fill(listX + 2, y, listX + listW - 2, y + ROW - 1, SELECTED);
			History.Session r = s == null ? mod.liveRecord(now) : s;
			double coins = Tracker.net(r.coins, r.breakdown, mod.config.keyCost);
			String name = s == null ? (mod.tracker.running() ? "Şu anki session" : "Şu anki (durdu)") : History.date(s.start);
			String value = Format.coins(coins);
			g.text(font, name, listX + 6, y + 2, s == null ? History.HEAD : History.TEXT, true);
			g.text(font, value, listX + listW - 6 - font.width(value), y + 2, History.GOOD, true);
			String info = Format.time(r.ms) + " · " + Format.coins(Tracker.perHour(coins, r.ms)) + "/sa";
			g.text(font, fit(info, listW - 12), listX + 6, y + 12, History.MUTED, true);
			String st = History.status(r.check);
			int stX = listX + 6 + font.width(info + "  ");
			if (stX + font.width(st) <= listX + listW - 6) g.text(font, st, stX, y + 12, History.statusColor(r.check), true);
		}

		// Details.
		box(g, detailX, top, detailW, bottom - top);
		if (sel == null) return;
		String head = live ? "Şu anki session · " + Format.time(sel.ms)
			: History.date(sel.start) + " – " + History.clock(sel.end) + " · " + Format.time(sel.ms);
		if (sel.check != null) sel.check.fillFamilies(mod.prices());
		List<History.Line> lines = History.lines(sel, head, mod.config.keyCost);
		int fits = (bottom - top - 8) / LINE;
		detailScroll = Math.max(0, Math.min(detailScroll, Math.max(0, lines.size() - fits)));
		int start = (int) detailScroll;
		int x = detailX + 6;
		int right = detailX + detailW - 6;
		for (int i = start; i < lines.size() && i < start + fits; i++) {
			History.Line l = lines.get(i);
			int y = top + 5 + (i - start) * LINE;
			int rw = l.right().isEmpty() ? 0 : font.width(l.right());
			g.text(font, fit(l.text(), right - x - rw - 6), x, y, l.color(), true);
			if (rw > 0) g.text(font, l.right(), right - rw, y, l.rightColor(), true);
		}
		if (lines.size() > fits) g.text(font, "↕", right - font.width("↕"), bottom - 11, History.MUTED, true);
	}

	private static void box(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		g.fill(x - 1, y - 1, x + w + 1, y + h + 1, BORDER);
		g.fill(x, y, x + w, y + h, PANEL);
	}

	/** Cuts text to a width (with "…"). */
	private String fit(String s, int w) {
		if (w <= 0) return "";
		if (font.width(s) <= w) return s;
		String dots = "…";
		int n = s.length();
		while (n > 0 && font.width(s.substring(0, n) + dots) > w) n--;
		return s.substring(0, n) + dots;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
		if (super.mouseClicked(e, doubleClick)) return true;
		double mx = e.x();
		double my = e.y();
		if (mx >= listX && mx < listX + listW && my >= top + 2 && my < listBottom) {
			int i = (int) listScroll + (int) ((my - top - 2) / ROW);
			List<History.Session> rows = entries(System.currentTimeMillis());
			if (i >= 0 && i < rows.size()) {
				chosen = id(rows.get(i));
				detailScroll = 0;
			}
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
		if (mx < detailX) listScroll -= scrollY;
		else detailScroll -= scrollY * 3;
		return true;
	}
}
