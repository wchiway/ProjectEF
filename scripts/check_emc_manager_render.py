"""Guard the compiled EMC screen against drawing a second background over its labels.

Run gradlew build first. This checks render-call ordering, not visual game output.
"""

from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[1]
SCREEN = "moze_intel.projecte.gameObjs.gui.EMCManagerScreen"


def method_body(bytecode, name):
    match = re.search(
        rf"(?ms)^  public void {name}\([^\n]*\);\r?\n(.*?)(?=^  (?:public|protected|private)|^}})",
        bytecode,
    )
    assert match, f"Missing compiled method: {name}"
    return match.group(1)


def main():
    result = subprocess.run(
        ["javap", "-c", "-p", "-classpath", str(ROOT / "build/classes/java/main"), SCREEN],
        check=True,
        capture_output=True,
        text=True,
        # Only ASCII method names are inspected; javap's string constants use the local console encoding.
        encoding="ascii",
        errors="replace",
    )
    render = method_body(result.stdout, "render")
    background = method_body(result.stdout, "renderBackground")
    assert render.count("Screen.render:") == 1, "Render widgets/background exactly once"
    assert "renderBackground:" not in render, "Do not redraw the background inside render()"
    assert "GuiGraphics.fill:" not in render, "Panel fills belong in renderBackground()"
    assert render.index("Screen.render:") < render.index("GuiGraphics.drawCenteredString:"), "Labels must follow the base render pass"
    assert background.count("Screen.renderBackground:") == 1, "Draw the vanilla background once"
    assert background.index("Screen.renderBackground:") < background.index("GuiGraphics.fill:"), "Panel must follow the vanilla background"
    print("PASS: compiled rendering order is background -> widgets -> labels, with no second background pass")


if __name__ == "__main__":
    main()
