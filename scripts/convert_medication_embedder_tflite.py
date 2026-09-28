"""
Converteste `medication_embedder.onnx` (exportat de `train_medication_embedder.py` /
`export_medication_embedder_onnx.py`) la `.tflite`, 100% LOCAL pe Windows — fara Colab.

DE CE un venv SEPARAT de cel de antrenare (`.venv-tflite`, Python 3.12, spre deosebire de
`.venv-train`, Python 3.14): `onnx2tf` depinde de `tensorflow`, care la data scrierii acestui
script NU are inca niciun pachet publicat pe PyPI pt. Python 3.14 (confirmat:
`pip index versions tensorflow` -> "No matching distribution found"). Aceasta e o limitare de
VERSIUNE DE PYTHON, nu de platforma — spre deosebire de exportul LiteRT al YOLO
(`export-yolo-seg-model.py`), unde Ultralytics arunca un `AssertionError` hard-codat pe orice
Windows nativ. `onnx2tf`/`tensorflow` nu au acea restrictie — ruleaza normal pe Windows, doar
au nevoie de un Python pe care TensorFlow chiar il suporta (3.12 la data scrierii).

RULARE (dupa ce ai rulat `train_medication_embedder.py` in `.venv-train`):

    py -3.12 -m venv scripts/.venv-tflite
    scripts\\.venv-tflite\\Scripts\\activate
    pip install onnx2tf onnx onnx-graphsurgeon sng4onnx tensorflow

    python scripts/convert_medication_embedder_tflite.py

REZULTAT: `scripts/artifacts/medication_embedder_tflite/medication_embedder_float32.tflite`,
copiat automat la `app/src/main/assets/medication_embedder_float32.tflite` (numele fix asteptat
de `MedicationEmbedderModel.kt`). Scriptul VERIFICA (nu presupune) layout-ul de input citind
`interpreter.get_input_details()` dupa conversie si opreste explicit daca nu e NHWC
`[1, 224, 224, 3]` — exact bug-ul real gasit la Faza 3a-ii (NCHW vs NHWC) trebuie evitat aici prin
verificare programatica, nu prin presupunere.
"""

from __future__ import annotations

import argparse
import shutil
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
DEFAULT_ONNX_PATH = SCRIPT_DIR / "artifacts" / "medication_embedder.onnx"
DEFAULT_TFLITE_OUTPUT_DIR = SCRIPT_DIR / "artifacts" / "medication_embedder_tflite"
# scripts/ -> "PillPronto App/assets"
DEFAULT_ASSET_PATH = SCRIPT_DIR.parent / "app" / "src" / "main" / "assets" / "medication_embedder_float32.tflite"

EXPECTED_INPUT_SHAPE = [1, 224, 224, 3]  # NHWC — vezi verificarea din preprocesarea Kotlin (MedicationEmbedderModel.kt)


def convert(onnx_path: Path, output_dir: Path) -> Path:
    import onnx2tf

    output_dir.mkdir(parents=True, exist_ok=True)
    onnx2tf.convert(
        input_onnx_file_path=str(onnx_path),
        output_folder_path=str(output_dir),
        output_signaturedefs=True,
        not_use_onnxsim=False,
    )

    tflite_path = output_dir / "medication_embedder_float32.tflite"
    if not tflite_path.is_file():
        candidates = sorted(output_dir.glob("*float32*.tflite"))
        if not candidates:
            raise SystemExit(f"onnx2tf nu a produs niciun .tflite float32 in {output_dir}")
        tflite_path = candidates[0]
    return tflite_path


def verify_input_layout(tflite_path: Path) -> None:
    import tensorflow as tf

    interpreter = tf.lite.Interpreter(model_path=str(tflite_path))
    interpreter.allocate_tensors()
    input_details = interpreter.get_input_details()
    output_details = interpreter.get_output_details()

    print("Input details:", input_details)
    print("Output details:", output_details)

    actual_shape = list(input_details[0]["shape"])
    if actual_shape != EXPECTED_INPUT_SHAPE:
        raise SystemExit(
            f"Layout de input neasteptat: {actual_shape}, asteptat {EXPECTED_INPUT_SHAPE} (NHWC).\n"
            "NU copia modelul in assets fara sa actualizezi preprocesarea Kotlin din "
            "MedicationEmbedderModel.kt sa corespunda cu acest layout real — vezi bug-ul NCHW vs "
            "NHWC de la Faza 3a-ii in CLAUDE.md."
        )
    print(f"\nLayout de input confirmat NHWC {actual_shape} — corespunde preprocesarii din MedicationEmbedderModel.kt.")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--onnx", type=Path, default=DEFAULT_ONNX_PATH)
    parser.add_argument("--output-dir", type=Path, default=DEFAULT_TFLITE_OUTPUT_DIR)
    parser.add_argument("--asset-path", type=Path, default=DEFAULT_ASSET_PATH)
    parser.add_argument("--skip-copy", action="store_true", help="Nu suprascrie assets/ automat")
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    if not args.onnx.is_file():
        raise SystemExit(f"Nu gasesc modelul ONNX: {args.onnx} (ruleaza intai train_medication_embedder.py)")

    tflite_path = convert(args.onnx, args.output_dir)
    print(f"\nModel TFLite generat la: {tflite_path}")

    verify_input_layout(tflite_path)

    if not args.skip_copy:
        args.asset_path.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(tflite_path, args.asset_path)
        print(f"\nCopiat la: {args.asset_path}")
        print(
            "\nUrmatorul pas: pe device, in EnrollMedicationScreen apasa \"Sterge galeria locala\" "
            "(embeddings vechi, alta dimensiune/model, NU sunt comparabile cu cele noi), apoi "
            "reinroleaza medicamentele si retesteaza recunoasterea."
        )
    else:
        print(f"\n--skip-copy: NU am suprascris {args.asset_path} (copiaza manual cand esti gata).")


if __name__ == "__main__":
    main()
