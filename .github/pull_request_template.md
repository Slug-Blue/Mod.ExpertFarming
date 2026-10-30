<!--
Заголовок PR = будущий squash-коммит: <тип>(<scope>): <что сделано>
Пример: feat(genetics): add meiosis with crossover per linkage group
Правила — CONTRIBUTING.md. PR открывается только с подтверждения владельца.
-->

## Задача

- ID: WR-NNN — <название из docs/06-tasks.md>
- Ветка: `<тип>/<id>-<slug>`
- Spike: нет / да (код не сливается, в `main` идёт только итог в ADR или документе)

## Что сделано

<!-- 2–5 строк: что и зачем. Ссылки на разделы спецификаций и ADR. -->

## Критерии приёмки

<!-- Скопируйте все критерии задачи из docs/06-tasks.md. Для каждого — ссылка на тест (класс#метод) или на запись ручной проверки ниже. -->

- [ ] <критерий 1> — JUnit: `ClassName#method` / GameTest: `ClassName#method` / CI: job / Ручная: см. ниже
- [ ] <критерий 2> — …

### Ручные проверки

<!-- Чек-лист и результат, если есть критерии «Ручная». Иначе — «нет». -->

## Definition of Done (`docs/06-tasks.md` §1)

- [ ] 1. Ветка `<тип>/<id>-<slug>` от свежего `main`; для spike код не сливается
- [ ] 2. Коммиты — Conventional Commits, в теле ID задачи; push и PR — с подтверждения владельца
- [ ] 3. Зелёные локально и в CI: `./gradlew build` и `./gradlew :mod:runGameTestServer` (с фазы 7 — `gametest-offline`)
- [ ] 4. Все критерии приёмки отмечены выше со ссылками на тесты или ручные проверки
- [ ] 5. Тесты написаны или проверены Test Automation Engineer; статистика с фиксированным сидом
- [ ] 6. Ревью Code Reviewer без блокеров; для `verification-metadata.xml`, `accesstransformer.cfg`, `wildroots.mixins.json`, workflow-файлов — ревью DevOps
- [ ] 7. Безопасность: строка SEC проверена (если касается); новые точки входа в `Guarded.run`; C→S пакеты через `ServerPacketGuard`
- [ ] 8. Тексты: ключи в `en_us.json` и `ru_ru.json`, чек-лист `04-narrative.md` §10.3, без хардкода строк
- [ ] 9. Без новых зависимостей без ADR + `verification-metadata.xml`; без mixin вне M1–M7 (ADR-0014); без сетевых API
- [ ] 10. Документы и ADR обновлены в этом PR или открыт вопрос оркестратору

## Чувствительные изменения

- [ ] Нет изменений `gradle/verification-metadata.xml`, wrapper, `accesstransformer.cfg`, `wildroots.mixins.json`, `.github/workflows/`
- [ ] Есть — перечислены ниже с источником каждой новой sha256 / номером ADR

## Совместимость сохранений

- [ ] Формат NBT и конфигов не менялся
- [ ] Менялся — есть миграция и фикстура `old_save_vN` (архитектура §5.4); если миграции нет — в заголовке `!` и `BREAKING CHANGE` в теле
