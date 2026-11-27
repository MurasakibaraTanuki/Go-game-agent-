"""Black-box check on an Android emulator or attached test phone. The app data is reset."""
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path

PACKAGE = "org.gomasters.mobile"


def adb(*args, check=True):
    return subprocess.run(["adb", *args], capture_output=True, text=True, check=check).stdout


def snapshot():
    adb("shell", "uiautomator", "dump", "/sdcard/go-window.xml", check=False)
    raw = adb("shell", "cat", "/sdcard/go-window.xml", check=False)
    try:
        return list(ET.fromstring(raw).iter("node"))
    except ET.ParseError:
        return []


def wait_for(predicate, timeout=100):
    start = time.monotonic()
    while time.monotonic()-start < timeout:
        for node in snapshot():
            if predicate(node):
                return node
        time.sleep(.25)
    raise AssertionError("Timed out waiting for UI: " + str([(n.get("text"), n.get("content-desc")) for n in snapshot()]))


def board(fragment):
    return wait_for(lambda n: n.get("content-desc", "").startswith("Доска го") and fragment in n.get("content-desc"))


def label(text):
    return wait_for(lambda n: text in n.get("text", ""))


def bounds(node):
    return list(map(int, re.findall(r"-?\d+", node.get("bounds"))))


def tap(x, y):
    adb("shell", "input", "tap", str(round(x)), str(round(y)))


def center(node):
    l,t,r,b=bounds(node)
    tap((l+r)/2,(t+b)/2)


def launch():
    adb("shell","am","start","-W","-n",PACKAGE+"/.MainActivity")
    label("Ваш ход")


def main():
    apk = Path("android/out/GoMasters-Android.apk")
    adb("install", "-r", str(apk))
    adb("shell", "pm", "clear", PACKAGE)
    adb("shell", "cmd", "connectivity", "airplane-mode", "enable")
    try:
        assert adb("shell", "settings", "get", "global", "airplane_mode_on").strip() == "1"
        launch()
        l,t,r,b=bounds(board("Ходов: 0."))
        x,y=(l+r)/2,(t+b)/2
        tap(x,y)
        board("Выбран пункт: K10.")
        # Choose a neighbor after zoom and commit it with a second tap there.
        x+=min(r-l,b-t)*2.6/19.2
        tap(x,y)
        candidate=board("Выбран пункт: L10.")
        assert "Ходов: 0." in candidate.get("content-desc")
        tap(x,y)
        board("Ходов: 2.")
        center(wait_for(lambda n: n.get("content-desc")=="Часы и меню партии"))
        center(label("Режим фокуса"))
        assert not any("Меню" in n.get("text","") for n in snapshot())
        center(wait_for(lambda n: n.get("content-desc")=="Часы и меню партии"))
        center(label("Выйти из фокуса"))
        label("Ваш ход")
        adb("shell","am","force-stop",PACKAGE)
        launch()
        board("Ходов: 2.")
        print("Passed offline cold start, touch preview, adjustment, confirmation, real Human SL reply, focus and restore.")
    finally:
        adb("shell", "cmd", "connectivity", "airplane-mode", "disable", check=False)


if __name__ == "__main__":
    main()
