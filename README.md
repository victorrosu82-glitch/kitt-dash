# KITT Dash

Bord în stilul KITT pentru Lexus NX 300h, pe ecranul mașinii prin Android Auto.
Citește datele prin adaptorul Bluetooth ELM327 (Viecar 1.5). Uz personal, fără Play Store.

Planul pe etape și hotărârile luate: [docs/kitt-dash-plan.md](docs/kitt-dash-plan.md).
Macheta aprobată a bordului: [docs/dashboard-kitt.html](docs/dashboard-kitt.html).

## Unde suntem

**Etapele 1 si 2 — gata, probate pe masina** (14.11.2026, Lexus NX 300h, Samsung S25+ /
Android 17, adaptor „Viecar" prin Bluetooth):

- ELM327 v1.5, protocol ISO 15765-4 (CAN 11/500), **10,5 raspunsuri/s**
- datele standard: viteza, turatie, sarcina, clapeta, temperaturi, tensiune modul
- **datele hibride**, de la calculatorul hibrid (adresa `7E2`): cat a mai ramas din
  bateria HV, tensiunea si curentul ei, puterea cu semn si modul EV / HYBRID / REGEN
- 90 de secunde fara nicio reconectare; tacerile izolate ale adaptorului sunt inghitite
- citirea tine si cu ecranul stins (serviciul in prim-plan)

Masina **nu da** nivelul de combustibil (`2F`) — cum era prevazut la Toyota/Lexus.

### De unde vin datele hibride

Nu din blocurile Toyota (`2102`–`2105` raspund „nu stiu"), ci din doua PID-uri **standard**
pe care masina le tine la calculatorul hibrid:

| Cerere | Ce da | Formula |
|---|---|---|
| `015B` la `7E2` | cat a mai ramas din bateria HV | `A · 100 / 255` |
| `019A` la `7E2` | stare, tensiune, curent | `C-D / 64` volti, `E-F / 10` amperi, cu semn |

Puterea bateriei iese din tensiune ori curent. **Semnul conteaza**: plus inseamna ca
bateria da curent (tractiune), minus ca primeste (incarcare sau recuperare la franare).
Bordul KITT asta deseneaza, tractiunea in sus si recuperarea in jos.

### Finisaje (etapa 5)

- **se conecteaza singur** la adaptorul de data trecuta, si pe telefon, si pe
  ecranul masinii. Alegi adaptorul o data, din lista; dupa aceea urci si merge;
- **zi si noapte**: noaptea bordul trage o perdea subtire peste tot desenul,
  ca sa nu bata in parbriz. Pe masina se ia dupa ce spune Android Auto, care
  are senzor de lumina; pe telefon, dupa tema sistemului;
- **avertizari**, una singura si cea mai grava, sub ceas: motor fierbinte
  (peste 105 °C), tensiune de bord mica (sub 11,8 V), bateria HV scazuta (sub
  35 %) si adaptorul care a amutit fara sa pice legatura - se intampla la
  clonele ELM327. Cele grave clipesc rar;
- **temperatura de afara** si **melodia care canta**, trecand o data ca o banda
  prin locul modulatorului de voce.

### APK-ul de predat

```
gradlew.bat assembleRelease
```

apoi, din `build-tools.0.0`, `zipalign` si `apksigner` (vezi mai sus).
Rezultatul: **2,04 MB**, semnat.

Minificarea cere grija: Android Auto porneste serviciul dupa numele scris in
manifest, nu printr-un apel din cod, deci R8 l-ar sterge fara sa spuna nimic -
bordul pur si simplu n-ar aparea pe ecranul masinii. Regulile din
`app/proguard-rules.pro` il tin pe loc, impreuna cu serviciul OBD si cel de
notificari.

Varianta de predat **nu are** butonul Simulator de pe ecranul masinii.

### Lista de gazde

Android Auto-ul din telefonul acesta are **doua** amprente, fiindca si-a
schimbat cheia pe parcurs: `fdb00c43...` (cea veche) si `1ca8dcc0...` (cea de
azi). Biblioteca le cere pe toate de la Android si accepta daca **macar una**
e in lista - iar `fdb00c43` era deja in lista oficiala.

Asta inseamna ca lista **nu** a fost pricina disparitiei bordului de pe
ecranul Lexusului, cum am crezut la inceput. Lista noastra din
`app/src/main/res/values/gazde.xml` ramane fiindca e mai stransa la viitor
(cand cheia veche o sa iasa din lista oficiala, noi o avem si pe cea noua),
dar nu ea a reparat nimic.

Amprenta se masoara asa, nu se scrie din memorie:

```
adb shell pm path com.google.android.projection.gearhead
adb pull <calea de mai sus>/base.apk gearhead.apk
apksigner verify  (cu "print-certs")  gearhead.apk
```

### Cutia neagra

Jurnalul telefonului se sterge la repornire. Cand bordul a disparut de pe
ecranul Lexusului, pana sa ajunga telefonul la calculator trei reporniri
stersesera tot, si nu mai stim nici azi de ce a disparut.

De aceea aplicatia isi scrie singura un jurnal, care tine peste reporniri:

```
adb pull /sdcard/Android/data/ro.vix.kittdash/files/jurnal.txt
```

sau, de pe telefon, cu Fisiere: *Android > data > ro.vix.kittdash > files*.

Scrie clipele care conteaza daca bordul nu apare: cand il cheama Android Auto,
ce amprenta are gazda si daca e in lista, ce suprafata primim si cat din ea ne
lasa libera masina, cand pierdem suprafata - si **orice cadere, cu urma ei**.
Urma e citibila si din APK-ul minificat (`proguard-rules.pro` tine numerele de
linie): asa s-a aflat, intr-o clipa, ca `NavigationTemplate` nu merge fara
banda de butoane.

### Cat ecran ne da masina

Pe unitatea principala: panza **800x400**, din care liber ne ramane
**776x300** la (12,88). Fasia de sus e banda de butoane a sablonului, si nu se
poate lua inapoi - fara `setActionStrip`, `NavigationTemplate` crapa la
construire. Zona "libera acum" incepe 776x376 si se strange tot la 776x300
dupa doua secunde, deci nici pe ea nu se castiga nimic, doar o resezare a
bordului in mers.

In zona primita bordul umple acum toata latimea: intinderea maxima a crescut
de la 220 la 360 de puncte din caietul de 1280, cat ii trebuie ca sa ajunga la
margini pe o zona de forma 2,59.

**Pe Lexus** (jurnalul din 15.11.2026, 17:10): panza **1190x460**, cu bara de
aplicatii Android Auto in stanga; ecranul e deci cam 1280x480. Fasia de 88 de
sus e libera cat nu atingi ecranul (Android Auto o raporteaza libera "acum"),
asa ca din 16.11.2026 bordul o foloseste: deseneaza pe toata inaltimea, cu 12
puncte margine, si se potriveste dupa partea desenata a caietului
(`SUS_CONTINUT`..`JOS_CONTINUT`), nu dupa tot caietul - iese cu o treime mai
mare. Cand atingi ecranul, butoanele sablonului apar o clipa peste coltul din
dreapta-sus. Scanerul e mai lat (creste cu intinderea), mai inalt si are halou.

Android Auto mai da si alte zone, dupa ce e pe ecran langa noi: **746x360**
in ecranul impartit (langa Spotify) si **386x412 la (792,24)** cat e deschisa
lista de aplicatii. Bordul se aseaza in fiecare.

### Emulatorul pe ecranul Lexusului

`C:\Users\vix\Proiecte\dhu\config\lexus_nx.ini` (1280x720 cu 240 margine) da
exact panza de 1190x460 a Lexusului:

```
.\desktop-head-unit.exe -c config\lexus_nx.ini
```

Capcane aflate pe 16.11.2026:
- emulatorul citeste comenzi din consola; daca intrarea se inchide, iese si
  lasa Android Auto blocat cu "Already connected". Se porneste cu intrarea
  tinuta deschisa (`tail -f fisier | desktop-head-unit.exe`), iar prin fisier
  se pot trimite comenzi ca `tap 40 283`;
- dupa o iesire urata: `adb shell am force-stop com.google.android.projection.gearhead`
  si apesi din nou butonul serverului pe telefon;
- **nu instala APK-ul cat e conectat**: procesul nou nu mai poate desena pe
  suprafata ("Nu pot desena pe panza" in jurnal) pana la reconectare;
- in lista de aplicatii de pe *telefon* ("Personalizati lansatorul") KITT nu
  apare - fara masina, Android Auto nu poate verifica aplicatiile instalate
  direct. Pe ecranul masinii apare. Instalarea se face cu
  `adb install -r -i com.android.vending KittDash.apk`.

### Instructiunile de navigatie (Waze, Google Maps)

Cand KITT ia locul lui Waze pe ecranul masinii, Waze merge mai departe pe
telefon si tine o notificare pe canalul "Waze Navigation Instructions"
(categoria `navigation`): titlul e distanta ("70 m"), textul e strada, imaginea
mare e sageata manevrei (36x36). Cat Waze e el insusi pe ecranul masinii,
notificarea lipseste. Ora sosirii nu e in notificare.

`navigatie/Navigatie.kt` o citeste prin ascultatorul de notificari (acelasi
acces ca la melodie) si bordul deseneaza un panou in dreapta (bordul se strange
la 64% din latime): sageata in ambra cu halou, distanta mare, bara de apropiere,
strada. Sub 50 m sageata clipeste in rosu. In ferestrele aproape patrate ramane
doar panoul. Aspectul: `docs/macheta-waze.html`.

Google Maps tine si o notificare de navigatie fara distanta ("Condu cu Google
Maps", ori doar numele strazii); de aceea se iau numai notificarile in care
titlul sau textul e o distanta. **Notificarile Waze trebuie sa fie pornite** din
setarile telefonului - erau oprite si atunci nu vine nimic.

### Suprafata noua

Android Auto poate da o suprafata noua fara s-o ia pe cea veche (16.11.2026:
doua in aceeasi clipa, apoi bordul inghetat pe ultimul cadru). Desenatorul
opreste acum bucla si o reia pe suprafata noua, iar daca nu poate desena o
secunda la rand scrie o data in jurnal motivul.

### Android Auto

Probat pe emulatorul Desktop Head Unit (15.11.2026, 00:11): bordul se deseneaza
pe ecranul masinii, umple latimea si nu intra sub barele Android Auto, iar
valorile curg. Ramane proba pe ecranul Lexusului.

Ca sa pornesti emulatorul: pe telefon, in **Android Auto > meniu > Setari pentru
dezvoltatori**, apasa **Porniti serverul unitatii principale**. E de unica
folosinta: dupa fiecare incercare trebuie apasat din nou. Apoi:

```
cd C:\Users\vix\Proiecte\dhu
.\adb.exe forward tcp:5277 tcp:5277
.\desktop-head-unit.exe
```

In bara de sus a ecranului de masina, butonul **Simulator** (numai in varianta
de lucru) porneste datele fara masina si fara adaptor.

### Cum au fost verificate formulele

Nicio formula nu e scrisa din memorie. Fiecare are o dovada pe masina asta:

- **semnul curentului**: in timpul probelor a pornit singur motorul termic ca sa incarce
  bateria, si in aceeasi clipa curentul a sarit de la `00 0D` la `FF 89`, adica de la
  +1,3 A la −11,9 A. Simultan tensiunea a urcat si procentul bateriei la fel — trei
  lucruri potrivindu-se deodata;
- **scara tensiunii** (1/64 V): confruntata cu aplicatia Dr Prius, care citea aceeasi
  masina in aceleasi minute si arata 274,87–283,51 V (suma celor 17 blocuri). Octetii
  nostri impartiti la 64 dau 266–285 V. Impartirea la 100, cum incepusem, dadea 174 V;
- **scara curentului** (0,1 A): Dr Prius arata „DISCHARGING 1.3 amp" in toate cele patru
  capturi, iar octetii nostri dau `00 0D` = 1,3 A;
- **procentul bateriei**: acelasi octet se regaseste ca ultimul octet util al blocului
  Toyota `2101`, la doua citiri diferite (`A3` si `9D`);
- **caderea de tensiune sub sarcina**: cand demarorul a tras 19,8 A, tensiunea a cazut la
  266 V si s-a ridicat la loc dupa.

| Etapa | Ce cuprinde | Stare |
|---|---|---|
| 0 | Ce date da masina prin Viecar | **gata** (vezi mai sus) |
| 1 | Conexiunea, datele standard, ecran de test | **gata, probata pe masina** |
| 2 | Datele hibride (baterie HV, kW, mod) | **gata, probata pe masina** |
| 3 | Bordul KITT pe telefon | **gata** |
| 4 | Android Auto | **merge pe Lexus** (15.11.2026) si pe emulatorul cu ecranul Lexusului |
| 5 | Finisaje | **gata** |

## Sonda de pe laptop (etapa 0)

Nu are nevoie de Android, doar de laptop cu Bluetooth, în mașină, cu contactul pe READY.
Întâi împerechează adaptorul: *Setări > Bluetooth > Adaugă dispozitiv*, cod `1234`.
Portul COM apare în *Mai multe setări Bluetooth > Porturi COM*.

```
python tools\sonda_elm327.py
```

Scrie un jurnal în `docs\sonda-<data>.txt` cu tot ce răspunde mașina: ce PID-uri dă,
valorile de acum, câte răspunsuri pe secundă dă adaptorul și — important — dacă adaptorul
execută `ATSH`, fără de care nu se ajunge la datele hibride.

Pentru o probă fără mașină: `python tools\sonda_elm327.py --simulator`.

## Aplicația

```
gradlew.bat test              # testele pe decodare și simulator, fără telefon
gradlew.bat assembleDebug     # APK în app\build\outputs\apk\debug
gradlew.bat installDebug      # pe telefonul legat pe USB
```

APK mic, semnat cu cheia de depanare, de copiat pe telefon:

```
gradlew.bat assembleRelease
```

apoi, din `C:\Users\vix\AppData\Local\Android\Sdk\build-tools\35.0.0`:

```
zipalign -p 4 app-release-unsigned.apk aliniat.apk
apksigner sign --ks %USERPROFILE%\.android\debug.keystore --ks-pass pass:android --ks-key-alias androiddebugkey --key-pass pass:android --out KittDash-etapa1.apk aliniat.apk
```

Pe telefon, butonul **Simulator** pornește bordul pe date inventate, fără mașină și fără
adaptor — util acasă. Dedesubt stau aparatele împerecheate; atingi unul și se leagă la el.
Cele care par adaptoare OBD sunt însemnate, dar **alegerea o faci tu**: prima variantă
ghicea singură și s-a legat la primul aparat în ordine alfabetică, care nu era adaptorul.

## Cum e alcătuit

```
app/src/main/java/ro/vix/kittdash/
  obd/ObdPid.kt              cererile, formulele și mărimile care ies din ele
  obd/ModHibrid.kt           modul EV / HYBRID / REGEN / READY
  obd/SondaHibrid.kt         întreabă calculatoarele hibride, fără să decodeze
  obd/ObdLink.kt             legătura ca text: trimite comandă, citește până la '>'
  obd/BluetoothObdLink.kt      ... peste Bluetooth clasic (RFCOMM/SPP)
  obd/SimulatorObdLink.kt      ... adaptor fals, pentru lucru fără mașină
  obd/Elm327.kt              inițializarea adaptorului și citirea unui PID
  obd/ObdPoller.kt           bucla de interogare, cu reconectare automată
  obd/CarState.kt            starea mașinii, un singur obiect imutabil
  obd/CarStateRepository.kt  singurul loc unde stă starea
  obd/ObdService.kt          serviciu în prim-plan, ține legătura cu ecranul stins
  bord/BordKitt.kt           desenează bordul pe Canvas, în caietul de 1280x720
  bord/LogicaBord.kt         deciziile desenului (câte bare, ce culoare), probate
  bord/StareBord.kt          ce se desenează acum + netezirea între citiri
  bord/EcranBord.kt          bordul pe telefon: pe lat, fără barele Android
  navigatie/InstructiuneNav.kt manevra urmatoare: distanta, strada, sageata, apropierea
  navigatie/Navigatie.kt     citeste notificarea de navigatie (Waze, Google Maps)
  ui/MainActivity.kt         ecranul de test al etapei 1 (valorile brute)
tools/sonda_elm327.py        sonda de pe laptop (etapa 0)
tools/serial_win.py          port serial pe Windows, fără pyserial
tools/fals_elm327.py         ELM fals în Python, pentru proba sondei
```

Desenătorul lucrează pe `android.graphics.Canvas`, nu pe ceva din Compose, tocmai ca la
etapa 4 ecranul mașinii să-l poată folosi neschimbat — primește altă pânză, atât.

## Mediul de build

- JDK 17 (`C:\Users\vix\AppData\Local\Programs\Microsoft\jdk-17.0.10.7-hotspot`), din `JAVA_HOME`.
- Android SDK: `C:\Users\vix\AppData\Local\Android\Sdk`, cu `platforms;android-35`,
  `build-tools;35.0.0` și `platform-tools` (puse pe 14.11.2026 cu `sdkmanager`).
- `local.properties` nu merge în git. Trebuie să cuprindă exact, **fără BOM și cu slash
  înainte** (cu backslash, Java îl mănâncă și AGP nu găsește SDK-ul):

  ```
  sdk.dir=C:/Users/vix/AppData/Local/Android/Sdk
  ```
- Gradle 8.14.3 prin wrapper, AGP 8.7.3, Kotlin 2.0.21, compileSdk 35, minSdk 29.
- Nu există emulator: proba se face pe telefonul real, pe USB, cu depanarea USB pornită.

## Reguli pe care codul le respectă

- **Nu se scrie nimic în mașină.** Numai cereri de citire (modurile 01, 09 și 21).
- Formulele hibride **nu se scriu din memorie** — se confirmă din jurnalul sondei.
- Simulatorul are aceeași purtare ca adaptorul, inclusiv `NO DATA` și răspunsuri lente,
  ca să nu descoperim în mașină ce puteam descoperi acasă.
