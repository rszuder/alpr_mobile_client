# HANDOFF — Android: STATIC/DYNAMIC, granica sceny, tożsamość encji, AutoZoom i lock/search

## Repozytorium i punkt odniesienia

Repozytorium: `rszuder/alpr_mobile_client`

Gałąź:
`phase3b2-motion-overlay-final-hardening`

HEAD w chwili przygotowania handoff:

```text
8a19730b405449be698426df311e4085e3963081
Ogranicz detekcję pojazdów w stabilnej scenie i odblokuj kolejkę po wolnym MP
```

Ten handoff powstał po zmianach UX galerii, historii odczytów, weryfikacji próbek oraz po zmianach stabilizujących MP, kolejkę i przypisanie tablic.

Nie cofaj ani nie omijaj tych zmian.

---

# 1. Cel

Obecne `SceneHandlingMode` zawiera:

```java
STRICT_SCENE_BOUNDARY
a DYNAMIC_CONTINUITY
```

Semantyka nie jest jednak dostatecznie rozdzielona.

`STRICT_SCENE_BOUNDARY` nadal korzysta z dużej części wspólnej maszyny continuity. Może więc analizować ciągłość celu i uruchamiać ścieżki odzyskiwania, mimo że funkcjonalnie tryb statyczny nie powinien próbować ustalać ciągłości obiektów pomiędzy kolejnymi statycznymi scenami.

Celem zadania jest wprowadzenie dwóch jednoznacznych polityk:

```text
STATIC
DYNAMIC
```

Nie mają to być dwa zestawy progów tej samej logiki.
Mają to być dwie różne zasady interpretacji kolejnych klatek.

---

# 2. Niezmienniki wspólne

Niezależnie od trybu zachowaj:

- `ContinuityStamp`,
- `sceneGeneration`,
- `visualEpoch`,
- `cameraTransformGeneration`,
- ochronę przed opóźnionymi wynikami,
- `VehicleEntityRepository`,
- `AcquisitionQueue`,
- `ScanAcquisitionController`,
- `VehicleTrackingCoordinator`,
- `PlateTrackCoordinator`,
- `PlateVehicleAssociator`,
- `RecognitionHistoryStore`,
- `CapturedPlateItem`,
- `Weryfikacja próbek`,
- `StationarySceneSupport`,
- `StableSceneVehicleCache`,
- research freeze i `.alprsession`,
- aktualny kontrakt modeli/runtime.

Zmiana dotyczy przede wszystkim polityki sceny i cyklu życia encji.

---

# 3. Semantyka STATIC

## 3.1. Podstawowe założenie

STATIC służy do analizy nieruchomych próbek/scen.

System nadal obserwuje kolejne klatki w czasie, ale interesuje go głównie:

```text
czy obraz jako całość nadal przedstawia tę samą statyczną próbkę?
```

Nie interesuje go:

```text
czy pojazd po dużej zmianie obrazu jest fizycznie tym samym pojazdem co wcześniej?
```

## 3.2. Granica sceny

W STATIC granica sceny jest **twardą granicą domeny, trackingu i prezentacji**.

Nie utożsamiaj jej z samą utratą lokalnego trackera. Powinna wynikać z polityki
zmiany statycznej próbki opisanej w sekcji 7.

Po potwierdzonym `NEW_SCENE` kolejność semantyczna ma być następująca:

```text
detect static scene change
-> advance sceneGeneration
-> close old-scene dispatch
-> hard reset domain/tracking/acquisition/presentation
-> WAIT_FOR_FRESH_SCENE_MEASUREMENT
-> accept only results stamped with new sceneGeneration
```

Reset ma objąć co najmniej:

- aktywne `VehicleEntity`,
- mapowania vehicleTrack -> entity,
- mapowania plateTrack -> entity,
- bieżące tracki MP/MT,
- stan aktywnego celu i `TargetSession`,
- kolejkę i krótką sesję akwizycji zależną od poprzedniej sceny,
- lock celu,
- bieżące konsensusy temporalne należące do poprzedniej sceny,
- cache ROI,
- `StableSceneVehicleCache`,
- budżet i pamięć AZ należące do starej sceny,
- `DetectionOverlayView` — wszystkie VEHICLE / VEHICLE_ROI / PLATE,
- badge i mapy odczytów encji,
- `pendingPlateReadings`,
- `pendingPlateTransfers` / plate absorption,
- fade/overlay/active-target animations,
- predykcyjną geometrię prezentacji.

Na twardej granicy STATIC nie wykonuj łagodnego fade starej geometrii.
Poprawnym stanem przejściowym jest pusty overlay.

Każdy wynik rozpoczęty dla poprzedniego `sceneGeneration` ma być odrzucony
przed aktualizacją domeny, konsensusu, historii bieżącej sceny i UI.
Wykorzystaj istniejące stampy oraz bariery dispatch/presentation; nie twórz
drugiego równoległego systemu generacji.

Nie resetuj globalnego licznika `nextEntityId`.
Nowa encja powinna dostać nowy identyfikator.

## 3.3. Najważniejsza reguła tożsamości

W STATIC:

> `sceneGeneration` jest twardą granicą tożsamości encji.

Przykład:

```text
scene=10 entity=41 text=WI1234A

zmiana sceny

scene=11 entity=52 text=WI1234A
```

To są dwie różne encje.

Nawet jeżeli:

- tekst tablicy jest identyczny,
- pojazd wygląda podobnie,
- geometrycznie znajduje się w podobnym miejscu,
- człowiek wie, że to ten sam samochód.

STATIC nie próbuje tego rozstrzygać.

## 3.4. Tekst tablicy nie jest kluczem encji

Nigdy nie używaj `plateText` / `groundTruthText` / konsensusu OCR do przywracania `entityId` po zmianie sceny.

Tekst może służyć do:

- historii użytkowej,
- późniejszej agregacji raportu,
- porównania wyników,
- wyszukiwania.

Nie może służyć do zachowania tożsamości domenowej pomiędzy scenami STATIC.

## 3.5. Brak cross-scene reacquire

Po `NEW_SCENE` w STATIC nie uruchamiaj ścieżki:

```text
SOFT_REACQUIRE starego celu
```

Nie próbuj zachować starej encji przez:

- appearance,
- motion explanation,
- vehicle pool,
- stary track,
- zgodność tekstu.

Nowa scena zaczyna się od czystego stanu aktywnych encji.

## 3.6. Co może pozostać w czasie w STATIC

W obrębie jednego `sceneGeneration` nadal dozwolone i potrzebne są:

- kolejne obserwacje MT/MZ,
- temporalny konsensus tekstu,
- krótkotrwałe śledzenie techniczne,
- stabilizacja overlayu,
- kolejka pojazdów,
- ponowne wykorzystanie świeżego MP przy stabilnym obrazie.

STATIC oznacza brak ciągłości **pomiędzy scenami**, a nie brak czasu wewnątrz pojedynczej sceny.

---

# 4. Semantyka DYNAMIC

## 4.1. Podstawowe założenie

W DYNAMIC duża zmiana obrazu nie oznacza automatycznie nowej sceny.

Może wynikać z:

- ruchu kamery,
- ruchu pojazdu,
- zmiany skali,
- częściowego zasłonięcia,
- chwilowej utraty celu,
- opóźnienia MP/MT.

## 4.2. Dowody ciągłości

DYNAMIC powinien wykorzystywać istniejące informacje o:

- ruchu kamery,
- globalnym ruchu obrazu,
- stanie trackera celu,
- stanie puli pojazdów,
- geometrii,
- appearance pojazdu/tablicy,
- świeżych pomiarach MP,
- świeżych pomiarach MT,
- wieku pomiarów.

## 4.3. Dozwolone decyzje

DYNAMIC może zwracać co najmniej:

```text
KEEP
SOFT_REACQUIRE
RELEASE_TARGET
NEW_SCENE / HARD_RESET
```

Jeżeli ciągłość zostanie potwierdzona, domenowe `entityId` może przetrwać zmianę technicznego tracku i krótką utratę celu.

Jeżeli ciągłość nie zostanie potwierdzona, wykonaj pełną granicę sceny i utwórz nowe encje.

---

# 5. Zalecana architektura polityk

Nie rozbudowuj dalej jednego `observeStrict()` jako wyjątków we wspólnej maszynie.

Preferowany kierunek:

```text
ScenePolicy
  ├── StaticScenePolicy
  └── DynamicContinuityPolicy
```

Wspólny koordynator może nadal być właścicielem:

- liczników generacji,
- deduplikacji zdarzeń,
- side effects / publikowania decyzji,
- `ContinuityStamp`.

Polityka powinna odpowiadać za interpretację dowodów.

Przykładowy kontrakt:

```java
interface ScenePolicy {
    ScenePolicyDecision observe(SceneEvidence evidence, ScenePolicyContext context);
    void reset();
}
```

Nie jest wymagane dokładnie takie API.
Ważne jest fizyczne rozdzielenie semantyki.

STATIC nie powinien wykonywać pełnego:

```text
TargetContinuityEvaluator
VehicleContinuityEvaluator
MotionExplanationEvaluator
```

po to, aby zdecydować, czy nowa statyczna scena jest nadal tą samą sceną.

DYNAMIC może zachować obecną rozbudowaną ocenę.

---

# 6. Kompatybilność `SceneHandlingMode`

Nie łam zapisanych ustawień i starych raportów.

Możliwe rozwiązanie:

```text
strict_scene_boundary -> STATIC
dynamic_continuity    -> DYNAMIC
```

W kodzie można:

1. zachować istniejący enum i zmienić jego semantykę, albo
2. wprowadzić nowy `AnalysisSceneMode` i parser aliasów.

Preferowane w UI:

```text
Statyczny
Dynamiczny
```

Nie pokazuj użytkownikowi słowa `Strict`.

---

# 7. Detekcja granicy STATIC — regiony ALPR + globalny bezpiecznik

Wykorzystaj istniejący lekki tor obrazu/luminancji. Nie twórz drugiego ciężkiego
pipeline'u tylko do wykrywania zmiany sceny.

Najważniejsza korekta względem poprzedniego założenia:

> STATIC nie powinien traktować każdego fragmentu całej klatki z jednakową wagą.
> Podstawowym sygnałem zmiany próbki mają być regiony istotne dla ALPR.

## 7.1. Budowanie `StaticSceneWatchRegions`

Regiony uzbrój dopiero po zakończeniu:

```text
BASELINE_QUEUE
-> AZ_REFINEMENT
-> return to base zoom 1.0x
```

Preferowana hierarchia źródeł:

```text
1. MP aktywny i mamy świeże pojazdy
   -> expanded vehicle bounds

2. MP nieaktywny / R0, ale MT znalazł tablice
   -> expanded plate bounds

3. brak wiarygodnych regionów
   -> global-only watcher
```

Margines rozszerzenia ma być parametrem polityki/profilu, nie przypadkową
stałą zaszytą w `MainActivity`.

Watch region jest **nieruchomym obszarem odniesienia sceny N**.
Nie jest trackiem, nie ma `entityId` i nie wolno go przesuwać na nowy obiekt.

## 7.2. Decyzja o `NEW_SCENE`

W `STATIC_IDLE` aktywny pozostaje lekki watcher:

```text
primary:
significant change in any StaticSceneWatchRegion

OR

fallback:
very strong / confirmed global frame change
```

Globalny bezpiecznik jest potrzebny dla:

- scen bez wykrytego pojazdu/tablicy,
- nowej istotnej treści pojawiającej się poza poprzednimi regionami,
- dużej podmiany całego obrazu.

Progi lokalne i globalne mogą być różne. Regiony ALPR mogą być bardziej czułe,
a globalny fallback bardziej konserwatywny.

Ważne:

- unikaj resetu od pojedynczego szumu,
- zachowaj odporność na krótkie zaburzenie ekspozycji,
- nie używaj continuity obiektu do anulowania wyraźnej granicy STATIC,
- nie aktualizuj watch regionów trackingiem w `STATIC_IDLE`,
- progi należą do profilu/polityki i muszą być testowalne,
- wykorzystaj istniejące globalne/luma metryki jako sygnał, gdzie to możliwe.

## 7.3. Hard boundary i bariera prezentacji

Po potwierdzeniu granicy:

```text
sceneGeneration++
-> HARD RESET OLD SCENE
-> EMPTY OVERLAY
-> WAIT_FOR_FRESH_SCENE_MEASUREMENT
```

Nie pozwól `stabilizeStationaryVehicles()` ani innemu mechanizmowi hold/prediction
wykorzystać `items` starej sceny do stabilizacji pierwszej ramki nowej sceny.

Stara ramka może służyć przed cutem jako `SceneWatchRegion`.
Po cut jej rola kończy się natychmiast.

Zabroniony przypadek:

```text
scene 1:
entity A / vehicle box / WI1234A

SCENE CUT

scene 2:
new vehicle B near old position

FORBIDDEN:
old box moves to B and keeps WI1234A
```

Do czasu świeżego MP/MT/MZ nowej sceny B ma nie mieć odziedziczonej geometrii
ani tekstu.

---

# 8. Aktywny zoom — wspólny kontrakt

AZ nie jest mechanizmem wyszukiwania tablicy.

Nadrzędna zasada:

```text
AZ wolno uruchomić tylko wtedy, gdy MT wcześniej znalazł tablicę
oraz istnieje poprawny validQuad / geometria celu.
```

Jeżeli:

```text
vehicle found
MT -> no plate / no valid quad
```

wynik:

```text
AZ = FORBIDDEN
```

Jeżeli:

```text
vehicle found
MT -> valid plate quad
MZ -> brak odczytu / słaby odczyt
```

AZ może zostać użyty zgodnie z polityką bieżącego trybu.

Sam kontrolowany zoom nie oznacza automatycznie nowej sceny.

Zachowaj:

```text
cameraTransformGeneration++
```

bez:

```text
sceneGeneration++
```

Wyniki rozpoczęte przed zmianą transformacji mają zostać odrzucone lub ponownie wyznaczone zgodnie z istniejącymi barierami.

`AutoZoomController` pozostaje właścicielem technicznego cyklu:

```text
READY
-> ZOOM_SETTLING
-> ZOOMED_RETRY
-> RETURNING
```

ale NIE powinien samodzielnie decydować, czy na danym etapie analizy wolno rozpocząć AZ.

Prawo do uruchomienia AZ ma wynikać z nadrzędnej polityki STATIC/DYNAMIC i stanu akwizycji/locka.

---

# 9. AZ w STATIC — osobna faza poprawy

STATIC ma mieć jawny cykl sceny:

```text
SCENE_START
    -> BASELINE_DISCOVERY_AND_ACQUISITION
    -> AZ_REFINEMENT
    -> STATIC_IDLE
    -> czekaj na NEW_SCENE
```

## 9.1. Faza podstawowa

Najpierw obsłuż zwykłą kolejkę pojazdów.

Dla kandydatów wykonaj normalny tok:

```text
MP / vehicle candidates
-> queue
-> MT
-> rectification
-> MZ
-> provisional/final reading
```

AZ nie może przerywać pierwszego przejścia kolejki tylko dlatego, że pierwszy pojazd ma małą tablicę albo słaby OCR.

Najpierw uzyskaj podstawowy obraz całej sceny.

## 9.2. Kolejka poprawy AZ

Po zakończeniu podstawowej kolejki utwórz logiczną kolejkę `AZ_REFINEMENT`.

Do AZ kwalifikują się wyłącznie encje, dla których:

```text
entity exists
AND plate geometry exists
AND validQuad == true
AND normal MT was completed
AND at least one normal MZ attempt was made
AND result can still benefit from refinement
AND AZ not attempted for this entity in this scene
```

`result can still benefit` obejmuje np.:

- świeże MZ nie wykryło czytelnych znaków,
- wynik jest niepotwierdzony,
- recognition confidence jest niski,
- tablica jest mała w obrazie.

Nie uruchamiaj AZ dla mocnego, potwierdzonego wyniku bez potrzeby.

## 9.3. Jeden cykl na encję/scenę

Budżet:

```text
(sceneGeneration, entityId) -> max 1 automatic AZ cycle
```

Po zakończeniu oznacz encję jako `AZ_DONE` dla bieżącej sceny niezależnie od tego, czy wynik się poprawił.

Nowy `sceneGeneration` tworzy nowe encje i nowy budżet AZ.

## 9.4. Właściwy cykl STATIC AZ

```text
normal plate geometry
-> request 1.8x zoom
-> settle AF/AE
-> fresh MT
-> fresh quad
-> rectification
-> fresh MZ
-> compare/store result
-> return 1.0x
-> AZ_DONE
```

W samym cyklu STATIC AZ nie uruchamiaj ponownie MP.
Cel został wcześniej zlokalizowany.

Świeży MT jest obowiązkowy po zoomie, ponieważ zmieniła się geometria obrazu.

## 9.5. STATIC_IDLE

Po:

```text
baseline queue complete
AND AZ refinement queue complete
```

przejdź do:

```text
STATIC_IDLE
```

W `STATIC_IDLE` zatrzymaj ciężki potok:

```text
MP = OFF
MT = OFF
MZ = OFF
AZ = OFF
```

Nie powtarzaj trackingu domenowego/akwizycji w nieskończoność dla tego samego nieruchomego obrazu.

Aktywny ma pozostać tylko lekki tor wykrywania zmiany całej sceny i minimalna infrastruktura potrzebna do ochrony UI.

Po `NEW_SCENE`:

```text
reset scene state
-> new sceneGeneration
-> new entities
-> BASELINE
-> AZ_REFINEMENT
-> STATIC_IDLE
```

---

# 10. AZ w DYNAMIC

W zwykłym `DYNAMIC + SCAN_ACQUIRE`:

```text
AZ = OFF
```

Nie zatrzymuj wielopojazdowej kolejki dla kosztownego zoomowania losowych kandydatów.

AZ może zostać dopuszczony tylko dla:

1. trwałego celu wybranego ręcznie (`USER_PICK` / `PICK_ACQUIRE_LOCK`),
2. trwałego celu `SEARCH_PURSUIT`,
3. krótkiej weryfikacji `POSSIBLE_MATCH` podczas wyszukiwania rejestracji.

Warunki rozpoczęcia DYNAMIC AZ:

```text
entityId active
AND validQuad
AND normal MZ already attempted
AND result needs refinement
AND no rapid camera motion
AND no continuity reacquire
AND target geometry is stable
AND target is not at unsafe frame edge
AND AZ budget not consumed
```

Podczas AZ:

```text
pause normal AcquisitionQueue dispatch
```

ale nie usuwaj pozostałych encji.

Po powrocie do 1.0x:

```text
fresh scene/geometry anchor
-> fresh MP if vehicle cascade requires it
-> resume queue
```

Jeżeli podczas zoomu wystąpi:

```text
rapid motion
TARGET_LOST
structural scene change
confirmed continuity break
```

wykonaj:

```text
ABORT AZ
-> RETURN 1.0x
-> hand control to DYNAMIC continuity/reacquire
```

Budżet dla trwałego locka:

```text
(entityId, lockRevision) -> max 1 automatic AZ cycle
```

Nie dopuszczaj oscylacji 1.0x <-> 1.8x co kilka sekund.

---

# 11. Wybór i trwały lock celu w DYNAMIC

LOCK jest funkcją DYNAMIC. W STATIC nie utrzymuj trwałego celu pomiędzy scenami.

Obecny model domenowy już zawiera właściwe rozróżnienie:

```text
ApplicationMode.SCAN_ACQUIRE
ApplicationMode.PICK_ACQUIRE_LOCK
ApplicationMode.SEARCH_VERIFY_PURSUIT
```

oraz cele:

```text
SCAN_ACQUISITION
USER_PICK
SEARCH_VERIFICATION
SEARCH_PURSUIT
```

Nie twórz równoległej, drugiej maszyny trybów. Dokończ istniejącą architekturę.

## 11.1. Manual lock — PICK_ACQUIRE_LOCK

W trybie DYNAMIC użytkownik może wskazać widoczną ramkę pojazdu.

Przepływ:

```text
DYNAMIC scan
-> user taps vehicle overlay
-> resolve entityId
-> ModeController.switchMode(PICK_ACQUIRE_LOCK)
-> startSession(entityId, USER_PICK)
-> persistent target session / LOCK
```

Lockuj domenowe `entityId`, nie `vehicleTrackId`, `plateTrackId` ani `plateText`.

Zmiana technicznego tracku nie może sama zakończyć manualnego locka, jeżeli DYNAMIC continuity potwierdza tę samą encję. Błędny albo zmieniony OCR również nie może automatycznie zwolnić ręcznie wskazanego celu.

Użytkownik powinien mieć jawną akcję `Zwolnij cel` albo możliwość wskazania innego pojazdu.

Jeżeli użytkownik ręcznie wybierze inny pojazd podczas automatycznego pursuit:

```text
manual USER_PICK > automatic SEARCH_PURSUIT
```

## 11.2. Search lock — wyszukiwanie wcześniej podanej rejestracji

Dokończ `SEARCH_VERIFY_PURSUIT`.

W głównym ekranie kamery w trybie DYNAMIC dodaj ikonę:

```text
ic_search_24.xml
```

Piktogram: lupa / search.

Kliknięcie otwiera mały dialog albo bottom sheet:

```text
Szukaj pojazdu

Numer rejestracyjny
[ WI1234A ]

[ ANULUJ ] [ SZUKAJ ]
```

Po zatwierdzeniu:

```text
switchMode(SEARCH_VERIFY_PURSUIT)
store normalized target registration
continue ordinary scan
```

Nie wymagaj, aby UI znało pozycję pojazdu. System ma go znaleźć przez normalną akwizycję.

## 11.3. Stany dopasowania

Wykorzystaj istniejący `SearchMatchState`:

```text
NOT_EVALUATED
NO_MATCH
POSSIBLE_MATCH
CONFIRMED_MATCH
REJECTED_MATCH
```

Nie ustanawiaj trwałego locka po jednym słabym podobnym odczycie.

Zalecana semantyka:

```text
no plausible similarity -> NO_MATCH
single exact or close reading -> POSSIBLE_MATCH
fresh independent verification -> CONFIRMED_MATCH
contradictory fresh evidence -> REJECTED_MATCH / back to scanning
```

Do generowania `POSSIBLE_MATCH` można użyć dokładnej zgodności, małej odległości edycyjnej (np. jeden znak) jako kandydata i reguł normalizacji rejestracji. Fuzzy match nie jest końcowym potwierdzeniem.

## 11.4. Weryfikacja możliwego dopasowania

`POSSIBLE_MATCH` otrzymuje priorytet nad zwykłą kolejką, ale jeszcze nie staje się trwałym pursuit.

```text
SEARCH_VERIFICATION
-> fresh MT
-> fresh MZ
-> evaluate match
```

Jeżeli istnieje `validQuad` i odczyt jest bliski szukanemu, ale nadal niepewny, można wykorzystać jeden AZ zgodnie z sekcją DYNAMIC AZ.

Po potwierdzeniu:

```text
CONFIRMED_MATCH
-> ModeController.promoteSearchToPursuit()
-> SEARCH_PURSUIT
-> persistent LOCK on entityId
```

Po wejściu w `SEARCH_PURSUIT` dalsze utrzymanie celu opiera się na ciągłości fizycznej encji. Nie wymagaj ponownego dokładnego OCR w każdej klatce.

## 11.5. Priorytet celu

Kolejność priorytetów w DYNAMIC:

```text
1. manual USER_PICK lock
2. active SEARCH_PURSUIT
3. POSSIBLE_MATCH / SEARCH_VERIFICATION
4. normal AcquisitionQueue
```

W danym momencie najwyżej jedna encja może mieć trwały lock i `cameraAttentionOwned=true`.

## 11.6. UI search

W DYNAMIC:
- zwykła lupa -> brak aktywnego wyszukiwania,
- aktywna/zaakcentowana lupa -> trwa wyszukiwanie,
- HUD pokazuje np. `Szukam: WI1234A`,
- przy `POSSIBLE_MATCH`: `Możliwe dopasowanie · weryfikuję…`,
- przy pursuit: `WI1234A · cel znaleziony`.

Ponowne kliknięcie lupy podczas aktywnego wyszukiwania powinno pozwolić wybrać `Zmień numer` albo `Zakończ wyszukiwanie`.

W STATIC ikona search ma być ukryta albo disabled. Nie udawaj trwałego pursuit w trybie, w którym granica sceny kończy tożsamość encji.

---

# 12. `StationarySceneSupport` i `StableSceneVehicleCache`

Te klasy zostały dodane przed tym refaktorem i należy je zachować.

Ich rola:

```text
optymalizacja kosztu MP w stabilnej scenie
```

Nie ich rola:

```text
autorytet tożsamości sceny/obiektu
```

Zachowaj zasadę:

```text
cache usable tylko dla zgodnych:
sceneGeneration
visualEpoch
cameraTransformGeneration
```

Cache musi zostać unieważniony po:

- nowej scenie,
- zmianie transformacji kamery,
- rozpoczęciu reacquire,
- utracie celu wymagającej świeżego MP,
- zmianie modeli/engine,
- zatrzymaniu/nowym uruchomieniu runu.

Nie pozwól, aby optymalizacja cache obchodziła politykę STATIC/DYNAMIC.

---

# 13. Encje i repozytorium

`VehicleEntityRepository` już opisuje się jako źródło prawdy dla bieżącej sceny.

W STATIC po granicy sceny wywołaj semantyczny reset sceny.

Oczekiwane:

```text
active entities = 0
vehicle ownership maps = empty
plate ownership maps = empty
active target/session = none
acquisition queue = reset
old-scene consensus = unavailable
old-scene overlay = empty
```

Reset repozytorium nie wystarcza sam w sobie. Warstwa prezentacji i asynchroniczne
wyniki mają własny stan, dlatego granica STATIC musi być egzekwowana również
przez `ContinuityStamp`, dispatch gate i presentation barrier.

Ale:

```text
nextEntityId
```

ma pozostać monotoniczny.

Nie usuwaj trwałej historii użytkowej i zapisanych już próbek badawczych.

W DYNAMIC zachowaj możliwość:

```text
reassignVehicleTrack(...)
reassignPlateTrack(...)
```

jeżeli ciągłość encji jest rzeczywiście potwierdzona.

---

# 13.1. Twardy reset prezentacji STATIC

Dodaj lub wydziel jedną operację semantyczną typu:

```text
hardResetForNewStaticScene(newSceneGeneration)
```

Nazwa implementacyjna może być inna, ale reset powinien być centralny i
idempotentny.

Powinien on:

```text
cancel animations
clear render/items geometry
clear vehicle recognition badges
clear pending plate readings/transfers
clear active marker
invalidate old prediction/hold state
arm presentation barrier for new sceneGeneration
```

Nie polegaj wyłącznie na `resetVehicleEntityStates()`, jeżeli pozostawia ono
`items` / `renderItems` albo animację geometrii.

Pierwszy overlay nowej sceny wolno pokazać dopiero z wyniku posiadającego
aktualny stamp.

---

# 14. Historia odczytów

Nie cofaj nowego `RecognitionHistoryStore`.

Obecne założenie jest poprawne:

```text
historyId = sceneGeneration + entityId
```

z fallbackiem na plateTrack/track przed przypisaniem do encji.

Ważny test STATIC:

```text
scene 10 / entity 41 / WI1234A
scene 11 / entity 52 / WI1234A
```

ma dawać dwa wpisy historii.

Nigdy nie deduplikuj historii wyłącznie po tekście `WI1234A`.

Zachowaj również:

- promocję provisional plate entry do entity entry,
- `visualEpoch` w kluczu prowizorycznego tracku,
- najlepszą miniaturę confidence -> sharpness -> czas,
- limit 40,
- recykling bitmap.

---

# 15. Weryfikacja próbek

Nie przebudowuj ponownie obecnego UX.

Zachowaj:

```text
Zgodne
Koryguj odczyt
Nie do oceny
```

oraz:

- read-only boxy MZ,
- `VerificationIssue`,
- `needsDesktopReview`,
- `verificationNote`,
- `eligible_for_text_metrics`,
- batch save,
- `HumanVerificationJson`,
- `CropInferenceTiming`.

Tryb sceny ma zostać tylko dodany do provenance/telemetrii próbki tam, gdzie jest to potrzebne.

---

# 16. Research mode

Usuń zasadę:

```text
experiment == true -> zawsze STRICT
```

Eksperyment ma jawnie wybierać i zamrażać:

```text
analysis_mode = static | dynamic
```

Przykłady:

```text
E1: porównanie MT n vs s na niezależnych statycznych próbkach
-> STATIC
```

```text
tracking / lock / reacquire
-> DYNAMIC
```

Przy START sesji tryb staje się częścią frozen research configuration.

Nie pozwalaj zmienić go w połowie aktywnego przebiegu.

---

# 17. Eksport i telemetry

Dodaj jednoznaczne pole semantyczne do raportu/research archive, np.:

```json
"analysis_mode": "static"
```

lub:

```json
"analysis_mode": "dynamic"
```

Dla kompatybilności można równolegle zachować historyczne:

```json
"scene_handling_mode": "strict_scene_boundary"
```

Raport powinien umożliwić jednoznaczne ustalenie, w jakiej semantyce wykonano przebieg.

Zdarzenie granicy STATIC powinno być mierzalne, np.:

```text
static_scene_boundary
scene_generation_before
scene_generation_after
reason
whole_frame_change metrics
```

Nie dodawaj nadmiarowych danych per-frame, jeżeli istniejące `InferenceTrace` już je zawiera.

---

# 18. UI

W Opcjach:

```text
Tryb analizy sceny

[ Statyczny ] [ Dynamiczny ]
```

Opis STATIC:

```text
Statyczny — wyraźna zmiana całego nieruchomego obrazu rozpoczyna nową scenę. Obiekty nie są łączone pomiędzy scenami.
```

Opis DYNAMIC:

```text
Dynamiczny — aplikacja próbuje zachować ciągłość pojazdu podczas ruchu kamery i chwilowej utraty celu.
```

W trybie badawczym pokaż zamrożony tryb w podsumowaniu konfiguracji.

---

# 19. Zmiana trybu podczas zwykłej pracy

Przełączenie:

```text
STATIC <-> DYNAMIC
```

musi być granicą polityki.

Nie przenoś aktywnej encji z jednej semantyki do drugiej.

Po zmianie trybu wykonaj kontrolowany reset stanu sceny/trackingu i rozpocznij nową generację lub równoważny bezpieczny reset.

Historia użytkowa może pozostać.

---

# 20. Testy obowiązkowe

## S1 — nowa scena tworzy nową encję

STATIC:

```text
scene=1 entity=10 WI1234A
change in ALPR watch region / confirmed global fallback
scene=2
WI1234A ponownie
```

Oczekiwane:

```text
entityId != 10
```

## S2 — ten sam OCR nie scala scen

Historia po S1 ma zawierać dwa wpisy.

## S3 — brak soft reacquire starego celu przez granicę STATIC

Po granicy sceny nie może zostać odzyskane stare `entityId`.

## S4 — mały jitter nie tworzy sceny

Niewielkie zmiany/statyczny szum nie zwiększają `sceneGeneration`.

## S5 — kontrolowany zoom

STATIC:

```text
cameraTransformGeneration++
sceneGeneration bez zmian
```

## S6 — konsensus działa wewnątrz jednej sceny

Kilka obserwacji MZ przed granicą może budować temporalny wynik.

Po granicy konsensus starej sceny nie może wpłynąć na nową.

## S7 — zmiana w regionie pojazdu tworzy nową scenę

STATIC z aktywnym MP:

```text
scene N -> vehicle watch region armed
content in that region changes strongly
```

Oczekiwane: `sceneGeneration++` nawet jeżeli reszta tła pozostała podobna.

## S8 — tło poza regionami nie powoduje łatwego resetu

Mała/lokalna zmiana poza regionami ALPR, która nie przekracza konserwatywnego
globalnego progu, nie tworzy nowej sceny.

## S9 — brak regionów korzysta z globalnego fallbacku

Scena bez MP/MT watch regions nadal może wykryć dużą podmianę całego kadru.

## S10 — zero przeniesienia starego overlayu

Scenariusz regresyjny obowiązkowy:

```text
scene 1:
entity A -> box A -> WI1234A

SCENE CUT

scene 2:
vehicle B appears near box A
```

Przed świeżym wynikiem sceny 2 oczekiwane:

```text
no old vehicle box
no old plate box
no WI1234A badge
```

Zakazane jest choćby jednoklatkowe przesunięcie starej ramki na B.

## S11 — opóźniony wynik starej sceny jest dropowany

Wynik MP/MT/MZ rozpoczęty w scenie 1 i zakończony już po `sceneGeneration++`
nie aktualizuje encji, historii bieżącej sceny ani UI.

## S12 — hard boundary anuluje animacje

Plate fade / absorption / overlay animation rozpoczęte w scenie 1 nie mogą
dokończyć się na scenie 2.

## D1 — ruch kamery z zachowaną ciągłością

DYNAMIC:

```text
large visual change + coherent motion + recovered entity
```

nie zwiększa `sceneGeneration` i zachowuje `entityId`.

## D2 — temporary loss + reacquire

DYNAMIC może przejść przez SOFT_REACQUIRE i wrócić do tej samej encji.

## D3 — continuity break

Brak dowodów ciągłości kończy scenę i tworzy nowe encje.

## M1 — mode switch

Zmiana STATIC/DYNAMIC nie może zachować aktywnej encji poprzedniej polityki.

## C1 — stable MP cache nie przechodzi granicy

`StableSceneVehicleCache.current()` po zmianie któregokolwiek:

```text
sceneGeneration
visualEpoch
cameraTransformGeneration
```

zwraca brak starego pomiaru.

## H1 — provisional history promotion nadal działa

Nie regresuj aktualnych testów galerii.

## H2 — identyczny tekst na różnych encjach nie jest scalany

Zachowaj aktualny test.

## R1 — frozen research mode

START w STATIC -> raport STATIC nawet po próbie zmiany ustawień.

START w DYNAMIC -> raport DYNAMIC.

## R2 — R0/R1/R2 nie wymuszają globalnie STATIC

Mode wynika z konfiguracji danego eksperymentu.

---

## AZ1 — STATIC nie używa AZ do discovery

MT bez validQuad -> zero REQUEST_ZOOM.

## AZ2 — STATIC AZ dopiero po baseline queue

Pierwszy słaby pojazd nie może przerwać podstawowej obsługi pozostałych encji.

## AZ3 — STATIC jeden AZ na entity/scene

Druga próba dla tego samego `(sceneGeneration, entityId)` jest blokowana.

## AZ4 — STATIC_IDLE

Po baseline + AZ refinement brak ciężkich inference do momentu NEW_SCENE.

## AZ5 — new scene resets AZ budget

Nowa encja w nowym `sceneGeneration` może ponownie otrzymać jedną próbę.

## AZ6 — DYNAMIC scan nie uruchamia AZ

`SCAN_ACQUIRE` bez lock/search verification -> zero automatic REQUEST_ZOOM.

## AZ7 — DYNAMIC lock może uruchomić AZ

USER_PICK albo SEARCH_PURSUIT + validQuad + weak result -> maksymalnie jedna próba.

## AZ8 — DYNAMIC AZ abort

Rapid motion / continuity break podczas zoomu -> RETURN_NORMAL i przejście do continuity/reacquire.

## L1 — manual lock wiąże entityId

Zmiana `vehicleTrackId` przy zachowanej continuity nie kończy USER_PICK.

## L2 — OCR nie zwalnia manual locka

Słabsza lub inna predykcja MZ nie powoduje automatycznego unlock.

## L3 — search UI normalization

Wpisana rejestracja jest normalizowana deterministycznie i uruchamia SEARCH_VERIFY_PURSUIT.

## L4 — possible match nie jest pursuit

Jedna bliska predykcja -> POSSIBLE_MATCH / SEARCH_VERIFICATION, bez trwałego locka.

## L5 — confirmed search promotes pursuit

Fresh verification -> CONFIRMED_MATCH -> `promoteSearchToPursuit()` dla tego samego entityId.

## L6 — manual override

USER_PICK innej encji przejmuje priorytet nad SEARCH_PURSUIT.

## L7 — search hidden/disabled in STATIC

UI nie pozwala rozpocząć trwałego wyszukiwania w STATIC.

# 21. Testy regresyjne, których nie wolno zepsuć

Uruchom istniejące testy dotyczące:

- `RecognitionHistoryStore`,
- `GalleryPresentationInstrumentedTest`,
- `HumanVerificationJsonInstrumentedTest`,
- `RuntimeCompositionInstrumentedTest`,
- `FinalPipelineResultDispatchInstrumentedTest`,
- `DetectionOverlayViewInstrumentedTest`,
- `StableSceneVehicleCacheTest`,
- `StationarySceneSupportTest`,
- `AcquisitionQueueTest`,
- `ScanAcquisitionControllerTest`,
- `PlateVehicleAssociatorTest`,
- `PlateTrackCoordinatorTest`,
- NCNN/runtime contract tests.

Nie deklaruj sukcesu testów, których faktycznie nie uruchomiono.

---

# 22. Elementy ostatnich commitów, które muszą pozostać

Zachowaj zachowanie wprowadzone po `471bacd4`:

- rozdzielenie `Ostatnie odczyty` / `Weryfikacja próbek`,
- provisional crop -> entity history promotion,
- brak scalania identycznego OCR różnych encji,
- `visualEpoch` w tymczasowej tożsamości historii,
- read-only MZ boxy,
- issue codes / Desktop review,
- per-crop timings,
- najstronger/best badge reading per entity,
- poprawione przypisanie tablicy z poszerzonego ROI do rzeczywistego pojazdu,
- live queue release po pierwszym czytelnym przypisanym odczycie,
- brak ponownego użycia numeru plate track po resetach,
- świeży wolny MP nie może zostać odrzucony tylko przez confidence decay,
- `StationarySceneSupport`,
- `StableSceneVehicleCache`.

---

# 23. Kryteria akceptacji

Zadanie jest zakończone, gdy:

1. STATIC i DYNAMIC mają odrębne, czytelne ścieżki polityki.
2. STATIC nie próbuje zachować encji przez potwierdzoną granicę statycznej sceny.
3. Ten sam numer tablicy po zmianie STATIC tworzy nową encję.
4. DYNAMIC nadal może zachować encję podczas ruchu i soft reacquire.
5. Kontrolowany zoom nie jest automatycznie nową sceną.
6. STATIC uruchamia AZ tylko jako jednorazowy etap poprawy po podstawowej kolejce i przechodzi do STATIC_IDLE.
7. DYNAMIC nie używa AZ w zwykłym skanowaniu; AZ jest dostępny tylko dla locka albo SEARCH_VERIFICATION.
8. Manualny lock działa na entityId i nie jest zrywany przez pojedynczy błąd OCR.
9. SEARCH_VERIFY_PURSUIT posiada UI z ikoną lupy, wprowadzeniem rejestracji, POSSIBLE_MATCH i promocją do SEARCH_PURSUIT po potwierdzeniu.
10. Historia i weryfikacja próbek zachowują obecne UX i dane.
11. Research freeze zapisuje prawdziwy `analysis_mode`.
12. R0/R1/R2 nie wymuszają jednego trybu sceny dla wszystkich eksperymentów.
13. Stable MP cache pozostaje optymalizacją i nie wpływa na cross-scene identity.
14. STATIC_IDLE monitoruje przede wszystkim nieruchome regiony ALPR, z konserwatywnym globalnym fallbackiem.
15. Po `NEW_SCENE` stara geometria, badge, animacje i wyniki nie mogą być widoczne ani użyte w nowej scenie nawet przez jedną klatkę.
16. Prezentacja nowej sceny pozostaje pusta do pierwszego świeżego wyniku z aktualnym `sceneGeneration`.
17. Wszystkie istotne testy regresyjne przechodzą albo agent jawnie raportuje konkretne nieuruchomione/nieprzechodzące testy.

---

# 24. Raport końcowy agenta

Po wdrożeniu podaj:

- commit SHA,
- listę zmienionych plików,
- ostateczne klasy/polityki STATIC i DYNAMIC,
- sposób mapowania legacy `strict_scene_boundary`,
- sposób budowania `StaticSceneWatchRegions` i warunki lokalnego/globalnego `NEW_SCENE` w STATIC,
- listę stanu zerowanego na granicy STATIC, w tym stan `DetectionOverlayView` i animacje,
- potwierdzenie nowego `entityId` dla tego samego OCR w nowej scenie,
- zachowanie DYNAMIC po soft reacquire,
- zachowanie AZ w STATIC i DYNAMIC, w tym STATIC_IDLE i budżety prób,
- sposób manualnego USER_PICK lock,
- sposób wyszukiwania rejestracji, POSSIBLE_MATCH i promocji SEARCH_PURSUIT,
- stan ikony/search UI i reguły manual override,
- sposób freeze i eksportu `analysis_mode`,
- wyniki testów S1--S12, D1--D3, M1, C1, H1--H2, R1--R2,
- wyniki istniejącego zestawu regresyjnego.
