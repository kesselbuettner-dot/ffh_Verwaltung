# FW-Cockpit — Designsystem

Status: **verbindliche Gestaltungsgrundlage**, einzelne Maße/Farbwerte sind Arbeitsstand und über Administration > Erscheinungsbild/Themes konfigurierbar. Bei Konflikten zwischen Entwurf und vorhandener funktionierender Oberfläche gilt: bestehende Funktion erhalten; Abweichung bewusst im PR dokumentieren.

## Grundlage und Markenführung
- Feste FW-Cockpit-Marke aus `/icons/fw-cockpit-brand.svg`; darf nicht versehentlich durch ein organisationsbezogenes Wachenlogo überschrieben werden. Organisationsname oben in der Navigation aus Stammdaten, Klick auf Logo/Name öffnet das nicht bearbeitbare Impressum.
- Eigenes Organisations-/Wachenlogo nur in dafür vorgesehenen Dashboard-/Druckbereichen; Softwarelogo als PWA-Icon/Favicon. Dark- und Light-Themes dürfen das Markenlogo nicht unsichtbar machen.
- Gemeinsame Design-Tokens (CSS Custom Properties) statt harter Farbwerte im Seiten-Code. Bestehende Tokens `--bg`, `--panel`, `--ink`, `--muted`, `--line`, `--accent`, `--nav`, `--blue`, `--ok`, `--warn` bleiben kompatibel.

## Themes und Buttonfarben
Semantische Tokens (Light/Dark werden jeweils passend aufgelöst):
| Zweck | Token (neu) | Einsatz |
|---|---|---|
| Fläche/Text | `--ui-page`, `--ui-surface`, `--ui-text`, `--ui-subtle` | App und Cards |
| Primäraktion | `--ui-primary`, `--ui-primary-text` | Speichern, Anlegen, Bestätigen |
| Sekundäraktion | `--ui-secondary`, `--ui-secondary-text` | Bearbeiten, Filter, Zurück |
| Warnung | `--ui-warning`, `--ui-warning-text` | Prüffrist demnächst, Rückfrage |
| Gefahr | `--ui-danger`, `--ui-danger-text` | Löschen, negative Prüfung, irreversibel |
| Erfolg | `--ui-success`, `--ui-success-text` | Prüfergebnis positiv, erfolgreich gespeichert |
| Fokus und Rahmen | `--ui-focus`, `--ui-border` | Tastatur, Tabellen, Inputs |

Ein Button verwendet eine **Bedeutung**, keine frei gewählte Farbe je Modul. Maximal eine dominante Primäraktion pro Dialog/Kartenkopf. Gefahraktion rot mit expliziter Bestätigung, niemals dieselbe Optik wie Speichern. Erfolg nicht allein durch Grün kennzeichnen: Text/Icon ergänzen. Button immer mit sichtbarem Hover-, Fokus-, Disabled- und Ladezustand. Der Administrationsbereich darf Theme-Farben konfigurieren, aber Fokus/Lesbarkeit und semantische Unterschiede müssen erhalten bleiben.

Die vorhandenen Klassen `.btn.primary`, `.btn.secondary`, `.btn.danger`, `.badge`, `.panel`, `.table`, `.field` sind die Migrationsbasis; keine sofortige globale Neugestaltung ohne Sichttest aller Module.

## Einheitlicher Seitenaufbau
1. Titelzeile: links Icon, H1, Kurzbeschreibung; rechts höchstens zwei Hauptaktionen inkl. „Neu“ oder „Aktualisieren“.
2. Darunter Suchfeld/Filterchips, optional Datum/Status/Verantwortliche, Aktionen „Filter zurücksetzen“ und Export nur mit Berechtigung.
3. Inhalt in Cards/Panels oder Tabelle; keine doppelte Datenerfassungsmaske für denselben Datentyp.
4. Detailansicht in einheitlicher Karte/Dialog mit Kopf, Feldern, Status, Historie und Abschlussleiste. Verlassen mit ungespeicherten Änderungen abfangen.
5. Leere Zustände („Keine Einträge“), Laden, Fehlermeldung und Erfolg nach gleichem Muster. Löschaktionen sind nie Standard-Primärbutton.

## Responsive Navigation
Desktop: Seitenmenü mit gruppierten oder einzeln stehenden Seiten, aufklappbare Gruppen; aktive Eltern bleiben offen. Tablet/Handy: Schublade + untere Leiste mit maximal **3 administrativ gewählten, rollenberechtigten Schnellzugriffen und festem Menüknopf**. Auf kleinen Displays Icon-only plus `aria-label`, Tastatur- und Touch-Bedienung. Nicht berechtigte Schnellzugriffe ausblenden, kein automatisches Ersatzrecht.

## Typografie und Maße (Startwerte, bei Bedarf im Designreview ändern)
- Schrift: Inter/Systemschrift, Body 14–16 px, H1 22–28 px, Label 12–14 px; System-Zoom und Textvergrößerung respektieren.
- Grid-Abstände in 4/8/12/16/24 px; Cards 12–16 px Radius; Buttons/Input-Touchziele ungefähr 44×44 px oder größer.
- Tabellen am Desktop, am Handy Cards oder horizontal scrollbarer Bereich mit sichtbaren Spaltenüberschriften; nie stillschweigend Pflichtfelder abschneiden.
- Standardmäßig ausreichender Kontrast in Light/Dark, sichtbarer Tastaturfokus, keine ausschließlich farbliche Statuskommunikation.

## Prüf- und Freigabeprozess
Neues Modul: Designkomponenten festlegen → Rollen/API-Guard klären → Mobilansicht testen → bestehende Funktionen vergleichen → CI/Manuelle Smoke-Tests protokollieren. Ein Screenshot ist Referenz, kein Ersatz für gemeinsame CSS-Komponenten.
