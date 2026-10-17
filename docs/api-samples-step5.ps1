# Sunrise Dental REST API - billing, reports and reminders (Windows PowerShell, curl.exe)
# Prerequisites: the Step 4 samples (login, patient, appointment) and the database migration:
#   mysql -u root -p -e "source database/migration_002_billing_reports_reminders.sql"
#   ( "<" redirection does not exist in PowerShell, so use the source command )
# Server window:
#   $env:SUNRISE_DB_PASSWORD = Read-Host "MySQL root password"   # only if your MySQL user has a password
#   $env:SUNRISE_NOTIFY_MODE = "file"            # reminders go to notifications-outbox.log (default: console)
#   mvn compile exec:java@server

$base = "http://localhost:8080"
function Login($user, $password) {
    $r = (@{ username = $user; password = $password } | ConvertTo-Json -Compress) |
        curl.exe -s -X POST "$base/api/auth/login" -H "Content-Type: application/json" --data-binary "@-" | ConvertFrom-Json
    "Authorization: Bearer $($r.token)"
}
$admin = Login "admin" "admin123"
$desk  = Login "reception" "reception123"

# ---- A. billing -------------------------------------------------------------------------------
# $apptId = id of an appointment (see the booking sample). Only COMPLETED appointments can be billed:
'{"status":"COMPLETED"}' |
    curl.exe -s -X PUT "$base/api/appointments/$apptId" -H $desk -H "Content-Type: application/json" --data-binary "@-"

# create the bill (no body = STANDARD). Total = treatment price + consultation fee - discount
curl.exe -s -i -X POST "$base/api/appointments/$apptId/bill" -H $desk

# the same call again -> 409 (one bill per appointment)
curl.exe -s -w "`nHTTP %{http_code}`n" -X POST "$base/api/appointments/$apptId/bill" -H $desk

# other schemes (use them on a different completed appointment):
'{"billingType":"SENIOR"}' |
    curl.exe -s -X POST "$base/api/appointments/$otherId/bill" -H $desk -H "Content-Type: application/json" --data-binary "@-"
'{"billingType":"INSURANCE","insuranceCoveragePercent":60}' |
    curl.exe -s -X POST "$base/api/appointments/$thirdId/bill" -H $desk -H "Content-Type: application/json" --data-binary "@-"

$billId = 1
curl.exe -s "$base/api/bills/$billId" -H $desk                                  # receipt as JSON
curl.exe -s "$base/api/bills/$billId?format=text" -H $desk                      # receipt as printable plain text
curl.exe -s "$base/api/bills/$billId?format=html" -H $desk -o receipt.html      # receipt as HTML
Start-Process .\receipt.html                                                    # opens it; Ctrl+P prints

# record the payment (the receipt files are refreshed)
'{"paymentMethod":"CASH"}' |
    curl.exe -s -X POST "$base/api/bills/$billId/pay" -H $desk -H "Content-Type: application/json" --data-binary "@-"

# the server also saved the receipts to its folder (SUNRISE_RECEIPTS_DIR, default .\receipts)
Get-ChildItem .\receipts
Get-Content .\receipts\RCP-000001.txt
Get-Content .\receipts\RCP-000001.txt | Out-Printer                              # send the text receipt to the default printer

# ---- B. reports (ADMIN only; a receptionist gets 403) -----------------------------------------
curl.exe -s -w "`nHTTP %{http_code}`n" "$base/api/reports/top-treatments" -H $desk

$month = (Get-Date).ToString("yyyy-MM-01"); $today = (Get-Date).ToString("yyyy-MM-dd")
curl.exe -s "$base/api/reports/daily-appointments?from=$month&to=$today" -H $admin
curl.exe -s "$base/api/reports/monthly-revenue?year=$((Get-Date).Year)&month=$((Get-Date).Month)" -H $admin
curl.exe -s "$base/api/reports/top-treatments?from=$month&to=$today&limit=5" -H $admin
curl.exe -s "$base/api/reports/dentist-utilization?from=$month&to=$today" -H $admin
curl.exe -s "$base/api/reports/cancellation-rate?from=$month&to=$today" -H $admin

# CSV export (UTF-8 with BOM: double-click it, Excel shows it correctly)
curl.exe -s "$base/api/reports/monthly-revenue?year=2026&month=10&format=csv" -H $admin -o monthly-revenue.csv
Import-Csv .\monthly-revenue.csv | Format-Table
# let the server pick the file name: -OJ saves it as top-treatments_<from>_<to>_5.csv
curl.exe -s -OJ "$base/api/reports/top-treatments?from=$month&to=$today&format=csv" -H $admin

# every problem is reported at once
curl.exe -s "$base/api/reports/top-treatments?from=2026-10-12&to=2026-10-01&limit=99&format=xml&foo=1" -H $admin

# ---- C. reminders -----------------------------------------------------------------------------
# The server runs this by itself every day at reminders.run.time (config.properties, default 18:00) for TOMORROW.
# An administrator can also trigger it by hand (no body = tomorrow, or give a day within the next 30 days):
curl.exe -s -X POST "$base/api/admin/reminders/run" -H $admin
'{"date":"2026-10-14"}' |
    curl.exe -s -X POST "$base/api/admin/reminders/run" -H $admin -H "Content-Type: application/json" --data-binary "@-"

Get-Content .\notifications-outbox.log -Tail 5      # SUNRISE_NOTIFY_MODE=file; in console mode look at the server window
# Run it twice: the second run reports sent=0 because reminders that were SENT are not repeated.
# Every attempt is a row in the notifications table:
#   mysql -u root -p sunrise_dental -e "SELECT notification_id, appointment_id, channel, status, failure_reason FROM notifications WHERE type='REMINDER'"
