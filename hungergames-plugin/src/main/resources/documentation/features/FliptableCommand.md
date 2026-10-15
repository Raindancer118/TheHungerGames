# Fliptable Command

**Command**: `/fliptable confirm`  
**Permission**: OP only

## Purpose
Complete server reset for a fresh HungerGames experience.

## What It Does
1. **Kicks all players** with a reset message
2. **Resets GameState** (clears arena center, player data)
3. **Merkt die Löschung vor** (JVM-Shutdown-Hook)
4. **Shuts down server** (2 s Verzögerung)
5. **Löscht beim Herunterfahren** die Weltordner (`world`, `world_nether`,
   `world_the_end`) und den Spielzustand (`whitelist.yml`, `teams.yml`,
   `session.yml`, `runtime.yml`) — Welten regenerieren beim nächsten Start

Konfiguration (`config.yml`, `loot.yml`, `gamemasters.yml`, Schematics) bleibt erhalten.

## Warum erst beim Shutdown?
Löscht man die Weltordner im laufenden Betrieb, schreibt Paper sie beim
Herunterfahren teilweise wieder (`level.dat`, Regionen, `session.lock`). Zurück
bleibt ein halber Weltordner; Paper 26.2 migriert den beim nächsten Start
(`VanillaWorldMigration`) und bricht dann ab:

```
Unable to read or access the world gen settings file! … world_gen_settings.dat
java.lang.IllegalStateException: Overworld settings missing
```

Der Shutdown-Hook läuft, nachdem Plugins deaktiviert und die Level gespeichert
und geschlossen sind. Er wartet pro Welt bis zu 30 s darauf, dass die
`session.lock` freigegeben ist, und löscht dann den kompletten Ordner — es kann
also nichts mehr nachträglich hineingeschrieben werden.

Bleibt nach einem Absturz doch ein kaputter Weltordner zurück, hilft nur:
`world`, `world_nether`, `world_the_end` manuell löschen.

## Usage
```
/fliptable           # Shows warning
/fliptable confirm   # Executes reset
```

## Safety
- Requires OP permission
- Requires explicit "confirm" argument
- 2-second delay before shutdown

## Warning
**This is irreversible!** All world data and HungerGames state will be lost.
