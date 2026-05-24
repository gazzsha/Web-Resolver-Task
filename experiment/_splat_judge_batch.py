#!/usr/bin/env python3
"""Splat a single judgement-batch JSON into 30 per-result graded.claude.json files.

Input file path passed as argv[1]. Each record must have:
  variant, itemId, runIndex, axis_correctness, axis_location,
  axis_actionable, axis_no_hallucinations, judge_rationale.

Output goes to experiment/results/<variant>/<itemId>__run<N>.graded.claude.json.
Refuses to overwrite an existing file by default — this protects against
double-grading. Pass --force to override.
"""
from __future__ import annotations
import json
import sys
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parent
RES = ROOT / "results"
JUDGE = "claude-opus-4-7-via-agent-sdk"


def main(path: str, force: bool = False) -> int:
    data = json.loads(Path(path).read_text(encoding="utf-8"))
    n_written = 0
    n_skipped = 0
    for r in data:
        variant = r["variant"]
        iid = r["itemId"]
        run = int(r["runIndex"])
        axes = {
            "axis_correctness": int(r["axis_correctness"]),
            "axis_location": int(r["axis_location"]),
            "axis_actionable": int(r["axis_actionable"]),
            "axis_no_hallucinations": int(r["axis_no_hallucinations"]),
        }
        for k, v in axes.items():
            if v not in (0, 1):
                raise SystemExit(f"bad axis value {k}={v} for {variant}/{iid}/run{run}")
        score = sum(axes.values())
        out = RES / variant / f"{iid}__run{run}.graded.claude.json"
        if out.exists() and not force:
            n_skipped += 1
            continue
        payload = {
            "itemId": iid,
            "variant": variant,
            "runIndex": run,
            "judge_model": JUDGE,
            "judge_call_id": str(uuid.uuid4()),
            **axes,
            "quality_score": score,
            "judge_rationale": r["judge_rationale"].strip(),
        }
        out.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
        n_written += 1
    print(f"written={n_written} skipped={n_skipped}")
    return 0


if __name__ == "__main__":
    force = "--force" in sys.argv
    paths = [a for a in sys.argv[1:] if not a.startswith("--")]
    for p in paths:
        print(f"== {p} ==")
        main(p, force=force)
