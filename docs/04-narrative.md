# Wild Roots (`wildroots`): нарратив, журнал, достижения, локализация

Фаза 1 · Narrative Designer · основан на `00-vision.md` и `01-gdd.md` v0.1.

## 0. Правила документа и допущения

| Версия | Дата | Автор | Изменения |
|---|---|---|---|
| 0.1 | 2026-10-28 | Narrative Designer | Первая редакция: журнал, исторические справки, достижения, названия, тултипы, ключи |

**Рабочие допущения** (владелец ещё не ответил; при другом ответе меняются только помеченные строки):

| # | Допущение | Связь с GDD | Что поменяется, если ответ другой |
|---|---|---|---|
| Д1 | Журнал выдаётся автоматически при первом сборе дикого растения | §10, §18 вопрос 4 | Запись `wildroots.journal.hint.welcome` переедет на событие «скрафтил журнал»; достижение `first_forager` перестанет упоминать журнал |
| Д2 | FD-объём полный: томат, лук, рис, капуста | §8, §18 вопрос 3 | Вычеркнуть строки с пометкой **FD** кроме капусты |
| Д3 | Финальный сорт отдаёт ванильный или FD-предмет без NBT; имя сорта живёт в лоте, маточнике, журнале, тосте и лупе | §5.4, §18 вопрос 2 | Если еда получит NBT — добавить строку тултипа «Сорт: %s» к ванильной еде (§7.6) |

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
7. **Термины одни и те же везде** (§9.1): «лот семян», «маточник», «ландрас», «сорт», «линия», «носитель». Синонимы не используем.
8. **Латинские названия** (*Daucus carota* и т. п.) — только в исторических справках журнала, и то по необходимости; в тултипах, достижениях и подсказках латыни нет.

### 1.3 Эталонные строки (по ним проверяют все остальные)

- «Один колос не рассыпался. Все рядом рассыпались, а этот — нет.» — увиденное и интрига без вывода.
- «Одинокое растение у дома дало чахлое потомство. Похоже, ему не хватило соседей.» — осторожный вывод.
- «Истинный дикий предок лука неизвестен. Ближайшие родственники растут в горах Средней Азии.» — честный факт.

## 2. Схема ключей локализации

Пять пространств из задания плюс три служебных. Служебные нужны потому, что вид и сорт — не предметы: финальная морковь отдаёт `minecraft:carrot`, а имя «Морковь» как сорт всё равно нужно журналу, тосту и лупе.

| Пространство | Шаблон | Что лежит | Пример |
|---|---|---|---|
| `item.wildroots.*` | `item.wildroots.<item_id>` | Имена предметов (ванильное правило Forge) | `item.wildroots.hand_lens` |
| `block.wildroots.*` | `block.wildroots.<block_id>` | Имена блоков | `block.wildroots.seed_analyzer` |
| `advancements.wildroots.*` | `advancements.wildroots.<adv_id>.title` / `.description` | Достижения | `advancements.wildroots.vavilov.title` |
| `wildroots.journal.*` | `wildroots.journal.<раздел>.<id>[.<часть>]` | Все тексты журнала | `wildroots.journal.hint.inbreeding` |
| `wildroots.tooltip.*` | `wildroots.tooltip.<предмет\|общее>.<строка>` | Строки тултипов, шкалы признаков | `wildroots.tooltip.scale.size.3` |
| `wildroots.species.*` | `wildroots.species.<species_id>` | Имя вида | `wildroots.species.wild_carrot` |
| `wildroots.cultivar.*` | `wildroots.cultivar.<cultivar_id>` и `.note` | Имя сорта и историческая справка (ключ `.note` совпадает с полем `historical_note` в GDD §6.3) | `wildroots.cultivar.eastern_carrot.note` |
| `wildroots.message.*` | `wildroots.message.<id>` | Сообщения в чат или action bar, тосты | `wildroots.message.brush.wrong_species` |

**Правила ключей.**
- Только `snake_case`, только ASCII, id совпадают с id JSON из GDD.
- Ключи для датапаков: если вид или сорт добавлен датапаком без своего ключа, показываем `id` пути с заглавной буквы и пишем предупреждение в лог `[ПРЕДЛОЖЕНИЕ]`. Автор датапака кладёт свои ключи в ресурс-пак по тем же шаблонам.
- Ключи сорта не зависят от предмета: `wildroots.cultivar.carrot` = «Морковь» существует, хотя предмета `item.wildroots.carrot` нет.
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
| — (вид ещё не открыт) | Unknown Wild Plant | Неизвестное дикое растение | ключ `wildroots.species.unknown` |

«Теосинте» в русском не склоняется и среднего рода: «теосинте выросло».

### 3.2 Сорта (`wildroots.cultivar.<id>`)

| Линия | Ступень | id (GDD) | en_us | ru_ru | Что отдаёт (еда) |
|---|---|---|---|---|---|
| Пшеница | 1A | `einkorn` | Einkorn | Однозернянка | `wildroots:einkorn` |
| Пшеница | 1B | `emmer` | Emmer | Полба | `wildroots:emmer` |
| Пшеница | 2 | `wheat` | Wheat | Пшеница | `minecraft:wheat` |
| Кукуруза | 1 | `primitive_maize` | Primitive Maize | Примитивная кукуруза | `wildroots:primitive_maize` |
| Кукуруза | 2 | `maize` | Maize | Кукуруза | `wildroots:corn` |
| Морковь | 1 | `eastern_carrot` | Eastern Carrot | Восточная морковь | `wildroots:eastern_carrot` |
| Морковь | 2 | `carrot` | Carrot | Морковь | `minecraft:carrot` |
| Капуста | 1 | `leaf_cabbage` | Leaf Cabbage | Листовая капуста | `wildroots:leaf_cabbage` |
| Капуста | 2A | `head_cabbage` | Head Cabbage | Кочанная капуста | `wildroots:cabbage`; **FD** — `farmersdelight:cabbage` |
| Капуста | 2B | `broccoli` | Broccoli | Брокколи | `wildroots:broccoli` |
| Томат **FD** | 1 | `cherry_tomato` | Cherry Tomato | Черри-томат | `wildroots:cherry_tomato` |
| Томат **FD** | 2 | `tomato` | Tomato | Томат | `farmersdelight:tomato` |
| Лук **FD** | 1 | `landrace_onion` | Landrace Onion | Местный лук | `wildroots:landrace_onion` |
| Лук **FD** | 2 | `onion` | Onion | Лук | `farmersdelight:onion` |
| Рис **FD** | 1 | `early_rice` | Early Rice | Ранний рис | `wildroots:early_rice` |
| Рис **FD** | 2 | `rice` | Rice | Рис | `farmersdelight:rice` |

Id еды ландрасов совпадают с id сортов, как в примере GDD §6.3 (`"food": "wildroots:eastern_carrot"`). Для кукурузы GDD задаёт отдельный id предмета `corn`, для кочанной — `cabbage`; имена предметов — в §7.

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

Формат в тултипе: `%1$s (%2$s)` → «Восточная морковь (жёлтая)», «Eastern Carrot (yellow)».

### 3.3 Ступени селекции (`wildroots.tooltip.tier.<n>`)

| Ключ | en_us | ru_ru | Где видно |
|---|---|---|---|
| `wildroots.tooltip.tier.0` | Wild ancestor | Дикий предок | Тултип лота и маточника, журнал |
| `wildroots.tooltip.tier.1` | Landrace | Ландрас | То же; еда ландраса |
| `wildroots.tooltip.tier.2` | Cultivar | Сорт | То же |

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
| `mother_plant` | посаженный маточник до цветения | Mother plant | Маточник |
| `woody` | двулетник, оставленный на корню после зрелости | Going woody | Одревесневает |

`harvest_ready` у разных видов уточняется через `wildroots.tooltip.stage.harvest_ready.<species_id>`: «корнеплод созрел» (морковь), «кочан сформирован» (капуста), «луковица созрела» (лук). Если ключа нет, берётся общий.

## 4. Полевой журнал

### 4.1 Как устроен

**Purpose.** Единственный учебник мода, который не выглядит учебником. Он записывает то, что игрок уже увидел, и добавляет один шаг вперёд.
**Player fantasy.** «Это мои записи. Тут всё, что я нашёл и вывел».

- **Предмет** `wildroots:field_journal` — только окно просмотра. Записи хранятся **на сервере, у игрока** (capability игрока), а не в NBT книги `[ПРЕДЛОЖЕНИЕ]`. Потерял журнал или умер — новый журнал (рецепт GDD §10) открывает те же записи. В мультиплеере у каждого свой журнал.
- **Выдача** — автоматически при первом сборе дикого растения (Д1). Конфиг `giveJournalOnFirstHarvest = false` — запись `welcome` всё равно создаётся и ждёт, пока игрок скрафтит книгу.
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
| `carrot` | Корень крупный, сладкий, без горечи. И оранжевый — без следа фиолетового. | A large, sweet root with no bitterness. And orange, without a trace of purple. |
| `leaf_cabbage` | Листья не горькие и не мельче средних. Урожай не скудный. | Leaves that aren't bitter, at least medium. A fair harvest. |
| `head_cabbage` | Верхушка сворачивается в плотный кочан, листья крупные. Цветонос не торопится. Без горечи. | The top folds into a firm head, the leaves are large. No rush to bolt. No bitterness. |
| `broccoli` | Плотная шапка соцветий на толстом стебле. Кочан не завивается. Без горечи. | A dense crown of flower buds on a thick stem. No head forms. No bitterness. |
| `cherry_tomato` **FD** | Ягоды не мельче средних и не ядовиты. | Berries at least medium, and not poisonous. |
| `tomato` **FD** | Плоды крупные, урожай щедрый, вкус приятный или лучше. | Large fruit, a generous harvest, a pleasant taste or better. |
| `landrace_onion` **FD** | Луковица не мельче средней и не жгучая до слёз. | A bulb at least medium, and not sharp enough to sting. |
| `onion` **FD** | Луковица крупная, вкус приятный или лучше, урожай не скудный. | A large bulb, a pleasant taste or better, a fair harvest. |
| `early_rice` **FD** | Метёлка не осыпается. Зерно не мелкое. | The panicle holds its grain. The grain isn't small. |
| `rice` **FD** | Урожай щедрый, зерно крупнее среднего и белое, без красной оболочки. Семена всходят дружно. | A generous harvest, grain above medium and white, without the red skin. Seeds that sprout together. |

### 4.5 Заметки: подсказки и триггеры

Ключ: `wildroots.journal.hint.<id>`; заголовок записи — `wildroots.journal.hint.<id>.title`. Все триггеры — **кастомные серверные события** мода (в столбце указано событие и условие). «Своё растение» — посаженное этим игроком (UUID сажавшего хранится в BlockEntity `[ПРЕДЛОЖЕНИЕ]`).

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
| `stagnation` | Застой / Stalled | GDD §14: три поколения подряд лучший `G` линии по признакам целевого профиля не растёт | Линия %s застыла: три поколения без улучшений. Похоже, изменчивость кончилась. Нужен свежий материал: из другого биома, из деревни или из древнего тайника. | Line %s has stalled: three generations without progress. The variety seems used up. Fresh stock is needed, from another biome, a village, or an old cache. |
| `missing_allele` | Пропажа / Gone Missing | GDD §14: после анализатора в линии нет ни одного носителя аллеля, нужного следующему сорту | В линии %s, похоже, не осталось ни одного носителя нужного признака. Вернуть его можно только скрещиванием с теми, у кого он есть. | Line %s seems to have no carriers of the trait it needs. The only way back is crossing with plants that have it. |
| `second_biome` | Другие места / Other Places | Открытие патча вида во второй категории биома (GDD §3.2) | Ещё одни заросли, но в другом месте, и растения здесь сильны в другом. Если свести их вместе, выйдет больше, чем у каждого по отдельности. | Another patch, somewhere else, and these plants are strong in other ways. Brought together, they'll give more than either alone. |
| `village_seeds` | Деревенские семена / Village Seed | Первое получение лота деревенского ландраса | Деревенские семена. Кто-то уже прошёл полпути: горечи нет, признаки одомашнивания есть. До сорта путь ещё неблизкий. | Village seed. Someone has walked half the road already: no bitterness, the signs of taming are there. The cultivar is still a long way off. |
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

### 4.6 Страницы инструментов (`wildroots.journal.tool.<id>`)

Открываются, когда предмет впервые попадает в инвентарь.

| id | ru_ru | en_us |
|---|---|---|
| `field_journal` | Здесь записано всё найденное и выведенное. Потерянный журнал можно сделать заново — записи никуда не денутся. | Everything found and bred is written here. A lost journal can be made again; the notes won't go anywhere. |
| `hand_lens` | Лупа показывает то, что глазом не оценить: вкус, выносливость, как прошло опыление, не слабеет ли растение. Работает и на растении, и на пакетике семян. | The lens shows what the eye can't judge: taste, hardiness, how pollination went, whether a plant is ailing. Works on plants and on seed packets alike. |
| `pollination_brush` | Кисть набирает пыльцу с цветка и переносит на другой цветок того же вида. Пыльца живёт недолго. Кисть по тому же цветку — самоопыление. | The brush picks up pollen from one flower and carries it to another of the same kind. Pollen doesn't last long. Brushing the same flower selfs it. |
| `seed_bank` | Здесь живут лоты и маточники. Их можно разложить по порядку и дать линиям имена. | Seed lots and mother plants live here. They can be sorted and their lines named. |
| `seed_analyzer` | Анализатор отвечает на главный вопрос: это наследственность или везение? Показывает, что растение передаст потомству, и находит носителей скрытых признаков. Семена не портит. | The analyser answers the big question: heredity or luck? It shows what a plant will pass on and finds carriers of hidden traits. The seed comes out unharmed. |
| `genotype_lab` | Лаборатория читает растение целиком и предсказывает, что выйдет из скрещивания двух лотов. | The lab reads a plant in full and predicts what a cross of two lots will give. |

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

**Числа в справках** — это исторические даты, а не игровые значения. Правило «без чисел» (§1.2) касается механики.

**Источники для сверки (для редактора, в игру не идут):** Zohary, Hopf, Weiss, *Domestication of Plants in the Old World* (4-е изд.); Heun et al. 1997 (однозернянка, Карачадаг); Matsuoka et al. 2002 (кукуруза, Бальсас); Piperno & Flannery 2001 (Гила-Накиц); Iorizzo et al. 2013 и Banga 1957/1963 (морковь); Razifard et al. 2020 (томат); Fuller et al. 2009 (Тяньлошань); Sweeney et al. 2006 (`Rc`); Jones & Clarke 1943 (мужская стерильность лука); Herodotus II.125; Cato, *De Agri Cultura* 156. Перед релизом факты сверяет второй человек `[ПРОВЕРИТЬ]`.

## 6. Метки «Из дикой природы» и имена линий

### 6.1 Метка «Из дикой природы»

**Зачем.** Мягкая награда за полный путь (GDD §9.5): «эту морковь я вывел из сорняка», а не «купил у фермера и довёл».

**Правило наследования — по материнской линии** `[ПРЕДЛОЖЕНИЕ для Game Designer]`. Флаг `from_wild` хранится в лоте и маточнике и переходит потомку **от матери**; отец на него не влияет.
- **Получают флаг:** лоты из травы и папоротника, с диких патчей, из сундуков структур, от странствующего торговца (это дикие предки).
- **Не получают:** лоты деревенских ландрасов.
- **Почему по матери:** GDD сам поощряет подмешивать деревенский ландрас как отца (§12.3, §7.3), и метка не должна за это наказывать. Реальная аналогия: хлоропласты у большинства растений наследуются по материнской линии. Если игрок сделал деревенское растение **матерью** — линия честно «пришла из деревни».
- **Где видна:** строка тултипа лота и маточника (§7), страница сорта и линии в журнале, условие достижения `from_the_wild` (§8).

| Ключ | en_us | ru_ru |
|---|---|---|
| `wildroots.journal.mark.from_the_wild` | From the Wild | Из дикой природы |
| `wildroots.tooltip.mark.from_the_wild` | From the wild | Из дикой природы |
| `wildroots.tooltip.mark.from_village` | Village stock | Деревенский материал |
| `wildroots.journal.mark.from_village` | From village stock | Из деревенского материала |

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

Тултип — это та же лестница информации, что в GDD §10. Уровень хранится в самом лоте или маточнике.

| Уровень | Когда | Что в тултипе | Числа |
|---|---|---|---|
| 0 — «на глаз» | Всегда | Имя, ступень, метка, линия, то, что видно при сборе: размер на глаз (3 слова), цвет, осыпание, число зёрен в колоске, форма куста, чахлость матери | Нет |
| 1 — «под лупой» | После осмотра лупой (флаг `inspected` в NBT лота / маточника) `[ПРЕДЛОЖЕНИЕ]` | Шкалы признаков матери словами (5 ступеней, §9.2), горечь, покой, как прошло опыление, «похоже на сорт» | Нет |
| 2 — «анализ» | После анализатора (флаг `analyzed`) | Наследственность `G` с шагом 0.05, носители, витальность, пороги известных сортов | Да |
| 3 — «лаборатория» | Только в интерфейсе лаборатории, не в тултипе | — | Да |

**Почему флаг, а не «лупа в руке».** Лупа неразрушающая и бесплатная (GDD §10), поэтому один осмотр открывает тир 1 навсегда. Игрок не держит лупу в другой руке, чтобы прочитать свой же семенной банк.
**Длина.** Без Shift показываются строки уровня 0 и одна строка-подсказка; строки уровней 1–2 — с зажатым Shift `[ПРЕДЛОЖЕНИЕ]`.

### 7.2 Лот семян `wildroots:seed_lot`

**Имя** (динамическое):

| Ключ | en_us | ru_ru | Когда |
|---|---|---|---|
| `item.wildroots.seed_lot` | Seeds | Семена | Запасное имя без NBT |
| `item.wildroots.seed_lot.named` | %s Seeds | Семена: %s | Вид открыт или лот сортовой |
| `item.wildroots.seed_lot.unknown` | Wild Seeds | Дикие семена | Вид ещё не открыт игроком (GDD §11.3, мин. 0–2) |

**Строки тултипа** (по порядку):

| # | Уровень | Ключ | en_us | ru_ru | Условие |
|---|---|---|---|---|---|
| 1 | 0 | `wildroots.tooltip.tier.<n>` + метка | Wild ancestor · From the wild | Дикий предок · Из дикой природы | Всегда; метка — если `from_wild` |
| 2 | 0 | `wildroots.tooltip.line` | Line "%1$s", gen. %2$d | Линия «%1$s», поколение: %2$d | Есть имя линии; иначе `wildroots.tooltip.generation` начиная со 2-го поколения |
| 3 | 0 | `wildroots.tooltip.unknown_species` | Plant it and see what comes up. | Посадите — и станет ясно, что вырастет. | Вид не открыт |
| 4 | 0 | `wildroots.tooltip.mother_eye` | Mother, by eye: %s | Мать на глаз: %s | Вид открыт. `%s` — список через запятую из слов §9.3 (размер, цвет, осыпание, форма) |
| 5 | 0 | `wildroots.tooltip.mother_ailing` | The mother looked sickly | Мать выглядела чахлой | Витальность матери ниже порога чахлости |
| 6 | 0 | `wildroots.tooltip.unattended` | Gathered unattended | Собраны без присмотра | Лот без отцов из-за поршня или воды |
| 7 | 0 | `wildroots.tooltip.rare_allele` | Kept for a reason | Хранили не просто так | Лот из сундука с редким аллелем |
| 8 | 0 | `wildroots.tooltip.far_lands` | From distant lands | Из далёких земель | Лот странствующего торговца |
| 9 | 0 | `wildroots.tooltip.hint.inspect` | Inspect with a hand lens to learn more | Осмотрите лупой, чтобы узнать больше | Не осмотрен и у игрока открыта страница лупы |
| 10 | 0 | `wildroots.tooltip.hint.shift` | Hold Shift for details | Shift — подробнее | Осмотрен или проанализирован |
| 11 | 1 | `wildroots.tooltip.trait` | %1$s: %2$s | %1$s: %2$s | По строке на признак вида: имя признака (§9.2) и слово шкалы |
| 12 | 1 | `wildroots.tooltip.pollination.*` | см. §9.4 | см. §9.4 | Как опылялась мать |
| 13 | 1 | `wildroots.tooltip.father_named` | Father: hand-pollinated from "%s" | Отец: ручное опыление от «%s» | Кисть, у отца есть имя линии (GDD §4.4) |
| 14 | 1 | `wildroots.tooltip.looks_like` | Looks like %s. Test the offspring. | Похоже на «%s». Проверьте потомство. | Фенотип проходит пороги сорта, генотип — нет |
| 15 | 2 | `wildroots.tooltip.analysis.*` | см. §9.5 | см. §9.5 | После анализатора |

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

### 7.3 Маточники

Маточник — само растение с геномом (GDD §5.2). Тултип как у лота, но строка 4 говорит о самом растении: `wildroots.tooltip.self_eye` = «На глаз: %s» / "By eye: %s".

| id | Статус id | en_us | ru_ru | Имя с NBT (`item.wildroots.<id>.named`) | Подсказка (`wildroots.tooltip.mother.hint`) |
|---|---|---|---|---|---|
| `mother_root` | GDD §5.2 | Mother Root | Маточный корень | %s (mother root) / %s (маточник) | Plant on farmland to flower and set seed. Or eat it. / Посаженный на пашню, зацветёт и даст семена. Или его можно съесть. |
| `mother_stump` | GDD §5.2 | Mother Stump | Маточная кочерыга | %s (mother stump) / %s (маточник) | то же |
| `mother_bulb` **FD** | `[ПРЕДЛОЖЕНИЕ]` (GDD §8.2 упоминает маточник-луковицу, но id не задаёт) | Mother Bulb | Маточная луковица | %s (mother bulb) / %s (маточник) | то же |

### 7.4 Дикая еда

Тултип: строка ступени «Дикий предок» и одна строка описания (серая, курсив) — ключ `item.wildroots.<id>.desc`. Еда без генома (GDD §5.2), поэтому описание говорит «часто», а не «этот». Id предметов в GDD не заданы — все `[ПРЕДЛОЖЕНИЕ]` для Software Architect.

| id | en_us | ru_ru | desc en_us | desc ru_ru |
|---|---|---|---|---|
| `wild_grain` | Wild Grain | Дикое зерно | Small and tightly hulled. | Мелкое, в жёстких плёнках. |
| `teosinte_nut` | Teosinte Nut | Орех теосинте | A kernel in a stony shell. Teeth won't crack it. | Зерно в каменной скорлупе. Зубы не берут. |
| `wild_carrot_root` | Wild Carrot Root | Корень дикой моркови | Thin, white, smells of carrot. Often bitter. | Тонкий, белый, пахнет морковью. Часто горчит. |
| `wild_cabbage_leaves` | Wild Cabbage Leaves | Листья дикой капусты | Tough and blue-grey. Nearly always bitter. | Жёсткие, сизые. Почти всегда горькие. |
| `wild_tomato_berry` **FD** | Wild Tomato Berry | Ягода дикого томата | Pea-sized. More often poisonous than not. | С горошину. Чаще ядовита, чем нет. |
| `wild_onion_bulb` **FD** | Wild Onion Bulb | Луковичка дикого лука | Tiny, and sharp enough to make eyes water. | Крошечная и жгучая до слёз. |
| `wild_rice_grain` **FD** | Wild Rice | Дикий рис | Reddish grain with a long awn. | Красноватое зерно с длинной остью. |

Слова «часто», «почти всегда», «чаще… чем нет» соответствуют долям горьких растений в диких патчах по GDD §3.6 (горечь доминантна: морковь ≈ 84 %, капуста ≈ 94 %, томат ≈ 91 %, лук ≈ 88 %). При изменении частот — пересмотреть слова.

### 7.5 Еда ландрасов и сортов мода

Тултип: строка ступени и строка описания. Цветовой вариант (§3.2) добавляется к имени, если у предмета есть вариант текстуры.

| id | Ступень | en_us | ru_ru | desc en_us | desc ru_ru |
|---|---|---|---|---|---|
| `einkorn` | 1 | Einkorn Grain | Зерно однозернянки | Hardy grain of cold fields. | Неприхотливое зерно холодных полей. |
| `emmer` | 1 | Emmer Grain | Зерно полбы | Hulled: first the mortar, then the pot. | Плёнчатое: сначала в ступу, потом в котёл. |
| `primitive_maize` | 1 | Primitive Maize Cob | Початок примитивной кукурузы | Smaller than a palm, but the kernels are bare. | Меньше ладони, зато зерно открытое. |
| `eastern_carrot` | 1 | Eastern Carrot | Восточная морковь | Yellow or purple, and a little sweet. | Жёлтая или фиолетовая, сладковатая. |
| `leaf_cabbage` | 1 | Leaf Cabbage | Листовая капуста | Big soft leaves, no bitterness. | Крупные мягкие листья, без горечи. |
| `cherry_tomato` **FD** | 1 | Cherry Tomato | Черри-томат | Small, sweet, and safe. | Маленький, сладкий и уже не опасный. |
| `landrace_onion` **FD** | 1 | Landrace Onion | Местный лук | Still sharp, but no longer to tears. | Ещё острый, но уже не до слёз. |
| `early_rice` **FD** | 1 | Early Rice | Ранний рис | Holds its grain. The skin is still red. | Держит зерно. Оболочка ещё красная. |
| `corn` | 2 | Corn | Кукуруза | — | — |
| `cabbage` | 2 | Cabbage | Капуста | — | — |
| `broccoli` | 2 | Broccoli | Брокколи | — | — |

У финальных предметов мода описания нет: они должны выглядеть как обычная еда, а вся история живёт в журнале. Имена `corn` и `cabbage` совпадают с привычными названиями (FD называет свою капусту «Капуста» `[ПРОВЕРИТЬ]` по `ru_ru` FD), сортовые имена «Кукуруза» / «Кочанная капуста» — в журнале и лоте.

### 7.6 Ванильные и FD-предметы финальных сортов

`minecraft:wheat`, `minecraft:carrot`, `farmersdelight:cabbage|tomato|onion|rice` **не получают** строк тултипа (Д3: еда без NBT, имя сорта по стопке не определить). Если владелец решит иначе — одна строка `wildroots.tooltip.cultivar` = «Сорт: %s» / "Cultivar: %s".

### 7.7 Инструменты и блоки

| id | Тип | en_us | ru_ru | Строки тултипа (`item.wildroots.<id>.desc` или `block.wildroots.<id>.desc`) en / ru |
|---|---|---|---|---|
| `field_journal` | предмет | Field Journal | Полевой журнал | A naturalist's notes. Use to open. / Записки натуралиста. ПКМ — открыть. |
| `hand_lens` | предмет | Hand Lens | Лупа | Use on a plant, or with seeds in the other hand. / ПКМ по растению или по семенам в другой руке. |
| `pollination_brush` | предмет | Pollination Brush | Кисть опыления | Pick up pollen from one flower, carry it to another. / Наберите пыльцу с одного цветка и перенесите на другой. |
| `seed_bank` | блок | Seed Bank | Семенной банк | Keeps seed lots and mother plants. / Хранит лоты семян и маточники. |
| `seed_analyzer` | блок | Seed Analyser | Анализатор семян | Heredity or luck? Uses bone meal. / Наследственность или везение? Расходует костную муку. |
| `genotype_lab` | блок | Genotype Lab | Лаборатория генотипа | Reads a plant in full and predicts crosses. Uses gold and bone meal. / Читает растение целиком и предсказывает скрещивания. Расходует золото и костную муку. |

**Состояние кисти** (строка тултипа, ключи `wildroots.tooltip.brush.*`):

| Ключ | en_us | ru_ru |
|---|---|---|
| `wildroots.tooltip.brush.empty` | No pollen | Пыльцы нет |
| `wildroots.tooltip.brush.loaded` | Pollen: %s | Пыльца: %s |
| `wildroots.tooltip.brush.loaded_named` | Pollen: %1$s, line "%2$s" | Пыльца: %1$s, линия «%2$s» |
| `wildroots.tooltip.brush.fading` | The pollen is drying out | Пыльца подсыхает |

`fading` — последняя минута жизни пыльцы (GDD §4.4: 5 мин `[PLACEHOLDER]`). En-вариант «Analyser» — британское написание в тон «натуралисту»; если проект предпочитает американское — «Analyzer» во всех строках сразу.

### 7.8 Блоки растений

Один блок на вид `[ПРЕДЛОЖЕНИЕ]` (сорт — состояние BlockEntity, а не отдельный блок). Имена блоков — те же строки, что `wildroots.species.<id>` (§3.1): `block.wildroots.wild_carrot` = «Дикая морковь». Растение-сорт в подсказках вроде Jade показывает имя сорта через `wildroots.cultivar.<id>` `[ПРОВЕРИТЬ]` API Jade/WTHIT. Деревенская декоративная грядка (GDD §9.5) — те же имена с ключами `block.wildroots.village_<species_id>` и той же строкой.

## 8. Дерево достижений

Отдельная вкладка `wildroots:root`, фон `minecraft:textures/block/rooted_dirt.png` `[ПРЕДЛОЖЕНИЕ]`. Тип (frame): **task** — шаг обучения, **goal** — веха линии, **challenge** — долгая цель. **V** — ванильный триггер, **К** — кастомный триггер мода (список и поля — §8.4). FD-достижения грузятся только при установленном FD (условие `forge:mod_loaded` в JSON достижения `[ПРОВЕРИТЬ]` формат условий для advancements в Forge 47).

### 8.1 Карта дерева

```
root (Дикие корни)
└─ first_forager (Первый сборщик)
   ├─ bitter_lesson
   ├─ kept_for_a_reason
   ├─ from_afar
   ├─ closer_look ── by_hand
   └─ seedbed (Каждая грядка — эксперимент)
      ├─ save_the_best
      ├─ bees_on_payroll
      ├─ too_close*            (* скрытое)
      ├─ cabbage_says_no*
      ├─ odd_one_out*
      ├─ seed_keepers
      ├─ named_line
      └─ first_landrace (Местный сорт)
         ├─ einkorn
         ├─ emmer ── wheat
         ├─ primitive_maize ── maize
         │                 └─ hybrid_vigor*
         ├─ eastern_carrot ── carrot
         ├─ leaf_cabbage ─┬─ head_cabbage ── two_roads
         │                └─ broccoli
         ├─ cherry_tomato ── tomato ── garden_to_kitchen   (FD)
         ├─ landrace_onion ── onion                         (FD)
         ├─ early_rice ── rice                              (FD)
         ├─ seed_analyzer ─┬─ carrier
         │                 └─ genotype_lab
         └─ from_the_wild ── vavilov (Вавилов был бы горд)
```

### 8.2 Структура

| id (`wildroots:<id>`) | Родитель | Триггер | Условие | Иконка | Тип | Скрыто |
|---|---|---|---|---|---|---|
| `root` | — | V `minecraft:inventory_changed` **или** К `wildroots:harvest_wild_plant` | Любой `wildroots:seed_lot` в инвентаре / любой сбор дикого растения | `wildroots:wild_carrot_root` | task | нет |
| `first_forager` | `root` | К `harvest_wild_plant` | Любой дикий вид | `wildroots:field_journal` | task | нет |
| `bitter_lesson` | `first_forager` | К `eat_bitter` | Любая горькая или ядовитая еда | `wildroots:wild_carrot_root` | task | нет |
| `kept_for_a_reason` | `first_forager` | V `minecraft:inventory_changed` | `seed_lot` с NBT-флагом редкого аллеля `[ПРОВЕРИТЬ]` имя тега | `wildroots:seed_lot` | task | нет |
| `from_afar` | `first_forager` | V `minecraft:villager_trade` | Предмет `seed_lot`, торговец — странствующий `[ПРОВЕРИТЬ]`, срабатывает ли триггер на `wandering_trader` | `minecraft:lead` | task | нет |
| `closer_look` | `first_forager` | V `minecraft:inventory_changed` | `wildroots:hand_lens` | `wildroots:hand_lens` | task | нет |
| `by_hand` | `closer_look` | К `hand_pollinate` | `self: false` | `wildroots:pollination_brush` | task | нет |
| `seedbed` | `first_forager` | К `plant_population` | `min_count: 16`, `radius: 8`, один вид, свои растения | `wildroots:seed_lot` | task | нет |
| `save_the_best` | `seedbed` | К `plant_mother` | Любой маточник | `wildroots:mother_root` | task | нет |
| `bees_on_payroll` | `seedbed` | К `bee_pollinated` | Своё растение | `minecraft:bee_nest` | task | нет |
| `too_close` | `seedbed` | К `plant_ailing` | Своё растение созрело чахлым (GDD §3.7) | `minecraft:dead_bush` | task | да |
| `cabbage_says_no` | `seedbed` | К `pollen_rejected` | Своё растение | `wildroots:wild_cabbage_leaves` | task | да |
| `odd_one_out` | `seedbed` | К `mutation_found` | Видимая мутация качественного признака | `wildroots:seed_lot` | task | да |
| `seed_keepers` | `seedbed` | V `minecraft:inventory_changed` | `wildroots:seed_bank` | `wildroots:seed_bank` | task | нет |
| `named_line` | `seedbed` | К `line_generation` | `named: true`, `min_generation: 5` | `minecraft:name_tag` | task | нет |
| `first_landrace` | `seedbed` | К `cultivar_bred` | `tier: 1` | `wildroots:eastern_carrot` | goal | нет |
| `einkorn` | `first_landrace` | К `cultivar_bred` | `cultivar: wildroots:einkorn` | `wildroots:einkorn` | task | нет |
| `emmer` | `first_landrace` | К `cultivar_bred` | `cultivar: wildroots:emmer` | `wildroots:emmer` | task | нет |
| `wheat` | `emmer` | К `cultivar_bred` | `cultivar: wildroots:wheat` | `minecraft:wheat` | goal | нет |
| `primitive_maize` | `first_landrace` | К `cultivar_bred` | `cultivar: wildroots:primitive_maize` | `wildroots:primitive_maize` | task | нет |
| `maize` | `primitive_maize` | К `cultivar_bred` | `cultivar: wildroots:maize` | `wildroots:corn` | goal | нет |
| `hybrid_vigor` | `primitive_maize` | К `hybrid_vigor` | Любой вид | `wildroots:corn` | goal | да |
| `eastern_carrot` | `first_landrace` | К `cultivar_bred` | `cultivar: wildroots:eastern_carrot` | `wildroots:eastern_carrot` | task | нет |
| `carrot` | `eastern_carrot` | К `cultivar_bred` | `cultivar: wildroots:carrot` | `minecraft:carrot` | goal | нет |
| `leaf_cabbage` | `first_landrace` | К `cultivar_bred` | `cultivar: wildroots:leaf_cabbage` | `wildroots:leaf_cabbage` | task | нет |
| `head_cabbage` | `leaf_cabbage` | К `cultivar_bred` | `cultivar: wildroots:head_cabbage` | `wildroots:cabbage` | goal | нет |
| `broccoli` | `leaf_cabbage` | К `cultivar_bred` | `cultivar: wildroots:broccoli` | `wildroots:broccoli` | goal | нет |
| `two_roads` | `head_cabbage` | К `cultivar_bred` ×2 (оба требования) | `head_cabbage` **и** `broccoli` | `wildroots:broccoli` | challenge | нет |
| `cherry_tomato` **FD** | `first_landrace` | К `cultivar_bred` | `cultivar: wildroots:cherry_tomato` | `wildroots:cherry_tomato` | task | нет |
| `tomato` **FD** | `cherry_tomato` | К `cultivar_bred` | `cultivar: wildroots:tomato` | `farmersdelight:tomato` | goal | нет |
| `landrace_onion` **FD** | `first_landrace` | К `cultivar_bred` | `cultivar: wildroots:landrace_onion` | `wildroots:landrace_onion` | task | нет |
| `onion` **FD** | `landrace_onion` | К `cultivar_bred` | `cultivar: wildroots:onion` | `farmersdelight:onion` | goal | нет |
| `early_rice` **FD** | `first_landrace` | К `cultivar_bred` | `cultivar: wildroots:early_rice` | `wildroots:early_rice` | task | нет |
| `rice` **FD** | `early_rice` | К `cultivar_bred` | `cultivar: wildroots:rice` | `farmersdelight:rice` | goal | нет |
| `garden_to_kitchen` **FD** | `tomato` | К `cultivar_bred` ×4 | `tomato`, `onion`, `rice`, `head_cabbage` | `farmersdelight:cooking_pot` `[ПРОВЕРИТЬ]` id | challenge | нет |
| `seed_analyzer` | `first_landrace` | V `minecraft:inventory_changed` | `wildroots:seed_analyzer` | `wildroots:seed_analyzer` | task | нет |
| `carrier` | `seed_analyzer` | К `analyze_lot` | `carrier: true` | `wildroots:seed_lot` | task | нет |
| `genotype_lab` | `seed_analyzer` | V `minecraft:inventory_changed` | `wildroots:genotype_lab` | `wildroots:genotype_lab` | goal | нет |
| `from_the_wild` | `first_landrace` | К `cultivar_bred` | `tier: 2`, `from_wild: true` | `wildroots:wild_carrot_root` | goal | нет |
| `vavilov` | `from_the_wild` | К `cultivar_bred` ×5 | `wheat`, `maize`, `carrot`, `head_cabbage`, `broccoli` | `wildroots:field_journal` | challenge | нет |

**Почему `vavilov` не требует FD-сортов и метки «Из дикой природы».** Это цель MVP, достижимая в любой сборке; FD-линии закрывает свой челлендж `garden_to_kitchen`. Метка — отдельная веха `from_the_wild`, чтобы не наказывать игрока, взявшего деревенский материал.
**Награды** (`rewards`): только у challenge — 100 опыта, как у ванильных испытаний `[ПРЕДЛОЖЕНИЕ]`; предметов не даём, баланс у Economy Designer.

### 8.3 Тексты (`advancements.wildroots.<id>.title` / `.description`)

| id | en_us title | en_us description | ru_ru title | ru_ru description |
|---|---|---|---|---|
| `root` | Wild Roots | A world where nothing has been tamed yet | Дикие корни | Мир, где ещё ничего не приручено |
| `first_forager` | First Forager | Harvest a wild plant. The journal takes it from there | Первый сборщик | Соберите дикое растение. Дальше подскажет журнал |
| `bitter_lesson` | A Bitter Lesson | Eat something bitter. Remember which plant it came from | Горький урок | Съешьте что-нибудь горькое. И запомните, с какого растения |
| `kept_for_a_reason` | Kept for a Reason | Find seeds with a rare trait in an old chest | Хранили не просто так | Найдите в старом сундуке семена с редким признаком |
| `from_afar` | From Distant Lands | Buy seeds from a wandering trader | Из далёких земель | Купите семена у странствующего торговца |
| `closer_look` | A Closer Look | Make a hand lens | Присмотритесь | Сделайте лупу |
| `by_hand` | By Hand | Cross two plants with a pollination brush | Своими руками | Скрестите два растения кистью |
| `seedbed` | Every Bed an Experiment | Plant at least 16 of one kind side by side | Каждая грядка — эксперимент | Посадите рядом не меньше 16 растений одного вида |
| `save_the_best` | Save the Best | Plant a mother plant and let it go to seed | Лучших — на семена | Посадите маточник, чтобы он дал семена |
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
| `primitive_maize` | Cave Cob | Breed primitive maize | Початок из пещеры | Выведите примитивную кукурузу |
| `maize` | Nine Thousand Years by Sundown | Breed maize from teosinte | Девять тысяч лет до заката | Выведите кукурузу из теосинте |
| `hybrid_vigor` | Hybrid Vigour | Cross two ailing lines and get sturdy offspring | Сила гибрида | Скрестите две чахлые линии и получите крепкое потомство |
| `eastern_carrot` | Out of the East | Breed the eastern carrot | С Востока | Выведите восточную морковь |
| `carrot` | The Carrot You Know | Breed the orange carrot | Та самая морковь | Выведите оранжевую морковь |
| `leaf_cabbage` | Cato's Cabbage | Breed leaf cabbage | Капуста Катона | Выведите листовую капусту |
| `head_cabbage` | Heads, You Win | Breed head cabbage | Сто одёжек | Выведите кочанную капусту |
| `broccoli` | Italian Asparagus | Breed broccoli | Итальянская спаржа | Выведите брокколи |
| `two_roads` | One Species, Two Roads | Breed both head cabbage and broccoli | Один вид — две дороги | Выведите и кочанную капусту, и брокколи |
| `cherry_tomato` **FD** | A Berry Grown Up | Breed the cherry tomato | Ягода подросла | Выведите черри-томат |
| `tomato` **FD** | Not Poisonous After All | Breed the tomato | Всё-таки не ядовит | Выведите томат |
| `landrace_onion` **FD** | Through Tears | Breed the landrace onion | Сквозь слёзы | Выведите местный лук |
| `onion` **FD** | Ancestor Unknown | Breed the onion | Предок неизвестен | Выведите лук |
| `early_rice` **FD** | The Rice That Stayed | Breed early rice | Рис, который не осыпался | Выведите ранний рис |
| `rice` **FD** | White Grain | Breed white rice | Белое зерно | Выведите белый рис |
| `garden_to_kitchen` **FD** | Garden to Kitchen | Breed tomato, onion, rice and head cabbage | С грядки — на кухню | Выведите томат, лук, рис и кочанную капусту |
| `seed_analyzer` | Nature or Luck? | Build a seed analyser | Наследственность или везение? | Постройте анализатор семян |
| `carrier` | Carrier | Find a hidden trait with the analyser | Носитель | Найдите анализатором скрытый признак |
| `genotype_lab` | Reading the Genes | Build a genotype lab | Читая гены | Постройте лабораторию генотипа |
| `from_the_wild` | From the Wild | Breed a cultivar whose line began with a wild plant | Из дикой природы | Выведите сорт, чья линия началась с дикого растения |
| `vavilov` | Vavilov Would Be Proud | Breed wheat, maize, carrot, head cabbage and broccoli | Вавилов был бы горд | Выведите пшеницу, кукурузу, морковь, кочанную капусту и брокколи |

«Сто одёжек» — отсылка к загадке «сидит дед, во сто шуб одет»; «Heads, You Win» — к «heads I win, tails you lose». Это не перевод друг друга, а одна шутка, адаптированная под язык.

**Запись журнала при `vavilov`** (`wildroots.journal.hint.vavilov`, заголовок «Вавилов» / "Vavilov"):
- ru: «Николай Вавилов объездил пять континентов, собирая семена культурных растений и их диких родственников. Он искал места, где культуры родились, — там их разнообразие больше всего. Его коллекция в Ленинграде пережила блокаду: сотрудники института умирали от голода, но семян не тронули.»
- en: "Nikolai Vavilov travelled five continents collecting the seeds of crops and their wild relatives. He looked for the places crops were born, where their variety runs richest. His collection in Leningrad survived the siege: the institute's staff starved to death rather than eat the seeds."

### 8.4 Кастомные триггеры (для инженеров)

Все срабатывают на сервере для игрока-владельца растения (UUID сажавшего), кроме `harvest_wild_plant` и `eat_bitter` (действующий игрок). Сорт засчитывается только тому, кто посадил растение: чужой сорт, собранный с чужой грядки, достижение не даёт.

| id (`wildroots:<id>`) | Когда | Поля условия |
|---|---|---|
| `harvest_wild_plant` | Сбор зрелого дикого растения (патч или своё ступени 0) | `species` (опц.) |
| `eat_bitter` | Съедена еда с эффектом горечи или яда (GDD §3.6) | `toxic` (опц.) |
| `plant_population` | Посадка лота; считаются свои растения вида в радиусе | `min_count`, `radius`, `species` (опц.) |
| `plant_mother` | Посадка маточника | `species` (опц.) |
| `bee_pollinated` | Пчела отложила донора на своё растение (GDD §4.3) | `species` (опц.) |
| `hand_pollinate` | Кисть перенесла пыльцу | `self` (bool) |
| `plant_ailing` | Своё растение созрело с витальностью ниже порога чахлости | `species` (опц.) |
| `pollen_rejected` | Самонесовместимость отвергла донора у своего растения | — |
| `mutation_found` | У своего растения проявилась мутация качественного признака | `trait` (опц.) |
| `hybrid_vigor` | Своё растение без чахлости, оба родителя чахлые | `species` (опц.) |
| `line_generation` | Посажен лот линии с номером поколения ≥ N | `named`, `min_generation` |
| `cultivar_bred` | Первое распознавание сорта у своего растения (GDD §6.2, п. 4) | `cultivar`, `tier`, `from_wild` (все опц.) |
| `analyze_lot` | Анализатор завершил анализ | `carrier` (bool) |

## 9. Прочие строки: сообщения, шкалы, интерфейс

### 9.1 Глоссарий (один термин — одно слово)

| Понятие GDD | ru_ru в игре | en_us в игре | Не использовать |
|---|---|---|---|
| Лот семян | семена (в имени), лот (в журнале) | seeds, seed lot | пакет, мешочек |
| Маточник | маточник | mother plant / mother root | клон, семенник |
| Ландрас | ландрас, местный сорт | landrace | полусорт, промежуточный сорт |
| Сорт ступени 2 | сорт | cultivar | вариант, порода |
| Линия | линия | line | род, семья |
| Дикий патч | заросли | patch | патч (в русском звучит как обновление игры) |
| Инбредная депрессия | чахлость; в справках — близкородственное скрещивание | ailing; inbreeding | вырождение |
| Гетерозигота по нужному признаку | носитель | carrier | гетерозигота (только в лаборатории) |
| Самоопыление | самоопыление | selfing / self-pollination | — |

### 9.2 Признаки и шкалы лупы

**Шкала 5 ступеней** — по `P` (лупа) или `G` (подсказки целей). Границы `[ПРЕДЛОЖЕНИЕ]`: `< 0.20 | 0.20–0.35 | 0.35–0.55 | 0.55–0.75 | ≥ 0.75`. Они неравные нарочно: пороги ландрасов GDD лежат у 0.35–0.45, пороги сортов — у 0.55–0.70, и слова целей (§4.4) совпадают со ступенями: «не мелкий» = ступень 3+, «крупный», «щедрый» = 4+. Слова приблизительны по замыслу; точную проверку порогов даёт анализатор.

**Имена признаков** (`wildroots.tooltip.trait_name.<trait>`; переопределение для вида — `.<trait>.<species_id>`):

| Признак | Общее en / ru | Переопределения ru (en) | Шкала |
|---|---|---|---|
| `fruit_size` | Size / Размер | морковь: Размер корня (Root size); пшеница, рис: Размер зерна (Grain size); теосинте: Размер початка (Ear size); капуста: Размер кочана (Head size); томат: Размер плода (Fruit size); лук: Размер луковицы (Bulb size) | `size` |
| `yield` | Yield / Урожай | — | `yield` |
| `growth_speed` | Growth / Рост | — | `growth` |
| `flavor` | Taste / Вкус | морковь: Вкус, шкала `sweetness`; капуста: Лист (Leaf), шкала `tenderness` | `flavor` |
| `cold_tolerance` | Cold hardiness / Холодостойкость | — | `tolerance` |
| `heat_tolerance` | Heat tolerance / Жаростойкость | — | `tolerance` |
| `drought_tolerance` | Drought tolerance / Засухоустойчивость | — | `tolerance` |
| `leaf_size` (капуста) | Leaf size / Размер листа | — | `size` |
| `terminal_bud` (капуста) | Heading / Завивается в кочан | — | `expression` |
| `inflorescence` (капуста) | Flower buds / Соцветия | — | `expression` |
| `stem` (капуста) | Thick stem / Утолщение стебля | — | `expression` |
| `lateral_buds` (капуста) | Side buds / Боковые почки | — | `expression` |
| `apical_dominance` (теосинте) | Single stalk / Одностебельность | — | `expression` |

Подписи построены так, чтобы слово шкалы всегда согласовывалось с мужским родом («Размер: крупный», «Урожай: щедрый») или было наречием/женским родом для всех строк своей шкалы («Холодостойкость: высокая», «Соцветия: заметно»).

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

**Качественные признаки под лупой** (`wildroots.tooltip.qual_state.<trait>.<state>`):

| Ключ | en_us | ru_ru |
|---|---|---|
| `bitterness.wild` / `.domestic` | Bitter: yes / Bitter: no | Горечь: есть / Горечь: нет |
| `bitterness.wild.toxic` / `.domestic.toxic` | Poisonous: yes / no | Ядовитость: есть / нет |
| `bitterness.wild.wild_onion` / `.domestic.wild_onion` | Pungency: fierce / mild | Острота: жгучая / мягкая |
| `dormancy.wild` / `.domestic` | Seed dormancy: yes / no | Покой семян: есть / нет |

### 9.3 Слова «на глаз» (строка `Мать на глаз: %s`)

Собираются через запятую: размер, затем цвет, затем форма. Размер — 3 слова: ступени 1–2 → «мелкий», 3 → «средний», 4–5 → «крупный». Слова согласованы с существительным внутри ключа, поэтому ключи видовые.

| Ключ | en_us | ru_ru |
|---|---|---|
| `wildroots.tooltip.eye.size.wild_carrot.{small,medium,large}` | small / medium / large root | корень мелкий / средний / крупный |
| `wildroots.tooltip.eye.size.wild_wheat.*`, `.wild_rice.*` | small / medium / large grain | зерно мелкое / среднее / крупное |
| `wildroots.tooltip.eye.size.teosinte.*` | small / medium / large ear | початок мелкий / средний / крупный |
| `wildroots.tooltip.eye.size.wild_cabbage.*` | small / medium / large leaves | листья мелкие / средние / крупные |
| `wildroots.tooltip.eye.size.wild_tomato.*` | small / medium / large fruit | плоды мелкие / средние / крупные |
| `wildroots.tooltip.eye.size.wild_onion.*` | small / medium / large bulb | луковица мелкая / средняя / крупная |
| `wildroots.tooltip.eye.color.wild_carrot.{white,yellow,orange,purple}` | white / yellow / orange / purple | белый / жёлтый / оранжевый / фиолетовый |
| `wildroots.tooltip.eye.color.teosinte.{white,yellow,red}` | white / yellow / red kernels | зерно белое / жёлтое / красное |
| `wildroots.tooltip.eye.color.wild_cabbage.{green,red}` | green / red | зелёные / красные |
| `wildroots.tooltip.eye.color.wild_rice.{red,white}` | red / white grain | зерно красное / белое |
| `wildroots.tooltip.eye.shatters` / `.holds` | ear shatters / ear holds | колос осыпается / колос держится |
| `wildroots.tooltip.eye.shatters.wild_rice` / `.holds.wild_rice` | panicle shatters / holds | метёлка осыпается / держится |
| `wildroots.tooltip.eye.grains.one` / `.two` | one grain per spikelet / two | одно зерно в колоске / два зерна в колоске |
| `wildroots.tooltip.eye.hulled` / `.free_threshing` | hulled / threshes clean | в плёнках / обмолачивается чисто |
| `wildroots.tooltip.eye.encased` / `.naked` | kernels in shells / naked kernels | зерно в скорлупе / голое зерно |
| `wildroots.tooltip.eye.bushy` / `.single_stalk` | bushy / single stalk | куст / один стебель |

Цвет моркови и капусты стоит после размера и согласуется с тем же существительным: «корень крупный, жёлтый», «листья средние, красные».

### 9.4 Опыление (лупа, тир 1; `wildroots.tooltip.pollination.<id>`)

| id | en_us | ru_ru | Когда |
|---|---|---|---|
| `bees` | Pollination: bees | Опыление: пчёлы | Был пчелиный донор |
| `hand` | Pollination: by hand | Опыление: вручную | Кисть, другой цветок |
| `bud` | Pollination: in the bud | Опыление: в бутоне | Кисть по себе у капусты |
| `forced_self` | Pollination: selfed by hand | Опыление: самоопыление вручную | Кисть по себе, не капуста |
| `neighbours` | Pollination: neighbours | Опыление: соседи | Доноры в радиусе, без пчёл и кисти |
| `wind` | Pollination: wind | Опыление: ветер | Ветроопыляемый вид с донорами |
| `self` | Pollination: selfed | Опыление: само на себя | Доноров нет |
| `no_pollinator` | No pollinators came | Опылители не прилетали | `seed_set_no_pollinator` у насекомоопыляемого |
| `rejected` | Neighbours' pollen refused | Пыльца соседей отвергнута | Самонесовместимость (GDD §14) |
| `ailing` | Signs of inbreeding | Признаки чахлости | Витальность ниже порога (GDD §3.7: «признаки инбредной депрессии») |

### 9.5 Анализатор (тир 2; `wildroots.tooltip.analysis.<id>`)

Числа здесь разрешены. Код форматирует число по локали (`0,45` в ru, `0.45` в en) и передаёт строкой.

| id | en_us | ru_ru |
|---|---|---|
| `header` | Analysis: | Анализ: |
| `trait` | %1$s: %2$s heritable | %1$s: %2$s по наследству |
| `carrier` | Carrier: %s | Носитель: %s |
| `fixed` | Fixed: %s | Закреплено: %s |
| `one_copy` | One copy only: %s | Одна копия: %s |
| `vitality` | Vitality: %s | Витальность: %s |
| `cultivar_check` | %1$s: %2$d of %3$d met | «%1$s»: выполнено %2$d из %3$d |

**Имена качественных признаков** для `carrier` / `fixed` / `one_copy` (`wildroots.tooltip.qual.<trait>`, домашний аллель): `shattering` — non-shattering / неосыпаемость; `dormancy` — no dormancy / дружные всходы; `bitterness` — no bitterness / без горечи (лук: mild / мягкость); `color_y1` — yellow / жёлтый цвет; `color_y2` — orange (second gene) / оранжевый (второй ген); `color_p` — purple / фиолетовый цвет; `spikelet_grains` — two grains / двузёрный колос; `free_threshing` — free threshing / голозёрность; `naked_kernel` — naked kernel / голое зерно; `tb1` — single stalk / одностебельность; `color_y` — yellow kernel / жёлтое зерно; `color_r` — red kernel / красное зерно; `color_a` — red leaf / красный лист; `color_rc` — white grain / белое зерно.

### 9.6 Сообщения (`wildroots.message.<id>`)

| id | Где | en_us | ru_ru |
|---|---|---|---|
| `journal.received` | чат | You now carry a field journal. | Теперь с собой полевой журнал. |
| `journal.new_entry` | тост | New journal entry: %s | Новая запись в журнале: %s |
| `species_discovered` | action bar | New species: %s | Найден вид: %s |
| `toast.cultivar_bred.title` | тост | Cultivar bred! | Выведен сорт! |
| `toast.landrace_bred.title` | тост | Landrace bred! | Выведен ландрас! |
| `toast.bred.body` | тост | %s | %s |
| `brush.picked` | action bar | Pollen collected: %s | Пыльца набрана: %s |
| `brush.applied` | action bar | Pollen transferred | Пыльца перенесена |
| `brush.selfed` | action bar | Selfed | Самоопыление |
| `brush.bud` | action bar | Bud-pollinated | Опылено в бутоне |
| `brush.wrong_species` | action bar | This pollen won't take here | Пыльца не подходит |
| `brush.not_flowering` | action bar | Not in flower | Растение не цветёт |
| `brush.expired` | action bar | The pollen has dried out | Пыльца засохла |
| `plant.needs_stock` | action bar | Won't take. Needs stock from wild plants. | Не взойдёт. Нужен посадочный материал диких растений. |
| `plant.needs_water` **FD** | action bar | Rice needs standing water | Рису нужна вода |
| `lens.nothing` | action bar | Nothing to look at here | Здесь нечего рассматривать |
| `lens.header` | чат | %1$s · %2$s | %1$s · %2$s |
| `lens.inspected` | action bar | Seeds inspected | Семена осмотрены |
| `analyzer.needs_bone_meal` | интерфейс | Needs bone meal | Нужна костная мука |
| `lab.needs_fuel` | интерфейс | Needs a gold nugget and bone meal | Нужны золотой самородок и костная мука |
| `analyzer.done` | интерфейс | Analysis complete | Анализ готов |

`lens.header` — первая строка осмотра растения: «Дикая морковь · Цветение» или «Восточная морковь (жёлтая) · Урожайная зрелость». Остальные строки осмотра — те же, что тир 1 тултипа (§7.2).

### 9.7 Интерфейс журнала (`wildroots.journal.ui.<id>`)

| id | en_us | ru_ru |
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
| `new` | New | Новое |

**Уточнения стадии** `wildroots.tooltip.stage.harvest_ready.<species_id>`: морковь — root ready / корнеплод созрел; капуста — head formed / кочан сформирован (для дикой — leaves ready / листья готовы); лук **FD** — bulb ready / луковица созрела.

## 10. Передача и открытые вопросы

### 10.1 Что нужно от смежных ролей

**Game Designer:**
- Утвердить наследование метки «Из дикой природы» и имени линии **по матери** (§6).
- Утвердить флаги `inspected` / `analyzed` в лоте вместо «лупа в другой руке» (§7.1).
- Утвердить границы словесной шкалы `0.20 / 0.35 / 0.55 / 0.75` (§9.2) — от них зависят слова целей в журнале.
- Id маточника лука `mother_bulb` (§7.3).
- Открытие вида при первом сборе или осмотре лупой; до этого лоты называются «Дикие семена» (§4.3).

**Software Architect / инженеры:**
- Id дикой еды (§7.4) — предложения, нужны в реестре.
- Журнал хранится в capability игрока, предмет — только окно (§4.1). Синхронизация: id открытых записей + данные авто-страниц.
- UUID сажавшего в BlockEntity растения — нужен для подсказок «своё растение» и для `cultivar_bred` (§4.5, §8.4).
- 13 кастомных триггеров достижений (§8.4); условие `forge:mod_loaded` для FD-достижений; `villager_trade` для странствующего торговца `[ПРОВЕРИТЬ]`.
- Перенос имени с наковальни в `line_name` (§6.2).
- Числа в строках анализатора форматируются по локали в коде (§9.5).

**Economy Designer:** если у ландрасов появятся свойства еды (сытость, эффекты), описания `desc` в §7.5 сверить с ними. Если появится ступень «обмолота» (GDD §16), нужны строки для обмолоченного зерна.

**Level Designer:** тексты «Что видно» (§4.3) описывают дикую капусту на скалах у моря, дикий рис в воде, дикую морковь с белыми зонтиками. Если размещение или модели будут другими — сообщить, тексты поправлю.

### 10.2 Открытые вопросы

1. **Метка по материнской линии** (§6.1) — устраивает ли Game Designer и владельца, или метку должна терять любая примесь деревенского материала? Моя рекомендация — по матери: иначе метка наказывает за приём, который GDD сам советует.
2. **Ванильная еда без имени сорта** (Д3). Если владелец решит, что финальная еда несёт NBT, добавляется одна строка тултипа (§7.6).
3. **Историческая запись при `vavilov`** упоминает блокаду Ленинграда. Тон сдержанный, факт точный; если владелец считает тему слишком тяжёлой для игры, последнюю фразу можно убрать без потери смысла.
4. **«Analyser» или «Analyzer»** в en_us (§7.7) — британское написание выбрано под тон натуралиста; решение за владельцем, менять нужно во всех строках сразу.
5. **Плотность подсказок.** В §4.5 их 45 (плюс запись `vavilov`). Каждая срабатывает один раз, тосты не чаще раза в 30 с, но реальную нагрузку покажет плейтест онбординга (GDD §15): если тестеры жалуются на поток записей, первыми вырезаются `select_quarter`, `left_on_stalk`, `bone_meal`.

### 10.3 Чек-лист для ревью текстов

- [ ] Каждая строка проходит тест «так сказал бы натуралист в своей записной книжке».
- [ ] Ни одной строки с родом в первом лице и ни одного `%d` перед словом во множественном числе.
- [ ] Числа механики не попали в тиры 0–1.
- [ ] Каждая историческая справка сверена вторым человеком по источникам §5.
- [ ] FD-строки не показываются без FD (журнал, достижения, тултипы).
- [ ] Ключи из §2 совпадают с id из JSON видов и сортов.
