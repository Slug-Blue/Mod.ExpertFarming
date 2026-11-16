# Wild Roots: цвета моркови — список ключей для нарратива (WR-704, ND)

Срез S1 (WR-701…703) добавил в `en_us.json` и `ru_ru.json` рабочие тексты, чтобы окно лупы и тултипы не показывали сырые ключи. Журнал (статья `guide.colors`, подсказки, легенда) не менялся: его пишет нарративный дизайнер (`docs/design/carrot-colours-climate.md` §1.4, WR-704). Это список ключей и мест, а не текст. Все тексты ниже рабочие, `[PLACEHOLDER]` до прохода ND.

## Уже есть (рабочие тексты, проверить стиль)

| Ключ | Где виден | Рабочий текст (en) |
|---|---|---|
| `wildroots.colour.white` / `.yellow` / `.orange` / `.purple` / `.red` | имя цвета в окне лупы | white, yellow, orange, purple, red |
| `wildroots.window.lens.colour` | окно лупы, стадия ≥ 3: первая строка цвета | Root colour: %s |
| `wildroots.window.lens.colour.heat.up` / `.heat.down` | окно лупы: жара | + stands the heat / − suffers in the heat |
| `wildroots.window.lens.colour.cold.up` / `.cold.down` | окно лупы: холод | + stands the cold / − suffers in the cold |
| `wildroots.window.lens.colour.water.less` / `.water.more` | окно лупы: вода | + needs less water / − needs more water |
| `wildroots.window.lens.colour.saturation` / `.saturation_less` | окно лупы: насыщение | + more filling (anthocyanins) / − less filling |
| `wildroots.tooltip.colour.purple` | тултип фиолетового урожая | Anthocyanins: a little more filling |
| `wildroots.cultivar.eastern_carrot.variant.red` | тултип и заголовок панели лупы красного урожая | Red root |
| `wildroots.tooltip.qual.color_r` | список качественных признаков | red root (lycopene) |

Слова про свойства (`heat.up` и т. д.) показываются по знаку поправки цвета из JSON вида, а не по числу: если баланс-проход (WR-715) поменяет число, не меняя знака, текст остаётся верным. Если знак изменится (например, у оранжевого `heat` станет 0), строка просто пропадёт.

## Сделать в WR-704

| Что | Ключ или файл | Заметка |
|---|---|---|
| Статья «Цвет корня» | `journal/entries/colors.json`, `guide.colors` (`en_us`, `ru_ru`) | расклады §1.1 (3:1, 192:48:9:3:4), свойства §2, красный как рецессив «двойного шага» |
| Подсказка красного корня | `hint.red_root`, `wildroots.journal.hint.red_root(.title)`, `journal/hint_links.json` → `guide.colors`, строка `line("carrot", …)` в `JournalHints` | «красный корень: рецессив, носитель сидел в грядке»; триггер: `HintTriggers.harvested`, `variant` 4 (сейчас без подсказки, `case 4` не добавлен) |
| Подсказка белого корня | `hint.white_cold`, там же | «белый: холодостойкий предок»; триггера нет, решает GD |
| Обобщить `hint.purple_root` | `wildroots.journal.hint.purple_root(.title)` | фиолетовый теперь и «жаростойкий, но не любит холод» |
| Легенда признаков | `journal/entries/traits.json`, если цвета попадают в легенду | слова шкалы лупы не менялись (0.20 / 0.35 / 0.55 / 0.75) |
| Страница-витрина красного урожая | `journal/entries/*.json`: `{"type": "spotlight", "item": "wildroots:eastern_carrot", "variant": "colour=red"}` | `SpotlightPage` и `GuidePageTypes` вариант `colour=red` уже понимают |
| Исторические справки | `wildroots.cultivar.eastern_carrot.note`, статья про цвет | названия генов пигментов моркови и утверждения о жаростойкости цветов: `[ПРОВЕРИТЬ]` приложения C п. 10 (второй человек, `04-narrative.md` §10.3) |
| Тексты климата | `wildroots.window.lens.env.*` не менялись | строка климата со словами по `S` — срез S2, WR-713 |

Ручная проверка этих мест в клиенте — в отчёте партии (`runClient`).
