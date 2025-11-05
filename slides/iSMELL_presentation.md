---
marp: true
theme: ismell
paginate: true
headingDivider: 2
title: iSMELL — Expert Toolsets + MoE + LLM Refactoring
description: Reproduction + extensions + demo
---

<!-- class: lead -->

# iSMELL: Assembling LLMs with Expert Toolsets via MoE

Reproduction, extensions, and hands‑on demo

— Your Name

---

## Motivation

- Complex smells (God Class, Feature Envy, Refused Bequest) are hard to detect consistently.
- Expert tools disagree; each is stronger on different cases.
- LLMs need grounded, tool‑level evidence to refactor safely.

---

## Paper Idea (at a glance)

- Train a Mixture‑of‑Experts (MoE) “gate” to pick the most reliable expert per instance.
- Features = 89‑dim code metrics + 768‑dim CodeBERT → 857‑dim input.
- Use the chosen tool’s evidence (scope/lines) to guide LLM refactoring.

---

## Research Questions (from the paper)

- RQ1 — Detection: Does assembling expert tools via an MoE gate improve smell detection (precision/recall/F1) over single tools and LLM baselines?
- RQ2 — Refactoring: Does feeding tool evidence to an LLM produce higher‑quality refactors than LLM‑only prompts?
- RQ3 — Tool Selection: How well does the gate’s recommendation agree with ground truth across smells and projects (vs. best single tool / oracle)?
- RQ4 — Ablation & Diversity: How sensitive is performance to the choice/number of tools; what happens if we remove/replace experts?
- RQ5 — Human & Automated Evaluation: Do human ratings and automated metrics (e.g., code quality measures) agree on refactor improvements?

Notes: wording condensed for slides; confirm exact phrasing with `ismell.pdf` if needed.

---

## Data & Features (repo)

- Precomputed dataset: `CodeDetection/updated_dataset.xlsx`.
- Loader builds features (standardize metrics, concat embedding):
  - `newmodel.py:46–50` → StandardScaler on 89 metrics, concat with 768 CodeBERT.
- Input to model = 857 dims.

---

## Model (code tour)

- Self‑attention (4 heads, head_size=16) over 857‑dim features.
- 7 experts: 857→256→64→4 (one logit per smell).
- Gating MLP: 857→256→128→7; top_k=7, pick top‑1 per smell column.
- Valid‑tool masks per smell.

Refs: `core.py` (MoE/Expert/Gating), `attention.py`.

---

## Training (5‑fold CV)

- Used authors’ data + model/loss; clean trainer added: `train_moe_colab.py`.
- Artifacts: `moe_fold{0..4}.pth` + `training_summary.json` (per‑fold accuracy per smell).

```bash
python CodeDetection/train_moe_colab.py \
  --epochs 120 --batch-size 1024 --kfolds 5 \
  --device auto --output-dir /content/moe_artifacts
```

---

## Ground Truth vs Suggestion

- GT: dataset label (1 smell / 0 no smell).
- Suggestion: vote of the MoE‑recommended expert.
- Correct if suggested vote == GT (reported per fold/per smell).

Refs: `train_moe_colab.py` (evaluation), `training_summary.json`.

---

## Smell Types (in this project)

- Long Method
- God Class
- Feature Envy
- Refused Bequest

---

## Results — Fold Metrics Snapshot

| Fold | Overall Acc | God Class | Feature Envy | Refused Bequest |
|:----:|:-----------:|:---------:|:------------:|:---------------:|
|  0   |    0.733    |   0.811   |    0.674     |      0.725      |
|  1   |    0.672    |   0.818   |    0.488     |      0.696      |
|  2   |    0.748    |   0.972   |    0.548     |      0.755      |
|  3   |    0.679    |   0.949   |    0.524     |      0.600      |
|  4   |    0.746    |   0.900   |    0.595     |      0.750      |

Averages (approx): Overall 0.716, God Class 0.890, Feature Envy 0.566, Refused Bequest 0.705.

Source: `moe_artifacts/moe_artifacts/training_summary.json`.

---

## Results — Tool Diversity (God Class, dataset)

- Detected counts (sample run):
  - DesigniteJava ≈ 98, organic ≈ 81, PMD ≈ 80, JDeodorant ≈ 76, JSpIRIT ≈ 64
- Common patterns include all‑undetected and all‑detected cases — MoE learns which expert to trust across these patterns.

Use this slide to discuss why a single expert is insufficient and why a gate helps.

---

## Demo A — Held‑Out Selection

```bash
python CodeDetection/moe_infer.py \
  --checkpoint moe_artifacts/.../moe_fold2.pth \
  --smell GodClass --fold 2 --ensure-heldout --device cpu
```

Shows: Project/Class/Lines, GT label, stored tool votes, MoE recommendation + scores.

---

## Demo B — New Java → MoE

1) Build one‑row Excel from `GodClass.java`:
```bash
python CodeDetection/custom_test/make_custom_sample.py \
  --java GodClass.java \
  --out CodeDetection/custom_test/custom.xlsx \
  --smell GodClass --label 1
```
2) Append to dataset → keep normalization consistent.
3) Run selection on appended row.

---

## Normalization Gotcha (and fix)

- Loader fits StandardScaler per input file.
- Single‑row file → metrics ≈ all zeros → embedding dominates.
- Fix: append custom row to the full dataset before inference.

Refs: `newmodel.py:46–47`.

---

## Refactoring Handoff (LLM)

- MoE selects the tool; the tool provides scope/lines.
- LLM prompt uses smell type + tool evidence + code span.

Refs: `CodeRefactoring/GPT_Refactoring/refactoringGodClass.py`.

---

## Value Added (beyond paper)

- Reproducible trainer/inference + artifacts.
- New‑class pipeline (Java → Excel row → MoE selection).
- Deployment policies: abstain/top‑2/ensemble across folds.
- CI/CD guidance: cache embeddings/metrics; changed files only.

---

## Limits & Risks

- External detectors not bundled; licensing/platform issues.
- Domain shift: OSS Java → enterprise code/languages.
- Fold variance; per‑file normalization.

Mitigate: domain adaptation, calibrated abstention, ensembling.

---

## Scalability & Adaptability (Q1)

- Use sparse MoE / top‑k; hierarchical routing by smell family.
- Version smells; continual learning; label smoothing.
- Profile latency/accuracy as smells/tools scale.

---

## Generalizability & Robustness (Q2)

- Domain adaptation (freeze/fine‑tune); polyglot embeddings.
- Self‑training with tool consensus; active learning.
- Calibrated abstain when uncertainty high.

---

## Tool Conflict & Failure (Q3)

- Top‑1 by default; run top‑2 on low confidence.
- Fallback chains; treat timeouts as missing gracefully.
- Performance bounded by tool coverage/quality.

---

## Evaluation Validity (Q4)

- Add commits‑based GT (RefactoringMiner), replay compile/tests.
- Reduce reliance on human scoring; automated benchmarks.
- Labels‑light gate via self‑supervision.

---

## Practicality & Model Choice (Q5)

- Latency dominated by feature extraction + LLM; gate is cheap.
- Cache, batch, and parallelize; smaller LLMs with tighter prompts.

---

## Future Extensions (Q6)

- Orchestrate refactoring tools; test‑aware closed loop.
- Plan multi‑step fixes (e.g., God Class decomposition).

---

## Interactive Polls (Slido)

1) Pick Your Least Favorite Smell

- Options: Long Method, God Class, Feature Envy, Refused Bequest

2) Trust the Gate? (when uncertain)

- Options: Run top‑2 & require agreement • Trust top‑1 • Abstain with ticket • Run all tools • LLM only

---

## GT vs Suggestion — Example Output

```
Ground truth label: 1
Stored tool votes: PMD=0, JDeodorant=1, DesigniteJava=1, JSpIRIT=1, ...
MoE recommendation: DesigniteJava (vote=1)

Weighted scores (God Class):
PMD 0.0568 | JDeodorant 0.0533 | DesigniteJava 0.1391 | JSpIRIT 0.0734 | ...
```

- Correct when recommended tool’s vote == GT
- Aggregated per fold in `training_summary.json`

---

## Interpreting MoE “Weighted Scores”

- Scores shown per tool are not probabilities; they are comparable logits per smell column.
- Masked tools show −1.000 (not valid for the smell).
- For a confidence‑like view, softmax over valid tools and check top‑1 margin.
- Policy suggestion: if top‑1 < 0.60 or (top‑1 − top‑2) < 0.02 → run top‑2 or abstain.

Example (custom row): PMD 0.0295, JDeodorant 0.0323, organic 0.0615, DesigniteJava −0.1695, JSpIRIT 0.0121 → almost uniform → treat as uncertain.

---

## Threats to Validity

- External tools not bundled → limited reproducibility; licensing/platform drift
- Domain shift: OSS Java → enterprise code/languages (style, frameworks)
- Single‑row normalization artifacts (metrics collapse to zeros)
- Fold variance; random split sensitivity
- Human scoring variability for refactors; static benchmarks vs real commits

Mitigate: append custom rows to base dataset; calibrated abstention; ensemble folds; commits‑based GT; compile/test replay.

---

## CI/CD Practicality

- Latency drivers: feature extraction (metrics+embedding), chosen tool run, LLM refactor
- Keep cost down: changed files only; cache embeddings/metrics; batch LLM calls
- Policy: if uncertain → run top‑2 experts; else trust top‑1; abstain for high‑impact smells
- Rollout: start “advisory” (no fail), collect telemetry, then gate on high‑confidence cases

---

## What I’d Test Next

- Commits‑based benchmark (RefactoringMiner) + compile/test replay
- Sparse MoE (compute top‑k only) + hierarchical routing by smell family
- Ensemble across folds; temperature‑scaled confidence + abstain threshold

---

## Open Questions for Discussion

1) Scalability & Adaptability
- How to scale MoE (compute/params) for 20+ smells and more experts? Sparse/top‑k routing? Hierarchical smell→tool routing? Versioned smells as definitions evolve?

2) Generalizability & Robustness
- Can the gate hold up on enterprise code, unseen styles, or new languages lacking mature toolsets? Domain adaptation, polyglot embeddings, calibrated abstention?

3) Tool Conflict & Failure
- What policy when experts disagree or time out? Top‑2 on uncertainty, fallback chains, degrade gracefully; how much does performance hinge on tool coverage/quality?

4) Evaluation Validity & Ground Truth
- Would results hold on commits‑based GT (RefactoringMiner) with compile/test replay? Is augmentation realistic? Can we operate with fewer labels via self‑training?

5) Practicality & Model Choice
- Latency/cost in CI/CD: feature extraction, chosen tool, LLM. Sensitivity to LLM choice/version/prompt? Could smaller models (CodeLlama/StarCoder) be “good enough”?

6) Future Extensions (Beyond Detection)
- Orchestrate refactoring tools (JDeodorant, IDE APIs); closed‑loop compile/test validation; multi‑step planning for large smells like God Class.

---

## Files / Commands Cheat Sheet

- Features + normalization: `newmodel.py:46–50`
- Model: `core.py:84`
- Train: `train_moe_colab.py`
- Infer: `moe_infer.py`
- Custom row: `custom_test/make_custom_sample.py`

Live demo quick copy:

```bash
# Held‑out selection
python CodeDetection/moe_infer.py \
  --checkpoint moe_artifacts/.../moe_fold2.pth \
  --smell GodClass --fold 2 --ensure-heldout --device cpu

# New Java → row → append → select
python CodeDetection/custom_test/make_custom_sample.py \
  --java GodClass.java --out CodeDetection/custom_test/custom.xlsx \
  --smell GodClass --label 1

python - <<'PY'
import pandas as pd
b=pd.read_excel('CodeDetection/updated_dataset.xlsx', sheet_name='Sheet1')
c=pd.read_excel('CodeDetection/custom_test/custom.xlsx', sheet_name='Sheet1')
pd.concat([b,c], ignore_index=True).to_excel('CodeDetection/custom_test/custom_all.xlsx', index=False)
print('Appended row index:', len(b))
PY

python CodeDetection/moe_infer.py \
  --checkpoint moe_artifacts/.../moe_fold2.pth \
  --dataset CodeDetection/custom_test/custom_all.xlsx \
  --project custom --class GodClass.java --smell GodClass --device cpu
```

---

## Thank You — Q&A

Discussion prompts:

- Ensemble vs single tool in CI?
- What evidence does the LLM need for safe refactors?
- How to benchmark on real commits?
