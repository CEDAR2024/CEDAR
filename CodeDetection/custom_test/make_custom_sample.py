import argparse
import pathlib
import re
from typing import List

import numpy as np
import pandas as pd


def read_text(path: pathlib.Path) -> str:
    text = path.read_text(encoding="utf-8", errors="ignore")
    return text


def simple_java_metrics(java_text: str) -> List[float]:
    """A minimal 89-dim metrics vector.

    We compute a handful of simple metrics and pad to 89 dims with zeros so the
    schema matches the replication dataset. This is NOT the paper's full metric
    set, but sufficient to exercise the MoE selector end-to-end.
    """
    lines = [ln for ln in java_text.splitlines()]
    loc = float(len(lines))

    # crude method detection: count lines that look like a method signature
    method_sig = re.compile(r"\b[a-zA-Z_][a-zA-Z0-9_<>\[\]]*\s+[a-zA-Z_][a-zA-Z0-9_]*\s*\([^;]*\)\s*\{")
    method_count = float(sum(1 for ln in lines if method_sig.search(ln)))

    # crude field detection: lines ending with ';' and not inside parentheses
    field_sig = re.compile(r";\s*$")
    field_count = float(sum(1 for ln in lines if field_sig.search(ln)))

    avg_method_len = float(loc / method_count) if method_count > 0 else 0.0

    vec = np.zeros(89, dtype=np.float64)
    vec[0] = loc
    vec[1] = method_count
    vec[2] = field_count
    vec[3] = avg_method_len
    return vec.tolist()


def compute_codebert(java_text: str, model_name: str = "microsoft/codebert-base") -> List[float]:
    try:
        from transformers import AutoTokenizer, AutoModel
        import torch
    except Exception as e:
        raise SystemExit(
            "Transformers + Torch are required. Install with: pip install transformers torch"
        ) from e

    # Prefer safetensors to avoid torch.load on .bin (CVE guard on older torch)
    tokenizer = AutoTokenizer.from_pretrained(model_name)
    try:
        model = AutoModel.from_pretrained(model_name, use_safetensors=True)
    except Exception:
        # Fallback: Transformers will fetch safetensors automatically if available
        model = AutoModel.from_pretrained(model_name)

    tokens = tokenizer(java_text, return_tensors="pt", truncation=True, max_length=512)
    with torch.no_grad():
        out = model(**tokens)
    emb = out.last_hidden_state.mean(dim=1).squeeze().cpu().numpy().astype(np.float64)
    return emb.tolist()


def main():
    ap = argparse.ArgumentParser(description="Create a one-row Excel matching the iSMELL dataset schema from a Java file.")
    ap.add_argument("--java", required=True, help="Path to a .java file")
    ap.add_argument("--out", required=True, help="Path to output Excel (e.g., CodeDetection/custom_test/custom.xlsx)")
    ap.add_argument("--smell", default="GodClass", choices=["LongMethod","GodClass","FeatureEnvy","RefusedBequest"])
    ap.add_argument("--label", type=int, default=1, help="Ground truth label (1=smell, 0=no smell)")
    ap.add_argument("--project", default="custom")
    ap.add_argument("--class-name", default=None, help="Class name to record; defaults to file name")
    ap.add_argument("--start-line", type=int, default=1)
    ap.add_argument("--end-line", type=int, default=999)
    ap.add_argument("--no-codebert", action="store_true", help="Skip CodeBERT embedding and use zeros (not recommended)")
    args = ap.parse_args()

    java_path = pathlib.Path(args.java).expanduser().resolve()
    out_path = pathlib.Path(args.out).expanduser().resolve()
    out_path.parent.mkdir(parents=True, exist_ok=True)

    text = read_text(java_path)

    smell_vec = simple_java_metrics(text)

    if args.no_codebert:
        codebert = [0.0] * 768
    else:
        codebert = compute_codebert(text)
        if len(codebert) != 768:
            raise SystemExit(f"Expected 768-dim CodeBERT vector, got {len(codebert)}")

    row = {
        "Smell": args.smell,
        "Label": int(args.label),
        "Project": args.project,
        "Link": f"file://{java_path}",
        "Class": args.class_name or java_path.name,
        "Starting Line Number": int(args.start_line),
        "Ending Line Number": int(args.end_line),
        # Tool votes (placeholders by default)
        "PMD": "Undetected",
        "Jdeodorant": "Undetected",
        "organic": "Undetected",
        "DesigniteJava": "Undetected",
        "JSpIRIT": "Undetected",
        "FeTruth": "Detection Not Supported",
        "Jmove": "Detection Not Supported",
        "Results": "Undetected",
        "code_smell_vector": ",".join(f"{x:.6f}" for x in smell_vec),
        "codebert_vector": ",".join(f"{x:.6e}" for x in codebert),
    }

    df = pd.DataFrame([row])
    # Must be Sheet1 to match loader default
    with pd.ExcelWriter(out_path, engine="openpyxl") as w:
        df.to_excel(w, index=False, sheet_name="Sheet1")

    print(f"Wrote 1 sample to {out_path}")


if __name__ == "__main__":
    main()
