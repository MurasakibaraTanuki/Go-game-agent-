"""Play and review an offline game on an Android emulator or test phone."""
import argparse
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path

PACKAGE = "org.gomasters.mobile"
UI_DUMP = "/data/local/tmp/go-window.xml"


def adb(*args, check=True):
    return adb_result(*args, check=check).stdout


def adb_result(*args, check):
    return subprocess.run(["adb", *args], capture_output=True, encoding="utf-8", errors="replace", check=check)


def snapshot():
    adb("shell", "rm", "-f", UI_DUMP)
    dump = adb_result("shell", "uiautomator", "dump", UI_DUMP, check=False)
    raw = adb("shell", "cat", UI_DUMP, check=False)
    try:
        nodes = list(ET.fromstring(raw).iter("node"))
    except ET.ParseError:
        nodes = []
    return nodes, (dump.stderr or dump.stdout).strip()


def fullscreen_confirmation(nodes):
    system_nodes = [node for node in nodes if node.get("package") == "com.android.systemui"]
    if any(node.get("text") == "Viewing full screen" for node in system_nodes):
        return next((node for node in system_nodes if node.get("text") == "Got it"), None)
    return None


def wait_for(predicate, timeout=100):
    start = time.monotonic()
    while True:
        nodes, diagnostic = snapshot()
        confirmation = fullscreen_confirmation(nodes)
        if confirmation is not None:
            center(confirmation)
        else:
            for node in nodes:
                if predicate(node):
                    return node
        if time.monotonic() - start >= timeout:
            break
        time.sleep(.25)
    labels = [(node.get("text"), node.get("content-desc")) for node in nodes]
    raise AssertionError(f"Timed out waiting for UI: {labels}. UI dump: {diagnostic}")


def board(fragment):
    return wait_for(lambda n: n.get("content-desc", "").startswith("Доска го") and fragment in n.get("content-desc"))


def label(text, timeout=100):
    return wait_for(lambda n: text in n.get("text", ""), timeout=timeout)


def exact(text, timeout=100):
    return wait_for(lambda n: text.casefold() == n.get("text", "").casefold(), timeout=timeout)


def menu(text):
    center(wait_for(lambda n: n.get("content-desc")=="Часы и меню партии"))
    center(exact(text))


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
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", type=Path, default=Path("android/out/GoMasters-Android.apk"))
    parser.add_argument("--reset-existing", action="store_true",
                        help="allow deleting the existing Go Masters game and app settings")
    args = parser.parse_args()
    if not args.apk.is_file():
        parser.error(f"APK not found: {args.apk}")

    paths = adb("shell", "pm", "path", PACKAGE, check=False)
    installed = any(line.startswith("package:") for line in paths.splitlines())
    if installed and not args.reset_existing:
        parser.error("Go Masters is already installed. Use a clean test device or pass --reset-existing.")
    if installed:
        adb("install", "-r", str(args.apk))
        if adb("shell", "pm", "clear", PACKAGE).strip() != "Success":
            raise RuntimeError("Could not clear Go Masters data on the test device")
    else:
        adb("install", str(args.apk))

    airplane_on = adb("shell", "settings", "get", "global", "airplane_mode_on").strip() == "1"
    try:
        if not airplane_on:
            adb("shell", "cmd", "connectivity", "airplane-mode", "enable")
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
        board("Ходов: 2.")
        nodes, diagnostic = snapshot()
        assert nodes, diagnostic
        assert not any("Меню" in n.get("text","") for n in nodes)
        center(wait_for(lambda n: n.get("content-desc")=="Часы и меню партии"))
        center(label("Выйти из фокуса"))
        label("Ваш ход")
        adb("shell","am","force-stop",PACKAGE)
        launch()
        board("Ходов: 2.")
        print("Passed offline cold start, touch preview, adjustment, confirmation, real Human SL reply, focus and restore.", flush=True)
        menu("Сдаться")
        center(exact("Отмена"))
        label("Ваш ход")
        menu("Сдаться")
        center(wait_for(lambda n: n.get("text","").casefold()=="сдаться" and n.get("clickable")=="true"))
        label("Партия завершена: вы сдались")
        adb("shell","am","force-stop",PACKAGE)
        adb("shell","am","start","-W","-n",PACKAGE+"/.MainActivity")
        label("Партия завершена: вы сдались")
        board("Ходов: 2.")
        menu("Просмотр партии / SGF")
        center(wait_for(lambda n:n.get("content-desc")=="Предыдущий ход"))
        board("Ходов: 1.")
        center(exact("Разбор"))
        # A real query for the position before move one, not mocked output.
        label("Перед ходом 1")
        label("KataGo предлагает",timeout=390)
        center(exact("Карта"))
        label("Прогноз владения")
        center(exact("Атари"))
        label("Групп с одним дыханием нет")
        center(exact("Варианты"))
        label("Возможные продолжения KataGo")
        center(wait_for(lambda n:n.get("text","").startswith("1. ") and "ходов" in n.get("text","")))
        label("Исходная позиция")
        center(exact("▶"))
        label("1 /")
        board("Ходов: 1.")
        center(exact("Закрыть"))
        center(exact("Закрыть"))
        center(exact("Закрыть"))
        label("Партия завершена: вы сдались")
        print("Passed resignation, restore, move review, native KataGo analysis, ownership, atari and variation navigation.",flush=True)
    except Exception:
        Path("android/out").mkdir(parents=True,exist_ok=True)
        Path("android/out/device-logcat.txt").write_text(adb("logcat","-d","-t","1500",check=False), encoding="utf-8")
        Path("android/out/device-ui.xml").write_text(adb("shell","cat",UI_DUMP,check=False), encoding="utf-8")
        with open("android/out/device-screen.png","wb") as file:
            subprocess.run(["adb","exec-out","screencap","-p"],stdout=file,check=False)
        raise
    finally:
        if not airplane_on:
            adb("shell", "cmd", "connectivity", "airplane-mode", "disable", check=False)


if __name__ == "__main__":
    main()
