# Zielarchitektur TheHungerGames v2

Grundlage: `docs/Analysis.md`. Ziel: saubere, erweiterbare Architektur mit öffentlicher
Java-API, UUID-Identität, zentraler Session-Verwaltung und Bukkit-frei testbarer Spiellogik.

## 1. Modulstruktur (Gradle)

```
TheHungerGames/
├── hungergames-api/       # Öffentliche API: Interfaces, immutable Records, Bukkit-Events
│   └── compileOnly paper-api (nur für Event-Basisklassen & Namespaces)
└── hungergames-plugin/    # Implementierung (Plugin-Hauptmodul)
    ├── com.hungergames.game/      # Bukkit-FREIE Spiellogik (reine Java-Typen, testbar)
    ├── com.hungergames.platform/  # Bukkit-Adapter: Listener, Scheduler, GUIs, Persistenz
    └── com.hungergames.legacy/    # Übernommene Features (Runner, Loot, Schem) bis Migration
```

Regeln:
- `hungergames-api` importiert **nie** interne Klassen; keine `org.bukkit.entity.Player`-
  Signaturen — Spieler ausschließlich als `UUID`.
- `com.hungergames.game` importiert **kein** `org.bukkit.*` (durchgesetzt per Test,
  siehe §8). Zeit, Events und Persistenz kommen als Interfaces herein (Ports & Adapters).
- Kein Singleton-Zugriff mehr: Konstruktor-Injektion, verdrahtet in `HungerGamesPlugin`.

## 2. Öffentliche API (`com.hungergames.api`)

Einstieg über Bukkit ServicesManager + statischen Provider:

```java
HungerGamesApi api = HungerGamesProvider.get();
```

### Interfaces

| Interface | Verantwortung |
|---|---|
| `HungerGamesApi` | Wurzel: Zugriff auf Sub-Services + Statusabfragen |
| `WhitelistService` | `add(UUID)`, `addByName(String) → CompletableFuture<UUID>`, `remove(UUID)`, `isWhitelisted(UUID)`, `entries()` |
| `ParticipantService` | `alive()`, `eliminated()`, `participant(UUID)`, `isAlive(UUID)` |
| `TeamService` | `create(name)`, `delete(id)`, `rename`, `setColor(id, TeamColor)`, `availableColors()`, `assign(UUID, TeamId)`, `remove(UUID)`, `setCaptain`, `teams()`, `teamOf(UUID)` |
| `GameStatusService` | `phase()`, `winner(): Optional<Winner>`, `sessionId()` |

Alle Mutationen liefern `Result`-Objekte (`TeamOperationResult` etc.) statt stiller
Fehler — z. B. `COLOR_ALREADY_TAKEN`, `TEAM_FULL`, `PHASE_LOCKED`.
`addByName` löst Namen **asynchron** über das Mojang-Profil auf (kein blockierender
Main-Thread-Lookup).

### Datenmodelle (immutable Records/Enums)

- `GamePhase` — NOT_INITIALIZED, PREFLIGHT, LOBBY, STARTUP, READY, RUNNING, FINISHED
- `Participant(UUID uuid, String lastKnownName, ParticipantState state, Optional<TeamId> team)`
  - `ParticipantState` — ALIVE, ELIMINATED. **Online-Status ist bewusst kein Zustand**:
    Disconnect ändert nichts an ALIVE.
- `TeamId(String value)` — stabiler Schlüssel (nicht die Farbe, nicht der Name)
- `Team(TeamId id, String name, TeamColor color, Set<UUID> members, Optional<UUID> captain)`
- `TeamColor` — 16 Werte, gemappt auf `DyeColor`/`NamedTextColor` (Helm, Nametag, GUI)
- `Winner` — sealed: `Winner.Solo(UUID uuid)` | `Winner.Team(TeamId id, Set<UUID> members)`
  | `Winner.None` (Zeitablauf ohne Sieger)

### Bukkit-Events (alle im api-Modul, gefeuert auf dem Main-Thread)

- `GamePhaseChangeEvent(old, new)`
- `ParticipantEliminatedEvent(uuid, Optional<UUID> killer, remainingAlive)`
- `ParticipantRevivedEvent(uuid)` (Admin-Korrektur)
- `WhitelistChangedEvent(uuid, added|removed)`
- `TeamCreatedEvent` / `TeamDeletedEvent` / `TeamColorChangedEvent` /
  `TeamMembershipChangedEvent(uuid, oldTeam, newTeam, cause: API|ADMIN|GUI|RANDOM)`
- `WinnerDeclaredEvent(Winner)`

## 3. Kern-Services (`com.hungergames.game`, Bukkit-frei)

| Service | Verantwortung |
|---|---|
| `GameSession` | Aggregat einer Runde: Phase, Start-/Endzeit, Referenz auf Registry/Teams, Winner. Einzige Quelle für Phasenwechsel (validierte Transitionen), feuert `GamePhaseChangeEvent` über `EventPublisher`-Port |
| `ParticipantRegistry` | UUID → Participant. Eliminierung, Revive, alive/eliminated-Abfragen. **Unabhängig von Online-Status** |
| `TeamRegistry` | Teams + Farb-Exklusivität + Regeln (maxSize, Wechsel bis Lock-Phase, Captain) — Regeln kommen aus `TeamRules`-Config-Objekt |
| `WinnerResolver` | Pure Function: `resolve(participants, teams) → Optional<Winner>`. Solo-Sieg (1 lebender ohne lebende Gegner), Team-Sieg (alle Lebenden im selben Team), kein Sieg sonst |
| `BorderPlan` / `BorderMath` | Border-Phasen, Trigger, Geschwindigkeits-/Konfliktberechnung (§5) |
| `SettingsRegistry` / `ConfigTransaction` | Typisierte Settings (§6) |
| Ports | `EventPublisher`, `Clock` (virtuelle Zeit/Speedup), `SessionStore` (Persistenz) |

**Persistenz:** `SessionStore` (YAML-Adapter im Plugin-Modul) schreibt bei jeder
Zustandsänderung `session.yml`: Phase, Teilnehmer (uuid, name, state, team), Teams,
Winner, Border-Fortschritt, Runden-Overrides. Ein Serverneustart während einer Runde
stellt die Session wieder her (heutiges Verhalten: alles außer Dead-Status verloren).

## 4. Bukkit-Adapter (`com.hungergames.platform`)

- `EliminationListener` — ersetzt Kick/Ban: `PlayerDeathEvent` → `registry.eliminate()`
  → `SpectatorService.enable(player)`. `death-action` bleibt konfigurierbar
  (SPECTATOR | KICK | BAN), **Default SPECTATOR**.
- `SpectatorService` — echter `GameMode.SPECTATOR`, Teleport-Kompass-GUI: nur
  **lebende, online befindliche** Teilnehmer als Ziele; Interaktions-/Inventar-Sperre.
- `ConnectionListener` — Join/Quit aktualisiert nur `lastKnownName` und Online-Sicht;
  lebende Rejoiner kommen an ihre letzte Position zurück, eliminierte in den Spectator.
- `TeamSelectionGui` — Startphase (LOBBY): Team beitreten/wechseln/verlassen,
  Farbwahl (belegte Farben ausgegraut mit Hinweis), Captain-Aktionen falls aktiviert.
- `ProtectionService` — Regelmatrix `(GamePhase, Region) → RuleSet`
  (break/place/interact/container). Regionen: `CORNUCOPIA` (Radius um Arena-Zentrum),
  `LOBBY`, `ARENA` (Rest). Default: Cornucopia in RUNNING **vollständig frei** für
  normale Spieler; in LOBBY/STARTUP/READY geschützt. Bypass über Permission
  `hungergames.protection.bypass` (nicht mehr `isOp()`-hardcoded).
- `MenuFramework` — leichtgewichtiges internes GUI-Kit (InventoryHolder-basiert):
  `Menu`, `MenuItem` (Klick-Handler, dynamische Lore), Untermenü-Navigation,
  `NumberEditor` (±1/±10/±100-Klickschritte, Shift = groß), Anvil-Eingabe über
  Papers `MenuType.ANVIL`-View für Freitext/Zahlen. Adventure-Components, kein `§`.

## 5. BorderService

Konfiguration als **Phasenliste**; jede Phase:

```yaml
border:
  initial-size: 2500
  minimum-size: 100
  max-edge-speed: 2.5        # Blöcke/Sekunde PRO KANTE (Fairness-Obergrenze)
  phases:
    - trigger: { time: "20m" }             # oder { alive-below: 8 } oder beides (OR)
      target-size: 1000
      mode: duration                        # duration | fixed-speed | max-speed
      duration: "10m"
    - trigger: { alive-below: 4 }
      target-size: 100
      mode: fixed-speed
      speed: 1.0
```

`BorderMath` (pure, testbar):
- Kantengeschwindigkeit = `(startSize − targetSize) / 2 / durationSeconds`
  (jede Kante bewegt sich um die halbe Durchmesser-Differenz).
- `validate(phase)` → Menge von Konflikten (z. B. implizite Geschwindigkeit >
  `max-edge-speed`, Zielgröße < `minimum-size`, Phasen überlappen zeitlich).
- `resolutions(conflict)` → konkrete Optionen, jeweils mit **berechneten Auswirkungen**:
  1. Dauer anpassen (→ neue Dauer X)
  2. Zielgröße anpassen (→ neue Größe Y)
  3. Startzeitpunkt verschieben (→ neuer Trigger Z)
  4. Spielzeit anpassen (→ neue Gesamtdauer)
  5. Geschwindigkeit nur als Maximum interpretieren (→ tatsächliche Dauer W)
  6. Änderung verwerfen

Ändert ein Admin Werte inkonsistent, öffnet sich die `BorderConflictGui`: eine Option
pro Item, Auswirkungen in der Lore, Übernahme erst nach Bestätigungsklick. **Nichts
wird stillschweigend angepasst.**

Nether-Border erhält `size / 8` (Koordinatenmaßstab), abschaltbar per Setting.

## 6. ConfigurationService

```java
SettingDefinition<T>(
    String key,            // "border.max-edge-speed"
    SettingType<T> type,   // INT, DOUBLE, BOOLEAN, STRING, ENUM, DURATION, MATERIAL_LIST
    T defaultValue,
    Validator<T> validator,     // Range, Regex, Custom — mit Fehlermeldung
    Component description,
    SettingCategory category,   // GAME, ARENA, BORDER, TEAMS, PROTECTION, ITEMS, LOOT
    ApplyTiming applyTiming     // IMMEDIATE, NEXT_PHASE, NEXT_ROUND, RESTART
)
```

- `SettingsRegistry`: alle Definitionen zentral; `config.yml` wird dagegen geladen und
  validiert (unbekannte Keys → Warnung, ungültige Werte → Default + Log).
- **Zwei Ebenen**: persistente Werte (`config.yml`) + Runden-Overrides (nur RAM/Session,
  fallen nach der Runde zurück).
- `ConfigTransaction`: Änderungen sammeln → `validate()` (alle oder keine) → `commit()`
  → feuert interne Change-Hooks; Settings mit `applyTiming != IMMEDIATE` werden als
  „pending" markiert und beim passenden Zeitpunkt angewendet (GUI zeigt das an).
- **Audit-Log**: `plugins/HungerGames/audit.log` (JSON-Lines: wer, wann, Key, alt→neu,
  Scope persistent/round).
- `ConfigGui`: Hauptmenü nach Kategorien → Untermenüs pro Kategorie → Item pro Setting
  (Beschreibung, aktueller Wert, Default, ApplyTiming in der Lore; Klickschritte für
  Zahlen, Toggle für Booleans, Auswahlmenü für Enums, Anvil für Freitext/exakte Zahl).

## 7. Identität & Migration der Datenbestände

- Intern **ausschließlich UUIDs**. Namen nur als `lastKnownName`-Anzeige-Cache.
- `whitelist.yml` v2: Einträge `{uuid, name, team, state}`. Loader migriert das alte
  Format (Name → UUID via Paper-Profil-API, asynchron beim ersten Start; nicht
  auflösbare Namen werden gemeldet statt verworfen).
- `teams.yml` v2: benannte Teams mit stabiler `TeamId`; alte Team-Nummern werden zu
  `team-<n>` migriert, Farben übernommen.
- Doppelte Wahrheit (dead per Name vs. eliminated per UUID) entfällt: **nur noch**
  `ParticipantRegistry` + `SessionStore`.

## 8. Teststrategie

- `com.hungergames.game` komplett Bukkit-frei → reine JUnit-5-Tests ohne Mocks von
  Bukkit-Statics. Ein Architektur-Test scannt die Imports des `game`-Pakets und
  schlägt bei `org.bukkit.*` fehl.
- Pflicht-Szenarien (aus der Anforderung):
  - Winner: 4 Lebende → kein Sieger; 3 Lebende aus 2 Teams → kein Sieger;
    n Lebende desselben Teams → Team-Sieg; 1 Lebender → Solo-Sieg;
    Disconnect eines Lebenden ändert **nichts** an der Siegprüfung.
  - Teams: Farb-Exklusivität, maxSize, Wechselsperre ab Lock-Phase, Random-Zuweisung.
  - Border: Kantengeschwindigkeit, Konflikt-Erkennung, jede Resolution-Option
    rechnerisch korrekt; 2,5-Blöcke/s-Grenze.
  - Config: Validierung, Transaktion (Rollback bei einem ungültigen Wert),
    Runden-Override-Fallback, ApplyTiming.
  - Protection: Regelmatrix — Normalspieler (kein OP, keine Permission) darf in
    RUNNING in der Cornucopia brechen/platzieren/Kisten öffnen; in LOBBY nicht.
  - Spectator: Zielliste enthält nur lebende Teilnehmer; Interaktionen geblockt.
- Bukkit-Adapter: gezielte Tests mit MockBukkit (`org.mockbukkit`) wo sinnvoll,
  sonst dünne Adapter ohne Logik halten.

## 9. Migrationsplan (überschaubare Schritte, je ein Commit/Meilenstein)

1. **Scaffold**: Multi-Modul-Setup; bestehenden Code unverändert nach
   `hungergames-plugin` verschieben; Build + Alt-Tests grün. Repo-Hygiene
   (Benchmark.java/task.md/test_check.sh raus, `.env`-Thema an Maintainer).
2. **API-Modul**: Interfaces, Records, Events, `HungerGamesProvider`; Plugin
   registriert eine (zunächst teilweise delegierende) Implementierung.
3. **Kern-Domäne**: `ParticipantRegistry`, `TeamRegistry`, `WinnerResolver`,
   `GameSession` + `SessionStore`; `WhitelistManager`/`TeamsManager` werden zu
   dünnen Fassaden über der neuen Domäne (Alt-Aufrufer funktionieren weiter);
   Datenmigration whitelist/teams v1→v2. Tests parallel.
4. **Death → Spectator**: `EliminationListener` + `SpectatorService` ersetzen
   Kick/Ban-Pfad in `GameRunner`; Winner-Check läuft über `WinnerResolver`
   (offline-korrekt). `GameRunner` schrumpft auf Timer/HUD.
5. **Teams komplett**: TeamService-API, Farb-Exklusivität, `TeamSelectionGui`,
   Random-Zuweisung, Regeln (Größe/Wechsel/Captain/Lock) als Settings.
6. **BorderService**: Phasen-Engine + `BorderMath` ersetzt Shrink-Zyklen in
   `GameRunner`; alte Border-Keys werden gemappt; Konflikt-GUI.
7. **ConfigurationService + GUIs**: SettingsRegistry für alle bestehenden Keys,
   Transaktionen, Overrides, Audit-Log, Config-GUI; `ConfigManager` wird Fassade
   und stirbt am Ende.
8. **Protection-Rework**: Regelmatrix + Regionen; Cornucopia-Freigabe in RUNNING.
9. **Aufräumen**: Legacy-Fassaden entfernen, `main` → `HungerGamesPlugin`,
   Adventure-Migration der Texte, Permissions namespacen
   (`hungergames.gamemaster`, alte Namen als Alias in plugin.yml).

Kompatibilität: Commands, Loot-System, Schematic-Pipeline und Startup-Sequenz bleiben
funktional unverändert; Datenformate werden automatisch migriert.

## 10. Bewusste Entscheidungen / Risiken

- **Kein Folia-Support** in diesem Schritt (Border-/Timer-Logik nutzt globalen
  Scheduler); Architektur hält Scheduler hinter einem Port, sodass Folia später
  machbar ist. `folia-supported` wird **nicht** gesetzt.
- Namensauflösung setzt Online-Mode voraus; im Offline-Mode liefert `addByName`
  die Offline-UUID (dokumentiert).
- `/allow` vergibt kein OP mehr (Breaking Change, sicherheitsbegründet) —
  stattdessen Permission-Hinweis.
- BAN als death-action bleibt möglich, schließt aber Spectator aus — Doku-Hinweis.
- Anvil-Eingabe nutzt Papers `MenuType.ANVIL` (1.21+); kein externes AnvilGUI-Lib nötig.
