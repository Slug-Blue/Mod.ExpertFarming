# S4 · WR-023: точки инъекции M1 и M2

Ветка: `spike/wr-023-mixin-points`. Код spike одноразовый: временные mixin, шаблон и GameTest лежат в ветке и в `main` не сливаются.

## Вопрос

1. Где в 1.20.1 (Parchment `2023.09.03-1.20.1`, Forge 47.4.28) читаются `Villager.FOOD_POINTS` и `Villager.WANTED_ITEMS`. Нужно найти все места.
2. Какая подпись у `FoodData.eat(Item, ItemStack, LivingEntity)` с патчем Forge.
3. Есть ли в Forge 47.x событие или хук свойств еды.
4. Прототип M1: жители размножаются на предмете с тегом `#wildroots:villager_food`.
5. Прототип M2: хлеб класса 1 даёт 2 голода и модификатор насыщения 0.24.
6. Генерируется ли refmap и применяются ли mixin в `runGameTestServer`.

## Вердикт

| # | Подпункт | Вердикт |
|---|---|---|
| 1 | Места чтения `FOOD_POINTS`/`WANTED_ITEMS` | **ПОДТВЕРЖДЕНО** (оба поля — неизменяемые статические коллекции, событий нет). Точка M1 уточнена: это не одна точка, а **три метода `Villager`** и **один вызов в `TradeWithVillager`** |
| 2 | Подпись `FoodData.eat` | **ПОДТВЕРЖДЕНО**: `public void eat(Item, ItemStack, @Nullable LivingEntity)`. Это метод, который **добавил Forge**, поэтому у него нет SRG-имени, и в `@Inject` нужен `remap = false` |
| 3 | Событие свойств еды в Forge 47.x | **ОПРОВЕРГНУТО** (события нет). Хук `IForgeItem#getFoodProperties(ItemStack, LivingEntity)` есть и вызывается из `FoodData.eat`, но покрывает только **наши** предметы. Для ванильного хлеба без M2 не обойтись |
| 4 | M1: размножение на теге | **ЧАСТИЧНО**. По тегу работают подбор с земли, счёт очков, условие `canBreed`, цель `BREED_TARGET` и поедание в цикле `VillagerMakeLove` (минус 12 очков на жителя). Рождения ребёнка в GameTest не было, причём и в ванильном контроле на хлебе: шаг `takeVacantBed` в тестовой площадке не срабатывает (подробности ниже) |
| 5 | M2: хлеб класса 1 → 2 / 0.24 | **ПОДТВЕРЖДЕНО**: `food=2`, `saturation=0.96` (= 2 · 0.24 · 2), ванильный хлеб без класса остался 5 / 0.6 |
| 6 | Refmap и применение mixin | **ПОДТВЕРЖДЕНО**: в jar лежит `wildroots.refmap.json` с SRG-целями, `MANIFEST.MF` содержит `MixinConfigs`. В `runGameTestServer` лог показывает `Mixing … into …` для всех четырёх классов |

## Доказательства

### 1. Чтения `FOOD_POINTS` и `WANTED_ITEMS`

Источник — `javap -c -p` по `forge-1.20.1-47.4.28_mapped_parchment_2023.09.03-1.20.1.jar` (кэш ForgeGradle `mod/build/fg_cache`). Поля в constant pool встречаются только в двух классах: `Villager` и `TradeWithVillager` (поиск `grep -rl` по всем `.class` распакованного jar).

| Класс#метод (Parchment) | SRG | Что читает | Зачем |
|---|---|---|---|
| `Villager#<clinit>` | — | пишет оба поля: `ImmutableMap.of(BREAD 4, POTATO 1, CARROT 1, BEETROOT 1)`, `ImmutableSet.of(BREAD, POTATO, CARROT, WHEAT, WHEAT_SEEDS, BEETROOT, BEETROOT_SEEDS, TORCHFLOWER_SEEDS, PITCHER_POD)` | — |
| `Villager#countFoodPointsInInventory()I` (private) | `m_35529_` | `FOOD_POINTS.entrySet()` → `inventory.countItem(item) * points` | Через него работают `canBreed` (`foodLevel + очки ≥ 12`), `wantsMoreFood` (< 12), `hasExcessFood` (≥ 24) и проверка в начале `eatUntilFull` |
| `Villager#eatUntilFull()V` (private) | `m_35526_` | `FOOD_POINTS.get(stack.getItem())` по каждому слоту | Поедание. Вызывается из `eatAndDigestFood` ← `VillagerMakeLove#tick` |
| `Villager#wantsToPickUp(ItemStack)Z` | `m_7243_` | `WANTED_ITEMS.contains(item)` или `profession.requestedItems()` | Подбор с земли (`InventoryCarrier.pickUpItem`), сенсор `NEAREST_VISIBLE_WANTED_ITEM` |
| `TradeWithVillager#tick` | — | `FOOD_POINTS.keySet()` → `throwHalfStack(...)`, если `hasExcessFood` и у партнёра `wantsMoreFood` | Жители делятся едой. Фермер отдельно кидает `WHEAT` |

Важно для дизайна M1:
- Ключ обеих коллекций — `Item`, а не стак. Очки «хлеб класса 1–2 → 2, 3–4 → 4» из economy §6.7 через подмену самих коллекций (AT `public-f` + замена `Map`) **не выражаются**: `countFoodPointsInInventory` считает `countItem(Item)`. Значит, нужен перехват методов, а не данных.
- Forge coremod тоже трансформирует `Villager` (`CoreModBaseTransformer: Transforming net/minecraft/world/entity/npc/Villager` в `debug.log`). С нашими инъекциями конфликта нет.

### 2–3. `FoodData.eat` и хуки еды

`patches/net/minecraft/world/food/FoodData.java.patch` (из `forge-1.20.1-47.4.28-sources.jar`):

```
+   public void eat(Item p_38713_, ItemStack p_38714_, @org.jetbrains.annotations.Nullable net.minecraft.world.entity.LivingEntity entity) {
       if (p_38713_.m_41472_()) {
-         FoodProperties foodproperties = p_38713_.m_41473_();
+         FoodProperties foodproperties = p_38714_.getFoodProperties(entity);
```

- Ванильный `eat(Item, ItemStack)` (`m_38712_`) помечен `@Deprecated` и делегирует в новый метод с `entity = null`. `Player#eat` (`Player.java.patch`) вызывает `getFoodData().eat(item, stack, this)`.
- `IForgeItemStack#getFoodProperties(LivingEntity)` → `IForgeItem#getFoodProperties(ItemStack, LivingEntity)`, по умолчанию `self().getFoodProperties()`. Наши `ProduceItem` переопределяют этот метод, и mixin им не нужен (архитектура §12.3 права).
- Других мест в `net/minecraftforge/**`, где упоминается `FoodProperties`, нет: только `IForgeItem` и `IForgeItemStack`. Событий вида `*Food*Event` в `net/minecraftforge/event` нет. Единственное событие рядом — `LivingEntityUseItemEvent.Finish`, но оно срабатывает **после** `finishUsingItem`, то есть после начисления ванильных 5 / 0.6. Откатить начисление точно нельзя: голод и насыщение при начислении упираются в потолок (`min(…, 20)`, `min(…, foodLevel)`). Поэтому этот путь отклонён.
- Байткод `FoodData#eat(Item, ItemStack, LivingEntity)`: `isEdible` → `stack.getFoodProperties(entity)` → `eat(getNutrition(), getSaturationModifier())`. Одна инъекция `@Inject(HEAD, cancellable)` закрывает задачу.
- **Ошибка компиляции без `remap = false`:** `FoodDataGradeMixin.java:18: error: Unable to locate obfuscation mapping for @Inject target eat`. У метода, добавленного Forge, нет SRG-маппинга, и AP его не находит. С `remap = false` всё работает и в dev, и в reobf-jar: имя `eat` в рантайме не меняется.

### 4–5. Прототип и GameTest

Временные mixin (`mod/src/main/java/io/github/slugblue/wildroots/mixin/`), все внесены в `wildroots.mixins.json` и `ci/allowlists/mixins.txt`. `bash ci/check-sources.sh` → `OK`.

| Класс | Цель | Инъекция |
|---|---|---|
| `VillagerFoodPointsMixin` | `Villager#countFoodPointsInInventory` | `@Inject(HEAD, cancellable)` → сумма `points(stack) · count` по стакам |
| `VillagerEatMixin` | `Villager#eatUntilFull` | `@Inject(HEAD, cancellable)` + `@Shadow foodLevel`, `@Shadow hungry()` |
| `VillagerWantedItemsMixin` | `Villager#wantsToPickUp` | `@Inject(HEAD, cancellable)`, только для тега |
| `FoodDataGradeMixin` | `FoodData#eat(Item, ItemStack, LivingEntity)` | `@Inject(HEAD, cancellable, remap = false)` → `eat(max(1, round(n·m)), s·m)` |

Логика лежит в `spike/SpikeFood`: тег = 1 очко, хлеб класса 1–2 = 2 очка, остальное — по ванили. Временная замена урожая ландраса — `minecraft:apple` в `data/wildroots/tags/items/villager_food.json`.

GameTest `SpikeFoodGameTests` (команда `JAVA_HOME=~/.jdks/temurin-17 ./gradlew :mod:runGameTestServer :mod:build`):

| Тест | Результат |
|---|---|
| `m1FoodPointsPerStack` | OK: 12 яблок → `canBreed`; 12 сладких ягод → нет; 5 хлебов класса 1 → `wantsMoreFood` и не `canBreed` (10 очков, в ванили было бы 20); 5 хлебов без класса → `canBreed`; `wantsToPickUp` верен для яблока, ягод и хлеба |
| `m1VillagerPicksUpTaggedFood` | OK (3 из 3 прогонов): сенсор ставит `NEAREST_VISIBLE_WANTED_ITEM` на яблоко, житель подбирает его |
| `m1VillagersBreedOnTaggedFood` | OK по критерию «цикл `VillagerMakeLove` прошёл»: `BREED_TARGET` был выставлен, на каждом жителе яблок стало 16 → 4 (`eatAndDigestFood` вызывается только из `VillagerMakeLove#tick`). Ребёнка нет |
| `m1ControlBreedOnBread` (ваниль) | То же: хлеба 4 → 1 на жителе, ребёнка нет |
| `m1ControlPicksUpBread` (ваниль, `required = false`) | Нестабилен: 1 успех из 3 прогонов. На apple-тест не влияет |
| `m2GradedBread` | OK |

Выдержки из `mod/run/logs/latest.log` (последний прогон):

```
SPIKE M2 graded bread: food=2 saturation=0.96000004 m2Hits=1
SPIKE M2 vanilla bread: food=5 saturation=5.0
SPIKE M1 breed ok (bread): babies=0, foodLeft=2/8, countHits=2163, eatHits=2
SPIKE M1 breed ok (tagged): babies=0, foodLeft=8/32, countHits=3099, eatHits=4
All 7 required tests passed :)
1 optional tests failed
BUILD SUCCESSFUL in 2m 12s
```

Диагностика размножения (тот же GameTest, промежуточный прогон):

```
SPIKE DIAG tagged t=200 food=16/16 poiHome=true ... act=Optional[idle] breed=false
SPIKE DIAG tagged t=600 food=4/4 poiHome=true ... act=Optional[idle] breed=false
SPIKE DIAG bread t=200 food=4/4 poiHome=true ... breed=true
SPIKE DIAG bread t=600 food=1/1 poiHome=true ... breed=false
```

POI `HOME` у кровати зарегистрирован (`poiHome=true`). Цикл заканчивается в `VillagerMakeLove#tryToGiveBirth` → `takeVacantBed` (`PoiManager.take(..., canReach, pos, 48)`). Дети не появляются **одинаково с тегом и с ванильным хлебом**, поэтому причина в тестовой площадке (путь до кровати, на которой стоят жители), а не в M1.

### 6. Refmap и применение

`unzip -p mod/build/libs/wildroots-1.20.1-0.0.0.jar wildroots.refmap.json`:

```
"VillagerFoodPointsMixin": {"countFoodPointsInInventory": "Lnet/minecraft/world/entity/npc/Villager;m_35529_()I"},
"VillagerEatMixin":        {"eatUntilFull": "Lnet/minecraft/world/entity/npc/Villager;m_35526_()V"},
"VillagerWantedItemsMixin":{"wantsToPickUp": "Lnet/minecraft/world/entity/npc/Villager;m_7243_(Lnet/minecraft/world/item/ItemStack;)Z"}
```

`FoodDataGradeMixin` в refmap нет, и это правильно (`remap = false`). `@Shadow`-члены в reobf-jar переименованы в SRG (`javap`: `private int f_35376_; private boolean m_35525_();`). `MANIFEST.MF`: `MixinConfigs: wildroots.mixins.json`.

`mod/run/logs/debug.log` в `runGameTestServer`:

```
[mixin/]: Mixing VillagerEatMixin from wildroots.mixins.json into net.minecraft.world.entity.npc.Villager
[mixin/]: Mixing VillagerFoodPointsMixin from wildroots.mixins.json into net.minecraft.world.entity.npc.Villager
[mixin/]: Mixing VillagerWantedItemsMixin from wildroots.mixins.json into net.minecraft.world.entity.npc.Villager
[mixin/]: Mixing FoodDataGradeMixin from wildroots.mixins.json into net.minecraft.world.food.FoodData
[mixin/]: Reference map 'wildroots.refmap.json' for wildroots.mixins.json could not be read. If this is a development environment you can ignore this message
```

Предупреждение про refmap в dev ожидаемо: в dev-запуске имена Mojang совпадают с целями, а refmap нужен только в reobf-jar.

### Попутная находка для всех GameTest

В `runGameTestServer` 1.20.1 шаблон ставится **на одну клетку выше** относительного нуля `GameTestHelper`: `origin=BlockPos{y=-60}`, блок `(1,0,1)` — воздух, `(1,1,1)` — камень пола шаблона `floor_3x3x3`. Сущности, заспавненные на относительном `y=1`, оказываются в полу и задыхаются (`Villager suffocated in a wall`). Сущности и блоки над полом нужно ставить на `y=2`.

## Что не проверено

- Само рождение ребёнка: в GameTest-площадке оно не произошло даже у ванильного контроля. Нужен отдельный тест с другой площадкой (кровать в стороне, свободный путь) или проверка на выделенном сервере вручную.
- Обмен едой между жителями (`TradeWithVillager#tick`, `throwHalfStack(FOOD_POINTS.keySet())`): прототипа нет, точка найдена по байткоду.
- Флаг отключения (SEC-45) и `Guarded.run` в прототипе не делались.
- Клиент (тултип, AppleSkin) — `runClient` не запускался.
- Совместимость с чужими mixin в `Villager`/`FoodData` (модпаки).

## Предлагаемые правки документов

1. **`docs/adr/0014-mixin-policy.md`, таблица «Закрытый список», строка M1.**
   Старое: `| M1 | `Villager`: очки еды и подбор предметов (`#wildroots:villager_food`) | Нужен (волна 1) |`
   Новое: `| M1 | `Villager#countFoodPointsInInventory`, `#eatUntilFull`, `#wantsToPickUp` (три класса mixin, по одной `@Inject(HEAD, cancellable)`); опционально `TradeWithVillager#tick` (`@ModifyArg` набора в `throwHalfStack`, обмен едой) — S4 | Нужен (волна 1) |`
   И в «Правила для mixin» добавить: «M1 — исключение из „одна цель на класс“ по смыслу: один ванильный класс, но три класса mixin, каждый с одной инъекцией».
2. **`docs/adr/0014-mixin-policy.md`, строка M2.**
   Старое: `| M2 | `FoodData.eat(Item, ItemStack, LivingEntity)`: класс у ванильных продуктов | Нужен (волна 1) |`
   Новое: `| M2 | `FoodData#eat(Item, ItemStack, LivingEntity)` — метод Forge-патча, `@Inject(HEAD, cancellable, remap = false)`; события свойств еды в Forge 47.x нет (S4) | Нужен (волна 1) |`
3. **`docs/05-architecture.md` §11.6 п. 6.**
   Старое: «Нужен mixin M1 (§16): очки еды и подбор предметов читают тег `#wildroots:villager_food` (очки из economy §6.7).»
   Новое: «Нужен mixin M1 (§16): `countFoodPointsInInventory` и `eatUntilFull` считают очки **по стаку** (тег `#wildroots:villager_food`, класс хлеба по economy §6.7), `wantsToPickUp` принимает тег. Подмена самих коллекций через AT не подходит: ключ — `Item`, а очки хлеба зависят от класса (S4).»
4. **`docs/05-architecture.md` §12.3, второй пункт.**
   Старое: «у Forge 47.x, насколько я знаю, нет события «свойства еды» `[ПРОВЕРИТЬ]`. Нужен mixin M2 в `FoodData.eat(Item, ItemStack, LivingEntity)` (подпись с патчем Forge `[ПРОВЕРИТЬ]`)»
   Новое: «у Forge 47.x нет события «свойства еды» (S4: свойства еды есть только в `IForgeItem`/`IForgeItemStack#getFoodProperties`). Нужен mixin M2 в `FoodData#eat(Item, ItemStack, @Nullable LivingEntity)` — метод добавлен патчем Forge, поэтому `remap = false`»
5. **`docs/05-architecture.md` §16, строки M1 и M2.** Снять `[ПРОВЕРИТЬ]`, вписать точки из п. 1–2.
6. **`docs/05-architecture.md` §17 (GameTest)** или `docs/dev-onboarding.md`: «Пол шаблона в `runGameTestServer` лежит на относительном `y=1`. Сущности ставить на `y=2` (S4).»

Задачи `06-tasks.md`, которые стоит пересмотреть:
- **WR-166 (M1):** приёмка «одна цель, одна инъекция» → «три класса mixin в `Villager`, у каждого одна `@Inject`». Добавить решение по `TradeWithVillager` (обмен тегом) и очки по классу хлеба. GameTest на размножение строить на площадке, где ванильный контроль рождает ребёнка, и держать этот контроль в тесте.
- **WR-171 (M2):** остаётся на mixin (событие не найдено). В приёмку добавить `remap = false` и GameTest «хлеб класса 1 → 2 / 0.24 (насыщение +0.96 с нуля)».
- **WR-013 (allowlist):** имена классов M1a/M1b/M1c.
