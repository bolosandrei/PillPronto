"""
Antreneaza un model de EMBEDDINGS (metric learning) pt. recunoasterea cutiilor de medicamente
pe pozele de enrollment din `Poze Antrenare Model/` (Faza 4c, PillPronto).

DE CE metric learning, nu clasificator inchis: decizie de arhitectura stabila a proiectului
(CLAUDE.md, sectiunea 3) — userii vor inrola mereu medicamente noi, fara reantrenare. Rolul acestui
model NU e sa clasifice cele 8 medicamente din setul curent — e sa invete o functie generala
"cat de similare sunt doua cutii", validata aici pe 8 clase, dar menita sa generalizeze la orice
cutie noua via nearest-neighbor pe embeddings (galeria din Faza 4b).

DE CE nu eliminam fundalul din poze inainte de antrenare (decizie luata cu utilizatorul,
2026-09-18): setul e mic (59 poze/8 clase) si e etapa de VALIDARE a pipeline-ului, nu antrenament
final de productie — fundalul e identic pe tot setul (nu difera intre clase), deci nu creeaza
confuzie intre clase la antrenare; riscul real e doar de generalizare la fundaluri noi la
inferenta, compensat aici prin augmentare agresiva (crop/rotatie/culoare/perspectiva/erasing), nu
prin curatare manuala a pozelor. Daca rezultatele ies slabe empiric (testare live pe device,
Faza 4c), de reluat aceasta decizie — vezi CLAUDE.md pt. istoricul complet al deciziei.

DE CE freeze partial pe backbone: 59 poze e foarte putin pt. fine-tuning complet al unei retele —
inghetam cea mai mare parte a extractorului de trasaturi preantrenat pe ImageNet (trasaturi
generice de nivel jos/mediu, deja utile), antrenam doar ultimele blocuri + capul de embedding, ca
sa reducem riscul de supra-invatare pe un set atat de mic.

RULARE (mediu SEPARAT de acest repo Android/Kotlin — are nevoie de Python, nu e parte din build-ul
Gradle; ca la `export-yolo-seg-model.py`):

    python -m venv .venv
    # Windows:
    .venv\\Scripts\\activate
    # macOS/Linux:
    source .venv/bin/activate

    pip install torch torchvision pytorch-metric-learning pillow onnx

    python scripts/train_medication_embedder.py

Ruleaza normal pe Windows (spre deosebire de exportul LiteRT al YOLO) — antrenarea PyTorch +
exportul ONNX nu au restrictia de platforma intalnita la `model.export(format="tflite")`.

REZULTAT: `scripts/artifacts/medication_embedder.onnx` — un model ONNX care primeste o imagine
224x224 si intoarce un vector de embedding normalizat (cosine similarity = produs scalar direct).

PASUL URMATOR (neacoperit de acest script, la fel ca la YOLO — conversie ONNX->TFLite cere
Linux/macOS, vezi `export-yolo-seg-model.py`): converteste `medication_embedder.onnx` la
`.tflite` via Google Colab, apoi verifica OBLIGATORIU layout-ul de input inainte de a scrie cod
Kotlin de inferenta:

    !pip install onnx2tf onnx-graphsurgeon sng4onnx
    !onnx2tf -i medication_embedder.onnx -o medication_embedder_tflite

    import tensorflow as tf
    interp = tf.lite.Interpreter(model_path="medication_embedder_tflite/medication_embedder_float32.tflite")
    interp.allocate_tensors()
    print(interp.get_input_details())   # <-- verifica shape-ul AICI, nu presupune

`onnx2tf` converteste de regula automat NCHW (PyTorch/ONNX) -> NHWC (conventia TFLite) — dar
EXACT aceasta presupunere (NCHW vs NHWC) a produs bug-ul real din Faza 3a-ii (vezi CLAUDE.md).
NU presupune orientarea — citeste `get_input_details()['shape']` din Colab si scrie codul Kotlin
de preprocesare (viitoarea Faza 4c) dupa ce ai raspunsul, nu inainte.

IMPORTANT — preprocesarea EXACTA asteptata de model (de replicat identic in Kotlin la inferenta):
  - imagine RGB (nu BGR), redimensionata la 224x224
  - valori normalizate [0,1] (impartite la 255), apoi standardizate cu media/deviatia ImageNet:
        mean = [0.485, 0.456, 0.406]
        std  = [0.229, 0.224, 0.225]
        pixel_normalizat = (pixel/255 - mean) / std   # per canal R,G,B
  - fara flip orizontal la inferenta (modelul a fost antrenat FARA flip — textul de pe cutie nu
    e niciodata oglindit in realitate, decizie explicita in augmentare, vezi mai jos)
  - output: vector float de EMBEDDING_DIM valori, deja normalizat L2 (norma 1) — similaritatea
    dintre doua embeddings = produs scalar simplu (echivalent cosine similarity), acelasi model
    matematic ca `domain/recognition/CosineSimilarity.kt` deja existent in aplicatie.
"""

from __future__ import annotations

import argparse
from collections import Counter
from pathlib import Path

import torch
import torch.nn as nn
from pytorch_metric_learning.losses import ArcFaceLoss
from pytorch_metric_learning.samplers import MPerClassSampler
from torch.utils.data import DataLoader
from torchvision import datasets, transforms
from torchvision.models import MobileNet_V3_Small_Weights, mobilenet_v3_small

SCRIPT_DIR = Path(__file__).resolve().parent
# scripts/ -> "PillPronto App/" -> "Disertatie/" -> "Poze Antrenare Model/"
DEFAULT_DATA_DIR = SCRIPT_DIR.parent.parent / "Poze Antrenare Model"
DEFAULT_OUTPUT_DIR = SCRIPT_DIR / "artifacts"

IMAGE_SIZE = 224  # standard pt. backbone-uri MobileNetV3 preantrenate pe ImageNet
EMBEDDING_DIM = 128  # suficient pt. cateva zeci-sute de clase; usor de marit ulterior daca creste galeria
IMAGENET_MEAN = [0.485, 0.456, 0.406]
IMAGENET_STD = [0.229, 0.224, 0.225]

# Cate blocuri finale din `features` ale MobileNetV3-Small raman antrenabile (restul, inghetate).
# MobileNetV3-Small are 13 blocuri (indici 0-12) in `features` — antrenam doar ultimele 3.
UNFROZEN_FEATURE_BLOCKS = 3


class EmbeddingNet(nn.Module):
    """MobileNetV3-Small preantrenat + cap de embedding L2-normalizat."""

    def __init__(self, embedding_dim: int = EMBEDDING_DIM) -> None:
        super().__init__()
        backbone = mobilenet_v3_small(weights=MobileNet_V3_Small_Weights.IMAGENET1K_V1)
        self.features = backbone.features
        self.avgpool = backbone.avgpool
        backbone_out_dim = backbone.classifier[0].in_features  # 576 la mobilenet_v3_small
        self.embedding_head = nn.Sequential(
            nn.Flatten(),
            nn.Linear(backbone_out_dim, embedding_dim),
        )
        self._freeze_early_layers()

    def _freeze_early_layers(self) -> None:
        num_blocks = len(self.features)
        for index, block in enumerate(self.features):
            requires_grad = index >= num_blocks - UNFROZEN_FEATURE_BLOCKS
            for param in block.parameters():
                param.requires_grad = requires_grad

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        x = self.features(x)
        x = self.avgpool(x)
        x = self.embedding_head(x)
        return nn.functional.normalize(x, p=2, dim=1)


def build_train_transforms() -> transforms.Compose:
    # Fara flip orizontal: textul de pe o cutie reala nu apare niciodata oglindit — un flip ar
    # invata modelul pe o distributie ireala. Restul augmentarilor compenseaza fundalul constant
    # din setul curent (vezi motivatia din docstring-ul modulului).
    return transforms.Compose(
        [
            transforms.RandomResizedCrop(IMAGE_SIZE, scale=(0.6, 1.0)),
            transforms.RandomRotation(15),
            transforms.RandomPerspective(distortion_scale=0.2, p=0.3),
            transforms.ColorJitter(brightness=0.3, contrast=0.3, saturation=0.3, hue=0.05),
            transforms.ToTensor(),
            transforms.Normalize(mean=IMAGENET_MEAN, std=IMAGENET_STD),
            transforms.RandomErasing(p=0.25, scale=(0.02, 0.15)),
        ]
    )


def build_dataloader(data_dir: Path, batch_size: int, iterations_per_epoch: int) -> tuple[DataLoader, int]:
    dataset = datasets.ImageFolder(str(data_dir), transform=build_train_transforms())
    class_counts = Counter(dataset.targets)
    min_per_class = min(class_counts.values())
    num_classes = len(dataset.classes)

    print(f"Set de date: {len(dataset)} poze, {num_classes} clase, min {min_per_class} poze/clasa")
    for class_name, class_index in sorted(dataset.class_to_idx.items()):
        print(f"  - {class_name}: {class_counts[class_index]} poze")

    m_per_class = min(min_per_class, batch_size // num_classes) if num_classes else 1
    m_per_class = max(m_per_class, 1)
    effective_batch_size = m_per_class * num_classes

    sampler = MPerClassSampler(
        dataset.targets,
        m=m_per_class,
        batch_size=effective_batch_size,
        length_before_new_iter=effective_batch_size * iterations_per_epoch,
    )
    loader = DataLoader(dataset, batch_size=effective_batch_size, sampler=sampler, drop_last=True)
    return loader, num_classes


def train(data_dir: Path, output_dir: Path, epochs: int, batch_size: int, iterations_per_epoch: int) -> None:
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    print(f"Device: {device}")

    loader, num_classes = build_dataloader(data_dir, batch_size, iterations_per_epoch)

    model = EmbeddingNet(EMBEDDING_DIM).to(device)
    loss_func = ArcFaceLoss(num_classes=num_classes, embedding_size=EMBEDDING_DIM, margin=28.6, scale=64).to(device)

    trainable_params = [p for p in model.parameters() if p.requires_grad] + list(loss_func.parameters())
    optimizer = torch.optim.AdamW(trainable_params, lr=1e-4, weight_decay=1e-4)

    model.train()
    for epoch in range(1, epochs + 1):
        epoch_loss = 0.0
        num_batches = 0
        for images, labels in loader:
            images, labels = images.to(device), labels.to(device)

            optimizer.zero_grad()
            embeddings = model(images)
            loss = loss_func(embeddings, labels)
            loss.backward()
            optimizer.step()

            epoch_loss += loss.item()
            num_batches += 1

        avg_loss = epoch_loss / max(num_batches, 1)
        print(f"Epoca {epoch}/{epochs} — loss mediu: {avg_loss:.4f}")

    output_dir.mkdir(parents=True, exist_ok=True)
    weights_path = output_dir / "medication_embedder.pt"
    torch.save(model.state_dict(), weights_path)
    print(f"\nGreutati salvate la: {weights_path}")

    export_onnx(model, output_dir / "medication_embedder.onnx", device)


def export_onnx(model: nn.Module, output_path: Path, device: torch.device) -> None:
    model.eval()
    dummy_input = torch.randn(1, 3, IMAGE_SIZE, IMAGE_SIZE, device=device)
    torch.onnx.export(
        model,
        dummy_input,
        str(output_path),
        input_names=["input"],
        output_names=["embedding"],
        opset_version=17,
        dynamo=False,  # exportatorul "clasic" — cel nou (dynamo) cere pachetul onnxscript
    )
    print(f"Model ONNX exportat la: {output_path}")
    print("Urmatorul pas (Colab, ca la exportul YOLO): converteste la .tflite — vezi instructiunile din docstring.")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--data-dir", type=Path, default=DEFAULT_DATA_DIR)
    parser.add_argument("--output-dir", type=Path, default=DEFAULT_OUTPUT_DIR)
    parser.add_argument("--epochs", type=int, default=60)
    parser.add_argument("--batch-size", type=int, default=24)
    parser.add_argument("--iterations-per-epoch", type=int, default=20)
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    if not args.data_dir.is_dir():
        raise SystemExit(f"Nu gasesc folderul de poze: {args.data_dir}")
    train(args.data_dir, args.output_dir, args.epochs, args.batch_size, args.iterations_per_epoch)


if __name__ == "__main__":
    main()
