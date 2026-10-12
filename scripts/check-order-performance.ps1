param([Alias('--help')][switch]$Help)
# Java21、Maven Wrapper、Docker Desktop和既有project-export-flow健康服务为前置条件。
$ErrorActionPreference = 'Stop'
if ($Help) {
    Write-Output '用法：check-order-performance.ps1。复用project-export-flow并隔离schema/vhost/账号，JVM512MiB，API预热/200样本、可用规模各5次Excel串行度量，直接读取exportflow.orders。'
    Write-Output '执行期间请停止其他压力测试；证据保存在artifacts/002-order-management，业务或环境失败退出非零；200000规模数据不足只记录前置条件缺口，不造数、不计业务失败。'
    exit 0
}
try {
    $taskProject = Split-Path -Parent $PSScriptRoot
    $taskBackend = Join-Path $taskProject 'backend'
    & (Join-Path $taskBackend 'mvnw.cmd') -f (Join-Path $taskBackend 'pom.xml') '-Dexportflow.performance=true' '-Dit.test=ApiPerformanceTest,ExcelPerformanceTest' '-DforkCount=1' '-Dtest.jvmArgs=-Xms512m -Xmx512m' test-compile failsafe:integration-test failsafe:verify
    exit $LASTEXITCODE
} catch {
    Write-Error '性能验证未完成，请检查Java、Wrapper与project-export-flow服务运行环境。' -ErrorAction Continue
    exit 1
}
