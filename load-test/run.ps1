param(
    [Parameter(Mandatory = $true)][string]$Manifest,
    [ValidateSet('burst', 'sustained')][string]$Mode = 'burst',
    [ValidateRange(1, 2000)][int]$Threads = 50,
    [ValidateRange(0, 120)][int]$RampSeconds = 5,
    [ValidateRange(5, 600)][int]$DurationSeconds = 60,
    [ValidateRange(0.1, 10000)][double]$TargetRps = 10,
    [switch]$ColdConnections,
    [switch]$HtmlReport
)

$ErrorActionPreference = 'Stop'
$manifestPath = (Resolve-Path -LiteralPath $Manifest).Path
$folder = Split-Path -Parent $manifestPath
$data = Get-Content -LiteralPath $manifestPath -Raw -Encoding UTF8 | ConvertFrom-Json
foreach ($field in @('voucherId', 'users', 'stock', 'baseUrl', 'preparedAt', 'beginTime', 'endTime')) {
    if ($null -eq $data.$field) { throw "Manifest is missing $field." }
}
$baseUri = [uri]$data.baseUrl
if ($baseUri.Scheme -notin @('http', 'https') -or $baseUri.AbsolutePath -notin @('', '/')) {
    throw 'baseUrl must be an HTTP(S) origin without a path.'
}
if ([long]$data.stock -lt 1) { throw 'Manifest stock must be positive.' }
if ([string]$data.voucherId -notmatch '^\d+$') { throw 'Invalid voucher ID.' }
$preparedAt = [DateTimeOffset]::Parse([string]$data.preparedAt)
$beginTime = [DateTimeOffset]::Parse([string]$data.beginTime)
$endTime = [DateTimeOffset]::Parse([string]$data.endTime)
$currentInstant = [DateTimeOffset]::Now
if ($currentInstant -gt $preparedAt.AddMinutes(25)) { throw 'Login tokens are too old. Prepare a new fixture.' }
if ($currentInstant -lt $beginTime) { throw 'Activity has not started. Wait until beginTime.' }
if ($currentInstant -ge $endTime) { throw 'Activity has ended. Prepare a new fixture.' }
if ($Mode -eq 'sustained' -and $currentInstant.AddSeconds($DurationSeconds + $RampSeconds + 30) -ge $endTime) {
    throw 'Activity will end during the sustained run. Prepare a fresh fixture.'
}
if ($null -ne $data.poolExpiresAtEpochMs) {
    $estimatedHttpSeconds = if ($Mode -eq 'sustained') { $DurationSeconds + $RampSeconds + 30 } else { $RampSeconds + 30 }
    $latestSafePoolInstant = [long]$data.poolExpiresAtEpochMs - 30000L
    if ($currentInstant.ToUnixTimeMilliseconds() + ([long]$estimatedHttpSeconds * 1000L) -ge $latestSafePoolInstant) {
        throw 'Token pool expires too soon for this run. Prepare a fresh user pool and fixture.'
    }
}

$tokenFile = Join-Path $folder 'tokens.csv'
if (-not (Test-Path -LiteralPath $tokenFile)) { throw 'tokens.csv is missing.' }
$tokenRows = @(Get-Content -LiteralPath $tokenFile -Encoding UTF8 | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
if ($tokenRows.Count -ne [int]$data.users) { throw 'Token row count does not match manifest users.' }
if (@($tokenRows | Sort-Object -Unique).Count -ne $tokenRows.Count) { throw 'Expected one distinct login token per user.' }
$expectedRequests = if ($Mode -eq 'burst') { $Threads } else { [int][math]::Ceiling($TargetRps * $DurationSeconds) }
$minimumUsers = if ($Mode -eq 'burst') { $Threads } else { $expectedRequests + $Threads }
if ($tokenRows.Count -lt $minimumUsers) {
    throw "Need at least $minimumUsers distinct users for this run; prepared $($tokenRows.Count). Sustained mode needs one new token per POST plus up to one termination row per thread."
}
# Do not print credentials or pass token values on the command line.
$tokenRows = $null

$client = [System.Net.Sockets.TcpClient]::new()
try {
    $pending = $client.BeginConnect($baseUri.Host, $baseUri.Port, $null, $null)
    if (-not $pending.AsyncWaitHandle.WaitOne(3000)) { throw 'Gateway connection timed out.' }
    $client.EndConnect($pending)
} finally {
    $client.Dispose()
}

$jmeter = (Get-Command jmeter.bat -ErrorAction Stop).Source
$jmeterBin = Split-Path -Parent $jmeter
$jmeterHome = Split-Path -Parent $jmeterBin
$jmeterJar = Join-Path $jmeterBin 'ApacheJMeter.jar'
$java = (Get-Command java.exe -ErrorAction Stop).Source
if (-not (Test-Path -LiteralPath $jmeterJar)) { throw 'Cannot locate ApacheJMeter.jar beside jmeter.bat.' }
$plan = Join-Path $PSScriptRoot "seckill-$Mode.jmx"
if (-not (Test-Path -LiteralPath $plan)) { throw "Missing plan: $plan" }
$startedMarker = Join-Path $folder '.load-test.started.json'
$completedMarker = Join-Path $folder '.load-test.completed.json'
if ((Test-Path -LiteralPath $startedMarker) -or (Test-Path -LiteralPath $completedMarker)) {
    throw 'This fixture has already been used or attempted. Prepare a new voucher; never reset its inventory to repeat a measurement.'
}
$runStamp = Get-Date -Format 'yyyyMMdd-HHmmss-fff'
$runLabel = "$Mode-$Threads-$runStamp"
$jtl = Join-Path $folder "$runLabel.jtl"
$logPath = Join-Path $folder "$runLabel.log"
$summaryPath = Join-Path $folder "$runLabel.summary.json"
$reportPath = Join-Path $folder "$runLabel-report"
$prewarm = -not $ColdConnections.IsPresent
$metadata = [ordered]@{
    mode = $Mode; threads = $Threads; rampSeconds = $RampSeconds
    configuredDurationSeconds = if ($Mode -eq 'sustained') { $DurationSeconds } else { $null }
    targetRps = if ($Mode -eq 'sustained') { $TargetRps } else { $null }
    expectedRequests = $expectedRequests; voucherId = [string]$data.voucherId
    stock = [long]$data.stock; usersPrepared = [int]$data.users
    prewarmConnections = $prewarm; preparedAt = [string]$data.preparedAt
    startedAt = [DateTimeOffset]::Now.ToString('o')
}
$markerStream = [System.IO.File]::Open($startedMarker, [System.IO.FileMode]::CreateNew, [System.IO.FileAccess]::Write, [System.IO.FileShare]::None)
try {
    $bytes = [System.Text.Encoding]::UTF8.GetBytes(($metadata | ConvertTo-Json -Depth 5))
    $markerStream.Write($bytes, 0, $bytes.Length)
} finally {
    $markerStream.Dispose()
}

$previousHeap = $env:HEAP
$previousJvmArgs = $env:JVM_ARGS
$env:HEAP = '-Xms64m -Xmx256m -XX:MaxMetaspaceSize=128m'
$env:JVM_ARGS = '-Xss256k'
$javaArgs = @(
    '-Xms64m', '-Xmx256m', '-XX:MaxMetaspaceSize=128m', '-Xss256k',
    '-Djava.awt.headless=true', '-Dfile.encoding=UTF-8',
    "-Djmeter.home=$jmeterHome", "-Djmeter.logfile=$logPath",
    "-Dlog4j.configurationFile=$(Join-Path $jmeterBin 'log4j2.xml')",
    '-jar', $jmeterJar
)
$jmeterArgs = @(
    '-n', '-d', $jmeterHome, '-t', $plan, '-l', $jtl, '-j', $logPath,
    "-Jhost=$($baseUri.Host)", "-Jport=$($baseUri.Port)", "-Jprotocol=$($baseUri.Scheme)",
    "-JvoucherId=$($data.voucherId)", "-Jthreads=$Threads", "-Jramp=$RampSeconds",
    "-JtokenFile=$($tokenFile.Replace('\', '/'))", "-Jprewarm=$($prewarm.ToString().ToLowerInvariant())",
    "-Jduration=$DurationSeconds", "-JtargetRps=$($TargetRps.ToString([Globalization.CultureInfo]::InvariantCulture))",
    "-JmaxDuration=$($DurationSeconds + $RampSeconds + 30)",
    '-Jjmeter.save.saveservice.output_format=csv',
    '-Jsampleresult.timestamp.start=true',
    '-Jsampleresult.default.encoding=UTF-8',
    '-Jsample_variables=',
    '-Jjmeter.save.saveservice.print_field_names=true',
    '-Jjmeter.save.saveservice.timestamp_format=ms',
    '-Jjmeter.save.saveservice.time=true',
    '-Jjmeter.save.saveservice.label=true',
    '-Jjmeter.save.saveservice.response_code=true',
    '-Jjmeter.save.saveservice.successful=true',
    '-Jjmeter.save.saveservice.response_data=false',
    '-Jjmeter.save.saveservice.response_data.on_error=false',
    '-Jjmeter.save.saveservice.requestHeaders=false',
    '-Jjmeter.save.saveservice.responseHeaders=false',
    '-Jjmeter.save.saveservice.samplerData=false',
    '-Jjmeter.save.saveservice.url=false',
    '-Jjmeter.save.saveservice.thread_counts=true',
    '-Jjmeter.save.saveservice.response_message=true',
    '-Jjmeter.save.saveservice.connect_time=true',
    '-Jjmeter.save.saveservice.latency=true',
    '-Jjmeter.save.saveservice.idle_time=true'
)
Write-Host "Mode=$Mode Threads=$Threads Ramp=${RampSeconds}s Prewarm=$prewarm Voucher=$($data.voucherId) Stock=$($data.stock)"
if ($Mode -eq 'sustained') { Write-Host "Target=$TargetRps POST/s Duration=${DurationSeconds}s ExpectedRequests=$expectedRequests" }
$watch = [System.Diagnostics.Stopwatch]::StartNew()
$exitCode = $null
$PSNativeCommandUseErrorActionPreference = $false
try {
    # Invoke Java directly: this installation's jmeter.bat pauses on failure.
    & $java @javaArgs @jmeterArgs
    $exitCode = $LASTEXITCODE
} finally {
    $watch.Stop()
    if ($null -eq $previousHeap) { Remove-Item Env:HEAP -ErrorAction SilentlyContinue } else { $env:HEAP = $previousHeap }
    if ($null -eq $previousJvmArgs) { Remove-Item Env:JVM_ARGS -ErrorAction SilentlyContinue } else { $env:JVM_ARGS = $previousJvmArgs }
}
$metadata.finishedAt = [DateTimeOffset]::Now.ToString('o')
$metadata.jmeterExitCode = $exitCode
$metadata.clientProcessSeconds = [math]::Round($watch.Elapsed.TotalSeconds, 3)
$metadata.rawResults = $jtl
$metadata.jmeterLog = $logPath

function Get-LatencyStats([array]$Samples) {
    if ($Samples.Count -eq 0) { return [ordered]@{ count = 0; avgMs = $null; p50Ms = $null; p95Ms = $null; p99Ms = $null; maxMs = $null } }
    $values = @($Samples | ForEach-Object { [double]$_.elapsed } | Sort-Object)
    $result = [ordered]@{ count = $values.Count; avgMs = [math]::Round(($values | Measure-Object -Average).Average, 3) }
    foreach ($pair in @(@('p50Ms', 0.50), @('p95Ms', 0.95), @('p99Ms', 0.99))) {
        $index = [math]::Max(0, [int][math]::Ceiling($values.Count * [double]$pair[1]) - 1)
        $result[$pair[0]] = $values[$index]
    }
    $result.maxMs = $values[-1]
    return $result
}

$allRows = if (Test-Path -LiteralPath $jtl) { @(Import-Csv -LiteralPath $jtl -Encoding UTF8) } else { @() }
$warmup = @($allRows | Where-Object label -eq 'prewarm')
$warmupFailures = @($warmup | Where-Object success -ne 'true')
# Control samplers are setIgnore(); only HTTP reservation outcomes belong in this window.
$labels = @('accepted', 'sold_out', 'duplicate', 'http_error', 'invalid_json', 'unexpected_response', 'POST reservation')
$rows = @($allRows | Where-Object { $_.label -in $labels })
$accepted = @($rows | Where-Object label -eq 'accepted')
$soldOut = @($rows | Where-Object label -eq 'sold_out')
$duplicate = @($rows | Where-Object label -eq 'duplicate')
$failed = @($rows | Where-Object { $_.success -ne 'true' -or $_.label -eq 'POST reservation' })
$handledCount = $accepted.Count + $soldOut.Count + $duplicate.Count
$startMs = $null; $endMs = $null; $sampleSeconds = 0.0
if ($rows.Count -gt 0) {
    $startMs = [long](($rows | ForEach-Object { [long]$_.timeStamp } | Measure-Object -Minimum).Minimum)
    $endMs = [long](($rows | ForEach-Object { [long]$_.timeStamp + [long]$_.elapsed } | Measure-Object -Maximum).Maximum)
    $sampleSeconds = [math]::Max(0.001, ($endMs - $startMs) / 1000.0)
}
$logText = if (Test-Path -LiteralPath $logPath) { Get-Content -LiteralPath $logPath -Raw -Encoding UTF8 } else { '' }
$clientScriptErrors = @([regex]::Matches([string]$logText, '(?im)^.*(?:ERROR[^\r\n]*JSR223|Problem in JSR223 script)[^\r\n]*$')).Count
$tokenExhaustedEarly = $rows.Count -lt $expectedRequests -and $logText -match '(?i)end of file|\bEOF\b'
$durationCovered = $Mode -ne 'sustained' -or $sampleSeconds -ge (0.9 * $DurationSeconds)
$prewarmValid = -not $prewarm -or ($warmup.Count -eq $Threads -and $warmupFailures.Count -eq 0)
$expectedAccepted = [math]::Min([long]$data.stock, $expectedRequests)
$measurementValid = $exitCode -eq 0 -and $rows.Count -eq $expectedRequests -and $durationCovered -and $prewarmValid -and -not $tokenExhaustedEarly -and $clientScriptErrors -eq 0
$passed = $measurementValid -and $failed.Count -eq 0 -and $duplicate.Count -eq 0 -and $accepted.Count -eq $expectedAccepted -and $soldOut.Count -eq ($expectedRequests - $expectedAccepted)

$summary = [ordered]@{}
foreach ($key in $metadata.Keys) { $summary[$key] = $metadata[$key] }
$summary.metricScope = if ($Mode -eq 'burst') { 'single burst HTTP reservation responses; not sustained capacity or order completion' } else { 'globally paced HTTP reservation responses; order completion is measured separately by monitor' }
$summary.actualRequests = $rows.Count
$summary.businessResponses = $handledCount
$summary.accepted = $accepted.Count
$summary.soldOut = $soldOut.Count
$summary.duplicate = $duplicate.Count
$summary.failures = $failed.Count
$summary.failureRatePercent = if ($rows.Count) { [math]::Round(100.0 * $failed.Count / $rows.Count, 3) } else { $null }
$summary.sampleStartUnixMs = $startMs
$summary.sampleEndUnixMs = $endMs
$summary.actualPostWindowSeconds = [math]::Round($sampleSeconds, 3)
$summary.sampleStartUtc = if ($null -ne $startMs) { [DateTimeOffset]::FromUnixTimeMilliseconds($startMs).ToString('o') } else { $null }
$summary.sampleEndUtc = if ($null -ne $endMs) { [DateTimeOffset]::FromUnixTimeMilliseconds($endMs).ToString('o') } else { $null }
$summary.attemptedRps = if ($sampleSeconds) { [math]::Round($rows.Count / $sampleSeconds, 3) } else { $null }
$summary.businessResponseRps = if ($sampleSeconds) { [math]::Round($handledCount / $sampleSeconds, 3) } else { $null }
$summary.acceptedRps = if ($sampleSeconds) { [math]::Round($accepted.Count / $sampleSeconds, 3) } else { $null }
$summary.allLatency = Get-LatencyStats $rows
$summary.acceptedLatency = Get-LatencyStats $accepted
$summary.prewarmRequests = $warmup.Count
$summary.prewarmFailures = $warmupFailures.Count
$summary.prewarmValid = $prewarmValid
$summary.durationCovered = $durationCovered
$summary.tokenExhaustedEarly = [bool]$tokenExhaustedEarly
$summary.clientScriptErrors = $clientScriptErrors
$summary.measurementValid = [bool]$measurementValid
$summary.expectedBusinessCountsPassed = [bool]$passed
$summary.outcomeCounts = @($rows | Group-Object label | ForEach-Object { [ordered]@{ label = $_.Name; count = $_.Count } })
$summary.responseCodeCounts = @($rows | Group-Object responseCode | ForEach-Object { [ordered]@{ code = $_.Name; count = $_.Count } })
$summary.failureExamples = @($failed | Select-Object -First 10 label, responseCode, responseMessage, elapsed)
$summary.summaryPath = $summaryPath
$summary | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $summaryPath -Encoding UTF8
@{ finishedAt = $metadata.finishedAt; summary = $summaryPath; measurementValid = [bool]$measurementValid } | ConvertTo-Json | Set-Content -LiteralPath $completedMarker -Encoding UTF8

Write-Host ''
Write-Host "POST requests=$($rows.Count) Accepted=$($accepted.Count) SoldOut=$($soldOut.Count) Duplicate=$($duplicate.Count) Failures=$($failed.Count)"
Write-Host "POST window=$($summary.actualPostWindowSeconds)s AttemptedRPS=$($summary.attemptedRps) BusinessRPS=$($summary.businessResponseRps) AcceptedRPS=$($summary.acceptedRps)"
Write-Host "Latency Avg=$($summary.allLatency.avgMs)ms P50=$($summary.allLatency.p50Ms)ms P95=$($summary.allLatency.p95Ms)ms P99=$($summary.allLatency.p99Ms)ms"
Write-Host "Prewarm=$($warmup.Count) failures=$($warmupFailures.Count) (excluded from POST metrics)"
Write-Host "MeasurementValid=$measurementValid ExpectedBusinessCountsPassed=$passed TokenExhaustedEarly=$tokenExhaustedEarly ClientScriptErrors=$clientScriptErrors"
Write-Host "Summary: $summaryPath"
Write-Host "Raw JTL: $jtl"
Write-Host "JMeter log: $logPath"
if ($Mode -eq 'burst') { Write-Host 'RPS above describes one short burst, not a sustainable per-second capacity.' }
if (-not $durationCovered) { Write-Warning 'Sustained POST window did not cover 90% of requested duration. Do not report this as a sustained run.' }
if ($clientScriptErrors -gt 0) { Write-Warning 'JMeter JSR223 errors invalidate this measurement. Inspect the JMeter log.' }
if (-not $measurementValid) { Write-Warning 'Measurement is incomplete or setup validation failed; inspect summary and log.' }
if ($measurementValid -and -not $passed) { Write-Warning 'The load measurement completed, but business counts or HTTP errors did not meet expectations.' }

if ($HtmlReport -and $rows.Count -gt 0) {
    $htmlHeap = $env:HEAP
    $htmlJvm = $env:JVM_ARGS
    try {
        $env:HEAP = '-Xms64m -Xmx256m -XX:MaxMetaspaceSize=128m'
        $env:JVM_ARGS = '-Xss256k'
        & $java @javaArgs -d $jmeterHome -g $jtl -o $reportPath
        if ($LASTEXITCODE -ne 0) { Write-Warning 'HTML report generation failed; raw JTL and JSON summary are preserved.' }
        else { Write-Host "HTML report: $reportPath (includes prewarm samples; use JSON summary for POST-only metrics)" }
    } finally {
        if ($null -eq $htmlHeap) { Remove-Item Env:HEAP -ErrorAction SilentlyContinue } else { $env:HEAP = $htmlHeap }
        if ($null -eq $htmlJvm) { Remove-Item Env:JVM_ARGS -ErrorAction SilentlyContinue } else { $env:JVM_ARGS = $htmlJvm }
    }
}
if (-not $measurementValid) { throw "Invalid or incomplete load run. Details: $summaryPath" }
