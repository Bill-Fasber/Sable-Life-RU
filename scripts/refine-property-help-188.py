import json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
DATA=r"""
bounds|Allowed values: %s to %s. The input edits this property only.|允许范围：%s 至 %s。此输入框只修改当前属性。
choice|Select this property's mode. Full module instructions are in Manual.|选择当前属性的模式；完整用法见操作手册。
sound|Choose and arrange the sounds for this event.|选择并编排此事件播放的音效。
particles|Choose the visual particles for this stage. This selection does not set damage.|选择此阶段的显示粒子；此选项不设置伤害。
color|Set this effect's color; RGB mode allows a custom color.|设置此效果的颜色；RGB 模式可自定义颜色。
volume|Volume of this sound stage. It does not set the hearing radius.|此阶段的音量，不影响声音可听范围。
radius|Radius of the area centered on this module's origin.|以本模块作用中心为基准的范围半径。
damage|Base damage per hit, before damage modifiers and defenses.|每次命中的基础伤害，之后仍计算伤害修正和防御。
blastDamage|Damage per target hit by the impact explosion.|命中爆炸对每个受影响目标造成的伤害。
blastRadius|Radius of one explosion. It does not increase projectile travel distance.|单次爆炸的半径，不增加弹射物飞行距离。
blastBlocks|Allow this explosion to destroy blocks, subject to server and core rules.|允许此爆炸破坏方块，仍受服务器和核心规则限制。
knockback|Knockback strength. Terrain and target physics affect the final distance.|击退强度；实际距离会受地形和目标物理状态影响。
burn|Burn duration applied by this attack.|此攻击附加的燃烧时间。
interval|Delay between this module's repeated actions.|本模块连续发动两次动作之间的间隔。
chargeTime|Time spent charging before the action begins.|动作开始前的蓄力时间。
warningTime|Warning duration before the attack takes effect.|攻击生效前的预警时间。
startDistance|Offset outward from the marked face before the effect begins. Target-position release ignores it.|从标记面向外偏移后开始释放；目标位置释放时不使用此值。
direction|Base direction relative to the marked part. The mark rotates with that part.|以标记部件为基准的基础方向，随部件旋转。
shotHorizontal|Horizontal angle relative to the base direction. Positive turns right.|相对基础方向的水平角度；正值向右偏转。
shotVertical|Vertical angle relative to the base direction. Positive turns up.|相对基础方向的垂直角度；正值向上偏转。
target|Eligible target category. Core whitelist, owner and faction rules still apply.|可选择的目标类别，仍遵守核心白名单、主人和阵营规则。
targetMultiple|Allow more than one eligible target. The module's own target-count limit still applies.|允许选取多个有效目标，仍受模块自身的目标数量限制。
targetRange|Search distance for eligible targets; separate from the attack's reach.|搜索有效目标的距离，与攻击实际覆盖范围分开计算。
lineOfSight|Require a clear line to the target. Off allows detection through obstructions.|要求与目标之间视线通畅；关闭后可隔障碍检测。
releasePosition|Release from the marked origin or at a selected target's position.|选择从标记点释放，或在选中目标的位置释放。
castTargetRange|Search radius for target-position release. It is not the effect radius.|目标位置释放时的搜索半径，不是技能作用半径。
projectile|Choose the displayed projectile item. Its visual appearance does not change the configured damage.|选择弹射物显示的物品；外观不会改变已设置的伤害。
projectileSize|Scale the displayed projectile model.|缩放弹射物的显示模型。
projectileHitbox|Collision size used to detect projectile hits.|用于检测弹射物命中的碰撞尺寸。
projectileRotationX|Rotate the displayed projectile around its local X axis.|让弹射物外观绕自身局部 X 轴旋转。
projectileRotationY|Rotate the displayed projectile around its local Y axis.|让弹射物外观绕自身局部 Y 轴旋转。
projectileRotationZ|Rotate the displayed projectile around its local Z axis.|让弹射物外观绕自身局部 Z 轴旋转。
speed|Projectile travel speed.|弹射物的飞行速度。
arrows|Number of projectiles fired in one volley.|一次齐射发出的弹射物数量。
endRadius|Spread radius at the far end of the shot pattern.|射击散布在末端的半径。
range|Maximum reach of this attack from its release point.|从释放点起计算的攻击最远距离。
shotAim|Choose fixed marked direction or target-based aiming.|选择固定标记方向或基于目标的瞄准方式。
arrowFaceCamera|Make the custom projectile item face the viewer, like a thrown snowball.|让自定义弹射物物品始终面向观看者，类似投掷雪球的外观。
arrowHitbox|Collision size for an arrow projectile; independent of the displayed item size.|箭矢的命中碰撞尺寸，与物品外观大小独立。
arrowResidue|How long a landed arrow remains visible.|箭矢落地后继续保留显示的时间。
bulletHitbox|Collision size used for gun projectile hits.|枪武器弹射物用于检测命中的碰撞尺寸。
launchAngle|Elevation angle used for the mortar's launch trajectory.|迫击炮发射弹道的仰角。
aimMode|Choose the mortar's aiming method.|选择迫击炮的瞄准方式。
mortar.speed|Launch speed used by the mortar trajectory. Target aiming also depends on gravity and the chosen arc.|迫击炮弹道使用的初速度；目标瞄准还受重力和弹道选择影响。
linearMode|Teleport changes position after charging; Travel moves continuously.|瞬移在蓄力后改变位置；行进则持续移动。
moveAim|Fixed direction uses distance and angles. Toward target records a target position when charging starts.|固定方向使用距离和角度；朝向目标会在蓄力开始时记录目标位置。
moveTargetRange|Search radius for the movement target. No eligible target means waiting.|移动目标的搜索半径；没有有效目标时等待。
moveStopDistance|Stop this many blocks before the recorded target center, measured from the movement mark.|以移动标记点到已记录目标中心的距离计算，提前这么多格停止。
moveTargetVertical|Off keeps the path horizontal. On includes the recorded target height.|关闭时保持水平路径；开启时计入已记录目标的高度。
facingCreative|Include Creative-mode players when choosing targets.|选择目标时也允许选中创造模式玩家。
moveDistance|Total distance for fixed-direction movement. Turning does not restart the distance count.|固定方向移动的总距离；转向不会重新累计距离。
moveHorizontal|Horizontal movement offset from the marked direction; positive turns right.|相对标记方向的水平移动偏角；正值向右。
moveVertical|Vertical movement angle; positive points upward. Gravity travel ignores this angle.|垂直移动角度；正值向上。受重力行进时不使用此角度。
travelSpeed|Speed during continuous Travel. It does not change the charge time.|持续行进时的移动速度，不改变蓄力时间。
moveGravity|Travel preserves gravity and vertical motion while driving horizontally.|行进时保留重力及垂直运动，同时驱动水平移动。
moveStep|Allow grounded parts to lift the connected build over an obstacle during Travel.|行进时允许已接地部件抬起相连结构，尝试越过障碍。
moveStepHeight|Height added by one step-assist lift. It does not guarantee overhead clearance.|单次辅助跨步的抬升高度，不保证头顶空间足够。
moveStepCooldown|Minimum delay between step-assist lifts. Clear ground contact is also required before another lift.|辅助跨步之间的最短间隔；再次抬升前还需恢复明确接地。
moveObstruction|Teleport: On checks the route; Off checks the destination only. Travel still checks its path.|瞬移开启时检查路线，关闭时仅检查目的地；行进仍检查路径。
moveForce|Skip collision prechecks, but still require loaded destination chunks and world bounds. Does not clear terrain.|跳过碰撞预检查，仍要求目的地区块已加载且在世界边界内；不会清除地形。
linear_movement.interval|Minimum interval between movement starts; 0 adds no cooldown. Movements still run in tick order.|两次移动开始之间的最短间隔；0 不额外等待，动作仍按游戏 tick 顺序执行。
moveChargeSound|Sounds played while movement charges.|移动蓄力阶段播放的音效。
moveDepartureSound|Sounds played when movement starts.|开始移动时播放的音效。
moveArrivalSound|Sounds played when movement finishes.|移动结束时播放的音效。
moveChargeSoundVolume|Volume of movement's charging sounds.|移动蓄力音效的音量。
moveDepartureSoundVolume|Volume of movement's departure sounds.|移动开始音效的音量。
moveArrivalSoundVolume|Volume of movement's arrival sounds.|移动结束音效的音量。
moveChargeSoundRadius|Hearing range of movement's charging sounds.|移动蓄力音效的可听范围。
moveDepartureSoundRadius|Hearing range of movement's departure sounds.|移动开始音效的可听范围。
moveArrivalSoundRadius|Hearing range of movement's arrival sounds.|移动结束音效的可听范围。
healthBarVisibility|Always shows while active; After taking damage shows only after actual health loss.|始终显示在激活时可见；受伤后显示只在实际掉血后出现。
healthBarHideDelay|Hide after this long without health loss. Another damaging hit restarts the timer.|这么长时间没有继续掉血后隐藏；再次受伤会重新计时。
healthBarViewDistance|Maximum viewer-to-bar-center distance. Outside this range, both name and bar are hidden.|观看者到血条中心的最大距离，超出时名字和血条一起隐藏。
healthBarStyle|Choose a rectangle or a bar with rounded ends.|选择长方形或两端半圆的血条样式。
healthBarWidth|World-space bar width. Does not resize the name.|血条在世界中的宽度，不改变名字大小。
healthBarHeight|World-space bar height. Does not resize the name.|血条在世界中的高度，不改变名字大小。
healthBarName|Show the owning core's name above the bar. An empty name stays hidden.|在血条上方显示所属核心的名字；名字为空时隐藏。
healthBarNameSize|Name text height, independent of bar width and height.|名字文字的高度，与血条宽高独立。
healthBarNameGap|Gap between the bar's upper edge and its name.|血条上边缘与名字之间的间距。
sensor.radius|Target detection radius, up to 256 blocks. Only already-loaded targets can be found.|目标检测半径，上限 256 格；只能发现已加载区域内的目标。
shape|Ring scans around the mark; Forward cone scans along its direction.|圆环围绕标记点检测；前向锥体沿标记方向检测。
innerRadius|Exclude targets closer to the marked origin than this distance.|排除距离标记点小于此值的目标。
frontWidth|Radius at the far end of the Forward cone. Only applies to Forward cone.|前向锥体末端的半径，只在前向锥体模式生效。
sensorChannel|Internal channel receiving the sensor signal. 0 means no internal output.|接收感应信号的内部频道；0 表示不向内部输出。
sensorPower|Signal strength for channel outputs when the sensor detects a target. The marked-face output remains 15.|感应到目标时，频道输出使用的信号强度；标记面输出仍为 15。
sensorInvert|Invert the sensor's output condition.|反转感应器的输出条件。
redstoneFrequency|Red/blue item samples choose the external wireless frequency; samples are not consumed.|红、蓝物品样本组成外部无线频率；样本不会消耗。
redstoneStrengthMode|Choose configured strength, follow input strength, or ramp between two strengths.|选择固定强度、跟随输入强度，或在两个强度之间渐变。
redstonePower|Configured fixed output strength, from 0 to 15.|固定输出模式使用的强度，范围 0 至 15。
redstoneRampStart|Starting signal strength of each ramp.|每次渐变开始时的信号强度。
redstoneRampEnd|Final strength reached by the ramp; held while the trigger remains active.|渐变结束时的强度；触发保持期间维持此值。
redstoneRampTime|Time to change from ramp start to ramp end.|从渐变起始强度变化到结束强度所需的时间。
redstoneMode|Hold follows the trigger; Pulse runs for its configured duration after a rising edge.|保持跟随触发状态；脉冲在上升沿后维持指定时间。
redstonePulse|Length of one output pulse. Ramp mode ignores it.|单次输出脉冲的时长；渐变模式不使用此值。
redstoneInvert|Reverse Hold/Pulse output. Ramp mode ignores inversion; swap its endpoints instead.|反转保持或脉冲输出；渐变模式忽略此项，要反转渐变请交换起止强度。
redstoneFaces|Output to every face or only the selected marked surface.|选择向所有面输出，或只向选中的标记面输出。
redstoneChannel|Internal output channel on this core. 0 disables it; enable that channel to receive the signal.|此核心的内部输出频道；0 为禁用，接收时还需启用对应频道。
laserAim|Fixed follows the marked direction; Tracking aims at an eligible target.|固定沿标记方向发射；追踪朝有效目标瞄准。
laserTracking|Choose how the beam selects or maintains its tracked target.|选择光束如何选取或维持追踪目标。
laserFollow|How quickly the tracked beam turns toward its target.|追踪光束转向目标的跟随强度。
beamLength|Maximum beam length. Obstructions may stop it earlier.|光束最大长度；遇到阻挡时可能提前终止。
laserBreakBlocks|Allow the firing beam to break eligible blocks. Charging and preview do not break blocks.|允许发射中的光束破坏可破坏方块；蓄力和预览不会破坏。
laserBreakMode|Whole line clears breakable centerline blocks; Cut forward advances from the first obstruction.|整线清除中心线上的可破坏方块；向前切割从首个障碍开始推进。
laserBreakSpeed|Forward-cut speed in blocks per second. Does not extend the firing duration.|向前切割每秒推进的格数，不延长发射时间。
beamHits|Number of damage pulses in one firing burst.|一轮光束发射包含的伤害脉冲次数。
hitInterval|Time between repeated damage hits.|连续伤害命中之间的时间间隔。
laser.interval|Pause after a beam burst finishes.|一轮光束发射结束后的停顿时间。
beamWidth|Beam diameter. Changes its thickness.|光束直径，用于调整粗细。
sweepAngle|Horizontal sweep angle. 0 fixes this axis; negative reverses the sweep.|水平扫射角度；0 为此轴固定，负值反转扫射方向。
verticalSweepAngle|Vertical sweep angle. 0 fixes this axis; negative reverses the sweep.|垂直扫射角度；0 为此轴固定，负值反转扫射方向。
sweepSpeed|Horizontal sweep speed in degrees per second. 0 automatically spans the firing burst.|每秒水平扫射的角度；0 自动按整轮发射时长完成扫射。
verticalSweepSpeed|Vertical sweep speed in degrees per second. 0 automatically spans the firing burst.|每秒垂直扫射的角度；0 自动按整轮发射时长完成扫射。
laserParticleSpacing|Spacing between extra particles along the beam. Smaller is denser; the display budget still applies.|沿光束的额外粒子间距，越小越密集；仍受显示数量预算限制。
laserVisualStyle|Choose the built-in beam appearance, separate from extra particles.|选择内置光束外观，与额外粒子分开设置。
beamColor|Color of the built-in beam.|内置光束的颜色。
interactionSpace|World interacts with world blocks; Structure interacts with blocks in the connected Sable structure.|世界模式交互世界方块；结构模式交互相连的 Sable 结构方块。
interactionMode|Interact once on activation or repeat while triggered.|激活时交互一次，或在触发期间持续重复交互。
interactionInterval|Delay between repeated right-click interactions.|连续两次右键交互之间的间隔。
interactionSize|Interaction search size, from 1 to 3. Only the nearest eligible block is used.|交互搜索尺寸，范围 1 至 3；每次只使用最近的有效方块。
interactionOffsetX|X offset of the interaction area from the marked origin.|交互区域相对标记点的 X 偏移。
interactionOffsetY|Y offset of the interaction area from the marked origin.|交互区域相对标记点的 Y 偏移。
interactionOffsetZ|Z offset of the interaction area from the marked origin.|交互区域相对标记点的 Z 偏移。
hitboxDamageMode|Entity only uses receivers for damage. Hybrid also keeps direct structure damage enabled.|实体入口只通过受击实体承伤；混合入口也保留结构直接承伤。
hitboxProvokeMobs|Allow hostile mobs and mobs with retaliation AI to select this receiver. Passive animals stay passive.|允许敌对生物及带反击 AI 的生物主动选中此受击框；被动动物保持被动。
hitboxWidth|Receiver width along world X. The receiver follows its marked part.|受击框沿世界 X 轴的宽度；位置跟随标记部件。
hitboxHeight|Receiver height along world Y.|受击框沿世界 Y 轴的高度。
hitboxDepth|Receiver depth along world Z. Receiver boxes remain world-axis-aligned.|受击框沿世界 Z 轴的深度；框保持世界轴对齐。
hitboxOffsetX|X offset of the receiver from its marked part.|受击框相对标记部件的 X 偏移。
hitboxOffsetY|Y offset of the receiver from its marked part.|受击框相对标记部件的 Y 偏移。
hitboxOffsetZ|Z offset of the receiver from its marked part.|受击框相对标记部件的 Z 偏移。
meleeLength|Length added to the marked melee attack volume.|对标记近战攻击体积扩展的长度。
meleeWidth|Width added to the marked melee attack volume.|对标记近战攻击体积扩展的宽度。
meleeHeight|Height added to the marked melee attack volume.|对标记近战攻击体积扩展的高度。
meleeHorizontal|Horizontal rotation of the melee volume around its marked origin.|近战体积围绕标记点的水平旋转角度。
meleeVertical|Vertical rotation of the melee volume around its marked origin.|近战体积围绕标记点的垂直旋转角度。
meleeHitLimit|Maximum damage hits during one melee action.|一次近战动作最多造成的伤害命中次数。
flameDirectHit|Allow direct flamethrower damage to ignore fire resistance.|允许喷火的直接伤害无视抗火效果。
flameShape|Choose a round cone or a fan-shaped flame volume.|选择圆锥或扇形的喷火范围。
flameFanAngle|Opening angle of the fan-shaped flame.|扇形喷火的展开角度。
width|Radius at the far end of the flame volume.|喷火范围在末端的半径。
flameSpeed|Speed at which the flame's visual effect travels.|喷火显示特效向外推进的速度。
freezeTarget|Choose players, ordinary mobs, or both. Sable creatures are excluded.|选择玩家、普通生物或两者；不包含 Sable 生命体。
freezeRadius|Radius of the circular freeze volume.|圆形冻结体积的半径。
freezeHeight|Vertical height of the freeze volume.|冻结范围的垂直高度。
freezeWarning|Warning duration before targets inside the area are frozen.|区域内目标被冻结前的预警时间。
freezeDuration|How long captured targets have their movement locked.|捕获目标的移动被锁定的持续时间。
fieldShape|Choose a circular or rectangular area.|选择圆形或长方形范围。
fieldLength|Length of the rectangular area.|长方形范围的长度。
fieldWidth|Width of the rectangular area.|长方形范围的宽度。
fieldAngle|Horizontal rotation of the rectangular area.|长方形范围的水平旋转角度。
fieldPlacement|Center the rectangular area on its origin, or extend it ahead.|长方形以作用中心居中，或向前展开。
grabTarget|Choose which target category can be grabbed.|选择可以被抓取的目标类别。
grabRadius|Search radius around the grab origin.|抓取中心周围的搜索半径。
grabWarning|Warning time before grabbing starts.|开始抓取前的预警时间。
grabDuration|How long the grabbed target remains restrained.|被抓取目标持续受到束缚的时间。
grabOffsetX|Horizontal offset of the held position.|被抓取目标停留位置的水平偏移。
grabOffsetY|Vertical offset of the held position.|被抓取目标停留位置的垂直偏移。
pullShape|Choose a sphere or directional funnel. Target-position release uses a sphere.|选择球形或定向漏斗；目标位置释放使用球形。
pullMotion|Choose ordinary attraction or stable suction movement.|选择普通牵引或稳定吸入的移动方式。
pullLength|Length of the directional funnel.|定向漏斗的长度。
pullStartRadius|Radius at the near end of the funnel.|漏斗靠近起点一端的半径。
pullEndRadius|Radius at the far end of the funnel. Equal end radii form a cylinder.|漏斗远端的半径；两端半径相同形成圆柱。
pullRadius|Radius of spherical attraction; independent of target search range.|球形牵引的作用半径，与目标搜索范围独立。
pullWarning|Warning time before attraction captures targets.|牵引捕获目标前的预警时间。
pullDuration|How long this attraction cast remains active.|本次牵引持续生效的时间。
pullStrength|Strength of the attraction applied toward the center.|朝牵引中心施加的牵引强度。
pullSpeed|Speed limit used by attraction movement.|牵引移动使用的速度上限。
pullStop|Radius near the center where attraction brakes or stops; not the search radius.|靠近中心时减速或停止的半径，不是搜索半径。
sphereDistance|Distance from the release origin to the blast center.|释放起点到爆破中心的距离。
sphereChargeSpeed|Movement speed of the charging blast effect.|蓄力爆破效果移动的速度。
fieldRelease|This field is released by a linked attack at its impact point.|此范围伤害场由关联攻击在命中点释放。
fieldRadius|Radius of a circular damage field.|圆形伤害场的半径。
fieldDuration|How long the damage field persists.|伤害场保留并生效的时间。
fieldBurn|Apply the configured burn duration to targets hit by this field.|让伤害场命中的目标受到已设置时长的燃烧。
density|Number or density of visual particles. Does not increase damage hit count.|显示粒子的数量或密度，不增加伤害命中次数。
blueprintEditor|Open the server blueprint selector and choose the blueprint this module generates.|打开服务器蓝图选择界面，指定本模块生成的蓝图。
spawnPosition|Fixed world point stays in place; Follow mark moves the origin with its marked part.|固定世界点保持原地；跟随标记让生成起点随标记部件移动。
spawnAnchor|Choose bottom center or the blueprint's saved origin. Preview shows the resulting placement.|选择底部中心或蓝图保存的原始基准点；预览显示最终放置位置。
spawnOffsetX|X offset applied to blueprint placement.|蓝图生成位置的 X 偏移。
spawnOffsetY|Y offset applied to blueprint placement.|蓝图生成位置的 Y 偏移。
spawnOffsetZ|Z offset applied to blueprint placement.|蓝图生成位置的 Z 偏移。
spawnYaw|Horizontal rotation of the generated blueprint.|生成蓝图的水平旋转角度。
spawnPitch|Pitch rotation of the generated blueprint.|生成蓝图的俯仰旋转角度。
spawnRoll|Roll rotation of the generated blueprint.|生成蓝图的侧倾旋转角度。
spawnActivate|Activate a valid Life Core after placement. The selected blueprint must support activation.|放置完成后激活有效生命核心；所选蓝图需满足激活条件。
spawnDelay|Wait this long after a trigger before generating the blueprint.|收到触发后等待这么长时间再生成蓝图。
spawnCooldown|Minimum time between successful blueprint generations by this module.|本模块连续成功生成蓝图之间的最短间隔。
spawnLimit|Maximum successful generations for this module. Other blueprint modules have separate counters.|本模块最多成功生成的次数；其他蓝图模块各自计数。
moveSetEditor|Edit moves, their conditions, priority, weight and scheduled actions.|编辑各招式的条件、优先级、权重和动作时间安排。
ignoreConditions|Allow selection without target conditions. Priority, weight, cooldown and action timing still apply.|忽略目标等条件参与抽选，仍保留优先级、权重、冷却及动作时间规则。
commandEditor|Open the virtual command editor. Execution is subject to server permissions.|打开虚拟指令编辑器；执行仍受服务器权限限制。
signalRules|Open this module's signal-rule editor.|打开本模块的信号规则编辑器。
healthPhaseRules|Edit health ranges and the signal output of each phase.|编辑血量区间及各阶段的信号输出。
logicMatch|Require all rules or at least one rule to match.|选择需要全部规则满足，或至少一条规则满足。
logicChannel|Internal channel receiving this logic module's result. 0 disables internal output.|接收逻辑结果的内部频道；0 为禁用内部输出。
logicPower|Signal strength of the logic result.|逻辑结果输出的信号强度。
logicMode|Hold the result while true, or emit a pulse when it becomes true.|条件成立期间保持输出，或由不成立变为成立时输出脉冲。
logicPulse|Duration of one logic-result pulse.|逻辑结果单次脉冲的时长。
accumMode|Count the creature's actual health loss continuously or within a timed window.|选择持续累计生命体实际损失的血量，或要求在指定时间窗口内达到。
accumWindow|Time window used by timed damage accumulation.|限时累计伤害使用的时间窗口。
accumRequired|Damage required to satisfy the timed accumulation condition.|限时累计模式需要达到的伤害量。
accumRepeat|Fire once per reset, or repeat as more damage accumulates.|每次重置后只触发一次，或随伤害增加继续触发。
accumThreshold|Damage needed for each cumulative trigger.|累计模式每次触发所需的伤害量。
accumChannel|Internal channel for damage accumulation output. 0 disables it.|伤害累计的内部输出频道；0 为禁用。
accumPower|Signal strength emitted by a successful accumulation.|累计条件满足时输出的信号强度。
accumPulse|Duration of the accumulation output pulse.|伤害累计输出脉冲的时长。
accumInvert|Invert the accumulation module's signal output.|反转伤害累计模块的输出信号。
accumReset|Clear the damage accumulation counter.|清空伤害累计计数。
hitThreshold|Count this many attack hits that cause actual target health loss, then output a signal.|累计造成目标实际掉血的攻击命中，达到此次数后输出信号。
hitSource|Sidebar number of the attack module to count; 0 counts all attack modules.|需要计数的攻击模块在侧栏中的编号；0 为全部攻击模块。
hitGap|Minimum interval between counted attack hits. 0 counts every qualifying damage application.|两次计入的攻击命中之间的最短间隔；0 为每次有效伤害都计数。
hitChannel|Internal output channel after the hit threshold is reached. 0 disables it.|达到命中次数阈值后的内部输出频道；0 为禁用。
hitPower|Strength of the hit-counter output signal.|受击计数输出的信号强度。
hitPulse|Duration of the hit-counter output pulse.|受击计数输出脉冲的时长。
healthAmount|Health added or removed per adjustment.|每次调整增加或减少的生命值。
healthUnits|Use a fixed health amount or a percentage of maximum health.|选择固定生命数值，或生命上限的百分比。
healthMode|Once applies at activation; Periodic repeats using the health-adjustment interval.|单次在激活时执行；周期按生命调整间隔重复执行。
healthInterval|Delay between repeated health adjustments.|重复调整生命值之间的间隔。
healthLethal|Allow a negative health adjustment to reduce health to zero.|允许负向生命调整将生命值降至零。
armorWholeBody|Apply the damage modifier across the whole creature instead of only marked parts.|让伤害修正作用于整个生命体，而不是仅作用于标记部位。
armorRules|Edit damage multipliers, durations and activation conditions for armor rules.|编辑部位伤害倍率、持续时间及生效条件。
sequenceSteps|Edit the ordered steps and signals of this sequence.|编辑序列的步骤顺序及各步信号。
sequenceRepeat|Choose how the sequence repeats.|选择序列的重复方式。
stopOnLow|Stop the active sequence when its trigger goes low.|触发信号变低时停止正在执行的序列。
resetChannel|Channel used to reset sequence progress.|用于重置序列进度的频道。
resetSource|Choose where the sequence reset signal is received from.|选择接收序列重置信号的来源。
"""

DATA+=r"""
explode|Create an explosion when this projectile hits.|弹射物命中时产生爆炸。
impactArea|Choose the linked area-damage module used at impact.|选择命中时调用的范围伤害模块。
impactVisual|Choose the visual effect at the hit point.|选择命中位置显示的视觉效果。
impactCount|Number of impact-effect instances per hit; not extra damage hits.|每次命中显示的效果数量，不是追加伤害次数。
impactSpreadMode|Choose how visual effects spread around the impact.|选择视觉效果在命中点附近的散布方式。
impactSpreadRadius|Maximum visual spread around the impact point.|命中点周围视觉效果的散布半径。
displayItem|Choose the item shown at the marked point.|选择标记点上显示的物品。
displaySize|Scale the displayed item model; does not change attack damage.|缩放显示的物品模型，不改变攻击伤害。
displayX|X offset of the displayed item.|物品显示位置的 X 偏移。
displayY|Y offset of the displayed item.|物品显示位置的 Y 偏移。
displayZ|Z offset of the displayed item.|物品显示位置的 Z 偏移。
displayRotX|Rotate the item around its local X axis.|物品绕自身局部 X 轴旋转。
displayRotY|Rotate the item around its local Y axis.|物品绕自身局部 Y 轴旋转。
displayRotZ|Rotate the item around its local Z axis.|物品绕自身局部 Z 轴旋转。
displayAlways|Ignore trigger and life activation for this display; still requires an enabled module and a valid mark.|显示不依赖触发和生命激活，仍要求模块启用且标记有效。
effects|Choose status effects applied by this module.|选择此模块附加的状态效果。
ambient|Edit the randomly repeated ambient sounds.|编辑随机重复播放的环境音。
ambientMin|Minimum wait before another ambient sound.|再次播放环境音前的最短等待时间。
ambientMax|Maximum wait before another ambient sound.|再次播放环境音前的最长等待时间。
approach|Edit the sounds used when players approach.|编辑玩家接近时播放的音效。
hurt|Edit sounds played when the creature is hurt.|编辑生命体受伤时播放的音效。
pitch|Pitch multiplier of the sound; does not change its hearing range.|声音音调倍率，不改变可听范围。
cooldown|Minimum delay before this event can repeat.|此事件可再次触发前的最短等待时间。
sound.radius|Hearing radius around the sound source.|以发声点为中心的可听半径。
triggerSound|Edit the sound sequence played by this trigger.|编辑触发时播放的音效序列。
soundMode|Play once per activation or repeat while the trigger remains active.|每次激活播放一次，或在触发保持期间重复播放。
contactMode|Also trigger the contact sound when ground contact is detected.|检测到接地时也触发接触音效。
contactDirection|Direction checked from the mark for contact.|从标记点检查接触的方向。
contactDistance|Maximum distance from the marked face to a contact surface.|从标记面到接触表面的最大检查距离。
contactCooldown|Minimum delay between contact-triggered effects.|接触触发效果之间的最短间隔。
bgm|Choose the background music sound.|选择背景音乐使用的音效。
bgmStart|Choose whether music starts on approach or after the creature is hit.|选择接近时或生命体受击后开始播放音乐。
bgmRadius|Distance within which players can hear the background music.|玩家可听见背景音乐的距离。
bgmFade|Time used to fade the music.|背景音乐淡入淡出的时间。
bgmVolume|Background music volume.|背景音乐的音量。
arenaRadius|Horizontal arena radius around the marked fixed center.|以已标记的固定中心为基准的水平场地半径。
warningRange|Horizontal nearby-player range around the living core.|以生命核心当前位置为中心，检查附近玩家的水平范围。
countdown|Warning time for each nearby Survival player after the creature leaves its arena.|生命体离开场地后，给予附近每位生存玩家的警告倒计时。
arenaDeath|When the creature leaves its arena, kill nearby Survival players after their warning countdown expires.|生命体离开场地后，对附近生存玩家倒计时；倒计时结束时处死玩家。
arenaForceReturn|Immediately return an out-of-arena creature to the center and restore its health. Overrides player punishment.|生命体越界时立即回到场地中心并回满生命，优先于玩家惩罚。
arenaReturn|Return the out-of-arena creature when no qualifying players remain in its nearby-player range.|越界生命体附近没有符合条件的玩家时，让生命体回到场地中心。
arenaBuffEnabled|Enable the configured nearby-player buff groups.|启用已配置的附近玩家状态效果组。
arenaBuffGroups|Open the arena buff-group editor.|打开场地状态效果组编辑器。
arenaRespawnEnabled|Restore the recorded arena structure using this module's respawn rules.|按本模块的重生规则还原已记录的场地结构。
arenaRespawnDelay|Wait this long between arena structure respawns.|场地结构两次重生之间的等待时间。
arenaRespawnRecord|Record the current structure as the arena restore template. Keep the record up to date.|将当前结构记录为场地还原模板，请确保记录最新结构。
arenaRespawnClear|Remove the recorded arena template.|清除已记录的场地模板。
hoverX|Pin the marked part on the world X axis.|沿世界 X 轴固定标记部件的位置。
hoverY|Pin the marked part on the world Y axis.|沿世界 Y 轴固定标记部件的位置。
hoverZ|Pin the marked part on the world Z axis.|沿世界 Z 轴固定标记部件的位置。
hoverRotation|Choose whether hovering also holds the structure's rotation.|选择悬停时是否同时固定结构姿态。
hoverMode|Choose direct anchor holding or force-based hovering.|选择直接固定锚点或施力悬停。
hoverStrength|Strength of the force pulling the structure toward its hover anchor.|将结构拉向悬停锚点的施力强度。
hoverDamping|Damping applied to hover motion to reduce oscillation.|悬停运动的阻尼，用于减少来回摆动。
hoverOffset|Maximum elastic displacement on each enabled axis. Hard pin does not use it.|每个启用轴允许的最大弹性位移；硬固定模式不使用此值。
gyroCalibrate|Record the current orientation as the gyroscope's reference pose.|将当前姿态记录为陀螺仪的参考姿态。
gyroTilt|Tilt threshold that starts recovery in either mode.|两种回正模式共用的启动倾角阈值。
gyroSpeed|Righting speed; applies to both recovery modes.|回正时的旋转速度，两种模式都生效。
gyroReturn|Choose how the gyroscope returns to its reference pose.|选择陀螺仪回到参考姿态的方式。
gyroCollision|Check for obstructions while correcting the structure's orientation.|修正结构姿态时检查障碍物。
facingRange|Search radius used to choose the facing target.|选择朝向目标时使用的搜索半径。
facingSpeed|Speed at which the structure turns toward its target.|结构转向目标的速度。
facingVertical|Also turn vertically to aim toward the target's height.|同时垂直转向，以朝向目标高度。
facingCollision|Check obstructions before rotating the structure.|旋转结构前检查障碍物。
turnSpeed|Rotation speed while the turning signal is active.|转向信号生效期间的旋转速度。
turnCollision|Check obstructions before signal-controlled rotation.|执行信号转向前检查障碍物。
verticalTracking|Include the target's height in navigation tracking.|导航追踪时计入目标高度。
targetLock|Keep the selected target or reselect the nearest eligible target.|持续锁定已选目标，或重新选择最近的有效目标。
deadZone|Horizontal angle within which no steering correction is requested.|不再请求水平转向修正的角度范围。
pitchDeadZone|Vertical angle within which no height correction is requested.|不再请求垂直转向修正的角度范围。
navigation.radius|Radius used to find the navigation target.|用于寻找导航目标的半径。
navigationFront|Choose which local direction counts as the structure's front.|选择结构的哪一个局部方向作为正面。
navigationInvert|Reverse the navigation steering output.|反转导航转向输出。
navigationLeftChannel|Internal channel requesting left steering. 0 disables this output.|请求左转的内部频道；0 为禁用此输出。
navigationRightChannel|Internal channel requesting right steering. 0 disables this output.|请求右转的内部频道；0 为禁用此输出。
navigationUpChannel|Internal channel requesting upward steering. 0 disables this output.|请求向上转向的内部频道；0 为禁用此输出。
navigationDownChannel|Internal channel requesting downward steering. 0 disables this output.|请求向下转向的内部频道；0 为禁用此输出。
navigationForwardChannel|Internal channel requesting forward movement. 0 disables this output.|请求前进的内部频道；0 为禁用此输出。
navigationBackwardChannel|Internal channel requesting backward movement. 0 disables this output.|请求后退的内部频道；0 为禁用此输出。
particlePattern|Choose the particle area's geometry.|选择粒子区域的几何形状。
particleExpandLength|Additional length around the marked particle area.|在标记粒子区域基础上增加的长度。
particleExpandWidth|Additional width around the marked particle area.|在标记粒子区域基础上增加的宽度。
particleExpandHeight|Additional height around the marked particle area.|在标记粒子区域基础上增加的高度。
particleRadius|Radius of the selected particle shape.|所选粒子形状的半径。
particleRingHeight|Vertical height of the particle ring.|粒子圆环的垂直高度。
particleLength|Length of the particle line or selected shape.|粒子线或所选形状的长度。
particleDistribution|Choose random or evenly spaced particle placement.|选择随机或均匀分布粒子。
particleFill|Fill the particle volume or place particles only on its surface.|在整个体积内填充粒子，或仅显示在表面。
particleLineMode|Place particles along the line or spray them along it.|沿线放置粒子，或沿线喷射粒子。
particlePropagation|Choose the direction in which particles move from their spawn positions.|选择粒子从生成位置出发的运动方向。
particleSpeed|Movement speed of the displayed particles.|显示粒子的运动速度。
trail|Leave particles at previous positions to form a trail.|让粒子保留在经过的位置，形成轨迹。
motionOnly|Emit particles only while the marked part is moving.|只在标记部件移动时发出粒子。
drift|Amount of random particle drift.|粒子随机漂移的幅度。
trailLength|Length of the distortion trail's volume.|扭曲拖尾体积的长度。
trailWidth|Width of the distortion trail's volume.|扭曲拖尾体积的宽度。
trailHeight|Height of the distortion trail's volume.|扭曲拖尾体积的高度。
trailOffset|Offset of the distortion trail from its marked origin.|扭曲拖尾相对标记起点的偏移。
trailThreshold|Minimum movement required to produce the distortion trail.|产生扭曲拖尾所需的最低运动量。
trailLife|How long each distortion-trail segment remains.|每段扭曲拖尾的保留时间。
trailDistortion|Strength of the screen distortion caused by the trail.|拖尾造成的画面扭曲强度。
trailSpacing|Distance between newly created trail segments.|新建拖尾片段之间的间距。
trailRange|Maximum distance at which the distortion trail affects the viewer.|扭曲拖尾影响观看者的最远距离。
filterEnabled|Enable the screen-color filter.|启用屏幕颜色滤镜。
filterColor|Color used by the screen filter.|屏幕滤镜使用的颜色。
filterStrength|Opacity or strength of the screen-color filter.|屏幕颜色滤镜的覆盖强度。
filterFadeIn|Time for the color filter to fade in.|颜色滤镜淡入所需的时间。
filterFadeOut|Time for the color filter to fade out.|颜色滤镜淡出所需的时间。
shockWaveEnabled|Enable the selected spatial screen effect.|启用所选的空间画面效果。
shockPattern|Choose an expanding ring or radial blur from the effect's release point.|选择扩散圆环，或以实际释放点为中心的径向模糊。
shockRadialStrength|Strength of the radial blur.|径向模糊的强度。
shockRadialTime|Duration of one radial-blur effect.|单次径向模糊的持续时间。
shockRadialClear|Radius around the blur center kept clear.|模糊中心周围保持清晰的半径。
shockShakeEnabled|Enable screen shake; does not require facing the source.|启用屏幕震动，不要求面向发动源。
shockZoomEnabled|Enable the screen zoom effect.|启用画面缩放效果。
shockContact|Trigger visual effects when the marked contact condition is met.|满足标记点的接触条件时触发视觉效果。
shockRadius|Maximum radius of the expanding visual ring.|扩散视觉圆环的最大半径。
shockReverse|Make the visual ring contract instead of expand.|让视觉圆环向内收缩，而不是向外扩散。
shockExpansion|Speed at which the visual ring expands or contracts.|视觉圆环扩散或收缩的速度。
shockWidth|Thickness of the visual ring.|视觉圆环的环带厚度。
shockDistortion|Distortion strength of the visual ring.|视觉圆环的画面扭曲强度。
shockBlur|Blur strength of the visual ring.|视觉圆环的模糊强度。
shockShake|Amplitude of the screen shake.|屏幕震动的幅度。
shockShakeFrequency|How quickly the screen oscillates; separate from shake amplitude.|屏幕振动的频率，与震动幅度独立。
shockShakeTime|Duration of the screen shake.|屏幕震动的持续时间。
shockZoom|Strength of the screen zoom.|画面缩放的强度。
shockZoomTime|Duration of the screen zoom.|画面缩放的持续时间。
shockRange|Maximum distance at which this visual effect affects a player.|此视觉效果影响玩家的最远距离。
shockMode|Choose how the visual effect is activated or repeated.|选择视觉效果的激活或重复方式。
shockInterval|Delay between repeated visual-effect releases.|重复释放视觉效果之间的间隔。
pulseStart|Distance from the marked origin to the pulse center.|标记起点到冲击波中心的距离。
pulse.radius|Radius reached by the shockwave's damage and knockback.|冲击波伤害和击退覆盖的半径。
pulseVisual|Enable the expanding pulse visual effect.|启用扩散冲击波的显示特效。
pulseExpandTime|Time used for the pulse's visual expansion.|冲击波显示特效完成扩散所需的时间。
slashHorizontal|Horizontal angle of the slash from its base direction.|斩击相对基础方向的水平角度。
slashVertical|Vertical angle of the slash from its base direction.|斩击相对基础方向的垂直角度。
slashLength|Length of one slash's attack shape.|单道斩击攻击形状的长度。
slashWidth|Width of one slash's attack shape.|单道斩击攻击形状的宽度。
slashSpeed|Travel speed of the slash.|斩击向前飞行的速度。
slashCount|Number of slashes released together.|同时释放的斩击数量。
slashFan|Angle spread across the simultaneous slashes.|同时释放的多道斩击之间的展开角度。
slashRoll|Roll angle of the slash plane.|斩击平面的侧倾角度。
slashBlockable|Allow the slash to be blocked using the supported defense behavior.|允许通过支持的防御行为格挡斩击。
slashIgnite|Apply the configured burn duration on a hit.|命中时施加所设时长的燃烧。
slashVisual|Choose the slash's visual appearance.|选择斩击的视觉外观。
quakeMode|Choose how the earthquake wave spreads.|选择地震波的扩散方式。
quakeOrigin|Choose the origin used by the earthquake wave.|选择地震波使用的释放起点。
quakeDetectRange|Search radius for targets used by earthquake aiming.|地震波瞄准时的目标搜索半径。
quakeRange|Maximum distance the earthquake wave travels.|地震波传播的最远距离。
quakeWidth|Width of the moving earthquake front.|移动中的地震波前沿宽度。
quakeSpeed|Speed at which the earthquake wave travels.|地震波的传播速度。
quakeLaunch|Upward launch strength applied to targets hit by the wave.|地震波命中目标时施加的向上抛起强度。
quakePush|Horizontal push strength applied to targets hit by the wave.|地震波命中目标时施加的水平推力。
quakeRise|Rise height used by the earthquake's effect.|地震波效果使用的抬升高度。
quakeGrounded|Require targets to be grounded for earthquake hits.|要求目标处于接地状态才受到地震波命中。
quakeReach|Vertical reach of the earthquake wave.|地震波在垂直方向的命中范围。
quakeWaves|Number of waves in one earthquake action.|一次地震动作释放的波次数量。
quakeGap|Delay between waves in the same action.|同一次动作中两道地震波之间的间隔。
strikeItemSize|Scale of the item displayed by a ground strike.|地面打击显示物品的缩放大小。
strikeItemMode|Choose the displayed ground-strike item's behavior.|选择地面打击显示物品的行为方式。
strikeItemX|Rotation of the displayed ground-strike item around its X axis.|地面打击物品外观绕 X 轴旋转的角度。
strikeItemY|Rotation of the displayed ground-strike item around its Y axis.|地面打击物品外观绕 Y 轴旋转的角度。
strikeItemZ|Rotation of the displayed ground-strike item around its Z axis.|地面打击物品外观绕 Z 轴旋转的角度。
strikeStart|Distance from the origin to the first ground strike.|从起点到第一次地面打击的距离。
strikeRays|Number of directions used for the ground-strike pattern.|地面打击图案展开的方向数量。
strikeRandomCount|Number of randomly placed strike points.|随机布置的打击点数量。
strikeMode|Choose how ground-strike points are arranged.|选择地面打击点的排列方式。
strikeRadius|Damage radius of each ground-strike impact, not the full pattern's reach.|每个地面打击点的伤害半径，不是整个图案的延伸距离。
strikeRange|Distance along which ground strikes extend.|地面打击向外延伸的距离。
strikeSpeed|Speed of the ground-strike projectile or descent.|地面打击弹射物或下落过程的速度。
strikeHeight|Height from which the strike descends.|打击开始下落的高度。
strikeStep|Spacing between consecutive ground-strike points.|连续地面打击点之间的距离。
waveDelay|Delay between successive ground-strike steps.|连续地面打击步骤之间的时间间隔。
homingCount|Number of projectiles in one homing salvo.|一轮悬浮追踪齐射的弹射物数量。
homingOffset|Forward distance to the deployed formation; also used for its layout radius.|部署阵型相对起点的向前距离，同时用于阵型的排列半径。
homingHeight|Vertical offset of the deployed formation from its origin.|部署阵型相对起点的垂直偏移。
homingFan|Spread angle of the deployed formation.|弹射物部署阵型的展开角度。
homingPlane|Choose the shape of the deployed projectile formation.|选择弹射物部署阵型的排列形状。
homingRotation|Horizontal rotation of the deployed formation.|部署阵型的水平旋转角度。
homingVerticalRotation|Vertical rotation of the deployed formation.|部署阵型的垂直旋转角度。
homingDeploy|Time used to deploy the salvo before firing.|发射前完成齐射部署所需的时间。
homingMode|Choose how the salvo locks on and pursues its target.|选择齐射锁定并追踪目标的方式。
homingTracking|Strength of homing steering toward the selected target.|向已选目标追踪转向的强度。
homingLife|Maximum lifetime of a homing projectile.|追踪弹射物最多保留的时间。
summonWindup|Wait before deploying the summoned weapon.|部署召唤武装前的等待时间。
summonAttack|Choose the attack module used by this summoned weapon.|选择召唤武装使用的攻击模块。
summonCount|Number of summoned weapons in the formation.|阵型内召唤武装的数量。
summonFormation|Choose how summoned weapons are arranged.|选择召唤武装的排列阵型。
summonRadius|Radius of the summoned formation.|召唤阵型的半径。
summonSpacing|Spacing between summoned weapons.|召唤武装之间的间距。
summonFan|Spread angle of a fan-shaped weapon formation.|扇形武装阵型的展开角度。
summonFormationRoll|Roll angle of the entire formation.|整个阵型的侧倾角度。
summonFormationYaw|Horizontal rotation of the entire formation.|整个阵型的水平旋转角度。
summonDeployGap|Delay between deploying consecutive weapons.|连续部署两件武装之间的间隔。
summonPosition|Choose how the weapon formation's position follows its origin.|选择武装阵型位置跟随起点的方式。
summonAim|Choose how summoned weapons aim.|选择召唤武装的瞄准方式。
summonTurnSpeed|Maximum speed at which a summoned weapon turns to aim.|召唤武装瞄准转向的最大速度。
summonYaw|Horizontal aiming offset of each weapon.|每件武装瞄准方向的水平偏角。
summonPitch|Vertical aiming offset of each weapon.|每件武装瞄准方向的垂直偏角。
summonMuzzleX|Local X offset of the firing point inside the weapon model.|武装模型内发射口的局部 X 偏移。
summonMuzzleY|Local Y offset of the firing point inside the weapon model.|武装模型内发射口的局部 Y 偏移。
summonMuzzleZ|Local Z offset of the firing point inside the weapon model.|武装模型内发射口的局部 Z 偏移。
summonDuration|How long the summoned weapon remains deployed.|召唤武装部署后的保留时间。
weighted|Choose independent drop chance or weighted loot draws.|选择独立掉落概率或按权重抽取战利品。
rolls|Number of draws when using weighted loot.|使用权重战利品时的抽取次数。
spawnEvent|Blueprint generation uses the module's trigger signal.|蓝图生成使用此模块的触发信号。
hurtMode|React once, for a timed duration, or while the hurt condition stays active.|选择受击后触发一次、维持指定时长，或持续满足条件时生效。
hurtCooldown|Minimum delay between hurt-trigger activations.|两次受击触发之间的最短间隔。
hurtDuration|How long one hurt-trigger activation remains active.|一次受击触发生效的持续时间。
dispatchWeight|Relative selection weight among eligible moves with the same priority.|同优先级的有效招式之间进行抽选时的相对权重。
dispatchCooldown|Cooldown before the same move can be selected again.|同一招式再次参与抽选前的冷却时间。
dispatchBusy|Minimum time the selected move remains active.|抽中招式后至少维持的动作时长。
dispatchRest|Rest time after the move finishes.|一招结束后的停顿时间。
phaseUpper|Upper health-percentage bound for this move's condition.|此招式条件允许的生命百分比上限。
phaseLower|Lower health-percentage bound for this move's condition.|此招式条件允许的生命百分比下限。
dispatchNear|Minimum target distance allowed by this move.|此招式允许的目标最小距离。
dispatchFar|Maximum target distance allowed by this move.|此招式允许的目标最大距离。
dispatchCone|Angle of the target-detection cone for this move.|此招式检测目标时使用的锥体角度。
dispatchFront|Choose which direction is the front of the move's detection cone.|选择招式检测锥体的正面方向。
dispatchRepeat|Allow repeating the previous move or prefer a different eligible move.|允许重复上一招，或尽量选择另一招有效招式。
"""

DATA+=r"""
turn_input|Choose an internal channel output or an externally received channel signal as the turning source.|选择内部频道输出，或频道接收的外部信号作为转向来源。
turn_channel|Channel for this turning direction. 0 means unbound.|控制此转向方向的频道；0 为未绑定。
summoned_weapon.projectile|Choose the item model shown as the summoned weapon.|选择召唤武装显示的物品模型。
"""

def main():
    entries={}
    for line in DATA.strip().splitlines():
        if not line.strip():continue
        key,en,zh=line.split('|');assert key not in entries,key;entries[key]=(en,zh)
    for locale,overlay in [('en_us','scripts/english-manual-review.json'),('zh_cn','scripts/chinese-polish.json')]:
        for rel in [f'src/main/resources/assets/sablelife/lang/{locale}.json',overlay]:
            path=ROOT/rel;data=json.loads(path.read_text(encoding='utf-8'))
            for key,words in entries.items():data['dashboard.sablelife.property_help.'+key]=words[0 if locale=='en_us' else 1]
            data.pop('manual.sablelife.figure.schematic',None)
            # Keep the general module description for the manual, but fix its damaged range text.
            for key in list(data):
                if key=='dashboard.sablelife.redstone_help':data[key]=data[key].replace('preserves0�15','preserves 0–15').replace('outputs15','outputs 15').replace('including15','including 15')
                if key=='manual.sablelife.sensor.settings':
                    data[key]=data[key].replace('1–128','1–256').replace('1-128','1-256').replace('1～128','1～256')
            path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    path=ROOT/'src/main/resources/assets/sablelife/manual/catalog.json';data=json.loads(path.read_text(encoding='utf-8'));data['revision']=max(188,data['revision']);path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print('Property help authored:',len(entries),'per locale')
if __name__=='__main__':main()
