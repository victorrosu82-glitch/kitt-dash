# Android Auto porneste serviciul nostru dupa numele scris in manifest, nu
# printr-un apel din cod. R8 nu are de unde sa stie asta si l-ar sterge sau
# redenumi, iar bordul n-ar mai aparea pe ecranul masinii - fara nicio
# eroare, pur si simplu n-ar fi acolo.
-keep class ro.vix.kittdash.masina.KittCarAppService { *; }
-keep class ro.vix.kittdash.masina.KittSession { *; }
-keep class ro.vix.kittdash.masina.EcranMasina { *; }

# La fel si serviciul de notificari, pornit de Android dupa nume.
-keep class ro.vix.kittdash.muzica.AscultatorNotificari { *; }

# Si serviciul OBD, pornit prin Intent cu numele clasei.
-keep class ro.vix.kittdash.obd.ObdService { *; }

# Car App Library tine sabloanele si le citeste prin reflexie.
-keep class androidx.car.app.** { *; }
-dontwarn androidx.car.app.**

# Cutia neagra: numele firului si urma caderii ajung in fisier, deci vrem
# sa se poata citi. Fara numere de linie, o urma de cadere nu spune mare lucru.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute Sursa
