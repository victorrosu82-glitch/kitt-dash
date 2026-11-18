# Confirmarea datelor bateriei: KITT, apoi Dr Prius

Scopul: să știm sigur că ce citește KITT din calculatorul hibrid (724) e același
lucru cu ce arată Dr Prius, înainte să le punem pe ecranul HIBRID.

## Ce comparăm

| Ce | KITT (bloc, formulă propusă) | Dr Prius (coloana din jurnal) |
|---|---|---|
| tensiunile celor 17 blocuri | 2181, 2 octeți × 79,99 / 65535 V | `Vol#1` … `Vol#17` |
| rezistențele interne | 2195, 1 octet, mΩ | `IR#1` … `IR#17` |
| temperaturile bateriei | 2187, 7 valori, 2 octeți × 255,9 / 65535 − 50 °C | `Batt Temp#1..3` (doar 3) |
| încărcarea | 015B la 7E2, × 100 / 255 | `SOC(%)` |
| curentul | 019A la 7E2 | `Current(Amp)` |
| limitele de putere | necunoscut încă (căutăm în 2101, 2146, 214C) | `Charge Limit(HP)`, `Discharge Limit(HP)` |

Pe 14.11 Dr Prius arăta IR 22–23, iar KITT a citit pe 18.11 valori 0x12–0x14
(18–20). Ori formula e alta (de pildă × 1,2), ori bateria era mai caldă. Proba lămurește.

## Cum se face

Condițiile trebuie să fie **aceleași** pentru amândouă, pentru că tensiunea unui bloc
se schimbă cu încărcarea și cu sarcina:

1. Mașina pe loc, frâna de mână trasă, **READY**, clima și farurile oprite.
2. Așteaptă să se oprească motorul termic (dacă pornește să încarce, mai aștepți).
3. Pe telefon, în KITT: **„Proba baterie (2 min, pentru Dr Prius)”**, sub „Explorează mașina”. Citește 2 minute, la 2 secunde,
   numai blocurile de mai sus, și scrie fișierul `KittDash-proba-baterie-….txt` în Download.
4. Când KITT spune „gata”, apasă **Oprește** în KITT (adaptorul ține o singură legătură).
5. Pornește Dr Prius, intră pe ecranul bateriei și **pornește jurnalul**. Lasă-l 2 minute.
6. Oprește jurnalul Dr Prius și exportă `BatteryMonitorLog.csv` în Download.
7. Repetă o dată, invers (Dr Prius întâi, apoi KITT), ca să vedem dacă ordinea contează.
8. Legi telefonul; iau fișierele și rulez comparația.

Totul durează cam 10 minute.

## Ce înseamnă „confirmat”

- **tensiuni**: același bloc e cel mai slab și același e cel mai tare; diferența pe bloc
  sub 0,05 V, cu motorul oprit;
- **rezistențe**: aceeași ordine a blocurilor; o formulă unică pentru toate 17;
- **temperaturi**: trei din cele șapte valori KITT se potrivesc cu cele trei Dr Prius
  la ±0,5 °C. Celelalte patru rămân „alți senzori” (aerul de răcire, probabil);
- **limitele de putere**: găsim octeții care dau 32 și 29 CP; dacă da, ele devin
  capetele barei de putere.

Ce nu trece rămâne pe ecran marcat „de confirmat” sau nu apare deloc.
