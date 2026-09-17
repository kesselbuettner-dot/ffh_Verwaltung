# Korrigierte Version – FFH Bestell-App Backend

Behobene Punkte:
1. `AppUserRepository` enthält `findByUsernameWithMember(...)` mit `LEFT JOIN FETCH`.
2. `/api/auth/login` verwendet den Fetch-Query und ist zusätzlich `@Transactional(readOnly=true)`.
3. Login liest `memberId`/`memberName` innerhalb der aktiven Transaktion; damit keine `LazyInitializationException`.
4. Admin-User-Endpunkte sind transaktional, damit die zugehörigen Member-Daten ebenfalls nicht lazy außerhalb der Session gelesen werden.
5. Warenkorb: Bestand 0 wird nicht mehr als verfügbar gemeldet.
6. `ArticleDtos.ActiveRequest` enthält weiterhin `ArticleType type`, damit `ArticleController` kompiliert.

Wichtig:
- Das JAR auf dem Server muss nach dem Pull neu gebaut werden. Nur `git pull` reicht nicht.
- Danach die laufenden Container `app` und `worker` neu bauen/starten.
