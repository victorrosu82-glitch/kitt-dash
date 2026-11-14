# KITT Dash — predare către altă sesiune Claude

Scris pe 14 noiembrie 2026. Citește întâi acest fișier, apoi `kitt-dash-plan.md`.

## Cine și ce vrea

Victor are un Lexus NX 300h (hibrid) și un adaptor Bluetooth OBD2 ELM327 „Viecar 1.5".
Vrea o aplicație Android, pentru uz personal, care să arate pe ecranul de navigație al
mașinii, prin Android Auto, un bord în stilul KITT din Knight Rider. A cerut explicit „foarte
frumos", nu o listă de cifre. Vorbește românește; răspunde-i în română, fără jargon de
programare.

## Fișierele existente (toate în `C:\Users\vix\Downloads`)

- `dashboard-kitt.html` — macheta animată a bordului, cu date simulate. **Victor a aprobat
  aspectul** („E cool"). Este referința vizuală: layout pe 1280x720, culori, elemente.
- `kitt-dash-plan.md` — designul scurt și planul pe etape (0–5), cu riscurile.
- `kitt-dash-predare.md` — acest fișier.

Proiectul Android nu există încă. Locul stabilit: `C:\Users\vix\Proiecte\KittDash`, cu git
(lângă `GisDeoLocator`). La crearea proiectului, copiază cele trei fișiere în `docs/` acolo;
`Downloads` nu e un loc sigur.

## Ce s-a hotărât

- **Afișarea:** suprafață desenată liber prin Car App Library, cu aplicația declarată în
  categoria navigație. Șabloanele Android Auto au fost respinse (nu pot arăta ca macheta).
  Consecințe acceptate: fără Play Store, instalare directă (APK), „Unknown sources" din
  modul developer al Android Auto; cât e bordul pe ecran, nu se vede Google Maps/Waze.
- **Aspectul:** cel din machetă — scanner roșu dus-întors, ceas de viteză din 54 de bare
  radiale cu cifră digitală și `READY`, baterie HV pe segmente, putere bidirecțională
  (tracțiune sus, recuperare jos în cyan), modulator de voce, mod EV / HYBRID / REGEN.
- **Alcătuirea:** strat OBD (Bluetooth clasic SPP, serviciu în prim-plan) + simulator ELM327
  în cod pentru lucru pe PC + un singur desenator pe Canvas folosit și pe telefon, și pe
  mașină + ecran de telefon (adaptor, stare, previzualizare) + serviciul Car App.
- **Limbaj:** Kotlin.

## Ce NU s-a lămurit încă

1. **Merge Android Auto pe ecranul mașinii lui?** Întrebarea i-a fost pusă și a rămas fără
   răspuns. NX 300h l-a primit din fabrică doar la modelele mai noi. Dacă mașina nu îl are,
   planul se schimbă. Întreabă din nou înainte de a scrie cod.
2. **Planul nu a fost aprobat explicit.** Victor a cerut predarea imediat după ce l-a primit.
   Cere-i acordul pe `kitt-dash-plan.md` înainte de etapa 1.
3. **Etapa 0 nu e făcută.** Victor trebuie să verifice în mașină, cu Car Scanner ELM OBD2
   (profil Toyota/Lexus hibrid), ce date vin prin Viecar: viteză, turație, temperatură lichid,
   încărcarea bateriei HV, curent și tensiune baterie HV, temperaturi baterie, MG1/MG2,
   combustibil. Coloanele finale ale bordului depind de rezultat.
4. Anul mașinii, modelul telefonului, Android Auto cu fir sau fără fir, rezoluția ecranului.

## Pasul următor

După răspunsurile de la punctele 1–2: planul detaliat de implementare pentru etapa 1
(proiectul, stratul OBD, simulatorul, ecranul de test pe telefon cu datele standard), apoi
implementarea. Etapa 0 poate merge în paralel, o face Victor.

## Mediul de build (verificat în august 2026 la GisDeoLocator; reverifică înainte de folosire)

- Android SDK: `J:\andoidstudio` (nu în locul implicit). În `local.properties`:
  `sdk.dir=J:/andoidstudio`.
- JDK: `C:\Program Files\Android\Android Studio\jbr` (JDK 25). `java` din PATH e vechi.
- Combinația care compilează: AGP 9.3.1, Gradle 9.7.1, compileSdk 37, targetSdk 36.
  Cu AGP 9 nu se declară pluginul `org.jetbrains.kotlin.android`; la targetSdk 36 e
  obligatoriu `enableEdgeToEdge()`.
- Nu există `cmdline-tools` (fără `sdkmanager`/`avdmanager`) și niciun emulator de telefon
  utilizabil: testarea se face pe telefonul real, pe USB.
- Desktop Head Unit (emulatorul de Android Auto) nu e instalat; se pune din SDK Manager-ul
  din Android Studio („Android Auto Desktop Head Unit Emulator").
- Semnare: `build-tools\36.0.0\apksigner.bat` cu `~/.android/debug.keystore`. Se livrează
  `assembleRelease` cu minify.
- Git: fără identitate globală (se setează per depozit), fără `gh`. Depozitul GitHub al lui
  Victor: `https://github.com/victorrosu82-glitch/vixrepo.git`; `git push` merge direct.
  Nu urca nimic acolo fără să-l întrebi.

## Note tehnice pentru implementare

Car App Library (verifică versiunile curente înainte de folosire):
- Dependență `androidx.car.app:app`. Un `CarAppService` cu intent-filter
  `androidx.car.app.CarAppService` și categoria `androidx.car.app.category.NAVIGATION`.
- Permisiuni: `androidx.car.app.NAVIGATION_TEMPLATES` și `androidx.car.app.ACCESS_SURFACE`.
- Meta-data `com.google.android.gms.car.application` către un `automotive_app_desc.xml` cu
  `<uses name="template"/>`, plus `androidx.car.app.minCarApiLevel`.
- Ecranul întoarce un `NavigationTemplate` (cere un `ActionStrip`); desenarea se face prin
  `AppManager.setSurfaceCallback`, pe `Surface`-ul primit în `onSurfaceAvailable`.
  `onVisibleAreaChanged` / `onStableAreaChanged` dau zona neacoperită de elementele Android
  Auto — layoutul trebuie să se așeze în ea.

OBD:
- ELM327 pe Bluetooth clasic, RFCOMM cu UUID-ul SPP `00001101-0000-1000-8000-00805F9B34FB`.
  Adaptorul se împerechează întâi din setările telefonului.
- Datele standard (mod 01): viteză `0D`, turație `0C`, temperatură lichid `05`, aer admis
  `0F`. Nivelul de combustibil `2F` lipsește des la Toyota/Lexus.
- Datele hibride sunt cereri specifice Toyota către calculatorul hibrid (adresare cu
  `ATSH`, de regulă `7E2`). **Neverificat pe această mașină.** Ia formulele din listele de
  PID-uri Toyota hibrid folosite de Car Scanner / Torque și confirmă-le pe mașina lui Victor;
  nu le scrie din memorie.
- Clonele ELM327 1.5 execută des greșit `ATSH`. Dacă datele hibride nu vin, adaptorul e
  primul suspect; rezervă: OBDLink CX/MX sau Vgate iCar Pro.
- Un ELM327 dă cam 10–20 de răspunsuri pe secundă în total. Interoghează des valorile rapide
  (viteză, putere) și rar pe cele lente (temperaturi), și netezește între citiri.
