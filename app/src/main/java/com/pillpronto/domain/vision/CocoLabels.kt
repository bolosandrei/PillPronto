package com.pillpronto.domain.vision

/** Cele 80 de clase COCO, în ordinea standard folosită de modelele Ultralytics YOLO preantrenate
 * (inclusiv `yolo11n-seg.pt`, Faza 3a-i/3a-ii) — indexul din listă = classId din tensorul de
 * ieșire al modelului. Model generic, NU medicamente (nu există încă dataset propriu de cutii
 * RO/UE) — vezi CLAUDE.md, decizia de a valida întâi pipeline-ul tehnic cu un model preantrenat. */
val COCO_LABELS: List<String> = listOf(
    "person", "bicycle", "car", "motorcycle", "airplane", "bus", "train", "truck", "boat",
    "traffic light", "fire hydrant", "stop sign", "parking meter", "bench", "bird", "cat", "dog",
    "horse", "sheep", "cow", "elephant", "bear", "zebra", "giraffe", "backpack", "umbrella",
    "handbag", "tie", "suitcase", "frisbee", "skis", "snowboard", "sports ball", "kite",
    "baseball bat", "baseball glove", "skateboard", "surfboard", "tennis racket", "bottle",
    "wine glass", "cup", "fork", "knife", "spoon", "bowl", "banana", "apple", "sandwich",
    "orange", "broccoli", "carrot", "hot dog", "pizza", "donut", "cake", "chair", "couch",
    "potted plant", "bed", "dining table", "toilet", "tv", "laptop", "mouse", "remote",
    "keyboard", "cell phone", "microwave", "oven", "toaster", "sink", "refrigerator", "book",
    "clock", "vase", "scissors", "teddy bear", "hair drier", "toothbrush"
)
