"""Keep three reading tabs, preserving every existing settings reference in Usage."""
from pathlib import Path
import json

root=Path(__file__).resolve().parents[1]
path=root/'src/main/resources/assets/sablelife/manual/catalog.json'
catalog=json.loads(path.read_text(encoding='utf-8'))
for chapter in catalog['entries']:
    sections=chapter['sections']
    before={key for values in sections.values() for key in values}
    merged=list(dict.fromkeys(sections['usage']+sections.get('settings',[])))
    chapter['sections']={'usage':merged,'example':sections['example'],'troubleshooting':sections['troubleshooting']}
    assert before=={key for values in chapter['sections'].values() for key in values},chapter['id']
catalog['revision']=max(184,catalog['revision'])
path.write_text(json.dumps(catalog,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print(f"Manual {catalog['revision']}: {len(catalog['entries'])} chapters, Settings merged into Usage without dropping text references.")
