# RailSync – Train Reservation System

Java 17+ · JavaFX · JDBC + SQLite · multithreaded, transactional seat booking.

## Run it (Windows)
1. Install a **full JDK 17 or newer** (https://adoptium.net) – check with `java -version` and `javac -version`.
2. Download the **JavaFX SDK** for your OS from https://openjfx.io and unzip it, e.g. to `C:\javafx-sdk-21`.
   (Or unzip it into `lib\javafx-sdk` inside this project and skip step 3.)
3. In a Command Prompt in the project folder:
   ```
   set JAVAFX_HOME=C:\javafx-sdk-21
   run.bat
   ```
   `run.bat` compiles on first start, then opens the app. The SQLite driver is already in `lib/`.

macOS / Linux: `export JAVAFX_HOME=/path/to/javafx-sdk-21` then `./run.sh`.

## Modes
| Command | What it does |
|---|---|
| `run.bat` | JavaFX desktop app (login screen) |
| `run.bat --cli` | console version (same database and services) |
| `run.bat --simulate` | multithreaded booking demo (last seat + 12 threads) |
| `run.bat --demo` | collections / generics / polymorphism on real data |
| `run.bat --awt` | standalone AWT event demo (also in the app: Tools → AWT Event Demo) |
| `test.bat` | 17 automated tests (each on its own temporary database) |

## Demo accounts
`passenger / pass123`, `priya / pass123`, `admin / admin123` (stored as salted PBKDF2 hashes).

## Data
* Database: `data/railsync.db` (created and seeded on first start). Override with `-Drailsync.db=PATH`
  or the `RAILSYNC_DB` environment variable. Delete the file to reset everything.
* Receipts: `data/receipts/<PNR>.txt` · Reports: `data/reports/`.
* Seed trains/stations are illustrative, not live railway data.

## Where things are
`model/` entities (abstract `Train` + subclasses, `User`/`PassengerAccount`/`Admin`) · `repository/` generic
`Repository<T,ID>` + JDBC · `database/` connection, schema, seeder · `manager/` `ReservationManager`,
`WaitingListManager`, `FileManager` · `thread/` `SeatAllocationService` (synchronized + transaction),
`BookingTask`, `ConcurrencySimulation` · `service/` auth, search, admin, fares, reports, analytics ·
`awt/` event demos · `javafx/` UI · `exception/` custom exceptions.
