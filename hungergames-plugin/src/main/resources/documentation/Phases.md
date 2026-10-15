# GAME PHASES

Dieses Dokument beschreibt die verschiedenen Phasen des Spiels, definiert in `com.hungergames.GameState.Phase`.

## Übersicht der Phasen

| Phase | Beschreibung | Trigger |
|-------|--------------|---------|
| `NOT_INITIALIZED` | Der Startzustand des Plugins. Die Arena ist noch nicht eingerichtet. | Standard beim Serverstart / `/reset` (hypothetisch) |
| `PREFLIGHT` | Die Arena wird eingerichtet. Terrain wird generiert, Plattformen und Lobby werden gebaut. | `/init` Command |
| `LOBBY` | (Platzhalter) Wartephase in der Lobby. Spieler können joinen. | Automatisch nach erfolgreichem `PREFLIGHT` |
| `STARTUP` | Die "Engaging Sequence". Spieler werden in den Untergrund teleportiert und durch Röhren zu den Plattformen levitiert. | `/startup` Command (nur aus `PREFLIGHT`/`LOBBY`) |
| `READY` | Alle Spieler sind auf ihren Plattformen angekommen, eingefroren und bereit für den Start. Lobby wurde entfernt. | Automatisch am Ende der `STARTUP` Sequenz |
| `RUNNING` | Das Spiel läuft. Countdown ist abgelaufen, Grace Period startet. | `/start` Command (nur aus `READY`) |
| `FINISHED` | Das Spiel ist vorbei. Ein Gewinner wurde gekürt oder das Spiel manuell beendet. | Automatisch bei Sieg oder `/test-end` |

## Detaillierte Ablaufbeschreibung

### 1. Initialisierung (`NOT_INITIALIZED` -> `PREFLIGHT`)
**Command:** `/init`
- Admin legt Spieleranzahl fest.
- Arena-Zentrum wird gesetzt.
- Terrain wird vorbereitet (Loch graben, glätten).
- Untergrund-Raum wird generiert.
- Plattformen und Röhren werden platziert.
- Lobby-Box wird über der Arena gebaut.
- Worldborder und Whitelist werden gesetzt.
- Admin wird in die Lobby teleportiert.
- **Endzustand:** `PREFLIGHT` (logisch eigentlich Übergang zu Lobby, Code setzt aber `PREFLIGHT` und lässt es dort).

### 2. Startup Sequenz (`PREFLIGHT`/`LOBBY` -> `STARTUP` -> `READY`)
**Command:** `/startup`
- Prüft, ob alle gewhitelisteten Spieler online sind.
- Teleportiert Spieler in den Untergrund-Raum.
- **Weltzeit wird auf 22500 (Sonnenuntergang) gesetzt** für dramatischen Effekt.
- Startet Levitation-Effekt (Spieler schweben durch Röhren nach oben).
- **Horizontale Bewegung wird durch `CountdownListener` blockiert** (Spieler können nicht weglaufen).
- Sobald ein Spieler oben ankommt, wird die Plattform unter ihm geschlossen.
- **Automatische Transition zu `READY`**, wenn alle Spieler oben sind:
    - Luft über Plattformen wird bereinigt.
    - Spieler werden eingefroren (Bewegungssperre durch `CountdownListener`, kein Slowness-Effekt).
    - Kisten in der Arena werden mit Loot gefüllt.
    - Die Lobby-Box wird entfernt.

### 3. Spielstart (`READY` -> `RUNNING`)
**Command:** `/start`
- Startet Countdown (Bossbar).
- De-opped Admins (außer Zuschauer).
- **Bei Countdown 0:**
    - Slowness wird entfernt.
    - Feuerwerk und Title "THE HUNGER GAMES BEGIN".
    - Phase wird auf `RUNNING` gesetzt.
    - Grace Period (Unverwundbarkeit) startet.

### 4. Spielende (`RUNNING` -> `FINISHED`)
- Automatisch, wenn nur noch ein Spieler (oder Team) lebt.
- Admin-Command (z.B. `/test-end`) kann Spiel vorzeitig beenden.
- Siegerehrung im Chat und Title.
- **Nächster Schritt:** `FinishRunner` (Rückbau oder Neustart - noch zu implementieren).
