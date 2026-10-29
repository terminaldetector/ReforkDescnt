"""Stage the full-volume physical hull only after exact-source CI gameplay checks pass."""
from pathlib import Path
import hashlib
import importlib.util
import json
import os
import shutil
import subprocess
import zipfile

VERSION = '1.1.33-combat.1'


def validate_proof(proof, source):
    if proof['source_sha'] != source or proof['version'] != VERSION:
        raise ValueError('Unexpected tested source or boarding version')
    if proof['unit']['tests'] < 657 or proof['gametest']['tests'] < 212:
        raise ValueError('Contact unit and world regressions are missing')
    if proof['jar'] != f'drmd-6dof-{VERSION}-fabric-1.21.1.jar':
        raise ValueError('Unexpected installable JAR')
    contact=proof.get('hull_contacts',{})
    if not all(contact.get(key) is True for key in ('actualHullPairs','wholeTickIntegrated','sequentialThreeHulls','rotatedHullImpulse')):
        raise ValueError('Actual physical hull contact proof is missing')
    fragment=proof.get('body_fractures',{})
    if not all(fragment.get(key) is True for key in ('actualFragments','continuousMaterialMotion','fastFragmentTickIntegrated','fragmentNbtRoundTrip')):
        raise ValueError('Physical fragment client proof is missing')

    combat=proof.get('body_combat',{})
    if not all(combat.get(key) is True for key in ('actualHitscanDamage','actualProjectileDamage','actualRadialDamage')):
        raise ValueError('Actual hull combat proof is missing')


def main():
    if os.environ.get('GITHUB_ACTIONS') != 'true':
        raise RuntimeError('CI evidence required')
    root = Path(__file__).resolve().parents[1]
    source = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root, text=True).strip()
    if source != os.environ['GITHUB_SHA']:
        raise ValueError('CI source mismatch')
    spec = importlib.util.spec_from_file_location('checkpoint_report', root / 'scripts/checkpoint_report.py')
    gate = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(gate)
    gate.verify(root)
    started = int((root / 'build/checkpoint/started-at.txt').read_text())
    gate.verify_audit(root, started)
    gate.verify_native_audit(root, started)
    proof = json.loads((root / 'build/checkpoint/verified.json').read_text())
    validate_proof(proof, source)
    gate.verify_boarding(root, started)
    gate.verify_ship_boarding(root, started)
    gate.verify_body_edits(root, started)
    gate.verify_body_index(root, started)
    gate.verify_body_scale(root, started)
    gate.verify_hull_contacts(root, started)
    gate.verify_body_fractures(root, started)
    gate.verify_body_combat(root, started)
    for name in ('snapshot', 'mirror-join', 'horizon-anchor', 'repair-visual'):
        gate.fresh(root / f'build/shootcheck/{name}-ok.json', started)
    snapshot = json.loads((root / 'build/shootcheck/snapshot-ok.json').read_text())
    if not all(snapshot.get(k) for k in ('actualWeaponInput', 'stableToolRoll', 'actualDroneInventory')):
        raise ValueError('Snapshot input regression is incomplete')
    if len(snapshot['scenes']) != 5 or any(s['frames'] < 90 for s in snapshot['scenes']):
        raise ValueError('Snapshot frames are incomplete')
    for scene in snapshot['scenes']:
        gate.fresh_png(root / f'build/shootcheck/snapshot-frames/{scene["scene"]}.png', started)
    jar = root / 'build/checkpoint/distribution' / proof['jar']
    if hashlib.sha256(jar.read_bytes()).hexdigest() != proof['sha256']:
        raise ValueError('JAR checksum mismatch')
    out = root / 'dist/releases' / VERSION
    out.mkdir()  # Existing verified packages are immutable.
    shutil.copy2(jar, out / jar.name)
    (out / 'SOURCE_SHA.txt').write_text(source + '\n')
    run = f"https://github.com/{os.environ['GITHUB_REPOSITORY']}/actions/runs/{os.environ['GITHUB_RUN_ID']}"
    (out / 'INSTALL_TLAUNCHER.txt').write_text(f'''UNGRAVITY {VERSION} — Minecraft1.21.1 / Fabric / Java21
Установить один {jar.name} в mods вместе с Fabric API; удалить предыдущий DRMD JAR.
Immersive Portals не требуется. N — инвентарь дрона, ЛКМ/ПКМ — выбранные оружия,
B — вспомогательное, G — режим ракет. Управление портальной пушкой сохранено.
Для native Cubic Chunks нужен отдельный Better Cubic Chunks и новый cubic_overworld.
Этот CI подтверждает обычный мир; native BCC-проверка публикуется отдельно.
Столкновения физических корпусов: обе скорости, вращение, трение и остаток тика.
Последовательные и общие контакты трёх корпусов проверены клиентом.
Пределы физики512м/с и2рад/с; за пределом ограничитель рассеивает импульс.
Проверены посадка, удержание, ходьба и прыжок игрока на движущемся BlockBody.
Исправлено повторение старого поворота неподвижного корпуса при смене render tick.
Исправлен поиск дальних частей корпуса: объёмный индекс вместо секции центра.
Локальные разрушение/установка инертных блоков летящего BlockBody сохранены.
Обычные ЛКМ/ПКМ работают вне оружейного6DoF-режима; Survival тратит блоки и прочность.
Полные крен/тангаж и комбинированное вращение проверяются реальным клиентом.
/d6body gravity <корпус> 0 -32 0 — включить поле; 0 0 0 — выключить.
Поле действует на игрока; прочие физические сущности ещё не подключены.
Полный объём корпуса удерживается серверными chunk tickets / native XYZ views.
При уходе игрока или удалении корпуса удержание освобождается.
Клиент и сервер должны использовать одну версию; boarding protocol4.
Мобы/предметы на борту, большой UFO, joints, ControlCore и полный3D AI ещё не готовы.
SHA-256: {proof['sha256']}
Исходники: {source}
''')
    (out / 'README.md').write_text(f'''# UNGRAVITY {VERSION}

[Установочный JAR]({jar.name}) · [Установка](INSTALL_TLAUNCHER.txt) · [SHA-256](SHA256SUMS).

Промежуточный этап ТЗ: разрушение связующего блока отделяет независимые физические
компоненты существующего BlockBody. Каждая часть сохраняет мировые положения
материальных точек и скорость v+omega×radius; исходная идентичность остаётся у большой
связной части. Повторный урон не создаёт дубли; сетка и физическое состояние каждой
части сохраняются независимо. Проверка NBT не объявляется полной перезагрузкой мира.
Попадания hitscan и реальных снарядов повреждают конкретные локальные блоки.
Взрывы выбирают материал по расстоянию до поверхности, а не до центра масс корабля;
один пакет разрушений проходит через общую атомарную фрагментацию.
Частичный урон сохраняется в NBT и переносится вместе с отделившимися блоками.
Повреждение пока блочное: деформация, микросетка и экранирование взрыва бронёй
внутри движущегося корпуса остаются отдельной работой.
Скорость корпусов расширена до512м/с вместе с бюджетом512 подшагов; обломки свыше60м/с
сохраняют импульс на следующем физическом тике. Пакеты свыше256 новых частей либо
требующие скорости свыше512м/с отклоняются до изменения материала; очереди массового
разрушения и simulation LOD ещё требуют отдельной работы. Искусственное поле остаётся
у исходного корпуса до реализации владения ControlCore.

Ориентированные физические корпуса сталкиваются друг с другом.
CCD учитывает движение обеих сторон и обнаруживает пролёт через чистые конечные позиции.
Контактные площадки сохраняют края граней; ответный импульс и трение передаются обоим телам.
После удара досчитывается остаток тика; новая траектория может ударить третий корпус.
Есть защита телепортационных разрывов и неподвижной опоры при недоступных кубах мира.
При исчерпании бюджета движение консервативно приостанавливается с сохранением импульса.
Скорость расширена с50 до512м/с вместе с бюджетом512 подшагов; вращение пока2рад/с; полный стресс-тест боевого флота ещё открыт.
Клиент проверяет8 контактных сцен и отдельные11 сцен контактов/фрагментов с минимум650 сравнениями сетевой позиции.

Общий объёмный индекс находит дальние края корпуса для
посадки, камеры, прицела, редактирования и оружия. На корпус приходится один узел;
сканирования чанков и отсечения физических контактов по лимиту кандидатов нет.
Локальная сетка сохраняет разрушение и установку блоков
обычными ЛКМ/ПКМ. Сервер проверяет луч/дальность/revision, Survival платит
материалом и прочностью инструмента; удалённый блок выдаёт vanilla loot.
Постоянный pivot отделён от COM: правка массы сохраняет материальные точки.
Лимит расширен до32768 блоков, исходная сборка до128×128×128, редактирование[-32,128).
Пересчитываются27 соседних поверхностей и только затронутые16³ секции коллайдера.
Сжатая разреженная сетка совместима со старыми сохранениями; журнал до255 правок
передаётся отдельно от базового snapshot. Клиент проверяет300 правок и rebase.
Серверный мотор начинает шаг из реальной позиции игрока; протокол boarding4.
Сохраняются геометрические contact/sweep-запросы к локальному BVH
BlockBody и контакт двух D6-тел с взаимной реакцией и ограниченным трением.
Новый решатель применяется к опоре BlockBody на землю. Устранено повторение
старого угла неподвижного корпуса при сбросе partial tick. Первая корабельная гравитация игрока использует ориентированный коллайдер, локальный
C2S-ввод и предсказание. Полные крен/тангаж/комбинированное вращение, глаза, модель
и прыжок с проверкой просвета отверстия в потолке проверяет клиент. Пассажиры-мобы/предметы, joints, ControlCore и полноценный3D AI
остаются открытыми. Все12 критериев Boarding и9 UFO по-прежнему OPEN.

Проверенный source SHA: `{source}`. [Точный CI-прогон]({run}).
Unit: {proof['unit']['tests']}; серверные GameTests: {proof['gametest']['tests']}.
Полная регрессия настоящего клиента, оружие, инвентарь, зеркала/Fabulous и LOD.
Дополнительно5 mouse-сцен редактирования наклонённого корпуса,4 сцены дальнего края
и6 сцен корпуса32768 блоков с300 сетевыми правками и освобождением leases
с доказанным пропуском старым поиском на клиенте и сервере, реальным расходом материала
и минимум1 блоком физического перелёта.
[CI-отчёт](CI_REPORT.md) · [Доказательства](ci-evidence.zip) · [Детали этапа](../../../docs/v5/boarding/HULL_COMBAT_1.1.33.md).

Программная графика Linux; новая native BCC-сессия и Windows/RTX этим CI не
подтверждены. Предыдущий snapshot с native-проверками сохранён отдельно.
Упаковочные коммиты отличаются от проверенной исходной ревизии.
''')
    shutil.copy2(root / 'build/checkpoint/REPORT.md', out / 'CI_REPORT.md')
    (out / 'VERIFIED.json').write_text(json.dumps({'sourceSha': source, 'jarSha256': proof['sha256'], 'run': run, 'proof': proof}, indent=2))
    with zipfile.ZipFile(out / 'ci-evidence.zip', 'w', zipfile.ZIP_DEFLATED) as archive:
        for pattern in ('build/checkpoint/logs/*', 'build/checkpoint/resources/*',
                        'build/checkpoint/verified.json', 'build/checkpoint/SOURCE_SHA.txt',
                        'build/checkpoint/started-at.txt', 'build/shipboardingcheck/ship-boarding-ok.json',
                        'build/hullcontactcheck/body-contacts-ok.json','build/hullcontactcheck/body-contact-frames/*.png',
                        'build/fracturecheck/body-contacts-ok.json','build/fracturecheck/body-contact-frames/*.png',
                        'build/bodyindexcheck/body-index-ok.json','build/bodyindexcheck/body-index-frames/*.png',
                        'build/bodyscalecheck/body-scale-ok.json','build/bodyscalecheck/body-scale-frames/*.png',
                        'build/bodyeditcheck/body-edit-ok.json', 'build/bodyeditcheck/body-edit-frames/*.png',
                        'build/shipboardingcheck/ship-boarding-frames/*.png', 'build/boardingcheck/boarding-ok.json',
                        'build/boardingcheck/boarding-frames/*.png', 'build/test-results/test/*.xml',
                        'build/gametest/reports/*.xml', 'build/gametest/item-resource-inventory.tsv',
                        'build/rendercheck/*ok.*', 'build/rendercheck/screenshots/*.png',
                        'build/shootcheck/*ok.json', 'build/shootcheck/*.csv', 'build/shootcheck/*.tsv',
                        'build/shootcheck/*-frames/*.png', 'build/shootcheck/screenshots/audit-*.png'):
            for path in root.glob(pattern):
                if path.is_file():
                    archive.write(path, path.relative_to(root))
    (out / 'SHA256SUMS').write_text(''.join(f'{hashlib.sha256(p.read_bytes()).hexdigest()}  {p.name}\n'
                                         for p in sorted(out.iterdir()) if p.is_file()))
    readme = root / 'dist/README.md'
    previous = readme.read_text()
    readme.write_text(f'''# Текущий проверенный этап ТЗ — {VERSION}

[Скачать JAR](releases/{VERSION}/{jar.name}) · [Установка](releases/{VERSION}/INSTALL_TLAUNCHER.txt)
· [Проверки и ограничения](releases/{VERSION}/README.md) · [SHA-256](releases/{VERSION}/SHA256SUMS).

Разрушение связующих блоков отделяет самостоятельные физические фрагменты с сохранением материального движения.
Скорость расширена до512м/с; ориентированные корпуса сталкиваются с учётом обеих скоростей, вращения и трения.
CCD сохраняет стороны, остаток тика и передачу импульса в цепочке из трёх корпусов.
Общий объёмный индекс BlockBody сохраняет выбор и контакты у дальнего края.
Обычное локальное редактирование инертных блоков сохранено.
Центр масс меняется с сохранением положения и скорости оставшегося материала.
Большой UFO, абордаж с механизмами/мобами и полный3D AI ещё не готовы.
Проверка CI использует обычный мир и программную графику Linux.

Ниже сохранён предыдущий snapshot и его отдельные native BCC-проверки.

---

''' + previous)
    print('Verified full-volume hull lookup staged:', out)


if __name__ == '__main__':
    main()
