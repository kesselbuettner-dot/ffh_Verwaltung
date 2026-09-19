# Rollen- und Rechteverwaltung – Umsetzungskonzept

Status: Entwicklungsentwurf. Keine produktive Freigabe und kein Release.

## Ziel
Administratoren können Rollen anlegen und Benutzern mehrere Rollen zuordnen. Für sämtliche bestehenden und später registrierten Funktionsbereiche werden Lesen, Schreiben, Löschen und gesonderte Aktionen verwaltet. Sichtbarkeit im Frontend und Autorisierung der Backend-APIs müssen dieselben Berechtigungskennungen verwenden.

## Datenmodell
- permission_catalog: stabile eindeutige Kennung (z. B. inventory.articles.read), Bereich, Unterbereich, Aktion, Beschreibung, systemgeschützt.
- app_roles: ID, eindeutiger Name, Beschreibung, systemgeschützt.
- role_permissions: role_id, permission_key; eindeutiges Paar.
- user_roles: user_id, role_id; eindeutiges Paar.
- Migration bestehender AppUser.role-Zuordnungen in user_roles; vorhandene Benutzer, Passwörter und Mitgliedsverknüpfungen bleiben erhalten.
- ADMIN als geschützte Systemrolle; Administratoren dürfen nicht versehentlich den letzten aktiven Administrator entfernen.

## Regeln
- Standardmäßig keine Berechtigung; neu registrierte Menüpunkte und APIs sind für Nichtadministratoren gesperrt.
- Mehrere Rollen wirken additiv. Schreibrechte setzen Leserechte voraus; Löschen und Sonderaktionen sind getrennt.
- Menüs und Untermenüs werden zentral mit stabilen Kennungen registriert und automatisch in der Rechteverwaltung angezeigt.
- Menüs verbergen ersetzt niemals die serverseitige Prüfung. Jeder geschützte Controller-Endpunkt muss einer Berechtigung zugeordnet werden.
- Rollenänderungen müssen bei bestehenden Sitzungen ohne erneuten Login greifen; keine langfristig im JWT gespeicherten effektiven Berechtigungen.
- Administration der Rollen und System-Updates erhalten separate, besonders geschützte Berechtigungen.

## Umsetzungsschritte
1. Bestand aller Menüpunkte, Rollen, Controller und Endpunkte erfassen; dokumentierte Berechtigungsmatrix erstellen.
2. Datenbankmigration und Rückwärtskompatibilität für bestehende Benutzer implementieren und testen.
3. Zentrale serverseitige Autorisierung, Katalogregistrierung und rollenbezogene APIs implementieren.
4. Administration: Rollen anlegen/ändern, Berechtigungen pro Bereich einstellen, mehrere Rollen pro Benutzer zuweisen.
5. Frontend-Menüs und Aktionen an effektive Rechte anbinden.
6. Tests: Migration, ADMIN-Schutz, Lesen/Schreiben/Löschen, direkte API-Aufrufe ohne Rechte, kombinierte Rollen, neue Bereiche standardmäßig gesperrt, Rechteänderung bei bestehender Sitzung sowie Regressionen für Login, Mitglieder, Kasse, Geräte und Updates.

## Freigabe
Erst nach vollständiger Implementierung, erfolgreichen automatisierten Tests und geprüftem Funktionsablauf: Pull Request zur Prüfung, Merge, anschließend eigener Release-Tag. Bis dahin keine Änderung am produktiven Server.
