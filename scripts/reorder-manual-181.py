"""Apply the requested guide order, then signals/attacks/functions, without changing prose."""
from pathlib import Path
import json
import re

root = Path(__file__).resolve().parents[1]
path = root / 'src/main/resources/assets/sablelife/manual/catalog.json'
data = json.loads(path.read_text(encoding='utf-8'))
first = ['getting_started', 'core', 'module_management', 'marking_system', 'preview_system', 'triggers', 'wireless_channels']
categories = {}
source = (root / 'src/main/java/dev/sablelife/client/ModuleCategory.java').read_text(encoding='utf-8')
for names, category in re.findall(r'case (.*?) -> (WEAPON|SIGNAL);', source):
    for name in re.findall(r'"([a-z_]+)"', names):
        categories[name] = category
def order(entry):
    if entry['id'] in first:
        return first.index(entry['id'])
    if not entry.get('module'):
        return 7
    return {'SIGNAL': 8, 'WEAPON': 9}.get(categories.get(entry['module']), 10)
data['entries'] = sorted((e for e in data['entries'] if e['id'] not in {'retaliation', 'death_unloading'}), key=order)
data['revision'] = max(181, data['revision'])
path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
