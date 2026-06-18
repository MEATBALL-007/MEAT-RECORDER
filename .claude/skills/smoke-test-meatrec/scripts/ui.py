#!/usr/bin/env python3
"""Drive the MEATrec UI over adb when screenshots are useless (swiftshader = black frame).

Uses `uiautomator dump` instead of screencap. Commands:

  ui.py labels            # dump + print every labelled node (text/desc) with bounds
  ui.py clickable         # dump + print every clickable node with bounds (chips often unlabelled)
  ui.py tap-text "Allow"  # dump, find a node whose text/desc == the arg, tap its centre
  ui.py tap 540 1076      # raw tap at x y

Exit code is non-zero if tap-text can't find the target, so it's scriptable.
"""
import os, re, subprocess, sys

ADB = os.path.join(os.environ.get("ANDROID_SDK_ROOT", os.path.expanduser("~/Library/Android/sdk")),
                   "platform-tools", "adb")

def adb(*a):
    return subprocess.run([ADB, *a], capture_output=True, text=True).stdout

def dump():
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    return adb("shell", "cat", "/sdcard/ui.xml")

def nodes(xml):
    for m in re.finditer(r"<node[^>]*>", xml):
        s = m.group(0)
        def g(k):
            mm = re.search(rf'{k}="([^"]*)"', s); return mm.group(1) if mm else ""
        b = re.findall(r"\d+", g("bounds"))
        cx = cy = None
        if len(b) == 4:
            cx, cy = (int(b[0]) + int(b[2])) // 2, (int(b[1]) + int(b[3])) // 2
        yield {"text": g("text"), "desc": g("content-desc"),
               "clickable": 'clickable="true"' in s, "bounds": g("bounds"), "cx": cx, "cy": cy}

def main():
    if len(sys.argv) < 2:
        print(__doc__); return 1
    cmd = sys.argv[1]
    if cmd == "tap" and len(sys.argv) == 4:
        adb("shell", "input", "tap", sys.argv[2], sys.argv[3]); return 0
    xml = dump()
    if cmd in ("labels", "clickable"):
        for n in nodes(xml):
            lbl = n["text"] or n["desc"]
            if (cmd == "labels" and lbl) or (cmd == "clickable" and n["clickable"]):
                flag = "C" if n["clickable"] else " "
                print(f'{flag} {lbl[:46]:48} {n["bounds"]}')
        return 0
    if cmd == "tap-text" and len(sys.argv) == 3:
        want = sys.argv[2]
        for n in nodes(xml):
            if want in (n["text"], n["desc"]) and n["cx"] is not None:
                adb("shell", "input", "tap", str(n["cx"]), str(n["cy"]))
                print(f'tapped "{want}" at {n["cx"]},{n["cy"]}'); return 0
        print(f'NOT FOUND: "{want}"'); return 2
    print(__doc__); return 1

if __name__ == "__main__":
    sys.exit(main())
