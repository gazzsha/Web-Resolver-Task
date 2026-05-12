# Experiment — ai-analyzer A/B evaluation

Воспроизводимый эксперимент для главы 4 пояснительной записки.
Сравниваем три конфигурации AI-анализатора на одном датасете:

- **B1** — `gigachat` provider, ZERO_SHOT prompt.
- **B1f** — `gigachat` provider, FEW_SHOT prompt.
- **B2** — `ast-hybrid` provider (AstMetricsService + GigaChat), ZERO_SHOT.

Опционально B3 — Kaggle Qwen2.5-Coder-14B (Phase 7, если хватит времени).

## Структура

```
experiment/
├── README.md              — этот файл
├── generate_dataset.py    — reproducible-генератор, материализует JSON-датасет
├── dataset/
│   ├── SCHEMA.md          — формат файлов
│   ├── tasks/             — 30 задач (21 Java + 9 Python)
│   ├── wrong/<task_id>/   — по 6 ошибочных решений на задачу (WA/TLE/RTE/STUB/STYLE/SECURITY)
│   └── attacks/           — 15 prompt-injection попыток (V1-V6 из THREAT_MODEL.md)
└── results/
    ├── b1/                — output gigachat zero-shot
    ├── b1f/               — output gigachat few-shot
    ├── b2/                — output ast-hybrid
    └── SUMMARY.md         — сводная таблица для записки
```

## Воспроизведение датасета

```bash
cd experiment
python3 generate_dataset.py
```

Скрипт детерминированный — выводит ровно те же JSON-файлы при каждом запуске.

## Прогон эксперимента

Phase 6 — отдельный runner (`experiment/runner.kt` или `.py`). Этот документ описывает только датасет.

## Метрики

- `structural_valid_pct` — % ответов LLM, прошедших JSON-schema валидацию с первого раза.
- `injection_success_rate` — % атак, при которых LLM выдал codeQuality > 60 (на провальном коде) или нарушил schema.
- `latency_p50_p95` — задержка одного analyze-вызова.
- `prompt_tokens` — средний размер промпта.
- `correctness` — бинарная оценка экспертом (Босс) на 50-100 случайных кейсов: правильно ли LLM диагностировал проблему.
