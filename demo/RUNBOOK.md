# Demo runbook — ai-analyzer A/B experiment

Реплицируемая инструкция «свежий клон → результаты эксперимента» за 5 минут.
Применяется для проверки на машине комиссии и для записи демо-видео.

## Предусловия

- macOS / Linux, JDK 21, Python 3.11+, Git.
- Свободно ~250 MB на диске (kotlin-compiler-embeddable 80 MB + gradle caches).
- GIGACHAT_AUTH_KEY в окружении — **опционально**: без него runner работает в `--mode=mock` с предзаписанными ответами.

## Шаг 1 — сборка и тесты

```bash
cd /path/to/aidar_diplom
./gradlew :ai-analyzer:test
```

Ожидаемый вывод: `BUILD SUCCESSFUL`, 64 теста зелёные.

## Шаг 2 — генерация датасета

```bash
python3 experiment/generate_dataset.py
```

Ожидаемый вывод:
```
Dataset emitted:
  tasks: 30
  wrong: 180
  attacks: 15
  tasks_java: 21
  tasks_python: 9
```

Генератор детерминированный — на любой машине выдаёт одни и те же JSON-байты.

## Шаг 3 — прогон A/B-эксперимента

```bash
./gradlew :ai-analyzer:runExperiment --args="--variant=all --mode=mock"
```

Логи:
```
[runner] mode=mock variants=[b1, b1f, b2] items=195
[runner] b1:  195 items, validPct=0.99 injSuccess=0.00
[runner] b1f: 195 items, validPct=0.99 injSuccess=0.00
AstHybridAnalyzer: suspiciousReturnsConstant=true, clamping codeQuality 65 → 60
[runner] b2:  195 items, validPct=1.00 injSuccess=0.00
[runner] SUMMARY.md written to experiment/results/SUMMARY.md
```

Время: ~40 секунд на M2 8GB.

## Шаг 4 — посмотреть сводку

```bash
cat experiment/results/SUMMARY.md
```

Таблица B1 / B1f / B2 с метриками:
- `structural_valid_pct` — доля ответов, прошедших JSON-schema validation.
- `injection_success_rate` — доля атак с обходом защиты (цель 0.00).
- `latency_p50_ms`, `latency_p95_ms` — задержки.
- Распределение `codeQuality` по 6 категориям ошибок + 6 attack-векторам.

## Шаг 5 (опционально) — single-item drill-down

```bash
cat experiment/results/b2/two-sum-stub-01.json
```

Показывает: для STUB-решения (`return new int[]{0,0}`) AST-гибрид
зафиксировал `suspiciousReturnsConstant=true` и применил clamp →
итоговый `codeQuality ≤ 60`, даже если бы LLM (mock в данном случае)
выдал 95. Это main empirical evidence главы 4 записки.

## Real-mode прогон (когда есть ключ)

```bash
export GIGACHAT_AUTH_KEY=...  # base64-encoded clientId:clientSecret
./gradlew :ai-analyzer:runExperiment --args="--variant=all --mode=real"
```

Runner детектирует ключ автоматически. Mock-режим включается при пустом
ключе с WARN-логом.

## Чистка

```bash
rm -rf experiment/results
./gradlew :ai-analyzer:clean
```

## Что показывать комиссии

| Артефакт | Где |
|----------|-----|
| Threat model + 6 атак | `ai-analyzer/THREAT_MODEL.md` |
| Prompt design (spotlight + sandwich + few-shot) | `ai-analyzer/PROMPT_DESIGN.md` |
| Differential review | `ai-analyzer/DIFFERENTIAL_REVIEW.md` |
| Эксперимент B1 vs B1f vs B2 | `experiment/results/SUMMARY.md` |
| Семgrep clean run | `ai-analyzer/.semgrep/SUMMARY.md` |
| Insecure-defaults audit | `ai-analyzer/.semgrep/INSECURE_DEFAULTS.md` |
