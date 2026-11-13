package com.mitocet.miningprofit;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * /kazanc (or its key in Controls): every command and setting as a button. Each button runs the
 * same /kazanc command, so the menu and the chat commands always do the same thing; the
 * command's answer goes to chat as before. Labels show the setting as it is now.
 */
public final class SettingsScreen extends Screen {
	private static final int ROW = 22;
	private static final int H = 20;

	private final MiningProfitClient mod;

	private Button start;
	private Button pause;
	private Button end;
	private Button check;
	private Button warnings;
	private Button size;
	private Button tab;
	private Button stopped;
	private Button keyCost;
	private Button autoFix;
	private Button idle;
	private Button idleDown;
	private Button keySave;
	private Button keyDelete;
	private Button notify;
	private Button log;
	private EditBox keyBox;

	private int colW;
	private int[] colX;
	private int top;

	public SettingsScreen(MiningProfitClient mod) {
		super(Component.literal("Mining Profit Calc — Ayarlar"));
		this.mod = mod;
	}

	private Button add(int col, int row, Component text, String tip, Button.OnPress press) {
		return add(colX[col], top + row * ROW, colW, text, tip, press);
	}

	private Button add(int x, int y, int w, Component text, String tip, Button.OnPress press) {
		Button b = addRenderableWidget(Button.builder(text, press).pos(x, y).size(w, H).build());
		if (tip != null) b.setTooltip(Tooltip.create(Component.literal(tip)));
		return b;
	}

	/** Runs /kazanc <sub> like typed in chat. */
	private void run(String sub) {
		mod.runCommand(sub);
	}

	/** Runs /kazanc <sub> with the menu closed, for answers that are read in chat. */
	private void runInChat(String sub) {
		Minecraft.getInstance().gui.setScreen(null);
		mod.runCommand(sub);
	}

	@Override
	protected void init() {
		int gap = 8;
		colW = Math.min(170, (width - 20 - 2 * gap) / 3);
		int x0 = (width - 3 * colW - 2 * gap) / 2;
		colX = new int[] { x0, x0 + colW + gap, x0 + 2 * (colW + gap) };
		top = 46;
		int half = (colW - 4) / 2;

		// Session.
		start = add(colX[0], top, half, Component.literal("Başlat"),
			"Yeni session başlatır (/kazanc baslat).", (b) -> run("baslat"));
		pause = add(colX[0] + half + 4, top, colW - half - 4, Component.literal("Durdur"),
			"Durdurur ya da durdurulan session'a devam eder (/kazanc dur, /kazanc devam).",
			(b) -> run(mod.tracker.running() ? "dur" : "devam"));
		end = add(0, 1, Component.literal("Bitir (geçmişe kaydet)"),
			"Session'ı bitirir, özeti sohbete yazar ve geçmişe kaydeder (/kazanc bitir).", (b) -> run("bitir"));
		add(0, 2, Component.literal("Geçmiş…"), "Session geçmişi menüsü (/kazanc gecmis).", (b) -> run("gecmis"));
		check = add(0, 3, Component.literal("Sack kontrolü (API)"),
			"Sack sayılarını Hypixel API ile karşılaştırır (/kazanc kontrol). API anahtarı gerekir.", (b) -> run("kontrol"));
		warnings = add(0, 4, Component.literal("Uyarılar"),
			"Bu session'daki uyarıları sohbete yazar (/kazanc uyari).", (b) -> runInChat("uyari"));
		add(0, 5, Component.literal("Özeti sohbete yaz"),
			"Session ve Toplam özetini sohbete yazar (/kazanc ozet).", (b) -> runInChat("ozet"));

		// Box and counting.
		size = add(1, 0, Component.empty(), "Büyük: kazanç başlıklara ayrılmış. Küçük: kazanç, saatlik, süre (/kazanc buyuk, /kazanc kucuk).",
			(b) -> run(mod.config.big ? "kucuk" : "buyuk"));
		tab = add(1, 1, Component.empty(), "Kutuda Session ile Toplam arasında geçer (/kazanc sekme).", (b) -> run("sekme"));
		stopped = add(1, 2, Component.empty(), "Session durmuşken kutu görünsün mü (/kazanc goster, /kazanc gizle).",
			(b) -> run(mod.config.showStopped ? "gizle" : "goster"));
		keyCost = add(1, 3, Component.empty(),
			"Kapalı: sadece Lapis corpse loot'u sayılır. Açık: bütün corpse'lar sayılır, kullanılan anahtarlar düşülür. Geriye dönük (/kazanc anahtar).",
			(b) -> run("anahtar"));
		autoFix = add(1, 4, Component.empty(),
			"Açıkken sack kontrolünde bulunan eksikler kendiliğinden eklenir (geçmiş menüsündeki ile aynı).",
			(b) -> mod.menuToggleAutoFix());
		idleDown = add(colX[1], top + 5 * ROW, 20, Component.literal("−"), "30 sn azalt (0 = hiç durmasın).",
			(b) -> run("bosta " + Math.max(0, mod.config.idleSeconds - 30)));
		idle = add(colX[1] + 22, top + 5 * ROW, colW - 44, Component.empty(),
			"Bu kadar süre kazanç gelmezse süre durur, ilk kazançta devam eder (/kazanc bosta <sn>).", (b) -> {});
		add(colX[1] + colW - 20, top + 5 * ROW, 20, Component.literal("+"), "30 sn artır.",
			(b) -> run("bosta " + Math.min(3600, mod.config.idleSeconds + 30)));

		// API and other.
		keyBox = new EditBox(font, colX[2], top + 10, colW, 16, Component.literal("API anahtarı"));
		keyBox.setMaxLength(64);
		keyBox.setTooltip(Tooltip.create(Component.literal("developer.hypixel.net anahtarını buraya yapıştır, Kaydet'e bas (/kazanc api <anahtar>). Sadece bu bilgisayarda saklanır.")));
		addRenderableWidget(keyBox);
		keySave = add(colX[2], top + 30, half, Component.literal("Kaydet"), "Kutudaki anahtarı kaydeder ve dener.", (b) -> {
			String k = keyBox.getValue().trim();
			if (k.isEmpty()) return;
			keyBox.setValue("");
			run("api " + k);
		});
		keyDelete = add(colX[2] + half + 4, top + 30, colW - half - 4, Component.literal("Sil"),
			"Kayıtlı API anahtarını siler (/kazanc api sil).", (b) -> run("api sil"));
		add(colX[2], top + 30 + ROW, colW, Component.literal("Fiyatları yenile"),
			"Fiyatları siteden hemen yeniden alır (/kazanc fiyat).", (b) -> run("fiyat"));
		add(colX[2], top + 30 + 2 * ROW, colW, Component.literal("Güncelle (şu an v" + mod.version() + ")"),
			"En yeni jar'ı indirir, oyun kapanınca değiştirir (/kazanc update).", (b) -> run("update"));
		notify = add(colX[2], top + 30 + 3 * ROW, colW, Component.empty(),
			"Oyun açılınca yeni sürüm varsa haber versin mi (/kazanc update bildirim).", (b) -> run("update bildirim"));
		log = add(colX[2], top + 30 + 4 * ROW, colW, Component.empty(),
			"Okunan mesajları config klasöründeki kayıt dosyasına yazar (/kazanc kayit).", (b) -> run("kayit"));
		add(colX[2], top + 30 + 5 * ROW, colW, Component.literal("Yardım (komut listesi)"),
			"Bütün komutları sohbete yazar (/kazanc help).", (b) -> runInChat("help"));

		// Bottom.
		int y = height - 26;
		int bw = (3 * colW + 2 * gap - gap) / 2;
		add(colX[0], y, bw, Component.literal("§cHepsini sil (sıfırla)"),
			"Bütün kayıtlı sessionları siler, onay sorar (/kazanc sifirla).", (b) -> run("sifirla"));
		add(colX[0] + bw + gap, y, 3 * colW + 2 * gap - bw - gap, Component.literal("Kapat"), null,
			(b) -> Minecraft.getInstance().gui.setScreen(null));
		refresh();
	}

	private static Component onOff(String name, boolean on) {
		return Component.literal(name + ": " + (on ? "§aAçık" : "Kapalı"));
	}

	/** Labels and active states from the settings as they are now (commands change them). */
	private void refresh() {
		long now = System.currentTimeMillis();
		boolean running = mod.tracker.running();
		boolean counted = running || mod.tracker.sessionMs(now) > 0;
		start.active = !running;
		pause.setMessage(Component.literal(running ? "Durdur" : "Devam"));
		pause.active = counted;
		end.active = counted;
		check.active = mod.hasApiKey();
		int n = mod.tracker.warnings.size();
		warnings.setMessage(Component.literal(n > 0 ? "Uyarılar (" + n + ")" : "Uyarılar"));
		warnings.active = n > 0;
		size.setMessage(Component.literal("Kutu: " + (mod.config.big ? "Büyük" : "Küçük")));
		tab.setMessage(Component.literal("Sekme: " + (mod.config.sessionTab ? "Session" : "Toplam")));
		stopped.setMessage(Component.literal("Durmuşken kutu: " + (mod.config.showStopped ? "Görünür" : "Gizli")));
		keyCost.setMessage(onOff("Anahtarlı corpse", mod.config.keyCost));
		autoFix.setMessage(onOff("Oto düzelt", mod.config.autoFix));
		idle.setMessage(Component.literal(mod.config.idleSeconds > 0 ? "Boşta durma: " + mod.config.idleSeconds + " sn" : "Boşta durma: yok"));
		idle.active = false;
		idleDown.active = mod.config.idleSeconds > 0;
		keySave.active = !keyBox.getValue().isBlank();
		keyDelete.active = mod.hasApiKey();
		notify.setMessage(onOff("Güncelleme haberi", mod.config.updateNotify));
		log.setMessage(onOff("Hata kaydı", mod.config.debugLog));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		refresh();
		super.extractRenderState(g, mouseX, mouseY, a);
		long now = System.currentTimeMillis();
		g.centeredText(font, title, width / 2, 8, History.HEAD);
		double s = Tracker.net(mod.tracker.sessionCoins, mod.tracker.sessionBreakdown, mod.config.keyCost);
		double t = Tracker.net(mod.tracker.totalCoins, mod.tracker.totalBreakdown, mod.config.keyCost);
		long sms = mod.tracker.sessionMs(now);
		String state = mod.tracker.running() ? (mod.tracker.idle(now) ? "§7(boşta)" : "§a(sayıyor)") : "§7(durdu)";
		g.centeredText(font, Component.literal("Session: §e" + Format.coins(s) + "§r · §e" + Format.coins(Tracker.perHour(s, sms))
			+ "/sa§r · " + Format.time(sms) + " " + state + "§r  |  Toplam: §e" + Format.coins(t)), width / 2, 20, History.TEXT);
		g.text(font, "Session", colX[0] + 2, top - 11, History.HEAD, true);
		g.text(font, "Kutu ve sayım", colX[1] + 2, top - 11, History.HEAD, true);
		g.text(font, "API ve diğer", colX[2] + 2, top - 11, History.HEAD, true);
		g.text(font, mod.hasApiKey() ? "API anahtarı: §akayıtlı" : "API anahtarı: §7yok", colX[2] + 2, top, History.TEXT, true);
	}
}
