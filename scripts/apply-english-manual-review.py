"""Apply the reviewed English manual without regenerating the historical catalog.

Only existing translation keys are replaced. Paragraph markers are refreshed for
edited entries; module schemas, chapter order and Chinese translations are kept.
"""
from pathlib import Path
import json

root = Path(__file__).resolve().parents[1]
assets = root / 'src/main/resources/assets/sablelife'
path = assets / 'lang/en_us.json'
strings = json.loads(path.read_text(encoding='utf-8'))
review = json.loads((root / 'scripts/english-manual-review.json').read_text(encoding='utf-8'))
layout_path = root / 'scripts/manual-paragraphs.json'
layout = json.loads(layout_path.read_text(encoding='utf-8'))
for key, value in review.items():
    assert key in strings, f'Unknown translation key: {key}'
    assert value.strip() == value and value, key
    strings[key] = value
    if key.startswith('manual.sablelife.'):
        suffix = key.removeprefix('manual.sablelife.')
        if suffix in layout:
            layout[suffix]['en_us'] = value.split('\n\n')[1:]
path.write_text(json.dumps(strings, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
layout_path.write_text(json.dumps(layout, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
catalog_path = assets / 'manual/catalog.json'
catalog = json.loads(catalog_path.read_text(encoding='utf-8'))
catalog['revision'] = max(catalog['revision'], 161)
catalog_path.write_text(json.dumps(catalog, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print(f'Applied {len(review)} reviewed English entries; manual revision {catalog["revision"]}.')
