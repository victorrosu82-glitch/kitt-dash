# Ce date dă Lexus NX 300h prin adaptorul Viecar

Cules pe 14.11.2026 cu sonda din aplicație (`docs/sonda-masina-20261114*.txt`),
confruntat cu Dr Prius (capturi + `BatteryMonitorLog.csv`).

Mașina are **două calculatoare** care răspund:

- **hibridul, adresa `7E2`** — bateria HV și pedala;
- **motorul termic, adresa `7E0`** — aerul, lambda, catalizatorul.

Unele PID-uri le dau amândouă; atunci valoarea se ia de la cel care o cunoaște
mai bine (viteza și turația de la motor, bateria de la hibrid).

În total **48 de PID-uri standard anunțate**. Atenție: *anunțat* nu înseamnă
*dă valori* — PID-ul `2F` (combustibil) e anunțat și totuși răspunde `NO DATA`.
Fiecare se confirmă la mașină înainte de a ajunge pe bord.

## Folosite deja pe bord

| PID | Calculator | Ce dă |
|---|---|---|
| `0D` | motor | viteza |
| `0C` | motor | turația |
| `04` | motor | sarcina motor |
| `11` | motor | poziția clapetei |
| `05` | motor | temperatura lichidului de răcire |
| `0F` | motor | temperatura aerului de admisie |
| `42` | motor | tensiunea de bord (12 V) |
| `1F` | motor | timpul de la pornire |
| `5B` | hibrid | cât a mai rămas din bateria HV |
| `9A` | hibrid | tensiunea, curentul și starea bateriei HV |
| `46` | motor | temperatura exterioară (consola) |
| `3C` | motor | temperatura catalizatorului (consola, în locul EGT) |
| `10` | motor | debitul de aer, din care se socotește consumul în l/h (consola) |

## Ce am putea mai pune

| PID | Calculator | Ce dă | De ce ar merita |
|---|---|---|---|
| `46` | amândouă | temperatura exterioară | lipsește de pe orice bord; e și semnul de polei |
| `10` | motor | debitul de aer (MAF) | din el iese **consumul instantaneu**, în l/h și l/100 km |
| `49` | hibrid | poziția pedalei de accelerație | cât apeși, nu cât s-a deschis clapeta |
| `4A` | hibrid | a doua pistă a pedalei | de control, pedalele au două piste |
| `33` | amândouă | presiunea barometrică | din ea iese altitudinea |
| `31` | amândouă | distanța de la ultima ștergere | poate ține loc de kilometraj de bord |
| `3C` | motor | temperatura catalizatorului | interesantă la rece, mai puțin în rest |
| `0B` | motor | presiunea în galeria de admisie | arată cât trage motorul |
| `43` | motor | sarcina absolută | altă măsură a efortului motorului |
| `45` | motor | clapeta relativă | |
| `0E` | motor | avansul la aprindere | |
| `06`, `07` | motor | corecțiile de amestec | sănătatea motorului, nu bord |
| `34` | motor | sonda lambda largă | |
| `51` | motor | tipul de combustibil | o dată, nu în timp real |

## Ce NU dă mașina

- **`2F` nivelul de combustibil** — anunțat, dar răspunde `NO DATA`. Confirmat de
  două ori. De aceea bordul scrie `---` la FUEL.
- **`5C` temperatura uleiului** — nu e anunțată.
- **`5E` consumul motorului** — nu e anunțat; de aceea consumul trebuie socotit
  din MAF.
- **Blocurile Toyota `2102`–`2105`** la `7E2` răspund „nu știu" (`7F 21 12`).
  Merg doar `2101` (24 de octeți) și `2106`.
- **Bateria la `7E3`** (`2181`–`2183`) — `NO DATA`.

## Temperaturile bateriei: de lămurit

Dr Prius le arată (20,3 / 21,5 / 17,9 °C în jurnalul din 14.11.2026, 22:39) și
le ia din blocul Toyota `2101`, pe care îl citim și noi. În octeții noștri se
văd candidați buni: `0x28` → 20,0 °C și `0x2B` → 21,5 °C dacă formula ar fi
*octet / 2*.

**Nu le punem pe bord până nu confirmăm.** Valorile lui Dr Prius au zecimale
care nu ies dintr-o împărțire la 2 (20,276), semn că aplică o curbă de termistor,
nu o formulă liniară. Confirmarea cere citiri în aceeași clipă din amândouă,
iar adaptorul ține o singură legătură Bluetooth — deci se face pe rând, cu
bateria la aceeași temperatură.

## Alte lucruri aflate din jurnalul Dr Prius

Jurnalul mai dă, tot din blocul Toyota, lucruri pe care încă nu le citim:

- **tensiunile celor 17 blocuri** ale bateriei, una câte una (16,09–16,26 V);
- **rezistența internă** a fiecărui bloc — sănătatea bateriei;
- **limitele de putere**: 32,2 CP la încărcare, 29,5 CP la descărcare. Astea ar
  fi capetele potrivite pentru bara de putere, în locul celor alese de noi
  (22 și 24 kW).
