# Spike S1 (WR-020): встроенные обязательные пакеты линий

Ветка: `spike/wr-020-line-packs`. Код spike: `mod/src/gametest/java/io/github/slugblue/wildroots/gametest/spike/LinePackSpike.java`, `LinePackSpikeGameTests.java`, пакет линии `mod/src/gametest/resources/resourcepacks/lines/spike_carrot/`, «конкурент» в `mod:wildroots`: `mod/src/gametest/resources/data/minecraft/recipes/golden_carrot.json`. Forge 47.4.28, Minecraft 1.20.1, Parchment 2023.09.03.

## Вопрос

1. Включается ли встроенный датапак из `AddPackFindersEvent` (`PackSource.BUILT_IN`, `required = true`, `Pack.Position.TOP`) в новом и в **существующем** мире (мир создан без пакета → пакет добавлен → перезапуск → пакет активен)?
2. Применяются ли его biome modifiers `forge:add_features` / `forge:remove_features`?
3. Выигрывает ли его перекрытие `minecraft:golden_carrot` у ресурсов модов (в документах — `mod_resources`)?
4. Поддерживает ли Forge 47.x `forge:conditions` в biome modifier (и в других записях datapack-реестров)?

## Вердикт

| # | Подпункт | Вердикт |
|---|---|---|
| 1а | Новый мир: пакет выбран, `required`, стоит последним (выше всех `mod:*`) | **ПОДТВЕРЖДЕНО** |
| 1б | Существующий мир, чей `level.dat` пакет не знает: пакет добавляется автоматически (`Found new data pack …, loading it automatically`), в итоговый `DataPackConfig` (то, что запишется в `level.dat`) попадает | **ПОДТВЕРЖДЕНО** (GameTest на коде загрузки + чтение байткода; настоящий перезапуск выделенного сервера не делался — см. «Что не проверено») |
| 1в | Даже если пакет в `level.dat` в `Disabled`, он всё равно выбран (обязательный) | **ПОДТВЕРЖДЕНО** |
| 2 | `forge:add_features` и `forge:remove_features` из пакета линии применяются к биому | **ПОДТВЕРЖДЕНО** |
| 3 | Перекрытие `minecraft:golden_carrot` выигрывает у пакета мода | **ЧАСТИЧНО**: выигрывает в новом мире и при первом добавлении пакета в старый мир. **Не** выигрывает у мода, установленного **после** того, как пакет линии записан в `level.dat`: пакет нового мода дописывается выше. `Position.TOP` действует только в момент вставки, порядок из `level.dat` сохраняется |
| 4 | `forge:conditions` в biome modifier и placed feature (datapack-реестры) | **ПОДТВЕРЖДЕНО**: ключ `forge:conditions`, работают `forge:false`, `forge:mod_loaded` и своё `wildroots:line_loaded` (через `CraftingHelper.register`). В рецептах ключ — `conditions` (без префикса), своё условие тоже работает |

Попутная находка: **пакета `mod_resources` в Forge 47.4.28 нет**. У каждого мода свой пакет `mod:<modid>` (`mod:forge`, `mod:wildroots`), а `mod_resources` из старых `level.dat` выдаёт `Missing data pack mod_resources`. Все упоминания `mod_resources` в документах нужно заменить.

## Доказательства

### Команда

```
JAVA_HOME="/c/Users/user/.jdks/temurin-17" ./gradlew :mod:runGameTestServer
→ [GameTestServer]: All 7 required tests passed :)   (EXIT 0)
```

GameTest (класс `io.github.slugblue.wildroots.gametest.spike.LinePackSpikeGameTests`):
- `s1PackSelectedAboveModResources`: пакет выбран, `isRequired()`, индекс выше `mod:wildroots`, пакета `mod_resources` нет;
- `s1GoldenCarrotOverrideWins`: у `RecipeManager.byKey(minecraft:golden_carrot)` результат 3 (ваниль 1, `mod:wildroots` 2, пакет линии 3);
- `s1BiomeModifiersApplied`: в `Biome#getGenerationSettings().features()` у `plains` есть `wildroots:spike_marker` и нет `minecraft:patch_pumpkin`; у `forest` (контроль) `patch_pumpkin` остался;
- `s1ForgeConditionsInDatapackRegistries`: реестр `ForgeRegistries.Keys.BIOME_MODIFIERS` содержит `add_marker`, `cond_line` (условия `forge:mod_loaded` + `wildroots:line_loaded(spike_carrot)`), не содержит `cond_false` (`forge:false`) и `cond_line_missing` (`line_loaded(nope)`); `placed_feature` `spike_pf_false` с `forge:false` не загружен; у `plains` есть фича из `cond_line` и нет фичи из `cond_false`;
- `s1RecipeConditionsKey`: рецепт с `"conditions": [{"type":"wildroots:line_loaded","line":"spike_carrot"}]` есть, с `line: "nope"` — нет;
- `s1ExistingWorldGetsRequiredPack`: четыре варианта старого `level.dat` (ниже);
- `modIsLoaded` (smoke из `main`).

### Выдержки лога (`mod/run/logs/latest.log`, 2-й прогон; строки укорочены)

```
[S1] AddPackFindersEvent: line pack wildroots:line/spike_carrot from resourcepacks/lines/spike_carrot -> required=true
[S1] new world selected packs (bottom->top): [vanilla, mod:forge, mod:wildroots, wildroots:line/spike_carrot]
[S1] minecraft:golden_carrot result count = 3 (vanilla 1, mod_resources 2, line pack 3)
[S1] plains has wildroots:spike_marker=true, minecraft:patch_pumpkin=false; forest patch_pumpkin=true
[S1] biome_modifier: add_marker=true cond_false=false cond_line=true cond_line_missing=false; placed_feature spike_pf_false=false; ...
[MinecraftServer]: Missing data pack mod_resources
[MinecraftServer]: Found new data pack wildroots:line/spike_carrot, loading it automatically
[S1] existing world (unknown): ... -> enabled=[vanilla, mod:forge, mod:wildroots, wildroots:line/spike_carrot] ... golden_carrot from wildroots:line/spike_carrot count=3 add_marker present=true
[S1] existing world (disabled): level.dat ... disabled=[wildroots:line/spike_carrot] -> enabled=[..., wildroots:line/spike_carrot] ... count=3
[S1] existing world (stored-mod-installed-later): level.dat enabled=[vanilla, mod:forge, wildroots:line/spike_carrot] -> selected=[vanilla, mod:forge, wildroots:line/spike_carrot, mod:wildroots] golden_carrot from main count=2
```

### Как проверен «существующий мир»

GameTest-сервер дважды на одной папке мира **не подходит**. `GameTestServer.create` (байткод) не читает `level.dat`, а каждый раз собирает `new WorldDataConfiguration(new DataPackConfig(packRepository.getAvailableIds(), List.of()), …)` («все доступные пакеты»). Поэтому проверен сам путь загрузки существующего мира:

1. Чтение байткода (`javap -c -p` по `forge-1.20.1-47.4.28_mapped_parchment_2023.09.03-1.20.1.jar` из `mod/build/fg_cache`; исходников Minecraft с именами Parchment в кэше нет):
   - `net.minecraft.server.Main#main` → `ServerPacksSource.createPackRepository(Path)` (Forge-патч: внутри `ModLoader.get().postEvent(new AddPackFindersEvent(PackType.SERVER_DATA, …))`) → `Main#loadOrCreateConfig` берёт `LevelStorageAccess#getDataConfiguration()` (то есть `DataPackConfig` из `level.dat`) → `WorldLoader.PackConfig#createResourceManager` → `MinecraftServer.configurePackRepository(repo, dataPackConfig, safeMode, flags)`;
   - `MinecraftServer#configurePackRepository`: Forge сначала вызывает `ResourcePackLoader.loadResourcePacks` и `DataPackConfig#addModPacks(ForgeHooks.getModPacks())` (дописывает отсутствующие `mod:*` **в конец** `enabled`). Потом идут `enabled` из конфига; затем для каждого доступного пакета, которого нет в `Disabled`, при `PackSource#shouldAddAutomatically()` (у `BUILT_IN` true) — лог «Found new data pack …» и добавление **в конец**. Затем `PackRepository#setSelected`;
   - `PackRepository#rebuildSelected`: каждый `Pack#isRequired()`, которого нет в списке, вставляется через `Pack.Position#insert` — даже если он в `Disabled`. Порядок пакетов, которые уже есть в списке, **не меняется**.
2. GameTest `s1ExistingWorldGetsRequiredPack` вызывает ровно эти методы (`ServerPacksSource.createPackRepository(tmp)` + `MinecraftServer.configurePackRepository(repo, <DataPackConfig старого level.dat>, false, FeatureFlags.DEFAULT_FLAGS)`), затем открывает `MultiPackResourceManager` на выбранных пакетах и смотрит, из какого пакета придёт `minecraft:recipes/golden_carrot.json` и виден ли `forge/biome_modifier/add_marker.json`.

### Условия: где обрабатываются

- Datapack-реестры: Forge-патч `RegistryDataLoader#loadRegistryContents` вызывает `ICondition.shouldRegisterEntry(JsonElement)`, а тот — `CraftingHelper.processConditions(obj, "forge:conditions", IContext.TAGS_INVALID)` (исходник `net/minecraftforge/common/crafting/conditions/ICondition.java` из `forge-1.20.1-47.4.28-sources.jar`). Условия, которые смотрят теги (`forge:tag_empty`), здесь не годятся (`TAGS_INVALID`).
- Рецепты: `RecipeManager#apply` → `CraftingHelper.processConditions(json, "conditions", context)`, лог «Skipping loading recipe {} as it's conditions were not met».

## Что не проверено

- **Настоящий перезапуск выделенного сервера** (`runServer` на мире, созданном без пакета). Для этого нужно принять EULA; по правилам spike этого не делали. Путь загрузки воспроизведён вызовом тех же методов (выше), но запись нового `DataPackConfig` в `level.dat` и повторная загрузка не наблюдались.
- Клиент и одиночная игра (`CreateWorldScreen`, `runClient`): не запускались. Байткод показывает, что `CreateWorldScreen` тоже вызывает `AddPackFindersEvent`.
- Фактическая генерация фичи в чанках: GameTest-мир плоский. Проверено только, что модификатор попал в `getGenerationSettings()` биома (по нему генерирует `ChunkGenerator`).
- Видимость и поведение пакета в `/datapack list|disable`. Байткод `rebuildSelected` показывает, что обязательный пакет снова вставляется, но командой это не проверялось.
- Условия в GLM (`global_loot_modifiers`) и в достижениях — вне вопроса S1.

## Предлагаемые правки документов

1. **`docs/05-architecture.md` §9.2**, второй пункт списка.
   Старое: «Пакеты регистрируются как `PackSource.BUILT_IN`, `required = true`, `Position.TOP` (выше `mod_resources`, чтобы перекрытие `minecraft:bread` и т. п. выигрывало у других модов). Игрок их не выключает. `[ПРОВЕРИТЬ]`: обязательный пакет включается и в уже существующих мирах, чей `level.dat` его не знает.»
   Новое: «Пакеты регистрируются как `PackSource.BUILT_IN`, `required = true`, `Position.TOP`. В Forge 47.4.x у каждого мода свой пакет `mod:<modid>` (пакета `mod_resources` нет). Пакет линии встаёт выше всех `mod:*` в новом мире и при первом появлении в старом мире (`MinecraftServer#configurePackRepository` дописывает новые пакеты в конец; обязательный пакет выбирается, даже если он в `Disabled` у `level.dat`) — подтверждено S1. Порядок сохраняется в `level.dat`: пакет мода, установленного **позже**, встаёт выше пакета линии, и его перекрытие того же ванильного рецепта выигрывает. Это принятое ограничение: конфликт перекрытий ванильных рецептов с другим модом решает порядок установки (известная несовместимость, документируется для игроков).»
   (Если владелец не принимает ограничение — нужен отдельный ADR: переупорядочивание выбранных пакетов, например mixin в `MinecraftServer#configurePackRepository`. В списке §16 такого mixin нет.)
2. **`docs/05-architecture.md` §9.3**, таблица.
   - Строка «Рецепты, достижения», старое: «Да: ключ `"conditions"` (в 1.20.1 ещё без префикса `forge:`), свои условия через `CraftingHelper.register(IConditionSerializer)` `[ПРОВЕРИТЬ]` точное имя ключа» → новое: «Да: ключ `"conditions"` (без префикса; `RecipeManager#apply` → `CraftingHelper.processConditions(json, "conditions", ctx)`), своё условие через `CraftingHelper.register(IConditionSerializer)` — подтверждено S1 для рецептов (достижения не проверялись)».
   - Строка «Biome modifiers, placed/configured features», старое: «Не уверен; … `[ПРОВЕРИТЬ]` | **Не полагаемся:** …» → новое: «Да: ключ `"forge:conditions"` (`RegistryDataLoader#loadRegistryContents` → `ICondition.shouldRegisterEntry`, контекст `TAGS_INVALID`: условия по тегам не работают), своё `wildroots:line_loaded` работает — подтверждено S1 | Основной механизм остаётся прежним (всё лежит в пакетах линий); условия допустимы как вторичный механизм, например для FD-части линии вместо отдельного пакета `<line>_fd`».
   - Абзац под таблицей «Пример из `03-worldgen.md` §8.7 с `forge:conditions` в biome modifier поэтому **не используется**…» → «Пример из `03-worldgen.md` §8.7 с `forge:conditions` в biome modifier рабочий (S1), но по ADR-0006 тот же JSON без условий кладётся в пакет FD-линии».
3. **`docs/05-architecture.md` §9.1**, третий пункт: «перекрытия ванильных рецептов (`minecraft:golden_carrot` → тип `wildroots:quality_shaped`)» — без изменений; добавить в конец пункта: «Перекрытие выигрывает у пакетов модов, установленных до появления линии в мире (S1)».
4. **`docs/adr/0006-lines-as-builtin-datapacks.md`**:
   - «Статус»: «Пункт о поддержке `forge:conditions` в biome modifiers 47.x — `[ПРОВЕРИТЬ]` spike'ом S1.» → «Принято по итогам S1 (2026-10-30): обязательный встроенный пакет включается в новых и существующих мирах; `forge:conditions` в datapack-реестрах 47.x поддерживаются.»
   - «Последствия», «Риск»: заменить на «Риск: пакет мода, установленного после появления линии в мире, стоит выше пакета линии (порядок хранится в `level.dat`), и его перекрытие того же ванильного рецепта выигрывает (S1). Запасной путь R1 не нужен.»
   - «Альтернативы», п. 1: добавить «S1 подтвердил, что `forge:conditions` работают в biome modifiers и placed features 47.x; вариант А остаётся вторичным механизмом».
5. **`docs/03-worldgen.md` §8.7** (строка 596): «Поддержка `forge:conditions` … в Forge 47.x `[ПРОВЕРИТЬ]`. Если не поддерживается — встроенные датапаки по линиям (§16.2).» → «`forge:conditions` в JSON записей datapack-реестров (biome modifier, placed feature) в Forge 47.x поддерживаются (S1; контекст без тегов). Основной путь — встроенные датапаки по линиям (§16.2, ADR-0006).» То же — в §9.2 (строка 697) и в вопросе 1 списка в конце документа (строка 965): снять `[ПРОВЕРИТЬ]`, решение — ADR-0006.
6. Поиск по `docs/` на `mod_resources`: заменить на «пакеты модов `mod:<modid>`».

### Задачи `06-tasks.md`, которые надо пересмотреть

- **WR-124** (`LineRegistry`, `LinePackFinder`, `line_loaded`): блокировка снята. Добавить в приёмку GameTest «существующий мир» по образцу `s1ExistingWorldGetsRequiredPack` (через `ServerPacksSource.createPackRepository` + `MinecraftServer.configurePackRepository`); помнить, что порядок пакетов из `level.dat` не меняется. Условие `wildroots:line_loaded` регистрировать в `FMLCommonSetupEvent#enqueueWork` через `CraftingHelper.register` (так в spike). Оно работает и в рецептах (`conditions`), и в datapack-реестрах (`forge:conditions`). Пункт приёмки «`/datapack list` показывает его, игрок не может выключить» spike не проверял.
- **WR-129, WR-150, WR-160, WR-170**: менять не нужно, раскладка пакетов из ADR-0006 остаётся. Ссылки на `mod_resources` в описаниях заменить.
