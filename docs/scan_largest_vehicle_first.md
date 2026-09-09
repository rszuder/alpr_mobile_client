# Kolejność odczytu: największe ramki pierwsze

Automatyczna kolejka Scan porównuje przede wszystkim widoczną powierzchnię
ramki pojazdu w obrazie źródłowym. Udział powierzchni w kadrze zachowuje tę samą
kolejność co liczba pikseli przy danej rozdzielczości. Część ramki poza obrazem
nie zwiększa priorytetu. Przy równych powierzchniach decyduje dotychczasowy wynik
jakości i oczekiwania, a następnie deterministyczne rozstrzygnięcie remisu.

Pojazdy są obsługiwane rundami: jeden ograniczony cykl na kwalifikującą się encję,
od największej. Historyczna liczba prób MT nie obniża na stałe pozycji dużego
pojazdu. Odroczenie i bramka rozmiaru nadal obowiązują. Po obsłużeniu dostępnych
pojazdów zaczyna się nowa runda, z ponowną oceną ich bieżącej wielkości. Wybrany
ręcznie cel/pogoń zachowuje wyłączność; aktualna inferencja nie jest przerywana
samą zmianą wielkości sąsiada.

Naprawiono także przyjmowanie wyników przy CONTINUE_ACTIVE_SESSION. Poprzednio
kontrola rewizji dopuszczała jedynie dyrektywy żądające MT; wyniki kolejnych
inferencji z aktualną rewizją CONTINUE mogły więc nie zwiększać licznika prób MZ.
Teraz liczą się również te wyniki, przy zachowaniu wymogu właściwej encji, sesji,
kontekstu i rewizji. Samo podtrzymanie geometrii bez nowego odczytu nie zeruje
budżetu braku postępu. Domyślne limity prób i czasu nie zostały zwiększone.

Telemetria kolejki zawiera `vehicle_area_ratio`, `queue_order_policy` oraz `area`
w logu ALPR_SCAN. Polityka to `largest_first_per_round`.

Weryfikacja: 643/643 testy JVM i poprawny build APK debug. Nowe regresje obejmują
większego sąsiada czekającego na P6, limit kolejnych prób MZ przy CONTINUE,
stare rewizje, brak postępu, wpływ pewności i historycznych prób, zmianę rozmiaru
między rundami, cooldown, bramkę MT i część ramki poza kadrem. Weryfikacja
z ruchomą kamerą na fizycznym telefonie pozostaje do wykonania po instalacji APK.
