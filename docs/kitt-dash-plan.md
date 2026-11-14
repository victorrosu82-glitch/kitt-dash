# KITT Dash — dashboard Knight Rider pentru Lexus NX 300h

Data: 14 noiembrie 2026

## Ce construim

O aplicație Android, pentru uz personal, care citește datele mașinii prin adaptorul
Bluetooth ELM327 (Viecar 1.5) și le afișează pe ecranul de navigație al mașinii, prin
Android Auto, ca un bord în stilul KITT. Aspectul de referință este macheta
`dashboard-kitt.html`, aprobată: scanner roșu, ceas de viteză din bare radiale, baterie HV,
putere bidirecțională (tracțiune / recuperare), modulator de voce, mod EV / HYBRID / REGEN.

## Ce nu construim

- Nu publicăm în Play Store. Aplicația se instalează direct pe telefon (APK).
- Nu facem diagnoză (citire/ștergere coduri de eroare), doar afișare.
- Nu scriem nimic în calculatoarele mașinii. Trimitem doar cereri de citire.

## Variantele luate în calcul

1. **Șabloane Android Auto** (liste, grile, text). Stabil și acceptat de Google, dar arată ca
   o listă de cifre. Respinsă: nu poate arăta ca macheta.
2. **Suprafață desenată liber, aplicația declarată de navigație.** Android Auto dă aplicațiilor
   de navigație o suprafață pe care desenăm orice. Nu trece de Play Store, dar merge instalată
   direct, cu „Unknown sources" activat în modul developer din Android Auto. **Aleasă.**
3. **Oglindirea ecranului telefonului** cu aplicații terțe. Respinsă: fragilă, depinde de
   unelte neoficiale care se strică la fiecare actualizare.

## Cum e alcătuită aplicația

- **Stratul OBD**: conexiune Bluetooth clasic (SPP) la ELM327, inițializare, interogare în
  buclă, decodare. Rulează într-un serviciu în prim-plan și ține „starea mașinii" la zi.
- **Simulator ELM327**: un adaptor fals, în cod, care răspunde ca mașina. Cu el dezvoltăm și
  testăm pe PC, fără să stăm în mașină.
- **Desenatorul KITT**: o singură bucată de cod care desenează bordul pe un Canvas, la orice
  rezoluție. Aceeași bucată desenează și pe telefon, și pe ecranul mașinii.
- **Ecranul de pe telefon**: alegerea adaptorului, starea conexiunii, previzualizarea bordului.
- **Partea Android Auto**: serviciul Car App (categoria navigație) care dă suprafața
  desenatorului.

Limbaj și unelte: Kotlin, mediul de build deja verificat (AGP 9.3.1, Gradle 9.7.1,
compileSdk 37, JDK-ul din Android Studio). Proiectul stă în `C:\Users\vix\Proiecte\KittDash`,
cu git.

## Etapele

### Etapa 0 — Verificări în mașină (le faci tu, cam 30 de minute)

Fără cod. Aflăm dacă proiectul stă în picioare.

1. Android Auto pornește pe ecranul mașinii cu telefonul tău (cu fir sau fără).
2. Instalezi Car Scanner ELM OBD2, alegi profilul Toyota/Lexus hibrid, te conectezi la Viecar.
3. Notezi (sau faci capturi) ce senzori dau valori: viteză, turație, temperatură lichid,
   încărcarea bateriei HV, curentul și tensiunea bateriei HV, temperaturile bateriei,
   turațiile MG1/MG2, nivelul de combustibil.

Rezultat: lista datelor pe care mașina le dă prin adaptorul tău. Dacă datele hibride nu vin,
decidem atunci: alt adaptor sau bord doar cu datele standard.

### Etapa 1 — Conexiunea cu mașina

Proiectul Android, stratul OBD, simulatorul și un ecran simplu de test pe telefon, cu valorile
brute. Doar datele standard (viteză, turație, temperaturi).

Gata când: în mașină, telefonul arată viteza și turația în timp real și se reconectează singur
dacă pică legătura.

### Etapa 2 — Datele hibride

Cererile specifice Toyota/Lexus către calculatorul hibrid: încărcarea bateriei HV, curent și
tensiune (din ele iese puterea în kW), temperaturi, starea motorului termic, modul
EV / HYBRID / REGEN.

Gata când: valorile de pe telefon se potrivesc cu cele din Car Scanner.

### Etapa 3 — Bordul KITT pe telefon

Macheta HTML rescrisă ca desenator Android, legată la datele reale. O vezi întâi pe ecranul
telefonului.

Gata când: bordul de pe telefon arată ca macheta și se mișcă după mașina reală.

### Etapa 4 — Android Auto

Serviciul Car App, desenarea pe suprafața mașinii, potrivirea pe rezoluția și zona liberă a
ecranului Lexus (Android Auto își pune bara lui peste imagine). Test întâi în emulatorul
Desktop Head Unit de pe PC, apoi în mașină.

Gata când: bordul apare pe ecranul mașinii și merge un drum întreg fără să se oprească.

### Etapa 5 — Finisaje

Pornire și conectare automată la urcarea în mașină, mod zi/noapte, avertizări (de exemplu
supraîncălzire), reglaje de aspect după ce îl vezi pe drum, APK final mic.

## Riscuri cunoscute

- **Adaptorul Viecar 1.5.** Multe clone 1.5 nu execută corect schimbarea adresei
  calculatorului, fără de care nu ajungem la datele hibride. Etapa 0 lămurește. Soluția de
  rezervă: un adaptor mai bun (OBDLink CX/MX, Vgate iCar Pro).
- **Date care pot lipsi.** Nivelul de combustibil nu se dă mereu prin OBD la Toyota/Lexus.
  Coloanele bordului se stabilesc definitiv după etapa 0.
- **Harta și bordul nu stau împreună.** Fiind declarată de navigație, aplicația ocupă locul
  hărții. Cât e ea pe ecran, nu vezi Google Maps sau Waze.
- **Android Auto se schimbă.** O actualizare poate înăspri regulile pentru aplicațiile
  instalate direct. Nu putem controla asta.
- **Viteza de reîmprospătare.** Un ELM327 dă cam 10–20 de răspunsuri pe secundă în total.
  Cu 8–10 valori urmărite, fiecare se actualizează de 1–2 ori pe secundă; animația rămâne
  fluidă prin netezire între citiri.
