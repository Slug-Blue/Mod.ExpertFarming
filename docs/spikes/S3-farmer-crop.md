# S3 · WR-022: фермер и `FieldPlantBlock extends CropBlock`

Ветка: `spike/wr-022-farmer-crop`. Код spike: `mod/src/gametest/java/io/github/slugblue/wildroots/gametest/spike/s3/` (только source set `gametest`, в jar не попадает). Ссылки: архитектура §11.6, §20.1 R3; ADR-0010.

## Вопрос

1. Совпадает ли `HarvestFarmland` 1.20.1 (Forge 47.4.28) с описанием в §11.6: проверка `CropBlock`, `isMaxAge`, `destroyBlock`, посадка на пустую пашню, какие семена берёт.
2. Работают ли на прототипе `FieldPlantBlock extends CropBlock` (BE + метка `village`/`player`): сбор только `village`, урожай в инвентарь жителя, пересадка, жнивьё `village_plot`.
3. GameTest: поле 3×3 `village` и 3×3 `player` рядом с фермером; через 2 игровых дня `player` не тронуто, `village` пересажено, урожай у жителя.

## Вердикт

| Подпункт | Вердикт |
|---|---|
| `HarvestFarmland`: `instanceof CropBlock` + `isMaxAge`, `destroyBlock(pos, true, villager)`, посадка на воздух над `FarmBlock` семенами с тегом `#minecraft:villager_plantable_seeds` | **ПОДТВЕРЖДЕНО** (с уточнениями ниже: радиус 3×3×3 вокруг жителя, `mobGriefing`, ветка `IPlantable` Forge) |
| Механизм «переопределить `isMaxAge(state)`» | **ОПРОВЕРГНУТО**: `CropBlock.isMaxAge(BlockState)` в 1.20.1 — `public final`. Рабочая замена без mixin — переопределить `getAge(BlockState)` (не final), `isMaxAge = getAge(state) >= getMaxAge()` |
| Сбор только `village`, `player` не трогается | **ПОДТВЕРЖДЕНО** (GameTest, 48 000 тиков) |
| Урожай в инвентарь жителя (через `getDrops` + `THIS_ENTITY`/`BLOCK_ENTITY`) | **ПОДТВЕРЖДЕНО** |
| Пересадка той же клетки с `harvestCount + 1` | **ПОДТВЕРЖДЕНО**, но не «в том же обработчике»: дроп считается до `setBlock(air)`, поэтому пересадка отложена до конца тика уровня |
| Жнивьё `village_plot` (`getAge` всегда max) собирается и пересаживается | **ПОДТВЕРЖДЕНО** |
| «`village` без паспорта → `isMaxAge = false`» (SEC-23) | **ОПРОВЕРГНУТО в формулировке**: `isMaxAge` видит только `BlockState`, BE ему недоступен. Защиту даёт обработчик дропа (проверено) и снятие флага по тику после установки (предложено, не проверено) |
| Запасной путь R3 (своё `Behavior` + mixin в `VillagerGoalPackages`) | **Не нужен** |

Итог по spike: **ЧАСТИЧНО** — подход ADR-0010 работает без mixin, но формулировка «переопределяет `isMaxAge`» невыполнима и заменяется на `getAge`, а пересадка и проверка паспорта устроены иначе, чем описано.

## Доказательства

### 1. `HarvestFarmland` по байткоду (javap)

Декомпилированных исходников в кэше ForgeGradle нет: FG6 не создаёт `*-sources.jar` для `_mapped_parchment_` без запроса IDE, а запускать декомпилятор значило бы тянуть новый инструмент. Поэтому я читал `javap -c -p` классов из `mod/build/fg_cache/net/minecraftforge/forge/1.20.1-47.4.28_mapped_parchment_2023.09.03-1.20.1/forge-…-1.20.1.jar`. Это тот jar, с которым мод компилируется и запускается в dev, имена Mojang.

`net.minecraft.world.entity.ai.behavior.HarvestFarmland`:
- Конструктор: память `LOOK_TARGET` и `WALK_TARGET` должна отсутствовать, `SECONDARY_JOB_SITE` — присутствовать.
- `checkExtraStartConditions(ServerLevel, Villager)`: `ForgeEventFactory.getMobGriefingEvent(level, villager)` (gamerule `mobGriefing` с хуком Forge); профессия `== VillagerProfession.FARMER`; перебирает клетки `x, y, z ∈ [-1, 1]` вокруг `villager.blockPosition()` — только 3×3×3 вокруг самого жителя; список годных клеток → случайная.
- `validPos(BlockPos, ServerLevel)`: `block instanceof CropBlock && ((CropBlock) block).isMaxAge(state)` **или** `state.isAir() && below instanceof FarmBlock`.
- `tick(ServerLevel, Villager, long)`: если житель в пределах 1.0 от клетки, то при `CropBlock && isMaxAge(state)` вызывается `level.destroyBlock(pos, true, villager)`. Затем, если `state.isAir() && below instanceof FarmBlock && villager.hasFarmSeeds()`, перебирается инвентарь: стак с `ItemTags.VILLAGER_PLANTABLE_SEEDS` и `BlockItem` → `setBlockAndUpdate(pos, block.defaultBlockState())`; иначе, если `IPlantable` с `PlantType.CROP` → `setBlock(pos, getPlant(level, pos), 3)` (ветка Forge); затем звук `CROP_PLANTED`, `shrink(1)`. В обеих проверках используется `state`, прочитанный **до** `destroyBlock`: в тике сбора посадки не бывает.
- `canStillUse`: не дольше 200 тиков; после `stop` — пауза 40 тиков, между клетками — 20.

`CropBlock` (javap): `public final boolean isMaxAge(BlockState)`; не final: `getAge(BlockState)`, `getMaxAge()`, `getAgeProperty()`, `getStateForAge(int)` (= `defaultBlockState().setValue(AGE, n)`, **сбрасывает остальные свойства**), `randomTick`, `growCrops`, `isValidBonemealTarget`, `getShape`, `isRandomlyTicking`.

`Level.destroyBlock(BlockPos, boolean, Entity, int)` (javap): `getBlockEntity` → `Block.dropResources(state, level, pos, be, entity, ItemStack.EMPTY)` → `setBlock(pos, fluid.createLegacyBlock(), 3, …)`. Дроп считается **до** установки воздуха, BE в этот момент ещё доступен.

Тег `data/minecraft/tags/items/villager_plantable_seeds.json` (client-extra.jar 1.20.1): `wheat_seeds`, `potato`, **`carrot`**, `beetroot_seeds`, `torchflower_seeds`, `pitcher_pod`.

### 2. Что понадобилось, чтобы фермер в GameTest взял работу

- `villager.setVillagerData(...setProfession(FARMER).setLevel(1))` и `setVillagerXp(1)`. Без опыта `ResetProfession` (javap: `JOB_SITE` absent, xp 0, level ≤ 1 → профессия `NONE`) сбросил бы профессию.
- Компостер в арене (POI `minecraft:farmer` регистрируется сам при `setBlock`), тикет `PoiManager.take(h -> h.is(PoiTypes.FARMER), …)`, память `JOB_SITE = GlobalPos(компостер)`, затем `villager.refreshBrain(level)`. Активность `WORK` требует `JOB_SITE`, а `ValidateNearbyPoi` стирает `JOB_SITE`, если POI нет (javap).
- `SECONDARY_JOB_SITE` ставит сам `SecondaryPoiSensor` (раз в 40 тиков, пашня в ±4 по X/Z и ±2 по Y), руками его задавать не нужно.
- Время: `GameTestServer` выключает только `doMobSpawning` и `doWeatherCycle` (javap `TEST_GAME_RULES`), цикл дня идёт. Сервер тикает без пауз (48 000 тиков ≈ 2 мин), поэтому прогнаны **два настоящих игровых дня** (`setDayTime(0)` → тик 48 000) с ванильным расписанием idle/work/meet/rest. `mobGriefing = true` по умолчанию.
- Арена: шаблон `gametest/arena_11x5x7` (пол из камня), стены высотой 3 из стекла и морских фонарей. При стене в 2 блока фермер в прогоне 2 ушёл через неё с компостера. Фонари нужны, потому что свежая арена без расчёта света роняла культуры при соседних обновлениях (`CropBlock.canSurvive`). Растения ставятся с флагами `UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE`.
- **Случайных тиков в GameTest-сервере нет**: `ServerChunkCache.tickChunks` тикает чанки только рядом с игроком. В прогоне 6 стадии пересаженных растений перед вторым днём: `0 0 0 0 0 0 0 0 0`. Поэтому на тике 24 000 тест выставляет деревенским растениям спелую стадию, имитируя рост.

### 3. GameTest (`:mod:runGameTestServer`)

Команда: `JAVA_HOME="C:/Users/user/.jdks/temurin-17" ./gradlew :mod:runGameTestServer`, прогон 6, `BUILD SUCCESSFUL in 3m 18s`.

`FarmerCropSpikeTests.farmerHarvestsOnlyVillageField`. Поле `village` 3×3: 6 спелых `spike_field_plant[village=true, age=7]` и 3 `spike_village_plot`, все с паспортом в BE. Рядом поле `player` 3×3 `[village=false, age=7]`. Условие успеха на тике ≥ 48 000: поле `player` ни разу не менялось (проверка каждый тик); 9/9 деревенских клеток — `village=true` с `harvestCount ≥ 2`, то есть собраны и пересажены в оба дня; в инвентаре жителя ≥ 30 урожая; на земле урожая нет.

```
[S3] farmer harvest at BlockPos{x=23, y=-58, z=2} -> inventory [2 spike_landrace_carrot], remainder [], replant queued (harvestCount 1)
[S3] replanted BlockPos{x=23, y=-58, z=2} harvestCount=1 gameTime=115130
[S3] tick 4000 dayTime 4000 activity Optional[work] replanted 0/9 inventoryHarvest 8 dropped 0 ...
[S3] day 2: stages before forced ripening: 0 0 0 0 0 0 0 0 0
[S3] tick 28000 dayTime 28000 activity Optional[work] replanted 7/9 inventoryHarvest 26 dropped 0 ...
[S3] SUCCESS at tick 48000: 9/9 cells replanted twice, inventoryHarvest 30, player field intact
All 3 required tests passed :)
```
(«replanted N/9» на тиках до второго дня = 0, потому что метрика считает только `harvestCount ≥ 2`.) За прогон 5 было 18 сборов фермером: 9 с `harvestCount=1` и 9 с `harvestCount=2`.

`FarmerCropSpikeTests.forgedVillageStateIsBrokenButNotReplanted` (зонд SEC-23): 9 клеток `[village=true, age=7]` без паспорта в BE, как после `/setblock`.
```
[S3] plain break at BlockPos{x=10, y=-58, z=2} by Villager['Farmer'/166, ...] (village=true)
[S3] FORGED: tick 2645 3 forged cells broken by the farmer, not replanted; inventory 0, on ground 6
```
Фермер ломает подделку, потому что `isMaxAge` видит только состояние. Обработчик не кладёт урожай в инвентарь и не пересаживает, урожай падает на землю.

### 4. Как устроен прототип

- `FieldPlantBlock extends CropBlock implements EntityBlock`. Стадия — ванильное `AGE` (0…7), плюс свойство `village`. `getAge(state)` = `getMaxAge()` только при `village && stage ≥ RIPE`, иначе `min(stage, getMaxAge() − 1)`. Так как `CropBlock` строит рост на `getAge`/`getStateForAge`, переопределены `randomTick`, `growCrops`, `isValidBonemealTarget`, `isRandomlyTicking`, `getShape`. Без этого `getStateForAge` сбросил бы `village`, а спелые растения игрока «росли» бы бесконечно и принимали костную муку.
- `VillagePlotBlock extends CropBlock implements EntityBlock`: `getAge` всегда `getMaxAge()`, не тикает, дропа нет.
- `getDrops(BlockState, LootParams.Builder)`: `THIS_ENTITY instanceof Villager`, не `FakePlayer`, профессия `FARMER`, `BLOCK_ENTITY` с валидным паспортом → `villager.getInventory().addItem(...)`, остаток возвращается как дроп; пересадка ставится в очередь. Во всех остальных случаях — обычный дроп.
- Пересадка: `TickEvent.LevelTickEvent` (фаза `END`). Если клетка — воздух над `FarmBlock`, ставится новое растение `village=true, AGE=0`, паспорт копируется с `harvestCount + 1`. Это происходит до следующего `HarvestFarmland.tick`, так что ванильная посадка семян на эту клетку не успевает.

## Что не проверено

- Настоящий рост от случайных тиков в деревне и повторный сбор без принудительного созревания (в GameTest нет игрока). Нужен выделенный сервер с игроком или прямой вызов `randomTick` в тесте.
- Переполнение инвентаря жителя (8 слотов) нашим урожаем и обмен едой между жителями. Наш предмет не входит в `WANTED_ITEMS`/`FOOD_POINTS`, это M1 (S4).
- Ванильные семена в инвентаре фермера: тег содержит `carrot` и `potato`. Фермер с ванильной морковью посадит её на **любую** пустую пашню в радиусе 1 блока от себя, в том числе на пашню игрока (ветка `BlockItem`). Утверждение ADR-0010 «фермер ничего не сажает на чужую пашню» верно только для наших лотов.
- Снятие `village` у подделки по тику после установки (`onPlace` → `scheduleTick`) не реализовано.
- Поведение модов-комбайнов (Create и др.) и подсказок Jade/WTHIT: они увидят у спелых растений игрока `getAge = max − 1`.
- Клиент (рендер, модели) — не запускался по правилам spike.
- `runServer` не запускался, EULA не принималась.

## Предлагаемые правки документов

1. **`docs/adr/0010-village-farmer-integration.md`, «Статус»**: `Поведение HarvestFarmland 1.20.1 — [ПРОВЕРИТЬ] по исходникам (spike S3).` → `Поведение HarvestFarmland 1.20.1 проверено spike S3 (docs/spikes/S3-farmer-crop.md): CropBlock + isMaxAge, destroyBlock(pos, true, villager), посадка на воздух над FarmBlock семенами #minecraft:villager_plantable_seeds; isMaxAge — final, используется getAge.`
2. **ADR-0010, «Решение», п. 1**: `FieldPlantBlock extends CropBlock переопределяет isMaxAge(state) = state.village && зрелость(stage).` → `FieldPlantBlock extends CropBlock переопределяет getAge(state): getMaxAge() при state.village && зрелость(stage), иначе min(stage, getMaxAge() − 1) (CropBlock.isMaxAge в 1.20.1 — final и равен getAge ≥ getMaxAge). Так как ванильные randomTick, growCrops, getStateForAge, isValidBonemealTarget, isRandomlyTicking и getShape опираются на getAge и сбрасывают свойства блока, блок переопределяет их на своей стадии.`
3. **ADR-0010, «Решение», п. 2**: дописать `Урожай кладётся в инвентарь в getDrops(BlockState, LootParams.Builder) по THIS_ENTITY и BLOCK_ENTITY. Level.destroyBlock считает дроп до setBlock(air), поэтому пересадка ставится в очередь и выполняется в TickEvent.LevelTickEvent (END) того же тика, если клетка — воздух над FarmBlock.`
4. **ADR-0010, «Решение», п. 5** (`Растение или жнивьё с village = true, но без валидного паспорта в BE → isMaxAge = false, флаг снимается при первом тике.`) → `isMaxAge видит только BlockState, поэтому подделку (village = true без валидного паспорта) фермер ломает. Защита: (1) обработчик дропа не кладёт урожай в инвентарь и не пересаживает без валидного паспорта (проверено S3); (2) onPlace планирует тик через 1 тик (scheduleTick), который снимает village при невалидном паспорте (BE из шаблона к этому моменту уже загружен).`
5. **ADR-0010, «Решение», п. 7** (`Наши лоты не получают #minecraft:villager_plantable_seeds, поэтому фермер ничего не сажает на чужую пашню.`) → `Наши лоты не получают #minecraft:villager_plantable_seeds. Ванильные carrot/potato/wheat_seeds/beetroot_seeds в теге остаются: фермер с ними в инвентаре сажает ванильную культуру на любую пустую пашню рядом с собой, в том числе на пашню игрока (ванильное поведение). Если линия убирает ванильную культуру, решаем отдельно: снять предмет с тега в пакете линии или оставить.`
6. **`docs/05-architecture.md` §11.6, абзац «Как работает ванильный фермер в 1.20.1 (по памяти, [ПРОВЕРИТЬ] …)»** → `Как работает ванильный фермер в 1.20.1 (проверено S3, javap HarvestFarmland): поведение из рабочего пакета (VillagerGoalPackages) стартует, если mobGriefing (хук Forge), профессия FARMER и есть память SECONDARY_JOB_SITE (пашня в ±4/±2 от жителя, SecondaryPoiSensor). Оно берёт клетки 3×3×3 вокруг самого жителя. Клетка годится, если block instanceof CropBlock && crop.isMaxAge(state) (isMaxAge final, = getAge ≥ getMaxAge) или если это воздух над FarmBlock. Зрелую культуру фермер ломает через level.destroyBlock(pos, true, villager) (дроп до установки воздуха). На пустую пашню сажает семена из инвентаря с тегом #minecraft:villager_plantable_seeds (defaultBlockState() блока BlockItem; ветка Forge — IPlantable с PlantType.CROP).`
7. **§11.6, п. 1**: `переопределяет isMaxAge(state) = state.village && зрелость(stage)` → `переопределяет getAge(state) (isMaxAge final), см. ADR-0010`. **П. 3**: `В том же обработчике после сбора …` → `После сбора (очередь, LevelTickEvent END того же тика) …`. **П. 4**: `получает isMaxAge = false, и флаг снимается при первом тике` → `не даёт урожая в инвентарь и пересадки; флаг снимается запланированным тиком после установки`.
8. **§17 (тестируемость)**, добавить строку: `В runGameTestServer нет игроков, поэтому случайных тиков нет (ServerChunkCache.tickChunks). Тесты роста вызывают randomTick напрямую или выставляют стадию. Цикл дня в GameTest-сервере включён (выключены только doMobSpawning и doWeatherCycle), сервер тикает без пауз.`
9. **§20.1 R3**: в столбец «Проверка» → `S3: подтверждено без mixin (docs/spikes/S3-farmer-crop.md)`.

Задачи `06-tasks.md`, которые надо пересмотреть:
- **WR-164, WR-165** — заменить «переопределить `isMaxAge`» на `getAge` + переопределения роста; пересадка через очередь в `LevelTickEvent`; критерии приёмки взять из GameTest этого spike (2 игровых дня, `player` не тронуто, `harvestCount` растёт).
- **WR-131** (рост) — учесть, что `CropBlock.getStateForAge` сбрасывает свойства, а в GameTest нет случайных тиков.
- Задача про SEC-23 (защита деревенского фонда) — добавить `scheduleTick` в `onPlace` и тест «подделка ломается, но не даёт ни инвентаря, ни пересадки».
- Новый мелкий вопрос владельцу или ED: ванильные семена из `villager_plantable_seeds` у фермера и пашня игрока (п. 5 выше).
