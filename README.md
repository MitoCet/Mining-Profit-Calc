# Mining Profit Calc

Hypixel SkyBlock için Minecraft **26.2 Fabric** modu. Mining yaparken kazandığın parayı sayar ve ekranda küçük bir kutuda gösterir. Sadece sohbet mesajlarını okur ve bilgi gösterir; oyunda hiçbir şeyi kendisi yapmaz.

## Kurulum

1. [Fabric Loader](https://fabricmc.net/use/) (26.2) ve [Fabric API](https://modrinth.com/mod/fabric-api) kurulu olsun.
2. [Releases](https://github.com/MitoCet/Mining-Profit-Calc/releases) sayfasından en yeni `mining-profit-calc-1.0.X.jar` dosyasını indir (X = release numarası; eski sürümün jar'ını mods klasöründen sil).
3. Dosyayı `.minecraft/mods` klasörüne at.

## Kullanım

| Komut | Ne yapar |
| --- | --- |
| `/kazanc baslat` | Yeni session başlatır (session sıfırlanır) |
| `/kazanc dur` | Durdurur ve özeti sohbete yazar |
| `/kazanc devam` | Durdurulan session'a devam eder |
| `/kazanc` | Session ve toplam özeti |
| `/kazanc sekme` | Kutuda Session / Toplam arasında geçiş |
| `/kazanc sifirla` | Toplam kazancı sıfırlar |
| `/kazanc goster`, `/kazanc gizle` | Session durmuşken de kutuyu gösterir / gizler (çalışırken kutu hep görünür) |
| `/kazanc buyuk`, `/kazanc kucuk` | Kutuyu büyük (başlıklara ayrılmış kazanç) ya da küçük (kazanç, saatlik, süre) yapar |
| `/kazanc fiyat` | Fiyatları siteden hemen yeniden alır |

Kutu session çalışırken görünür. T ile sohbeti açınca kutuyu fareyle basılı tutup sürükleyebilir, sağ alt köşesinden çekerek büyütüp küçültebilir, üstteki **Session** / **Toplam** yazısına tıklayarak sekme değiştirebilirsin. Sağ üstteki **[+]** / **[-]** kutuyu büyütür ya da küçültür.

Büyük kutuda kazanç başlıklara ayrılır: Gemstone, Dwarven Metal (Mithril, Titanium, Tungsten, Umber), Corpse Loot, Fiesta (Glossy Gemstone, Refined Mineral), Diğer (Glacite, Hard Stone, Suspicious Scrap). En çok kazandıran üstte, kazancı olmayan başlık görünmez. Başlığa tıklayınca açılır ve neyden kaç tane geldiği yazar (ör. `Ruby: 1.200 Rough, 15 Flawed · 45,0k`). Session sekmesi bu session'ı, Toplam sekmesi tüm zamanları gösterir. Açık başlıklar ve kutu boyu oyun kapanınca da hatırlanır.

## Neyi sayar

- **Sack mesajları** (`[Sacks] +1,234 items`): sitedeki Mining sekmesinde olan malzemeler (Tungsten, Umber, Glacite, Mithril, Titanium, Hard Stone, Refined Mineral, Glossy Gemstone, Suspicious Scrap) ve gemstone'lar. Ham → Enchanted craftlamak kazanç sayılmaz, sack'ten bir şey çıkarmak eksi yazmaz.
- **Frozen corpse loot'u**: ödül listesindeki fiyatı olan her item, envantere düşenler dahil. Corpse'tan gelip sack'e giren itemler iki kez sayılmaz.
- Compact'tan düşen Enchanted itemler ve Pristine'den düşen Flawed gemler sack'e girince kendi değerleriyle sayılır (sack doluyken envantere düşenler sayılmaz).
- Harcamalar (anahtarlar dahil) düşülmez.

## Değerler nereden geliyor

Mod son indirdiği değerleri kaydeder; oyun açılınca önce onları kullanır, yenileri gelince onlara geçer. Kutuda eski fiyat kullanılıyorsa "⚠ Fiyatlar güncel değil" notu çıkar. Değerler [skyblock-forge-flip](https://mitocet.github.io/skyblock-forge-flip/) sitesinin fiyat toplayıcısından gelir (`mod-values.json`, 15 dakikada bir yenilenir). Mining malzemeleri sitedeki en kârlı yolla (ör. Tungsten → Plate, Glacite → Amalgamation, gemlerde Fine ya da Flawless) değerlenir; diğer itemler Bazaar sell offer ya da AH lowest BIN, vergi düşülmüş. Sitenin varsayılan ayarları kullanılır.

## Derleme

GitHub Actions her push'ta derler; `main` dalındaki her derleme Releases'a eklenir. Yerelde: `./gradlew build` (Java 25).

## Bilinen sınırlar

- Sack'e mining dışında giren itemler de sayılır: sandıktan ya da `/pickupstash` ile sack'e koyduğun, Bazaar'dan alıp sack'e giden Mining malzemeleri kazanç gibi görünür. Bunları yaparken session'ı durdur (`/kazanc dur`, sonra `/kazanc devam`).
- Sack ve corpse mesajlarının biçimi oyunda denenerek doğrulanmadı; Hypixel biçimi farklıysa sayım 0 kalabilir.
