# Cloud Password Vault

A zero-knowledge password manager built with Java. Passwords are encrypted client-side with AES-256-GCM before ever leaving the machine, so the cloud only ever stores ciphertext. Includes a JavaFX desktop app, a local REST API, and a Chrome extension with autofill and credential capture.

## Architecture

Chrome Extension → Local API (127.0.0.1:17431) → JavaFX Desktop App → AWS DynamoDB

All encryption and decryption happens in the desktop app. The encryption key is derived from the user's master password using PBKDF2 (65,536 iterations, SHA-256) with a unique random salt per entry. AWS stores only encrypted blobs and can never read a password. The master password itself is never stored; it is verified via an encrypted check token.

## Features

**Desktop app (JavaFX)**
- Master password unlock with attempt limits and animated feedback
- Save existing passwords or generate cryptographically secure ones (SecureRandom)
- Search, reveal/hide toggle, per-entry copy, and one-click Open to launch each site's login page
- Dark themed UI with CSS styling and animations

**Chrome extension**
- Detects login forms and injects fill and generate icons next to password fields
- Autofills saved credentials for the current site
- Captures submitted logins and offers to save them
- In-popup credential management with site auto-detection

**Security**
- AES-256-GCM authenticated encryption with tamper detection
- Unique salt and IV per encrypted entry
- AWS IAM least-privilege policy scoped to a single DynamoDB table and only the four actions used (GetItem, PutItem, DeleteItem, Scan)
- Local API bound to 127.0.0.1 only
- AWS credentials stored in the standard AWS credentials file, never in code or the repository

## Tech Stack

Java 21, JavaFX 21, AWS SDK for Java v2 (DynamoDB), Maven, JDK built-in HTTP server, Chrome Extension (Manifest V3), AES-256-GCM with PBKDF2WithHmacSHA256 key derivation

## Setup

1. Create a DynamoDB table named `PasswordVault` with partition key `id` (String)
2. Create an IAM user with a least-privilege policy scoped to that table and generate access keys
3. Place credentials in `~/.aws/credentials` and set your region in `~/.aws/config`
4. In `pom.xml`, set the JavaFX classifier for your platform: `win`, `mac-aarch64`, `mac`, or `linux`
5. Build and run:

   mvn clean install
   mvn javafx:run

6. Load the extension: Chrome → chrome://extensions → Developer mode → Load unpacked → select the browser-extension folder
7. The desktop app must be running and unlocked for the extension to fill or save credentials

## Project History

v1 was a Java Swing app storing passwords in a local SQLite database. v2 is a full rewrite: cloud storage on DynamoDB, client-side encryption, a JavaFX interface, and browser integration. The commit history reflects the migration.