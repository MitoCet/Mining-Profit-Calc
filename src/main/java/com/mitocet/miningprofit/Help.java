package com.mitocet.miningprofit;

import java.util.List;

/**
 * The text of /kazanc help (and /kazanc yardim): every command, grouped, with a short line
 * shown in chat and a longer explanation shown on hover. Kept as plain data so a test can
 * check that every registered command is listed here.
 */
public final class Help {
	private Help() {}

	/** One command line: what to type, what is shown in chat, what is shown on hover. */
	public record Entry(String command, String usage, String shortText, String details) {}

	public record Group(String title, List<Entry> entries) {}

	public static final String INTRO =
		"Mining yaparken kazandığın parayı sayar ve kutuda gösterir. Sadece sohbet mesajlarını okur, oyunda hiçbir şey yapmaz."
		+ " Komutun üstüne gel: ayrıntı. Tıkla: komut sohbete yazılır.";

	public static final List<Group> GROUPS = List.of(
		new Group("Session", List.of(
			new Entry("baslat", "/kazanc baslat", "Yeni session başlatır",
				"Session sıfırlanır ve sayım başlar. Fiyatlar siteden yenilenir. API anahtarı varsa Hypixel'den başlangıç sack sayıları alınır (/kazanc kontrol için)."),
			new Entry("dur", "/kazanc dur", "Durdurur, özeti yazar",
				"Sayım ve süre durur, session silinmez. /kazanc devam ile kaldığın yerden sürer."),
			new Entry("devam", "/kazanc devam", "Durdurulan session'a devam eder",
				"/kazanc dur ile durdurulan session'ın sayımı ve süresi kaldığı yerden sürer."),
			new Entry("bitir", "/kazanc bitir", "Session'ı bitirir ve sıfırlar",
				"Durdurur, özeti (kazanç, süre, saatlik) sohbete yazar ve session'ı sıfırlar. Toplam'a dokunmaz. Yeni session kendiliğinden başlamaz. API anahtarı varsa sack kontrolü de yapılır. Session açıkken oyunu kapatırsan (ya da oyun çökerse) aynısı kendiliğinden olur: sonraki açışta özet sohbete yazılır, API anahtarı varsa önceki session'ın sack kontrolü kendiliğinden yapılır; yeni session yine /kazanc baslat ile başlar."),
			new Entry("", "/kazanc", "Session ve Toplam özeti",
				"Session ve Toplam kazancı, süresi ve saatliği sohbete yazar."),
			new Entry("sifirla", "/kazanc sifirla", "Toplam kazancı sıfırlar",
				"Sadece Toplam (tüm zamanlar) sıfırlanır, session'a dokunulmaz."))),
		new Group("Kutu", List.of(
			new Entry("sekme", "/kazanc sekme", "Session / Toplam sekmesi",
				"Kutuda Session (bu session) ile Toplam (tüm zamanlar) arasında geçer. Kutudaki yazıya tıklayarak da değişir."),
			new Entry("buyuk", "/kazanc buyuk", "Büyük kutu: başlıklara ayrılmış",
				"Kazanç Gemstone, Dwarven Metal, Corpse Loot, Fiesta, Diğer başlıklarına ayrılır. T ile sohbeti açıp başlığa tıklayınca neyden kaç tane geldiği yazar. Kutudaki [+] de aynısını yapar."),
			new Entry("kucuk", "/kazanc kucuk", "Küçük kutu: kazanç, saatlik, süre",
				"Kutuda sadece kazanç, saatlik ve süre görünür. Kutudaki [-] de aynısını yapar."),
			new Entry("goster", "/kazanc goster", "Durmuşken de kutuyu göster",
				"Session durmuşken de kutu görünür. Çalışırken kutu zaten hep görünür."),
			new Entry("gizle", "/kazanc gizle", "Durmuşken kutuyu gizle",
				"Kutu sadece session çalışırken görünür."))),
		new Group("Sayım ayarları", List.of(
			new Entry("bosta", "/kazanc bosta [sn]", "Boşta süre durması",
				"Bu kadar saniye hiç kazanç gelmezse süre durur, ilk kazançta devam eder; saatlik sadece aktif süreyle hesaplanır. Varsayılan 90, 0 = hiç durmasın. Sayı yazmazsan şu anki ayarı söyler."),
			new Entry("anahtar", "/kazanc anahtar", "Anahtarlı corpse'ları say, anahtarı düş (aç/kapa)",
				"Kapalıyken (varsayılan) sadece Lapis corpse loot'u sayılır. Açıkken Tungsten/Umber/Vanguard corpse loot'u da sayılır ve kullanılan Tungsten/Umber/Skeleton Key'ler kazançtan düşülür (büyük kutuda Corpse Loot altında). Resourceful anahtarı kurtarırsa düşülmez. Geriye dönük: açıp kapatınca toplam da değişir."),
			new Entry("fiyat", "/kazanc fiyat", "Fiyatları hemen yenile",
				"Değerler siteden yeniden alınır (normalde 15 dakikada bir kendiliğinden yenilenir). Bitince sohbete 'Fiyatlar yenilendi' (item sayısı, verinin yaşı) ya da hata yazılır. Eski fiyat kullanılıyorsa kutuda '⚠ Fiyatlar güncel değil' yazar."))),
		new Group("Kontrol ve sorunlar", List.of(
			new Entry("api", "/kazanc api <anahtar>", "Hypixel API anahtarını kaydet",
				"developer.hypixel.net anahtarı sadece bu bilgisayardaki mod ayarına kaydedilir. /kazanc api sil siler, /kazanc api kayıtlı mı söyler. Sack kontrolü için gerekir."),
			new Entry("kontrol", "/kazanc kontrol", "Sack sayılarını API ile karşılaştır",
				"Modun okuduğu sack değişimi Hypixel API'deki gerçek sayılarla item item karşılaştırılır. Fark varsa 'gerçek X, mod Y' ve API'ye göre gerçek kazanç yazılır. Hypixel birkaç dakika geç günceller; 2-3 dk sonra tekrar dene. Eksik çıkanlar /kazanc duzelt ile eklenir."),
			new Entry("duzelt", "/kazanc duzelt [otomatik|elle]", "Kaçan sack mesajını API'den ekle",
				"Sack mesajı gelmeden Mineshaft'tan ya da sunucudan çıkarsan son ~30 sn'nin itemleri sack'e girer ama mod göremez. /kazanc kontrol bunları API'den bulur, /kazanc duzelt değerlerini kazanca ekler (session bittiyse sadece Toplam'a). Varsayılan elle. otomatik: kontrolde bulunan eksikler kendiliğinden eklenir ve sunucu değişiminden ya da kısa kesilmiş bir sack listesinden ~3 dk sonra kontrol kendiliğinden yapılır. Not: envanterden sack'e elle koyduğun itemler de fark olarak görünür, otomatikte onlar da eklenir."),
			new Entry("uyari", "/kazanc uyari", "Bu session'daki uyarılar",
				"Kutuda '⚠ Emin değilim' yazıyorsa nedenini söyler: okunamayan ya da iki kez gelen sack mesajı, kısa kesilmiş liste ('+N other items'; gizli kısım az önceki corpse loot'uysa uyarı çıkmaz), tanınmayan item gibi."),
			new Entry("kayit", "/kazanc kayit", "Hata kaydı (aç/kapa)",
				"Açıkken okunan sack mesajları, corpse satırları ve sayılan coinler config/mining-profit-calc-debug.log dosyasına yazılır. Oyun her açılışta yeni dosya başlar; bir önceki oyunun kaydı .old dosyasında kalır. Sayım yanlışsa bu dosyayı gönder."),
			new Entry("update", "/kazanc update", "Modu en yeni sürüme güncelle",
				"GitHub Releases'taki en yeni jar'ı indirir. Oyun açıkken eski jar kullanımda olduğu için hemen değişmez: oyunu kapatınca eski jar silinir, yenisi mods klasörüne konur, tekrar açınca yeni sürüm çalışır. Oyun açılınca yeni sürüm varsa sohbette haber verir; /kazanc update bildirim bu haberi açar/kapatır."),
			new Entry("help", "/kazanc help", "Bu liste",
				"/kazanc yardim da aynı listeyi gösterir."))));

	/** Things the mod does on its own, so the player knows what is and isn't counted. */
	public static final List<String> NOTES = List.of(
		"Sayılır: sack mesajlarındaki mining itemleri ve gemler, frozen corpse loot'u (anahtar ayarı kapalıyken sadece Lapis), /pickupstash ile gelenler.",
		"Oyunu kapatınca session biter ve Toplam'a eklenir; sonraki açışta özeti ve sack kontrolü gelir, yeni session /kazanc baslat ile başlar.",
		"Sayılmaz: envanterden ya da Sack menüsünden sack'e koyulanlar, /craft ile yapılanlar, sack'ten alınanlar.",
		"Değerler sitedeki Mining sekmesinin en kârlı yoluyla hesaplanır. Kutuyu T ile sohbeti açıp sürükleyebilir, köşesinden büyütebilirsin.");
}
