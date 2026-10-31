# S14 · WR-033: ванильные лимиты пакетов 1.20.1 и мешочек в шалкере

Ветка `spike/wr-033-packet-limits`. Код spike одноразовый: `mod/src/gametest/java/io/github/slugblue/wildroots/gametest/spike/` (`SpikeItems`, `S14ShulkerGameTests`).

## Вопрос

1. Точные значения лимитов Minecraft 1.20.1 + Forge 47.4.28: `FriendlyByteBuf.readNbt`/`readAnySizeNbt`/`NbtAccounter`, `CompressionDecoder`, `Varint21FrameDecoder`, custom payload C→S и S→C, разбиение пакетов Forge, `NetworkHooks`, семантика `readUtf(max)`.
2. Где проверяется `Item#canFitInsideContainerItems`: клик в меню шалкера, воронка, `quickMoveStack`, bundle. Попадает ли в шалкер мешочек с `canFitInsideContainerItems() == false`, и какие пути это пропускают.

## Вердикт

| Подпункт | Вердикт |
|---|---|
| `readNbt` = 2 097 152 «байт учёта» (`NbtAccounter`), глубина ≤ 512; `readAnySizeNbt` без лимита | **ПОДТВЕРЖДЕНО**, с уточнением: учитывается оценка памяти, а не байты на проводе |
| `CompressionDecoder`: максимум распакованного 8 388 608 | **ЧАСТИЧНО**: проверка работает только на сервере (C→S). Клиент её не выполняет |
| «8 МиБ на пакет» | **ЧАСТИЧНО**: 8 МиБ — предел тела пакета до сжатия (`PacketEncoder`). Но кадр на проводе ≤ 2 097 151 байт (`Varint21*`). При выключенном сжатии (`network-compression-threshold=-1`) реальный предел — **≈ 2 МиБ несжатого пакета** |
| Custom payload C→S 32 767, S→C 1 048 576 | **ПОДТВЕРЖДЕНО** |
| Forge разбивает большие пакеты | **ЧАСТИЧНО**: `VanillaPacketSplitter` режет только пакеты > 8 МиБ и только 4 типа (рецепты, теги, достижения, login). Контейнеры и пакеты `SimpleChannel` **не** режутся |
| `readUtf(max)`: символы или байты | **Обе проверки**: сначала байты ≤ 3·max (до декодирования), затем `String.length()` ≤ max в **UTF-16 code units** (не кодовых точках) |
| Шалкер: клик (PICKUP л/п), SWAP (хотбар, offhand), QUICK_CRAFT, shift-клик, воронка, дроппер, capability BE и предмета | **ПОДТВЕРЖДЕНО**: мешочек не попадает ни одним путём, контроль (земля) попадает всеми |
| Bundle (оба направления клика) | **ПОДТВЕРЖДЕНО**: мешочек не попадает и не теряется |
| Обходные пути | Есть: `Container#canPlaceItem`/`setItem` у `ShulkerBoxBlockEntity` и `new InvWrapper(box)` пропускают мешочек (воспроизведено); креатив, команды, лут-таблицы, сторонние рюкзаки (по исходникам) |

## Доказательства

### Команды

```
JAVA_HOME="$USERPROFILE/.jdks/temurin-17" ./gradlew :mod:runGameTestServer
```
Итог прогона (`mod/run/logs/latest.log`): `All 10 required tests passed :)` (1 smoke + 9 тестов S14).

Исходники читались `javap -c -p -l` по jar `forge-1.20.1-47.4.28_mapped_parchment_2023.09.03-1.20.1.jar` из `mod/build/fg_cache` (имена Parchment, параметры из `LocalVariableTable`). Декомпилятор не использовался: зависимостей не добавлял.

### Лимиты по исходникам

| Класс#метод (Parchment) | Что делает |
|---|---|
| `FriendlyByteBuf.DEFAULT_NBT_QUOTA = 2097152`; `readNbt()` → `readNbt(new NbtAccounter(2097152L))` | Лимит одного NBT. Его вызывает `readItem()` → один `ItemStack` ≤ 2 МиБ учёта |
| `FriendlyByteBuf#readAnySizeNbt()` → `NbtAccounter.UNLIMITED` | Без лимита. На стороне сервера в C→S пакетах не использовать |
| `NbtAccounter#accountBytes(long)` → `RuntimeException("Tried to read NBT tag that was too big; tried to allocate: …")` | Считает **оценку памяти**: `CompoundTag` 48 Б, запись ≈ 68 Б + 2·длина ключа; строки — через патч Forge `NbtAccounter#readUTF` (2 + байты UTF) |
| `CompoundTag$1#load` → `"Tried to read NBT tag with too high complexity, depth > 512"` | Глубина ≤ 512 |
| `CompressionDecoder.MAXIMUM_UNCOMPRESSED_LENGTH = 8388608`; проверка в `decode` только при `validateDecompressed` | `ServerLoginPacketListenerImpl` вызывает `Connection#setupCompression(threshold, true)`, `ClientHandshakePacketListenerImpl#handleCompression` — `(threshold, false)`. Сервер режет C→S > 8 МиБ; клиент S→C не проверяет. Константа `MAXIMUM_COMPRESSED_LENGTH = 2097152` объявлена, в `decode` не используется |
| `PacketEncoder#encode`: тело пакета > 8 388 608 → `IllegalArgumentException`; если `!packet.isSkippable()` — исключение уходит в канал, соединение рвётся | `ClientboundContainerSetContentPacket` не переопределяет `isSkippable` (по умолчанию `false`) → **отключение игрока** |
| `CompressionEncoder#encode` | > 8 МиБ только пишет ERROR с дампом (Forge `PacketDump`), не режет |
| `Varint21LengthFieldPrepender.MAX_BYTES = 3`; `Varint21FrameDecoder` → `CorruptedFrameException("length wider than 21-bit")` | Кадр ≤ 2²¹ − 1 = **2 097 151 байт** (после сжатия). Без сжатия это предел несжатого пакета |
| `ServerboundCustomPayloadPacket.MAX_PAYLOAD_SIZE = 32767`, проверка в конструкторе чтения | C→S payload (в т.ч. `SimpleChannel`) ≤ 32 767 Б |
| `ClientboundCustomPayloadPacket.MAX_PAYLOAD_SIZE = 1048576` (конструктор и чтение) | S→C payload ≤ 1 048 576 Б |
| `ClientboundCustomQueryPacket`/`ServerboundCustomQueryPacket.MAX_PAYLOAD_SIZE = 1048576` | login-payload Forge ≤ 1 МиБ в обе стороны |
| `VanillaPacketSplitter`: `PROTOCOL_MAX = 8388608`, `PART_SIZE = 1048570`; `appendPackets` режет только пакеты > 8 МиБ. Вызывается из `ForgeConnectionNetworkFilter` только для `ClientboundUpdateRecipesPacket`, `ClientboundUpdateTagsPacket`, `ClientboundUpdateAdvancementsPacket`, `ClientboundLoginPacket` | Контейнеры, `SimpleChannel` и наш `DefinitionsSync` **не** разбиваются |
| `NetworkHooks#openScreen(…, Consumer<FriendlyByteBuf>)`: extra data > 32 600 или < 1 → `IllegalArgumentException`; `PlayMessages$OpenContainer` читает `readByteArray(32600)` | Доп. данные меню ≤ 32 600 Б |
| `FriendlyByteBuf#readUtf(int max)`: `readVarInt()` ≤ `max*3` → иначе `DecoderException`; затем `String.length() > max` → `DecoderException`. `readUtf()` = `readUtf(32767)`. `writeUtf` проверяет то же | См. GameTest ниже |
| `ServerGamePacketListenerImpl#handleSetCreativeModeSlot` | Проверяет только креатив, номер слота 1…45, `count ≤ 64`, флаги фич; копирует BE по `BlockEntityTag{x,y,z}`. **Содержимое NBT не проверяется** |

### GameTest (класс `S14ShulkerGameTests`)

| Тест | Что делает | Результат |
|---|---|---|
| `s14_shulkerMenu_pouchRejected` | `ShulkerBoxMenu` с mock-игроком (survival): PICKUP л/п, QUICK_MOVE (shift из инвентаря), SWAP (хотбар 0, offhand 40), QUICK_CRAFT (drag); контроль — 16 земли | мешочек: `[]`; земля: `[PICKUP PICKUP_RIGHT QUICK_MOVE SWAP SWAP_OFFHAND QUICK_CRAFT]` |
| `s14_hopper_pouchRejected` / `s14_hopper_dirtControl` | воронка над шалкером, 40 тиков | мешочек: `inserted=false, hopperEmpty=false`; земля: `inserted=true` |
| `s14_dropper_pouchRejected` / `s14_dropper_dirtControl` | дроппер → шалкер, запуск блоком редстоуна | мешочек: `inserted=false, dropperEmpty=false`; земля: `inserted=true` |
| `s14_capabilities` | `ITEM_HANDLER` шалкера (BE) со сторон `null, UP, DOWN, NORTH`; capability предмета-шалкера; `canPlaceItem`; `new InvWrapper(box)` | BE: не принят ни с одной стороны; предмет: мешочек `false`, земля `true`; **`Container#canPlaceItem(pouch)=true`, `new InvWrapper(box).insertItem(pouch)` принимает** |
| `s14_bundle_pouchRejected` | `ChestMenu`: мешочек на bundle правой кнопкой; bundle на мешочек правой кнопкой | мешочек: `[]` (не вошёл, не потерян); контроль: `[STACKED_ON_BUNDLE … BUNDLE_ON_ITEM]` |
| `s14_readNbtQuota` | бинарный поиск по `byte[]` в `CompoundTag`, `writeNbt` → `readNbt()` | максимум `byte[]` = 2 096 996 (на проводе 2 097 008 Б); отказ: `Tried to read NBT tag that was too big; tried to allocate: 2097153bytes where max allowed: 2097152`; `readAnySizeNbt` (3 МиБ) — ok |
| `s14_readUtfSemantics` | `writeUtf` → `readUtf(max)` | `€×10 / 10` ok; `€×11 / 10` FAIL `(33 > 30)` по байтам; `a×11 / 10` FAIL `(11 > 10)` по длине; `😀×5 / 5` FAIL `(20 > 15)`; `😀×5 / 10` ok (10 units) |

Выдержка из лога:
```
[S14] shulker menu: pouch placed via [], dirt placed via [PICKUP PICKUP_RIGHT QUICK_MOVE SWAP SWAP_OFFHAND QUICK_CRAFT]
[S14] hopper -> shulker, item spike_pouch: inserted=false, hopperEmpty=false
[S14] dropper -> shulker, item spike_pouch: inserted=false, dropperEmpty=false
[S14] BE capability accepted pouch for sides []; item capability accepts pouch=false dirt=true; Container#canPlaceItem(pouch)=true; new InvWrapper(box).insertItem(pouch) accepts=true
[S14] bundle: pouch [], dirt control [STACKED_ON_BUNDLE (carried-consumed) BUNDLE_ON_ITEM]
[S14] readNbt(): max byte[] payload accepted = 2096996 (wire bytes 2097008); first rejection: java.lang.RuntimeException: Tried to read NBT tag that was too big; tried to allocate: 2097153bytes where max allowed: 2097152; readAnySizeNbt(3 MiB) ok=true
[S14] readUtf: euro10/10=ok(10 units) euro11/10=FAIL(... (33 > 30)) a11/10=FAIL(... (11 > 10)) emoji5cp/5=FAIL(... (20 > 15)) emoji5cp/10=ok(10 units)
All 10 required tests passed :)
```

### Где проверяется `canFitInsideContainerItems` (поиск по всему jar)

Вызывают метод только: `ShulkerBoxSlot#mayPlace`, `ShulkerBoxBlockEntity#canPlaceItemThroughFace` (плюс `Block.byItem(...) instanceof ShulkerBoxBlock`), `BundleItem#overrideStackedOnOther` (до `Slot#safeTake`, поэтому мешочек не теряется) и `BundleItem#add`, `BlockItem#canFitInsideContainerItems` (шалкер в шалкер), Forge `ShulkerItemStackInvWrapper` (capability предмета). `AbstractContainerMenu#doClick` во всех ветках (PICKUP, SWAP, QUICK_CRAFT, `moveItemStackTo` для QUICK_MOVE) спрашивает `Slot#mayPlace`. Forge: `ShulkerBoxBlockEntity#createUnSidedHandler` = `new SidedInvWrapper(this, Direction.UP)`, поэтому capability с любой стороны (и `null`) идёт через `canPlaceItemThroughFace`; воронка и дроппер Forge (`VanillaInventoryCodeHooks`) используют эту capability.

### Пути, которые пропускают мешочек в шалкер

1. **`ShulkerBoxBlockEntity#canPlaceItem`** не переопределён (возвращает `true`), `setItem` не проверяет ничего. Любой код, который работает с BE как с `Container`, а не через capability или `WorldlyContainer`, кладёт мешочек: `new InvWrapper(box)` (воспроизведено), сторонние моды-трубы и сортировщики, написанные «по-ванильному» через `Container`. Предмет-шалкер с мешочком при разрушении блока сохраняет содержимое в `BlockEntityTag`.
2. **Креатив** (`handleSetCreativeModeSlot`): клиент присылает любой `ItemStack` с NBT ≤ 2 МиБ учёта, в т.ч. шалкер или сундук с `BlockEntityTag.Items` из мешочков; ctrl+pick копирует NBT BE в предмет. Не проверено GameTest (нужен клиент), вывод по исходникам.
3. **Команды** `/give`, `/item`, `/data` (оператор) и **лут-таблицы/структуры датапаков** (`set_contents`, `set_nbt`). Не проверено.
4. **Сторонние рюкзаки и предметы-контейнеры** со своим `IItemHandler` проверяют `canFitInsideContainerItems` по своему усмотрению. Не проверено.

В ванилле 1.20.1 survival других предметов-контейнеров нет (`DecoratedPotBlockEntity` предметов не хранит).

## Что не проверено

- Клиент (`runClient` не запускался): реальное отключение при пакете > 2 097 151 Б на проводе и поведение клиента без проверки 8 МиБ; креативный инвентарь и ctrl+pick.
- Реальный выделенный сервер с `network-compression-threshold=-1`.
- Сторонние моды (трубы, рюкзаки).
- Константы Forge `NetworkHooks` для `SpawnEntity` (не относятся к вопросу).

## Готовая таблица для `09-security.md` §1.6.2 (замена `[ПРОВЕРИТЬ]`)

| Лимит (MC 1.20.1, Forge 47.4.28) | Значение | Где (Parchment) | Сторона |
|---|---|---|---|
| NBT одного `readNbt()` / `readItem()` | 2 097 152 байт **учёта** `NbtAccounter` (оценка памяти: `CompoundTag` 48, запись ≈ 68 + 2·ключ, `byte[]` ≈ 24 + n). Плоский `byte[]` проходит до 2 096 996 Б | `FriendlyByteBuf.DEFAULT_NBT_QUOTA`, `NbtAccounter#accountBytes` | обе |
| Глубина NBT | 512 | `CompoundTag$1#load` | обе |
| `readAnySizeNbt` | без лимита | `NbtAccounter.UNLIMITED` | — |
| Тело пакета до сжатия | 8 388 608 Б; больше → исключение в `PacketEncoder`, пакет не skippable → разрыв соединения | `PacketEncoder#encode` | отправитель |
| Распакованный входящий пакет | 8 388 608 Б — **только сервер** (C→S); клиент не проверяет | `CompressionDecoder#decode`, `validateDecompressed` | сервер |
| Кадр на проводе (после сжатия; без сжатия — весь пакет) | 2 097 151 Б (VarInt ≤ 3 байт) | `Varint21FrameDecoder`, `Varint21LengthFieldPrepender` | обе |
| Custom payload C→S (`SimpleChannel` к серверу) | 32 767 Б | `ServerboundCustomPayloadPacket.MAX_PAYLOAD_SIZE` | сервер |
| Custom payload S→C | 1 048 576 Б | `ClientboundCustomPayloadPacket.MAX_PAYLOAD_SIZE` | клиент |
| Login custom query (handshake Forge) | 1 048 576 Б в обе стороны | `Clientbound/ServerboundCustomQueryPacket` | обе |
| Разбиение Forge | только > 8 388 608 Б, части по 1 048 570 Б, только рецепты, теги, достижения, `ClientboundLoginPacket`; контейнеры и `SimpleChannel` — **нет** | `VanillaPacketSplitter`, `ForgeConnectionNetworkFilter` | S→C |
| Доп. данные `NetworkHooks.openScreen` | 1…32 600 Б | `NetworkHooks#openScreen`, `PlayMessages$OpenContainer` | S→C |
| `readUtf(max)` | байты ≤ 3·max (до декодирования), затем `length()` ≤ max в UTF-16 code units; `readUtf()` = 32 767 | `FriendlyByteBuf#readUtf(int)` | обе |
| Креативный слот C→S | содержимое NBT не проверяется, только слот 1…45, `count ≤ 64` | `ServerGamePacketListenerImpl#handleSetCreativeModeSlot` | сервер |

## Предлагаемые правки документов

1. **`09-security.md` §1.6.2, SEC-11.** Старое: «мешочек не кладётся в шалкер и bundle `[ПРОВЕРИТЬ]` проверку в `ShulkerBoxBlockEntity.canPlaceItemThroughFace` и слоте шалкера 1.20.1». Новое: «мешочек не кладётся в шалкер и bundle (S14: проверено GameTest — клики меню, shift, SWAP, drag, воронка, дроппер, capability BE и предмета, bundle в обе стороны). Обходы: `Container#canPlaceItem`/`setItem` и `new InvWrapper(be)` у `ShulkerBoxBlockEntity` (сторонние моды), креатив, команды, лут-таблицы — защищает только SEC-09 (инертность при превышении лимита) и бюджет пакетов SEC-12».
2. **`09-security.md` §1.6.2, SEC-11.** Старое: «Оба меньше предела пакета 2 МиБ на `ItemStack` и 8 МиБ на пакет `[ПРОВЕРИТЬ]`». Новое: «Предел `ItemStack` — 2 097 152 байт учёта `NbtAccounter` (не байт провода); тело пакета ≤ 8 388 608 Б; кадр на проводе ≤ 2 097 151 Б, а при выключенном сжатии это предел **несжатого** пакета. Расчётный бюджет ведём от 2 097 151 Б несжатого (S14)».
3. **`09-security.md` SEC-12.** Старое: «`ClientboundContainerSetContentPacket` ≤ 4 МиБ (запас ×2 к 8 МиБ)». Новое: «`ClientboundContainerSetContentPacket` худшего случая ≤ 1 МиБ **несжатым** (запас ×2 к кадру 2 097 151 Б при `network-compression-threshold=-1`); пакет меню считается вместе с 36 слотами инвентаря игрока; `ItemStack` ≤ 512 КиБ учёта `NbtAccounter` (×4 к 2 МиБ)». Цифры худших случаев — в S10.
4. **`09-security.md` T-D1.** «≈ 8 МБ `[ПРОВЕРИТЬ]` `CompressionDecoder`» → «8 388 608 Б тела пакета; на проводе кадр ≤ 2 097 151 Б; клиент распакованный размер не проверяет, отправку > 8 МиБ режет `PacketEncoder` разрывом соединения».
5. **`09-security.md` T-D7.** «≈ 1 МБ `[ПРОВЕРИТЬ]`» → «1 048 576 Б (`ClientboundCustomPayloadPacket`); Forge не разбивает пакеты `SimpleChannel`, поэтому `DefinitionsSync` бьётся на части сам (≤ 1 МиБ на часть с запасом, например ≤ 512 КиБ)».
6. **`09-security.md` SEC-03.** «`readUtf` проверяет и байты `[ПРОВЕРИТЬ]` семантику 1.20.1» → «`readUtf(max)` проверяет байты ≤ 3·max до декодирования и `String.length()` ≤ max в UTF-16 code units. Лимиты в кодовых точках (имя ≤ 24 кодовые точки) проверять отдельно после чтения; `max` для `readUtf` = 2 × лимит в кодовых точках». Также добавить: «C→S пакеты мода ≤ 32 767 Б (`ServerboundCustomPayloadPacket`)».
7. **`09-security.md` таблица открытых вопросов (строка «Ванильные лимиты пакетов … `[ПРОВЕРИТЬ]`»)** → закрыть ссылкой на S14.
8. **`05-architecture.md` §6.3.** «(`[ПРОВЕРИТЬ]` в S14)» → «(проверено в S14; обход только через `Container`-API, креатив и команды)». Последняя фраза абзаца «меньше ванильных пределов пакета (SEC-12, проверка в S14)» → ссылка на числа S10/S14: кадр 2 097 151 Б.
9. Новое требование (предлагаю SEC-11a): «Наши блоки-хранилища (семенной банк, анализатор) переопределяют **и** `canPlaceItem`, **и** `canPlaceItemThroughFace`, а capability отдаёт обёртку с той же проверкой — иначе `new InvWrapper(be)` стороннего мода обходит запрет (S14)».

**Задачи `06-tasks.md` к пересмотру:** WR-127 (сетевой канал и ограниченные декодеры: C→S ≤ 32 767 Б, `readUtf(max)` в UTF-16 units + отдельная проверка кодовых точек), WR-128 (`DefinitionsSyncPacket`: Forge не разбивает `SimpleChannel`, своё разбиение на части ≤ 1 048 576 Б), WR-122 (`NbtLimits`/`PlayerText`: семантика `readUtf`, учёт `NbtAccounter` ≠ байты провода), WR-138 (мешочек: запрет вложения подтверждён), WR-185 (семенной банк: переопределить `canPlaceItem` и `canPlaceItemThroughFace`, capability с той же проверкой), WR-197 (бюджет пакетов: предел кадра 2 097 151 Б без сжатия, пакет меню вместе с инвентарём игрока).
