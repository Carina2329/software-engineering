[CmdletBinding()]
param(
    [uri]$BaseUri = 'http://localhost:8088',
    [string]$ContainerName = 'contest-nginx',
    [string]$OutputPath = '',
    [int]$LoginAttempts = 24,
    [int]$CaptchaAttempts = 36,
    [int]$EmailOtpAttempts = 24,
    [int]$PasswordResetAttempts = 24,
    [int]$NonSensitiveAttempts = 12,
    [switch]$SkipConfigTest
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Net.Http
$baseUrl = $BaseUri.AbsoluteUri.TrimEnd('/')
$client = [System.Net.Http.HttpClient]::new()
$client.Timeout = [TimeSpan]::FromSeconds(30)
$failures = [System.Collections.Generic.List[object]]::new()

function New-ProbeRequest {
    param(
        [string]$Method,
        [string]$Path,
        [string]$Body = ''
    )

    $request = [System.Net.Http.HttpRequestMessage]::new(
        [System.Net.Http.HttpMethod]::new($Method),
        "$baseUrl$Path"
    )
    if ($Method -in @('POST', 'PUT', 'PATCH')) {
        $request.Content = [System.Net.Http.StringContent]::new(
            $Body,
            [System.Text.Encoding]::UTF8,
            'application/json'
        )
    }
    return $request
}

function Read-ProbeResponse {
    param([System.Net.Http.HttpResponseMessage]$Response)

    $responseBody = $Response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
    $traceHeader = ''
    $traceValues = $null
    if ($Response.Headers.TryGetValues('X-Trace-Id', [ref]$traceValues)) {
        $traceHeader = [string]($traceValues | Select-Object -First 1)
    }
    $contentType = ''
    if ($null -ne $Response.Content.Headers.ContentType) {
        $contentType = $Response.Content.Headers.ContentType.ToString()
    }

    return [pscustomobject]@{
        Status      = [int]$Response.StatusCode
        Body        = $responseBody
        ContentType = $contentType
        TraceHeader = $traceHeader
    }
}

function Invoke-Probe {
    param(
        [string]$Method,
        [string]$Path,
        [string]$Body = ''
    )

    $request = New-ProbeRequest -Method $Method -Path $Path -Body $Body
    $response = $null
    try {
        $response = $client.SendAsync($request).GetAwaiter().GetResult()
        return Read-ProbeResponse -Response $response
    }
    finally {
        if ($null -ne $response) {
            $response.Dispose()
        }
        $request.Dispose()
    }
}

function Add-Failure {
    param([string]$Message)
    $script:failures.Add([pscustomobject]@{ message = $Message })
}

function Test-RateLimit {
    param(
        [string]$EndpointId,
        [string]$Method,
        [string]$Path,
        [string]$Body = '',
        [int]$Attempts
    )

    $limited = [System.Collections.Generic.List[object]]::new()
    $passedStatuses = [System.Collections.Generic.List[int]]::new()
    $traceIds = [System.Collections.Generic.List[string]]::new()

    $pending = [System.Collections.Generic.List[object]]::new()
    for ($index = 1; $index -le $Attempts; $index++) {
        $request = New-ProbeRequest -Method $Method -Path $Path -Body $Body
        try {
            $task = $client.SendAsync($request)
        }
        catch {
            $request.Dispose()
            throw
        }
        $pending.Add([pscustomobject]@{ Request = $request; Task = $task })
    }

    foreach ($item in $pending) {
        $response = $null
        try {
            $response = $item.Task.GetAwaiter().GetResult()
            $probe = Read-ProbeResponse -Response $response
            if ($probe.Status -eq 429) {
                $limited.Add($probe)
            }
            else {
                $passedStatuses.Add($probe.Status)
                if ($probe.Status -in @(502, 504)) {
                    Add-Failure "$EndpointId 上游不可用，HTTP $($probe.Status)；请启动本地后端后重跑。"
                }
            }
        }
        catch {
            Add-Failure "$EndpointId 探测请求失败：$($_.Exception.Message)"
        }
        finally {
            if ($null -ne $response) {
                $response.Dispose()
            }
            $item.Request.Dispose()
        }
    }

    if ($limited.Count -lt 2) {
        Add-Failure "$EndpointId 仅观察到 $($limited.Count) 个 429，无法完成双响应 trace_id 对照。"
    }

    foreach ($probe in $limited) {
        try {
            $payload = $probe.Body | ConvertFrom-Json -ErrorAction Stop
        }
        catch {
            Add-Failure "$EndpointId 的 429 响应不是 JSON：$($probe.Body)"
            continue
        }

        if ($probe.ContentType -notmatch '^application/json;\s*charset=utf-8$') {
            Add-Failure "$EndpointId 的 Content-Type 不符合契约：$($probe.ContentType)"
        }
        if ($payload.code -ne 40001) {
            Add-Failure "$EndpointId 的 429 业务码不是数字 40001。"
        }
        if ($payload.message -cne '请求过于频繁，请稍后再试') {
            Add-Failure "$EndpointId 的 429 message 不符合契约。"
        }
        if ($null -eq $payload.PSObject.Properties['data'] -or $null -ne $payload.data) {
            Add-Failure "$EndpointId 的 429 data 字段不是 null。"
        }

        $traceId = [string]$payload.trace_id
        if ([string]::IsNullOrWhiteSpace($traceId) -or $traceId -eq '$request_id') {
            Add-Failure "$EndpointId 的 trace_id 为空或未展开 `$request_id。"
        }
        if ($traceId -cne $probe.TraceHeader) {
            Add-Failure "$EndpointId 的 trace_id=$traceId 与 X-Trace-Id=$($probe.TraceHeader) 不一致。"
        }
        if (-not [string]::IsNullOrWhiteSpace($traceId)) {
            $traceIds.Add($traceId)
        }
    }

    if (@($traceIds | Select-Object -Unique).Count -lt 2) {
        Add-Failure "$EndpointId 未观察到至少两个不同的 trace_id。"
    }

    $samples = @($limited | Select-Object -First 2 | ForEach-Object {
        [ordered]@{
            status       = $_.Status
            content_type = $_.ContentType
            x_trace_id   = $_.TraceHeader
            body         = $_.Body
        }
    })

    return [pscustomobject][ordered]@{
        endpoint_id      = $EndpointId
        method           = $Method
        path             = $Path
        attempts         = $Attempts
        rejected_429     = $limited.Count
        non_429_statuses = @($passedStatuses | Group-Object | ForEach-Object { "$($_.Name):$($_.Count)" })
        samples          = $samples
    }
}

function Test-NonSensitivePath {
    param(
        [string]$Name,
        [string]$Method,
        [string]$Path,
        [int]$Attempts
    )

    $statuses = [System.Collections.Generic.List[int]]::new()
    for ($index = 1; $index -le $Attempts; $index++) {
        $probe = Invoke-Probe -Method $Method -Path $Path -Body '{}'
        $statuses.Add($probe.Status)
        if ($probe.Status -eq 429) {
            Add-Failure "$Name 被认证低额度限流误伤，HTTP 429，路径 $Path。"
        }
        if ($probe.Status -in @(502, 504)) {
            Add-Failure "$Name 上游不可用，HTTP $($probe.Status)；请启动对应本地服务后重跑。"
        }
    }

    return [pscustomobject][ordered]@{
        name      = $Name
        method    = $Method
        path      = $Path
        attempts  = $Attempts
        statuses  = @($statuses | Group-Object | ForEach-Object { "$($_.Name):$($_.Count)" })
    }
}

try {
    if (-not $SkipConfigTest) {
        & docker exec $ContainerName nginx -t
        if ($LASTEXITCODE -ne 0) {
            throw "docker exec $ContainerName nginx -t 失败。"
        }
    }

    $health = Invoke-Probe -Method 'GET' -Path '/nginx-health'
    if ($health.Status -ne 200 -or $health.Body -ne "ok`n") {
        Add-Failure "Nginx 健康检查异常：HTTP $($health.Status)，body=$($health.Body)"
    }

    $sensitiveResults = @(
        Test-RateLimit -EndpointId 'EP-001' -Method 'POST' -Path '/api/v1/auth/login' -Body '{}' -Attempts $LoginAttempts
        Test-RateLimit -EndpointId 'EP-067' -Method 'GET' -Path '/api/v1/auth/captcha?scene=NGINX_PROBE' -Attempts $CaptchaAttempts
        Test-RateLimit -EndpointId 'EP-068' -Method 'POST' -Path '/api/v1/auth/email-verification-codes' -Body '{}' -Attempts $EmailOtpAttempts
        Test-RateLimit -EndpointId 'EP-071' -Method 'POST' -Path '/api/v1/auth/password-reset/request' -Body '{}' -Attempts $PasswordResetAttempts
    )

    $nonSensitiveResults = @(
        Test-NonSensitivePath -Name '前端静态页面' -Method 'GET' -Path '/login' -Attempts $NonSensitiveAttempts
        Test-NonSensitivePath -Name '普通业务 API' -Method 'GET' -Path '/api/v1/users/me' -Attempts $NonSensitiveAttempts
        Test-NonSensitivePath -Name '文件批量上传路径' -Method 'POST' -Path '/api/v1/files/batch' -Attempts $NonSensitiveAttempts
        Test-NonSensitivePath -Name '分片上传路径' -Method 'PUT' -Path '/api/v1/uploads/nginx-rate-limit-probe/chunks/0' -Attempts $NonSensitiveAttempts
        Test-NonSensitivePath -Name '文件下载路径' -Method 'GET' -Path '/api/v1/files/nginx-rate-limit-probe/content' -Attempts $NonSensitiveAttempts
    )

    $report = [ordered]@{
        generated_at = (Get-Date).ToString('o')
        base_url     = $baseUrl
        health       = [ordered]@{ status = $health.Status; body = $health.Body.Trim() }
        sensitive    = $sensitiveResults
        non_sensitive = $nonSensitiveResults
        failures     = @($failures)
        success      = $failures.Count -eq 0
        note         = '仅验证 Nginx 与本地链路；未使用真实账号、邮箱或 SMTP，不替代 B/C 合并后的最终验收。'
    }

    $json = $report | ConvertTo-Json -Depth 8
    if (-not [string]::IsNullOrWhiteSpace($OutputPath)) {
        $parent = Split-Path -Parent $OutputPath
        if (-not [string]::IsNullOrWhiteSpace($parent) -and -not (Test-Path $parent)) {
            New-Item -ItemType Directory -Path $parent -Force | Out-Null
        }
        $json | Set-Content -Path $OutputPath -Encoding UTF8
    }
    Write-Output $json

    if ($failures.Count -gt 0) {
        throw "Nginx 验收发现 $($failures.Count) 个问题，详见结果 JSON。"
    }
}
finally {
    $client.Dispose()
}
