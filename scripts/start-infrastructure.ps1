param(
    [ValidateSet('up', 'status')][string]$Action = 'up',
    [ValidateRange(1, 600)][int]$TimeoutSeconds = 180,
    [Alias('--help')][switch]$Help
)
# Windows PowerShell 5.1+；只管理当前项目Compose，不探测或复用其他项目容器。
$ErrorActionPreference = 'Stop'
$taskInfrastructureRoot = Split-Path -Parent $PSScriptRoot

function Invoke-DockerCommand([string[]]$Arguments) {
    $taskPreviousErrorAction = $ErrorActionPreference
    try {
        # Compose正常进度也会写stderr，以退出码判定结果。
        $ErrorActionPreference = 'Continue'
        $taskOutput = & docker @Arguments 2>&1
        $taskExitCode = $LASTEXITCODE
    } finally { $ErrorActionPreference = $taskPreviousErrorAction }
    if ($taskExitCode -ne 0) { throw "Docker操作 $($Arguments[0]) 失败：$($taskOutput -join [Environment]::NewLine)" }
    return $taskOutput
}

function New-InfrastructureSecret {
    $taskBytes = New-Object byte[] 32
    $taskRandom = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $taskRandom.GetBytes($taskBytes) } finally { $taskRandom.Dispose() }
    return ([BitConverter]::ToString($taskBytes)).Replace('-', '').ToLowerInvariant()
}

function Initialize-InfrastructureConfiguration {
    # 保留基础设施凭据；应用连接以本项目配置为准，修改前备份并保留其他设置。
    $taskEnvironmentFile = Join-Path $taskInfrastructureRoot '.env.infrastructure.local'
    if (-not (Test-Path -LiteralPath $taskEnvironmentFile)) {
        $taskTemplate = [IO.File]::ReadAllText((Join-Path $taskInfrastructureRoot '.env.infrastructure.example'))
        foreach ($taskKey in @('MYSQL_ROOT', 'DB', 'MIGRATOR', 'RABBITMQ')) {
            $taskTemplate = $taskTemplate.Replace("<${taskKey}_PASSWORD>", (New-InfrastructureSecret))
        }
        [IO.File]::WriteAllText($taskEnvironmentFile, $taskTemplate, [Text.UTF8Encoding]::new($false))
        Write-Host '已生成 .env.infrastructure.local（本地随机凭据，不提交Git）。'
    }
    $taskValues = @{}
    foreach ($taskLine in [IO.File]::ReadAllLines($taskEnvironmentFile)) {
        $taskText = $taskLine.Trim()
        if (-not $taskText -or $taskText.StartsWith('#')) { continue }
        if ($taskText -notmatch '^(EXPORT_FLOW_[A-Z_]+)=(.*)$') { throw '基础设施配置必须使用KEY=value格式。' }
        $taskValues[$Matches[1]] = $Matches[2]
    }
    foreach ($taskKey in @('MYSQL_ROOT', 'DB', 'MIGRATOR', 'RABBITMQ')) {
        $taskValue = $taskValues["EXPORT_FLOW_${taskKey}_PASSWORD"]
        if ($taskValue -notmatch '^[0-9a-f]{64}$') { throw "EXPORT_FLOW_${taskKey}_PASSWORD须为64位小写十六进制值；首次执行make infra会自动生成。" }
    }
    foreach ($taskKey in @('MYSQL_PORT', 'REDIS_PORT', 'RABBITMQ_PORT', 'RABBITMQ_MANAGEMENT_PORT')) {
        $taskValue = $taskValues["EXPORT_FLOW_$taskKey"]
        if ($taskValue -notmatch '^\d{1,5}$' -or [int]$taskValue -lt 1 -or [int]$taskValue -gt 65535) { throw "EXPORT_FLOW_$taskKey 端口无效。" }
    }
    # 文件是本项目Compose配置的唯一来源，避免继承旧应用环境改变实际凭据或端口。
    foreach ($taskKey in $taskValues.Keys) { [Environment]::SetEnvironmentVariable($taskKey, $taskValues[$taskKey], 'Process') }
    $taskApplicationFile = Join-Path $taskInfrastructureRoot '.env.local'
    $taskApplication = [IO.File]::ReadAllText((Join-Path $taskInfrastructureRoot '.env.example'))
    $taskApplication = $taskApplication.Replace('<运行密码>', $taskValues['EXPORT_FLOW_DB_PASSWORD'])
    $taskApplication = $taskApplication.Replace('<迁移密码>', $taskValues['EXPORT_FLOW_MIGRATOR_PASSWORD'])
    $taskApplication = $taskApplication.Replace('<MQ密码>', $taskValues['EXPORT_FLOW_RABBITMQ_PASSWORD'])
    $taskApplication = $taskApplication.Replace('127.0.0.1:3306', "127.0.0.1:$($taskValues['EXPORT_FLOW_MYSQL_PORT'])")
    $taskApplication = $taskApplication.Replace('127.0.0.1:5672', "127.0.0.1:$($taskValues['EXPORT_FLOW_RABBITMQ_PORT'])")
    $taskApplication = $taskApplication.Replace('127.0.0.1:15672', "127.0.0.1:$($taskValues['EXPORT_FLOW_RABBITMQ_MANAGEMENT_PORT'])")
    $taskApplication = $taskApplication.Replace('EXPORT_FLOW_REDIS_PORT=6379', "EXPORT_FLOW_REDIS_PORT=$($taskValues['EXPORT_FLOW_REDIS_PORT'])")
    if (-not (Test-Path -LiteralPath $taskApplicationFile)) {
        [IO.File]::WriteAllText($taskApplicationFile, $taskApplication, [Text.UTF8Encoding]::new($false))
        Write-Host '已生成 .env.local，应用连接与新基础设施一致。'
    } else {
        $taskOriginalApplication = [IO.File]::ReadAllText($taskApplicationFile)
        $taskUpdatedApplication = $taskOriginalApplication
        $taskManagedKeys = @('EXPORT_FLOW_JDBC_URL', 'EXPORT_FLOW_DB_USER', 'EXPORT_FLOW_DB_PASSWORD',
            'SPRING_FLYWAY_USER', 'SPRING_FLYWAY_PASSWORD', 'EXPORT_FLOW_AMQP_URL', 'EXPORT_FLOW_MANAGEMENT_URL',
            'EXPORT_FLOW_REDIS_HOST', 'EXPORT_FLOW_REDIS_PORT', 'EXPORT_FLOW_REDIS_USER', 'EXPORT_FLOW_REDIS_PASSWORD')
        foreach ($taskLine in ($taskApplication -split '\r?\n')) {
            if ($taskLine -notmatch '^([A-Z][A-Z0-9_]*)=(.*)$' -or $taskManagedKeys -notcontains $Matches[1]) { continue }
            $taskKey = $Matches[1]
            $taskPattern = '(?m)^' + [regex]::Escape($taskKey) + '=[^\r\n]*'
            $taskReplacement = $taskLine
            if ([regex]::IsMatch($taskUpdatedApplication, $taskPattern)) {
                $taskUpdatedApplication = [regex]::Replace($taskUpdatedApplication, $taskPattern, [Text.RegularExpressions.MatchEvaluator]{ param($taskMatch) $taskReplacement })
            } else {
                if ($taskUpdatedApplication -and -not $taskUpdatedApplication.EndsWith("`n")) { $taskUpdatedApplication += [Environment]::NewLine }
                $taskUpdatedApplication += $taskLine + [Environment]::NewLine
            }
        }
        if ($taskUpdatedApplication -cne $taskOriginalApplication) {
            $taskBackup = $taskApplicationFile + '.backup-' + [DateTime]::UtcNow.ToString('yyyyMMddTHHmmss') + '-' + [guid]::NewGuid().ToString('N')
            Copy-Item -LiteralPath $taskApplicationFile -Destination $taskBackup -ErrorAction Stop
            [IO.File]::WriteAllText($taskApplicationFile, $taskUpdatedApplication, [Text.UTF8Encoding]::new($false))
            Write-Host '已备份.env.local并同步本项目连接字段；应用端口、文件根和其他设置保留。'
        } else { Write-Host '应用连接与本项目基础设施一致，保留已有配置。' }
    }
}

function Get-InfrastructureComposeArguments {
    return @('compose', '--project-name', 'export-flow', '--project-directory', $taskInfrastructureRoot,
        '--env-file', (Join-Path $taskInfrastructureRoot '.env.infrastructure.local'),
        '--file', (Join-Path $taskInfrastructureRoot 'docker-compose.yml'))
}

function Get-InfrastructureContainers {
    # 仅查询本项目Compose的容器；绝不按宿主机端口收集其他项目的服务。
    $taskIds = @(Invoke-DockerCommand -Arguments ((Get-InfrastructureComposeArguments) + @('ps', '-a', '-q')))
    if ($taskIds.Count -eq 0) { return @() }
    $taskJson = Invoke-DockerCommand -Arguments (@('inspect') + $taskIds)
    return @((($taskJson -join [Environment]::NewLine) | ConvertFrom-Json))
}

function Wait-Infrastructure([int]$TimeoutSeconds) {
    $taskDeadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    do {
        $taskContainers = @(Get-InfrastructureContainers)
        $taskReady = $true
        foreach ($taskService in @('mysql', 'redis', 'rabbitmq')) {
            $taskMatches = @($taskContainers | Where-Object {
                $_.Config.Labels.'com.docker.compose.project' -eq 'export-flow' -and
                $_.Config.Labels.'com.docker.compose.service' -eq $taskService
            })
            if ($taskMatches.Count -ne 1) { $taskReady = $false; continue }
            $taskContainer = $taskMatches[0]
            if ($taskContainer.State.Status -notin @('running', 'restarting', 'created')) { throw "$taskService 容器未运行，请检查Docker日志。" }
            if ($taskContainer.State.Health.Status -eq 'unhealthy') { throw "$taskService 健康检查失败。" }
            if ($taskContainer.State.Status -ne 'running' -or $taskContainer.State.Health.Status -ne 'healthy') { $taskReady = $false }
        }
        if ($taskReady) { return }
        if ([DateTime]::UtcNow -ge $taskDeadline) { break }
        Start-Sleep -Milliseconds 500
    } while ($true)
    throw "基础设施健康检查超时（$TimeoutSeconds 秒）；容器和数据保留，请检查Docker日志。"
}

function Start-Infrastructure([int]$TimeoutSeconds = 180) {
    $null = Invoke-DockerCommand -Arguments @('info', '--format', '{{.ServerVersion}}')
    $null = Invoke-DockerCommand -Arguments @('compose', 'version')
    Initialize-InfrastructureConfiguration
    $null = Invoke-DockerCommand -Arguments ((Get-InfrastructureComposeArguments) + @('config', '--quiet'))
    $taskOutput = Invoke-DockerCommand -Arguments ((Get-InfrastructureComposeArguments) + @('up', '-d', '--no-recreate', 'mysql', 'redis', 'rabbitmq'))
    $taskOutput | ForEach-Object { Write-Host $_ }
    Wait-Infrastructure -TimeoutSeconds $TimeoutSeconds
    Write-Host '当前项目MySQL / Redis / RabbitMQ已初始化并健康。接下来执行make dev启动应用。'
}

if ($MyInvocation.InvocationName -ne '.') {
    if ($Help) {
        Write-Output '用法：make infra；完成后执行make dev。make infra-status只读查看本项目服务。'
        Write-Output '前置：Windows PowerShell 5.1+、GNU Make、Docker Desktop Linux引擎及Compose v2+。'
        Write-Output '首次自动生成本地凭据、应用连接配置，创建本项目容器、数据库、账号、空订单来源表和MQ vhost。'
        Write-Output '重复执行保留本项目数据和配置；不发现或复用其他项目容器。支持-TimeoutSeconds 180与--help。'
        exit 0
    }
    try {
        if ($Action -eq 'up') { Start-Infrastructure -TimeoutSeconds $TimeoutSeconds }
        elseif (-not (Test-Path -LiteralPath (Join-Path $taskInfrastructureRoot '.env.infrastructure.local'))) {
            Write-Output '尚未初始化，请先执行make infra。'
        } else {
            $taskOutput = Invoke-DockerCommand -Arguments ((Get-InfrastructureComposeArguments) + @('ps', '-a'))
            $taskOutput | ForEach-Object { Write-Output $_ }
        }
        exit 0
    } catch {
        Write-Error $_.Exception.Message -ErrorAction Continue
        exit 1
    }
}
