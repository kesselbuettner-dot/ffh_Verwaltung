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


## Verbindliches komponentenbasiertes Designsystem (ab Release-Branch 2026-09-22)

Die App verwendet weiterhin Spring Boot mit einer bestehenden HTML/JavaScript-PWA; die neuen **Tailwind-inspirierten, modular kombinierbaren CSS-Hilfsklassen** sind lokal und erfordern weder CDN noch Node-Build noch Tailwind-JIT. Es handelt sich um eine gezielt kleine Utility-First-Schicht, **nicht** um das vollständige Tailwind-CSS-Paket. Alte Module werden nicht automatisch umgestaltet.

**Zentrale Stellen (nicht kopieren):**
- `src/main/resources/static/design-system.css`: Design-Tokens (Abstand, Rundung, Schrift, Kartenbreite, Schatten), Utility-Klassen `.u-*` und **alle verbindlichen Komponentenklassen `.ds-*`**.
- `src/main/resources/static/design-system.js`: `window.FWComponents` mit DOM-basierten Vorlagen `page`, `card`, `button`, `table`, `field`, `badge`, `empty`, `modalContent`. HTML und Attributwerte werden nicht aus unkontrolliertem Benutzertext zusammengesetzt; Textfelder nutzen `textContent`.
- `src/main/resources/static/ui-theme.css`: zentrale vorhandene semantische Light-/Dark-Farben und Alt-Klassen; das FW-Cockpit-Softwarelogo bleibt unabhängig davon.
- **Administration → Designsystem**: Farben bleiben unter „Erscheinungsbild“, während die Systemkomponenten hier zentral Rundungen, Standardabstände, Grundschriftgröße, Eingabehöhe, Seitenbreite und Schatten erhalten. Live-Vorschau; Speichern über gesonderten, serverseitig auf ADMIN begrenzten Endpunkt `PUT /api/settings/design-system`; `GET` ist nur für angemeldete Benutzer freigegeben. Die Daten bleiben in der AppSettings-Tabelle und ändern **keine** Rollen, Menüs, Stammdaten oder Benutzerrechte.
- Gemeinsame zentrale Änderungen können entweder global in CSS/JS für zukünftige Seiten oder bei den validierten Eigenschaften ohne Quellcode in der Administration vorgenommen werden. Bereits bestehende Seiten wechseln **nicht automatisch** zur neuen Vorlage und werden einzeln mit Regressionstests migriert.

### Vorgegebene Vorlagen für neue Seiten
```js
const ui = window.FWComponents;
const buttons = [
  ui.button({label: 'Neues Mitglied', variant: 'primary', onClick: openMemberForm}),
  ui.button({label: 'Zurück', variant: 'secondary', onClick: goBack})
];
const search = ui.field({label: 'Suche', type: 'search', onChange: filterMembers});
const records = ui.table({
  columns: [
    {key: 'name', label: 'Mitglied'},
    {key: 'status', label: 'Status', render: member => ui.badge({
      label: member.status, variant: member.active ? 'success' : 'warning'
    })}
  ],
  rows: memberRows
});
const screen = ui.page({
  title: 'Mitgliederverwaltung',
  description: 'Überblick und Aufgaben',
  actions: buttons,
  children: [ui.card({title: 'Filter', content: search}),
             ui.card({title: 'Mitglieder', content: records})]
});
content.replaceChildren(screen);
```

Die tatsächliche Anzeige der Buttons und Aktionen muss zusätzlich zur serverseitigen API-Autorisierung auf dem erforderlichen **effektiven Recht** beruhen. Eine reine Menüsichtbarkeit ersetzt niemals eine Serverprüfung. Alte Seiten haben Bestandschutz, neue Seiten verwenden den gemeinsamen Komponentenbaukasten.
