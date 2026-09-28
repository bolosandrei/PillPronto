"""
Fine-tune YOLO11n-seg pe clasa UNICA "cutie_medicament" (Faza 4c-ii, PillPronto) — transfer
learning de la greutatile COCO-pretrained deja folosite (`export-yolo-seg-model.py`), NU
antrenare de la zero.

DE CE clasa unica: acest detector NU face identificare de produs (aia ramane treaba
embeddings-urilor din Faza 4a-c-i) — face doar LOCALIZARE ("unde e o cutie in cadru"). Setul de
date (`scripts/artifacts/box_dataset/`, generat de `auto_annotate_boxes.py`) contine capturi din
toate cele 8 medicamente ca exemple ale ACELEIASI clase.

RULARE (venv .venv-train, acelasi ca `auto_annotate_boxes.py`):

    scripts\\.venv-train\\Scripts\\activate
    python scripts/train_box_detector.py

Ruleaza normal pe Windows — antrenarea Ultralytics NU are restrictia de platforma intalnita doar
la exportul `format="tflite"` (vezi `export-yolo-seg-model.py`). Exportul de aici e la ONNX
(`format="onnx"`), care nu are acea restrictie — verificat empiric la rulare.

REZULTAT: `scripts/artifacts/box_detector_run/weights/best.onnx` — urmatorul pas e
`scripts/convert_box_detector_tflite.py` (conversie locala ONNX->TFLite, ca la embedder).
"""

from __future__ import annotations

import argparse
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
DATASET_YAML = SCRIPT_DIR / "artifacts" / "box_dataset" / "data.yaml"
RUN_NAME = "box_detector_run"
BASE_MODEL = "yolo11n-seg.pt"
IMAGE_SIZE = 640  # standard Ultralytics, aceeasi rezolutie ca la modelul COCO deja folosit in app


def train(epochs: int, batch: int) -> Path:
    from ultralytics import YOLO

    model = YOLO(BASE_MODEL)
    model.train(
        data=str(DATASET_YAML),
        epochs=epochs,
        imgsz=IMAGE_SIZE,
        batch=batch,
        project=str(SCRIPT_DIR / "artifacts"),
        name=RUN_NAME,
        exist_ok=True,
        # Set mic (80 poze) — augmentari mai conservatoare decat default-ul Ultralytics (gandit pt.
        # seturi mari) reduc riscul de supra-invatare; fara flip orizontal, acelasi motiv ca la
        # embedder (train_medication_embedder.py) — textul de pe cutie nu apare niciodata oglindit.
        fliplr=0.0,
        degrees=10.0,
        translate=0.1,
        scale=0.3,
        patience=20,
    )

    best_pt = SCRIPT_DIR / "artifacts" / RUN_NAME / "weights" / "best.pt"
    if not best_pt.is_file():
        raise SystemExit(f"Antrenarea nu a produs {best_pt}")
    return best_pt


def export_onnx(weights_path: Path) -> None:
    from ultralytics import YOLO

    model = YOLO(str(weights_path))
    exported = model.export(format="onnx", imgsz=IMAGE_SIZE, opset=17)
    print(f"\nModel ONNX exportat la: {exported}")
    print("Urmatorul pas: python scripts/convert_box_detector_tflite.py")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--epochs", type=int, default=100)
    parser.add_argument("--batch", type=int, default=8)
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    if not DATASET_YAML.is_file():
        raise SystemExit(f"Nu gasesc setul de date: {DATASET_YAML} (ruleaza intai auto_annotate_boxes.py --finalize)")

    best_pt = train(args.epochs, args.batch)
    print(f"\nGreutati salvate la: {best_pt}")
    export_onnx(best_pt)


if __name__ == "__main__":
    main()
