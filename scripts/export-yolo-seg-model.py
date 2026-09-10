"""
Export YOLO11n-seg (preantrenat, clase COCO) la LiteRT (.tflite), pt. Faza 3a-ii (PillPronto).

DE CE un model preantrenat generic, nu unul antrenat pe cutii de medicamente: nu exista inca un
dataset propriu de cutii RO/UE (motiv documentat in CLAUDE.md — niciun dataset public nu exista).
Scopul acestui model NU e sa recunoasca medicamente — e sa VALIDEZE pipeline-ul tehnic (CameraX ->
inferenta on-device -> overlay) pe un model care oricum functioneaza, inainte sa investim in
antrenare custom. Cand va exista un dataset propriu, acest script se poate adapta usor (fine-tune
pe `yolo11n-seg.pt` in loc de folosirea lui direct).

RULARE (mediu SEPARAT de acest repo Android/Kotlin — are nevoie de Python, nu e parte din build-ul
Gradle):

    python -m venv .venv
    # Windows:
    .venv\\Scripts\\activate
    # macOS/Linux:
    source .venv/bin/activate

    pip install ultralytics

    python scripts/export-yolo-seg-model.py

Rezultat: fisierul exportat (ex. `yolo11n-seg_saved_model/yolo11n-seg_float32.tflite` sau similar,
in functie de versiunea ultralytics — verifica output-ul consolei) — copiaza-l in
`app/src/main/assets/` cand incepem Faza 3a-ii (inca NECONSUMAT de aplicatie in stadiul actual,
doar CameraX preview fara model).
"""

from ultralytics import YOLO

MODEL_NAME = "yolo11n-seg.pt"  # nano — cel mai mic/rapid din familia YOLO11-seg, potrivit pt. mobil
EXPORT_FORMAT = "tflite"  # LiteRT — succesorul TensorFlow Lite, runtime-ul recomandat curent pt. Android
IMAGE_SIZE = 640  # standard Ultralytics pt. export mobil (LiteRT/CoreML/QNN)


def main() -> None:
    model = YOLO(MODEL_NAME)  # descarca automat, o singura data, daca nu exista deja local
    exported_path = model.export(format=EXPORT_FORMAT, imgsz=IMAGE_SIZE)
    print(f"\nModel exportat la: {exported_path}")
    print("Copiaza fisierul .tflite rezultat in app/src/main/assets/ cand incepe Faza 3a-ii.")


if __name__ == "__main__":
    main()
