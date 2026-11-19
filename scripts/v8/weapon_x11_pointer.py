#!/usr/bin/env python3
"""Deliver real X11 mouse motion to an isolated weapon-presentation test window."""

import argparse
import ctypes as ffi
import json
from pathlib import Path
import time


def bind(library, name, result, *arguments):
    method = getattr(library, name)
    method.restype = result
    method.argtypes = arguments
    return method


def drive(case, timeout):
    x11 = ffi.CDLL("libX11.so.6")
    xtest = ffi.CDLL("libXtst.so.6")
    ptr, xid, number = ffi.c_void_p, ffi.c_ulong, ffi.c_int
    int_ptr, xid_ptr = ffi.POINTER(number), ffi.POINTER(xid)
    open_display = bind(x11, "XOpenDisplay", ptr, ffi.c_char_p)
    close_display = bind(x11, "XCloseDisplay", number, ptr)
    root_window = bind(x11, "XDefaultRootWindow", xid, ptr)
    focus = bind(x11, "XGetInputFocus", number, ptr, xid_ptr, int_ptr)
    translate = bind(x11, "XTranslateCoordinates", number, ptr, xid, xid, number, number,
                     int_ptr, int_ptr, xid_ptr)
    move = bind(xtest, "XTestFakeMotionEvent", number, ptr, number, number, number, xid)
    sync = bind(x11, "XSync", number, ptr, number)
    display_file = case / "display.txt"
    request = case / "cubic-client-automation" / "weapon-native-pointer.json"
    done = case / "native.exit"
    deadline = time.monotonic() + timeout
    display = None
    last_event = None
    try:
        while not done.exists() and time.monotonic() < deadline:
            if display is None and display_file.is_file():
                name = display_file.read_text().strip()
                if name.isdecimal():
                    display = open_display(f":{name}".encode())
            if display is None or not request.is_file():
                time.sleep(.05)
                continue
            event = json.loads(request.read_text())
            if event == last_event:
                time.sleep(.05)
                continue

            window, x, y = (event[key] for key in ("x11Window", "x", "y"))
            if not (isinstance(window, int) and window > 1 and 0 <= x < 7680 and 0 <= y < 4320):
                raise ValueError("Pointer request must belong to the local test window")
            focused, mode = xid(), number()
            focus(display, ffi.byref(focused), ffi.byref(mode))
            if focused.value != window:
                time.sleep(.05)
                continue

            screen_x, screen_y, child = number(), number(), xid()
            if not translate(display, window, root_window(display), x, y,
                             ffi.byref(screen_x), ffi.byref(screen_y), ffi.byref(child)):
                raise ValueError("No input path from the game window to its isolated display")
            if not move(display, -1, screen_x.value, screen_y.value, 0):
                raise ValueError("XTEST did not admit native mouse motion")
            sync(display, 0)
            last_event = event
            print(json.dumps({**event, "rootX": screen_x.value, "rootY": screen_y.value}), flush=True)
    finally:
        if display is not None:
            close_display(display)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("case", type=Path)
    parser.add_argument("--timeout", type=int, default=320)
    args = parser.parse_args()
    drive(args.case, args.timeout)


if __name__ == "__main__":
    main()
