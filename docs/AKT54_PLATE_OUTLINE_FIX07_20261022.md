# AKT54 — obrys ramek „bez przypisania”, FIX07

Zastosowano zatwierdzony plan na dokładnym lokalnym stanie po FIX06. **Testy JVM: PASS. Kompilacja testów Androida: PASS. Renderowanie na A12: NOT_RUN.** Nie instalowano APK i nie uruchamiano eksperymentu ani inferencji na telefonie. Bez commitu/pushu.

## Przyczyna

Operator potwierdził, że cienka ramka pozostaje na nieruchomym obrazie w statycznym eksperymencie i ma etykietę **„bez przypisania”**. W kodzie znaleziono niespójność odpowiadającą tej ścieżce:

1. MZ otrzymuje crop rektyfikowany z czterech narożników tablicy.
2. `animatePlateObservation()` dla odczytu bez właściciela usuwa zwykłą warstwę PLATE i przechowuje oczekujący odczyt.
3. Przed FIX07 zapis tego odczytu pomijał narożniki, a `drawPendingPlateReadings()` rysował prostokąt z osobnego bboxa MT. Późniejsze odświeżenia ponownie wpisywały sam bbox.
4. Przy poprawnych narożnikach i niskim bboxie tekst może być czytelny, lecz ramka po OCR staje się paskiem. Statyczne IDLE utrzymuje tę warstwę zgodnie z istniejącym czasem ważności.

Deterministyczny przypadek ma prawidłowy czworokąt wysokości **50 px** i bbox wysokości **2 px**. Przy skali 0,72 poprzednia ścieżka rysowała prostokąt wysokości **1,44 px** zamiast obrysu wysokości **36 px**. Test geometrii potwierdza obecnie wybór rzeczywistych narożników i zachowanie surowego bboxa 2 px bez zmian. To potwierdza niespójność kodu; nie jest zapisem odtworzenia konkretnego incydentu na A12.

## Zmiana

- Oczekujący odczyt przechowuje własną, niemutowalną geometrię prezentacji: uporządkowane rzeczywiste narożniki, ich obwiednię oraz odpowiadające im wymiary obrazu. Wewnętrzny `PlateDisplayGeometry` nie zależy od klas graficznych Androida.
- Źródłem jest `PlateGeometry.forRecognition()`, również przy geometrii efektywnego cropu autozoomu. Dane modelu i metadane cropu nie są modyfikowane.
- Poprawny quad rysowany jest jako ścieżka, a pozycja etykiety wynika z jego obwiedni. Ta sama funkcja rysowania obsługuje zwykłe PLATE i oczekujące odczyty. Nie dodano minimalnej sztucznej wysokości ani narożników wygenerowanych z bboxa.
- Przy brakujących lub niepoprawnych narożnikach pozostaje dotychczasowy fallback prostokąta. Poprawność narożników korzysta z istniejącej walidacji czworokąta w pikselach źródła; progów MT/MZ nie zmieniono.
- Odświeżenie ze świeżym quadem wymienia jednocześnie punkty, obwiednię i rozmiar źródła. Wywołanie `setItems()` przekazuje w tym celu wymiary nowego wyniku, zamiast korzystać z wymiarów poprzedniego.
- Aktualizacja zawierająca wyłącznie bbox nie nadpisuje zachowanego poprawnego quada i nie przedłuża wieku jego geometrii. Dla wpisu, który nigdy nie miał quada, bbox nadal może się odświeżać.
- Początek transferu odczytu do pojazdu korzysta z obwiedni rzeczywistego cropu, jeżeli zwykła ramka nie jest już dostępna. Przypisanie, wygaszanie, limity oczekujących odczytów oraz reset po zmianie sceny pozostają zachowane.

Nie zmieniono publicznych API ani schematów JSON, modeli, progów conf/IoU, rektyfikacji MZ, danych badawczych, zabezpieczeń INT8, START, timera ani wybudzania z FIX06. Poprawka dotyczy prezentacji.

## Walidacja

Wykonano:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:compileDebugAndroidTestJavaWithJavac --console=plain
```

**PASS: 819 testów JVM, 162 zestawy, 0 failures, 0 errors, 0 skipped.** Dziesięć nowych testów obejmuje quad 50 px przy bboxie 2 px, efektywną geometrię cropu, kolejność punktów, fallback przy brakujących lub niepoprawnych narożnikach, aktualizację rozmiaru źródła i położenia, ochronę przed nadpisaniem samym bboxem oraz własność danych. Surowa geometria pozostaje bez zmian.

**Kompilacja testów Androida: PASS. Wykonanie: NOT_RUN.** Siedem nowych scenariuszy używa rzeczywistego `DetectionOverlayView` i Canvas do bitmapy. Obejmują:

- obrys przed i po OCR w trzech proporcjach widoku oraz ponowne publikowanie nieruchomej sceny;
- skośne krawędzie quada zamiast jego prostokątnej obwiedni;
- odrzucenie odświeżenia samym bboxem bez przedłużania wieku;
- zmianę wymiarów źródła i niezależność od mutowalnych `PointF`;
- efektywny crop autozoomu, legacy fallback i twardy reset;
- przypisanie odczytu do pojazdu bez duplikacji ramki;
- rzeczywiste wywołanie `renderStaticIdle()` w Activity z syntetycznym stanem pipeline, bez START i inferencji.

Kompilacja tych testów nie potwierdza pikseli rzeczywiście wyświetlonych na A12. Testy bitmapowe i Activity wymagają oddzielnego wykonania Androidowego. Nie deklaruje się urządzeniowego wyniku PASS ani pomiaru jakości modeli.

`git diff --check`: PASS. Porównanie z wejściowym snapshotem obejmuje wszystkie pliki Java; poza wskazaną warstwą UI i nowymi testami pozostają identyczne. Dokładne zastosowanie patcha w kopii, backup, idempotencję oraz odmowę zapisu dla obcego źródła lub naruszonej bramki SHA odnotowano w manifeście poniżej.

## Artefakty

- [Patch FIX07](patches/patch_android_plate_outline_fix07_20261022.py), wejście: dokładny lokalny stan po FIX06.
- [Diff](patches/PLATE_OUTLINE_FIX07_20261022.diff), [SHA przed/po, warunki wstępne, backup i walidacja](patches/PLATE_OUTLINE_FIX07_20261022_sha256.json).
- [Poprzedni raport FIX06](AKT54_STATIC_FEATURE_ADDITION_FIX06_20261022.md).

Zmiany są już zastosowane w tym checkoutcie. Kontrola wyłącznie do odczytu:

```powershell
python "C:\Users\48572\AndroidStudioProjects\ALPR_v1\docs\patches\patch_android_plate_outline_fix07_20261022.py" --check
```

Zastosowanie w zgodnej kopii wejściowej tworzy backup:

```powershell
python "C:\Users\48572\AndroidStudioProjects\ALPR_v1\docs\patches\patch_android_plate_outline_fix07_20261022.py" --apply
```

Następny krok operatora po kompilacji i instalacji w Android Studio: krótki techniczny test nieruchomego obrazu z odczytem „bez przypisania” i porównaniem obrysu przed/po OCR. Nie uruchamiano i nie zleca się ponownej kampanii F4.
