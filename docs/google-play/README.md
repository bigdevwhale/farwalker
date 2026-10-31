# Google Play — всё для ручной публикации

Тексты лежат в `listing/<язык>/*.txt` (копируйте как есть), графика — в `graphics/`.
Ниже — что вписать в каждое поле Play Console и в каком порядке.

## 0. Перед стартом

- **Нужен AAB, не APK.** Релизный workflow кладёт в GitHub Release ещё и `Farwalker-v<версия>.aab`.
  Первая версия для Play — **v1.0.2**: AAB добавляется к уже вышедшему релизу повторным запуском
  Actions → Release → Run workflow → `1.0.2`, без нового релиза. Сборка идёт с текущего `main`, так что
  перед запуском в `main` не должно быть ничего, что не вошло в 1.0.2. Секреты `SIGNING_*` в репозитории заданы;
  без них сборка подписывается debug-ключом, а такой файл Play отклонит.
- **Языки.** AAB собирается без разбиения по языкам (`bundle.language.enableSplit = false`), иначе Play
  ставит только язык телефона и переключение языка в настройках приложения перестаёт работать.
  Не удаляйте этот блок.
- **Регистрация имени пакета.** Play Console при создании приложения пишет «You can't use this package
  name because it hasn't been registered». Порядок
  ([справка Google](https://support.google.com/googleplay/android-developer/answer/16761053)):
  1. Play Console → **Android developer verification** → **Package names** → **Register package name** → `app.farwalker`.
  2. **Select key** → ключ с SHA-256 `631b6242d960be177c68f5d36dc86b8ff424c5692b80ead3d6c17bdceb9e613f`
     (сертификат CN=Farwalker, O=Stillcraft — им подписаны все релизы).
  3. **Upload APK** → скопировать сниппет, собрать релизный APK с файлом `assets/adi-registration.properties`,
     в котором этот сниппет, подписать тем же ключом и загрузить. Ключ есть только в секретах GitHub,
     так что APK собирает workflow **Package registration proof** (Actions → Run workflow → вставить сниппет),
     APK — в артефактах запуска.
  4. Дождаться письма о регистрации, потом создавать приложение.
- **Подпись (Play App Signing).** При первой загрузке выберите **«Использовать свой ключ»** и загрузите ключ
  из того же keystore через PEPK (инструкция в самом диалоге). Так `app.farwalker` остаётся за одним
  зарегистрированным ключом, а APK из GitHub и версия из Play обновляют друг друга.
  Если оставить «Google создаст ключ подписи», версии из Play и GitHub друг на друга не встанут.
- **versionCode** берётся из номера запуска workflow (`github.run_number`), он только растёт — Play это устраивает.
- **Новый личный аккаунт разработчика** (создан после ноября 2023): перед выходом в production нужен
  закрытый тест — минимум 12 тестировщиков, которые не выходят из теста 14 дней подряд.
- **Политика конфиденциальности** — `PRIVACY.md` в корне репозитория. После push ссылка:
  `https://github.com/bigdevwhale/farwalker/blob/main/PRIVACY.md`
  (или перенесите текст на сайт Stillcraft).
- **Видео для фоновой службы.** Play требует ролик (YouTube, можно «доступ по ссылке») для разрешения
  `FOREGROUND_SERVICE_HEALTH`, см. раздел 4. Снимите его заранее: 20–40 секунд записи экрана —
  открыть атлас с километрами за сегодня, свернуть приложение, показать в шторке «Путник в дороге»,
  пройтись, вернуться — километров прибавилось, путник на карте продвинулся, уведомление исчезло.

## 1. Создание приложения

| Поле | Значение |
|---|---|
| App name | `Farwalker: Walk the World` |
| Default language | English (United States) – en-US |
| App or game | **App** |
| Free or paid | Free |
| Declarations | обе галочки (Developer Program Policies, US export laws) |

## 2. Store listing → Main store listing

| Поле | en-US | ru-RU (Add translations → Russian) |
|---|---|---|
| App name (≤30) | `listing/en-US/title.txt` | `listing/ru-RU/title.txt` |
| Short description (≤80) | `listing/en-US/short_description.txt` | `listing/ru-RU/short_description.txt` |
| Full description (≤4000) | `listing/en-US/full_description.txt` | `listing/ru-RU/full_description.txt` |
| App icon 512×512 | `graphics/icon-512.png` | та же |
| Feature graphic 1024×500 | `graphics/feature-graphic-en.png` | `graphics/feature-graphic-ru.png` |
| Phone screenshots (2–8) | `graphics/screenshots-en/1…6.png` | `graphics/screenshots-ru/1…6.png` |
| Tablet screenshots | можно не загружать | — |
| Video | пусто | — |

Скриншоты 1080×1920 собраны `tools/play.py` из рендеров экранов (`app/build/art/screens-<язык>`,
1233×2673): Play не берёт картинки длиннее двух ширин, поэтому экран уходит за нижний край под подписью.
Как пересобрать:

```bash
./gradlew testDebugUnitTest -Prender --tests '*RenderArtScreens*'
python tools/play.py   # atlas, world, passport, goal, journal, road
```

## 3. Store settings

| Поле | Значение |
|---|---|
| App category | Application → **Health & Fitness** |
| Tags (до 5) | Activity tracker, Health & fitness, Workout, Entertainment, Lifestyle |
| Email address | *ваш публичный email* — обязательно, его увидят все |
| Website | `https://bigdevwhale.github.io/stillcraft/apps/farwalker/` |
| Phone | пусто |
| External marketing | можно оставить включённым |

**Категория.** «Кругосветка» — путешествие по карте, но приходят в неё за шагомером: в Health & Fitness
её найдут по «шагомер», «pedometer», «ходьба». Альтернатива — **Travel & Local**, но там ищут билеты и
путеводители по настоящим поездкам. Категорию можно сменить позже в Store settings.

**Теги** — из списка Play для приложений, те же, что у Липовки. Activity tracker и Health & fitness — это
шагомер; Workout — туда же Play относит шагомеры для ходьбы; Entertainment и Lifestyle — путешествие,
паспорт и атлас. Weight loss и Running не подходят: про похудение и бег приложение не говорит.

**Имя разработчика** (Settings → Developer account) — **Stillcraft**, одинаковое для всех приложений:
тогда Play сам покажет под карточкой «Другие приложения этого разработчика».

## 4. App content (Policy → App content)

**Privacy policy** — URL выше.

**App access** — *All functionality is available without any access restrictions.*

**Ads** — *No, my app does not contain ads.*

**Content rating** (анкета IARC)
- Email — ваш; категория — **All Other App Types**.
- На все вопросы — **No**: насилия, страха, сексуального контента, ненормативной лексики, алкоголя,
  табака и наркотиков нет; монеты — внутренняя валюта за километры, их нельзя купить, азартных игр нет;
  общения между пользователями и обмена контентом нет; местоположение другим пользователям не передаётся;
  браузера и поиска по интернету нет (поиск городов — по встроенному списку).
- Ожидаемый результат: Everyone / PEGI 3 / 0+.

**Target audience and content**
- Target age: **13–15, 16–17, 18 and over** (политика: приложение не для детей младше 13).
  Младше 13 не отмечать: иначе действует политика Families с отдельными требованиями к сетевым запросам.
- *Could your store listing unintentionally appeal to children?* → **No**: гравированный атлас,
  паспорт и дневник, без мультяшных героев.

**News apps** — **No.**

**COVID-19 contact tracing and status apps** — My app is not a publicly available COVID-19 app.

**Data safety**
- Разработчику не уходит ничего: нет серверов, аналитики, отчётов о сбоях. Шаги, путешествие и дневник
  лежат на телефоне. Единственный трафик — погода: координаты путника на карте (3 знака после точки)
  уходят в Open-Meteo или MET Norway. GPS не используется, но в начале пути путник стоит в вашем городе,
  поэтому по определению Google это всё же местоположение — честнее задекларировать:
  - *Does your app collect or share…?* → **Yes**.
  - *Is all of the user data collected by your app encrypted in transit?* → **Yes** (только HTTPS).
  - *Do you provide a way for users to request that their data is deleted?* → **No** (на сервере
    ничего не хранится; всё стирается вместе с приложением).
  - Data types: **Location → Approximate location** — *Collected*: Yes, *Shared*: No (передача
    сервис-провайдерам по правилам Play не считается «sharing»); *Processed ephemerally*: **Yes**;
    **Required** (отключить погоду нельзя); purpose: **App functionality**.
  - Больше ничего не отмечать: шаги («Health and fitness → Fitness info»), рост, путешествие и дневник
    с устройства не уходят. Системное резервное копирование Android делает система, а не приложение,
    его не декларируем.
  - Итог в карточке: «Collects: Location», «No data shared with third parties», «Data is encrypted in transit».
- Вариант смелее — **No** на первый вопрос («No data collected»): уходят координаты нарисованного путника,
  а не телефона, и через пару недель пути они уже далеко от дома. Но ревью может придраться к первым дням
  пути — рекомендую декларацию выше.

**Government apps** — No.

**Financial features** — *My app doesn't provide any financial features.*

**Health apps** — отметьте **Activity and fitness** (шагомер). Health Connect, медицинские функции
и прочее — не отмечать.

**Advertising ID** — *No* (рекламы и SDK аналитики нет, разрешения `AD_ID` в сборке нет).

**Foreground service permissions** (появится после загрузки AAB: targetSdk 36 и `FOREGROUND_SERVICE_HEALTH`)
- *What tasks require your app to use the FOREGROUND_SERVICE_HEALTH permission?* → отметить только
  **Step counter** (Health data sync и Exercise tracker — нет). Описание, если просят:
  > Farwalker is a pedometer: the user's real steps, read from the phone's step counter sensor, walk a
  > traveller across a map of the world. Android only delivers step counts to an app the user can see, so
  > when the user leaves the app a health foreground service with a visible, silent ongoing notification
  > ("The traveller is on the road") keeps reading the step counter. It runs only if the user granted
  > Physical activity, stops as soon as the app is opened again or swiped away, and steps never leave the device.
- Ссылка на видео — см. раздел 0.
- ⚠️ Служба стартует сама, когда приложение уходит в фон, а не по отдельной кнопке. Для шагомера это
  обычная схема (и в описании это сказано честно), но если ревью отклонит декларацию, напишите —
  можно добавить в настройки переключатель «Считать шаги в фоне».

**Прочие разрешения** — особых деклараций не нужно: `ACTIVITY_RECOGNITION` (шагомер), `POST_NOTIFICATIONS`,
`INTERNET` не относятся к чувствительным, как и `RECEIVE_BOOT_COMPLETED`, `WAKE_LOCK` и
`ACCESS_NETWORK_STATE`, которые добавляет WorkManager. Точных будильников (exact alarm) и геолокации нет —
периодическая работа идёт через WorkManager.

## 5. Pricing & distribution

- Countries / regions: все доступные.
- Free, без встроенных покупок.

## 6. Релиз

1. Testing → **Internal testing** (или Closed testing, если аккаунт новый) → Create new release.
2. Загрузить `Farwalker-v1.0.2.aab` из GitHub Release.
3. Release name: `1.0.2`. Release notes:

```
<en-US>
(listing/en-US/release_notes.txt)
</en-US>
<ru-RU>
(listing/ru-RU/release_notes.txt)
</ru-RU>
```

4. Review release → Start rollout. Первая проверка Google обычно занимает от нескольких дней до недели;
   с фоновой службой — может дольше.
5. После установки из Play проверьте: Настройки → Язык → Русский/English — экран должен
   переключиться сразу; шаги считаются, в том числе со свёрнутым приложением; поиск города и цели
   находит города на обоих языках; погода у путника показывается.

## Следующие версии

- `release_notes.txt` переписать под новую версию (для первой там «First release on Google Play»).
- Скриншоты пересобирать, только если поменялись показанные экраны.

## После публикации

Добавьте в README бейдж и ссылку `https://play.google.com/store/apps/details?id=app.farwalker`.
