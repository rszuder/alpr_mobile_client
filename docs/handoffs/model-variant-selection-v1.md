# Handoff: jawny wybór wariantu wykonawczego modelu na Androidzie

## Cel

Naprawić obsługę modeli mobilnych zawierających wiele wariantów wykonawczych, tak aby użytkownik mógł na telefonie jawnie wybrać wariant dla każdego etapu ALPR:

- MP — model pojazdu,
- MT — model tablicy,
- MZ — model znaków.

Przez **wariant wykonawczy** rozumiemy konkretną kombinację:

`runtime + precision + pliki modelu + ewentualne nadpisania kontraktu input/output`

Przykłady:

- TFLite FP32,
- TFLite INT8,
- ONNX FP32,
- ONNX INT8,
- NCNN FP32.

Nie nazywaj tego w kodzie ani UI wyłącznie „formatem”, bo runtime i precyzja są osobnymi własnościami wariantu.

---

## Ważne przed rozpoczęciem

Agent pracuje obecnie nad innym zestawem zmian, który nie został jeszcze wypchnięty do GitHuba.

**Nie resetuj ani nie checkoutuj repo do podanego niżej commita. Nie nadpisuj lokalnych zmian.**

Najpierw:

1. sprawdź bieżący `git status`,
2. sprawdź bieżący `git diff`,
3. zakończ lub zachowaj aktualne lokalne zmiany,
4. potraktuj poniższy commit wyłącznie jako punkt odniesienia do diagnozy.

Ostatni wypchnięty stan użyty do analizy:

- repo: `rszuder/alpr_mobile_client`
- branch: `phase3b2-motion-overlay-final-hardening`
- commit: `8a19730b405449be698426df311e4085e3963081`

Po zakończeniu bieżącego zadania ponownie sprawdź aktualny kod, bo część wskazanych metod mogła zostać zmodyfikowana.

---

# 1. Diagnoza obecnego stanu

Problem **nie leży w formacie paczki ani w samym importerze**.

Eksporter desktopowy może umieszczać w jednym `alpr.model.v1` wiele wariantów, np.:

```text
variants:
  tflite-fp32
  tflite-int8
  onnx-fp32
  onnx-int8
  ncnn-fp32
```

Androidowy `ModelManifest` odczytuje całą tablicę `variants`.

`ModelPackageImporter` przechodzi po wszystkich wariantach, weryfikuje pliki, sumy SHA-256 i kontrakty wykonawcze. Nie redukuje paczki do jednego wariantu.

`ModelVariant` już zawiera:

```java
id
runtime
precision
files
sha256
inputOverride
outputOverride
```

Silnik również nie jest na sztywno związany z jednym typem modelu. `MobileAlprEngine` pobiera wariant przez:

```java
autoTuneManager.chosenVariant(model)
```

a backend jest tworzony przez:

```java
RuntimeBackendFactory.create(model, variant, profile)
```

czyli istnieją już ścieżki dla TFLite, ONNX i NCNN.

## Główne problemy są dwa

### Problem A — wybór wariantu jest praktycznie ukryty w UI

W `SettingsActivity` istnieje już:

```java
showVariantSelection(ModelRole role, InstalledModel model)
```

oraz:

```java
autoTuneManager.pinVariant(model, variant.id())
```

Jednak dialog wyboru wariantu jest uruchamiany dopiero po wejściu w wybór modelu i ponownym zatwierdzeniu modelu.

Obecna ścieżka jest mniej więcej taka:

```text
MP / MT / MZ
    ↓
Wybierz model
    ↓
wybierz model
    ↓
Zastosuj
    ↓
dopiero teraz:
Wybierz wariant wykonawczy
```

Dla użytkownika wygląda to tak, jakby możliwości zmiany wariantu w ogóle nie było.

### Problem B — AutoTune celowo uprzywilejowuje FP32

W `AutoTuneManager.tuneLocked()` istnieje obecnie logika w rodzaju:

```java
boolean hasFp32 = hasExecutableFp32(model);
...
boolean selectable = !hasFp32 || isFp32(variant);
```

W praktyce oznacza to:

- INT8 może zostać przetestowany,
- ale jeżeli istnieje wykonywalny FP32, INT8 nie może zostać zwycięskim wariantem AutoTune.

Podobne ograniczenie występuje w `chosenVariant()`.

To jest stary bezpiecznik i należy go oddzielić od samego mechanizmu strojenia.

---

# 2. Docelowa architektura wyboru

Dla każdego aktywnego modelu MP/MT/MZ mają istnieć dwie niezależne decyzje:

```text
MODEL
  ↓
WARIANT WYKONAWCZY
  np. TFLite INT8
  ↓
PROFIL WYKONANIA
  np. CPU ×2
  ↓
InferenceBackend
```

Nie mieszaj tych pojęć.

## Warstwa 1 — wariant modelu

Wariant określa przede wszystkim:

```text
runtime
precision
variant_id
input/output contract
pliki modelu
```

Przykład:

```text
MT
model = plate-yolo26s
variant = tflite-int8
```

## Warstwa 2 — profil wykonania

Profil określa:

```text
CPU ×1
CPU ×2
CPU ×4
GPU
```

Przykład:

```text
variant = tflite-int8
execution = CPU ×2
```

AutoTune może stroić profil wykonania i — w trybie AUTO — może również porównywać wykonywalne warianty, ale ręcznie przypięty wariant ma zawsze pierwszeństwo.

---

# 3. Wymagany UX

Nie przebudowuj całego ekranu ustawień.

Wykorzystaj istniejący mechanizm dialogów i istniejące węzły MP/MT/MZ.

## Menu po kliknięciu MP / MT / MZ

Obecne menu w `showNodeActions()` należy rozszerzyć do:

```text
Wybierz model
Wybierz wariant wykonawczy
Zaimportuj model MP/MT/MZ
```

Dla MP nadal zachowaj istniejącą możliwość wyłączenia etapu przez wybór braku modelu.

### „Wybierz wariant wykonawczy”

Akcja ma działać bez ponownego wybierania modelu.

Jeżeli aktywny model nie istnieje:

```text
Brak aktywnego modelu MP/MT/MZ.
```

Jeżeli istnieje, otwieramy istniejący lub zrefaktoryzowany:

```java
showVariantSelection(role, activeModel)
```

Nie twórz drugiego, równoległego mechanizmu wyboru.

---

# 4. Dialog wariantu

Lista powinna zawierać:

```text
AUTO — aktualnie: TFLite FP32 · CPU ×2
TFLite FP32
TFLite INT8
ONNX FP32
ONNX INT8
NCNN FP32
```

Jeżeli wariant ma techniczny `variant.id`, może być pokazany jako informacja dodatkowa, ale nie powinien być główną nazwą dla użytkownika.

Przykład:

```text
TFLite INT8
tflite-int8
```

albo w jednej linii:

```text
TFLite INT8 · tflite-int8
```

### Wariant niedostępny

Jeżeli runtime nie jest dostępny na urządzeniu:

```text
NCNN FP32 · niedostępny: <reason>
```

Pozycję można pokazać, ale nie wolno jej skutecznie zatwierdzić.

Użyj istniejącego:

```java
RuntimeBackendFactory.isRuntimeAvailable(...)
RuntimeBackendFactory.unavailableReason(...)
```

---

# 5. Priorytet wyboru wariantu

Docelowa kolejność ma być jednoznaczna:

```text
1. wariant zamrożony przez aktywną sesję badawczą
2. ręcznie przypięty wariant użytkownika
3. wynik AutoTune
4. deterministyczny fallback
```

Nie wolno pozwolić, aby AutoTune nadpisał ręczny wybór.

---

# 6. Ręczny wybór — istniejący pin

Wykorzystaj istniejące:

```java
pinVariant(...)
clearPinnedVariant(...)
pinnedVariantId(...)
isVariantPinned(...)
chosenVariant(...)
```

Nie twórz nowego SharedPreferences tylko do wariantu, jeżeli obecny mechanizm działa poprawnie.

Klucz jest związany z rolą i fingerprintem modelu, co jest właściwe: pin starego modelu nie powinien automatycznie przechodzić na nową instalację modelu.

Po ręcznym wyborze:

```text
MT
TFLite · INT8 · CPU ×2
ręczny
```

Po wyborze `AUTO`:

```text
MT
TFLite · FP32 · CPU ×2
AutoTune
```

`ModelStatusFormatter` już pokazuje runtime, precision, CPU/GPU oraz „ręczny” / „AutoTune”. Zachowaj tę funkcję.

---

# 7. Zmiana polityki AutoTune

## Usunąć blokadę „FP32 zawsze wygrywa, jeżeli istnieje”

W `AutoTuneManager.tuneLocked()` nie ograniczaj kandydatów do FP32 tylko dlatego, że FP32 istnieje.

Obecna idea:

```java
boolean hasFp32 = hasExecutableFp32(model);
boolean selectable = !hasFp32 || isFp32(variant);
```

powinna zostać usunięta lub zastąpiona logiką, w której każdy wykonywalny wariant może być kandydatem AUTO.

Czyli:

```text
TFLite FP32  → benchmark
TFLite INT8  → benchmark
ONNX FP32    → benchmark
ONNX INT8    → benchmark
NCNN FP32    → benchmark

najlepszy poprawnie wykonany kandydat → AUTO
```

Kryterium może pozostać obecne, czyli najmniejsza mediana czasu, o ile bieżący kod AutoTune nie został w międzyczasie przebudowany.

## `chosenVariant()`

Jeżeli profil AutoTune wskazuje `chosen_variant_id`, nie odrzucaj tego wariantu tylko dlatego, że istnieje FP32.

Warunki powinny być w rodzaju:

```text
variant.id == chosen_variant_id
runtime dostępny
wariant zgodny z kontraktem
```

bez dodatkowego:

```text
musi być FP32, jeśli FP32 istnieje
```

## Fallback bez wykonanego AutoTune

Tutaj można zachować bezpieczne, deterministyczne pierwszeństwo FP32.

Czyli jeśli:

- nie ma pinu,
- nie ma ważnego profilu AutoTune,

fallback może nadal preferować np.:

```text
TFLite FP32
ONNX FP32
inne wykonywalne FP32
TFLite INT8
inne wykonywalne
```

To jest ważne, żeby sama instalacja nowej paczki nie zmieniała automatycznie zachowania aplikacji przed wykonaniem AutoTune.

Innymi słowy:

```text
fallback ≠ AutoTune
```

Fallback może być konserwatywny.
AutoTune ma faktycznie stroić wszystkie dostępne warianty.

---

# 8. Ważna uwaga o ONNX INT8

Nie utożsamiaj:

```text
precision = INT8
```

z:

```text
public input tensor = INT8
```

Eksporter ONNX INT8 używa QDQ. Publiczne wejście modelu może i powinno pozostać `FLOAT32`, podczas gdy wnętrze grafu jest kwantyzowane.

Nie zmieniaj istniejącego kontraktu:

```text
ONNX INT8 QDQ
precision = int8
input.data_type = FLOAT32
```

`ModelVariantContract` już to obsługuje.

---

# 9. Reload silnika po zmianie wariantu

Po zmianie wariantu nie wystarczy odświeżyć napisu w `SettingsActivity`.

Nowy wariant musi rzeczywiście trafić do `MobileAlprEngine`.

Obecnie wybór backendu następuje przy tworzeniu silnika:

```java
ModelVariant variant = autoTuneManager.chosenVariant(model);
RuntimeBackendFactory.create(model, variant, profile);
```

Dlatego po pin/unpin:

```text
SettingsActivity
    ↓
settings revision
    ↓
MainActivity
    ↓
przeładowanie konfiguracji / silnika
    ↓
MobileAlprEngine tworzony z nowym wariantem
```

Sprawdź aktualną implementację `applySettingsRevision()` i mechanizm `reloadRequested`/przeładowania pipeline'u.

**Warunek odbioru: po zmianie wariantu nie może być wymagany restart aplikacji.**

Po powrocie z ustawień pierwsza kolejna inferencja ma używać nowego wariantu.

Nie przebudowuj całego pipeline'u, jeśli istniejący mechanizm revision/reload już to zapewnia.

---

# 10. Tryb badawczy

To jest bardzo ważne dla pracy dyplomowej.

Sesja badawcza musi zamrozić faktycznie wybrany wariant.

`ResearchStageExecutionConfig` już przechowuje m.in.:

```text
modelId
modelFingerprint
variantId
runtime
precision
cpuThreads
gpu
inputDataType
```

oraz podczas wykonania używa `requireVariant(...)`.

Tego kontraktu nie osłabiaj.

## Zasada

Przed START użytkownik może ustawić:

```text
MT = TFLite INT8
MZ = TFLite FP32
MP = NCNN FP32
```

Po START konfiguracja sesji badawczej ma zapamiętać dokładnie te warianty.

W trakcie trwającego przebiegu badawczego:

- nie wolno po cichu przełączyć wariantu przez AutoTune,
- nie wolno po cichu przełączyć runtime,
- nie wolno po cichu przełączyć precision.

Jeżeli obecny UI pozwala wejść do ustawień w trakcie sesji, zmiana globalnego pinu nie może zmienić już zamrożonego przebiegu. Najprościej zablokować zmianę lub zastosować ją dopiero do kolejnej sesji.

---

# 11. Import paczki

Nie zmieniaj importera tylko po to, żeby naprawić ten problem.

Aktualny importer już:

- czyta wszystkie `variants`,
- sprawdza pliki wariantu,
- sprawdza SHA-256,
- waliduje runtime,
- waliduje TFLite/ONNX/NCNN,
- waliduje ONNX INT8 QDQ.

Nie rozbijaj jednego `InstalledModel` na kilka sztucznych modeli.

Poprawny model domenowy pozostaje:

```text
InstalledModel
    └── ModelManifest
          └── List<ModelVariant>
```

a nie:

```text
InstalledModel TFLite FP32
InstalledModel TFLite INT8
InstalledModel ONNX FP32
...
```

To są warianty tego samego modelu, a nie różne modele treningowe.

---

# 12. Pliki, które najprawdopodobniej trzeba zmienić

Po ponownej inspekcji bieżącego lokalnego kodu zweryfikuj przede wszystkim:

```text
app/src/main/java/com/example/alpr_v1/SettingsActivity.java
app/src/main/java/com/example/alpr_v1/autotune/AutoTuneManager.java
app/src/main/java/com/example/alpr_v1/ui/ModelStatusFormatter.java
app/src/main/java/com/example/alpr_v1/MainActivity.java
app/src/main/res/values/strings.xml
```

Potencjalnie testy:

```text
app/src/androidTest/java/com/example/alpr_v1/model/RuntimeCompositionInstrumentedTest.java
app/src/test/.../autotune/
app/src/test/.../model/
```

Nie zakładaj jednak, że po aktualnie wdrażanych zmianach struktura będzie identyczna.

---

# 13. Testy obowiązkowe

## V1 — import wielu wariantów

Paczka modelu zawiera:

```text
TFLite FP32
TFLite INT8
ONNX FP32
ONNX INT8
NCNN FP32
```

Po imporcie:

```text
manifest.variants().size() == 5
```

żaden poprawny wariant nie znika.

## V2 — jawna lista wariantów

Dla aktywnego modelu MT menu:

```text
MT
→ Wybierz wariant wykonawczy
```

pokazuje wszystkie warianty z paczki.

## V3 — ręczny INT8

Użytkownik wybiera:

```text
MT → TFLite INT8
```

Po powrocie do analizy:

- `chosenVariant()` zwraca `tflite-int8`,
- diagnostyka silnika pokazuje TFLite INT8,
- backend otwiera plik wariantu INT8,
- restart aplikacji zachowuje wybór.

## V4 — zmiana z INT8 na FP32

Użytkownik wybiera:

```text
MT → TFLite FP32
```

Silnik zostaje przeładowany bez restartu aplikacji i używa FP32.

## V5 — AUTO może wybrać INT8

Jeżeli benchmark INT8 przechodzi poprawnie i ma lepszą medianę niż FP32:

```text
AUTO → INT8
```

FP32 nie może blokować wyboru tylko przez sam fakt swojego istnienia.

## V6 — ręczny pin wygrywa z AutoTune

Jeżeli AutoTune wcześniej wybrał FP32, a użytkownik przypnie INT8:

```text
chosenVariant() == INT8
```

aż do wybrania `AUTO` lub zmiany modelu.

## V7 — fallback bez profilu

Brak:

```text
pinu
profilu AutoTune
```

nie powoduje losowego wyboru. Zachowaj deterministyczny fallback, najlepiej dotychczasowy FP32-first.

## V8 — niedostępny runtime

Wariant runtime'u, którego aplikacja nie może uruchomić:

- może być widoczny w liście,
- ma jasną adnotację „niedostępny”,
- nie może zostać skutecznie przypięty.

## V9 — ONNX INT8

`precision=int8` z publicznym `input.data_type=FLOAT32` nie może zostać błędnie odrzucony.

## V10 — różne role

Ręczny wybór ma być niezależny:

```text
MP = NCNN FP32
MT = TFLite INT8
MZ = ONNX FP32
```

Nie wolno jednym wyborem zmieniać pozostałych etapów.

## V11 — zmiana modelu

Pin wariantu starego modelu nie może zostać zastosowany do nowego modelu z innym fingerprintem.

## V12 — sesja badawcza

Przed START:

```text
MT = TFLite INT8
```

Po START `ResearchStageExecutionConfig` zapisuje:

```text
variant_id = tflite-int8
runtime = tflite
precision = int8
```

i przebieg nie zmienia tego wariantu do zakończenia.

## V13 — zgodność kompletnego pakietu ALPR

To samo musi działać zarówno dla:

```text
pojedynczego .alprmodel
```

jak i dla modelu zagnieżdżonego w:

```text
alpr.package.v1
```

## V14 — brak regresji pipeline'u

Po zmianach nadal działają:

```text
TFLite
ONNX
NCNN
MP opcjonalny
MT wymagany
MZ wymagany
tryb badawczy
AutoTune
```

---

# 14. Diagnostyka

Po załadowaniu silnika log diagnostyczny dla każdego etapu powinien jednoznacznie pokazywać co najmniej:

```text
role
model_id
fingerprint
variant_id
runtime
precision
input size
```

Przykładowo:

```text
MT model=plate-yolo26s
variant=tflite-int8
runtime=tflite
precision=int8
input=640x640
```

Dzięki temu da się jednoznacznie udowodnić, że telefon nie tylko wyświetlił INT8 w UI, ale rzeczywiście uruchomił ten wariant.

Nie dodawaj drugiego niezależnego źródła prawdy. Log ma opisywać ten sam `ModelVariant`, który został przekazany do `RuntimeBackendFactory`.

---

# 15. Czego NIE robić

Nie:

- zmieniaj schematu `alpr.model.v1`, jeśli nie okaże się to konieczne,
- rozbijaj wariantów jednego modelu na osobne `InstalledModel`,
- konwertuj modeli na telefonie,
- kwantyzuj modeli na telefonie,
- dobieraj wariantu na podstawie nazwy pliku,
- utożsamiaj INT8 z typem publicznego tensora wejściowego,
- pozwalaj AutoTune nadpisywać ręczny pin,
- pozwalaj AutoTune zmieniać wariant w środku zamrożonego przebiegu badawczego,
- resetuj obecnych lokalnych zmian agenta,
- przebudowuj pipeline'u MP→MT→MZ bez potrzeby.

---

# 16. Oczekiwany rezultat końcowy

Użytkownik importuje jedną paczkę zawierającą kilka wariantów:

```text
MT:
  TFLite FP32
  TFLite INT8
  ONNX FP32
  ONNX INT8
  NCNN FP32
```

Na telefonie może wejść:

```text
Ustawienia
→ MT
→ Wybierz wariant wykonawczy
→ TFLite INT8
```

Po zatwierdzeniu:

```text
MT
TFLite · INT8 · CPU ×2
ręczny
```

i następna inferencja rzeczywiście używa `tflite-int8`.

Po wybraniu:

```text
AUTO
```

aplikacja wraca do AutoTune. AutoTune może wybrać również INT8, jeżeli ten wariant jest wykonywalny i wygrywa pomiar czasu. Brak profilu AutoTune nadal korzysta z deterministycznego bezpiecznego fallbacku.

W trybie badawczym faktycznie użyty:

```text
variant_id
runtime
precision
execution profile
```

jest zamrożony i trafia do raportu sesji.

---

# 17. Definition of Done

Zadanie jest zakończone dopiero, gdy jednocześnie:

- wszystkie warianty z poprawnej paczki pozostają dostępne po imporcie,
- użytkownik ma bezpośredni i czytelny wybór wariantu MP/MT/MZ,
- można ręcznie przełączać FP32 ↔ INT8 bez ponownego importu,
- można ręcznie przełączać runtime, jeśli paczka go zawiera i backend jest dostępny,
- ręczny wybór rzeczywiście zmienia backend używany przez silnik,
- zmiana nie wymaga restartu aplikacji,
- AutoTune nie eliminuje INT8 tylko dlatego, że istnieje FP32,
- ręczny pin ma pierwszeństwo przed AutoTune,
- sesja badawcza zamraża wybrany wariant,
- testy regresji importera, kompozycji i runtime'ów przechodzą,
- dokumentacja architektury / dziennik budowy zostają zaktualizowane o jawny wybór wariantu wykonawczego.

Na końcu przygotuj krótkie podsumowanie:

```text
1. jakie pliki zmieniono,
2. jak działa wybór ręczny,
3. jak działa AUTO,
4. jak wymuszane jest przeładowanie silnika,
5. jak wariant jest zamrażany w badaniu,
6. jakie testy uruchomiono i z jakim wynikiem.
```
