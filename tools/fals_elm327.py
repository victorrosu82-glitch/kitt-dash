"""Un ELM327 fals, in Python, cu aceeasi purtare ca SerialWin.

Serveste la verificarea sondei fara masina. Imita si neajunsurile adaptorului:
'NO DATA' la PID-uri care lipsesc, '?' la comenzi necunoscute si cateva
blocuri hibride care raspund numai pe adresa 7E2.

Perechea lui in aplicatia Android e SimulatorObdLink.kt.
"""

from __future__ import annotations

import math
import time

# PID-uri pe care "masina" le da (ca la un Lexus hibrid: fara MAF, fara combustibil).
PID_DATE = {
    0x04: lambda t: [int(28 + 20 * math.sin(t / 7)) * 255 // 100],
    0x05: lambda t: [int(min(88, 22 + t * 1.5)) + 40],
    0x0C: lambda t: _doi((int(1400 + 900 * abs(math.sin(t / 11)))) * 4),
    0x0D: lambda t: [int(abs(math.sin(t / 23)) * 90)],
    0x0F: lambda t: [18 + 40],
    0x11: lambda t: [int((15 + 25 * abs(math.sin(t / 5))) * 255 / 100)],
    0x1F: lambda t: _doi(int(t)),
    0x33: lambda t: [101],
    0x42: lambda t: _doi(13900),
    0x46: lambda t: [11 + 40],
    0x5C: lambda t: [int(min(95, 25 + t)) + 40],
}

# Grupele de PID-uri anuntate ca suportate (bitii pentru PID_DATE).
GRUPE = {0x00: None, 0x20: None, 0x40: None}

BLOCURI_HIBRID = {
    ("7E2", "2101"): "6101000003E8" + "12B4" + "7FD8" + "0040" + "2C2D2B",
    ("7E2", "2102"): "610200FF12340000ABCD",
    ("7E2", "2103"): "6103" + "1A2B" + "3C4D" + "0000",
}


def _doi(v: int) -> list[int]:
    v = max(0, min(65535, int(v)))
    return [v // 256, v % 256]


def _masca(baza: int) -> str:
    """Masca de PID-uri suportate pentru grupa [baza, baza+32)."""
    biti = 0
    for i in range(32):
        pid = baza + i + 1
        if pid in PID_DATE:
            biti |= 1 << (31 - i)
    # Bitul din urma: exista si grupa urmatoare?
    if (baza + 0x20) in GRUPE:
        biti |= 1
    return "{:08X}".format(biti)


class FalsElm327:
    """Are aceeasi suprafata ca SerialWin: deschide / comanda / inchide."""

    def __init__(self, intarziere_ms: int = 0) -> None:
        self.intarziere = intarziere_ms / 1000.0
        self.adresa = "7E0"
        self.t0 = time.monotonic()
        self.port = "SIMULATOR"

    # --- aceeasi suprafata ca SerialWin -------------------------------

    def deschide(self) -> None:
        self.t0 = time.monotonic()

    def inchide(self) -> None:
        pass

    def __enter__(self) -> "FalsElm327":
        self.deschide()
        return self

    def __exit__(self, *_) -> None:
        self.inchide()

    def comanda(self, cmd: str, timeout_ms: int | None = None) -> str:
        if self.intarziere:
            time.sleep(self.intarziere)
        c = cmd.strip().upper().replace(" ", "")
        t = time.monotonic() - self.t0
        return self._raspunde(c, t) + "\r\r"

    # --- raspunsurile -------------------------------------------------

    def _raspunde(self, c: str, t: float) -> str:
        if c.startswith("ATSH"):
            self.adresa = c[4:]
            return "OK"
        if c == "ATZ":
            self.adresa = "7E0"
            return "ELM327 v1.5"
        if c == "ATI":
            return "ELM327 v1.5"
        if c == "ATDP":
            return "AUTO, ISO 15765-4 (CAN 11/500)"
        if c == "ATRV":
            return "13.9V"
        if c.startswith("AT"):
            return "OK"
        if c == "0902":
            return "490201314A544542414D4432"
        if c.startswith("01") and len(c) >= 4:
            return self._mod01(c, t)
        if c.startswith("21") and len(c) >= 4:
            cheie = (self.adresa, c[:4])
            return BLOCURI_HIBRID.get(cheie, "NO DATA")
        return "?"

    def _mod01(self, c: str, t: float) -> str:
        try:
            pid = int(c[2:4], 16)
        except ValueError:
            return "?"
        if pid in GRUPE:
            return "41{:02X}{}".format(pid, _masca(pid))
        if pid not in PID_DATE:
            return "NO DATA"
        octeti = PID_DATE[pid](t)
        return "41{:02X}".format(pid) + "".join(
            "{:02X}".format(max(0, min(255, int(o)))) for o in octeti)
