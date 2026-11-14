package com.mitocet.miningprofit;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

/**
 * The forge plan's shopping list, on the left of the screen (/kazanc forge). While the chat is open (T),
 * a craft row opens that item's recipe (/viewrecipe, Supercraft from there), a buy row opens the
 * Bazaar search (/bz name) and copies the amount still to buy, [x] hides the box. One command per click,
 * nothing automatic (like SkyHanni's visitor shopping list, BazaarApi.searchForBazaarItem; LGPL-2.1).
 */
public final class ListHud {
	private static final int PAD = 4;
	private static final int LINE = 10;
	private static final int BG = 0x90000000;
	private static final int HOVER = 0x40FFFFFF;
	private static final int X = 4;

	private final MiningProfitClient mod;
	private List<ShoppingList.Line> lines = new ArrayList<>();
	private int width;
	private int height;
	private int y;
	private boolean visible;
	private double mouseX = -1;
	private double mouseY = -1;
	/** Set by the chat screen's open and close events. */
	public boolean chatOpen;

	public ListHud(MiningProfitClient mod) {
		this.mod = mod;
	}

	public void render(GuiGraphicsExtractor g, DeltaTracker delta) {
		ShoppingList list = mod.shoppingList;
		visible = list != null && mod.config.listVisible;
		if (!visible) return;
		Minecraft mc = Minecraft.getInstance();
		Font font = mc.font;
		lines = list.lines();
		int w = 0;
		for (ShoppingList.Line l : lines) w = Math.max(w, font.width(l.text()) + (l.right().isEmpty() ? 0 : 12 + font.width(l.right())));
		ShoppingList.Line hover = chatOpen ? lineAt(mouseX, mouseY) : null;
		String hint = hover == null || hover.row() == null ? null : hint(hover.row());
		if (hint != null) w = Math.max(w, font.width(hint));
		width = w + 2 * PAD;
		height = (lines.size() + (hint != null ? 1 : 0)) * LINE + 2 * PAD;
		int sh = mc.getWindow().getGuiScaledHeight();
		// Left side, a third of the way down (the earnings box sits top left by default).
		y = Math.max(0, Math.min(sh / 3, sh - height));
		g.fill(X, y, X + width, y + height, BG);
		for (int i = 0; i < lines.size(); i++) {
			ShoppingList.Line l = lines.get(i);
			int ly = y + PAD + i * LINE;
			if (l == hover && l.row() != null) g.fill(X, ly - 1, X + width, ly + LINE - 1, HOVER);
			g.text(font, l.text(), X + PAD, ly, l.row() != null ? ShoppingList.TEXT : l.color(), true);
			if (!l.right().isEmpty()) g.text(font, l.right(), X + width - PAD - font.width(l.right()), ly, l.color(), true);
		}
		if (hint != null) g.text(font, hint, X + PAD, y + PAD + lines.size() * LINE, ShoppingList.TODO, true);
	}

	private String hint(ShoppingList.Row r) {
		if (r.action() == 'c') return "Tıkla: /viewrecipe " + r.id();
		return "Tıkla: /bz " + r.name() + " · " + Format.count(mod.shoppingList.left(r)) + " panoya";
	}

	private ShoppingList.Line lineAt(double mx, double my) {
		if (!visible || mx < X || mx >= X + width || my < y + PAD) return null;
		int idx = (int) Math.floor((my - y - PAD) / LINE);
		return idx >= 0 && idx < lines.size() ? lines.get(idx) : null;
	}

	/** Called every frame while the chat screen is open. */
	public void hover(double mx, double my) {
		mouseX = mx;
		mouseY = my;
	}

	/** Mouse down in the chat screen. Returns true when the list took the click. */
	public boolean click(double mx, double my, int button) {
		if (button != 0) return false;
		ShoppingList.Line l = lineAt(mx, my);
		if (l == null) return false;
		if (lines.indexOf(l) == 0 && mx >= X + width - PAD - 20) {
			mod.runCommand("forge");
			return true;
		}
		if (l.row() == null) return true;
		mod.listClick(l.row());
		return true;
	}
}
