# HTTrack → PDF: PC и Android

Инструменты для архивации сайтов/блогов (LiveJournal и др.) в **PDF-книгу с
оглавлением**. Проект разделён на две части — для ПК и для Android.

```
pc/                      ← версии для компьютера (Windows / Linux)
  httrack_pdf_mod/         модуль HTTrack + standalone httrack2pdf
                           (CLI и Win32-GUI), очистка HTML, экспорт в PDF,
                           склейка через Ghostscript, автозагрузка браузера

android/                 ← версии для Android
  httrack-pdf/             форк официального HTTrack Android + кнопка
                           «PDF book» (фоновый сервис: WebView→PDF + PDFBox),
                           нативные .so взяты из готового APK (без NDK)
  lj2pdf/                  отдельное приложение: вводишь URL блога (LiveJournal,
                           Habr, Facebook, произвольный сайт) → скачивает и
                           собирает книгу PDF/EPUB или корпус RAG

docs/                    ← документация
  facebook.md              вход как браузер (Android + ПК), границы, риски
```

CI живёт в корне репозитория: **`.github/workflows/lj2pdf-android.yml`** —
автосборка обоих Android-APK (debug), Linux-CLI и Windows-GUI `.exe`
(кросс-сборка MinGW). Срабатывает на любые изменения в `html2pdf/**`
(и вручную через *Actions → Run workflow*).

## Сборка

| Что | Как |
|-----|-----|
| **ПК, Windows, в один клик** | дабл-клик `pc/httrack_pdf_mod/autobuild.bat` — сам ставит портативный MinGW (если нужно), собирает GUI+CLI и запускает |
| ПК, Linux CLI | `make -C pc/httrack_pdf_mod exe` → `httrack2pdf` |
| ПК, Windows GUI (вручную) | `pc/httrack_pdf_mod/build-win-gui.bat` (нужен MinGW) → `httrack2pdf.exe` |
| Android `httrack-pdf` | открыть `android/httrack-pdf` в Android Studio → Build APK |
| Android `lj2pdf` | открыть `android/lj2pdf` в Android Studio → Build APK |
| Всё сразу | пуш в репозиторий → **GitHub Action** соберёт артефакты |

Подробности — в `README.md` каждого подпроекта.

## Страницы за логином (Facebook)

Facebook отдаёт стену только вошедшему пользователю и не даёт открытого API для
выгрузки, поэтому обе части проекта авторизуются **как браузер, а не как
приложение** — без регистрации приложения и без API-ключей:

| Где | Как |
|-----|-----|
| Android (`lj2pdf`) | кнопка **FB → Войти**: настоящая страница входа в `WebView`, дальше загрузчик ходит с теми же cookie; читается `mbasic.facebook.com` |
| ПК (`httrack2pdf`) | `--login <папка-профиля>` — один раз войти в обычном окне браузера, дальше печатать с `profile=<папка-профиля>` |

Для страниц, где вся ценность в картинках, есть режим **«Только изображения»**:
фотографии сохраняются **в оригинале** (mbasic отдаёт только превью — архиватор
переходит по ссылке на полный файл), мусор отсеивается, дубликаты отбрасываются
по содержимому, на выходе папка с файлами и альбом **CBZ / PDF**.

Подробности, границы применимости и предупреждение о правилах Facebook —
в [`docs/facebook.md`](docs/facebook.md).

## Статус

Десктопные сборки проверены на Linux. Android-проекты собираются в Android
Studio / CI; на устройстве в этом окружении не проверялись (нет Android SDK).
GitHub Action даёт готовые артефакты для скачивания на вкладке *Actions*.
