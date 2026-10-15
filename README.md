# TheHungerGames

Die Tribute von Panem als Minecraft-Turnier: Ein [Paper](https://papermc.io/)-Plugin, das eine komplette
Hunger-Games-Runde ausrichtet — von der automatisch generierten Arena mit Füllhorn über die
Startröhren-Sequenz bis zum Kanonenschuss für gefallene Tribute und der Siegerehrung.

> *Möge das Glück stets mit euch sein!*

**Version 2** ist eine vollständige Überarbeitung: UUID-basierte Spielerverwaltung, echte
Team-Unterstützung mit Inventory-GUIs, Spectator-System statt Kick/Ban, eine phasenbasierte
Worldborder mit Fairness-Grenze, ein zur Laufzeit per GUI verwaltbares Konfigurationssystem —
und eine öffentliche Java-API für andere Plugins.

**Neu:** Das Plugin ist im Live-Event-Betrieb **vollständig ingame steuerbar** — `/hg admin`
öffnet die zentrale Schaltzentrale für Spielsteuerung, Konfiguration, Spielmacher-Modus,
Deathmatch, Supply Drops, Sponsor-System, LootTable-Editor, Preflight-Check und Debug.

**Zuletzt dazugekommen** (feat/admin-suite):

- **Custom-Items** — vom Fiendfinder über Rauchbombe, Medikit, Blitzschlag und Hermes'
  Stiefel bis zum craftbaren **Exmatrikulator** (Blitz-Minigun). Jedes Item ist voll
  konfigurierbar, per `/hg give` ausgebbar und in Shop, Loot-Tables und Crafting nutzbar.
- **Sound- & Effekt-Studio** — jedes Event (Kill, Sieg, Startsequenz, Supply Drop, Items …)
  bekommt beliebig viele überlagerte Sounds *und* Partikel-Effekte, komplett per GUI editierbar
  (Lautstärke, Tonhöhe, Echo, Verzögerung, Farbe …) und einzeln abschaltbar.
- **Monster-Wellen** — Spielmacher spawnen an einem Ort Monster in konfigurierten Wellen.
- **Test-Simulation mit Mannequins** — ein Admin allein simuliert komplette Runden.
- **Admin-Hotbar** — Admins im Kreativmodus/in der Lobby bekommen automatisch Menü-Items;
  mitspielende Admins am Füllhorn ein Start-Item.

---

## Inhalt

- [Voraussetzungen & Installation](#voraussetzungen--installation)
- [Spielablauf](#spielablauf)
- [Die Admin-Zentrale: /hg admin](#die-admin-zentrale-hg-admin)
- [Commands](#commands)
- [Permissions](#permissions)
- [Teams](#teams)
- [Spectator-System](#spectator-system)
- [Spielmacher-Modus](#spielmacher-modus)
- [Deathmatch](#deathmatch)
- [Supply Drops](#supply-drops)
- [Monster-Wellen](#monster-wellen)
- [Sponsor-System](#sponsor-system)
- [Custom-Items](#custom-items)
- [Sound- & Effekt-System](#sound--effekt-system)
- [Test-Simulation (Mannequins)](#test-simulation-mannequins)
- [LootTable-Editor](#loottable-editor)
- [HTTP-API](#http-api)
- [Worldborder](#worldborder)
- [Konfiguration](#konfiguration)
- [Entwickler-API](#entwickler-api)
- [Bauen & Testen](#bauen--testen)
- [Migration von v0.16](#migration-von-v016)
- [Architektur](#architektur)

---

## Voraussetzungen & Installation

| | |
|---|---|
| Server | Paper 26.2 (Spigot reicht nicht) |
| Java | 21 oder neuer |
| Abhängigkeit | [WorldEdit](https://enginehub.org/worldedit) (für die Arena-Schematics) |

1. `TheHungerGames-2.x.jar` in den `plugins/`-Ordner legen.
2. WorldEdit installieren.
3. Server starten — das Plugin legt `config.yml`, `loot.yml` und die Schematics an.
4. Tribute registrieren: `/hg whitelist add <Name>` (oder per API, siehe unten).

Solange keine Arena initialisiert wurde, dürfen nur die in `game.pre-init-admins`
konfigurierten Spieler joinen.

## Spielablauf

Eine Runde durchläuft feste Phasen; Übergänge werden validiert (kein Überspringen möglich):

```
NOT_INITIALIZED → PREFLIGHT → LOBBY → STARTUP → READY → RUNNING → FINISHED
```

1. **`/init`** — Arena wird an der aktuellen Position generiert: Terrain geglättet, Füllhorn
   platziert, Plattformkreis, unterirdischer Startraum, Glas-Lobby. Der Welt-Spawn liegt
   **auf** der Glasbox; whitelistete Tribute werden beim Join automatisch hinein
   teleportiert. In der Box ist kein PVP möglich.
2. **LOBBY** — Spieler wählen Teams (`/hg team`), Admins konfigurieren (`/hg config`).
3. **`/startup`** — Tribute werden in den Startraum teleportiert und nacheinander durch die
   Röhren auf ihre Plattformen levitiert.
4. **`/start`** — Countdown, dann beginnen die Spiele: Schutzfrist (kein PvP), danach ist
   alles erlaubt. Loot liegt im Füllhorn, die Spielmacher verkleinern die Arena.
5. **Sieg** — Der letzte lebende Tribut (oder das letzte Team) gewinnt. Danach: `/init` für
   die nächsten Spiele.

**Wichtig:** Ein Verbindungsabbruch ist kein Tod. Disconnectete Tribute gelten weiterhin als
lebend, blockieren die Siegerermittlung und können jederzeit weiterspielen. Optional lässt
sich eine Frist setzen (`game.disconnect-elimination: 2m`, Default `0` = aus): Wer länger
getrennt ist, wird von den Spielmachern eliminiert — mit Kanonenschuss wie bei jedem Tod. Auch ein
**Serverneustart mitten in der Runde** wird überlebt — Session (Phase, Tribute, Teams, Kills)
und Laufzeit-Zustand (Arena-Geometrie, virtuelle Spielzeit, Border-Fortschritt) werden aus
`session.yml`/`runtime.yml` wiederhergestellt. Server-Downtime zählt dabei standardmäßig
**nicht** als Spielzeit (`game.restart.offline-time-policy: PAUSE`; `COUNT` für das alte
Verhalten).

## Die Admin-Zentrale: /hg admin

`/hg admin` öffnet die zentrale Inventory-GUI für Admins und Spielmacher. **Jede
Admin-Funktion und jede Einstellung ist hier erreichbar** — Commands bleiben als Shortcuts.
Admins sehen alles; reine Spielmacher sehen Admin-Bereiche sichtbar gesperrt mit
Permission-Hinweis. Gefährliche Aktionen (Runde beenden/zurücksetzen, Deathmatch, Tabellen
löschen, Fliptable) verlangen eine Bestätigungs-GUI.

| Bereich | Inhalt |
|---|---|
| **Spielsteuerung** | Status, Arena initialisieren, Startsequenz, Start, Runde beenden/zurücksetzen, Preflight-Check, Test-Simulation, Monster-Wellen |
| **Konfiguration** | Alle Einstellungen nach Kategorie (identisch mit `/hg config`) |
| **Spieler & Tribute** | Whitelist pflegen, wiederbeleben, Kopf-Übersicht mit Paging |
| **Teams** | Teams anlegen/löschen, Zufallsverteilung |
| **Spielmacher** | GM-Modus togglen, Spielmodus, Teleport, Namensliste |
| **Worldborder** | Border-Einstellungen mit Konfliktprüfung |
| **Loot** | LootTables ansehen, testen, bearbeiten (GUI-Item-Picker inkl. Custom-Items), speichern |
| **Sponsor-System** | Toggles, Beacons setzen/entfernen, Test-Token, **Shop-Editor** (Items/Tränke/Custom per GUI, Verzauberungen) |
| **Supply Drops** | Zeitplan mit Status, manuell ausliefern |
| **Deathmatch** | Starten (mit Warnphase), abbrechen, konfigurieren |
| **Sounds, Ansagen & Effekte** | Jeden Sound & Partikel-Effekt pro Event probehören/bearbeiten, Ansage-Texte und Kanäle |
| **System & Debug** | Reloads, Speedup, Test-Aktionen, Fliptable (doppelt bestätigt) |

Der **Preflight-Check** prüft vor dem Start elf Punkte (Arena, ≥ 2 lebende Tribute, Teams,
Border-Konflikte, Supply-Drop-Plan, Sponsor-Shop, Spielmacher, Deop-Plan für mitspielende
Admins, Loot, WorldEdit) — rote Fehler blockieren den Start, gelbe Warnungen kann der Admin
bewusst ignorieren.

Jede Runde schreibt ein **Rundenlog** (`plugins/HungerGames/logs/round-<zeit>.log`):
Phasenwechsel, Kills, Revives, Drops, Sponsor-Käufe, Deathmatch und Admin-Aktionen.

## Commands

### Für alle Spieler

| Command | Beschreibung |
|---|---|
| `/hg team` | Teamwahl-GUI: Team beitreten, wechseln, verlassen, erstellen, Farbe wählen |
| `/hg spectate` | Teleport-Menü für Zuschauer (nur lebende Tribute als Ziele) |
| `/hg status` | Phase, lebende/eliminierte Tribute, Sieger |
| `/hg border` | Border-Konfiguration und Konfliktprüfung anzeigen |

### Für Spielleiter & Admins

| Command | Beschreibung |
|---|---|
| `/hg admin` | **Zentrale Admin-/Spielmacher-GUI** — alles Weitere sind Shortcuts |
| `/init` | Arena an der aktuellen Position generieren |
| `/startup` | Startsequenz (Röhren + Levitation) |
| `/start` | Countdown und Spielstart |
| `/hg gm [on\|off\|add\|remove\|list]` | Spielmacher-Modus togglen / Liste pflegen |
| `/hg deathmatch [start\|cancel\|status]` | Deathmatch auslösen, abbrechen, Status |
| `/hg supplydrop [now\|status]` | Lieferung manuell auslösen / Zeitplan-Status |
| `/hg waves [start [mob] [anzahl] [wellen] [intervall]\|stop\|status]` | Monster-Wellen an deiner Position |
| `/hg sim [spawn [n]\|eliminate\|clear]` | Test-Simulation mit Mannequin-Tributen |
| `/hg give <item> [anzahl] [spieler]` | Custom-Item ausgeben (Tab-Completion) |
| `/hg sponsor [status\|give\|clear\|beacon …]` | Sponsor-Tokens und -Beacons verwalten |
| `/hg loot [tables\|table\|test\|validate\|reload\|export]` | LootTables ansehen, testen, speichern |
| `/hg config` | Konfigurations-GUI (alle Einstellungen zur Laufzeit) |
| `/hg whitelist add\|remove\|list <Name>` | Turnier-Whitelist verwalten |
| `/hg team assign\|remove\|random\|create\|delete\|list` | Team-Administration |
| `/hg revive <Name>` | Eliminierten Tribut wiederbeleben |
| `/speedup <Faktor\|off>` | Spielzeit beschleunigen (Test); wirkt auf Timer, Border, Drops, Tokens |
| `/allow <Name>` | Spieler zur Server-Whitelist hinzufügen (ohne OP) |
| `/hg-reload`, `/config-reload` | `loot.yml` neu laden bzw. + Server-Whitelist anwenden |
| `/test-start`, `/test-end`, `/test-loot` | Testmodus und Loot-Test |
| `/fliptable confirm` | ⚠ Alles löschen und neu starten |

## Permissions

| Permission | Default | Bedeutung |
|---|---|---|
| `hungergames.gamemaster` | OP | Spiel starten/steuern, Deathmatch/Drops auslösen, Beacons setzen, Loot ansehen/testen, `/hg admin` |
| `hungergames.admin` | OP | Alles: Konfiguration, Whitelist, Teams, Revive, Loot-Editor, Sponsor-Config, Reset |
| `hungergames.protection.bypass` | OP | Umgeht den Gebietsschutz |
| `gamemaster` | OP | *Veraltet* — Alias für `hungergames.gamemaster` |

## Teams

- Teams entstehen auf vier Wegen: **Spieler-GUI** (`/hg team`), **Admin-Commands**,
  **Zufallszuweisung** (`/hg team random`, füllt kleinste Teams zuerst) oder die
  **öffentliche API**.
- Jedes Team beansprucht eine von 16 Farben — **exklusiv**: Eine vergebene Farbe kann kein
  zweites Team wählen; im Farbwahl-GUI sind belegte Farben ausgegraut und zeigen, wer sie hält.
- Die Teamfarbe erscheint auf **Nametags** (Scoreboard), **gefärbten Lederhelmen** und im Chat.
- Konfigurierbar: maximale Teamgröße, maximale Teamanzahl, Wechselerlaubnis, Captain-System,
  ob Spieler Teams erstellen/Farben wählen dürfen und ab welcher Phase Teams **gesperrt** sind
  (Default: ab STARTUP).
- Sieg: Gehören alle Lebenden demselben Team an, gewinnt das **gesamte Team** — auch bereits
  gefallene Mitglieder werden im Ergebnis mitgeführt.

## Spectator-System

Fällt ein Tribut, ertönt für alle der Kanonenschuss (Totem-Klang mit Explosions-Bass und
Echo) — und der Gefallene wechselt in den **echten Spectator-Modus** (Standard; `KICK` und
`BAN` sind konfigurierbar):

- Das **Teleport-Menü öffnet sich automatisch** kurz nach der Eliminierung und ist jederzeit
  über `/hg spectate` erreichbar; als Ziele erscheinen ausschließlich **lebende, online
  befindliche** Tribute — inklusive Teamname.
- Auch das native Spectator-Verfolgen (Linksklick auf Spieler) ist abgesichert: eliminierte
  oder unbeteiligte Spieler sind keine gültigen Ziele.
- Beim Rejoin nach dem Tod landet man automatisch wieder im Spectator-Modus, bevorzugt beim
  eigenen lebenden Teamkollegen.
- Spectator können das Spiel nicht beeinflussen (Vanilla-Spectator: keine Interaktion, keine
  Kollision, für Tribute unsichtbar).

## Spielmacher-Modus

Spielmacher (Game Master) begleiten das Event, ohne Teilnehmer zu sein — sie lösen **keine
Eliminierungs- oder Sieger-Logik** aus:

- Erkennung per Permission, Namensliste (`gamemaster.names`) oder beidem
  (`gamemaster.permission-mode`).
- Aktivierung (`/hg gm` oder GUI) setzt den konfigurierten Modus (`SPECTATOR`/`CREATIVE`);
  Deaktivierung stellt den vorherigen Spielmodus wieder her. OP bleibt erhalten
  (`gamemaster.keep-op`).
- Teleport-Menü zu Tributen, optional aus Spielerzahl-Anzeigen ausgenommen.
- Der Aktiv-Zustand überlebt Server-Neustarts (`gamemasters.yml`).

**Mitspielende Admins:** Ist ein OP zugleich Tribut, wird er beim Spielstart automatisch
**deoped** und bei Eliminierung bzw. Rundenende **re-oped** (`game.admin-participants.*`).
Der ursprüngliche OP-Status liegt in `runtime.yml` — ein Neustart mitten in der Runde
verliert ihn nicht. Nach der Eliminierung optional Creative + Teleport über die Arena-Mitte.

## Deathmatch

Das Deathmatch ist ein **manuell ausgelöstes Event** (kein Automatismus): Nach einer
konfigurierbaren Warnphase mit Countdown-Ansagen und Sound übernimmt es die Border und
schrumpft sie auf die Zielgröße (`deathmatch.target-border-size`, respektiert die
Fairness-Grenze). Optional werden lebende Tribute zur Mitte teleportiert und erhalten eine
kurze Schutzzeit. **Abbruch** ist möglich, solange die Warnphase läuft. Ein aktives
Deathmatch übersteht Server-Neustarts. Start per GUI (mit Bestätigung) oder
`/hg deathmatch start`.

## Supply Drops

Kapitol-Lieferungen landen zu konfigurierten **Spielzeitpunkten**
(`events.supply-drops.schedule`, Default `20m, 45m, 75m`): Warnung mit (optional ungenauen)
Koordinaten, dann landet eine Loot-Kiste an sicherer Zufallsposition — mit Beacon-Lichtsäule,
Feuerwerk und Partikeln. Befüllt wird sie aus der editierbaren LootTable `supply_drop`.
Die Zeitpunkte hängen an der **virtuellen Spielzeit** (`/speedup` wirkt mit); nach einem
Neustart wird nichts doppelt ausgelöst, eine ausstehende Landung wird nachgeholt.
Manuell: GUI oder `/hg supplydrop now`. Blocktyp der 3×3-Basis und ein optionaler
**Schutz** (Kiste/Beacon/Basis unzerstörbar, öffnen bleibt möglich) sind konfigurierbar.

## Monster-Wellen

Spielmacher können an einem Ort **Monster in Wellen** spawnen (`/hg admin` → Spielsteuerung →
Monster-Wellen, oder `/hg waves`): Eine Gruppe Monster erscheint sofort, dann nach dem
konfigurierten Abstand die nächste — so oft wie eingestellt. GUI mit „Start hier" und
Schnellwahl (Zombies, Skelette, Spinnen, Creeper, Blazes, Piglin-Brutes) sowie Stop-Button;
per Command auch mit eigenen Werten: `/hg waves start SKELETON 8 10 20`. Konfigurierbar über
`events.monster-waves.*` (Standard-Monster, Anzahl pro Welle, Wellenzahl, Intervall, Streuung).
Bei Rundenreset/-ende werden laufende Wellen automatisch gestoppt.

## Sponsor-System

Wer überlebt, findet Sponsoren: Lebende Tribute erhalten zeitbasiert **Sponsor Tokens**
(Default: 1 Token alle 10 Minuten, erstes nach 10 Minuten) — physische Nether Stars, die
über einen PersistentDataContainer-Key als echt erkannt werden. **Normale Nether Stars
zählen nie als Token.** Volle Inventare lassen Tokens am Spieler droppen; Rejoin holt
verpasste Vergaben nach, ein Neustart vergibt nichts doppelt.

Eingelöst werden Tokens an **Sponsor-Beacons** — physischen Shop-Stationen mit
Rechtsklick-GUI. Ein Center-Beacon beim Spielstart ist über `sponsors.beacons.center-on-start`
schaltbar (kombinierbar mit dem Zeitplan-Modus `RANDOM_TIMED`); Basis-Blocktyp und Schutz
sind ebenfalls konfigurierbar. Beacon-Positionen überleben Neustarts; nur lebende Tribute
können kaufen.

Der **Shop-Editor** (`/hg admin` → Sponsoren → Shop-Editor) verwaltet die Angebote komplett
per GUI — kein Amboss nötig:

- **Hinzufügen** von Custom-Items, beliebigen Materialien oder **Tränken** (Variante +
  Trank-Typ) über paginierte Auswahl-Menüs, danach Preis & Menge per Klick.
- **Verzauberungen** pro Angebot nachträglich setzen (alle Enchantments, beliebige Stufen).
- Jedes Angebot einzeln **an-/abschaltbar**, bearbeitbar oder löschbar.

Technisch bleibt es `sponsors.shop.items` (`id|reward|kosten|Name`): Material (`BREAD:8`),
Effekt (`EFFECT:SPEED:15:0`), Custom-Item (`ITEM:FIENDFINDER:1`), Trank
(`POTION:SPLASH:STRONG_HEALING:1`), optional mit `+ENCHANT:LEVEL`. Tokens und Beacons sind
**separat abschaltbar**; Tod-Verhalten (`drop-on-death`, `clear-on-elimination`) konfigurierbar.

## Custom-Items

Sponsoren-Gadgets mit echtem Verhalten — per Rechtsklick nutzbar, über PDC erkannt (nie am
Material allein), voll konfigurierbar (`/hg config` → Items) inkl. eigener Sounds & Effekte.
Verfügbar im **Shop**, in **Loot-Tables**, per `/hg give <id>` und teils **craftbar**.

| Item | Wirkung |
|---|---|
| **Fiendfinder** | Enthüllt den nächsten Gegner (Glowing) |
| **Rauchbombe** | Vernebelt Gegner (Blind/Slow) + macht dich kurz komplett unsichtbar (inkl. Rüstung) |
| **Medikit** | Vollheilung, Regeneration, Absorption — wirkt erst nach Countdown (Default 3s, `items.medikit.countdown-seconds`); Schaden bricht die Behandlung ab, das Item bleibt dann erhalten |
| **Blitzschlag** | Ruft ein Gewitter aufs Ziel (mehrere Einschläge, Flächenschaden, Feuer) |
| **Hermes' Stiefel** | Kurzzeitiger Flug mit Warnsound vor Ablauf |
| **Krückauwasser** | Wurftrank → Übelkeit im Umkreis |
| **Aura der Bewahrung** | Kill-Aura auf Zeit (trifft Gegner & optional Mobs rundum) |
| **Enterhaken** | Zieht dich zum anvisierten Punkt |
| **Druckwelle** | Stößt Gegner (& optional Mobs) weg + Slow |
| **Festmahl** | Füllt Hunger, Regen, goldene Äpfel |
| **Kriegsausrüstung** | Komplettes Rüstungsset (Material konfigurierbar) |
| **Sprungfeder** | Katapult nach vorn mit sanfter Landung |
| **Trottel-Schutz** | Passiv: rettet einmalig vor **Umweltschaden** (Lava/Sturz/Feuer/Monster) — nicht vor Spieler-Kills/Custom-Items — und stößt Gegner weg |
| **Exmatrikulator** | **Craftbar** (Rezept per GUI-Editor unter `/hg admin` → Crafting-Rezepte oder über `items.exmatrikulator.recipe` editierbar): Blitz-Minigun-Aura, die einige Sekunden alles im Umkreis mit Blitzsalven beschießt. Wer dadurch stirbt, „wurde exmatrikuliert" — mit zufälliger Todesnachricht samt Modul und Killer (z. B. „… als er im Drittversuch in Mathematik I gegen Tom versagte."); Templates und Modul-Liste sind über `items.exmatrikulator.death-messages` / `.modules` konfigurierbar |

Offensive Blitze schaden nie dem Auslöser; Custom-Item-Schaden wird vom Trottel-Schutz nicht
abgefangen.

## Sound- & Effekt-System

Jedes Event (Spielstart, Kill, Eliminierung/Kanonenschuss, Sieg, Startsequenz-Levitation,
Supply Drop, Deathmatch, Sponsor-Beacon, jedes Custom-Item …) hat einen **Sound** *und* einen
**Partikel-Effekt**, beide vollständig per GUI editierbar (`/hg admin` → Sounds, Ansagen &
Effekte):

- **Mehrere Ebenen gleichzeitig** pro Event; Auswahl aus der kompletten Sound- bzw.
  Partikel-Registrierung (paginiert), mit „Kombination testen" und „Sounds stoppen".
- **Feineinstellungen** pro Ebene: Lautstärke, Tonhöhe, Echo, Startverzögerung, Zeitlimit
  (Sounds) bzw. Anzahl, Streuung, Tempo, Wiederholung, Farbe (Effekte).
- **Leerer Wert = aus.** Textformat z. B. `ENTITY_WITHER_HURT; ENTITY_GENERIC_EXPLODE@1.5~0.7^2`
  bzw. `FLAME@40~0.6; DUST@30#ff0000>200`.

## Test-Simulation (Mannequins)

Ein Admin allein kann eine ganze Runde simulieren (`/hg admin` → Spielsteuerung →
Test-Simulation, oder `/hg sim`): Es werden **Mannequin-Tribute** als echte Teilnehmer auf
automatisch angelegten Test-Teams gespawnt. Sie durchlaufen dieselben Eliminierungs-, Sieger-
und Deathmatch-Flows wie echte Spieler — tötbar per Treffer im laufenden Spiel oder per
Menü-/Command-Button. `/hg sim clear` entfernt alle Attrappen samt Test-Teams; hängengebliebene
Simulationen werden beim Serverstart aufgeräumt.

## LootTable-Editor

LootTables lassen sich **komplett ingame** verwalten (`/hg admin` → Loot oder `/hg loot`):

- **Liste** mit Eintragszahl, Gesamtgewicht, Gültigkeit und Verwendungszweck; Tabellen
  öffnen, testen, duplizieren, löschen (Confirm).
- **Eintrags-Editor**: Item (GUI-Picker mit Material- und **Custom-Item**-Liste, Amboss als
  Fallback), Min/Max-Anzahl, Gewicht (mit Prozent-Anzeige), Name, Lore, Enchantments,
  Unzerstörbar, Aktiv/Draft — Zahlen per Klickschritt oder Amboss, Validierung inklusive.
  Ungültige Einträge sind nur als deaktivierte Drafts speicherbar.
- **Loot-Test**: 1×/10×/100× simulieren mit Häufigkeitsübersicht, Test-Chest am Standort,
  Test-Items ins Inventar — ohne Rundendaten zu verfälschen.
- Gespeichert wird **explizit** (GUI-Button oder `/hg loot export`), auf Wunsch mit Backup
  (`loot.yml.bak-<timestamp>`). Ungespeicherte Änderungen werden angezeigt; `/hg loot reload`
  warnt davor. Spielmacher dürfen ansehen und testen, bearbeiten nur Admins.

## HTTP-API

Für Event-Regie, Dashboards und Stream-Overlays bringt das Plugin eine eingebettete
HTTP-API mit (JDK-HttpServer, kein zusätzliches Dependency). Sie ist **standardmäßig
deaktiviert** und für den Betrieb in **geschlossenen Netzen** gedacht — kein TLS, also nie
direkt ins Internet exposen (bei Bedarf Reverse-Proxy davor).

```yaml
api:
  enabled: true          # Default: false (Änderung braucht Neustart)
  bind-address: 127.0.0.1  # 0.0.0.0 = alle Interfaces (nur im geschlossenen Netz!)
  port: 8567
  key: ""                # leer = wird beim Start generiert und in die config.yml geschrieben
  read-only: false       # true sperrt alle schreibenden Endpunkte (POST/PUT/PATCH/DELETE)
```

Jede Anfrage (außer `/api/health`) braucht den Schlüssel als `X-API-Key`-Header (alternativ
`Authorization: Bearer <key>`); der Vergleich ist zeitkonstant. Alle Bukkit-Zugriffe laufen
über den Main-Thread (Timeout 5 s → `503`). Schreibaktionen landen im Rundenlog als
Akteur „HTTP-API", Konfigurationsänderungen zusätzlich im Audit-Log.

**Der Server beschreibt sich selbst:** `GET /api` liefert alle Endpunkte mit Methode, Pfad
und Kurzbeschreibung — die Liste unten ist damit nie veraltet.

```bash
curl -H "X-API-Key: <key>" http://127.0.0.1:8567/api | jq '.endpoints[] | "\(.method) \(.path)"'
```

Spielerangaben akzeptieren überall UUID **oder** Name (`{"uuid": ...}` bzw. `{"player": ...}`,
in Pfaden beides); Namen werden gegen registrierte Tribute und Online-Spieler aufgelöst — für
unregistrierte Offline-Spieler die UUID schicken.

### Status & Tribute

| Endpunkt | Methode | Beschreibung |
|---|---|---|
| `/api/health` | GET | Liveness-Check (ohne Key), Endpunktzahl, read-only-Status |
| `/api` | GET | Alle Endpunkte (Selbstbeschreibung) |
| `/api/status` | GET | Phase, lebende Tribute, Deathmatch/Drops, Spielzeit, Border, Sieger |
| `/api/participants` | GET | Alle Tribute mit Status, Team, Kills, Tokens, online |
| `/api/participants/{uuid\|name}` | GET | Einzelner Tribut (404, wenn unbekannt) |
| `/api/players` | GET | Online-Spieler mit Position, Spielmodus, Leben |
| `/api/log?lines=200` | GET | Letzte Zeilen des Rundenlogs |
| `/api/whitelist` | POST | `{"uuid", "name"?}` — Tribut registrieren |
| `/api/whitelist/{uuid\|name}` | DELETE | Tribut aus dem Turnier entfernen |
| `/api/revive` | POST | `{"uuid"\|"player"}` — Eliminierung zurücknehmen |
| `/api/eliminate` | POST | `{"uuid"\|"player", "killer"?}` — Tribut eliminieren (nur RUNNING) |

### Teams

| Endpunkt | Methode | Beschreibung |
|---|---|---|
| `/api/teams` | GET | Teams mit Farbe, Mitgliedern (UUID + Name + lebend) und Captain |
| `/api/teams` | POST | `{"name", "color"?}` — Team erstellen (liefert `teamId`) |
| `/api/teams` | DELETE | Alle Teams löschen (Tribute bleiben registriert) |
| `/api/teams/colors` | GET | Freie, belegte und alle Teamfarben |
| `/api/teams/random` | POST | Teamlose Tribute zufällig verteilen (liefert `assigned`) |
| `/api/teams/{id}` | GET | Einzelnes Team |
| `/api/teams/{id}` | PATCH | `{"name"?, "color"?, "captain"?}` — Team ändern |
| `/api/teams/{id}` | DELETE | Team löschen |
| `/api/teams/{id}/members` | POST | `{"uuid"\|"player"}` — Spieler dem Team zuordnen |
| `/api/teams/{id}/members/{uuid\|name}` | DELETE | Spieler aus dem Team entfernen |
| `/api/teams/{id}/captain` | POST | `{"uuid"\|"player"}` — Captain setzen |

`PATCH` wendet `name`, `color`, `captain` in dieser Reihenfolge an und nennt in `applied`,
was übernommen wurde; bricht ein Schritt mit `409` ab, bleiben die vorherigen wirksam.

### Spielablauf, Zeit & Border

| Endpunkt | Methode | Beschreibung |
|---|---|---|
| `/api/game` | GET | Phase, `canInit`/`canStartup`/`canStart`/`canEndRound`, Grenzen |
| `/api/game/preflight` | GET | Preflight-Check mit allen Befunden und `blocking` |
| `/api/game/init` | POST | `{"admin": "Name", "playerCount": 24}` — Arena initialisieren |
| `/api/game/startup` | POST | `{"admin": "Name"}` — Startsequenz (Röhren/Levitation) |
| `/api/game/start` | POST | `{"admin": "Name"}` — Countdown und Start |
| `/api/game/end` | POST | Runde wie bei Zeitablauf werten (Alias: `/api/round/end`) |
| `/api/game/reset` | POST | Runde zurücksetzen, Tribute bleiben registriert |
| `/api/game/phase` | POST | `{"phase": "LOBBY"}` — Phase erzwingen (**Notfall**) |
| `/api/time/multiplier` | POST | `{"multiplier": 4.0}` — virtuelle Zeit beschleunigen |
| `/api/border` | GET | Aktuelle Größe, Startgröße, Untergrenze, Phasenplan |
| `/api/border/shrink` | POST | `{"size": 200}` — sofort schrumpfen (liefert Dauer) |
| `/api/border/reset` | POST | Border auf Startgröße zurücksetzen |

`init`/`startup`/`start` brauchen einen **online** Referenz-Admin: Die Stage-Runner nehmen
dessen Position als Arena-Zentrum, teleportieren ihn in die Lobby und melden ihm den
Fortschritt. Ist niemand mit dem Namen online, kommt `409`.

### Spielmacher-Ereignisse

| Endpunkt | Methode | Beschreibung |
|---|---|---|
| `/api/deathmatch` | GET | Zustand und Statuszeile |
| `/api/deathmatch/start` \| `/cancel` | POST | Deathmatch auslösen bzw. Warnphase abbrechen |
| `/api/supplydrop` | GET | Zeitplan mit ausgelösten Lieferungen |
| `/api/supplydrop` | POST | Lieferung manuell auslösen |
| `/api/sponsor` | GET | Tokens/Beacons aktiv, Beacon-Positionen |
| `/api/sponsor/give` | POST | `{"player", "amount"?}` — Tokens geben |
| `/api/sponsor/clear` | POST | `{"player"}` — Tokens einziehen |
| `/api/sponsor/beacons` | POST | `{"player"}` oder `{"world","x","y","z"}` — Beacon setzen |
| `/api/sponsor/beacons` | DELETE | Alle Sponsor-Beacons entfernen |
| `/api/monsterwaves` | GET | Laufende Serien und Standardwerte |
| `/api/monsterwaves` | POST | Ort + `{"mob"?, "count"?, "waves"?, "interval"?}` — Serie starten |
| `/api/monsterwaves` | DELETE | Alle Serien stoppen |
| `/api/announce` | POST | `{"message": "§6Text"}` — Broadcast an alle |
| `/api/sounds` \| `/api/effects` | GET | Alle bekannten Sound- bzw. Partikelnamen |
| `/api/sounds/test` \| `/api/effects/test` | POST | `{"player", "sound"\|"effect"}` — vorspielen |

### Konfiguration

| Endpunkt | Methode | Beschreibung |
|---|---|---|
| `/api/config` | GET | Alle Einstellungen (`?category=BORDER`, `?changed=true`) |
| `/api/config/categories` | GET | Kategorien mit Anzahl |
| `/api/config/{key}` | GET | Einzelne Einstellung mit Default, Typ, Timing, Pending |
| `/api/config/{key}` | PUT | `{"value": "...", "scope": "PERSISTENT"\|"ROUND"}` |
| `/api/config` | POST | `{"values": {...}, "scope"?}` — mehrere Werte **atomar** |
| `/api/config/reload` | POST | config.yml, loot.yml, Rezepte und Server-Whitelist neu laden |

Werte werden als Text übergeben und wie in der GUI geparst und validiert; ein ungültiger
Wert lässt die ganze Transaktion scheitern (`400` mit `errors`). Einstellungen mit
`applyTiming != IMMEDIATE` landen unter `pending` und werden zum passenden Zeitpunkt aktiv.
Der API-Schlüssel (`api.key`) wird beim Lesen als `***` maskiert (`secret: true`), lässt
sich aber setzen.

### LootTables

| Endpunkt | Methode | Beschreibung |
|---|---|---|
| `/api/loot/tables` | GET | Tabellen mit Eintragszahl, Gewicht, Verwendung, `dirty` |
| `/api/loot/tables` | POST | `{"name"}` anlegen oder `{"source", "name"}` duplizieren |
| `/api/loot/tables/{name}` | GET | Tabelle mit allen Einträgen |
| `/api/loot/tables/{name}` | DELETE | Tabelle löschen |
| `/api/loot/tables/{name}/entries` | POST | `{"item", "weight"?, "minAmount"?, "maxAmount"?, "enabled"?, "custom"?, "displayName"?, "lore"?, "enchantments"?, "unbreakable"?}` |
| `/api/loot/tables/{name}/entries/{index}` | PATCH | Eintrag ändern (nur übergebene Felder) |
| `/api/loot/tables/{name}/entries/{index}` | DELETE | Eintrag löschen |
| `/api/loot/validate` | GET | Alle Tabellen prüfen |
| `/api/loot/save` | POST | Tabellen auf die Platte schreiben |
| `/api/loot/reload` | POST | Von der Platte laden (verwirft Ungespeichertes) |

Änderungen wirken sofort im Spiel, bleiben aber bis `POST /api/loot/save` ungespeichert —
genau wie im GUI-Editor (`dirty: true`).

### Spielmacher, Zuschauer & Test-Simulation

| Endpunkt | Methode | Beschreibung |
|---|---|---|
| `/api/gamemaster` | GET | Eingetragene Namen und aktive Spielmacher |
| `/api/gamemaster/names` | POST | `{"name"}` — Spielmacher eintragen |
| `/api/gamemaster/names/{name}` | DELETE | Spielmacher austragen |
| `/api/gamemaster/activate` \| `/deactivate` | POST | `{"player"}` — Modus an/aus |
| `/api/gamemaster/mode` | POST | `{"player", "mode": "SPECTATOR"}` — Spielmodus setzen |
| `/api/spectator/teleport` | POST | `{"player", "target"}` — Zuschauer zu einem Tribut |
| `/api/simulation` | GET | Attrappen, lebende, Test-Teams, `canSpawn` |
| `/api/simulation/spawn` | POST | `{"admin", "count"}` — Mannequin-Tribute spawnen |
| `/api/simulation/eliminate` | POST | `{"admin"}` — eine Attrappe eliminieren |
| `/api/simulation` | DELETE | Alle Attrappen und Test-Teams entfernen |

Bewusst **nicht** über die API steuerbar: `/fliptable` (löscht Welten und startet den Server
neu) — ein solcher Totalreset bleibt an der Konsole.

### Beispiele

```bash
curl -H "X-API-Key: <key>" http://127.0.0.1:8567/api/status
curl -H "X-API-Key: <key>" http://127.0.0.1:8567/api/participants/Katniss

# Tribut registrieren, Team anlegen, zuordnen, Captain setzen
curl -X POST -H "X-API-Key: <key>" -H "Content-Type: application/json" \
     -d '{"uuid":"069a79f4-44e9-4726-a5be-fca90e38aaf5","name":"Katniss"}' \
     http://127.0.0.1:8567/api/whitelist
curl -X POST -H "X-API-Key: <key>" -H "Content-Type: application/json" \
     -d '{"name":"Distrikt 12","color":"GRAY"}' http://127.0.0.1:8567/api/teams
# teamId kommt aus der Antwort von POST /api/teams (hier: "distrikt-12")
curl -X POST -H "X-API-Key: <key>" -H "Content-Type: application/json" \
     -d '{"player":"Katniss"}' http://127.0.0.1:8567/api/teams/distrikt-12/members
curl -X POST -H "X-API-Key: <key>" -H "Content-Type: application/json" \
     -d '{"player":"Katniss"}' http://127.0.0.1:8567/api/teams/distrikt-12/captain

# Teams umbenennen/einfärben und den Rest zufällig verteilen
curl -X PATCH -H "X-API-Key: <key>" -H "Content-Type: application/json" \
     -d '{"name":"Die Spotttölpel","color":"CYAN"}' \
     http://127.0.0.1:8567/api/teams/distrikt-12
curl -X POST -H "X-API-Key: <key>" http://127.0.0.1:8567/api/teams/random

# Konfiguration nur für diese Runde ändern
curl -X POST -H "X-API-Key: <key>" -H "Content-Type: application/json" \
     -d '{"values":{"border.max-edge-speed":"3.5","deathmatch.enabled":"true"},"scope":"ROUND"}' \
     http://127.0.0.1:8567/api/config
```

Fehlerantworten sind JSON (`{"error": "..."}`): `400` bei ungültiger Eingabe, `401` ohne
gültigen Key, `403` bei `read-only`, `404` bei unbekanntem Endpunkt/Objekt, `405` bei
falscher Methode (mit Liste der erlaubten), `409` wenn die Aktion im aktuellen Zustand nicht
möglich ist (mit derselben typisierten Begründung wie die GUI), `503` bei
Main-Thread-Timeout.

## Worldborder

Die Border ist **phasenbasiert** konfiguriert (`border.phases`), eine Phase pro Zeile:

```yaml
border:
  initial-size: 2500        # Startdurchmesser
  minimum-size: 100.0       # absolute Untergrenze
  max-edge-speed: 2.5       # Fairness-Grenze: Blöcke/Sekunde PRO KANTE
  scale-nether: true        # Nether-Border 1:8 mitführen
  phases:
    - "50% -> 1000 @ max:2.5"          # bei halber Spielzeit auf 1000, max. 2,5 b/s
    - "80% -> 200 @ max:2.5"
    # weitere Beispiele:
    # "20m -> 1000 @ duration:10m"          # nach 20 Min., Schrumpfdauer 10 Min.
    # "alive<4 -> 200 @ speed:1.0"          # sobald weniger als 4 leben, fix 1 b/s
    # "1h|alive<6 -> 500 @ max:2.5"         # was zuerst eintritt (ODER)
    # "50% -> 1000 @ max:2.5,prefer:25m"    # Ziel: 25 Min., aber nie schneller als 2,5 b/s
```

**Syntax:** `<Trigger> -> <Zielgröße> @ <Modus>` — Trigger sind Spielzeit (`20m`, `1h30m`),
Prozent der Spielzeit (`50%`), Spielerzahl (`alive<4`) oder ODER-Kombinationen (`|`).
Modi: `duration:` (feste Dauer), `speed:` (feste Kantengeschwindigkeit), `max:`
(Geschwindigkeit als Obergrenze — mit optionalem Zeitanker `prefer:` wird die
Geschwindigkeit aus der angestrebten Dauer berechnet und nur bei Bedarf geklemmt;
ein unerreichbarer Anker wird als Konflikt gemeldet).

**Konfliktauflösung:** Ändert ein Admin Werte so, dass sie nicht gleichzeitig einzuhalten
sind (z. B. Geschwindigkeit über der Fairness-Grenze, Zielgröße unter dem Minimum, Phase
passt nicht mehr in die Spielzeit), passt das Plugin **nichts stillschweigend an**. Stattdessen
öffnet sich ein GUI mit berechneten Lösungsoptionen — Dauer anpassen, Zielgröße anpassen,
Start verschieben, Spielzeit verlängern, Geschwindigkeit als Maximum interpretieren oder
verwerfen — jede Option zeigt ihre konkreten Auswirkungen in der Item-Lore und wird erst
nach einem Bestätigungsklick übernommen.

## Konfiguration

Alles ist zur Laufzeit über `/hg config` (bzw. `/hg admin` → Konfiguration) verwaltbar —
nach Kategorien gegliederte Untermenüs (Spiel, Arena, Lobby, Startsequenz, Teams,
Worldborder, Gebietsschutz, Items, Loot, Events & Drops, Sounds, Effekte, Sponsoren,
Deathmatch, Spielmacher, Ansagen, HTTP-API):

- Jede Einstellung zeigt **Beschreibung, aktuellen Wert, Default und Wirksamkeit**
  (sofort / nächste Phase / nächste Runde / Neustart) in der Lore.
- **Zahlen** über Klickschritte (±1/±10/±100) oder exakt per **Amboss-Eingabe**; Booleans
  togglen, Enums rotieren.
- **Linksklick** ändert dauerhaft, **Rechtsklick** nur für die aktuelle Runde (Override, fällt
  danach automatisch zurück).
- Änderungen sind **transaktional und validiert** — ungültige Werte werden mit Begründung
  abgelehnt, nie teilweise übernommen.
- Jede bestätigte Änderung landet im **Audit-Log** (`plugins/HungerGames/audit.log`,
  JSON-Lines: wer, wann, Schlüssel, alt → neu, Scope).

Der **Gebietsschutz** ist phasen- und regionsabhängig: Die Füllhorn-Region ist vor dem Spiel
geschützt (Kisten bleiben benutzbar), während des Spiels für alle **vollständig frei** —
Kisten öffnen, Blöcke abbauen und platzieren, ohne OP. Beides ist konfigurierbar.

## Entwickler-API

Das Modul `hungergames-api` stellt eine dokumentierte, stabile API bereit. Spieler werden
ausschließlich über **UUIDs** referenziert; alle Datenmodelle sind unveränderlich.

```java
// plugin.yml: depend: [HungerGames]
HungerGamesApi api = HungerGamesProvider.get();
// oder: Bukkit.getServicesManager().load(HungerGamesApi.class)

// Whitelist
api.whitelist().add(uuid);
api.whitelist().addByName("Katniss").thenAccept(uuid -> ...); // async Namensauflösung
api.whitelist().remove(uuid);
Set<UUID> tribute = api.whitelist().entries();

// Spielstatus & Teilnehmer
GamePhase phase = api.phase();
Set<UUID> lebend = api.participants().alive();
Set<UUID> eliminiert = api.participants().eliminated();
Optional<Winner> sieger = api.winner();   // Winner.Solo | Winner.Team | Winner.None

// Teams
var result = api.teams().create("Distrikt 12", TeamColor.GRAY);
api.teams().assign(uuid, result.team().orElseThrow().id());
Set<TeamColor> frei = api.teams().availableColors();

// Statistik
int kills = api.stats().kills(uuid);
List<KillEntry> top = api.stats().topKillers(5);
Optional<Duration> spielzeit = api.stats().elapsedGameTime();
```

Alle Mutationen liefern typisierte Ergebnisse (`TeamOperationResult.COLOR_TAKEN`,
`TEAM_FULL`, `PHASE_LOCKED`, …) statt stiller Fehler.

**Events** (normale Bukkit-Listener, gefeuert auf dem Main-Thread):

| Event | Wann |
|---|---|
| `GamePhaseChangeEvent` | Phasenwechsel |
| `ParticipantEliminatedEvent` | Tribut eliminiert (mit Killer und Verbleibenden) |
| `ParticipantKillEvent` | Kill gezählt (mit neuem Gesamtstand) |
| `ParticipantRevivedEvent` | Admin-Wiederbelebung |
| `TeamCreatedEvent` / `TeamDeletedEvent` | Team angelegt/gelöscht |
| `TeamColorChangedEvent` | Teamfarbe gewechselt |
| `TeamMembershipChangedEvent` | Beitritt/Wechsel/Austritt (mit Ursache: API/Admin/Spieler/Zufall) |
| `WhitelistChangedEvent` | Whitelist-Änderung |
| `WinnerDeclaredEvent` | Rundenende mit Ergebnis |

## Bauen & Testen

```bash
# Build inkl. aller Tests (Gradle 9 via Wrapper, Java 25)
java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain build
```

Das fertige Plugin liegt danach unter
`hungergames-plugin/build/libs/TheHungerGames-<version>.jar` (API-Klassen inklusive — ein
einzelnes Jar genügt).

Die Spiellogik (`com.hungergames.game`) ist **komplett Bukkit-frei** und ohne Server testbar;
ein Architektur-Test erzwingt das. Die Suite (129 Tests) deckt u. a. ab: Gewinnerermittlung
(Solo/Team/Timeout/Disconnects), Farb-Exklusivität und Teamregeln, Border-Geschwindigkeit
und alle Konflikt-Optionen, transaktionale Config-Änderungen mit Overrides, die Zeitpläne
für Supply Drops und Sponsor-Tokens (kein Doppel-Trigger nach Neustart), die
Sponsor-Shop-Syntax sowie die Schutzregeln aus Sicht eines Spielers ohne OP.

## Migration von v0.16

Bestehende Installationen funktionieren ohne Handarbeit weiter:

- Alte `config.yml`-Schlüssel (`gameduration`, `arena.worldborder-size`, …) werden beim
  ersten Start auf die neuen Keys gemappt.
- `whitelist.yml` (v1: Namen + Teamnummern + `dead`-Flags) wird automatisch in die neue
  UUID-basierte Session migriert; nicht auflösbare Namen werden im Log gemeldet.
- Die Permission `gamemaster` bleibt als Alias erhalten.

**Breaking Changes:** `/allow` vergibt kein OP mehr (Sicherheitsentscheidung). Die
Death-Action steht standardmäßig auf `SPECTATOR` statt `KICK`.

## Architektur

```
hungergames-api/            Öffentliche API: Interfaces, Records, Bukkit-Events
hungergames-plugin/
 └─ com.hungergames
     ├─ game/               Bukkit-FREIER Kern (testbar ohne Server)
     │   ├─ GameSession     Aggregat: Phasen, Whitelist, Eliminierung, Sieg, Persistenz
     │   ├─ border/         Phasenmodell, BorderMath (Konflikte + Lösungen), Engine
     │   ├─ config/         Typisierte Settings, Transaktionen, Overrides, Audit
     │   └─ protection/     Phasen-/Regions-Regelmatrix
     ├─ platform/           Bukkit-Adapter: Listener, GUIs, Scheduler, YAML, API-Impl
     └─ (legacy)            Arena-Generierung, Startsequenz, Loot — über Fassaden angebunden
```

Details: [`docs/Architecture.md`](docs/Architecture.md) (Zielbild und Migrationsplan) und
[`docs/Analysis.md`](docs/Analysis.md) (Schwachstellenanalyse der v0.16).

---

**Autor:** [Raindancer118](https://github.com/Raindancer118) · Mitwirkende: TeddyBear2004
