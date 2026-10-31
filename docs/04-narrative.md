# Wild Roots (`wildroots`): нарратив, журнал, достижения, локализация

Фаза 1 · Narrative Designer · основан на `00-vision.md` (включая ответы владельца п. 6–19) и `01-gdd.md` v0.3.

## 0. Правила документа и решения

| Версия | Дата | Автор | Изменения |
|---|---|---|---|
| 0.1 | 2026-10-28 | Narrative Designer | Первая редакция: журнал, исторические справки, достижения, названия, тултипы, ключи |
| 0.3 | 2026-10-29 | Narrative Designer | Под GDD v0.3 (промежуточная v0.2 не выпускалась отдельно): 8 новых линий (тыква, арбуз, картофель, свёкла, тростник, какао, малина, яблоня), урожай — предмет мода с одним классом «грубый / простой / добротный / отборный» и согласованием по роду, переработка с качеством, мешочек для семян, клоны и прививка, сорт яблони с именем игрока, селекционер и его записки, волны выпуска, «Analyser», 6 новых триггеров; закрытые вопросы убраны |

**Решения, на которых стоит документ** (бывшие допущения Д1–Д3 закрыты):

| Решение | Источник | Что это значит для текстов |
|---|---|---|
| Журнал выдаётся автоматически при первом сборе дикого растения | Владелец №9 | Запись `welcome` и достижение `first_forager` привязаны к первому сбору |
| FD — полный набор: капуста, томат, лук, рис | Владелец №8 | Строки **FD** активны при установленном FD; капуста — линия ядра и существует всегда |
| Все полевые культуры, сладкие ягоды и яблоня получают линии; светящиеся ягоды — без линии | Владелец №6, №13; GDD §7.5–7.13 | Новые виды, сорта, справки, подсказки, достижения; ванильные сладкие ягоды = малина |
| **Финального ванильного продукта нет.** Урожай всегда — предмет мода с именем и классом качества; совместимость — через теги | Владелец №7, GDD §6.4–6.5 | Тултип урожая — «Морковь · добротная» (§7.6) |
| Не больше 6 вариантов стака: один класс 1–4 плюс горечь в классах 1–2; мешочек для семян | Владелец №19, GDD §5.3, §6.4 | Одна шкала слов классов; строки мешочка (§7.3) |
| Переработанные продукты наследуют качество сырья; бумага с первого дня, но плохая; сахар — с ландраса | Владелец №14, №16; GDD §6.6 | «Хлеб · грубый», «Грубая бумага», подсказки `bread_grade`, `rough_paper`, `no_sugar` (§7.7, §4.5) |
| Компромиссы и биологический предел | Владелец №7, GDD §3.10 | Подсказки `tradeoff`, `limit`; строки анализатора «уходит на…» |
| Деревенские поля живые и не эволюционируют; фермер полезнее; редкий фермер-селекционер | Владелец №10, №17; GDD §9.5–9.6 | Подсказки о деревне, профессия, записки, реплики на товарах (§4.7, §9.9) |
| Ландрасы тростника и какао — только в ареале | Владелец №18, GDD §7.9–7.10 | Подсказка `feral_planting`; торговец их не продаёт |
| Выпуск волнами | Владелец №15, GDD §20 | Строки и достижения линии входят с её волной (§3.2, §8) |
| en_us: **Analyser**; id `seed_analyser` | Владелец №11, GDD §10 | Во всех строках и ключах |
| Запись о Вавилове — только об экспедициях и центрах происхождения | Владелец №12 | Текст `vavilov` (§8.3) |
| Приняты мои предложения v0.1: шкала 0.20/0.35/0.55/0.75, метка и имя линии по матери, флаги `inspected`/`analyzed`, id дикой еды, `mother_bulb`, UUID посадившего, открытие вида, Shift для тиров 1–2, 13 триггеров | GDD §19.4 | Пометки `[ПРЕДЛОЖЕНИЕ]` с них сняты |

**Обозначения.**
- `[ПРЕДЛОЖЕНИЕ]` — id или правило, которого нет в GDD; нужно согласие Game Designer или Software Architect.
- `[ПРОВЕРИТЬ]` — техническое утверждение, которое инженер сверяет перед реализацией.
- **FD** — строка активна только при установленном Farmer's Delight.
- `%s`, `%d`, `%1$s` — аргументы `Component.translatable` в формате Minecraft.

**Что этот документ не делает.** Не задаёт числа, пороги и рецепты (это GDD и Economy Designer). Не создаёт lang-файлы: таблицы ниже — источник для `en_us.json` и `ru_ru.json` в фазе реализации.

## 1. Голос и тон

**Чей это голос.** Журнал — записная книжка самого игрока. Он не учитель и не рассказчик, а натуралист, который смотрит, записывает и осторожно делает выводы. Игрок узнаёт в записях свои же наблюдения и чуть-чуть больше.

### 1.1 Столпы голоса

| Столп | Как звучит | Как не звучит |
|---|---|---|
| Сначала увиденное, потом вывод | «Корни в одних зарослях разные: тонкие и потолще, белые и желтоватые.» | «Изменчивость — основа селекции.» |
| Короткие фразы, одна мысль на фразу | «Пчёлы садятся на зонтики. Семян стало больше.» | Абзац с тремя придаточными |
| Осторожность в выводах | «Похоже», «кажется», «стоит проверить» | «Это доказано», «всегда» |
| Совет вместо приказа | «Стоит посадить их рядом.» | «Посадите 24 растения!» |
| Реальный факт — коротко и честно | «Истинный дикий предок лука неизвестен.» | Даты с точностью до года, которых никто не знает |
| Тёплая ирония, редко | «Пчёлы злятся, но работу делают.» | Мемы, отсылки к интернету, шутки в каждой строке |

### 1.2 Правила для всех авторов

1. **Первый абзац у каждого вида — что игрок видит**, а не что ему делать (GDD §16).
2. **Без чисел в тиере 0–1.** Слова шкалы (§9.2) вместо цифр. Числа появляются только в строках анализатора и лаборатории.
3. **Без «как вы знаете».** Журнал не пересказывает игроку то, что он только что сделал, если это не ведёт к новому выводу.
4. **Русский без рода в первом лице.** Никаких «я заметил / заметила». Пишем настоящим временем или безлично: «Заметно, что…», «Похоже, что…». Английский — так же нейтрально.
5. **Без множественного числа рядом с `%d`.** У Minecraft нет форм множественного числа. Пишем «поколение: 7», «семян: 12», а не «7 поколений».
6. **Длина.** Подсказка — до 3 предложений. Историческая справка — 2–4. Строка тултипа — до 40 символов (по ширине в ванильном шрифте ≈ 200 px) `[ПРОВЕРИТЬ]`.
7. **Термины одни и те же везде** (§9.1): «лот семян», «маточник», «клон», «черенок», «семенной клубень», «ландрас», «сорт», «линия», «носитель», «класс урожая». Синонимы не используем.
8. **Латинские названия** (*Daucus carota* и т. п.) — только в исторических справках журнала, и то по необходимости; в тултипах, достижениях и подсказках латыни нет.

### 1.3 Эталонные строки (по ним проверяют все остальные)

- «Один колос не рассыпался. Все рядом рассыпались, а этот — нет.» — увиденное и интрига без вывода.
- «Одинокое растение у дома дало чахлое потомство. Похоже, ему не хватило соседей.» — осторожный вывод.
- «Истинный дикий предок лука неизвестен. Ближайшие родственники растут в горах Средней Азии.» — честный факт.

## 2. Схема ключей локализации

Пять пространств из задания плюс пять служебных. Служебные нужны потому, что вид и сорт — не предметы: у сорта есть имя, ступень и справка, а предмет урожая — лишь то, что он роняет (у пшеницы-сорта это `wildroots:wheat`, у полбы — `emmer_grain`). Журналу, тосту и лупе нужно имя сорта, а не предмета.

| Пространство | Шаблон | Что лежит | Пример |
|---|---|---|---|
| `item.wildroots.*` | `item.wildroots.<item_id>` | Имена предметов (ванильное правило Forge) | `item.wildroots.hand_lens` |
| `block.wildroots.*` | `block.wildroots.<block_id>` | Имена блоков | `block.wildroots.seed_analyser` |
| `advancements.wildroots.*` | `advancements.wildroots.<adv_id>.title` / `.description` | Достижения | `advancements.wildroots.vavilov.title` |
| `wildroots.journal.*` | `wildroots.journal.<раздел>.<id>[.<часть>]` | Все тексты журнала | `wildroots.journal.hint.inbreeding` |
| `wildroots.tooltip.*` | `wildroots.tooltip.<предмет\|общее>.<строка>` | Строки тултипов, шкалы признаков | `wildroots.tooltip.scale.size.3` |
| `wildroots.species.*` | `wildroots.species.<species_id>` | Имя вида | `wildroots.species.wild_carrot` |
| `wildroots.cultivar.*` | `wildroots.cultivar.<cultivar_id>` и `.note` | Имя сорта и историческая справка (ключ `.note` совпадает с полем `historical_note` в GDD §6.3) | `wildroots.cultivar.eastern_carrot.note` |
| `wildroots.message.*` | `wildroots.message.<id>` | Сообщения в чат или action bar, тосты | `wildroots.message.brush.wrong_species` |
| `wildroots.biome_category.*` | `wildroots.biome_category.<category_id>` | Имена категорий биомов для записок селекционера (§4.7) | `wildroots.biome_category.steppe` |
| `wildroots.grammar.*` | `wildroots.grammar.<namespace>.<path>` | Род и число предмета для слова класса, только в ru_ru (§9.8) | `wildroots.grammar.minecraft.bread` = `m` |

**Правила ключей.**
- Только `snake_case`, только ASCII, id совпадают с id JSON из GDD.
- Ключи для датапаков: если вид или сорт добавлен датапаком без своего ключа, показываем `id` пути с заглавной буквы и пишем предупреждение в лог `[ПРЕДЛОЖЕНИЕ]`. Автор датапака кладёт свои ключи в ресурс-пак по тем же шаблонам.
- Ключи сорта не зависят от предмета: `wildroots.cultivar.table_beet` = «Столовая свёкла», а её урожай — `item.wildroots.beetroot` = «Свёкла». Где имена совпадают (`wildroots.cultivar.carrot` и `item.wildroots.carrot`), это два ключа с одной строкой.
- Тексты журнала, которые зависят от вида, лежат под id вида: `wildroots.journal.species.<species_id>.<часть>`. Новый вид из датапака получает страницу, если есть хотя бы `.seen`; остальные части необязательны.

## 3. Виды, сорта, ступени и стадии

### 3.1 Дикие виды (`wildroots.species.<id>`)

| id (GDD) | en_us | ru_ru | Линия |
|---|---|---|---|
| `wild_wheat` | Wild Wheat | Дикая пшеница | Пшеница |
| `teosinte` | Teosinte | Теосинте | Кукуруза |
| `wild_carrot` | Wild Carrot | Дикая морковь | Морковь (обучающая) |
| `wild_cabbage` | Wild Cabbage | Дикая капуста | Капуста |
| `wild_tomato` **FD** | Wild Tomato | Дикий томат | Томат |
| `wild_onion` **FD** | Wild Onion | Дикий лук | Лук |
| `wild_rice` **FD** | Wild Rice | Дикий рис | Рис |
| `wild_gourd` | Wild Gourd | Дикая тыква | Тыква |
| `wild_watermelon` | Wild Watermelon | Дикий арбуз | Арбуз |
| `wild_potato` | Wild Potato | Дикий картофель | Картофель |
| `sea_beet` | Sea Beet | Морская свёкла | Свёкла |
| `wild_cane` | Wild Cane | Дикий тростник | Сахарный тростник |
| `wild_cacao` | Wild Cacao | Дикое какао | Какао |
| `wild_raspberry` | Wild Raspberry | Дикая малина | Малина (ванильные сладкие ягоды) |
| `wild_apple` | Wild Apple | Дикая яблоня | Яблоня |
| — (вид ещё не открыт) | Unknown Wild Plant | Неизвестное дикое растение | ключ `wildroots.species.unknown` |

«Теосинте» и «какао» в русском не склоняются и среднего рода: «теосинте выросло», «дикое какао зацвело».

### 3.2 Сорта (`wildroots.cultivar.<id>`)

Урожай — всегда предмет мода (GDD §6.1, §6.5); имена предметов — в §7. Ванильных и FD-продуктов наши растения не выдают.

| Линия | Ступень | id (GDD) | en_us | ru_ru | Урожай (`wildroots:`) |
|---|---|---|---|---|---|
| Пшеница | 1A | `einkorn` | Einkorn | Однозернянка | `einkorn_grain` |
| Пшеница | 1B | `emmer` | Emmer | Полба | `emmer_grain` |
| Пшеница | 2 | `wheat` | Wheat | Пшеница | `wheat` |
| Кукуруза | 1 | `primitive_maize` | Primitive Maize | Примитивная кукуруза | `primitive_maize` |
| Кукуруза | 2 | `maize` | Maize | Кукуруза | `corn` |
| Морковь | 1 | `eastern_carrot` | Eastern Carrot | Восточная морковь | `eastern_carrot` |
| Морковь | 2 | `carrot` | Carrot | Морковь | `carrot` |
| Капуста | 1 | `leaf_cabbage` | Leaf Cabbage | Листовая капуста | `leaf_cabbage` |
| Капуста | 2A | `head_cabbage` | Head Cabbage | Кочанная капуста | `cabbage` |
| Капуста | 2B | `broccoli` | Broccoli | Брокколи | `broccoli` |
| Тыква | 1 | `ancient_squash` | Ancient Squash | Древняя тыква | `ancient_squash` |
| Тыква | 2A | `field_pumpkin` | Field Pumpkin | Полевая тыква | `field_pumpkin` |
| Тыква | 2B | `sugar_pumpkin` | Sugar Pumpkin | Сахарная тыква | `sugar_pumpkin` |
| Арбуз | 1 | `nile_melon` | Nile Melon | Нильский арбуз | `nile_melon_slice` |
| Арбуз | 2A | `picnic_watermelon` | Picnic Watermelon | Крупный арбуз | `picnic_watermelon_slice` |
| Арбуз | 2B | `icebox_watermelon` | Icebox Watermelon | Сахарный арбуз | `icebox_watermelon_slice` |
| Картофель | 1 | `andean_potato` | Andean Potato | Андский картофель | `andean_potato` |
| Картофель | 2 | `potato` | Potato | Картофель | `potato` |
| Свёкла | 1 | `roman_beet` | Roman Beet | Римская свёкла | `roman_beet` |
| Свёкла | 2A | `table_beet` | Table Beet | Столовая свёкла | `beetroot` |
| Свёкла | 2B | `sugar_beet` | Sugar Beet | Сахарная свёкла | `sugar_beet` |
| Тростник | 1 | `garden_cane` | Garden Cane | Садовый тростник | `garden_cane_stalk` |
| Тростник | 2 | `noble_cane` | Noble Cane | Благородный тростник | `noble_cane` |
| Какао | 1 | `nacional_cacao` | Nacional Cacao | Какао насьональ | `nacional_cacao_beans` |
| Какао | 2A | `criollo` | Criollo | Криолло | `criollo_beans` |
| Какао | 2B | `forastero` | Forastero | Форастеро | `forastero_beans` |
| Малина | 1 | `garden_raspberry` | Monastery Raspberry | Монастырская малина | `garden_raspberry` |
| Малина | 2 | `raspberry` | Thornless Raspberry | Бесшипая малина | `raspberry` |
| Яблоня | 1 | `silk_road_apple` | Silk Road Apple | Яблоко Шёлкового пути | `silk_road_apple` |
| Яблоня | 2A | `aport` | Aport | Апорт | `aport_apple` |
| Яблоня | 2B | `antonovka` | Antonovka | Антоновка | `antonovka_apple` |
| Томат **FD** | 1 | `cherry_tomato` | Cherry Tomato | Черри-томат | `cherry_tomato` |
| Томат **FD** | 2 | `tomato` | Tomato | Томат | `tomato` |
| Лук **FD** | 1 | `landrace_onion` | Landrace Onion | Местный лук | `landrace_onion` |
| Лук **FD** | 2 | `onion` | Onion | Лук | `onion` |
| Рис **FD** | 1 | `early_rice` | Early Rice | Ранний рис | `early_rice` |
| Рис **FD** | 2 | `rice` | Rice | Рис | `rice` |

«Крупный арбуз» и «Сахарный арбуз» — русские имена веток GDD §7.6; английские — по реальным типам (picnic и icebox). «Какао насьональ» — в русском так пишут сорт Nacional; в английском — «Nacional Cacao». У малины id ландраса `garden_raspberry`, а в игре он называется «Монастырская малина» (прототип GDD §7.11).

**Волны выпуска** (GDD §20). Строки линии попадают в lang-файл вместе с её волной: 1 — морковь; 2 — пшеница, кукуруза, капуста; 3 — тыква, арбуз, картофель, свёкла; 4 — тростник, какао, малина, яблоня; FD — после волны 2. Общие строки (журнал, тултипы, сообщения) входят в волну 1 целиком, даже если описывают механику поздней волны: лишний ключ в lang-файле ничего не ломает, а переводчику проще работать с одним файлом.

**Сорт яблони, названный игроком** (GDD §5.7 п. 3). Сеянец, прошедший пороги апорта или антоновки, — новый сорт игрока, и игрок даёт ему имя. Имя сорта тогда состоит из двух частей: имени игрока и типа ветки.

| Ключ | en_us | ru_ru | Пример |
|---|---|---|---|
| `wildroots.cultivar.player_named` | "%1$s" (%2$s) | «%1$s» (%2$s) | «Златка» (тип апорта) |
| `wildroots.cultivar.aport.type` | aport type | тип апорта | — |
| `wildroots.cultivar.antonovka.type` | antonovka type | тип антоновки | — |
| `wildroots.cultivar.unnamed_seedling` | Unnamed seedling (%s) | Безымянный сеянец (%s) | Безымянный сеянец (тип антоновки) |

Имя вводится в журнале, на странице нового сорта (поле ввода, до 24 символов) `[ПРЕДЛОЖЕНИЕ]`; запасной путь — переименовать привой на наковальне, как лот. Имя переходит на все привитые от этого дерева деревья, в журнал и в тост.

**Цветовые варианты** (одна позиция сорта, разные текстуры — GDD §7.2–7.4). Ключ: `wildroots.cultivar.<id>.variant.<variant>`; добавляется к имени в тултипе маточника и лота как уточнение.

| Ключ | en_us | ru_ru |
|---|---|---|
| `wildroots.cultivar.eastern_carrot.variant.yellow` | yellow | жёлтая |
| `wildroots.cultivar.eastern_carrot.variant.purple` | purple | фиолетовая |
| `wildroots.cultivar.head_cabbage.variant.red` | red | краснокочанная |
| `wildroots.cultivar.maize.variant.yellow` / `primitive_maize.variant.yellow` | yellow-kernelled | желтозёрная |
| `wildroots.cultivar.maize.variant.red` / `primitive_maize.variant.red` | red-kernelled | краснозёрная |
| `wildroots.cultivar.maize.variant.white` / `primitive_maize.variant.white` | white-kernelled | белозёрная |
| `wildroots.cultivar.wild_carrot.variant.yellowish` (дикий корень с `y1y1`) | yellowish | желтоватый |
| `wildroots.cultivar.roman_beet.variant.red` / `.white` | red / white | красная / белая |
| `wildroots.cultivar.nile_melon.variant.red` (ландрас с `flesh_red`) | red-fleshed | красномясый |

Формат в тултипе: `%1$s (%2$s)` → «Восточная морковь (жёлтая)», «Eastern Carrot (yellow)».

### 3.3 Ступени селекции (`wildroots.tooltip.tier.<n>`)

| Ключ | en_us | ru_ru | Где видно |
|---|---|---|---|
| `wildroots.tooltip.tier.0` | Wild ancestor | Дикий предок | Тултип лота и маточника, журнал |
| `wildroots.tooltip.tier.1` | Landrace | Ландрас | То же; урожай ландраса |
| `wildroots.tooltip.tier.2` | Cultivar | Сорт | То же; урожай сорта |

Слово «ландрас» в игре объясняется один раз, в записи `wildroots.journal.hint.first_landrace` (§4.5).

### 3.4 Стадии роста (`wildroots.tooltip.stage.<id>`)

Показываются лупой (GDD §10, тир 1) и в модах-подсказках вроде Jade/WTHIT `[ПРОВЕРИТЬ]` интеграцию. Названия — по GDD §5.1.

| id | Для каких видов | en_us | ru_ru |
|---|---|---|---|
| `seed` | все | Seed | Семя |
| `dormant` | все, если сработал покой (§3.6 GDD) | Dormant seed | Семя в покое |
| `sprout` | все, стадия 1 | Sprout | Всходы |
| `vegetative` | однолетники, 2–3 | Growing | Вегетация |
| `rosette` | двулетники, 1–2 | Rosette | Розетка |
| `harvest_ready` | двулетники, 3 | Ready to harvest | Урожайная зрелость |
| `flowering` | однолетники, 4; маточник | Flowering | Цветение |
| `seed_ripening` | все, 5 | Ripening seed | Созревание семян |
| `mother_plant` | Съесть или посадить / Eat or Plant | Первый сбор двулетника (морковь, капуста, свёкла, **FD** лук) | Сорванный корень можно съесть, а можно посадить. Посаженный, он зацветёт и даст семена — это то же растение, со всеми его достоинствами. Так и отбирают: выкопать, посмотреть, лучших — обратно в землю. | A pulled root can be eaten, or planted. Planted, it flowers and sets seed, and it's the same plant with all its merits. That's how selection goes: dig up, look, put the best back. |
| `woody` | двулетник, оставленный на корню после зрелости | Going woody | Одревесневает |
| `bolting` | свёкла с `B_`: цветёт в первый год | Bolting | Пошла в стрелку |
| `setting_fruit` | тыква, арбуз, стадия 3: плеть ставит плоды | Setting fruit | Завязывает плоды |
| `tubers` | картофель, стадия 3 | Forming tubers | Завязывает клубни |
| `stalk` | тростник, рост колонны | Growing stalk | Тянется вверх |
| `tree` | какао, стадии 1–3 | Young tree | Молодое деревце |
| `regrowing` | многолетник после сбора (тростник, какао) | Regrowing | Отрастает |
| `waiting_for_heat` | тростник на стадии цветения при T < 0.95 | Needs tropical heat to flower | Для цветения нужно тропическое тепло |
| `juvenile` | сеянец яблони до первого цветения (GDD §5.7) | Young seedling, not yet bearing | Молодой сеянец, ещё не плодоносит |
| `grafted` | привитое деревце до первого цветения | Grafted sapling | Привитый саженец |
| `rootstock` | сеянец яблони на стадии ≤ 2 (можно прививать) | Can take a graft | Годится в подвой |

`harvest_ready` у разных видов уточняется через `wildroots.tooltip.stage.harvest_ready.<species_id>` (ключи и тексты — §9.7). Если ключа нет, берётся общий.

## 4. Полевой журнал

### 4.1 Как устроен

**Purpose.** Единственный учебник мода, который не выглядит учебником. Он записывает то, что игрок уже увидел, и добавляет один шаг вперёд.
**Player fantasy.** «Это мои записи. Тут всё, что я нашёл и вывел».

- **Предмет** `wildroots:field_journal` — только окно просмотра. Записи хранятся **на сервере, у игрока** (capability игрока), а не в NBT книги `[ПРЕДЛОЖЕНИЕ]`. Потерял журнал или умер — новый журнал (рецепт GDD §10) открывает те же записи. В мультиплеере у каждого свой журнал.
- **Выдача** — автоматически при первом сборе дикого растения (ответ владельца №9). Конфиг `giveJournalOnFirstHarvest = false` — запись `welcome` всё равно создаётся и ждёт, пока игрок скрафтит книгу.
- **Новая запись** — тост `wildroots.message.journal.new_entry` («Новая запись в журнале») и метка на вкладке. Не чаще одного тоста в 30 с: остальные записи копятся молча `[ПРЕДЛОЖЕНИЕ]`.
- **Каждая подсказка срабатывает один раз на игрока**, кроме `stagnation` и `missing_allele` (один раз на линию, повтор не раньше чем через 3 поколения этой линии).
- **Сервер решает, клиент рисует:** открытие записей — серверное событие; клиенту уходит список id открытых записей и данные авто-страниц (координаты патчей, даты, имена линий).

### 4.2 Разделы

| Вкладка (ключ) | en_us | ru_ru | Содержимое | Когда появляется |
|---|---|---|---|---|
| — | Field Journal | Полевой журнал | Титульная страница: «Полевой журнал. Владелец: %s» (`wildroots.journal.title_page`) | С выдачей |
| `wildroots.journal.tab.species` | Species | Виды | Страница на каждый открытый вид (§4.3) | С первым видом |
| `wildroots.journal.tab.cultivars` | Cultivars | Сорта | Коллекция сортов: открытые — полностью, остальные — силуэт и подсказка (§4.4) | С первым видом |
| `wildroots.journal.tab.lines` | Lines | Линии | Родословные именованных линий и линий, давших сорт (§6) | С первым именованным лотом или первым сортом |
| `wildroots.journal.tab.notes` | Notes | Заметки | Подсказки (§4.5) в порядке открытия, новые сверху | С первой подсказкой (`welcome`) |
| `wildroots.journal.tab.tools` | Tools | Инструменты | Короткая страница на каждый полученный инструмент (§4.6) | С первым инструментом |
| `wildroots.journal.tab.breeder` | Breeder's Notes | Записки селекционера | Страница из хижины селекционера и купленные записки о том, где искать лучшие признаки (§4.7) | С первой запиской или страницей из хижины |

### 4.3 Страница вида

Порядок блоков фиксирован. Пустые блоки не показываются.

| Блок | Ключ | Когда открывается | Содержимое |
|---|---|---|---|
| Заголовок | `wildroots.species.<id>` | Открытие вида | Имя, иконка дикой еды |
| Что видно | `wildroots.journal.species.<id>.seen` | Открытие вида | Описание внешнего вида (ниже) |
| Где найдено | авто, `wildroots.journal.species.found_at` | Каждый новый патч | Строка `found_at.row` (§9.7): биом (ванильный ключ `biome.minecraft.*`), x, z. До 8 последних патчей |
| Куда вести | `wildroots.journal.cultivar.<id>.goal` для каждого сорта вида | Открытие вида (GDD §10) | Словесные цели сортов (§4.4) |
| Историческая справка | `wildroots.journal.species.<id>.history` | Открытие вида | §5.1 |

**Открытие вида** (кастомное серверное событие `SpeciesDiscovered`): первый сбор зрелого растения вида **или** первый осмотр растения вида лупой. До этого лоты этого вида называются «Дикие семена» (§7.2): игрок сажает неизвестное и узнаёт, что выросло.

**Тексты «Что видно»** (`wildroots.journal.species.<id>.seen`):

| Вид | ru_ru | en_us |
|---|---|---|
| `wild_carrot` | Белые зонтики видно издалека. В середине зонтика часто сидит один тёмно-пурпурный цветок. Корень тонкий, белый, пахнет морковью — и бывает горьким. Корни в одних зарослях заметно разные. | White umbels, visible from far off. A single dark purple floret often sits at the centre of each. The root is thin and white, smells of carrot, and can be bitter. Roots from one patch differ noticeably. |
| `wild_wheat` | Невысокая трава с тонкими колосьями. Стоит тронуть спелый колос — и он рассыпается, зёрна падают в траву. Зерно мелкое, в жёстких плёнках. | A short grass with slender ears. Touch a ripe ear and it falls apart, the grains dropping into the grass. The grain is small and tightly hulled. |
| `teosinte` | Кустистая трава, похожая на кукурузу разве что листьями. Вместо початка — колоски на десяток зёрен, и каждое зерно в твёрдой скорлупе. Зубы её не берут. | A bushy grass that looks like maize only in its leaves. No cob: just small spikes of a dozen kernels, each sealed in a hard case. Teeth won't crack it. |
| `wild_cabbage` | Растёт на скалах у моря. Листья жёсткие, сизые, с восковым налётом и горькие. Цветы жёлтые, на высоких стеблях. | Grows on cliffs by the sea. The leaves are tough, blue-grey, waxy and bitter. Yellow flowers on tall stalks. |
| `wild_tomato` **FD** | Тонкий раскидистый куст с мелкими жёлтыми цветами. Ягоды красные, с горошину. Зелёные лучше не пробовать. | A thin, sprawling bush with small yellow flowers. Red berries the size of peas. Best not to try the green ones. |
| `wild_onion` **FD** | Пучок полых листьев и шар мелких бледных цветов. Луковица маленькая и такая острая, что слезятся глаза. | A tuft of hollow leaves and a globe of small pale flowers. The bulb is tiny and sharp enough to make eyes water. |
| `wild_rice` **FD** | Растёт прямо в воде, на мелководье. Метёлки поникшие, у зёрен длинные ости и красноватая оболочка. Спелое зерно почти сразу осыпается в воду. | Grows right in the water, in the shallows. Drooping panicles; the grains have long awns and a reddish skin. Ripe grain drops into the water almost at once. |
| `wild_gourd` | Плеть стелется по берегу реки. Плоды с небольшое яблоко, полосатые, в коре такой твёрдой, что её не поцарапать ножом. Мякоть горькая — пробовать не стоит. | A vine sprawls along the riverbank. The fruits are the size of a small apple, striped, with a rind too hard to scratch with a knife. The flesh is bitter; best left untasted. |
| `wild_watermelon` | Плеть на горячем песке, листья глубоко рассечены. Плоды круглые и полосатые, мякоть бледная и горькая. Зато внутри много воды. | A vine on hot sand, its leaves deeply cut. Round, striped fruits with pale, bitter flesh. But there is a lot of water inside. |
| `wild_potato` | Невысокий куст на горном лугу, цветы белые или лиловые. Клубни мелкие и разбегаются далеко от куста на длинных подземных побегах. Есть их не стоит: горчат и вредны. | A low bush on a mountain meadow, with white or mauve flowers. The tubers are small and run far from the bush on long underground shoots. Best not eaten: bitter and harmful. |
| `sea_beet` | Растёт у самого моря, на песке и камнях. Листья блестящие, мясистые и вполне съедобные. Корень тонкий и деревянистый, а многие растения цветут уже в первый год. | Grows at the very edge of the sea, on sand and stones. The leaves are glossy, fleshy and perfectly edible. The root is thin and woody, and many plants flower in their first year. |
| `wild_cane` | Высокая жёсткая трава у воды, стебли в коленцах, как у бамбука, только тоньше. Если разжевать стебель — сладости почти нет, одни волокна. | Tall, tough grass by the water, its stalks jointed like bamboo but thinner. Chew a stalk and there's barely any sweetness, only fibre. |
| `wild_cacao` | Невысокое деревце в тени джунглей. Стручки растут прямо на стволе. Мякоть вокруг бобов сладкая, а сами бобы горькие и терпкие. | A small tree in the jungle shade. The pods grow straight from the trunk. The pulp around the beans is sweet; the beans themselves are bitter and astringent. |
| `wild_raspberry` | Колючий куст на вырубках и гарях северного леса. Ягоды мелкие, кислые и рассыпаются в пальцах. Пробраться сквозь куст без царапин не выйдет. | A thorny bush in the clearings and burns of the northern forest. The berries are small, sour, and crumble in the fingers. There's no getting through it without scratches. |
| `wild_apple` | Яблони растут целым лесом на склонах гор. Деревья не похожи друг на друга: у одного яблоки мелкие и терпкие, у соседнего — крупные и почти сладкие. | Apple trees grow as a whole forest on the mountain slopes. No two trees are alike: one bears small, sour fruit, the next large and nearly sweet. |

### 4.4 Страница сорта и словесные цели

**Закрытый сорт** показывает силуэт иконки, имя `???` и цель `goal` — только после открытия вида. Пока вид не открыт, весь ряд скрыт.
**Открытый сорт** (событие `CultivarBred`, впервые для игрока):

| Строка | Ключ | ru_ru | en_us |
|---|---|---|---|
| Имя и ступень | `wildroots.cultivar.<id>` + `wildroots.tooltip.tier.<n>` | Восточная морковь · Ландрас | Eastern Carrot · Landrace |
| Дата | `wildroots.journal.cultivar.bred_on` | Выведен на %d-й день | Bred on day %d |
| Линия | `wildroots.journal.cultivar.line` | Линия: %1$s, поколение: %2$d | Line: %1$s, generation: %2$d |
| Предки | `wildroots.journal.cultivar.ancestry` | Путь: %s | Path: %s |
| Метка | `wildroots.journal.mark.from_the_wild` (§6) | Из дикой природы | From the Wild |
| Справка | `wildroots.cultivar.<id>.note` | §5.2 | §5.2 |

«Путь» — цепочка ступеней, через которые прошла материнская линия: «Дикая морковь → Восточная морковь → Морковь» (разделитель `wildroots.journal.arrow` = « → »).

**Словесные цели** (`wildroots.journal.cultivar.<id>.goal`). Пересказ порогов GDD §7–8 словами шкалы лупы (§9.2). Числа не пишем. Соответствие «порог → слово» дано в §9.2.

| Сорт | ru_ru | en_us |
|---|---|---|
| `einkorn` | Колос не рассыпается. В колоске одно зерно. Зерно не мелкое, растение не боится холода. | The ear holds together. One grain per spikelet. The grain isn't small, and the plant shrugs off the cold. |
| `emmer` | Колос не рассыпается. В колоске два зерна. Урожай не скудный, зерно не мельче среднего. | The ear holds together. Two grains per spikelet. A fair harvest, grain at least medium. |
| `wheat` | Всё, что у полбы, и ещё: зерно само выходит из плёнок при обмолоте. Урожай щедрый, зерно крупнее среднего, семена всходят дружно. | Everything emmer has, plus: the grain threshes free of its hulls. A generous harvest, grain above medium, seeds that sprout together. |
| `primitive_maize` | Зерно без каменной скорлупы. Один крепкий стебель вместо куста. Колос не рассыпается, початок не мельче среднего. | Kernels without the stony case. One sturdy stalk instead of a bush. The ear holds together; the cob is at least medium. |
| `maize` | Всё, что у примитивной кукурузы, и ещё: урожай щедрый, початки крупные, растёт не медленно, семена всходят дружно. | Everything primitive maize has, plus: a generous harvest, large cobs, steady growth, seeds that sprout together. |
| `eastern_carrot` | Корень не горький, не мельче среднего и хоть немного сладкий. И цветной: жёлтый или фиолетовый. | A root that isn't bitter, at least medium, and a little sweet. And coloured: yellow or purple. |
| `carrot` | Корень очень крупный, сладкий, без горечи. И оранжевый — без следа фиолетового. | A very large, sweet root with no bitterness. And orange, without a trace of purple. |
| `leaf_cabbage` | Листья не горькие и не мельче средних. Урожай не скудный. | Leaves that aren't bitter, at least medium. A fair harvest. |
| `head_cabbage` | Верхушка сворачивается в очень плотный кочан, листья крупные, урожай не скудный. Цветонос не торопится. Без горечи. | The top folds into a very firm head, the leaves are large, the harvest fair. No rush to bolt. No bitterness. |
| `broccoli` | Плотная шапка соцветий на толстом стебле. Кочан не завивается. Без горечи. | A dense crown of flower buds on a thick stem. No head forms. No bitterness. |
| `cherry_tomato` **FD** | Ягоды не мельче средних, не ядовиты и на вкус уже не грубые. | Berries at least medium, not poisonous, and no longer harsh to the taste. |
| `tomato` **FD** | Плоды крупные, урожай не скудный, вкус приятный. Крупнее и вкуснее сразу — трудно: большой плод водянист. | Large fruit, a fair harvest, a pleasant taste. Bigger and tastier at once is hard: a big fruit runs watery. |
| `landrace_onion` **FD** | Луковица не мельче средней, вкус приятный, без жгучести до слёз. | A bulb at least medium, a pleasant taste, no tear-stinging bite. |
| `onion` **FD** | Луковица очень крупная, вкус хороший, урожай не скудный. | A very large bulb, a good taste, a fair harvest. |
| `early_rice` **FD** | Метёлка не осыпается. Зерно не мельче среднего. | The panicle holds its grain. The grain is at least medium. |
| `rice` **FD** | Урожай щедрый, зерно крупнее среднего и белое, без красной оболочки. | A generous harvest, grain above medium and white, without the red skin. |
| `ancient_squash` | Плоды не мельче средних, без горечи, и мякоть уже не грубая. | Fruit at least medium, not bitter, the flesh no longer harsh. |
| `field_pumpkin` | Плоды очень крупные, урожай не скудный. Кора мягкая: мякоть съедобна, а плод можно вырезать. Без горечи. | Very large fruit, a fair harvest. A soft rind: the flesh is edible and the fruit can be carved. No bitterness. |
| `sugar_pumpkin` | Вкус отменный, плоды средние — не гиганты. Кора мягкая, без горечи. | An excellent taste, medium fruit, no giants. A soft rind, no bitterness. |
| `nile_melon` | Плоды не мельче средних, мякоть без горечи и приятная на вкус. | Fruit at least medium, the flesh not bitter and pleasant to taste. |
| `picnic_watermelon` | Плоды очень крупные, мякоть красная, вкус приятный. Без горечи. | Very large fruit, red flesh, a pleasant taste. No bitterness. |
| `icebox_watermelon` | Вкус отменный, мякоть красная, плоды средние — не гиганты. Без горечи. | An excellent taste, red flesh, medium fruit, no giants. No bitterness. |
| `andean_potato` | Клубни не мельче средних, урожай не скудный, и они не ядовиты. | Tubers at least medium, a fair harvest, and not poisonous. |
| `potato` | Клубни крупные, урожай щедрый, и все клубни держатся у самого куста. Не ядовиты. | Large tubers, a generous harvest, all of them close to the bush. Not poisonous. |
| `roman_beet` | Корень не мельче среднего и не грубый на вкус. Растение не цветёт в первый год — копит силы в корне. | A root at least medium, not harsh to taste. The plant doesn't flower in its first year; it stores its strength in the root. |
| `table_beet` | Корень очень крупный и красный, вкус хороший. Цветёт только на второй год. | A very large red root with a good taste. Flowers only in its second year. |
| `sugar_beet` | Корень белый, не мельче среднего и очень сахаристый. Гигантом сахарная свёкла не бывает. Цветёт только на второй год. | A white root, at least medium, very sugary. A sugar beet is never a giant. Flowers only in its second year. |
| `garden_cane` | Стебли не тоньше средних, сахаристость средняя. | Stalks at least medium, middling sugar. |
| `noble_cane` | Стебли толстые, мягкие и сочные, сахаристость высокая. | Thick, soft, juicy stalks with a high sugar content. |
| `nacional_cacao` | Бобы без лишней горечи, аромат приятный, урожай хоть какой-то. | Beans without the harsh bitterness, a pleasant aroma, a harvest of some sort. |
| `criollo` | Аромат отменный, урожай умеренный — не больше. Без горечи. | An excellent aroma, a moderate harvest and no more. No bitterness. |
| `forastero` | Урожай щедрый, дерево выносливое. Без горечи; аромат — какой получится. | A generous harvest, a hardy tree. No bitterness; the aroma, whatever it turns out to be. |
| `garden_raspberry` | Ягоды крупные, вкус приятный. | Large berries, a pleasant taste. |
| `raspberry` | Ягоды очень крупные, вкус хороший — и куст без шипов. | Very large berries, a good taste, and a bush without thorns. |
| `silk_road_apple` | Яблоки не мельче средних, приятные на вкус, без терпкой горечи. | Apples at least medium, pleasant to eat, without the astringent bite. |
| `aport` | Яблоки крупные, вкус хороший, без терпкости. Искать среди сеянцев. | Large apples, a good taste, no astringency. To be found among seedlings. |
| `antonovka` | Урожай щедрый, дерево не боится холода, яблоки не мельче средних, без терпкости. Искать среди сеянцев. | A generous harvest, a tree that takes the cold, apples at least medium, no astringency. To be found among seedlings. |

### 4.5 Заметки: подсказки и триггеры

Ключ: `wildroots.journal.hint.<id>`; заголовок записи — `wildroots.journal.hint.<id>.title`. Все триггеры — **кастомные серверные события** мода (в столбце указано событие и условие). «Своё растение» — посаженное этим игроком (метка `player` и UUID посадившего в BlockEntity, GDD §6.2, §9.5).

#### Начало и отбор

| id | Заголовок ru / en | Триггер | ru_ru | en_us |
|---|---|---|---|---|
| `welcome` | Первая запись / First Entry | `SpeciesDiscovered` через сбор, первый раз | Найден вид: %s. Растения в одном месте не одинаковые. Если сажать семена лучших, потомство, похоже, пойдёт в них. | New species: %s. Plants from one spot are not alike. Sow seed from the best, and the offspring seem to take after them. |
| `plant_together` | Соседи / Neighbours | Первая посадка лота | Растения одного вида опыляют друг друга, если растут рядом. Одинокое растение опыляет само себя — и это, говорят, не к добру. | Plants of one kind pollinate each other when they grow close. A lone plant pollinates itself, which is said to bode ill. |
| `lone_plant` | Одно в поле / Alone in the Field | Посадка лота перекрёстника (не пшеница, не рис), когда в радиусе 8 нет своих растений того же вида | Растение посажено в одиночку. Если рядом не зацветёт никто из своих, оно опылит само себя, и потомство может ослабнуть. | This plant stands alone. If none of its kind flowers nearby, it will pollinate itself, and the offspring may come out weak. |
| `small_population` | Маловато / Too Few | Созревание своего растения, когда своих растений вида в радиусе 8 не больше 9 (GDD §12.2) | Грядка маловата. Из горстки трудно выбрать лучших, а редкие признаки легко потерять. Хороший ориентир — грядка пять на пять. | The bed is too small. A handful gives little to choose from, and rare traits slip away. A five-by-five bed is a good rule of thumb. |
| `select_quarter` | Кого оставить / Whom to Keep | Сбор 16-го своего растения одного вида за один игровой день | На семена хватит каждого четвёртого — самых лучших. Строже — быстрее, но так легко потерять то, что ещё не проявилось. | Every fourth plant, the very best, is enough for seed. Choosier is faster, but it's easy to lose what hasn't shown itself yet. |
| `mother_plant` | Съесть или посадить / Eat or Plant | Первый сбор двулетника (морковь, капуста, **FD** лук) | Сорванный корень можно съесть, а можно посадить. Посаженный, он зацветёт и даст семена — это то же растение, со всеми его достоинствами. Так и отбирают: выкопать, посмотреть, лучших — обратно в землю. | A pulled root can be eaten, or planted. Planted, it flowers and sets seed, and it's the same plant with all its merits. That's how selection goes: dig up, look, put the best back. |
| `left_on_stalk` | Вслепую / Picking Blind | Первый лот с двулетника, оставленного на корню (стадия `woody`) | Оставленное на корню растение зацвело само. Удобно, но корня так никто и не увидел — отбор вслепую. | Left in the ground, the plant flowered on its own. Convenient, but nobody ever saw the root. Selection blindfolded. |

#### Признаки, которые видно

| id | Заголовок ru / en | Триггер | ru_ru | en_us |
|---|---|---|---|---|
| `bitter` | Горько / Bitter | Первое поедание горькой еды | Горько. Но горчат не все растения, а только некоторые — значит, горечь можно не взять с собой в следующее поколение. | Bitter. But only some plants are, not all, which means the bitterness can be left behind next generation. |
| `toxic_tomato` **FD** | Паслёновые / Nightshades | Первое отравление ягодой дикого томата | В Европе томаты долго считали ядовитыми. У дикого томата для этого есть основания: зелёные ягоды и правда опасны, а у некоторых растений — и спелые. | Europe long thought tomatoes poisonous. The wild tomato gives some cause: green berries really are harmful, and on some plants even the ripe ones. |
| `yellow_root` | Жёлтый корень / A Yellow Root | Первый сбор моркови с жёлтым корнем | Жёлтый корень! Похоже, так начиналась восточная морковь. Цвет проявляется, только если его передали оба родителя, а у остальных он может прятаться. | A yellow root! This may be how the eastern carrot began. The colour only shows when both parents pass it on; in the rest it can hide. |
| `orange_root` | Оранжевый / Orange | Первый сбор оранжевого корня | Оранжевый корень. Одного жёлтого для него мало: нужен ещё один скрытый признак, и тоже от обоих родителей. Беречь как зеницу ока. | An orange root. Yellow alone isn't enough: it needs a second hidden trait, again from both parents. Guard it closely. |
| `purple_root` | Фиолетовый / Purple | Первый сбор фиолетового корня | Фиолетовый корень. Этот цвет сильный: хватает одного родителя. Зато он перекрывает и жёлтый, и оранжевый. | A purple root. This colour is strong: one parent is enough. But it masks both yellow and orange. |
| `shattering` | Рассыпалось / It Shattered | Первый сбор осыпающегося растения (пшеница, теосинте, **FD** рис) | Колос рассыпался прямо в руках, и большая часть зёрен ушла в землю. Дикому растению так и надо. Земледельцу — нет. | The ear fell apart in hand, and most of the grain went to the ground. Good for a wild plant. Not for a farmer. |
| `full_ear` | Целый колос / One Ear Held | Первый сбор неосыпающегося растения дикого вида | Один колос не рассыпался. Все рядом рассыпались, а этот — нет. Его семена стоит держать отдельно. | One ear didn't shatter. All the others did; this one held. Its seed is worth keeping apart. |
| `brush_for_wheat` | Сама себе пара / Its Own Partner | Первый сбор дикой пшеницы (GDD §12.3, вывод 3) | Дикая пшеница опыляет себя сама, ещё в закрытом цветке. Соседи ей почти не нужны и почти ничего не дают. Чтобы соединить двух хороших родителей, пыльцу придётся переносить вручную: перо, нить и палка. | Wild wheat pollinates itself inside the closed flower. Neighbours barely matter to it. To bring two good parents together, the pollen has to be moved by hand: a feather, some string and a stick. |
| `dormancy` | Неровные всходы / Uneven Sprouting | Первый лот, где часть семян ушла в покой | Всходы неровные: одни семена проросли, другие будто чего-то ждут. В дикой природе так надёжнее. На грядке хочется, чтобы всходили дружно. | Patchy sprouting: some seeds came up, others seem to be waiting. In the wild that's a safe bet. In a garden, it's better when they rise together. |
| `teosinte_case` | Каменная скорлупа / Stone Shell | Первый сбор теосинте | Каждое зерно — в каменной скорлупе. Но изредка попадается растение с голым зерном. Такое стоит запомнить. | Every kernel sits in a stony shell. Now and then, though, a plant has naked kernels. That one is worth remembering. |
| `single_stalk` | Один стебель / One Stalk | Первое растение теосинте с `Tb_` в поле зрения игрока (осмотр лупой или сбор) | Это растение выросло одним стеблем, без куста, и колос у него крупнее. Похоже, такая перемена передаётся по наследству. | This plant grew a single stalk instead of a bush, and its ear is bigger. A change like that looks inherited. |
| `wind_pollen` | Пыльца на ветру / Pollen on the Wind | Первое цветение своего теосинте | Пыльца теосинте летит по ветру, и далеко. Всё, что цветёт поблизости, перемешается. Чистая линия растёт подальше от остальных. | Teosinte pollen rides the wind, and far. Anything flowering nearby will mix. A pure line grows well away from the rest. |
| `cabbage_roads` | Две дороги / Two Roads | Первое выведение листовой капусты | У капусты две дороги: свернуть листья в кочан или нарастить соцветия. Одно мешает другому, и на одной грядке обе не вывести. | Cabbage has two roads: fold its leaves into a head, or build up its flower buds. Each gets in the other's way; one bed won't give both. |
| `rice_water` **FD** | Ноги в воде / Feet in Water | Первая попытка посадить рис не в воду | Дикий рис растёт только в воде. На сухой земле он не взойдёт. | Wild rice grows only in water. It won't come up on dry ground. |

#### Опыление и пчёлы

| id | Заголовок ru / en | Триггер | ru_ru | en_us |
|---|---|---|---|---|
| `bees` | Пчёлы / Bees | Первый визит пчелы к своему растению | Пчёлы перелетают с цветка на цветок и носят пыльцу между растениями. После их визитов семян заметно больше. | Bees hop from flower to flower, carrying pollen between plants. After their visits there's noticeably more seed. |
| `no_pollinators` | Пусто в семенниках / Empty Seed Heads | Первый лот насекомоопыляемого вида, завязанный без опылителя | Семенников мало. Похоже, цветы никто не навестил. Нужны опылители: пчелиное гнездо неподалёку можно перенести, если обращаться с ним очень бережно. | Few seeds set. It seems nobody visited the flowers. Pollinators are needed; a wild bee nest nearby can be moved, if handled with a very gentle touch. |
| `hand_pollination` | Своими руками / By Hand | Первое скрещивание кистью двух разных растений | Пыльца перенесена вручную. Почти все семена будут от выбранного отца, но не все: цветок открыт, и пчёлы своё дело знают. | Pollen moved by hand. Nearly all the seed will be from the chosen father, but not all: the flower is open, and the bees know their business. |
| `forced_self` | Сам с собой / Selfing | Первое принудительное самоопыление кистью (не капуста) | Самоопыление вытаскивает наружу скрытые признаки — те, что прячутся у родителя. Но и скрытые слабости тоже. | Selfing brings hidden traits into the open, the ones the parent carries quietly. Hidden weaknesses too. |
| `bud_pollination` | В бутоне / In the Bud | Первое самоопыление капусты кистью | Капуста не принимает свою пыльцу. Но нераскрытый бутон ещё не умеет отказывать, и иногда получается. Семян мало, зато свои. | Cabbage refuses its own pollen. An unopened bud hasn't learned to refuse yet, so sometimes it works. Little seed, but its own. |
| `self_incompat` | Не со своими / Not With Kin | Первый лот капусты с завязываемостью, урезанной самонесовместимостью | Капуста не опыляет сама себя и близкую родню. Нужны разные растения, лучше из разных мест, и пчёлы. | Cabbage won't pollinate itself or its close kin. It needs different plants, ideally from different places, and bees. |

#### Инбридинг и свежая кровь

| id | Заголовок ru / en | Триггер | ru_ru | en_us |
|---|---|---|---|---|
| `inbreeding` | Чахлые / Ailing | Первое своё растение с витальностью ниже порога чахлости (GDD §3.7) | Растения бледные и низкие. Их родители — слишком близкая родня или одно растение, опылившее само себя. Обычно помогает свежая кровь из других зарослей. | Pale, short plants. Their parents were too closely related, or one plant pollinated itself. Fresh blood from another patch usually helps. |
| `hybrid_vigor` | Сила гибрида / Hybrid Vigour | Первое своё растение без признаков чахлости, у которого оба родителя были чахлыми | Две чахлые линии дали крепкое потомство. Похоже, слабости у них были разные, и в гибриде они прикрыли друг друга. | Two ailing lines gave sturdy offspring. Their weaknesses must have been different ones, and in the hybrid each covered for the other. |
| `stagnation` | Застой / Stalled | GDD §14: три поколения подряд лучший `G` линии по признакам целевого профиля не растёт | Линия %s застыла: три поколения без улучшений. Похоже, изменчивость кончилась. Нужен свежий материал: из другого биома, из деревни, из одичавшего сада или древнего тайника. Селекционер подскажет, где искать. | Line %s has stalled: three generations without progress. The variety seems used up. Fresh stock is needed, from another biome, a village, a feral garden or an old cache. A plant breeder can tell where to look. |
| `missing_allele` | Пропажа / Gone Missing | GDD §14: после анализатора в линии нет ни одного носителя аллеля, нужного следующему сорту | В линии %s, похоже, не осталось ни одного носителя нужного признака. Вернуть его можно только скрещиванием с теми, у кого он есть. | Line %s seems to have no carriers of the trait it needs. The only way back is crossing with plants that have it. |
| `second_biome` | Другие места / Other Places | Открытие патча вида во второй категории биома (GDD §3.2) | Ещё одни заросли, но в другом месте, и растения здесь сильны в другом. Если свести их вместе, выйдет больше, чем у каждого по отдельности. | Another patch, somewhere else, and these plants are strong in other ways. Brought together, they'll give more than either alone. |
| `village_seeds` | Деревенские семена / Village Seed | Событие `villager_landrace`: первая покупка лота у фермера или первый сбор с живой деревенской фермы | Деревенские семена. Кто-то уже прошёл полпути: горечи нет, признаки одомашнивания есть. Но деревенское поле не меняется год от года, и до сорта путь ещё неблизкий. | Village seed. Someone has walked half the road already: no bitterness, the signs of taming are there. But a village field stays the same year after year, and the cultivar is still a long way off. |
| `rare_allele` | Из тайника / From the Cache | Первое получение лота с флагом «редкий аллель» (GDD §9.4) | Эти семена хранили не просто так. В них может найтись то, чего нет в диких зарослях. | These seeds weren't kept for nothing. They may hold something no wild patch has. |
| `far_lands` | Издалека / From Afar | Первая покупка лота у странствующего торговца | Семена из далёких земель. У тамошних растений свои сильные стороны. | Seeds from distant lands. Plants from there have strengths of their own. |
| `climate` | Не по климату / Out of Its Element | Первое своё растение со штрафом температуры (GDD §3.5) | Растению здесь не по климату: растёт, но через силу. Можно отбирать самых выносливых, а можно сажать там, где ему привычнее. | This plant is out of its climate: it grows, but grudgingly. Select the hardiest, or plant it somewhere more familiar. |
| `bone_meal` | Подкормка / A Feed | Первая костная мука на растение мода | Костная мука торопит рост, но не делает растение лучше. Потомство от неё не изменится. | Bone meal hurries growth but doesn't improve the plant. The offspring won't change for it. |
| `unattended` | Без присмотра / Unattended | Первый лот, собранный поршнем или водой (GDD §14) | Семена собраны без присмотра, и кто опылял цветы — неизвестно. Считать их стоит самоопылёнными. | Seed gathered unattended; who pollinated the flowers is anyone's guess. Best treat it as selfed. |

#### Сорта и инструменты

| id | Заголовок ru / en | Триггер | ru_ru | en_us |
|---|---|---|---|---|
| `looks_like` | Похоже, но… / Looks Like It, But… | Первая надпись лупы «похоже на сорт» (GDD §6.2, п. 5) | Растение похоже на %s. Но похожее ещё не значит такое же. Стоит посадить его семена и посмотреть, держится ли признак в потомстве. | This plant looks like %s. Looking like isn't being, though. Sow its seed and see whether the trait holds in the offspring. |
| `first_landrace` | Ландрас / Landrace | Первое `CultivarBred` ступени 1 | Выведен ландрас — местный сорт. Ещё не всё, чего хочется, но уже не дикое растение: съедобно, без горечи, и признаки держатся. Так выглядели огороды первых земледельцев. | A landrace: a local variety. Not everything one could want, but no longer wild. Edible, not bitter, and its traits hold. The first farmers' gardens looked like this. |
| `first_cultivar` | Сорт / A Cultivar | Первое `CultivarBred` ступени 2 | Сорт. Каждое растение в нём — потомок того самого первого, с которого всё началось. | A cultivar. Every plant in it descends from that first one, where it all began. |
| `mutation` | Странное растение / An Odd One | Первое своё растение с мутацией качественного признака, видимой в фенотипе (GDD §3.8) | Необычное растение, не похожее ни на одного из родителей. Такое бывает редко. Его семена стоит сохранить. | An unusual plant that resembles neither parent. That's rare. Its seed is worth saving. |
| `carrier` | Носитель / Carrier | Первый «носитель» в анализаторе | Анализатор нашёл носителя: признак есть, но спрятан, и снаружи его не видно. Если скрестить двух носителей, примерно у четверти потомства он проявится. | The analyser found a carrier: the trait is there but hidden, invisible from outside. Cross two carriers and about a quarter of the offspring will show it. |
| `half_fixed` | Не закреплено / Not Yet Fixed | Первый сорт, держащийся на гетерозиготе (GDD §3.6, правило проверки) | Сорт держится на одной копии признака из двух. Примерно у четверти потомства он пропадёт. Чтобы закрепить, нужны растения, получившие признак от обоих родителей. | The cultivar rests on one copy of the trait out of two. About a quarter of the offspring will lose it. To fix it, look for plants that got it from both parents. |
| `genotype_lab` | Всё как есть / Everything Laid Bare | Первое использование лаборатории | Лаборатория видит всё: скрытые слабости, аллели самонесовместимости, вероятный исход скрещивания. Выбор всё равно остаётся за селекционером. | The lab sees everything: hidden weaknesses, self-incompatibility alleles, the likely outcome of a cross. The choice still belongs to the breeder. |

#### Компромиссы и качество

| id | Заголовок ru / en | Триггер | ru_ru | en_us |
|---|---|---|---|---|
| `tradeoff` | Всего сразу нельзя / Can't Have It All | Первое своё растение, у которого компромисс (GDD §3.10, слой 1) срезал вкус хотя бы на ступень шкалы | Плоды крупные, а вкус пресный. Похоже, растение тратит силы на размер, и на сладость их не хватает. Гигант редко бывает самым вкусным. | Big fruit, bland taste. The plant seems to spend its strength on size, with too little left for sweetness. The giant is rarely the tastiest. |
| `limit` | Предел / The Limit | Первый анализ, где сработал ресурсный бюджет (GDD §3.10, слой 2) | По наследству растение может больше, чем выходит на деле. Сил у него не бесконечно: что-то приходится отдать. Выбрать, во что вложиться, — это и значит вывести сорт. | By heredity this plant could do more than it shows. Its strength isn't endless; something has to give. Choosing what to invest in is what breeding a cultivar means. |
| `grades` | Не одинаковые / Not All Alike | Первый урожай, разложившийся в инвентаре минимум на два класса | Урожай с одной грядки лёг в разные кучки: грубый к грубому, добротный к добротному. Чем лучше растение, тем сытнее его урожай. | One bed's harvest sorted itself into different piles: coarse with coarse, fine with fine. The better the plant, the more filling its crop. |
| `bitter_grade` | Горечь портит всё / Bitterness Spoils It | Первый горький урожай класса 2 | Крупный, хороший с виду — и горький. Горький урожай добротным не бывает. | Big and fine-looking, and bitter. A bitter crop is never fine stock. |
| `bread_grade` | Хлеб помнит зерно / Bread Remembers the Grain | Первый хлеб, испечённый игроком | Из какого зерна хлеб, такой и хлеб: из дикого — грубый и несытный, из отборного — лучше покупного. Ложка мёда в тесте поднимает его ещё на ступень. | Bread is only as good as its grain: wild grain makes coarse, thin bread; choice grain makes bread better than any bought loaf. A spoonful of honey in the dough lifts it a step further. |
| `rough_paper` | Грубая бумага / Rough Paper | Первая бумага класса 1 | Бумага из дикого тростника грубая и волокнистая. Писать на ней можно, но из садового тростника её выйдет больше и лучше. | Paper from wild cane is rough and stringy. It takes ink, but garden cane would give more of it, and better. |
| `no_sugar` | Сахара нет / No Sugar | Первая попытка сделать сахар из дикого тростника или морской свёклы | Сахара из этого не выжать — одни волокна. Нужен тростник или свёкла послаще, хотя бы ландрас. | There's no sugar to be had from this, only fibre. It needs a sweeter cane or beet, a landrace at least. |

#### Клоны, прививка и многолетники

| id | Заголовок ru / en | Триггер | ru_ru | en_us |
|---|---|---|---|---|
| `clone` | Копия / A Copy | Первое получение клона: семенной клубень, черенок, отпрыск малины, привой | Из этого вырастет то же самое растение — копия, без перемен. Удобно, чтобы размножить лучшее. Но ничего нового клон не принесёт. | This will grow into the very same plant, an unchanged copy. Handy for multiplying the best. But a clone brings nothing new. |
| `clone_of_clone` | Всё то же / Still the Same | Событие `plant_clone`: посажен клон растения, которое само выросло из клона | Клон клона — всё то же растение. Новое дают только семена: цветы, опыление, сеянцы. А лучший сеянец — снова в клоны. | A clone of a clone is still the same plant. Only seed brings anything new: flowers, pollen, seedlings. Then the best seedling goes back to cloning. |
| `potato_berries` | Ягоды картофеля / Potato Berries | Первый лот с ягод картофеля | На кусте завязались зелёные ягоды с семенами. Из них вырастет что угодно: сеянцы все разные. Есть ягоды нельзя — они ядовиты. | Green berries full of seed have set on the bush. Anything might grow from them; every seedling differs. The berries are not to be eaten: they're poisonous. |
| `long_stolons` | Беглые клубни / Runaway Tubers | Первый сбор картофеля, у которого часть клубней ушла в соседние блоки | Часть клубней ушла далеко от куста и потерялась. Дикому картофелю так и надо — расселяться. Кусты, у которых клубни держатся рядом, стоит отметить. | Some tubers ran far from the bush and were lost. Fine for a wild potato that wants to spread. Bushes that keep their tubers close are worth marking. |
| `cane_tropics` | Только в тропиках / Tropics Only | Первый свой тростник, дошедший до цветения при T < 0.95 | Тростник растёт, но не цветёт. Ему нужно настоящее тепло — саванна или джунгли. Дома его можно только размножать черенками, а скрещивать — там, где он цветёт. | The cane grows but won't flower. It needs real heat: savanna or jungle. At home it can only be multiplied from cuttings; crossing has to happen where it blooms. |
| `cacao_hand` | Пчёлы не помогут / No Help From Bees | Первое цветение своего какао | Цветы какао крошечные и сидят прямо на стволе. Пчёлы их не замечают: в природе их опыляют мелкие мошки. Надёжнее перенести пыльцу кистью. | Cacao flowers are tiny and sit right on the trunk. Bees ignore them; in the wild, tiny midges do the work. A brush is the surer way. |
| `perennial` | Растение остаётся / It Stays | Первый сбор многолетника (тростник, какао, малина, яблоня) | После сбора растение не гибнет и снова даст урожай. Но и не меняется: лучше оно станет только в потомстве. | The plant survives the harvest and will bear again. But it won't change; only its offspring can be better. |
| `thornless` | Без шипов / No Thorns | Первый куст малины без шипов (`ss`) | Сквозь этот куст можно пройти и не оцарапаться. Шипов нет совсем. Признак проявляется, только если его передали оба родителя. | This bush can be walked through without a scratch. Not a thorn on it. The trait only shows when both parents pass it on. |
| `apple_seedlings` | Яблоко от яблони / The Apple and the Tree | Первая посадка лота яблони | Сеянцы яблони не повторяют мать. Из косточек лучшего дерева вырастут самые разные деревья, и большинство — так себе. Ждать плодов придётся долго. | Apple seedlings don't take after their mother. The pips of the best tree grow into all kinds of trees, most of them middling. And the wait for fruit is long. |
| `apple_pips` | Горькие косточки / Bitter Pips | Первый сбор лота-косточек яблони | В косточках яблок есть амигдалин — горький и ядовитый. Само яблоко есть можно, а косточки — не корм для птиц. | Apple pips contain amygdalin, bitter and poisonous. The apple is fine to eat; the pips are no feed for birds. |
| `seedling_cultivar` | Новый сорт / A New Variety | Событие `seedling_cultivar`: свой сеянец яблони прошёл пороги апорта или антоновки | Среди сеянцев нашлось одно особенное дерево. Это новый сорт — такого раньше не было ни у кого. Ему нужно имя. | One remarkable tree has turned up among the seedlings. It's a new variety; nobody has had one like it before. It needs a name. |
| `graft` | Прививка / Grafting | Событие `graft_apple`: первая удачная прививка | Привой прижился. Дерево вырастет копией того, с которого срезан привой, и зацветёт без долгого ожидания. Так сорта яблонь и живут: в прививках, а не в семенах. | The graft has taken. The tree will grow as a copy of the one the scion came from, and flower without the long wait. That's how apple varieties live: in grafts, not in seed. |
| `graft_failed` | Не прижился / It Didn't Take | Первая неудачная прививка | Привой не прижился. Бывает и у опытных садоводов. Подвой цел — можно попробовать снова. | The graft didn't take. It happens to seasoned gardeners too. The rootstock is fine; try again. |

#### Тыквы, арбузы, свёкла

| id | Заголовок ru / en | Триггер | ru_ru | en_us |
|---|---|---|---|---|
| `stone_gourd` | Каменная тыква / Stone Gourd | Первый сбор дикой тыквы | Кора как камень, мякоть горькая. Похоже, в таких плодах ценили семена, а не мякоть. Мягкая кора у некоторых растений бывает — её стоит искать. | A rind like stone, bitter flesh. In fruits like these it must have been the seeds that mattered. Some plants do have a soft rind; worth looking for. |
| `empty_flowers` | Пустые цветы / Empty Flowers | Первая плеть тыквы или арбуза, отцветшая без плодов из-за отсутствия опылителя | Цветы отцвели, а плодов почти нет. У тыквы мужские и женские цветки разные, и пыльцу между ними носят пчёлы. Без них плод не завяжется. | The flowers have faded, and there's almost no fruit. Squash has separate male and female flowers, and bees carry the pollen between them. Without bees, no fruit sets. |
| `carve_small` | Мал для резьбы / Too Small to Carve | Первая попытка вырезать плод мельче «крупного» | Плод слишком мал, чтобы вырезать лицо. Нужна тыква покрупнее — а значит, ещё несколько поколений отбора. | Too small to carve a face into. A bigger pumpkin is needed, and that means a few more generations of selection. |
| `carve_first` | Первое лицо / The First Face | Событие `carve_pumpkin`: первая резная тыква | Первая резная тыква. Теперь есть из чего собрать голема. Чем крупнее плод, тем ярче светит фонарь. | The first carved pumpkin. Now there's something to build a golem with. The bigger the fruit, the brighter the lantern. |
| `red_flesh` | Красная мякоть / Red Flesh | Первый разрезанный арбуз с красной мякотью | Мякоть красная! У диких арбузов она бледная. Красный цвет проявляется, только если его передали оба родителя. | Red flesh! Wild watermelons are pale inside. The red only shows when both parents pass it on. |
| `beet_bolting` | В стрелку / Bolting | Первый сбор морской свёклы, зацветшей в первый год | Свёкла зацвела в первый же год, а корень одеревенел. Те, что не спешат цвести, копят силы в корне — их и стоит отбирать. | The beet flowered in its first year, and the root went woody. Those in no hurry to flower store their strength in the root; those are the ones to keep. |
| `beet_roads` | Красная или белая / Red or White | Первое выведение римской свёклы | Свёклу можно вести к красному сочному корню — для стола, или к белому сахаристому — для сахара. Сахар и размер мешают друг другу: сахарная свёкла не бывает гигантом. | The beet can go towards a red, juicy root for the table, or a white, sugary one for sugar. Sugar and size get in each other's way: a sugar beet is never a giant. |

#### Деревня, ареал и селекционер

| id | Заголовок ru / en | Триггер | ru_ru | en_us |
|---|---|---|---|---|
| `village_field` | Деревенское поле / The Village Field | Своё растение зацвело в радиусе опыления цветущего деревенского растения того же вида | Рядом цветёт деревенское поле того же вида. Пыльца с него долетит и до этой грядки, и потомство смешается. Иногда это кстати, иногда нет. | A village field of the same kind is flowering nearby. Its pollen will reach this bed too, and the offspring will mix. Sometimes that helps, sometimes not. |
| `feral_planting` | Здесь был сад / There Was a Garden Here | Первое открытие одичавшего насаждения (садовый тростник, роща насьональ, одичавший сад, одичавшая малина) | Ряды слишком ровные для дикого леса, а в траве — камни старой межи. Здесь когда-то был сад. Растения одичали, но кое-что от прежних хозяев в них осталось. | The rows are too straight for wild woodland, and old boundary stones lie in the grass. There was a garden here once. The plants have gone wild, but something of their old keepers remains in them. |
| `breeder_met` | Селекционер / The Plant Breeder | Событие `breeder_trade`: первая сделка с селекционером | В этой деревне есть житель, который понимает, чем занята эта грядка. У него можно оценить семена, продать удачную находку и узнать, где искать лучшее. | There's a villager here who understands what this garden bed is about. Seeds can be assessed there, lucky finds sold, and advice had on where to look for the best. |
| `rare_find_sold` | Удачная находка / A Lucky Find | Первая продажа селекционеру лота с необычным растением или редким аллелем | Селекционер долго вертел пакетик в руках и заплатил не торгуясь. Похоже, такое попадается нечасто. | The breeder turned the packet over for a long while and paid without haggling. Finds like this must be rare. |
| `graded_by_hand` | Руками / By Hand | Первый крафт продукта с классом качества (рецепт `wildroots:quality_*`) | Урожай с отметкой качества книга рецептов сама не раскладывает. Его кладут на верстак руками — зато видно, что из чего выйдет. | The recipe book won't lay out graded produce on its own. It goes on the table by hand — and you see what will come of it. |
| `seed_pouch` | Мешочек / The Pouch | Первый мешочек для семян в инвентаре | Пакетики семян теперь не рассыпаются по карманам. В мешочке каждый лежит отдельно, и наружу первым выходит тот, что положен последним. | Seed packets no longer spill across every pocket. In the pouch each lies apart, and the last one in is the first one out. |

### 4.6 Страницы инструментов (`wildroots.journal.tool.<id>`)

Открываются, когда предмет впервые попадает в инвентарь.

| id | ru_ru | en_us |
|---|---|---|
| `field_journal` | Здесь записано всё найденное и выведенное. Потерянный журнал можно сделать заново — записи никуда не денутся. | Everything found and bred is written here. A lost journal can be made again; the notes won't go anywhere. |
| `hand_lens` | Лупа показывает то, что глазом не оценить: вкус, выносливость, как прошло опыление, не слабеет ли растение. Работает и на растении, и на пакетике семян. | The lens shows what the eye can't judge: taste, hardiness, how pollination went, whether a plant is ailing. Works on plants and on seed packets alike. |
| `pollination_brush` | Кисть набирает пыльцу с цветка и переносит на другой цветок того же вида. Пыльца живёт недолго. Кисть по тому же цветку — самоопыление. | The brush picks up pollen from one flower and carries it to another of the same kind. Pollen doesn't last long. Brushing the same flower selfs it. |
| `seed_bank` | Здесь живут лоты, маточники и клоны. Их можно разложить по порядку и дать линиям имена. | Seed lots, mother plants and clones live here. They can be sorted and their lines named. |
| `seed_pouch` | Мешочек для семян. Пакетики в нём не смешиваются: каждый лежит отдельно, и достать можно только тот, что положен последним. | A seed pouch. The packets inside never mix; each lies apart, and the last one put in is the first to come out. |
| `sorting_table` | Сита, корзинки, ящички с пакетиками. За таким столом работает селекционер: если поставить стол в деревне, кто-нибудь из жителей займётся этим делом. | Sieves, baskets, little drawers of seed packets. A plant breeder works at a table like this; set one up in a village and some villager may take up the trade. |
| `seed_analyser` | Анализатор отвечает на главный вопрос: это наследственность или везение? Показывает, что растение передаст потомству, сколько из этого реально проявляется и на что уходят силы. Находит носителей скрытых признаков. Семена не портит. | The analyser answers the big question: heredity or luck? It shows what a plant will pass on, how much of it actually shows, and where its strength goes. It finds carriers of hidden traits. The seed comes out unharmed. |
| `genotype_lab` | Лаборатория читает растение целиком и предсказывает, что выйдет из скрещивания двух лотов. | The lab reads a plant in full and predicts what a cross of two lots will give. |

### 4.7 Записки селекционера

Голос селекционера — не голос журнала. Это житель-практик: говорит короткими советами, немного по-стариковски, без терминов, обращается к читателю на «ты» и в прошедшем времени о себе не говорит (у жителей нет рода). Записки появляются во вкладке `wildroots.journal.tab.breeder`.

**Страница из хижины** (`wildroots.journal.breeder.hut_page`, лут бочки хижины, GDD §9.6). Одна страница, открывается при подборе предмета `breeder_note` с флагом `hut_page` или при первом открытии бочки хижины `[ПРЕДЛОЖЕНИЕ]`:

| Часть | ru_ru | en_us |
|---|---|---|
| `.title` | Записки с опытной делянки | Notes from the Trial Plot |
| `.1` | Не верь самому крупному. Посади его семена рядом с соседскими — что вырастет, то и правда. | Don't trust the biggest one. Sow its seed beside the others; what comes up is the truth. |
| `.2` | Капуста сама с собой не дружит. Бери на семена разных, из разных мест, и держи улей поближе. | Cabbage won't keep company with itself. Take seed from different plants, from different places, and keep a hive close. |
| `.3` | Пшеницу пчёлы не сватают. Хочешь пару — бери кисть и сватай сам. | Bees don't play matchmaker for wheat. Want a pairing? Take a brush and do it yourself. |
| `.4` | Поле у нас хорошее, да одно и то же из года в год. Лучшее растёт не здесь — его надо искать. | Our field is good, but it's the same year after year. The best doesn't grow here; it has to be found. |

**Записка селекционера** — предмет `wildroots:breeder_note`, покупается на уровне 3 (GDD §9.6). При использовании открывает в журнале одну запись: вид × признак → категория биома, где у этого вида элитные локусы по этому признаку. Текст собирается из шаблона; вариант выбирается по `hash(species, trait)`, чтобы одна и та же записка всегда звучала одинаково.

| Ключ | ru_ru | en_us |
|---|---|---|
| `wildroots.journal.breeder.note.title` | Записка: %1$s, %2$s | Note: %1$s, %2$s |
| `wildroots.journal.breeder.note.v1` | %1$s. За признаком «%2$s» стоит идти сюда: %3$s. Проверено не одним урожаем. | %1$s. For "%2$s", go here: %3$s. Proven over more than one harvest. |
| `wildroots.journal.breeder.note.v2` | %1$s, «%2$s». Лучшие растения попадались вот где: %3$s. Там и ищи. | %1$s, "%2$s". The best plants turned up here: %3$s. Look there. |
| `wildroots.journal.breeder.note.v3` | «%2$s» у вида «%1$s» сильнее всего здесь: %3$s. Привези оттуда пару растений — своей линии не испортишь, а пользу получишь. | "%2$s" in %1$s is strongest here: %3$s. Bring a couple of plants back from there; it won't spoil your line, and it will help. |
| `wildroots.tooltip.breeder_note.unread` | Прочитайте, чтобы записать в журнал | Read it to add it to your journal |
| `wildroots.tooltip.breeder_note.read` | Уже в журнале | Already in your journal |

Аргументы: `%1$s` — имя вида (`wildroots.species.<id>`), `%2$s` — признак строчными (`wildroots.journal.breeder.trait.<trait>`), `%3$s` — имя категории биома (`wildroots.biome_category.<id>`). В записке нет чисел и нет координат: селекционер говорит «где», а не «сколько» (столп 2).

**Признаки для записок** (`wildroots.journal.breeder.trait.<trait>`):

| trait | ru_ru | en_us |
|---|---|---|
| `fruit_size` | крупность | size |
| `yield` | урожайность | yield |
| `growth_speed` | скороспелость | quick growth |
| `flavor` | вкус | taste |
| `cold_tolerance` | холодостойкость | cold hardiness |
| `heat_tolerance` | жаростойкость | heat tolerance |
| `drought_tolerance` | засухоустойчивость | drought tolerance |
| `leaf_size` | крупный лист | large leaves |
| `terminal_bud` | плотный кочан | a firm head |
| `inflorescence` | соцветия | flower buds |
| `stem` | толстый стебель | a thick stem |
| `apical_dominance` | один стебель | a single stalk |
| `sugar` | сахаристость | sugar |

**Имена категорий биомов** (`wildroots.biome_category.<id>`; список категорий — `03-worldgen.md` §2.3, при изменении списка строки добавляет Narrative):

| id | ru_ru | en_us |
|---|---|---|
| `steppe` | степи | the steppes |
| `upland_meadow` | горные луга | upland meadows |
| `oak_woodland` | дубравы и березняки | oak and birch woods |
| `plains` | равнины | the plains |
| `meadow` | луга | meadows |
| `flower_fields` | цветочные поля | flower fields |
| `forest_edge` | лесные опушки | forest edges |
| `savanna` | саванна | the savanna |
| `savanna_upland` | плато саванны | savanna plateaus |
| `savanna_water` | водопои саванны | savanna waterholes |
| `dry_tropical_forest` | редколесье джунглей | sparse jungle |
| `beach` | песчаные берега | sandy shores |
| `rocky_shore` | каменистые берега | rocky shores |
| `cold_coast` | снежные берега | snowy shores |
| `salt_marsh` | солончаки у моря | salt marshes |
| `windswept_hills` | ветреные холмы | windswept hills |
| `mountain_conifer` | хвойная тайга | conifer taiga |
| `dry_valley` | бесплодные земли | the badlands |
| `oasis` | оазисы | oases |
| `riverbank` | речные берега | riverbanks |
| `swamp` | болота | swamps |
| `jungle` | джунгли | the jungle |
| `bamboo_wetland` | бамбуковые заросли | bamboo thickets |
| `mangrove` | мангры | mangroves |
| `snowy_boreal` | снежная тайга | snowy taiga |
| `birch_clearing` | березовые вырубки | birch clearings |
| `fruit_forest_slope` | ветреные лесистые склоны | windswept wooded slopes |
| `fruit_forest_flower` | цветущий лес | flower forests |
| `fruit_forest_blossom` | вишнёвые рощи | cherry groves |

**Строки товаров селекционера** (тултипы предметов, которые он продаёт; в ванили у сделки нет описания, поэтому «реплика» живёт на самом предмете, GDD §9.6):

| Ключ | Предмет | ru_ru | en_us |
|---|---|---|---|
| `wildroots.tooltip.trade.picked` | Перебранный лот (ур. 1) | Перебрано: горьких и чахлых матерей нет | Picked over: no bitter or ailing mothers |
| `wildroots.tooltip.trade.assessed` | Лот после «оценки семян» (ур. 2) | Оценено селекционером | Assessed by a plant breeder |
| `wildroots.tooltip.trade.choice` | Отборный лот (ур. 4) | Отборное: лучшее с опытной делянки | Choice stock: the best of the trial plot |
| `wildroots.tooltip.trade.kept` | Хранимые семена (ур. 5) | Хранили не просто так | Kept for a reason |
| `wildroots.tooltip.trade.pair` | Проверенная пара (ур. 5) | Проверенная пара: скрестить с «%s» | A proven pair: cross with "%s" |
| `wildroots.tooltip.trade.imported` | Привозной ландрас (ур. 3) | Привезено из других мест | Brought from elsewhere |

## 5. Исторические справки

**Правила фактов.** 2–4 предложения. Только то, что принято в литературе по одомашниванию. Даты — круглые, с «около». Спорное помечено словами «вероятно», «спорят». Где игра упрощает, справка говорит об этом одной фразой (GDD §0: «упрощаем, но не врём о направлении эффекта»). Источники для сверки фактов — в конце раздела.

### 5.1 Справки о видах (`wildroots.journal.species.<id>.history`)

| Вид | ru_ru | en_us |
|---|---|---|
| `wild_wheat` | Дикие однозернянка и полба до сих пор растут на холмах Плодородного полумесяца, от Леванта до гор Загрос. Их зерно собирали задолго до того, как начали сеять. Дикую полбу учёные описали только в 1906 году: её нашёл агроном Аарон Ааронсон в Галилее. | Wild einkorn and wild emmer still grow on the hills of the Fertile Crescent, from the Levant to the Zagros. People gathered their grain long before anyone sowed it. Science only described wild emmer in 1906, when the agronomist Aaron Aaronsohn found it in Galilee. |
| `teosinte` | То, что кукуруза произошла от теосинте, долго казалось невероятным — слишком уж они непохожи. Генетик Джордж Бидл скрещивал их и отстаивал родство; позже ДНК указала на теосинте из долины реки Бальсас в Мексике. Одомашнивание началось около 9 тысяч лет назад. | That maize came from teosinte long seemed absurd: the two look nothing alike. The geneticist George Beadle crossed them and argued for the link; later, DNA pointed to teosinte from Mexico's Balsas River valley. Domestication began around 9,000 years ago. |
| `wild_carrot` | Дикая морковь растёт по всей Европе и Западной Азии, а в Северной Америке её зовут «кружевом королевы Анны». Корень у неё белый и жёсткий. Похоже, сначала её ценили не за корень, а за ароматные листья и семена. | Wild carrot grows across Europe and western Asia; in North America it's called Queen Anne's lace. Its root is white and tough. It seems people first valued it not for the root but for its fragrant leaves and seeds. |
| `wild_cabbage` | Дикая капуста растёт на меловых и известняковых обрывах Атлантики и Средиземноморья — например, на скалах Дувра. Из этого одного вида выведены кейл, кочанная и цветная капуста, брокколи, кольраби и брюссельская. Все они до сих пор скрещиваются друг с другом. | Wild cabbage grows on the chalk and limestone cliffs of the Atlantic and Mediterranean coasts, Dover's among them. This single species gave us kale, head cabbage, cauliflower, broccoli, kohlrabi and Brussels sprouts. They all still interbreed. |
| `wild_tomato` **FD** | Ближайший дикий родственник томата — смородиновый томат с побережья Эквадора и Перу, с ягодами около сантиметра. Генетики считают, что одомашнивание шло в два шага: мелкоплодные томаты появились в Южной Америке, а крупноплодные — уже в Мезоамерике. | The tomato's closest wild relative is the currant tomato of coastal Ecuador and Peru, with berries about a centimetre across. Geneticists think domestication came in two steps: small-fruited tomatoes in South America, large-fruited ones later in Mesoamerica. |
| `wild_onion` **FD** | Истинный дикий предок лука неизвестен. Ближайшие дикие родственники — например, лук Вавилова и лук Ошанина — растут в горах Средней Азии и Ирана, где лук, вероятно, и одомашнили. Лук Вавилова назван в честь Николая Вавилова, который искал родину культурных растений. | The onion's true wild ancestor is unknown. Its closest wild relatives, such as Allium vavilovii and Allium oschaninii, grow in the mountains of Central Asia and Iran, where the onion was probably domesticated. Allium vavilovii honours Nikolai Vavilov, who searched for the homelands of crops. |
| `wild_rice` **FD** | Дикий рис растёт в болотах и на мелководье от Южного Китая до Индии и северной Австралии. Где именно одомашнили рис японской группы, спорят до сих пор: в долине Янцзы или Жемчужной реки. Ясно одно — это было в Южном Китае и шло медленно. | Wild rice grows in marshes and shallows from southern China to India and northern Australia. Where japonica rice was first domesticated is still argued over: the Yangtze valley or the Pearl River. It was southern China, and it was slow. |
| `wild_gourd` | Дикая тыква растёт в поймах рек Мексики и Техаса: плоды мелкие, твёрдые и горькие. Тыква — одна из первых культур Америки: её семена из пещеры Гила-Накиц в Оахаке датируются примерно 10 тысячами лет. Похоже, сначала ценили семена и прочную кору, а не мякоть. | Wild gourds grow on the floodplains of Mexico and Texas: small, hard, bitter fruit. Squash was one of the first crops of the Americas; its seeds from Guilá Naquitz cave in Oaxaca date to about 10,000 years ago. It seems the seeds and the tough rind mattered first, not the flesh. |
| `wild_watermelon` | Откуда родом арбуз, долго спорили. Сейчас ближайшим диким родственником считают кордофанскую дыню из Судана — у неё, в отличие от многих диких родичей, мякоть не горькая. В игре дикий арбуз горький: так устроены его дикие родственники вроде колоцинта. | Where the watermelon came from was long debated. Its closest wild relative is now thought to be the Kordofan melon of Sudan, whose flesh, unlike many wild kin, is not bitter. In the game the wild watermelon is bitter, like its wild relatives such as the colocynth. |
| `wild_potato` | Дикие картофели растут высоко в Андах, а предком культурного считают группу диких видов из окрестностей озера Титикака. Клубни у них мелкие и содержат ядовитые гликоалкалоиды. Андские народы до сих пор вымораживают и сушат картофель, делая чуньо. | Wild potatoes grow high in the Andes, and the cultivated potato is thought to descend from a group of wild species near Lake Titicaca. Their tubers are small and contain poisonous glycoalkaloids. Andean peoples still freeze-dry potatoes into chuño. |
| `sea_beet` | Морская свёкла растёт по берегам Атлантики и Средиземного моря. Её листья ели задолго до корня; от листовой свёклы пошёл и мангольд. Многие дикие растения цветут в первый же год: за это отвечает один доминантный ген. | Sea beet grows along the Atlantic and Mediterranean coasts. Its leaves were eaten long before its root; leaf beets gave us chard. Many wild plants flower in their first year, and a single dominant gene is responsible. |
| `wild_cane` | Сахарный тростник родом с Новой Гвинеи и из Южной Азии. Дикие виды тонкие, жёсткие и почти без сахара. Вне тропиков тростник почти не цветёт, и долго считалось, что семян он не даёт вовсе; рассаду из семян получили только в конце XIX века на Барбадосе и Яве. | Sugarcane comes from New Guinea and South Asia. The wild species are thin, tough and almost sugarless. Outside the tropics cane hardly flowers, and it was long thought to set no seed at all; seedlings were first raised only in the late 19th century, on Barbados and Java. |
| `wild_cacao` | Какао родом из верховий Амазонки, с границы Эквадора и Перу. На стоянке культуры Майо-Чинчипе нашли следы какао возрастом около 5 300 лет — на полторы тысячи лет старше находок в Мезоамерике. Цветы какао опыляют крошечные мошки, а не пчёлы. | Cacao comes from the upper Amazon, on the border of Ecuador and Peru. At a Mayo-Chinchipe site, traces of cacao about 5,300 years old have been found, some 1,500 years older than the finds in Mesoamerica. Cacao flowers are pollinated by tiny midges, not bees. |
| `wild_raspberry` | Малина растёт по всему северу Евразии — на вырубках, гарях и опушках, где лесу нужно время, чтобы вернуться. Её ягоды собирали с древности, а в сады она попала поздно, около XVI века. В игре ванильные «сладкие ягоды» стали малиной: колючий куст северного леса — это она. | Raspberries grow across northern Eurasia, in clearings, burns and forest edges where the woods take time to return. People gathered the berries since antiquity, but the plant came into gardens late, around the 16th century. In the game the vanilla sweet berry bush became the raspberry: the thorny bush of the northern forest is this one. |
| `wild_apple` | Дикая яблоня Сиверса растёт лесами на склонах Тянь-Шаня, в горах вокруг Алматы. Генетика показала, что именно она — главный предок культурной яблони. Деревья в этих лесах очень разные, и у некоторых яблоки уже крупные и сладкие. | Sievers' wild apple grows as forests on the slopes of the Tian Shan, in the mountains around Almaty. Genetics has shown it to be the main ancestor of the cultivated apple. The trees there are remarkably varied, and some already bear large, sweet fruit. |

### 5.2 Справки о сортах (`wildroots.cultivar.<id>.note`)

Показываются на странице открытого сорта. Ключ совпадает с полем `historical_note` в JSON сорта (GDD §6.3).

| Сорт | ru_ru | en_us |
|---|---|---|
| `einkorn` | Однозернянку одомашнили около 10 500 лет назад; генетика указывает на горы Карачадаг на юго-востоке Турции. Она мирится с бедной почвой и холодом, но урожай даёт скромный. Её отруби нашли в желудке Эци — человека, пролежавшего во льду Альп больше пяти тысяч лет. | Einkorn was domesticated around 10,500 years ago; genetics points to the Karacadağ mountains in south-eastern Turkey. It puts up with poor soil and cold but yields modestly. Its bran was found in the stomach of Ötzi, the man who lay in Alpine ice for over five thousand years. |
| `emmer` | Полба была главной пшеницей древнего мира: в Египте фараонов из неё пекли хлеб и варили пиво. Зерно у неё плёнчатое, и вылущить его можно только в ступе. В горах Италии и Эфиопии её сеют до сих пор. | Emmer was the main wheat of the ancient world: in pharaonic Egypt it made both bread and beer. Its grain is hulled and has to be pounded free in a mortar. It is still sown in the hills of Italy and Ethiopia. |
| `wheat` | Голозёрная пшеница обмолачивается чисто, без плёнок, во многом благодаря мутации гена Q. Мягкая пшеница родилась от случайного скрещивания культурной пшеницы с диким злаком эгилопсом, вероятно к юго-западу от Каспия, около 9 тысяч лет назад. В игре этот шаг упрощён: хватает одной линии. | Free-threshing wheat comes clean of its hulls, largely thanks to a mutation in the Q gene. Bread wheat was born of a chance cross between cultivated wheat and a wild grass, Aegilops, probably south-west of the Caspian, around 9,000 years ago. The game simplifies that step: one line is enough. |
| `primitive_maize` | Древнейшие известные початки нашли в пещере Гила-Накиц в Оахаке; им около 6 250 лет. Они совсем крошечные, всего несколько сантиметров, но уже не рассыпались, как колос теосинте. | The oldest known cobs come from Guilá Naquitz cave in Oaxaca, around 6,250 years old. They are tiny, a few centimetres long, but they no longer fell apart like a teosinte ear. |
| `maize` | К приходу европейцев кукуруза росла от юга Канады до Чили, и у каждого народа были свои сорта. Колумб привёз её в Испанию, и меньше чем за век она обошла Старый Свет. Сегодня это самое производимое зерно на Земле. | By the time Europeans arrived, maize grew from southern Canada to Chile, every people with its own varieties. Columbus brought it to Spain, and within a century it had spread across the Old World. Today it is the most-produced grain on Earth. |
| `eastern_carrot` | Окрашенная морковь — фиолетовая и жёлтая — появилась в Средней Азии и Афганистане. Около X века персидские авторы уже описывают её как овощ, а к XII веку она добралась до Испании. | Coloured carrots, purple and yellow, arose in Central Asia and Afghanistan. By around the 10th century Persian writers describe them as a vegetable, and by the 12th they had reached Spain. |
| `carrot` | Оранжевая морковь распространилась в Европе в XVI–XVII веках, и голландские огородники сделали её главной. Легенда, что её вывели в честь Оранской династии, ничем не подтверждена. Цвет ей даёт бета-каротин, из которого организм делает витамин A. | Orange carrots spread through Europe in the 16th–17th centuries, and Dutch growers made them the standard. The story that they were bred to honour the House of Orange has no evidence behind it. The colour is beta-carotene, which the body turns into vitamin A. |
| `leaf_cabbage` | Греки и римляне выращивали листовую капусту, похожую на нынешний кейл. Катон Старший считал её первой среди овощей и советовал есть перед пиром, чтобы не опьянеть. | The Greeks and Romans grew leafy cabbage much like today's kale. Cato the Elder ranked it first among vegetables and advised eating it before a feast to keep from getting drunk. |
| `head_cabbage` | Плотные кочаны появились в Европе около XII–XIII веков. Кочан — это одна огромная почка: листья плотно обнимают верхушку и не дают ей зацвести. Квашеная капуста потом кормила моряков в долгих плаваниях. | Firm heads appeared in Europe around the 12th–13th centuries. A head is one giant bud: the leaves wrap the growing tip and keep it from flowering. Later, sauerkraut fed sailors on long voyages. |
| `broccoli` | Брокколи вывели в Италии; название значит «ростки, побеги». В XVIII веке она попала в Англию, где её звали итальянской спаржей. Съедобная часть — плотные нераскрывшиеся бутоны. | Broccoli was bred in Italy; the name means "little sprouts". It reached England in the 18th century, where it was called Italian asparagus. The part we eat is a mass of unopened flower buds. |
| `cherry_tomato` **FD** | Черри-томаты похожи на томат на полпути к одомашниванию. Такие растения до сих пор дичают в Южной и Центральной Америке и почти везде, куда завезли томат. | Cherry tomatoes look like the tomato halfway to domestication. Plants like them still run wild across South and Central America, and nearly everywhere the tomato went. |
| `tomato` **FD** | Слово «томат» пришло из языка науатль, от «томатль». В Европу томат попал в XVI веке и долго рос как диковина: его считали ядовитым, как многих родственников из паслёновых. Итальянцы одними из первых стали его есть. | The word "tomato" comes from Nahuatl "tomatl". It reached Europe in the 16th century and was long grown as a curiosity, thought poisonous like many of its nightshade kin. Italians were among the first to eat it. |
| `landrace_onion` **FD** | Лук — одна из древнейших культур: его выращивали в Месопотамии и Египте. Геродот писал, что надпись на пирамиде сообщала, сколько серебра ушло на редьку, лук и чеснок для строителей. | The onion is one of the oldest crops, grown in Mesopotamia and Egypt. Herodotus wrote that an inscription on a pyramid recorded how much silver was spent on radishes, onions and garlic for the builders. |
| `onion` **FD** | Лук плохо переносит близкородственное скрещивание: самоопылённые линии быстро слабеют. Гибридные сорта стали возможны лишь в XX веке, когда нашли растения лука с мужской стерильностью. | Onions take inbreeding badly: selfed lines weaken fast. Hybrid varieties only became possible in the 20th century, once male-sterile onion plants were found. |
| `early_rice` **FD** | На стоянке Тяньлошань культуры Хэмуду, около 7 тысяч лет назад, археологи пересчитали основания колосков. За три века доля неосыпающихся выросла всего примерно с 27 до 39 %. Одомашнивание шло веками, а не годами. | At Tianluoshan, a Hemudu-culture site about 7,000 years old, archaeologists counted spikelet bases. Over three centuries the share of non-shattering ones rose only from about 27% to 39%. Domestication took centuries, not years. |
| `rice` **FD** | Белый рис — следствие маленькой поломки в гене Rc: у дикого риса оболочка зерна красная. Сегодня рис — основная еда для половины человечества. | White rice comes from a small break in the Rc gene; wild rice has a red grain skin. Today rice is the staple food for half of humanity. |
| `ancient_squash` | Ранние тыквы Мезоамерики выглядели почти как дикие, но их сажали уже ради съедобных семян. Позже тыква вместе с кукурузой и фасолью стала одной из «трёх сестёр» — их сажали на одном поле. | The early squashes of Mesoamerica looked almost wild, but were already planted for their edible seeds. Later, squash joined maize and beans as one of the "three sisters", grown together in one field. |
| `field_pumpkin` | Крупные полевые тыквы выращивали народы северо-востока Северной Америки; один из старых сортов — «Коннектикутская полевая». Из таких тыкв вырезают фонари, а в Ирландии и Шотландии раньше вырезали их из репы. | Large field pumpkins were grown by the peoples of north-eastern North America; one old variety is the Connecticut Field. Lanterns are carved from pumpkins like these; in Ireland and Scotland they were once carved from turnips. |
| `sugar_pumpkin` | Пироговые тыквы мельче полевых, но мякоть у них плотнее и слаще. К XIX веку тыквенный пирог стал в Америке праздничным блюдом. | Pie pumpkins are smaller than field pumpkins, with denser, sweeter flesh. By the 19th century pumpkin pie had become a holiday dish in America. |
| `nile_melon` | Арбузы изображены в египетских гробницах около 4 тысяч лет назад, а их семена находят в погребениях. Вероятно, сначала их ценили как запас воды и за семена, а не за сладость. | Watermelons appear in Egyptian tombs about 4,000 years ago, and their seeds are found in burials. They were probably valued first as a store of water and for their seeds, not for sweetness. |
| `picnic_watermelon` | Красная сладкая мякоть — дело одомашнивания: у диких родственников она бледная и часто горькая. Крупные полосатые арбузы для летних праздников вывели в США в XIX–XX веках. | Red, sweet flesh is a product of domestication: in wild relatives it is pale and often bitter. The large striped watermelons of summer picnics were bred in the United States in the 19th–20th centuries. |
| `icebox_watermelon` | В 1950-х в США вывели небольшие арбузы вроде 'Sugar Baby' — такой целиком помещается в холодильник. Мельче, зато слаще. | In the 1950s American breeders produced small watermelons such as 'Sugar Baby', small enough to fit whole in a refrigerator. Smaller, but sweeter. |
| `andean_potato` | В Андах картофель одомашнили около 8–10 тысяч лет назад, и там до сих пор растут тысячи местных сортов всех цветов и форм. Культурный картофель — тетраплоид, у него четыре набора хромосом; в игре это упрощено. | The potato was domesticated in the Andes around 8,000–10,000 years ago, and thousands of local varieties of every colour and shape still grow there. The cultivated potato is a tetraploid, with four sets of chromosomes; the game simplifies this. |
| `potato` | Европейский картофель ведёт род в основном от чилийских форм, привычных к долгому летнему дню. В Европу картофель попал в XVI веке, а в XIX стал для многих стран главной едой. | European potatoes descend mainly from Chilean forms, adapted to long summer days. The potato reached Europe in the 16th century and in the 19th became a staple food for many countries. |
| `roman_beet` | В XVI веке европейские травники описывают свёклу с толстым красным корнем — её называли римской. До того свёклу ели в основном листьями. | In the 16th century European herbalists describe a beet with a thick red root, called the Roman beet. Before that, beets were eaten mostly for their leaves. |
| `table_beet` | Красный цвет столовой свёкле дают беталаины — те же пигменты, что у амаранта и плодов кактусов. Из неё варят борщ и свекольник. | Table beet gets its red from betalains, the same pigments found in amaranth and cactus fruit. It goes into borscht and cold beet soup. |
| `sugar_beet` | В 1747 году химик Андреас Маргграф нашёл в свёкле тот же сахар, что в тростнике. Его ученик Франц Ахард отобрал белую силезскую свёклу и в 1801 году открыл первый свеклосахарный завод. Так у Европы появился свой сахар, независимый от тропиков. | In 1747 the chemist Andreas Marggraf found in beet the same sugar as in cane. His student Franz Achard selected the White Silesian beet and opened the first beet sugar factory in 1801. Europe gained a sugar of its own, independent of the tropics. |
| `garden_cane` | На Новой Гвинее сладкий тростник сажали в огородах и жевали как лакомство, а не ради сахара. Размножали его черенками и веками оставляли самые сочные стебли. | In New Guinea sweet cane was planted in gardens and chewed as a treat, not grown for sugar. It was propagated from cuttings, and for centuries the juiciest stalks were the ones kept. |
| `noble_cane` | Благородный тростник одомашнили на Новой Гвинее около 8 тысяч лет назад; кристаллический сахар научились делать в Индии. Современные сорта — сложные гибриды со множеством наборов хромосом; в игре это упрощено. | Noble cane was domesticated in New Guinea around 8,000 years ago; crystal sugar was first made in India. Modern varieties are complex hybrids with many sets of chromosomes; the game simplifies this. |
| `nacional_cacao` | «Насьональ» — старая эквадорская линия какао, известная цветочным ароматом. В торговле её бобы зовут «Арриба», то есть «сверху»: их привозили вниз по реке из верховий. | Nacional is an old Ecuadorian line of cacao, known for its floral aroma. In trade its beans are called "Arriba", meaning "from upriver", for they came down the river from the uplands. |
| `criollo` | Криолло — какао Мезоамерики: майя и ацтеки готовили из него горький пенный напиток. Бобы у него ароматные, но деревья нежные и урожай невелик. Сегодня чистого криолло — малая доля мирового урожая. | Criollo is the cacao of Mesoamerica, which the Maya and Aztecs made into a bitter, frothy drink. Its beans are fragrant, but the trees are delicate and yield little. Pure criollo is now a small share of the world crop. |
| `forastero` | «Форастеро» по-испански значит «чужак»: так называли амазонское какао, завезённое в другие края. Оно урожайное и выносливое, но вкус грубее. Большая часть шоколада в мире делается из него. | "Forastero" is Spanish for "outsider", the name given to Amazonian cacao taken to other lands. It is productive and hardy, but coarser in taste. Most of the world's chocolate is made from it. |
| `garden_raspberry` | В сады Европы малина попала поздно — около XVI века; её растили при монастырях и усадьбах, отбирая кусты с ягодами покрупнее. | The raspberry entered European gardens late, around the 16th century; it was grown at monasteries and manors, keeping the bushes with the biggest berries. |
| `raspberry` | Малину без шипов вывели в Шотландии во второй половине XX века; ген бесшипости рецессивен. Собирать такую ягоду можно голыми руками. | Thornless raspberries were bred in Scotland in the late 20th century; the thornless gene is recessive. The berries can be picked bare-handed. |
| `silk_road_apple` | Яблоки ушли с Тянь-Шаня на запад по торговым путям, которые потом назовут Шёлковым. По дороге они скрещивались с лесной яблоней Европы, и культурная яблоня получила гены обеих. | Apples travelled west from the Tian Shan along trade routes later called the Silk Road. On the way they crossed with Europe's crab apple, and the cultivated apple carries genes from both. |
| `aport` | Апорт — огромное ароматное яблоко, которое прославило сады Верного, нынешней Алматы, в XIX веке. Само название Алматы связывают со словом «яблоко». | Aport is a huge, fragrant apple that made the orchards of Verny, today's Almaty, famous in the 19th century. The name Almaty itself is linked to the word for apple. |
| `antonovka` | Антоновка — старый народный сорт Средней России: зимостойкое дерево и кисловатые яблоки, которые долго лежат. Откуда она взялась, точно не известно: скорее всего, как многие старые яблони, — из случайного сеянца. | Antonovka is an old folk variety of central Russia: a winter-hardy tree with tart apples that keep for a long time. Its origin isn't known for certain; most likely, like many old apples, it began as a chance seedling. |

**Числа в справках** — это исторические даты, а не игровые значения. Правило «без чисел» (§1.2) касается механики.

**Источники для сверки (для редактора, в игру не идут):** Zohary, Hopf, Weiss, *Domestication of Plants in the Old World* (4-е изд.); Heun et al. 1997 (однозернянка, Карачадаг); Matsuoka et al. 2002 (кукуруза, Бальсас); Piperno & Flannery 2001 (Гила-Накиц); Smith 1997 (*Cucurbita pepo*, Гила-Накиц); Iorizzo et al. 2013 и Banga 1957/1963 (морковь); Razifard et al. 2020 (томат); Renner et al. 2021 (кордофанская дыня и арбуз); Spooner et al. 2005 (единое происхождение картофеля); Zarrillo et al. 2018 (какао, Майо-Чинчипе); Cornille et al. 2012 и Velasco et al. 2010 (яблоня, *M. sieversii*); Jennings 1988 (малина, бесшипость); Fuller et al. 2009 (Тяньлошань); Sweeney et al. 2006 (`Rc`); Jones & Clarke 1943 (мужская стерильность лука); Marggraf 1747, Achard 1801 (сахарная свёкла); Herodotus II.125; Cato, *De Agri Cultura* 156. Перед релизом факты сверяет второй человек `[ПРОВЕРИТЬ]`.

## 6. Метки «Из дикой природы» и имена линий

### 6.1 Метка «Из дикой природы»

**Зачем.** Мягкая награда за полный путь (GDD §9.5): «эту морковь я вывел из сорняка», а не «купил у фермера и довёл».

**Правило наследования — по материнской линии** (принято, GDD §5.3). Флаг `from_wild` хранится в лоте, маточнике и клоне и переходит потомку **от матери**; отец на него не влияет. Клон и привитое дерево копируют флаг вместе с генотипом.
- **Получают флаг:** лоты из травы и папоротника, с диких патчей, из сундуков структур, от странствующего торговца (это дикие предки).
- **Не получают:** лоты, у которых мать — растение с меткой `village` (живые деревенские фермы, опытная делянка селекционера), товары фермера и селекционера, лоты и клоны одичавших насаждений (там генетика ландраса). Их метка — «Деревенский материал» или «Из одичавшего сада».
- **Исключение для своего ландраса:** если игрок вывел ландрас сам от дикого растения и продал его селекционеру (GDD §9.6, ур. 4), это и есть «полный путь» — проверка в торге смотрит на метку `from_wild` лота.
- **Почему по матери:** GDD сам поощряет подмешивать деревенский ландрас как отца (§12.3, §7.3), и метка не должна за это наказывать. Реальная аналогия: хлоропласты у большинства растений наследуются по материнской линии. Если игрок сделал деревенское растение **матерью** — линия честно «пришла из деревни».
- **Где видна:** строка тултипа лота и маточника (§7), страница сорта и линии в журнале, условие достижения `from_the_wild` (§8).

| Ключ | en_us | ru_ru |
|---|---|---|
| `wildroots.journal.mark.from_the_wild` | From the Wild | Из дикой природы |
| `wildroots.tooltip.mark.from_the_wild` | From the wild | Из дикой природы |
| `wildroots.tooltip.mark.from_village` | Village stock | Деревенский материал |
| `wildroots.journal.mark.from_village` | From village stock | Из деревенского материала |
| `wildroots.tooltip.mark.from_feral` | From a feral planting | Из одичавшего сада |
| `wildroots.journal.mark.from_feral` | From a feral planting | Из одичавшего насаждения |

### 6.2 Имена линий

- **Как назвать:** переименовать лот (или маточник) на наковальне (GDD §5.3) или в семенном банке. Имя из наковальни — это `display name` предмета; сервер переносит его в поле `line_name` лота и снимает кастомное имя, чтобы предмет по-прежнему назывался «Семена: …» `[ПРЕДЛОЖЕНИЕ]`.
- **Наследование:** имя линии и номер поколения идут **по матери**, как метка. Номер поколения потомка = поколение матери + 1.
- **Скрещивание двух именованных линий:** потомок несёт имя матери; журнал добавляет в родословную строку «× %s» с именем отца.
- **Переименование** продолжает счёт поколений; журнал пишет «Переименована: %1$s → %2$s».
- **Без имени:** в тултипе строка линии не показывается вовсе. В журнале линия, давшая сорт без имени, получает автоимя `wildroots.journal.line.unnamed` = «Линия без имени (%s)», где `%s` — имя сорта.
- **Длина имени:** как у ванильной наковальни (50 символов); в тултипе обрезается до 24 с «…» `[ПРЕДЛОЖЕНИЕ]`.

| Ключ | en_us | ru_ru |
|---|---|---|
| `wildroots.tooltip.line` | Line "%1$s", gen. %2$d | Линия «%1$s», поколение: %2$d |
| `wildroots.tooltip.generation` (без имени) | Generation: %d | Поколение: %d |
| `wildroots.journal.line.header` | Line "%s" | Линия «%s» |
| `wildroots.journal.line.unnamed` | Unnamed line (%s) | Линия без имени (%s) |
| `wildroots.journal.line.started` | Started on day %1$d from %2$s | Начата на %1$d-й день: %2$s |
| `wildroots.journal.line.cross` | × %s | × %s |
| `wildroots.journal.line.renamed` | Renamed: %1$s → %2$s | Переименована: %1$s → %2$s |
| `wildroots.journal.line.reached` | Gen. %1$d: %2$s | Поколение %1$d: %2$s |

Пример страницы линии (ru): «Линия «Рыжик» · Из дикой природы / Начата на 3-й день: Дикая морковь / × Линия «Северная» / Поколение 4: Восточная морковь / Поколение 13: Морковь».

## 7. Предметы и тултипы

### 7.1 Принцип: тултип знает столько, сколько знает игрок

Тултип — это та же лестница информации, что в GDD §10. У семенного материала (лоты, маточники, клоны) уровень хранится в самом предмете флагами `inspected` и `analyzed` (принято, GDD §5.3). У урожая и продуктов генома нет, поэтому их тултип всегда одинаков: ступень, класс словом, горечь (§7.6).

| Уровень | Когда | Что в тултипе | Числа |
|---|---|---|---|
| 0 — «на глаз» | Всегда | Имя, ступень, метка, линия, то, что видно при сборе: размер на глаз (3 слова), цвет, осыпание, число зёрен в колоске, форма куста, чахлость матери; у урожая — класс словом | Нет |
| 1 — «под лупой» | После осмотра лупой (флаг `inspected`) | Шкалы признаков матери словами (5 ступеней, §9.2), горечь, покой, как прошло опыление, «похоже на сорт» | Нет |
| 2 — «анализ» | После анализатора или «оценки семян» у селекционера (флаг `analyzed`) | `G` и `G_eff` с шагом 0.05, «уходит на…», носители, витальность, пороги известных сортов | Да |
| 3 — «лаборатория» | Только в интерфейсе лаборатории | — | Да |

**Длина.** Без Shift видны строки уровня 0 и одна строка-подсказка; строки уровней 1–2 — с зажатым Shift (принято).

### 7.2 Лот семян `wildroots:seed_lot`

**Имя** (динамическое):

| Ключ | en_us | ru_ru | Когда |
|---|---|---|---|
| `item.wildroots.seed_lot` | Seeds | Семена | Запасное имя без NBT |
| `item.wildroots.seed_lot.named` | %s Seeds | Семена: %s | Вид открыт или лот сортовой |
| `item.wildroots.seed_lot.unknown` | Wild Seeds | Дикие семена | Вид ещё не открыт игроком (GDD §11.3) |
| `item.wildroots.seed_lot.pips` | %s Pips | Косточки: %s | Лот яблони |
| `item.wildroots.seed_lot.beans` | %s Beans (for sowing) | Бобы на посадку: %s | Лот какао (бобы = лот, GDD §5.1) |

**Строки тултипа** (по порядку):

| # | Уровень | Ключ | en_us | ru_ru | Условие |
|---|---|---|---|---|---|
| 1 | 0 | `wildroots.tooltip.tier.<n>` + метка | Wild ancestor · From the wild | Дикий предок · Из дикой природы | Всегда; метка — по §6.1 |
| 2 | 0 | `wildroots.tooltip.line` | Line "%1$s", gen. %2$d | Линия «%1$s», поколение: %2$d | Есть имя линии; иначе `wildroots.tooltip.generation` начиная со 2-го поколения |
| 3 | 0 | `wildroots.tooltip.unknown_species` | Plant it and see what comes up. | Посадите — и станет ясно, что вырастет. | Вид не открыт |
| 4 | 0 | `wildroots.tooltip.mother_eye` | Mother, by eye: %s | Мать на глаз: %s | Вид открыт. `%s` — слова §9.3 через запятую |
| 5 | 0 | `wildroots.tooltip.mother_ailing` | The mother looked sickly | Мать выглядела чахлой | Витальность матери ниже порога чахлости |
| 6 | 0 | `wildroots.tooltip.unattended` | Gathered unattended | Собраны без присмотра | Лот без отцов из-за поршня, воды или фейкового игрока |
| 7 | 0 | `wildroots.tooltip.rare_allele` | Kept for a reason | Хранили не просто так | Лот с редким аллелем (сундук, селекционер ур. 5) |
| 8 | 0 | `wildroots.tooltip.far_lands` | From distant lands | Из далёких земель | Лот странствующего торговца |
| 9 | 0 | `wildroots.tooltip.trade.*` | §4.7 | §4.7 | Товар селекционера |
| 10 | 0 | `wildroots.tooltip.apple.seedlings` | Apple seedlings don't take after their mother | Сеянцы яблони не повторяют мать | Лот яблони (GDD §5.7 п. 2) |
| 11 | 0 | `wildroots.tooltip.apple.pips` | The pips are poisonous | Косточки ядовиты | Лот яблони (GDD §5.7 п. 6) |
| 12 | 0 | `wildroots.tooltip.hint.inspect` | Inspect with a hand lens to learn more | Осмотрите лупой, чтобы узнать больше | Не осмотрен и у игрока открыта страница лупы |
| 13 | 0 | `wildroots.tooltip.hint.shift` | Hold Shift for details | Shift — подробнее | Осмотрен или проанализирован |
| 14 | 1 | `wildroots.tooltip.trait` | %1$s: %2$s | %1$s: %2$s | По строке на признак вида: имя признака (§9.2) и слово шкалы |
| 15 | 1 | `wildroots.tooltip.pollination.*` | §9.4 | §9.4 | Как опылялась мать |
| 16 | 1 | `wildroots.tooltip.father_named` | Father: hand-pollinated from "%s" | Отец: ручное опыление от «%s» | Кисть, у отца есть имя линии |
| 17 | 1 | `wildroots.tooltip.looks_like` | Looks like %s. Test the offspring. | Похоже на «%s». Проверьте потомство. | Фенотип проходит пороги сорта, генотип — нет |
| 18 | 2 | `wildroots.tooltip.analysis.*` | §9.5 | §9.5 | После анализа |

**Пример (ru, Shift зажат):**
```
Семена: Дикая морковь
Дикий предок · Из дикой природы
Линия «Рыжик», поколение: 4
Мать на глаз: корень крупный, жёлтый
Размер корня: крупный
Урожай: умеренный
Вкус: сладковатый
Холодостойкость: средняя
Горечь: нет
Опыление: пчёлы
```

### 7.3 Мешочек для семян `wildroots:seed_pouch`

Работает как ванильный `bundle` (GDD §5.3): записи внутри не сливаются, наружу выходит последняя положенная.

| Ключ | en_us | ru_ru |
|---|---|---|
| `item.wildroots.seed_pouch` | Seed Pouch | Мешочек для семян |
| `wildroots.tooltip.pouch.fill` | Seeds: %1$d / %2$d | Семян: %1$d из %2$d |
| `wildroots.tooltip.pouch.places` | Places: %1$d / %2$d | Мест: %1$d из %2$d |
| `wildroots.tooltip.pouch.entry` | %1$s ×%2$d | %1$s ×%2$d |
| `wildroots.tooltip.pouch.last` | Last put in: %s | Последним положено: %s |
| `wildroots.tooltip.pouch.more` | …and %d more | …и ещё записей: %d |
| `wildroots.tooltip.pouch.empty` | Empty. Holds seed lots only. | Пусто. Только для семян. |
| `wildroots.message.pouch.full` | The pouch is full | Мешочек полон |
| `wildroots.message.pouch.not_seeds` | Only seeds go in the pouch | В мешочек — только семена |

Список содержимого: последняя положенная запись сверху, до 6 строк, остальное — строкой `more`. Строка `entry` — имя лота (§7.2) и число семян в записи. Мешочек вмещает 16 мест по одному семени — 16 семян всего (`00-vision.md` п. 21): `pouch.full` выводится, когда заняты все 16 мест.

### 7.4 Маточники и клоны

Маточник и клон — само растение с геномом (GDD §5.2, §5.5). Тултип как у лота, но строка 4 говорит о самом растении: `wildroots.tooltip.self_eye` = «На глаз: %s» / "By eye: %s". У клона добавляется строка `wildroots.tooltip.clone` = «Клон: копия одного растения» / "Clone: a copy of one plant".

| id | en_us | ru_ru | Имя с NBT (`item.wildroots.<id>.named`) en / ru | Подсказка (`item.wildroots.<id>.hint`) en / ru |
|---|---|---|---|---|
| `mother_root` | Mother Root | Маточный корень | %s (mother root) / %s (маточник) | Plant on farmland to flower and set seed. Or eat it. / Посаженный на пашню, зацветёт и даст семена. Или его можно съесть. |
| `mother_stump` | Mother Stump | Маточная кочерыга | %s (mother stump) / %s (маточник) | то же |
| `mother_bulb` **FD** | Mother Bulb | Маточная луковица | %s (mother bulb) / %s (маточник) | то же |
| `seed_tuber` | Seed Tuber | Семенной клубень | %s (seed tuber) / %s (семенной клубень) | Plant it to grow an exact copy of its bush. / Посаженный, даст точную копию своего куста. |
| `cane_cutting` | Cane Cutting | Черенок тростника | %s (cutting) / %s (черенок) | Plant by water to grow an exact copy. / Посаженный у воды, даст точную копию. |
| `cacao_cutting` | Cacao Cutting | Черенок какао | %s (cutting) / %s (черенок) | Plant at the foot of a jungle tree to grow an exact copy. / Посаженный у ствола тропического дерева, даст точную копию. |
| `raspberry_cane` | Raspberry Sucker | Отпрыск малины | %s (sucker) / %s (отпрыск) | A root sucker: an exact copy of its bush. / Корневой отпрыск: точная копия своего куста. |
| `apple_scion` | Apple Scion | Привой яблони | %s (scion) / %s (привой) | Cannot be planted. Graft it onto a young seedling. / Сажать нельзя — только прививать на молодой сеянец. |

Маточник свёклы — `mother_root`, как у моркови (GDD §5.2).

### 7.5 Дикая еда

Тултип: строка ступени «Дикий предок», строка класса (§7.6) и одна строка описания (серая, курсив) — ключ `item.wildroots.<id>.desc`. Еда без генома, поэтому описание говорит «часто», а не «этот». Id приняты (GDD §19.4).

| id | en_us | ru_ru | desc en_us | desc ru_ru |
|---|---|---|---|---|
| `wild_grain` | Wild Grain | Дикое зерно | Small and tightly hulled. | Мелкое, в жёстких плёнках. |
| `teosinte_nut` | Teosinte Nut | Орех теосинте | A kernel in a stony shell. Teeth won't crack it. | Зерно в каменной скорлупе. Зубы не берут. |
| `wild_carrot_root` | Wild Carrot Root | Корень дикой моркови | Thin, white, smells of carrot. Often bitter. | Тонкий, белый, пахнет морковью. Часто горчит. |
| `wild_cabbage_leaves` | Wild Cabbage Leaves | Листья дикой капусты | Tough and blue-grey. Nearly always bitter. | Жёсткие, сизые. Почти всегда горькие. |
| `wild_gourd` | Wild Gourd | Дикая тыква | Hard as stone, bitter inside. | Твёрдая как камень, внутри горькая. |
| `wild_melon_slice` | Wild Melon Slice | Ломтик дикого арбуза | Pale and watery. More often poisonous than not. | Бледный, водянистый. Чаще ядовит, чем нет. |
| `wild_potato_tuber` | Wild Potato Tuber | Клубень дикого картофеля | Small and bitter. Nearly always poisonous. | Мелкий и горький. Почти всегда ядовит. |
| `sea_beet_leaves` | Sea Beet Leaves | Листья морской свёклы | Glossy and fleshy. Good to eat. | Блестящие, мясистые. Вполне съедобные. |
| `wild_cane_stalk` | Wild Cane Stalk | Стебель дикого тростника | All fibre, barely sweet. Makes rough paper. | Одни волокна, сладости почти нет. Годится на грубую бумагу. |
| `wild_cacao_beans` | Wild Cacao Beans | Бобы дикого какао | Bitter and astringent. The pulp around them is sweet. | Горькие и терпкие. Зато мякоть вокруг них сладкая. |
| `wild_raspberry` | Wild Raspberry | Дикая малина | Small and sour. Crumbles in the fingers. | Мелкая и кислая, рассыпается в пальцах. |
| `wild_apple` | Wild Apple | Дикое яблоко | Small and tart. But no two trees are alike. | Мелкое и терпкое. Но деревья все разные. |
| `wild_tomato_berry` **FD** | Wild Tomato Berry | Ягода дикого томата | Pea-sized. More often poisonous than not. | С горошину. Чаще ядовита, чем нет. |
| `wild_onion_bulb` **FD** | Wild Onion Bulb | Луковичка дикого лука | Tiny, and sharp enough to make eyes water. | Крошечная и жгучая до слёз. |
| `wild_rice_grain` **FD** | Wild Rice | Дикий рис | Reddish grain with a long awn. | Красноватое зерно с длинной остью. |

Слова «часто», «почти всегда», «чаще… чем нет» соответствуют долям горьких растений в диких патчах по GDD §3.6 (горечь доминантна: морковь ≈ 84 %, капуста ≈ 94 %, тыква ≈ 96 %, арбуз ≈ 91 %, картофель ≈ 94 %, какао ≈ 96 %, томат ≈ 91 %, лук ≈ 88 %). При изменении частот — пересмотреть слова.

### 7.6 Урожай ландрасов и сортов; классы качества

**Финального ванильного продукта нет** (владелец №7): весь урожай — предметы мода, совместимые с рецептами через теги (GDD §6.5). Имя предмета — имя продукта, имя сорта — в журнале, лоте, тосте и лупе.

**Строки тултипа урожая** (для всех ступеней, включая дикую еду):

| # | Ключ | en_us | ru_ru | Условие |
|---|---|---|---|---|
| 1 | `wildroots.tooltip.name_grade` (строка имени) | %1$s · %2$s | %1$s · %2$s | Всегда, если у предмета есть класс: «Морковь · добротная», «Хлеб · грубый» |
| 2 | `wildroots.tooltip.tier.<n>` | Landrace | Ландрас | Урожай мода |
| 3 | `wildroots.tooltip.produce.bitter` | Bitter | Горчит | Флаг `wildroots:bitter` |
| 4 | `wildroots.tooltip.produce.toxic` | May poison you | Может отравить | Флаг горечи у вида с `toxic: true` |
| 5 | `item.wildroots.<id>.desc` | см. ниже | см. ниже | Дикие и ландрасы |

Класс дописывается к имени **только в тултипе** (имя стака, наковальня и чужие моды видят обычное имя) `[ПРЕДЛОЖЕНИЕ]`. Слово класса согласуется с родом и числом предмета (§9.8).

**Классы** (`wildroots.tooltip.grade.<n>.<род>`; границы — шкала §9.2, GDD §6.4):

| Класс | en_us | ru_ru м. р. | ж. р. | ср. р. | мн. ч. | Смысл |
|---|---|---|---|---|---|---|
| 1 | coarse | грубый | грубая | грубое | грубые | Сытость ×0.4 |
| 2 | plain | простой | простая | простое | простые | ×0.7, уровень деревни |
| 3 | fine | добротный | добротная | добротное | добротные | ×1.0, как современная культура |
| 4 | choice | отборный | отборная | отборное | отборные | ×1.2, лучше современной |

Почему эти слова: они звучат как оценка товара на рынке, а не как цифра; «добротный» честно значит «как надо», а «отборный» — «лучше обычного». Горький урожай не бывает выше «простого» (GDD §6.4), и строка «Горчит» объясняет почему.

**Предметы урожая ландрасов и сортов:**

| id | Ступень | en_us | ru_ru | Род | desc en_us | desc ru_ru |
|---|---|---|---|---|---|---|
| `einkorn_grain` | 1 | Einkorn Grain | Зерно однозернянки | ср. | Hardy grain of cold fields. | Неприхотливое зерно холодных полей. |
| `emmer_grain` | 1 | Emmer Grain | Зерно полбы | ср. | Hulled: first the mortar, then the pot. | Плёнчатое: сначала в ступу, потом в котёл. |
| `primitive_maize` | 1 | Primitive Maize Cob | Початок примитивной кукурузы | м. | Smaller than a palm, but the kernels are bare. | Меньше ладони, зато зерно открытое. |
| `eastern_carrot` | 1 | Eastern Carrot | Восточная морковь | ж. | Yellow or purple, and a little sweet. | Жёлтая или фиолетовая, сладковатая. |
| `leaf_cabbage` | 1 | Leaf Cabbage | Листовая капуста | ж. | Big soft leaves, no bitterness. | Крупные мягкие листья, без горечи. |
| `ancient_squash` | 1 | Ancient Squash | Древняя тыква | ж. | Small and thick-skinned, but no longer bitter. | Небольшая, в толстой коре, но уже не горькая. |
| `nile_melon_slice` | 1 | Nile Melon Slice | Ломтик нильского арбуза | м. | Pale flesh, plenty of water. | Бледная мякоть, много воды. |
| `andean_potato` | 1 | Andean Potato | Андский картофель | м. | Small knobbly tubers of every colour. | Мелкие узловатые клубни всех цветов. |
| `roman_beet` | 1 | Roman Beet | Римская свёкла | ж. | A proper root at last. | Наконец-то настоящий корнеплод. |
| `garden_cane_stalk` | 1 | Garden Cane Stalk | Стебель садового тростника | м. | Good for chewing. Sometimes yields sugar. | Хорош, чтобы жевать. Иногда даёт сахар. |
| `nacional_cacao_beans` | 1 | Nacional Cacao Beans | Бобы какао насьональ | мн. | Bitter still, with a hint of flowers. | Ещё горчат, но с цветочной ноткой. |
| `garden_raspberry` | 1 | Monastery Raspberry | Монастырская малина | ж. | Bigger berries, but the bush still scratches. | Ягоды крупнее, но куст ещё колется. |
| `silk_road_apple` | 1 | Silk Road Apple | Яблоко Шёлкового пути | ср. | An apple that has travelled far. | Яблоко, проделавшее долгий путь. |
| `cherry_tomato` **FD** | 1 | Cherry Tomato | Черри-томат | м. | Small, sweet, and safe. | Маленький, сладкий и уже не опасный. |
| `landrace_onion` **FD** | 1 | Landrace Onion | Местный лук | м. | Still sharp, but no longer to tears. | Ещё острый, но уже не до слёз. |
| `early_rice` **FD** | 1 | Early Rice | Ранний рис | м. | Holds its grain. The skin is still red. | Держит зерно. Оболочка ещё красная. |
| `wheat` | 2 | Wheat | Пшеница | ж. | — | — |
| `corn` | 2 | Corn | Кукуруза | ж. | — | — |
| `carrot` | 2 | Carrot | Морковь | ж. | — | — |
| `cabbage` | 2 | Cabbage | Капуста | ж. | — | — |
| `broccoli` | 2 | Broccoli | Брокколи | ж. | — | — |
| `field_pumpkin` | 2 | Field Pumpkin | Полевая тыква | ж. | — | — |
| `sugar_pumpkin` | 2 | Sugar Pumpkin | Сахарная тыква | ж. | — | — |
| `picnic_watermelon_slice` | 2 | Picnic Watermelon Slice | Ломтик крупного арбуза | м. | — | — |
| `icebox_watermelon_slice` | 2 | Icebox Watermelon Slice | Ломтик сахарного арбуза | м. | — | — |
| `potato` | 2 | Potato | Картофель | м. | — | — |
| `beetroot` | 2 | Beetroot | Свёкла | ж. | — | — |
| `sugar_beet` | 2 | Sugar Beet | Сахарная свёкла | ж. | — | — |
| `noble_cane` | 2 | Noble Cane | Благородный тростник | м. | — | — |
| `criollo_beans` | 2 | Criollo Cocoa Beans | Бобы криолло | мн. | — | — |
| `forastero_beans` | 2 | Forastero Cocoa Beans | Бобы форастеро | мн. | — | — |
| `raspberry` | 2 | Raspberry | Малина | ж. | — | — |
| `aport_apple` | 2 | Aport Apple | Апорт | м. | — | — |
| `antonovka_apple` | 2 | Antonovka Apple | Антоновка | ж. | — | — |
| `tomato` **FD** | 2 | Tomato | Томат | м. | — | — |
| `onion` **FD** | 2 | Onion | Лук | м. | — | — |
| `rice` **FD** | 2 | Rice | Рис | м. | — | — |

У сортового урожая нет описания: он должен выглядеть как обычная еда, а история живёт в журнале. Зерно, бобы, стебли тростника и сахарная свёкла тоже несут класс (GDD §6.4: ось «выполненность зерна» или сахаристость).

**Пример тултипа (ru):**
```
Морковь · добротная
Сорт
```
```
Корень дикой моркови · грубый
Дикий предок
Горчит
Тонкий, белый, пахнет морковью. Часто горчит.
```

### 7.7 Переработанные продукты с качеством

Продукты с ванильными механиками сохраняют ванильный id и несут класс 1–4 без горечи (GDD §6.6). Их тултип — та же строка `name_grade`; предмет без поля класса (из `soft`, чужих модов, сундуков) считается «добротным», но слово класса **не показывается**, чтобы не вводить в заблуждение `[ПРЕДЛОЖЕНИЕ]`.

| Продукт | Род (§9.8) | Пример строки имени | Особые строки |
|---|---|---|---|
| Хлеб | м. | Хлеб · грубый | Бонус мёда: `wildroots.tooltip.booster.honey` = «С мёдом» / "With honey" |
| Сахар | м. | Сахар · простой | — |
| Тыквенный пирог | м. | Тыквенный пирог · отборный | `wildroots.tooltip.booster.sugar` = «На хорошем сахаре» / "Made with good sugar" |
| Печенье | ср. | Печенье · добротное | то же |
| Торт | м. | Торт · простой | то же |
| Печёный картофель | м. | Печёный картофель · грубый | — |
| Свекольный суп | м. | Свекольный суп · добротный | — |
| Кроличье рагу | ср. | Кроличье рагу · простое | `wildroots.tooltip.booster.onion` = «С луком» / "With onion"; `.mushroom` = «С грибами» / "With mushrooms" |
| Золотая морковь | ж. | Золотая морковь · отборная | — |
| Сверкающий ломтик арбуза | м. | Сверкающий ломтик арбуза · добротный | — |
| Золотое яблоко | ср. | Золотое яблоко · простое | — |
| Бумага | — | без класса в имени | Класс 1: строка `wildroots.tooltip.paper.rough` = «Грубая бумага: волокнистая, но писать можно» / "Rough paper: stringy, but it takes ink". Классы 2–4 не подписываются: качество бумаги выражено выходом рецепта |
| Коричневый краситель | — | без класса | Класс не важен (GDD §6.6) |
| Блюда FD **FD** | по предмету | «Говяжье рагу · добротное» | Если архитектор выберет запасной вариант без хука — классов у FD-блюд нет, строк тоже |

Русские имена ванильных продуктов — из ванильного `ru_ru` `[ПРОВЕРИТЬ]` точные формы («Светильник Джека», «Сверкающий ломтик арбуза»); род для них задаётся таблицей §9.8.

**Резная тыква и фонарь** (GDD §5.6): класс у них — размер, а не качество.

| Ключ | en_us | ru_ru |
|---|---|---|
| `block.wildroots.carved_pumpkin` | Carved Pumpkin | Резная тыква |
| `block.wildroots.jack_o_lantern` | Jack o'Lantern | Светильник Джека |
| `wildroots.tooltip.carved.large` | Size: large | Размер: крупный |
| `wildroots.tooltip.carved.huge` | Size: huge | Размер: огромный |
| `wildroots.tooltip.carved.light` | Fits any golem | Годится для любого голема |
| `wildroots.message.carve.too_small` | Too small to carve | Плод слишком мал, чтобы вырезать |

### 7.8 Инструменты и блоки

| id | Тип | en_us | ru_ru | Строки тултипа (`item.wildroots.<id>.desc` или `block.wildroots.<id>.desc`) en / ru |
|---|---|---|---|---|
| `field_journal` | предмет | Field Journal | Полевой журнал | A naturalist's notes. Use to open. / Записки натуралиста. ПКМ — открыть. |
| `hand_lens` | предмет | Hand Lens | Лупа | Use on a plant, or with seeds in the other hand. / ПКМ по растению или по семенам в другой руке. |
| `pollination_brush` | предмет | Pollination Brush | Кисть опыления | Pick up pollen from one flower, carry it to another. / Наберите пыльцу с одного цветка и перенесите на другой. |
| `seed_pouch` | предмет | Seed Pouch | Мешочек для семян | §7.3 |
| `breeder_note` | предмет | Breeder's Note | Записка селекционера | §4.7 |
| `seed_bank` | блок | Seed Bank | Семенной банк | Keeps seed lots, mother plants and clones. / Хранит лоты семян, маточники и клоны. |
| `seed_analyser` | блок | Seed Analyser | Анализатор семян | Heredity or luck? Uses bone meal. / Наследственность или везение? Расходует костную муку. |
| `genotype_lab` | блок | Genotype Lab | Лаборатория генотипа | Reads a plant in full and predicts crosses. Uses gold and bone meal. / Читает растение целиком и предсказывает скрещивания. Расходует золото и костную муку. |
| `sorting_table` | блок | Sorting Table | Сортировочный стол | Sieves, baskets and seed drawers. A plant breeder's workplace. / Сита, корзинки и ящички с семенами. Рабочее место селекционера. |

**Состояние кисти** (строка тултипа, ключи `wildroots.tooltip.brush.*`):

| Ключ | en_us | ru_ru |
|---|---|---|
| `wildroots.tooltip.brush.empty` | No pollen | Пыльцы нет |
| `wildroots.tooltip.brush.loaded` | Pollen: %s | Пыльца: %s |
| `wildroots.tooltip.brush.loaded_named` | Pollen: %1$s, line "%2$s" | Пыльца: %1$s, линия «%2$s» |
| `wildroots.tooltip.brush.fading` | The pollen is drying out | Пыльца подсыхает |

`fading` — последняя минута жизни пыльцы (GDD §4.4).

### 7.9 Блоки растений

Один блок на вид (сорт — состояние BlockEntity). Имена блоков — те же строки, что `wildroots.species.<id>` (§3.1): `block.wildroots.wild_carrot` = «Дикая морковь». Растение-сорт в подсказках вроде Jade показывает имя сорта через `wildroots.cultivar.<id>` `[ПРОВЕРИТЬ]` API Jade/WTHIT. Деревенские растения — тот же блок с меткой `village`; отдельных ключей нет.

Дополнительные блоки `[ПРЕДЛОЖЕНИЕ]` по id (GDD описывает их, но id не задаёт):

| id | en_us | ru_ru | Что это |
|---|---|---|---|
| `gourd_fruit` | Gourd | Плод тыквы | Плод-блок на плети тыквы (GDD §5.6); в Jade — «Плод: %s» по сорту |
| `watermelon_fruit` | Watermelon | Плод арбуза | Плод-блок арбуза |
| `apple_trunk` | Apple Trunk | Ствол яблони | Нижний блок дерева с геномом (GDD §5.7 п. 7) |
| `apple_leaves` | Apple Leaves | Листва яблони | — |
| `apple_fruit` | Hanging Apple | Яблоко на ветке | Плод под листвой |
| `cacao_pod` | Cacao Pod | Стручок какао | Плод на стволе |

Формат подсказки плода: `wildroots.tooltip.fruit_of` = «Плод: %s» / "Fruit of: %s".

## 8. Дерево достижений

Отдельная вкладка `wildroots:root`, фон `minecraft:textures/block/rooted_dirt.png` `[ПРЕДЛОЖЕНИЕ]`. Тип (frame): **task** — шаг обучения, **goal** — веха линии, **challenge** — долгая цель. **V** — ванильный триггер, **К** — кастомный триггер мода (§8.4). FD-достижения грузятся только при установленном FD (условие `forge:mod_loaded` `[ПРОВЕРИТЬ]` формат условий для advancements в Forge 47). **Волны** (GDD §20): достижения линии входят в датапак вместе с её волной; столбец «В» в §8.2.

### 8.1 Карта дерева

```
root (Дикие корни)
└─ first_forager (Первый сборщик)
   ├─ bitter_lesson
   ├─ kept_for_a_reason
   ├─ from_afar
   ├─ village_neighbours ── plant_breeder
   ├─ closer_look ── by_hand
   └─ seedbed (Каждая грядка — эксперимент)
      ├─ save_the_best
      ├─ exact_copy
      ├─ bees_on_payroll
      ├─ too_close*  cabbage_says_no*  odd_one_out*      (* скрытые)
      ├─ seed_keepers
      ├─ named_line
      └─ first_landrace (Местный сорт)
         ├─ einkorn
         ├─ emmer ── wheat ── choice_bread
         ├─ primitive_maize ── maize
         │                 └─ hybrid_vigor*
         ├─ eastern_carrot ── carrot
         ├─ leaf_cabbage ─┬─ head_cabbage ── two_roads ── every_fork
         │                └─ broccoli
         ├─ ancient_squash ─┬─ golem_head
         │                  ├─ field_pumpkin
         │                  └─ sugar_pumpkin
         ├─ nile_melon ─┬─ picnic_watermelon
         │              └─ icebox_watermelon
         ├─ andean_potato ── potato
         ├─ roman_beet ─┬─ table_beet
         │              └─ sugar_beet
         ├─ garden_cane ── noble_cane
         ├─ nacional_cacao ─┬─ criollo
         │                  └─ forastero
         ├─ garden_raspberry ── raspberry
         ├─ silk_road_apple ── grafted ── one_in_a_hundred ─┬─ aport
         │                                                  └─ antonovka
         ├─ cherry_tomato ── tomato ── garden_to_kitchen    (FD)
         ├─ landrace_onion ── onion                         (FD)
         ├─ early_rice ── rice                              (FD)
         ├─ seed_analyser ─┬─ carrier
         │                 ├─ cant_have_it_all
         │                 └─ genotype_lab
         └─ from_the_wild ── vavilov (Вавилов был бы горд)
```

### 8.2 Структура

| id (`wildroots:<id>`) | Родитель | Триггер | Условие | Иконка | Тип | Скрыто | В |
|---|---|---|---|---|---|---|---|
| `root` | — | V `minecraft:inventory_changed` **или** К `harvest_wild_plant` | Любой `seed_lot` / любой сбор дикого растения | `wildroots:wild_carrot_root` | task | нет | 1 |
| `first_forager` | `root` | К `harvest_wild_plant` | Любой дикий вид | `wildroots:field_journal` | task | нет | 1 |
| `bitter_lesson` | `first_forager` | К `eat_bitter` | Любая горькая или ядовитая еда | `wildroots:wild_carrot_root` | task | нет | 1 |
| `kept_for_a_reason` | `first_forager` | V `minecraft:inventory_changed` | `seed_lot` с флагом редкого аллеля `[ПРОВЕРИТЬ]` имя тега | `wildroots:seed_lot` | task | нет | 1 |
| `from_afar` | `first_forager` | V `minecraft:villager_trade` | `seed_lot` у странствующего торговца `[ПРОВЕРИТЬ]` | `minecraft:lead` | task | нет | 1 |
| `village_neighbours` | `first_forager` | К `villager_landrace` | Любой источник | `minecraft:composter` | task | нет | 1 |
| `plant_breeder` | `village_neighbours` | К `breeder_trade` | Любая сделка | `wildroots:sorting_table` | task | нет | 2 |
| `closer_look` | `first_forager` | V `minecraft:inventory_changed` | `wildroots:hand_lens` | `wildroots:hand_lens` | task | нет | 1 |
| `by_hand` | `closer_look` | К `hand_pollinate` | `self: false` | `wildroots:pollination_brush` | task | нет | 1 |
| `seedbed` | `first_forager` | К `plant_population` | `min_count: 16`, `radius: 8`, один вид | `wildroots:seed_lot` | task | нет | 1 |
| `save_the_best` | `seedbed` | К `plant_mother` | Любой маточник | `wildroots:mother_root` | task | нет | 1 |
| `exact_copy` | `seedbed` | К `plant_clone` | Любой клон | `wildroots:seed_tuber` | task | нет | 3 |
| `bees_on_payroll` | `seedbed` | К `bee_pollinated` | Своё растение | `minecraft:bee_nest` | task | нет | 1 |
| `too_close` | `seedbed` | К `plant_ailing` | Своё растение созрело чахлым | `minecraft:dead_bush` | task | да | 1 |
| `cabbage_says_no` | `seedbed` | К `pollen_rejected` | Своё растение | `wildroots:wild_cabbage_leaves` | task | да | 2 |
| `odd_one_out` | `seedbed` | К `mutation_found` | Видимая мутация | `wildroots:seed_lot` | task | да | 1 |
| `seed_keepers` | `seedbed` | V `minecraft:inventory_changed` | `wildroots:seed_bank` | `wildroots:seed_bank` | task | нет | 1 |
| `named_line` | `seedbed` | К `line_generation` | `named: true`, `min_generation: 5` | `minecraft:name_tag` | task | нет | 1 |
| `first_landrace` | `seedbed` | К `cultivar_bred` | `tier: 1` | `wildroots:eastern_carrot` | goal | нет | 1 |
| `einkorn` | `first_landrace` | К `cultivar_bred` | `einkorn` | `wildroots:einkorn_grain` | task | нет | 2 |
| `emmer` | `first_landrace` | К `cultivar_bred` | `emmer` | `wildroots:emmer_grain` | task | нет | 2 |
| `wheat` | `emmer` | К `cultivar_bred` | `wheat` | `wildroots:wheat` | goal | нет | 2 |
| `choice_bread` | `wheat` | V `minecraft:inventory_changed` | `minecraft:bread` с NBT `wildroots:grade: 4` | `minecraft:bread` | goal | нет | 2 |
| `primitive_maize` | `first_landrace` | К `cultivar_bred` | `primitive_maize` | `wildroots:primitive_maize` | task | нет | 2 |
| `maize` | `primitive_maize` | К `cultivar_bred` | `maize` | `wildroots:corn` | goal | нет | 2 |
| `hybrid_vigor` | `primitive_maize` | К `hybrid_vigor` | Любой вид | `wildroots:corn` | goal | да | 2 |
| `eastern_carrot` | `first_landrace` | К `cultivar_bred` | `eastern_carrot` | `wildroots:eastern_carrot` | task | нет | 1 |
| `carrot` | `eastern_carrot` | К `cultivar_bred` | `carrot` | `wildroots:carrot` | goal | нет | 1 |
| `leaf_cabbage` | `first_landrace` | К `cultivar_bred` | `leaf_cabbage` | `wildroots:leaf_cabbage` | task | нет | 2 |
| `head_cabbage` | `leaf_cabbage` | К `cultivar_bred` | `head_cabbage` | `wildroots:cabbage` | goal | нет | 2 |
| `broccoli` | `leaf_cabbage` | К `cultivar_bred` | `broccoli` | `wildroots:broccoli` | goal | нет | 2 |
| `two_roads` | `head_cabbage` | К `cultivar_bred` ×2 | `head_cabbage` и `broccoli` | `wildroots:broccoli` | challenge | нет | 2 |
| `every_fork` | `two_roads` | К `cultivar_bred` ×12 | Обе ветки капусты, тыквы, арбуза, свёклы, какао, яблони | `minecraft:oak_sapling` | challenge | нет | 4 |
| `ancient_squash` | `first_landrace` | К `cultivar_bred` | `ancient_squash` | `wildroots:ancient_squash` | task | нет | 3 |
| `golem_head` | `ancient_squash` | К `carve_pumpkin` | Любой размер | `wildroots:carved_pumpkin` | task | нет | 3 |
| `field_pumpkin` | `ancient_squash` | К `cultivar_bred` | `field_pumpkin` | `wildroots:field_pumpkin` | goal | нет | 3 |
| `sugar_pumpkin` | `ancient_squash` | К `cultivar_bred` | `sugar_pumpkin` | `wildroots:sugar_pumpkin` | goal | нет | 3 |
| `nile_melon` | `first_landrace` | К `cultivar_bred` | `nile_melon` | `wildroots:nile_melon_slice` | task | нет | 3 |
| `picnic_watermelon` | `nile_melon` | К `cultivar_bred` | `picnic_watermelon` | `wildroots:picnic_watermelon_slice` | goal | нет | 3 |
| `icebox_watermelon` | `nile_melon` | К `cultivar_bred` | `icebox_watermelon` | `wildroots:icebox_watermelon_slice` | goal | нет | 3 |
| `andean_potato` | `first_landrace` | К `cultivar_bred` | `andean_potato` | `wildroots:andean_potato` | task | нет | 3 |
| `potato` | `andean_potato` | К `cultivar_bred` | `potato` | `wildroots:potato` | goal | нет | 3 |
| `roman_beet` | `first_landrace` | К `cultivar_bred` | `roman_beet` | `wildroots:roman_beet` | task | нет | 3 |
| `table_beet` | `roman_beet` | К `cultivar_bred` | `table_beet` | `wildroots:beetroot` | goal | нет | 3 |
| `sugar_beet` | `roman_beet` | К `cultivar_bred` | `sugar_beet` | `wildroots:sugar_beet` | goal | нет | 3 |
| `garden_cane` | `first_landrace` | К `cultivar_bred` | `garden_cane` | `wildroots:garden_cane_stalk` | task | нет | 4 |
| `noble_cane` | `garden_cane` | К `cultivar_bred` | `noble_cane` | `wildroots:noble_cane` | goal | нет | 4 |
| `nacional_cacao` | `first_landrace` | К `cultivar_bred` | `nacional_cacao` | `wildroots:nacional_cacao_beans` | task | нет | 4 |
| `criollo` | `nacional_cacao` | К `cultivar_bred` | `criollo` | `wildroots:criollo_beans` | goal | нет | 4 |
| `forastero` | `nacional_cacao` | К `cultivar_bred` | `forastero` | `wildroots:forastero_beans` | goal | нет | 4 |
| `garden_raspberry` | `first_landrace` | К `cultivar_bred` | `garden_raspberry` | `wildroots:garden_raspberry` | task | нет | 4 |
| `raspberry` | `garden_raspberry` | К `cultivar_bred` | `raspberry` | `wildroots:raspberry` | goal | нет | 4 |
| `silk_road_apple` | `first_landrace` | К `cultivar_bred` | `silk_road_apple` | `wildroots:silk_road_apple` | task | нет | 4 |
| `grafted` | `silk_road_apple` | К `graft_apple` | `success: true` | `wildroots:apple_scion` | task | нет | 4 |
| `one_in_a_hundred` | `grafted` | К `seedling_cultivar` | Любой сорт яблони | `wildroots:apple_scion` | goal | нет | 4 |
| `aport` | `one_in_a_hundred` | К `cultivar_bred` | `aport` | `wildroots:aport_apple` | goal | нет | 4 |
| `antonovka` | `one_in_a_hundred` | К `cultivar_bred` | `antonovka` | `wildroots:antonovka_apple` | goal | нет | 4 |
| `cherry_tomato` **FD** | `first_landrace` | К `cultivar_bred` | `cherry_tomato` | `wildroots:cherry_tomato` | task | нет | FD |
| `tomato` **FD** | `cherry_tomato` | К `cultivar_bred` | `tomato` | `wildroots:tomato` | goal | нет | FD |
| `landrace_onion` **FD** | `first_landrace` | К `cultivar_bred` | `landrace_onion` | `wildroots:landrace_onion` | task | нет | FD |
| `onion` **FD** | `landrace_onion` | К `cultivar_bred` | `onion` | `wildroots:onion` | goal | нет | FD |
| `early_rice` **FD** | `first_landrace` | К `cultivar_bred` | `early_rice` | `wildroots:early_rice` | task | нет | FD |
| `rice` **FD** | `early_rice` | К `cultivar_bred` | `rice` | `wildroots:rice` | goal | нет | FD |
| `garden_to_kitchen` **FD** | `tomato` | К `cultivar_bred` ×4 | `tomato`, `onion`, `rice`, `head_cabbage` | `farmersdelight:cooking_pot` `[ПРОВЕРИТЬ]` | challenge | нет | FD |
| `seed_analyser` | `first_landrace` | V `minecraft:inventory_changed` | `wildroots:seed_analyser` | `wildroots:seed_analyser` | task | нет | 1 |
| `carrier` | `seed_analyser` | К `analyze_lot` | `carrier: true` | `wildroots:seed_lot` | task | нет | 1 |
| `cant_have_it_all` | `seed_analyser` | К `analyze_lot` | `tradeoff: true` | `wildroots:seed_analyser` | task | нет | 1 |
| `genotype_lab` | `seed_analyser` | V `minecraft:inventory_changed` | `wildroots:genotype_lab` | `wildroots:genotype_lab` | goal | нет | 2 |
| `from_the_wild` | `first_landrace` | К `cultivar_bred` | `tier: 2`, `from_wild: true` | `wildroots:wild_carrot_root` | goal | нет | 1 |
| `vavilov` | `from_the_wild` | К `cultivar_bred`, по группе на линию | Хотя бы один сорт ступени 2 в каждой линии ядра, загруженной в сборке | `wildroots:field_journal` | challenge | нет | 1→4 |

**`vavilov` по волнам.** Требования — «группа ИЛИ на линию»: пшеница; кукуруза; морковь; кочанная **или** брокколи; полевая **или** сахарная тыква; крупный **или** сахарный арбуз; картофель; столовая **или** сахарная свёкла; благородный тростник; криолло **или** форастеро; бесшипая малина; апорт **или** антоновка. Файл достижения собирается из линий волны (как `removedVanillaCrops`, GDD §20): в волне 1 это одна морковь, к волне 4 — все 12 `[ПРОВЕРИТЬ]` генерацию JSON при сборке. FD-линии не входят: их закрывает `garden_to_kitchen`.
**`every_fork`** — для тех, кто проходит обе ветки каждой развилки; появляется в волне 4, когда есть все шесть развилок.
**Награды** (`rewards`): только у challenge — 100 опыта, как у ванильных испытаний `[ПРЕДЛОЖЕНИЕ]`.

### 8.3 Тексты (`advancements.wildroots.<id>.title` / `.description`)

| id | en_us title | en_us description | ru_ru title | ru_ru description |
|---|---|---|---|---|
| `root` | Wild Roots | A world where nothing has been tamed yet | Дикие корни | Мир, где ещё ничего не приручено |
| `first_forager` | First Forager | Harvest a wild plant. The journal takes it from there | Первый сборщик | Соберите дикое растение. Дальше подскажет журнал |
| `bitter_lesson` | A Bitter Lesson | Eat something bitter. Remember which plant it came from | Горький урок | Съешьте что-нибудь горькое. И запомните, с какого растения |
| `kept_for_a_reason` | Kept for a Reason | Find seeds with a rare trait in an old chest | Хранили не просто так | Найдите в старом сундуке семена с редким признаком |
| `from_afar` | From Distant Lands | Buy seeds from a wandering trader | Из далёких земель | Купите семена у странствующего торговца |
| `village_neighbours` | Neighbourly Farmers | Buy seed from a village farmer, or harvest a village field | Соседи-земледельцы | Купите семена у деревенского фермера или соберите урожай с деревенского поля |
| `plant_breeder` | Someone Who Knows | Trade with a plant breeder | Знающий человек | Поторгуйтесь с селекционером |
| `closer_look` | A Closer Look | Make a hand lens | Присмотритесь | Сделайте лупу |
| `by_hand` | By Hand | Cross two plants with a pollination brush | Своими руками | Скрестите два растения кистью |
| `seedbed` | Every Bed an Experiment | Plant at least 16 of one kind side by side | Каждая грядка — эксперимент | Посадите рядом не меньше 16 растений одного вида |
| `save_the_best` | Save the Best | Plant a mother plant and let it go to seed | Лучших — на семена | Посадите маточник, чтобы он дал семена |
| `exact_copy` | Exact Copy | Plant a clone: a seed tuber, a cutting or a sucker | Точная копия | Посадите клон: семенной клубень, черенок или отпрыск |
| `bees_on_payroll` | Bees on the Payroll | Have a bee pollinate one of your plants | Пчёлы в доле | Дождитесь, пока пчела опылит ваше растение |
| `too_close` | Too Close for Comfort | Grow a plant weakened by inbreeding | Слишком близкая родня | Вырастите растение, ослабленное близкородственным скрещиванием |
| `cabbage_says_no` | The Cabbage Says No | Learn that cabbage refuses pollen from its kin | Капуста против | Узнайте, что капуста не принимает пыльцу родни |
| `odd_one_out` | Odd One Out | Find a plant unlike either of its parents | Белая ворона | Найдите растение, не похожее ни на одного из родителей |
| `seed_keepers` | Seed Keepers | Build a seed bank | Хранители семян | Постройте семенной банк |
| `named_line` | A Line With a Name | Carry a named line to its fifth generation | Линия с именем | Доведите именованную линию до пятого поколения |
| `first_landrace` | Landrace | Breed your first landrace | Местный сорт | Выведите первый ландрас |
| `einkorn` | One Grain at a Time | Breed einkorn | По зёрнышку | Выведите однозернянку |
| `emmer` | Bread of the Pharaohs | Breed emmer | Хлеб фараонов | Выведите полбу |
| `wheat` | Clean Threshing | Breed wheat that threshes free of its hulls | Чистый обмолот | Выведите пшеницу, которая обмолачивается без плёнок |
| `choice_bread` | Better Than Bought | Bake choice bread | Лучше покупного | Испеките отборный хлеб |
| `primitive_maize` | Cave Cob | Breed primitive maize | Початок из пещеры | Выведите примитивную кукурузу |
| `maize` | Nine Thousand Years by Sundown | Breed maize from teosinte | Девять тысяч лет до заката | Выведите кукурузу из теосинте |
| `hybrid_vigor` | Hybrid Vigour | Cross two ailing lines and get sturdy offspring | Сила гибрида | Скрестите две чахлые линии и получите крепкое потомство |
| `eastern_carrot` | Out of the East | Breed the eastern carrot | С Востока | Выведите восточную морковь |
| `carrot` | The Carrot You Know | Breed the orange carrot | Та самая морковь | Выведите оранжевую морковь |
| `leaf_cabbage` | Cato's Cabbage | Breed leaf cabbage | Капуста Катона | Выведите листовую капусту |
| `head_cabbage` | Heads, You Win | Breed head cabbage | Сто одёжек | Выведите кочанную капусту |
| `broccoli` | Italian Asparagus | Breed broccoli | Итальянская спаржа | Выведите брокколи |
| `two_roads` | One Species, Two Roads | Breed both head cabbage and broccoli | Один вид — две дороги | Выведите и кочанную капусту, и брокколи |
| `every_fork` | Every Fork in the Road | Breed both branches of every line that forks | Все развилки | Выведите обе ветки каждой линии, где дорога раздваивается |
| `ancient_squash` | First of the Three Sisters | Breed the ancient squash | Первая из трёх сестёр | Выведите древнюю тыкву |
| `golem_head` | A Head for a Golem | Carve a pumpkin big enough for a face | Голова для голема | Вырежьте тыкву, на которой поместится лицо |
| `field_pumpkin` | Fit for a Coach | Breed the field pumpkin | Хоть в карету | Выведите полевую тыкву |
| `sugar_pumpkin` | Pie Season | Breed the sugar pumpkin | К пирогу | Выведите сахарную тыкву |
| `nile_melon` | Water in the Desert | Breed the Nile melon | Вода в пустыне | Выведите нильский арбуз |
| `picnic_watermelon` | Enough for Everyone | Breed the picnic watermelon | На всю компанию | Выведите крупный арбуз |
| `icebox_watermelon` | Small but Sweet | Breed the icebox watermelon | Мал, да сладок | Выведите сахарный арбуз |
| `andean_potato` | Papas Nativas | Breed the Andean potato | Папас нативас | Выведите андский картофель |
| `potato` | The Humble Spud | Breed the potato | Второй хлеб | Выведите картофель |
| `roman_beet` | Roots, Not Leaves | Breed the Roman beet | Корень, а не ботва | Выведите римскую свёклу |
| `table_beet` | Borscht Ready | Breed the table beet | Будет борщ | Выведите столовую свёклу |
| `sugar_beet` | Sugar Without the Tropics | Breed the sugar beet | Сахар без тропиков | Выведите сахарную свёклу |
| `garden_cane` | Something to Chew On | Breed garden cane | Есть что пожевать | Выведите садовый тростник |
| `noble_cane` | Of Noble Stock | Breed noble cane | Благородных кровей | Выведите благородный тростник |
| `nacional_cacao` | From Upriver | Breed Nacional cacao | Сверху по реке | Выведите какао насьональ |
| `criollo` | Food of the Gods | Breed criollo | Пища богов | Выведите криолло |
| `forastero` | The Outsider | Breed forastero | Чужак | Выведите форастеро |
| `garden_raspberry` | Monastery Garden | Breed the monastery raspberry | Монастырский сад | Выведите монастырскую малину |
| `raspberry` | Not a Scratch | Breed the thornless raspberry | Без царапин | Выведите бесшипую малину |
| `silk_road_apple` | Along the Silk Road | Breed the Silk Road apple | По Шёлковому пути | Выведите яблоко Шёлкового пути |
| `grafted` | It Took | Graft a scion onto a young apple seedling | Прижилось | Привейте черенок на молодой сеянец яблони |
| `one_in_a_hundred` | One in a Hundred | Find a new variety among apple seedlings and name it | Один из сотни | Найдите среди сеянцев яблони новый сорт и дайте ему имя |
| `aport` | City of Apples | Raise an aport-type apple | Город яблок | Вырастите яблоню типа апорта |
| `antonovka` | Scent of Autumn | Raise an antonovka-type apple | Антоновские яблоки | Вырастите яблоню типа антоновки |
| `cherry_tomato` **FD** | A Berry Grown Up | Breed the cherry tomato | Ягода подросла | Выведите черри-томат |
| `tomato` **FD** | Not Poisonous After All | Breed the tomato | Всё-таки не ядовит | Выведите томат |
| `landrace_onion` **FD** | Through Tears | Breed the landrace onion | Сквозь слёзы | Выведите местный лук |
| `onion` **FD** | Ancestor Unknown | Breed the onion | Предок неизвестен | Выведите лук |
| `early_rice` **FD** | The Rice That Stayed | Breed early rice | Рис, который не осыпался | Выведите ранний рис |
| `rice` **FD** | White Grain | Breed white rice | Белое зерно | Выведите белый рис |
| `garden_to_kitchen` **FD** | Garden to Kitchen | Breed tomato, onion, rice and head cabbage | С грядки — на кухню | Выведите томат, лук, рис и кочанную капусту |
| `seed_analyser` | Nature or Luck? | Build a seed analyser | Наследственность или везение? | Постройте анализатор семян |
| `carrier` | Carrier | Find a hidden trait with the analyser | Носитель | Найдите анализатором скрытый признак |
| `cant_have_it_all` | Can't Have It All | Find out with the analyser where a plant's strength goes | Всего сразу не бывает | Узнайте анализатором, на что уходят силы растения |
| `genotype_lab` | Reading the Genes | Build a genotype lab | Читая гены | Постройте лабораторию генотипа |
| `from_the_wild` | From the Wild | Breed a cultivar whose line began with a wild plant | Из дикой природы | Выведите сорт, чья линия началась с дикого растения |
| `vavilov` | Vavilov Would Be Proud | Breed a cultivar in every line | Вавилов был бы горд | Выведите сорт в каждой линии |

Отсылки, которые не переводятся дословно, а адаптируются: «Сто одёжек» — загадка «сидит дед, во сто шуб одет», «Heads, You Win» — «heads I win, tails you lose»; «Хоть в карету» / "Fit for a Coach" — тыква Золушки; «Второй хлеб» — русское прозвище картофеля, "The Humble Spud" — английское; «Антоновские яблоки» — название рассказа Бунина (1900), "Scent of Autumn" — тот же образ без отсылки; «Пища богов» / "Food of the Gods" — перевод родового имени *Theobroma*; «Город яблок» — название Алматы связывают со словом «яблоко».

**Запись журнала при `vavilov`** (`wildroots.journal.hint.vavilov`, заголовок «Вавилов» / "Vavilov"):
- ru: «Николай Вавилов объездил пять континентов, собирая семена культурных растений и их диких родственников. Он искал места, где культуры родились, — там их разнообразие больше всего. Яблоневые леса под Алматы он называл родиной яблони.»
- en: "Nikolai Vavilov travelled five continents collecting the seeds of crops and their wild relatives. He looked for the places crops were born, where their variety runs richest. He named the apple forests near Almaty as the apple's homeland."

### 8.4 Кастомные триггеры (для инженеров)

Все срабатывают на сервере для игрока-владельца растения (UUID посадившего), кроме `harvest_wild_plant`, `eat_bitter`, `villager_landrace`, `breeder_trade` и `carve_pumpkin` (действующий игрок). Сорт засчитывается только тому, кто посадил растение.

| id (`wildroots:<id>`) | Когда | Поля условия |
|---|---|---|
| `harvest_wild_plant` | Сбор зрелого дикого растения (патч или своё ступени 0) | `species` (опц.) |
| `eat_bitter` | Съедена еда с флагом горечи (GDD §3.6) | `toxic` (опц.) |
| `plant_population` | Посадка лота; считаются свои растения вида в радиусе | `min_count`, `radius`, `species` (опц.) |
| `plant_mother` | Посадка маточника | `species` (опц.) |
| `bee_pollinated` | Пчела отложила донора на своё растение | `species` (опц.) |
| `hand_pollinate` | Кисть перенесла пыльцу | `self` (bool), `species` (опц.) |
| `plant_ailing` | Своё растение созрело с витальностью ниже порога чахлости | `species` (опц.) |
| `pollen_rejected` | Самонесовместимость отвергла донора у своего растения | `species` (опц.) |
| `mutation_found` | У своего растения проявилась мутация качественного признака | `trait` (опц.) |
| `hybrid_vigor` | Своё растение без чахлости, оба родителя чахлые | `species` (опц.) |
| `line_generation` | Посажен лот линии с номером поколения ≥ N | `named`, `min_generation` |
| `cultivar_bred` | Первое распознавание сорта у своего растения | `cultivar`, `tier`, `from_wild` (все опц.) |
| `analyze_lot` | Анализ завершён (анализатор или «оценка» селекционера) | `carrier` (bool), `tradeoff` (bool: сработал компромисс или бюджет) |
| `carve_pumpkin` | Вырезана тыква (GDD §5.6) | `size` (`large` / `huge`, опц.) |
| `plant_clone` | Посажен клон (клубень, черенок, отпрыск) | `species` (опц.), `clone_of_clone` (bool) |
| `villager_landrace` | Покупка лота у фермера или сбор растения с меткой `village` | `source` (`trade` / `harvest`, опц.) |
| `graft_apple` | Попытка прививки | `success` (bool) |
| `seedling_cultivar` | Свой сеянец яблони прошёл пороги сорта (GDD §5.7 п. 3) | `cultivar` (опц.) |
| `breeder_trade` | Сделка с селекционером | `level` (опц.), `kind` (`sell_find` / `assess` / `note` / `other`, опц.) |

У яблони `seedling_cultivar` и `cultivar_bred` срабатывают вместе: первое — «новый сорт среди сеянцев», второе — тип ветки (апорт или антоновка).

## 9. Прочие строки: сообщения, шкалы, интерфейс

### 9.1 Глоссарий (один термин — одно слово)

| Понятие GDD | ru_ru в игре | en_us в игре | Не использовать |
|---|---|---|---|
| Лот семян | семена (в имени), лот (в журнале); у яблони — косточки | seeds, seed lot; pips | пакет |
| Маточник | маточник | mother plant / mother root | семенник |
| Клон | клон | clone | копия (только в пояснениях) |
| Черенок (тростник, какао) | черенок | cutting | саженец |
| Корневой отпрыск (малина) | отпрыск | sucker | побег, клон-куст |
| Семенной клубень | семенной клубень | seed tuber | посадочная картошка |
| Привой | привой | scion | черенок (у яблони) |
| Подвой | подвой | rootstock | основа |
| Сеянец | сеянец | seedling | росток |
| Одичавшее насаждение | одичавший сад (общее), садовый тростник, роща насьональ, одичавшая малина | feral garden / feral planting | заброшенная ферма |
| Класс урожая | класс; в тултипе — слово класса | grade | качество (в тултипе), тир, звёзды |
| Ландрас | ландрас, местный сорт | landrace | полусорт |
| Сорт ступени 2 | сорт | cultivar | вариант, порода |
| Линия | линия | line | род, семья |
| Дикий патч | заросли | patch | патч |
| Инбредная депрессия | чахлость; в справках — близкородственное скрещивание | ailing; inbreeding | вырождение |
| Гетерозигота по нужному признаку | носитель | carrier | гетерозигота (только в лаборатории) |
| Самоопыление | самоопыление | selfing | — |
| Фермер-селекционер | селекционер | plant breeder | агроном, учёный |

### 9.2 Признаки и шкалы лупы

**Шкала 5 ступеней** — по `P` (лупа) или `G` (цели в журнале). Границы приняты (GDD §10): `< 0.20 | 0.20–0.35 | 0.35–0.55 | 0.55–0.75 | ≥ 0.75`. Они же — границы классов урожая (§7.6). Слова целей (§4.4): «не мелкий», «не мельче среднего» = ступень 3+; «крупный», «щедрый», «крупнее среднего» = 4+; «очень…», «отменный» = 5.

**Имена признаков** (`wildroots.tooltip.trait_name.<trait>`; переопределение для вида — `.<trait>.<species_id>`):

| Признак | Общее en / ru | Переопределения ru (en) | Шкала |
|---|---|---|---|
| `fruit_size` | Size / Размер | морковь, свёкла: Размер корня (Root size); пшеница, рис: Размер зерна (Grain size); теосинте: Размер початка (Ear size); капуста: Размер кочана (Head size); томат, тыква, арбуз, яблоня: Размер плода (Fruit size); лук: Размер луковицы (Bulb size); картофель: Размер клубня (Tuber size); тростник: Размер стебля (Stalk size); какао: Размер стручка (Pod size); малина: Размер ягоды (Berry size) | `size` |
| `yield` | Yield / Урожай | — | `yield` |
| `growth_speed` | Growth / Рост | — | `growth` |
| `flavor` | Taste / Вкус | морковь: Вкус, шкала `sweetness`; капуста: Лист (Leaf), шкала `tenderness`; тростник: Сахаристость (Sugar), шкала `tolerance`; какао: Аромат (Aroma), шкала `flavor` | `flavor` |
| `sugar` (свёкла) | Sugar / Сахаристость | — | `tolerance` |
| `cold_tolerance` | Cold hardiness / Холодостойкость | какао: Выносливость (Hardiness) | `tolerance` |
| `heat_tolerance` | Heat tolerance / Жаростойкость | — | `tolerance` |
| `drought_tolerance` | Drought tolerance / Засухоустойчивость | — | `tolerance` |
| `leaf_size` (капуста) | Leaf size / Размер листа | — | `size` |
| `terminal_bud` (капуста) | Heading / Завивается в кочан | — | `expression` |
| `inflorescence` (капуста) | Flower buds / Соцветия | — | `expression` |
| `stem` (капуста) | Thick stem / Утолщение стебля | — | `expression` |
| `lateral_buds` (капуста) | Side buds / Боковые почки | — | `expression` |
| `apical_dominance` (теосинте) | Single stalk / Одностебельность | — | `expression` |

Подписи построены так, чтобы слово шкалы согласовывалось с мужским родом («Размер: крупный», «Аромат: отменный») или было женского рода / наречием для всех строк своей шкалы («Сахаристость: высокая», «Соцветия: заметно»).

**Шкалы** (`wildroots.tooltip.scale.<scale>.<1..5>`):

| Шкала | 1 | 2 | 3 | 4 | 5 |
|---|---|---|---|---|---|
| `size` ru | очень мелкий | мелкий | средний | крупный | очень крупный |
| `size` en | very small | small | medium | large | very large |
| `yield` ru | очень скудный | скудный | умеренный | щедрый | обильный |
| `yield` en | very poor | poor | fair | generous | abundant |
| `growth` ru | очень медленный | медленный | обычный | быстрый | очень быстрый |
| `growth` en | very slow | slow | steady | quick | very quick |
| `flavor` ru | грубый | пресный | приятный | хороший | отменный |
| `flavor` en | harsh | bland | pleasant | good | excellent |
| `sweetness` ru | деревянистый | едва сладкий | сладковатый | сладкий | очень сладкий |
| `sweetness` en | woody | barely sweet | sweetish | sweet | very sweet |
| `tenderness` ru | жёсткий | грубоватый | мягкий | нежный | очень нежный |
| `tenderness` en | tough | coarse | soft | tender | very tender |
| `tolerance` ru | очень низкая | низкая | средняя | высокая | очень высокая |
| `tolerance` en | very poor | poor | fair | good | excellent |
| `expression` ru | почти нет | слабо | заметно | сильно | очень сильно |
| `expression` en | barely | slight | noticeable | strong | very strong |

Шкала `flavor` (свойство растения) и слова классов (свойство урожая, §7.6) — разные наборы слов, чтобы «грубый вкус» под лупой не путался с «грубым» классом стака. Совпадает одно слово «грубый»; на ступени 1 это честно одно и то же.

**Качественные признаки под лупой** (`wildroots.tooltip.qual_state.<trait>.<state>`):

| Ключ | en_us | ru_ru |
|---|---|---|
| `bitterness.wild` / `.domestic` | Bitter: yes / Bitter: no | Горечь: есть / Горечь: нет |
| `bitterness.wild.toxic` / `.domestic.toxic` | Poisonous: yes / no | Ядовитость: есть / нет |
| `bitterness.wild.wild_onion` / `.domestic.wild_onion` | Pungency: fierce / mild | Острота: жгучая / мягкая |
| `bitterness.wild.wild_cacao` / `.domestic.wild_cacao`, то же для `wild_apple` | Astringency: yes / no | Терпкость: есть / нет |
| `dormancy.wild` / `.domestic` | Seed dormancy: yes / no | Покой семян: есть / нет |
| `hard_rind.wild` / `.domestic` | Rind: hard / soft | Кора: твёрдая / мягкая |
| `soft_stalk.wild` / `.domestic` | Stalk: tough / juicy | Стебель: жёсткий / сочный |
| `bolting.wild` / `.domestic` | Flowers in first year / Biennial | Цветёт в первый год / Двулетник |

### 9.3 Слова «на глаз» (строка `Мать на глаз: %s`)

Собираются через запятую: размер, затем цвет, затем форма. Размер — 3 слова: ступени 1–2 → «мелкий», 3 → «средний», 4–5 → «крупный». Слова согласованы с существительным внутри ключа, поэтому ключи видовые.

| Ключ | en_us | ru_ru |
|---|---|---|
| `wildroots.tooltip.eye.size.wild_carrot.{small,medium,large}`, `.sea_beet.*` | small / medium / large root | корень мелкий / средний / крупный |
| `wildroots.tooltip.eye.size.wild_wheat.*`, `.wild_rice.*` | small / medium / large grain | зерно мелкое / среднее / крупное |
| `wildroots.tooltip.eye.size.teosinte.*` | small / medium / large ear | початок мелкий / средний / крупный |
| `wildroots.tooltip.eye.size.wild_cabbage.*` | small / medium / large leaves | листья мелкие / средние / крупные |
| `wildroots.tooltip.eye.size.wild_tomato.*`, `.wild_gourd.*`, `.wild_watermelon.*`, `.wild_apple.*` | small / medium / large fruit | плоды мелкие / средние / крупные |
| `wildroots.tooltip.eye.size.wild_onion.*` | small / medium / large bulb | луковица мелкая / средняя / крупная |
| `wildroots.tooltip.eye.size.wild_potato.*` | small / medium / large tubers | клубни мелкие / средние / крупные |
| `wildroots.tooltip.eye.size.wild_cane.*` | thin / medium / thick stalks | стебли тонкие / средние / толстые |
| `wildroots.tooltip.eye.size.wild_cacao.*` | small / medium / large pods | стручки мелкие / средние / крупные |
| `wildroots.tooltip.eye.size.wild_raspberry.*` | small / medium / large berries | ягоды мелкие / средние / крупные |
| `wildroots.tooltip.eye.color.wild_carrot.{white,yellow,orange,purple}` | white / yellow / orange / purple | белый / жёлтый / оранжевый / фиолетовый |
| `wildroots.tooltip.eye.color.sea_beet.{white,red}` | white / red | белый / красный |
| `wildroots.tooltip.eye.color.teosinte.{white,yellow,red}` | white / yellow / red kernels | зерно белое / жёлтое / красное |
| `wildroots.tooltip.eye.color.wild_cabbage.{green,red}` | green / red | зелёные / красные |
| `wildroots.tooltip.eye.color.wild_rice.{red,white}` | red / white grain | зерно красное / белое |
| `wildroots.tooltip.eye.flesh.wild_watermelon.{pale,red}` | pale / red flesh | мякоть бледная / красная |
| `wildroots.tooltip.eye.shatters` / `.holds` | ear shatters / ear holds | колос осыпается / колос держится |
| `wildroots.tooltip.eye.shatters.wild_rice` / `.holds.wild_rice` | panicle shatters / holds | метёлка осыпается / держится |
| `wildroots.tooltip.eye.grains.one` / `.two` | one grain per spikelet / two | одно зерно в колоске / два зерна в колоске |
| `wildroots.tooltip.eye.hulled` / `.free_threshing` | hulled / threshes clean | в плёнках / обмолачивается чисто |
| `wildroots.tooltip.eye.encased` / `.naked` | kernels in shells / naked kernels | зерно в скорлупе / голое зерно |
| `wildroots.tooltip.eye.bushy` / `.single_stalk` | bushy / single stalk | куст / один стебель |
| `wildroots.tooltip.eye.stolons.long` / `.short` | tubers run far / tubers stay close | клубни разбегаются / клубни у куста |
| `wildroots.tooltip.eye.bolted` | flowered in the first year | зацвела в первый год |
| `wildroots.tooltip.eye.thorny` / `.thornless` | thorny / thornless | колючий / без шипов |

Цвет стоит после размера и согласуется с тем же существительным: «корень крупный, жёлтый», «листья средние, красные», «плоды крупные, мякоть красная».

### 9.4 Опыление (лупа, тир 1; `wildroots.tooltip.pollination.<id>`)

| id | en_us | ru_ru | Когда |
|---|---|---|---|
| `bees` | Pollination: bees | Опыление: пчёлы | Был пчелиный донор |
| `hand` | Pollination: by hand | Опыление: вручную | Кисть, другой цветок |
| `bud` | Pollination: in the bud | Опыление: в бутоне | Кисть по себе у самонесовместимых (капуста, картофель, свёкла, какао, яблоня) |
| `forced_self` | Pollination: selfed by hand | Опыление: самоопыление вручную | Кисть по себе, самосовместимый вид |
| `neighbours` | Pollination: neighbours | Опыление: соседи | Доноры в радиусе, без пчёл и кисти |
| `wind` | Pollination: wind | Опыление: ветер | Ветроопыляемый вид с донорами |
| `village` | Pollination: a village field nearby | Опыление: деревенское поле рядом | Среди доноров есть растение с меткой `village` |
| `self` | Pollination: selfed | Опыление: само на себя | Доноров нет |
| `no_pollinator` | No pollinators came | Опылители не прилетали | Насекомоопыляемый вид без опылителя |
| `rejected` | Neighbours' pollen refused | Пыльца соседей отвергнута | Самонесовместимость; в т. ч. клоны одного растения |
| `ailing` | Signs of inbreeding | Признаки чахлости | Витальность ниже порога |

### 9.5 Анализатор (тир 2; `wildroots.tooltip.analysis.<id>`)

Числа здесь разрешены. Код форматирует число по локали (`0,45` в ru, `0.45` в en) и передаёт строкой. На каждый признак — две строки (GDD §3.10): что передаётся по наследству (`G`) и что реально проявляется (`G_eff`); если они различаются, третья строка говорит, на что уходит разница.

| id | en_us | ru_ru |
|---|---|---|
| `header` | Analysis: | Анализ: |
| `trait` | %1$s: %2$s heritable | %1$s: %2$s по наследству |
| `trait_realised` | %1$s: %2$s realised | %1$s: %2$s реализуется |
| `spent_on` | spent on: %s | уходит на: %s |
| `at_limit` | The plant is at the limit of its strength | Растение упёрлось в предел своих сил |
| `carrier` | Carrier: %s | Носитель: %s |
| `fixed` | Fixed: %s | Закреплено: %s |
| `one_copy` | One copy only: %s | Одна копия: %s |
| `vitality` | Vitality: %s | Витальность: %s |
| `cultivar_check` | %1$s: %2$d of %3$d met | «%1$s»: выполнено %2$d из %3$d |
| `assessed_by` | Assessed by a plant breeder | Оценено селекционером |

`spent_on` получает имя признака-«потребителя» (§9.2) или несколько через запятую: «уходит на: Размер плода, Урожай». `at_limit` — когда сработал бюджет (слой 2), а не пара (слой 1).

**Имена качественных признаков** для `carrier` / `fixed` / `one_copy` (`wildroots.tooltip.qual.<trait>`, домашний аллель): `shattering` — non-shattering / неосыпаемость; `dormancy` — no dormancy / дружные всходы; `bitterness` — no bitterness / без горечи (лук: mild / мягкость; какао, яблоня: no astringency / без терпкости); `color_y1` — yellow / жёлтый цвет; `color_y2` — orange (second gene) / оранжевый (второй ген); `color_p` — purple / фиолетовый цвет; `spikelet_grains` — two grains / двузёрный колос; `free_threshing` — free threshing / голозёрность; `naked_kernel` — naked kernel / голое зерно; `tb1` — single stalk / одностебельность; `color_y` — yellow kernel / жёлтое зерно; `color_r` — red kernel / красное зерно (у свёклы: `color_r.sea_beet` — red root / красный корень); `color_a` — red leaf / красный лист; `color_rc` — white grain / белое зерно; `hard_rind` — soft rind / мягкая кора; `flesh_red` — red flesh / красная мякоть; `short_stolon` — short stolons / короткие столоны; `bolting` — biennial / двулетность; `soft_stalk` — soft stalk / мягкий стебель; `thornless` — thornless / бесшипость.

### 9.6 Сообщения (`wildroots.message.<id>`)

| id | Где | en_us | ru_ru |
|---|---|---|---|
| `journal.received` | чат | You now carry a field journal. | Теперь с собой полевой журнал. |
| `journal.new_entry` | тост | New journal entry: %s | Новая запись в журнале: %s |
| `species_discovered` | action bar | New species: %s | Найден вид: %s |
| `toast.cultivar_bred.title` | тост | Cultivar bred! | Выведен сорт! |
| `toast.landrace_bred.title` | тост | Landrace bred! | Выведен ландрас! |
| `toast.seedling_cultivar.title` | тост | A new variety among the seedlings! | Найден новый сорт среди сеянцев! |
| `toast.bred.body` | тост | %s | %s |
| `toast.seedling_cultivar.body` | тост | Give it a name in your journal | Дайте ему имя в журнале |
| `brush.picked` | action bar | Pollen collected: %s | Пыльца набрана: %s |
| `brush.applied` | action bar | Pollen transferred | Пыльца перенесена |
| `brush.selfed` | action bar | Selfed | Самоопыление |
| `brush.bud` | action bar | Bud-pollinated | Опылено в бутоне |
| `brush.wrong_species` | action bar | This pollen won't take here | Пыльца не подходит |
| `brush.not_flowering` | action bar | Not in flower | Растение не цветёт |
| `brush.expired` | action bar | The pollen has dried out | Пыльца засохла |
| `plant.needs_stock` | action bar | Won't take. Needs stock from wild plants. | Не взойдёт. Нужен посадочный материал диких растений. |
| `plant.needs_water` | action bar | Needs standing water | Нужна вода |
| `plant.needs_jungle_tree` | action bar | Plant it at the foot of a jungle tree | Сажать у ствола тропического дерева |
| `plant.scion_not_plantable` | action bar | A scion can't be planted; graft it onto a seedling | Привой не сажают — его прививают на сеянец |
| `graft.success` | action bar | The graft has taken | Привой прижился |
| `graft.failed` | action bar | The graft didn't take | Привой не прижился |
| `graft.too_old` | action bar | Too old for grafting; use a young seedling | Слишком старое для прививки — нужен молодой сеянец |
| `clone.taken` | action bar | Cutting taken | Черенок срезан |
| `clone.not_ready` | action bar | Not mature enough to take a cutting | Растение ещё не готово дать черенок |
| `clone.once_per_cycle` | action bar | Already taken this season | В этом цикле уже брали |
| `carve.too_small` | action bar | Too small to carve | Плод слишком мал, чтобы вырезать |
| `sugar.not_sweet` | action bar | Too little sugar in this to press | Сахара из этого не выжать |
| `lens.nothing` | action bar | Nothing to look at here | Здесь нечего рассматривать |
| `lens.header` | чат | %1$s · %2$s | %1$s · %2$s |
| `lens.inspected` | action bar | Seeds inspected | Семена осмотрены |
| `analyser.needs_bone_meal` | интерфейс | Needs bone meal | Нужна костная мука |
| `lab.needs_fuel` | интерфейс | Needs a gold nugget and bone meal | Нужны золотой самородок и костная мука |
| `analyser.done` | интерфейс | Analysis complete | Анализ готов |
| `breeder_note.learned` | action bar | Noted in your journal | Записано в журнал |
| `seedling.name_prompt` | журнал | Name this variety | Назовите этот сорт |
| `seedling.named` | action bar | The variety is now called "%s" | Теперь этот сорт зовётся «%s» |
| `pouch.full`, `pouch.not_seeds` | action bar | §7.3 | §7.3 |

`lens.header` — первая строка осмотра растения: «Дикая морковь · Цветение» или «Восточная морковь (жёлтая) · Урожайная зрелость». Остальные строки осмотра — те же, что тир 1 тултипа (§7.2).

### 9.7 Интерфейс журнала и стадии

| id (`wildroots.journal.ui.<id>`) | en_us | ru_ru |
|---|---|---|
| `title_page` (`wildroots.journal.title_page`) | Field Journal. Kept by %s | Полевой журнал. Владелец: %s |
| `section.seen` | What it looks like | Что видно |
| `section.found_at` | Where it was found | Где найдено |
| `section.goals` | Where to take it | Куда вести |
| `section.history` | A little history | Из истории |
| `locked` | ??? | ??? |
| `locked.cultivar` | Not yet bred | Ещё не выведен |
| `empty` | Nothing here yet | Пока пусто |
| `page` | %1$d / %2$d | %1$d / %2$d |
| `found_at.row` (`wildroots.journal.species.found_at`) | %1$s · %2$d, %3$d | %1$s · %2$d, %3$d |
| `found_at.feral` | %1$s · %2$d, %3$d · feral garden | %1$s · %2$d, %3$d · одичавший сад |
| `new` | New | Новое |
| `name_field` | Name: | Имя: |

**Уточнения стадии** `wildroots.tooltip.stage.harvest_ready.<species_id>`:

| Вид | en_us | ru_ru |
|---|---|---|
| `wild_carrot`, `sea_beet` | Root ready | Корнеплод созрел |
| `wild_cabbage` (сорт ступени 2A) | Head formed | Кочан сформирован |
| `wild_cabbage` (дикая, ландрас, брокколи) | Ready to pick | Готова к сбору |
| `wild_onion` **FD** | Bulb ready | Луковица созрела |
| `wild_potato` | Tubers ready | Клубни созрели |
| `wild_cane` | Stalks ready | Стебли созрели |
| `wild_cacao` | Pods ripe | Стручки созрели |
| `wild_raspberry` | Berries ripe | Ягоды созрели |
| `wild_apple` | Apples ripe | Яблоки созрели |

### 9.8 Род и число предметов (для слова класса)

Слово класса (§7.6) согласуется с предметом. Русскому коду нужен род каждого предмета с классом; он хранится в lang-файле ключом `wildroots.grammar.<namespace>.<path>` со значением `m`, `f`, `n` или `pl` `[ПРЕДЛОЖЕНИЕ]`. В en_us ключи не нужны (форма одна). Нет ключа — используется `m` и предупреждение в лог (для предметов из датапаков и чужих модов).

| Род | Предметы мода | Ванильные продукты |
|---|---|---|
| `m` | `teosinte_nut`, `wild_carrot_root`, `wild_melon_slice`, `wild_potato_tuber`, `wild_cane_stalk`, `wild_rice_grain`, `primitive_maize`, `nile_melon_slice`, `andean_potato`, `garden_cane_stalk`, `picnic_watermelon_slice`, `icebox_watermelon_slice`, `potato`, `noble_cane`, `aport_apple`, `cherry_tomato`, `landrace_onion`, `early_rice`, `tomato`, `onion`, `rice` | `bread`, `sugar`, `pumpkin_pie`, `cake`, `baked_potato`, `beetroot_soup`, `glistering_melon_slice` |
| `f` | `wild_gourd`, `wild_tomato_berry`, `wild_onion_bulb`, `wild_raspberry`, `eastern_carrot`, `leaf_cabbage`, `ancient_squash`, `roman_beet`, `garden_raspberry`, `wheat`, `corn`, `carrot`, `cabbage`, `broccoli`, `field_pumpkin`, `sugar_pumpkin`, `beetroot`, `sugar_beet`, `raspberry`, `antonovka_apple` | `paper` (класс в имя не идёт, §7.7), `golden_carrot` |
| `n` | `wild_grain`, `wild_apple`, `einkorn_grain`, `emmer_grain`, `silk_road_apple` | `cookie`, `rabbit_stew`, `golden_apple` |
| `pl` | `wild_cabbage_leaves`, `sea_beet_leaves`, `wild_cacao_beans`, `nacional_cacao_beans`, `criollo_beans`, `forastero_beans` | — |

### 9.9 Профессия селекционера (GDD §9.6)

| Ключ | en_us | ru_ru |
|---|---|---|
| `entity.minecraft.villager.wildroots.plant_breeder` `[ПРОВЕРИТЬ]` формат ключа модовой профессии | Plant Breeder | Селекционер |
| `block.wildroots.sorting_table` | Sorting Table | Сортировочный стол |

Уровни торговли — ванильные ключи `merchant.level.1`–`5` (Новичок, Ученик, Подмастерье, Эксперт, Мастер — по ванильному `ru_ru` `[ПРОВЕРИТЬ]`); отдельных имён уровней для одной профессии ванильный интерфейс не поддерживает, и мод их не вводит. Названия уровней в GDD §9.6 — рабочие, для таблицы торгов. Реплики селекционера живут на его товарах (§4.7) и в записках, потому что у ванильной сделки нет поля описания.

## 10. Передача и открытые вопросы

### 10.1 Что нужно от смежных ролей

**Game Designer:**
- Подтвердить, что слово класса дописывается к имени только в тултипе (§7.6), а не в имя предмета.
- Подтвердить исключение метки «Из дикой природы» для лотов одичавших насаждений (§6.1: «Из одичавшего сада»).
- Подтвердить ввод имени нового сорта яблони в журнале с запасным путём через наковальню (§3.2).
- Id блоков, которых нет в GDD: `gourd_fruit`, `watermelon_fruit`, `apple_trunk`, `apple_leaves`, `apple_fruit`, `cacao_pod` (§7.9).

**Software Architect / инженеры:**
- Род предмета для слова класса — ключи `wildroots.grammar.*` в lang-файле (§9.8); строка имени `name_grade` собирается в тултипе.
- Журнал в capability игрока; синхронизация id открытых записей, данных авто-страниц, записок селекционера и имён сортов яблони.
- 19 кастомных триггеров (§8.4); `vavilov` собирается из линий волны (§8.2); условие `forge:mod_loaded` для FD-достижений; `villager_trade` для странствующего торговца `[ПРОВЕРИТЬ]`.
- Шаблон записки селекционера: вариант по `hash(species, trait)`; имя категории биома — ключ `wildroots.biome_category.<id>` (§4.7).
- Ключ профессии `entity.minecraft.villager.wildroots.plant_breeder` `[ПРОВЕРИТЬ]`.
- Числа анализатора форматируются по локали в коде (§9.5).

**Economy Designer:** описания `desc` ландрасов (§7.6) сверить со свойствами еды, когда будут заданы `n_ref`/`s_ref`; список особых компонентов (`boosters`) — строки `wildroots.tooltip.booster.*` (§7.7) добавляются под каждый принятый компонент; цены и уровни торгов селекционера — если появятся новые товары, им нужна строка-реплика (§4.7).

**Level Designer:** тексты «Что видно» (§4.3) описывают тыкву у реки, арбуз на песке, картофель на горном лугу, свёклу у моря, тростник у воды, какао на стволе, малину на вырубках и яблоневый лес на склонах. Имена категорий биомов (§4.7) — по `03-worldgen.md` §2.3; при изменении списка категорий — сообщить. Читаемые признаки одичавшего насаждения (ряды, межа) совпадают с текстом подсказки `feral_planting`.

### 10.2 Открытые вопросы

1. **Плотность подсказок.** В §4.5 теперь около 80 записей. Каждая срабатывает один раз, тосты не чаще раза в 30 с, а записи поздних волн в ранней сборке не срабатывают. Нагрузку покажет плейтест онбординга (GDD §15); первыми вырезаются `select_quarter`, `left_on_stalk`, `bone_meal`, `perennial`.
2. **Голос селекционера на «ты»** (§4.7). Это отличает его от журнала и звучит как деревенский совет. Если тон покажется фамильярным — меняется на безличный без потери смысла.
3. **Совпадение имён с ванилью в `soft`.** В режиме `soft` в мире есть и ванильная «Морковь», и наша «Морковь · добротная». Слово класса их различает, но предмет без класса (наш, из сундука) выглядит как ванильный. Предлагаю в `soft` добавлять к нашему урожаю строку ступени всегда (уже так, §7.6) и не менять имена.
4. **Светящиеся ягоды** остаются без линии (GDD §7.13) — текстов для них нет. Если владелец попросит фэнтези-линию, ей понадобится отдельная оговорка в журнале: это единственная культура без реального прообраза.

### 10.3 Чек-лист для ревью текстов

- [ ] Каждая строка проходит тест «так сказал бы натуралист в своей записной книжке».
- [ ] Ни одной строки с родом в первом лице и ни одного `%d` перед словом во множественном числе.
- [ ] Числа механики не попали в тиры 0–1.
- [ ] Каждая историческая справка сверена вторым человеком по источникам §5.
- [ ] FD-строки не показываются без FD (журнал, достижения, тултипы).
- [ ] Ключи из §2 совпадают с id из JSON видов и сортов.
- [ ] Слово класса согласовано с родом и числом каждого предмета (§9.8).
- [ ] Строки линии появляются в lang-файле не позже её волны (§3.2).
- [ ] Запись `vavilov` — только об экспедициях и центрах происхождения (решение владельца №12).
