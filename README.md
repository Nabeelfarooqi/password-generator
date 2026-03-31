# Password Manager

A Java Swing desktop application that generates cryptographically secure passwords and stores them locally using SQLite.

## Features
- Generates secure 12-character passwords using SecureRandom
- Makes sure there is at least one uppercase, lowercase, number, and special character
- Save passwords with site and username
- View all saved entries in a password.db file
- Remove saved entries by selecting a row and clicking Remove Selected
- Local storage in SQLite

## Requirements
- Java 17+
- Download the following jars and add them via **File → Project Structure → Libraries → + → Java**:
    - [sqlite-jdbc-3.45.0.0.jar](https://repo1.maven.org/maven2/org/xerial/sqlite-jdbc/3.45.0.0/sqlite-jdbc-3.45.0.0.jar)
    - [slf4j-api-2.0.9.jar](https://repo1.maven.org/maven2/org/slf4j/slf4j-api/2.0.9/slf4j-api-2.0.9.jar)
    - [slf4j-simple-2.0.9.jar](https://repo1.maven.org/maven2/org/slf4j/slf4j-simple/2.0.9/slf4j-simple-2.0.9.jar)

## How to Run
Either clone the entire repo or open the project into your personal IDE. Make sure to add sqlite-jdbc from Maven. Run `GeneratePasswordButton.java`

## Files
- `src/PasswordGenerator.java` — core password generation logic
- `src/GeneratePasswordButton.java` — Swing GUI + database integration
- `src/DatabaseManager.java` — SQLite connection and queries
- `passwords.db` — auto-created on first run