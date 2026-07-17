# SantiyaLocalAiHub

## Hermes Local agent

The default agent is now **Hermes Local**. It runs a GGUF model on the device,
uses the app's sandboxed Hermes tools, and does not start or connect to an
OpenClaw Gateway. The recommended local model is
`NousResearch/Hermes-3-Llama-3.1-8B-GGUF:Q4_K_M`.

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

Если приложение оказалось полезным, спасибо за поддержку. Она помогает мне
поддерживать локальный режим, исправлять ошибки и добавлять новые функции.
Перед переводом, пожалуйста, проверьте выбранную сеть.

Кошельки:

- Bitcoin (BTC, Bitcoin network): `bc1qhft9dxkn0g07zm9ht8zrfqyrh85djhueu4q49k`
- Ethereum (ETH, Ethereum network): `0x5311B0318A24F63196A572b447609bc336A4C7b2`
- Solana (SOL, Solana network): `9i76uPGouNh8KVB8LtippfFY7p6kG2ZSLbtLwPqb6i76`
- USDT (Tether, Solana/SPL network): `9i76uPGouNh8KVB8LtippfFY7p6kG2ZSLbtLwPqb6i76`
<!-- SANTIYA_SUPPORT_START -->
## Support / Поддержать проект

If this project has been useful to you, thank you for supporting it. Your help
gives me time to keep improving it, fixing rough edges, and shipping updates.
Please take a moment to double-check the network before sending funds.

Wallets / Кошельки:

- Bitcoin (BTC, Bitcoin network): `bc1qhft9dxkn0g07zm9ht8zrfqyrh85djhueu4q49k`
- Ethereum (ETH, Ethereum network): `0x5311B0318A24F63196A572b447609bc336A4C7b2`
- Solana (SOL, Solana network): `9i76uPGouNh8KVB8LtippfFY7p6kG2ZSLbtLwPqb6i76`
- USDT (Tether, Solana/SPL network): `9i76uPGouNh8KVB8LtippfFY7p6kG2ZSLbtLwPqb6i76`
<!-- SANTIYA_SUPPORT_END -->
