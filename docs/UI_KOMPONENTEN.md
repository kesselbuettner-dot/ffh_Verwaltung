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

## Standard: drei Kategorien und druckbare Mitgliederübersicht

- Die Wehrleiter-Mitgliederübersicht zeigt in einer Tabelle **Mitglied | Qualifikationen | Zertifikate / Dokumente | Tauglichkeiten**. Jede der drei fachlichen Spalten enthält unabhängig voneinander nebeneinanderliegende, kompakte Kacheln mit Kürzel, typbezogener Farbe und Statusmuster. Leere Spalten bleiben leer; Mitgliedsnamen und andere Kategorien werden nicht verschoben.
- Im Baukasten ist jeder Kacheltyp explizit einer der drei Kategorien `QUALIFICATION`, `CERTIFICATE_DOCUMENT` oder `SUITABILITY` zugeordnet. Für bereits bestehende Typen `MEDICAL_DUE` und `DRIVERS_LICENSE` greifen bis zur späteren bewussten Umstellung die passenden bisherigen Bedeutungen Tauglichkeit bzw. Dokument; zugewiesene Mitglieds- und Prüfdaten werden nicht verändert.
- Der **Druckbericht** verwendet dieselben Daten, Filter, Sortierung und serverseitigen Berechtigungen wie die gerade angezeigte Tabelle. Er hat alle vier Spalten, die farbigen Kürzel-Kacheln, Schraffur für bald fällig, graue abgelaufene Kacheln und eine textliche Farb-/Statuslegende. A4 quer, wiederholte Tabellenüberschriften, möglichst kein Zeilenumbruch innerhalb eines Mitglieds.
- Beim Drucken im Browser für farbige Flächen gegebenenfalls „Hintergrundgrafiken drucken“ aktivieren. Der Druckdialog erlaubt „Als PDF speichern“; es wird keine ungeschützte PDF-Datei auf dem Server erzeugt. Gesundheitsbezogene Tauglichkeiten erscheinen ausschließlich für Mitglieder mit der vorhandenen Berechtigung für sensible Qualifikationen.
- Diese dreispaltige Tabelle ist die verbindliche UI-Vorlage für diese Mitgliederübersicht. Andere Module übernehmen nur passende wiederverwendbare Tabellen-, Filter-, Druck- und Berechtigungsregeln, nicht automatisch diese Fachkategorien.

## Qualifikationskacheln (bestehendes Modul)

Jede Kachel beschreibt einen **Typ**, pro Mitglied eine **Zuordnung**: Titel, Icon, optionale Ausstellungs-/Gültigkeitsdaten, Ergebnis/Prüfstatus, Fälligkeitsdatum, Vorwarnzeit, Prüfintervall und Berechtigung für Ansicht/Bearbeitung. Wehrleiter wählen aus einem Baukasten die Typen für ihre Übersicht; obere Filter nach Qualifikation/Fälligkeit/Status, hinter jedem Mitglied kompakte Statuskacheln. Persönlichkeits-/Gesundheitsdaten nur für eng definierte berechtigte Personen; keine sensiblen Detaildiagnosen in globalen Dashboards oder Push-Nachrichten.

## Beispiel-Abnahmekriterien
Neue Seite auf Desktop/Tablet/Handy: passende Spalten, Sortierung, Filter, leere Liste, Ladefehler, Rechte ohne Schreibfunktion, Datum unbekannt, Tastaturbedienung, Anmeldung/Abmeldung, Export und PWA nach Reload.

## Zentrale Component-Design-Templates – neu

**Einmal definieren, überall gleich gestalten.** Neue Seiten verwenden in der vorhandenen PWA `FWComponents` und die gemeinsamen CSS-Klassen aus `design-system.css`. Die Komponente trägt ihre Semantik, die zentrale CSS-Datei ihre Erscheinung. Komponenten sind DOM-Elemente und können in bestehende Dialoge/Seiten mit `append` oder `replaceChildren` eingebunden werden; kein Frameworkwechsel.

| Template | JS-Funktion | Zentrale CSS-Klassen | Standard |
|---|---|---|---|
| Seitenlayout | `FWComponents.page({title,description,actions,children})` | `.ds-page`, `.ds-page-header`, `.ds-page-actions` | Titel, Erklärung, rechte Aktionen, Inhalte |
| Karte/Kachel | `FWComponents.card({title,content,actions})` | `.ds-card`, `.ds-card-title` | einheitlicher Abstand, Radius, Fläche |
| Button | `FWComponents.button({label,variant,onClick,disabled})` | `.ds-btn`, `.ds-btn-primary/secondary/danger` | Primär/Sekundär/Löschen, Fokus und Touch |
| Such-/Formularfeld | `FWComponents.field({label,type,value,options,onChange})` | `.ds-field`, `.ds-input`, `.ds-select` | Label, 44 px Touchfläche, Validierung durch Fachmodul |
| Tabelle | `FWComponents.table({columns,rows,emptyMessage})` | `.ds-table-wrap`, `.ds-table` | Kopf, Zeilen, leere Liste, Scrollbereich |
| Status | `FWComponents.badge({label,variant})` | `.ds-badge`, `.is-success/warning/danger` | semantischer Text und Statusfarbe |
| Leerzustand | `FWComponents.empty({message})` | `.ds-empty` | einheitliche Leermeldung |
| Dialoginhalt | `FWComponents.modalContent({title,content,actions})` | `.ds-modal-content`, `.ds-modal-actions` | Kopf, Inhalt, Abschlussaktionen |

**Templates statt seitenweiser Kopien:** Zusätzliche Variationen/Komponenten werden zuerst hier und in `design-system.css`/`design-system.js` definiert. Erst danach wird eine neue Seite daraus zusammengesetzt. Responsive Utilities: `.u-flex`, `.u-grid`, `.u-grid-2/3`, `.u-gap-*`, `.u-p-*`, `.u-mt-*`, `.u-rounded-*`. Keine unüberschaubare Sammlung zufälliger Ad-hoc-Klassen.

**Zentraler Editor:** Administration → Designsystem ändert nur serverseitig validierte Werte für `space`, `radius`, `controlHeight`, `pageWidth`, `textSize`, `shadow`; bestehende Farbtheme-Einstellungen unter Erscheinungsbild werden übernommen. Die zentralen Werte gelten für alle neuen `.ds-*`-Komponenten. Datenbankgestützte Benutzerrollen, individuelle Stammdaten oder Altabläufe werden dadurch nicht verändert.

**Wichtige Grenzen:** Die neue `FWComponents.table`-Vorlage ist eine sichere Basis-Tabelle, kein fertiger Datenmanager; fachliche Sortierung, Filter, Pagination und Export sind vom jeweiligen Modul mit bestehenden zentralen Filterkomponenten zu ergänzen. Ein Render-Callback darf DOM-Knoten oder Klartext zurückgeben, niemals unbereinigten HTML-Code. Beim Umstellen einer vorhandenen Seite eine eigene Regression für Desktop, Handy, Berechtigungen und Datenänderung ergänzen.

## Sechs vollständige Seiten-Templates (verbindliche Vorlage für neue Module)

Die bisherigen Grundkomponenten (Button, Tabelle, Karte, Formularfeld) werden nun zu **ganzen Seiten** zusammengesetzt. Die zentrale Implementierung liegt in `src/main/resources/static/page-templates.js`, die gemeinsame Gestaltung in `page-templates.css`. Beide Dateien verwenden das vorhandene Designsystem mit `FWComponents`, den `.ds-*`-Klassen und den zentralen Farben, Schriftgrößen, Abständen und Rundungen. **Nicht die HTML-Struktur für jedes Modul erneut kopieren.**

| Template-ID | Zweck | Übergabe / Standard |
|---|---|---|
| `overview` | Dashboard und Bereichsübersichten | `title`, `description`, `metrics`, `panels`, `actions` |
| `management` | Verwaltungslisten | `title`, `actions`, `columns`, `rows`, `filters`, `onSearch`, `onReset` |
| `detail` | Mitglieds-/Geräte-/Fahrzeugdetails | `fields`, `status`, `tiles`, `history`, `actions` |
| `form` | Stammdaten- und Bearbeitungsformulare | `groups` oder `fields`, `onSubmit`, `onCancel` |
| `tasks` | Fälligkeiten, Prüfungen und Aufgaben | `rows`, `filters`, `columns`, `actions`, pro Zeile `action` |
| `settings` | Administration und Konfigurationsseiten | `sections`, `onSave` |

**Aufruf für eine neue Seite:**
```js
const template = window.FWPageTemplates;
const screen = template.render('management', {
  title: 'Geräteverwaltung',
  description: 'Geräte suchen und prüfen',
  actions: [window.FWComponents.button({
    label: '＋ Gerät', variant: 'primary', onClick: () => openDeviceForm()
  })],
  columns: [
    {key: 'name', label: 'Gerät'},
    {key: 'location', label: 'Standort'}
  ],
  rows: permittedDeviceRows,
  onSearch: term => filterDevices(term)
});
content.replaceChildren(screen);
```

`FWPageTemplates.list()` liefert den Katalog, `FWPageTemplates.preview('management')` eine sichere, datenfreie Vorschau, `FWPageTemplates.render(id,options)` die eigentliche Seite. Die einzelnen `overview`/`management`/…-Funktionen sind ebenfalls nutzbar. Ein Template führt keine API-Abfrage durch, legt keine Daten an und erteilt keine Rollenrechte; Fachmodule übergeben **nur bereits serverseitig autorisierte Daten** und fachliche Callbacks. Speichern, Druck, Sortierung und Statusübergänge bleiben explizite Fachlogik.

**Zentrale Anpassung:** Die `--ds-*`-Tokens und alle strukturellen Klassen für Seiten-Templates stehen in den beiden gemeinsamen CSS-Dateien. Administration → Designsystem verändert die validierten globalen Tokenwerte. Administration → Seiten-Templates zeigt sechs auswählbare, interaktive Demo-Vorschauen. Änderungen an gemeinsamen Klassen wirken auf **jede neue Seite, die das Template verwendet**. Die Galerie speichert keine separaten modulspezifischen Layoutkopien; so können Module nicht unbemerkt auseinanderdriften.

**Bestandschutz:** Die bisherigen Seiten werden nicht automatisch ersetzt. Insbesondere die Wehrleiter-Mitgliedertabelle mit ihren drei Fachspalten, Berechtigungen, Filter-/Druckfunktionen und gespeicherten Daten bleibt bis zu einer gesondert getesteten Migration unverändert.

## Administration als einheitliches Einstellungs-Template

**Adminbereich:** `adminSettingsPage(section)` rendert den gemeinsamen `FWPageTemplates.settings`-Rahmen mit responsive linker Seitennavigation und aktivem Abschnitt. Darin bleiben die bestehenden Module `members/accounts/roles/rights/menu/dashboard/master/appearance/design/templates/update` mit genau ihren bisherigen Daten, Rollen und Speicher-APIs erhalten. Neue Administrationsmodule müssen ihren Inhalt nur noch in `settingsContent` rendern und die zentrale Navigation ergänzen. Bestandsmodule verwenden die gemeinsamen `.ds-admin-module`-Tokens, statt separate Tab-/Seitenkopf-Layouts zu definieren.

## Vorlage „Meine Aufgaben → Geräteprüfung“

Neue Seite `DeviceCycleTasks.page()` verwendet `FWPageTemplates.tasks` und die gemeinsamen Komponenten für Status, Tabelle, Filter, Zuweisung und Prüfabschluss.

- Ein aktives Gerät mit gesetztem Prüfzyklus (`inspectionIntervalMonths`) bekommt **einmalig pro Geräte-ID und Fälligkeitsdatum** eine Aufgabe, sobald es binnen 30 Tagen fällig wird. Fehlt das erste Prüfdatum, wird die initiale Fälligkeit einmalig auf das heutige Datum gesetzt. Die tägliche Generierung läuft nur im Worker; die Gerätewartansicht generiert zusätzlich beim Öffnen. Keine unbegrenzte Erzeugung von Altzyklen.
- Offene, noch nicht zugewiesene Aufgaben liegen im **gemeinsamen Postfach der Rolle GERATEWART** (ADMIN sieht sie ebenfalls). Nur Gerätewart/ADMIN kann in jeder Gerätezeile mittels „Weitergeben“ ein aktives Mitglied mit freigegebenem Benutzerkonto wählen oder die Aufgabe ins Rollenpostfach zurückgeben.
- Zugewiesene Mitglieder sehen in „Meine Aufgaben → Geräteprüfung“ ausschließlich ihre eigenen Aufgaben; andere Geräte, Mitgliederdaten und die Zuweisungsliste sind nicht abrufbar. Ein Mitglied kann das Gerät als „Bestanden“, „Mit Mangel“ oder „Nicht bestanden“ abschließen. Mängel benötigen eine Bemerkung. Jeder Abschluss erfasst Benutzer/Datum und einen `DeviceInspection`-Historieneintrag; bei „Bestanden“ wird der nächste Termin aus dem Prüfzyklus berechnet. Bei Mangel wird die Fälligkeit nicht als bestanden vorgetragen und der Gerätestatus auf „DEFECTIVE“ gesetzt.
- Die neuen zyklischen Einzelaufgaben ergänzen die bestehende dienst-/terminbasierte Geräteprüfung; deren Event-/Serientermine und Prüflisten werden nicht gelöscht oder verändert. Zentralisierte UI-Templates ändern **keine** API-Berechtigungen: der Server validiert Rollen und Besitzer bei jeder Aktion.

Vor Produktivfreigabe: Mitglied mit GERATEWART/ADMIN, freigeschaltetes Mitglied, unfreigeschaltetes Mitglied und fremde Benutzer-IDs prüfen. Auch den Grenzfall „erstes Prüfdatum fehlt“, doppelte Tagesausführung, Mangel, Wiederholungsintervall, Browser-PWA-Cache und die bestehenden Geräteprüfprotokolle testen.
