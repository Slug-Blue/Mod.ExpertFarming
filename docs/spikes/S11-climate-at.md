# Spike S11 (WR-030): AT `Biome#getHeightAdjustedTemperature`

Ветка: `spike/wr-030-climate-at` (от `main` 0f1e3da). Код spike:
- AT: `mod/src/main/resources/META-INF/accesstransformer.cfg`, allowlist `ci/allowlists/accesstransformer.txt`;
- `mod/src/main/java/io/github/slugblue/wildroots/spike/ClimateService.java`;
- `mod/src/gametest/java/io/github/slugblue/wildroots/gametest/spike/ClimateSpikeGameTests.java`.

## Вопрос

1. Открывается ли `Biome#getHeightAdjustedTemperature(BlockPos)` через AT (строка в `accesstransformer.cfg`, проходящая `ci/check-sources.sh`)?
2. Даёт ли `ClimateService.temperature(level, pos)` то же значение, что ванильный метод (GameTest на 5 биомах и 3 высотах)?
3. Совпадает ли это с фильтром worldgen и с цифрами `docs/03-worldgen.md` §2.2?

## Вердикт

| # | Подпункт | Вердикт |
|---|---|---|
| 1 | Метод `private` в 1.20.1, открывается AT-строкой `public net.minecraft.world.level.biome.Biome m_47528_(Lnet/minecraft/core/BlockPos;)F`; код в `main` компилируется и вызывается в рантайме; `ci/check-sources.sh` проходит с обновлённым allowlist | **ПОДТВЕРЖДЕНО** |
| 2 | `ClimateService.temperature` побитово (`Float.compare == 0`) совпадает с ванильным `Biome#getTemperature(BlockPos)` (закрытый кэширующий метод, через который ваниль решает снег, лёд и осадки) — 5 биомов × 3 высоты × 3 точки (x, z) = 45 точек, плюс вариант `(level, pos)`; `coldEnoughToSnow(pos) == (T < 0.15)` во всех точках | **ПОДТВЕРЖДЕНО** |
| 3а | Базовые температуры таблицы worldgen §2.2 (36 биомов) совпадают с реестром биомов 1.20.1 | **ПОДТВЕРЖДЕНО** |
| 3б | «Выше Y = 80 минус 0.00125 за блок, луг на Y 150 ≈ 0.41» | **ПОДТВЕРЖДЕНО с уточнением**: к линейной поправке добавлен шум `TEMPERATURE_NOISE` (смещение до ±8 блоков, то есть до ±0.01 по T; наблюдалось до 0.00895). Луг на (0, 150, 0) = 0.4125. У биомов с `temperature_modifier: frozen` (`frozen_ocean`, `deep_frozen_ocean`) базовая T сама зависит от шума: `frozen_ocean` с base 0.0 на (0, 64, 0) даёт 0.2 |
| 3в | Одна функция для роста и worldgen | **ПОДТВЕРЖДЕНО по построению**: `ClimateService` принимает `LevelReader`, это и `ServerLevel` (рост), и `WorldGenLevel` (`PlacementContext#getLevel()` в фильтре размещения); температура зависит только от `Holder<Biome>` и `BlockPos`. Сам фильтр worldgen в spike не писался (см. «Что не проверено») |

## Доказательства

### Команды

```
bash ci/check-sources.sh
→ check-sources: OK (…/agent-ae47358008d0a46e5)
JAVA_HOME="/c/Users/user/.jdks/temurin-17" ./gradlew :mod:runGameTestServer
→ [GameTestServer]: All 3 required tests passed :)   (EXIT 0)
```

ForgeGradle при сборке пересобрал Minecraft с AT (`--inJar …forge-1.20.1-47.4.28-injected.jar … Transformer file …\META-INF\accesstransformer.cfg`), после этого вызов закрытого метода из `ClimateService` в `main` компилируется. Без AT метод `private` — это видно по `javap -p` на `forge-1.20.1-47.4.28_mapped_parchment_2023.09.03-1.20.1.jar`: `private float getHeightAdjustedTemperature(net.minecraft.core.BlockPos);`. SRG-имена взяты из `mod/build/createSrgToMcp/output.srg`: `m_47528_` → `getHeightAdjustedTemperature`, `m_47505_` → `getTemperature`.

GameTest (`io.github.slugblue.wildroots.gametest.spike.ClimateSpikeGameTests`):
- `s11FiveBiomesThreeHeights`: `plains`, `meadow`, `desert`, `taiga`, `frozen_ocean` × Y 64 / 120 / 200 × (0, 0), (1000, −2000), (12345, 6789). Сравнение с `Biome#getTemperature` (вызов через `ObfuscationReflectionHelper` **только в тесте spike**: в коде мода рефлексия запрещена SEC-43). Проверка `|T − (base − 0.00125·(y − 80))| ≤ 0.01` для не-frozen биомов. Вариант `ClimateService.temperature(level, pos)` в точке теста;
- `s11WorldgenTableBaseTemperatures`: 36 биомов таблицы §2.2 против `Biome#getBaseTemperature()`.

### Выдержка лога (`mod/run/logs/latest.log`)

```
[S11] worldgen .2 table: 36 biomes checked, mismatches: none
[S11] minecraft:plains base=0.8 at x=0,z=0: y64=0,8000 y120=0,7500 y200=0,6500
[S11] minecraft:meadow base=0.5 at x=0,z=0: y64=0,5000 y120=0,4500 y200=0,3500
[S11] minecraft:desert base=2.0 at x=0,z=0: y64=2,0000 y120=1,9500 y200=1,8500
[S11] minecraft:taiga base=0.25 at x=0,z=0: y64=0,2500 y120=0,2000 y200=0,1000
[S11] minecraft:frozen_ocean base=0.0 at x=0,z=0: y64=0,2000 y120=0,1500 y200=0,0500
[S11] meadow y150 = 0.4125; compared 45 points, ClimateService == Biome#getTemperature for all; max |T - (base - 0.00125*(y-80))| = 0.008952171
[S11] level variant at BlockPos{x=15, y=-58, z=1} (minecraft:deep_dark): 0.8
```

(«worldgen .2» — символ § потерялся в кодировке лога. Шум в (0, 0) равен 0, поэтому в строках x = 0 видна чистая линейная поправка.)

### Формула (байткод `Biome#getHeightAdjustedTemperature`, `javap -c -p`)

```
t = climateSettings.temperatureModifier.modifyTemperature(pos, getBaseTemperature());
if (pos.getY() > 80) t -= (TEMPERATURE_NOISE.getValue(x/8f, z/8f, false) * 8f + pos.getY() - 80f) * 0.05f / 40f;
return t;
```

`Biome#getTemperature(BlockPos)` (`private`, помечен `@Deprecated`) — это та же функция плюс `ThreadLocal`-кэш `Long2FloatLinkedOpenHashMap` на 1024 позиции. Поэтому прямой вызов `getHeightAdjustedTemperature` даёт тот же результат без кэша.

## Что не проверено

- **Настоящий фильтр размещения worldgen** (`PlacementModifier` с `PlacementContext#getLevel()` при генерации чанков). В GameTest-мире своя генерация, свой фильтр в spike не писался. Равенство следует из того, что функция зависит только от `(Holder<Biome>, BlockPos)`. Возможное расхождение — только в том, **какой** биом вернёт `getBiome(pos)` в `WorldGenRegion` и в `ServerLevel`: оба идут через `BiomeManager` с тем же сидом, но сравнением это не подтверждалось.
- **Совместимость с модами сезонов и климата**: если чужой mixin меняет `Biome#getTemperature` (кэширующий путь), а мы вызываем `getHeightAdjustedTemperature`, наши значения разойдутся с их значениями. Модов для проверки нет.
- Клиентская сторона не проверялась (на клиенте `ClimateService` не нужен).
- Отдельный JUnit не писался: метод требует загруженного реестра биомов, поэтому проверка сделана GameTest.

## Предлагаемые правки документов

1. **`docs/05-architecture.md` §11.8**, строка таблицы «Температура с поправкой на высоту».
   Старое: «`Biome.getHeightAdjustedTemperature` закрыт `[ПРОВЕРИТЬ]` → AT. Одна функция `ClimateService.temperature(level, pos)` для роста и worldgen»
   Новое: «`Biome#getHeightAdjustedTemperature` (SRG `m_47528_`) в 1.20.1 `private` → AT (S11). `ClimateService.temperature(LevelReader, BlockPos)` — одна функция для роста и worldgen (`ServerLevel` и `PlacementContext#getLevel()`); побитово совпадает с ванильным `Biome#getTemperature` (тем же значением ваниль решает снег и лёд)»
2. **`docs/05-architecture.md` §16**, абзац «Access transformer»: после «`Biome#getHeightAdjustedTemperature`» добавить «(`public net.minecraft.world.level.biome.Biome m_47528_(Lnet/minecraft/core/BlockPos;)F`, проверено S11)».
3. **`docs/03-worldgen.md` §2.2**, абзац над таблицей.
   Старое: «Базовая `temperature` из `data/minecraft/worldgen/biome/*.json` `[ПРОВЕРИТЬ]` по ванильным JSON. Поправка на высоту (принята в GDD §3.2): выше Y = 80 температура падает на `0.00125` за блок (луг на Y 150 ≈ 0.41) `[ПРОВЕРИТЬ]`.»
   Новое: «Базовая `temperature` ванильных биомов 1.20.1 (таблица сверена с реестром биомов, S11). Поправка на высоту (GDD §3.2) — ванильная `Biome#getHeightAdjustedTemperature`: выше Y = 80 температура падает на `0.00125` за блок, плюс шум ±0.01 (смещение высоты на ±8 блоков по `TEMPERATURE_NOISE(x/8, z/8)`); луг на Y 150 = 0.4125 ± 0.01. У биомов с `temperature_modifier: frozen` (`frozen_ocean`, `deep_frozen_ocean`) базовая T тоже зависит от шума (0.0 → до 0.2). Края окон T проверять с допуском 0.01.»
4. **`ci/allowlists/accesstransformer.txt`**: в `main` добавить строку `public net.minecraft.world.level.biome.Biome m_47528_(Lnet/minecraft/core/BlockPos;)F` в задаче WR-132 (не в spike). Комментарий в шапке allowlist поправить: `Biome#getHeightAdjustedTemperature` → «SRG `m_47528_` (S11)».

### Задачи `06-tasks.md`, которые надо пересмотреть

- **WR-132** (среда и рост): блокировка снята. В приёмку добавить AT-строку и allowlist (п. 4 выше), сигнатуру `ClimateService.temperature(LevelReader, BlockPos)` и GameTest «совпадает с `Biome#getTemperature` на 5 биомах × 3 высотах». Для сравнения в тесте нужен доступ к закрытому `getTemperature`; без рефлексии остаётся проверка через `coldEnoughToSnow`/`warmEnoughToRain`, поэтому рефлексию в `src/gametest` надо явно разрешить или заменить проверкой формулы.
- **WR-150** (`wild_patch`): блокировка снята. Фильтр окна T вызывает `ClimateService.temperature(context.getLevel(), pos)`. Для краёв окон учесть шум ±0.01 выше Y 80 и биомы `frozen`.
