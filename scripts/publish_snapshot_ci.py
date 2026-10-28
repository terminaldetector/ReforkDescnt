"""Publish only this explicitly requested snapshot after fresh exact-source CI evidence passes."""
from pathlib import Path
import hashlib, importlib.util, json, os, shutil, subprocess, zipfile
root=Path(__file__).resolve().parents[1]
assert os.environ.get('GITHUB_ACTIONS')=='true'
sha=subprocess.check_output(['git','rev-parse','HEAD'],cwd=root,text=True).strip()
assert sha==os.environ['GITHUB_SHA']
spec=importlib.util.spec_from_file_location('checkpoint_report',root/'scripts/checkpoint_report.py')
gate=importlib.util.module_from_spec(spec);spec.loader.exec_module(gate)
gate.verify(root)
started=int((root/'build/checkpoint/started-at.txt').read_text())
audit=gate.verify_audit(root,started);gate.verify_native_audit(root,started)
proof=json.loads((root/'build/checkpoint/verified.json').read_text())
assert proof['source_sha']==sha and proof['version']=='1.1.23-snapshot.1'
assert proof['unit']['tests']>=578 and proof['gametest']['tests']>=144
snapshot=root/'build/shootcheck/snapshot-ok.json';gate.fresh(snapshot,started)
result=json.loads(snapshot.read_text());assert all(result.get(k) for k in ('actualWeaponInput','stableToolRoll','actualDroneInventory'))
assert len(result['scenes'])==5 and all(s['frames']>=90 for s in result['scenes'])
for scene in result['scenes']:gate.fresh_png(root/f'build/shootcheck/snapshot-frames/{scene["scene"]}.png',started)
for name in ('mirror-join','horizon-anchor','repair-visual'):
    gate.fresh(root/f'build/shootcheck/{name}-ok.json',started)
gate.fresh(root/'build/rendercheck/collapse-view-ok.json',started)
view=json.loads((root/'build/rendercheck/collapse-view-ok.json').read_text());assert min(view.values())>=2000
out=root/'dist/releases'/proof['version'];assert not out.exists();out.mkdir(parents=True)
jar=root/'build/checkpoint/distribution'/proof['jar'];assert hashlib.sha256(jar.read_bytes()).hexdigest()==proof['sha256']
shutil.copy2(jar,out/jar.name);(out/'SOURCE_SHA.txt').write_text(sha+'\n')
run=f"https://github.com/{os.environ['GITHUB_REPOSITORY']}/actions/runs/{os.environ['GITHUB_RUN_ID']}"
install=f'''UNGRAVITY {proof['version']} — Minecraft 1.21.1 / Fabric / Java 21
Установить один {jar.name} в mods вместе с Fabric API; удалить предыдущий DRMD JAR.
Immersive Portals не требуется. N — инвентарь дрона, ЛКМ — основное,
ПКМ — второе, B — вспомогательное, G — режим ракет. Предметы для строительства
по-прежнему используют обычный ПКМ. Портальная пушка сохраняет два цвета кнопок.
Для native Cubic Chunks нужен отдельный Better Cubic Chunks и новый cubic_overworld.
Этот CI проверяет обычный мир; новый native кубический клиент не сертифицирован.
Advanced и старые автоматические генераторы отключены; существующие блоки не удаляются.
SHA-256: {proof['sha256']}
Проверенные исходники: {sha}
'''
(out/'INSTALL_TLAUNCHER.txt').write_text(install)
(out/'README.md').write_text(f'''# UNGRAVITY {proof['version']}

[Установочный JAR]({jar.name}) · [Установка](INSTALL_TLAUNCHER.txt) · [SHA-256](SHA256SUMS).

Snapshot исправлений оружия, инвентаря дрона, Advanced и рендерной изоляции.
Проверенные исходники: `{sha}`. [Точный прогон GitHub Actions]({run}).
578+ unit, 144+ серверных GameTests, обычная полная регрессия и матрица видеоаудита,
пять новых сцен настоящего ввода/инвентаря, зеркала с Fabulous и привязка горизонта.
См. [доказательства](ci-evidence.zip) и [автоматический отчёт](CI_REPORT.md).

Проверки используют программную графику. Windows/RTX3060 и актуальный native
Cubic Chunks этим прогоном не подтверждены. Динамический абордаж, joints,
ControlCore, полный 3D AI и полное Master ТЗ остаются открытыми.
Упаковочные коммиты отличаются от исходной ревизии, использованной при тестировании.
''')
shutil.copy2(root/'build/checkpoint/REPORT.md',out/'CI_REPORT.md')
(out/'VERIFIED.json').write_text(json.dumps({'sourceSha':sha,'jarSha256':proof['sha256'],'run':run,'snapshot':result,'audit':audit},indent=2))
with zipfile.ZipFile(out/'ci-evidence.zip','w',zipfile.ZIP_DEFLATED) as archive:
    for pattern in ('build/checkpoint/logs/*','build/checkpoint/resources/*','build/checkpoint/verified.json','build/checkpoint/SOURCE_SHA.txt','build/checkpoint/started-at.txt','build/test-results/test/*.xml','build/gametest/reports/*.xml','build/gametest/item-resource-inventory.tsv','build/rendercheck/*ok.*','build/rendercheck/screenshots/*.png','build/shootcheck/*ok.json','build/shootcheck/*.csv','build/shootcheck/*.tsv','build/shootcheck/snapshot-frames/*.png','build/shootcheck/mirror-join-frames/*.png','build/shootcheck/horizon-anchor-frames/*.png','build/shootcheck/repair-visual-frames/*.png','build/shootcheck/screenshots/audit-*.png'):
        for path in root.glob(pattern):
            if path.is_file():archive.write(path,path.relative_to(root))
(out/'SHA256SUMS').write_text(''.join(f'{hashlib.sha256(p.read_bytes()).hexdigest()}  {p.name}\n' for p in sorted(out.iterdir()) if p.is_file()))
readme=root/'dist/README.md';text=readme.read_text().replace('1.1.22-portals.1',proof['version'])
text=text.replace('Все 11 ролей', 'N — отдельный инвентарь дрона. ЛКМ / ПКМ — два выбранных оружия, B — вспомогательное.\nСтарые Advanced-генераторы отключены. [Пределы snapshot](releases/1.1.23-snapshot.1/README.md).\n\nВсе 11 ролей')
readme.write_text(text)
print('Verified snapshot ready for separate binary/evidence commits:',out)
