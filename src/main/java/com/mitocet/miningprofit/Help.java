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
				"Durdurur, özeti (kazanç, süre, saatlik) sohbete yazar, session'ı geçmişe kaydeder (/kazanc gecmis) ve sıfırlar. Kazancı olmayan session kaydedilmez. Yeni session kendiliğinden başlamaz. API anahtarı varsa sack kontrolü de yapılır. Session açıkken oyunu kapatırsan (ya da oyun çökerse) aynısı kendiliğinden olur: sonraki açışta özet sohbete yazılır, API anahtarı varsa önceki session'ın sack kontrolü kendiliğinden yapılır; yeni session yine /kazanc baslat ile başlar."),
			new Entry("", "/kazanc", "Ayar menüsü: her komutun butonu",
				"Bütün komutlar ve ayarlar butonlarla: üstte Session ve Toplam özeti; Başlat, Durdur/Devam, Bitir, Geçmiş, Sack kontrolü, Uyarılar; kutu boyu, sekme, durmuşken kutu, anahtarlı corpse, oto düzelt, boşta durma (−/+ 30 sn); API anahtarı (kutuya yapıştır, Kaydet), fiyat yenileme, güncelleme, güncelleme haberi, hata kaydı, yardım, hepsini sil. Butonlar aynı komutları çalıştırır, cevap sohbete yazılır; fareyi butonun üstünde tutunca ne yaptığı yazar. Kısayol tuşu: Ayarlar → Kontroller → Tuş atamaları → Mining Profit Calc → 'Kazanç menüsü' (varsayılan boş)."),
			new Entry("ozet", "/kazanc ozet", "Session ve Toplam özeti",
				"Session ve Toplam kazancı, süresi ve saatliği sohbete yazar (eskiden sadece /kazanc yazınca çıkıyordu)."),
			new Entry("gecmis", "/kazanc gecmis", "Session geçmişi menüsü",
				"Kayıtlı bütün sessionlar (en yeni üstte, süren session en üstte): tarih, süre, kazanç, saatlik, neyden ne kadar kazanıldığı ve API sack kontrolünün sonucu (mod ile gerçek farkı; Ne kazandım başlıklarına ve ailelere ayrılmış, her ailenin net farkı yanında: Glacite + Enchanted Glacite, bir gemin Rough/Flawed/Fine/Flawless'ı…). Düzelt: kontrolün bulduğu eksikleri o session'a ve Toplam'a ekler. Anahtarlı corpse (listenin altında): /kazanc anahtar ile aynı ayar, basınca tutarlar hemen değişir. Oto düzelt (listenin altında, varsayılan kapalı): açıkken kontrolde bulunan eksikler kendiliğinden eklenir ve sunucu değişiminden, /kazanc bitir'den ya da kısa kesilmiş bir sack listesinden ~3 dk sonra kontrol kendiliğinden yapılır. Kontrol et: süren ya da son biten session'ı API ile tekrar karşılaştırır. Sil: session'ı siler, Toplam'dan da çıkar (onay sorar). Toplam = kayıtlı sessionlar + süren session."),
			new Entry("sifirla", "/kazanc sifirla", "Bütün sessionları siler (onaylı)",
				"Geçmiş menüsünde onay sorar; evet dersen bütün kayıtlı sessionlar silinir ve Toplam sıfırlanır. Süren session silinmez. Menüdeki 'Hepsini sil' ile aynı."))),
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
				"Kapalıyken (varsayılan) sadece Lapis corpse loot'u sayılır. Açıkken Tungsten/Umber/Vanguard corpse loot'u da sayılır ve kullanılan Tungsten/Umber/Skeleton Key'ler kazançtan düşülür (büyük kutuda Corpse Loot altında). Resourceful anahtarı kurtarırsa düşülmez. Geriye dönük: açıp kapatınca toplam da değişir. /kazanc menüsündeki ve /kazanc gecmis'teki \"Anahtarlı corpse\" butonu da aynı ayarı açıp kapatır."),
			new Entry("fiyat", "/kazanc fiyat", "Fiyatları hemen yenile",
				"Değerler siteden yeniden alınır (normalde 15 dakikada bir kendiliğinden yenilenir). Bitince sohbete 'Fiyatlar yenilendi' (item sayısı, verinin yaşı) ya da hata yazılır. Eski fiyat kullanılıyorsa kutuda '⚠ Fiyatlar güncel değil' yazar."))),
		new Group("Kontrol ve sorunlar", List.of(
			new Entry("api", "/kazanc api <anahtar>", "Hypixel API anahtarını kaydet",
				"developer.hypixel.net anahtarı sadece bu bilgisayardaki mod ayarına kaydedilir. /kazanc api sil siler, /kazanc api kayıtlı mı söyler. Sack kontrolü için gerekir. SkyBlock'a her girince mod anahtarın hâlâ çalışıp çalışmadığına bakar; bitmişse kırmızı, 12 saatten az kaldıysa (oyun başına bir kez) sarı uyarı verir."),
			new Entry("kontrol", "/kazanc kontrol", "Sack sayılarını API ile karşılaştır",
				"Modun okuduğu sack değişimi Hypixel API'deki gerçek sayılarla item item karşılaştırılır. Fark varsa 'gerçek X, mod Y' ve API'ye göre gerçek kazanç yazılır. Hypixel birkaç dakika geç günceller; 2-3 dk sonra tekrar dene. Sack mesajı gelmeden Mineshaft'tan ya da sunucudan çıkarsan son ~30 sn'nin itemleri sack'e girer ama mod göremez; kontrol bunları bulur. Eksik çıkanlar /kazanc gecmis'te session seçilip Düzelt'e basınca eklenir (mesajdaki /kazanc gecmis'e tıklayabilirsin). Tekrar kontrol edince eklenecek miktar yeni sonuca göre değişir; önceden eklenen tekrar eklenmez. Not: envanterden sack'e elle koyduğun itemler de fark olarak görünür."),
			new Entry("uyari", "/kazanc uyari", "Bu session'daki uyarılar",
				"Kutuda '⚠ Emin değilim' yazıyorsa nedenini söyler: okunamayan ya da iki kez gelen sack mesajı, kısa kesilmiş liste ('+N other items'; gizli kısım az önceki corpse loot'uysa uyarı çıkmaz), tanınmayan item gibi."),
			new Entry("kayit", "/kazanc kayit", "Hata kaydı (aç/kapa)",
				"Açıkken okunan sack mesajları, corpse satırları ve sayılan coinler config/mining-profit-calc-debug.log dosyasına yazılır. Oyun her açılışta yeni dosya başlar; bir önceki oyunun kaydı .old dosyasında kalır. Sayım yanlışsa bu dosyayı gönder."),
			new Entry("update", "/kazanc update", "Modu en yeni sürüme güncelle",
				"GitHub Releases'taki en yeni jar'ı indirir. Oyun açıkken eski jar kullanımda olduğu için hemen değişmez: oyunu kapatınca eski jar silinir, yenisi mods klasörüne konur, tekrar açınca yeni sürüm çalışır. Oyunu açıp SkyBlock'a ilk girince yeni sürüm varsa sohbette haber verir; /kazanc update bildirim bu haberi açar/kapatır."),
			new Entry("help", "/kazanc help", "Bu liste",
				"/kazanc yardim da aynı listeyi gösterir."))));

	/** Things the mod does on its own, so the player knows what is and isn't counted. */
	public static final List<String> NOTES = List.of(
		"Sayılır: sack mesajlarındaki mining itemleri ve gemler, frozen corpse loot'u (anahtar ayarı kapalıyken sadece Lapis), /pickupstash ile gelenler.",
		"Oyunu kapatınca session biter ve geçmişe kaydedilir; sonraki açışta SkyBlock'a girince özeti ve sack kontrolü gelir, yeni session /kazanc baslat ile başlar.",
		"Sayılmaz: envanterden ya da Sack menüsünden sack'e koyulanlar, /craft ile yapılanlar, sack'ten alınanlar.",
		"Değerler sitedeki Mining sekmesinin en kârlı yoluyla hesaplanır. Kutuyu T ile sohbeti açıp sürükleyebilir, köşesinden büyütebilirsin.");
}
