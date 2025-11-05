Custom test workflow (local, no Colab)

This folder provides small utilities to create a one‑row Excel in the same
schema as `CodeDetection/updated_dataset.xlsx` from a raw Java file and then run
the trained MoE gate to pick the recommended external tool.

Prerequisites (Python 3.12 recommended)
- `pip install "numpy<2" pandas openpyxl scikit-learn javalang transformers safetensors torch==2.2.2`

Steps
- Create a custom sample from a Java file:
  - `python CodeDetection/custom_test/make_custom_sample.py --java GodClass.java --out CodeDetection/custom_test/custom.xlsx --smell GodClass --label 1`
  - By default, this computes a CodeBERT embedding and fills a lightweight set of
    metrics into an 89‑dim `code_smell_vector` (remaining slots are zeros).
    If you don’t want the embedding, pass `--no-codebert`.
    If you see a CVE warning about torch.load on `.bin` weights, ensure `safetensors`
    is installed; the script prefers `.safetensors` automatically.

- Ask the MoE to recommend a tool for that row:
  - `python CodeDetection/moe_infer.py --checkpoint moe_artifacts/moe_artifacts/checkpoints/moe_fold2.pth --dataset CodeDetection/custom_test/custom.xlsx --smell GodClass --index 0 --device cpu`

Notes
- These utilities do not run external detectors (PMD, JDeodorant, DesigniteJava, etc.).
- The `code_smell_vector` here is a minimal proxy; for best fidelity, replace it
  with your real metrics in the Excel.
