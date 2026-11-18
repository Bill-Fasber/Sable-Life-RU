"""Laser sweep/spacing controls and server-owned faction creation policy, bilingual."""
from pathlib import Path
import json

root = Path(__file__).resolve().parents[1]
assets = root / 'src/main/resources/assets/sablelife'

def read(path):
    return json.loads(path.read_text(encoding='utf-8'))

def write(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')

labels = {
    'dashboard.sablelife.sweepSpeed': ('Horizontal sweep speed (°/s)', '水平扫射速度（度/秒）'),
    'dashboard.sablelife.verticalSweepSpeed': ('Vertical sweep speed (°/s)', '垂直扫射速度（度/秒）'),
    'dashboard.sablelife.laserParticleSpacing': ('Extra particle spacing (blocks)', '额外粒子间距（格）'),
    'dashboard.sablelife.laser_sweep_speed_help': (
        '0 = automatic: sweep once over the firing duration, as before. A positive value sets degrees per second for this axis. Reaching the end early holds that angle; a slow sweep may end before reaching it. The angle sign controls direction. Does not change damage count or interval. Only shown for a nonzero sweep angle in fixed-direction mode.',
        '0＝自动，沿用原来在一轮发射时间内扫完的方式。大于 0 时，按本轴每秒转过的角度扫射。提前到达末端后保持角度；速度太慢可能在扫完前结束。扫射角度的正负决定方向，不改变伤害次数或间隔。仅在固定方向且对应扫射角度不为 0 时显示。'),
    'dashboard.sablelife.laser_particle_spacing_help': (
        'Spacing along the beam for selected extra particles. Smaller values give denser effects. Default 4 blocks; range 0.25–32. Does not change the built-in beam, damage or firing frequency. Long beams and dense effects remain subject to visual budgets.',
        '控制沿光束分布的自选额外粒子，数值越小越密。默认 4 格，范围 0.25–32 格。不改变内置光束、伤害或发射频率。长光束和密集特效仍受显示预算限制。'),
    'relations.sablelife.faction_name_required': ('Please enter a faction name.', '请输入阵营名称。'),
    'relations.sablelife.faction_name_reserved': ('Wilds is reserved. Please choose another faction name.', 'Wilds 为保留名称，请换一个阵营名称。'),
    'relations.sablelife.faction_name_invalid': ('Use up to 40 characters, without control characters.', '阵营名称最多 40 个字符，不能包含控制字符。'),
    'relations.sablelife.faction_name_duplicate': ('A faction with this name already exists.', '已存在同名阵营，请换一个名称。'),
    'relations.sablelife.faction_server_limit': ('The server has reached its 256-faction limit.', '服务器阵营总数已达到 256 个上限。'),
    'relations.sablelife.faction_player_limit': ('You already own the maximum number of factions (%s). The server config controls this limit.', '你创建的阵营已达到个人上限（%s 个），可由服务器 config 调整。'),
}
layout_path = root / 'scripts/manual-paragraphs.json'
layout = read(layout_path)

for index, lang in enumerate(('en_us', 'zh_cn')):
    path = assets / f'lang/{lang}.json'
    strings = read(path)
    overlay_path = root / ('scripts/english-manual-review.json' if index == 0 else 'scripts/chinese-polish.json')
    overlay = read(overlay_path)
    for key, pair in labels.items():
        strings[key] = overlay[key] = pair[index]
    key = 'manual.sablelife.laser.settings'
    paragraphs = strings[key].split('\n\n')
    paragraphs[0] = (
        'Beam and damage\nAfter charging, beam length and width determine contact. Damage count × damage interval determines the firing duration; the firing interval separates bursts. A zero sweep angle fixes that axis.' if index == 0 else
        '光束与伤害\n蓄力后发射，长度和宽度决定命中范围。伤害次数×伤害间隔决定一轮发射时间，发射间隔分隔各轮。扫射角度为 0 时，对应轴保持固定。')
    if not any(p.startswith(('Sweep speeds\n', '扫射速度\n')) for p in paragraphs):
        paragraphs.insert(1, (
            'Sweep speeds\nIn fixed-direction mode, a nonzero horizontal or vertical sweep angle reveals its own speed control (0–720°/s). The default 0 preserves the old automatic timing. Positive speeds are independent of damage timing: start at one edge, sweep toward the other, then hold if reached early. Slow sweeps may finish partway through the angle. Negative angles reverse that axis. Each burst starts again from the first edge.' if index == 0 else
            '扫射速度\n固定方向模式下，水平或垂直扫射角度不为 0 时，会显示对应速度，范围 0–720 度/秒。默认 0 沿用自动速度。大于 0 时，按设定速度从一端扫向另一端，提前扫完后保持在末端；速度较慢时，可能在扫完前结束。角度为负会反转对应方向，每轮重新从起始端开始。调整速度不改变伤害次数和间隔。'))
    visual = (
        'The default continuous beam follows the moving muzzle, supports RGB color and interpolates between visual updates each frame. The legacy style keeps its particle beam. Extra particles are optional; their spacing is 0.25–32 blocks (default 4), with at most 64 sample positions per beam per tick and the shared player visual budget. Smaller spacing makes them denser. Particles already emitted retain their normal lifetime and may leave trails. Visual settings do not change hit width or damage timing.' if index == 0 else
        '默认连续光束跟随移动发射口，支持 RGB 颜色，并在两次视觉更新之间逐帧平滑过渡。旧粒子样式继续保留。自选额外粒子的间距为 0.25–32 格，默认 4 格，越小越密；每束每个游戏刻最多 64 个采样位置，并受玩家共享显示预算限制。已生成的粒子按自身寿命消失，可能留下拖尾。视觉设置不改变伤害宽度或命中时序。')
    # Keep every terrain, origin and impact paragraph after the presentation section.
    paragraphs = paragraphs[:2] + [visual] + layout['laser.settings'][lang][1:]
    layout['laser.settings'][lang][0] = visual
    strings[key] = overlay[key] = '\n\n'.join(paragraphs)
    key = 'manual.sablelife.core.settings'
    creation = (
        'Faction creation\nNon-operators can own one faction by default. maximumFactionsPerPlayer in the server config changes this limit; 0 removes it. Operators (permission level 2+) bypass the personal limit, while the global 256-faction limit still applies. Existing factions are retained, and joining multiple factions remains allowed. Deleting an owned faction frees a personal slot.' if index == 0 else
        '创建阵营\n普通玩家默认最多创建 1 个阵营。服务器 config 的 maximumFactionsPerPlayer 可调整个人上限，0 表示不限制。管理员（权限等级 2 及以上）不受个人上限限制，仍受服务器总共 256 个阵营的上限约束。已有阵营保留，玩家仍可加入多个阵营；删除自己创建的阵营后会空出名额。')
    if creation not in strings[key]:
        parts = strings[key].split('\n\n')
        at = next(i for i, p in enumerate(parts) if 'Each creature has one faction' in p or '每个生命体加入一个阵营' in p)
        parts.insert(at + 1, creation)
        strings[key] = '\n\n'.join(parts)
    overlay[key] = strings[key]
    write(path, strings)
    write(overlay_path, overlay)

write(layout_path, layout)
path = assets / 'manual/catalog.json'
catalog = read(path)
catalog['revision'] = max(183, catalog['revision'])
write(path, catalog)
