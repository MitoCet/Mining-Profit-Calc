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
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.Identifier;
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
			if (Files.exists(pricesFile)) prices = Prices.parse(Files.readString(pricesFile, StandardCharsets.UTF_8));
		} catch (Exception e) {
			LOG.warn("Could not read saved mining values: {}", e.toString());
		}
		tracker.totalCoins = config.totalCoins;
		tracker.totalMs = config.totalMs;
		tracker.sessionCoins = config.sessionCoins;
		tracker.sessionMs = config.sessionMs;
		if (config.sessionBreakdown != null) tracker.sessionBreakdown = config.sessionBreakdown;
		if (config.totalBreakdown != null) tracker.totalBreakdown = config.totalBreakdown;
		if (config.open == null) config.open = new java.util.HashSet<>();

		ScheduledExecutorService pool = Executors.newSingleThreadScheduledExecutor((r) -> {
			Thread t = new Thread(r, "miningprofit-prices");
			t.setDaemon(true);
			return t;
		});
		pool.scheduleAtFixedRate(this::refreshPrices, 0, PRICE_REFRESH_MIN, TimeUnit.MINUTES);

		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			if (!overlay) onMessage(message);
		});

		hud = new Hud(this);
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("miningprofit", "box"), hud::render);
		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
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
			if (++ticks >= 600) {
				ticks = 0;
				if (tracker.running()) save();
			}
		});

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, ctx) -> dispatcher.register(
			ClientCommands.literal("kazanc")
				.executes(this::status)
				.then(ClientCommands.literal("baslat").executes((c) -> {
					tracker.start(System.currentTimeMillis());
					save();
					refreshPricesAsync();
					return say(c, "Yeni session başladı. Sack ve corpse kazançları sayılıyor.");
				}))
				.then(ClientCommands.literal("dur").executes((c) -> {
					tracker.stop(System.currentTimeMillis());
					save();
					status(c);
					return say(c, "Durduruldu. Devam etmek için /kazanc devam, yeni session için /kazanc baslat.");
				}))
				.then(ClientCommands.literal("devam").executes((c) -> {
					tracker.resume(System.currentTimeMillis());
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
			tracker.onSackMessage(hovers, now);
			return;
		}
		for (String line : Tracker.lines(text)) tracker.onChatLine(line, now);
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
		say(c, "Session: " + Format.coins(tracker.sessionCoins) + " (" + Format.time(sms) + ", "
			+ Format.coins(Tracker.perHour(tracker.sessionCoins, sms)) + "/sa)"
			+ (tracker.running() ? "" : " · durdu"));
		return say(c, "Toplam: " + Format.coins(tracker.totalCoins) + " (" + Format.time(tms) + ", "
			+ Format.coins(Tracker.perHour(tracker.totalCoins, tms)) + "/sa)");
	}

	private static int say(CommandContext<FabricClientCommandSource> c, String text) {
		c.getSource().sendFeedback(Component.literal("§6[Kazanç] §r" + text));
		return Command.SINGLE_SUCCESS;
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
			pricesFresh = true;
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
