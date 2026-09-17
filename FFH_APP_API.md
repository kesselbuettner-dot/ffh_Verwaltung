# FFH Bestell-App API

Die Android-App nutzt die bestehende Spring-Boot-Anwendung über HTTPS und JWT.

## Authentifizierung

`POST /api/auth/login`

Das Login liefert den JWT. Für alle `/api/app/**`-Endpunkte wird dieser als
`Authorization: Bearer <token>` gesendet.

## Menü

- `GET /api/app/menu` – Getränke und Essen in einer Antwort
- `GET /api/app/menu/drinks` – nur Getränke
- `GET /api/app/menu/food` – nur Essen

Nur aktive Artikel mit verfügbarem Bestand werden im Menü ausgegeben.
Artikel werden über `ArticleType.DRINK` bzw. `ArticleType.FOOD` unterschieden.

## Warenkorb

Der Warenkorb wird pro Benutzer in Redis gespeichert und läuft nach 24 Stunden ab.

- `GET /api/app/cart`
- `PUT /api/app/cart`
- `DELETE /api/app/cart`

Beispiel für `PUT`:

```json
[
  {"articleId": 12, "quantity": 2},
  {"articleId": 31, "quantity": 1}
]
```

## Bestellungen

- `POST /api/app/orders` – Bestellung aufgeben
- `GET /api/app/orders` – eigene letzten 50 Bestellungen
- `GET /api/app/orders/{id}` – eigene Bestellung
- `POST /api/app/orders/{id}/cancel` – eigene Bestellung stornieren

Bei der Bestellung werden Preise immer serverseitig aus der Datenbank gelesen.
Die App übergibt keine Preise und keine `memberId`.

Die Identität des Mitglieds wird aus dem JWT bestimmt.

### Bestellung aufgeben

```json
{
  "items": [
    {"articleId": 12, "quantity": 2},
    {"articleId": 31, "quantity": 1}
  ]
}
```

Der Server prüft:

1. Benutzer und Mitglied
2. Aktivität der Artikel
3. Bestand
4. Guthaben
5. Preis und Gesamtsumme
6. Bestands- und Guthabenänderung
7. Speicherung von `Order` und `OrderItem`

Die gesamte Buchung läuft transaktional. Nach erfolgreicher Bestellung wird der Redis-Warenkorb geleert.

## Stornierung per App

`POST /api/app/orders/{id}/cancel`

Ein Mitglied kann eine eigene Bestellung im Status `NEW` oder `CONFIRMED` stornieren.
Dabei werden der Bestand und das Mitgliederguthaben zurückgebucht.

Ab `PREPARING` ist die Stornierung über die App nicht mehr möglich.

## Theke

Zusätzlich steht für die Theke zur Statussteuerung bereit:

`PUT /api/theke/orders/{id}/status`

```json
{"status":"CONFIRMED"}
```

Erlaubte Folge:

`NEW -> CONFIRMED -> PREPARING -> READY -> COMPLETED`

Die bestehende Theken-Bestellfunktion mit `Drink` bleibt für Altbestand kompatibel.
Neue App-Bestellungen verwenden `Article`.
