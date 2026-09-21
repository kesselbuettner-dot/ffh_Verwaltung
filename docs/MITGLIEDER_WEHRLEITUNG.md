# FW-Cockpit — Mitgliederdaten, Rollen und Wehrleiterqualifikationen

Status: **V1 auf dem Entwicklungszweig `feature/wehrleiter-qualifications-v1` implementiert, nicht produktiv auf dem Server abgenommen.** Dieser Text benennt auch offene Punkte. Vor der Freigabe reale PWA-/Smartphone- und Serverdatenbank-Tests durchführen. Änderungen an den Datenmodellen, Datenschutz, Rollen und Automationen erfordern separate Tests und Migration. Bestehende Mitglieder-, Kontostands- und Geräteprüffunktionen dürfen dabei nicht verloren gehen.

## Führende Bereiche und Verantwortlichkeit
| Bereich | Zweck | Anlegen/Bearbeiten | Wer sieht was? |
|---|---|---|---|
| Administration > Mitgliederstammdaten | Ein Mitglied je Datensatz/ID: Vor-/Nachname, Adresse/Kontakt, Geburtsdatum, Eintritt, Status, ggf. benutzerdefinierte Stammdatenfelder | Nur ADMIN | ADMIN; andere Bereiche nur benötigte Projektion |
| Administration > Benutzerkonten | Anmeldung, Passwort, E-Mail/Login, Status, Freigabe, Link auf vorhandene Mitglieds-ID | Nur ADMIN; Registrierung ggf. mit Adminfreigabe | ADMIN, eigener Kontostatus |
| Administration > Rollen & Rechte | Rollen definieren und Berechtigungen zuweisen; explizite Rolle WEHRLEITER nur mit abgestimmter Migration | Nur ADMIN | ADMIN |
| Wehrleiter > Mitgliederverwaltung | Feuerwehrfachliche Qualifikationen/Prüfungen als Kacheln pro Mitglied, Filter, Termine/Überwachung; KEINE zweite Mitglieds-Stammdatenmaske | WEHRLEITER und ADMIN gemäß Serverrechten | Nur erforderliche Qualifikations-/Statusdaten; keine Finanzdaten aus Mitgliederstamm |
| Wehrleiter > Führerscheinkontrolle | Prüfterminserie, Mitglieder mit Führerschein-Kachel, Nachweise, Status und Protokoll | WEHRLEITER und ADMIN gemäß Serverrechten | Nur berechtigte Rollen und jeweils betroffene Mitglieder im eigenen Prüfauftrag |

Die bisherige einfache `Mitglieder`-Übersicht zeigt aktuell Rolle/Kontostand; bei Einführung des Wehrleitermoduls darf diese fachfremde Finanzsicht nicht einfach als Wehrleiterliste umetikettiert werden. Bestehende Kassen-/Mitgliedsfunktionen erst nach Prüfung ihres Verwendungszwecks verschieben, nicht löschen.

## Stammdaten und Import
Mitglied ist die führende Tabelle; Benutzerkonto optional 1:1 verknüpft. Geburtsdatum/Eintritt als optionales `LocalDate`, eigenes Metadatenschema für freie Felder mit Typ (Text, Datum, Auswahl, Zahl), Pflicht, Sichtbarkeit, Validierung, Änderungsprotokoll. Sensible Felder nicht beliebig vom Admin als „global sichtbar“ deklarieren.

CSV: UTF-8, konfigurierbarer Separator, Vorschau mit Spaltenzuordnung und Dublettenvorschlägen (Mitglieds-ID, optional E-Mail/Name + weitere Merkmale), erst nach expliziter Bestätigung importieren; teilweises Scheitern pro Zeile protokollieren. **Google Kontakte**: Ein Google-Kontakte-CSV-Export kann mit Voransicht importiert werden. Eine direkte autorisierte OAuth/People-API-Verbindung ist **nicht** implementiert; keine automatische Übernahme aller Kontakte. Import ist weder eine neue Login-Berechtigung noch darf er bestehende Mitglieder still überschreiben.

## Qualifikationsbaukasten (spätere Datenmodelle)
- `qualification_type`: Schlüssel, Anzeige/Emoji/SVG, Felder/Validierung, sensitiv?, ausstellungsdatum optional, gültig-bis optional, Wiederholungsregel und Vorwarnzeit, Listen-/Dashboard-Freigaben. „Führerschein“, „Motorkettenschein“, „Untersuchung“, „Führerscheinkontrolle“, „Wiederholungsausbildung“ als getrennte Typen.
- `member_qualification`: Mitglieds-ID, Typ, Datum/Aussteller soweit erforderlich, Status, Ablauf/letzte Prüfung, manuelle Hinweise; keine parallelen Name-/Adress-Duplikate. Führerscheinnummer, wenn für den Abgleich erforderlich, nur unter strengem Zugriffsmodell mit festgelegter Aufbewahrung, vorzugsweise nicht als Klartext.
- `qualification_check`: unveränderlich protokollierte Prüfung/Serie, Plan-ID, Prüfer, Prüfzeit, Ergebnis, Quelle (Mitglied digital / Wehrleiter manuell), ggf. Ablehnungsgrund **ohne Dokumentfoto und ohne vollständige Führerscheinnummer**.
- `qualification_schedule`: Intervall, nächster Termin, Vorwarnzeit in Tagen, zugewiesene Prüfpersonen und Empfänger, benutzerdefinierte Stop-/Aufschubregeln. Einzel- und Serienänderung konsistent mit vorhandener Terminserienfunktion.

## Führerscheinkontrolle — gewünschter späterer Ablauf
1. WEHRLEITER/ADMIN konfiguriert Typ und Kontrollserie; nur Mitglieder mit Führerschein-Kachel werden in die Liste aufgenommen.
2. Vorwarnzeit löst ab Stichtag eine **individuelle, rechtlich/organisatorisch abgestimmte** Nachricht an das betreffende Mitglied aus. Im Wehrleiter-Dashboard erscheinen nur eigene fällige Prüfungen und notwendige Statusdaten; keine unnötigen medizinischen Details.
3. Mitglied öffnet nach der konfigurierten Vorwarnzeit den eigenen angemeldeten Prüfauftrag und fotografiert den Führerschein; Das Kamerabild wird im Browser verkleinert und für die lokale, selbst gehostete OCR-Engine über die authentifizierte Verbindung an den Server übermittelt. Das Bild wird dort **nur flüchtig im RAM und über die Standardeingabe des OCR-Prozesses** verarbeitet; die Anwendung legt keine Upload-/Bilddatei an. Die Erkennung erfolgt auf dem Server, nicht anhand manipulierbarer Browser-OCR-Textfelder. Vollständiger Name und vollständige erkannte Nummer werden gegen den Mitgliederstamm beziehungsweise einen HMAC-Referenzwert abgeglichen; Rohnummern und Bilder bleiben außerhalb von Datenbank, Export und Prüfprotokoll. **Verbindliche Nutzerentscheidung:** Wenn eine verfügbare mobile OCR-Funktion sowohl den vollständigen Namen als auch die vollständige Führerscheinnummer mit den zuvor verifizierten Referenzdaten exakt abgleicht, wird der Kontrollauftrag **automatisch mit positivem Ergebnis abgeschlossen**. Das Protokoll kennzeichnet den Prüfweg als automatischen OCR-Abgleich. Dieser Vorgang bestätigt den Datenabgleich, nicht unabhängig die Echtheit des Dokuments oder die Identität. Bei fehlendem Referenzwert, unsicherer Erkennung oder Abweichung erfolgt kein automatischer Abschluss; die Wehrleitung kann den Auftrag separat über „Führerschein geprüft“ manuell positiv dokumentieren.
4. Es wird **kein Foto in der Anwendung gespeichert**: weder im Browsercache/LocalStorage noch in einer Bilddatei, Datenbank, Anwendunglogs, Hintergrundjobs oder Exporten. Der Server empfängt Bildbytes kurzzeitig nur für den OCR-Abgleich im RAM; dabei können Netz-/Betriebssystemkomponenten außerhalb der App nicht pauschal ausgeschlossen werden. Das Protokoll enthält nur Mitglieds-ID, Termin, Datum/Zeit, Prüfer/Prüfweg, Ergebnis und minimal nötigen Auditvermerk.
5. Wehrleiter kann denselben Auftrag über „Führerschein geprüft“ manuell abschließen (mit nachvollziehbarem Prüfer, Zeitstempel und Ergebnis), ohne Mitgliedsfoto.
6. Filterbare Liste, Druck/PDF/CSV-Export mit Rollenprüfung, Prüfserie und Historie; nur minimal nötige personenbezogene Daten. Sonderfall: fehlendes Ausstellungsdatum oder fehlende Nummer als „nicht bekannt“, nicht als erfundener Wert.

## Zugriff, Sicherheit und Abnahme
- Rollen sind nicht bloß Menüicons. Getrennte effektive Rechte etwa `members.master.read/write`, `fire.qualifications.read/write`, `fire.qualifications.sensitive.read`, `fire.driving-check.read/write/self`, `fire.schedules.manage`; Definition und Einführung mit Migration.
- Ein individueller Push-Link führt nur nach Login zum eigenen Prüfauftrag; dessen API prüft die Mitglieds-ID serverseitig. Ein gesonderter zeitlich befristeter Einmal-Link ist noch nicht implementiert. Rate-Limits, sichere Löschung temporärer Daten und Audit ohne unnötige Werte.
- Zu Gesundheits-/Untersuchungsdaten nur Fälligkeit/„geeignet bis“ und nötige Berechtigungen, nicht Diagnosen in Kacheln oder Benachrichtigungen. Organisationsinterne Regeln zu Aufbewahrung, Zustimmung/Transparenz und zulässigem Prüfverfahren vor Aktivierung festlegen.
- Testmatrix: ADMIN, WEHRLEITER, MEMBER, keine Rolle, deaktiver Nutzer; erlaubte/unerlaubte API-Aufrufe; fehlender Termin; verspätete Prüfung; Offline/PWA; Dublettenimport; keine Foto-Artefakte; Serie/Einzeltermin; Export. Bestehende Geräteprüfung bleibt unabhängig.

## Implementiert und vor Produktivfreigabe noch offen

- **V1 implementiert:** Admin-Mitgliederprofil mit Geburts-/Eintrittsdatum und typisierten freien Feldern, CSV-Vorschau und Import (einschließlich Google-Kontakte-CSV), explizite Wehrleiter-Rolle ohne automatische Neuvergabe entfernter Rechte, Qualifikationskacheln, Fristfilter, Wehrleiter-Dashboard, Führerscheinkontrollserie per Intervall und Vorwarnzeit, gezielte Push-Erinnerung (nur an registrierte Push-Abonnements), manueller und automatischer positiver Abschluss mit Audit/CSV und Druckansicht.
- **OCR im Entwicklungsstand:** Kamera-JPEG wird über authentifizierte Verbindung kurzzeitig an den FW-Cockpit-Server übertragen und mit dort installierter deutscher/englischer Tesseract-OCR ohne Fotodatei verarbeitet. Vollständiger Name und erkannte Nummer müssen mit zuvor erfassten Referenzdaten übereinstimmen. Automatisch positiv bedeutet **Datenabgleich**, nicht unabhängige Echtheits- oder Identitätsprüfung. Schlecht lesbare Fotos benötigen eine erneute Aufnahme oder manuelle Kontrolle.
- **Offen:** Direkte Google-Contacts-OAuth-Integration, individueller Bearbeitungsdialog einer bereits erstellten Prüfserie (statt Typ-Intervall), Nutzung des bestehenden zentralen Kalenders für diese Serien, rollenabhängiger Export als eigenständige PDF-Datei (Browserdruck zu PDF funktioniert als Alternative), umfassender Produktiv-/Browser-/Datenbank-Smoketest auf echtem Server und Gerät, Schutz vor Missbrauch durch gezieltes Rate-Limiting, organisationsinterne Regelung von Prüfverfahren und Aufbewahrung. Keine automatische Aktivierung ohne Abnahme.

## Reihenfolge
**Phase 1:** Dokumente und semantische Button-Themes, Administrationsstruktur entdoppeln (Mitgliederstammdaten/Konten/Rollen & Rechte), bestehende Mitgliedsbearbeitung serverseitig auf ADMIN begrenzen. Keine neuen sensiblen Daten ohne Felder-/Migrationskonzept.

**Phase 2:** Geburt/Eintritt/freie Stammfelder, CSV/Google-Kontakte-Import mit Vorschau, Dublettenkontrolle, einheitliche Mitglieds-ID.

**Phase 3:** WEHRLEITER als echte Rolle/feingranulare Rechte, Baukasten/Kacheln, Filter und Fälligkeiten/Dashboard ausschließlich Wehrleiter.

**Phase 4:** Führerscheinkontrolle mit Serien, individueller Benachrichtigung, mobiler Prüfung, datensparsamer Dokumentation, Druck/Export und manueller Bestätigung. Vor Freischaltung Sicherheits-/Datenschutz-/Bedienprüfung.
