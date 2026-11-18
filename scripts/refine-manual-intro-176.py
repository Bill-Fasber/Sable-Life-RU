"""Apply the focused bilingual introduction review without regenerating module chapters."""
from pathlib import Path
import json

root = Path(__file__).resolve().parents[1]
base = root / 'src/main/resources/assets/sablelife'
retired = {'simple_start', 'basic_operations'}
updates = {
    'getting_started.usage': (
        'Start with an ordinary Heart Core in Creative mode. It gives an assembled Sable structure health and module-controlled behavior. Follow the six steps below to make one simple arrow attack.\n\nA Sable structure is your physical build; a creature is that build controlled by a Heart Core. Connected parts can include a body and limbs joined by bearings or joints.',
        '第一次使用时，先在创造模式下用普通生命核心练习。它让已组装的 Sable 结构拥有生命值，并通过模块控制行为。下面用六步完成一次简单的箭矢攻击。\n\nSable 结构是实际搭建的机械，生命体是由生命核心控制的结构；躯干和通过轴承或关节连接的肢体可以属于同一个生命体。'),
    'getting_started.settings': (
        'Choose a core\n\nHeart Core: practice in Creative mode. Survival editing is disabled by default; administrators can enable it.\n\nSurvival Heart Core: editable in Survival mode, with Energy costs for health and paid abilities. Read its chapter before activating.\n\nRedstone Core: runs supported controls on an assembled structure, without creature health or life activation.',
        '选择核心\n\n普通生命核心：先在创造模式练习，默认不允许生存编辑，管理员可另行开放。\n\n生存生命核心：可在生存模式编辑，生命值和收费技能使用能量；激活前请阅读其独立章节。\n\n红石核心：在已组装结构上运行支持的控制功能，没有生命值，也无需生命化。'),
    'getting_started.automation': (
        'Automation: three controls to learn\n\nOnce your first module works, focus on Move set, Wireless Channel and Wireless Redstone. Understanding how they connect is the key to building automated behavior.\n\nMove set arranges modules into moves. Conditions, priority and weight choose a move; action windows control when each referenced module runs and whether actions overlap. It is more than an attack list: supported movement, sound and function modules can also take part.\n\nWireless Channel is the core\u2019s channel page, not another module. Configure channels so modules can publish and read signals. Internal channel numbers belong to this core; external reception uses matching red/blue frequency items. A channel number is not a global wireless frequency.\n\nWireless Redstone is a module that turns its trigger into output: redstone at a marked face, external transmission using frequency items, or an internal channel signal. It can turn a Move set action window into a signal that other modules read.\n\nFor a first combination, enable channel 1 as Sequence / internal. Give a Move set the Always trigger and enable Ignore conditions for selection. Add a Wireless Redstone action from 0 to 2 seconds: use Move set as its trigger, Continuous output, Invert off, and internal output channel 1. An enabled, marked Particle effects module with a selected particle can then read internal channel 1. It emits during that window; existing particles finish their own lifetimes afterward. Internal output needs no marked redstone face or frequency items. Keep the wireless master switch on.\n\nThen read Move set for move selection and timing, Triggers and signals for inputs, and Wireless Redstone for outputs. Start with separate input and output channels to avoid accidental feedback.',
        '自动化从这里开始\n\n第一个模块运行成功后，重点理解下面三项。它们分别负责动作编排、频道连接和红石输出；理解三者如何配合，是制作自动化行为的关键。\n\nMove Set（自定义动作抽选）\n条件、优先级和权重决定选哪一招，动作窗口决定各模块何时运行、是否重叠。除了攻击，也能调度支持的移动、声音和功能模块。\n\nWireless Channel（无线频道）\n这是核心的频道页面。在这里配置频道，让模块输出或读取信号。内部编号只属于本核心；外部接收使用匹配的红蓝频率物品。频道编号不等于全局无线频率。\n\nWireless Redstone（无线红石）\n这个模块把触发转为输出：标记面的红石、频率物品的对外发射，或内部频道信号。它能把一段动作窗口变成信号，交给其他模块读取。\n\n小组合：先启用频道1，设为“序列控制／内部”。让抽选控制器持续激活，并开启“忽略条件参与抽选”。加入一个 0–2秒的无线红石动作：该模块由抽选控制器触发，输出选持续、反转关闭、内部输出频道1。再让已启用、已标记且选好粒子的粒子模块读取内部频道1，即可在这段窗口内发射粒子，已有粒子之后按自身寿命消失。内部输出无需标记红石面或设置频率物品，无线总开关需保持开启。\n\n随后分别阅读“自定义动作抽选”的选招与时序、“触发与信号”的输入，以及“无线红石”的输出。初学时先分开输入、输出频道，避免意外的信号自保持。'),
    'getting_started.example': (
        'Minimal arrow setup\n\nCore: ordinary Heart Core in Creative mode, assembled with one core across all connected parts.\n\nModule: Arrow launcher, enabled. Aiming: Straight. Trigger: Always. Start distance: 1 block. Mark: an outward-facing block face on the structure. Keep other settings at their defaults.\n\nPreview should place the muzzle outside the body. After life activation, the module fires along that direction. To aim at a moving target later, change the aiming mode and configure target selection separately.',
        '最小箭矢配置\n\n核心：创造模式下的普通生命核心，结构已组装，相连部件合计只有一个核心。\n\n模块：箭矢发射，已启用。瞄准：直线。触发：持续激活。初始释放位置：1 格。标记：结构上朝外的方块表面。其余属性先保持默认。\n\n预览时发射点应在身体外；生命体激活后，模块沿该方向发射。之后需要追踪移动目标时，再切换瞄准方式并设置目标筛选。'),
    'getting_started.troubleshooting': (
        'If nothing happens, first check that the structure is assembled and has only one core across its connected parts. Then check four things: the creature is active, the module is enabled and allowed by the server, any required mark is valid and connected, and the trigger is satisfied. For channel-controlled actions, also check the channel and wireless master switch. Paid Survival Heart Core actions need enough Energy. A Redstone Core does not need life activation.\n\nRelease before changing locked settings; controls intended for live use remain available. Sneak-right-click activates a dormant Heart Core but does not release an active creature.\n\nUse an empty hand to open the core; holding a block can disguise it instead. Release keeps saved settings but does not restore removed blocks or give death rewards.',
        '没有反应时，先确认结构已组装，且相连部件合计只有一个核心。再检查四件事：生命体已激活；模块已启用且服务端允许；必需标记有效并保持连接；触发条件成立。频道控制还要检查对应频道和无线总开关，生存生命核心的收费动作还需足够能量。红石核心无需生命化。\n\n锁定设置变灰时，先解除生命化；允许实时操作的控件仍可使用。潜行时空手右键可以激活休眠核心，不会解除已激活的生命体。\n\n打开核心时请空手，手持方块可能改为伪装核心。解除会保留已保存设置，但不会恢复已移除的方块，也不会触发死亡奖励。'),
    'module_management.usage': (
        'Manage abilities as virtual modules inside the core. Purple modules handle signals, red modules handle attacks, and yellow modules provide functions. Renaming a module keeps its original type; hover its sidebar entry to identify it.',
        '在核心内管理虚拟模块，不必为每种能力单独放实体模块方块。紫色为信号、红色为攻击、黄色为功能。改名后仍保留原类型，悬停列表名称即可辨认。'),
    'module_management.settings': (
        'Use + Add to browse All, Signal, Attack, Function or Presets. Search by the localized name, English name or module ID. Select a module and add it, or double-click to add it directly.\n\nApply edited names and numbers with their checkmark or Enter; mode buttons save immediately. Hover a number field to adjust it with the mouse wheel. Steps depend on the property: counts and channels usually change by 1, angles by 5 degrees, and small sizes or movement values can use decimals.\n\nThe sidebar search works across pages and accepts names, types and original numbers; #12 finds module 12 exactly. Clear the search to show all modules. Use the arrows beside Add to reorder them.\n\nCopy inserts an independent module after the original. It keeps marks, rules, channels and action references, but not running state. Recheck copied marks and bindings before using both modules together. Copy is unavailable at the module limit or when editing is locked.\n\nDisable keeps the module\u2019s configuration. Delete requires a second click to confirm removal.',
        '点击“添加”，按全部、信号、攻击、功能或预设筛选，也可搜索中文名称、英文名称或模块 ID。单击选中后添加，或双击直接加入。\n\n修改名称和数值后，用对应的勾或 Enter 保存；模式按钮切换后立即保存。悬停数值框可用滚轮调整，步长随属性变化：次数和频道通常为 1，角度为 5 度，小尺寸和运动参数可保留小数。\n\n左侧列表支持跨页搜索名称、类型和原编号，输入 #12 可精确查找编号 12，清空搜索则恢复全部。添加旁的上下箭头用于调整模块顺序。\n\n复制会在原模块后插入独立副本，保留标记、规则、频道和动作引用，但不继承运行状态。同时使用时，请重新检查副本的标记和绑定。达到模块上限或当前不可编辑时，无法复制。\n\n停用会保留配置；删除需要再次点击确认。'),
    'module_management.color_picker': (
        'Color picker\n\nFor settings that support color, click RGB to choose hue, saturation and brightness visually. Expand Precise RGB to enter values from 0 to 255. The preview compares the original and new colors. Confirm saves the choice; Cancel or Esc discards it. Use presets returns to preset colors while keeping the custom value for later.',
        '色盘\n\n支持颜色的属性可点击 RGB，调整色相、饱和度和明暗，也可展开“精确 RGB”输入 0–255 数值。预览会对比原色与新色。点击确定保存，取消或 Esc 放弃修改；“使用预设”返回预设颜色，并保留自定义颜色供以后使用。'),
    'module_management.manual_controls': (
        'Language and layout\n\nLanguage switches between English and Simplified Chinese without restarting. Density adjusts the layout to your window and GUI scale. The manual keeps your chapter, tab and search when returning; right-click the density button to cycle backward.',
        '语言与布局\n\n语言按钮可即时切换英文与简体中文，无需重启。界面密度按窗口和游戏缩放调整排版。返回手册时会保留章节、页签和搜索内容；右键密度按钮可反向切换。'),
    'module_management.presets': (
        'Presets\n\nUse Save preset beside Delete module, enter a name, then confirm. Presets are saved in the client configuration and can be reused across worlds. They keep applied module settings, sound layers, particles and status effects; marks, arena centers and other module references need new bindings.\n\nDuplicate names receive a number suffix. The preset list has its own sorting arrows, and deletion needs a second click to confirm. Players hearing custom audio still need the corresponding files on their clients.',
        '预设\n\n点击“删除模块”旁的“保存预设”，输入名称并确认。预设保存在客户端配置中，可跨世界使用，保留已应用的模块属性、声音层、粒子和状态效果；标记、场地中心及其他模块引用需要重新绑定。\n\n重名时自动添加数字后缀。预设列表有独立的排序箭头，删除需要再次点击确认。自定义音频仍需在收听玩家的客户端提供对应文件。'),
    'module_management.example': (
        'Copy an existing Arrow launcher named Left cannon to make a Right cannon. Rename the copy, then mark a block on the right side: copying initially keeps the original mark. Disable the copy if you want to compare the two configurations without deleting either.\n\nSave a preset to reuse the configuration in another build. A preset needs new marks and bindings; it does not carry the old body with it.',
        '把已有的箭矢模块命名为“左炮”，再复制为“右炮”。修改副本名称后，重新标记右侧方块：复制时最初沿用的是原标记。需要对比两份配置时，可以暂时停用其中一个，不必删除。\n\n需要在另一台造物上复用时再保存预设。预设需要重新标记和绑定，不会把原来的身体一起带过去。'),
    'module_management.troubleshooting': (
        'A red number field contains an invalid value; use the displayed limits. Renaming does not break module references, but disabling or deleting an action required by a Move set can make the whole move unavailable.\n\nServer module switches are under [modules.heartCore], [modules.redstoneCore] and [modules.survivalHeartCore]. Setting one to false hides it from Add and suspends existing instances while keeping their settings. Re-enable it to restore operation. These switches cannot give a core an unsupported ability. For a module that still does not run, use the checklist in Getting Started.',
        '输入框变红表示数值无效，请按标示范围设置。改名不会改变模块的引用身份，但停用或删除 Move Set 的必需动作，可能使整条招式不可选。\n\n服务端模块开关分别位于 [modules.heartCore]、[modules.redstoneCore] 和 [modules.survivalHeartCore]。false 会从添加列表隐藏模块，并停止已有实例运行，设置仍保留；重新开启后恢复。这些开关不能让核心获得原本不支持的能力。其他无反应情况，请使用“核心入门”的排查清单。'),
}


def refine(catalog, languages, changed=None):
    catalog['entries'] = [e for e in catalog['entries'] if e['id'] not in retired]
    intro = next(e for e in catalog['entries'] if e['id'] == 'getting_started')
    intro['sections']['example'] = ['manual.sablelife.getting_started.example']
    intro['sections']['settings'] = ['manual.sablelife.getting_started.settings', 'manual.sablelife.getting_started.automation']
    for index, lang in enumerate(('en_us', 'zh_cn')):
        strings = languages[lang]
        for key in list(strings):
            if any(key == 'manual.sablelife.chapter.' + old or key == 'manual.sablelife.' + old or key.startswith('manual.sablelife.' + old + '.') for old in retired):
                del strings[key]
                if changed is not None: changed[lang].pop(key, None)
        for suffix, pair in updates.items():
            key = 'manual.sablelife.' + suffix
            strings[key] = pair[index]
            if changed is not None: changed[lang][key] = pair[index]
        key = 'manual.sablelife.server_limits.settings'
        note = (
            'allowEditHeartCoreUnderSurvivalMode defaults to false. Enabling it permits ordinary Heart Core editing in Survival mode, including loot, experience rewards and terrain destruction. It is separate from the Survival Heart Core, which does not need this switch.'
            if lang == 'en_us' else
            'allowEditHeartCoreUnderSurvivalMode 默认 false。开启后允许生存玩家编辑普通生命核心，包括战利品、经验奖励和地形破坏。这与生存生命核心是两回事，生存生命核心无需开启此开关。'
        )
        if note not in strings[key]: strings[key] += '\n\n' + note
        if changed is not None: changed[lang][key] = strings[key]
    catalog['revision'] = max(catalog['revision'], 176)
    if catalog['revision'] >= 178:
        import runpy
        runpy.run_path(str(root/'scripts/refine-manual-reading-178.py'))['refine'](catalog,languages,changed)


if __name__ == '__main__':
    catalog = json.loads((base / 'manual/catalog.json').read_text(encoding='utf-8'))
    languages = {lang: json.loads((base / f'lang/{lang}.json').read_text(encoding='utf-8')) for lang in ('en_us', 'zh_cn')}
    refine(catalog, languages)
    for index, lang in enumerate(languages):
        (base / f'lang/{lang}.json').write_text(json.dumps(languages[lang], ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
        overlay = root / 'scripts' / ('english-manual-review.json' if lang == 'en_us' else 'chinese-polish.json')
        reviewed = json.loads(overlay.read_text(encoding='utf-8'))
        for key in list(reviewed):
            if key not in languages[lang]: del reviewed[key]
        reviewed.update({'manual.sablelife.' + suffix: languages[lang]['manual.sablelife.' + suffix] for suffix in updates})
        reviewed['manual.sablelife.server_limits.settings'] = languages[lang]['manual.sablelife.server_limits.settings']
        overlay.write_text(json.dumps(reviewed, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    (base / 'manual/catalog.json').write_text(json.dumps(catalog, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    layout_path = root / 'scripts/manual-paragraphs.json'
    layout = json.loads(layout_path.read_text(encoding='utf-8'))
    for suffix in list(layout):
        if suffix.split('.')[0] in retired: del layout[suffix]
        elif suffix in updates:
            layout[suffix] = {lang: pair.split('\n\n')[1:] for lang, pair in zip(('en_us', 'zh_cn'), updates[suffix])}
    layout_path.write_text(json.dumps(layout, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print('Manual introduction refined:', len(catalog['entries']), 'chapters; revision', catalog['revision'])
