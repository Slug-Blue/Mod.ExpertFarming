# S5 · WR-024: печь и выходной слот с NBT; `quality_shaped`

Ветка: `spike/wr-024-furnace-quality`. Код spike одноразовый, лежит только в `mod/src/gametest`.

## Вопрос

1. Учитывают ли `AbstractFurnaceBlockEntity.canBurn`/`burn` NBT выходного слота? Не сольются ли две плавки разных классов в одну стопку и не потеряется ли класс?
2. Работает ли `assemble` у подкласса `ShapedRecipe` (`quality_shaped`):
   - в верстаке (слот результата, Shift-клик);
   - в книге рецептов (`ServerPlaceRecipe`);
   - в автокрафтере Create.

## Вердикт

| # | Подпункт | Вердикт |
|---|---|---|
| 1a | Печь вызывает `assemble`, и класс попадает в результат | **ПОДТВЕРЖДЕНО**: патч Forge заменяет `getResultItem` на `assemble(this, registryAccess)` в `canBurn` и `burn` |
| 1b | Выходной слот сравнивается с учётом NBT («классы не сливаются») | **ОПРОВЕРГНУТО**. Классы **сливаются**: `canBurn` проверяет `ItemStack.isSameItem`, `burn` — `is(Item)` и `grow`. Картофель класса 4 попал в стопку класса 1 (`2x baked_potato{grade:1}`), класс 4 потерян. M3 или замена ему **нужны** |
| 1c | Замена M3 без mixin: защита в `matches` нашего рецепта | **ПОДТВЕРЖДЕНО** (новый вариант). Печь передаёт в `getRecipeFor` сам BE, поэтому `matches` видит слот 2. Если там другой класс, рецепт возвращает `false`, и печь ждёт, пока слот освободят. Класс сохраняется |
| 2a | `quality_shaped` в верстаке | **ПОДТВЕРЖДЕНО**: класс виден в слоте результата, Shift-клик отдаёт хлеб с классом |
| 2b | `quality_shaped` в книге рецептов | **ЧАСТИЧНО**. Рецепт находится, и после раскладки вызывается `assemble`: с обычной пшеницей результат `bread{grade:3}`. Но **книга рецептов не раскладывает ингредиенты с NBT**: пшеница с `wildroots:grade` осталась в инвентаре, сетка пуста. Это ограничение ванили, mixin его не обходит |
| 2c | Автокрафтер Create | **НЕ ПРОВЕРЕНО**: Create недоступен (новые зависимости запрещены) |

## Доказательства

### 1. Печь: исходники

Патч `patches/net/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity.java.patch` (`forge-1.20.1-47.4.28-sources.jar`). Методы `canBurn` (`m_155005_`) и `burn` (`m_266209_`) Forge сделал нестатическими:

```
-         ItemStack itemstack = p_155006_.m_8043_(p_266924_);
+         ItemStack itemstack = ((Recipe<WorldlyContainer>) p_155006_).m_5874_(this, p_266924_);   // canBurn: assemble
             } else if (!ItemStack.m_41656_(itemstack1, itemstack)) {                                  // isSameItem, без NBT
-         ItemStack itemstack1 = p_266780_.m_8043_(p_266740_);
+         ItemStack itemstack1 = ((Recipe<WorldlyContainer>) p_266780_).m_5874_(this, p_266740_);  // burn: assemble
          } else if (itemstack2.m_150930_(itemstack1.m_41720_())) {                                    // is(Item)
-            itemstack2.m_41769_(1);
+            itemstack2.m_41769_(itemstack1.m_41613_());                                               // grow
```

Байткод того же jar с маппингом Parchment (`javap -c -p`):
- `canBurn(RegistryAccess, Recipe, NonNullList, int)` вызывает `Recipe.assemble` и `ItemStack.isSameItem`;
- `burn(...)` вызывает `Recipe.assemble`, `ItemStack.is(Item)` и `ItemStack.grow`.

Попутно: `CampfireBlockEntity` тоже вызывает `CampfireCookingRecipe.assemble` и роняет результат через `Containers.dropItemStack`. Выходного слота у костра нет, поэтому и сливаться нечему.

### 1. Печь: GameTest

Прототип `SpikeRecipes.GradedSmeltingRecipe extends SmeltingRecipe` со своим сериализатором `wildroots:spike_graded_smelting`. `assemble` копирует класс входа в результат. Ванильные рецепты перекрыты по их же id: `data/minecraft/recipes/baked_potato.json` (без защиты) и `dried_kelp_from_smelting.json` (`"guard": true`).

Команда: `JAVA_HOME=~/.jdks/temurin-17 ./gradlew :mod:runGameTestServer :mod:build` → `All 5 required tests passed :)`, `BUILD SUCCESSFUL`.

`furnaceOutputIgnoresNbt`: картофель класса 1, затем картофель класса 4.

```
SPIKE S5 furnace#1 after first smelt: in=0xair out=1xbaked_potato{"wildroots:grade":1}
SPIKE S5 furnace#1 after second smelt: in=0xair out=2xbaked_potato{"wildroots:grade":1}
```

`furnaceGuardInMatches`: ламинария класса 1, затем класса 4, затем выходной слот освобождается.

```
SPIKE S5 furnace#2 guarded, before take: in=1xkelp{"wildroots:grade":4} out=1xdried_kelp{"wildroots:grade":1}
SPIKE S5 furnace#2 guarded, after take: in=0xair out=1xdried_kelp{"wildroots:grade":4}
```

У защиты в `matches` есть побочный эффект. Если вход положили, пока печь заблокирована, `AbstractFurnaceBlockEntity#setItem(0, …)` → `getTotalCookTime` рецепта не находит, и время готовки откатывается к 200 тикам (`sipush 200`) вместо значения из рецепта. В первом прогоне ожидание в 100 тиков поэтому не дождалось результата. Для ванильных рецептов печи время и так 200, у коптильни и доменной печи — 100. Это косметика: готовка просто идёт дольше.

### 2a. Верстак

`SpikeRecipes.QualityShapedRecipe extends ShapedRecipe` (класс результата = минимальный класс входов) перекрывает `data/minecraft/recipes/bread.json` (`wildroots:spike_quality_shaped`). Подмена по id сработала: `minecraft:bread recipe class = …SpikeRecipes$QualityShapedRecipe`.

`qualityShapedInCraftingTable`: игрок `FakePlayerFactory.getMinecraft(level)` (`ServerPlayer`, пакеты никуда не уходят), `new CraftingMenu(0, inv, ContainerLevelAccess.create(level, pos))`, пшеница классов 4/1/4 по две штуки в первой строке. Цепочка `Slot.set` → `CraftingMenu#slotsChanged` → `slotChangedCraftingGrid` (по байткоду: `RecipeManager.getRecipeFor` → `CraftingRecipe.assemble` → `ResultContainer.setItem`). После этого `quickMoveStack(player, 0)`.

```
SPIKE S5 table result slot: 1xbread{"wildroots:grade":1} (assemble calls +1)
SPIKE S5 table after shift-click: inventory bread=1 first=1xbread{"wildroots:grade":1} grid1=1xwheat{"wildroots:grade":4} result=1xbread{"wildroots:grade":1}
```

После взятия сетка пересчиталась, и новый результат снова собран через `assemble`.

### 2b. Книга рецептов

`qualityShapedViaRecipeBook`: `RecipeBookMenu#handlePlacement(false, bread, player)` → `ServerPlaceRecipe#recipeClicked`.

```
SPIKE S5 book ungraded: grid=0xair,0xair,0xair result=1xbread{"wildroots:grade":3}
SPIKE S5 book graded 3x g2: grid=0xair,0xair,0xair result=0xair invWheat=3
```

Почему лог показывает пустую сетку `grid=` при готовом результате: лог печатает слоты 1–3, а книга кладёт рецепт в одну строку не обязательно в первый ряд. Результат `bread{grade:3}` подтверждает, что раскладка была и что `assemble` вызвался.

Причина отказа с NBT видна в байткоде:
- `ServerPlaceRecipe` берёт ингредиент как `StackedContents.fromStackingIndex(id)`, то есть «чистый» стак без NBT, и передаёт его в `moveItemToGrid`;
- `Inventory#findSlotMatchingUnusedItem` сравнивает через `ItemStack.isSameItemSameTags` (и пропускает повреждённые, зачарованные и переименованные стаки);
- стак с `wildroots:grade` поэтому не находится, и в сетку ничего не кладётся. Клиент при этом может показать призрачный рецепт.

## Что не проверено

- Автокрафтер Create и другие автокрафтеры (AE2, RS): моды недоступны.
- Клиентская часть книги рецептов (призрачный рецепт, подсветка «можно скрафтить»): `runClient` не запускался.
- Воронки и порядок `getRecipeFor` с несколькими подходящими рецептами при защите в `matches` (другой мод с рецептом на тот же вход может перехватить плавку, пока наш рецепт заблокирован).
- Коптильня и доменная печь отдельно не гонялись: у них тот же `AbstractFurnaceBlockEntity`.

## Предлагаемые правки документов

1. **`docs/05-architecture.md` §12.1, пункт «Печь, коптильня».**
   Старое: «`AbstractFurnaceBlockEntity.canBurn`/`burn` в 1.20.1, по-моему, сравнивают выходной слот через `ItemStack.isSameItem` (без NBT) `[ПРОВЕРИТЬ]`, и тогда печёный картофель класса 3 сольётся в стопку класса 2. Если это подтвердится, нужен mixin M3 (замена на `isSameItemSameTags`). Запасной путь без mixin — у печёного картофеля класс по нижнему из входа и выхода (теряется точность, не данные).»
   Новое: «Forge 47.x вызывает в печи `assemble(this, registryAccess)`, но выходной слот сравнивает без NBT (`canBurn`: `ItemStack.isSameItem`; `burn`: `is(Item)` + `grow`). Классы сливаются: это проверено в S5. Решение без mixin: `quality_smelting`/`_smoking` возвращают `matches = false`, если в слоте 2 лежит тот же предмет другого класса (контейнер — сам BE). Печь ждёт, пока результат заберут, и класс не теряется. Побочный эффект: вход, положенный в заблокированную печь, готовится 200 тиков. M3 остаётся запасным, если защита в `matches` конфликтует с чужими рецептами.»
2. **`docs/05-architecture.md` §12.1, пункт «Работает везде, где вызывается `assemble`».**
   Старое: «Работает везде, где вызывается `assemble`: верстак, книга рецептов, автокрафтеры модов.»
   Новое: «Работает везде, где вызывается `assemble`: верстак (слот результата и Shift-клик проверены в S5), печь (Forge-патч). **Книга рецептов не раскладывает ингредиенты с NBT** (`ServerPlaceRecipe` + `Inventory#findSlotMatchingUnusedItem` → `isSameItemSameTags`), поэтому урожай с классом через неё не крафтится. Это ограничение ванили, известное и для переименованных предметов. Автокрафтеры модов не проверены.»
3. **`docs/05-architecture.md` §16, строка M3.**
   Старое: «Только если spike подтвердит сравнение без NBT | Условный»
   Новое: «Сравнение без NBT подтверждено (S5), но закрыто без mixin — защитой в `matches` рецептов `quality_smelting`/`_smoking` | Запасной»
4. **`docs/adr/0014-mixin-policy.md`, строка M3.**
   Старое: `| M3 | `AbstractFurnaceBlockEntity.canBurn`/`burn`: сравнение выхода с NBT | Условный (S5) |`
   Новое: `| M3 | `AbstractFurnaceBlockEntity.canBurn`/`burn`: сравнение выхода с NBT | Запасной: слияние подтверждено в S5, по умолчанию закрыто защитой в `matches` (ADR-0012) |`
5. **`docs/adr/0012-quality-inheritance-recipes.md`, «Решение», пункт «Печь».**
   Старое: «Печь: если `canBurn`/`burn` сравнивают выход без NBT — mixin M3.»
   Новое: «Печь: `canBurn`/`burn` сравнивают выход без NBT (S5). `quality_smelting`/`_smoking` в `matches` отказываются плавить, пока в выходном слоте лежит тот же предмет другого класса. M3 — запасной путь.»
   В «Последствия → Минусы» добавить: «Книга рецептов не раскладывает ингредиенты с классом (ванильное `isSameItemSameTags`). Игрок кладёт их в сетку вручную.» Там же снять `[ПРОВЕРИТЬ]` для печи в «Статусе».
6. **`docs/05-architecture.md` §20.1, R5.** Пометить, что печь закрыта без mixin, а R5 остаётся по котлу FD (S6).

Задачи `06-tasks.md`, которые стоит пересмотреть:
- **WR-170** (сериализаторы качества): в приёмку добавить GameTest «две плавки разных классов: вторая ждёт, класс не теряется» и защиту в `matches` для `quality_smelting`/`_smoking`. Для `_campfire` защита не нужна (выход роняется на землю).
- **WR-306** (волна 3, печь): M3 не включать. Оставить только как запасной путь при конфликте защиты в `matches`.
- **Тултип и онбординг (Narrative/UX):** подсказка, что ингредиенты с классом нельзя разложить книгой рецептов. Или отдельная задача на исследование клиентского обхода, вне MVP.
