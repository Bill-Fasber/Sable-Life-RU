"""Standalone short channel guide, checked against HeartWireless/WirelessChannel routing."""
from pathlib import Path
import json

root=Path(__file__).resolve().parents[1]
base=root/'src/main/resources/assets/sablelife'
updates={
'chapter.wireless_channels':('Wireless Channels','无线频道'),
'wireless_channels.usage':(
    'Open Wireless Channel from the module dashboard. It is the core\'s signal panel, not the Wireless Redstone module. Keep Master and the selected channels enabled. Heart Cores need life activation; assembled Redstone Cores run without it.\n\nChannel numbers belong to this core. Core A channel 1 and core B channel 1 are unrelated. External links match ordered red/blue frequency items instead, so A channel 1 can talk to B channel 7. Samples are not consumed.',
    '从模块主页面打开无线频道，这是核心的信号面板，不是无线红石模块。保持总开关与所用频道启用。生命核心需已激活；红石核心组装后即可运行。\n\n频道编号只属于本核心，A 核心的 1 不会自动连到 B 核心的 1。外部连接看红蓝频率物品及其顺序，所以 A 的 1 也能连接 B 的 7；取样不消耗物品。'),
'wireless_channels.settings':(
    'Choose a channel mode\nInternal repeat: generate a local waveform using Delay, On, Off, Strength and Loop. No frequency items needed.\n\nExternal receive / RX: listen for matching external frequency items. Modules read this with External RX, not Internal wireless.\n\nExternal output / TX: transmit the timed waveform through matching frequency items. Its output can also be read internally.\n\nSequence / internal: accept output from signal-producing modules, rather than generating a waveform. Internal wireless reads it; no frequency items needed.\n\nSequence / external TX: accept those module outputs and transmit them externally. Internal readers can use the same output. External modes need at least one frequency item; both slots, including empty slots, must match the other end.\n\nLatch and names\nLatch holds a nonzero output until its Reset channel fires; reset wins, and input must go low before relatching. It is for output channels, not RX, and runtime state resets on reload. Names are cosmetic (up to 40 characters); Enter/checkmark saves without restarting the signal.',
    '选择频道模式\n内部循环：按延迟、开启时长、关闭时长、强度和循环生成本地波形，不需频率物品。\n\n外部接收／RX：监听匹配的外部频率，模块用“外部接收”触发读取，不是“内部无线”。\n\n外部输出／TX：把定时波形通过频率物品发到外面；其输出也可在核心内部读取。\n\n序列控制／内部：接受信号模块写入，不自行生成波形。模块用“内部无线”读取，不需频率物品。\n\n序列控制／外部发射：接受上述模块输出，并发到外部；内部也能读取同一输出。外部模式至少一格有频率物品，两端红蓝位置及空格必须一致。\n\n锁存与命名\n锁存保留非零输出，直到重置频道触发；重置优先，输入先变低才能再锁存。它用于输出频道，不是 RX，重载后不保留运行状态。名字只作辨认，最多 40 字符，Enter／打钩保存且不重启信号。'),
'wireless_channels.example':(
    'Inside -> inside\nEnable channel 1 as Sequence / internal. Let a signal module write channel 1; set the receiving module to Internal wireless, channel 1. For a Move set action, use Wireless Redstone as the signal producer.\n\nInside -> outside\nSet that output channel to Sequence / external TX and choose red/blue frequency A. Put the same ordered items on an external Create Redstone Link in receive mode. Alternatively, Wireless Redstone can transmit directly from its own frequency slots, without an output channel.\n\nOutside -> inside\nA powered external Create Redstone Link transmits frequency A. Set core channel 1 to External receive / RX with A. Set the consuming module to External RX, channel 1. A needs to be within Create\'s link range in the same dimension, with both ends loaded.\n\nNeed a local copy? Let Wireless Redstone read External RX channel 1 and write channel 2 (Sequence / internal). Other modules then read Internal wireless channel 2.\n\nOutside -> outside\nUse that RX channel as Wireless Redstone\'s trigger. Set its own frequency slots to a different frequency B, Continuous output, Invert Off; choose Follow input to preserve strength. A receiver matching B gets the relayed signal. No marked face or internal output channel is required.',
    '内部 -> 内部\n频道 1 设为已启用的“序列控制／内部”。信号模块向 1 输出，使用者选“内部无线，频道 1”。动作抽选可调度无线红石作为信号来源。\n\n内部 -> 外部\n把输出频道改为“序列控制／外部发射”，设置红蓝频率 A；外面的 Create 红石链接选接收，用同样顺序的物品。也可让无线红石从自己的频率格直接外发，无需输出频道。\n\n外部 -> 内部\n外面的 Create 红石链接用频率 A 发射电力。核心频道 1 设“外部接收／RX”并匹配 A；使用模块选“外部接收，频道 1”。两端需加载、同维度并在 Create 链接距离内。\n\n若要转成本地频道：无线红石读取外部接收 1，输出到设为“序列控制／内部”的频道 2；其他模块再用内部无线 2。\n\n外部 -> 外部\n无线红石用上述 RX 频道触发，自己的频率格设为不同的 B，选持续输出、反转关；跟随输入可保留强度。匹配 B 的接收端收到转发。不需标记面，也不需内部输出频道。'),
'wireless_channels.troubleshooting':(
    'RX input and channel output are separate. An RX channel can show received power while Internal wireless reads zero: use External RX or the local-copy bridge above.\n\nMatch frequency items and red/blue order, not names or channel numbers. Check Master, channel enable, core operation, Create link range and loaded chunks. A module may still need its own valid mark.\n\nMultiple producers combine by highest strength. Give relay inputs/outputs different channels and frequencies to prevent feedback. Output binding 0 means unbound.\n\nRepeat/TX modes generate their own waveform; Sequence modes need a producer. A short pulse may end before a slow action runs. See Wireless Redstone for Pulse, Reverse and Ramp.',
    'RX 输入与频道输出是两路。接收频道有电，但“内部无线”读到 0 时，应改用外部接收，或按示例桥接到本地频道。\n\n核对频率物品与红蓝顺序，不看名字或两端频道编号是否相同。检查总开关、频道启用、核心运行状态、Create 距离与区块加载；使用模块本身仍可能需要有效标记。\n\n多来源取最高强度。转发的输入／输出用不同频道与频率，避免反馈；输出绑定 0 表示未绑定。\n\n循环／定时 TX 自带波形，序列模式需信号来源。脉冲过短可能来不及运行较慢动作。脉冲、反转、渐变细节见无线红石章节。'),
'triggers.troubleshooting':(
    'A short trigger can allow only one shot or hit. Hurt cooldown limits new reactions, not the attack\'s own interval.\n\nInternal wireless reads channel output; External RX reads received input. Channel numbers are local, while external red/blue items choose the frequency. See Wireless Channels for the four connection routes.',
    '短触发可能只够一次发射／命中；受击冷却限制新的反应，不替代攻击自身间隔。\n\n内部无线读取频道输出，外部接收读取 RX 输入。频道编号属于本核心，外部红蓝物品才决定频率。四种内外连接见“无线频道”章节。'),
}
labels={
'wireless.sablelife.mode_repeat':('Channel: internal repeat','频道：内部循环'),
'wireless.sablelife.mode_receive':('Channel: external receive / RX','频道：外部接收／RX'),
'wireless.sablelife.mode_output':('Channel: external output / TX','频道：外部输出／TX'),
'wireless.sablelife.mode_program':('Channel: sequence / internal','频道：序列控制／内部'),
'wireless.sablelife.mode_program_output':('Channel: sequence / external TX','频道：序列控制／外部发射'),
'wireless.sablelife.mode_help':(
    'Internal repeat generates a local timed waveform; External output / TX transmits one. External receive / RX listens for a matching frequency and is read with the External RX trigger. Both Sequence modes accept outputs from signal-producing modules and ignore waveform timing. Internal modes need no frequency items; external modes need a matching red/blue pair with at least one slot filled.',
    '内部循环生成本地定时波形，外部输出／TX 对外发射波形。外部接收／RX 监听匹配频率，模块用外部接收触发读取。两种序列模式接受信号模块输出，不使用波形时序。内部模式不需频率物品，外部模式需匹配红蓝两格且至少一格非空。'),
}
# Keep this sentence about the link endpoints, rather than the frequency itself.
updates['wireless_channels.example']=tuple(s.replace('A needs to be within Create\'s link range in the same dimension, with both ends loaded.',
    'Keep both ends loaded, in the same dimension and within Create\'s link range.') for s in updates['wireless_channels.example'])
updates['wireless_channels.troubleshooting']=tuple(s.replace('Repeat/TX modes generate their own waveform; Sequence modes need a producer.',
    'Internal repeat and External output / TX generate waveforms; both Sequence modes need a producer.') for s in updates['wireless_channels.troubleshooting'])

def refine(catalog,languages,changed=None):
    for suffix,pair in updates.items():
        for i,lang in enumerate(('en_us','zh_cn')):
            key='manual.sablelife.'+suffix;languages[lang][key]=pair[i]
            if changed is not None:changed[lang][key]=pair[i]
    for key,pair in labels.items():
        for i,lang in enumerate(('en_us','zh_cn')):
            languages[lang][key]=pair[i]
            if changed is not None:changed[lang][key]=pair[i]
    chapter={'id':'wireless_channels','title':'manual.sablelife.chapter.wireless_channels',
             'sections':{tab:['manual.sablelife.wireless_channels.'+tab] for tab in ('usage','settings','example','troubleshooting')}}
    catalog['entries']=[e for e in catalog['entries'] if e['id']!='wireless_channels']
    after=next(i for i,e in enumerate(catalog['entries']) if e['id']=='triggers')+1
    catalog['entries'].insert(after,chapter);catalog['revision']=max(catalog['revision'],179)

if __name__=='__main__':
    catalog=json.loads((base/'manual/catalog.json').read_text(encoding='utf-8'))
    languages={lang:json.loads((base/f'lang/{lang}.json').read_text(encoding='utf-8')) for lang in ('en_us','zh_cn')}
    refine(catalog,languages)
    for i,(lang,strings) in enumerate(languages.items()):
        (base/f'lang/{lang}.json').write_text(json.dumps(strings,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
        path=root/'scripts'/('english-manual-review.json' if lang=='en_us' else 'chinese-polish.json');overlay=json.loads(path.read_text(encoding='utf-8'))
        overlay.update({'manual.sablelife.'+suffix:pair[i] for suffix,pair in updates.items()});path.write_text(json.dumps(overlay,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
        overlay.update({key:pair[i] for key,pair in labels.items()});path.write_text(json.dumps(overlay,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    (base/'manual/catalog.json').write_text(json.dumps(catalog,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    path=root/'scripts/manual-paragraphs.json';layout=json.loads(path.read_text(encoding='utf-8'))
    for suffix,pair in updates.items():
        if suffix in layout:layout[suffix]={lang:text.split('\n\n')[1:] for lang,text in zip(('en_us','zh_cn'),pair)}
    path.write_text(json.dumps(layout,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print('Wireless Channels guide: four routes, five channel modes, revision',catalog['revision'])
