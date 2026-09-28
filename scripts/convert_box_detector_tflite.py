"""
Exporta modelul antrenat al detectorului de cutii (`train_box_detector.py`, `best.pt`) la
`.tflite` via `litert_torch` — calea REALA, actuala, folosita intern de Ultralytics 8.4+ pt.
`model.export(format="tflite")`, NU vechiul pipeline PyTorch->ONNX->onnx2tf->TFLite (inlocuit
intern de Ultralytics insasi la un moment dat — vezi CLAUDE.md, "Bug real gasit + fixat" mai jos
pt. detalii complete).

DE CE NU mai folosim `onnx2tf` pt. acest model (schimbare fata de `convert_medication_embedder_tflite.py`,
care ramane pe onnx2tf — vezi mai jos DE CE embedder-ul e OK): prima incercare (export ONNX +
onnx2tf, ca la embedder) a produs un `.tflite` care rula PERFECT in Python
(`tf.lite.Interpreter`, verificat cu inferenta reala, nu doar shape) dar CRAPA NATIV (SIGSEGV) pe
device la rulare prin `com.google.ai.edge.litert.CompiledModel` — runtime-ul Android, mai strict
decat interpretorul Python clasic. Cauza reala, gasita citind sursa Ultralytics instalata local
(`ultralytics/utils/export/litert.py`): incepand cu o versiune recenta, Ultralytics NU mai
foloseste onnx2tf pt. LiteRT — foloseste `litert_torch.convert()` (conversie PyTorch->LiteRT
directa, FARA ONNX/TensorFlow intermediar), cu fix-uri EXPLICITE de compatibilitate pt. capul de
detectie YOLO (`Detect`/`Segment`): inlocuieste index-uri int64 cu int32 (delegate-urile GPU nu
accepta int64) si evita `GATHER_ND` (neimplementat de delegate-urile GPU) — exact genul de operatii
care pot crapa nativ un runtime compilat strict, dar pe care un interpretor flexibil (Python)
le tolereaza oricum. Modelul COCO original (Faza 3a-ii/iii) mersese pt. ca fusese exportat prin
`model.export(format="tflite")` (Ultralytics, deci deja prin acest pipeline `litert_torch` la acea
vreme) — pipeline-ul nostru manual onnx2tf (adaugat in aceasta sesiune doar din cauza restrictiei
Linux/macOS a exportului `tflite` direct — vezi mai jos) l-a ocolit din greseala, si tocmai asta a
fost bug-ul.

DE CE embedder-ul (`convert_medication_embedder_tflite.py`) RAMANE pe onnx2tf, fara aceasta
problema: e un backbone simplu (MobileNetV3 + linear + L2-norm), FARA cap de detectie Ultralytics
(`Detect`/`Segment`) — nu are gather/top-k/index-uri int64 problematice, deci onnx2tf produce un
graf compatibil cu `CompiledModel`. Doar modelele cu cap Ultralytics YOLO (detectie/segmentare) au
nevoie de `litert_torch`.

DE CE ocolim restrictia Linux/macOS (`assert MACOS or (LINUX and not ARM64)` in
`ultralytics/engine/exporter.py`): e un `assert` la nivel de Ultralytics, NU o limitare reala a
`litert_torch` insusi — pachetul are wheel Windows/Python 3.14 (`pip index versions litert-torch`
rezolva normal). La fel ca ocolirea similara pt. `format="tflite"` clasic (blocata doar de
Ultralytics, `.tflite`-ul rezultat rulase normal pe orice platforma) — aici monkey-patch-uim
`ultralytics.engine.exporter.LINUX = True` doar cat dureaza apelul, ca sa lasam Ultralytics sa-si
faca toata pregatirea interna (fuse, eval, tracing, metadata) — nu reimplementam manual acei pasi.

**LIMITARE CONFIRMATA (2026-09-25): exportul `litert_torch` NU merge local pe Windows.** Spre
deosebire de restrictia artificiala (`assert MACOS or LINUX`) ocolita mai sus, aceasta e o
limitare REALA: `litert-converter` (dependinta care face conversia efectiva, folosita intern de
`litert_torch`) NU are NICIUN build Windows pe PyPI, pe nicio versiune de Python (confirmat cu
`pip download litert-converter` -> "No matching distribution found", pe ambele venv-uri,
3.12 si 3.14). Exact ca la exportul `format="tflite"` clasic al modelului COCO original — tot
Colab, alternativa deja stabilita in acest proiect (vezi `export-yolo-seg-model.py`):

    (a) Google Colab (recomandat — zero instalare locala, gratuit):
        - colab.research.google.com -> notebook nou -> incarca `best.pt`
          (`scripts/artifacts/box_detector_run/weights/best.pt`, ~6MB) -> ruleaza:
              !pip install ultralytics
              from ultralytics import YOLO
              model = YOLO("best.pt")
              model.export(format="tflite", imgsz=640)
        - descarca fisierul `.tflite` rezultat, pune-l langa `best.pt` (sau oriunde local),
          apoi ruleaza acest script cu `--tflite <cale>` (sare peste exportul local, doar verifica
          + copiaza).

    (b) WSL2 — daca preferi local, e Linux real, deci `litert-converter` are build acolo.

RULARE LOCALA (functioneaza doar pe Linux/macOS/WSL2 — pe Windows nativ foloseste `--tflite` cu
fisierul descarcat din Colab, vezi mai sus). Acelasi venv `.venv-train` ca `train_box_detector.py`
— SPRE DEOSEBIRE de embedder, NU are nevoie de `.venv-tflite`/TensorFlow deloc, `litert_torch`
merge direct din PyTorch:

    scripts\\.venv-train\\Scripts\\activate
    pip install litert-torch ai-edge-litert

    python scripts/convert_box_detector_tflite.py

REZULTAT: `app/src/main/assets/medication_box_detector.tflite`. Verificarea de layout foloseste
`.venv-tflite` (TensorFlow, deja instalat din sesiunea anterioara) — fisierul `.tflite` nu e legat
de versiunea de Python care l-a produs, doar conversia insasi avea nevoie de `litert_torch`.

IMPORTANT — layout de input DIFERIT fata de conversia onnx2tf incercata initial: exportul
`litert_torch` (Colab, verificat empiric 2026-09-25) a iesit NCHW `[1,3,640,640]` (ca modelul
COCO original), NU NHWC ca incercarea onnx2tf. `YoloSegModel.kt::bitmapToNchwFloatArray` e deja
actualizat pt. acest layout — daca reconvertesti cu alta unealta, VERIFICA din nou, nu presupune.
"""

from __future__ import annotations

import argparse
import shutil
import subprocess
import sys
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
DEFAULT_WEIGHTS_PATH = SCRIPT_DIR / "artifacts" / "box_detector_run" / "weights" / "best.pt"
DEFAULT_ASSET_PATH = SCRIPT_DIR.parent / "app" / "src" / "main" / "assets" / "medication_box_detector.tflite"
IMAGE_SIZE = 640

NUM_CLASSES = 1
MASK_DIM = 32
NUM_ANCHORS = 8400
PROTO_SIZE = 160
DETECTION_CHANNELS = 4 + NUM_CLASSES + MASK_DIM


def export(weights_path: Path) -> Path:
    import ultralytics.engine.exporter as exporter_module

    # Bypass doar assert-ul de OS — litert_torch insusi ruleaza pe Windows (wheel confirmat).
    exporter_module.LINUX = True

    from ultralytics import YOLO

    model = YOLO(str(weights_path))
    exported_path = Path(model.export(format="tflite", imgsz=IMAGE_SIZE))
    print(f"\nModel LiteRT exportat la: {exported_path}")
    return exported_path


def verify_layout(tflite_path: Path) -> None:
    """Verifica layout-ul in `.venv-tflite` (TensorFlow) — subproces separat, script-ul principal
    ruleaza in `.venv-train` (fara TensorFlow)."""
    tflite_python = SCRIPT_DIR / ".venv-tflite" / "Scripts" / "python.exe"
    if not tflite_python.is_file():
        print(f"\n⚠️  Nu gasesc {tflite_python} — sar peste verificarea de layout (fa-o manual daca vrei).")
        return

    code = f"""
import tensorflow as tf
interp = tf.lite.Interpreter(model_path=r"{tflite_path}")
interp.allocate_tensors()
inp = interp.get_input_details()
outs = interp.get_output_details()
print("Input:", [(d['name'], list(d['shape'])) for d in inp])
print("Outputs:", [(d['name'], list(d['shape'])) for d in outs])
detection = next((d for d in outs if {DETECTION_CHANNELS} in list(d['shape']) and {NUM_ANCHORS} in list(d['shape'])), None)
proto = next((d for d in outs if {MASK_DIM} in list(d['shape']) and list(d['shape']).count({PROTO_SIZE}) == 2), None)
assert detection is not None, "Nu gasesc tensorul de detectie cu shape-ul asteptat"
assert proto is not None, "Nu gasesc tensorul de proto-masti cu shape-ul asteptat"
det_cf = list(detection['shape'])[1] == {DETECTION_CHANNELS}
proto_cf = list(proto['shape'])[1] == {MASK_DIM}
print(f"Detectie channel-first: {{det_cf}}, proto channel-first: {{proto_cf}}")
if not det_cf or not proto_cf:
    raise SystemExit("Layout DIFERIT de ce asteapta YoloOutputDecoder.kt/MaskDecoder.kt — verifica manual.")
print("Layout confirmat channel-first pe ambele tensoare.")
"""
    result = subprocess.run([str(tflite_python), "-c", code], capture_output=True, text=True)
    print(result.stdout)
    if result.returncode != 0:
        print(result.stderr, file=sys.stderr)
        raise SystemExit("Verificarea de layout a esuat — vezi eroarea de mai sus.")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--weights", type=Path, default=DEFAULT_WEIGHTS_PATH)
    parser.add_argument(
        "--tflite", type=Path, default=None,
        help="Fisier .tflite deja exportat (ex. din Colab, vezi docstring) — sare peste exportul local (esueaza oricum pe Windows nativ), doar verifica+copiaza."
    )
    parser.add_argument("--asset-path", type=Path, default=DEFAULT_ASSET_PATH)
    parser.add_argument("--skip-copy", action="store_true")
    parser.add_argument("--skip-verify", action="store_true")
    return parser.parse_args()


def main() -> None:
    args = parse_args()

    if args.tflite is not None:
        if not args.tflite.is_file():
            raise SystemExit(f"Nu gasesc fisierul .tflite: {args.tflite}")
        tflite_path = args.tflite
    else:
        if not args.weights.is_file():
            raise SystemExit(f"Nu gasesc greutatile: {args.weights} (ruleaza intai train_box_detector.py)")
        tflite_path = export(args.weights)

    if not args.skip_verify:
        verify_layout(tflite_path)

    if not args.skip_copy:
        args.asset_path.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(tflite_path, args.asset_path)
        print(f"\nCopiat la: {args.asset_path}")
    else:
        print(f"\n--skip-copy: NU am suprascris {args.asset_path}.")


if __name__ == "__main__":
    main()
