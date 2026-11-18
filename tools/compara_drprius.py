"""Compara proba de baterie KITT cu jurnalul Dr Prius (BatteryMonitorLog.csv).

Folosire: python tools/compara_drprius.py <KittDash-proba-baterie-...txt> <BatteryMonitorLog.csv>
"""
from __future__ import annotations
import csv, re, sys, statistics as st

def octeti(raspuns, antet):
    cadre = re.findall(r"\b[0-9A-F]{3}((?: [0-9A-F]{2}(?![0-9A-F]))+)", raspuns.upper())
    date, total = [], -1
    for rest in cadre:
        b = [int(x, 16) for x in rest.split()]
        if b[0] >> 4 == 0:
            date = b[1:1 + (b[0] & 15)]; total = len(date)
        elif b[0] >> 4 == 1:
            total = ((b[0] & 15) << 8) | b[1]; date = b[2:]
        elif b[0] >> 4 == 2:
            date += b[1:]
    if total >= 0: date = date[:total]
    h = "".join(f"{x:02X}" for x in date)
    p = h.find(antet)
    if p < 0 or p % 2: return None
    r = h[p + len(antet):]
    return [int(r[i:i+2], 16) for i in range(0, len(r) - 1, 2)]

def kitt(cale):
    tururi = []
    for bucata in open(cale, encoding="utf-8").read().split("--- turul ")[1:]:
        cap = bucata.splitlines()[0]
        t = {"ora": cap.split(", ")[1]}
        for rand in bucata.splitlines():
            m = re.match(r"^([0-9A-F]{4}) -> (.*)$", rand)
            if m: t[m.group(1)] = m.group(2)
        tururi.append(t)
    return tururi

def v2(b, i): return b[i] * 256 + b[i + 1]

def main(fk, fd):
    K = kitt(fk)
    D = list(csv.DictReader(open(fd, encoding="utf-8-sig")))
    print(f"KITT: {len(K)} tururi {K[0]['ora']}..{K[-1]['ora']};  Dr Prius: {len(D)} randuri {D[0]['DateTime']}..{D[-1]['DateTime']}")

    kv, kir, kt, ksoc, ki = [], [], [], [], []
    for t in K:
        b = octeti(t.get("2181", ""), "6181")
        if b and len(b) >= 34: kv.append([v2(b, 2*i) * 79.99 / 65535 for i in range(17)])
        b = octeti(t.get("2195", ""), "6195")
        if b and len(b) >= 17: kir.append(b[:17])
        b = octeti(t.get("2187", ""), "6187")
        if b and len(b) >= 14: kt.append([v2(b, 2*i) * 255.9 / 65535 - 50 for i in range(7)])
        b = octeti(t.get("015B", ""), "415B")
        if b: ksoc.append(b[0] * 100 / 255)
    dv = [[float(r[f"Vol#{i}"]) for i in range(1, 18)] for r in D]
    dir_ = [[int(float(r[f"IR#{i}"])) for i in range(1, 18)] for r in D]
    dt = [[float(r[f"Batt Temp#{i}(C)"]) for i in range(1, 4)] for r in D]
    dsoc = [float(r["SOC(%)"]) for r in D]

    print("\n== TENSIUNI: forma (abaterea fiecarui bloc fata de media lui de tur, mV), medie pe toata proba")
    def forma(serii):
        return [st.mean(s[i] - st.mean(s) for s in serii) * 1000 for i in range(17)]
    fk_, fd_ = forma(kv), forma(dv)
    print("bloc   " + " ".join(f"{i:>5}" for i in range(1, 18)))
    print("KITT   " + " ".join(f"{x:5.0f}" for x in fk_))
    print("DrPr   " + " ".join(f"{x:5.0f}" for x in fd_))
    print("corelatie forma:", round(st.correlation(fk_, fd_), 3))
    print("cel mai slab  KITT", 1 + fk_.index(min(fk_)), " DrPr", 1 + fd_.index(min(fd_)))
    print("cel mai tare  KITT", 1 + fk_.index(max(fk_)), " DrPr", 1 + fd_.index(max(fd_)))
    print(f"media bloc KITT {st.mean(map(st.mean, kv)):.3f} V   DrPr {st.mean(map(st.mean, dv)):.3f} V")
    # pasul de rezolutie al Dr Prius
    pasi = sorted({round(abs(a - b), 3) for r in dv for a in r for b in r if a != b})
    print("cei mai mici pasi Dr Prius (V):", pasi[:5])
    # ultimul tur KITT vs primul rand Dr Prius si randurile cu viteza 0
    zero = [r for r, x in zip(dv, D) if float(x["Vehicle Speed(KPH)"]) == 0]
    print(f"randuri Dr Prius cu masina pe loc: {len(zero)}")

    print("\n== REZISTENTE (mOhm)")
    for nume, s in (("KITT inceput", kir[0]), ("KITT sfarsit", kir[-1]), ("DrPr inceput", dir_[0]), ("DrPr sfarsit", dir_[-1])):
        print(f"{nume:13} " + " ".join(f"{x:3}" for x in s))
    print("valori KITT pe proba:", sorted({x for s in kir for x in s}), " DrPr:", sorted({x for s in dir_ for x in s}))

    print("\n== TEMPERATURI (°C)")
    print("KITT inceput ", " ".join(f"{x:6.2f}" for x in kt[0]))
    print("KITT sfarsit ", " ".join(f"{x:6.2f}" for x in kt[-1]))
    print("DrPr inceput ", " ".join(f"{x:6.2f}" for x in dt[0]))
    print("DrPr sfarsit ", " ".join(f"{x:6.2f}" for x in dt[-1]))
    print("valori distincte DrPr #1:", sorted({x[0] for x in dt}))

    print("\n== SOC")
    print(f"KITT {min(ksoc):.1f}..{max(ksoc):.1f}   DrPr {min(dsoc):.2f}..{max(dsoc):.2f}  (DrPr x255/100: {sorted({round(x*2.55,2) for x in dsoc})})")

    print("\n== LIMITE si alte coloane Dr Prius")
    for col in ("Charge Limit(HP)", "Discharge Limit(HP)", "Aux volt", "Delta SOC(%)"):
        vals = [float(r[col]) for r in D]
        print(f"{col:22} {min(vals)} .. {max(vals)}   distincte: {sorted(set(vals))[:8]}")

if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
