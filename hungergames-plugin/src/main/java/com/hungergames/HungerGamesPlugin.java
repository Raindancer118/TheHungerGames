package com.hungergames;

import com.hungergames.api.HungerGamesProvider;
import com.hungergames.api.model.GamePhase;
import com.hungergames.commands.AllowCommand;
import com.hungergames.commands.ConfigReloadCommand;
import com.hungergames.commands.FliptableCommand;
import com.hungergames.commands.HgCommand;
import com.hungergames.commands.InitCommand;
import com.hungergames.commands.ReloadCommand;
import com.hungergames.commands.SpeedupCommand;
import com.hungergames.commands.StartCommand;
import com.hungergames.commands.StartupCommand;
import com.hungergames.commands.TestEndCommand;
import com.hungergames.commands.TestLootCommand;
import com.hungergames.commands.TestStartCommand;
import com.hungergames.game.GameSession;
import com.hungergames.game.TeamRules;
import com.hungergames.game.config.ConfigurationService;
import com.hungergames.game.port.GameClock;
import com.hungergames.items.ItemListener;
import com.hungergames.listeners.PortalListener;
import com.hungergames.platform.BukkitEventPublisher;
import com.hungergames.platform.NameResolver;
import com.hungergames.platform.VirtualTime;
import com.hungergames.platform.api.HungerGamesApiImpl;
import com.hungergames.platform.border.BorderService;
import com.hungergames.platform.config.FileAuditSink;
import com.hungergames.platform.config.HgSettings;
import com.hungergames.platform.config.YamlConfigBackend;
import com.hungergames.platform.game.GameTimerService;
import com.hungergames.platform.gui.AnvilInput;
import com.hungergames.platform.gui.MenuListener;
import com.hungergames.platform.listener.ConnectionListener;
import com.hungergames.platform.listener.EliminationListener;
import com.hungergames.platform.listener.ProtectionService;
import com.hungergames.platform.persistence.YamlSessionStore;
import com.hungergames.platform.spectator.SpectatorService;
import com.hungergames.platform.team.TeamPresentationService;
import com.hungergames.player.WhitelistManager;
import com.hungergames.stagerunner.FinishRunner;
import com.hungergames.utils.ChatInputHandler;
import com.hungergames.utils.ConfigManager;
import com.hungergames.utils.Sender;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.time.Duration;
import java.util.Random;

/**
 * Hauptklasse: verdrahtet den Bukkit-freien Spielkern (GameSession, Config,
 * Border) mit den Plattform-Services und registriert die öffentliche API.
 */
public final class HungerGamesPlugin extends JavaPlugin {

    private GameSession session;
    private ConfigurationService configService;
    private HgSettings settings;
    private GameTimerService gameTimer;
    private com.hungergames.api.HungerGamesApi api;
    private com.hungergames.platform.sound.SoundService soundService;
    private com.hungergames.platform.resourcepack.ResourcepackService resourcepackService;
    private com.hungergames.platform.effect.EffectService effectService;
    private com.hungergames.platform.announce.AnnouncementService announcements;
    private com.hungergames.platform.log.RoundLogService roundLog;
    private com.hungergames.platform.gui.admin.AdminServices adminServices;
    private com.hungergames.platform.web.HttpApiService httpApi;

    @Override
    public void onEnable() {
        Sender.log("HungerGames v2 startet ...");
        getDataFolder().mkdirs();

        // === Konfiguration ===
        settings = new HgSettings();
        configService = new ConfigurationService(
                settings.registry,
                new YamlConfigBackend(new File(getDataFolder(), "config.yml"), getLogger()),
                new FileAuditSink(new File(getDataFolder(), "audit.log").toPath(), getLogger()),
                System::currentTimeMillis);
        ConfigManager.init(configService, settings);

        // Vollständige Setting-Referenz für manuelle YML-Edits generieren
        com.hungergames.platform.config.ConfigReferenceWriter.write(
                new File(getDataFolder(), "config-reference.yml"), settings.registry, getLogger());

        // === Spielkern ===
        YamlSessionStore sessionStore = new YamlSessionStore(
                new File(getDataFolder(), "session.yml"), getLogger());
        session = new GameSession(this::currentTeamRules,
                new BukkitEventPublisher(this), sessionStore, GameClock.system(), new Random());
        GameState.bindSession(session);

        NameResolver nameResolver = new NameResolver(this);
        WhitelistManager.init(this, session, nameResolver);

        // === Plattform-Services ===
        VirtualTime virtualTime = new VirtualTime();
        SpeedupCommand.bindVirtualTime(virtualTime);

        BorderService borderService = new BorderService(configService, settings, session,
                virtualTime, () -> {
            var center = GameState.getInstance().getArenaCenter();
            return center != null ? center.getWorld() : null;
        });
        TeamPresentationService teamPresentation = new TeamPresentationService(session);
        SpectatorService spectatorService = new SpectatorService(this, session);
        com.hungergames.platform.persistence.RuntimeStateStore runtimeStore =
                new com.hungergames.platform.persistence.RuntimeStateStore(
                        new File(getDataFolder(), "runtime.yml"), getLogger());

        // === Sound / Ansagen / Rundenlog ===
        soundService = new com.hungergames.platform.sound.SoundService(
                this, configService, settings, getLogger());
        resourcepackService = new com.hungergames.platform.resourcepack.ResourcepackService(
                configService, settings, getLogger());
        effectService = new com.hungergames.platform.effect.EffectService(
                this, configService, settings);
        announcements = new com.hungergames.platform.announce.AnnouncementService(
                configService, settings);
        roundLog = new com.hungergames.platform.log.RoundLogService(
                new File(getDataFolder(), "logs"), session, configService, settings, getLogger());

        gameTimer = new GameTimerService(this, session, configService, settings,
                virtualTime, borderService, teamPresentation, runtimeStore, announcements);

        AnvilInput anvilInput = new AnvilInput(this);

        // === API registrieren ===
        api = new HungerGamesApiImpl(session, nameResolver, virtualTime, configService, settings,
                getServer().getScheduler().getMainThreadExecutor(this));
        HungerGamesProvider.register(api);
        getServer().getServicesManager().register(
                com.hungergames.api.HungerGamesApi.class, api, this, ServicePriority.Normal);

        // === Listener ===
        var pm = getServer().getPluginManager();
        pm.registerEvents(roundLog, this);
        pm.registerEvents(new com.hungergames.platform.announce.AnnouncementListener(
                session, configService, settings, announcements, soundService, effectService), this);
        pm.registerEvents(new MenuListener(), this);
        pm.registerEvents(anvilInput, this);
        pm.registerEvents(spectatorService, this);
        pm.registerEvents(teamPresentation, this);
        pm.registerEvents(gameTimer, this);
        pm.registerEvents(new EliminationListener(this, session, spectatorService,
                configService, settings, effectService, soundService), this);
        pm.registerEvents(new ConnectionListener(session, spectatorService, teamPresentation,
                gameTimer, resourcepackService), this);
        pm.registerEvents(resourcepackService, this);
        // Glasbox-Regeln: Tribute beim Join in die aktive Box holen, kein PVP darin
        pm.registerEvents(new com.hungergames.platform.lobby.LobbyBoxService(session), this);
        pm.registerEvents(new ProtectionService(session, configService, settings,
                () -> GameState.getInstance().getArenaCenter()), this);
        pm.registerEvents(new PortalListener(), this);
        pm.registerEvents(new com.hungergames.platform.listener.WinnerFinishListener(
                new FinishRunner(session), this), this);
        ChatInputHandler.getInstance().register();
        ItemListener.register();
        var invisibilityCloak = new com.hungergames.items.InvisibilityCloak(this);
        pm.registerEvents(invisibilityCloak, this);
        com.hungergames.items.CustomItems.setCloak(invisibilityCloak);
        com.hungergames.items.CustomItems.setServices(soundService, effectService, configService, settings);
        com.hungergames.items.CustomItems.registerRecipes(this);
        // Kein CountdownListener mehr: Barrier-Ringe auf Kopfhöhe (StartupRunner)
        // verhindern das Weglaufen, statt Bewegungen zu canceln.

        // Optionale Disconnect-Eliminierung (game.disconnect-elimination, 0 = aus)
        new com.hungergames.platform.listener.DisconnectEliminationService(
                session, configService, settings).start(this);

        // === Commands ===
        registerCommand("init", new InitCommand());
        registerCommand("start", new StartCommand());
        registerCommand("startup", new StartupCommand());
        registerCommand("test-start", new TestStartCommand());
        registerCommand("test-end", new TestEndCommand());
        registerCommand("test-loot", new TestLootCommand());
        registerCommand("fliptable", new FliptableCommand());
        registerCommand("config-reload", new ConfigReloadCommand());
        registerCommand("hg-reload", new ReloadCommand());
        registerCommand("speedup", new SpeedupCommand());
        registerCommand("allow", new AllowCommand());
        var borderGuard = new com.hungergames.platform.border.BorderDraftGuard(
                configService, settings, anvilInput);
        var gameControl = new com.hungergames.platform.game.GameControlService(session, roundLog);
        adminServices = new com.hungergames.platform.gui.admin.AdminServices(this, session,
                configService, settings, anvilInput, spectatorService, borderService, nameResolver,
                this::currentTeamRules, borderGuard, gameControl, soundService, announcements,
                roundLog, virtualTime);
        var gamemasterService = new com.hungergames.platform.gamemaster.GamemasterService(
                session, configService, settings, roundLog,
                new File(getDataFolder(), "gamemasters.yml"), getLogger());
        pm.registerEvents(gamemasterService, this);
        adminServices.gamemaster = gamemasterService;

        var opTracker = new com.hungergames.platform.game.OpTrackerService(this, session,
                configService, settings, runtimeStore, roundLog);
        pm.registerEvents(opTracker, this);
        opTracker.restoreFromStore();
        adminServices.opTracker = opTracker;

        var deathmatchService = new com.hungergames.platform.deathmatch.DeathmatchService(this,
                session, configService, settings, borderService, soundService, announcements,
                roundLog, runtimeStore);
        pm.registerEvents(deathmatchService, this);
        deathmatchService.setEffects(effectService);
        adminServices.deathmatch = deathmatchService;

        var supplyDropService = new com.hungergames.platform.supplydrop.SupplyDropService(this,
                session, configService, settings, virtualTime, soundService, announcements,
                roundLog, runtimeStore);
        pm.registerEvents(supplyDropService, this);
        supplyDropService.setEffects(effectService);
        supplyDropService.start();
        adminServices.supplyDrops = supplyDropService;

        var sponsorTokenService = new com.hungergames.platform.sponsor.SponsorTokenService(this,
                session, configService, settings, virtualTime, soundService, announcements,
                roundLog, runtimeStore);
        pm.registerEvents(sponsorTokenService, this);
        sponsorTokenService.start();
        adminServices.sponsorTokens = sponsorTokenService;

        var sponsorBeaconService = new com.hungergames.platform.sponsor.SponsorBeaconService(this,
                session, configService, settings, virtualTime, soundService, announcements,
                roundLog, runtimeStore, sponsorTokenService);
        pm.registerEvents(sponsorBeaconService, this);
        sponsorBeaconService.setEffects(effectService);
        sponsorBeaconService.start();
        adminServices.sponsorBeacons = sponsorBeaconService;

        // Stellt sicher, dass loot.yml auf der Platte liegt, bevor der Editor lädt
        com.hungergames.loot.LootConfig.getInstance();
        adminServices.lootTables = new com.hungergames.platform.loot.LootTableRepository(
                new File(getDataFolder(), "loot.yml"), getLogger(), configService, settings,
                roundLog);

        adminServices.preflight = new com.hungergames.platform.game.PreflightCheckService(
                session, configService, settings, gamemasterService, opTracker,
                adminServices.lootTables);

        // HTTP-API (Default aus; nur für geschlossene Netze gedacht)
        httpApi = new com.hungergames.platform.web.HttpApiService(this, adminServices, getLogger());
        httpApi.start();
        registerCommand("hg", new HgCommand(session, configService, settings, anvilInput,
                spectatorService, this::currentTeamRules, borderService, nameResolver, borderGuard,
                adminServices));

        // Admins/Spielmacher im Kreativ-Modus bekommen automatisch Hotbar-Items
        // (Kompass = Gesamtmenü, Nether-Stern = Spielsteuerung).
        var adminHotbar = new com.hungergames.platform.gui.admin.AdminHotbarService(this, adminServices);
        pm.registerEvents(adminHotbar, this);
        adminHotbar.syncOnlinePlayers();

        // Test-Simulation mit Mannequin-Tributen
        var mannequinSim = new com.hungergames.platform.sim.MannequinSimService(
                this, session, configService, settings, roundLog);
        pm.registerEvents(mannequinSim, this);
        adminServices.mannequinSim = mannequinSim;
        adminServices.mannequinSim.setEffects(effectService);

        // Visuelle Effekte (Partikel) im Admin-Menü verfügbar machen
        adminServices.effects = effectService;

        // Monster-Wellen
        var monsterWaves = new com.hungergames.platform.event.MonsterWaveService(
                this, configService, settings, roundLog);
        pm.registerEvents(monsterWaves, this);
        adminServices.monsterWaves = monsterWaves;

        // Vanilla-Spawnschutz aus — Schutz übernimmt der ProtectionService
        getServer().setSpawnRadius(0);

        // === Session-Wiederherstellung nach Neustart ===
        sessionStore.load().ifPresentOrElse(snapshot -> {
            session.restore(snapshot);
            boolean arenaRestored = runtimeStore.restoreArena(GameState.getInstance());
            Sender.log("Session wiederhergestellt: Phase " + snapshot.phase()
                    + ", " + snapshot.participants().size() + " Teilnehmer"
                    + (arenaRestored ? ", Arena-Geometrie geladen" : ""));

            if (snapshot.phase() == GamePhase.RUNNING) {
                var runtime = runtimeStore.loadRuntime().orElse(null);
                Duration elapsed;
                if (configService.get(settings.offlineTimePolicy) == HgSettings.OfflineTimePolicy.PAUSE
                        && runtime != null) {
                    // PAUSE (Default): Downtime zählt nicht — gespeicherte
                    // virtuelle Spielzeit wird exakt fortgesetzt.
                    elapsed = Duration.ofMillis(runtime.virtualElapsedMs());
                } else {
                    elapsed = snapshot.runningSinceMillis() != null
                            ? Duration.ofMillis(System.currentTimeMillis() - snapshot.runningSinceMillis())
                            : Duration.ZERO;
                }
                int borderIndex = runtime != null ? runtime.nextBorderPhaseIndex() : 0;

                if (!arenaRestored) {
                    Sender.warning("Arena-Geometrie fehlt — Runde wird fortgesetzt, aber Border "
                            + "und Gebietsschutz sind eingeschränkt, bis /init erneut läuft.");
                }
                gameTimer.resume(elapsed, borderIndex);
                Sender.log("Laufende Runde fortgesetzt: " + elapsed.toMinutes()
                        + "m Spielzeit, Border-Phase " + borderIndex);
            }
        }, () -> WhitelistManager.getInstance().migrateLegacyFile());

        // Nach dem Session-Restore: aktives Deathmatch und ausstehende Drops wieder aufnehmen
        deathmatchService.restoreFromStore();
        supplyDropService.restoreFromStore();

        // Hängengebliebene Test-Simulation (Attrappen + Test-Teams) aufräumen
        mannequinSim.purgeStaleSimulation();

        // Pre-Init-Zugangskontrolle, solange keine Arena existiert
        if (!session.phase().isInitialized()) {
            WhitelistManager.getInstance()
                    .setupPreInitWhitelist(configService.get(settings.preInitAdmins));
        }

        Sender.log("HungerGames v2 aktiviert — möge das Glück stets mit euch sein!");
    }

    @Override
    public void onDisable() {
        if (httpApi != null) {
            httpApi.stop();
        }
        if (gameTimer != null) {
            gameTimer.stop();
        }
        if (api != null) {
            getServer().getServicesManager().unregister(api);
            HungerGamesProvider.unregister();
        }
        Sender.log("HungerGames deaktiviert.");
    }

    private TeamRules currentTeamRules() {
        return new TeamRules(
                configService.get(settings.teamsEnabled),
                configService.get(settings.teamMaxSize),
                configService.get(settings.teamMaxTeams),
                configService.get(settings.teamAllowSwitching),
                configService.get(settings.teamCaptainEnabled),
                configService.get(settings.teamPlayersCanCreate),
                configService.get(settings.teamPlayersChooseColor),
                configService.get(settings.teamLockPhase));
    }

    private void registerCommand(String name, org.bukkit.command.CommandExecutor executor) {
        var command = getCommand(name);
        if (command != null) {
            command.setExecutor(executor);
            if (executor instanceof org.bukkit.command.TabCompleter completer) {
                command.setTabCompleter(completer);
            }
        } else {
            Sender.warning("Command '" + name + "' fehlt in plugin.yml");
        }
    }

    public GameSession session() {
        return session;
    }

    /** Service-Bündel für Admin-GUI, Legacy-Runner und HTTP-API. */
    public com.hungergames.platform.gui.admin.AdminServices adminServices() {
        return adminServices;
    }

    /** Für Legacy-Runner: konfigurierbare Sounds/Effekte. */
    public com.hungergames.platform.sound.SoundService soundService() {
        return soundService;
    }

    public com.hungergames.platform.effect.EffectService effectService() {
        return effectService;
    }

    public HgSettings settings() {
        return settings;
    }
}
