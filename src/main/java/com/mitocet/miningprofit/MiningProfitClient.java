package com.mitocet.miningprofit;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Mining Profit Calc: counts what mining earns, valued like the skyblock-forge-flip site's
 * Mining tab. It only reads chat messages and shows a box; it never does anything in the game.
 */
public final class MiningProfitClient implements ClientModInitializer {
	public static final Logger LOG = LoggerFactory.getLogger("miningprofit");
	private static final long PRICE_REFRESH_MIN = 15;

	private volatile Prices prices = Prices.EMPTY;
	public final Tracker tracker = new Tracker(() -> prices);
	public Config config;
	private Path configFile;
	private Path pricesFile;
	private DebugLog debug;
	private volatile String pricesJson;
	private String lastInventoryLogged;
	private Hud hud;
	private int ticks;
	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

	/** False until values were downloaded in this game session (only the saved ones are in use). */
	public volatile boolean pricesFresh;

	public Prices prices() {
		return prices;
	}

	@Override
	public void onInitializeClient() {
		configFile = FabricLoader.getInstance().getConfigDir().resolve("mining-profit-calc.json");
		config = Config.load(configFile);
		// Start with the values saved last time, so the box works at once (and offline) until the
		// fresh ones arrive.
		pricesFile = FabricLoader.getInstance().getConfigDir().resolve("mining-profit-calc-values.json");
		try {
			if (Files.exists(pricesFile)) {
				pricesJson = Files.readString(pricesFile, StandardCharsets.UTF_8);
				prices = Prices.parse(pricesJson);
			}
		} catch (Exception e) {
			LOG.warn("Could not read saved mining values: {}", e.toString());
		}
		debug = new DebugLog(FabricLoader.getInstance().getConfigDir().resolve("mining-profit-calc-debug.log"));
		tracker.totalCoins = config.totalCoins;
		tracker.totalMs = config.totalMs;
		tracker.sessionCoins = config.sessionCoins;
		tracker.sessionMs = config.sessionMs;
		if (config.sessionBreakdown != null) tracker.sessionBreakdown = config.sessionBreakdown;
		if (config.totalBreakdown != null) tracker.totalBreakdown = config.totalBreakdown;
		if (config.open == null) config.open = new java.util.HashSet<>();
		tracker.idleMs = Math.max(0, config.idleSeconds) * 1000L;
		if (config.sackCheck == null) config.sackCheck = new SackCheck();
		if (config.sackCheck.read == null) config.sackCheck.read = new java.util.HashMap<>();
		if (config.sackCheck.corpse == null) config.sackCheck.corpse = new java.util.HashMap<>();
		tracker.corpseMining = (id, n) -> config.sackCheck.onCorpseItem(id, n);

		ScheduledExecutorService pool = Executors.newSingleThreadScheduledExecutor((r) -> {
			Thread t = new Thread(r, "miningprofit-prices");
			t.setDaemon(true);
			return t;
		});
		pool.scheduleAtFixedRate(this::refreshPrices, 0, PRICE_REFRESH_MIN, TimeUnit.MINUTES);

		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			if (!overlay) onMessage(message);
		});

		// /pickupstash (typed or clicked in chat): what it brings into the inventory is from the stash.
		ClientSendMessageEvents.COMMAND.register((command) -> {
			if (command.trim().toLowerCase(java.util.Locale.ROOT).startsWith("pickupstash")) {
				long now = System.currentTimeMillis();
				tracker.onStashPickup(now);
				if (config.debugLog) debug.simple("stash", now);
			}
		});

		hud = new Hud(this);
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("miningprofit", "box"), hud::render);
		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			// While a Sack menu is open (and just after), sack changes are not mining.
			if (Tracker.isSackMenu(screen.getTitle().getString())) {
				tracker.onSackMenu(true, System.currentTimeMillis());
				if (config.debugLog) debug.simple("sackmenu-open", System.currentTimeMillis());
				ScreenEvents.remove(screen).register((s) -> {
					tracker.onSackMenu(false, System.currentTimeMillis());
					if (config.debugLog) debug.simple("sackmenu-close", System.currentTimeMillis());
				});
				return;
			}
			if (!(screen instanceof ChatScreen)) return;
			hud.chatOpen = true;
			ScreenMouseEvents.allowMouseClick(screen).register((s, e) -> !hud.click(e.x(), e.y(), e.button()));
			ScreenMouseEvents.allowMouseRelease(screen).register((s, e) -> !hud.release());
			ScreenEvents.afterExtract(screen).register((s, g, mx, my, delta) -> hud.drag(mx, my));
			ScreenEvents.remove(screen).register((s) -> {
				hud.chatOpen = false;
				hud.release();
			});
		});

		// Closing the game ends a running session's time here; the next game starts stopped.
		ClientLifecycleEvents.CLIENT_STOPPING.register((client) -> save());
		// Save every 30 seconds while running, so a crash loses little.
		ClientTickEvents.END_CLIENT_TICK.register((client) -> {
			// Look at the inventory twice a second: items put from it into the sacks are not earnings.
			if (ticks % 10 == 0) readInventory(client);
			if (++ticks >= 600) {
				ticks = 0;
				if (tracker.running()) save();
			}
		});

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, ctx) -> dispatcher.register(
			ClientCommands.literal("kazanc")
				.executes(this::status)
				.then(ClientCommands.literal("baslat").executes((c) -> {
					long now = System.currentTimeMillis();
					tracker.start(now);
					config.sackCheck.endedCoins = null;
					logControl("start", now);
					save();
					refreshPricesAsync();
					say(c, "Yeni session başladı. Sack ve corpse kazançları sayılıyor.");
					if (hasKey()) takeBase(c.getSource());
					return Command.SINGLE_SUCCESS;
				}))
				.then(ClientCommands.literal("dur").executes((c) -> {
					long now = System.currentTimeMillis();
					tracker.stop(now);
					logControl("stop", now);
					save();
					status(c);
					return say(c, "Durduruldu. Devam etmek için /kazanc devam, yeni session için /kazanc baslat.");
				}))
				.then(ClientCommands.literal("bitir").executes((c) -> {
					long now = System.currentTimeMillis();
					long ms = tracker.sessionMs(now);
					double coins = Tracker.net(tracker.sessionCoins, tracker.sessionBreakdown, config.keyCost);
					tracker.end(now);
					// /kazanc kontrol after this still compares the session that just ended.
					config.sackCheck.endedCoins = coins;
					logControl("end", now);
					save();
					say(c, "Session bitti: " + Format.coins(coins) + " (" + Format.time(ms) + ", "
						+ Format.coins(Tracker.perHour(coins, ms)) + "/sa). Session sıfırlandı, Toplam'a eklendi."
						+ " Yeni session için /kazanc baslat.");
					if (hasKey() && config.sackCheck.hasBase()) check(c.getSource(), coins);
					return Command.SINGLE_SUCCESS;
				}))
				.then(ClientCommands.literal("devam").executes((c) -> {
					long now = System.currentTimeMillis();
					tracker.resume(now);
					logControl("resume", now);
					save();
					return say(c, "Session devam ediyor.");
				}))
				.then(ClientCommands.literal("sifirla").executes((c) -> {
					tracker.resetTotal(System.currentTimeMillis());
					save();
					return say(c, "Toplam kazanç sıfırlandı (session'a dokunulmadı).");
				}))
				.then(ClientCommands.literal("sekme").executes((c) -> {
					config.sessionTab = !config.sessionTab;
					save();
					return say(c, "Kutuda " + (config.sessionTab ? "Session" : "Toplam") + " gösteriliyor.");
				}))
				.then(ClientCommands.literal("gizle").executes((c) -> {
					config.showStopped = false;
					save();
					return say(c, "Kutu sadece session çalışırken görünecek.");
				}))
				.then(ClientCommands.literal("goster").executes((c) -> {
					config.showStopped = true;
					save();
					return say(c, "Kutu session durmuşken de görünecek. Gizlemek için /kazanc gizle.");
				}))
				.then(ClientCommands.literal("buyuk").executes((c) -> {
					config.big = true;
					save();
					return say(c, "Kutu büyük: kazanç başlıklara ayrılmış. Başlığa tıklayınca (T ile) açılır.");
				}))
				.then(ClientCommands.literal("kucuk").executes((c) -> {
					config.big = false;
					save();
					return say(c, "Kutu küçük: sadece kazanç ve saatlik.");
				}))
				.then(ClientCommands.literal("kayit").executes((c) -> {
					config.debugLog = !config.debugLog;
					save();
					if (config.debugLog) {
						long now = System.currentTimeMillis();
						lastInventoryLogged = null;
						if (pricesJson != null) debug.prices(pricesJson, now);
						if (tracker.running()) debug.simple("start", now);
						return say(c, "Kayıt açık: okunan mesajlar " + debug.file().getFileName()
							+ " dosyasına (config klasörü) yazılıyor. Kapatmak için /kazanc kayit.");
					}
					return say(c, "Kayıt kapalı. Uyarılar yine de dosyaya yazılır.");
				}))
				.then(ClientCommands.literal("uyari").executes((c) -> {
					if (tracker.warnings.isEmpty()) return say(c, "Bu session'da uyarı yok.");
					say(c, "Bu session'daki uyarılar (ham mesajlar " + debug.file().getFileName() + " dosyasında):");
					for (String w : tracker.warnings) say(c, "⚠ " + w);
					return Command.SINGLE_SUCCESS;
				}))
				.then(ClientCommands.literal("anahtar").executes((c) -> {
					config.keyCost = !config.keyCost;
					save();
					return say(c, config.keyCost
						? "Corpse anahtarları kazançtan düşülüyor (büyük kutuda Corpse Loot altında). Kapatmak için /kazanc anahtar."
						: "Corpse anahtarları kazançtan düşülmüyor.");
				}))
				.then(ClientCommands.literal("bosta")
					.executes((c) -> say(c, config.idleSeconds > 0
						? config.idleSeconds + " sn kazanç gelmezse süre durur, ilk kazançta devam eder. Değiştirmek için /kazanc bosta <sn> (0 = hiç durmasın)."
						: "Boşta süre durmuyor. Açmak için /kazanc bosta <sn> (ör. 90)."))
					.then(ClientCommands.argument("sn", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 3600)).executes((c) -> {
						config.idleSeconds = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "sn");
						tracker.idleMs = config.idleSeconds * 1000L;
						save();
						return say(c, config.idleSeconds > 0
							? config.idleSeconds + " sn kazanç gelmezse süre durur."
							: "Boşta süre durmayacak.");
					})))
				.then(ClientCommands.literal("api")
					.executes((c) -> say(c, hasKey()
						? "API anahtarı kayıtlı (" + mask(config.apiKey) + "). Değiştirmek için /kazanc api <anahtar>, silmek için /kazanc api sil."
						: "API anahtarı yok. /kazanc api <anahtar> ile gir (developer.hypixel.net). Sadece bu bilgisayarda saklanır."))
					.then(ClientCommands.argument("anahtar", com.mojang.brigadier.arguments.StringArgumentType.word()).executes((c) -> {
						String key = com.mojang.brigadier.arguments.StringArgumentType.getString(c, "anahtar").trim();
						if (key.equalsIgnoreCase("sil")) {
							config.apiKey = null;
							save();
							return say(c, "API anahtarı silindi.");
						}
						config.apiKey = key;
						save();
						say(c, "API anahtarı kaydedildi, deneniyor...");
						// Try it now; with no base yet, the answer becomes the base.
						if (!config.sackCheck.hasBase()) takeBase(c.getSource());
						else fetchSacks(c.getSource(), (counts) -> say(c.getSource(), "Anahtar çalışıyor."));
						return Command.SINGLE_SUCCESS;
					})))
				.then(ClientCommands.literal("kontrol").executes((c) -> {
					if (!hasKey()) return say(c, "Önce API anahtarını gir: /kazanc api <anahtar>");
					if (!config.sackCheck.hasBase()) {
						say(c, "Karşılaştırma için başlangıç sayısı yoktu, şimdi alınıyor. Biraz kazdıktan sonra tekrar /kazanc kontrol yaz.");
						takeBase(c.getSource());
						return Command.SINGLE_SUCCESS;
					}
					long now = System.currentTimeMillis();
					// Right after /kazanc bitir the session is zero: compare the one that ended.
					boolean ended = !tracker.running() && tracker.sessionMs(now) == 0 && config.sackCheck.endedCoins != null;
					check(c.getSource(), ended ? config.sackCheck.endedCoins
						: Tracker.net(tracker.sessionCoins, tracker.sessionBreakdown, config.keyCost));
					return Command.SINGLE_SUCCESS;
				}))
				.then(ClientCommands.literal("help").executes(this::help))
				.then(ClientCommands.literal("yardim").executes(this::help))
				.then(ClientCommands.literal("fiyat").executes((c) -> {
					refreshPricesAsync();
					return say(c, "Fiyatlar siteden yeniden alınıyor.");
				}))
		));
	}

	private void onMessage(Component message) {
		long now = System.currentTimeMillis();
		String text = message.getString();
		if (Tracker.strip(text).contains("[Sacks]")) {
			List<String> hovers = new ArrayList<>();
			collectHovers(message, hovers);
			long warned = tracker.warningCount;
			config.sackCheck.onSackMessage(hovers, prices);
			double coins = tracker.onSackMessage(hovers, now);
			// With the log on everything goes in; with it off only messages that gave a warning.
			if (config.debugLog || tracker.warningCount != warned) debug.sack(hovers, coins, now);
			logWarnings(warned, now);
			return;
		}
		for (String line : Tracker.lines(text)) {
			boolean corpse = tracker.corpseOpen();
			long warned = tracker.warningCount;
			double coins = tracker.onChatLine(line, now);
			String low = Tracker.strip(line).toLowerCase(java.util.Locale.ROOT);
			boolean related = corpse || tracker.corpseOpen() || coins > 0
				|| low.contains("stash") || low.contains("supercrafted") || low.contains("resourceful");
			if (related && (config.debugLog || tracker.warningCount != warned)) debug.chat(line, coins, now);
			logWarnings(warned, now);
		}
	}

	/** Writes the warnings given since {@code before} to the log (always, even with the log off). */
	private void logWarnings(long before, long now) {
		long added = tracker.warningCount - before;
		List<String> w = tracker.warnings;
		for (int i = (int) Math.max(0, w.size() - added); i < w.size(); i++) debug.warn(w.get(i), now);
	}

	private void logControl(String kind, long now) {
		if (!config.debugLog) return;
		if (kind.equals("start") && pricesJson != null) debug.prices(pricesJson, now);
		debug.simple(kind, now);
	}

	private void readInventory(Minecraft client) {
		LocalPlayer player = client.player;
		if (player == null) {
			tracker.onInventory(null, System.currentTimeMillis());
			return;
		}
		List<Map.Entry<String, Integer>> stacks = new ArrayList<>();
		Inventory inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) add(stacks, inv.getItem(i));
		// The item held on the cursor in a menu still belongs to the inventory.
		add(stacks, player.containerMenu.getCarried());
		long now = System.currentTimeMillis();
		tracker.onInventory(stacks, now);
		if (config.debugLog) {
			// Only mining items, and only when they changed.
			List<Map.Entry<String, Integer>> mining = new ArrayList<>();
			for (Map.Entry<String, Integer> e : stacks) {
				Prices.Item item = prices.byName(e.getKey());
				if (item != null && item.mining()) mining.add(e);
			}
			String sig = mining.toString();
			if (!sig.equals(lastInventoryLogged)) {
				lastInventoryLogged = sig;
				debug.inventory(mining, now);
			}
		}
	}

	private static void add(List<Map.Entry<String, Integer>> out, ItemStack stack) {
		if (!stack.isEmpty()) out.add(Map.entry(stack.getHoverName().getString(), stack.getCount()));
	}

	private static void collectHovers(Component c, List<String> out) {
		HoverEvent hover = c.getStyle().getHoverEvent();
		if (hover instanceof HoverEvent.ShowText show) out.add(show.value().getString());
		for (Component child : c.getSiblings()) collectHovers(child, out);
	}

	private int status(CommandContext<FabricClientCommandSource> c) {
		long now = System.currentTimeMillis();
		long sms = tracker.sessionMs(now);
		long tms = tracker.totalMs(now);
		double s = Tracker.net(tracker.sessionCoins, tracker.sessionBreakdown, config.keyCost);
		double t = Tracker.net(tracker.totalCoins, tracker.totalBreakdown, config.keyCost);
		say(c, "Session: " + Format.coins(s) + " (" + Format.time(sms) + ", "
			+ Format.coins(Tracker.perHour(s, sms)) + "/sa)"
			+ (tracker.running() ? (tracker.idle(now) ? " · boşta" : "") : " · durdu"));
		say(c, "Toplam: " + Format.coins(t) + " (" + Format.time(tms) + ", "
			+ Format.coins(Tracker.perHour(t, tms)) + "/sa)"
			+ (config.keyCost ? " · anahtarlar düşüldü" : ""));
		return say(c, "§7Tüm komutlar: /kazanc help");
	}

	/** /kazanc help: every command with a short line; hover for details, click to type it. */
	private int help(CommandContext<FabricClientCommandSource> c) {
		FabricClientCommandSource src = c.getSource();
		src.sendFeedback(Component.literal("§6§l[Kazanç] Komutlar"));
		src.sendFeedback(Component.literal("§7" + Help.INTRO));
		for (Help.Group group : Help.GROUPS) {
			src.sendFeedback(Component.literal("§e" + group.title() + ":"));
			for (Help.Entry e : group.entries()) {
				// Commands that take an argument are typed with a trailing space, the rest in full.
				String typed = "/kazanc" + (e.command().isEmpty() ? "" : " " + e.command())
					+ (e.usage().contains("<") || e.usage().contains("[") ? " " : "");
				Component hover = Component.literal("§b" + e.usage() + "\n§r" + e.details() + "\n§7Tıkla: sohbete yaz");
				MutableComponent line = Component.literal(" §b" + e.usage() + " §7- §r" + e.shortText())
					.setStyle(Style.EMPTY
						.withHoverEvent(new HoverEvent.ShowText(hover))
						.withClickEvent(new ClickEvent.SuggestCommand(typed)));
				src.sendFeedback(line);
			}
		}
		for (String note : Help.NOTES) src.sendFeedback(Component.literal("§7• " + note));
		return Command.SINGLE_SUCCESS;
	}

	private static int say(CommandContext<FabricClientCommandSource> c, String text) {
		c.getSource().sendFeedback(Component.literal("§6[Kazanç] §r" + text));
		return Command.SINGLE_SUCCESS;
	}

	// ---------- sack check against the Hypixel API ----------
	private boolean hasKey() {
		return config.apiKey != null && !config.apiKey.isBlank();
	}

	private static String mask(String key) {
		return key.length() <= 4 ? "****" : "****" + key.substring(key.length() - 4);
	}

	private static void say(FabricClientCommandSource src, String text) {
		src.sendFeedback(Component.literal("§6[Kazanç] §r" + text));
	}

	/** Takes the API's sack counts as the new base of the check. */
	private void takeBase(FabricClientCommandSource src) {
		fetchSacks(src, (counts) -> {
			config.sackCheck.setBase(counts, System.currentTimeMillis());
			save();
			say(src, "Sack sayıları API'den alındı; /kazanc bitir'de (ya da /kazanc kontrol ile) modun okuduğuyla karşılaştırılacak.");
		});
	}

	/** Compares the API's sack counts now with the base and what the mod read since. */
	private void check(FabricClientCommandSource src, double modCoins) {
		say(src, "Sack sayıları API'den karşılaştırılıyor...");
		fetchSacks(src, (counts) -> {
			SackCheck.Result r = config.sackCheck.compare(counts, prices);
			if (r.apiUnchanged()) {
				say(src, "Hypixel sack verisini henüz güncellemedi (birkaç dakika gecikebiliyor). 2-3 dk sonra /kazanc kontrol yaz.");
				return;
			}
			if (r.diffs().isEmpty()) {
				config.sackCheck.lastReport = null;
				save();
				say(src, "§aSack sayıları tutuyor§r: API ile modun okuduğu aynı (" + r.matched() + " item). Kazanç: " + Format.coins(modCoins) + ".");
				return;
			}
			double missingCoins = 0;
			for (SackCheck.Diff d : r.diffs()) {
				Prices.Item item = prices.byId(d.id());
				missingCoins += d.missing() * (item != null ? item.value() : 0);
			}
			// The same differences as the last check: one line, no new warning.
			if (r.key().equals(config.sackCheck.lastReport)) {
				say(src, "Önceki kontrolle aynı, değişen yok: " + r.diffs().size() + " itemde fark. Mod: "
					+ Format.coins(modCoins) + " → API'ye göre gerçek: §a~" + Format.coins(modCoins + missingCoins) + "§r.");
				return;
			}
			config.sackCheck.lastReport = r.key();
			save();
			say(src, "§e⚠ Fark var§r (" + r.matched() + " item tutuyor). Gerçek (API) / modun okuduğu:");
			int shown = 0;
			for (SackCheck.Diff d : r.diffs()) {
				Prices.Item item = prices.byId(d.id());
				double value = item != null ? item.value() : 0;
				if (++shown > 12) continue;
				String name = item != null && item.name() != null ? item.name() : d.id();
				say(src, "⚠ " + name + ": gerçek " + signed(d.api()) + ", mod " + signed(d.read())
					+ " (fark " + signed(d.missing()) + ", " + (d.missing() * value >= 0 ? "+" : "")
					+ Format.coins(d.missing() * value) + ")");
			}
			if (shown > 12) say(src, "... ve " + (shown - 12) + " item daha.");
			say(src, "Mod: " + Format.coins(modCoins) + " → API'ye göre gerçek: §a~" + Format.coins(modCoins + missingCoins)
				+ "§r (fark " + (missingCoins >= 0 ? "+" : "") + Format.coins(missingCoins) + ").");
			say(src, "Fark Hypixel'in gecikmesinden de olabilir: 2-3 dk sonra /kazanc kontrol ile tekrar bak. Kalıcıysa sack mesajı gelmemiş ya da iki kez okunmuştur (/kazanc kayit ile kaydı açık tut). Envanterden sack'e koyduğun ya da sack'ten sattığın itemler de API farkına girer.");
			long now = System.currentTimeMillis();
			long before = tracker.warningCount;
			tracker.warn("API sack sayısıyla " + r.diffs().size() + " itemde fark: mod " + Format.coins(modCoins)
				+ ", gerçek ~" + Format.coins(modCoins + missingCoins));
			logWarnings(before, now);
		});
	}

	private static String signed(long n) {
		return (n > 0 ? "+" : "") + String.format(java.util.Locale.ROOT, "%,d", n).replace(',', '.');
	}

	/** Gets the selected profile's sack counts off the game thread; answers on the game thread. */
	private void fetchSacks(FabricClientCommandSource src, java.util.function.Consumer<Map<String, Long>> then) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null) {
			say(src, "Oyunda değilsin, API'ye bakılamadı.");
			return;
		}
		String uuid = player.getUUID().toString().replace("-", "");
		String key = config.apiKey.trim();
		Thread t = new Thread(() -> {
			Map<String, Long> counts;
			String error;
			try {
				HttpRequest req = HttpRequest.newBuilder(URI.create(SackCheck.URL + uuid))
					.header("API-Key", key).timeout(Duration.ofSeconds(30)).GET().build();
				HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
				counts = SackCheck.parseSacks(res.body(), uuid);
				error = null;
			} catch (IllegalStateException e) {
				counts = null;
				error = e.getMessage();
			} catch (Exception e) {
				LOG.warn("Hypixel API failed: {}", e.toString());
				counts = null;
				error = "Hypixel API'ye ulaşılamadı (" + e.getClass().getSimpleName() + ").";
			}
			Map<String, Long> got = counts;
			String err = error;
			mc.execute(() -> {
				if (got != null) then.accept(got);
				else say(src, "§c" + err);
			});
		}, "miningprofit-hypixel");
		t.setDaemon(true);
		t.start();
	}

	private void refreshPricesAsync() {
		Thread t = new Thread(this::refreshPrices, "miningprofit-prices-now");
		t.setDaemon(true);
		t.start();
	}

	private void refreshPrices() {
		try {
			HttpRequest req = HttpRequest.newBuilder(URI.create(Prices.URL)).timeout(Duration.ofSeconds(30)).GET().build();
			HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
			if (res.statusCode() != 200) throw new IllegalStateException("HTTP " + res.statusCode());
			prices = Prices.parse(res.body());
			pricesJson = res.body();
			pricesFresh = true;
			if (config.debugLog) debug.prices(pricesJson, System.currentTimeMillis());
			Files.writeString(pricesFile, res.body(), StandardCharsets.UTF_8);
			LOG.info("Mining values loaded");
		} catch (Exception e) {
			LOG.warn("Could not load mining values: {}", e.toString());
		}
	}

	public void save() {
		long now = System.currentTimeMillis();
		config.totalCoins = tracker.totalCoins;
		config.totalMs = tracker.totalMs(now);
		config.sessionCoins = tracker.sessionCoins;
		config.sessionMs = tracker.sessionMs(now);
		config.sessionBreakdown = tracker.sessionBreakdown;
		config.totalBreakdown = tracker.totalBreakdown;
		config.save(configFile);
	}
}
