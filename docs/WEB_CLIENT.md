# Web client (Tier 1) - plain HTML, CSS and JavaScript

The browser client lives in `frontend/` and is served by the Java server itself at `/`
(`StaticFileHandler`). The page and the REST API share one origin, so there is no CORS to configure.
No framework, no jQuery, no Bootstrap, no chart library, no npm.

## Start the server and open the site (PowerShell)

Open PowerShell **in the project folder** (the folder that contains `pom.xml` and `frontend\`).

**Window 1 - start the server**
```powershell
cd C:\dev\sunrise-dental
$env:SUNRISE_DB_PASSWORD = Read-Host "MySQL root password"
mvn compile exec:java@server
```
Wait for `Sunrise Dental REST API listening on 127.0.0.1:8080`. (Port busy? add `$env:SUNRISE_SERVER_PORT = "8081"`
before `mvn` and open port 8081 instead.)

**Window 2 - open the browser**
```powershell
Start-Process "http://localhost:8080"
```

Or do both with the script: `powershell -ExecutionPolicy Bypass -File .\run-web.ps1` (add `-Port 8081` if needed).

Log in with `admin` / `admin123` (ADMIN, sees Reports) or `reception` / `reception123` (RECEPTIONIST).
In IntelliJ just run the `Server` configuration (see `INTELLIJ_GUIDE.md`) and open the same address.

## Pages

| Page | Purpose |
|---|---|
| `login.html` | Staff login (served at `/`). Shows "session ended" / "signed out" notices. |
| `dashboard.html` | Menu: Register Appointment, Search, Bill, Reports (ADMIN only), Help, Exit + today's appointments. |
| `register-appointment.html` | Find or register the patient, pick dentist/treatment (dropdowns from the API), date and time, book. Shows the dentist's already-booked times. |
| `search.html` | Appointment number -> full details (patient, dentist, treatment, notes) and status actions. |
| `billing.html` | Issue the bill, receipt on screen, **Print receipt** (`window.print()` + print stylesheet), mark as paid. |
| `reports.html` | ADMIN only: 5 reports as tables + canvas bar charts, CSV download, print. |
| `help.html` | Step-by-step guide for new staff. |

## Files

```
frontend/
  login.html dashboard.html register-appointment.html search.html billing.html reports.html help.html
  css/styles.css                 responsive layout (flexbox/grid) + @media print
  js/api.js                      the only module that calls fetch(); one function per endpoint
  js/auth.js                     sessionStorage token, expiry, redirect to login, role-based menu
  js/validators.js               client-side rules that mirror the server
  js/ui.js                       escaping, DOM helpers, alerts, loading states, tables, page shell
  js/charts.js                   canvas bar chart + small data helpers
  js/login.js ... js/help.js     one script per page
  tests/*.test.mjs               unit tests (optional, need only Node 18+)
```

## How it stays safe and simple
- All API data is inserted as text (`textContent`) or through the ``html`` template tag, which escapes every
  value. No inline scripts or styles; the server sends a strict `Content-Security-Policy`.
- The login token is kept in `sessionStorage` (gone when the tab closes). If the server answers 401 (for example
  after a restart) the browser returns to `login.html`. **Exit** calls `POST /api/auth/logout`, clears the session
  and returns to `login.html`.
- The role only hides menu items; the server checks the role on every request.
- `StaticFileHandler` serves only `GET`/`HEAD`, only html/css/js/svg/png/ico/txt files inside `frontend/`.

## Tests
```powershell
node --test "frontend/tests/*.test.mjs"      # validators, escaping, api.js/auth.js, chart helpers (Node 21+)
mvn test                                     # includes StaticFilesTest for the Java StaticFileHandler
```
(On Node 18/20 list the files instead of using the quotes: `node --test frontend/tests/validators.test.mjs ...`.)
