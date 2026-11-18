"""Apply the bilingual 0.1.390 manual review to resource files, without regenerating old content."""
from pathlib import Path
import json

root=Path(__file__).resolve().parents[1]
base=root/'src/main/resources/assets/sablelife'
langs={lang:json.loads((base/f'lang/{lang}.json').read_text(encoding='utf8')) for lang in ('en_us','zh_cn')}
catalog=json.loads((base/'manual/catalog.json').read_text(encoding='utf8'))
changed={lang:{} for lang in langs}

def put(key,en,zh):
    if not key.startswith(('manual.','dashboard.')):key='manual.sablelife.'+key
    for lang,value in zip(langs,(en,zh)):
        langs[lang][key]=value
        changed[lang][key]=value
    return key

def append(key,en,zh):
    if not key.startswith('manual.'):key='manual.sablelife.'+key
    for lang,value in zip(langs,(en,zh)):
        if value not in langs[lang][key]:langs[lang][key]+='\n\n'+value
        changed[lang][key]=langs[lang][key]

def entry(id):return next(e for e in catalog['entries'] if e['id']==id)
def guide(id,title_en,title_zh,sections,module=None):
    e={'id':id,'title':put('chapter.'+id,title_en,title_zh),'sections':{}}
    if module:e['module']=module
    for tab,(en,zh) in sections.items():e['sections'][tab]=[put(id+'.'+tab,en,zh)]
    catalog['entries']=[old for old in catalog['entries'] if old['id']!=id]
    catalog['entries'].append(e)
    return e

put('getting_started.usage',
    "Start with an ordinary Heart Core in Creative mode. It gives an assembled Sable structure health and module-controlled behavior. The steps below build one simple arrow attack.\n\nA Sable structure is the physical build. A creature is that build controlled by a Heart Core. Connected parts may include several rigid bodies joined by bearings or joints.",
    "第一次使用时，先在创造模式下用普通生命核心练习。生命核心让已组装的 Sable 结构拥有生命值，并通过模块控制行为。下面用一个箭矢攻击走完基本流程。\n\nSable 结构指实际搭建的机械；生命体指由生命核心控制的结构。相连部件可以由多个刚体组成，例如通过轴承或关节连接的躯干和四肢。")
steps=[
 ('assemble',"1. Build and assemble","1. 搭建并组装",
  "Place one Heart Core in a small structure. Add a block to use as the firing point, then assemble the build with Sable. Keep only one core across all connected parts. Placing blocks on ordinary terrain alone does not assemble them.",
  "在小型结构中放入一个生命核心，再选一个方块作为发射口。按 Sable 的组装流程把结构组装起来，所有相连部件合计只保留一个核心。只把方块放在地面上，不等于完成组装。"),
 ('dashboard',"2. Add one attack","2. 添加一个攻击模块",
  "Right-click the core with an empty hand. In Modules, click + Add, select Arrow launcher and load it. Select the new module. Keep Straight aiming and Trigger: Always for this first test. Apply edited numbers with the checkmark or Enter.",
  "空手右键核心，进入模块面板。点击“添加”，选择“箭矢发射”并载入，再选中这个模块。第一次测试先用直线瞄准和“持续激活”触发；修改数值后，用勾或 Enter 保存。"),
 ('mark',"3. Mark the firing face","3. 标记发射面",
  "Click Mark area/origin. In the world, right-click the face of the structure block that should fire outward. The mark chooses the firing part and direction; it does not select a player or mob to attack. A successful mark returns to the editor.",
  "点击“标记位置／范围”，然后在世界中右键发射口朝外的方块表面。标记选择的是发射部件和方向，不是要攻击的玩家或生物。标记成功后会返回面板。"),
 ('preview',"4. Check the launch point","4. 预览发射位置",
  "Set Start distance to 1 block as a starting point, then click Preview. Check that the muzzle is outside the structure and the direction points outward. Adjust it if needed. Preview only shows the configured result; it does not fire an arrow or deal damage.",
  "可先把“初始释放位置”设为 1 格，再点击“预览”。确认发射点在结构外，方向朝外；不合适就返回调整。预览只展示位置和方向，不会发射箭矢或造成伤害。"),
 ('activate',"5. Activate and test","5. 激活并测试",
  "Close the menu. Sneak-right-click the core with an empty hand to activate it. Keep the muzzle clear and test against an eligible mob in front of it. Always now supplies the trigger. For later target-aimed modules, check target type, range and visibility separately.",
  "关闭界面，潜行时空手右键核心，激活生命体。让发射口前方保持净空，用前方符合目标筛选的生物测试；此时“持续激活”会提供触发。以后改用目标瞄准时，还要分别检查目标类型、搜索范围和视线。"),
 ('release',"6. Release before changing locked settings","6. 解除后修改锁定设置",
  "Use Release in the dashboard, or right-click the creature with the Creative Release Tool. This returns it to dormant editing and keeps the structure and saved settings. It stops runtime actions; it is not a pause button. Check the mark and activate again after editing.",
  "点击面板中的“解除生命化”，或用创造模式生命解除工具右键生命体，使其回到休眠编辑状态。结构和已保存设置会保留，运行中的动作会停止；这不是暂停后续播。修改后检查标记，再重新激活。")]
step_keys=[];figures={}
for id,en_title,zh_title,en,zh in steps:
    key=put('getting_started.step_'+id,en_title+'\n\n'+en,zh_title+'\n\n'+zh)
    step_keys.append(key);figures[key]=id
append('getting_started.step_assemble',
    'The dispenser icon is only an example of a firing block. Any valid structure block can be marked; an actual dispenser is not required.',
    '图中的发射器图标只是示意，可标记任意有效的结构方块，不需要真实发射器。')
entry('getting_started')['sections']['usage']=['manual.sablelife.getting_started.usage']+step_keys
entry('getting_started')['sections']['example']=step_keys
entry('getting_started')['figures']=figures
put('getting_started.settings',
    "Ordinary Heart Core: Creative editing by default. An administrator can allow Survival editing with allowEditHeartCoreUnderSurvivalMode; this also exposes powerful reward and terrain settings.\n\nSurvival Heart Core: editable in Survival and uses Energy. Read its separate chapter before activation. Redstone Core: runs supported controls on an assembled structure without life activation.\n\nEmpty-hand right-click opens the core. Sneak-right-click activates a Heart Core; repeating that gesture does not deactivate it. Use Release to end activation.",
    "普通生命核心：默认在创造模式编辑。管理员可通过 allowEditHeartCoreUnderSurvivalMode 开放生存编辑，但这也会开放奖励和地形等强力设置。\n\n生存生命核心：可在生存模式编辑，使用能量，激活前请阅读其独立章节。红石核心：在已组装结构上运行支持的控制功能，无需生命化。\n\n空手右键打开核心，潜行时空手右键激活生命核心。再次这样点击不会解除生命化，结束激活请使用“解除生命化”。")
entry('getting_started')['sections']['settings']=['manual.sablelife.getting_started.settings']
put('getting_started.troubleshooting',
    "If a locked setting is gray, use Release first. Controls intended for live use remain available.\n\nCheck assembly, a single connected core, a valid mark, module enable state, Trigger and Wireless Master. A valid mark alone does not start an attack.\n\nHold nothing when opening the core: a held block may camouflage it instead. Release does not restore blocks already removed or produce death loot.",
    "锁定设置变灰时，先解除生命化；允许实时操作的控件仍可使用。\n\n依次检查：结构已组装、相连部件只有一个核心、标记有效、模块已启用、触发成立、无线总开关已开启。标记有效本身不会启动攻击。\n\n打开核心时请空手；手持方块可能改为伪装核心。解除生命化不会恢复已移除的方块，也不会触发死亡奖励。")
for key,en,zh in [
 ('schematic','Diagram · not clickable','操作示意 · 非操作按钮'),('one_core','One core','一个核心'),('assembled','Assembled','已组装'),('connected','Connected parts','相连部件'),
 ('empty_hand','Empty hand','空手'),('add','+ Add','＋添加'),('arrow','Arrow launcher','箭矢发射'),('always','Trigger: Always','触发：持续激活'),('mark','Mark','标记'),('face','Face','方块表面'),('not_target','Mark the firing block, not a mob','标记发射方块，不是生物'),
 ('start','Start distance','初始释放位置'),('no_damage','Preview: no attack','预览不会攻击'),('shift','Sneak','潜行'),('plus','+','+'),('empty_click','Empty-hand right-click','空手右键'),('active','Active','已激活'),('release','Release','解除生命化'),('or','or','或'),('dormant','Dormant','休眠')]:put('figure.'+key,en,zh)
put('simple_start.settings',
    "A module needs to be enabled, allowed by the server and triggered. If it needs a mark, that marked part must remain connected. Core-wide modules and supported unmarked signal outputs are exceptions.\n\nHeart Core modules normally need life activation. A Redstone Core runs its supported controls and visual modules on an assembled structure without health or life activation. Survival Heart Cores additionally require Energy for paid actions.",
    "模块需要已启用、服务端允许且满足触发；需要标记的模块，还要求标记部件保持连接。作用于核心的模块和支持无标记输出的信号功能是例外。\n\n生命核心的模块通常需要生命体先激活。红石核心在已组装结构上运行支持的控制和视觉模块，没有生命值，也无需生命化。生存生命核心的收费动作还需要足够能量。")
for lang in langs:
    k='manual.sablelife.module_management'
    langs[lang][k]=langs[lang][k].replace('both core types','all three core types').replace('两种核心','三种核心');changed[lang][k]=langs[lang][k]
append('module_management.troubleshooting',
    "Survival Heart Core module switches are under [modules.survivalHeartCore]. Server switches can disable supported features; they cannot add capabilities that a core type does not support.",
    "生存生命核心的模块开关位于 [modules.survivalHeartCore]。服务端开关可以停用受支持的功能，但不能让某类核心获得原本不支持的能力。")
put('marking_system.usage',
    "A mark selects a block, face or area of the Sable structure. A mouth mark can supply a Flamethrower's origin; two corners can define a Melee volume. It does not mark a player or mob as an attack target.\n\nOrdinary marks follow their part's position and rotation. Arena uses a fixed world center. Blueprint generation can use a fixed world point or follow a marked part. Block Interaction marks the reference part, then searches its configured world or structure area. Marking does not move blocks.",
    "标记选择 Sable 结构上的方块、表面或范围。例如嘴部标记可作为喷火原点，两个角点可确定近战范围。它不是给玩家或生物指定攻击目标。\n\n普通标记跟随所属部件移动和旋转。场地边界使用固定世界中心；蓝图生成可选固定世界点或跟随部件；方块交互先标记参考部件，再搜索所设的世界或结构区域。标记本身不会移动方块。")
append('preview_system.settings',
    "Mark and volume previews use continuous outlines in their own colors. Entity hitbox preview is white. Blueprint and natural-spawn previews can also show block models; large templates may show only bounds because of the projection limits. A visible projection is not proof that placement is allowed.",
    "标记和范围预览使用各自颜色的连续边框，实体受击框为白色。蓝图及自然生成预览还可显示方块模型；大型模板超过投影限制时可能只有边框。看得到投影，不代表实际放置条件已经通过。")
put('survival_core.usage',
    "The Redstone Core is a survival-craftable control core with a purple crystal. It controls an assembled Sable structure without giving it life or health. It is a different item from the Energy-powered Survival Heart Core.",
    "红石核心是可在生存模式合成的紫色控制核心。它在已组装的 Sable 结构上运行控制功能，不赋予结构生命值。它与需要能量的“生存生命核心”是两种物品。")
put('survival_core.settings',
    "Supported modules are Move set, Signal logic, Pulse sequence, Wireless redstone, Target sensor, Target alignment, Auto facing, Signal turning, Gyroscope, Hover anchor, Linear movement, Visual Effects, Distortion trail, Particle effects, Item display, Trigger sound and Block Interaction. They are enabled by default; administrators can disable them in [modules.redstoneCore]. Sound, BGM and combat/life modules remain unavailable.\n\nAssemble the structure with exactly one core across all connected parts. Wireless Master controls output. Supported triggers are Always, marked-point power, internal channel and external receive. Compatible actions can also use Move set.\n\nMove set always ignores target, distance, cone, visibility and health conditions. Priority, Weight, Move cooldown, Move time, Rest and Consecutive repeats still apply.\n\nLinear movement is restricted to collision-checked Travel, with Force movement unavailable. Gravity and step assist keep their own Travel settings. Old unsupported settings remain stored but cannot run on this core.",
    "支持：自定义动作抽选、信号逻辑、脉冲序列、无线红石、目标感应、目标对准、自动朝向、信号转向、陀螺仪、悬停锚点、直线移动、视觉特效、扭曲拖尾、粒子特效、物品显示、触发音效和方块交互。这些功能默认开放，管理员可在 [modules.redstoneCore] 中停用。普通声音、背景音乐及战斗／生命模块仍不支持。\n\n先组装结构，所有相连部件合计只保留一个核心。无线总开关控制输出。支持持续激活、标记点供电、内部频道和外部接收；兼容动作也可由自定义动作抽选触发。\n\n动作抽选始终忽略目标、距离、角度、视线及生命值条件，优先级、权重、冷却、招式时长、招后停顿和连续重复仍然生效。\n\n直线移动只支持带碰撞检查的连续移动，不支持强制位移；重力及跨阶助力仍使用连续移动各自的设置。旧存档中的不支持设置会保留，但不会在此核心上运行。")

guide('survival_heart_core','Survival Heart Core','生存生命核心',{
 'usage':("The Survival Heart Core gives a Sable structure health and modules in Survival mode. Energy pays for health, healing, attacks and certain control abilities. It is separate from the ordinary Heart Core and Redstone Core.\n\nEmpty-hand right-click opens it. Configure while dormant, refuel it, complete marks, then sneak-right-click with an empty hand to activate. Current Life shows the stored health before activation and the current health while active.",
          "生存生命核心让 Sable 结构在生存模式拥有生命值和模块。生命值、回血、攻击及部分控制技能使用能量计费；它不同于普通生命核心和红石核心。\n\n空手右键打开，休眠时配置并补充能量、完成标记，再潜行时空手右键激活。“当前生命值”在休眠时显示保留血量，激活后显示实时血量。"),
 'settings':("Refuel shows the server's accepted materials and Energy value per item. Hover an item to read its value, place real items in the input slots, then confirm twice. Confirmation consumes those items. Closing before confirmation returns unspent inputs. A normally mined core item retains its remaining Energy. Remaining life is preserved by Release, not by mining the core; ordinary blueprints reset both to zero to prevent copying paid resources. Each server supplies its own prices.\n\nActivation tries to restore missing health and pays only for that difference. If Energy is insufficient but some life remains, activation can use that remaining life instead. Zero life requires enough Energy to restore it. Healing charges for health actually restored and is limited by the available Energy.\n\nThe golden estimate and ? beside each module show the current server's formula. Melee charges per successful hit; most attacks pay per release, and repeated releases pay again. Armor reduction is capped at 90% and is billed per active second, even without incoming hits. The default is 1 Energy per percentage point per second: 90% costs 90/s. A spawned weapon can also pay for its referenced attack.\n\nLoot, death XP, command, Arena and Blueprint generation are unavailable. Potion-effect attributes are removed; native burning and visual effects are separate. Adaptive defense, collision terrain breaking, void-height death, force chunk loading and detached-part cleanup are unavailable. Other-Life damage is always accepted, Survival interaction is always allowed, and permitted player mining drops blocks. Settings that are fixed are hidden.\n\nHealth reaching zero plays the life-depleted effect and automatically releases the structure; it does not run the ordinary death-delay/removal/reward process. Release keeps remaining life and Energy. An active ordinary Heart Core cannot be released with the Survival Release Tool.",
              "“补充能量”页面列出服务器接受的材料，悬停可查看每件价值。把实际物品放入输入格，连续确认两次后才会消耗材料；未确认就关闭，会退回未消耗物品。正常挖掘掉落的核心物品可保留剩余能量。余血保留指解除生命化，不是挖掉核心；普通蓝图会把两者清零，避免复制付费资源。材料价值和费用由当前服务器决定。\n\n激活时会尝试补满，只为缺少的生命值付费。能量不足但仍有余血时，可按余血激活；生命为 0 时，需要足够能量恢复。回血只为实际恢复的数值收费，并受剩余能量限制。\n\n模块旁的金色预计消耗及问号显示当前服务器的公式。近战按成功命中计费；多数攻击按每次释放计费，重复释放会再次收费。部位减伤最高 90%，按激活秒数收费，未受击也收费；默认每减伤 1% 每秒 1 能量，90% 即 90／秒。召唤武装还可能为引用的攻击另行付费。\n\n不提供战利品、死亡经验、命令、场地边界和蓝图生成。药水效果属性移除，普通点燃和视觉特效是独立功能。自适应防御、碰撞破坏地形、虚空高度死亡、强制区块加载及断肢清理不可用。受到其他生命体的伤害始终开启，生存交互始终允许，允许的玩家挖掘会掉落方块；固定属性在界面隐藏。\n\n生命清空后播放生命耗尽特效，并自动解除生命化，不进入普通核心的死亡延迟、移除及奖励流程。解除会保留余血和能量；生存生命解除工具不能解除普通生命核心。"),
 'example':("1. Craft the Redstone Core recipe with redstone dust in the center instead of the amethyst shard. Assemble it in a small Sable structure.\n\n2. Open Refuel, inspect the listed values and insert accepted materials. Confirm twice.\n\n3. Set maximum health to 100. At the default 1 Energy/HP, a fresh 0/100 core needs 100 Energy for full health.\n\n4. Add one attack, mark it and check its estimate. Activate, test one release, then inspect Energy and Current Life.\n\n5. Use Release or the Survival Release Tool before editing locked settings. Taking damage, releasing and activating again does not grant free full health.",
            "1. 使用红石核心的配方，把中间紫水晶碎片换为红石粉。在小型 Sable 结构中组装这个核心。\n\n2. 打开补充能量，查看材料价值，放入接受的材料并连续确认两次。\n\n3. 最大生命设为 100。默认每点生命 1 能量，全新 0／100 的核心补满需要 100 能量。\n\n4. 添加一个攻击，完成标记并查看预计消耗。激活后测试一次，再检查能量和当前生命。\n\n5. 修改锁定设置前，使用解除按钮或生存生命解除工具。受伤后解除再激活，不会免费补满血。"),
 'troubleshooting':("Insufficient Energy can prevent a paid release or healing; an enabled trigger is not proof of payment. Estimates depend on the current settings and server rates, not only the Damage field. Blast radius, speed, range, duration, projectile count and repeated ground-strike points can change the price.\n\nPlayer names, owners and factions control target selection; they do not grant editor permissions. This core is editable by Survival players who can build and interact there. Server protections can still reject interaction.\n\nCheck [modules.survivalHeartCore] for skill switches and [survivalTokens] for material values and rates. Internal config/data keys may retain Token names; the displayed resource is Energy. Do not use the ordinary-core Survival-editing switch as a requirement for this core.",
                    "能量不足可能阻止收费技能释放或回血，触发已开启不代表付款成功。预计费用按当前参数和服务端倍率计算，不只看伤害：爆炸半径、速度、射程、时长、弹体数量和地面打击落点数等都可能改变价格。\n\n玩家名单、主人和阵营用于选敌，不授予编辑权限。允许在当地建造和交互的生存玩家可编辑此核心，服务器保护仍可能拒绝操作。\n\n模块开关在 [modules.survivalHeartCore]，材料与费率在 [survivalTokens]。内部配置和数据仍可能使用 Token 命名，界面资源名称为“能量”。此核心不需要开启普通生命核心的生存编辑总开关。")})

guide('natural_spawning','Natural spawning','自然生成',{
 'usage':("The Wild Encounter Tool lets an operator choose server blueprints and spawn them around nearby players. Rules are saved on the server, including tracked objects across restarts. Creative players can trigger automatic searches; Spectators cannot.\n\nDensity controls search frequency and distance. There is no separate percentage chance: a valid candidate can spawn when cooldowns and population limits allow it. Random positions and failed checks affect how often you actually see a spawn.",
          "管理员可用野外遭遇工具，选择服务器蓝图并在玩家附近自然生成。规则和已跟踪对象保存在服务端，重启后继续读取。创造模式玩家也会触发自动搜点，旁观模式不会。\n\n密度控制搜点频率和距离，目前没有独立的百分比刷新概率。冷却结束、数量未满且候选位置有效时，就可生成；随机位置及检查失败会影响实际刷出频率。"),
 'settings':("Add a rule, choose a server blueprint and Load it. Activate core: Yes requires exactly one ordinary Heart Core and no Redstone Core. No permits a pure structure. Choose Conditions, then Enable the rule. Enabled and Yes use green buttons.\n\nDensity has a cooldown after each successful spawn, a retry delay after failed searches, candidate positions per search, and minimum/maximum distance. The server performs at most one automatic search round per second and stops a round after a successful spawn. All rules share this scheduling; increasing candidates does not bypass cooldown or caps.\n\nMax present for this blueprint limits tracked objects from that rule. Global defaults are 64 across all rules and dimensions, and 4 within 128 blocks; administrators can change these in config. These limits are separate and do not multiply. Rule lists use Current to show tracked counts.\n\nLifetime requests removal after the configured time while the server is ticking; 0 disables this limit. The timer can expire while the object is unloaded; actual removal waits for the required data and chunks. No-player cleanup starts after no living non-Spectator player is within 128 blocks of the object's recorded/current position; returning resets the absence timer. It does not depend on chunk unloading, so a creature's own chunk loader does not prevent absence cleanup. Either expiry can request removal. Pure structures follow the same tracking rules.\n\nRelease after spawning detaches new spawns from the rule: no tracked count, lifetime or absence cleanup for them. Lifetime 0 alone does not release them. Released objects cannot be removed by that rule's Clear all current button.\n\nGround clearance raises the full blueprint above its terrain reference. Require open sky rejects covered placement; turning it Off removes that check, not a command to spawn in caves. All footprint chunks and final volumes must still pass applicable checks. Water below a flying build is allowed if it does not enter the checked volume.\n\nSkip full blueprint bounds check tests the blueprint's occupied block volumes instead of requiring its entire enclosing box to be empty. It still checks loaded terrain, world limits, fluids and other structures. Clear obstacles can remove blocks in the blueprint bounds and can cut terrain or buildings; protected ownership, block entities, unbreakable blocks and removal limits can reject the candidate. It is not general base protection.\n\nChoose allowed biomes in the two panels; empty means any. Overworld includes all non-ocean Overworld biomes, including correctly tagged modded biomes. Add an ocean biome explicitly if wanted. Choose a faction per blueprint; Wilds is the hidden default, and Wilds creatures do not automatically attack each other. Spawn announcement broadcasts the entered text to all online players after successful spawning, including Test spawn and released objects; empty disables it. The Coords checkbox beside Spawn announcement appends the actual placement coordinates (blueprint bottom center). It defaults Off, is disabled for blank text, and clears when the announcement is emptied.",
              "添加规则，选择服务器蓝图并载入。“是否激活核心：Yes”要求蓝图有且只有一个普通生命核心，且没有红石核心；No 允许生成纯结构。设置条件后，在外层点击启用。已启用及 Yes 使用绿色按钮。\n\n密度包含每只成功刷新后的冷却、搜点失败后的重试间隔、每轮候选位置数和最小／最大距离。全服每秒最多进行一轮自动搜点，一轮成功生成后停止；所有规则共用这个调度。增加候选数不会绕过冷却或数量上限。\n\n“此蓝图同时在场上限”限制本规则跟踪的数量。全服默认所有规则、所有维度合计 64 个，128 格内默认 4 个，管理员可通过 config 修改。这些限制分别生效，不会相乘。列表的 Current 显示当前跟踪数量。\n\n寿命从生成后按服务器运行时间倒数，0 表示不设寿命限制。对象未加载时计时仍可到期，实际移除会等待所需数据与区块。“附近无人后清理”在对象的当前／最近记录位置 128 格内没有存活的非旁观玩家时开始累计，玩家返回会重置离开计时。它不以区块卸载为前提，因此生命体自带区块加载也不能阻止无人清理。任一到期都可请求移除，纯结构也遵守同样的跟踪规则。\n\n“生成后放生”让新对象脱离规则：不计入跟踪数量，也不再受这条规则的寿命和无人清理管理。寿命设为 0 不等于放生；放生后不能用该规则的“清除当前所有”移除。\n\n离地间距用于把整个蓝图抬到地形参考面上方。“需要露天”会拒绝有遮挡的生成位置；关闭只是不检查露天，不等于指定在洞穴生成。蓝图覆盖区块和最终体积仍须通过相应检查。高空结构下方可以有水，只要液体没有进入检查体积。\n\n“跳过完整蓝图边界检查”改为检查实际方块体积，不再要求整个外接盒都空闲；仍检查已加载区域、世界限制、液体及其他结构。“生成前清除障碍”可清除蓝图边界内的方块，可能削地形或建筑，并非据点保护。已有归属、方块实体、不可破坏方块和清除预算等仍可拒绝该位置。\n\n在双面板选择允许群系，空列表表示不限。“Overworld”包含主世界非海洋群系及正确标记的模组群系；需要海洋时可另行加入。每个蓝图可选阵营，隐藏的默认 Wilds 之间不自动互相攻击。出生公告在成功生成后向全服在线玩家发送，试生成和放生也包括在内，留空关闭。 出生公告右侧的“坐标”勾选可在公告末尾附上实际生成坐标（蓝图底部中心）。默认关闭；文字为空时禁用，清空文字也会取消勾选。"),
 'example':("1. Add a rule, select a small server blueprint and Load. Choose whether to activate its core.\n\n2. Set ground clearance, allowed biomes, faction and a low population limit. Set Lifetime and No-player cleanup, or deliberately choose Release after spawning.\n\n3. Use Preview ahead to inspect bounds and projection. Reopen the tool to return to that rule. A blocked preview may still be visible and will show a reason.\n\n4. Use Test spawn for actual placement, then check Current in the list. Enable the rule to test automatic spawning around a non-Spectator player.\n\n5. Use Clear all current to remove this rule's tracked objects. The red X deletes the rule after a second confirmation; it is separate from clearing spawned objects.",
            "1. 添加规则，选择一个小型服务器蓝图并载入，决定是否激活核心。\n\n2. 设置离地间距、群系、阵营及较低的同时在场上限。设置寿命和无人清理，或明确选择生成后放生。\n\n3. 点击前方预览检查边框和投影，重新打开工具会回到该规则。即使位置不允许生成，预览仍可能显示，并给出阻止原因。\n\n4. 用试生成验证实际放置，再查看列表中的当前数量。启用规则后，用非旁观玩家测试自然生成。\n\n5. “清除当前所有”移除该规则跟踪的对象；红色 X 再次确认后删除规则，两者不是同一操作。"),
 'troubleshooting':("If nothing spawns, read the Test spawn failure reason. Check master config, rule enable state, cooldown, all population caps, loaded footprint, biome, sky, water/obstacles and build height. Large blueprints can fail where small ones fit. Raising ground clearance does not fix an unloaded footprint or every other failure.\n\nAn empty allowed-biome list permits every biome; the Overworld group excludes ocean types. More candidates improve search opportunities, not guaranteed success. The limits count pure structures too, and include tracked unloaded objects.\n\nSet enableWildEncounters=false in the server config to stop automatic spawning, Test spawn and managed expiry cleanup while preserving saved rules. Already placed structures are not deleted and active creatures can keep running. Large native blueprint placement can pause the server; a configurable cap is not a performance guarantee.",
                    "不刷新时，先看试生成给出的失败原因，再检查总 config、规则是否启用、冷却、各层数量限制、整片覆盖区块是否加载、群系、露天、液体／障碍和建造高度。小蓝图能放的位置，大蓝图不一定能放；抬高离地间距不能解决未加载范围或所有其他失败。\n\n群系列表为空表示任何群系，Overworld 组合则排除海洋。增加候选数只增加搜点机会，不保证成功。数量限制也计算纯结构及仍被跟踪的未加载对象。\n\n在服务端 config 把 enableWildEncounters 设为 false，会停用自然生成、试生成及受管理对象的到期清理，并保留规则。已生成结构不会因此删除，激活的生命体仍可继续运行。大型原生蓝图放置可能造成停顿，可调上限不代表性能保证。")})

guide('block_interaction','Block Interaction','方块交互',{
 'usage':("Simulates an empty-hand right-click on the nearest supported block in a small area. Mark the reference part, then choose World interaction or Structure interaction. It does not attack entities or open inventory screens.",
          "在小范围内，对最近的受支持方块模拟一次空手右键。先标记参考部件，再选择世界交互或结构交互；它不会攻击实体，也不会打开库存界面。"),
 'settings':("Size defaults to 1 and accepts 1–3: a 1×1×1 to 3×3×3 search cube. Only the nearest supported block to the reference point is used each time, not every block in that cube.\n\nStart distance moves the point along the marked face. X/Y/Z offsets use the marked part's local axes. In World mode, the point follows the moving part but searches ordinary world blocks. Structure mode searches only the marked rigid body's own blocks.\n\nOnce runs on a new trigger or Move set activation. Repeat uses the configured interval while triggered. Supported targets are vanilla wooden doors/trapdoors, fence gates, buttons and levers. Iron doors/trapdoors, containers, crafting tables and other mods' blocks are ignored. Preview outlines the search area and highlights the currently chosen block in white.",
              "大小默认 1，可设为 1–3，对应 1×1×1 至 3×3×3 的搜索立方体。每次只操作离参考点最近的受支持方块，不会把范围内所有方块都点一遍。\n\n初始释放位置沿标记面移动参考点，X/Y/Z 偏移按标记部件的局部坐标轴计算。世界模式让参考点跟随部件移动，但只搜索普通世界方块；结构模式只搜索标记所在刚体自己的方块。\n\n单次模式在新触发或新的动作抽选激活时执行；重复模式在触发持续期间按间隔执行。支持原版木门／活板门、栅栏门、按钮和拉杆；铁门／铁活板门、容器、工作台和其他模组方块会忽略。预览显示搜索范围，并用白框标出当前选中的方块。"),
 'example':("1. Mark a block on a moving hand. Choose World interaction, size 1 and Once.\n\n2. Set Start distance and offsets so Preview's cube covers a world lever.\n\n3. Trigger once to toggle it. For an onboard lever instead, select Structure interaction and place the search cube over a lever on the same rigid body.\n\n4. Use Repeat only when repeated clicks are intended: a lever or door can toggle back again.",
            "1. 标记机械手上的方块，选择世界交互、大小 1 和单次执行。\n\n2. 调整初始释放位置及偏移，让预览框覆盖世界中的拉杆。\n\n3. 触发一次切换拉杆。要操作机身拉杆时，改为结构交互，并把搜索框对准同一刚体上的拉杆。\n\n4. 确实需要反复点击时才用重复模式，否则拉杆或门可能又被切换回去。"),
 'troubleshooting':("The marked block is a reference, not necessarily the block that gets clicked. The search cube must cover a supported target in the selected space. A target on another connected rigid body is not part of Structure mode's search.\n\nServer interaction protection or a canceled interaction event can reject the click. This module uses its own simulated player; it does not impersonate the named Owner or bypass claims.",
                    "标记方块是定位基准，不一定就是最终点击的方块。搜索框必须覆盖所选空间中的受支持目标；即使关节相连，其他刚体上的方块也不属于结构模式的搜索范围。\n\n服务器交互保护或被取消的交互事件可能阻止点击。模块使用专用模拟玩家，不冒充 Owner，也不绕过领地保护。")},module='block_interaction')

# Keep detailed compatibility coverage, but move it out of the basic core setup page.
compat_keys=['manual.sablelife.core.'+suffix for suffix in ('iron_spells','iron_areas','iron_physical_followup')]
guide('combat_compatibility','Combat compatibility','战斗模组兼容',{
 'usage':("These notes describe explicitly supported native damage paths. They do not promise every spell, weapon or entity-only event will recognize a Sable body. Entity hitbox is a separate optional module; the adapters below do not require it.",
          "这里说明已经明确适配的原生伤害路径，不代表所有法术、武器或仅面向实体的事件都能识别 Sable 身体。实体受击框是独立可选模块，下列接口不要求先添加受击框。"),
 'settings':("Read the version and attack-family notes below. Part damage rules and core defenses still apply. Cosmetic explosions must not be counted again as independent damage.",
              "请按下方版本和攻击家族说明使用，部位伤害规则及核心防御仍参与结算。仅用于视觉的爆炸不能再当成独立伤害重复计算。"),
 'example':("Test a single supported attack against a small creature first. Check actual HP loss, projectile removal and any native lifesteal or skill charge. Then test the same weapon with Entity-only or Hybrid mode if using receivers.",
            "先对小型生命体测试一个已支持的攻击，检查实际掉血、弹体消失及原生吸血／技能充能。需要实体受击框时，再分别测试仅实体和混合模式。"),
 'troubleshooting':("A hit may arrive through structure contact, an entity receiver or a mod-specific adapter. Hybrid deduplication needs a recognizable shared source. Independently generated sources or unverified mod versions may need explicit compatibility work. See Entity hitbox for its routing limits.",
                    "命中可能来自结构接触、实体受击框或专用适配。混合去重依赖可识别的共同来源；来源不同的多段攻击及未核对版本可能需要专门适配。实体入口的限制见实体受击框章节。")})
entry('combat_compatibility')['sections']['settings']+=compat_keys
entry('core')['sections']['usage']=[k for k in entry('core')['sections']['usage'] if k not in compat_keys]
put('core.protected_tools',
    "Player mining has three modes: Prohibited, Always allowed, and Only after death. The mining-drops switch controls allowed player-mined block items separately. Explosion destruction is a separate rule; enabling mining drops does not make explosions drop blocks.\n\nAn active ordinary Heart Core itself remains protected from Survival mining even if its body is mineable. Detached parts keep the creature's applicable protection until release or cleanup. Default detached-part cleanup is 3 seconds; it is configured separately from limb loot.\n\nWhen mining is prohibited, supported tool hits damage the active creature instead of mining its blocks. Ordinary terrain keeps its normal mining behavior.",
    "玩家挖掘有三种模式：禁止、全时段允许、仅死亡后允许。“玩家挖掘时掉落方块”单独控制允许挖掘时的方块物品；爆炸破坏是另一项规则，开启挖掘掉落不会让爆炸也掉方块。\n\n普通生命核心激活时，核心本身仍禁止生存挖掘，即使身体允许挖掘。断肢在解除或清理前保留相应保护；默认断肢清理为 3 秒，与部位战利品分别设置。\n\n禁止挖掘时，受支持的工具命中会伤害已激活的生命体，而不是挖掉身体方块。普通地形沿用正常挖掘规则。")
put('core.epic_fight',
    "With Epic Fight 21.17.3.1, supported native main-hand hits charge the matching weapon innate skill by actual body health damage. Native caps, activation and synchronization apply; one phase cannot charge twice. Immunity gives no charge. This does not extend support to every entity-only skill event.",
    "Epic Fight 21.17.3.1 中，已适配的原生主手命中会按身体实际掉血量为对应武器特殊技能充能，沿用原生上限、激活与同步规则。同一阶段不重复充能，免伤不充能；这不代表所有仅面向实体的技能事件均已适配。")
entry('combat_compatibility')['sections']['settings'].append('manual.sablelife.core.epic_fight')

# Correct facts introduced by newer modules without replacing unrelated detailed instructions.
put('entity_hitbox.settings',
    "Mark a block on the intended part. Width, Height and Depth are 0.25–16 blocks; local X/Y/Z offsets are -16 to 16. The server permits the first 8 valid, triggered receivers by default. maximumEntityHitboxesPerCreature can change this to 1–32.\n\nNew modules default to Hybrid: entity + structure. Entity only disables direct structure damage while valid receivers exist. If any active receiver uses Hybrid, both damage paths are enabled for the whole creature. Both share core health and the marked part's defenses. Existing mode choices are preserved.\n\nAlways keeps receivers active during the creature's life, subject to valid marks, module/server switches, triggers and Master. When none remain, normal structure-only damage returns. Death, Release and unloading remove the receivers; loaded eligible cores recreate them as needed.\n\nThe position and local offset follow the part's rotation, but each actual hitbox stays aligned with world axes and does not tilt. Use several small boxes for long moving limbs.\n\nAttract hostile mobs lets nearby hostile mobs and mobs with retaliation goals acquire a receiver before taking damage. Existing targets take priority. Passive animals without attack goals remain passive. Retaliation after being damaged is separate and does not require this switch.",
    "在所需部位标记一个方块。宽、高、深各为 0.25–16 格，局部 X/Y/Z 偏移为 -16 至 16 格。服务器默认允许前 8 个有效且满足触发的受击框，可通过 maximumEntityHitboxesPerCreature 调为 1–32 个。\n\n新模块默认混合：实体＋结构。仅实体模式在有有效框时关闭结构直接受伤；任一激活框使用混合模式，就为整个生命体开放两路伤害。两路共用核心血量和标记部位的防御，旧存档的入口选择保留。\n\n“持续激活”可让框在生命体存活期间常驻，但仍受标记、模块及服务端开关、触发和总开关限制。所有框停止后恢复仅结构受伤。死亡、解除和卸载会移除框，满足条件的已加载核心再按需重建。\n\n位置及局部偏移跟随部件旋转，但实际框始终与世界轴对齐，不会倾斜。长肢体可用多个小框近似。\n\n“吸引敌对生物”让附近敌对生物或带反击目标 AI 的生物在未受伤前也能选择框，已有目标优先。没有攻击 AI 的鸡牛等仍保持被动。被打伤后反击是独立功能，不要求开启这个开关。")
append('laser.settings',
    "Beam visual style chooses Continuous beam or Legacy particles. Continuous beam is the default and follows the moving emitter; Beam color/RGB colors it. Legacy keeps the particle-style presentation. Built-in beam visuals are always enabled; an empty extra-particle selection does not disable the built-in beam.\n\nHorizontal and Vertical sweep angles each accept -180 to 180 degrees; 0 keeps that axis fixed. Sweep changes direction during firing without moving the muzzle. These visuals and sweep settings do not replace the beam's damage width or hit interval.",
    "光束视觉样式可选连续光束或旧版粒子。连续光束为默认样式，会跟随移动的发射部件，颜色／RGB 可调色；旧版保留粒子式表现。内置光束始终开启，清空附加粒子不会关闭内置光束。\n\n水平与垂直扫射角分别可设为 -180 至 180 度，0 表示该轴固定。扫射在发射期间改变方向，不移动发射口；视觉和扫射设置不替代伤害宽度或命中间隔。")
append('arrow.settings',
    "For a custom item projectile, Projectile always faces camera keeps the displayed item facing the viewer, useful for snowballs and other flat icons. It changes only the visual orientation, not gravity, the flight path or the damage hitbox.",
    "自定义物品弹体可勾选“弹射物始终面向镜头”，让雪球等平面图标朝向观看者。它只改变外观朝向，不改变重力、轨迹或伤害判定。")
for module in ('arrow','gun','mortar'):
    append(module+'.settings',
        "Custom item rotation X/Y/Z accepts -180 to 180 degrees and defaults to 0. These are local model offsets relative to flight: custom gun bullets and mortar shells follow their velocity, including the mortar arc. Arrow camera-facing mode applies offsets relative to the viewer instead. Rotation changes appearance only; it does not change launch direction, trajectory, collision or Energy cost.",
        "自定义物品旋转 X/Y/Z 可设为 -180 至 180 度，默认均为 0，按飞行姿态的局部坐标轴调整模型。自定义枪弹和迫击炮弹会跟随速度方向，迫击炮弹会随抛物线转向；箭矢面向镜头时则相对观看者旋转。旋转仅改变外观，不改变发射方向、弹道、碰撞或 Energy 消耗。")
for module in ('arrow','gun','slash','sphere_blast','pull','freeze','pulse'):
    k='manual.sablelife.'+module+'.settings'
    value=langs['zh_cn'][k].replace('选择最近的有效目标','按核心目标优先级选择一个有效目标').replace('选择最近目标','按核心目标优先级选择一个目标').replace('读取最近有效目标的位置','读取按核心目标优先级选出的有效目标位置')
    langs['zh_cn'][k]=value;changed['zh_cn'][k]=value
for module,tab in [('linear_movement','settings'),('auto_facing','settings'),('grab','example'),('grab','troubleshooting')]:
    k=f'manual.sablelife.{module}.{tab}';value=langs['zh_cn'][k]
    value=value.replace('选择搜索范围内最近的有效目标','按核心目标优先级选择搜索范围内的有效目标').replace('仅选最近无遮挡目标','只选符合筛选条件且无遮挡的目标').replace('捕获最近的有效对象','按核心目标优先级捕获一个有效对象').replace('每次抓最近一名有效对象','每次按核心目标优先级抓取一名有效对象')
    langs['zh_cn'][k]=value;changed['zh_cn'][k]=value
for module in ('item_display','distortion_trail'):
    k=f'manual.sablelife.{module}.settings';value=langs['en_us'][k]
    value=value.replace('it is disabled there by default','it is enabled there by default').replace('where it is disabled by default','where it is enabled by default')
    langs['en_us'][k]=value;changed['en_us'][k]=value
put('combat_event.usage_note',
    "This chapter covers attributed Kills, Blocked attacks and Own death. Own death is for the ordinary Heart Core's death process; a Survival Heart Core instead releases when its life is depleted.",
    "本章包含归属于自身的击杀、攻击被格挡及自身死亡。自身死亡使用普通生命核心的死亡流程；生存生命核心则在生命耗尽后直接解除生命化。")
if 'manual.sablelife.combat_event.usage_note' not in entry('combat_event')['sections']['usage']:entry('combat_event')['sections']['usage'].append('manual.sablelife.combat_event.usage_note')
k='manual.sablelife.combat_event.settings';langs['zh_cn'][k]=langs['zh_cn'][k].replace('当前只保留击杀和攻击被格挡两类事件。','击杀和攻击被格挡按下列计数规则运行；自身死亡见后文。');changed['zh_cn'][k]=langs['zh_cn'][k]
append('combat_event.troubleshooting',
    "For Own death, choose an enabled Sequence-controlled external transmitter and set its red/blue frequencies. An internal-only output cannot survive the dying core. Kills and Blocked attacks can use their supported ordinary outputs.",
    "自身死亡必须选择已启用、已设置红蓝频率的序列控制／外部发送频道，仅内部输出无法在核心消失后持续。击杀和攻击被格挡仍可使用各自支持的普通输出。")
put('dashboard.sablelife.description_blueprint_spawn',
    "Places a selected server blueprint when its trigger rises. Can also be linked to an ordinary Heart Core's Own death event.",
    "触发成立时生成选定的服务器蓝图，也可连接普通生命核心的自身死亡事件。")
append('blueprint_spawn.settings',
    "The generation limit belongs to this blueprint module across the current life activation, not to each trigger pulse. For example, a limit of 2 permits two completed generations in total before Release/reactivation resets the counter. Delay postpones a submitted job; Cooldown limits when another can be submitted. Activate core requires one ordinary Heart Core and no Redstone Core; leave it Off for a pure structure.",
    "生成上限属于本蓝图模块，累计本次生命化期间的生成，不是每个触发脉冲各算一份。例如上限 2，表示解除并重新激活前最多完成两次生成。延迟推迟已提交任务，冷却限制下一次提交时间。生成后激活要求恰好一个普通生命核心且没有红石核心；纯结构请关闭此项。")
append('armor.example',
    "The -100% examples above are for ordinary Heart Cores. A Survival Heart Core stops at -90% and charges Energy while reduction is active. Use its module estimate and server rate when planning a timed defense.",
    "以上 -100% 示例用于普通生命核心。生存生命核心最低为 -90%，减伤生效期间持续消耗能量；设计限时防御时，请按模块预计费用和服务器费率计算。")

# Chinese output and event instructions must match the English rather than omit essential steps.
put('sensor.settings',langs['en_us']['manual.sablelife.sensor.settings'],
    "检测半径为 1–64 格，默认 12。形状、半径、内半径、前方宽度和方向决定检测区域，目标类型及视线进一步筛选对象。\n\n标记面输出强度 15 的弱红石信号，例如可点亮该输出处的灯，不会从六个面同时输出。内部输出请选择已启用的模块输出频道，0 表示不绑定；红蓝频率物品可增加外部输出。频道强度设置不改变标记面固定的 15 强度。\n\n反转信号在没有目标时输出，有目标时归零；模块禁用、未运行或触发不成立时不输出。\n\nOwner 只检测核心填写的主人，即使名字在白名单中，也可检测创造模式主人。玩家必须在线、同维度且在范围内；找不到时不改选其他对象。其他目标模式排除主人。")
put('sound_timeline.settings',langs['en_us']['manual.sablelife.sound_timeline.settings'],
    "每层可设置开始时间、音调、音量、范围、播放时长上限及淡出。上限为 0 时，普通音效自然播放，背景音乐沿用正常循环。\n\n正数上限在指定秒数后停止，淡出使用最后一段时间。例如上限 1.5 秒、淡出 0.5 秒，会从第 1 秒开始淡出，在 1.5 秒停止。淡出 0 表示到时立即停止；淡出长于上限时覆盖整段。已经自然结束的声音不会因此延长。\n\n时间线的虚线尾部只是示意，不是测得的文件长度。开始、音调、音量、上限和淡出的滚轮步长为 0.1，范围为 8；淡出可设为 0–600 秒。")

# Preserve exact long-form behavior while removing implementation tangents and correcting scope.
for lang in langs:
    k='manual.sablelife.core.settings';v=langs[lang][k]
    if lang=='en_us':v=v.replace('Owner is an optional player username on page 2.','Owner is an optional player username on page 2 of an ordinary Heart Core, or page 1 of a Survival Heart Core.')
    else:v=v.replace(' 每核心独立的可选玩家名，',' Owner 是每核心独立的可选玩家名，普通核心位于第 2 页，生存核心位于第 1 页，')
    langs[lang][k]=v;changed[lang][k]=v
append('core.settings',
    "These pages describe the ordinary Heart Core. The Survival Heart Core hides fixed or unsupported settings; its health, release and Energy rules are explained in its own chapter. Factions and Owner names do not lock the editor or grant mining access.",
    "本章这些页面说明普通生命核心。生存生命核心会隐藏固定或不支持的设置，生命值、解除和能量规则见其独立章节。阵营和 Owner 名字不会锁定编辑器，也不授予挖掘权限。")
append('server_limits.settings',
    "Entity hitboxes default to 8 per creature (maximumEntityHitboxesPerCreature, 1–32). Blueprint model projection defaults to 8,192 blocks; larger or unsupported multipart projections use bounds only. Natural-spawn limits are separate from Arena limits.\n\nModule switches are grouped by [modules.heartCore], [modules.redstoneCore] and [modules.survivalHeartCore]. Survival material prices and multipliers are under [survivalTokens]. Use the server's loaded sablelife-server.toml; client changes do not set multiplayer prices or limits.",
    "实体受击框默认每生命体 8 个，可通过 maximumEntityHitboxesPerCreature 设为 1–32。蓝图模型投影默认上限 8192 方块，过大或不支持的多部件投影只显示边框。自然生成与场地重生各有独立限额。\n\n三类核心的模块开关分别位于 [modules.heartCore]、[modules.redstoneCore] 和 [modules.survivalHeartCore]，生存材料价值和倍率位于 [survivalTokens]。以服务端实际加载的 sablelife-server.toml 为准，客户端不能决定联机价格和限额。")
append('schema_note',
    'Module chapters describe the full ordinary Heart Core feature set. Other core types and server switches can hide or restrict settings; see Redstone Core and Survival Heart Core for their rules.',
    '模块章节按普通生命核心的完整功能说明。其他核心类型及服务器开关可能隐藏或限制属性；各自规则请看红石核心和生存生命核心章节。')
append('module_management.manual_controls',
    'Body text stays at a readable minimum of 85%; the chapter list still follows your selected density.',
    '正文最低保持 85% 字号，章节列表仍按所选密度排列。')
k='manual.sablelife.combat_event.troubleshooting'
langs['zh_cn'][k]=langs['zh_cn'][k].replace('确认无线频道总开关已开启，目标频道已启用，并设为“序列控制／内部”。','击杀和格挡规则需确认无线总开关开启，并选择已启用且配置正确的输出频道。')
changed['zh_cn'][k]=langs['zh_cn'][k]

# Small wording fixes across already accurate detailed chapters.
for lang in langs:
    for key in list(langs[lang]):
        if not key.startswith('manual.sablelife.'):continue
        v=langs[lang][key]
        if lang=='zh_cn':
            v=v.replace('15 游戏刻 预热','15 游戏刻预热').replace('行时长0','声音层时长为 0 时').replace('无害红石程序','红石控制程序')
        if v!=langs[lang][key]:langs[lang][key]=v;changed[lang][key]=v

# Preserve the later focused introduction review when reapplying this historical review.
import runpy
runpy.run_path(str(root/'scripts/refine-manual-intro-176.py'))['refine'](catalog,langs,changed)

# Retire obsolete layout assertions only for paragraphs deliberately rewritten in this review.
layout_path=root/'scripts/manual-paragraphs.json'
layout=json.loads(layout_path.read_text(encoding='utf8'))
for suffix in list(layout):
    if suffix.split('.')[0] in {'simple_start','basic_operations'}:
        del layout[suffix]
        continue
    key='manual.sablelife.'+suffix
    for lang,boundaries in list(layout[suffix].items()):
        if key in changed[lang]:
            value=langs[lang][key]
            layout[suffix][lang]=[b for b in boundaries if '\n\n'+b in value]
layout_path.write_text(json.dumps(layout,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
workbench_key='manual.sablelife.natural_spawning.workbench'
if workbench_key in langs['en_us'] and workbench_key in langs['zh_cn']:
    if workbench_key not in entry('natural_spawning')['sections']['settings']:
        entry('natural_spawning')['sections']['settings'].append(workbench_key)
    catalog['revision']=max(catalog['revision'],177)
catalog['revision']=max(catalog['revision'],176)
for lang,data in langs.items():(base/f'lang/{lang}.json').write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
(base/'manual/catalog.json').write_text(json.dumps(catalog,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
(root/'diagnostics/manual-review-390-changes.json').write_text(json.dumps(changed,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
print('Manual revision',catalog['revision'],':',len(catalog['entries']),'chapters;', {lang:len(data) for lang,data in changed.items()},'reviewed or added keys')
