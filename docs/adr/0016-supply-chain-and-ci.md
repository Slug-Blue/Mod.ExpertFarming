# ADR-0016: Цепочка поставок и CI

## Статус
Предложено (2026-10-29), источник — threat model (`09-security.md` Δ9, SEC-37…SEC-44). Каркас — фаза 4, обязательный зелёный offline-job — фаза 7.

## Контекст
Мод собирается из зависимостей нескольких экосистем (Forge, Parchment, SpongePowered, Modrinth, BlameJared, TerraformersMC), большинство артефактов без PGP-подписей. Видение (метрика 7): ноль исходящих соединений. Релиз распространяется игрокам и модпакам; подмена артефакта или jar — главный риск цепочки поставок.

## Решение
- **Репозитории:** только официальные, по https, каждый через `exclusiveContent` со своим набором групп; `mavenLocal`, JitPack, `flatDir`, `http://` запрещены; CurseMaven — только если зависимости нет на Modrinth Maven. CI-скрипт проверяет Gradle-файлы.
- **Версии:** точные, все в `gradle.properties`; без `+`, диапазонов, `latest.*`, `-SNAPSHOT` (диапазоны допустимы только в `mods.toml`). CI-grep.
- **Gradle wrapper:** `distributionSha256Sum` с официальной суммой; `wrapper-validation` в каждом прогоне; обновление — отдельным PR.
- **Dependency verification:** `gradle/verification-metadata.xml` с sha256 всех внешних артефактов и плагинов, без подписей; `trusted-artifacts` (≤ 5 правил с комментариями) — только для артефактов, которые ForgeGradle генерирует локально (`[ПРОВЕРИТЬ]` regex в spike S7). Dependency locking не включаем: при точных версиях и verification он избыточен, а с конфигурациями ForgeGradle хрупок.
- **JDK:** локально — foojay (решение владельца); в CI — `actions/setup-java` Temurin 17, автозагрузка toolchain выключена.
- **Offline GameTest:** job `gametest-offline` запускает `runGameTestServer` без сети (`--network none` или `unshare -n` после прогрева кэша, `versionCheck = false`). В `mods.toml` нет `updateJSONURL`. ArchUnit запрещает сетевые API, `ProcessBuilder`, загрузку классов и рефлексию в коде мода.
- **Jar:** воспроизводимая сборка (двойная сборка, сравнение sha256); allowlist содержимого (`jar tf`): пакет мода (включая классы `:genetics`), `mods.toml`, `MANIFEST.MF`, AT, `wildroots.mixins.json`, refmap, `pack.mcmeta`, `assets/wildroots/**`, `data/**`, `resourcepacks/lines/**`, логотип; без вложенных jar и нативных библиотек.
- **GitHub Actions:** `uses:` по полному SHA; `permissions: contents: read`; PR через `pull_request` без секретов; gitleaks, secret scanning, Dependabot еженедельно. **Релиз — только `workflow_dispatch`** в environment `release` с обязательным ревьюером-владельцем; токены площадок — только в секретах этого environment; к релизу — `SHA256SUMS` и attestation происхождения.
- Новая зависимость = ADR + PR, который меняет `verification-metadata.xml` с указанием источника.

## Альтернативы
1. **Только точные версии без verification.** Не защищает от подмены артефакта в репозитории или кэше. Отклонено.
2. **Проверка подписей PGP.** Большая часть экосистемы Forge без подписей — сборка невозможна. Отклонено.
3. **Автоматический релиз по тегу.** Тег может поставить скомпрометированный аккаунт или workflow; ручной релиз с ревьюером дешевле в эксплуатации. Отклонено.

## Последствия
- Плюсы: подмена зависимости или содержимого jar ломает сборку; поведенческое доказательство метрики 7.
- Минусы: обновление зависимостей и wrapper требует ручного ревью диффа метаданных; offline-job дольше обычного.
