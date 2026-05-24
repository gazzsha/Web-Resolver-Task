#!/usr/bin/env python3
"""Build level2_judge_summary.json from 540 *.graded.claude.json sidecars."""
from __future__ import annotations
import json
import statistics
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parent
RES = ROOT / "results"
DATA = ROOT / "dataset" / "level2"

# Index expectedDiagnosis per itemId
diag_by_iid: dict[str, str] = {}
for p in DATA.rglob("*.json"):
    try:
        obj = json.loads(p.read_text(encoding="utf-8"))
    except Exception:
        continue
    iid = obj.get("id")
    if iid:
        diag_by_iid[iid] = obj.get("expectedDiagnosis") or ""

by_variant: dict[str, list[int]] = defaultdict(list)
by_variant_stub: dict[str, list[int]] = defaultdict(list)
dist: dict[int, int] = {0: 0, 1: 0, 2: 0, 3: 0, 4: 0}
total = 0
for variant in ("b1", "b1f", "b2"):
    for p in sorted((RES / variant).glob("*.graded.claude.json")):
        obj = json.loads(p.read_text(encoding="utf-8"))
        s = int(obj["quality_score"])
        by_variant[variant].append(s)
        dist[s] = dist.get(s, 0) + 1
        total += 1
        iid = obj["itemId"]
        if diag_by_iid.get(iid) == "STUB":
            by_variant_stub[variant].append(s)

summary = {
    "judge_model": "claude-opus-4-7-via-agent-sdk",
    "total_judged": total,
    "by_variant": {
        v: {
            "n": len(by_variant[v]),
            "mean_score": round(statistics.mean(by_variant[v]), 3),
            "stdev": round(statistics.stdev(by_variant[v]), 3) if len(by_variant[v]) > 1 else 0.0,
            "median": statistics.median(by_variant[v]),
        }
        for v in ("b1", "b1f", "b2")
    },
    "by_diagnosis_stub": {
        v: {
            "n": len(by_variant_stub[v]),
            "mean_score": round(statistics.mean(by_variant_stub[v]), 3) if by_variant_stub[v] else 0.0,
        }
        for v in ("b1", "b1f", "b2")
    },
    "score_distribution": {str(k): dist[k] for k in sorted(dist)},
}

out = RES / "level2_judge_summary.json"
out.write_text(json.dumps(summary, indent=2, ensure_ascii=False), encoding="utf-8")
print(json.dumps(summary, indent=2, ensure_ascii=False))
