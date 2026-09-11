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

**LIMITARE CONFIRMATA (testat live, 2026-09-11): exportul LiteRT NU merge pe Windows nativ.**
Ultralytics arunca `AssertionError: LiteRT export only supported on Linux x86 and macOS` — e o
restrictie hard-codata in unealta de export (nu un flag/pachet lipsa de reparat), .tflite-ul
REZULTAT ruleaza normal pe orice platforma (inclusiv Android) — doar procesul de export cere
Linux x86_64 sau macOS. Alternative pt. Windows (alege una):

    (a) Google Colab (recomandat — zero instalare locala, gratuit):
        - colab.research.google.com -> notebook nou -> ruleaza intr-o celula:
              !pip install ultralytics
              from ultralytics import YOLO
              model = YOLO("yolo11n-seg.pt")
              print(model.export(format="tflite", imgsz=640))
        - descarca fisierul .tflite rezultat (panoul de fisiere din stanga Colab, sau
          `from google.colab import files; files.download(...)`).

    (b) WSL2 (Windows Subsystem for Linux) — daca preferi local:
        - `wsl --install` (daca nu e deja instalat), deschide un terminal Ubuntu/WSL, ruleaza
          pasii de mai sus (venv + pip install + python scripts/export-yolo-seg-model.py) acolo —
          WSL2 e un kernel Linux real, trece testul `Linux x86`.

Rezultat: fisierul exportat (ex. `yolo11n-seg_saved_model/yolo11n-seg_float32.tflite` sau similar,
in functie de versiunea ultralytics — verifica output-ul consolei) — copiaza-l in
`app/src/main/assets/yolo11n_seg.tflite` (nume EXACT, folosit de `YoloSegModel.kt`, Faza 3a-ii).
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
