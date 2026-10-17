<#
.SYNOPSIS  Starts the Sunrise Dental server in a new window and opens the web client when it is ready.
.EXAMPLE   powershell -ExecutionPolicy Bypass -File .\run-web.ps1
.EXAMPLE   powershell -ExecutionPolicy Bypass -File .\run-web.ps1 -Port 8081
#>
param(
    [int]$Port = 8080
)
$ErrorActionPreference = "Stop"
Set-Location -Path $PSScriptRoot

if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
    throw "Maven (mvn) was not found on PATH. Start the Server run configuration in IntelliJ instead."
}

# MySQL root password (just press Enter if your MySQL user has no password)
$env:SUNRISE_DB_PASSWORD = Read-Host -Prompt "MySQL root password"
$env:SUNRISE_SERVER_PORT = "$Port"

# The new window inherits the environment variables set above.
Start-Process -FilePath "powershell" -ArgumentList "-NoExit", "-Command", "mvn compile exec:java@server"

Write-Host "Waiting for the server on port $Port ..."
$deadline = (Get-Date).AddSeconds(120)
do {
    Start-Sleep -Seconds 2
    $up = (Test-NetConnection -ComputerName localhost -Port $Port -WarningAction SilentlyContinue).TcpTestSucceeded
} until ($up -or (Get-Date) -gt $deadline)

if ($up) {
    Start-Process "http://localhost:$Port"
} else {
    Write-Warning "The server did not answer within 2 minutes. Look at the server window for the error."
}
