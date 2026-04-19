param(
	[string]$ApiUrl = "http://localhost:8080",
	[string]$FrontendUrl = "http://localhost:3000",
	[int]$UsersCount = 30,
	[string]$ProbePath = "/api/public/ping",
	[int]$TimeoutSec = 6,
	[string]$AdminToken = ""
)

$ErrorActionPreference = "Continue"
$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$results = New-Object System.Collections.Generic.List[object]

function Add-Result {
	param(
		[string]$Name,
		[string]$Status,
		[string]$Details
	)
	$results.Add([PSCustomObject]@{ Name = $Name; Status = $Status; Details = $Details })
}

function Write-StatusLine {
	param(
		[string]$Name,
		[string]$Status,
		[string]$Details
	)
	$color = switch ($Status) {
		"PASS" { "Green" }
		"WARN" { "Yellow" }
		"FAIL" { "Red" }
		default { "Gray" }
	}
	Write-Host ("{0,-34} {1,-5} {2}" -f $Name, $Status, $Details) -ForegroundColor $color
	Add-Result -Name $Name -Status $Status -Details $Details
}

function Test-Endpoint {
	param(
		[string]$Name,
		[string]$Url,
		[int]$Timeout = 5,
		[hashtable]$Headers = @{},
		[int[]]$WarnStatusCodes = @()
	)

	try {
		$response = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec $Timeout -Headers $Headers -ErrorAction Stop
		$details = "HTTP $($response.StatusCode)"
		if ($response.Content -and $response.Content.Length -gt 0) {
			$contentText = if ($response.Content -is [string]) {
				$response.Content
			} else {
				[System.Text.Encoding]::UTF8.GetString([byte[]]$response.Content)
			}
			$snippet = $contentText.Substring(0, [Math]::Min($contentText.Length, 120)).Replace("`r", " ").Replace("`n", " ")
			$details = "$details; body=$snippet"
		}
		Write-StatusLine -Name $Name -Status "PASS" -Details $details
	}
	catch {
		$message = $_.Exception.Message
		if ($_.Exception.Response -and $_.Exception.Response.StatusCode) {
			$code = [int]$_.Exception.Response.StatusCode
			if ($WarnStatusCodes -contains $code) {
				Write-StatusLine -Name $Name -Status "WARN" -Details "HTTP $code; endpoint reachable but not authorized"
				return
			}
			Write-StatusLine -Name $Name -Status "FAIL" -Details "HTTP $code; $message"
			return
		}
		Write-StatusLine -Name $Name -Status "FAIL" -Details $message
	}
}

function Test-ComposeStack {
	param(
		[string]$StackName,
		[string]$ComposePath
	)

	if (-not (Test-Path -LiteralPath $ComposePath)) {
		Write-StatusLine -Name "$StackName compose" -Status "WARN" -Details "File not found: $ComposePath"
		return
	}

	try {
		$output = & docker compose -f $ComposePath ps --format json 2>$null
		if (-not $output) {
			Write-StatusLine -Name "$StackName compose" -Status "WARN" -Details "No services in compose output"
			return
		}

		$rows = @()
		foreach ($line in $output) {
			if (-not [string]::IsNullOrWhiteSpace($line)) {
				$rows += ($line | ConvertFrom-Json)
			}
		}

		$running = @($rows | Where-Object { $_.State -eq "running" }).Count
		$total = @($rows).Count
		$status = if ($running -eq $total -and $total -gt 0) { "PASS" } else { "WARN" }
		Write-StatusLine -Name "$StackName compose" -Status $status -Details "running=$running total=$total"
	}
	catch {
		Write-StatusLine -Name "$StackName compose" -Status "FAIL" -Details $_.Exception.Message
	}
}

Write-Host "`n====================================" -ForegroundColor Cyan
Write-Host "TRAJECTA HOST DIAGNOSTICS" -ForegroundColor Cyan
Write-Host "====================================" -ForegroundColor Cyan
Write-Host "API URL      : $ApiUrl"
Write-Host "Frontend URL : $FrontendUrl"
Write-Host "Users probe  : $UsersCount"
Write-Host "Probe path   : $ProbePath"

Write-Host "`n[1/4] Host and containers" -ForegroundColor Yellow
if (Get-Command docker -ErrorAction SilentlyContinue) {
	try {
		$null = & docker info 2>$null
		if ($LASTEXITCODE -eq 0) {
			Write-StatusLine -Name "Docker daemon" -Status "PASS" -Details "Reachable"
		} else {
			Write-StatusLine -Name "Docker daemon" -Status "FAIL" -Details "docker info returned code $LASTEXITCODE"
		}
	}
	catch {
		Write-StatusLine -Name "Docker daemon" -Status "FAIL" -Details $_.Exception.Message
	}
} else {
	Write-StatusLine -Name "Docker CLI" -Status "FAIL" -Details "docker command not found"
}

Test-ComposeStack -StackName "API" -ComposePath (Join-Path $scriptRoot "Trajecta-api\compose.yaml")
Test-ComposeStack -StackName "Worker" -ComposePath (Join-Path $scriptRoot "Trajecta-worker\docker-compose.yml")
Test-ComposeStack -StackName "Frontend" -ComposePath (Join-Path $scriptRoot "Trajecta-frontend\docker-compose.yml")

Write-Host "`n[2/4] HTTP endpoints" -ForegroundColor Yellow
Test-Endpoint -Name "Frontend root" -Url $FrontendUrl -Timeout $TimeoutSec
Test-Endpoint -Name "API health" -Url "$ApiUrl/actuator/health" -Timeout $TimeoutSec -WarnStatusCodes @(401, 403)
Test-Endpoint -Name "API health db" -Url "$ApiUrl/actuator/health/db" -Timeout $TimeoutSec -WarnStatusCodes @(401, 403, 404)
Test-Endpoint -Name "API liveness" -Url "$ApiUrl/actuator/health/livenessState" -Timeout $TimeoutSec -WarnStatusCodes @(401, 403, 404)
Test-Endpoint -Name "API db metrics" -Url "$ApiUrl/actuator/metrics/db.connection.pool" -Timeout $TimeoutSec -WarnStatusCodes @(401, 403)
Test-Endpoint -Name "Public probe endpoint" -Url "$ApiUrl$ProbePath" -Timeout $TimeoutSec -WarnStatusCodes @(401, 403)

if (-not [string]::IsNullOrWhiteSpace($AdminToken)) {
	Test-Endpoint -Name "Admin service health" -Url "$ApiUrl/api/v1/admin/cache/service-health" -Timeout $TimeoutSec -Headers @{ Authorization = "Bearer $AdminToken" }
} else {
	Write-StatusLine -Name "Admin service health" -Status "WARN" -Details "Skipped (set -AdminToken to enable)"
}

Write-Host "`n[3/4] Concurrent API probe" -ForegroundColor Yellow
$jobs = @()
$times = @()
$codes = @{}
$ok = 0
$failed = 0

for ($i = 1; $i -le $UsersCount; $i++) {
	$jobs += Start-Job -ScriptBlock {
		param($url, $path)
		try {
			$timer = [System.Diagnostics.Stopwatch]::StartNew()
			$response = Invoke-WebRequest -Uri "$url$path" -UseBasicParsing -TimeoutSec 10 -ErrorAction Stop
			$timer.Stop()
			return @{ code = [int]$response.StatusCode; time = $timer.ElapsedMilliseconds }
		}
		catch {
			if ($_.Exception.Response -and $_.Exception.Response.StatusCode) {
				return @{ code = [int]$_.Exception.Response.StatusCode; time = 0 }
			}
			return @{ code = 0; time = 0 }
		}
	} -ArgumentList $ApiUrl, $ProbePath
}

foreach ($job in $jobs) {
	$r = Receive-Job -Job $job -Wait
	$code = [int]$r.code
	if (-not $codes.ContainsKey($code)) {
		$codes[$code] = 0
	}
	$codes[$code]++

	if (($code -ge 200 -and $code -lt 400)) {
		$ok++
		if ($r.time -gt 0) {
			$times += [double]$r.time
		}
	} else {
		$failed++
	}
}

$jobs | Remove-Job -Force

$avg = if ($times.Count -gt 0) { [math]::Round(($times | Measure-Object -Average).Average, 1) } else { 0 }
$max = if ($times.Count -gt 0) { [int](($times | Measure-Object -Maximum).Maximum) } else { 0 }
$codesSummary = (($codes.Keys | Sort-Object) | ForEach-Object { "$_=$($codes[$_])" }) -join ", "

if ($failed -eq 0) {
	Write-StatusLine -Name "Concurrent probe" -Status "PASS" -Details "path=$ProbePath ok=$ok failed=$failed avg=${avg}ms max=${max}ms codes=[$codesSummary]"
} elseif ($ok -gt 0) {
	Write-StatusLine -Name "Concurrent probe" -Status "WARN" -Details "path=$ProbePath ok=$ok failed=$failed avg=${avg}ms max=${max}ms codes=[$codesSummary]"
} else {
	Write-StatusLine -Name "Concurrent probe" -Status "FAIL" -Details "path=$ProbePath ok=$ok failed=$failed codes=[$codesSummary]"
}

$probeStatus = if ($failed -eq 0) { "PASS" } elseif ($ok -gt 0) { "WARN" } else { "FAIL" }

Write-Host "`n[4/4] Summary" -ForegroundColor Yellow
$passCount = @($results | Where-Object { $_.Status -eq "PASS" }).Count
$warnCount = @($results | Where-Object { $_.Status -eq "WARN" }).Count
$failCount = @($results | Where-Object { $_.Status -eq "FAIL" }).Count

Write-Host "PASS: $passCount" -ForegroundColor Green
Write-Host "WARN: $warnCount" -ForegroundColor Yellow
Write-Host "FAIL: $failCount" -ForegroundColor Red

if ($failCount -eq 0 -and $warnCount -eq 0) {
	Write-Host "System status: HEALTHY" -ForegroundColor Green
} elseif ($failCount -eq 0) {
	Write-Host "System status: PARTIAL (warnings present)" -ForegroundColor Yellow
} else {
	Write-Host "System status: UNHEALTHY (failures detected)" -ForegroundColor Red
}

$ready = $failCount -eq 0 -and $probeStatus -ne "FAIL"
if ($ready) {
	Write-Host "Readiness verdict: READY for approx $UsersCount concurrent probe users" -ForegroundColor Green
} else {
	Write-Host "Readiness verdict: NOT READY for $UsersCount concurrent probe users" -ForegroundColor Red
}

Write-Host "`nDone.`n" -ForegroundColor Cyan

if (-not $ready) {
	exit 1
}
