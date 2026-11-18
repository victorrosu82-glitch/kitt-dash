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
