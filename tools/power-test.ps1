<#
.SYNOPSIS
    A/B 播放功耗与 Offload 验证自动化抓取脚本
.DESCRIPTION
    用于对比 Bilibili 官方客户端与 Neri Classical 低功耗定制版在熄屏长音频播放时的电量、CPU、WakeLock 与 Audio Offload 状态。
#>

[CmdletBinding()]
param(
    [string]$PackageName = "moe.ouom.neriplayer.classical",
    [string]$Label = "neri-classical",
    [switch]$NoResetStats
)

$ErrorActionPreference = "Stop"

# 1. 查找可用 adb
$adb = "adb"
if (Get-Command adb -ErrorAction SilentlyContinue) {
    $adb = (Get-Command adb).Source
} elseif (Test-Path "D:\Android\Sdk\platform-tools\adb.exe") {
    $adb = "D:\Android\Sdk\platform-tools\adb.exe"
}

Write-Host "==> 使用 adb: $adb" -ForegroundColor Cyan

# 2. 检查设备连接
$devices = & $adb devices | Select-String -Pattern "\bdevice$"
if (-not $devices) {
    Write-Error "未检测到已连接的 Android 设备！请确保已启用 USB 调试并已授权电脑。"
    exit 1
}
Write-Host "==> 已检测到设备: $($devices[0].Line)" -ForegroundColor Green

function Get-BatteryLevel {
    $dump = & $adb shell dumpsys battery
    $levelLine = $dump | Select-String -Pattern "^\s*level:\s*(\d+)"
    $statusLine = $dump | Select-String -Pattern "^\s*status:\s*(\d+)"
    $scaleLine = $dump | Select-String -Pattern "^\s*scale:\s*(\d+)"
    $level = if ($levelLine) { [int]$levelLine.Matches[0].Groups[1].Value } else { -1 }
    $scale = if ($scaleLine) { [int]$scaleLine.Matches[0].Groups[1].Value } else { 100 }
    $status = if ($statusLine) { [int]$statusLine.Matches[0].Groups[1].Value } else { -1 }
    return @{ Level = $level; Scale = $scale; Status = $status; Raw = ($dump -join "`n") }
}

$startBat = Get-BatteryLevel
$startTime = Get-Date
$timestamp = $startTime.ToString("yyyyMMdd-HHmmss")
$outputDir = Join-Path $PSScriptRoot "..\power-results\$timestamp-$Label"

New-Item -ItemType Directory -Path $outputDir -Force | Out-Null

Write-Host "==================================================" -ForegroundColor Yellow
Write-Host "  Neri Classical A/B 功耗测试" -ForegroundColor Yellow
Write-Host "  测试标签: $Label" -ForegroundColor Yellow
Write-Host "  测试目标应用: $PackageName" -ForegroundColor Yellow
Write-Host "  开始时间: $($startTime.ToString('yyyy-MM-dd HH:mm:ss'))" -ForegroundColor Yellow
Write-Host "  当前电量: $($startBat.Level)%" -ForegroundColor Yellow
Write-Host "  输出目录: $outputDir" -ForegroundColor Yellow
Write-Host "==================================================" -ForegroundColor Yellow

if (-not $NoResetStats) {
    Write-Host "--> 重置 batterystats 与 logcat 缓冲区..." -ForegroundColor Cyan
    & $adb shell dumpsys batterystats --reset | Out-Null
    & $adb logcat -c | Out-Null
    Write-Host "--> batterystats 已重置。" -ForegroundColor Green
}

# 记录开始时系统电池快照
Set-Content -LiteralPath (Join-Path $outputDir "battery-start.txt") -Value $startBat.Raw -Encoding UTF8

$meta = @{
    Label = $Label
    PackageName = $PackageName
    StartTimeUtc = $startTime.ToUniversalTime().ToString("o")
    StartBatteryPercent = $startBat.Level
}
$meta | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $outputDir "test-meta.json") -Encoding UTF8

Write-Host "`n>>> 请现在开始在手机上操作播放（如 Bilibili 合集 / 古典乐列表），戴上蓝牙耳机，音量调至常规试听水平，并【熄屏】。" -ForegroundColor Magenta
Write-Host ">>> 建议保持稳定运行 60 分钟，期间手机不进行其他交互。" -ForegroundColor Magenta
Write-Host ">>> 测试结束时，请按 [Enter] 键抓取诊断数据并生成报告...`n" -ForegroundColor Yellow

[Console]::ReadLine() | Out-Null

# 结束测试
$endTime = Get-Date
$duration = $endTime - $startTime
$endBat = Get-BatteryLevel
$dropPercent = $startBat.Level - $endBat.Level

Write-Host "--> 正在抓取结束时电池与系统统计..." -ForegroundColor Cyan
Set-Content -LiteralPath (Join-Path $outputDir "battery-end.txt") -Value $endBat.Raw -Encoding UTF8

Write-Host "--> 正在导出 dumpsys batterystats..." -ForegroundColor Cyan
& $adb shell dumpsys batterystats | Out-File -FilePath (Join-Path $outputDir "dumpsys-batterystats.txt") -Encoding UTF8

Write-Host "--> 正在导出目标应用详细电池与唤醒锁数据..." -ForegroundColor Cyan
& $adb shell dumpsys batterystats $PackageName | Out-File -FilePath (Join-Path $outputDir "dumpsys-package-batterystats.txt") -Encoding UTF8

Write-Host "--> 正在抓取 CPU 使用率..." -ForegroundColor Cyan
& $adb shell dumpsys cpuinfo | Out-File -FilePath (Join-Path $outputDir "dumpsys-cpuinfo.txt") -Encoding UTF8

Write-Host "--> 正在抓取 NeriPower Logcat 日志..." -ForegroundColor Cyan
& $adb logcat -d -s NeriPower:I | Out-File -FilePath (Join-Path $outputDir "logcat-neripower.txt") -Encoding UTF8

# 汇总报告
$summary = @"
==================================================
Neri Classical 功耗测试报告
==================================================
测试标签: $Label
目标应用: $PackageName
开始时间: $($startTime.ToString('yyyy-MM-dd HH:mm:ss'))
结束时间: $($endTime.ToString('yyyy-MM-dd HH:mm:ss'))
测试时长: $($duration.TotalMinutes.ToString('0.00')) 分钟 ($($duration.TotalHours.ToString('0.00')) 小时)

电量变化:
  初始电量: $($startBat.Level)%
  结束电量: $($endBat.Level)%
  总耗电量: $dropPercent%
  折合每小时耗电: $(if ($duration.TotalHours -gt 0) { ($dropPercent / $duration.TotalHours).ToString('0.00') } else { "N/A" }) %/h

输出文件清单:
  - test-meta.json: 测试元数据
  - battery-start.txt: 初始电池状态
  - battery-end.txt: 结束电池状态
  - dumpsys-batterystats.txt: 全系统 batterystats
  - dumpsys-package-batterystats.txt: 目标应用 CPU/Wakelock
  - dumpsys-cpuinfo.txt: CPU 使用率采样
  - logcat-neripower.txt: NeriPower 状态与 Offload 事件

==================================================
"@

Write-Host $summary -ForegroundColor Green
Set-Content -LiteralPath (Join-Path $outputDir "summary.txt") -Value $summary -Encoding UTF8
Write-Host "--> 报告与日志已保存在: $outputDir" -ForegroundColor Cyan
