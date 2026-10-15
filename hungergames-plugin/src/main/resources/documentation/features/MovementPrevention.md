# Movement Prevention Feature

## Übersicht

Dieses Feature verhindert, dass Spieler sich vor und während des Spielstarts unerlaubt bewegen können.

## Funktionsweise

### CountdownListener

Der `CountdownListener` blockiert horizontale Bewegung (X/Z-Achse) in folgenden Phasen:

| Phase | Beschreibung |
|-------|--------------|
| `STARTUP` | Spieler im Untergrund vor Levitation |
| `READY` | Spieler auf Plattformen während Countdown |

**Vertikale Bewegung (Y-Achse) bleibt erlaubt** für:
- Levitation während STARTUP
- Natürliches Fallen nach Levitation

### Kein Slowness-Effekt

Ab der READY Phase wird **kein Slowness-Effekt** mehr verwendet. Der `CountdownListener` allein reicht für die Bewegungssperre aus. Das hat folgende Vorteile:
- Flüssigere Animationen
- Kein "klebriges" Gefühl für Spieler
- Weniger visuelle Effekte

### Jump-Boost Negation

Sprung-Boost mit negativem Wert (-128) bleibt aktiv um zu verhindern, dass Spieler von ihren Plattformen springen.

## Technische Details

### Betroffene Dateien

- `CountdownListener.java` - Blockiert horizontale Bewegung
- `StartupRunner.java` - Entfernt Slowness bei Ankunft auf Plattform

### Event-Handling

```java
// Horizontal movement is blocked by correcting position
Location correctedLocation = from.clone();
correctedLocation.setY(to.getY()); // Y movement allowed
event.setTo(correctedLocation);
```

## Konfiguration

Keine Konfigurationswerte erforderlich - Feature ist fest implementiert.
