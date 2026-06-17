# Prompt Design — ai-analyzer

## Применённые паттерны и обоснование

### 1. Spotlighting (выделение данных)

Код студента оборачивается в XML-тег `<STUDENT_CODE_BASE64 lang=...>` и кодируется в Base64. Это пространственно отделяет ненадёжные данные от инструкций модели и снижает риск prompt-injection через содержимое кода.

Источник: Anthropic Prompt Engineering Docs — «Separating data from instructions» (docs.anthropic.com/en/docs/build-with-claude/prompt-engineering/use-xml-tags).

### 2. Instruction Hierarchy (иерархия авторитетности)

Система явно декларирует три уровня истины: (1) вердикт sandbox — детерминирован, не оспаривается; (2) AST-факты — авторитетны, не выдумываются; (3) explanation/issues/recommendations — зона ответственности модели. Это предотвращает ситуацию, когда LLM «улучшает» codeQuality вопреки провальным тестам.

Источник: OpenAI «System prompt best practices» (platform.openai.com/docs/guides/prompt-engineering) — раздел «Specify the steps to complete a task».

### 3. Sandwich / Role Reminder

После блока JSON-схемы системный промпт повторяет роль модели и запрет смены роли. Паттерн «sandwich» снижает вероятность дрейфа роли при длинных контекстах, когда инструкции в начале промпта «ослабевают» к концу.

Источник: Anthropic Prompt Engineering Docs — «Reinforce role after long instruction sections».

### 4. Few-Shot Learning (примеры разборов)

Три канонических примера (O(n) HashMap, O(n²) brute-force, заглушка) демонстрируют эталонный стиль и диапазон codeQuality. Загружаются из `/prompts/few-shot-examples.txt` один раз (lazy) — без оверхеда на каждый вызов.

Порядок примеров: от лучшего к худшему — соответствует принципу «anchor with positive example first» из OpenAI Few-Shot guide.

Источник: Brown et al. «Language Models are Few-Shot Learners» (NeurIPS 2020); Anthropic «Give Claude examples» (docs.anthropic.com/en/docs/build-with-claude/prompt-engineering/use-examples).

### 5. A/B Framework

`PromptVariant.ZERO_SHOT` / `FEW_SHOT` позволяет сравнивать качество объяснений в production-traffic. Метрики для оценки: точность classification codeQuality в диапазонах [0-40, 40-70, 70-100] на размеченной выборке, средняя длина explanation (FEW_SHOT ожидаемо длиннее).

## Что намеренно пропущено

- **Chain-of-Thought** — нецелесообразен: нам нужен только итоговый JSON, не рассуждения. CoT увеличил бы latency и стоимость без выигрыша по точности.
- **Constitutional AI self-critique** — избыточен при наличии JSON-schema валидации и retry-стратегии.
- **Dynamic few-shot selection** (embedding-based) — не реализован из-за отсутствия vector store в дипломе. Три статических примера покрывают основные случаи.
