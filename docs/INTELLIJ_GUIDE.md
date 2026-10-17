# Run Sunrise Dental in IntelliJ IDEA (Community Edition is enough)

Three parts work together: **MySQL** (database) -> **Server** (`ServerApplication`) -> **web browser** (or the
optional Swing client). Start them in that order.

## 1. Open the project
1. Unzip to a simple folder such as `C:\dev\sunrise-dental`.
2. IntelliJ: **File > Open**, choose `pom.xml`, **Open as Project**. Wait for Maven to finish.
3. **File > Project Structure > Project**: SDK = JDK 17 or newer.

## 2. Create the database (once)
Use the MySQL 8 client from your MySQL install (not XAMPP's). Run in a terminal in the project folder, one line at a time:
```powershell
$mysql = "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
& $mysql -u root -p -e "source database/schema.sql"
& $mysql -u root -p -e "source database/data.sql"
& $mysql -u root -p -e "source database/migration_002_billing_reports_reminders.sql"
```
Enter your MySQL root password each time. Run them in this order, once. `schema.sql` deletes and recreates the
tables, so run it again only if you want a fresh start.

Check: `& $mysql -u root -p -e "select username, password, role from sunrise_dental.users"` shows `admin` and `reception`.

## 3. Start the server
1. **Run > Edit Configurations > + > Application**
   - Name: `Server`
   - Module: `sunrise-dental`, Main class: `com.sunrisedental.server.ServerApplication`
   - **Working directory:** the project folder
   - Environment variables (**Modify options > Environment variables**): `SUNRISE_DB_PASSWORD=your-mysql-root-password`
     (if port 8080 is busy, for example Apache/XAMPP, add `;SUNRISE_SERVER_PORT=8081`)
2. Press **Run**. You should see `Sunrise Dental REST API listening on 127.0.0.1:8080`.

## 4. Use it
Open **http://localhost:8080** (or 8081) and log in:

| Username | Password | Role |
|---|---|---|
| `admin` | `admin123` | ADMIN (also sees Reports) |
| `reception` | `reception123` | RECEPTIONIST |

Try: **Register Appointment** (new patient: name letters only, phone `07XXXXXXXX`, future date, time 08:00-18:00) ->
**Search** (appointment number such as `APT-0001`) -> mark it completed -> **Bill** -> **Print receipt**.

## 5. Optional: the Swing desktop client
Run > Edit Configurations > + > Application: Name `Client`, Module `sunrise-dental`, Main class
`com.sunrisedental.client.ClientApplication`. If the server is not on port 8080 add `SUNRISE_API_URL=http://localhost:8081`.

## 6. Tests
Right-click `src/test/java` > **Run 'All Tests'** (no database needed).

## Changing a password
Passwords are plain text, so just update the table:
```sql
UPDATE sunrise_dental.users SET password = 'newpass123' WHERE username = 'admin';
```
(Passwords must be at least 4 characters.) To add a user:
```sql
INSERT INTO sunrise_dental.users (username, password, full_name, email, role)
VALUES ('nimal', 'nimal123', 'Nimal Perera', 'nimal@example.com', 'RECEPTIONIST');
```

## Troubleshooting
| You see | Fix |
|---|---|
| `Address already in use: bind` | Something else uses the port. Add `SUNRISE_SERVER_PORT=8081` and open that port instead. |
| `Access denied for user 'root'` | `SUNRISE_DB_PASSWORD` is missing or wrong (use your MySQL root password). |
| `Unknown database 'sunrise_dental'` / missing tables | Redo step 2. |
| `Communications link failure` | MySQL is not running (start the `MySQL80` service). |
| Login says "Wrong username or password" | Use the table above. If still failing, redo step 2 (`data.sql` creates the users). |
| `Web client folder not found` | Set the run configuration's **Working directory** to the project folder. |
| "Your session has ended" | The server was restarted; log in again. |
