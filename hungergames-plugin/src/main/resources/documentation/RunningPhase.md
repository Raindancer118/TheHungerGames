# RUNNING PHASE

Dieses Dokument beschreibt die RUNNING-Phase des Spiels.

## Übersicht

Die RUNNING-Phase beginnt nach dem `/start`-Countdown und endet, wenn ein Sieger feststeht oder die Zeit abläuft.

## Features

### Grace Period
- Zu Beginn sind alle Spieler unverwundbar (konfigurierbar: `graceperiod` in Sekunden).
- Visuelle Effekte: Glowing-Effekt, grüne Bossbar.

### Spieler-Tod
- Bei Tod werden Spieler **sofort gekickt oder gebannt** (konfigurierbar: `running.death-action`).
- Bei `KICK`: Nachricht, dass erneutes Joinen zum Zuschauen möglich ist.
- Bei `BAN`: Permanenter Ban mit Todesgrund.

### Team-Tracking
- Spieler erhalten zu Beginn einen Lederhelm in ihrer Team-Farbe.
- Nametags werden in Team-Farben angezeigt.
- Broadcast, wenn ein Team vollständig eliminiert wurde.

### Loot-System
- Truhen werden in der **READY-Phase** automatisch mit Loot gefüllt (nach `onAllPlayersArrived()`).
- Der `LootManager` scannt einen 30-Block-Radius um das Arena-Zentrum.
- Verschiedene Container-Typen haben unterschiedliche Loot-Pools:
  - `CHEST`: Common-Tier Loot (Grundausstattung)
  - `COPPER_CHEST`: Mid-Tier Loot (bessere Waffen/Rüstung)
  - `TRAPPED_CHEST`: High-Tier Loot (Cornucopia/Füllhorn)
  - `BARREL`: Supply-fokussiert (Ressourcen, Nahrung)
  - `SHELF`: Sichtbare High-Value Items
- Konfiguration in `loot.yml`:
  - `fill-percentage`: Prozent der Slots, die gefüllt werden
  - Jeder Container-Typ hat eigene Item-Liste mit Gewichtungen

### Worldborder
| Config-Wert | Beschreibung |
|-------------|--------------|
| `preptime` (Game Settings) | Vorbereitungszeit (% der Spielzeit) vor erstem Shrink |
| `worldborder.prep-warnings` | Warnungen vor erstem Shrink (z.B. `[10, 5, 3, 2, 1]`) |
| `worldborder.shrink-count` | Anzahl der Shrink-Zyklen |
| `worldborder.final-size` | Endgröße der Border (Blöcke) |
| `worldborder.final-time-percent` | Zeit am Ende bei Endgröße (% der Spielzeit) |
| `worldborder.shrink-duration` | Dauer jedes Shrinks (Sekunden) |
| `worldborder.shrink-warning` | Warnung vor jedem Shrink (Sekunden) |

- Die Border schrumpft gleichmäßig vom Startwert (`arena.worldborder-size`) zum Endwert (`final-size`).
- Der Intervall wird automatisch berechnet basierend auf Spielzeit und Shrink-Count.
- Overworld und Nether schrumpfen synchron.

### Siegbedingungen
1. **Solo-Sieg:** Nur ein Spieler übrig.
2. **Team-Sieg:** Alle verbleibenden Spieler gehören zu einem Team.

## Konfiguration

Siehe `config.yml` unter:
- `running.death-action` (KICK oder BAN)
- `worldborder.*`

Siehe `teams.yml` für Team-Farben.
