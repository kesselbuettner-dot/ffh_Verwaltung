# FW-Cockpit — Komponentenbibliothek und Seitenvorlage

Verbindliche Beschreibung für neue Seiten; `docs/DESIGN_SYSTEM.md` bestimmt Theme/Farben/Abstände. Vor einem neuen Widget bestehende `.panel`, `.table`, `.field`, `.btn` und die Menü-Designer-Elemente prüfen.

## Seite
```text
[Icon] Seitentitel                [Sekundäraktion] [Primäraktion]
Kurzbeschreibung
[Suche________________] [Status ▼] [Fällig bis ▼] [Filter zurücksetzen]
┌ Übersicht/Tabelle oder Karten ────────────────────────────────┐
│ Spalten: Eintrag · Status · Termin · Verantwortlich · Aktion  │
└───────────────────────────────────────────────────────────────┘
```
Die Verwaltung eines Datensatzes hat genau **eine** führende Seite. In anderen Modulen nur Links zur führenden Bearbeitungsstelle oder ausdrücklich freigegebene Teilaktionen.

## Tabellen
- Kopf ist sortierbar per Klick/Tap, aktiver Sortierpfeil sichtbar; Suchfeld und Filter liegen **oberhalb** der Tabelle; keine nur am PC nutzbaren Dropdowns/Rechtsklicks.
- Erste Spalte identifiziert den Datensatz, Fristen einheitlich als Datum im örtlichen Format, leere Zellen als „–“.
- Zustände durch Text + Icon + Badge: offen, demnächst fällig, überfällig, geprüft, nicht bestanden, deaktiviert. Überfälligkeit nicht durch rote Hintergrundfarbe allein.
- Tabellenaktionen: Ansehen standardmäßig, Bearbeiten nur mit Recht; Löschen getrennt und bestätigt. Touch: Zeile als Karte, große Aktionen, keine versteckte Hover-only-Funktion.
- Paginierung/virtuelle Liste bei größeren Beständen, feste Tabellenköpfe nur wenn mobil gut nutzbar; Export berücksichtigt Filter und Berechtigungen.

## Formulare und Dialoge
- `.field > label + input/select/textarea`, Kennzeichnung Pflicht/optional, Inline-Validierung, verständliche Fehler direkt am Feld.
- Datum der Ausstellung, Fälligkeit, Prüfer, Ergebnis als getrennte typisierte Felder; „unbekannt“ darf nicht als erfundenes Datum ersetzt werden.
- Bearbeiten nur an führender Stelle; abbrechen ohne Seiteneffekt, speichern mit Servervalidierung und Erfolgsmeldung.
- Destruktive Aktion ist `.btn.danger`, Bestätigung nennt Objekt und Folgen.
- Dialog: Titel, relevanter Inhalt, unten „Abbrechen“ links, „Speichern/Abschließen“ rechts; für Handy Vollbild-Alternative erwägen.
- Auswahl aus Mitgliederstamm erfolgt über Mitglieds-ID, niemals ungeprüft über nur ähnliche Namen.

## Buttons/Themes
| Variante | Klasse (kompatibler Start) | Zweck |
|---|---|---|
| Primär | `.btn.primary` | Anlegen, Speichern, Abschließen |
| Sekundär | `.btn.secondary` | Filter, Anzeigen, Bearbeiten, Zurück |
| Gefährlich | `.btn.danger` | Löschen, dauerhaft verwerfen |
| Status | `.badge` + semantische Variante | Fällig, positiv, negativ, gesperrt |

Bei künftiger Refaktorierung UI-Tokens aus `DESIGN_SYSTEM.md` auf die vorhandenen CSS-Klassen mappen; zunächst keine bestehenden Module durch pauschale CSS-Overrides ungetestet verändern.

## Verbindliche Such-/Filter-/Sortierzeile für Tabellen und Kachelübersichten

- Jede Seite mit durchsuchbaren Einträgen nutzt die gemeinsame CSS-Komponente `.ui-filterbar`: ein Suchfeld, beschriftete Status-/Kategorie-Filter, eine **explizite Sortierauswahl** und eine Aktion zum Zurücksetzen. Für die Felder `.ui-filterfield`, für die Suchspalte `.ui-filter-search`, für Aktionen `.ui-filter-actions`; keine verstreuten unbeschrifteten Selects oder pro Modul abweichenden Feldhöhen.
- Desktop: gleichmäßige Spalten mit einheitlichen 42-px-Eingabefeldern und Aktionen am Ende der Zeile. Tablet: zweispaltig; Handy: Suche volle Breite, Filter in zwei Spalten, Aktionen volle Breite. Vorhandene Filter-/Sortierauswahl nach dem Neuzeichnen beibehalten.
- Sortierung und Suche bleiben unabhängig von den Statusfiltern. Das Zurücksetzen stellt alle Filter, Suchtexte und Sortierung gemeinsam zurück. Bei Filterung nach Status oder Qualifikation verschwinden Mitglieder ohne passenden Treffer; ohne Filter bleiben auch Mitglieder ohne Kacheln sichtbar.
- Die Mitgliederübersicht der Wehrleitung zeigt links ein kleines Avatarbild bzw. Initialen, rechts nebeneinanderliegende **kleine Qualifikationskacheln mit nur dem konfigurierten Kürzel**. Der vollständige Titel und der Status sind per Tooltip/zugänglichem Namen abrufbar, Detail- und Datumsbearbeitung erfolgt beim Klick mit Berechtigung.
- Gültige Kacheln besitzen eine über den Typ stabil definierte Farbe; **bald fällig: schraffiert**, **abgelaufen: grau**, jeweils mit lesbarem Tooltip und prüfbarem Status. Farbe allein ist keine Statusinformation. Sehr lange Kürzel auf zehn Zeichen begrenzen; empfohlen zwei bis vier Zeichen.
- Mitgliedsavatar ausschließlich bei Administration > Mitgliederstammdaten anlegen/bearbeiten; kleines, komprimiertes Foto nur für Berechtigte ausgeben. Ohne Avatar Initialen aus dem Mitgliedernamen anzeigen. Das Logo der Anwendung ist kein Mitgliederavatar.
- Dieselbe Filterzeile und das responsive Verhalten sind für künftig neue Tabellen und Listen verbindlich; kein neues modulspezifisches Layout ohne begründete Ergänzung dieser Datei und der gemeinsamen CSS-Klassen.

## Qualifikationskacheln (bestehendes Modul)

Jede Kachel beschreibt einen **Typ**, pro Mitglied eine **Zuordnung**: Titel, Icon, optionale Ausstellungs-/Gültigkeitsdaten, Ergebnis/Prüfstatus, Fälligkeitsdatum, Vorwarnzeit, Prüfintervall und Berechtigung für Ansicht/Bearbeitung. Wehrleiter wählen aus einem Baukasten die Typen für ihre Übersicht; obere Filter nach Qualifikation/Fälligkeit/Status, hinter jedem Mitglied kompakte Statuskacheln. Persönlichkeits-/Gesundheitsdaten nur für eng definierte berechtigte Personen; keine sensiblen Detaildiagnosen in globalen Dashboards oder Push-Nachrichten.

## Beispiel-Abnahmekriterien
Neue Seite auf Desktop/Tablet/Handy: passende Spalten, Sortierung, Filter, leere Liste, Ladefehler, Rechte ohne Schreibfunktion, Datum unbekannt, Tastaturbedienung, Anmeldung/Abmeldung, Export und PWA nach Reload.
