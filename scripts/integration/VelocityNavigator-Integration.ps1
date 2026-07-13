[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [ValidateSet('Setup', 'Start', 'Status', 'Stop', 'RestartBackend', 'Command')]
    [string] $Action,
    [switch] $AcceptMinecraftEula,
    [switch] $Rebuild,
    [ValidateSet('proxy', 'backend')]
    [string] $Target,
    [string] $ConsoleCommand
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$projectRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$environmentRoot = Join-Path $projectRoot 'test_env'
$proxyDirectory = Join-Path $environmentRoot 'proxy'
$backendDirectory = Join-Path $environmentRoot 'backend'
$runner = Join-Path $PSScriptRoot 'Run-MinecraftProcess.ps1'

# These are intentionally pinned so a failed smoke test is reproducible.
# Paper 1.21.11 is the newest supported Paper line for Java 21; Paper 26.1+
# requires Java 25. See scripts/integration/README.md for the official sources.
$paper = [ordered]@{
    Version = '1.21.11'
    Url = 'https://fill-data.papermc.io/v1/objects/5ffef465eeeb5f2a3c23a24419d97c51afd7dbb4923ff42df9a3f58bba1ccfba/paper-1.21.11-132.jar'
    Sha256 = '5ffef465eeeb5f2a3c23a24419d97c51afd7dbb4923ff42df9a3f58bba1ccfba'
}
$velocity = [ordered]@{
    Version = '3.5.1'
    Url = 'https://fill-data.papermc.io/v1/objects/b4e3164df5377346854dc6cb9e6a78022b1946ff69e89676313f5f6f1c6f0fb3/velocity-3.5.1-615.jar'
    Sha256 = 'b4e3164df5377346854dc6cb9e6a78022b1946ff69e89676313f5f6f1c6f0fb3'
}
$userAgent = 'VelocityNavigator-integration-harness/1.0 (https://github.com/DemonZ-Development/VelocityNavigator)'

function Write-Status([string] $Message) {
    Write-Host "[VelocityNavigator integration] $Message"
}

function Set-Utf8NoBom([string] $Path, [string] $Value) {
    [System.IO.File]::WriteAllText($Path, $Value, [System.Text.UTF8Encoding]::new($false))
}

function Get-JavaPath {
    $command = Get-Command java -ErrorAction Stop
    $previousErrorActionPreference = $ErrorActionPreference
    try {
        # Java writes its version banner to stderr even when it exits successfully.
        $ErrorActionPreference = 'Continue'
        $versionOutput = & $command.Source -version 2>&1 | Out-String
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
    if ($versionOutput -notmatch '(?:version\s+"|openjdk\s+)(\d+)') {
        throw "Could not determine the Java version from: $versionOutput"
    }
    $major = [int] $Matches[1]
    if ($major -lt 21) {
        throw "This integration stack requires Java 21 or newer; found Java $major."
    }
    return $command.Source
}

function Assert-Hash([string] $Path, [string] $ExpectedHash) {
    if (-not (Test-Path -LiteralPath $Path)) { return $false }
    return ((Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash -eq $ExpectedHash)
}

function Ensure-Download([System.Collections.IDictionary] $Artifact, [string] $Destination) {
    if (Assert-Hash $Destination $Artifact.Sha256) {
        Write-Status "Using verified $(Split-Path -Leaf $Destination)."
        return
    }
    if (Test-Path -LiteralPath $Destination) {
        throw "Checksum mismatch for $Destination. Delete it manually before downloading again."
    }
    Write-Status "Downloading $(Split-Path -Leaf $Destination)."
    Invoke-WebRequest -Uri $Artifact.Url -Headers @{ 'User-Agent' = $userAgent } -OutFile $Destination
    if (-not (Assert-Hash $Destination $Artifact.Sha256)) {
        Remove-Item -LiteralPath $Destination -Force
        throw "Checksum verification failed for $(Split-Path -Leaf $Destination)."
    }
}

function Get-ProjectVersion {
    [xml] $pom = Get-Content -LiteralPath (Join-Path $projectRoot 'pom.xml')
    return [string] $pom.project.version
}

function Ensure-PluginJar {
    $version = Get-ProjectVersion
    $jar = Join-Path (Join-Path $projectRoot 'target') "VelocityNavigator-$version.jar"
    if ($Rebuild -or -not (Test-Path -LiteralPath $jar)) {
        Write-Status 'Building the release JAR with Maven.'
        Push-Location $projectRoot
        try {
            & mvn package --batch-mode --no-transfer-progress | Out-Host
            if ($LASTEXITCODE -ne 0) { throw 'Maven package failed.' }
        } finally {
            Pop-Location
        }
    }
    if (-not (Test-Path -LiteralPath $jar)) { throw "Expected build output was not found: $jar" }
    return $jar
}

function New-SharedSecret {
    $secretFile = Join-Path $proxyDirectory 'forwarding.secret'
    if (Test-Path -LiteralPath $secretFile) {
        return (Get-Content -LiteralPath $secretFile -Raw).Trim()
    }
    $bytes = [byte[]]::new(32)
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $rng.GetBytes($bytes)
    } finally {
        $rng.Dispose()
    }
    $secret = [Convert]::ToBase64String($bytes)
    Set-Content -LiteralPath $secretFile -Value $secret -NoNewline -Encoding ascii
    return $secret
}

function Write-ProxyConfig {
    $config = @'
config-version = "2.7"
bind = "127.0.0.1:25565"
motd = "<gold>VelocityNavigator integration</gold>"
show-max-players = 20
online-mode = false
force-key-authentication = false
prevent-client-proxy-connections = false
player-info-forwarding-mode = "modern"
forwarding-secret-file = "forwarding.secret"
announce-forge = false
kick-existing-players = false
ping-passthrough = "DESCRIPTION"
enable-player-address-logging = false

[servers]
lobby = "127.0.0.1:25566"
try = ["lobby"]

[forced-hosts]
"lobby.local" = ["lobby"]
'@
    Set-Utf8NoBom (Join-Path $proxyDirectory 'velocity.toml') $config
}

function Write-BackendProperties {
    $properties = @'
server-ip=127.0.0.1
server-port=25566
online-mode=false
enforce-secure-profile=false
motd=VelocityNavigator integration backend
enable-rcon=false
'@
    Set-Content -LiteralPath (Join-Path $backendDirectory 'server.properties') -Value $properties -Encoding ascii
}

function Write-NavigatorConfig {
    $proxyData = Join-Path (Join-Path $proxyDirectory 'plugins') 'velocitynavigator'
    New-Item -ItemType Directory -Path $proxyData -Force | Out-Null
    $config = @'
config_version = 8

[routing]
default_lobbies = ["lobby"]
selection_mode = "least_players"

[health_checks]
enabled = true
timeout_ms = 3000
cache_seconds = 1

[update_checker]
enabled = false

[dashboard]
enabled = false
'@
    Set-Utf8NoBom (Join-Path $proxyData 'navigator.toml') $config
}

function Set-PaperVelocityForwarding([string] $Secret) {
    $configPath = Join-Path (Join-Path $backendDirectory 'config') 'paper-global.yml'
    if (-not (Test-Path -LiteralPath $configPath)) {
        throw "Paper did not generate $configPath."
    }
    $lines = [System.Collections.Generic.List[string]](Get-Content -LiteralPath $configPath)
    $velocityStart = -1
    for ($index = 0; $index -lt $lines.Count; $index++) {
        if ($lines[$index] -match '^\s*velocity:\s*$') { $velocityStart = $index; break }
    }
    if ($velocityStart -lt 0) { throw 'Could not find proxies.velocity in paper-global.yml.' }
    $velocityIndent = ($lines[$velocityStart].Length - $lines[$velocityStart].TrimStart().Length)
    $velocityEnd = $lines.Count
    for ($index = $velocityStart + 1; $index -lt $lines.Count; $index++) {
        $line = $lines[$index]
        if ($line.Trim().Length -eq 0 -or $line.TrimStart().StartsWith('#')) { continue }
        $indent = $line.Length - $line.TrimStart().Length
        if ($indent -le $velocityIndent) { $velocityEnd = $index; break }
    }
    $values = @{ enabled = 'true'; 'online-mode' = 'false'; secret = "`"$Secret`"" }
    foreach ($key in $values.Keys) {
        $found = $false
        for ($index = $velocityStart + 1; $index -lt $velocityEnd; $index++) {
            if ($lines[$index] -match "^(\s*)$([regex]::Escape($key)):\s*.*$") {
                $indent = $Matches[1]
                $lines[$index] = "${indent}${key}: $($values[$key])"
                $found = $true
                break
            }
        }
        if (-not $found) {
            $lines.Insert($velocityEnd, "  ${key}: $($values[$key])")
            $velocityEnd++
        }
    }
    Set-Utf8NoBom $configPath ($lines -join [Environment]::NewLine)
}

function Start-ManagedServer([string] $Name, [string] $Directory, [string] $Jar, [string] $Arguments, [string] $StopCommand) {
    $stopPath = Join-Path $Directory 'stop.request'
    Remove-Item -LiteralPath $stopPath -Force -ErrorAction SilentlyContinue
    $stdout = Join-Path $Directory 'console.log'
    $stderr = Join-Path $Directory 'console-error.log'
    Set-Content -LiteralPath $stdout -Value "[$([DateTime]::UtcNow.ToString('o'))] Starting $Name." -Encoding utf8
    Set-Content -LiteralPath $stderr -Value '' -Encoding utf8
    $quote = {
        param([string] $Value)
        '"' + $Value.Replace('"', '\"') + '"'
    }
    $launcherArguments = @(
        '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', (& $quote $runner),
        '-Name', (& $quote $Name), '-WorkingDirectory', (& $quote $Directory), '-JavaPath', (& $quote (Get-JavaPath)),
        '-JarPath', (& $quote $Jar), '-ArgumentLine', (& $quote $Arguments), '-StopCommand', (& $quote $StopCommand)
    ) -join ' '
    $launcher = Start-Process -FilePath 'powershell.exe' -ArgumentList $launcherArguments -RedirectStandardOutput $stdout -RedirectStandardError $stderr -WindowStyle Hidden -PassThru
    Set-Content -LiteralPath (Join-Path $Directory 'launcher.pid') -Value $launcher.Id -NoNewline -Encoding ascii
}

function Get-RuntimeLog([string] $Directory) {
    $paths = @(
        (Join-Path $Directory 'console.log'),
        (Join-Path $Directory 'console-error.log'),
        (Join-Path (Join-Path $Directory 'logs') 'latest.log')
    ) | Where-Object { Test-Path -LiteralPath $_ }
    return (($paths | ForEach-Object { Get-Content -LiteralPath $_ -Raw }) -join [Environment]::NewLine)
}

function Get-ManagedConsoleLog([string] $Directory) {
    $paths = @(
        (Join-Path $Directory 'console.log'),
        (Join-Path $Directory 'console-error.log')
    ) | Where-Object { Test-Path -LiteralPath $_ }
    return (($paths | ForEach-Object { Get-Content -LiteralPath $_ -Raw }) -join [Environment]::NewLine)
}

function Wait-ForLog([string] $Directory, [string] $Pattern, [int] $TimeoutSeconds = 120) {
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    while ([DateTime]::UtcNow -lt $deadline) {
        # Exclude logs/latest.log from readiness checks because it can still
        # contain the previous run while the new Java process is booting.
        if ((Get-ManagedConsoleLog $Directory) -match $Pattern) { return }
        $pidPath = Join-Path $Directory 'java.pid'
        if ((Test-Path -LiteralPath $pidPath) -and -not (Get-Process -Id ([int](Get-Content -LiteralPath $pidPath -Raw)) -ErrorAction SilentlyContinue)) {
            throw "$Directory stopped before it became ready. See $(Join-Path $Directory 'console-error.log')."
        }
        Start-Sleep -Seconds 1
    }
    throw "Timed out waiting for '$Pattern' in $(Join-Path $Directory 'console.log')."
}

function Request-Stop([string] $Directory) {
    $pidPath = Join-Path $Directory 'java.pid'
    if (-not (Test-Path -LiteralPath $pidPath)) { return }
    New-Item -ItemType File -Path (Join-Path $Directory 'stop.request') -Force | Out-Null
    $deadline = [DateTime]::UtcNow.AddSeconds(35)
    while ((Test-Path -LiteralPath $pidPath) -and [DateTime]::UtcNow -lt $deadline) {
        Start-Sleep -Milliseconds 500
    }
    if (Test-Path -LiteralPath $pidPath) {
        throw "The server in $Directory did not shut down gracefully. Its console is still available there."
    }
}

function Send-ManagedCommand([string] $Directory, [string] $Command) {
    if (-not (Test-ManagedServerRunning $Directory)) {
        throw "The server in $Directory is not running."
    }
    if ([string]::IsNullOrWhiteSpace($Command)) {
        throw 'ConsoleCommand cannot be empty.'
    }
    $request = Join-Path $Directory ("command.request." + [Guid]::NewGuid().ToString('N') + '.txt')
    $temporary = "$request.tmp"
    Set-Utf8NoBom $temporary ($Command.Trim() + [Environment]::NewLine)
    Move-Item -LiteralPath $temporary -Destination $request -Force
    Write-Status "Queued command for $(Split-Path -Leaf $Directory): $Command"
}

function Test-ManagedServerRunning([string] $Directory) {
    $pidPath = Join-Path $Directory 'java.pid'
    if (-not (Test-Path -LiteralPath $pidPath)) { return $false }
    $processId = [int](Get-Content -LiteralPath $pidPath -Raw)
    return $null -ne (Get-Process -Id $processId -ErrorAction SilentlyContinue)
}

function Test-TcpPort([string] $Name, [int] $Port) {
    $client = [System.Net.Sockets.TcpClient]::new()
    try {
        $task = $client.ConnectAsync('127.0.0.1', $Port)
        if (-not $task.Wait(3000)) { throw "$Name did not accept a TCP connection on port $Port." }
        if (-not $client.Connected) { throw "$Name did not accept a TCP connection on port $Port." }
    } finally {
        $client.Dispose()
    }
}

function Invoke-Setup {
    $null = Get-JavaPath
    New-Item -ItemType Directory -Path $proxyDirectory, $backendDirectory, (Join-Path $proxyDirectory 'plugins'), (Join-Path $backendDirectory 'plugins') -Force | Out-Null
    Ensure-Download $velocity (Join-Path $proxyDirectory 'velocity.jar')
    Ensure-Download $paper (Join-Path $backendDirectory 'paper.jar')
    $pluginJar = Ensure-PluginJar
    Copy-Item -LiteralPath $pluginJar -Destination (Join-Path (Join-Path $proxyDirectory 'plugins') 'VelocityNavigator.jar') -Force
    Copy-Item -LiteralPath $pluginJar -Destination (Join-Path (Join-Path $backendDirectory 'plugins') 'VelocityNavigator.jar') -Force
    $null = New-SharedSecret
    Write-ProxyConfig
    Write-BackendProperties
    Write-NavigatorConfig
    Write-Status "Setup complete in $environmentRoot"
}

function Invoke-Start {
    Invoke-Setup
    if (-not $AcceptMinecraftEula) {
        throw 'Paper requires acceptance of the Minecraft EULA. Re-run with -AcceptMinecraftEula only if you agree to it.'
    }
    Set-Content -LiteralPath (Join-Path $backendDirectory 'eula.txt') -Value "eula=true`n" -Encoding ascii
    $secret = New-SharedSecret
    $paperConfig = Join-Path (Join-Path $backendDirectory 'config') 'paper-global.yml'
    if (-not (Test-Path -LiteralPath $paperConfig)) {
        Write-Status 'Starting Paper once to generate its configuration.'
        Start-ManagedServer 'paper-bootstrap' $backendDirectory (Join-Path $backendDirectory 'paper.jar') '-Xms1G -Xmx1G' 'stop'
        Wait-ForLog $backendDirectory 'Done \(' 180
        Request-Stop $backendDirectory
    }
    Set-PaperVelocityForwarding $secret
    if (-not (Test-ManagedServerRunning $backendDirectory)) {
        Write-Status 'Starting Paper backend.'
        Start-ManagedServer 'paper-backend' $backendDirectory (Join-Path $backendDirectory 'paper.jar') '-Xms1G -Xmx1G' 'stop'
    } else {
        Write-Status 'Paper backend is already running; reusing it.'
    }
    Wait-ForLog $backendDirectory 'VelocityNavigator universal JAR is running in BACKEND GUI BRIDGE mode\.' 180
    Wait-ForLog $backendDirectory 'Done \(' 180
    if (-not (Test-ManagedServerRunning $proxyDirectory)) {
        Write-Status 'Starting Velocity proxy.'
        Start-ManagedServer 'velocity-proxy' $proxyDirectory (Join-Path $proxyDirectory 'velocity.jar') '-Xms512M -Xmx512M' 'end'
    } else {
        Write-Status 'Velocity proxy is already running; reusing it.'
    }
    Wait-ForLog $proxyDirectory 'VelocityNavigator universal JAR is running in VELOCITY PROXY mode\.' 90
    Invoke-Status
}

function Invoke-Status {
    Test-TcpPort 'Paper backend' 25566
    Test-TcpPort 'Velocity proxy' 25565
    $backendLog = Get-RuntimeLog $backendDirectory
    $proxyLog = Get-RuntimeLog $proxyDirectory
    if ($backendLog -notmatch 'VelocityNavigator universal JAR is running in BACKEND GUI BRIDGE mode\.') { throw 'Backend bridge mode was not confirmed in the Paper log.' }
    if ($proxyLog -notmatch 'VelocityNavigator universal JAR is running in VELOCITY PROXY mode\.') { throw 'Proxy mode was not confirmed in the Velocity log.' }
    Write-Status 'PASS: Paper, Velocity, and both VelocityNavigator runtime modes are live on loopback.'
    Write-Status 'Connect a Java client to 127.0.0.1:25565, then run /lobby to validate an actual routed player session.'
}

function Invoke-Stop {
    Request-Stop $proxyDirectory
    Request-Stop $backendDirectory
    Write-Status 'The integration stack stopped cleanly.'
}

function Invoke-RestartBackend {
    if (-not (Test-ManagedServerRunning $proxyDirectory)) {
        throw 'Velocity must be running before the backend-only restart.'
    }
    Request-Stop $backendDirectory
    Write-Status 'Restarting Paper backend while Velocity remains live.'
    Start-ManagedServer 'paper-backend' $backendDirectory (Join-Path $backendDirectory 'paper.jar') '-Xms1G -Xmx1G' 'stop'
    Wait-ForLog $backendDirectory 'VelocityNavigator universal JAR is running in BACKEND GUI BRIDGE mode\.' 180
    Wait-ForLog $backendDirectory 'Done \(' 180
    Write-Status 'Paper backend restarted and is ready.'
}

switch ($Action) {
    'Setup' { Invoke-Setup }
    'Start' { Invoke-Start }
    'Status' { Invoke-Status }
    'Stop' { Invoke-Stop }
    'RestartBackend' { Invoke-RestartBackend }
    'Command' {
        if ([string]::IsNullOrWhiteSpace($Target)) { throw 'Target must be proxy or backend when Action is Command.' }
        Send-ManagedCommand ($(if ($Target -eq 'proxy') { $proxyDirectory } else { $backendDirectory })) $ConsoleCommand
    }
}
