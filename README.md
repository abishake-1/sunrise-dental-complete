# Sunrise Dental Clinic - Appointment & Patient Management System

Plain Java 17+ (no frameworks) with three tiers:

1. **Client** - a web client (`frontend/`, plain HTML/CSS/JavaScript, served by the server) and an optional Swing desktop client.
2. **Server** - REST API on the JDK's built-in `HttpServer` (JSON).
3. **Database** - MySQL, plain JDBC.

## Quick start

1. **Database** (once). In a terminal in this folder, with the MySQL 8 client:
   ```powershell
   & "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" -u root -p -e "source database/schema.sql"
   & "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" -u root -p -e "source database/data.sql"
   & "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" -u root -p -e "source database/migration_002_billing_reports_reminders.sql"
   ```
2. **Server** (IntelliJ): run `com.sunrisedental.server.ServerApplication` with the working directory set to this
   folder and the environment variable `SUNRISE_DB_PASSWORD=<your MySQL root password>`.
3. **Open** http://localhost:8080 (if the port is busy add `SUNRISE_SERVER_PORT=8081`) and log in.

| Username | Password | Role |
|---|---|---|
| `admin` | `admin123` | ADMIN (can open Reports) |
| `reception` | `reception123` | RECEPTIONIST |

Passwords are stored as **plain text** in the `users` table, to keep the system simple. That is fine for learning
and demos; do not use real patient data or real passwords with it.

Full steps: `docs/INTELLIJ_GUIDE.md`. Web client details: `docs/WEB_CLIENT.md`.

## Settings (environment variables, all optional)

| Variable | Default |
|---|---|
| `SUNRISE_DB_USER` | `root` |
| `SUNRISE_DB_PASSWORD` | empty |
| `SUNRISE_DB_URL` | `jdbc:mysql://localhost:3306/sunrise_dental` |
| `SUNRISE_SERVER_PORT` | `8080` |
| `SUNRISE_FRONTEND_DIR` | `./frontend` |

Clinic name, consultation fee, reminder time and receipt formats are in `src/main/resources/config.properties`.

## How login works

`POST /api/auth/login` checks the username and password against the `users` table and returns a random token.
The client sends it as `Authorization: Bearer <token>`. The token is kept in memory on the server until the user
logs out (or the server restarts). Roles: ADMIN and RECEPTIONIST (only ADMIN may open reports). There is no
lockout, no timeout, no cookie and no CORS.

## Tests
`mvn test` (Java, JUnit 5) and `node --test "frontend/tests/*.test.mjs"` (web client, optional).
