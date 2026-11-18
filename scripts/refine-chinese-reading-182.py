"""Apply player-facing wording corrections without changing manual coverage or behavior."""
from pathlib import Path
import json

root = Path(__file__).resolve().parents[1]
assets = root / 'src/main/resources/assets/sablelife'

updates = {
    'getting_started.step_release': '''6. 解除生命化并修改设置

点击面板中的“解除生命化”，让生命体回到未激活状态，即可修改激活期间不可编辑的设置。也可以在创造模式下，手持“创造解除工具”右键生命体。

解除会保留结构和已保存的设置，并停止动作、清除当前运行状态。修改完成后，检查标记，再重新激活。模块会根据触发条件重新运行。''',
    'getting_started.automation': '''自动化从这里开始
自定义动作抽选根据条件、优先级和权重选招，再用动作窗口安排各模块何时开始、持续多久。它可以调度支持的攻击、移动、声音与功能模块。

无线频道是核心的频道管理页面。内部频道编号只在本核心内使用；外部接收需要匹配红、蓝两个频率物品，频道编号不能代替无线频率。

无线红石可以根据触发信号，在标记面输出红石、向外部频率发送信号，或向本核心的内部频道输出信号。这样便能用动作窗口控制其他模块。

第一个组合

1. 启用频道 1，设为“序列控制／内部”，并开启无线总开关。

2. 添加自定义动作抽选，触发设为“持续启用”，开启“忽略条件”，再添加一个 0–2 秒的无线红石动作。

3. 将这个无线红石模块交给自定义动作抽选触发，选择“触发期间持续输出”，关闭反转，将内部输出频道设为 1。此组合不需要标记输出面，也不需要频率物品。

4. 添加粒子特效模块，启用并完成标记、选择粒子，再让它读取内部频道 1。粒子会在动作窗口内持续发出，已经发出的粒子按各自寿命消失。

之后可分别阅读自定义动作抽选、触发与信号、无线红石章节。初学时请分开输入、输出频道，避免输出信号再次触发自身。''',
    'module_management.usage': '在核心面板中管理模块。紫色为信号、红色为攻击、黄色为功能。模块改名后仍保留原类型，悬停侧栏中的模块名称即可查看。',
    'module_management.settings': '''添加与编辑
点击“+ 添加”，可浏览全部、信号、攻击、功能和预设。搜索支持当前语言的名称、英文名和 ID。选中后点击添加，或双击直接加入。名称和数值可按 Enter 或点击勾号保存；模式按钮点击后立即保存。滚轮调整的步长随属性而变化。

查找与排序
侧栏搜索会跨页匹配模块名称、类型和原编号。输入 #12 可查找编号为 12 的模块；清空搜索后恢复显示全部。使用添加按钮旁的箭头调整顺序。

复制与移除
复制会在原模块后插入独立副本，保留标记、规则、频道和动作引用，清除运行状态。同时使用原模块和副本前，请检查这些绑定。激活期间不可编辑，或模块数量已达上限时，不能复制。禁用会保留设置；删除需要再点击一次确认。''',
    'marking_system.usage': '标记用于指定 Sable 结构上的方块、表面或范围。例如，在嘴部标记喷火的释放位置，用两个角点确定近战范围。攻击玩家或生物的目标由模块的目标设置决定。',
    'preview_system.usage': '激活前，先用预览检查释放位置、方向、作用范围和移动路径。范围与路径预览只显示形状，不会执行攻击或移动生命体；粒子预览则会显示选定的装饰粒子。',
    'freeze.troubleshooting': '''预警结束时，模块只捕获接触设定范围的有效对象。玩家不包括创造模式、旁观模式及已死亡的玩家；生物目标不包括 Sable 生命体和盔甲架。

冻结会将生物固定在捕获时的位置。它仍会寻找目标、攻击、转头和尝试移动；生命值、伤害和状态效果正常结算。模块不会关闭生物 AI，也不会改变重力设置。

多个模块施加的冻结分别计时。抓取仍可搬运被冻结的对象；松开时若冻结尚未结束，对象会停在松开的位置。触发关闭、模块删除、核心卸载或死亡、对象卸载或切换维度，以及时间到期时，都会解除对应来源的冻结。冻结状态不会保存到存档。''',
    'summoned_weapon.settings': '''生成与持续时间
每次激活生成一组武装。触发信号关闭或自定义动作抽选的动作结束后，已经生成和等待生成的武装仍会继续；再次激活会追加一组。每件武装在自己的存在时长结束后消失，生命体死亡或解除生命化时会统一清理。每个核心最多有 256 件正在运行或等待生成的武装。

攻击前摇从每件武装出现时开始计时，计入存在时长，默认为 0。引用模块本身的预警或蓄力时间仍然生效。默认外观是发射器，出现时播放星芒粒子和烟花发射声。生成、消失的粒子与声音可以分别选择，控件位于属性列表末尾。

引用模块
支持枪武器、箭矢发射、喷火、激光、斩击、落地雷、地震波、牵引、冻结、冲击波和抓取。释放位置、朝向和初始距离由武装决定；攻击范围和抓取偏移保留引用模块的设置。已经释放的预警会留在当时的位置。

同一阵型每 5 个游戏刻共同搜索一次目标，每个游戏刻更新位置、转向和攻击。“跟随”会让阵型随结构移动；固定世界位置则会让它留在释放位置。

预览
预览显示实际物品外观：绿色为模型轴线，橙色光点为发射口，蓝色为发射方向。预览与实际武装使用相同的物品显示方式，均包含模型绕 Y 轴旋转 180° 的修正。追踪目标的预览显示新武装刚出现时的第一步转向。

自定义动作抽选可以直接安排召唤武装。每次动作激活都会生成一组，动作结束不会缩短这组武装的存在时间。''',
    'summoned_weapon.example': '''1. 新建枪武器模块，设置伤害、射速和射程，并禁用它。

2. 新建召唤武装，放入武器物品，并引用刚才的枪武器模块。

3. 将初始释放位置设为 3 格，位置模式设为跟随，朝向设为持续对准目标，存在时长设为 5 秒。

4. 用物品 X、Y、Z 旋转校正外观，再用发射口 Z 偏移将起点移到枪口。预览中可以检查物品轴线和发射方向。

5. 用自定义动作抽选触发。如果需要统计命中次数，将命中计数的来源设为引用的枪武器模块。

阵型示例：数量 5、垂直圆环、半径 3、生成间隔 0.15 秒、存在时长 5 秒，可以逐个展开一圈浮游炮。

多组示例：位置固定在世界中，存在时长 10 秒、攻击前摇 0.5 秒，每 2 秒重新激活一次。旧组继续工作，新组在新的释放位置出现。''',
    'summoned_weapon.troubleshooting': '''持续通电只会生成一组；需要信号重新从关闭变为开启，或自定义动作抽选再次激活动作，才会追加一组。断电不会清理已经生成或等待生成的武装。

攻击前摇超过存在时长时，武装来不及攻击就会消失。预览只对操作者显示，约持续 3 秒。消失粒子和声音默认未选择，可按需添加。

每件武装消失时，只结束它自己的攻击或束缚，不影响其他武装或被引用的原模块。''',
    'entity_hitbox.troubleshooting': '''伤害如何传递
受击框使用隐形生命实体接收攻击，再将伤害交给核心，按核心防御设置结算。它没有 AI、推挤、战利品或独立血量。受击框实体不会保存到存档，由已加载的核心按需重建。武器可能先撞到地形，再到达受击框；需要时调整偏移或扩大框。

混合模式的重复命中
命中会在当前游戏刻结束时结算。能够识别来源的弹射物，同时碰到结构本体和实体受击框时，对同一生命体只计算一次；已经等待结算的实体命中可以由本体命中替代。本体免疫这次伤害时，也会消耗该弹射物。

同一攻击者在同一游戏刻内、可识别为同一次近战攻击的重复命中会合并，不同攻击者分别计算。每次原生爆炸只结算一次，采用各接触部位中修正后最高的伤害。标记为纯视觉的法术爆炸不会再次造成伤害。

受伤后的事件只按核心实际损失的生命值触发一次；伤害接收和受伤前事件仍在接触时触发。连续攻击直接命中结构时，仍按攻击模组原有的伤害节奏结算。

无法识别攻击来源、同一攻击使用了不同来源 ID，或同一施法者同时发出不同攻击时，可能仍需单独适配。混合模式仍属实验功能；特定武器异常时可尝试仅实体模式。并非所有武器的选取目标方式和命中后效果都能兼容。

够不到核心时
在创造模式下，手持创造解除工具右键任一已激活的受击框，即可解除对应生命体；副手也可使用。即使受击框覆盖了核心，也能这样解除。普通右键交互不会解除。''',
}

def read(path):
    return json.loads(path.read_text(encoding='utf-8'))

def write(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')

zh = read(assets / 'lang/zh_cn.json')
en = read(assets / 'lang/en_us.json')
polish_path = root / 'scripts/chinese-polish.json'
english_path = root / 'scripts/english-manual-review.json'
polish = read(polish_path)
english = read(english_path)

# Remove the redundant definition paragraph, in both languages and regeneration sources.
intro = 'manual.sablelife.getting_started.usage'
updates['getting_started.usage'] = zh[intro].split('\n\n')[0]
en[intro] = en[intro].split('\n\n')[0]
english[intro] = en[intro]

replacements = {
    'getting_started.troubleshooting': [('锁定设置变灰时，先解除生命化；允许实时操作的控件仍可使用。', '激活期间不可修改的设置会变灰。需要调整时，先解除生命化；支持实时操作的控件仍可使用。')],
    'survival_heart_core.usage': [('休眠时配置', '未激活时配置')],
    'survival_heart_core.settings': [('正常挖掘掉落的核心物品可保留剩余能量。余血保留指解除生命化，不是挖掉核心；普通蓝图会把两者清零，避免复制付费资源。', '正常挖掘掉落的核心物品会保留剩余能量。解除生命化时会保留当前生命值；挖掉核心不会保留生命值。普通蓝图不会复制剩余能量和生命值，以免重复获取已付费的资源。'), ('生存生命解除工具', '生存解除工具')],
    'survival_heart_core.example': [('修改锁定设置前，使用解除按钮或生存生命解除工具。', '需要修改激活期间不可编辑的设置时，先点击解除按钮，或使用生存解除工具。')],
    'sphere_blast.troubleshooting': [('必需标记丢失、禁用或触发结束会取消待爆蓄力。', '必需标记丢失、模块禁用或触发结束时，会停止尚未完成的爆炸蓄力。')],
    'blueprint_spawn.example': [('生成任务提交时冻结世界位置与姿态，原部件之后消失也不会改变它。', '提交生成任务时会记录世界位置与朝向。之后原部件即使消失，任务仍使用这组记录。')],
    'core.iron_spells': [('保留已支持路径的附属可共用接口，独立路径仍需核对。未经核对的铁魔法版本不会注入接口。', '附属模组如果沿用已适配的攻击流程，可以复用兼容逻辑；使用独立攻击流程的技能仍需单独检查。仅对上述已核对的铁魔法版本启用适配。')],
}
for suffix, pairs in replacements.items():
    key = 'manual.sablelife.' + suffix
    value = zh[key]
    for old, new in pairs:
        assert old in value or new in value, (key, old)
        value = value.replace(old, new)
    updates[suffix] = value

for suffix, value in updates.items():
    key = 'manual.sablelife.' + suffix
    assert key in zh, key
    zh[key] = polish[key] = value

# Keep hover help short; full settings and limits remain in the manual chapter.
zh['dashboard.sablelife.summon_help'] = polish['dashboard.sablelife.summon_help'] = '''每次激活生成一组武装，按引用模块的设置攻击。触发关闭或动作结束后，已经生成和等待生成的武装仍会继续；再次激活会追加一组。每件武装按自己的存在时长消失，生命体死亡或解除生命化时统一清理。

用物品旋转和发射口偏移调整外观与起点，预览检查实际物品、绿色模型轴线、橙色发射口和蓝色发射方向。生成、消失的粒子及声音在属性列表末尾设置。完整规则与限制见手册的召唤武装章节。'''

# Verify defaults against VirtualModule: dispenser item, starburst particles, firework launch sound.
key = 'manual.sablelife.summoned_weapon.settings'
en[key] = en[key].replace(
    'the default launch presentation uses dispenser sound, starburst and firework particles.',
    'the default item is a dispenser, with starburst particles and a firework launch sound when it appears.')
english[key] = en[key]

write(assets / 'lang/zh_cn.json', zh)
write(assets / 'lang/en_us.json', en)
write(polish_path, polish)
write(english_path, english)
catalog_path = assets / 'manual/catalog.json'
catalog = read(catalog_path)
catalog['revision'] = max(182, catalog['revision'])
write(catalog_path, catalog)
print(f'Applied {len(updates)} Chinese manual wording updates; synced both introductory paragraphs and help sources.')
