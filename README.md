# KITT Dash

Bord în stilul KITT pentru Lexus NX 300h, pe ecranul mașinii prin Android Auto.
Citește datele prin adaptorul Bluetooth ELM327 (Viecar 1.5). Uz personal, fără Play Store.

Planul pe etape și hotărârile luate: [docs/kitt-dash-plan.md](docs/kitt-dash-plan.md).
Macheta aprobată a bordului: [docs/dashboard-kitt.html](docs/dashboard-kitt.html).

## Unde suntem

**Etapa 1 — legătura cu mașina.** Codul e scris; mai trebuie compilat și probat pe telefon.

| Etapă | Ce cuprinde | Stare |
|---|---|---|
| 0 | Ce date dă mașina prin Viecar | sonda e scrisă, de rulat în mașină |
| 1 | Conexiunea, datele standard, ecran de test | cod scris |
| 2 | Datele hibride (baterie HV, kW, mod) | nu a început |
| 3 | Bordul KITT pe telefon | nu a început |
| 4 | Android Auto | nu a început |
| 5 | Finisaje | nu a început |

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
adaptor — util acasă. **Conectează** caută adaptorul împerecheat (după nume: Viecar, OBD,
ELM) și se leagă la el.

## Cum e alcătuit

```
app/src/main/java/ro/vix/kittdash/
  obd/ObdPid.kt              PID-urile standard și formulele (SAE J1979)
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
