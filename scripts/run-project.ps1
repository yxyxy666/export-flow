param(
    [ValidateSet('check', 'install', 'build', 'backend', 'frontend')]
    [string]$Action = 'check',
    [Alias('--help')][switch]$Help
)
# Windows PowerShell 5.1+；启动职责及配置见 scripts/README.md。
# 不操作容器、账号或订单数据；只封装已批准的本地工具链命令。
$ErrorActionPreference = 'Stop'
$taskProjectRoot = Split-Path -Parent $PSScriptRoot

function Import-LocalConfiguration {
    # 读取白名单键值，不执行配置内容；本地文件优先于当前进程环境。
    $taskConfiguration = Join-Path $taskProjectRoot '.env.local'
    if (-not (Test-Path -LiteralPath $taskConfiguration)) { return }
    $taskAllowedKeys = @(
        'EXPORT_FLOW_JDBC_URL', 'EXPORT_FLOW_DB_USER', 'EXPORT_FLOW_DB_PASSWORD',
        'SPRING_FLYWAY_USER', 'SPRING_FLYWAY_PASSWORD', 'EXPORT_FLOW_AMQP_URL',
        'EXPORT_FLOW_MANAGEMENT_URL', 'EXPORT_FLOW_FILE_ROOT', 'EXPORT_FLOW_LOG_FILE',
        'EXPORT_FLOW_INSTANCE_ID', 'EXPORT_FLOW_PROGRESS_ENABLED', 'EXPORT_FLOW_BACKEND_PORT',
        'EXPORT_FLOW_REDIS_HOST', 'EXPORT_FLOW_REDIS_PORT', 'EXPORT_FLOW_REDIS_USER', 'EXPORT_FLOW_REDIS_PASSWORD'
    )
    $taskLineNumber = 0
    foreach ($taskLine in Get-Content -LiteralPath $taskConfiguration -Encoding UTF8) {
        $taskLineNumber++
        $taskText = $taskLine.Trim()
        if (-not $taskText -or $taskText.StartsWith('#')) { continue }
        if ($taskText -notmatch '^([A-Z][A-Z0-9_]*)=(.*)$') {
            throw ".env.local 第 $taskLineNumber 行格式错误；请使用 KEY=value。"
        }
        $taskKey = $Matches[1]
        $taskValue = $Matches[2]
        if ($taskAllowedKeys -notcontains $taskKey) {
            throw ".env.local 第 $taskLineNumber 行包含不支持的配置键。"
        }
        # 支持整值引号；#、=、$ 等值内字符均按原文保留。
        if ($taskValue.Length -ge 2 -and (
            ($taskValue.StartsWith('"') -and $taskValue.EndsWith('"')) -or
            ($taskValue.StartsWith("'") -and $taskValue.EndsWith("'"))
        )) { $taskValue = $taskValue.Substring(1, $taskValue.Length - 2) }
        [Environment]::SetEnvironmentVariable($taskKey, $taskValue, 'Process')
    }
}

function Assert-Command([string]$Name) {
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "缺少 $Name，请按 scripts/README.md 安装运行工具。"
    }
}

function Assert-BackendConfiguration {
    $taskBackendPort = [Environment]::GetEnvironmentVariable('EXPORT_FLOW_BACKEND_PORT', 'Process')
    if ($taskBackendPort -and ($taskBackendPort -notmatch '^\d{1,5}$' -or [int]$taskBackendPort -lt 1 -or [int]$taskBackendPort -gt 65535)) {
        throw 'EXPORT_FLOW_BACKEND_PORT 必须为1至65535的端口号。'
    }
    $taskRequiredKeys = @(
        'EXPORT_FLOW_JDBC_URL', 'EXPORT_FLOW_DB_USER', 'EXPORT_FLOW_DB_PASSWORD',
        'SPRING_FLYWAY_USER', 'SPRING_FLYWAY_PASSWORD', 'EXPORT_FLOW_AMQP_URL'
    )
    $taskMissingKeys = @($taskRequiredKeys | Where-Object {
        $taskValue = [Environment]::GetEnvironmentVariable($_, 'Process')
        [string]::IsNullOrWhiteSpace($taskValue) -or $taskValue -match '<[^>]+>'
    })
    if ($taskMissingKeys.Count -gt 0) {
        throw ('缺少有效连接配置：' + ($taskMissingKeys -join ', ') + '。复制 .env.example 为 .env.local 并填写真实值；应用库、账号和 vhost 需已准备。')
    }
}

function Invoke-CheckedCommand([string]$Command, [string[]]$Arguments) {
    & $Command @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Command 执行失败（退出码 $LASTEXITCODE）。" }
}

function Invoke-Frontend([string[]]$Arguments) {
    # 不依赖全局 pnpm 版本，始终使用 package.json 锁定的包管理器。
    Assert-Command 'node'
    $taskNpm = Find-Npm
    Invoke-CheckedCommand -Command $taskNpm -Arguments (@('exec', '--yes', '--package=pnpm@10.34.6', '--', 'pnpm', '--dir', 'frontend') + $Arguments)
}

function Find-Npm {
    # 支持标准 Node 安装的 npm.cmd 和当前 nvm 安装的 npm.exe。
    $taskNpm = Get-Command 'npm.exe', 'npm.cmd' -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $taskNpm) { throw '缺少 npm，请按 scripts/README.md 安装 Node.js（含 npm）。' }
    return $taskNpm.Source
}

function Build-Backend {
    Assert-Command 'java'
    # 启动构建不重复运行已经完成/免验的测试；正式质量门禁仍为 Wrapper verify。
    Invoke-CheckedCommand -Command (Join-Path $taskProjectRoot 'backend/mvnw.cmd') -Arguments @('-f', 'backend/pom.xml', '-Dmaven.test.skip=true', 'package')
}

if ($Help) {
    Write-Output '用法：make dev | backend | frontend | install | build | check | help'
    Write-Output 'dev 同时启动前后端；backend/frontend 可分别在两个终端启动，Ctrl+C 停止。'
    Write-Output '前置：Windows PowerShell 5.1+、GNU Make、Java21、Node22、npm，以及已准备的 MySQL/RabbitMQ 和应用连接配置。'
    Write-Output '复制 .env.example 为 .env.local 后填写真实值；也支持环境变量。不会创建或停止容器，不造数，不运行性能测试。'
    Write-Output '没有 make 时：powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/run-project.ps1 -Action backend（或 frontend）。'
    exit 0
}

try {
    Push-Location $taskProjectRoot
    Import-LocalConfiguration
    switch ($Action) {
        'check' {
            Assert-Command 'java'
            Assert-Command 'node'
            $null = Find-Npm
            Assert-BackendConfiguration
            Write-Output '运行工具和连接配置已就绪；实际依赖连接由后端启动核验。'
        }
        'install' { Invoke-Frontend -Arguments @('install', '--frozen-lockfile') }
        'build' {
            Invoke-Frontend -Arguments @('install', '--frozen-lockfile')
            Invoke-Frontend -Arguments @('build')
            Build-Backend
        }
        'backend' {
            Assert-BackendConfiguration
            Build-Backend
            # 本机 Windows 用户临时目录创建 JDK NIO pipe 会连接失败。
            # 与已核验的测试环境一致，使用构建目录完整路径；不改系统 TEMP。
            $taskSocketRoot = Join-Path $taskProjectRoot 'backend/target'
            New-Item -ItemType Directory -Path $taskSocketRoot -Force | Out-Null
            $taskBackendPort = if ($env:EXPORT_FLOW_BACKEND_PORT) { $env:EXPORT_FLOW_BACKEND_PORT } else { '8080' }
            Write-Output "后端 http://127.0.0.1:$taskBackendPort；启用真实导出消费者，Ctrl+C 停止。"
            Invoke-CheckedCommand -Command 'java' -Arguments @('-Xmx512m', "-Djdk.net.unixdomain.tmpdir=$taskSocketRoot", '-jar', 'backend/target/export-flow-0.2.0-SNAPSHOT.jar', '--export-flow.consumer-enabled=true')
        }
        'frontend' {
            Invoke-Frontend -Arguments @('install', '--frozen-lockfile')
            Write-Output '前端 http://127.0.0.1:5173/orders；Ctrl+C 停止。'
            Invoke-Frontend -Arguments @('dev')
        }
    }
    exit 0
} catch {
    Write-Error $_.Exception.Message -ErrorAction Continue
    exit 1
} finally {
    Pop-Location
}
