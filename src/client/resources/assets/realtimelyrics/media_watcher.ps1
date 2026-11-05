param([int]$ParentPid = 0, [int]$IntervalMs = 400)

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding $false
Add-Type -AssemblyName System.Runtime.WindowsRuntime

$asTaskGeneric = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
    $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and
    $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
})[0]

function Await($op, [Type]$type) {
    $task = $asTaskGeneric.MakeGenericMethod($type).Invoke($null, @($op))
    $null = $task.Wait(3000)
    $task.Result
}

$null = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType = WindowsRuntime]
$mgrType = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]
$propsType = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties]
$mgr = Await ($mgrType::RequestAsync()) $mgrType

function Emit($obj) {
    [Console]::Out.WriteLine(($obj | ConvertTo-Json -Compress))
    [Console]::Out.Flush()
}

while ($true) {
    if ($ParentPid -gt 0 -and -not (Get-Process -Id $ParentPid -ErrorAction SilentlyContinue)) { exit 0 }
    try {
        $sessions = @($mgr.GetSessions())
        $pick = $null
        foreach ($s in $sessions) {
            if ($s.SourceAppUserModelId -match 'Spotify' -and [string]$s.GetPlaybackInfo().PlaybackStatus -eq 'Playing') { $pick = $s; break }
        }
        if (-not $pick) {
            foreach ($s in $sessions) {
                if ([string]$s.GetPlaybackInfo().PlaybackStatus -eq 'Playing') { $pick = $s; break }
            }
        }
        if (-not $pick) { $pick = $mgr.GetCurrentSession() }

        if ($pick) {
            $props = Await ($pick.TryGetMediaPropertiesAsync()) $propsType
            $tl = $pick.GetTimelineProperties()
            $pb = $pick.GetPlaybackInfo()
            Emit ([ordered]@{
                ok         = $true
                app        = [string]$pick.SourceAppUserModelId
                title      = [string]$props.Title
                artist     = [string]$props.Artist
                album      = [string]$props.AlbumTitle
                status     = [string]$pb.PlaybackStatus
                positionMs = [long]$tl.Position.TotalMilliseconds
                durationMs = [long]($tl.EndTime - $tl.StartTime).TotalMilliseconds
                updatedMs  = [long]$tl.LastUpdatedTime.ToUnixTimeMilliseconds()
                nowMs      = [long][DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
            })
        } else {
            Emit ([ordered]@{ ok = $false })
        }
    } catch {
        Emit ([ordered]@{ ok = $false; error = [string]$_.Exception.Message })
    }
    Start-Sleep -Milliseconds $IntervalMs
}
