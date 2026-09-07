# Plan wdrożenia v4

Gałąź: `feature/exclusive-target-focus-v4`.
Baza z poprawkami UX i weryfikacji: `f7d305b`.
Specyfikacja: [handoff v4](static-dynamic-scene-identity-az-lock-focus-v4.md).

- [x] Wyprowadzić wyłączny fokus z istniejącego persistent TargetSession i cameraAttentionOwned; zachować SEARCH_VERIFICATION jako niewyłączny.
- [x] Wstrzymać zwykłą kolejkę i odczyty innych encji; dopuścić pomocniczy MP/reacquire bez ich akwizycji.
- [x] Wprowadzić centralny filtr prezentacji celu, anulować obce animacje/transfery i zabezpieczyć spóźnione callbacki.
- [x] Zachować fokus podczas RECOVERING i AZ, a po release wymagać świeżego MP przed odbudową widoku.
- [x] Zachować query po utracie pursuit, historię, badania oraz granice STATIC; doprecyzować opis STATIC.
- [x] Uruchomić L8–L15 oraz regresje v3/UX/weryfikacji, sprawdzić telefon, przygotować raport i commit.

Wynik: 533 testy JVM i 78 testów Android — PASS. Implementacja: `f9ffc7f`.
[Raport i ograniczenia walidacji](implementation-report-v4.md).

Nie przebudowujemy diagramów ani nie dodajemy nowego ApplicationMode.
