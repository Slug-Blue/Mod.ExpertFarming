# S12 · WR-031: момент `VillagerTradesEvent` / `WandererTradesEvent`

Ветка: `spike/wr-031-trade-events`. Код spike: `mod/src/gametest/java/io/github/slugblue/wildroots/gametest/spike/s12/` и `mod/src/gametest/resources/data/wildroots/spike_trades/carrot_line.json`. Он лежит только в source set `gametest` и в jar не попадает. Ссылки: архитектура §11.3, §20.1 R10.

## Вопрос

1. Срабатывают ли `VillagerTradesEvent`/`WandererTradesEvent` до загрузки reload listener'ов: по исходникам Forge `VillagerTradingManager` и по логам.
2. Берут ли ленивые `ItemListing` сделки из снимка определений, который загрузил reload listener.
3. Получает ли **новый** фермер сделки «линии моркови» из снимка после `/reload` и после перезапуска. Заглушка: JSON в data → reload listener → снимок.

## Вердикт

| Подпункт | Вердикт |
|---|---|
| События срабатывают до загрузки reload listener'ов (риск R10) | **ОПРОВЕРГНУТО**, риска нет. События постятся в `ServerAboutToStartEvent`, а первая загрузка датапаков (`AddReloadListenerEvent` → `apply`) идёт раньше, ещё до создания сервера. В логе `snapshot gen at event=1` |
| События повторяются на `/reload` | **Нет**. Они срабатывают один раз за запуск сервера; на `/reload` таблицы `VillagerTrades.TRADES` / `WANDERING_TRADER_TRADES` не перестраиваются (`farmerEvents still 1`) |
| Ленивый `ItemListing` читает снимок в `getOffer`, а не в момент события | **ПОДТВЕРЖДЕНО** (фермер и торговец) |
| Новый фермер после `/reload` получает сделку из нового снимка | **ПОДТВЕРЖДЕНО** (7 → 3 моркови) |
| Новый фермер после перезапуска получает сделку из снимка | **ПОДТВЕРЖДЕНО** (3 моркови из датапака мира) |
| Уже созданные предложения старого фермера | Не меняются (7 моркови после `/reload`): `offers` хранятся в жителе. Это ожидаемо и согласуется с §11.3 («без пересборки списков» относится к новым предложениям) |

Итог: **ПОДТВЕРЖДЕНО** — способ из §11.3 (ленивые листинги на снимке) работает; R10 можно закрыть.

## Доказательства

### Исходники Forge и Minecraft

- `net.minecraftforge.common.VillagerTradingManager` (`forge-1.20.1-47.4.28-sources.jar`). В статическом инициализаторе копирует ванильные `VillagerTrades.TRADES` и `WANDERING_TRADER_TRADES` в `VANILLA_TRADES`/`WANDERER_TRADES`. Метод `loadTrades(ServerAboutToStartEvent)` вызывает `postWandererEvent()`, затем `postVillagerEvents()`: для каждой профессии из `ForgeRegistries.VILLAGER_PROFESSIONS` создаются **новые** изменяемые списки из копии, постится событие, результат записывается обратно в глобальный `VillagerTrades.TRADES`. Поэтому повторный запуск сервера (в том числе повторный вход в мир в одиночной игре) не дублирует сделки.
- `ForgeMod` (там же, строка 463): `MinecraftForge.EVENT_BUS.addListener(VillagerTradingManager::loadTrades)` — единственная точка вызова; на `/reload` ничего не подписано.
- javap jar `…_mapped_parchment_2023.09.03-1.20.1.jar`: `ServerLifecycleHooks.handleServerAboutToStart` вызывается из `DedicatedServer.initServer()` и `GameTestServer.initServer()`. `WorldLoader.load` (в нём `ReloadableServerResources.loadResources` и reload listener'ы) вызывается раньше: в `net.minecraft.server.Main.main` (выделенный сервер) и в `GameTestServer.create`, ещё до создания объекта сервера. `/reload` идёт через `MinecraftServer.reloadResources` → `ReloadableServerResources.loadResources` и `ServerAboutToStartEvent` не постит.

### GameTest

`TradeEventsSpikeTests.lazyListingsFollowSnapshot`, команда `JAVA_HOME="C:/Users/user/.jdks/temurin-17" ./gradlew :mod:runGameTestServer`, два запуска подряд на одном мире `mod/run/world`.

Прогон 1 (фаза FIRST): в jar `buy_count = 7`. Тест пишет датапак мира `world/datapacks/s12_override` (`buy_count = 3`) и выполняет настоящую команду `reload` через `server.getCommands()`.
```
[main] [S12] reload listener apply: snapshot gen=1 defs={carrot_line=...buyCount=7...} thread=main
[Server thread] [S12] ServerAboutToStartEvent (our listener, snapshot gen now 1)
[Server thread] [S12] WandererTradesEvent #1 snapshot gen at event=1
[Server thread] [S12] VillagerTradesEvent FARMER #1 snapshot gen at event=1 thread=Server thread
[S12] reload listener apply: snapshot gen=2 defs={carrot_line=...buyCount=3...} thread=Server thread
[S12] FIRST: after /reload gen=2 selectedPacks=[vanilla, mod:forge, mod:wildroots, file/s12_override]: new farmer -> 3 carrots, old farmer keeps 7 carrots, wanderer listing -> 3, farmerEvents still 1
All 2 required tests passed :)
```
Прогон 2 (фаза RESTART, датапак остался в мире):
```
[main] [S12] reload listener apply: snapshot gen=1 defs={carrot_line=...buyCount=3...} thread=main
[Server thread] [S12] VillagerTradesEvent FARMER #1 snapshot gen at event=1 thread=Server thread
[S12] RESTART: new farmer -> 3 carrots, wanderer listing -> 3; override pack deleted
All 2 required tests passed :)
```
Устройство прототипа (`TradeSpike`):
- `SimplePreparableReloadListener` читает `data/*/spike_trades/*.json` и атомарно подменяет неизменяемый `Snapshot(generation, defs)`.
- `VillagerTradesEvent` (`HIGHEST`, только `FARMER`) очищает уровни 1–5 и кладёт на уровень 1 `LazyListing("carrot_line")`.
- `WandererTradesEvent` добавляет тот же листинг в generic.
- `LazyListing.getOffer` читает `snapshot` в момент вызова; если определения нет, отдаёт ванильную сделку (22 моркови).
- Сделку торговца тест проверяет прямым вызовом нашего листинга из `VillagerTrades.WANDERING_TRADER_TRADES`: торговец берёт 5 случайных листингов, и проверка через `getOffers()` была бы нестабильной.

Попутное наблюдение: в GameTest-сервере `apply` при `/reload` выполнился на `Server thread`, а при старте — на `main`. Значит, снимок должен быть `volatile` или атомарной ссылкой (как в §8.2 п. 6).

## Что не проверено

- Интегрированный сервер (одиночная игра, повторный вход в мир) — клиент не запускался. По исходникам порядок тот же (`WorldOpenFlows` грузит `WorldStem` до создания `IntegratedServer`), но в логах не подтверждено.
- Выделенный сервер `runServer` — не запускался (EULA). Порядок подтверждён байткодом `Main.main` / `DedicatedServer.initServer` и аналогичным путём `GameTestServer`.
- Взаимодействие с другими модами, которые меняют сделки фермера на обычном приоритете, — не проверялось.
- Повышение уровня жителя (`increaseMerchantCareer` → `updateTrades`) — механизм тот же (`getOffer` в момент генерации), но отдельно не прогонялся.

## Предлагаемые правки документов

1. **`docs/05-architecture.md` §20.1, строка R10**. Было: `| R10 | VillagerTradesEvent срабатывает до загрузки наших определений | Средняя / низкое | Ленивые ItemListing читают снимок при генерации предложения | S12 |`. Стало: `| R10 | Сделки, построенные в VillagerTradesEvent, не видят изменений определений после /reload (событие срабатывает один раз в ServerAboutToStartEvent, после первой загрузки датапаков) | Проверено / низкое | Ленивые ItemListing читают снимок при генерации предложения; уже сгенерированные предложения жителя не меняются | S12: подтверждено |`.
2. **§11.3, пункт «Фермер»**: после `Наши листинги **ленивые**: …` дописать `События постятся один раз за запуск сервера (Forge VillagerTradingManager, ServerAboutToStartEvent), после первой загрузки reload listener'ов, и на /reload не повторяются. Таблицы перестраиваются из копии ванильных при каждом запуске, поэтому повторный вход в мир сделки не дублирует. Предложения, которые житель уже сгенерировал, хранятся в его NBT и после /reload не меняются, а новые берутся из нового снимка (S12).`
3. **§8.2 п. 6**: дописать `apply выполняется на main при старте и на Server thread при /reload, поэтому ссылка на снимок volatile/атомарная.`

Задачи `06-tasks.md`: **WR-167, WR-169** можно стартовать без изменений по сути. Как критерий приёмки предлагаю двухфазный GameTest из этого spike: `/reload` с датапаком мира → новый фермер получает новую сделку, и перезапуск → сделка из снимка. Пересмотр SA не нужен.
