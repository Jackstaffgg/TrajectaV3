param(
    [Parameter(Position = 0)]
    [string[]]$Targets = @("all"),

    [string]$ProjectName = "trajecta",
    [string]$ComposeFile = "docker-compose.vps.yml",
    [string]$EnvFile = ".env.vps",

    [switch]$Down,
    [switch]$Yes,
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"

$targetMap = [ordered]@{
    db       = @("pgdata")
    redis    = @("redis_data")
    rabbitmq = @("rabbitmq_data")
    minio    = @("minio_data")
    edge     = @("caddy_data", "caddy_config")
}

function Resolve-VolumeKeys {
    param([string[]]$RawTargets)

    if (-not $RawTargets -or $RawTargets.Count -eq 0) {
        return @($targetMap.Values | ForEach-Object { $_ } | Select-Object -Unique)
    }

    $normalized = $RawTargets | ForEach-Object { $_.ToLowerInvariant().Trim() } | Where-Object { $_ -ne "" }
    if ($normalized -contains "all") {
        return @($targetMap.Values | ForEach-Object { $_ } | Select-Object -Unique)
    }

    $unknown = @($normalized | Where-Object { -not $targetMap.Contains($_) } | Select-Object -Unique)
    if ($unknown.Count -gt 0) {
        throw "Unknown target(s): $($unknown -join ', '). Allowed: all, $($targetMap.Keys -join ', ')"
    }

    $resolved = New-Object System.Collections.Generic.List[string]
    foreach ($target in $normalized) {
        foreach ($volumeKey in $targetMap[$target]) {
            if (-not $resolved.Contains($volumeKey)) {
                $resolved.Add($volumeKey)
            }
        }
    }

    return $resolved.ToArray()
}

function Get-ComposeArgs {
    $args = @("-f", $ComposeFile)
    if (Test-Path -LiteralPath $EnvFile) {
        $args = @("--env-file", $EnvFile) + $args
    }
    return $args
}

function Get-ExistingVolumeNames {
    param(
        [string]$ComposeProject,
        [string[]]$VolumeKeys
    )

    $names = New-Object System.Collections.Generic.List[string]

    foreach ($volumeKey in $VolumeKeys) {
        $result = & docker volume ls `
            --filter "label=com.docker.compose.project=$ComposeProject" `
            --filter "label=com.docker.compose.volume=$volumeKey" `
            --format "{{.Name}}"

        if ($LASTEXITCODE -ne 0) {
            throw "Failed to list docker volumes for key '$volumeKey'."
        }

        foreach ($name in $result) {
            if (-not [string]::IsNullOrWhiteSpace($name) -and -not $names.Contains($name)) {
                $names.Add($name)
            }
        }
    }

    return $names.ToArray()
}

$volumeKeys = Resolve-VolumeKeys -RawTargets $Targets

Write-Host "Selected groups : $($Targets -join ', ')"
Write-Host "Project name    : $ProjectName"
Write-Host "Volume keys     : $($volumeKeys -join ', ')"

$dockerCommand = Get-Command docker -ErrorAction SilentlyContinue
if (-not $dockerCommand -and -not $DryRun) {
    throw "Docker CLI is not available. Install Docker or run with -DryRun for plan output."
}

if ($DryRun -and -not $dockerCommand) {
    $predicted = $volumeKeys | ForEach-Object { "${ProjectName}_$_" }
    Write-Host "[DryRun] Docker is unavailable, predicted volume names: $($predicted -join ', ')"
    exit 0
}

$volumeNames = Get-ExistingVolumeNames -ComposeProject $ProjectName -VolumeKeys $volumeKeys

if ($volumeNames.Count -eq 0) {
    Write-Host "No matching volumes found for project '$ProjectName'. Nothing to reset."
    exit 0
}

Write-Host "Matching volumes: $($volumeNames -join ', ')"

if (-not $Yes) {
    Write-Host "This action will permanently remove data from the volumes above." -ForegroundColor Yellow
    Write-Host "Re-run with -Yes to confirm."
    exit 1
}

if ($DryRun) {
    Write-Host "[DryRun] Would remove volumes: $($volumeNames -join ', ')"
    if ($Down) {
        Write-Host "[DryRun] Would also run: docker compose $(Get-ComposeArgs -join ' ') down --remove-orphans"
    }
    exit 0
}

if ($Down) {
    $composeArgs = Get-ComposeArgs
    Write-Host "Stopping VPS stack before volume removal..."
    & docker compose @composeArgs down --remove-orphans
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose down failed"
    }
}

Write-Host "Removing volumes..."
& docker volume rm $volumeNames
if ($LASTEXITCODE -ne 0) {
    throw "docker volume rm failed. Volumes may still be in use; try running with -Down."
}

Write-Host "Done. Removed volumes: $($volumeNames -join ', ')" -ForegroundColor Green
Write-Host "Tip: start stack again with deploy script or docker compose up -d."

