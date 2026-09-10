# Fixture Android–Desktop

Zestaw dla handoffu z 10.09.2026. Migracja wcześniej zapisanych sesji jest poza zakresem.

Skrypty uruchamiamy w środowisku testowym z zależnościami Desktopu oraz
`tools/interop/requirements.txt`. Walidacja cropów korzysta z
`docs/alpr-crop-session-v1.schema.json`, opisującego profil nowych zapisów.

## Desktop → Android

```powershell
python tools/interop/generate_desktop_fixtures.py --desktop-root C:/Users/48572/Desktop/ALPR_Desktop --output app/src/androidTest/assets/interop/models
```

Generator wywołuje prawdziwy eksporter Desktopu, ale podstawia kontrolowane
artefakty zamiast trenowania/konwersji checkpointu. Manifest, kompozycja, zapis
archiwum i sumy są wytwarzane przez Desktop. ONNX FP32 i QDQ są wykonywalne;
TFLite i NCNN to stuby wyłącznie do kontroli importu. Pliki są małe i dołączone
w `app/src/androidTest/assets/interop/models`.

`DesktopInteropInstrumentedTest` importuje pojedyncze MP/MT/MZ oraz kompozycje
MT+MZ i MP+MT+MZ. Testuje błędną akcję, brak dziecka/pliku, rolę, hash,
traversal i duplikaty. Uruchamia obydwa warianty ONNX, preprocessing i dekoder.
Sprawdza też wspólne wektory normalizacji z `registration_cases.json`.

## Android → Desktop

`InteropArtifactInstrumentedTest` tworzy pliki przez `ResearchSessionStore`,
`ResearchArchive` i `CropSessionStore` w prywatnym katalogu aplikacji `files/interop`.
To fixture kontrolowanych obserwacji; nie sesja mierzona kamerą.

Przykładowe uruchomienie na emulatorze z zainstalowanymi APK:

```powershell
adb -s emulator-5556 shell am instrument -w -r -e class com.example.alpr_v1.experiment.InteropArtifactInstrumentedTest com.example.alpr_v1.test/androidx.test.runner.AndroidJUnitRunner
```

Przeniesienie binarnego ZIP należy wykonać przez `adb exec-out run-as ... cat`
z binarnym przekazaniem stdout, np. `subprocess.run(..., capture_output=True)`
w Pythonie. Nie używać tekstowego przekierowania PowerShell do zapisu ZIP.

Aktualne pliki są w [fixtures/android](fixtures/android). Weryfikacja:

```powershell
python tools/interop/verify_desktop_exchange.py --desktop-root C:/Users/48572/Desktop/ALPR_Desktop --artifacts tools/interop/fixtures/android
```

Skrypt używa rzeczywistych `ReportBundleReader`, `MobileReviewSession` i normalizatora
z podanego repozytorium Desktopu. Nie modyfikuje jego kodu ani źródłowych paczek.
Tworzy dodatkowo negatywne fixture i `desktop-verification.json`.

| Plik | Zawartość |
|---|---|
| `research-complete.alprsession` | 0/1/3 detekcji, świeży odczyt i pusty MZ, błąd MT, anulowanie, NOT_RUN. |
| `research-partial.alprsession` | Crop MZ istnieje, wejście MT nie zostało zachowane; jawny PARTIAL. |
| `processing-error.alprsession` | Późniejszy błąd nie zamienia wcześniejszego MT w błąd backendu. |
| `crop-session.zip` | Jedna grupa AAA123, trzy raw, P4/P9, jeden obraz, brak duplikacji po wznowieniu. |
| `bad-hash.alprsession` | Zmieniony raport bez aktualizacji hashy. |
| `bad-count.alprsession` | Poprawne hashe, sprzeczna liczba detekcji w invocation. |

Zwykły crop ZIP ma osobny kontrakt. Skrypt weryfikuje jego zawartość niezależnie
i potwierdza odmowę interpretowania jako badanie przez obecny czytnik raportów.
Oddzielny adapter do warsztatu Desktopu jest nadal wymagany.

Schema sesji i modeli pozostają bez zmian; znaczenie nowych pól, polityki
normalizacji i ograniczenia potwierdzenia opisuje
[raport Androida](../../docs/android_full_interop_2026-09-10.md).
