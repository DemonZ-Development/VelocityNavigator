[CmdletBinding()]
param(
    [Parameter(Mandatory)] [string] $Name,
    [Parameter(Mandatory)] [string] $WorkingDirectory,
    [Parameter(Mandatory)] [string] $JavaPath,
    [Parameter(Mandatory)] [string] $JarPath,
    [Parameter(Mandatory)] [string] $ArgumentLine,
    [Parameter(Mandatory)] [string] $StopCommand
)

$ErrorActionPreference = 'Stop'

$pidPath = Join-Path $WorkingDirectory 'java.pid'
$stopPath = Join-Path $WorkingDirectory 'stop.request'

Remove-Item -LiteralPath $stopPath -Force -ErrorAction SilentlyContinue
Get-ChildItem -LiteralPath $WorkingDirectory -Filter 'command.request.*.txt' -File -ErrorAction SilentlyContinue |
    Remove-Item -Force -ErrorAction SilentlyContinue

$startInfo = [System.Diagnostics.ProcessStartInfo]::new()
$startInfo.FileName = $JavaPath
$startInfo.Arguments = "$ArgumentLine -jar `"$JarPath`""
$startInfo.WorkingDirectory = $WorkingDirectory
$startInfo.UseShellExecute = $false
$startInfo.CreateNoWindow = $true
$startInfo.RedirectStandardInput = $true
$startInfo.RedirectStandardOutput = $false
$startInfo.RedirectStandardError = $false

$process = [System.Diagnostics.Process]::new()
$process.StartInfo = $startInfo

if (-not $process.Start()) {
    throw "Could not start $Name."
}

Set-Content -LiteralPath $pidPath -Value $process.Id -NoNewline -Encoding ascii

try {
    while (-not $process.HasExited) {
        if (Test-Path -LiteralPath $stopPath) {
            Remove-Item -LiteralPath $stopPath -Force
            $process.StandardInput.WriteLine($StopCommand)
            $process.StandardInput.Flush()
            if (-not $process.WaitForExit(30000)) {
                throw "$Name did not stop within 30 seconds after '$StopCommand'."
            }
            break
        }
        # The integration harness writes each command by atomically renaming a
        # request file, so the server never consumes a partially written command.
        Get-ChildItem -LiteralPath $WorkingDirectory -Filter 'command.request.*.txt' -File -ErrorAction SilentlyContinue |
            Sort-Object Name |
            ForEach-Object {
                $command = [System.IO.File]::ReadAllText($_.FullName).Trim()
                Remove-Item -LiteralPath $_.FullName -Force -ErrorAction SilentlyContinue
                if (-not [string]::IsNullOrWhiteSpace($command)) {
                    $process.StandardInput.WriteLine($command)
                    $process.StandardInput.Flush()
                }
            }
        Start-Sleep -Milliseconds 250
    }
} finally {
    if (-not $process.HasExited) {
        $process.Kill($true)
        $process.WaitForExit(5000)
    }
    Remove-Item -LiteralPath $pidPath -Force -ErrorAction SilentlyContinue
    $process.Dispose()
}
