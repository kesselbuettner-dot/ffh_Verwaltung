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


## Lager / Einkaufsliste / Wareneingang

Der aktuelle Stand enthält:
- Artikel mit EAN/GTIN, Bestand und Warnschwelle.
- Verkäufe reduzieren den Bestand automatisch; Stornos buchen den Bestand zurück.
- Admin-Unterpunkte unter „Verkauf“: Einkaufsliste, Wareneingang, Artikel verwalten.
- Einkaufsliste zeigt Bestand, Warnschwelle, Vorschlagsmenge, letzten und mengen-gewichteten mittleren Einkaufspreis.
- Wareneingänge erfassen Menge, Einkaufspreis je Stück, Datum, Lieferant und Notiz.
- Barcode-Erfassung per Kamera über die Browser-`BarcodeDetector`-API; zusätzlich manuelle EAN-Eingabe. Für Kamera-Zugriff muss die Anwendung über HTTPS bzw. localhost aufgerufen werden und der Browser muss die BarcodeDetector-API unterstützen.
- Marktguru ist als Backend-Adapter vorbereitet. Die konkrete API-URL und der API-Schlüssel werden nicht fest im Quellcode hinterlegt, sondern über `MARKTGURU_ENDPOINT`, `MARKTGURU_API_KEY` und `MARKTGURU_ZIP` gesetzt.

### Marktguru

Die öffentlich auffindbaren Marktguru-Seiten beschreiben Angebote, regionale Unterschiede und die Nutzung von EAN/GTIN, veröffentlichen aber keine belastbare öffentliche Developer-API-Spezifikation für diesen Anwendungsfall. Deshalb wurde bewusst kein nicht dokumentierter interner Endpoint fest verdrahtet. Sobald ein freigegebener Marktguru-API-Endpunkt bzw. dessen Dokumentation vorliegt, wird nur der Adapter konfiguriert bzw. auf das offizielle Response-Schema angepasst.

## Marktguru-API – konkrete Integration

Die Einkaufsliste verwendet die Marktguru-Angebotssuche:

`GET https://api.marktguru.de/api/v1/offers/search?as=web&limit=24&offset=0&q=<SUCHTEXT>&zipCode=<PLZ>`

Die Anfrage verwendet die Header:

- `x-clientkey: <MARKTGURU_CLIENT_KEY>`
- `x-apikey: <MARKTGURU_API_KEY>`

Die Einkaufsliste fragt Marktguru über den **Artikeltext/Namen** (`Drink.name`) ab. Die EAN wird dabei nicht mehr als Marktguru-Suchanfrage verwendet. Die PLZ kommt aus `MARKTGURU_ZIP`.

### Relevantes Antwortformat

Die Antwort ist ein JSON-Objekt mit `results[]`. Ein Angebot enthält unter anderem:

```json
{
  "id": 18323273,
  "description": "(+ 3.10 Pfand) je Ka. 20 x 0,5-l-Fl.",
  "price": 9.99,
  "oldPrice": null,
  "referencePrice": 1.00,
  "requiresLoyalityMembership": false,
  "validityDates": [
    { "from": "2025-08-27T22:00:00Z", "to": "2025-09-03T18:00:00Z" }
  ],
  "advertisers": [
    { "uniqueName": "kaufland", "name": "Kaufland" }
  ],
  "product": { "id": 37383, "name": "Pils" },
  "unit": { "shortName": "l", "id": 1, "name": "Liter" }
}
```

Die Integration wertet alle `results[]` aus und nimmt für die Einkaufsliste das günstigste Angebot mit einem gültigen Preis. Zusätzlich werden Händler, Produkt, Beschreibung, Aktionspreis, alter Preis, Referenzpreis, Gültigkeit und Kundenkarten-Hinweis an das Frontend übertragen.

Die öffentlich auffindbaren Quellen bestätigen dieses konkrete Response-Schema; eine offizielle öffentliche Developer-Dokumentation von marktguru für diese Schnittstelle konnte nicht gefunden werden. Die verwendeten Header und der Endpoint stammen daher aus der beobachtbaren Web-API bzw. Community-Integrationen und sollten nur im Rahmen einer zulässigen Nutzung eingesetzt werden.


## Marktguru Gebinde-Preisvergleich

Marktguru-Angebote werden beim Preisvergleich ausschließlich anhand der **Einzelgröße** des Artikels abgeglichen. Die Anzahl des Marktguru-Einkaufsgebindes wird bewusst ignoriert. Historische Einkaufspreise werden ebenfalls nur gegen Einkäufe derselben Einzelgröße verglichen. Marktguru-Suchen verwenden `Artikelname + Einzelgröße`, niemals die Gebindeanzahl.

Im Artikelstamm sind Einzelgröße (`sizeVolume`, `sizeUnitShortName`) und Einkaufsgebinde (`packageQuantity`, `packageVolume`, `packageUnitShortName`) getrennt. Für ältere Artikel dient `packageVolume/packageUnitShortName` als Rückfallwert für die Einzelgröße.

Marktguru-Angebotspreise werden für den Vergleich auf **Preis pro Stück** normalisiert (`price / quantity`), weil historische Einkaufspreise als Stückpreise geführt werden. Die Gebindegröße des Marktguru-Angebots bleibt nur als Anzeigeinformation erhalten. `spring.jpa.hibernate.ddl-auto=update` ergänzt die Spalten beim nächsten Start automatisch.

### Kasse, Einzelgröße und Einkaufsgebinde

Die Kasse verkauft ausschließlich einzelne Stücke. In `Kasse / Theke` wird die **Einzelgröße** (`sizeVolume + sizeUnitShortName`) angezeigt, aber niemals das Einkaufsgebinde. Der Bestand wird bei einem Verkauf um die tatsächlich verkaufte Stückzahl reduziert. Einkaufsgebinde bleiben auf Einkauf/Wareneingang und Marktguru beschränkt.

## Interaktive Fahrzeugbeladung V2

Die vorhandene Fahrzeugverwaltung verwendet weiterhin `fire_vehicles`, `vehicle_compartments` und `devices`. Ergänzt wurde ausschließlich die untergeordnete Tabelle `vehicle_compartment_elements` für Regalboden, Schublade, Auszug, Halterung, Trennwand, Gerätekasten und freie Ablage.

- Die Ansichten Fahrerseite, Heck, Beifahrerseite, Front, Mannschaftsraum und Dach basieren auf skalierbarem SVG.
- Geräteräume liegen innerhalb der Fahrzeugzeichnung und öffnen ihren Rollladen durch Antippen.
- Die Detailansicht besitzt einen getrennten Bearbeitungsmodus. Dadurch können Geräte im normalen Ansichtsmodus nicht versehentlich verschoben werden.
- Position, Größe, Drehwinkel, Ebene, Gruppe und Ablageelement werden pro Gerät serverseitig gespeichert.
- Geräteraumelemente werden ebenfalls mit relativer Position, Größe, Drehwinkel und Ebene gespeichert.
- Gruppierte Geräte bleiben einzelne Gerätedatensätze. Die Gruppe wird lediglich über eine gemeinsame Gruppierungs-ID dargestellt.
- Prüfpflicht und Defektstatus verwenden weiterhin die vorhandene Geräte- und Prüfverwaltung. Es gibt keine zweite Defektdatenbank.
- Schreibende Funktionen benötigen weiterhin `fire.vehicles.write` und bei Geräteänderungen zusätzlich `fire.devices.write`.

Bestehende Gerätezuordnungen bleiben erhalten. Neue Felder sind nullable und werden nach dem Hibernate-Schema-Update durch `LegacySchemaBackfill` mit sicheren Standardwerten ergänzt.
