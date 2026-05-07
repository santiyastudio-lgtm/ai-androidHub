# SantiyaLocalAiHub

Android-приложение для локальной работы с AI-моделями, GGUF-импорта, Gemma, OpenClaw Local и офлайн-инструментов.

## Статус

Проект находится в активной разработке.

Что реально есть в текущем состоянии репозитория:
- локальный AI-чат для Android;
- импорт GGUF-моделей через системный picker;
- локальное хранение и индексация импортированных моделей;
- встроенный store/download flow для моделей;
- OpenClaw Local как отдельный режим агента внутри приложения;
- офлайн-город / офлайн-карты как отдельное направление внутри приложения;
- поддержка и отправка логов через `@SantiyaSupportBot`.

Что важно понимать:
- часть функций ещё доводится и активно тестируется;
- OpenClaw Local уже встроен в приложение, но это Android-local адаптация, а не весь upstream-стек OpenClaw;
- не все сценарии одинаково стабильны на разных устройствах и объёмах RAM.

## Основные возможности

- локальный запуск текстовых моделей на устройстве;
- импорт внешних GGUF-моделей;
- выбор и активация локальных моделей внутри AI Hub;
- Gemma-path с приоритетом локального runtime устройства, когда он доступен;
- OpenClaw Local для агентного режима, браузерных и встроенных инструментов;
- офлайн-город и локальные travel/map сценарии;
- отправка логов и обращений в поддержку через `@SantiyaSupportBot`.

## Сборка

Требуется:
- Android Studio;
- JDK 17;
- Android SDK / NDK, совместимые с Gradle-конфигом проекта.

Сборка debug APK:

```bash
./gradlew :app:assembleDebug
```

APK будет в:

```text
app/build/outputs/apk/debug/
```

## Репозиторий

- GitHub: [santiyastudio-lgtm/ai-androidHub](https://github.com/santiyastudio-lgtm/ai-androidHub)

## Поддержка

- баги, логи и обращения в поддержку: `@SantiyaSupportBot`

## 💖 Поддержать проект

Карты:
- T-Bank карта (только РФ): `2200701933182781`
- Ozon Bank карта (только РФ): `2204320688009192`

Кошельки:
- Solana: `97j3xnrjHtM5dDUZ8xAkAKqxY1Axro4gvsPCkqgZKQTj`
- Ethereum: `0x061dE20Bb9b2fA9c1C3d8E38939092aCB76284fe`
- Bitcoin: `bc1qfyzhnhajm8rslkhell9mg54na2tla90e6dkf3d`

Поддержка через Telegram Stars:
- нажмите кнопку поддержки у `@SantiyaSupportBot`, чтобы отправить поддержку через Stars прямо боту
