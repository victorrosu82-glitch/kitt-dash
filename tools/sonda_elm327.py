"""Sonda ELM327 - etapa 0 facuta de pe laptop, in masina.

Se conecteaza la adaptorul Viecar prin portul COM Bluetooth, intreaba masina
ce stie sa dea si scrie TOT intr-un jurnal. Nimic nu se scrie in masina:
numai comenzi de citire (modurile 01, 09 si cereri 21 catre calculatorul
hibrid, care sunt si ele doar citiri).

Folosire (din PowerShell, in masina, cu contactul pus pe READY):

    python tools\\sonda_elm327.py                 # alege singur portul
    python tools\\sonda_elm327.py --port COM5
    python tools\\sonda_elm327.py --fara-hibrid   # doar datele standard

La sfarsit arata calea jurnalului. Trimite-mi fisierul acela.
"""

from __future__ import annotations

import argparse
import datetime as dt
import pathlib
import sys
import time

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from serial_win import SerialWin, porturi_com  # noqa: E402

# ---------------------------------------------------------------- date fixe

INIT = [
    ("ATZ", "reset adaptor", 5000),
    ("ATE0", "fara ecou", 2000),
    ("ATL0", "fara linefeed", 2000),
    ("ATS0", "fara spatii", 2000),
    ("ATH0", "fara antete", 2000),
    ("ATSP0", "protocol automat", 2000),
    ("ATI", "versiunea adaptorului", 2000),
    ("ATRV", "tensiunea bateriei de 12 V", 2000),
]

# Modul 01: numele si formula, ca sa citim ce iese. Doar PID-uri standard.
PID_STANDARD = {
    0x04: ("Sarcina motor", "%", lambda b: b[0] * 100 / 255),
    0x05: ("Temp. lichid racire", "C", lambda b: b[0] - 40),
    0x0C: ("Turatie", "rot/min", lambda b: (b[0] * 256 + b[1]) / 4),
    0x0D: ("Viteza", "km/h", lambda b: b[0]),
    0x0F: ("Temp. aer admisie", "C", lambda b: b[0] - 40),
    0x10: ("Debit aer (MAF)", "g/s", lambda b: (b[0] * 256 + b[1]) / 100),
    0x11: ("Clapeta", "%", lambda b: b[0] * 100 / 255),
    0x1F: ("Timp de la pornire", "s", lambda b: b[0] * 256 + b[1]),
    0x2F: ("Nivel combustibil", "%", lambda b: b[0] * 100 / 255),
    0x33: ("Presiune barometrica", "kPa", lambda b: b[0]),
    0x42: ("Tensiune modul", "V", lambda b: (b[0] * 256 + b[1]) / 1000),
    0x46: ("Temp. ambianta", "C", lambda b: b[0] - 40),
    0x5C: ("Temp. ulei motor", "C", lambda b: b[0] - 40),
    0x5E: ("Consum motor", "L/h", lambda b: (b[0] * 256 + b[1]) / 20),
}

# Cereri catre calculatorul hibrid. NU decodam aici: scriem raspunsul brut.
# Formulele se confirma dupa ce vedem ce raspunde masina asta.
SONDE_HIBRID = [
    ("7E2", "2101", "bloc hibrid 1 (HV: SOC, curent, tensiune)"),
    ("7E2", "2102", "bloc hibrid 2"),
    ("7E2", "2103", "bloc hibrid 3 (MG1/MG2)"),
    ("7E2", "2104", "bloc hibrid 4"),
    ("7E2", "2105", "bloc hibrid 5"),
    ("7E2", "2106", "bloc hibrid 6"),
    ("7E3", "2181", "baterie HV - bloc 81 (tensiuni celule)"),
    ("7E3", "2182", "baterie HV - bloc 82"),
    ("7E3", "2183", "baterie HV - bloc 83 (temperaturi)"),
    ("7E0", "2101", "motor termic - bloc 1"),
]


class Jurnal:
    """Scrie si pe ecran, si in fisier."""

    def __init__(self, cale: pathlib.Path) -> None:
        self.f = cale.open("w", encoding="utf-8")
        self.cale = cale

    def __call__(self, text: str = "") -> None:
        print(text)
        self.f.write(text + "\n")
        self.f.flush()

    def inchide(self) -> None:
        self.f.close()


def curata(raspuns: str) -> str:
    """Raspunsul adaptorului, pe o singura linie, fara zgomot."""
    return " ".join(raspuns.replace("\r", " ").replace("\n", " ").split())


def octeti_din(raspuns: str, antet: str) -> list[int] | None:
    """Scoate octetii de date dintr-un raspuns de tip 41xx / 61xx."""
    text = curata(raspuns).replace(" ", "").upper()
    poz = text.find(antet)
    if poz < 0:
        return None
    hexul = text[poz + len(antet):]
    hexul = "".join(c for c in hexul if c in "0123456789ABCDEF")
    if len(hexul) % 2:
        hexul = hexul[:-1]
    return [int(hexul[i:i + 2], 16) for i in range(0, len(hexul), 2)]


def pid_suportate(s: SerialWin, jurnal: Jurnal) -> set[int]:
    """Intreaba masina ce PID-uri standard da, in grupe de 32 (0100, 0120...)."""
    gasite: set[int] = set()
    for baza in (0x00, 0x20, 0x40, 0x60, 0x80, 0xA0, 0xC0):
        cerere = "01{:02X}".format(baza)
        brut = s.comanda(cerere)
        jurnal("  {} -> {}".format(cerere, curata(brut)))
        octeti = octeti_din(brut, "41{:02X}".format(baza))
        if not octeti or len(octeti) < 4:
            break
        masca = int.from_bytes(bytes(octeti[:4]), "big")
        for i in range(32):
            if masca & (1 << (31 - i)):
                gasite.add(baza + i + 1)
        # Bitul din urma spune daca exista si grupa urmatoare.
        if not masca & 1:
            break
    return gasite


def masoara_viteza(s: SerialWin, jurnal: Jurnal, secunde: float = 5.0) -> None:
    """Cate raspunsuri pe secunda da adaptorul - de asta depinde cat de viu e bordul."""
    jurnal()
    jurnal("--- Cat de repede raspunde adaptorul ({:.0f} s pe 010D/010C) ---".format(secunde))
    t0 = time.monotonic()
    n = 0
    rele = 0
    while time.monotonic() - t0 < secunde:
        for cerere, antet in (("010D", "410D"), ("010C", "410C")):
            brut = s.comanda(cerere, timeout_ms=1500)
            n += 1
            if octeti_din(brut, antet) is None:
                rele += 1
    dt_s = time.monotonic() - t0
    jurnal("  {} cereri in {:.1f} s = {:.1f} raspunsuri/s, {} fara raspuns bun".format(
        n, dt_s, n / dt_s, rele))
    jurnal("  => cu 8 valori urmarite, fiecare se reinnoieste de ~{:.1f} ori/s".format(
        n / dt_s / 8))


def main() -> int:
    ap = argparse.ArgumentParser(description="Sonda ELM327 pentru KITT Dash")
    ap.add_argument("--port", help="portul COM al adaptorului, de ex. COM5")
    ap.add_argument("--fara-hibrid", action="store_true",
                    help="sare peste cererile catre calculatorul hibrid")
    ap.add_argument("--fara-viteza", action="store_true",
                    help="sare peste masurarea vitezei de raspuns")
    ap.add_argument("--simulator", action="store_true",
                    help="nu se conecteaza la nimic: foloseste ELM-ul fals, pentru proba")
    ap.add_argument("--jurnal", help="unde sa scrie jurnalul")
    arg = ap.parse_args()

    porturi = [] if arg.simulator else porturi_com()
    port = "SIMULATOR" if arg.simulator else arg.port
    if not port:
        if not porturi:
            print("Nu vad niciun port COM. Imperecheaza adaptorul din Setari >")
            print("Bluetooth, apoi 'Mai multe setari Bluetooth' > porturi COM.")
            return 2
        if len(porturi) == 1:
            port = porturi[0][0]
        else:
            print("Porturi COM gasite:")
            for p, nume in porturi:
                print("  {}  ({})".format(p, nume))
            print("\nAlege unul cu --port COMx")
            return 2

    if arg.jurnal:
        cale = pathlib.Path(arg.jurnal)
    else:
        cale = (pathlib.Path(__file__).resolve().parent.parent / "docs" /
                "sonda-{:%Y%m%d-%H%M}.txt".format(dt.datetime.now()))
    cale.parent.mkdir(parents=True, exist_ok=True)
    jurnal = Jurnal(cale)

    jurnal("=" * 72)
    jurnal("Sonda ELM327 - KITT Dash     {:%d.%m.%Y %H:%M}".format(dt.datetime.now()))
    jurnal("Port: {}".format(port))
    jurnal("Porturi COM vazute: " +
           ", ".join("{} ({})".format(p, n) for p, n in porturi))
    jurnal("=" * 72)

    if arg.simulator:
        from fals_elm327 import FalsElm327
        legatura = FalsElm327(intarziere_ms=5)
    else:
        legatura = SerialWin(port)

    try:
        with legatura as s:
            jurnal()
            jurnal("--- Initializare ---")
            for cmd, ce, tmo in INIT:
                brut = s.comanda(cmd, timeout_ms=tmo)
                jurnal("  {:<6} {:<30} -> {}".format(cmd, ce, curata(brut)))

            jurnal()
            jurnal("--- Protocolul ales (dupa prima cerere la masina) ---")
            brut = s.comanda("0100", timeout_ms=5000)
            jurnal("  0100   prima cerere                   -> {}".format(curata(brut)))
            jurnal("  ATDP   protocol                       -> {}".format(
                curata(s.comanda("ATDP"))))

            if "UNABLE TO CONNECT" in curata(brut).upper():
                jurnal()
                jurnal("!!! Adaptorul nu ajunge la masina. Contactul e pus pe READY?")
                jurnal("    Adaptorul e bine infipt in mufa OBD?")
                jurnal.inchide()
                print("\nJurnal: {}".format(cale))
                return 1

            jurnal()
            jurnal("--- Ce PID-uri standard spune masina ca da ---")
            suportate = pid_suportate(s, jurnal)
            jurnal("  {} PID-uri anuntate: ".format(len(suportate)) +
                   " ".join("{:02X}".format(p) for p in sorted(suportate)))

            jurnal()
            jurnal("--- Valorile standard, citite acum ---")
            for pid in sorted(PID_STANDARD):
                nume, unit, formula = PID_STANDARD[pid]
                anuntat = "da " if pid in suportate else "nu "
                brut = s.comanda("01{:02X}".format(pid))
                octeti = octeti_din(brut, "41{:02X}".format(pid))
                if octeti:
                    try:
                        valoare = "{:,.1f} {}".format(formula(octeti), unit)
                    except (IndexError, ValueError):
                        valoare = "octeti prea putini"
                else:
                    valoare = "FARA DATE"
                jurnal("  01{:02X}  anuntat:{} {:<22} {:<18} brut: {}".format(
                    pid, anuntat, nume, valoare, curata(brut)))

            if not arg.fara_viteza:
                masoara_viteza(s, jurnal)

            if not arg.fara_hibrid:
                jurnal()
                jurnal("--- Calculatorul hibrid (raspunsuri BRUTE, nedecodate) ---")
                jurnal("    Aici conteaza daca adaptorul executa ATSH. Daca toate dau")
                jurnal("    'NO DATA' sau '?', clona nu schimba adresa si trebuie alt adaptor.")
                for adresa, cerere, ce in SONDE_HIBRID:
                    r_sh = curata(s.comanda("ATSH" + adresa))
                    brut = s.comanda(cerere, timeout_ms=4000)
                    jurnal("  ATSH{} [{}]  {}  {}".format(adresa, r_sh, cerere, ce))
                    jurnal("      -> {}".format(curata(brut)))
                # Lasam adaptorul cum l-am gasit.
                s.comanda("ATSH7E0")
                s.comanda("ATZ", timeout_ms=5000)

            jurnal()
            jurnal("--- Numarul de identificare (VIN) ---")
            jurnal("  0902 -> {}".format(curata(s.comanda("0902", timeout_ms=5000))))

    except OSError as e:
        jurnal()
        jurnal("!!! {}".format(e))
        jurnal.inchide()
        print("\nJurnal: {}".format(cale))
        return 1

    jurnal()
    jurnal("Gata.")
    jurnal.inchide()
    print("\nJurnal scris in: {}".format(cale))
    print("Trimite-mi fisierul acesta.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
