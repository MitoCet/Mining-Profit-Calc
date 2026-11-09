# Mining Profit Calc

Hypixel SkyBlock için Minecraft **26.2 Fabric** modu. Mining yaparken kazandığın parayı sayar ve ekranda küçük bir kutuda gösterir. Sadece sohbet mesajlarını okur ve bilgi gösterir; oyunda hiçbir şeyi kendisi yapmaz.

## Kurulum

1. [Fabric Loader](https://fabricmc.net/use/) (26.2) ve [Fabric API](https://modrinth.com/mod/fabric-api) kurulu olsun.
2. [Releases](https://github.com/MitoCet/Mining-Profit-Calc/releases) sayfasından en yeni `mining-profit-calc-1.0.0.jar` dosyasını indir.
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
| `/kazanc gizle`, `/kazanc goster` | Kutuyu gizler / gösterir |
| `/kazanc fiyat` | Fiyatları siteden hemen yeniden alır |

T ile sohbeti açınca kutuyu fareyle basılı tutup sürükleyebilir, üstteki **Session** / **Toplam** yazısına tıklayarak sekme değiştirebilirsin.

## Neyi sayar

- **Sack mesajları** (`[Sacks] +1,234 items`): sitedeki Mining sekmesinde olan malzemeler (Tungsten, Umber, Glacite, Mithril, Titanium, Hard Stone, Refined Mineral, Glossy Gemstone, Suspicious Scrap) ve gemstone'lar. Ham → Enchanted craftlamak kazanç sayılmaz, sack'ten bir şey çıkarmak eksi yazmaz.
- **Frozen corpse loot'u**: ödül listesindeki fiyatı olan her item, envantere düşenler dahil. Corpse'tan gelip sack'e giren itemler iki kez sayılmaz.
- Harcamalar (anahtarlar dahil) düşülmez.

## Değerler nereden geliyor

Değerler [skyblock-forge-flip](https://mitocet.github.io/skyblock-forge-flip/) sitesinin fiyat toplayıcısından gelir (`mod-values.json`, 15 dakikada bir yenilenir). Mining malzemeleri sitedeki en kârlı yolla (ör. Tungsten → Plate, Glacite → Amalgamation, gemlerde Fine ya da Flawless) değerlenir; diğer itemler Bazaar sell offer ya da AH lowest BIN, vergi düşülmüş. Sitenin varsayılan ayarları kullanılır.

## Derleme

GitHub Actions her push'ta derler; `main` dalındaki her derleme Releases'a eklenir. Yerelde: `./gradlew build` (Java 25).
