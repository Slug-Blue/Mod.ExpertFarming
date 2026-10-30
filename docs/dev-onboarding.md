# Wild Roots: введение для разработчика

Описание каркаса фазы 4 (задачи WR-001…WR-017, `docs/06-tasks.md` §3) в том виде, в каком он лежит в `main`. Здесь только то, что есть в коде и конфигах; у каждого утверждения есть путь к файлу. Правила работы (ветки, коммиты, DoD) описаны в `CONTRIBUTING.md`, здесь на них только ссылки.

## Коротко

Wild Roots (`wildroots`) — Gradle-сборка мода для Minecraft 1.20.1 / Forge 47.4.28 на Java 17. В ней два подпроекта: `:genetics` (чистая Java) и `:mod` (ForgeGradle 6 + Parchment + MixinGradle). Сейчас это **только каркас**: точка входа `@Mod`, пустые mixin-конфиг, AT и lang-файлы, один smoke GameTest, правила ArchUnit, проверки CI и dependency verification.

**Чего пока нет.** Геномов, растений, блоков, предметов, реестров (`DeferredRegister`), конфигов, сети и клиентского кода в репозитории нет. В `:genetics` есть только пустой интерфейс `GeneticsService`, а `WildRoots` пишет в лог одну строку. Состав пакетов, который должен появиться, — это **план**: `docs/05-architecture.md` §3 (пакеты `:genetics` и `:mod`) и задачи волн в `docs/06-tasks.md` (например, операции `GeneticsService` придут с WR-113, как сказано в его javadoc).

## За пять минут

| Что | Где |
|---|---|
| Состав сборки | `settings.gradle`: `include ':genetics', ':mod'`, репозитории плагинов, foojay |
| Все версии | `gradle.properties` (точные, без диапазонов, SEC-38) |
| Движок генетики (пока заглушка) | `genetics/src/main/java/io/github/slugblue/wildroots/genetics/api/GeneticsService.java` |
| Точка входа мода | `mod/src/main/java/io/github/slugblue/wildroots/WildRoots.java` |
| Метаданные мода | `mod/src/main/resources/META-INF/mods.toml`, `pack.mcmeta` |
| Сборка мода, запуски, jar | `mod/build.gradle` |
| Тесты архитектуры (JUnit + ArchUnit) | `mod/src/test/java/io/github/slugblue/wildroots/arch/` |
| GameTest | `mod/src/gametest/` |
| Проверки CI | `ci/*.sh`, `.github/workflows/ci.yml` |
| Ручной релиз | `.github/workflows/release.yml` |
| Хэши зависимостей | `gradle/verification-metadata.xml` |

**Входы:** Gradle-задачи (`build`, `test`, `run*`); при запуске игры Forge загружает класс с `@Mod("wildroots")`.
**Выходы:** jar `mod/build/libs/wildroots-<mod_version>.jar` (имя задаёт `base.archivesName = mod_id`, версия — `mod_version=1.20.1-0.0.0`; этот путь ищет `ci/check-jar.sh`); отчёты тестов в `genetics/build/reports/tests/` и `mod/build/reports/tests/`; логи запусков в `mod/run/logs/`.

**Основная цепочка сборки:** `settings.gradle` → `genetics/build.gradle` (компиляция движка) → `mod/build.gradle` (`evaluationDependsOn(':genetics')`, `implementation project(':genetics')`) → задача `jar` кладёт в jar мода классы `:genetics` (`from(project(':genetics').sourceSets.main.output)`), после неё идёт `reobfJar`.

---

## 1. Структура репозитория

| Путь | Назначение |
|---|---|
| `settings.gradle` | `rootProject.name = 'wildroots'`, `include ':genetics', ':mod'`. В `pluginManagement` версии плагинов берутся из `gradle.properties`, репозитории плагинов объявлены через `exclusiveContent` (§5 ниже). Подключён плагин `org.gradle.toolchains.foojay-resolver-convention` |
| `gradle.properties` | `org.gradle.jvmargs=-Xmx3G`, `org.gradle.daemon=false`; `minecraft_version=1.20.1`, `forge_version=47.4.28`, `forgegradle_version=6.0.54`, `foojay_resolver_version=1.0.0`, `parchment_librarian_version=1.2.0`, `parchment_mappings_version=2023.09.03`, `mixingradle_version=0.7.38`, `mixin_version=0.8.5`, `junit_version=5.14.4`, `archunit_version=1.5.1`; метаданные мода `mod_id`, `mod_name`, `mod_license`, `mod_version`, `mod_group_id`, `mod_authors` |
| `gradle/wrapper/gradle-wrapper.properties` | Gradle 8.8, `distributionSha256Sum` официального `gradle-8.8-bin.zip` (WR-003) |
| `gradle/verification-metadata.xml` | sha256 всех скачиваемых артефактов и плагинов (§5 ниже) |
| `genetics/` | Подпроект `:genetics` |
| `mod/` | Подпроект `:mod` |
| `ci/` | Скрипты проверок, allowlist'ы, негативные фикстуры, self-test |
| `.github/` | `workflows/ci.yml`, `workflows/release.yml`, `dependabot.yml`, `pull_request_template.md` |
| `docs/` | Документы проекта: `00-vision.md` … `06-tasks.md`, `09-security.md`, `STATUS.md`, этот файл |
| `docs/adr/` | ADR-0001…ADR-0016 |
| `CONTRIBUTING.md` | Правила веток, коммитов, PR, DoD, зависимостей, команды |

### 1.1 `:genetics` — чистая Java

Файл `genetics/build.gradle`: плагин `java-library`, toolchain Java 17. Зависимостей нет, кроме тестовых JUnit 5 (BOM `junit_version`). Единственный репозиторий — Maven Central через `exclusiveContent` с группами `org.junit*`, `org.opentest4j`, `org.apiguardian`.

- `genetics/src/main/java/io/github/slugblue/wildroots/genetics/api/GeneticsService.java` — пустой интерфейс-заглушка фасада.
- `genetics/src/test/java/io/github/slugblue/wildroots/genetics/GeneticsIsolationTest.java` — проверяет, что `GeneticsService` — интерфейс и что `net.minecraft.world.level.Level` и `net.minecraftforge.fml.common.Mod` на classpath не находятся (`ClassNotFoundException`).

Класс, который импортирует `net.minecraft.*`, в `:genetics` не скомпилируется: Minecraft на classpath подпроекта нет. Это же проверяют правила ArchUnit `geneticsUsesOnlyJdk` и `geneticsDoesNotDependOnMod` (§4.5).

### 1.2 `:mod` — Forge-мод

Файл `mod/build.gradle`: плагины `eclipse`, `idea`, `net.minecraftforge.gradle`, `org.parchmentmc.librarian.forgegradle`, `org.spongepowered.mixin`; toolchain Java 17; маппинги `channel: 'parchment', version: "2023.09.03-1.20.1"`.

Source sets:

| Source set | Путь | Что внутри | Попадает в jar |
|---|---|---|---|
| `main` | `mod/src/main` | `WildRoots.java`, `META-INF/mods.toml`, `META-INF/accesstransformer.cfg`, `wildroots.mixins.json`, `pack.mcmeta`, `assets/wildroots/lang/en_us.json`, `ru_ru.json`. Дополнительно подключён `src/generated/resources` (туда пишет `runData`; сейчас каталога в репозитории нет) | да |
| `test` | `mod/src/test` | JUnit 5 + ArchUnit: `arch/ArchRules.java`, `arch/ProductionArchitectureTest.java`, `arch/ArchRulesSelfTest.java`, фикстуры-нарушители в пакете `archfixtures` | нет |
| `gametest` | `mod/src/gametest` | `gametest/SmokeGameTests.java`, шаблон `data/wildroots/structures/gametest/floor_3x3x3.nbt` | нет |

Source set `gametest` объявлен в `mod/build.gradle` (`sourceSets { gametest { … } }`): он видит `main` и наследует `implementation`/`runtimeOnly`. В каждом dev-запуске мод `wildroots` собирается из трёх источников (`minecraft.runs.configureEach.mods.wildroots`): `sourceSets.main`, `project(':genetics').sourceSets.main` и `sourceSets.gametest`.

Прочие настройки `mod/build.gradle`:
- `processResources` подставляет `mod_id`, `mod_name`, `mod_license`, `mod_version`, `mod_authors` в `META-INF/mods.toml` и `pack.mcmeta`.
- `mixin { add sourceSets.main, "wildroots.refmap.json"; config "wildroots.mixins.json" }`; annotation processor `org.spongepowered:mixin:0.8.5:processor`.
- Архивы воспроизводимые: `preserveFileTimestamps = false`, `reproducibleFileOrder = true` (так же в `genetics/build.gradle`, SEC-42).
- `JavaCompile` в UTF-8; `test` на JUnit Platform.

### 1.3 `ci/`

| Файл | Что проверяет |
|---|---|
| `ci/check-repositories.sh` | В Gradle-файлах нет `mavenLocal`, JitPack, `flatDir`, `http://`; https-хосты репозиториев только из списка `ALLOWED_HOSTS` (SEC-37) |
| `ci/check-sources.sh` | В `*/src/main` нет `updateJSONURL`, `Runtime.getRuntime`, `ProcessBuilder`, `ScriptEngine`, `URLClassLoader`, а `http(s)://` есть только в `displayURL`/`issueTrackerURL` файла `mods.toml`; нигде нет `@Overwrite`; в Gradle-файлах только точные версии; `wildroots.mixins.json` сохраняет `required`, `minVersion 0.8.5`, `defaultRequire 1`, свой package, а все mixin-классы есть в `ci/allowlists/mixins.txt`; каждая строка `accesstransformer.cfg` есть в `ci/allowlists/accesstransformer.txt`. Для разбора JSON нужен `python3` (переменная `PYTHON` переопределяет интерпретатор) |
| `ci/check-workflows.sh` | Все `uses:` закреплены полным 40-символьным SHA; нет `pull_request_target`; у каждого workflow есть верхнеуровневый `permissions:`; `release*.yml` запускается только по `workflow_dispatch` (SEC-41) |
| `ci/check-jar.sh` | `jar tf` (или `unzip -Z1`) собранного jar сверяется со списком ADR-0016: пакет `io/github/slugblue/wildroots/**`, `MANIFEST.MF`, `mods.toml`, AT, mixin-конфиг, refmap, `pack.mcmeta`, `assets/wildroots/**`, `data/**`, `resourcepacks/lines/**`, `wildroots_logo.png`; вложенный jar или нативная библиотека дают ошибку. Без аргумента ищет ровно один `mod/build/libs/wildroots-*.jar` |
| `ci/offline-gametest.sh` | `prepare` записывает `versionCheck = false` в `mod/run/config/fml.toml`; `scan [log]` ищет в `mod/run/logs/debug.log` следы сетевых попыток (`ConnectException`, `UnknownHostException`, `Starting version check` и др.) |
| `ci/selftest.sh` | Прогоняет каждую проверку на её негативной фикстуре и ждёт отказа; дополнительно собирает «плохой» jar (нужна утилита `jar` из JDK) |
| `ci/allowlists/mixins.txt`, `ci/allowlists/accesstransformer.txt` | Allowlist'ы; в фазе 4 содержат только комментарии |
| `ci/fixtures/` | Негативные примеры: `bad-project/` (дерево, нарушающее правила `check-sources.sh`; в сборку не входит), `bad-repositories.gradle.txt`, `bad-workflow.yml.txt`, `release-bad.yml.txt`, `network-attempt.log.txt` |

### 1.4 `.github/`

- `.github/workflows/ci.yml` запускается на `pull_request` и на `push` в `main`, `permissions: contents: read`. В нём четыре job:
  - `checks` («Repository checks»): `check-repositories.sh`, `check-workflows.sh`, `check-sources.sh`, Temurin 17, `selftest.sh`;
  - `gitleaks` («Secret scan (gitleaks)»): скачивает gitleaks 8.30.1, сверяет sha256 и сканирует всю историю;
  - `build` («Build, tests, GameTest»): `wrapper-validation`, Temurin 17 с кэшем Gradle, `./gradlew --no-daemon --stacktrace -Porg.gradle.java.installations.auto-download=false :genetics:test :mod:test :mod:runGameTestServer build`, затем `ci/check-jar.sh`; при падении выгружаются отчёты и `mod/run/logs/`;
  - `gametest-offline` («GameTest without network»): прогрев кэшей (`build :mod:prepareRunGameTestServer`), `offline-gametest.sh prepare`, `./gradlew --offline … :mod:runGameTestServer` внутри `sudo unshare --net`, затем `offline-gametest.sh scan`. В фазе 4 это каркас, обязательной проверкой job станет с фазы 7 (WR-605).
- `.github/workflows/release.yml` запускается только по `workflow_dispatch`. Две независимые сборки (matrix `attempt`) с той же Gradle-командой и `check-jar.sh`, затем job `reproducibility` сравнивает sha256 двух jar, затем job `release` в environment `release`: `SHA256SUMS`, attestation, выгрузка.
- `.github/dependabot.yml`: еженедельные обновления `gradle` (префикс коммита `build`) и `github-actions` (префикс `ci`), не больше 5 открытых PR на экосистему.

---

## 2. Требования

- **JDK 17.** Оба подпроекта задают `java.toolchain.languageVersion = JavaLanguageVersion.of(17)` (`genetics/build.gradle`, `mod/build.gradle`). Локально недостающий JDK 17 скачивает foojay; в CI JDK ставит `actions/setup-java`, а автозагрузка выключена параметром `-Porg.gradle.java.installations.auto-download=false`.
- **JDK для запуска самого Gradle.** `CONTRIBUTING.md` §8: wrapper — Gradle 8.8, «он запускается на JDK 17–22»; ForgeGradle и foojay требуют для запуска JDK ≥ 17. На JDK 23 и новее Gradle 8.8 не стартует.
- **На этой машине** Temurin 17 лежит в `%USERPROFILE%\.jdks\temurin-17` (в Git Bash — `~/.jdks/temurin-17`). Системные переменные не меняют, `JAVA_HOME` задают в самой команде. Дословно из `CONTRIBUTING.md` §8: `JAVA_HOME=~/.jdks/temurin-17 ./gradlew build` в Git Bash или строка `org.gradle.java.home=C:/Users/<вы>/.jdks/temurin-17` в **пользовательском** `~/.gradle/gradle.properties` (не в репозитории).
- **Для скриптов `ci/`:** `bash` (Git Bash), `python3` (для `check-sources.sh`), `jar` из JDK (для `selftest.sh` и `check-jar.sh`; если `jar` нет, `check-jar.sh` пробует `unzip`).
- Память: `org.gradle.jvmargs=-Xmx3G`, демон выключен (`org.gradle.daemon=false`) в `gradle.properties`.

---

## 3. Как запускать

Имена задач проверены через `./gradlew :mod:tasks --all` и `./gradlew :genetics:tasks`: `runClient`, `runServer`, `runGameTestServer`, `runData`, `prepareRun*`, `genIntellijRuns`, `genEclipseRuns`, `genVSCodeRuns`, `reobfJar`, `test`, `build`.

В PowerShell нет записи `VAR=x cmd`: `$env:JAVA_HOME` задаётся отдельной инструкцией в той же строке и действует до закрытия этого окна PowerShell. Системные переменные при этом не меняются. В `CONTRIBUTING.md` PowerShell-вариант не приведён; он ниже.

| Что | Git Bash | PowerShell |
|---|---|---|
| Клиент | `JAVA_HOME=~/.jdks/temurin-17 ./gradlew :mod:runClient` | `$env:JAVA_HOME="$env:USERPROFILE\.jdks\temurin-17"; .\gradlew.bat :mod:runClient` |
| Выделенный сервер | `JAVA_HOME=~/.jdks/temurin-17 ./gradlew :mod:runServer` | `$env:JAVA_HOME="$env:USERPROFILE\.jdks\temurin-17"; .\gradlew.bat :mod:runServer` |
| Все GameTest | `JAVA_HOME=~/.jdks/temurin-17 ./gradlew :mod:runGameTestServer` | `$env:JAVA_HOME="$env:USERPROFILE\.jdks\temurin-17"; .\gradlew.bat :mod:runGameTestServer` |
| JUnit движка | `JAVA_HOME=~/.jdks/temurin-17 ./gradlew :genetics:test` | `$env:JAVA_HOME="$env:USERPROFILE\.jdks\temurin-17"; .\gradlew.bat :genetics:test` |
| JUnit + ArchUnit мода | `JAVA_HOME=~/.jdks/temurin-17 ./gradlew :mod:test` | `$env:JAVA_HOME="$env:USERPROFILE\.jdks\temurin-17"; .\gradlew.bat :mod:test` |
| Сборка | `JAVA_HOME=~/.jdks/temurin-17 ./gradlew build` | `$env:JAVA_HOME="$env:USERPROFILE\.jdks\temurin-17"; .\gradlew.bat build` |
| Полная CI-команда | `JAVA_HOME=~/.jdks/temurin-17 ./gradlew :genetics:test :mod:test :mod:runGameTestServer build` | `$env:JAVA_HOME="$env:USERPROFILE\.jdks\temurin-17"; .\gradlew.bat :genetics:test :mod:test :mod:runGameTestServer build` |
| Проверки CI на фикстурах | `PATH="$HOME/.jdks/temurin-17/bin:$PATH" bash ci/selftest.sh` (или `bash ci/selftest.sh`, если `jar` уже есть в `PATH`) | `bash ci/selftest.sh` (нужен `bash` из Git в `PATH` и `jar` в `PATH`) |
| Остальные проверки | `bash ci/check-repositories.sh`, `bash ci/check-workflows.sh`, `bash ci/check-sources.sh`, после сборки `bash ci/check-jar.sh` | те же команды через `bash` |

В CI к Gradle-команде добавлены `--no-daemon --stacktrace -Porg.gradle.java.installations.auto-download=false` (`.github/workflows/ci.yml`).

Особенности запусков (`mod/build.gradle`, блок `minecraft.runs`):

- **Рабочая папка** всех запусков — `mod/run` (`workingDirectory project.file('run')`), кроме `data` — у него `mod/run-data`. Обе папки в `.gitignore` (`run/`, `run-data/`).
- **`runServer`** запускается с `--nogui` и останавливается на запросе EULA Minecraft. Чтобы сервер дошёл до «Done», нужно принять EULA в файле **`mod/run/eula.txt`** (`eula=false` → `eula=true`). Файл создаёт первый запуск сервера; он не в git. Принимает EULA сам разработчик: в коммите WR-007 за владельца её не принимали.
- **`runGameTestServer`** выполняет все GameTest пространства `wildroots` без экрана и завершается с кодом, равным числу упавших обязательных тестов (`mod/build.gradle`, комментарий у `gameTestServer`; `docs/05-architecture.md` §17). EULA ему не нужна: Forge 47.x сам принимает её только для GameTest-сервера (§17).
- `client`, `server`, `gameTestServer` получают `forge.enabledGameTestNamespaces=wildroots`; общие свойства: `forge.logging.markers=REGISTRIES`, `forge.logging.console.level=debug`, `mixin.env.remapRefMap=true`, `mixin.env.refMapRemappingFile=…/build/createSrgToMcp/output.srg`.
- **`runData`**: `--mod wildroots --all --output mod/src/generated/resources/ --existing mod/src/main/resources/`. Data-генераторов в коде пока нет.
- Конфигурации запуска для IDE создают `genIntellijRuns`, `genEclipseRuns`, `genVSCodeRuns` (задачи ForgeGradle).

---

## 4. Где что добавлять

### 4.1 Точка входа

`mod/src/main/java/io/github/slugblue/wildroots/WildRoots.java`: класс `final`, `@Mod(WildRoots.MOD_ID)`, `MOD_ID = "wildroots"`. Конструктор только пишет `LOGGER.debug("Wild Roots constructed")`. Реестров, слушателей событий и конфигов в нём нет. Javadoc требует, чтобы `MOD_ID` совпадал с `mod_id` в `gradle.properties` и в `META-INF/mods.toml`. По плану (`docs/05-architecture.md` §3, «(корень)») здесь будет сборка `DeferredRegister`.

### 4.2 `mods.toml` и `pack.mcmeta`

`mod/src/main/resources/META-INF/mods.toml`:
- `loaderVersion="[47,)"`, `license`, `issueTrackerURL`; `modId`, `version`, `displayName`, `authors` подставляются из `gradle.properties` при `processResources`.
- Зависимости: `forge` `[47.2.0,)` и `minecraft` `[1.20.1,1.21)` — обязательные; `farmersdelight`, `jei`, `jade` — `mandatory=false`, `versionRange="*"`, `ordering="AFTER"`, `side="BOTH"`.
- Ключа `updateJSONURL` нет и быть не должно: его ловит `ci/check-sources.sh` (SEC-43). URL-адреса допустимы только в `displayURL` и `issueTrackerURL`.

`mod/src/main/resources/pack.mcmeta`: `pack_format` 15.

### 4.3 Ресурсы и переводы

- Переводы: `mod/src/main/resources/assets/wildroots/lang/en_us.json` и `ru_ru.json`, оба сейчас `{}`. По DoD (`CONTRIBUTING.md` §6, п. 8) новый ключ добавляют в оба файла.
- Другие ресурсы мода — `mod/src/main/resources/assets/wildroots/…` и `mod/src/main/resources/data/…`: эти пути разрешены allowlist'ом jar (`ci/check-jar.sh`). Файл в jar вне списка (`assets/<чужой namespace>`, классы вне `io/github/slugblue/wildroots/`) роняет `check-jar.sh`.

### 4.4 Mixin и Access Transformer (под allowlist)

- `mod/src/main/resources/wildroots.mixins.json`: `required: true`, `minVersion: "0.8.5"`, `package: "io.github.slugblue.wildroots.mixin"`, `compatibilityLevel: "JAVA_17"`, `refmap: "wildroots.refmap.json"`, `injectors.defaultRequire: 1`; списки `mixins`, `client`, `server` пустые. Пакета `mixin` в исходниках пока нет.
- `mod/src/main/resources/META-INF/accesstransformer.cfg` содержит только комментарии и подключён в `mod/build.gradle` (`minecraft.accessTransformer`).
- Оба файла сверяются в CI: каждый mixin-класс должен стоять в `ci/allowlists/mixins.txt`, каждая строка AT — в `ci/allowlists/accesstransformer.txt`. Иначе падает `ci/check-sources.sh`. Обязательные поля mixin-конфига (`required`, `minVersion`, `defaultRequire`, `package`) тоже проверяются. `@Overwrite` запрещён во всех Java-исходниках.
- По правилам: новый mixin — это изменение ADR-0014 (закрытый список M1–M7), кандидаты AT перечислены в `docs/05-architecture.md` §16 (комментарий в `ci/allowlists/accesstransformer.txt`). Изменения этих файлов дополнительно ревьюит DevOps (`CONTRIBUTING.md` §6, п. 6; §7).

### 4.5 GameTest

- Классы: `mod/src/gametest/java/io/github/slugblue/wildroots/gametest/`. Образец — `SmokeGameTests.java`: `@GameTestHolder(WildRoots.MOD_ID)`, `@PrefixGameTestTemplate(false)`, тест `modIsLoaded` проверяет `ModList.get().isLoaded("wildroots")`.
- Шаблоны структур: `mod/src/gametest/resources/data/wildroots/structures/gametest/*.nbt`. С `@PrefixGameTestTemplate(false)` id шаблона — `wildroots:gametest/<имя>`, в аннотации пишут `template = "gametest/<имя>"`. Сейчас есть один шаблон, `floor_3x3x3.nbt` (3×3×3, каменный пол, по коммиту WR-011 — DataVersion 3465).
- Source set `gametest` в jar не попадает, но подключён ко всем dev-запускам.

### 4.6 Правила ArchUnit

- `mod/src/test/java/io/github/slugblue/wildroots/arch/ArchRules.java` — все правила; каждое параметризовано корневым пакетом (`ROOT = "io.github.slugblue.wildroots"`):
  - `noClientCodeOutsideClientPackage` — `net.minecraft.client..` только в `..client..` и в классах с аннотациями из `CLIENT_PLUGIN_ANNOTATIONS`;
  - `compatIsolated` — `compat` и пакеты из `FOREIGN_MOD_PACKAGES` (FD, JEI, Jade, EMI, WTHIT) импортирует только `compat`;
  - `geneticsDoesNotDependOnMod`, `geneticsUsesOnlyJdk`;
  - `noNetworkNoExec` — список из 17 правил SEC-43 (`java.net` кроме `URI`, `URI#toURL`, `java.net.http`, `javax.net`, `io.netty.bootstrap`, `Runtime#exec`, `ProcessBuilder`, `ClassLoader#defineClass`, `URLClassLoader`, `Lookup#defineClass`/`defineHiddenClass`, `javax.script`, object streams, `System/Runtime#load*`, `Unsafe`, `Class#forName`, `setAccessible`/`trySetAccessible`, `privateLookupIn`);
  - заготовки: `packetConsumersOnlyFrom`, `worldSecretHidden`, `levelReadsThroughLoadedLevelAccess`, `noComponentSerializerFromJson`.
- `ProductionArchitectureTest.java` проверяет правила на production-классах `:mod` и `:genetics` (`ImportOption.Predefined.DO_NOT_INCLUDE_TESTS`). Заготовки в нём отключены через `@Disabled`; включает каждую своя задача:

| Тест | Правило | SEC | Включает |
|---|---|---|---|
| `packetConsumersOnlyFromGuard` | регистрация C→S только из `network.ServerPacketGuard` и `network.WrNetwork` | SEC-01 | WR-127 |
| `worldSecretHidden` | `core.safety.WorldSecret` недоступен из `network`, `client`, `compat` | SEC-25 | WR-123 |
| `levelReadsThroughLoadedLevelAccess` | `plant`, `pollination`, `village`, `compat` читают блоки только через `core.safety.LoadedLevelAccess` | SEC-32 | WR-121 |
| `noComponentSerializerFromJson` | нет вызовов `Component.Serializer#fromJson*` (вместе с `core.safety.PlayerText`) | SEC-07 | WR-122 |

- `ArchRulesSelfTest.java` прогоняет каждое правило, заготовки тоже, на классе-нарушителе из `mod/src/test/java/io/github/slugblue/wildroots/archfixtures/` и на разрешённом варианте. Новое правило добавляют в `ArchRules`, вызывают в `ProductionArchitectureTest` и покрывают фикстурой в `ArchRulesSelfTest`. Так устроены все существующие правила.
- По коммиту WR-012: `:mod:test` — 50 тестов, 0 падений, 4 пропущены (заготовки).

---

## 5. Как обновить зависимость

**Правило** (`CONTRIBUTING.md` §7, `docs/05-architecture.md` §18, ADR-0016): новая зависимость оформляется через ADR и PR, который меняет `gradle/verification-metadata.xml` и указывает источник артефакта. Версии точные, только в `gradle.properties` (без `+`, диапазонов, `latest.*`, `-SNAPSHOT`; это проверяет `ci/check-sources.sh`). Диапазоны допустимы только в `mods.toml`.

**Dependency verification.** В `gradle/verification-metadata.xml`: `verify-metadata` = `true`, `verify-signatures` = `false`, sha256 для каждого артефакта (`origin="Generated by Gradle"`). В `trusted-artifacts` два правила с комментариями — для файлов, которые ForgeGradle 6 генерирует локально:
1. `net.minecraftforge:forge` с версией `.*_mapped_.*` (regex);
2. `net.minecraft:(client|server|joined)` (regex).

Перегенерация (дословно `CONTRIBUTING.md` §7, на JDK 17):

```bash
# Git Bash
JAVA_HOME=~/.jdks/temurin-17 ./gradlew --write-verification-metadata sha256 build :mod:runGameTestServer
```

```powershell
# PowerShell
$env:JAVA_HOME="$env:USERPROFILE\.jdks\temurin-17"; .\gradlew.bat --write-verification-metadata sha256 build :mod:runGameTestServer
```

После перегенерации обязательно ревью диффа (`CONTRIBUTING.md` §7). Записи `trusted-artifacts` Gradle не пишет; записи удалённых зависимостей вычищают вручную. Ревьюер DevOps сверяет каждую новую sha256 с источником и проверяет, что правил `trusted-artifacts` не больше 5 и проверка подписей не включена. Изменение файла без изменения зависимостей отклоняется. PR Dependabot для Gradle проходит CI, только если в том же PR перегенерирован этот файл (`.github/dependabot.yml`, `CONTRIBUTING.md` §10).

**Репозитории через `exclusiveContent`.** Каждый репозиторий ограничен своими группами:

| Где | Репозиторий | Группы |
|---|---|---|
| `settings.gradle` (`pluginManagement`) | MinecraftForge `https://maven.minecraftforge.net/` | `net.minecraftforge*`, `de.oceanlabs.mcp` |
| | ParchmentMC `https://maven.parchmentmc.org/` | `org.parchmentmc*` |
| | SpongePowered `https://repo.spongepowered.org/repository/maven-public/` | `org.spongepowered*` |
| | Gradle Plugin Portal | `org.gradle.toolchains*` |
| | `mavenCentral()` без фильтра | обычные библиотеки classpath плагинов (gson, asm и т. п.) |
| `mod/build.gradle` | SpongePowered | `org.spongepowered*` |
| | Maven Central | `org.junit*`, `org.opentest4j`, `org.apiguardian`, `com.tngtech.archunit` |
| `genetics/build.gradle` | Maven Central | `org.junit*`, `org.opentest4j`, `org.apiguardian` |

Новая группа из Maven Central добавляется в `filter` соответствующего `exclusiveContent`. Новый хост должен быть в списке `ALLOWED_HOSTS` в `ci/check-repositories.sh`. Там уже есть ещё не используемые `maven.blamejared.com`, `maven.terraformersmc.com` и `api.modrinth.com` из списка §18.

Gradle wrapper обновляют отдельным PR с официальной `distributionSha256Sum` (`CONTRIBUTING.md` §7, SEC-39).

---

## 6. Ветки, коммиты, PR

Кратко; полные правила — в `CONTRIBUTING.md`.

- Trunk-based: `main` и короткие ветки `<тип>/<id>-<slug>` (`feat/`, `fix/`, `docs/`, `chore/`), `<id>` в нижнем регистре, например `feat/wr-105-meiosis` (§2). Код spike-веток не сливается (§2.1).
- Conventional Commits: `<тип>(<scope>): <что сделано>`, по-английски, до 72 символов; первая строка тела — ID задачи (`WR-105.`) (§3).
- В `main` только squash merge; сливает оркестратор или владелец (§1, §4).
- `git push`, открытие PR, теги, релизы — только с подтверждения владельца (§1). `--no-verify` запрещён (§7).
- PR по шаблону `.github/pull_request_template.md`; DoD — §6 (зелёные `./gradlew build` и `./gradlew :mod:runGameTestServer` локально и в CI).
- Версия мода `1.20.1-x.y.z` в `gradle.properties` (`mod_version`), тег `v1.20.1-x.y.z` (§5).

---

## 7. Известные ограничения

Источники — тела коммитов WR-005…WR-016 (`git log main`) и `docs/05-architecture.md` §17–§18.

- **ForgeGradle качает часть артефактов мимо dependency verification.** Userdev Forge, MCPConfig и jar Mojang FG скачивает своим загрузчиком в `caches/forge_gradle`; их проверяет только сам FG по sha1 из манифеста Mojang (§17, коммит WR-009).
- **`trusted-artifacts` — предварительный итог.** Оба regex-правила окончательно подтверждает spike S7 (WR-026) (§17, `docs/STATUS.md`).
- **`FAIL_ON_PROJECT_REPOS`/`PREFER_SETTINGS` неприменимы:** ForgeGradle 6 сам добавляет в `:mod` свои репозитории (Forge, Mojang libraries, Maven Central, локальные кэши). Поэтому репозитории проекта объявлены в `build.gradle`, а подмену артефакта ловит `verification-metadata.xml` (§18, коммит WR-008).
- **Forge version check выходит в сеть.** С `versionCheck = true` в `mod/run/config/fml.toml` Forge `VersionChecker` обращается к `https://files.minecraftforge.net`. Job `gametest-offline` перед офлайн-прогоном пишет `versionCheck = false` (`ci/offline-gametest.sh prepare`) и после прогона ищет следы сетевых попыток в логе. Forge перехватывает такие ошибки, поэтому сам GameTest остался бы зелёным (коммит WR-014). `mod/run/` не в git, поэтому значение `versionCheck` в локальном `mod/run/config/fml.toml` у каждого разработчика своё; сборка его не задаёт.
- **`gametest-offline` — каркас:** обязательная проверка с фазы 7 (WR-605); шаг `unshare` на GitHub Actions ещё не выполнялся (коммит WR-014).
- **Workflow ещё не запускались на GitHub Actions:** `ci.yml` (коммит WR-010), `release.yml` (коммит WR-016). Gitleaks локально не запускался; проверка «тестовый секрет пойман» отложена до draft PR (коммит WR-015).
- **`runServer` останавливается на EULA, `runClient` до конца не проверен:** по коммиту WR-007 EULA за владельца не принимали, поэтому выход сервера на «Done» и запуск клиента остаются ручной проверкой.
- **Refmap не создаётся, пока список mixin пуст:** Mixin AP не пишет `wildroots.refmap.json` без единого mixin-класса (коммит WR-005).
- **Нижняя граница `forge` `[47.2.0,)` в `mods.toml`** проверена только для каркаса (`@Mod` и логгер); её поднимет задача, которая начнёт пользоваться более новым API (§18).
- Строки `[ПРОВЕРИТЬ]` в §18 (Farmer's Delight, JEI, EMI, Jade, WTHIT) — версии ещё не подключённых интеграций; в Gradle-файлах этих зависимостей нет.
