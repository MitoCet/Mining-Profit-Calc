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
| `/kazanc bitir` | Session'ı bitirir: durdurur, özeti sohbete yazar ve session'ı sıfırlar (Toplam'a dokunmaz) |
| `/kazanc devam` | Durdurulan session'a devam eder |
| `/kazanc` | Session ve toplam özeti |
| `/kazanc sekme` | Kutuda Session / Toplam arasında geçiş |
| `/kazanc sifirla` | Toplam kazancı sıfırlar |
| `/kazanc goster`, `/kazanc gizle` | Session durmuşken de kutuyu gösterir / gizler (çalışırken kutu hep görünür) |
| `/kazanc buyuk`, `/kazanc kucuk` | Kutuyu büyük (başlıklara ayrılmış kazanç) ya da küçük (kazanç, saatlik, süre) yapar |
| `/kazanc fiyat` | Fiyatları siteden hemen yeniden alır |
| `/kazanc kayit` | Kaydı açar / kapatır: mod okuduğu mesajları dosyaya yazar (aşağıya bak) |
| `/kazanc uyari` | Bu session'daki uyarıları sohbete yazar |
| `/kazanc anahtar` | Corpse anahtarlarını kazançtan düşmeyi açar / kapatır (varsayılan kapalı) |
| `/kazanc api <anahtar>` | Hypixel API anahtarını kaydeder (sadece bu bilgisayarda, `config/mining-profit-calc.json`); `/kazanc api sil` siler |
| `/kazanc kontrol` | Sack sayılarını Hypixel API ile karşılaştırır (aşağıya bak) |
| `/kazanc bosta [sn]` | Kaç saniye kazanç gelmezse sürenin duracağını gösterir / ayarlar (varsayılan 90, 0 = hiç durmasın) |

Kutu session çalışırken görünür. T ile sohbeti açınca kutuyu fareyle basılı tutup sürükleyebilir, sağ alt köşesinden çekerek büyütüp küçültebilir, üstteki **Session** / **Toplam** yazısına tıklayarak sekme değiştirebilirsin. Sağ üstteki **[+]** / **[-]** kutuyu büyütür ya da küçültür.

Büyük kutuda kazanç başlıklara ayrılır: Gemstone, Dwarven Metal (Mithril, Titanium, Tungsten, Umber), Corpse Loot, Fiesta (Glossy Gemstone, Refined Mineral), Diğer (Glacite, Hard Stone, Suspicious Scrap). En çok kazandıran üstte, kazancı olmayan başlık görünmez. Başlığa tıklayınca açılır ve neyden kaç tane geldiği yazar (ör. `Ruby: 1.200 Rough, 15 Flawed · 45,0k`). Session sekmesi bu session'ı, Toplam sekmesi tüm zamanları gösterir. Açık başlıklar ve kutu boyu oyun kapanınca da hatırlanır.

## Neyi sayar

- **Sack mesajları** (`[Sacks] +1,234 items`): sitedeki Mining sekmesinde olan malzemeler (Tungsten, Umber, Glacite, Mithril, Titanium, Hard Stone, Refined Mineral, Glossy Gemstone, Suspicious Scrap) ve gemstone'lar. Sack'ten bir şey çıkarmak eksi yazmaz.
- **Envanterden sack'e koyulanlar sayılmaz** (mod envanteri izler; envanterden az önce çıkan item sack'e girince kazanç değildir). Envantere düşüp oradan sack'e giden mining itemleri de bu yüzden sayılmaz.
- **Stash'ten gelenler sayılır:** `/pickupstash` ile envantere gelen itemler (envanterde craftlansalar bile) sack'e koyunca kazanç sayılır.
- **Craftlamak kazancı değiştirmez:** sack'teki itemleri `/craft` ile craftlamak (ör. Glacite → Enchanted Glacite) ya da envanterde craftlayıp sack'e koymak kazanç eklemez, kazılanlardan da düşmez.
- **Frozen corpse loot'u**: ödül listesindeki fiyatı olan her item, envantere düşenler dahil. Corpse'tan gelip sack'e giren itemler iki kez sayılmaz.
- Compact'tan düşen Enchanted itemler ve Pristine'den düşen Flawed gemler sack'e girince kendi değerleriyle sayılır (sack doluyken envantere düşenler sayılmaz).
- **Sack menüsünden koyulan/alınanlar sayılmaz:** bir Sack menüsü açıkken ve kapandıktan 10 saniye sonrasına kadar gelen sack değişimleri kazanç değildir (stash'ten gelenler yine sayılır).
- **`/craft` ile yapılanlar sayılmaz:** "You Supercrafted ..." mesajından sonraki 30 saniyede gelen ilk sack mesajında o item kazanç sayılmaz.
- **Boşta süre durur:** 90 saniye (`/kazanc bosta` ile değişir) hiç kazanç gelmezse süre durur, kutuda "⏸ Boşta, süre durdu" yazar; ilk kazançta kendiliğinden devam eder. Saatlik sadece aktif süreye göre hesaplanır.
- **Anahtarlar:** varsayılan olarak düşülmez. `/kazanc anahtar` açıkken kullanılan Tungsten/Umber/Skeleton Key'ler (kullanıldıkları andaki değerle) kazançtan düşülür; büyük kutuda Corpse Loot altında "Anahtar: ..." satırı çıkar. Resourceful perk'i anahtarı kurtarırsa düşülmez. Kapatınca toplam yine düşülmemiş haline döner. Diğer harcamalar düşülmez.

## Değerler nereden geliyor

Mod son indirdiği değerleri kaydeder; oyun açılınca önce onları kullanır, yenileri gelince onlara geçer. Kutuda eski fiyat kullanılıyorsa "⚠ Fiyatlar güncel değil" notu çıkar. Değerler [skyblock-forge-flip](https://mitocet.github.io/skyblock-forge-flip/) sitesinin fiyat toplayıcısından gelir (`mod-values.json`, 15 dakikada bir yenilenir). Mining malzemeleri sitedeki en kârlı yolla (ör. Tungsten → Plate, Glacite → Amalgamation, gemlerde Fine ya da Flawless) değerlenir; diğer itemler Bazaar sell offer ya da AH lowest BIN, vergi düşülmüş. Sitenin varsayılan ayarları kullanılır.

## Uyarılar ve kayıt

Mod bir mesajı okuyamazsa ya da bir şey yanlış görünürse sessizce saymaz; kutuda **⚠ Emin değilim: N uyarı** yazar, `/kazanc uyari` ne olduğunu söyler. Uyarı verenler:

- Sack mesajında hiçbir satır okunamadı (Hypixel biçimi değişmiş olabilir).
- Aynı sack mesajı 5 saniye içinde iki kez geldi (çift sayım riski).
- Sack listesi "and N other items" ile kısa kesilmiş (görünmeyen itemler sayılamaz).
- Mining'e benzeyen ama değer listesinde olmayan bir item.
- Corpse loot başlığı geldi ama ödül satırı okunamadı.

Uyarı veren mesaj ham haliyle (hover dahil) `config/mining-profit-calc-debug.log` dosyasına yazılır. `/kazanc kayit` açıkken her sack mesajı, corpse satırı, envanterdeki mining itemleri ve modun her biri için saydığı coin de yazılır (dosya 5 MB'ı geçince `.old` olur). Bu dosya, sayım yanlışsa nedenini görmek ve gerçek mesajlarla test yazmak için kullanılır: `src/test/resources/replay/` klasörüne konan kayıtlar her derlemede baştan oynatılır (bkz. oradaki README).

## API ile sack kontrolü

Modun sack mesajlarını doğru okuyup okumadığını Hypixel API'deki gerçek sack sayılarıyla karşılaştırır. Kullanmak için [developer.hypixel.net](https://developer.hypixel.net/) anahtarını `/kazanc api <anahtar>` ile gir (Development Key 3 günde biter; bitince mod "anahtar geçersiz" der). Anahtar sadece bu bilgisayardaki ayar dosyasında durur. Oyunda SkyBlock API ayarlarında Inventory/Sacks API açık olmalı.

- `/kazanc baslat`: API'den başlangıç sack sayıları alınır.
- O andan sonra mining itemlerinin her sack mesajı satırı (+ ve −, kazanç kurallarından bağımsız) toplanır, session dursa bile.
- `/kazanc bitir` ya da `/kazanc kontrol`: API'deki değişim (şimdi − başlangıç) modun okuduğuyla item item karşılaştırılır. Tutmayan itemler sohbete yazılır (`API +1.000, mod +500, fark +500`) ve kutuda ⚠ uyarısı çıkar. Kazanç kendiliğinden düzeltilmez.
- Hypixel sack verisini birkaç dakika geç kaydeder. API hiç değişmemişse "henüz güncellemedi" der; 2-3 dakika sonra `/kazanc kontrol` ile tekrar bakılır. Başlangıç sayısı da eski olabileceği için tek seferlik bir fark gecikmeden kaynaklanabilir; tekrar bakınca da kalan fark, kaçan ya da iki kez okunan mesaj demektir.
- Sack doluyken envantere düşenler ne sack'e ne mesaja girer, karşılaştırmayı bozmaz. Mesajsız sack değişimleri (ör. Bazaar'a sack'ten satış, mesaj gelmiyorsa) fark olarak görünür.

SkyHanni API anahtarı kullanmıyor (sack sayısını sadece mesajlardan okuyor), bu kontrol bu moda özgü. İstek oyun sunucusuna değil api.hypixel.net'e gider ve sadece bu komutlarda yapılır.

## Teşekkür

Mesaj biçimleri (Supercraft, corpse türü, Resourceful) ve sack menüsü / boşta durma kuralları [SkyHanni](https://github.com/hannibal002/SkyHanni)'den (LGPL-2.1) alındı.

## Derleme

GitHub Actions her push'ta derler; `main` dalındaki her derleme Releases'a eklenir. Yerelde: `./gradlew build` (Java 25).

## Bilinen sınırlar

- Envanterden sandığa/Bazaar'a giden bir mining itemi, 1 dakika içinde aynı itemden kazılanların o kadarını saymayabilir.
- Sack'ten forge'a ya da Bazaar'a giden itemler, aynı ailenin daha üst tier'inden (ör. Glacite giderken Compact'tan düşen Enchanted Glacite) 1 dakika içinde gelenleri o kadar düşürebilir; kazılan aynı ya da alt tier itemler etkilenmez.
- Sack ve corpse mesajlarının biçimi oyunda denenerek doğrulanmadı; Hypixel biçimi farklıysa kutuda uyarı çıkar. Sack mesajı biçimi SkyHanni'nin okuduğu biçimle aynı (`+1,234 Item (Sack)` satırları, "Added"/"Removed" hover'ları).
