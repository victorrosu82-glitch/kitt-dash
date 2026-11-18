# Tot ce se poate scoate din mașină prin adaptor

Lexus NX 300h E-Four, adaptorul Viecar (ELM327 v1.5). Lista adună sonda din 14.11,
explorările din 17–18.11 și proba de baterie confruntată cu Dr Prius (18.11.2026).

Cum se citește tabelul:
- **Stare**: ✅ confirmat · 🟡 se citește, dar încă nu știm sigur ce e · ⛔ mașina nu-l dă.
- **Unde**: calculatorul care răspunde. Codul de cerere e trecut pentru lucru, nu trebuie reținut.
- ★ = ar merita pus pe un ecran.

Mașina are **12 calculatoare** care răspund la adaptor. Numai două (motorul și hibridul)
vorbesc limba standard a tuturor mașinilor. Celelalte vorbesc doar „limba Toyota”, cu
blocuri de octeți pe care trebuie să le descifrăm noi.

---

## 1. Pe ecrane, deja

| Ce | Unde | Stare |
|---|---|---|
| viteza | motor | ✅ |
| turația motorului termic | motor | ✅ |
| încărcarea bateriei HV (%) | hibrid | ✅ (aceeași formulă ca Dr Prius) |
| tensiunea și curentul bateriei HV, deci puterea în kW | hibrid | ✅ |
| tensiunile celor 17 blocuri ale bateriei | hibrid, bloc 81 | ✅ (se potrivesc 99,4% cu Dr Prius) |
| rezistența internă a fiecărui bloc, mΩ | hibrid, bloc 95 | ✅ |
| temperaturile bateriei, 3 senzori | hibrid, bloc 87 | ✅ (la 0,01 °C de Dr Prius) |
| temperatura aerului aspirat de baterie | hibrid, bloc 87 | 🟡 foarte probabil (~23 °C, cât în habitaclu) |
| alți 3 senzori de temperatură ai bateriei | hibrid, bloc 87 | 🟡 valori de baterie, Dr Prius nu le arată |
| combustibilul din rezervor, litri | bord (7C0) | ✅ (29 l = acul, 18.11) |
| temperaturile MG1 / MG2 / invertor / convertor | hibrid, blocuri 61–63, 70 | 🟡 cresc lin în mers; numele sunt ghicite |
| temperatura lichidului de răcire | motor | ✅ |
| temperatura aerului la admisie | motor | ✅ |
| sarcina motorului, poziția clapetei | motor | ✅ |
| tensiunea bateriei de 12 V | motor | ✅ |
| temperatura catalizatorului | motor | ✅ |
| debitul de aer → consumul în l/h și l/100 km | motor | ✅ socotit |
| temperatura exterioară | motor | 🟡 la motor arăta 34 °C, la hibrid 18 °C; motorul o ia probabil de lângă el |

## 2. Standard, răspunde, dar nu e folosit încă

| Ce | Unde | Ce înseamnă pentru tine |
|---|---|---|
| ★ **martorul „Check engine” și câte erori sunt** | motor, hibrid | dacă se aprinde ceva, KITT poate spune înainte să te uiți la bord |
| ★ **poziția pedalei de accelerație** (două piste) | hibrid | cât apeși tu, nu cât deschide calculatorul clapeta. Arată frumos ca bară lângă putere |
| ★ **presiunea atmosferică** | motor, hibrid | din ea iese **altitudinea** (±50 m). Interesant la munte |
| ★ **km de la ultima ștergere a erorilor** | motor, hibrid | 4.332 km pe 18.11. Ține loc de contor de parcurs, dar nu e kilometrajul |
| ★ **minute de mers de la ultima ștergere** | motor, hibrid | 152 de ore; împreună cu km de mai sus dă viteza medie pe termen lung |
| presiunea în galeria de admisie | motor | cât de tare trage motorul; la oprit = presiunea aerului |
| avansul la aprindere | motor | pentru curioși; sare mult |
| corecțiile de amestec (scurte și lungi) | motor | **sănătatea motorului**: dacă cele lungi trec de ±10%, ceva nu e în regulă (aer fals, injectoare) |
| sonda lambda din față și cea din spate | motor | amestecul real; cea din spate arată dacă **catalizatorul** mai lucrează |
| amestecul cerut de calculator | motor | se compară cu cel real |
| sarcina absolută, clapeta relativă, comanda clapetei | motor | alte măsuri ale efortului motorului |
| temperatura catalizatorului după catalizator | motor | a doua temperatură, mai rece |
| câte porniri la cald de la ștergerea erorilor | motor | statistic |
| timpul de la pornirea motorului termic | motor | se resetează la fiecare pornire a lui; din el se poate număra **de câte ori pornește motorul pe drum** |
| purjarea vaporilor de benzină, recircularea gazelor | motor | tehnic, doar diagnoză |
| tipul de combustibil | motor | „benzină”; o singură dată |
| ieșirile auxiliare | hibrid | nedescifrat |

## 3. Identificarea mașinii (o singură dată)

| Ce | Unde |
|---|---|
| seria de șasiu (VIN) | motor |
| numele calculatoarelor („ECM-EngineControl”, „HPCM-HybridPtCtrl”) | motor, hibrid |
| versiunile de program și semnăturile lor | motor, hibrid |
| statistica testelor interne (câte au rulat, câte au trecut) | motor |

## 4. Erorile — se pot citi fără niciun risc

Încă nu le cerem: explorarea a fost limitată la citiri de valori. Citirea codurilor de
eroare e la fel de sigură (doar citește); **ștergerea lor nu o facem niciodată**.

| Ce | Ce-ți spune |
|---|---|
| ★ erorile memorate | codul și ce înseamnă (P0A80 = bateria hibrid slăbită etc.) |
| ★ erorile în așteptare | o problemă văzută o dată, care încă n-a aprins martorul. **Avertizare din timp** |
| erorile permanente | cele care nu se pot șterge până nu se repară |
| „poza” din clipa erorii | viteza, turația, temperaturile în momentul în care a apărut |

## 5. Din blocurile Toyota — candidați găsiți în explorare, de confirmat

| Ce pare | Unde | Cum îl confirmăm |
|---|---|---|
| ★ **viteza fiecăreia dintre cele 4 roți** | ABS (7B0), bloc 03 | o curbă strânsă: roțile din exterior merg mai repede. Arată **patinarea** și lucrul tracțiunii din spate |
| ★ **presiunea de frânare / pedala de frână** | ABS, blocuri 04 și 07 | apeși frâna pe loc și urmărim octetul. Cu ea: **cât frânează cu motorul electric și cât cu discurile** |
| ★ **accelerația laterală și longitudinală** | ABS, blocuri 47 și 06 | o curbă și o frânare. Un cerc cu „bila” G, ca la mașinile sport |
| ★ **unghiul volanului** | direcția (7A1), blocuri 16 și 21–22 | volan stânga-dreapta pe loc |
| temperatura motorului direcției | direcția, bloc 0A | crește lin; doar curiozitate |
| viteza afișată pe bord | bord (7C0), bloc 21 | se compară cu viteza adevărată (bordul arată de obicei cu 2–4 km/h mai mult) |
| ★ **temperatura din habitaclu și cea de afară** (cele ale climei) | clima (7C4), blocuri 21–26 | comparat cu ce arată clima |
| temperatura evaporatorului | clima, bloc 24 | scade brusc când pornește clima |
| temperaturile cerute stânga / dreapta | clima, blocuri 41–43 | schimbi temperatura pe climă și urmărim |
| alte temperaturi din sistemul hibrid (răcirea invertorului) | hibrid, blocuri 71–74 | cresc lin în mers |
| ★ **limitele de putere ale bateriei** (cât poate primi / da) | hibrid, încă negăsite | Dr Prius le arată (27 / 25,5 kW); nu sunt în blocurile citite până acum. Bara de putere folosește deja 25 kW |
| poziția schimbătorului (P/R/N/D/B) | probabil hibrid | nu am căutat încă; ar ieși dintr-o probă: P → R → N → D → B pe loc |
| modul de condus (Eco / Normal / Sport, EV) | probabil hibrid | ca mai sus, apăsând butoanele |
| turația motoarelor electrice MG1 / MG2 | probabil hibrid | se vede doar în mers; ar arăta cum împarte transmisia puterea |

Calculatoarele 780 (airbag), 790, 791, 7D0 și 7F1 au răspuns, dar ce dau nu pare
interesant pentru un bord (sau nu știm ce sunt).

## 6. Ce se poate socoti din ce avem deja (fără cereri noi)

| Ce | Din ce |
|---|---|
| ★ **autonomia rămasă**, km | combustibilul din rezervor × consumul mediu |
| ★ **procentul de drum pe baterie** | viteza + motorul termic oprit (e deja pe HIBRID) |
| energia recuperată la frânare, kWh | puterea bateriei cu semn minus (e deja pe HIBRID) |
| ★ **energia scoasă și băgată în baterie** pe drum | puterea bateriei în timp |
| ★ **sănătatea bateriei**, ca o singură notă | diferența dintre blocuri + rezistența medie, urmărite de la o zi la alta |
| de câte ori a pornit motorul termic pe drum | turația care trece din 0 în peste 0 |
| viteza medie, timpul de mers, timpul stat pe loc | viteza în timp |
| altitudinea și cât ai urcat / coborât | presiunea atmosferică |
| sănătatea bateriei de 12 V | tensiunea la pornire și cu motorul pornit (sub 12,4 V oprit = slăbită) |

## 7. Ce nu dă mașina (sau n-am găsit)

| Ce | De ce |
|---|---|
| ⛔ nivelul combustibilului pe calea standard | anunțat, dar răspunde „fără date”. **Îl luăm de la bord, 7C0** |
| ⛔ temperatura uleiului | nu e anunțată |
| ⛔ consumul raportat de motor | nu e anunțat; îl socotim din debitul de aer |
| kilometrajul total | negăsit; dacă există, e probabil la bord (7C0), în alt bloc |
| presiunea în pneuri | calculatoarele de caroserie (adresele 750+) n-au răspuns |
| ascultarea a ce își spun calculatoarele | prin priza OBD trece doar un mesaj (4E0): Lexus ține rețeaua internă după o poartă |

---

## Ce propun, în ordine

1. **Erorile** (memorate și în așteptare): un semn pe toate ecranele când apare ceva, iar vocea KITT spune codul. E cel mai util și nu costă nimic din viteza bordului.
2. **Autonomia** și **energia băgată/scoasă din baterie**: socotite, fără cereri noi.
3. **Pedala de accelerație** ca bară, lângă putere, pe ecranul de viteză.
4. **O probă scurtă de confirmare** (5 minute, pe loc și o tură prin parcare) pentru vitezele roților, frâna, volanul, accelerațiile și schimbătorul. Apoi un ecran „DINAMICA”: cele 4 roți, bila G, frâna electrică vs. discuri.
5. **Altitudinea**, din presiunea aerului.

Fiecare valoare nouă cerută des încetinește puțin restul. De aceea, ca la HIBRID, cele
rare se cer doar cât e deschis ecranul care le arată.
