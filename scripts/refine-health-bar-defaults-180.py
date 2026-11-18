"""Keep health-bar defaults and bilingual authoring sources in sync."""
from pathlib import Path
import json

root = Path(__file__).resolve().parents[1]
assets = root / 'src/main/resources/assets/sablelife'
for lang, overlay in [('en_us', 'english-manual-review.json'), ('zh_cn', 'chinese-polish.json')]:
    paths = [assets / f'lang/{lang}.json', root / 'scripts' / overlay]
    for path in paths:
        data = json.loads(path.read_text(encoding='utf-8'))
        for key in list(data):
            if 'health_bar' not in key or not isinstance(data[key], str):
                continue
            value = data[key]
            if lang == 'en_us':
                value = value.replace('Visibility defaults to Always.', 'Visibility defaults to After taking damage; Always is also available.')
                value = value.replace('default 2.5', 'default 1.6').replace('default 0.2', 'default 0.1')
                value = value.replace('Style defaults to Rectangle; Rounded ends gives a capsule outline with semicircular ends.', 'Style defaults to Rounded ends, with semicircular ends; Rectangle is also available.')
                value = value.replace('A thin gray-purple', 'A gray-purple')
                value = value.replace('Set Width 2.5, Height 0.2 and Start distance 0.75', 'Keep Width 1.6, Height 0.1 and Start distance 0.75')
            else:
                value = value.replace('显示方式默认“始终显示”。', '显示方式默认“受伤后显示”，也可切换为“始终显示”。')
                value = value.replace('默认 2.5', '默认 1.6').replace('默认 0.2', '默认 0.1')
                value = value.replace('样式默认“长方形”，可切换为两端半圆的“圆头”', '样式默认两端半圆的“圆头”，可切换为“长方形”')
                value = value.replace('灰紫色细边框', '灰紫色边框')
                value = value.replace('设置宽度 2.5、高度 0.2、初始距离 0.75', '保留默认宽度 1.6、高度 0.1、初始距离 0.75')
            data[key] = value
        path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
catalog_path = assets / 'manual/catalog.json'
catalog = json.loads(catalog_path.read_text(encoding='utf-8'))
catalog['revision'] = max(180, catalog['revision'])
catalog_path.write_text(json.dumps(catalog, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
