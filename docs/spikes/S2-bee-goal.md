# Spike S2 (WR-021): цель опыления пчёл

Ветка: `spike/wr-021-bee-goal`. Код spike одноразовый, лежит в `mod/src/gametest/java/io/github/slugblue/wildroots/gametest/spike/bee/`, плюс одна строка AT в `mod/src/main/resources/META-INF/accesstransformer.cfg` и `ci/allowlists/accesstransformer.txt`.

## Вопрос

1. Можно ли добавить свою `BeeCropPollinateGoal` через `EntityJoinLevelEvent`? Публичен ли `Mob#goalSelector` в 1.20.1 (Parchment)?
2. Можно ли выставить нектар через AT `Bee#setHasNectar`? Или метод уже доступен?
3. Переживает ли capability «пыльца» пребывание пчелы в улье? Сериализуются ли Forge capabilities, когда `BeehiveBlockEntity` сохраняет NBT пчелы?
4. Интеграционный критерий из `06-tasks.md` §4: 10 пчёл за 3 игровых дня записывают доноров в BE и делают мёд, а ванильные цветы продолжают опыляться.

## Вердикт

| # | Подпункт | Вердикт |
|---|---|---|
| 1 | Цель через `EntityJoinLevelEvent`, доступ к `goalSelector` | **ПОДТВЕРЖДЕНО**. `Mob#goalSelector` — `public final`, AT не нужен |
| 2 | `setHasNectar` через AT | **ПОДТВЕРЖДЕНО**. Метод package-private, поэтому AT **нужен**: `public net.minecraft.world.entity.animal.Bee m_27919_(Z)V` |
| 3 | Capability переживает улей | **ПОДТВЕРЖДЕНО**. `ForgeCaps` лежит в `Bees[].EntityData` улья и возвращается выпущенной пчеле, в том числе после save/load BE улья. Пчела выходит **новой сущностью с новым UUID**, и цель добавляется ей заново |
| 4a | За 3 игровых дня доноры в NBT BE и мёд | **ПОДТВЕРЖДЕНО**, но только если окно свежести пыльцы 1200 тиков считается **по тикам жизни пчелы вне улья**. Если считать по мировому времени, окно **не выполняется ни разу**, подробности ниже. Это расхождение с GDD §4.3 |
| 4b | Ванильные цветы опыляются, пока нет цветущих растений | **ПОДТВЕРЖДЕНО** |
| 4c | Конфликт двух целей (зависание, игнор цветов) | **Не обнаружен**: 0 тайм-аутов нашей цели за 72 000 тиков. Ванильная цель тоже срабатывала рядом с нашей. Запасной путь M5 (mixin) не нужен |

## Доказательства

### Исходники (javap по `mod/build/fg_cache/.../forge-1.20.1-47.4.28_mapped_parchment_2023.09.03-1.20.1.jar`)

- `net.minecraft.world.entity.Mob`: `public final GoalSelector goalSelector;` (SRG `f_21345_`).
- `net.minecraft.world.entity.animal.Bee`: `void setHasNectar(boolean)` package-private (SRG `m_27919_`), `public boolean hasNectar()`, `public void dropOffNectar()`. В `Bee#registerGoals` ванильная `Bee$BeePollinateGoal` добавлена с приоритетом **4**. У `BeeEnterHiveGoal` приоритет 1, у `BeeLocateHiveGoal`/`BeeGoToHiveGoal` — 5, у `BeeGoToKnownFlowerGoal` — 6, у `BeeGrowCropGoal` — 7, у `BeeWanderGoal` — 8.
- `BeehiveBlockEntity#addOccupantWithPresetTicks` вызывает `Entity#save(CompoundTag)`. Forge-патч `Entity#saveWithoutId` пишет `serializeCaps()` в `"ForgeCaps"`, а `Entity#load` читает `"ForgeCaps"` → `deserializeCaps`. Выпуск: `releaseOccupant` → `EntityType.loadEntityRecursive`.
- `BeehiveBlockEntity#removeIgnoredBeeTags` вырезает `Air`, `Brain`, `Pos`, `Motion`, `HivePos`, `CannotEnterHiveTicks`, `TicksSincePollination`, `CropsGrownSincePollination`, `Passengers`, `Leash`, **`UUID`** и другие. **`ForgeCaps` и `ForgeData` не вырезаются.**
- Минимальное время в улье: 2400 тиков с нектаром, 600 без нектара. Ночью и в дождь пчела не выходит (кроме `EMERGENCY`).

### GameTest

Команда (Git Bash): `JAVA_HOME="C:/Users/user/.jdks/temurin-17" ./gradlew :mod:runGameTestServer`. Итог последнего прогона: `All 5 required tests passed :)` (4 теста spike + smoke).

Класс `io.github.slugblue.wildroots.gametest.spike.bee.BeeSpikeGameTests`:

| Тест | Что проверяет |
|---|---|
| `goalInjectedAndNectarSettable` | После `helper.spawn(BEE)` в `goalSelector` ровно одна `BeeCropPollinateGoal` с тем же приоритетом, что у ванильной (4). `bee.setHasNectar(true)` через AT работает. Код компилируется только с AT: без него `setHasNectar` недоступен из другого пакета |
| `pollenSurvivesHive` | Задаём пыльцу (вид, источник, возраст, маркер) → `hive.addOccupant(bee, true)` → проверяем `writeBees()[0].EntityData.ForgeCaps` → `hive.load(hive.saveWithoutMetadata())` (save/load BE) → `emptyAllLivingFromHive(..., EMERGENCY)`. У выпущенной пчелы пыльца совпадает побайтно. UUID другой, `EntityJoinLevelEvent` снова добавил цель |
| `beesWriteDonorsAndMakeHoney` (batch `s2_bees_field`) | Поле 17×10×17: 4 улья, 10 пчёл, 8 тестовых растений (BE с `Donors`), 4 мака по углам. Идёт **полные 72 000 тиков = 3 игровых дня** со сменой дня и ночи. Проверка на тике 71 000: доноры в NBT BE ≥ 1, мёд в улье ≥ 1, ванильный нектар ≥ 1 |
| `vanillaFlowersStillPollinated` (batch `s2_bees_vanilla`) | Те же ульи и 10 пчёл, растения **не цветут** (`blooming=false`), 8 маков рядом. Ожидаем: наш нектар = 0, ванильный нектар ≥ 3 |

**Про ускорение.** Тайм-ауты не укорачивались. `GameTestServer` не держит 20 TPS и тикает с максимальной скоростью: 72 000 тиков заняли около 2 минут реального времени (`12:40:30` → `12:42:02` по логу). Так что 3 игровых дня прогнаны честно. Улей, ночь и время в улье ванильные.

Выдержки из лога (`mod/run/logs/latest.log` последнего прогона):

```
[S2] goal injected: ours=1 prio=4 vanillaPrio=4
[S2] hive EntityData.ForgeCaps = {"wildroots:bee_pollen":{AgeOutside:77,Collected:4242L,...,Marker:"hive-roundtrip",Source:{X:123,Y:64,Z:-45},Species:"carrot",...}}
[S2] released bee uuid 5ea05d6d-… (old 3d4bc2c5-…), pollen {AgeOutside:77,Collected:4242L,...,Species:"carrot",...}
[S2] vanilla SUCCESS at t=465 Stats[liveBees=9, hiveBees=1, donors=0, maxHoney=0, ourNectar=0, vanillaNectar=3, ...]
[S2] field t=3000 Stats[... donors=0, maxHoney=3, ourNectar=6, vanillaNectar=1, ...]
[S2] field t=4000 Stats[... donors=5, maxHoney=3, ourNectar=12, ...]
[S2] visit BlockPos{x=54,…} pollen from BlockPos{x=53,…} ageOutside=459 gameAge=2860 visits=0 -> donor=true
[S2] field SUCCESS at t=71000 Stats[liveBees=2, hiveBees=5, donors=12, maxHoney=5, ourNectar=102, vanillaNectar=1, ourDonorWrites=95, timeouts=0, visits=103]
All 5 required tests passed :)
```

Сводка по 95 визитам с донором за 3 дня:
- возраст пыльцы **вне улья** — 449…1115 тиков (всегда ≤ 1200);
- возраст пыльцы **по мировому времени** — 2850…13 946 тиков (**всегда > 1200**);
- в BE осталось 12 доноров: 3 растения × лимит 4 в прототипе, старейшие вытеснялись;
- мёд дошёл до уровня 5.

Посещались 3 из 8 растений: те, что в радиусе ~5.5 от зоны ульев, где пчёлы держатся. Это ожидаемо при радиусе поиска как у ванили.

### Что сделано в прототипе (кратко)

- `SpikeBeeEvents#onJoin`: `EntityJoinLevelEvent` на сервере, `bee.goalSelector.addGoal(4, new BeeCropPollinateGoal(bee))` с защитой от повторного добавления через `getAvailableGoals()`.
- `BeeCropPollinateGoal`: флаг `MOVE`, запрос мини-индекса (`SpikeBloomIndex`, без скана мира) не чаще раза в 20 тиков. Подлёт через `getNavigation().moveTo`, вблизи — `getMoveControl().setWantedPosition` и 400 тиков зависания, отказ после 1200 тиков. Визит пишет донора, заменяет пыльцу и вызывает `setHasNectar(true)`.
- `BeePollen` (`ICapabilitySerializable<CompoundTag>`): вешается через `AttachCapabilitiesEvent<Entity>`, регистрируется через `RegisterCapabilitiesEvent`. Возраст пыльцы растёт в `LivingEvent.LivingTickEvent`, то есть только пока пчела — живая сущность.

## Находки, которые влияют на дизайн

1. **Окно свежести пыльцы 1200 тиков несовместимо с ванильным циклом, если считать по мировому времени.** Ванильная пчела после одного удачного опыления получает нектар, сразу летит в улей и сидит там не меньше 2400 тиков, а ночью не выходит вообще. Поэтому между двумя визитами к растениям всегда проходит больше 2400 мировых тиков (в прогоне минимум 2850). Значит, нужно одно из двух:
   - **(рекомендую)** считать возраст пыльцы в тиках жизни пчелы вне улья. Счётчик в capability растёт в `LivingTickEvent` и замирает в улье, потому что пчела там не сущность. Так и сделано в прототипе;
   - либо нектар выдавать не за каждый визит к нашему растению (например, за второй), чтобы пчела успела сделать 2 визита за один вылет. Это меняет ванильное поведение мёда; не проверялось.
2. **Пчела выходит из улья новой сущностью**: UUID вырезается в `removeIgnoredBeeTags`. Нельзя хранить ссылки на пчелу по UUID (например, «последняя пчела» в BE растения). `EntityJoinLevelEvent` срабатывает снова, и цель добавляется новой сущности, это нормально.
3. **`GoalSelector` перепроверяет `canContinueToUse` раз в 2 тика, а `tick()` при `requiresUpdateEveryTick()` вызывается каждый тик.** После завершения визита внутри `tick()` следующий `tick()` придёт ещё до `stop()`. Первый прогон упал с NPE (`Ticking entity`) на `Vec3.atBottomCenterOf(null)`. В боевой цели `tick()` должен быть идемпотентным после визита.
4. Ванильная цель и наша с одинаковым приоритетом 4 и флагом `MOVE` друг друга не вытесняют: при равном приоритете замена не происходит. Работает та, что стартовала первой. Зависаний нет (0 тайм-аутов).
5. Наблюдение для авторов GameTest: в этом окружении пол шаблона из `y=0` структуры оказался на относительной `y=1` (`helper.getBlockState(...)` вернул траву на `y=1`). Блоки ставятся на `y=2`. Сначала улей стоял «в полу», выход был закрыт, и пчёлы не выходили.
6. `GameTestServer` тикает без ограничения 20 TPS, поэтому длинные (многодневные) GameTest в CI дешёвые (~2 мин на 3 игровых дня с 10 пчёлами).

## Что не проверено

- Клиент (`runClient`) не запускался: частицы и визуальное зависание не проверены.
- Настоящий `BloomIndex` (ADR-0008) и лимит ≤ 64 запросов на уровень за тик не реализованы. В прототипе статический `Set<BlockPos>` и ограничение «раз в 20 тиков» на пчелу.
- Сохранение и перезагрузка **мира** (выгрузка чанка с пчёлами в улье) напрямую не проверялись. Проверен save/load самого BE улья (`saveWithoutMetadata` → `load`), а это тот же путь NBT.
- Совместимость с другими модами пчёл (Productive Bees и т. п.) не проверялась.
- Условие «≤ 2 визитов» фактически не проверяется: каждый визит к нашему растению заменяет пыльцу, а визиты к ванильным цветам пыльцу не трогают, поэтому `visits` всегда 0. Как считать ванильные визиты — вопрос к GD.
- В смешанном поле ванильный нектар был 1 на 102 наших визита. Маки стояли в углах дальше 5 блоков от ульев. Доля ванильных визитов при соседстве цветов и цветущих растений не измерялась: это вопрос баланса, а не конфликта целей.
- Бонус `bee_growables` на наши блоки: тестовый блок в тег не входит, `BeeGrowCropGoal` его не трогал. Отдельного теста нет.

## Предлагаемые правки документов

1. `docs/05-architecture.md` §10.3 п. 1:
   - старое: «(`goalSelector.addGoal`; поле `Mob.goalSelector` в 1.20.1 доступно `[ПРОВЕРИТЬ]`, иначе access transformer).»
   - новое: «(`goalSelector.addGoal`; поле `Mob.goalSelector` в 1.20.1 — `public final`, AT не нужен; S2). Цель добавляется при каждом входе сущности в мир, включая выход из улья, с проверкой `getAvailableGoals()` против дубля.»
2. `docs/05-architecture.md` §10.3 п. 3:
   - старое: «при свежей пыльце (≤ 2 визитов, ≤ 1200 тиков)»
   - новое: «при свежей пыльце (≤ 2 визитов, ≤ 1200 тиков **жизни пчелы вне улья**: счётчик в `BeePollen` растёт в `LivingTickEvent` и не идёт, пока пчела в улье; по мировому времени окно недостижимо, S2)»
   - старое: «(`setHasNectar(true)`; метод приватный `[ПРОВЕРИТЬ]` → access transformer)»
   - новое: «(`setHasNectar(true)`; метод package-private → AT `public net.minecraft.world.entity.animal.Bee m_27919_(Z)V`, S2)»
3. `docs/05-architecture.md` §10.3 п. 4:
   - старое: «улей хранит NBT сущности вместе с `ForgeCaps` `[ПРОВЕРИТЬ]`.»
   - новое: «улей хранит NBT сущности вместе с `ForgeCaps` (`BeehiveBlockEntity#removeIgnoredBeeTags` их не вырезает; S2). UUID вырезается: из улья выходит новая сущность, поэтому ссылки на пчёл по UUID не хранить.»
4. `docs/05-architecture.md` §10.3 п. 6: дописать в конце «S2: конфликта нет (0 зависаний за 3 игровых дня), M5 не нужен.»
5. `docs/05-architecture.md` §16, абзац «Access transformer»:
   - старое: «`Bee#setHasNectar` `[ПРОВЕРИТЬ]`, `Biome#getHeightAdjustedTemperature`, поля `StructureTemplatePool` (`templates`, `rawTemplates`), при необходимости `Mob#goalSelector`.»
   - новое: «`Bee#setHasNectar` (`m_27919_`, подтверждено S2), `Biome#getHeightAdjustedTemperature`, поля `StructureTemplatePool` (`templates`, `rawTemplates`). `Mob#goalSelector` публичен, в AT не нужен (S2).»
6. `docs/05-architecture.md` §20.1, строка R2: в столбец мер дописать «S2: риск не подтвердился».
7. `docs/adr/0009-bee-pollination-goal.md`, «Решение»:
   - «`setHasNectar(true)` (access transformer `[ПРОВЕРИТЬ]`)» → «`setHasNectar(true)` (AT `m_27919_`, подтверждено S2)»;
   - «переживает улей вместе с `ForgeCaps` `[ПРОВЕРИТЬ]`» → «переживает улей вместе с `ForgeCaps` (подтверждено S2); возраст пыльцы считается в тиках вне улья»;
   - статус: «Принято (S2 подтвердил, 2026-10-30)».
8. `docs/adr/0014-mixin-policy.md`, строка M5: «Запасной (S2)» → «Не нужен (S2 подтвердил путь без mixin)».
9. `docs/01-gdd.md` §4.3, Output п. 1 (решение GD):
   - старое: «собранную ≤ 2 визитов и ≤ 1200 тиков назад»
   - новое: «собранную ≤ 2 визитов и ≤ 1200 тиков полёта назад (время в улье не считается)»
   - в «Tuning levers» уточнить «окно памяти пыльцы (тики вне улья)».
10. `ci/allowlists/accesstransformer.txt`: в комментарии убрать `Mob#goalSelector` из кандидатов.

### Задачи `06-tasks.md`, которые надо пересмотреть

- **WR-142**: в приёмку добавить:
  - GameTest «пыльца стареет только вне улья: после входа в улей и выхода `ageOutside` не вырос»;
  - GameTest «3 игровых дня, 10 пчёл, 4 улья: доноры в NBT BE и мёд» (`GameTestServer` прогоняет его ~2 мин);
  - требование идемпотентного `tick()` после визита.

  Строку AT `m_27919_` заводит WR-142 (или WR-013 для allowlist). Переписывать на M5 не нужно.
- **WR-013**: M5 в allowlist mixin не добавлять.
- **WR-021**: закрыть как «подтверждено с уточнением окна пыльцы».
