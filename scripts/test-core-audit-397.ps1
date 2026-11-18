param([string]$Pass='baseline',[string[]]$Only=@())
$ErrorActionPreference = 'Stop'
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
$env:GRADLE_USER_HOME = (Resolve-Path '.gradle-user-home').Path
$auditRoot = 'diagnostics/core-audit-397-20261108' + $(if($Pass -eq 'baseline'){''}else{'-'+$Pass})
if(Test-Path ($auditRoot + '/results.json')){throw 'This pass already has results. Choose a new -Pass name to preserve evidence.'}
New-Item -ItemType Directory -Force -Path $auditRoot | Out-Null
$groups = [ordered]@{
    'core-policy' = 'core_ui,module_policy,redstone_core,management,tests'
    'signals' = 'control,signal,redstone_preview'
    'combat' = 'blast,combat,melee,retaliation'
    'movement' = 'facing,freeze,quake'
    'effects' = 'particle,sound,summon,surface'
    'targets-defense' = 'relations,owner,priority,defense,hitbox'
    'lifecycle' = 'lifecycle,release,chunk,cleanup,cleanup_regression,audit,decoration_cleanup'
    'blueprint-arena' = 'arena,respawn,plot_read,plot_cache'
    'workbench' = 'workbench'
    'cross-core' = 'core_module_audit'
    'whole-alone' = 'whole_cleanup'
    'blueprint-alone' = 'blueprint_spawn'
}
$results = [System.Collections.Generic.List[object]]::new()
foreach ($entry in $groups.GetEnumerator()) {
    $label = $entry.Key
    if($Only.Count -gt 0 -and $label -notin $Only){continue}
    $namespaces = (($entry.Value -split ',') | ForEach-Object {
        if ($_ -eq 'tests') { 'sablelife_tests' } else { 'sablelife_' + $_ + '_tests' }
    }) -join ','
    $runRoot = 'runs/core-audit-397-20261108-' + $Pass + '-' + $label
    New-Item -ItemType Directory -Force -Path ($runRoot + '/config') | Out-Null
    if ($label -ne 'workbench') {
        Set-Content -Path ($runRoot + '/config/sablelife-server.toml') -Encoding utf8 -Value 'wildWorkbenchProtectionRadius = 0'
    }
    $logPath = $auditRoot + '/' + $label + '.log'
    Write-Output ('START ' + $label + ' ' + (Get-Date -Format o))
    & .\gradlew.bat --offline runGameTestServer ('-PgameTestDirectory=' + $runRoot) ('-PgameTestNamespaces=' + $namespaces) *> $logPath
    $runExitCode = $LASTEXITCODE
    $logText = Get-Content $logPath -Raw
    $completed = [regex]::Match($logText, '(\d+) GAME TESTS COMPLETE')
    $passed = [regex]::Match($logText, 'All (\d+) required tests passed')
    $results.Add([pscustomobject]@{
        group=$label; exitCode=$runExitCode; namespaces=$namespaces
        tests=if($completed.Success){[int]$completed.Groups[1].Value}else{0}
        passed=$passed.Success; log=$logPath; finished=(Get-Date -Format o)
    })
    $results | ConvertTo-Json -Depth 5 | Set-Content ($auditRoot + '/results.json') -Encoding utf8
    Write-Output ('END ' + $label + ' exit=' + $runExitCode + ' tests=' + $results[-1].tests + ' passed=' + $passed.Success)
    Select-String -Path $logPath -Pattern 'failed!|required tests failed|All .*required tests passed|GAME TESTS COMPLETE|BUILD FAILED' | Select-Object -Last 12 | ForEach-Object { $_.Line }
}
