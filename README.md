# FFH Verwaltung App V2 – Login & Rollen

Java 21 / Spring Boot / PostgreSQL / Redis / REST / JWT.

## Rollen

- ADMIN: Benutzer, Mitglieder, Getränke und Systemverwaltung
- THEKE: Mitglieder lesen und Getränke buchen
- MEMBER: eigener Mitgliederbereich; weitere persönliche Endpunkte folgen

## Start

```bash
cp .env.example .env
# .env anpassen
docker compose up -d --build
```

Web: http://SERVER-IP:8080/
Swagger: http://SERVER-IP:8080/swagger-ui.html

## Erstes Admin-Konto

Beim ersten Start wird für die initiale Einrichtung automatisch angelegt:

Benutzer: `admin`
Passwort: `admin123!`

**Nach dem ersten Login muss dieses Konto ersetzt bzw. das Passwort geändert werden.**
Für einen produktiven Betrieb sollte das automatische Seed-Konto entfernt und ein sicherer Initialisierungsmechanismus verwendet werden.

## Benutzer anlegen

Admin-JWT holen:

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123!"}'
```

Danach Benutzer über Swagger oder:

```http
POST /api/admin/users
Authorization: Bearer <TOKEN>
Content-Type: application/json

{
  "username": "theke1",
  "password": "sicheres-passwort",
  "role": "THEKE",
  "memberId": null
}
```

Für ein Mitglied kann `role` auf `MEMBER` und `memberId` auf die entsprechende Mitglieds-ID gesetzt werden.

## APK

Die spätere Android-App verwendet exakt dieselbe REST-API:

```text
POST /api/auth/login
GET  /api/drinks
GET  /api/member/...
POST /api/bookings
```

Das JWT wird als

```text
Authorization: Bearer <TOKEN>
```

gesendet.

## Nächste sinnvolle Ausbaustufe

1. Refresh Tokens / Sessionverwaltung
2. Passwort ändern / Passwort vergessen
3. echte rollenabhängige Dashboards
4. persönlicher Deckel nur für das angemeldete Mitglied
5. Theken-PIN/QR-Code
6. Produkte/EAN und Angebotsfinder
7. PayPal
8. Audit-Log
