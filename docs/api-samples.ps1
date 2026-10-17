# Sunrise Dental REST API - sample requests for Windows PowerShell (5.1 or 7) using curl.exe
# Always type "curl.exe": in Windows PowerShell plain "curl" is an alias of Invoke-WebRequest.
# JSON is piped through stdin ("--data-binary @-") so PowerShell cannot mangle the double quotes.
# Seed logins (development only): admin / admin123   reception / reception123

# ---- 0. start the server (separate window, project root) -------------------------------------
#   $env:SUNRISE_DB_PASSWORD = Read-Host "MySQL root password"     # only if your MySQL user has a password
#   mvn compile exec:java@server

$base = "http://localhost:8080"

# ---- 1. login (token goes into $token) --------------------------------------------------------
'{"username":"admin","password":"admin123"}' |
    curl.exe -s -i -X POST "$base/api/auth/login" -H "Content-Type: application/json" --data-binary "@-"

$login = '{"username":"admin","password":"admin123"}' |
    curl.exe -s -X POST "$base/api/auth/login" -H "Content-Type: application/json" --data-binary "@-" |
    ConvertFrom-Json
$token = $login.token
$auth  = "Authorization: Bearer $token"

curl.exe -s "$base/api/auth/me" -H $auth

# ---- 2. reference data ------------------------------------------------------------------------
curl.exe -s "$base/api/dentists"   -H $auth
curl.exe -s "$base/api/treatments" -H $auth

# ---- 3. register a patient (validated: 07XXXXXXXX phone, letters-only names, address 5+ chars) --
$patient = '{"firstName":"Nimal","lastName":"De Silva","dateOfBirth":"1985-05-10","gender":"MALE","phone":"0771234567","email":"nimal@example.com","address":"12 Temple Road, Kilinochchi"}' |
    curl.exe -s -X POST "$base/api/patients" -H $auth -H "Content-Type: application/json" --data-binary "@-" |
    ConvertFrom-Json
$patientId = $patient.id

# ---- 4. book an appointment (tomorrow 10:00, dentist 1, treatment 1 = check-up) ---------------
$date = (Get-Date).AddDays(1).ToString("yyyy-MM-dd")
$booking = @"
{"patientId":$patientId,"dentistId":1,"treatmentId":1,"date":"$date","time":"10:00","notes":"First visit"}
"@
$booking | curl.exe -s -i -X POST "$base/api/appointments" -H $auth -H "Content-Type: application/json" --data-binary "@-"

$appt = $booking |
    curl.exe -s -X POST "$base/api/appointments" -H $auth -H "Content-Type: application/json" --data-binary "@-" |
    ConvertFrom-Json          # a second identical booking -> 409 (this is the double-booking check)

# create a different slot to keep working with:
$booking2 = @"
{"patientId":$patientId,"dentistId":1,"treatmentId":1,"date":"$date","time":"11:00"}
"@
$appt = $booking2 |
    curl.exe -s -X POST "$base/api/appointments" -H $auth -H "Content-Type: application/json" --data-binary "@-" |
    ConvertFrom-Json
$apptId = $appt.id; $apptNo = $appt.appointmentNumber

# ---- 5. read ----------------------------------------------------------------------------------
curl.exe -s "$base/api/appointments/$apptNo" -H $auth
curl.exe -s "$base/api/appointments?date=$date&dentistId=1" -H $auth      # quotes keep PowerShell from eating the &
curl.exe -s "$base/api/appointments?date=$date" -H $auth                  # all dentists
curl.exe -s "$base/api/patients?search=Nimal" -H $auth

# ---- 6. update: reschedule (PUT date+time) or change status (PUT status), never both ----------
$moved = @"
{"date":"$date","time":"14:30","notes":"Moved at patient's request"}
"@
$moved | curl.exe -s -X PUT "$base/api/appointments/$apptId" -H $auth -H "Content-Type: application/json" --data-binary "@-"
'{"status":"CONFIRMED"}' |
    curl.exe -s -X PUT "$base/api/appointments/$apptId" -H $auth -H "Content-Type: application/json" --data-binary "@-"

# ---- 7. cancel (admin: any; receptionist: only appointments they created) ---------------------
curl.exe -s -X DELETE "$base/api/appointments/$apptId" -H $auth

# ---- 8. validation: every problem is reported at once -----------------------------------------
'{"patientId":0,"dentistId":"x","date":"2020-01-01","time":"19:07","colour":"red"}' |
    curl.exe -s -X POST "$base/api/appointments" -H $auth -H "Content-Type: application/json" --data-binary "@-"
'{"firstName":"R2D2","lastName":"","dateOfBirth":"2999-01-01","gender":"?","phone":"0112345678","address":"abc"}' |
    curl.exe -s -X POST "$base/api/patients" -H $auth -H "Content-Type: application/json" --data-binary "@-"

# ---- 9. authentication failures ---------------------------------------------------------------
curl.exe -s -w "`nHTTP %{http_code}`n" "$base/api/dentists"                                   # no token -> 401
curl.exe -s -w "`nHTTP %{http_code}`n" "$base/api/dentists" -H "Authorization: Bearer nope"   # bad token -> 401

# wrong password -> 401 (there is no lockout)
'{"username":"reception","password":"wrong-password"}' |
    curl.exe -s -w "  <- HTTP %{http_code}`n" -X POST "$base/api/auth/login" -H "Content-Type: application/json" --data-binary "@-"

# ---- 10. logout (ends the session) --------------------------------------------------------------
curl.exe -s -i -X POST "$base/api/auth/logout" -H $auth
curl.exe -s -w "`nHTTP %{http_code}`n" "$base/api/auth/me" -H $auth                          # now 401

# Non-ASCII text (e.g. Tamil/Sinhala names): use PowerShell 7, or write the JSON to a BOM-less UTF-8 file
# and send it with   --data-binary "@body.json"   (Windows PowerShell 5.1 pipes ASCII and "Set-Content -Encoding utf8" adds a BOM).
