# Как вносить изменения в Wild Roots

Мод `wildroots` для Minecraft 1.20.1, Forge 47.x. Репозиторий: два Gradle-подпроекта — `:genetics` (чистая Java, движок генетики) и `:mod` (ForgeGradle 6), см. `docs/05-architecture.md` §3.

Документ обязателен для людей и для агентов команды. Источники правил: `docs/06-tasks.md` §0–§1 (план и Definition of Done), `docs/05-architecture.md` §16–§18, ADR-0014 (mixin), ADR-0016 (цепочка поставок и CI), `docs/09-security.md`.

## 1. Что запрещено без подтверждения владельца

- **`git push` в любую ветку `origin`**, открытие PR, создание тегов на `origin` — только после явного подтверждения владельца (`docs/STATUS.md`, «Блокеры»).
- **Force-push** (`--force`, `--force-with-lease`) — только в свою ветку задачи и только с подтверждением владельца. В `main` — никогда.
- **Публикации:** релизы, загрузка jar на площадки (CurseForge, Modrinth), публичные посты, issue и комментарии от имени проекта — только с подтверждением владельца. Релиз собирается только вручную через `workflow_dispatch` в environment `release` (ADR-0016); тег или push релизом не являются.
- **Слияние в `main`** — делает оркестратор или владелец после ревью. Исполнитель задачи сам в `main` не сливает.

## 2. Ветки

Модель — **trunk-based**: одна долгоживущая ветка `main`, короткие ветки задач от свежего `main`.

```
main ──●──────●──────●──────●──   (всегда собирается и проходит тесты)
        \    /  \   /  \   /
         ●──●    ●─●    ●─●       (ветка на задачу, squash в main)
```

Имя ветки: `<тип>/<id>-<slug>`.

| Тип | Когда | Пример |
|---|---|---|
| `feat/` | Новая функциональность, задача плана | `feat/wr-105-meiosis` |
| `fix/` | Исправление ошибки | `fix/wr-131-pouch-lifo-order` |
| `docs/` | Только документы и ADR | `docs/wr-017-contributing` |
| `chore/` | Сборка, настройки репозитория, зависимости, CI | `chore/wr-001-gitignore` |

- `<id>` — ID задачи из `docs/06-tasks.md` в нижнем регистре, как в `06-tasks.md` §1 (`wr-105`). Ветки фазы 4 `chore/WR-001-gitignore` и `docs/WR-017-contributing` созданы с верхним регистром до этого правила. Две ветки, отличающиеся только регистром, не заводят: в Windows и macOS они конфликтуют.
- `<slug>` — 2–4 слова латиницей через дефис.
- Одна ветка — одна задача. Работа без ID (мелкая правка) согласуется с оркестратором и получает ID или идёт как `chore/<slug>`.

### 2.1 Spike-ветки

Spike S1–S14 (WR-020…WR-033) идут по той же схеме: `feat/wr-020-spike-s1-line-packs`. **Код spike не сливается** (`docs/05-architecture.md` §20.2: «код выбрасывается»). В `main` попадает только отдельная ветка `docs/<id>-<slug>` с итогом: правка ADR или раздела документа. Spike-ветка остаётся до закрытия вопроса, потом удаляется (с подтверждения владельца, если она есть на `origin`).

## 3. Коммиты: Conventional Commits

Формат заголовка: `<тип>(<scope>): <что сделано>` — по-английски, в повелительном наклонении, без точки, до 72 символов.

**Типы** (`docs/06-tasks.md` §1): `feat`, `fix`, `test`, `docs`, `build`, `ci`, `refactor`, `chore`.

**Scope** — модуль или область:

| Scope | Область |
|---|---|
| `genetics` | подпроект `:genetics` |
| `mod` | подпроект `:mod` в целом; для крупных пакетов допустим пакет: `plant`, `pollination`, `village`, `worldgen`, `network`, `client`, `compat`, `data`, `quality`, `safety` |
| `build` | Gradle, wrapper, `gradle.properties`, `.gitignore`/`.gitattributes` |
| `deps` | зависимости и `verification-metadata.xml` |
| `ci` | `.github/workflows`, скрипты проверок |
| `docs` | документы в `docs/`, `CONTRIBUTING.md` |
| `adr` | записи `docs/adr/` |

**Тело:** ID задачи (`WR-105.`) первой строкой, затем зачем и что изменено. Нарушение совместимости сохранений или конфигов — `!` после scope и абзац `BREAKING CHANGE: …`. Агенты добавляют в конец строку `Co-Authored-By:` своей модели.

Пример:

```
feat(genetics): add meiosis with crossover per linkage group

WR-105.

Gametes are produced by GenomeLayout groups; crossover uses SplitMix64
seeded from the plant hash, so results are deterministic in tests.
```

Коммит атомарный: собирается, проходит тесты и откатывается отдельно.

## 4. Слияние в `main`

**Только squash merge**, заголовок squash-коммита = заголовок PR в формате Conventional Commits с ID задачи.
Обоснование: одна задача — один атомарный коммит в линейной истории `main`, а промежуточные коммиты ревью туда не попадают.

- Перед слиянием ветка обновлена от свежего `main` (`git rebase origin/main`), конфликты решает автор ветки.
- После слияния ветка удаляется локально (`git branch -D`, так как squash не оставляет связи с коммитами ветки) и на `origin` (с подтверждения владельца).
- Merge-коммиты в `main` не создаются; revert делается `git revert <sha>` отдельным PR.

## 5. Версии и выпуски

Версия мода: **`1.20.1-x.y.z`** (SemVer после префикса версии Minecraft), хранится в `gradle.properties` (`mod_version`).

| Часть | Когда растёт |
|---|---|
| `x` (major) | Нарушение совместимости сохранений или протокола без миграции. До `1.0.0` — `0`; выпуск `1.0.0` назначает владелец |
| `y` (minor) | **Каждая волна выпуска** (`docs/05-architecture.md` §9): волна 1 «морковь» — `1.20.1-0.1.0`, волна 2 — `0.2.0`, волна 3 — `0.3.0`, волна 4 — `0.4.0`; FD-линии и другие крупные блоки — тоже minor |
| `z` (patch) | Исправления без новых линий и механик |

Тег релиза — `v1.20.1-x.y.z`, ставится на коммит `main`, из которого ручной workflow собрал релиз. Тег и релиз — только с подтверждения владельца (§1).

## 6. Definition of Done

Задача закрыта, только если выполнено всё (копия `docs/06-tasks.md` §1; при расхождении главнее `06-tasks.md`):

1. **Ветка** `feat/<id>-<slug>` (или `fix/`, `docs/`, `chore/`) от `main`. Spike — та же схема, код не сливается (§2.1).
2. **Коммиты — Conventional Commits**, в теле — ID задачи. Push и PR — только после подтверждения владельца.
3. **Зелёные локально и в CI:** `./gradlew build` (включает `:genetics:test`, `:mod:test`, ArchUnit) и `./gradlew :mod:runGameTestServer`. С фазы 7 — ещё job `gametest-offline` (SEC-44).
4. **Все критерии приёмки** задачи проверены; в описании PR — список критериев с отметкой и ссылкой на тест (класс и метод) или на запись ручной проверки.
5. **Тесты** написаны или проверены Test Automation Engineer: каждый критерий «JUnit»/«GameTest» — тест в репозитории. Статистика — с фиксированным сидом `SplitMix64`.
6. **Ревью Code Reviewer** без открытых замечаний уровня «блокер». Изменения `verification-metadata.xml`, `accesstransformer.cfg`, `wildroots.mixins.json`, workflow-файлов — дополнительно ревью DevOps (SEC-40, SEC-45).
7. **Безопасность:** проверка строки таблицы SEC выполнена, если задача её касается; новые точки входа обёрнуты в `Guarded.run` (SEC-34); новые C→S пакеты — только через `ServerPacketGuard` (SEC-01).
8. **Тексты:** новые ключи есть в `en_us.json` и `ru_ru.json`, прошли чек-лист `docs/04-narrative.md` §10.3; хардкода строк нет.
9. **Без сюрпризов:** нет новых зависимостей без ADR и PR с `verification-metadata.xml`; нет новых mixin вне списка M1–M7 (ADR-0014); нет сетевых API (SEC-43).
10. **Документы:** если реализация уточнила поведение, правка спецификации или ADR — в том же PR (или открыт вопрос оркестратору).

## 7. Зависимости, mixin, чувствительные файлы

- **Новая зависимость = ADR + PR**, который меняет `gradle/verification-metadata.xml` и указывает источник артефакта (`docs/05-architecture.md` §18, ADR-0016). Версии — точные, в `gradle.properties`; без `+`, диапазонов, `latest.*`, `-SNAPSHOT`. Репозитории — только из списка §18, через `exclusiveContent`.
- **Ревью диффа `verification-metadata.xml`:** ревьюер DevOps сверяет каждую новую или изменённую sha256 с источником (страница Maven-репозитория или локально скачанный артефакт), проверяет, что не добавлены `trusted-artifacts` сверх ≤ 5 правил ADR-0016 и не включена проверка подписей. Изменения файла без изменения зависимостей отклоняются.
- **Новый mixin = изменение ADR-0014.** Закрытый список M1–M7; mixin вне списка не принимается, пока ADR не обновлён и не одобрен. `wildroots.mixins.json` и `accesstransformer.cfg` сверяются в CI с allowlist.
- **Gradle wrapper** обновляется отдельным PR с официальной `distributionSha256Sum` (SEC-39).
- **Workflow-файлы:** `uses:` по полному SHA, `permissions: contents: read` (SEC-41).
- Секреты, токены и личные данные в репозиторий не попадают (gitleaks в CI).

## 8. Команды

Работают на чистом клоне в Git Bash, Linux и macOS. Gradle-команды доступны после импорта MDK (WR-002); в Windows вместо `./gradlew` можно `gradlew.bat`.

**JDK для запуска Gradle.** Wrapper — Gradle 8.8 (его требует ForgeGradle 6), он запускается на JDK 17–22, а ForgeGradle и foojay требуют для запуска JDK ≥ 17. Компиляцию ведёт toolchain Java 17 (`java.toolchain.languageVersion = 17` в обоих подпроектах): если JDK 17 не найден, его скачивает foojay. Если в системе только JDK новее 22 (например, 25), поставьте Temurin 17 и укажите его Gradle, не меняя системных переменных: `JAVA_HOME=~/.jdks/temurin-17 ./gradlew build` в Git Bash или строка `org.gradle.java.home=C:/Users/<вы>/.jdks/temurin-17` в **пользовательском** `~/.gradle/gradle.properties` (не в репозитории). В CI JDK ставит `actions/setup-java` (Temurin 17), автозагрузка toolchain выключена (`org.gradle.java.installations.auto-download=false`, SEC-40).

```bash
# клон
git clone https://github.com/Slug-Blue/Mod.ExpertFarming.git
cd Mod.ExpertFarming

# начать задачу от свежего main
git fetch origin
git switch --no-track -c feat/wr-105-meiosis origin/main

# проверить перед PR (после WR-002)
./gradlew build
./gradlew :mod:runGameTestServer

# обновить ветку от main перед ревью
git fetch origin
git rebase origin/main

# посмотреть, что уйдёт в PR
git log --oneline origin/main..HEAD
git diff --stat origin/main...HEAD

# публикация ветки — ТОЛЬКО после подтверждения владельца (§1)
# git push -u origin feat/wr-105-meiosis
```

Восстановление, если что-то пошло не так: `git reflog` показывает прошлые положения `HEAD`; ветку можно вернуть `git branch <имя> <sha>` без потери работы.

## 9. Pull request

Шаблон — `.github/pull_request_template.md`. Заголовок PR — будущий squash-коммит: `<тип>(<scope>): <что сделано>`, в описании — ID задачи, критерии приёмки с отметкой и ссылкой на тест или ручную проверку, чек-лист DoD.
