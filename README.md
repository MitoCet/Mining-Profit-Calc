# Mining Profit Calc

Hypixel SkyBlock için Minecraft **26.2 Fabric** modu. Mining yaparken kazandığın parayı sayar ve ekranda küçük bir kutuda gösterir. Sadece sohbet mesajlarını okur ve bilgi gösterir; oyunda hiçbir şeyi kendisi yapmaz.

## Kurulum

1. [Fabric Loader](https://fabricmc.net/use/) (26.2) ve [Fabric API](https://modrinth.com/mod/fabric-api) kurulu olsun.
2. [Releases](https://github.com/MitoCet/Mining-Profit-Calc/releases) sayfasından en yeni `mining-profit-calc-1.0.X.jar` dosyasını indir (X = release numarası; eski sürümün jar'ını mods klasöründen sil).
3. Dosyayı `.minecraft/mods` klasörüne at.

Sonraki sürümler için elle indirmek gerekmez: oyunda `/kazanc update` yaz. Yeni sürüm varsa sohbette "İndirilsin mi? [Evet] [Hayır]" diye sorar; sormadan indirmez. [Evet]'e basınca mod en yeni jar'ı Releases'tan indirir (boyutu ve sha256'sı kontrol edilir) ve önce `mining-profit-calc-update/` klasöründe bekletir, çünkü Windows çalışan jar'ı silmeye izin vermez. Oyunu kapatınca küçük bir yardımcı işlem oyunun kapanmasını bekler, eski jar'ı siler ve yenisini mods klasörüne koyar; oyunu tekrar açınca yeni sürüm çalışır. Eski jar silinemezse yenisi konmaz (mod iki kez yüklenip oyun açılmamazlık etmesin), bir sonraki kapanışta tekrar denenir; ne olduğu `mining-profit-calc-update/update.log` dosyasında yazar. Oyunu açıp SkyBlock'a ilk girince yeni sürüm varsa aynı soru sorulur ([Evet] deyince indirilir); `/kazanc update bildirim` bu soruyu açar/kapatır.

## Kullanım

| Komut | Ne yapar |
| --- | --- |
| `/kazanc help`, `/kazanc yardim` | Bütün komutları Türkçe açıklamalarıyla listeler; komutun üstüne gelince ayrıntısı çıkar, tıklayınca komut sohbete yazılır |
| `/kazanc baslat` | Yeni session başlatır (session sıfırlanır) |
| `/kazanc dur` | Durdurur ve özeti sohbete yazar |
| `/kazanc bitir` | Session'ı bitirir: durdurur, özeti sohbete yazar, geçmişe kaydeder ve session'ı sıfırlar. Session açıkken oyunu kapatınca (ya da oyun çökünce) aynısı kendiliğinden olur |
| `/kazanc devam` | Durdurulan session'a devam eder |
| `/kazanc` | **Ayar menüsü**: her komut ve ayar bir buton (aşağıya bak) |
| `/kazanc ozet` | Session ve toplam özetini sohbete yazar |
| `/kazanc sekme` | Kutuda Session / Toplam arasında geçiş |
| `/kazanc gecmis` | Session geçmişi menüsü (aşağıya bak) |
| `/kazanc sifirla` | Onay sorup bütün kayıtlı sessionları siler, Toplam sıfırlanır (süren session kalır) |
| `/kazanc goster`, `/kazanc gizle` | Session durmuşken de kutuyu gösterir / gizler (çalışırken kutu hep görünür) |
| `/kazanc buyuk`, `/kazanc kucuk` | Kutuyu büyük (başlıklara ayrılmış kazanç) ya da küçük (kazanç, saatlik, süre) yapar |
| `/kazanc fiyat` | Fiyatları siteden hemen yeniden alır; bitince "Fiyatlar yenilendi" (item sayısı, verinin yaşı) ya da hata yazar |
| `/kazanc kayit` | Kaydı açar / kapatır: mod okuduğu mesajları dosyaya yazar (aşağıya bak) |
| `/kazanc uyari` | Bu session'daki uyarıları sohbete yazar |
| `/kazanc anahtar` | Anahtarlı corpse'ları (Tungsten/Umber/Vanguard) sayıp anahtarlarını düşmeyi açar / kapatır (varsayılan kapalı: sadece Lapis corpse'ları sayılır); `/kazanc gecmis`'te buton olarak da var |
| `/kazanc api <anahtar>` | Hypixel API anahtarını kaydeder (sadece bu bilgisayarda, `config/mining-profit-calc.json`); `/kazanc api sil` siler |
| `/kazanc kontrol` | Sack sayılarını Hypixel API ile karşılaştırır (aşağıya bak) |
| `/kazanc update` | Yeni sürüm varsa indirilsin mi diye sorar, [Evet]'e basınca indirir; oyunu kapatıp açınca yeni sürüm yüklenir (eski jar kendiliğinden silinir). `/kazanc update bildirim`: SkyBlock'a ilk girişte yeni sürüm haberini açar/kapatır |
| `/kazanc bosta [sn]` | Kaç saniye kazanç gelmezse sürenin duracağını gösterir / ayarlar (varsayılan 90, 0 = hiç durmasın) |

Kutu session çalışırken görünür. T ile sohbeti açınca kutuyu fareyle basılı tutup sürükleyebilir, sağ alt köşesinden çekerek büyütüp küçültebilir, üstteki **Session** / **Toplam** yazısına tıklayarak sekme değiştirebilirsin. Sağ üstteki **[+]** / **[-]** kutuyu büyütür ya da küçültür.

Büyük kutuda kazanç başlıklara ayrılır: Gemstone, Dwarven Metal (Mithril, Titanium, Tungsten, Umber), Corpse Loot, Fiesta (Glossy Gemstone, Refined Mineral), Ore (Coal, Iron, Gold, Diamond, Lapis, Redstone, Emerald, Quartz, Obsidian), Diğer (Glacite, Hard Stone, Suspicious Scrap). En çok kazandıran üstte, kazancı olmayan başlık görünmez. Başlığa tıklayınca açılır ve neyden kaç tane geldiği yazar (ör. `Ruby: 1.200 Rough, 15 Flawed · 45,0k`). Session sekmesi bu session'ı, Toplam sekmesi tüm zamanları gösterir. Açık başlıklar ve kutu boyu oyun kapanınca da hatırlanır.

### Ayar menüsü (`/kazanc`)

`/kazanc` yazınca (ya da atadığın tuşa basınca) bir menü açılır: üstte Session ve Toplam özeti, altında her komutun ve ayarın butonu. Session: Başlat, Durdur/Devam, Bitir, Geçmiş, Sack kontrolü, Uyarılar, Özeti sohbete yaz. Kutu ve sayım: kutu boyu, sekme, durmuşken kutu, Anahtarlı corpse, Oto düzelt, Boşta durma (−/+ 30 sn). API ve diğer: API anahtarı kutusu (yapıştır, Kaydet / Sil), Fiyatları yenile, Güncelle, Güncelleme haberi, Hata kaydı, Yardım; en altta Hepsini sil ve Kapat. Butonlar aynı `/kazanc` komutlarını çalıştırır, cevapları sohbete yazılır; fareyi butonun üstünde tutunca ne yaptığı çıkar. Komutlar da aynen çalışır.

Kısayol tuşu: Ayarlar → Kontroller → Tuş atamaları → Mining Profit Calc → **Kazanç menüsü** (varsayılan atanmamış).

### Session geçmişi (`/kazanc gecmis`)

Biten her session (`/kazanc bitir` ya da oyunu kapatınca) kaydedilir; kazancı olmayan session kaydedilmez. **Toplam = kayıtlı sessionlar + süren session.** Menüde solda sessionlar (en yeni üstte, süren session en üstte; tarih, süre, kazanç, saatlik, sack kontrolü durumu), sağda seçilenin ayrıntısı: neyden ne kadar kazanıldığı ve API sack kontrolü (mod ne saydı, API'ye göre gerçek, fark: Ne kazandım başlıklarına (Gemstone, Dwarven Metal, Diğer, Ore…) ve ailelere ayrılmış, her başlığın toplamı ve her ailenin net farkı yanında, itemleri altında). Her session'ın son kontrol sonucu onunla birlikte saklanır.

- **Düzelt**: kontrolün bulduğu eksikleri o session'a ve Toplam'a ekler (eski sessionlarda da çalışır). Fark ve Düzelt aile bazında net hesaplanır (Glacite + Enchanted Glacite, Rough/Flawed/Fine aynı gem): kaybolan bir Supercraft mesajı "Glacite fazla, Enchanted Glacite eksik" gösterir, ikisi birbirinden düşülür. Değeri düşen aile eklenmez, kazançtan da düşülmez; menüde o aile "mod fazla · çıkarılmaz" yazar (sack API envantere düşenleri görmez, eksi fark çoğu zaman modun yanlış saydığı anlamına gelmez). Yukarıdaki Fark ile Düzelt tutarı hep aynıdır.
- Corpse loot'u envanterde kalırsa (sack doluysa ya da sack'in almadığı Flawless gem gibi) sack kontrolünde fark sayılmaz.
- **Kontrol et**: süren session'ı ya da (yeni session başlamadan) son biten session'ı API ile tekrar karşılaştırır; Hypixel eski sack sayılarını vermediği için daha eskiler tekrar kontrol edilemez, kayıtlı sonuçları görünür.
- **Anahtarlı corpse: Sayılıyor/Sayılmıyor** (listenin altında): `/kazanc anahtar` ile aynı ayar; basınca menüdeki tutarlar, kutu ve Toplam hemen değişir (geriye dönük).
- **Sil**: onay sorar, session'ı siler ve Toplam'dan çıkarır. **Hepsini sil** = `/kazanc sifirla`.

## Neyi sayar

- **Sack mesajları** (`[Sacks] +1,234 items`): sitedeki Mining sekmesinde olan malzemeler (Tungsten, Umber, Glacite, Mithril, Titanium, Hard Stone, Refined Mineral, Glossy Gemstone, Suspicious Scrap), gemstone'lar ve ore'lar (Coal, Iron, Gold, Diamond, Lapis, Redstone, Emerald, Quartz, Obsidian; ham, Block, Enchanted, Enchanted Block). Sack'ten bir şey çıkarmak eksi yazmaz.
- **Envantere düşenler geldiği anda sayılır** (sack doluyken, `/pickupstash` vb.; Mito, 2026-11-13). Mod envanteri saniyede ~2 kez okur. Sonra envanterde craftlamak (ör. Coal → Enchanted Coal), ürünün direkt sack'e gitmesi ya da elle sack'e koymak tekrar saymaz; satmak ya da atmak düşürmez. Sayılmayanlar: bir menü açıkken (Bazaar, sack, storage, craft…) ve kapandıktan 1 sn sonrasına kadar gelenler, dünya/sunucu değişiminden sonraki 3 sn, "Moved … from your Sacks to your inventory" (`/gfs`) ile gelenler (kurallar SkyHanni'nin OwnInventoryData'sından, LGPL-2.1). Envantere gelip sonra sack mesajında sack'ten çıktığı görülen itemler kazançtan geri düşülür.
- **Ore değeri:** sadece Enchanted Block satışı (Obsidian'da Enchanted Obsidian), ham adet başına payı; her biçim kaç ham ettiyse o kadar değerlidir.
- **Craftlamak kazancı değiştirmez:** sack'teki itemleri `/craft` (Supercraft) ile craftlamak (ör. Glacite → Enchanted Glacite), envanterde craftlamak ya da oto craft (ürün Hypixel tarafından direkt sack'e gider) kazanç eklemez, kazılanlardan da düşmez. Envanterden az önce çıkan alt biçimler (ör. 160 Coal), sack'e giren üst biçimle (1 Enchanted Coal) eşleşir.
- **Frozen corpse loot'u**: ödül listesindeki fiyatı olan her item, envantere düşenler dahil (anahtarlı corpse'lar sadece `/kazanc anahtar` açıkken, aşağıya bak). Corpse'tan gelip sack'e giren itemler iki kez sayılmaz.
- Compact'tan düşen Enchanted itemler ve Pristine'den düşen Flawed gemler kendi değerleriyle sayılır (sack'e de envantere de düşse).
- **Sack menüsünden koyulan/alınanlar sayılmaz:** bir Sack menüsü açıkken ve kapandıktan 10 saniye sonrasına kadar gelen sack değişimleri kazanç değildir.
- **`/craft` ile yapılanlar sayılmaz:** "You Supercrafted ..." mesajından sonraki 30 saniyede gelen ilk sack mesajında o item kazanç sayılmaz.
- **Boşta süre durur:** 90 saniye (`/kazanc bosta` ile değişir) hiç kazanç gelmezse süre durur, kutuda "⏸ Boşta, süre durdu" yazar; ilk kazançta kendiliğinden devam eder. Saatlik sadece aktif süreye göre hesaplanır.
- **Corpse'lar ve anahtarlar:** `/kazanc anahtar` kapalıyken (varsayılan) sadece Lapis corpse loot'u sayılır (anahtar istemez); Tungsten/Umber/Vanguard corpse loot'u kazanca girmez (sack'e girse de sayılmaz). Açıkken bütün corpse loot'u sayılır ve kullanılan Tungsten/Umber/Skeleton Key'ler (kullanıldıkları andaki değerle) kazançtan düşülür; büyük kutuda Corpse Loot altında "Anahtar: ..." satırı çıkar. Resourceful perk'i anahtarı kurtarırsa anahtar düşülmez ama loot yine sayılır. Ayar geriye dönüktür: açıp kapatınca toplam da değişir. Bu değişiklikten önce sayılan corpse loot'u türünü bilmediği için hep sayılı kalır. Diğer harcamalar düşülmez.

## Değerler nereden geliyor

Mod son indirdiği değerleri kaydeder; oyun açılınca önce onları kullanır, yenileri gelince onlara geçer. Kutuda eski fiyat kullanılıyorsa "⚠ Fiyatlar güncel değil" notu çıkar. Değerler [skyblock-forge-flip](https://mitocet.github.io/skyblock-forge-flip/) sitesinin fiyat toplayıcısından gelir (`mod-values.json`, 15 dakikada bir yenilenir). Mining malzemeleri sitedeki en kârlı yolla (ör. Tungsten → Plate, Glacite → Amalgamation, gemlerde Fine ya da Flawless) değerlenir; diğer itemler Bazaar sell offer ya da AH lowest BIN, vergi düşülmüş. Sitenin varsayılan ayarları kullanılır.

## Uyarılar ve kayıt

Mod bir mesajı okuyamazsa ya da bir şey yanlış görünürse sessizce saymaz; kutuda **⚠ Emin değilim: N uyarı** yazar, `/kazanc uyari` ne olduğunu söyler. Uyarı verenler:

- Sack mesajında hiçbir satır okunamadı (Hypixel biçimi değişmiş olabilir).
- Aynı sack mesajı 5 saniye içinde iki kez geldi (çift sayım riski).
- Sack listesi "+N other items" ile kısa kesilmiş ve gizli kısım az önceki corpse loot'u değil. Gizli N item önce corpse mesajından zaten sayılan itemlerle eşleştirilir (Hypixel corpse'tan gelenleri genelde listenin sonuna, gizli kısma koyuyor); onlarla kapanıyorsa uyarı çıkmaz. Kalan varsa `/kazanc kontrol` API'den bulur (`/kazanc gecmis` → Oto düzelt açıksa ~3 dk sonra kendiliğinden bakar).
- Mining'e benzeyen ama değer listesinde olmayan bir item.
- Corpse loot başlığı geldi ama ödül satırı okunamadı.

Uyarı veren mesaj ham haliyle (hover dahil) `config/mining-profit-calc-debug.log` dosyasına yazılır. `/kazanc kayit` açıkken her sack mesajı, corpse satırı, envanterdeki mining itemleri ve modun her biri için saydığı coin de yazılır (oyun her açılışta yeni dosya başlar, önceki oyunun kaydı `.old` olarak kalır; oyun açıkken dosya 5 MB'ı geçerse de `.old` olur). Bu dosya, sayım yanlışsa nedenini görmek ve gerçek mesajlarla test yazmak için kullanılır: `src/test/resources/replay/` klasörüne konan kayıtlar her derlemede baştan oynatılır (bkz. oradaki README).

## API ile sack kontrolü

Modun sack mesajlarını doğru okuyup okumadığını Hypixel API'deki gerçek sack sayılarıyla karşılaştırır. Kullanmak için [developer.hypixel.net](https://developer.hypixel.net/) anahtarını `/kazanc api <anahtar>` ile gir (Development Key 3 günde biter). SkyBlock'a her girişte (oyunu açınca ilk giriş, sonra lobiden ya da başka oyundan; ada değiştirmek sayılmaz, 5 dk'da en fazla bir kez) mod anahtarın hâlâ çalışıp çalışmadığına bakar: bitmişse ya da geçersizse sohbette kırmızı uyarı verir, kaydedildikten 3 gün dolmasına 12 saatten az kaldıysa oyun başına bir kez sarı uyarı verir; çalışıyorsa bir şey yazmaz. Anahtar sadece bu bilgisayardaki ayar dosyasında durur. Oyunda SkyBlock API ayarlarında Inventory/Sacks API açık olmalı.

- `/kazanc baslat`: API'den başlangıç sack sayıları alınır.
- O andan sonra mining itemlerinin her sack mesajı satırı (+ ve −, kazanç kurallarından bağımsız) toplanır, session dursa bile.
- `/kazanc bitir` ya da `/kazanc kontrol`: API'deki değişim (şimdi − başlangıç) modun okuduğuyla item item karşılaştırılır. Tutmayan itemler sohbete yazılır (`Tungsten: gerçek +1.000, mod +500 (fark +500, +53.3k)`), altında "Mod: X → API'ye göre gerçek: ~Y" satırı çıkar ve kutuda ⚠ uyarısı (`/kazanc uyari`) verilir. Aynı fark tekrar çıkarsa tek satır yazılır, yeni uyarı eklenmez. `/kazanc bitir`'den sonraki kontroller bitmiş session'ın kazancını kullanır. Corpse loot'undan sayılan mining itemleri, sack mesajı gelmese de okunmuş sayılır.
- Oyun kapanınca: açık session bitirilir, sack kontrolü için gereken her şey ayar dosyasında kalır. Sonraki açışta SkyBlock'a ilk girince (lobide değil) özet sohbete yazılır ve API anahtarı varsa önceki session'ın sack kontrolü kendiliğinden yapılır (Hypixel henüz güncellememişse 3 dk arayla 3 kez daha denenir), sonra yeni başlangıç sayıları alınır. Yeni session kendiliğinden başlamaz, `/kazanc baslat` gerekir. Önceki session'da eksik çıkanlar `/kazanc gecmis`'te Düzelt ile o session'a ve Toplam'a eklenir.
- Düzelt (`/kazanc gecmis` menüsünde; ayrı komut yok): kontrolde eksik çıkan itemler (aile bazında net, pozitif fark) kazanca eklenir; session bittiyse geçmişteki o session'a ve Toplam'a. Mineshaft'tan ya da sunucudan son sack mesajı gelmeden çıkınca Hypixel o mesajı göndermiyor, Düzelt onu telafi eder. Tekrar kontrol edince eklenecek miktar yeni sonuçla değişir, önceden eklenen tekrar eklenmez. Menüdeki Oto düzelt açıkken: eksikler kendiliğinden eklenir, sunucu değişiminden, `/kazanc bitir`'den ve kısa kesilmiş bir sack listesinden ~3 dk sonra kontrol kendiliğinden yapılır. Varsayılan kapalı.
- Elle konanlar (Mito, 2026-11-14): sack menüsü açıkken envanterden çıkan mining itemleri (örn. satın alıp sack'e attıkların) not edilir. Sack mesajı onları gösterirse sorun yok. Mesaj gelmeden sunucu değişirse ya da ~75 sn içinde hiçbir mesaj göstermezse API farkına girmiş olabilirler: kontrolde "⚠ Sack'e elle koydukların" uyarısı item ve sayısıyla çıkar, Fark'ta o ailenin yanında "elle konmuş · tıkla: çıkar" yazar. Tıklayınca sadece elle konan miktar Düzelt'ten çıkar (ailenin kalanı, yani kaybolan mesajlardan gelen gerçek kazanç yine eklenir; aile eksiye düşmez). Tekrar tıklayınca geri alınır. Oto düzelt açıkken elle konanlar kendiliğinden çıkarılır. `/pickupstash` ile stash'ten sack'e geçenlerin miktarı görülemiyor, onlar için sadece uyarı çıkar.
- Hypixel sack verisini birkaç dakika geç kaydeder. API hiç değişmemişse "henüz güncellemedi" der; 2-3 dakika sonra `/kazanc kontrol` ile tekrar bakılır. Başlangıç sayısı da eski olabileceği için tek seferlik bir fark gecikmeden kaynaklanabilir; tekrar bakınca da kalan fark, kaçan ya da iki kez okunan mesaj demektir.
- Sack doluyken envantere düşenler ne sack'e ne mesaja girer, karşılaştırmayı bozmaz. Mesajsız sack değişimleri (ör. Bazaar'a sack'ten satış, mesaj gelmiyorsa) fark olarak görünür.

SkyHanni API anahtarı kullanmıyor (sack sayısını sadece mesajlardan okuyor), bu kontrol bu moda özgü. İstek oyun sunucusuna değil api.hypixel.net'e gider ve sadece bu komutlarda yapılır.

## Teşekkür

Mesaj biçimleri (Supercraft, corpse türü, Resourceful) ve sack menüsü / boşta durma kuralları [SkyHanni](https://github.com/hannibal002/SkyHanni)'den (LGPL-2.1) alındı.

`/kazanc update`'in jar değiştirme yöntemi (yeni jar'ı bekletip oyun kapanınca ayrı bir işlemle eskisini silmek) SkyHanni ve NEU'nun kullandığı [libautoupdate](https://github.com/nea89o/libautoupdate)'teki fikirle aynı; kod buraya ayrıca yazıldı, kopyalanmadı.

## Derleme

GitHub Actions her push'ta derler; `main` dalındaki her derleme Releases'a eklenir. Main derlemeleri sıraya girer (aynı anda çalışmaz) ve her zaman `main`'in en son halini derler; iki PR saniyeler arayla merge edilse de en yeni release ikisini de içerir, aynı commit iki kez release olmaz. Yerelde: `./gradlew build` (Java 25).

## Bilinen sınırlar

- Envanterden sandığa/Bazaar'a giden bir mining itemi, 1 dakika içinde aynı itemden kazılanların o kadarını saymayabilir.
- Sack'ten forge'a ya da Bazaar'a giden itemler, aynı ailenin daha üst tier'inden (ör. Glacite giderken Compact'tan düşen Enchanted Glacite) 1 dakika içinde gelenleri o kadar düşürebilir; kazılan aynı ya da alt tier itemler etkilenmez.
- Sack ve corpse mesajlarının biçimi oyunda denenerek doğrulanmadı; Hypixel biçimi farklıysa kutuda uyarı çıkar. Sack mesajı biçimi SkyHanni'nin okuduğu biçimle aynı (`+1,234 Item (Sack)` satırları, "Added"/"Removed" hover'ları).
