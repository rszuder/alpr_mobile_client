# Galeria: zgodność kanonicznego numeru i zachowanie surowego MZ

**Aktualizacja 10.09.2026:** decyzja z handoffu interoperacyjności zastępuje
wcześniejszą równość raw kluczem `uppercase_alphanumeric.v1`. `aaa123`, `AAA123`
i `AA A-123` tworzą jedną grupę, lecz każda obserwacja zachowuje własny raw.
Encje nie są scalane, a opis bazowego cropa pozostaje niezmieniony. Nie migrujemy
wcześniejszych sesji. [Aktualny kontrakt i testy](android_full_interop_2026-09-10.md).

Poniżej zapis historycznej decyzji z 9.09, wyjaśniającej źródło wcześniejszego
mieszania różnych rejestracji. Jej wymóg równości raw nie jest już aktualny.

Wpis galerii reprezentuje jeden konkretny tekst odczytany przez MZ, a nie encję
pojazdu. Przykład: `AAA` z P4 oraz `AAA` z późniejszej P9 to jeden crop i dwie
obserwacje. Numery obu encji pozostają w metadanych obserwacji. `AAB` tworzy
osobny wpis, również gdy pochodzi z P4 lub P9.

Przyczyna mieszania odczytów: wcześniejszy `upsertObservation` po nieudanym
dopasowaniu numeru korzystał z aliasu encji/tracka. Pozwalał także zastępować
reprezentanta innym, lepiej ocenionym odczytem bez usunięcia poprzednich
obserwacji. Łączenie prowizorycznych grup mogło przenosić różne teksty razem.

Nowe zasady:

- Kluczem zwykłej historii w obu trybach sceny jest dokładny `freshPrediction`.
  Sprawdzana jest pełna równość znaków, bez podobieństwa, usuwania separatorów
  czy zmiany wielkości liter. Nie ma wyjątku dla odczytów częściowych.
- Encja, track, scena ani wyższa pewność nie pozwalają połączyć różnych tekstów.
- Obraz bazowy i jego tekst, pewność, czasy oraz pochodzenie pozostają razem.
  Kolejne zgodne wystąpienia dopisują tylko metadane i mogą nie mieć bitmapy.
- Różny odczyt bez własnego obrazu nie modyfikuje żadnej istniejącej grupy.
- `RecognitionHistoryItem.record` dodatkowo odrzuca obserwację z innym tekstem.
- Nadal odrzucane są puste wyniki MZ i sam konsensus przeniesiony z innej klatki.
- Tożsamość encji w trackerze i potoku nie jest scalana po odczycie.

Starsze opisy grupowania po znormalizowanym numerze i dopasowaniu po encji
nie obowiązują dla tej ścieżki zapisu. Testy obejmują przejście
`AAA/P4 → AAB/P4 → AAB/P9 → AAA/P9`, zachowanie bazowej bitmapy, zgodność
wszystkich obserwacji w grupie, zmiany pojedynczego znaku, prefiksu, spacji,
myślnika i wielkości liter, usunięcie wpisów oraz ograniczenie pojemności.

Weryfikacja: 636/636 testów JVM i 18/18 testów instrumentacyjnych historii,
szczegółów oraz galerii na emulatorze Pixel 3. APK debug zbudowane poprawnie;
`git diff --check` bez błędów. Nie instalowano APK na telefonie.
