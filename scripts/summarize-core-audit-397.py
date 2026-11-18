"""Summarize the isolated 2026-11-08 audit without treating retries as new test cases."""
import csv
import hashlib
import json
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'diagnostics/core-audit-397-20261108'
final_path = ROOT / 'diagnostics/core-audit-397-20261108-final01/results.json'
results = json.loads(final_path.read_text(encoding='utf-8-sig'))
assert len(results) == 12 and all(row['passed'] and row['exitCode'] == 0 for row in results), 'Final audit is incomplete or failed'
ui = json.loads((ROOT / 'runs/core-module-audit-client-397-20261108/core-module-ui-audit.json').read_text())
assert ui['passed'] == ui['cases'] == 218 and not ui['failures']
units = [ET.parse(path).getroot() for path in (ROOT / 'build/test-results/test').glob('TEST-*.xml')]
unit_summary = {key: sum(int(row.attrib[key]) for row in units) for key in ('tests', 'failures', 'errors', 'skipped')}
assert units and unit_summary['failures'] == unit_summary['errors'] == 0

source = (ROOT / 'src/main/java/dev/sablelife/module/VirtualModule.java').read_text(encoding='utf-8')
types = re.findall(r'"([a-z_]+)"', re.search(r'AVAILABLE_TYPES = List.of\((.*?)\);', source, re.S).group(1))
policy = (ROOT / 'src/main/java/dev/sablelife/heart/RedstoneCorePolicy.java').read_text(encoding='utf-8')
redstone = re.findall(r'"([a-z_]+)"', re.search(r'TYPES=List.of\((.*?)\);', policy, re.S).group(1))
survival = [value for value in types if value not in {'command', 'loot', 'arena', 'blueprint_spawn'}]
assert (len(types), len(redstone), len(survival)) == (48, 17, 44)
lang = json.loads((ROOT / 'src/main/resources/assets/sablelife/lang/zh_cn.json').read_text(encoding='utf-8'))
families = {
    'move_set': 'MoveSet; RedstoneCore; SummonMoveSet',
    'signal_logic': 'SignalModule; RedstoneCore; ModuleAvailability',
    'combat_event': 'SignalModule; EventCount',
    'health_phase': 'HealthPhaseRule; SensorHealthPhase',
    'hit_counter': 'HitHealth; HitCounterExternal',
    'damage_accumulation': 'DamageAccumulation',
    'sequence': 'DispatchRedstone; RedstoneCore',
    'wireless_redstone': 'DispatchRedstone; FollowRedstone; RedstoneInverse; InternalRedstone',
    'command': 'DispatchRedstone; AuditSafety',
    'sensor': 'SensorOutput; SensorHealthPhase; VirtualModule',
    'navigation': 'RedstonePreview; TargetPriority; RedstoneCore',
    'melee': 'MeleeOffset; CoreRelease; CoreModuleAudit',
    'flame': 'FlameBody; VirtualModule; CoreModuleAudit',
    'arrow': 'VirtualModule; ProjectileAim; CoreRelease; CoreModuleAudit',
    'gun': 'ProjectileAim; SurfaceFieldAttack; CoreModuleAudit',
    'mortar': 'MortarBlast; ProjectileAim; CoreModuleAudit',
    'homing': 'ProjectileAim; MultiOrigin; CoreModuleAudit',
    'laser': 'LaserBreaking; ProjectileAim; CoreModuleAudit',
    'sphere_blast': 'SphereBlast; CoreModuleAudit',
    'slash': 'Slash; SlashGuard; CoreModuleAudit',
    'ground_strike': 'GroundStrikeItem; Quake; CoreModuleAudit',
    'quake': 'Quake; CoreModuleAudit',
    'pull': 'RelationsAndPull; PullVisual; CoreModuleAudit',
    'freeze': 'Freeze; FreezeMultiple; MobFreeze; CoreModuleAudit',
    'grab': 'GrabVolume; MobGrab; CoreModuleAudit',
    'pulse': 'Shockwave; ShockwaveZoom; CoreModuleAudit',
    'surface_field': 'SurfaceField; SurfaceFieldAttack; SurfaceFieldBlueprint; CoreModuleAudit',
    'summoned_weapon': 'SummonedWeapon; SummonConfiguration; SummonExtension; SummonFormation; SummonTargetCache; CoreModuleAudit',
    'arena': 'Arena; ArenaBuff; ArenaRespawn; RespawnDisplayPlacement',
    'armor': 'ArmorRule; WholeArmor; MoveSetArmor; CoreRelease',
    'health_adjustment': 'HitHealth; CoreModuleAudit',
    'linear_movement': 'LinearTarget; GravityTravel; ForcedMovement; ModuleAvailability',
    'auto_facing': 'AutoFacing; TravelTurning; RedstoneCore',
    'signal_turn': 'SignalTurn; RedstonePreview',
    'gyroscope': 'Gyroscope',
    'hover_anchor': 'HoverAnchor',
    'visual_shockwave': 'VisualFilter; ShockwaveZoom; ModuleAvailability',
    'distortion_trail': 'DistortionTrail; ModuleAvailability',
    'particles': 'ParticlePattern; ParticleColor; ModuleAvailability',
    'health_bar': 'HealthBar',
    'item_display': 'ItemDisplay; ModuleAvailability',
    'trigger_sound': 'ContactSound; VirtualModule; ModuleAvailability',
    'sound': 'SoundTimeline; VirtualModule',
    'bgm': 'VirtualModule',
    'loot': 'LootEditor; VirtualModule; TerrainBreak',
    'blueprint_spawn': 'BlueprintSpawn; PlotChunkCache',
    'entity_hitbox': 'EntityHitbox; LifeRetaliation',
    'block_interaction': 'ModuleAvailability',
}
assert set(families) == set(types)
coverage = [dict(type=value, name=lang['dashboard.sablelife.type_' + value], heart=True,
                 redstone=value in redstone, survival=value in survival, families=families[value]) for value in types]
with (OUT / 'module-coverage.csv').open('w', encoding='utf-8-sig', newline='') as target:
    writer = csv.DictWriter(target, fieldnames=list(coverage[0]))
    writer.writeheader()
    writer.writerows(coverage)
summary = {
    'version': '0.1.397', 'date': '2026-11-08', 'gameTests': sum(row['tests'] for row in results),
    'groups': results, 'unitTests': unit_summary, 'clientUi': ui,
    'catalogCounts': {'heart': 48, 'redstone': 17, 'survival': 44},
    'coverage': coverage,
    'productionChange': 'Initialize sound-library settings as empty strings, not numeric zero.',
    'sourceSha256': {str(path.relative_to(ROOT)): hashlib.sha256(path.read_bytes()).hexdigest() for path in [
        ROOT / 'src/main/java/dev/sablelife/module/VirtualModule.java',
        ROOT / 'src/gameTest/java/dev/sablelife/testing/CoreModuleAuditGameTests.java',
        ROOT / 'src/gameTest/java/dev/sablelife/testing/CoreModuleClientAudit.java']},
    'limitations': ['Isolated fixtures, not every setting combination or arbitrary player creation.',
                    'Client inventory pages use supplied snapshots without connecting to a live world.',
                    'No new 64-creature benchmark, multiplayer soak test, all-shader or all-mod compatibility claim.',
                    'Historical rare unable-to-release feedback and SlashBlade target-filter diagnosis are not declared fixed.'],
}
(OUT / 'summary.json').write_text(json.dumps(summary, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
group_rows = '\n'.join(f"| {row['group']} | {row['tests']} | 通过 |" for row in results)
module_rows = '\n'.join(f"| {row['name']} `{row['type']}` | ✓ | {'✓' if row['redstone'] else '—'} | {'✓' if row['survival'] else '—'} | {row['families']} |" for row in coverage)
report = f'''# 三核心模块功能回归，2026-11-08

当前开发版本 0.1.397，协议 44。最终隔离运行的 **{summary['gameTests']} 项 GameTest、{unit_summary['tests']} 项单元测试全部通过**；真实客户端 **218 个中英文模块页面案例、342 次参数分页检查通过**。这里的通过指报告所列测试场景，不等于所有造物、所有参数组合和所有外部模组均已验证。未打包新 JAR。

## 范围与方法

- 普通 Heart Core：48 种模块；Redstone Core：17 种能力；Survival Heart Core：44 种能力。相同模块在不同核心的运行入口、权限和费用并不完全相同。
- 三个核心各遍历全部 48 种存档模块，检查可用目录、允许／禁止边界、保存载入、管理员禁用、禁用后保留设置。实际可用能力共 109 种组合；另外 35 种组合验证禁止运行。
- 在原有单模块专项之外，新增生存核心 16 种攻击／控制／召唤的服务器实际 tick 检查：先保持已激活且余额为 0，再补充 Energy，验证实际进入扣费路径、费用不低于报价、关闭或解除后的行为。未付费阶段同时检查测试目标没有受伤、余额不为负。并补测附着区域伤害与服务器倍率、回血仅支付实际恢复量。
- 近战按成功命中收费；召唤武装的已有成员按设计不随部署触发结束而消失，其攻击模板仍需付费，解除会清除。喷火报价对应一次付费火焰前沿，持续喷射可连续扣费，因此不能把几 tick 的总扣费与单次报价当成同一个数。
- 页面检查使用真实 Minecraft 客户端、独立无世界目录和供应的菜单快照：中英文各 109 个模块案例，GUI 2／3 交替，当前 85% 界面密度。检查实际显示的按钮译文、控件视口范围与参数分页，并保存代表截图。**每个模块没有在每一种 GUI／密度组合下重复运行**；没有逐个点击所有子编辑器或穷举所有动态模式。

## 最终运行结果

| 隔离批次 | GameTest 数 | 结果 |
| --- | ---: | --- |
{group_rows}
| 合计（不重复累计重试） | {summary['gameTests']} | 全通过 |

JUnit：20 个测试类、{unit_summary['tests']} 项、0 失败、0 错误。手册修订 179 的 63 章节／48 模块／双语静态校验通过。`git diff --check` 通过，仅有既有 CRLF 提示。

主要覆盖：攻击命中、弹射物、遮挡与多发、部位与全身免伤、目标与阵营过滤、移动／旋转／悬停、无线信号、动作抽选、效果与声音、实体框、蓝图与场地、模块编辑与存档、充值／扣费／回血／余额转移、激活／死亡／解除／清理。工作台生成保护单独保留默认 64 格检查；其他历史蓝图场景使用隔离配置关闭该项，避免新增保护改变这些测试的前提。

## 这次发现及处理

1. **音效字段初始化类型不一致，已修正。** 新模块原先把所有属性先写成数字 0，未显式设默认音效的 library 字段（例如 Slash 的持续飞行音效）会在首次载入时变成空字符串。现在 library 属性一开始就写为空字符串，再应用已有默认声音。跨三个核心的全部 48 种模块存档检查通过。这是数据类型一致性修复，并非发现所有默认声音都会丢失。
2. **首轮 821 项中 11 项失败，9 项为旧测试预期。** 更新了旧效果编辑 API、动态无线频道创建、移动火焰前沿的到达时间、红石核心已开放的粒子能力、粒子预览改为实线／斜纹数据包、第三个核心注册数；补齐绿宝石实际充值验证。没有通过降低产品规则来让旧测试通过。
3. **其余 2 项与测试环境隔离有关。** 大型整体清理测试与其他全世界清理场景合并时，附近哨兵结构被移除；单独世界三项整体清理连续复测通过。最终把全局清理与大型测试分开，**没有据此声称已定位并修复一个产品误删缺陷**。蓝图删除测试的实际失败原因是 `placement_nearby_cap`：此前案例有意保留的对象占满附近名额。该删除专项临时提高隔离测试名额并恢复，数量上限专项仍保留原检查，最终全组通过。
4. 新增测试初期也纠正了不完整的召唤攻击模板、近战目标位置、以及错误地要求部署模块关闭就取消已有召唤的测试假设。原始失败日志均保留。

## 逐模块覆盖表

✓ 表示该核心有此能力；— 表示按设计不提供，不是漏测。表中测试族对应 `src/gameTest/java/dev/sablelife/testing/*GameTests.java`；所有类型还参加 `CoreModuleAuditGameTests` 的策略／配置／存档检查和客户端巡检。测试族是覆盖入口索引，不表示每个测试都在三个核心上重复一遍。

| 模块 | Heart | 红石 | 生存 | 主要测试族 |
| --- | :---: | :---: | :---: | --- |
{module_rows}

## 环境、复跑与证据

- Windows、本地 JDK 21、Minecraft 1.21.1、NeoForge 21.1.235、Sable 2.0.5、Create 6.0.10、Create Aeronautics bundled 1.3.2、Photomancy 1.0.1。使用开发运行依赖，不是玩家完整整合包。
- 各批次使用新的 `runs/core-audit-397-20261108-final01-*` 世界；没有加载或修改玩家原世界、原配置和原蓝图。测试中的清理命令和临时配置只作用于隔离世界。
- 复跑：`scripts/test-core-audit-397.ps1 -Pass 新的唯一名称`。已存在结果的名称会拒绝覆盖。JUnit 用 `gradlew --offline test`，客户端用 `gradlew --offline runCoreModuleAuditClient`。
- 最终批次结果：`diagnostics/core-audit-397-20261108-final01/results.json`；每批原始日志同目录。
- 机器汇总／逐模块 CSV：`diagnostics/core-audit-397-20261108/summary.json`、`module-coverage.csv`。汇总记录关键源码 SHA-256。
- 客户端：`diagnostics/core-audit-397-20261108/client-ui-01.log`、`client-ui-results.json`；截图在其 `screenshots/`，人工查看了英文普通核心喷火、英文红石交互与中文生存交互三张代表图。
- JUnit 原始 XML 已复制至同目录 `unit-tests/`。首次、retry01、retry02 与最终日志分别保留，不用重试次数增加通过数。

## 仍不能由本轮排除的问题

- 任意自制机械的极端旋转、跨区块／跨维度、超大结构和全部参数组合；多人抢改、长时间反复重启和崩溃恢复；全部第三方武器与光影组合。
- 历史上“持续攻击时偶发无法解除”的用户反馈，本轮解除专项及攻击后解除通过，但没有用户原问题造物，不能宣布该罕见反馈已被复现或修复。
- SlashBlade 的目标过滤问题仍见独立诊断报告；本轮没有实施其兼容修改，也不能宣传为全模组适配。
- 本轮不是新的 64 只自行动作压力测试。旧 MSPT 报告、自然生成生命周期与五套光影报告继续保留，应连同各自限制引用。

本轮结论：列出的隔离功能回归已通过；保留这些边界继续实机测试。版本与独立功能累计数不增加，尚未打包。
'''
(ROOT / 'docs/CORE_MODULE_AUDIT_2026-11-08.md').write_text(report, encoding='utf-8')
print(json.dumps({key: summary[key] for key in ('gameTests', 'unitTests', 'clientUi', 'catalogCounts')}, ensure_ascii=False))
