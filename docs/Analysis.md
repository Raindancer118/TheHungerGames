# Bestandsanalyse TheHungerGames v0.16.0 (Stand: 2026-07-02)

Analyse der Version aus dem GitHub-Repo `Raindancer118/TheHungerGames` (main, `c8699ae`)
als Grundlage für die Überarbeitung. Paper 1.21.11, Java 21, Single-Modul-Gradle,
~5.300 Zeilen Main-Code.

## 0. Sofort-Befund: Secrets im Repository (KRITISCH, kein Code-Thema)

`.env` steht zwar in der `.gitignore`, ist aber **im Git-Index getrackt** und liegt
damit inklusive **SFTP-Passwort, API-Token und Host** in der GitHub-Historie.
→ Muss behandelt werden: Credentials rotieren, Datei aus dem Index entfernen
(`git rm --cached .env`), ggf. Historie bereinigen. **Nicht automatisch durchgeführt** —
Entscheidung des Maintainers (History-Rewrite ist destruktiv).

## 1. Spieleridentität: Namen statt UUIDs (Kernproblem)

- `WhitelistManager` schlüsselt **alles über lowercase-Spielernamen**:
  Whitelist, Teamzugehörigkeit (`Map<String, Integer> playerTeams`), Dead-Status.
- `TeamsManager` trackt Eliminierungen dagegen **per UUID** (`Set<UUID> eliminatedPlayers`)
  → **zwei parallele Wahrheiten** („dead" per Name in `whitelist.yml`, „eliminated" per UUID
  im RAM), die divergieren können (z. B. nach `reload()`, das nur eine Seite leert).
- Durchgängig `Bukkit.getOfflinePlayer(name)` (deprecated, potenziell **blockierender
  Netzwerk-Lookup** auf dem Main-Thread) zur Name→UUID-Auflösung.
- Namensänderungen eines Accounts zerstören Team-/Dead-Zuordnung.
- Scoreboard-Teams via `team.addEntry(playerName)` — konsistent mit Namen, aber
  an die brüchige Namensidentität gekoppelt.

## 2. Spielzustand: flüchtig, verstreut, global

- `GameState` ist ein Singleton mit mutablem Zustand (Phase, Arena-Locations,
  Plattformpositionen). **Keine Persistenz** — Serverneustart während einer Runde
  verliert Phase und Arena-Geometrie; nur der Dead-Status überlebt (in `whitelist.yml`).
- Kein Session-Begriff: Teilnehmer, Gewinner, Startzeit, Border-Zustand existieren
  nirgends als zusammenhängende Einheit.
- **Gewinner wird nirgends gespeichert** — `GameRunner.endGame()` broadcastet nur.
  Weder abfragbar noch für andere Plugins verfügbar.
- `playersRemaining` wird dekrementiert statt aus dem Registry-Zustand berechnet —
  driftet bei Randfällen (Doppel-Tod, Rejoin-Anomalien).

## 3. Gewinnerermittlung ignoriert Offline-Spieler (Logikfehler)

`GameRunner.checkForWinner()` und `countAlivePlayers()` iterieren **nur
`Bukkit.getOnlinePlayers()`**. Ein lebender Spieler mit Verbindungsabbruch:

- zählt nicht als lebend → Spiel kann vorzeitig einen falschen Sieger küren,
- sein Team kann fälschlich als eliminiert gelten (`isTeamEliminated` behandelt
  Offline-ohne-Elimination zwar als „noch im Spiel", aber `checkForWinner` nicht —
  die beiden Methoden widersprechen sich).

Neue Anforderung (Disconnected = lebend bis zur Eliminierung) ist mit dieser
Struktur nicht erfüllbar.

## 4. Tod = Kick/Ban statt Spectator

`GameRunner.onPlayerDeath` kickt oder **bannt** Tote nach 3 s (`running.death-action`).
Tote Rejoiner werden im `JoinListener` zwar zu Spectatorn, aber:
- kein Teleport-Menü, nur Auto-Teleport zum Teammate,
- keine Ziel-Validierung gegen „lebend & Teilnehmer",
- Ban-Variante schließt Zuschauen komplett aus.

## 5. Singleton-/Zuständigkeits-Architektur

- Singletons überall: `GameState`, `ConfigManager`, `WhitelistManager`, `TeamsManager`,
  `ChatInputHandler`, `LootConfig` + **statischer** Zustand in `SpeedupCommand`
  (`getCurrentMultiplier()` wird von `GameRunner` gepollt). Folgen: versteckte
  Abhängigkeiten, keine Isolation in Tests (Tests mocken Bukkit-Statics), keine
  Mehrfach-Sessions, Reihenfolge-Abhängigkeiten beim Startup.
- **Reload-Bug:** `ConfigManager.reload()` ersetzt die Singleton-Instanz, aber
  `GameRunner`/`ProtectionListener` halten die alte Instanz in `final`-Feldern —
  nach `plugin.reloadConfig()` zeigt deren `FileConfiguration` auf das alte Objekt.
  Reload wirkt für bereits konstruierte Konsumenten schlicht nicht.
- `GameRunner` ist eine 820-Zeilen-God-Class: Timer, virtuelle Zeit, Bossbar,
  Leaderboard, Kill-Tracking, Border-Zyklen, Death-Handling, Winner-Logik, Endscreen.
- `TeamManager.java` (4 Zeilen, leer) neben `TeamsManager.java` — totes Duplikat.
- Klasse `main` (lowercase) verletzt Java-Konventionen.

## 6. Keine öffentliche API, keine Events

Es gibt **keinerlei** Custom-Events oder API-Oberfläche. Andere Plugins müssten
interne Singletons anfassen und bekämen Bukkit-`Player`-Objekte bzw. Strings.
Anforderungen (Whitelist per UUID/Name, Status-, Winner-, Team-Abfragen,
Status-/Eliminations-/Team-/Winner-Events) sind vollständig unerfüllt.

## 7. Konfiguration: untypisierte Getter-Halde

- `ConfigManager` = 40 Getter mit Inline-Defaults; **keine** Validierung, keine
  Beschreibungen, keine Kategorien, kein Anwendungszeitpunkt, keine Overrides,
  kein Audit-Log, keine GUI. Defaults doppelt gepflegt (Code + `config.yml`).
- Key-Schema inkonsistent (flach `gameduration` neben verschachtelt `arena.*`).
- Änderungen zur Laufzeit nur per Datei-Edit + `/hg-reload` (der wegen des
  Reload-Bugs oben teilweise wirkungslos ist).

## 8. Schutzregeln: statisch statt phasenabhängig

`ProtectionListener` schützt einen **festen Radius um das Arena-Zentrum in jeder
Phase** (wenn aktiviert). Anforderung: Cornucopia muss während RUNNING für normale
Spieler voll nutzbar sein (Kisten, Abbau, Platzierung). Heute gilt:
- Schutz kennt keine Phasen und keine benannten Regionen,
- Bypass hardcoded über `player.isOp()`,
- Abbau-/Platzier-Ausnahme nur, wenn Chest-Protection **deaktiviert** ist
  (verschachtelte Negativlogik, schwer konfigurierbar),
- Standard `protection.enabled: false` → das Feature ist praktisch aus.

## 9. Worldborder: starr und ohne Fairness-Grenzen

- Gleichverteilte Shrink-Zyklen (`shrink-count`), Dauer je Zyklus fix
  (`shrink-duration`) **unabhängig von der Distanz** → Border-Geschwindigkeit
  variiert unkontrolliert; keine Max-Geschwindigkeit pro Kante (Anforderung ~2,5 Blöcke/s).
- Keine Phasen, keine spielerzahlbasierten Trigger, keine Mindestgröße außer
  `final-size`, kein Konfliktmodell (Geschwindigkeit vs. Dauer vs. Zielgröße),
  keine GUI-Auflösung.
- Nether-Border erhält **dieselbe Größe** wie die Overworld — ignoriert den
  8:1-Koordinatenmaßstab.
- Verschachtelte `BukkitRunnable`-Ketten (`startShrinkCycle` rekursiv über
  `runTaskLater`): beim Spielende werden nur `timerTask`/`worldborderTask`
  gecancelt — bereits geplante innere Tasks **feuern weiter** (Shrinks nach FINISHED).

## 10. Veraltete/riskante API-Nutzung

- Legacy-Text durchgängig: `§`-Codes, `ChatColor`, `sendTitle`, `broadcastMessage`,
  `setDisplayName` — statt Adventure/MiniMessage (Paper-Standard, tw. deprecated-for-removal).
- `player.getMaxHealth()` (deprecated), `Bukkit.getOfflinePlayer(name)` (deprecated/blocking),
  `kickPlayer(String)`.
- `/allow` vergibt **OP** an Spieler — massives Rechte-Risiko als Standardpfad.
- Permission-Namen nicht namespaced (`gamemaster` statt `hungergames.gamemaster`).
- `whitelist.yml`/`teams.yml` als Live-Datenbank mit synchronem Save bei jedem Tod.

## 11. Tests

- Vorhandene Tests (~1.400 Zeilen) mocken Bukkit-Statics (`Bukkit.getOfflinePlayer`
  etc.) und testen Implementierungsdetails der Singletons; die eigentliche
  Spiellogik (Winner, Border-Mathematik, Teamregeln) ist **nicht isoliert testbar**,
  weil sie in Bukkit-gekoppelten God-Classes steckt.
- Kein CI-Workflow im Repo (Branches deuten auf externe Tools: jules, copilot).

## 12. Repo-Hygiene

- `Benchmark.java`, `task.md`, `test_check.sh` im Root getrackt (Arbeitsreste).
- `settings.gradle.kts` verweist auf privates Nexus (`nexus.engelmann.hamburg`)
  für das SFTP-Deploy-Plugin → Build scheitert ohne Zugriff; Deploy-Task ist
  auskommentiert. Für die Überarbeitung: Plugin-Abhängigkeit optional machen.
- 19 offene Remote-Branches (Bot-generiert), teils gemergt.

## Erhaltenswerte Funktionalität (wird übernommen)

- **Arena-Generierung**: PreflightRunner (Terrain-Flattening, Plattform-Kreis,
  Glass-Lobby, Underground-Room, Schematics via WorldEdit inkl. Path-Traversal-Schutz).
- **Startup-Sequenz**: Tube-Teleport, Redstone-Lampen, gestaffelte Levitation,
  Plattform-Versiegelung, Movement-Lock (CountdownListener).
- **Loot-System**: gewichtete Loot-Tables je Container-Typ (`loot.yml`), Scan-Radius.
- **Countdown/Start**: Bossbar-Countdown, Feuerwerk, De-Op von Admins.
- **Grace Period** mit Glowing + Unverwundbarkeit (außer Void).
- **Kill-Leaderboard** (Sidebar) und Bossbar-Timer mit virtueller Zeit (`/speedup`).
- **Fiendfinder**-Item (Einweg-Spyglass, Enemy-Glowing).
- **Portal-Blocking**, Pre-Init-Zugangskontrolle, `/fliptable`-Reset.
- Dead-Player-Persistenz über Restarts (Konzept bleibt, Umsetzung wird UUID-basiert).
