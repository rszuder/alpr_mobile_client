# Dolny panel i HUD

Dolny panel ma jeden rząd czterech kafelków o wysokości 72 dp, zgodnej z AZ
i przełącznikiem Static/Dynamic: Start/Stop, Cropy, Podgląd i Ostatnie.
Wektory 32 dp mają wspólny styl liniowy. Krótkie podpisy zastępują rozbudowane
CTA, a pełne opisy czynności pozostają dostępne dla czytnika ekranu i tooltipów.
Start/Stop zmienia piktogram; aktywny podgląd i zbieranie cropów mają osobne
kolory. Dotychczasowe warunki dostępności i obsługa kliknięć pozostają zachowane.

Licznik Ostatnich jest małym badge w rogu kafelka (0–99, następnie 99+).
Opis dostępności zawiera pełną liczbę. W trybie badawczym ten sam kafelek
prowadzi do weryfikacji i pokazuje liczbę cropów.

Przycisk HUD z własnym piktogramem zastępuje ikonę „i”. Jest osobnym elementem
obok paska statusu i pozostaje dostępny również przed startem, po zatrzymaniu
analizy oraz przy braku modeli. Zatrzymanie kamery nie zamyka otwartego HUD;
nieaktualne pomiary i szczegóły są czyszczone, a pola bez danych pokazują „—”.
Karta HUD zawiera FPS kamery, ostatni czas Pipeline, osobne MP/MT/MZ,
rozdzielczość, FPS analizy, pominięte klatki, sumę inferencji i pozostałych
czasów oraz dotychczasowe informacje Scan/AZ/KLT. Na mniejszych ekranach
zawartość przewija się w przestrzeni nad wynikiem; karta nie przykrywa
panelu wyniku ani przycisków. HUD można zamknąć przyciskiem HUD lub krzyżykiem.
Scan pokazuje rzeczywisty stan AZ. Usunięto nieaktualny komunikat
„Auto zoom pominięty podczas skanowania”.

FPS kamery jest mierzony przy odbiorze klatek luma przed bramką analizy.
Dzięki temu STATIC_IDLE nie pokazuje fałszywego 0 FPS kamery tylko dlatego,
że MP/MT/MZ czekają na zmianę sceny. Licznik jest ograniczony do trzech
jednosekundowych kubełków i wylicza tempo z dwóch zakończonych sekund.
FPS analizy jest odczytywany z istniejących liczników przetworzonych klatek.
Nie wyliczamy FPS jako odwrotności czasu pojedynczej inferencji.
Brak pomiaru jest przedstawiany jako „—”; czasy mają jednostki ms/s, a wyniki
poprzedniej sceny są wygaszane zgodnie z istniejącą bramką świeżości.

Testy obejmują układ na krótszym i wyższym ekranie, rozmiar kafelków,
brak kolizji HUD/wynik/przyciski, licznik historii, dostępność, otwieranie
i zamykanie HUD, rozdzielenie FPS kamery od analizy i reset liczników.
Materiały z telefonu i podsumowanie walidacji: `app/build/reports/live-dashboard/`.

Weryfikacja końcowa: 569 testów JVM bez błędów. Pełny pakiet UI przed
doprecyzowaniem źródła FPS: 74 testy zaliczone, jeden test opt-in pominięty.
Po doprecyzowaniu źródła FPS ponownie przeszły trzy testy panelu/HUD.
Na telefonie sprawdzono Podgląd, Start/Stop, przełączanie zbierania cropów,
galerię, licznik i zamykanie HUD. W STATIC_IDLE główny licznik pokazywał
20 FPS kamery przy 0 FPS analizy; czasy MP i Pipeline pozostały opisane jako
ostatnie pomiary. Finalny APK jest zainstalowany; hash zapisano w `verification.json`.

Po udostępnieniu HUD niezależnie od stanu kamery: 570 testów JVM i trzy testy
Android panelu/HUD bez błędów. Sprawdzono otwieranie w STOPPED, przejścia przez
PREVIEW/SETUP_REQUIRED/SEARCHING i zachowanie panelu przy zatrzymaniu oraz
resetowaniu AZ. Na telefonie potwierdzono widoczny przycisk i otwarty HUD
z pustymi pomiarami przed startem. Materiały: `app/build/reports/hud-always-available/`.
