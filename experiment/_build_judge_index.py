#!/usr/bin/env python3
"""Build a compact index of 540 (item, result) pairs for in-context judging.

NO grading happens here — this is pure data plumbing. Each output record
contains everything Claude (the judge) needs to render a verdict:
  itemId, variant, runIndex, expectedDiagnosis, code, attack flags,
  explanation, issues, recommendations.

Splits into 18 batches × 30 records so each Read fits comfortably.
"""
from __future__ import annotations
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent
DATA = ROOT / "dataset" / "level2"
RES = ROOT / "results"
OUT = ROOT / "_judge_batches"
OUT.mkdir(exist_ok=True)

# Index items by id (wrong + attacks)
items: dict[str, dict] = {}
for p in DATA.rglob("*.json"):
    try:
        obj = json.loads(p.read_text(encoding="utf-8"))
    except Exception:
        continue
    iid = obj.get("id")
    if not iid:
        continue
    items[iid] = obj

print(f"indexed_items={len(items)}")

records: list[dict] = []
for variant in ("b1", "b1f", "b2"):
    for rp in sorted((RES / variant).glob("*__run*.json")):
        if rp.name.endswith(".graded.json") or rp.name.endswith(".graded.gigachat-self.json") or rp.name.endswith(".graded.claude.json"):
            continue
        r = json.loads(rp.read_text(encoding="utf-8"))
        iid = r["itemId"]
        item = items.get(iid, {})
        rec = {
            "variant": variant,
            "itemId": iid,
            "runIndex": r.get("runIndex"),
            "expectedDiagnosis": r.get("expectedDiagnosis") or item.get("expectedDiagnosis") or "",
            "owaspClass": r.get("owaspClass"),
            "attackVector": r.get("attackVector") or item.get("vector"),
            "successCriterion": r.get("successCriterion") or item.get("success_criterion"),
            "code": item.get("code", ""),
            "explanation": r.get("explanation", ""),
            "issues": r.get("issues", []),
            "recommendations": r.get("recommendations", []),
            "codeQuality": r.get("codeQuality"),
            "expectedKeywords": item.get("expectedKeywords") or item.get("expectedExplanationKeywords") or [],
        }
        records.append(rec)

print(f"records={len(records)}")
# Sort: by variant, expectedDiagnosis, itemId, runIndex — keeps related items adjacent
records.sort(key=lambda r: (r["variant"], r["expectedDiagnosis"] or "", r["itemId"], r["runIndex"] or 0))

BATCH = 30
for i in range(0, len(records), BATCH):
    chunk = records[i : i + BATCH]
    name = f"batch_{i // BATCH:02d}.json"
    (OUT / name).write_text(json.dumps(chunk, ensure_ascii=False, indent=1), encoding="utf-8")
print(f"batches={(len(records) + BATCH - 1) // BATCH}")
