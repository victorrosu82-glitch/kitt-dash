"""Analizeaza un raport de explorare KITT Dash: ce octeti se misca in citirile repetate.

Folosire:  python tools/analiza_explorare.py docs/explorare/KittDash-explorare-....txt

Pentru fiecare bloc citit repetat (pasul 5) asambleaza raspunsul din cadre,
scoate antetul si octetii de control, apoi arata octetii care s-au schimbat
intre tururi, cu valorile lor si cu viteza / turatia / bateria din acelasi tur.
"""

from __future__ import annotations

import re
import sys
from collections import defaultdict

LINIE = re.compile(r"^([0-9A-F]{4,6}) -> (.*)$")


def octeti(raspuns: str) -> list[int] | None:
    """Datele utile dintr-un raspuns cu antete (ATH1), fara antet si fara PCI."""
    cadre = re.findall(r"\b([0-9A-F]{3})((?: [0-9A-F]{2})+)", raspuns)
    if not cadre:
        return None
    date: list[int] = []
    total = None
    for _, rest in cadre:
        b = [int(x, 16) for x in rest.split()]
        pci = b[0] >> 4
        if pci == 0:  # cadru unic
            date = b[1 : 1 + (b[0] & 0x0F)]
            total = len(date)
        elif pci == 1:  # primul cadru
            total = ((b[0] & 0x0F) << 8) | b[1]
            date = b[2:]
        elif pci == 2:  # cadru urmator
            date += b[1:]
    if total is not None:
        date = date[:total]
    # un 7F 21 78 ("asteapta") inainte de raspunsul adevarat
    return date


def main(cale: str) -> None:
    text = open(cale, encoding="utf-8").read()
    sectiune = text.split("== 5.")[1].split("== 6.")[0]
    tururi = sectiune.split("--- turul ")[1:]
    serii: dict[tuple[str, str], list[list[int]]] = defaultdict(list)
    repere: list[dict[str, float]] = []
    for tur in tururi:
        adresa = None
        reper: dict[str, float] = {}
        for rand in tur.splitlines():
            if rand.startswith("ATSH"):
                adresa = rand.split()[0][4:]
                continue
            m = LINIE.match(rand)
            if not m:
                continue
            cerere, raspuns = m.groups()
            b = octeti(raspuns)
            if b is None:
                continue
            if cerere == "010D" and len(b) >= 3:
                reper["km/h"] = b[2]
            elif cerere == "010C" and len(b) >= 4:
                reper["rpm"] = (b[2] * 256 + b[3]) / 4
            elif cerere == "015B" and len(b) >= 3:
                reper["soc"] = round(b[2] * 100 / 255, 1)
            elif cerere == "019A" and len(b) >= 8:
                i = b[6] * 256 + b[7]
                i = i - 0x10000 if i >= 0x8000 else i
                reper["A"] = i / 10
            if cerere.startswith("21"):
                serii[(adresa or "?", cerere)].append(b)
        repere.append(reper)

    print(f"{len(tururi)} tururi")
    for i, r in enumerate(repere, 1):
        print(f"  turul {i:2}: {r}")
    print()
    for (adresa, cerere), lista in sorted(serii.items()):
        lung = min(len(x) for x in lista)
        miscati = [k for k in range(2, lung) if len({x[k] for x in lista}) > 1]
        if not miscati:
            continue
        print(f"{adresa} {cerere}  ({lung - 2} octeti de date, {len(lista)} citiri)")
        for k in miscati:
            valori = [x[k] for x in lista]
            print(f"   octetul {k - 2:2}: {' '.join(f'{v:02X}' for v in valori)}")


if __name__ == "__main__":
    main(sys.argv[1])
