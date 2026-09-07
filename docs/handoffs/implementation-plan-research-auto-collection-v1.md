# Plan: automatyczna trwała sesja badawcza

Baza `506649b`, gałąź `feature/research-session-auto-collection-v1`.
Przed rozpoczęciem `git status` i `git diff` potwierdziły czyste drzewo.

1. Dwuetapowy START: tożsamość i zamrożony config, trwałe PREPARED, uzbrojony
   kolektor, wspólne t0 dla domeny i metryk. Błąd magazynu blokuje pomiar.
2. ResearchSessionStore: ograniczona kolejka, jeden writer, journal prób,
   obrazy niezależne od galerii, jawne straty i odzyskiwanie PARTIAL.
3. Pasywny audyt rzeczywistych wywołań MT/MZ przed filtrami UI: dowód ROI
   dla MT miss, wszystkie próby MZ, anulowane wyniki i tożsamość bez OCR.
4. Automatyczny STOP/drain/ZIP, addytywny samples v2 w istniejącym ResearchArchive,
   hashe obrazów i metadanych, możliwość eksportu pełnej lub częściowej sesji.
5. Integracja lifecycle/timera/rotacji, podgląd galerii i testy A1–A15.

Zrealizowano 2026-09-08. Weryfikacja: 543 testy JVM, 145 testów Androida
oraz niezależna walidacja dwóch końcowych paczek ZIP na komputerze — bez błędów.
Szczegóły i ograniczenia: [raport wdrożenia](implementation-report-research-auto-collection-v1.md).
