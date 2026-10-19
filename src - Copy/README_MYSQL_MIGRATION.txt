PHC PROJECT - MYSQL MIGRATION NOTES
===================================

WHAT CHANGED
------------
- FileHelper.java now uses MySQL instead of SQLite while keeping the same public
  methods used by the existing Swing screens.
- SQLiteToMySQLMigration.java is a one-time utility to copy tables and rows from
  data/phc.db into the local MySQL database.
- data/mysql.properties.example is a configuration template.

IMPORTANT: THE ZIP YOU PROVIDED DID NOT CONTAIN data/phc.db OR THE CSV FILES.
Copy your original data/phc.db into this project's data/ folder before migrating.
Keep a separate backup of the original database. The migration utility DROPS and
recreates tables with matching names in the target MySQL database, so run it only
against a target database that you are happy to replace.

SETUP
-----
1. Install/start MySQL Server on your laptop.
2. In MySQL Workbench, connect to your local server and run:
       CREATE DATABASE IF NOT EXISTS phc CHARACTER SET utf8mb4;
3. Copy data/mysql.properties.example to data/mysql.properties.
4. Edit data/mysql.properties and set the correct MySQL password. Do not upload
   mysql.properties to GitHub because it contains credentials.
5. Add MySQL Connector/J to the project's Java build path.
6. For the one-time data migration, also add the SQLite JDBC driver to the build
   path, place your original database at data/phc.db, then run
   SQLiteToMySQLMigration.java once. Check its console output for each table.
7. For normal app use, the SQLite JDBC driver is no longer needed. Keep MySQL
   Connector/J available at runtime (including in the runnable JAR export).
8. Run Main.java and test login, every management screen, reports, and CRUD
   operations before making a new runnable JAR.

CONFIGURATION
-------------
Default connection:
  jdbc:mysql://localhost:3306/phc
  username: root
  password: configured in data/mysql.properties

The server must be running when the application is used. This setup is for a
local demonstration on the computer hosting MySQL Server.

NOTES
-----
- The migration utility transfers SQLite table values as text, matching the
  original helper's string-based data model. Review the tables after migration.
- The CSV files are separate from the SQLite database; retain them if any other
  part of the app reads them directly.
- Do not share your MySQL password in chat or commit it to a public repository.
