"""Retain raw image-test rounds and combine the explicit Complementary threshold retest."""
from pathlib import Path
import json
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
sources = [
    'runs/health-bar-water-shaders-397-final/water-health-bar-report.json',
    'runs/health-bar-water-complementary-397-final/water-health-bar-report.json',
]
samples = {}
for source in sources:
    report = json.loads((root / source).read_text(encoding='utf-8'))
    for sample in report['samples']:
        sample['source'] = source
        samples[(sample['mode'], sample['phase'])] = sample
assert len(samples) == 24 and len({s[0] for s in samples}) == 8
assert all(s['pass'] for s in samples.values()), 'A final image sample failed'
server_xml = root / 'runs/health-bar-final-397-20261108/build/gametest-report.xml'
tests = list(ET.parse(server_xml).getroot().iter('testcase'))
assert tests and not any(t.find('failure') is not None for t in tests)
units = []
for path in (root / 'build/test-results/test').glob('TEST-*.xml'):
    units.extend(ET.parse(path).getroot().iter('testcase'))
assert units and not any(t.find('failure') is not None for t in units)
summary = {
    'success': True,
    'imageSamples': list(samples.values()),
    'gameTests': len(tests),
    'unitTests': len(units),
    'note': 'Same production renderer across both final sources. The targeted rerun corrects a Complementary dark-blue-slot threshold misclassification; earlier raw reports remain unchanged.',
}
destination = root / 'diagnostics/health-bar-water-397-summary.json'
destination.write_text(json.dumps(summary, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print(f"24 image samples, {len(tests)} GameTests and {len(units)} unit tests passed")
