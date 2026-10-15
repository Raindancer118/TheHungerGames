# /speedup Command

Der `/speedup` Befehl ermöglicht es Gamemasters, die Spielzeit für Testzwecke zu beschleunigen.

## Verwendung

| Befehl | Beschreibung |
|--------|-------------|
| `/speedup <multiplier>` | Setzt den Zeit-Multiplikator (z.B. `/speedup 4` = 4x schneller) |
| `/speedup <multiplier> true` | Zeit-Multiplikator mit Auto-Stop bei Spielereignissen |
| `/speedup off` | Deaktiviert die Zeitbeschleunigung |
| `/speedup` | Zeigt den aktuellen Status an |

## Beispiele

```
/speedup 4           → Zeit läuft 4x schneller (bleibt aktiv bis manuell deaktiviert)
/speedup 4 true      → Zeit läuft 4x schneller, stoppt bei Spielereignissen automatisch
/speedup 10          → Zeit läuft 10x schneller
/speedup off         → Normale Geschwindigkeit
/speedup reset       → Normale Geschwindigkeit (Alternative)
```

## Auto-Stop Feature

Mit `/speedup <multiplier> true` wird die Zeitbeschleunigung automatisch bei folgenden Ereignissen deaktiviert:

| Ereignis | Beschreibung |
|----------|-------------|
| **PLAYER_DEATH** | Ein Spieler ist gestorben |
| **WORLDBORDER_SHRINK_START** | Die Worldborder beginnt zu schrumpfen |
| **WORLDBORDER_SHRINK_COMPLETE** | Die Worldborder hat die Zielgröße erreicht |

Bei Auto-Stop wird eine Benachrichtigung an alle Spieler gesendet.

## Technische Details

- Beeinflusst den Tag/Nacht-Zyklus der Arena-Welt
- Maximaler Multiplikator: 100x (aus Stabilitätsgründen begrenzt)
- Multiplikator 1.0 = normale Geschwindigkeit
- Auto-Stop Events werden von `GameRunner.java` ausgelöst

## Berechtigung

- Erfordert `gamemaster` Permission
- Nur für Spieler verfügbar (nicht von Konsole ausführbar)
