"""Port serial pe Windows, fara pyserial: direct pe API-ul Windows prin ctypes.

Un port Bluetooth SPP (adaptorul ELM327 imperecheat) apare ca un COM obisnuit.
Viteza nu conteaza la Bluetooth, dar o setam oricum, ca driverul sa fie fericit.
"""

from __future__ import annotations

import ctypes
import ctypes.wintypes as w
import winreg

GENERIC_READ = 0x80000000
GENERIC_WRITE = 0x40000000
OPEN_EXISTING = 3
FILE_ATTRIBUTE_NORMAL = 0x80
INVALID_HANDLE_VALUE = ctypes.c_void_p(-1).value

PURGE_RXCLEAR = 0x0008
PURGE_TXCLEAR = 0x0004


class DCB(ctypes.Structure):
    _fields_ = [
        ("DCBlength", w.DWORD),
        ("BaudRate", w.DWORD),
        ("fFlags", w.DWORD),
        ("wReserved", w.WORD),
        ("XonLim", w.WORD),
        ("XoffLim", w.WORD),
        ("ByteSize", ctypes.c_ubyte),
        ("Parity", ctypes.c_ubyte),
        ("StopBits", ctypes.c_ubyte),
        ("XonChar", ctypes.c_char),
        ("XoffChar", ctypes.c_char),
        ("ErrorChar", ctypes.c_char),
        ("EofChar", ctypes.c_char),
        ("EvtChar", ctypes.c_char),
        ("wReserved1", w.WORD),
    ]


class COMMTIMEOUTS(ctypes.Structure):
    _fields_ = [
        ("ReadIntervalTimeout", w.DWORD),
        ("ReadTotalTimeoutMultiplier", w.DWORD),
        ("ReadTotalTimeoutConstant", w.DWORD),
        ("WriteTotalTimeoutMultiplier", w.DWORD),
        ("WriteTotalTimeoutConstant", w.DWORD),
    ]


k32 = ctypes.WinDLL("kernel32", use_last_error=True)


def porturi_com() -> list[tuple[str, str]]:
    """Porturile COM din registri, cu o descriere scurta."""
    gasite: list[tuple[str, str]] = []
    try:
        cheie = winreg.OpenKey(winreg.HKEY_LOCAL_MACHINE, r"HARDWARE\DEVICEMAP\SERIALCOMM")
    except OSError:
        return gasite
    with cheie:
        i = 0
        while True:
            try:
                nume, valoare, _ = winreg.EnumValue(cheie, i)
            except OSError:
                break
            gasite.append((valoare, nume))
            i += 1
    return sorted(gasite)


class SerialWin:
    """Deschide un COM si schimba text cu el, cu timeout pe citire."""

    def __init__(self, port: str, baud: int = 38400, timeout_ms: int = 2000) -> None:
        self.port = port
        self.baud = baud
        self.timeout_ms = timeout_ms
        self._h = None

    def deschide(self) -> None:
        cale = port_lung(self.port)
        h = k32.CreateFileW(
            ctypes.c_wchar_p(cale),
            w.DWORD(GENERIC_READ | GENERIC_WRITE),
            w.DWORD(0),
            None,
            w.DWORD(OPEN_EXISTING),
            w.DWORD(FILE_ATTRIBUTE_NORMAL),
            None,
        )
        if h == INVALID_HANDLE_VALUE or h is None:
            cod = ctypes.get_last_error()
            raise OSError(f"Nu pot deschide {self.port} (eroare Windows {cod}). "
                          "Adaptorul e imperecheat si in raza? Alt program il tine ocupat?")
        self._h = w.HANDLE(h)

        dcb = DCB()
        dcb.DCBlength = ctypes.sizeof(DCB)
        if not k32.GetCommState(self._h, ctypes.byref(dcb)):
            self.inchide()
            raise OSError(f"GetCommState a picat pe {self.port}")
        dcb.BaudRate = self.baud
        dcb.ByteSize = 8
        dcb.Parity = 0
        dcb.StopBits = 0
        dcb.fFlags = 1  # fBinary
        if not k32.SetCommState(self._h, ctypes.byref(dcb)):
            self.inchide()
            raise OSError(f"SetCommState a picat pe {self.port}")

        t = COMMTIMEOUTS()
        t.ReadIntervalTimeout = 50
        t.ReadTotalTimeoutMultiplier = 0
        t.ReadTotalTimeoutConstant = 60
        t.WriteTotalTimeoutMultiplier = 0
        t.WriteTotalTimeoutConstant = 1000
        k32.SetCommTimeouts(self._h, ctypes.byref(t))
        self.goleste()

    def goleste(self) -> None:
        if self._h:
            k32.PurgeComm(self._h, w.DWORD(PURGE_RXCLEAR | PURGE_TXCLEAR))

    def scrie(self, text: str) -> None:
        if not self._h:
            raise OSError("portul nu e deschis")
        date = text.encode("ascii", "ignore")
        scrise = w.DWORD(0)
        if not k32.WriteFile(self._h, date, w.DWORD(len(date)), ctypes.byref(scrise), None):
            raise OSError(f"WriteFile a picat (eroare {ctypes.get_last_error()})")

    def _citeste_bloc(self, n: int = 256) -> bytes:
        buf = ctypes.create_string_buffer(n)
        citite = w.DWORD(0)
        if not k32.ReadFile(self._h, buf, w.DWORD(n), ctypes.byref(citite), None):
            raise OSError(f"ReadFile a picat (eroare {ctypes.get_last_error()})")
        return buf.raw[: citite.value]

    def comanda(self, cmd: str, timeout_ms: int | None = None) -> str:
        """Trimite comanda si citeste pana la promptul '>'."""
        import time

        limita = time.monotonic() + (timeout_ms or self.timeout_ms) / 1000.0
        self.goleste()
        self.scrie(cmd + "\r")
        bucati: list[str] = []
        while time.monotonic() < limita:
            bloc = self._citeste_bloc()
            if not bloc:
                continue
            text = bloc.decode("ascii", "replace")
            bucati.append(text)
            if ">" in text:
                return "".join(bucati).split(">")[0]
        return "".join(bucati) + "  <<TIMEOUT>>"

    def inchide(self) -> None:
        if self._h:
            k32.CloseHandle(self._h)
            self._h = None

    def __enter__(self) -> "SerialWin":
        self.deschide()
        return self

    def __exit__(self, *_) -> None:
        self.inchide()


def port_lung(port: str) -> str:
    """De la COM10 in sus, Windows cere forma lunga de port."""
    p = port.upper()
    if p.startswith("\\\\"):
        return p
    return rf"\.\{p}"
