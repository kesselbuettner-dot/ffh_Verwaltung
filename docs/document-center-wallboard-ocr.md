# FFH Verwaltung: Dokumentenverwaltung, OCR und Wachendisplay

Stand: 25.09.2026. Diese Erweiterung wird über GitHub-PR #40 bereitgestellt.

## Dokumente und lokale Erkennung

- Dokumente öffnen: **Weitere Bereiche > Dokumente**. Je nach Rollen-/Rechteverwaltung muss `documents.read`, `documents.write` bzw. `documents.delete` explizit vergeben werden. Administration und Vorstand besitzen die vorgesehene Verwaltungsfreigabe.
- Vertrauliche Dokumente (`RESTRICTED`) sind ausschließlich für Vorstand/Administration abrufbar und erscheinen nicht auf dem Wachendisplay. `MEMBERS` bedeutet **nicht öffentlich**: Nur angemeldete Benutzer mit dem passenden Dokumenten-Leserecht sehen diese Dokumente.
- Hochgeladene PDF-, DOCX-, TXT-, JPG- und PNG-Dateien bleiben unter dem privaten Docker-Volume `private_docs` außerhalb der öffentlichen `/uploads/`-Routen. Alle Downloads durchlaufen die Dokumentenberechtigungen.
- Nach Upload startet ein Auftrag für den vorhandenen **worker**. Er verarbeitet wenige Versionen pro Durchlauf. Bei PDFs wird zuerst auswählbarer Text gelesen, andernfalls werden maximal die ersten drei Seiten lokal mit Tesseract OCR bearbeitet. ZIP/Office- oder Scanvorlagen mit schlecht lesbarem Text können unvollständig bleiben.
- Im Versionsdialog den OCR-Status aktualisieren. Bei Fehlschlag kann ein berechtigter Bearbeiter die Erkennung erneut anstoßen. Im Analyse-Dialog erscheinen Kategorie, erkannte Daten und nur exakte Seriennummerntreffer aus dem Gerätebestand, soweit der Benutzer dort Leserecht besitzt.
- **Keine automatische Änderung von Gerätedaten, Prüfergebnissen oder Unterschriften.** Metadaten wie Kategorie und Frist können erst nach ausdrücklicher Sichtprüfung und Bestätigung übernommen werden.
- Bestehende Versionen bleiben erhalten. Archivieren blendet Dokumente aus, löscht aber keine Dateien oder Versionshistorie. Bei Wiederherstellung müssen **PostgreSQL und das Volume `private_docs` gemeinsam** gesichert und wiederhergestellt werden.

## Externe Auswertung: standardmäßig vollständig ausgeschaltet

Es wird kein externes KI-System vorausgesetzt oder ohne Aktivierung aufgerufen. Nur Administration/Vorstand dürfen **nicht vertrauliche** (`MEMBERS`) Dokumente optional extern auswerten lassen, und zwar nur nach zusätzlicher Einzelfreigabe. Der Benutzer sieht den lokalen OCR-Text, kann Angaben manuell entfernen und bestätigt vor jeder Übertragung explizit, dass der **tatsächlich zu übertragende Text** weder vertraulich noch personenbezogen ist. Die Datei selbst wird niemals versendet; nur der bestätigte Ausschnitt (max. 6.000 Zeichen) an den konfigurierten Anbieter. Die Antwort sind unverbindliche Vorschläge und verändert keine Daten.

Es werden nur die zustimmende Person, Datum, Dokument-/Versionskennung, Provider-Hostname, Ergebnis und SHA-256 des übermittelten Textes protokolliert, **nicht** der Text oder die Anbieterantwort. Der Dienst erwartet eine kompatible Chat-Completion-HTTPS-API und ist erst verfügbar, wenn im `.env` folgende vier Variablen *gemeinsam* gesetzt sind:

```env
DOC_AI_ENDPOINT=
DOC_AI_API_KEY=
DOC_AI_MODEL=
DOC_AI_ALLOWED_HOST=
```

`DOC_AI_ALLOWED_HOST` muss exakt zum HTTPS-Endpoint passen. Solange kein geeigneter Anbieter mit geklärtem Datenschutz-/Auftragsverarbeitungsverhältnis ausgewählt wurde, **alle vier Variablen leer lassen**. Diese Konfiguration darf nicht auf dem Fernseher oder im GitHub-Repository gespeichert werden. Ein Rollenrecht allein gestattet keinen externen Transfer.

## Wachendisplay

- Ein berechtigter Benutzer öffnet im Menü **Wachendisplay** die eigene Ansicht `/display.html`. Vor dem Betrieb im Gerätehaus ein ausschließlich hierfür bestimmtes Benutzerkonto erwägen, statt ein Administrator-Konto am Fernseher anzumelden.
- In der Meldungsverwaltung entscheidet ein separater Haken **Auf dem Wachendisplay zeigen** pro Meldung oder Termin über die Freigabe. Der TV-Endpunkt gibt ausschließlich aktive, explizit freigegebene Datensätze und keine Dokumente zurück.
- Allgemeine interne Dienst- und Ausbildungstermine erscheinen nicht automatisch auf dem TV: nur explizit veröffentlichte Termine der Meldungsverwaltung.
- Für Fire TV / Fully Kiosk sind automatische Querformat-Skalierung, Vollbild, Größenregler und ein konfigurierbares Aktualisierungsintervall vorhanden. Bei fehlender Netzwerkverbindung bleiben zuletzt geladene TV-Kacheln in der aktuellen Sitzung sichtbar, bis die Seite neu gestartet wird. Nach Sitzungsablauf ist eine erneute Anmeldung nötig.

## Tests und Veröffentlichung

GitHub PR CI: Maven-Paketierung, JUnit-Tests für Dokumentrechte, OCR/Texterkennung, Worker und Einzelfreigabe sowie Node-Smoke-Test für die Dokumenten-/TV-Oberfläche. Vor produktivem Einsatz zusätzlich persönlich im Browser und im Fire-TV-Kiosk testen, insbesondere Schriftgrößen und Sitzungsablauf. Vor jedem Server-Update ein konsistentes Backup von Datenbank **und** dem neuen privaten Volume anlegen.
