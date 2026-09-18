"""
Exporta la ONNX greutatile deja antrenate de `train_medication_embedder.py`, fara reantrenare.

Util cand exportul ONNX a picat separat de antrenare (ex. lipsea pachetul `onnxscript` cerut de
exportatorul "dynamo" din PyTorch recent) — antrenarea (partea lenta) ramane neatinsa, doar
reincarcam `.pt`-ul deja salvat si reexportam.

RULARE (acelasi mediu/venv ca `train_medication_embedder.py`):

    python scripts/export_medication_embedder_onnx.py
"""

from __future__ import annotations

import argparse
from pathlib import Path

import torch

from train_medication_embedder import DEFAULT_OUTPUT_DIR, EmbeddingNet, export_onnx


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--weights", type=Path, default=DEFAULT_OUTPUT_DIR / "medication_embedder.pt")
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT_DIR / "medication_embedder.onnx")
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    if not args.weights.is_file():
        raise SystemExit(f"Nu gasesc greutatile antrenate: {args.weights}")

    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    model = EmbeddingNet().to(device)
    model.load_state_dict(torch.load(args.weights, map_location=device))

    export_onnx(model, args.output, device)


if __name__ == "__main__":
    main()
