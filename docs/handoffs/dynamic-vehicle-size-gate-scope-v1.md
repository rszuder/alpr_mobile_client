# HANDOFF — doprecyzowanie zakresu mechanizmu: tylko tryb DYNAMIC

## Cel

Zaktualizować opis mechanizmu priorytetowego śledzenia encji i bramki minimalnego rozmiaru pojazdu tak, aby jednoznacznie wynikało, że dotyczy on **trybu dynamicznego** aplikacji mobilnej.

Nie zmieniać architektury ani zasad działania trybu STATIC. To jest doprecyzowanie zakresu opisu.

## Wymagane zmiany

1. W tytule sekcji zaznaczyć tryb dynamiczny, np.:

```text
Mechanizm priorytetowego śledzenia encji i bramka rozmiaru pojazdu w trybie dynamicznym
```

2. W pierwszym akapicie wyjaśnić, że w DYNAMIC pojazd może:
- zostać wykryty i objęty śledzeniem wcześniej,
- zmieniać rozmiar w kolejnych klatkach,
- pozostawać tą samą encją mimo chwilowego braku kwalifikacji do MT.

3. Jawnie rozdzielić:

```text
śledzenie encji
≠
zgoda na uruchomienie MT
```

Mały pojazd nadal jest śledzony, ale MT nie jest uruchamiany, dopóki świeża geometria nie spełni minimalnego progu.

4. Dodać jedno zdanie odcinające tryb STATIC, np.:

> Mechanizm ten dotyczy trybu dynamicznego, w którym geometria pojazdu zmienia się w czasie. Tryb statyczny wykorzystuje odrębną politykę analizy sceny.

5. W podsumowaniu użyć jednoznacznego sformułowania:

> W trybie dynamicznym pojazd może być śledzony od chwili jego wykrycia, natomiast model MT zostaje uruchomiony dopiero po osiągnięciu przez aktualną, świeżo zmierzoną ramkę pojazdu minimalnych wymiarów wymaganych do analizy tablicy.

## Czego nie zmieniać

Nie zmieniać:
- zasad STATIC,
- mechanizmu AutoZoom,
- hierarchii encji,
- histerezy progów,
- wymogu świeżego pomiaru MP,
- telemetrii `VEHICLE_TOO_SMALL`,
- drugiej kontroli rozmiaru bezpośrednio przed MT.

## Definition of Done

Opis jest poprawny, gdy czytelnik nie może odnieść wrażenia, że:
- mechanizm oczekiwania na wzrost ramki działa identycznie w STATIC,
- tryb DYNAMIC automatycznie przełącza się w STATIC,
- zbyt mały pojazd traci swoją encję.

Zakres ma być jednoznaczny:

```text
SceneHandlingMode = DYNAMIC
+
śledzona VehicleEntity
+
bramka rozmiaru przed MT
```
