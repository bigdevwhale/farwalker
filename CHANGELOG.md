# Changelog

<!--
Telegram: end each version's section with a hidden block for the StillCraft chat. Start it with a
line "<!-- tg", then 2-4 short lines in Russian for users (what is new, what is fixed), no markdown,
then close it like any HTML comment. The release notes carry it, and the chat bot posts those lines.
-->

## [Unreleased]

### Added
- Sights: 81 famous places round the world, from the Eiffel Tower to Machu Picchu, each with a line on what it is. A sight is collected on reaching the town nearest it; they stand as stars on the map, wait in the goal search and under "Sights nearby", and fill a page of the passport, with a button to set out for any of them (#1, #3).
- Medals in the passport: km on foot, countries, sights, ends of the earth, parts of the world, a long day, the sea, round the earth, tasks done (#1).
- Tasks, in the journal: today's steps, the week's km and the days of the week the day's task was met. Sized by your own recent days; each pays coins as soon as it is done (#5).
- A widget for the home screen: the day and the land underfoot, where the traveller is, today's steps, the road to the next town and the day's task, beside a small engraved map of the country round the traveller (#3, #4).
- Sights, medals and tasks done are news of the road, in the journal and as notifications.

### Changed
- The passport opens on its own page: the states stamped, the km on foot, by train and by sea, the days on the road. Stamps, medals and sights are three tabs; the medals stand in a row for each kind, with how far it is to the next one, and the sights are split into the ones seen and the ones ahead, nearest first.
- The journal opens on the week: a bar for each day's steps with the day's task marked on it, and the three tasks under it. A day's medals and tasks done are gathered on one line each.

### Fixed
- Steps no longer stop being counted after coming back to the app within a moment (the notification shade, a permission dialog): Android could close the app as the background counting stopped too early, and nothing was counted until it was opened again (#7).
- A step counter reading lower than the last one without a reboot no longer lands the whole count since boot as new steps, and no reading brings more steps than could be walked since the last one.
- Step counting passed by on the first screens can be allowed later: on the atlas and in Settings → Steps.
- On MIUI and others that drop an app's background work when it is swiped away, the widget puts the quarter-hourly step reading back every half hour.
- Belarus is "Беларусь" in Russian (#2), and Kazakhstan's capital is Astana again.

<!-- tg
Кругосветка: достопримечательности мира (81 место) — звёзды на карте, коллекция в паспорте, можно выбрать целью; медали за километры, страны и места
Задания на день и на неделю, обновлённые паспорт и дневник; виджет с картой для рабочего стола
Исправлено: шаги переставали считаться после быстрого возврата в приложение; Беларусь и Астана названы правильно
-->

## [1.1.0] - 2026-11-03

### Added
- Steps can come from Health Connect: Settings → Steps picks the phone's own counter or Health Connect, where Samsung Health, Mi Fitness, Zepp Life, Google Fit, watches and bands put the steps they count. What is new on a day is walked, so a watch's late steps still arrive and nothing is counted twice when switching mid-day. Huawei Health doesn't share with Health Connect; the Health Sync app can carry its steps over.

<!-- tg
Кругосветка 1.1.0: шаги теперь можно брать из Health Connect — из Samsung Health, Mi Fitness, Zepp Life, Google Fit, часов и браслетов
Включается в настройках, раздел «Шаги»
-->

## [1.0.2] - 2026-10-29

### Added
- Your height, on the first screens and in the settings: the step length is worked out from it, so the steps turn into the right number of km. A measured step can still be set by hand.

### Fixed
- Zoomed out, the world keeps its shape instead of being pressed narrow when the traveller is far north.

<!-- tg
Кругосветка 1.0.2: длина шага теперь считается по росту — укажите его в настройках, и километры станут точнее
Карта мира при отдалении больше не сплющивается
-->

## [1.0.1] - 2026-10-29

### Fixed
- The app no longer freezes when you type your town on the first screens: the map stays smooth while the keyboard opens, and search answers at once.

<!-- tg
Кругосветка 1.0.1: приложение больше не зависает, когда на первом экране вводишь свой город
-->

## [1.0.0] - 2026-10-29

The traveller sets out.

### Added
- The real world from Natural Earth: 195 states and 47 other lands, 5,580 towns with their time zones, roads that keep to land with every border along them, sea lanes and the great crossings, real bridges and tunnels (the Øresund, the Channel Tunnel), and 22 ends of the earth from Cabo da Roca to the South Pole.
- Walking by the phone's own step counter, in the background too: the traveller goes kilometre for kilometre along the road to the goal, and every kilometre walked is a coin.
- Trains (a coin buys 8 km, from anywhere on the road to a station ahead) and ships (a coin buys 15 km, from the port where the road meets the sea), both running on the real clock.
- The engraved atlas: hatched seas, ripples along the coasts, hand-coloured borders of the countries stamped, the red line walked, the railway in black and white, names you can read at a glance.
- The passport: a stamp for every country, cut from its code, the parts of the world and the ends of the earth, km on foot, by train and by sea.
- The journal: where each night was spent, on the road between two towns, on the train or at sea, with the borders and the ports of the day.
- The goal search in English and Russian, with the nearest unstamped countries and ends of the earth, the length of the way, the sea in it and the countries it passes.
- Local time and the real weather where the traveller is (Open-Meteo, or MET Norway); news of the road as notifications; English and Russian.

<!-- tg
Новое приложение — «Кругосветка»: шагомер, который ведёт путника по настоящей карте мира, через границы и моря
За каждый пройденный километр — монета на поезд или корабль, за каждую границу — штамп в паспорте
Местное время и настоящая погода там, где сейчас путник, а впереди 195 стран и 22 края земли, от мыса Рока до Южного полюса
-->
