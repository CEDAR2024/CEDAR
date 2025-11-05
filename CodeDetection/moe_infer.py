"""
Utility to query the trained Mixture-of-Experts model for a specific class.

Example usage (after unzipping checkpoints into `moe_artifacts/moe_artifacts`):

```bash
python3 moe_infer.py \
    --checkpoint ../moe_artifacts/moe_artifacts/checkpoints/moe_fold2.pth \
    --project apache_1.2 \
    --class Project.java \
    --smell GodClass
```

This will print the expert (external tool) selected by the MoE gate along with
the stored tool outputs from the dataset for comparison.
"""

from __future__ import annotations

import argparse
import sys
import types
import importlib.util
from pathlib import Path
from typing import Optional

import torch
import pandas as pd


BASE_DIR = Path(__file__).resolve().parent


def _ensure_local_moe_package() -> None:
    """Allow importing `moe.*` without installing the repo as a package."""
    if "moe" in sys.modules:
        return

    package = types.ModuleType("moe")
    package.__path__ = [str(BASE_DIR)]
    sys.modules["moe"] = package

    for name in ("attention", "augement_data", "core", "newmodel"):
        module_name = f"moe.{name}"
        if module_name in sys.modules:
            continue
        spec = importlib.util.spec_from_file_location(
            module_name, BASE_DIR / f"{name}.py"
        )
        module = importlib.util.module_from_spec(spec)
        sys.modules[module_name] = module
        assert spec.loader is not None
        spec.loader.exec_module(module)  # type: ignore[arg-type]


_ensure_local_moe_package()

from moe.core import MoE  # type: ignore  # noqa: E402
from moe.newmodel import ExcelDataset, num_experts, top_k, top_k_per_column  # type: ignore  # noqa: E402


TOOL_NAMES = ["PMD", "Jdeodorant", "organic", "DesigniteJava", "JSpIRIT", "FeTruth", "Jmove"]
SMELL_INDICES = {"LongMethod": 0, "GodClass": 1, "FeatureEnvy": 2, "RefusedBequest": 3}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Inspect MoE expert selection for a specific dataset entry."
    )
    parser.add_argument(
        "--checkpoint",
        required=True,
        type=str,
        help="Path to the trained MoE checkpoint (.pth).",
    )
    parser.add_argument(
        "--dataset",
        type=str,
        default=str(BASE_DIR / "updated_dataset.xlsx"),
        help="Path to the Excel dataset with precomputed tool outputs.",
    )
    parser.add_argument("--sheet", type=str, default="Sheet1", help="Excel sheet name.")
    parser.add_argument(
        "--index",
        type=int,
        help="Direct row index (0-based) into the dataset.",
    )
    parser.add_argument("--project", type=str, help="Filter by project name.")
    parser.add_argument("--class", dest="clazz", type=str, help="Filter by class name.")
    parser.add_argument(
        "--smell",
        type=str,
        default="GodClass",
        choices=SMELL_INDICES.keys(),
        help="Expected smell type for the target entry.",
    )
    parser.add_argument(
        "--start-line",
        type=float,
        help="Optional starting line filter (matches 'Starting Line Number').",
    )
    parser.add_argument(
        "--end-line",
        type=float,
        help="Optional ending line filter (matches 'Ending Line Number').",
    )
    parser.add_argument(
        "--device",
        choices=["cpu", "cuda", "auto"],
        default="auto",
        help="Device preference for inference.",
    )
    parser.add_argument("--fold", type=int, help="Fold index used for the checkpoint (0-based).")
    parser.add_argument("--kfolds", type=int, default=5, help="Number of folds used during training.")
    parser.add_argument("--seed", type=int, default=808, help="Random seed used during KFold.")
    parser.add_argument(
        "--ensure-heldout",
        action="store_true",
        help="Ensure the chosen sample is from the validation split of the given fold.",
    )
    return parser.parse_args()


def select_device(pref: str) -> torch.device:
    def _has_cuda() -> bool:
        if not torch.backends.cuda.is_built():
            return False
        try:
            return torch.cuda.is_available()
        except (AssertionError, RuntimeError):
            return False

    if pref == "cpu":
        return torch.device("cpu")
    if pref == "cuda":
        if not _has_cuda():
            raise RuntimeError("CUDA requested but not available.")
        return torch.device("cuda")
    return torch.device("cuda" if _has_cuda() else "cpu")


def find_index(
    df: pd.DataFrame,
    smell: str,
    idx: Optional[int],
    project: Optional[str],
    clazz: Optional[str],
    start_line: Optional[float],
    end_line: Optional[float],
) -> int:
    if idx is not None:
        return idx

    mask = df["Smell"] == smell
    if project:
        mask &= df["Project"] == project
    if clazz:
        mask &= df["Class"] == clazz
    if start_line is not None:
        mask &= df["Starting Line Number"] == start_line
    if end_line is not None:
        mask &= df["Ending Line Number"] == end_line

    matches = df[mask]
    if matches.empty:
        raise ValueError("No dataset rows match the given filters.")
    if len(matches) > 1:
        raise ValueError(
            f"Multiple rows match filters {project=} {clazz=} {start_line=} {end_line=}. "
            "Consider providing --index as well."
        )
    return int(matches.index[0])


def compute_fold_indices(df: pd.DataFrame, n_splits: int, seed: int, fold: int):
    """Recreate the KFold split used by training to get held-out indices for the fold."""
    from sklearn.model_selection import KFold

    kf = KFold(n_splits=n_splits, shuffle=True, random_state=seed)
    for i, (train_idx, val_idx) in enumerate(kf.split(df)):
        if i == fold:
            return train_idx, val_idx
    raise ValueError(f"Fold {fold} not found with n_splits={n_splits}")


def main() -> None:
    args = parse_args()
    device = select_device(args.device)
    checkpoint_path = Path(args.checkpoint).expanduser().resolve()
    if not checkpoint_path.exists():
        raise FileNotFoundError(f"Checkpoint not found: {checkpoint_path}")

    dataset = ExcelDataset(args.dataset, sheet_name=args.sheet)
    df = dataset.df
    # Determine target row
    if args.ensure_heldout:
        if args.fold is None:
            raise SystemExit("--ensure-heldout requires --fold to be provided.")
        train_idx, val_idx = compute_fold_indices(df, args.kfolds, args.seed, args.fold)
        # If index was explicitly given, validate it; otherwise search within held-out rows
        if args.index is not None:
            if int(args.index) not in set(val_idx.tolist()):
                raise SystemExit(
                    f"Row {args.index} is not in fold {args.fold}'s held-out set."
                )
            sample_idx = int(args.index)
        else:
            # Try to find a single row in the held-out set matching the filters; fall back to first GodClass row
            subdf = df.iloc[val_idx]
            mask = subdf["Smell"] == args.smell
            if args.project:
                mask &= subdf["Project"] == args.project
            if args.clazz:
                mask &= subdf["Class"] == args.clazz
            if args.start_line is not None:
                mask &= subdf["Starting Line Number"] == args.start_line
            if args.end_line is not None:
                mask &= subdf["Ending Line Number"] == args.end_line
            cand = subdf[mask]
            if cand.empty:
                # fallback to first GodClass in held-out
                cand = subdf[subdf["Smell"] == args.smell]
            if cand.empty:
                raise SystemExit("No matching held-out rows found for this fold.")
            sample_idx = int(cand.index[0])
            print(f"[Info] Auto-selected held-out row index {sample_idx} for fold {args.fold}.")
    else:
        sample_idx = find_index(
            df,
            smell=args.smell,
            idx=args.index,
            project=args.project,
            clazz=args.clazz,
            start_line=args.start_line,
            end_line=args.end_line,
        )

    sample_features, label, smell_idx, expert_votes = dataset[sample_idx]
    smell_idx = int(smell_idx.item())
    smell_name = list(SMELL_INDICES.keys())[smell_idx]

    model = MoE(sample_features.shape[-1], num_experts, top_k=top_k, top_k_per_column=top_k_per_column)
    state_dict = torch.load(checkpoint_path, map_location=device)
    model.load_state_dict(state_dict)
    model.to(device)
    model.eval()

    with torch.no_grad():
        inputs = sample_features.unsqueeze(0).to(device, dtype=torch.float32)
        weighted_scores, top_k_indices = model(inputs)

    expert_idx = int(top_k_indices[0, 0, smell_idx].item())
    recommended_tool = TOOL_NAMES[expert_idx]
    vote = int(expert_votes[expert_idx].item())
    label_val = int(label.item())

    print(f"Dataset row index : {sample_idx}")
    print(f"Project/Class     : {df.loc[sample_idx, 'Project']} / {df.loc[sample_idx, 'Class']}")
    print(f"Lines             : {df.loc[sample_idx, 'Starting Line Number']} - {df.loc[sample_idx, 'Ending Line Number']}")
    print(f"Smell type        : {smell_name}")
    print(f"Ground truth label: {label_val}")
    print("Stored tool votes :")
    for tool, vote_val in zip(TOOL_NAMES, expert_votes.tolist()):
        print(f"  - {tool:14s}: {int(vote_val)}")
    print(f"\nMoE recommendation: {recommended_tool} (vote={vote})")

    weights = weighted_scores[0, :, smell_idx].cpu()
    print("\nMoE weighted scores for this smell column:")
    for idx, (tool, score) in enumerate(zip(TOOL_NAMES, weights.tolist())):
        print(f"  {idx}: {tool:14s} -> {score:.4f}")


if __name__ == "__main__":
    main()
