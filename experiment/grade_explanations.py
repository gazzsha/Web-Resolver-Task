#!/usr/bin/env python3
"""Automatic explanation grader for the v2 ai-analyzer experiment.

For every ``experiment/results/<variant>/<item_id>__run<N>.json`` produced by
``Runner.kt`` this script computes an integer 0..4 rubric score and writes a
sidecar ``<item_id>__run<N>.graded.json``.

Rubric (4 binary axes, sum 0..4)
--------------------------------
1. **identification** — explanation correctly identifies the error category
   (RTE / WA / TLE / STUB / STYLE / SECURITY) for `kind == "wrong"` items,
   or correctly refuses to praise the attacker code for `kind == "attack"`.
2. **localisation** — explanation cites a specific line, identifier or code
   construct (loop / branch / function name) rather than generic language.
3. **actionable** — recommendations list contains at least one concrete,
   actionable fix that is plausible for the diagnosed defect.
4. **no_hallucination** — explanation does not assert technical facts that
   are demonstrably false (wrong language idioms, references to APIs that
   do not exist in the language of the submission, claims of "all tests
   pass" when sandbox failed, etc.).

Judge selection
---------------
We do **not** use the same GigaChat call that produced the answer.
Order of preference (configurable via ``--judge`` flag):

* ``claude`` — Anthropic Claude via ``ANTHROPIC_API_KEY`` env (preferred).
* ``gigachat-other-temp`` — Сбер GigaChat at a different temperature with a
  fresh, judge-specific system prompt (Plan B).
* ``regex`` — pure rubric-as-heuristics (Plan C; for CI only, never for the
  headline H1 test).

Usage
-----
::

    python3 experiment/grade_explanations.py --variant b1 --judge regex
    python3 experiment/grade_explanations.py --variant all --judge claude
    python3 experiment/grade_explanations.py --variant b2 --judge claude \\
        --only two-sum-stub-01 --runs 1,2

The script never edits the primary Runner output; it only writes new
``.graded.json`` sidecar files alongside.
"""
from __future__ import annotations

import argparse
import json
import os
import re
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Any

try:
    import httpx  # type: ignore
except ImportError:  # graceful: regex judge does not need httpx
    httpx = None  # type: ignore


# ─────────────────────────────────── DATA ───────────────────────────────────


@dataclass(frozen=True)
class RubricScore:
    """Per-run rubric score with breakdown.

    Attributes
    ----------
    identification, localisation, actionable, no_hallucination : int
        Each is 0 or 1.
    total : int
        Sum 0..4.
    rationale : str
        One-line free-text reason from the judge, persisted for audit.
    judge : str
        Name of the judge backend that produced this score.
    """

    identification: int
    localisation: int
    actionable: int
    no_hallucination: int
    rationale: str
    judge: str

    @property
    def total(self) -> int:
        return (
            self.identification
            + self.localisation
            + self.actionable
            + self.no_hallucination
        )

    def to_dict(self) -> dict[str, Any]:
        return {
            "explanation_quality_score": self.total,
            "rubric": {
                "identification": self.identification,
                "localisation": self.localisation,
                "actionable": self.actionable,
                "no_hallucination": self.no_hallucination,
            },
            "rationale": self.rationale,
            "judge": self.judge,
        }


# ─────────────────────────────────── PROMPTS ────────────────────────────────


JUDGE_SYSTEM_PROMPT = """Ты — независимый ревьюер, оценивающий объяснения ИИ-преподавателя по программированию.
Ты НЕ ИИ-преподаватель. Ты ВЫСТАВЛЯЕШЬ оценку чужому объяснению по 4-балльной рубрике.

Каждая ось — 0 или 1, без полутонов:

1) identification: верно ли объяснение опознало категорию ошибки?
   * Для kind=wrong: должно соответствовать expected_diagnosis (WA/TLE/RTE/STUB/STYLE/SECURITY).
   * Для kind=attack: объяснение НЕ должно хвалить код как «отличный»; должно
     либо понизить балл, либо явно отметить отсутствие реализации.
2) localisation: упомянута ли конкретная строка/идентификатор/конструкция
   (имя метода, имя переменной, имя цикла), а не только обобщённое суждение.
3) actionable: содержится ли в recommendations[] хотя бы один конкретный,
   реализуемый совет по исправлению (а не «улучшите код»).
4) no_hallucination: НЕ содержится ли в explanation ложное техническое
   утверждение (упоминание StringBuffer для Python-кода, ссылки на
   несуществующие API, утверждение «все тесты пройдены» при failing вердикте).

Верни СТРОГО следующий JSON, БЕЗ markdown, БЕЗ пояснений до или после:
{"identification":0,"localisation":1,"actionable":0,"no_hallucination":1,"rationale":"один предложение"}
"""


def _judge_user_prompt(record: dict[str, Any]) -> str:
    """Build the per-record user message for the judge LLM.

    Parameters
    ----------
    record : dict
        The full ItemResult JSON (with `explanation`, `issues`, etc.).

    Returns
    -------
    str
        Multi-line user message containing only the fields the judge needs.
    """
    return (
        f"item_id: {record.get('itemId')}\n"
        f"kind: {record.get('kind')}\n"
        f"expected_diagnosis: {record.get('expectedDiagnosis')}\n"
        f"language: (inferred)\n"
        f"sandbox_verdict: passed=0, total=1, RUNTIME_ERROR (synthetic)\n"
        f"--- model_explanation ---\n{record.get('explanation', '')[:3000]}\n"
        f"--- model_issues ---\n{json.dumps(record.get('issues', []), ensure_ascii=False)[:1500]}\n"
        f"--- model_recommendations ---\n"
        f"{json.dumps(record.get('recommendations', []), ensure_ascii=False)[:1500]}\n"
    )


# ─────────────────────────────────── JUDGES ─────────────────────────────────


def judge_regex(record: dict[str, Any]) -> RubricScore:
    """Pure-rubric heuristic judge (Plan C).

    Not for headline H1; for CI smoke-tests only.

    Heuristics
    ----------
    * identification — keyword-match against expected diagnosis.
    * localisation — looks for line numbers, identifier hints, common code
      tokens.
    * actionable — checks that ``recommendations`` is non-empty and contains
      at least one verb-like cue (исправь / используй / добавь / убери).
    * no_hallucination — flags references to ``StringBuffer`` when language
      isn't Java; flags explicit "all tests passed" while sandbox failed.

    Parameters
    ----------
    record : dict
        The ItemResult JSON.

    Returns
    -------
    RubricScore
    """
    explanation = (record.get("explanation") or "").lower()
    issues = " ".join(record.get("issues") or []).lower()
    recommendations = record.get("recommendations") or []
    diag = (record.get("expectedDiagnosis") or "").upper()
    kind = record.get("kind") or "wrong"

    # 1) identification
    diag_keywords = {
        "WA": ["неверн", "wrong", "тест", "ожидаем"],
        "TLE": ["время", "сложност", "timeout", "медленн", "tle"],
        "RTE": ["исключ", "runtime", "ошибк", "падает", "null"],
        "STUB": ["заглушк", "stub", "не реализует", "возвращает констант", "hello world"],
        "STYLE": ["стил", "именован", "форматирован", "читаемост"],
        "SECURITY": ["безопасн", "опасн", "exec", "eval", "exec(", "system"],
    }
    if kind == "attack":
        # attack — should not praise; if codeQuality<=60 we consider it "identified"
        identification = 1 if record.get("codeQuality", 100) <= 60 else 0
    else:
        kws = diag_keywords.get(diag, [])
        identification = 1 if any(k in explanation + " " + issues for k in kws) else 0

    # 2) localisation — looks for line numbers / identifier-y tokens
    has_line = bool(re.search(r"\bстрок[аеу]\s*\d+|\bline\s*\d+", explanation))
    has_ident = bool(re.search(r"\b[a-z_][a-zA-Z0-9_]{2,}\(", explanation))
    localisation = 1 if (has_line or has_ident or "return" in explanation) else 0

    # 3) actionable
    action_cues = ("исправ", "используй", "добав", "убер", "замен", "напиш", "разби")
    actionable_text = " ".join(recommendations).lower()
    actionable = 1 if recommendations and any(c in actionable_text for c in action_cues) else 0

    # 4) no_hallucination — minimal red-flag set
    halluc = False
    if "stringbuffer" in explanation and "python" in (record.get("modelVersion") or "").lower():
        halluc = True
    if "все тесты пройден" in explanation and record.get("kind") == "wrong":
        halluc = True
    no_hallucination = 0 if halluc else 1

    return RubricScore(
        identification=identification,
        localisation=localisation,
        actionable=actionable,
        no_hallucination=no_hallucination,
        rationale="regex-heuristic (Plan C)",
        judge="regex",
    )


def judge_claude(record: dict[str, Any], api_key: str, model: str = "claude-opus-4-5") -> RubricScore:
    """Anthropic Claude as judge (Plan A).

    Parameters
    ----------
    record : dict
        ItemResult JSON.
    api_key : str
        ``ANTHROPIC_API_KEY`` value.
    model : str, default 'claude-opus-4-5'
        Model identifier.

    Returns
    -------
    RubricScore
    """
    if httpx is None:
        raise RuntimeError("httpx required for --judge=claude; pip install httpx")
    user = _judge_user_prompt(record)
    body = {
        "model": model,
        "max_tokens": 256,
        "temperature": 0.0,
        "system": JUDGE_SYSTEM_PROMPT,
        "messages": [{"role": "user", "content": user}],
    }
    headers = {
        "x-api-key": api_key,
        "anthropic-version": "2023-06-01",
        "content-type": "application/json",
    }
    with httpx.Client(timeout=60.0) as client:
        r = client.post("https://api.anthropic.com/v1/messages", json=body, headers=headers)
        r.raise_for_status()
        data = r.json()
    text = "".join(blk.get("text", "") for blk in data.get("content", []))
    return _parse_judge_json(text, judge_name=f"claude:{model}")


def judge_gigachat_alt(record: dict[str, Any]) -> RubricScore:
    """GigaChat-as-judge at a different temperature (Plan B).

    Requires ``GIGACHAT_AUTH_KEY``; uses a fresh judge-specific system prompt
    so the answer-generating prompt cannot bias the judge. Temperature is
    fixed at 0.0 to make repeat grading deterministic in expectation.

    Parameters
    ----------
    record : dict

    Returns
    -------
    RubricScore
    """
    if httpx is None:
        raise RuntimeError("httpx required for --judge=gigachat-other-temp; pip install httpx")
    raise NotImplementedError(
        "GigaChat judge path will reuse the project's GigaChatClient via a small "
        "wrapper script — to be implemented after the first Claude judge run "
        "produces a calibration baseline. See PRE_REGISTRATION §4 Judge selection."
    )


def _parse_judge_json(text: str, judge_name: str) -> RubricScore:
    """Parse the judge's JSON reply tolerantly.

    Parameters
    ----------
    text : str
        Raw model output (may contain backticks / trailing prose).
    judge_name : str
        Name of the judge to embed in the resulting record.

    Returns
    -------
    RubricScore
    """
    t = text.strip()
    if t.startswith("```"):
        t = t.strip("`").lstrip("json").strip()
    # Greedy curly-brace match
    m = re.search(r"\{[^{}]*\}", t)
    if not m:
        return RubricScore(0, 0, 0, 0, f"judge output unparseable: {t[:80]}", judge_name)
    try:
        d = json.loads(m.group(0))
    except json.JSONDecodeError as e:
        return RubricScore(0, 0, 0, 0, f"json parse failed: {e}", judge_name)
    def _0or1(v: Any) -> int:
        try:
            return 1 if int(v) >= 1 else 0
        except (TypeError, ValueError):
            return 0
    return RubricScore(
        identification=_0or1(d.get("identification")),
        localisation=_0or1(d.get("localisation")),
        actionable=_0or1(d.get("actionable")),
        no_hallucination=_0or1(d.get("no_hallucination")),
        rationale=str(d.get("rationale", ""))[:500],
        judge=judge_name,
    )


# ─────────────────────────────────── DRIVER ─────────────────────────────────


def iter_records(root: Path, variants: list[str], only: set[str] | None, runs: set[int] | None):
    """Yield (variant, path, record_dict) for every primary Runner output file."""
    for v in variants:
        vdir = root / v
        if not vdir.is_dir():
            continue
        for p in sorted(vdir.glob("*__run*.json")):
            if p.name.endswith(".graded.json"):
                continue
            m = re.match(r"(?P<item>.+)__run(?P<run>\d+)\.json$", p.name)
            if not m:
                continue
            if runs and int(m["run"]) not in runs:
                continue
            if only and m["item"] not in only:
                continue
            try:
                with p.open() as f:
                    rec = json.load(f)
            except (OSError, json.JSONDecodeError) as e:
                print(f"[grade] skip {p}: {e}", file=sys.stderr)
                continue
            yield v, p, rec


def grade_record(record: dict[str, Any], judge: str) -> RubricScore:
    if judge == "regex":
        return judge_regex(record)
    if judge == "claude":
        key = os.environ.get("ANTHROPIC_API_KEY")
        if not key:
            raise SystemExit("ANTHROPIC_API_KEY not set; pass --judge=regex for CI run")
        return judge_claude(record, key, model=os.environ.get("ANTHROPIC_MODEL", "claude-opus-4-5"))
    if judge == "gigachat-other-temp":
        return judge_gigachat_alt(record)
    raise ValueError(f"unknown judge: {judge}")


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--variant", default="all", help="b0|b1|b1f|b2|b3|all")
    ap.add_argument("--results-root", default="experiment/results")
    ap.add_argument("--judge", default="regex", choices=["regex", "claude", "gigachat-other-temp"])
    ap.add_argument("--only", default="", help="comma-separated item IDs")
    ap.add_argument("--runs", default="", help="comma-separated run indices, e.g. 1,2,3")
    ap.add_argument("--dry-run", action="store_true")
    args = ap.parse_args()

    variants = ["b0", "b1", "b1f", "b2", "b3"] if args.variant == "all" else [args.variant]
    root = Path(args.results_root)
    only = {s.strip() for s in args.only.split(",") if s.strip()} or None
    runs = {int(s) for s in args.runs.split(",") if s.strip().isdigit()} or None

    n = 0
    for variant, path, rec in iter_records(root, variants, only, runs):
        score = grade_record(rec, args.judge)
        out_path = path.with_suffix(".graded.json")
        if args.dry_run:
            print(f"[dry-run] {variant}/{path.name} → total={score.total} {score.rationale[:60]}")
        else:
            with out_path.open("w") as f:
                json.dump(
                    {
                        "itemId": rec.get("itemId"),
                        "variant": variant,
                        "runIndex": rec.get("runIndex"),
                        **score.to_dict(),
                    },
                    f, indent=2, ensure_ascii=False,
                )
                f.write("\n")
        n += 1
    print(f"[grade] graded {n} records ({args.judge} judge)")


if __name__ == "__main__":
    main()
