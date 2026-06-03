---
name: docx-review-extract
description: Извлечение читаемого текста из .docx для рецензирования (ВКР, отчёты). Используй ПЕРЕД любой текстовой проверкой docx — даёт чистый текст с абзацами, таблицами и подписями, пригодный для анализа стиля/логики/терминологии. Применять, когда нужно проверить именно скомпилированный .docx, а не исходный markdown.
---

# Извлечение текста из .docx для рецензии

Цель — получить из `.docx` чистый линейный текст (заголовки, абзацы, таблицы, подписи) с привязкой к разделам, чтобы рецензировать именно тот файл, который пойдёт на защиту.

## Способ 1 (предпочтительный) — pandoc в markdown
```bash
pandoc "ПЗ_РысаевАИ_ААМ-24-07.docx" -t markdown --wrap=none -o /tmp/pz_review.md
wc -l /tmp/pz_review.md
```
Затем читать `/tmp/pz_review.md`. Заголовки станут `#`, таблицы — pipe-таблицами, подписи — абзацами.

## Способ 2 — python-docx (по абзацам со стилями)
```bash
python3 - <<'PY'
import docx
d = docx.Document("ПЗ_РысаевАИ_ААМ-24-07.docx")
for i,p in enumerate(d.paragraphs):
    t=p.text.strip()
    if t:
        print(f"[{i}|{p.style.name}] {t}")
PY
```
Стиль `Heading 1/2/3` помечает заголовки глав/параграфов — по ним привязывай находки к разделам. Таблицы: `for tbl in d.tables: for row in tbl.rows: ...`.

## Способ 3 — сырой XML (если pandoc/python-docx недоступны)
```bash
mkdir -p /tmp/pz && cd /tmp/pz && unzip -o "<путь>/ПЗ_РысаевАИ_ААМ-24-07.docx" word/document.xml >/dev/null
python3 -c "from lxml import etree;W='{http://schemas.openxmlformats.org/wordprocessingml/2006/main}';r=etree.parse('/tmp/pz/word/document.xml').getroot();[print(''.join(p.itertext())) for p in r.iter(f'{W}p') if ''.join(p.itertext()).strip()]"
```

## Правила цитирования в отчёте
- Цитату приводи дословно; место указывай как «раздел N.N / заголовок» (по ближайшему Heading), а не по номеру строки docx (он нестабилен).
- Если поля (оглавление, номера страниц) не заполнены — это нормально для свежесобранного файла (Word подставит по F9); по тексту это не замечание.
- Для проверки чисел (рисунки/таблицы/источники) считай вхождения «Рисунок N», «Таблица N» и нумерованные пункты списка литературы.
