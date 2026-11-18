"""Shorten Guide/Signal Usage only; preserve the introduction and other tabs."""
from pathlib import Path
import json

ROOT = Path(__file__).resolve().parents[1]
# chapter | English Usage | Chinese Usage
DATA = r"""
core|Core settings control the creature's health, regeneration, targeting, protection and death behavior. They apply to the whole creature; the Survival Heart Core has its own fixed rules and Energy costs.|核心设置控制整个生命体的血量、回血、目标筛选、防护和死亡行为；生存生命核心另有固定规则与能量消耗。
module_management|Add, rename, copy and organize abilities inside the core. Purple modules handle signals, red modules handle attacks, and yellow modules provide functions. Presets let you reuse a module's settings.|在核心内添加、改名、复制和整理模块。紫色为信号，红色为攻击，黄色为功能；可用预设重复使用模块设置。
marking_system|Marks choose a module's origin, direction or area. Most marks follow the marked part, while some modules use a fixed world point. A mark selects a location, not a player or mob.|标记用于选择模块的起点、方向或范围。多数标记跟随部件移动，部分模块使用固定世界点；标记的是位置，不是玩家或生物。
preview_system|Preview shows a module's origin, direction, area or movement path before use. Blueprint previews can also show block models. A preview helps check the setup, but does not guarantee that an action or placement can succeed.|预览用于检查模块的起点、方向、范围或移动路线，蓝图还可显示方块投影。预览方便检查设置，但不代表动作或生成一定能够成功。
triggers|Triggers decide when an enabled module runs. Use continuous activation, marked-point redstone or wireless signals, or let a Move set schedule several modules together.|触发决定已启用模块何时运行。可使用持续启用、标记点红石或无线信号，也可由招式组安排多个模块配合运行。
wireless_channels|Wireless Channel manages a core's local signals and external links. Internal channels connect modules within one core; matching red/blue frequency items link different cores or compatible devices. Wireless Redstone is a separate module.|无线频道管理核心内部信号与对外连接。内部频道连接同一核心的模块，匹配的红蓝频率物品连接不同核心或兼容设备；无线红石是另一个模块。
survival_core|The Redstone Core controls supported modules on an assembled Sable structure without giving it health. Use it for signal-driven machines and automation; it is separate from the Energy-powered Survival Heart Core.|红石核心在已组装的 Sable 结构上运行受支持模块，不提供生命值，适合信号控制和机械自动化；它与消耗能量的生存生命核心不同。
survival_heart_core|The Survival Heart Core gives a Sable structure health and abilities in Survival mode. Refuel it with server-approved materials to pay for health, healing and paid modules. When life reaches zero, the structure releases automatically.|生存生命核心让 Sable 结构在生存模式中拥有血量和能力。投入服务器认可的材料补充能量，支付血量、回血及收费模块的消耗；生命清空后自动解除生命化。
natural_spawning|The Wild Encounter Tool lets operators spawn server blueprints around players using rules for placement, density, faction and cleanup. Fixed-world crafting tables protect nearby areas. Release after spawning makes new objects independent of their rule.|管理员可用野外生命体生成工具，按位置、密度、阵营和清理规则在玩家附近生成服务器蓝图。固定在世界中的工作台保护附近区域；生成后放生会让新对象脱离规则。
server_limits|Server config controls module availability, resource limits and Survival Heart Core prices. These settings apply to the server and all connected players; client settings cannot override them.|服务器配置控制模块开关、资源上限和生存生命核心价格，对服务器及所有联机玩家生效；客户端设置不能覆盖它们。
move_set|Runs named moves made of timed module actions, allowing attacks, movement and effects to overlap. Conditions select eligible moves, then Priority or Weight chooses the next move. Ignore conditions allows behavior without a target.|把多个模块安排成带时间轴的招式，让攻击、移动和特效配合运行。先按条件筛选招式，再按优先级或权重抽选；忽略条件可用于无目标行为。
health_phase|Outputs signals when the creature's health is within a chosen range or enters it. Use these signals to switch attacks, movement or effects between health phases.|在生命值处于指定范围内，或进入该范围时输出信号，可用于切换不同血量阶段的攻击、移动和特效。
damage_accumulation|Tracks actual health lost and outputs a signal when accumulated damage reaches a threshold, or when damage during a timed window meets a chosen condition. It can also invert its output.|根据实际损失的生命值计数，在累计伤害达到门槛，或限时内的伤害符合条件时输出信号，也可反转输出。
sequence|Plays a sequence of channel outputs and waiting periods. Use it to trigger several actions in order, either once or repeatedly while the input remains active.|按顺序执行频道输出与等待步骤，让多个动作依次触发；可执行一次，也可在输入有效时循环运行。
wireless_redstone|Outputs redstone at marked faces and can route signals to internal channels or external red/blue frequencies. It supports held signals, pulses and gradual changes in strength.|在标记面输出红石，也可向内部频道或外部红蓝频率传递信号，支持持续输出、脉冲和强度渐变。
command|Runs virtual impulse, repeating and chain commands from the core. It requires command-block permissions and is intended for administrator-controlled behavior.|从核心执行虚拟脉冲、循环和连锁命令，需要命令方块权限，适合管理员控制的行为。
sensor|Detects eligible targets inside a chosen area and outputs a signal. It can drive attacks or automation through marked-face redstone, internal channels or external frequencies; only loaded targets can be detected.|检测指定范围内的有效目标，并通过标记面红石、内部频道或外部频率输出信号，用于控制攻击或自动化；只能检测已加载的目标。
navigation|Compares a target's direction with the structure's marked front and outputs directional redstone signals. Connect them to your movement or aiming mechanism; Target alignment itself does not move or turn the structure.|比较目标方向与结构标记的正面，输出各方向红石信号，用于驱动自行搭建的移动或瞄准机构；目标对齐本身不会移动或转动结构。
hit_counter|Counts successful health-damaging hits from a selected attack module and emits a pulse at the threshold. Use it to follow up an attack after a chosen number of hits.|统计指定攻击模块实际造成生命值伤害的命中次数，达到门槛后输出脉冲，可用于命中数满足后衔接其他动作。
signal_logic|Combines internal channel conditions using All or Any, then outputs a held signal or a rising-edge pulse. Use it to make an action depend on several signals.|以全部满足或任一满足的方式组合内部频道条件，输出持续信号或上升沿脉冲，让动作由多个信号共同控制。
combat_event|Emits pulses after attributed kills or blocked attacks. Own death can send an external pulse during an ordinary Heart Core's death process; a Survival Heart Core releases when life is depleted instead.|在归属击杀或格挡事件满足条件后输出脉冲；自身死亡可在普通生命核心进入死亡流程时发送外部脉冲。生存生命核心生命清空后采用自动解除流程。
"""


def main():
    entries = {}
    for line in DATA.strip().splitlines():
        chapter, en, zh = line.split('|')
        assert chapter not in entries, chapter
        assert len(en) <= 330 and len(zh) <= 150, chapter
        entries[chapter] = {'en_us': en, 'zh_cn': zh}
    path = ROOT / 'src/main/resources/assets/sablelife/manual/catalog.json'
    catalog = json.loads(path.read_text(encoding='utf-8'))
    before = json.loads(json.dumps(catalog))
    for chapter in catalog['entries']:
        if chapter['id'] in entries:
            chapter['sections']['usage'] = [f'manual.sablelife.{chapter["id"]}.brief_usage']
    assert len(entries) == 21
    assert entries.keys() <= {chapter['id'] for chapter in catalog['entries']}
    for old, new in zip(before['entries'], catalog['entries']):
        assert old['sections']['example'] == new['sections']['example']
        assert old['sections']['troubleshooting'] == new['sections']['troubleshooting']
        if old['id'] == 'getting_started':
            assert old == new
    catalog['revision'] = max(194, catalog['revision'])
    path.write_text(json.dumps(catalog, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    for locale, overlay in [('en_us', 'english-manual-review.json'), ('zh_cn', 'chinese-polish.json')]:
        for rel in [f'src/main/resources/assets/sablelife/lang/{locale}.json', f'scripts/{overlay}']:
            path = ROOT / rel
            strings = json.loads(path.read_text(encoding='utf-8'))
            for chapter, values in entries.items():
                strings[f'manual.sablelife.{chapter}.brief_usage'] = values[locale]
            path.write_text(json.dumps(strings, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print('Short bilingual Usage:', len(entries), 'chapters; Getting Started and other tabs preserved.')


if __name__ == '__main__':
    main()
