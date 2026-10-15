# Dead Player Tracking

## Übersicht

Das Dead Player Tracking-Feature ermöglicht die persistente Speicherung des Todesstatus von Spielern in der `whitelist.yml`. Wenn tote Spieler während des Spiels rejoinen, werden sie automatisch in den Spectator-Modus versetzt und zu ihrem lebenden Teammate teleportiert.

## Funktionalität

### Bei Spieler-Tod
1. Spieler wird in `whitelist.yml` als `dead: true` markiert
2. Spieler wird gekickt (oder gebannt, je nach Config)
3. TeamsManager trackt Eliminierung im Speicher

### Bei Spieler-Rejoin (während RUNNING/FINISHED)
1. Prüfung ob Spieler als `dead` markiert ist
2. Falls ja:
   - GameMode wird auf SPECTATOR gesetzt
   - Alle Potion-Effekte werden entfernt
   - Teleport zu lebendem Teammate (falls vorhanden)
   - Info-Nachricht an Spieler

### YAML-Format

```yaml
players:
  - name: Raindancer118
    team: 1
  - name: TeddyBear2004
    team: 1
    dead: true  # Spieler ist eliminiert
```

## Betroffene Dateien

| Datei | Änderung |
|-------|----------|
| `WhitelistManager.java` | Neue Methoden: `markAsDead()`, `isDead()`, `getLivingTeammate()` |
| `JoinListener.java` | Handling für RUNNING/FINISHED Phase |
| `GameRunner.java` | Ruft `whitelist.markAsDead()` bei Tod auf |

## API

### WhitelistManager

```java
// Markiert Spieler als tot (persistiert in whitelist.yml)
void markAsDead(String playerName)

// Prüft ob Spieler tot ist
boolean isDead(String playerName)

// Findet lebenden Teammate
String getLivingTeammate(String playerName)

// Setzt Dead-Status zurück
void clearDeadStatus(String playerName)
void clearAllDeadStatus()
```

## Konfiguration

Das Feature nutzt die bestehende `death_action`-Konfiguration in `config.yml`:
- `KICK`: Spieler wird gekickt und kann als Spectator rejoinen
- `BAN`: Spieler wird vom Server gebannt

## Sicherheit

- Spielernamen werden case-insensitive behandelt (lowercase)
- Null-Checks für alle Eingaben
- YAML-Persistenz mit Fehlerbehandlung
