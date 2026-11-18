"""Document the same bounded & formatting supported by creature names, for broadcasts."""
from pathlib import Path
import json
root=Path(__file__).resolve().parents[1]
base=root/'src/main/resources/assets/sablelife'
for language in ('en_us','zh_cn'):
    path=base/f'lang/{language}.json'
    strings=json.loads(path.read_text(encoding='utf-8'))
    text=('Supports &0–&f colors, &l bold, &o italic, &n underline, &m strikethrough, &k obfuscated and &r reset. Hex: &#FF5555. Example: &e&lMachine has arrived! Formatting counts toward the 256-character limit.' if language=='en_us' else
          '支持 &0–&f 颜色、&l 粗体、&o 斜体、&n 下划线、&m 删除线、&k 随机字符和 &r 重置。十六进制颜色：&#FF5555。示例：&e&l机械已降临！格式代码计入 256 字符上限。')
    key='wild.sablelife.announcement_format_help';strings[key]=text
    manual='manual.sablelife.natural_spawning.settings'
    addition=('Announcements support &0–&f colors, &l bold, &o italic, &n underline, &m strikethrough, &k obfuscated, &r reset and &#RRGGBB hex colors. Example: &e&lMachine has arrived! Formatting codes count toward the 256-character limit.' if language=='en_us' else
              '公告支持 &0–&f 颜色、&l 粗体、&o 斜体、&n 下划线、&m 删除线、&k 随机字符、&r 重置及 &#RRGGBB 十六进制颜色。例如：&e&l机械已降临！格式代码计入 256 字符上限。')
    if addition not in strings[manual]:strings[manual]+='\n\n'+addition
    path.write_text(json.dumps(strings,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    overlay=root/('scripts/english-manual-review.json' if language=='en_us' else 'scripts/chinese-polish.json')
    data=json.loads(overlay.read_text(encoding='utf-8'));data[key]=text;data[manual]=strings[manual]
    overlay.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
path=base/'manual/catalog.json';catalog=json.loads(path.read_text(encoding='utf-8'));catalog['revision']=max(185,catalog['revision'])
path.write_text(json.dumps(catalog,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
