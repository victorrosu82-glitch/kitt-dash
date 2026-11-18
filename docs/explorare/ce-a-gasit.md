# Explorarea din 17.11.2026, 15:56–15:59

Raport brut: `KittDash-explorare-20261117-155918.txt` (din Download-ul telefonului).
Mașina pe loc, motorul termic pornit (ATRV 14,6 V), adaptorul Viecar „ELM327 v1.5”,
protocol ISO 15765-4 CAN 11 biți / 500 kbit/s.

**Explorarea s-a oprit la jumătate**, după primul calculator. Cauza: la 15:59:16 s-a
apăsat „Acasă”. Android Auto a trimis cererea de navigație chiar către KITT
(e și el aplicație de navigație), iar sesiunea a repornit legătura cu adaptorul și
a rupt-o pe cea a explorării. Reparat pe 18.11.2026:
- „Acasă” trimite cererea direct la Waze (`com.waze.car_lib.WazeCarAppService`);
- o cerere de navigație primită de KITT nu mai repornește legătura;
- ecranul mașinii nu mai repornește legătura dacă merge deja cu același adaptor.

## Calculatoarele care răspund (12)

| Cerere → răspuns | Ce e (după numele dat de calculator sau după obiceiul Toyota) | Serviciul 21 |
|---|---|---|
| 7E0 → 7E8 | motorul termic (ECM) | da, bloc 2101 de 28 de octeți |
| 7E2 → 7EA | calculatorul hibrid, adresa OBD | da |
| **724 → 72C** | **același calculator hibrid**, adresa Toyota. Se numește singur `HPCM-HybridPtCtrl` (090A). Răspunde la fel ca 7E2 la 0100 și 2101 | da, **63 de blocuri** |
| 780 → 788 | airbag (SRS), de obicei | da |
| 790 → 798, 791 → 799 | necunoscute; în răspuns apar literele „LIP” și „LIW” | da |
| 7A1 → 7A9 | direcția asistată (EPS), de obicei | da (7F 21 12 la blocul 01) |
| 7B0 → 7B8 | ABS / frâne / VSC, de obicei | da (7F 21 12 la blocul 01) |
| **7C0 → 7C8** | **bordul de instrumente**, de obicei. Aici stă de regulă **nivelul combustibilului** la Toyota | da (7F 21 12 la blocul 01) |
| 7C4 → 7CC | clima, de obicei | da (7F 21 12 la blocul 01) |
| 7D0 → 7D8 | necunoscut | da (7F 21 12 la blocul 01) |
| 7F1 → 7F9 | necunoscut | **nu** (7F 21 11); poate răspunde la serviciul 22 |

Adresele cu octet suplimentar (750 + xx, caroseria): adaptorul știe ATCEA, dar
nimeni n-a răspuns la 750/40. Restul de 255 nu s-au încercat — explorarea s-a oprit înainte.

„7F 21 12” la blocul 01 înseamnă „știu serviciul, dar nu blocul ăsta”: calculatorul
are alte blocuri. Ele nu s-au cerut încă, din cauza opririi.

## Bateria HV stă la calculatorul hibrid, nu la 7E3

De aceea `7E3 2181` dădea `NO DATA`. Aceleași blocuri vin de la **724** (și probabil de la 7E2):

| Bloc | Ce conține | Dovada |
|---|---|---|
| **2181** | **tensiunile celor 17 blocuri** ale bateriei, câte 2 octeți, × 79,99 / 65535 V | 17,25–17,42 V; suma 294,1 V, iar 019A în aceeași explorare: 291,0 V (1 %) |
| **2192** | blocul cu tensiunea cea mai mică și cel cu cea mai mare | `37 43 0B 37 BE 09 11`: min 17,27 V la blocul 0B, max 17,42 V la blocul 09, 0x11 = 17 blocuri. Se potrivesc exact cu 2181 |
| **2195** | **rezistența internă** a fiecărui bloc, 1 octet, în mΩ | 17 valori, 18–19 mΩ, ca la Dr Prius |
| **2187** | **temperaturile bateriei**, 7 valori, × 255,9 / 65535 − 50 °C | 16,1 / 24,0 / 24,7 / 21,3 / 21,4 / 24,3 / 23,8 °C — candidat, de confirmat cu Dr Prius |

Formulele sunt cele Toyota cunoscute pentru Prius. La 2181 și 2192 sunt confirmate de
sumă și de min/max; la 2187 sunt doar plauzibile până la comparația cu Dr Prius.

## Identificare

- 090A (724): `HPCM-HybridPtCtrl`.
- 0904 (724): calibrările `89983782500`, `899857801000`, `89884481330`, … (răspunsul
  a umplut memoria adaptorului: `BUFFER FULL`).
- 0902 (seria de șasiu) nu e anunțată de calculatorul hibrid; la motor nu s-a ajuns.

## Ce urmează

1. **Explorarea din nou**, cu APK-ul reparat, fără să atingi ecranul mașinii cât durează.
   Interesează mai ales **7C0** (combustibilul, kilometrajul), 7B0 (frânele, roțile),
   7C4 (clima) și 7F1 (serviciul 22).
2. Pe bord, din ce e deja sigur: **tensiunile celor 17 blocuri** și **diferența dintre
   cel mai slab și cel mai tare** (sănătatea bateriei), plus temperaturile bateriei după
   confirmare.

## Explorările din 18.11.2026

- **10:07–10:11** (`KittDash-explorare-20261118-101111.txt`): aceleași 12 calculatoare.
  A citit tot calculatorul hibrid (724) și airbagul (780), apoi adaptorul a amuțit
  (8 tăceri la rând, fără prompt) la blocurile 216x ale airbagului.
- **10:16** (`KittDash-explorare-20261118-101637.txt`): adaptorul a amuțit după prima adresă.

Noutăți:
- 724 nu știe serviciul 22 (`7F 22 11`); la 1A80 răspunde `5A 01`.
- Airbagul (780) nu știe modurile 01 și 09 (`7F 01 10`), dar are blocurile
  2101, 2103 (text: `780400006800068000…`), 2104, 2105, 2106, 210A, 210B, 2120, 2140, 2160.

Reparat după asta:
- când adaptorul amuțește de 3 ori la rând, exploratorul închide legătura, o
  redeschide, pune adaptorul la loc (adresă, filtru) și continuă; de cel mult 4 ori;
- „Acasă”: Android Auto refuză o cerere de navigație cu componentă
  („Navigation intent cannot have a component”). Acum se încearcă pe rând:
  cererea de navigație cu pachetul Waze, Waze pornit direct cu adresa, apoi
  cererea fără adresant. Jurnalul spune care a mers.

## Explorarea completă din 18.11.2026, 12:22–12:56

Raport: `KittDash-explorare-20261118-125622.txt` (34 min, fără întreruperi).
Analiză: `python -I tools/analiza_explorare.py <raport>`.
Citirile repetate (pasul 5) s-au făcut **în mers**: 0–53 km/h, motorul pornind și
oprindu-se, bateria între 42 și 62 %. Un tur durează ~2 minute, deci viteza din tur
nu e din aceeași clipă cu fiecare bloc — corelațiile de mai jos sunt orientative.

Toate cele 12 calculatoare au răspuns pe serviciul 21. Niciunul nu știe 22, iar
modurile 01/09 le știu numai motorul și hibridul. 7F1 nu știe nimic din ce am întrebat.

### Candidați, de confirmat cu o probă țintită

| Calculator | Bloc · octet | Ce pare | De ce |
|---|---|---|---|
| 7C0 bord | **2129** · 0 | **nivelul combustibilului**, A/2 litri | 0x26–0x3E (19–31 l), se clatină în mers ca un plutitor; formula e cea Toyota cunoscută. Confirmare: comparat cu acul de pe bord |
| 7C0 bord | 2121 · 0 | viteza afișată | 0 când mașina stă, până la 0x3B = 59 |
| 7B0 ABS | **2103** · 0–3 | **viteza fiecărei roți** | patru octeți aproape egali, 0 pe loc, cresc împreună |
| 7B0 ABS | 2121, 2143 · 0 | viteza mașinii după ABS | urmează 2103 |
| 7B0 ABS | 2104 · 3, 2107 · 0 | presiunea de frânare / pedala | stă la 0x19 și sare la 0x33–0x3A în aceleași tururi |
| 7B0 ABS | 2147 · 0–1, 2106 · 3 | accelerații laterale / longitudinale | în jurul lui 0x80 / 0xFF, cu semn |
| 7A1 direcție | 2116 · 0–1, 2121–2122 | unghiul / cuplul volanului | în jurul lui 0x80, se mișcă doar în mers |
| 7A1 direcție | 210A · 1 | temperatura motorului direcției | crește lin 0x0E → 0x23 |
| 7C4 clima | 2121, 2122, 2124–2126, 214B, 2153 | temperaturi: habitaclu, afară, evaporator | variază lent; 2124 scade 0x46 → 0x11 când pornește clima |
| 7C4 clima | 2141–2143, 2170 | temperaturile cerute, stânga = dreapta | perechi de octeți egali, se schimbă în trepte |
| 724 hibrid | 2161–2163 · 0, 2 | temperaturile MG1 / MG2 / invertorului | urcă lin 0x3C–0x55 (−40 → 20–45 °C) |
| 724 hibrid | 2170–2174 · 0–1 | alte temperaturi (convertor, răcire) | 0x3B–0x4A, urcă lin |
| 724 hibrid | 2187 | temperaturile bateriei (confirmat ca formă) | urcă lin 17,5 → 18,5 °C pe primul senzor |
| 724 hibrid | 2181 | tensiunile celor 17 blocuri (confirmat) | în mers coboară sub sarcină: 0x2C.. = 13,8 V la descărcare puternică |

### Proba de confirmare propusă

În loc de altă explorare de 34 de minute, o probă scurtă care citește numai
blocurile de mai sus, de câteva ori pe secundă, cu momente notate: pe loc → frână
apăsată → volan stânga-dreapta → clima pornită → demaraj. Pentru combustibil e
nevoie doar de ce arată acul de pe bord în același moment.

## Tiparele Corolla puse peste NX (18.11.2026, seara)

Sursa: lista Torque pentru Corolla hibrid (`corolla-pids-torque.txt`). Corolla nouă vorbește
serviciul 22 la adresele 747 și 7D2, pe care NX nu le are. Formulele însă au aceeași formă ca
blocurile 21 ale calculatorului hibrid NX (7E2). Verificat pe cele 15 tururi din mers ale
explorării de la 12:22:

| Bloc NX | Octeți | Ce e | Formula | Dovada |
|---|---|---|---|---|
| **2161** | A, B, C | temperatura MG1: acum, la pornire, maximă | octet − 40 | ca Corolla 103A (C/D/E) |
| **2161** | D:E | **turația MG1** | D×256+E − 32768 | 0 pe loc; −3619 la 48 km/h cu motorul termic oprit (MG1 se rotește invers), −475 cu motorul la 1434 rpm |
| **2162** | A, B, C / D:E | temperatura MG2 / **turația MG2** | idem | +3435 la 48 km/h: ~72 rpm pe km/h, cât dau reducerile NX (~3170 socotit) |
| **2163** | A, B, C / D:E | temperatura MGR / **turația MGR** (spate) | idem | +2675 la 48 km/h; socotit din reducerea punții spate: ~2520 |
| 2167, 2168, 2169 | A:B, C:D | cuplul MG1, MG2, MGR (cerut și realizat) | (X − 32768) / 8 Nm | 0 pe loc; MG1 negativ cât motorul termic merge (generator); MG2 +50 Nm la accelerare, −97 la frânare |
| 2170, 2171, 2172 | A, B, C | temperaturi de invertor (acum, la pornire, maximă) | octet − 40 | ca Corolla 10A4 / 10A5; nu știm încă care e al cui |
| 2174 | A–D | temperaturile convertorului ridicător (sus, jos, la pornire, maximă) | octet − 40 | ca Corolla 10A7 |
| 2174 | F:G, H:I | tensiunea înainte și după ridicător | X / 2 V | 273 / 270 V pe loc, ca bateria |

Turațiile au timp de citire diferit de viteză (un tur dura 2 minute), deci raportul rpm/km/h e
orientativ. Proba ghidată (pasul de drum) le citește lângă viteză.

**Win / Wout** (limitele de putere): Corolla le are la 747 1807, (X − 32768) / 100 kW. Nicio
pereche de octeți din blocurile NX citite nu dă constant 27 / 25,5 kW cu formula asta. Rămân negăsite.

## ScanGauge, Prius generația 3 (aceeași generație cu NX 300h)

Sursa: <https://www.scangauge.com/support/x-gauge-commands/toyota-lexus-scion/>, secțiunea
„Generation III Prius (2010-2015)”. Comenzile sunt pe 7E2 / 7C0 / 7C4, serviciul 21 — exact ce are NX.

Cum se citesc: în RXD, primul octet e bitul de început. Numărat în RXF, octetul 4 e 0x61 la un cadru
simplu (0x61 la octetul 5 la cel lung), iar biții din RXD sunt cu un octet înaintea lui RXF. Cu
decalajul ăsta, fiecare comandă cade pe octetul pe care l-am găsit singuri:

| ScanGauge | Bloc · octet NX | Formula | Se potrivește cu |
|---|---|---|---|
| Traction Battery Temperature | 2187 · A | A − 50 °C | formula noastră pe 2 octeți (confirmată cu Dr Prius) |
| Fuel Level | 7C0 2129 · A | A / 2 l | confirmat cu acul (29 l) |
| MG1 / MG2 RPM | 2161 / 2162 · D:E | − 32768 | ce am găsit cu lista Corolla |
| MG1 / MG2 Torque | 2167 / 2168 · A:B | − 32768 | idem |
| MG1 / MG2 Temp | 2161 / 2162 · **C** | C − 40 | ScanGauge ia al treilea octet. La noi C nu scade niciodată în 30 min (pare maximul), iar A urcă și coboară. De lămurit după o oprire lungă |
| Inverter MG1 / MG2 Temp | 2170 / 2171 · A | A − 40 | da: 2170 = invertorul MG1, 2171 = invertorul MG2 (2172 probabil MGR) |
| DC/DC Upper / Lower Temp | 2174 · A / B | − 40 | ca Corolla |
| Inverter Coolant Temp | 2175 | − 40 | la NX blocul e mai lung; de găsit octetul |
| **Outside Temperature** | 7E2 **2101 · D** | D − 40 | 0x3A = 18 °C, la fel ca 0146 de la hibrid. **Cel de la motor dădea 34 °C**: KITT o ia acum de la hibrid |
| **Room Temperature** (habitaclu) | **7C4 2121 · A** | A / 4 − 6,5 °C (din 4,5·A + 203, în zecimi de °F) | candidat: 0x7E–0x82 în mers = 25–26 °C. De confirmat cu un termometru (proba ghidată, pasul 7) |
| **Battery Discharge / Charge Max** (Wout / Win) | 7E2 **2198 · C / D** | (5·C − 201)/10 și (5·D − 1081)/10 | la 12:22: C=0x4A, D=0xB3 → 16,9 și −18,6 (kW?). Constante, ca limitele. **De confirmat cu Dr Prius**: proba de baterie citește acum și 2198 |
| MG1 / MG2 Carrier Frequency | 217C · A / B | × 10 / 20 | frecvența de comutare a invertoarelor |
| A/C Power Usage | 217D · C | × 50 W | puterea compresorului climei |

Prius generația 2 (`prius-gen2-pids.pdf`, lista Torque oguchi-rd) e altă generație: blocurile
(21C3, 21C4, 21CE–21D0) nu există la NX. Folositoare doar numele: „Drive Condition ID”,
„Shift Position”, „EV-Mode Active” — la Prius 2 vin din mesajele de pe rețea (0x0120, 0x0529),
pe care poarta NX nu le lasă să ajungă la priză.
