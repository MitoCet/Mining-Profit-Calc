# Replay testleri

Buradaki her `.jsonl` dosyası modun `/kazanc kayit` ile yazdığı `config/mining-profit-calc-debug.log` biçimindedir.
`ReplayTest` her dosyayı yeni bir Tracker'a baştan oynatır: her `sack` ve `chat` satırı `coins` kadar
(ya da elle yazılmış `expect` kadar) saymalıdır.

Oyundan gelen gerçek bir kayıtta mod yanlış saydıysa: kaydı buraya koy, yanlış satırlara doğru değeri
`"expect": ...` olarak yaz, test kırmızı olur; kodu test yeşil olana kadar düzelt. Böylece o hata bir daha
geri gelemez. `//` ile başlayan satırlar açıklamadır.

`ornek-*.jsonl` dosyaları elle yazıldı (gerçek kayıt değil); gerçek kayıtlar geldikçe yanlarına eklenir.
