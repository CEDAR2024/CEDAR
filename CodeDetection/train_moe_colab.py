"""
Colab-ready training script for the iSMELL Mixture-of-Experts selector.

Usage example inside Google Colab (GPU runtime recommended):

```python
!git clone https://github.com/iSMELL2024/iSMELL.git
%cd iSMELL/CodeDetection

!pip install "numpy<2" pandas openpyxl scikit-learn tensorboard
!pip install torch==2.2.2

!python train_moe_colab.py --epochs 120 --batch-size 1024 --device auto \
    --output-dir /content/moe_artifacts --kfolds 5 --eval-interval 5
```

The script consumes the precomputed dataset bundled with the repository
(`updated_dataset.xlsx`), trains the MoE gate using k-fold cross validation,
and stores the best checkpoint for each fold under the chosen output directory.
"""

from __future__ import annotations

import argparse
import json
import random
import sys
import time
import types
import importlib.util
from dataclasses import dataclass, asdict
from pathlib import Path
from typing import Dict, List, Tuple

import numpy as np
import torch
from torch.utils.data import DataLoader, Subset, ConcatDataset
from sklearn.model_selection import KFold


def _ensure_local_moe_package(base_dir: Path) -> None:
    """
    Allow importing `moe.*` modules without installing the project as a package.
    """
    if "moe" in sys.modules:
        return

    package = types.ModuleType("moe")
    package.__path__ = [str(base_dir)]
    sys.modules["moe"] = package

    for name in ("attention", "augement_data", "core", "newmodel"):
        module_name = f"moe.{name}"
        if module_name in sys.modules:
            continue
        spec = importlib.util.spec_from_file_location(
            module_name, base_dir / f"{name}.py"
        )
        module = importlib.util.module_from_spec(spec)
        sys.modules[module_name] = module
        assert spec.loader is not None
        spec.loader.exec_module(module)  # type: ignore[arg-type]


BASE_DIR = Path(__file__).resolve().parent
_ensure_local_moe_package(BASE_DIR)

from moe.core import custom_loss, MoE  # type: ignore  # noqa: E402
from moe.newmodel import (  # type: ignore  # noqa: E402
    ExcelDataset,
    num_experts,
    top_k,
    top_k_per_column,
    flip_cols,
)
from moe.augement_data import (  # type: ignore  # noqa: E402
    binary_interpolation,
    Linear_interpolation,
    augment_data,
)


@dataclass
class FoldResult:
    fold: int
    best_epoch: int
    best_loss: float
    overall_accuracy: float
    smell_accuracy: Dict[str, float]
    checkpoint_path: str


SMELL_TYPES = ["LongMethod", "GodClass", "FeatureEnvy", "RefusedBequest"]


def evaluate_model(
    model: MoE,
    dataloader: DataLoader,
    device: torch.device,
) -> Tuple[float, float, Dict[str, float]]:
    model.eval()
    losses: List[float] = []
    total_correct = 0
    total_samples = 0
    correct_counts = {smell: 0 for smell in SMELL_TYPES}
    total_counts = {smell: 0 for smell in SMELL_TYPES}

    with torch.no_grad():
        for inputs, labels, types, x_experts in dataloader:
            inputs = inputs.to(device, dtype=torch.float32)
            labels = labels.to(device)
            types = types.to(device)
            x_experts = x_experts.to(device)

            normalized_accuracies, top_k_indices = model(inputs)
            batch_loss = custom_loss(
                normalized_accuracies, labels, types, x_experts, flip_cols, top_k_indices
            )
            losses.append(batch_loss.item())

            for i in range(labels.size(0)):
                smell_index = types[i].item()
                smell_name = SMELL_TYPES[smell_index]
                expert_idx = top_k_indices[i, 0, smell_index].item()
                prediction = x_experts[i, expert_idx].item()
                is_correct = int(prediction == labels[i].item())

                correct_counts[smell_name] += is_correct
                total_counts[smell_name] += 1
                total_correct += is_correct
                total_samples += 1

    avg_loss = float(np.mean(losses)) if losses else 0.0
    overall_accuracy = total_correct / total_samples if total_samples else 0.0
    smell_accuracy = {
        smell: (
            correct_counts[smell] / total_counts[smell] if total_counts[smell] else 0.0
        )
        for smell in SMELL_TYPES
    }
    return avg_loss, overall_accuracy, smell_accuracy


def build_combined_loader(
    train_subset: Subset,
    device: torch.device,
    batch_size: int,
    use_augmentation: bool,
    augment_factor: int,
) -> DataLoader:
    """
    Optionally augment data using the helper utilities defined in the repo.
    """
    base_loader = DataLoader(train_subset, batch_size=batch_size, shuffle=True)

    if not use_augmentation:
        return base_loader

    binary_loader = binary_interpolation(
        base_loader, device=device, num=augment_factor, batch_size=batch_size * 2
    )
    linear_loader = Linear_interpolation(
        base_loader,
        device=device,
        num=augment_factor,
        batch_size=batch_size * 2,
        target_types=[0, 1, 2, 3],
    )
    gaussian_loader = augment_data(
        base_loader,
        n_augmentations=augment_factor,
        std_dev=0.10,
        augment_types=[0, 1, 2, 3],
        batch_size=batch_size * 2,
    )

    datasets = [
        base_loader.dataset,
        binary_loader.dataset,
        linear_loader.dataset,
        gaussian_loader.dataset,
    ]
    combined_dataset = ConcatDataset(datasets)
    combined_loader = DataLoader(
        combined_dataset, batch_size=batch_size, shuffle=True
    )
    return combined_loader


def train_fold(
    fold_idx: int,
    train_ids: np.ndarray,
    val_ids: np.ndarray,
    dataset: ExcelDataset,
    args: argparse.Namespace,
    device: torch.device,
    output_dir: Path,
) -> FoldResult:
    train_subset = Subset(dataset, train_ids)
    val_subset = Subset(dataset, val_ids)
    combined_loader = build_combined_loader(
        train_subset,
        device=device,
        batch_size=args.batch_size,
        use_augmentation=args.use_augmentation,
        augment_factor=args.augment_factor,
    )
    val_loader = DataLoader(
        val_subset, batch_size=args.eval_batch_size, shuffle=False
    )

    sample_inputs, _, _, _ = next(iter(DataLoader(train_subset, batch_size=1)))
    input_dim = sample_inputs.shape[1]

    model = MoE(input_dim, num_experts, top_k=top_k, top_k_per_column=top_k_per_column)
    model.to(device)

    optimizer = torch.optim.Adam(model.parameters(), lr=args.learning_rate)
    scheduler = torch.optim.lr_scheduler.ReduceLROnPlateau(
        optimizer, mode="min", patience=3
    )

    best_loss = float("inf")
    best_epoch = -1
    best_accuracy = 0.0
    best_smell_accuracy: Dict[str, float] = {}
    checkpoint_dir = output_dir / "checkpoints"
    checkpoint_dir.mkdir(parents=True, exist_ok=True)
    checkpoint_path = checkpoint_dir / f"moe_fold{fold_idx}.pth"

    for epoch in range(1, args.epochs + 1):
        model.train()
        epoch_losses: List[float] = []

        for inputs, labels, types, x_experts in combined_loader:
            inputs = inputs.to(device, dtype=torch.float32)
            labels = labels.to(device)
            types = types.to(device)
            x_experts = x_experts.to(device)

            optimizer.zero_grad()
            normalized_accuracies, top_k_indices = model(inputs)
            loss = custom_loss(
                normalized_accuracies, labels, types, x_experts, flip_cols, top_k_indices
            )
            loss.backward()
            optimizer.step()
            epoch_losses.append(loss.item())

        avg_train_loss = float(np.mean(epoch_losses))
        if epoch % args.eval_interval == 0:
            val_loss, overall_acc, smell_acc = evaluate_model(
                model, val_loader, device
            )
            scheduler.step(val_loss)

            print(
                f"[Fold {fold_idx}] Epoch {epoch:04d} | "
                f"train_loss={avg_train_loss:.4f} | "
                f"val_loss={val_loss:.4f} | overall_acc={overall_acc:.4f}"
            )

            if val_loss < best_loss:
                best_loss = val_loss
                best_epoch = epoch
                best_accuracy = overall_acc
                best_smell_accuracy = smell_acc
                torch.save(model.state_dict(), checkpoint_path)
        else:
            if epoch % max(1, args.eval_interval // 5) == 0:
                print(
                    f"[Fold {fold_idx}] Epoch {epoch:04d} | "
                    f"train_loss={avg_train_loss:.4f}"
                )

    if checkpoint_path.exists():
        print(
            f"[Fold {fold_idx}] Best val_loss={best_loss:.4f} at epoch {best_epoch}. "
            f"Checkpoint saved to {checkpoint_path}"
        )
    else:
        print(f"[Fold {fold_idx}] No checkpoint saved (model did not improve).")

    return FoldResult(
        fold=fold_idx,
        best_epoch=best_epoch,
        best_loss=best_loss,
        overall_accuracy=best_accuracy,
        smell_accuracy=best_smell_accuracy,
        checkpoint_path=str(checkpoint_path),
    )


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Train the iSMELL MoE gate using the precomputed dataset."
    )
    parser.add_argument("--epochs", type=int, default=100)
    parser.add_argument("--batch-size", type=int, default=512)
    parser.add_argument("--eval-batch-size", type=int, default=1024)
    parser.add_argument("--eval-interval", type=int, default=5)
    parser.add_argument("--learning-rate", type=float, default=3e-5)
    parser.add_argument("--kfolds", type=int, default=5)
    parser.add_argument("--use-augmentation", action="store_true")
    parser.add_argument("--augment-factor", type=int, default=5)
    parser.add_argument("--seed", type=int, default=808)
    parser.add_argument(
        "--device",
        choices=["cpu", "cuda", "auto"],
        default="auto",
        help="Device preference; 'auto' picks CUDA when available.",
    )
    parser.add_argument(
        "--output-dir",
        type=str,
        default=str(BASE_DIR / "colab_artifacts"),
        help="Directory to store checkpoints and metrics.",
    )
    return parser.parse_args()


def select_device(preference: str) -> torch.device:
    if preference == "cpu":
        return torch.device("cpu")
    if preference == "cuda":
        if not torch.cuda.is_available():
            raise RuntimeError("CUDA requested but not available.")
        return torch.device("cuda")
    # auto
    return torch.device("cuda" if torch.cuda.is_available() else "cpu")


def main() -> None:
    args = parse_args()
    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    device = select_device(args.device)
    print(f"Using device: {device}")

    random.seed(args.seed)
    np.random.seed(args.seed)
    torch.manual_seed(args.seed)
    if torch.cuda.is_available():
        torch.cuda.manual_seed_all(args.seed)

    dataset_path = BASE_DIR / "updated_dataset.xlsx"
    dataset = ExcelDataset(str(dataset_path), sheet_name="Sheet1")

    kfold = KFold(n_splits=args.kfolds, shuffle=True, random_state=args.seed)
    results: List[FoldResult] = []

    start_time = time.time()
    for fold_idx, (train_ids, val_ids) in enumerate(kfold.split(dataset.df), start=0):
        result = train_fold(
            fold_idx=fold_idx,
            train_ids=train_ids,
            val_ids=val_ids,
            dataset=dataset,
            args=args,
            device=device,
            output_dir=output_dir,
        )
        results.append(result)

    elapsed = time.time() - start_time
    metrics_path = output_dir / "training_summary.json"
    metrics = {
        "total_training_time_sec": elapsed,
        "folds": [asdict(r) for r in results],
        "config": {
            "epochs": args.epochs,
            "batch_size": args.batch_size,
            "eval_batch_size": args.eval_batch_size,
            "eval_interval": args.eval_interval,
            "learning_rate": args.learning_rate,
            "kfolds": args.kfolds,
            "use_augmentation": args.use_augmentation,
            "augment_factor": args.augment_factor,
            "seed": args.seed,
            "device": str(device),
        },
    }

    with metrics_path.open("w", encoding="utf-8") as fh:
        json.dump(metrics, fh, indent=2)

    print(f"Training complete in {elapsed/60:.2f} minutes. Summary saved to {metrics_path}")


if __name__ == "__main__":
    main()
