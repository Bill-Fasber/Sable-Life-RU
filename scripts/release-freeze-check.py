"""Run isolated native tests using an actual packaged production mod."""
from pathlib import Path
import argparse, subprocess, json, time, re

ROOT = Path(__file__).resolve().parent.parent
EVIDENCE = ROOT / 'diagnostics/release-freeze-fix10'
JAVA = Path('C:/Program Files/Java/jdk-21/bin/java.exe')

def launch(label, namespaces, jar, extra=(), run=None, workbench=False, server=False):
    run = run or ROOT / 'runs' / ('release-freeze-fix10-' + label)
    run.mkdir(parents=True, exist_ok=True)
    if not workbench:
        (run/'config').mkdir(exist_ok=True)
        config=run/'config/sablelife-server.toml'
        if not config.exists(): config.write_text('wildWorkbenchProtectionRadius = 0\n', encoding='utf-8')
    vm=EVIDENCE/'gameTestServerRunVmArgs.txt'
    program=EVIDENCE/'gameTestServerRunProgramArgs.txt'
    folders=';'.join(['sablelife%%'+jar.as_posix(),
        'sablelife_tests%%'+(ROOT/'build/classes/java/gameTest').as_posix(),
        'sablelife_tests%%'+(ROOT/'build/resources/gameTest').as_posix()])
    paths=[Path(s).as_posix() for s in (EVIDENCE/'runtime-classpath.txt').read_text().splitlines()
        if s and Path(s) not in [ROOT/'build/classes/java/main',ROOT/'build/resources/main']]
    classpath=EVIDENCE/'packaged-classpath.args'
    classpath.write_text('-cp\n"'+';'.join(paths)+'"\n',encoding='utf-8')
    args=[str(JAVA),'@'+str(vm),'@'+str(classpath),'-Xmx3G','-Dfml.modFolders='+folders,
        '-Dneoforge.enabledGameTestNamespaces='+namespaces,
        '-Dsablelife.expectedReleaseJar='+jar.name,*extra,'@'+str(program)]
    if server:
        (run/'eula.txt').write_text('eula=true\n')
        args=[a for a in args if not a.startswith('-Dneoforge.enabledGameTestNamespaces=')]
        # The copied VM argfile enables GameTest; override for real server boots.
        args.insert(-1,'-Dneoforge.gameTestServer=false')
        args.insert(-1,'-Dneoforge.enableGameTest=false')
        args.append('--nogui')
    log=EVIDENCE/(label+'.log')
    if log.exists(): raise RuntimeError('Preserve previous evidence: '+str(log))
    start=time.monotonic()
    with log.open('w',encoding='utf-8') as f:
        proc=subprocess.Popen(args,cwd=run,stdout=f,stderr=subprocess.STDOUT,
            creationflags=subprocess.CREATE_NO_WINDOW)
        try: code=proc.wait(timeout=600)
        except subprocess.TimeoutExpired:
            proc.terminate();code=proc.wait(timeout=30)
    text=log.read_text(encoding='utf-8',errors='replace')
    passed=re.search(r'All (\d+) required tests passed',text)
    row=dict(label=label,jar=jar.name,exitCode=code,seconds=round(time.monotonic()-start,2),
        tests=int(passed[1]) if passed else 0,passed=bool(passed) and code==0,log=str(log.relative_to(ROOT)))
    if server: row['passed']=code==0 and ('success=true' in text or 'RELEASE_REPLAY_PASS' in text)
    (EVIDENCE/(label+'.result.json')).write_text(json.dumps(row,indent=2),encoding='utf-8')
    print(json.dumps(row),flush=True)
    return row

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--group',default='core-policy');p.add_argument('--prefix',default='');p.add_argument('--jar');a=p.parse_args()
    groups={
      'core-policy':'core_ui,module_policy,redstone_core,management',
      'targets-defense':'relations,owner,priority,defense,hitbox,hitbox_mob_geometry',
      'lifecycle':'lifecycle,release,chunk,cleanup,cleanup_regression,audit,decoration_cleanup',
      'cross-core':'core_module_audit',
      'blueprint-arena':'arena,respawn,plot_read,plot_cache',
      'blueprint-alone':'blueprint_spawn',
      'whole-alone':'whole_cleanup',
      'workbench':'workbench',
      'release-candidate':'release_candidate',
    }
    selected=list(groups) if a.group=='all' else a.group.split(',')
    jar=Path(a.jar).resolve() if a.jar else EVIDENCE/'sablelife-neoforge-1.21.1-v0.1.402-fix11.jar'
    failed=False
    for group in selected:
        namespaces=','.join('sablelife_'+s+'_tests' for s in groups[group].split(','))
        failed=not launch(a.prefix+group,namespaces,jar,workbench=group=='workbench')['passed'] or failed
    if failed: raise SystemExit(1)
