"""Two real local clients and a packaged dedicated server; isolated directories only."""
import concurrent.futures, json, runpy, subprocess, time

m=runpy.run_path('scripts/release-freeze-check.py')
r=m['ROOT'];d=m['EVIDENCE'];jar=d/'sablelife-neoforge-1.21.1-v0.1.402-fix11.jar'
world=r/'runs/release-freeze-fix11-network';world.mkdir(exist_ok=True)
(world/'server.properties').write_text('online-mode=false\nserver-ip=127.0.0.1\nserver-port=25591\nview-distance=3\nsimulation-distance=3\nspawn-protection=0\nmax-tick-time=120000\n',encoding='utf-8')
program=d/'network-client-program.args'
program.write_text((r/'build/moddev/coreModuleAuditClientRunProgramArgs.txt').read_text().replace('\n1600\n','\n640\n').replace('\n900\n','\n480\n'))
clients=[]
with concurrent.futures.ThreadPoolExecutor(max_workers=1) as pool:
    future=pool.submit(m['launch'],'network-fixed','',jar,['-Dsablelife.releaseNetworkServer=true'],world,False,True)
    log=d/'network-fixed.log'
    for attempt in range(120):
        if log.exists() and 'Done (' in log.read_text(errors='replace'):break
        if future.done():break
        time.sleep(1)
    for role in ['A','B']:
        folder=r/'runs'/('release-freeze-fix11-client-'+role);folder.mkdir(exist_ok=True)
        (folder/'options.txt').write_text('renderDistance:3\nsimulationDistance:3\nmaxFps:30\npauseOnLostFocus:false\nautoJump:false\nonboardAccessibility:false\n',encoding='utf-8')
        folders=';'.join(['sablelife%%'+jar.as_posix(),'sablelife_tests%%'+(r/'build/classes/java/gameTest').as_posix(),'sablelife_tests%%'+(r/'build/resources/gameTest').as_posix()])
        cmd=[str(m['JAVA']),'@'+str(r/'build/moddev/coreModuleAuditClientRunVmArgs.txt'),'@'+str(d/'packaged-classpath.args'),'-Xmx2G','-Dfml.modFolders='+folders,'-Dneoforge.enableGameTest=false','-Dsablelife.coreModuleAudit=false','-Dsablelife.releaseNetworkClient=true','-Dsablelife.networkRole='+role,'@'+str(program),'--username','Release'+role]
        f=(d/('network-fixed-client-'+role+'.log')).open('w',encoding='utf-8')
        clients.append((role,subprocess.Popen(cmd,cwd=folder,stdout=f,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW),f))
    try: result=future.result(timeout=400)
    finally:
        for role,proc,f in clients:
            try:code=proc.wait(timeout=35)
            except subprocess.TimeoutExpired:proc.terminate();code=proc.wait(timeout=20)
            f.close();print('CLIENT_EXIT',role,code,flush=True)
    texts={role:(d/('network-fixed-client-'+role+'.log')).read_text(errors='replace') for role in ['A','B']}
    result['bothClientFlowsComplete']=all('RELEASE_NETWORK_CLIENT_COMPLETE phase=3' in s for s in texts.values())
    result['configSyncExceptionAbsent']=all('Cannot call getFullPath()' not in s for s in texts.values())
    (d/'network-fixed-summary.json').write_text(json.dumps(result,indent=2),encoding='utf-8')
    print(json.dumps(result),flush=True)
    if not all(result[k] for k in ['passed','bothClientFlowsComplete','configSyncExceptionAbsent']): raise SystemExit(1)
