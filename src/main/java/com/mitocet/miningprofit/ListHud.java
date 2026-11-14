package com.mitocet.miningprofit;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

import java.util.ArrayList;
import java.util.List;

/**
 * The forge plan's shopping list, on the left of the screen (/kazanc forge). While the chat is open (T),
 * a craft row opens that item's recipe (/viewrecipe, Supercraft from there), a buy row opens the
 * Bazaar search (/bz name) and copies the amount still to buy, [x] hides the box. One command per click,
 * nothing automatic (like SkyHanni's visitor shopping list, BazaarApi.searchForBazaarItem; LGPL-2.1).
 * While an inventory or menu is open the list sits flush on its left and its rows can be clicked there too.
 */
public final class ListHud {
	private static final int PAD = 4;
	private static final int LINE = 10;
	private static final int BG = 0x90000000;
	private static final int HOVER = 0x40FFFFFF;
	private static final int X = 4;
	/** Gap between the list and an open menu's left edge, and the minimum margin to the screen edge. */
	private static final int GAP = 2;
	/** Smaller than this would be unreadable: the list is hidden while that menu is open. */
	static final float MIN_SCALE = 0.5f;

	private final MiningProfitClient mod;
	private List<ShoppingList.Line> lines = new ArrayList<>();
	private int width;
	private int height;
	private int x = X;
	private int y;
	private float scale = 1f;
	private boolean visible;
	private double mouseX = -1;
	private double mouseY = -1;
	/** Set by the chat screen's open and close events. */
	public boolean chatOpen;
	/** The open inventory/menu (chest, forge, sack…), or null. The list is drawn flush on its left. */
	public AbstractContainerScreen<?> container;

	public ListHud(MiningProfitClient mod) {
		this.mod = mod;
	}

	/** HUD layer: left side of the screen, a third of the way down. Skipped while a menu is open. */
	public void render(GuiGraphicsExtractor g, DeltaTracker delta) {
		if (container != null) return;
		ShoppingList list = mod.shoppingList;
		visible = list != null && mod.config.listVisible;
		if (!visible) return;
		Minecraft mc = Minecraft.getInstance();
		Font font = mc.font;
		lines = list.lines();
		scale = 1f;
		x = X;
		ShoppingList.Line hover = chatOpen ? lineAt(mouseX, mouseY) : null;
		String hint = hintFor(hover);
		measure(font, hint);
		int sh = mc.getWindow().getGuiScaledHeight();
		// The earnings box sits top left by default.
		y = Math.max(0, Math.min(sh / 3, sh - height));
		draw(g, font, hover, hint);
	}

	/**
	 * Drawn on top of an open menu, flush against its left edge and level with its top; shrunk when it does not
	 * fit beside it, hidden when it would get too small (like SkyHanni's overlays next to the inventory, which
	 * read the menu's leftPos/topPos the same way).
	 */
	public void renderInScreen(GuiGraphicsExtractor g, AbstractContainerScreen<?> screen, double mx, double my) {
		ShoppingList list = mod.shoppingList;
		visible = list != null && mod.config.listVisible;
		mouseX = mx;
		mouseY = my;
		if (!visible) return;
		Font font = Minecraft.getInstance().font;
		lines = list.lines();
		measure(font, null);
		int right = screen.leftPos - GAP;
		float s = Layout.fit(width, height, right - GAP, screen.height - 2 * GAP);
		if (s < MIN_SCALE) {
			visible = false;
			return;
		}
		ShoppingList.Line hover = lineAt(mx, my, right, s, screen);
		String hint = hintFor(hover);
		// The hint widens the box to the left only while it still fits; the edge on the menu stays put.
		if (hint != null) {
			int baseW = width;
			measure(font, hint);
			if (width * s > right - GAP) {
				hint = null;
				measure(font, null);
			}
			if (width != baseW) hover = lineAt(mx, my, right, s, screen);
		}
		scale = s;
		x = Math.round(right - width * s);
		y = Layout.top(screen.topPos, Math.round(height * s), screen.height, GAP);
		draw(g, font, hover, hint);
	}

	/** Hover for the in-menu layout, before x/y are known for this frame. */
	private ShoppingList.Line lineAt(double mx, double my, int right, float s, AbstractContainerScreen<?> screen) {
		scale = s;
		x = Math.round(right - width * s);
		y = Layout.top(screen.topPos, Math.round(height * s), screen.height, GAP);
		return lineAt(mx, my);
	}

	private void measure(Font font, String hint) {
		int w = 0;
		for (ShoppingList.Line l : lines) w = Math.max(w, font.width(l.text()) + (l.right().isEmpty() ? 0 : 12 + font.width(l.right())));
		if (hint != null) w = Math.max(w, font.width(hint));
		width = w + 2 * PAD;
		height = (lines.size() + (hint != null ? 1 : 0)) * LINE + 2 * PAD;
	}

	private void draw(GuiGraphicsExtractor g, Font font, ShoppingList.Line hover, String hint) {
		// Drawn at size 1 and scaled, so the whole list (text too) shrinks.
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(scale, scale);
		g.fill(0, 0, width, height, BG);
		for (int i = 0; i < lines.size(); i++) {
			ShoppingList.Line l = lines.get(i);
			int ly = PAD + i * LINE;
			if (l == hover && l.row() != null) g.fill(0, ly - 1, width, ly + LINE - 1, HOVER);
			g.text(font, l.text(), PAD, ly, l.row() != null ? ShoppingList.TEXT : l.color(), true);
			if (!l.right().isEmpty()) g.text(font, l.right(), width - PAD - font.width(l.right()), ly, l.color(), true);
		}
		if (hint != null) g.text(font, hint, PAD, PAD + lines.size() * LINE, ShoppingList.TODO, true);
		g.pose().popMatrix();
	}

	private String hintFor(ShoppingList.Line hover) {
		return hover == null || hover.row() == null ? null : hint(hover.row());
	}

	private String hint(ShoppingList.Row r) {
		if (r.action() == 'c') return "Tıkla: /viewrecipe " + r.id();
		return "Tıkla: /bz " + r.name() + " · " + Format.count(mod.shoppingList.left(r)) + " panoya";
	}

	private ShoppingList.Line lineAt(double mx, double my) {
		if (!visible) return null;
		double lx = (mx - x) / scale;
		double ly = (my - y) / scale;
		if (lx < 0 || lx >= width || ly < PAD) return null;
		int idx = (int) Math.floor((ly - PAD) / LINE);
		return idx >= 0 && idx < lines.size() ? lines.get(idx) : null;
	}

	/** Called every frame while the chat screen is open. */
	public void hover(double mx, double my) {
		mouseX = mx;
		mouseY = my;
	}

	/** Mouse down in the chat screen or an open menu. Returns true when the list took the click. */
	public boolean click(double mx, double my, int button) {
		if (button != 0) return false;
		ShoppingList.Line l = lineAt(mx, my);
		if (l == null) return false;
		if (lines.indexOf(l) == 0 && (mx - x) / scale >= width - PAD - 20) {
			mod.runCommand("forge");
			return true;
		}
		if (l.row() == null) return true;
		mod.listClick(l.row());
		return true;
	}

	/** Size and place next to a menu; pure so it can be tested without the game. */
	static final class Layout {
		private Layout() {}

		/** Scale (at most 1) so a w×h list fits in availW×availH; 0 when there is no room at all. */
		static float fit(int w, int h, int availW, int availH) {
			if (availW <= 0 || availH <= 0 || w <= 0 || h <= 0) return 0f;
			return Math.min(1f, Math.min((float) availW / w, (float) availH / h));
		}

		/** Level with the menu's top, moved up only as far as needed to stay on screen. */
		static int top(int menuTop, int scaledH, int screenH, int margin) {
			return Math.max(margin, Math.min(menuTop, screenH - margin - scaledH));
		}
	}
}
