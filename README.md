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
| 3 | Bordul KITT pe telefon | nu a inceput |
| 4 | Android Auto | nu a inceput |
| 5 | Finisaje | nu a inceput |

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
  ui/MainActivity.kt         ecranul de test al etapei 1 (valorile brute)
tools/sonda_elm327.py        sonda de pe laptop (etapa 0)
tools/serial_win.py          port serial pe Windows, fără pyserial
tools/fals_elm327.py         ELM fals în Python, pentru proba sondei
```

Desenătorul KITT (etapa 3) se va așeza lângă `ui/`, ca o singură bucată de cod folosită
și pe telefon, și pe ecranul mașinii.

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
