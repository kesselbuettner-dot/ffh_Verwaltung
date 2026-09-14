Ja. Ich habe die nächste Ausbaustufe bereits auf Basis des aktuellen FFH-Verwaltungsstands vorbereitet.



Was jetzt dazukommt



Verkauf → Artikel verwalten



Artikelnummer

Bezeichnung / Kurzname

Kategorie

Marke

Gebinde/Einheit

Verkaufspreis

Mitgliederpreis

Einkaufspreis

Pfand

MwSt.

Lieferant

Bestand / Mindestbestand

Allergene / Zusatzstoffe

Aktiv/Inaktiv

eigener Suchbegriff für Angebotsvergleich



Dazu bekommt jeder Artikel den Button 🔎 Angebote.



Automatische lokale Angebotssuche



Die Suche arbeitet über die öffentlich erreichbare Marktguru-Angebotsschnittstelle. Marktguru liefert regionale Ergebnisse anhand der PLZ; die Ergebnisse können nach Preis sortiert werden. Genau diese Funktionalität wird dort auch für lokale Produktsuchen verwendet.



In unserer Anwendung wäre dann beispielsweise:



Artikel: Krombacher Pils Kasten 20 × 0,5 l

PLZ: 04109

→ Angebote suchen



und anschließend etwa:



🏆 Kaufland — 9,99 €

Netto — 10,99 €

REWE — 11,99 €



Dabei sollen zusätzlich Händler, Beschreibung, Gültigkeit, Referenzpreis und ggf. Kundenkartenpflicht angezeigt werden.



Wichtig: Die Marktguru-Schnittstelle ist keine von mir als offizielle öffentliche Entwickler-API verifizierte API. Die technische Schnittstelle ist aber dokumentiert bzw. wird von Drittprojekten verwendet; deshalb habe ich sie bewusst hinter einer eigenen OfferSearchService-Schicht gekapselt. Wenn Marktguru die Schnittstelle ändert, müssen wir nur diesen Teil anpassen.



Nächster Schritt



Ich würde jetzt nicht einfach die bisherigen Getränke ersetzen. Der Artikelstamm soll die zentrale Grundlage werden:



Verkauf

├── Artikel

│   ├── Getränke

│   ├── Essen

│   └── Sonstiges

├── Kasse / Theke

├── Lager

└── Einkauf



Das entspricht auch der vorgesehenen Grundstruktur, bei der Getränke und Essen zentral verwaltet und später gemeinsam über die Kasse verarbeitet werden sollen.



Ich würde als Nächstes den Artikelstamm fertig machen und anschließend die bestehende Kasse auf Artikel statt ausschließlich Getränke umstellen. Damit haben wir danach eine einzige Warenbasis für Kasse, Lager, Einkauf und den Preisvergleich.

