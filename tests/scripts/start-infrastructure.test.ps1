# Windows PowerShell 5.1+；命令替身验证编排，临时目录验证零配置初始化。
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
. (Join-Path $taskRoot 'scripts/start-infrastructure.ps1')
$taskNativeInitializer = ${function:Initialize-InfrastructureConfiguration}
function Assert-True([bool]$Condition, [string]$Message) { if (-not $Condition) { throw $Message } }
function Reset-TestState {
    $script:taskCalls = @()
    $script:taskFail = ''
    $script:taskHealth = 'healthy'
    $script:taskProject = 'export-flow'
}
function Invoke-DockerCommand([string[]]$Arguments) {
    $script:taskCalls += ,$Arguments
    if ($Arguments[0] -eq 'info') {
        if ($script:taskFail -eq 'daemon') { throw 'Docker引擎不可用。' }
        return 'ready'
    }
    if ($Arguments -contains 'version') { return 'ready' }
    if ($Arguments[0] -eq 'ps' -or $Arguments[0] -eq 'start') { throw '不得扫描或复用其他项目的容器。' }
    if ($Arguments -contains 'up') {
        if ($script:taskFail -eq 'compose') { throw 'Compose启动失败（包括端口冲突）。' }
        return 'created'
    }
    if ($Arguments -contains 'ps') { return @('mysql-id', 'redis-id', 'rabbitmq-id') }
    if ($Arguments[0] -eq 'inspect') {
        return ConvertTo-Json -InputObject @(@('mysql', 'redis', 'rabbitmq') | ForEach-Object {
            [pscustomobject]@{ Config = @{ Labels = @{ 'com.docker.compose.project' = $script:taskProject; 'com.docker.compose.service' = $_ } }; State = @{ Status = 'running'; Health = @{ Status = $script:taskHealth } } }
        }) -Depth 10 -Compress
    }
    return 'ready'
}
function Initialize-InfrastructureConfiguration { $script:taskCalls += ,@('initialize') }

$taskPassed = 0
Reset-TestState
Start-Infrastructure -TimeoutSeconds 1
$taskUp = @($script:taskCalls | Where-Object { $_ -contains 'up' })
Assert-True ($taskUp.Count -eq 1) 'make infra必须启动本项目Compose。'
Assert-True ($taskUp[0] -contains 'mysql' -and $taskUp[0] -contains 'redis' -and $taskUp[0] -contains 'rabbitmq') '必须创建完整的三个服务。'
Assert-True ($taskUp[0] -contains '--no-recreate' -and $taskUp[0] -contains 'export-flow') '重复执行只管理本项目并保留数据。'
Assert-True ($script:taskCalls[2][0] -eq 'initialize') '创建容器前必须生成配置。'
$taskPassed++

foreach ($taskCase in @('daemon', 'compose', 'unhealthy', 'timeout', 'foreign-project')) {
    Reset-TestState
    switch ($taskCase) {
        'daemon' { $script:taskFail = 'daemon' }
        'compose' { $script:taskFail = 'compose' }
        'unhealthy' { $script:taskHealth = 'unhealthy' }
        'timeout' { $script:taskHealth = 'starting' }
        'foreign-project' { $script:taskProject = 'project-export-flow' }
    }
    $taskFailed = $false
    try { Start-Infrastructure -TimeoutSeconds 1 } catch { $taskFailed = $true }
    Assert-True $taskFailed "$taskCase 必须明确失败，不能把外部服务视为本项目就绪。"
    Assert-True (@($script:taskCalls | Where-Object { $_[0] -in @('stop', 'rm', 'down', 'exec') }).Count -eq 0) '失败时不能删除或修改其他服务。'
    $taskPassed++
}

$taskTemporaryRoot = Join-Path ([IO.Path]::GetTempPath()) ('export-flow-bootstrap-' + [guid]::NewGuid().ToString('N'))
$taskSavedRoot = $taskInfrastructureRoot
$taskOriginalEnvironment = @{}
foreach ($taskKey in @('MYSQL_ROOT_PASSWORD', 'DB_PASSWORD', 'MIGRATOR_PASSWORD', 'RABBITMQ_PASSWORD', 'MYSQL_PORT', 'REDIS_PORT', 'RABBITMQ_PORT', 'RABBITMQ_MANAGEMENT_PORT')) {
    $taskOriginalEnvironment["EXPORT_FLOW_$taskKey"] = [Environment]::GetEnvironmentVariable("EXPORT_FLOW_$taskKey", 'Process')
}
try {
    New-Item -ItemType Directory -Path $taskTemporaryRoot | Out-Null
    Copy-Item -LiteralPath (Join-Path $taskRoot '.env.infrastructure.example'), (Join-Path $taskRoot '.env.example') -Destination $taskTemporaryRoot
    $taskInfrastructureRoot = $taskTemporaryRoot
    & $taskNativeInitializer
    $taskInfrastructureText = [IO.File]::ReadAllText((Join-Path $taskTemporaryRoot '.env.infrastructure.local'))
    $taskApplicationText = [IO.File]::ReadAllText((Join-Path $taskTemporaryRoot '.env.local'))
    Assert-True ($taskInfrastructureText -notmatch '<[^>]+>' -and $taskApplicationText -notmatch '<运行密码>|<迁移密码>|<MQ密码>') '首次执行必须填完所需密码。'
    $taskSecrets = @([regex]::Matches($taskInfrastructureText, '(?m)^EXPORT_FLOW_\w+_PASSWORD=([0-9a-f]{64})\r?$') | ForEach-Object { $_.Groups[1].Value })
    Assert-True ($taskSecrets.Count -eq 4 -and @($taskSecrets | Select-Object -Unique).Count -eq 4) '必须生成四个独立随机凭据。'
    Assert-True ($taskApplicationText.Contains($taskSecrets[1]) -and $taskApplicationText.Contains($taskSecrets[2]) -and $taskApplicationText.Contains($taskSecrets[3])) '应用连接必须使用初始化账号对应凭据。'
    $taskPassed++
    & $taskNativeInitializer
    Assert-True ([IO.File]::ReadAllText((Join-Path $taskTemporaryRoot '.env.infrastructure.local')) -ceq $taskInfrastructureText) '重复运行不得重置凭据。'
    Assert-True ([IO.File]::ReadAllText((Join-Path $taskTemporaryRoot '.env.local')) -ceq $taskApplicationText) '重复运行不得覆盖本地应用配置。'
    $taskPassed++
    $taskCustomFile = Join-Path $taskTemporaryRoot '.env.local'
    $taskLegacyConfiguration = "# user-owned configuration`nEXPORT_FLOW_BACKEND_PORT=8081`nEXPORT_FLOW_FILE_ROOT=D:/custom/export-data`nSPRING_FLYWAY_PASSWORD=legacy-password`nEXPORT_FLOW_DB_PASSWORD=legacy-password`nEXPORT_FLOW_AMQP_URL=amqp://legacy:legacy-password@127.0.0.1:5672`n"
    [IO.File]::WriteAllText($taskCustomFile, $taskLegacyConfiguration)
    & $taskNativeInitializer
    $taskSyncedConfiguration = [IO.File]::ReadAllText($taskCustomFile)
    Assert-True ($taskSyncedConfiguration.Contains("SPRING_FLYWAY_PASSWORD=$($taskSecrets[2])") -and $taskSyncedConfiguration.Contains("EXPORT_FLOW_DB_PASSWORD=$($taskSecrets[1])") -and $taskSyncedConfiguration.Contains("amqp://export_flow_mq:$($taskSecrets[3])@127.0.0.1:5672")) '旧连接凭据必须同步为本项目新基础设施凭据。'
    Assert-True ($taskSyncedConfiguration.Contains('EXPORT_FLOW_BACKEND_PORT=8081') -and $taskSyncedConfiguration.Contains('EXPORT_FLOW_FILE_ROOT=D:/custom/export-data') -and $taskSyncedConfiguration.Contains('# user-owned configuration')) '同步连接时必须保留用户端口、文件根和注释。'
    $taskBackups = @(Get-ChildItem -LiteralPath $taskTemporaryRoot -Filter '.env.local.backup-*')
    Assert-True ($taskBackups.Count -eq 1 -and [IO.File]::ReadAllText($taskBackups[0].FullName) -ceq $taskLegacyConfiguration) '同步前必须备份原始应用配置。'
    & $taskNativeInitializer
    Assert-True (@(Get-ChildItem -LiteralPath $taskTemporaryRoot -Filter '.env.local.backup-*').Count -eq 1) '连接未变化时不得重复备份或重写。'
    $taskPassed++
    Remove-Item -LiteralPath $taskCustomFile
    $taskInfrastructureText = $taskInfrastructureText.Replace('MYSQL_PORT=3306', 'MYSQL_PORT=13306').Replace('REDIS_PORT=6379', 'REDIS_PORT=16379').Replace('RABBITMQ_PORT=5672', 'RABBITMQ_PORT=15673').Replace('RABBITMQ_MANAGEMENT_PORT=15672', 'RABBITMQ_MANAGEMENT_PORT=25672')
    [IO.File]::WriteAllText((Join-Path $taskTemporaryRoot '.env.infrastructure.local'), $taskInfrastructureText)
    & $taskNativeInitializer
    $taskApplicationText = [IO.File]::ReadAllText($taskCustomFile)
    Assert-True ($taskApplicationText.Contains('127.0.0.1:13306') -and $taskApplicationText.Contains('127.0.0.1:15673') -and $taskApplicationText.Contains('127.0.0.1:25672') -and $taskApplicationText.Contains('EXPORT_FLOW_REDIS_PORT=16379')) '应用连接必须匹配可配置端口。'
    $taskPassed++
} finally {
    $taskInfrastructureRoot = $taskSavedRoot
    foreach ($taskKey in $taskOriginalEnvironment.Keys) { [Environment]::SetEnvironmentVariable($taskKey, $taskOriginalEnvironment[$taskKey], 'Process') }
    # 只清理本次创建的完整临时路径，不递归删除项目或业务目录。
    $taskResolvedTemporaryRoot = [IO.Path]::GetFullPath($taskTemporaryRoot)
    if ($taskResolvedTemporaryRoot.StartsWith([IO.Path]::GetFullPath([IO.Path]::GetTempPath())) -and (Split-Path -Leaf $taskResolvedTemporaryRoot).StartsWith('export-flow-bootstrap-')) {
        Remove-Item -LiteralPath $taskResolvedTemporaryRoot -Recurse -Force
    }
}
$taskMakefile = [IO.File]::ReadAllText((Join-Path $taskRoot 'Makefile'))
$taskDevRecipe = [regex]::Match($taskMakefile, '(?m)^dev:.*(?:\r?\n\t.*)*').Value
Assert-True ($taskDevRecipe -notmatch '-Action infra') 'make dev不能创建基础设施，必须与make infra分开。'
$taskPassed++
Write-Output "基础设施初始化验证通过：$taskPassed 项。"
