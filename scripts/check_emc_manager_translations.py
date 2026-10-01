"""Check EMC Manager translations without requiring the disabled legacy JUnit suite."""

import json
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
PREFIX = "gui.projecte.emc_manager."
KEYBIND = "key.projecte.emc_manager"


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise AssertionError(f"Duplicate JSON key: {key}")
        result[key] = value
    return result


def load_language(relative_path):
    content = (ROOT / relative_path).read_text(encoding="utf-8")
    entries = json.loads(content, object_pairs_hook=unique_object)
    return {key: value for key, value in entries.items() if key.startswith(PREFIX) or key == KEYBIND}


def main():
    english = load_language("src/datagen/generated/assets/projecte/lang/en_us.json")
    chinese = load_language("src/main/resources/assets/projecte/lang/zh_cn.json")
    assert english, "No EMC Manager translations found"
    assert english.keys() == chinese.keys(), f"Language keys differ: {english.keys() ^ chinese.keys()}"
    for key in english:
        assert english[key] and chinese[key], f"Empty translation: {key}"
        assert re.findall(r"%(?:\d+\$)?s", english[key]) == re.findall(r"%(?:\d+\$)?s", chinese[key]), f"Placeholders differ: {key}"

    provider = (ROOT / "src/datagen/java/moze_intel/projecte/client/lang/PELangProvider.java").read_text(encoding="utf-8")
    generated = {}
    for key, value in re.findall(r'add\("((?:gui\.projecte\.emc_manager\.|key\.projecte\.emc_manager)[^"\\]*)",\s*("(?:[^"\\]|\\.)*")\);', provider):
        assert key not in generated, f"Duplicate datagen key: {key}"
        generated[key] = json.loads(value)
    assert generated == english, "Datagen English translations do not match shipped resources"

    screen = (ROOT / "src/main/java/moze_intel/projecte/gameObjs/gui/EMCManagerScreen.java").read_text(encoding="utf-8")
    required = {PREFIX + key for key in re.findall(r'\btext\("([^"]+)"', screen)}
    for first, second in re.findall(r'\btext\([^,\n]*\?\s*"([^"]+)"\s*:\s*"([^"]+)"', screen):
        required.update((PREFIX + first, PREFIX + second))
    response = (ROOT / "src/main/java/moze_intel/projecte/network/packets/to_client/EMCManagerResponsePKT.java").read_text(encoding="utf-8")
    statuses = re.search(r"public enum Status\s*\{([^;]+);", response).group(1)
    required.update(PREFIX + "status." + status.strip().lower() for status in statuses.split(","))
    required.add(KEYBIND)
    assert required <= english.keys(), f"Missing referenced translations: {required - english.keys()}"
    print(f"PASS: {len(english)} bilingual entries, unique JSON keys, placeholders, datagen parity and GUI/status coverage")


if __name__ == "__main__":
    main()
