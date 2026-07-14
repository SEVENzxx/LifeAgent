$ErrorActionPreference = "Stop"

$endpoints = @(
    "http://localhost:8080/nginx-health",
    "http://localhost:8080/actuator/health/readiness",
    "http://localhost:8080/api/v1/system",
    "http://localhost:8080/v3/api-docs"
)

foreach ($endpoint in $endpoints) {
    $response = Invoke-WebRequest -Uri $endpoint -UseBasicParsing
    if ($response.StatusCode -ne 200) {
        throw "Health check failed: $endpoint"
    }
    Write-Host "Health check passed: $endpoint"
}
