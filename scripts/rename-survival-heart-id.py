"""Rename the unpublished registry/resource ID; deliberately no legacy alias or migration."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
source = root / 'src'
old, new = 'survival_life_core', 'survival_heart_core'
changed = []
for path in source.rglob('*'):
    if path.is_file() and path.suffix in {'.java', '.json'}:
        if path.name == 'SurvivalCoreIdGameTests.java':
            continue  # This regression deliberately asserts that the former ID is absent.
        text = path.read_text(encoding='utf-8')
        if old in text:
            path.write_text(text.replace(old, new), encoding='utf-8')
            changed.append(str(path.relative_to(root)))
for path in list(source.rglob('*' + old + '*')):
    if not path.is_file():
        raise RuntimeError(f'Expected a resource file: {path}')
    target = path.with_name(path.name.replace(old, new))
    assert path.resolve().is_relative_to(source.resolve()) and target.resolve().is_relative_to(source.resolve())
    if target.exists():
        raise RuntimeError(f'Refusing to overwrite {target}')
    path.rename(target)
    changed.append(f'{path.relative_to(root)} -> {target.name}')
print('\n'.join(changed))
