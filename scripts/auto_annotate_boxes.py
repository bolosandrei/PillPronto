"""
Adnotare AUTOMATA (nu manuala) a pozelor din `Poze Antrenare Model/` pt. antrenarea unui detector
propriu de cutii de medicamente, clasa UNICA (Faza 4c-ii, PillPronto).

DE CE o singura clasa, nu 8 (cate una per medicament din folderele existente): acest detector NU
face identificare de produs (aia ramane treaba embeddings-urilor din Faza 4a-c-i, pe galeria de
enrollment) — face doar LOCALIZARE ("unde e o cutie in cadru"). Foloseste TOATE cele 115 poze din
toate cele 8 foldere ca exemple ale ACELEIASI clase ("cutie_medicament") — diversitatea de FORME
(cutie dreptunghiulara, sticla de spray, blister etc., deja prezenta natural in cele 8 produse)
conteaza mai mult decat multe poze la acelasi produs.

DE CE SAM (Segment Anything), nu adnotare manuala: pozele existente sunt poze normale din galeria
telefonului (NU capturate prin ecranul de inrolare al aplicatiei — vezi CLAUDE.md, Faza 4c —
deci FARA o pozitie cunoscuta a unui dreptunghi-ghidaj). Rezultatul se verifica vizual de
utilizator (pas scurt, nu adnotare de la zero) inainte de a intra in setul de antrenare.

DE CE prompt de tip CUTIE centrala, nu punct central (schimbare fata de prima incercare): un
singur punct e ambiguu pt. SAM — poate segmenta obiectul intreg SAU doar o sub-parte locala (ex.
o eticheta/litera de pe cutie) care contine punctul, iar la prima rulare ~31% din poze au iesit cu
masca sub 2% din imagine — semn clar ca modelul prindea des doar un detaliu, nu cutia intreaga. O
cutie de prompt (regiune centrala generoasa, nu tot cadrul) ancoreaza SAM mult mai fiabil spre
obiectul principal, comportament documentat al SAM/MobileSAM (promptul de tip cutie e modul
"implicit" de utilizare, cel de punct e doar un shortcut mai ambiguu).

RULARE (venv .venv-train, Python 3.14 — vezi train_medication_embedder.py pt. motivul separarii
de .venv-tflite):

    scripts\\.venv-train\\Scripts\\activate
    pip install ultralytics   # include deja suport SAM/MobileSAM

    python scripts/auto_annotate_boxes.py

PAS 1 (acest script, prima rulare): genereaza masti + imagini de verificare in
`scripts/artifacts/box_annotation_review/<clasa>/<poza>.jpg` (originalul cu masca suprapusa
semi-transparent). Utilizatorul parcurge folderul si STERGE fisierele unde masca e clar gresita
(obiect gresit selectat, masca goala/prea mica etc.).

PAS 2 (acest script, a doua rulare, cu --finalize): citeste ce a mai ramas in
`box_annotation_review/` (adica exact pozele confirmate bune de utilizator) si scrie setul de
antrenare YOLO-seg in `scripts/artifacts/box_dataset/` (images/{train,val} + labels/{train,val} +
data.yaml, o singura clasa).
"""

from __future__ import annotations

import argparse
import random
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

SCRIPT_DIR = Path(__file__).resolve().parent
# scripts/ -> "PillPronto App/" -> "Disertatie/" -> "Poze Antrenare Model/"
DEFAULT_SOURCE_DIR = SCRIPT_DIR.parent.parent / "Poze Antrenare Model"
REVIEW_DIR = SCRIPT_DIR / "artifacts" / "box_annotation_review"
DATASET_DIR = SCRIPT_DIR / "artifacts" / "box_dataset"

CLASS_NAME = "cutie_medicament"
SAM_MODEL_NAME = "mobile_sam.pt"  # rapid, suficient pt. o masca "obiect principal centrat"
# Cutie de prompt = regiune centrala generoasa (fractiune din latime/inaltime), nu tot cadrul —
# suficient de larga sa acopere obiectul chiar daca nu e perfect centrat, suficient de ingusta sa
# excluda cea mai mare parte a fundalului din prompt.
PROMPT_BOX_WIDTH_FRACTION = 0.7
PROMPT_BOX_HEIGHT_FRACTION = 0.7
MIN_MASK_FRACTION = 0.03  # sub 3% din suprafata imaginii = probabil masca gresita (prea mica)
MAX_MASK_FRACTION = 0.95  # peste 95% = probabil a segmentat tot fundalul, nu obiectul
VAL_FRACTION = 0.15
SPLIT_SEED = 42


def collect_source_images(source_dir: Path) -> list[Path]:
    images = sorted(p for p in source_dir.rglob("*.jpg")) + sorted(source_dir.rglob("*.jpeg")) + sorted(source_dir.rglob("*.png"))
    if not images:
        raise SystemExit(f"Nu gasesc poze in {source_dir}")
    return images


def mask_to_contour(mask: np.ndarray) -> np.ndarray | None:
    """Extrage cel mai mare contur din masca booleana, in coordonate PIXEL (nu normalizate) —
    normalizarea se face separat, dupa ce conturul e deja folosit si pt. desenarea de verificare."""
    import cv2

    mask_uint8 = (mask.astype(np.uint8)) * 255
    contours, _ = cv2.findContours(mask_uint8, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    if not contours:
        return None
    largest = max(contours, key=cv2.contourArea)
    if len(largest) < 3:
        return None
    return largest.reshape(-1, 2)  # [[x,y], ...] in pixeli


def contour_to_normalized_polygon(contour: np.ndarray, width: int, height: int) -> list[float]:
    polygon: list[float] = []
    for x, y in contour:
        polygon.append(x / width)
        polygon.append(y / height)
    return polygon


def run_annotation(source_dir: Path) -> None:
    from ultralytics import SAM

    REVIEW_DIR.mkdir(parents=True, exist_ok=True)
    model = SAM(SAM_MODEL_NAME)

    images = collect_source_images(source_dir)
    print(f"Gasite {len(images)} poze in {source_dir}")

    kept, skipped = 0, 0
    for image_path in images:
        with Image.open(image_path) as img:
            img = img.convert("RGB")
            width, height = img.size
            box_w = width * PROMPT_BOX_WIDTH_FRACTION
            box_h = height * PROMPT_BOX_HEIGHT_FRACTION
            prompt_box = [[
                (width - box_w) / 2,
                (height - box_h) / 2,
                (width + box_w) / 2,
                (height + box_h) / 2,
            ]]

            result = model.predict(img, bboxes=prompt_box, verbose=False)[0]
            if result.masks is None or len(result.masks.data) == 0:
                print(f"  [fara masca] {image_path.name}")
                skipped += 1
                continue

            mask = result.masks.data[0].cpu().numpy().astype(bool)
            fraction = mask.mean()
            if fraction < MIN_MASK_FRACTION or fraction > MAX_MASK_FRACTION:
                print(f"  [masca suspecta, {fraction:.1%} din imagine] {image_path.name}")
                skipped += 1
                continue

            contour = mask_to_contour(mask)
            if contour is None:
                print(f"  [contur invalid] {image_path.name}")
                skipped += 1
                continue
            polygon = contour_to_normalized_polygon(contour, width, height)

            # Salveaza imaginea de verificare: contur gros LIME (contrast garantat pe orice fundal/
            # cutie, spre deosebire de prima incercare cu fill rosu translucid — aproape invizibil
            # pe cutii roz/rosii, exact culoarea tipica a multor ambalaje farma) + fill cyan usor
            # translucid pt. context de suprafata. Fisierul .polygon.npy asociat e folosit la
            # finalize daca userul pastreaza poza.
            overlay = img.copy().convert("RGBA")
            fill_layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
            mask_img = Image.fromarray((mask * 70).astype(np.uint8), mode="L")
            cyan_layer = Image.new("RGBA", img.size, (0, 255, 255, 0))
            cyan_layer.putalpha(mask_img)
            overlay = Image.alpha_composite(overlay, cyan_layer)

            draw = ImageDraw.Draw(overlay)
            outline_points = [(int(x), int(y)) for x, y in contour]
            draw.line(outline_points + [outline_points[0]], fill=(50, 255, 50, 255), width=8)

            overlay = overlay.convert("RGB")

            class_dir = REVIEW_DIR / image_path.parent.name
            class_dir.mkdir(parents=True, exist_ok=True)
            review_path = class_dir / image_path.name
            overlay.save(review_path, quality=90)

            polygon_path = review_path.with_suffix(".polygon.npy")
            np.save(polygon_path, np.array(polygon, dtype=np.float32))
            # Pastreaza si calea catre poza originala, ca sa nu depindem de structura de foldere
            # sursa la finalize (userul poate redenumi/muta REVIEW_DIR fara sa strice legatura).
            (review_path.with_suffix(".source.txt")).write_text(str(image_path), encoding="utf-8")

            kept += 1

    print(f"\nGata: {kept} masti generate (de verificat in {REVIEW_DIR}), {skipped} sarite automat.")
    print("Following steps: deschide folderul de mai sus, sterge pozele unde masca (rosu) e gresita,")
    print("apoi ruleaza din nou acest script cu --finalize.")


def finalize_dataset() -> None:
    review_images = sorted(p for p in REVIEW_DIR.rglob("*.jpg") if not p.name.endswith((".polygon.npy", ".source.txt")))
    if not review_images:
        raise SystemExit(f"Nu gasesc poze ramase in {REVIEW_DIR} — ruleaza intai fara --finalize.")

    print(f"{len(review_images)} poze confirmate (ramase dupa verificarea ta) — construiesc setul YOLO-seg.")

    random.Random(SPLIT_SEED).shuffle(review_images)
    val_count = max(1, int(len(review_images) * VAL_FRACTION))
    val_set = set(review_images[:val_count])

    for split in ("train", "val"):
        (DATASET_DIR / "images" / split).mkdir(parents=True, exist_ok=True)
        (DATASET_DIR / "labels" / split).mkdir(parents=True, exist_ok=True)

    for review_path in review_images:
        polygon_path = review_path.with_suffix(".polygon.npy")
        source_path_file = review_path.with_suffix(".source.txt")
        if not polygon_path.is_file() or not source_path_file.is_file():
            print(f"  [lipsesc metadate, sarit] {review_path.name}")
            continue

        source_path = Path(source_path_file.read_text(encoding="utf-8").strip())
        if not source_path.is_file():
            print(f"  [poza sursa lipsa, sarita] {source_path}")
            continue

        polygon = np.load(polygon_path)
        split = "val" if review_path in val_set else "train"

        dest_image = DATASET_DIR / "images" / split / f"{review_path.parent.name}_{review_path.stem}.jpg"
        dest_label = DATASET_DIR / "labels" / split / f"{review_path.parent.name}_{review_path.stem}.txt"

        with Image.open(source_path) as img:
            img.convert("RGB").save(dest_image, quality=95)

        coords = " ".join(f"{v:.6f}" for v in polygon)
        dest_label.write_text(f"0 {coords}\n", encoding="utf-8")

    data_yaml = DATASET_DIR / "data.yaml"
    data_yaml.write_text(
        f"path: {DATASET_DIR}\n"
        f"train: images/train\n"
        f"val: images/val\n"
        f"nc: 1\n"
        f"names: ['{CLASS_NAME}']\n",
        encoding="utf-8",
    )
    print(f"\nSet de antrenare YOLO-seg scris la: {DATASET_DIR}")
    print(f"Urmatorul pas: python scripts/train_box_detector.py")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-dir", type=Path, default=DEFAULT_SOURCE_DIR)
    parser.add_argument("--finalize", action="store_true", help="Al 2-lea pas: construieste setul YOLO-seg din ce a ramas dupa verificarea ta")
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    if args.finalize:
        finalize_dataset()
    else:
        if not args.source_dir.is_dir():
            raise SystemExit(f"Nu gasesc folderul de poze: {args.source_dir}")
        run_annotation(args.source_dir)


if __name__ == "__main__":
    main()
