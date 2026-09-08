# SmartTube VOT — Yandex Voice-Over Translation for Android TV

**Неофициальный форк SmartTube с закадровым переводом видео через Яндекс.**

> SmartTube VOT is an unofficial fork of SmartTube. It is not affiliated with
> the SmartTube developers or Yandex.

[![Release](https://img.shields.io/github/v/release/Adrena1ine-ai/SmartTube-Yandex-VOT?display_name=tag&label=release)](https://github.com/Adrena1ine-ai/SmartTube-Yandex-VOT/releases/latest)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

Текущая версия: **SmartTube VOT 32.38 r1**, база — **SmartTube 32.38 Stable**.

## Возможности

- закадровый перевод англоязычных YouTube-видео на русский прямо из плеера;
- автоматический перевод нерусских видео;
- синхронизация перевода с воспроизведением, паузой, перемоткой и скоростью;
- раздельная громкость оригинала и перевода;
- использование готовой русской YouTube-дорожки, если она доступна;
- работа без OAuth;
- собственный Yandex OAuth-токен для более живого голоса (необязательно);
- отдельный пакет `org.smarttube.vot`, который устанавливается рядом с обычным
  SmartTube и не заменяет его.

## Скачать

Скачивайте APK только из раздела [Releases](https://github.com/Adrena1ine-ai/SmartTube-Yandex-VOT/releases).

Для релиза `v32.38-vot-public-r1`:

| Параметр | Значение |
|---|---|
| APK | `SmartTube_Yandex_VOT_32.38_r1_universal.apk` |
| SHA-256 APK | `5a0abf7aee347abc3b27877aa4722987733e8b761289a22218d0498698c51f56` |
| SHA-256 сертификата | `438644d831f64b92b2174ed616f83e690a3e62aabbf9d3f9169e055d1c026a7c` |
| Application ID | `org.smarttube.vot` |
| Архитектуры | `arm64-v8a`, `armeabi-v7a` |

Проверка файла в PowerShell:

```powershell
Get-FileHash .\SmartTube_Yandex_VOT_32.38_r1_universal.apk -Algorithm SHA256
```

## Установка

1. Скачайте APK из GitHub Releases.
2. Разрешите установку приложений из неизвестных источников.
3. Установите SmartTube VOT.
4. При необходимости включите кнопку **«Закадровый перевод»** в настройках
   кнопок плеера.
5. Откройте англоязычное видео и включите перевод.

Обычный SmartTube удалять не требуется:

```text
SmartTube      → org.smarttube.stable
SmartTube VOT  → org.smarttube.vot
```

Приложения имеют разные UID, каталоги данных, настройки, Android authorities и
сертификаты подписи.

## OAuth и более живой голос

OAuth-токен **не обязателен**: обычный перевод работает без него. Собственный
токен можно указать здесь:

```text
Настройки → Проигрыватель → Закадровый перевод (Яндекс)
```

Токен сохраняется только в приватных данных приложения пользователя. В исходном
коде и публикуемом APK пользовательских OAuth-токенов нет.

## Известные особенности r1

На некоторых длинных или редко переводимых видео Яндекс может долго готовить
озвучку, а в отдельных случаях запрос завершается ошибкой. Интерфейс использует
реальные состояния API и его ETA; искусственный процент по таймеру не создаётся.

- серый — перевод выключен;
- оранжевый — перевод подготавливается;
- зелёный — перевод воспроизводится;
- красный — ошибка.

Если ролик не переводится, создайте
[issue](https://github.com/Adrena1ine-ai/SmartTube-Yandex-VOT/issues/new/choose) и обязательно
приложите ссылку на видео. Не публикуйте OAuth-токены и другие секреты.

## Сборка из исходников

Требуется **JDK 11**. Старый D8/R8 этого проекта несовместим с JDK 21.

```powershell
.\gradlew.bat :common:testStstableDebugUnitTest --tests com.liskovsoft.smartyoutubetv2.common.vot.* --tests com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.AppUpdatePolicyTest --no-daemon
.\gradlew.bat :smarttubetv:assembleStvotRelease --no-daemon
```

`ststable` и `stvot` нужно собирать отдельными вызовами Gradle. Совместный вызов
активирует upstream Google Services plugin для public package.

Release-подпись не хранится в репозитории. Чтобы выпустить совместимое обновление
для `org.smarttube.vot`, сопровождающему нужен тот же отдельный закрытый ключ.
Публичный сертификат можно сверить по fingerprint выше.

Подробнее о реализации, тестировании и переносе на новую базу — в
[VOT_PORTING.md](VOT_PORTING.md). Оригинальный README находится в
[основном репозитории SmartTube](https://github.com/yuliskov/SmartTube#readme).

## Происхождение и благодарности

- [SmartTube](https://github.com/yuliskov/SmartTube) — основной проект;
- [SmartTube PR #5817](https://github.com/yuliskov/SmartTube/pull/5817) от
  [mikhkz](https://github.com/mikhkz) — исходный TV-native порт Yandex VOT;
- [ilyhalight/voice-over-translation](https://github.com/ilyhalight/voice-over-translation);
- [FOSWLY/vot-cli](https://github.com/FOSWLY/vot-cli);
- [FOSWLY/vot.js](https://github.com/FOSWLY/vot.js).

Сведения о лицензиях и атрибуции находятся в
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

## Отказ от ответственности

SmartTube VOT — неофициальная пользовательская модификация. Проект не связан с
разработчиками SmartTube или Яндекс. Для перевода используется неофициальный
Yandex VOT API; его работа, лимиты и доступность могут измениться независимо от
проекта.
