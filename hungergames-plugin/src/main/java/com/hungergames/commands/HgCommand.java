package com.hungergames.commands;

import com.hungergames.api.model.GamePhase;
import com.hungergames.api.model.Team;
import com.hungergames.api.model.TeamColor;
import com.hungergames.api.model.TeamId;
import com.hungergames.api.service.TeamOperationResult;
import com.hungergames.game.GameSession;
import com.hungergames.game.TeamRules;
import com.hungergames.game.border.BorderConflict;
import com.hungergames.game.border.BorderMath;
import com.hungergames.game.config.ConfigurationService;
import com.hungergames.game.port.GameEventPublisher.MembershipCause;
import com.hungergames.platform.Messages;
import com.hungergames.platform.NameResolver;
import com.hungergames.platform.border.BorderService;
import com.hungergames.platform.config.HgSettings;
import com.hungergames.platform.gui.AnvilInput;
import com.hungergames.platform.gui.config.ConfigMainMenu;
import com.hungergames.platform.gui.team.TeamSelectionMenu;
import com.hungergames.platform.spectator.SpectatorService;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Zentraler /hg-Command.
 *
 * <ul>
 *   <li>{@code /hg team} — Teamwahl-GUI (Spieler, in der Startphase)</li>
 *   <li>{@code /hg config} — Konfigurations-GUI (Admins)</li>
 *   <li>{@code /hg whitelist add|remove|list <name>} — Turnier-Whitelist</li>
 *   <li>{@code /hg team assign|remove|random ...} — Team-Administration</li>
 *   <li>{@code /hg revive <name>} — Eliminierung zurücknehmen</li>
 *   <li>{@code /hg border} — Border-Status und Konfliktprüfung</li>
 *   <li>{@code /hg spectate} — Spectator-Teleportmenü</li>
 *   <li>{@code /hg status} — Rundenübersicht</li>
 * </ul>
 */
public final class HgCommand implements CommandExecutor, TabCompleter {

    private static final String ADMIN_PERM = "hungergames.admin";

    private final GameSession session;
    private final ConfigurationService config;
    private final HgSettings settings;
    private final AnvilInput anvilInput;
    private final SpectatorService spectatorService;
    private final Supplier<TeamRules> teamRules;
    private final BorderService borderService;
    private final NameResolver nameResolver;
    private final com.hungergames.platform.gui.config.ConfigCategoryMenu.ChangeInterceptor borderGuard;
    private final com.hungergames.platform.gui.admin.AdminServices adminServices;

    public HgCommand(GameSession session, ConfigurationService config, HgSettings settings,
                     AnvilInput anvilInput, SpectatorService spectatorService,
                     Supplier<TeamRules> teamRules, BorderService borderService,
                     NameResolver nameResolver,
                     com.hungergames.platform.gui.config.ConfigCategoryMenu.ChangeInterceptor borderGuard,
                     com.hungergames.platform.gui.admin.AdminServices adminServices) {
        this.session = session;
        this.config = config;
        this.settings = settings;
        this.anvilInput = anvilInput;
        this.spectatorService = spectatorService;
        this.teamRules = teamRules;
        this.borderService = borderService;
        this.nameResolver = nameResolver;
        this.borderGuard = borderGuard;
        this.adminServices = adminServices;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            return help(sender);
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "admin" -> handleAdmin(sender);
            case "gm" -> handleGamemaster(sender, args);
            case "deathmatch" -> handleDeathmatch(sender, args);
            case "supplydrop" -> handleSupplyDrop(sender, args);
            case "sponsor" -> handleSponsor(sender, args);
            case "loot" -> handleLoot(sender, args);
            case "team" -> handleTeam(sender, args);
            case "config" -> handleConfig(sender);
            case "whitelist" -> handleWhitelist(sender, args);
            case "revive" -> handleRevive(sender, args);
            case "border" -> handleBorder(sender);
            case "spectate" -> handleSpectate(sender);
            case "sim" -> handleSim(sender, args);
            case "waves" -> handleWaves(sender, args);
            case "give" -> handleGive(sender, args);
            case "status" -> handleStatus(sender);
            default -> help(sender);
        }
        return true;
    }

    private boolean help(CommandSender sender) {
        sender.sendMessage(Messages.info("/hg admin — zentrale Admin-/Spielmacher-GUI"));
        sender.sendMessage(Messages.info("/hg team — Teamwahl (Spieler) | assign/remove/random (Admin)"));
        sender.sendMessage(Messages.info("/hg config — Konfigurations-GUI (Admin)"));
        sender.sendMessage(Messages.info("/hg whitelist add|remove|list [Name] (Admin)"));
        sender.sendMessage(Messages.info("/hg revive <Name> — Tribut wiederbeleben (Admin)"));
        sender.sendMessage(Messages.info("/hg border — Border-Status und Konfliktprüfung"));
        sender.sendMessage(Messages.info("/hg spectate — Beobachter-Teleport"));
        sender.sendMessage(Messages.info("/hg status — Rundenübersicht"));
        return true;
    }

    // ==================== team ====================

    private void handleTeam(CommandSender sender, String[] args) {
        if (args.length == 1) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Messages.error("Nur für Spieler."));
                return;
            }
            new TeamSelectionMenu(player, session, teamRules, anvilInput).open();
            return;
        }

        if (!sender.hasPermission(ADMIN_PERM)) {
            sender.sendMessage(Messages.error("Dafür fehlt dir die Berechtigung."));
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "assign" -> {
                if (args.length < 4) {
                    sender.sendMessage(Messages.error("/hg team assign <Spieler> <TeamId>"));
                    return;
                }
                resolveParticipant(sender, args[2], uuid -> {
                    TeamOperationResult result = session.teamAssign(uuid,
                            new TeamId(args[3]), MembershipCause.ADMIN);
                    sender.sendMessage(result.isSuccess()
                            ? Messages.ok(args[2] + " → Team " + args[3])
                            : Messages.error(Messages.describe(result)));
                });
            }
            case "remove" -> {
                if (args.length < 3) {
                    sender.sendMessage(Messages.error("/hg team remove <Spieler>"));
                    return;
                }
                resolveParticipant(sender, args[2], uuid -> {
                    TeamOperationResult result = session.teamRemovePlayer(uuid, MembershipCause.ADMIN);
                    sender.sendMessage(result.isSuccess()
                            ? Messages.ok(args[2] + " aus dem Team entfernt.")
                            : Messages.error(Messages.describe(result)));
                });
            }
            case "random" -> {
                if (!session.teamRegistry().enabled()) {
                    sender.sendMessage(Messages.error(Messages.describe(TeamOperationResult.TEAMS_DISABLED)));
                    return;
                }
                int assigned = session.teamAssignRandomly();
                sender.sendMessage(Messages.ok(assigned + " Tribute zufällig auf Teams verteilt."));
            }
            case "create" -> {
                if (args.length < 3) {
                    sender.sendMessage(Messages.error("/hg team create <Name> [Farbe]"));
                    return;
                }
                TeamColor color = args.length >= 4
                        ? parseColor(args[3]).orElse(null) : null;
                var result = session.teamCreate(args[2], color);
                sender.sendMessage(result.status().isSuccess()
                        ? Messages.ok("Team \"" + args[2] + "\" erstellt ("
                        + result.team().orElseThrow().color() + ").")
                        : Messages.error(Messages.describe(result.status())));
            }
            case "delete" -> {
                if (args.length < 3) {
                    sender.sendMessage(Messages.error("/hg team delete <TeamId>"));
                    return;
                }
                TeamOperationResult result = session.teamDelete(new TeamId(args[2]));
                sender.sendMessage(result.isSuccess()
                        ? Messages.ok("Team gelöscht.")
                        : Messages.error(Messages.describe(result)));
            }
            case "list" -> {
                for (Team team : session.teamRegistry().all()) {
                    String members = team.members().stream()
                            .map(uuid -> session.participants().nameOf(uuid).orElse("?"))
                            .reduce((a, b) -> a + ", " + b).orElse("—");
                    sender.sendMessage(Messages.info(team.id() + " \"" + team.name() + "\" ["
                            + team.color() + "]: " + members));
                }
            }
            default -> sender.sendMessage(Messages.error(
                    "/hg team [assign|remove|random|create|delete|list]"));
        }
    }

    // ==================== admin ====================

    private void handleAdmin(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.info("Die Admin-GUI ist nur ingame verfügbar. Für die "
                    + "Konsole: /hg status, /hg whitelist, /hg team, /hg revive."));
            return;
        }
        if (!com.hungergames.platform.Perms.canUseAdminGui(player)) {
            player.sendMessage(Messages.error("Dafür brauchst du "
                    + com.hungergames.platform.Perms.ADMIN + " oder "
                    + com.hungergames.platform.Perms.GAMEMASTER + "."));
            return;
        }
        new com.hungergames.platform.gui.admin.AdminMainMenu(player, adminServices).open();
    }

    // ==================== gm ====================

    private void handleGamemaster(CommandSender sender, String[] args) {
        var gm = adminServices.gamemaster;
        String sub = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "";

        switch (sub) {
            case "add", "remove" -> {
                if (!sender.hasPermission(ADMIN_PERM)) {
                    sender.sendMessage(Messages.error("Dafür fehlt dir die Berechtigung."));
                    return;
                }
                if (args.length < 3) {
                    sender.sendMessage(Messages.error("/hg gm " + sub + " <Spieler>"));
                    return;
                }
                List<String> errors = sub.equals("add")
                        ? gm.addName(sender.getName(), args[2])
                        : gm.removeName(sender.getName(), args[2]);
                if (errors.isEmpty()) {
                    sender.sendMessage(Messages.ok(args[2] + (sub.equals("add")
                            ? " zur Spielmacher-Liste hinzugefügt." : " von der Liste entfernt.")));
                } else {
                    errors.forEach(error -> sender.sendMessage(Messages.error(error)));
                }
            }
            case "list" -> {
                sender.sendMessage(Messages.info("Spielmacher-Liste: "
                        + (gm.names().isEmpty() ? "—" : String.join(", ", gm.names()))));
                var online = gm.onlineActive();
                sender.sendMessage(Messages.info("Aktiv: " + (online.isEmpty() ? "—"
                        : online.stream().map(Player::getName)
                        .reduce((a, b) -> a + ", " + b).orElse("—"))));
            }
            case "", "on", "off" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Messages.error("Nur für Spieler."));
                    return;
                }
                boolean active = gm.isActive(player.getUniqueId());
                boolean turnOn = sub.equals("on") || (sub.isEmpty() && !active);
                if (turnOn == active) {
                    player.sendMessage(Messages.info("Spielmacher-Modus ist bereits "
                            + (active ? "aktiv." : "inaktiv.")));
                    return;
                }
                var error = turnOn ? gm.activate(player) : gm.deactivate(player);
                error.ifPresentOrElse(
                        message -> player.sendMessage(Messages.error(message)),
                        () -> player.sendMessage(Messages.ok("Spielmacher-Modus "
                                + (turnOn ? "aktiviert." : "deaktiviert."))));
            }
            default -> sender.sendMessage(Messages.error("/hg gm [on|off|add|remove|list]"));
        }
    }

    // ==================== deathmatch ====================

    private void handleDeathmatch(CommandSender sender, String[] args) {
        var deathmatch = adminServices.deathmatch;
        String sub = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "status";
        switch (sub) {
            case "start" -> {
                if (!sender.hasPermission(com.hungergames.platform.Perms.GAMEMASTER)) {
                    sender.sendMessage(Messages.error("Dafür fehlt dir die Berechtigung."));
                    return;
                }
                deathmatch.start(sender.getName()).ifPresentOrElse(
                        error -> sender.sendMessage(Messages.error(error)),
                        () -> sender.sendMessage(Messages.ok("Deathmatch ausgelöst.")));
            }
            case "cancel" -> {
                if (!sender.hasPermission(com.hungergames.platform.Perms.GAMEMASTER)) {
                    sender.sendMessage(Messages.error("Dafür fehlt dir die Berechtigung."));
                    return;
                }
                deathmatch.cancel(sender.getName()).ifPresentOrElse(
                        error -> sender.sendMessage(Messages.error(error)),
                        () -> sender.sendMessage(Messages.ok("Deathmatch abgebrochen.")));
            }
            case "status" -> sender.sendMessage(Messages.info(
                    "Deathmatch: " + deathmatch.statusLine()));
            default -> sender.sendMessage(Messages.error("/hg deathmatch [start|cancel|status]"));
        }
    }

    // ==================== supplydrop ====================

    private void handleSupplyDrop(CommandSender sender, String[] args) {
        String sub = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "status";
        switch (sub) {
            case "now" -> {
                if (!sender.hasPermission(com.hungergames.platform.Perms.GAMEMASTER)) {
                    sender.sendMessage(Messages.error("Dafür fehlt dir die Berechtigung."));
                    return;
                }
                adminServices.supplyDrops.triggerNow(sender.getName()).ifPresentOrElse(
                        error -> sender.sendMessage(Messages.error(error)),
                        () -> sender.sendMessage(Messages.ok("Lieferung unterwegs.")));
            }
            case "status" -> sender.sendMessage(Messages.info(
                    "Supply Drops: " + adminServices.supplyDrops.statusLine()));
            default -> sender.sendMessage(Messages.error("/hg supplydrop [now|status]"));
        }
    }

    // ==================== sponsor ====================

    private void handleSponsor(CommandSender sender, String[] args) {
        String sub = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "status";
        var tokens = adminServices.sponsorTokens;
        var beacons = adminServices.sponsorBeacons;

        if (!sub.equals("status") && !sender.hasPermission(com.hungergames.platform.Perms.GAMEMASTER)) {
            sender.sendMessage(Messages.error("Dafür fehlt dir die Berechtigung."));
            return;
        }
        switch (sub) {
            case "status" -> {
                sender.sendMessage(Messages.info("Sponsor-Tokens: "
                        + (tokens.tokensEnabled() ? "aktiv" : "deaktiviert")));
                sender.sendMessage(Messages.info("Sponsor-Beacons: " + beacons.statusLine()));
            }
            case "give", "clear" -> {
                if (args.length < 3) {
                    sender.sendMessage(Messages.error("/hg sponsor " + sub + " <Spieler>"
                            + (sub.equals("give") ? " [Anzahl]" : "")));
                    return;
                }
                Player target = Bukkit.getPlayer(args[2]);
                if (target == null) {
                    sender.sendMessage(Messages.error("\"" + args[2] + "\" ist nicht online."));
                    return;
                }
                if (sub.equals("give")) {
                    int amount = 1;
                    if (args.length >= 4) {
                        try {
                            amount = Math.max(1, Integer.parseInt(args[3]));
                        } catch (NumberFormatException e) {
                            sender.sendMessage(Messages.error("\"" + args[3] + "\" ist keine Zahl."));
                            return;
                        }
                    }
                    tokens.giveManually(sender.getName(), target, amount);
                    sender.sendMessage(Messages.ok(amount + " Token an " + target.getName() + "."));
                } else {
                    int removed = tokens.clearTokens(target);
                    sender.sendMessage(Messages.ok(removed + " Token bei "
                            + target.getName() + " entfernt."));
                }
            }
            case "beacon" -> {
                String action = args.length >= 3 ? args[2].toLowerCase(Locale.ROOT) : "list";
                switch (action) {
                    case "create" -> {
                        if (!(sender instanceof Player player)) {
                            sender.sendMessage(Messages.error("Nur für Spieler."));
                            return;
                        }
                        beacons.createBeacon(player.getLocation(), player.getName())
                                .ifPresentOrElse(
                                        error -> sender.sendMessage(Messages.error(error)),
                                        () -> sender.sendMessage(Messages.ok("Sponsor-Beacon erstellt.")));
                    }
                    case "remove" -> {
                        if (!(sender instanceof Player player)) {
                            sender.sendMessage(Messages.error("Nur für Spieler."));
                            return;
                        }
                        beacons.removeNearest(player.getLocation(), player.getName())
                                .ifPresentOrElse(
                                        error -> sender.sendMessage(Messages.error(error)),
                                        () -> sender.sendMessage(Messages.ok("Beacon entfernt.")));
                    }
                    case "list" -> {
                        var active = beacons.activeBeacons();
                        sender.sendMessage(Messages.info("Aktive Sponsor-Beacons: " + active.size()));
                        for (var beacon : active) {
                            sender.sendMessage(Messages.info("  " + beacon.getBlockX() + " / "
                                    + beacon.getBlockY() + " / " + beacon.getBlockZ()));
                        }
                    }
                    default -> sender.sendMessage(Messages.error(
                            "/hg sponsor beacon [create|remove|list]"));
                }
            }
            default -> sender.sendMessage(Messages.error(
                    "/hg sponsor [status|give|clear|beacon]"));
        }
    }

    // ==================== loot ====================

    private void handleLoot(CommandSender sender, String[] args) {
        var repo = adminServices.lootTables;
        String sub = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "";
        boolean gamemaster = sender.hasPermission(com.hungergames.platform.Perms.GAMEMASTER)
                || sender.hasPermission(ADMIN_PERM);
        boolean admin = sender.hasPermission(ADMIN_PERM);

        if (!gamemaster) {
            sender.sendMessage(Messages.error("Dafür fehlt dir die Berechtigung."));
            return;
        }
        switch (sub) {
            case "" -> {
                if (sender instanceof Player player) {
                    new com.hungergames.platform.gui.loot.LootMainMenu(player, adminServices,
                            player::closeInventory).open();
                } else {
                    sender.sendMessage(Messages.info(
                            "/hg loot tables|table <name>|test <name>|validate|reload|export"));
                }
            }
            case "tables" -> {
                sender.sendMessage(Messages.info("LootTables (" + repo.tableNames().size() + "):"));
                for (String name : repo.tableNames()) {
                    var table = repo.table(name).orElseThrow();
                    sender.sendMessage(Messages.info("  " + name + " — " + table.entries.size()
                            + " Einträge, Gewicht " + repo.totalWeight(table)
                            + " (" + repo.usageOf(name) + ")"));
                }
            }
            case "table" -> {
                if (args.length < 3) {
                    sender.sendMessage(Messages.error("/hg loot table <Name>"));
                    return;
                }
                if (sender instanceof Player player) {
                    new com.hungergames.platform.gui.loot.LootTableDetailMenu(player,
                            adminServices, args[2], player::closeInventory).open();
                } else {
                    repo.table(args[2]).ifPresentOrElse(table -> table.entries.forEach(entry ->
                                    sender.sendMessage(Messages.info("  " + entry.item + " ×"
                                            + entry.minAmount + "-" + entry.maxAmount
                                            + " (Gewicht " + entry.weight + ")"))),
                            () -> sender.sendMessage(Messages.error("Unbekannte Tabelle.")));
                }
            }
            case "test" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Messages.error("Nur für Spieler."));
                    return;
                }
                String table = args.length >= 3 ? args[2] : "chest";
                new com.hungergames.platform.gui.loot.LootTestMenu(player, adminServices,
                        table, player::closeInventory).open();
            }
            case "validate" -> {
                if (!admin) {
                    sender.sendMessage(Messages.error("Nur Admins dürfen validieren."));
                    return;
                }
                List<String> problems = repo.validateAll();
                if (problems.isEmpty()) {
                    sender.sendMessage(Messages.ok("Alle LootTables sind gültig."));
                } else {
                    problems.forEach(problem -> sender.sendMessage(Messages.error(problem)));
                }
            }
            case "reload" -> {
                if (!admin) {
                    sender.sendMessage(Messages.error("Nur Admins dürfen neu laden."));
                    return;
                }
                if (repo.isDirty()) {
                    sender.sendMessage(Messages.error("Es gibt ungespeicherte Änderungen! "
                            + "Erst /hg loot export (speichern) oder in der GUI verwerfen."));
                    return;
                }
                repo.loadFromDisk();
                com.hungergames.loot.LootConfig.getInstance().reload();
                sender.sendMessage(Messages.ok("Loot-Konfiguration neu geladen."));
            }
            case "export" -> {
                if (!admin) {
                    sender.sendMessage(Messages.error("Nur Admins dürfen speichern."));
                    return;
                }
                List<String> errors = repo.save(sender.getName());
                if (errors.isEmpty()) {
                    sender.sendMessage(Messages.ok("Loot-Konfiguration gespeichert."));
                } else {
                    errors.forEach(error -> sender.sendMessage(Messages.error(error)));
                }
            }
            default -> sender.sendMessage(Messages.error(
                    "/hg loot [tables|table <name>|test <name>|validate|reload|export]"));
        }
    }

    // ==================== config / whitelist / revive ====================

    private void handleConfig(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.error("Nur für Spieler."));
            return;
        }
        if (!player.hasPermission(ADMIN_PERM)) {
            player.sendMessage(Messages.error("Dafür fehlt dir die Berechtigung."));
            return;
        }
        new ConfigMainMenu(player, config, anvilInput, borderGuard).open();
    }

    private void handleWhitelist(CommandSender sender, String[] args) {
        if (!sender.hasPermission(ADMIN_PERM)) {
            sender.sendMessage(Messages.error("Dafür fehlt dir die Berechtigung."));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Messages.error("/hg whitelist add|remove|list [Name]"));
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "add" -> {
                if (args.length < 3) {
                    sender.sendMessage(Messages.error("/hg whitelist add <Name>"));
                    return;
                }
                nameResolver.resolve(args[2]).whenComplete((resolved, error) ->
                        Bukkit.getScheduler().runTask(pluginOf(sender), () -> {
                            if (resolved == null) {
                                sender.sendMessage(Messages.error("Kein Account namens \""
                                        + args[2] + "\" gefunden."));
                                return;
                            }
                            boolean added = session.whitelistAdd(resolved.uuid(), resolved.name());
                            sender.sendMessage(added
                                    ? Messages.ok(resolved.name() + " als Tribut registriert.")
                                    : Messages.info(resolved.name() + " ist bereits registriert."));
                        }));
            }
            case "remove" -> {
                if (args.length < 3) {
                    sender.sendMessage(Messages.error("/hg whitelist remove <Name>"));
                    return;
                }
                resolveParticipant(sender, args[2], uuid -> {
                    session.whitelistRemove(uuid);
                    sender.sendMessage(Messages.ok(args[2] + " aus dem Turnier entfernt."));
                });
            }
            case "list" -> {
                var participants = session.participants().all();
                sender.sendMessage(Messages.info("Tribute (" + participants.size() + "):"));
                for (var participant : participants) {
                    sender.sendMessage(Messages.info("  " + participant.lastKnownName()
                            + (participant.isAlive() ? " §a(lebend)" : " §c(eliminiert)")));
                }
            }
            default -> sender.sendMessage(Messages.error("/hg whitelist add|remove|list"));
        }
    }

    private void handleRevive(CommandSender sender, String[] args) {
        if (!sender.hasPermission(ADMIN_PERM)) {
            sender.sendMessage(Messages.error("Dafür fehlt dir die Berechtigung."));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Messages.error("/hg revive <Name>"));
            return;
        }
        resolveParticipant(sender, args[1], uuid -> {
            if (session.revive(uuid)) {
                sender.sendMessage(Messages.ok(args[1] + " ist zurück im Spiel."));
                Player player = Bukkit.getPlayer(uuid);
                if (player != null) {
                    player.setGameMode(org.bukkit.GameMode.SURVIVAL);
                    player.sendMessage(Messages.ok("Das Kapitol gewährt dir eine zweite Chance!"));
                }
            } else {
                sender.sendMessage(Messages.error(args[1] + " ist nicht eliminiert."));
            }
        });
    }

    // ==================== border / spectate / status ====================

    private void handleBorder(CommandSender sender) {
        var borderSettings = borderService.currentSettings();
        Duration gameDuration = config.get(settings.gameDuration);

        sender.sendMessage(Messages.info("Border: " + (int) borderSettings.initialSize()
                + " → min " + (int) borderSettings.minimumSize()
                + " | max " + borderSettings.maxEdgeSpeed() + " Blöcke/s je Kante"));
        int index = 0;
        for (var phase : borderSettings.phases()) {
            sender.sendMessage(Messages.info("  Phase " + (++index) + ": → "
                    + (int) phase.targetSize() + " ("
                    + String.format(Locale.ROOT, "%.2f", BorderMath.impliedSpeed(borderSettings, index - 1))
                    + " Blöcke/s, " + BorderMath.effectiveDuration(borderSettings, index - 1).toSeconds()
                    + "s)"));
        }

        List<BorderConflict> conflicts = BorderMath.validate(borderSettings, Optional.of(gameDuration));
        if (conflicts.isEmpty()) {
            sender.sendMessage(Messages.ok("Keine Konflikte in der Border-Konfiguration."));
        } else if (sender instanceof Player player && player.hasPermission(ADMIN_PERM)) {
            sender.sendMessage(Messages.error(conflicts.size() + " Konflikt(e) gefunden — öffne Auflösung ..."));
            // Gespeicherte Konfiguration ist der "Entwurf"; keine zurückgehaltene Änderung
            new com.hungergames.platform.gui.border.BorderConflictMenu(player, config, settings,
                    borderSettings, gameDuration, conflicts.get(0),
                    com.hungergames.game.config.ConfigurationService.Scope.PERSISTENT,
                    tx -> { }, player::closeInventory, anvilInput).open();
        } else {
            conflicts.forEach(conflict -> sender.sendMessage(Messages.error(
                    "Konflikt in Phase " + (conflict.phaseIndex() + 1) + ": " + conflict.type())));
        }
    }

    // ==================== waves (Monster-Wellen) ====================

    private void handleWaves(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.error("Nur für Spieler (Ort = deine Position)."));
            return;
        }
        if (!sender.hasPermission(com.hungergames.platform.Perms.GAMEMASTER)
                && !sender.hasPermission(ADMIN_PERM)) {
            sender.sendMessage(Messages.error("Dafür fehlt dir die Berechtigung."));
            return;
        }
        var svc = adminServices.monsterWaves;
        if (svc == null) {
            sender.sendMessage(Messages.error("Monster-Wellen nicht verfügbar."));
            return;
        }
        String sub = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "";
        switch (sub) {
            case "stop" -> player.sendMessage(Messages.ok(svc.stopAll() + " Serie(n) gestoppt."));
            case "status" -> player.sendMessage(Messages.info("Laufende Wellen-Serien: "
                    + svc.activeSeries()));
            case "start" -> {
                // /hg waves start [mob] [count] [waves] [interval]
                String mob = args.length >= 3 ? args[2] : svc.defaultMob();
                int count = argInt(args, 3, svc.defaultCount());
                int waves = argInt(args, 4, svc.defaultWaves());
                int interval = argInt(args, 5, svc.defaultInterval());
                svc.start(player.getLocation(), mob, count, waves, interval, player.getName())
                        .ifPresentOrElse(
                                error -> player.sendMessage(Messages.error(error)),
                                () -> player.sendMessage(Messages.ok(waves + " " + mob.toUpperCase(Locale.ROOT)
                                        + "-Wellen à " + count + " gestartet (Intervall " + interval + "s).")));
            }
            default -> new com.hungergames.platform.gui.admin.MonsterWaveMenu(player, adminServices,
                    () -> new com.hungergames.platform.gui.admin.AdminMainMenu(player, adminServices)
                            .open()).open();
        }
    }

    private static int argInt(String[] args, int index, int fallback) {
        if (args.length > index) {
            try {
                return Integer.parseInt(args[index].trim());
            } catch (NumberFormatException ignored) {
                // Fallback
            }
        }
        return fallback;
    }

    // ==================== give (Custom-Items) ====================

    private void handleGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission(com.hungergames.platform.Perms.GAMEMASTER)
                && !sender.hasPermission(ADMIN_PERM)) {
            sender.sendMessage(Messages.error("Dafür fehlt dir die Berechtigung."));
            return;
        }
        var ids = com.hungergames.items.CustomItems.ids();
        if (args.length < 2 || args[1].equalsIgnoreCase("list")) {
            sender.sendMessage(Messages.info("Custom-Items: " + String.join(", ", ids)));
            sender.sendMessage(Messages.info("/hg give <item> [anzahl] [spieler]"));
            return;
        }
        String id = args[1].toUpperCase(Locale.ROOT);
        org.bukkit.inventory.ItemStack item = com.hungergames.items.CustomItems.create(id);
        if (item == null) {
            sender.sendMessage(Messages.error("Unbekanntes Custom-Item: " + args[1]
                    + " (bekannt: " + String.join(", ", ids) + ")"));
            return;
        }
        int amount = 1;
        if (args.length >= 3) {
            try {
                amount = Math.max(1, Math.min(64, Integer.parseInt(args[2].trim())));
            } catch (NumberFormatException e) {
                sender.sendMessage(Messages.error("\"" + args[2] + "\" ist keine Zahl."));
                return;
            }
        }
        Player target;
        if (args.length >= 4) {
            target = org.bukkit.Bukkit.getPlayerExact(args[3]);
            if (target == null) {
                sender.sendMessage(Messages.error("Spieler \"" + args[3] + "\" ist nicht online."));
                return;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            sender.sendMessage(Messages.error("Von der Konsole: /hg give <item> [anzahl] <spieler>"));
            return;
        }
        item.setAmount(amount);
        var leftover = target.getInventory().addItem(item);
        leftover.values().forEach(rest ->
                target.getWorld().dropItemNaturally(target.getLocation(), rest));
        String label = com.hungergames.items.CustomItems.byId(id)
                .map(com.hungergames.items.CustomItems::displayName).orElse(id);
        target.sendMessage(Messages.ok(amount + "× " + label + " erhalten."));
        if (!target.equals(sender)) {
            sender.sendMessage(Messages.ok(amount + "× " + label + " an " + target.getName() + " gegeben."));
        }
    }

    // ==================== sim (Mannequin-Test) ====================

    private void handleSim(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.error("Nur für Spieler."));
            return;
        }
        if (!sender.hasPermission(ADMIN_PERM)) {
            sender.sendMessage(Messages.error("Dafür fehlt dir die Berechtigung."));
            return;
        }
        var sim = adminServices.mannequinSim;
        if (sim == null) {
            sender.sendMessage(Messages.error("Simulation nicht verfügbar."));
            return;
        }
        String sub = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "";
        switch (sub) {
            case "spawn" -> {
                int count = 1;
                if (args.length >= 3) {
                    try {
                        count = Integer.parseInt(args[2]);
                    } catch (NumberFormatException e) {
                        player.sendMessage(Messages.error("\"" + args[2] + "\" ist keine Zahl."));
                        return;
                    }
                }
                player.sendMessage(Messages.info(sim.spawn(player, count)));
            }
            case "eliminate", "kill" ->
                    sim.eliminateOne(player).ifPresent(msg -> player.sendMessage(Messages.info(msg)));
            case "clear" ->
                    player.sendMessage(Messages.ok(sim.clear() + " Attrappen entfernt."));
            default -> new com.hungergames.platform.gui.admin.SimulationMenu(player, adminServices,
                    () -> new com.hungergames.platform.gui.admin.AdminMainMenu(player, adminServices)
                            .open()).open();
        }
    }

    private void handleSpectate(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.error("Nur für Spieler."));
            return;
        }
        if (player.getGameMode() != org.bukkit.GameMode.SPECTATOR
                && session.participants().isAlive(player.getUniqueId())) {
            player.sendMessage(Messages.error("Du bist noch im Spiel, Tribut!"));
            return;
        }
        spectatorService.openTeleportMenu(player);
    }

    private void handleStatus(CommandSender sender) {
        GamePhase phase = session.phase();
        sender.sendMessage(Messages.info("Phase: " + phase));
        sender.sendMessage(Messages.info("Tribute: " + session.participants().aliveCount()
                + " lebend / " + session.participants().eliminated().size() + " eliminiert"));
        if (adminServices.deathmatch != null) {
            sender.sendMessage(Messages.info("Deathmatch: " + adminServices.deathmatch.statusLine()));
        }
        session.winner().ifPresent(winner ->
                sender.sendMessage(Messages.info("Sieger: " + describeWinner(winner))));
    }

    private String describeWinner(com.hungergames.api.model.Winner winner) {
        return switch (winner) {
            case com.hungergames.api.model.Winner.Solo solo ->
                    session.participants().nameOf(solo.uuid()).orElse("?");
            case com.hungergames.api.model.Winner.Team team ->
                    session.teamRegistry().team(team.teamId())
                            .map(Team::name).orElse(team.teamId().value());
            case com.hungergames.api.model.Winner.None none -> "keiner";
        };
    }

    // ==================== Hilfen ====================

    private void resolveParticipant(CommandSender sender, String name,
                                    java.util.function.Consumer<java.util.UUID> action) {
        Optional<java.util.UUID> known = session.participants().all().stream()
                .filter(participant -> participant.lastKnownName().equalsIgnoreCase(name))
                .map(participant -> participant.uuid())
                .findFirst();
        if (known.isPresent()) {
            action.accept(known.get());
        } else {
            sender.sendMessage(Messages.error("\"" + name + "\" ist kein registrierter Tribut."));
        }
    }

    private static Optional<TeamColor> parseColor(String input) {
        try {
            return Optional.of(TeamColor.valueOf(input.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static org.bukkit.plugin.Plugin pluginOf(CommandSender sender) {
        return org.bukkit.plugin.java.JavaPlugin.getPlugin(com.hungergames.HungerGamesPlugin.class);
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return filter(args[0], "admin", "gm", "deathmatch", "supplydrop", "sponsor", "loot",
                    "team", "config", "whitelist", "revive", "border", "spectate", "sim", "waves",
                    "give", "status");
        }
        if (args.length == 2) {
            return switch (args[0].toLowerCase(Locale.ROOT)) {
                case "team" -> filter(args[1], "assign", "remove", "random", "create", "delete", "list");
                case "whitelist" -> filter(args[1], "add", "remove", "list");
                case "gm" -> filter(args[1], "on", "off", "add", "remove", "list");
                case "deathmatch" -> filter(args[1], "start", "cancel", "status");
                case "supplydrop" -> filter(args[1], "now", "status");
                case "sponsor" -> filter(args[1], "status", "give", "clear", "beacon");
                case "loot" -> filter(args[1], "tables", "table", "test", "validate",
                        "reload", "export");
                case "sim" -> filter(args[1], "spawn", "eliminate", "clear");
                case "waves" -> filter(args[1], "start", "stop", "status");
                case "give" -> filterList(args[1], com.hungergames.items.CustomItems.ids());
                default -> List.of();
            };
        }
        // /hg give <item> <anzahl> <spieler>
        if (args.length == 4 && args[0].equalsIgnoreCase("give")) {
            return filterList(args[3], org.bukkit.Bukkit.getOnlinePlayers().stream()
                    .map(org.bukkit.entity.Player::getName).toList());
        }
        return List.of();
    }

    private static List<String> filter(String prefix, String... options) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return Stream.of(options).filter(option -> option.startsWith(lower)).toList();
    }

    private static List<String> filterList(String prefix, java.util.Collection<String> options) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(lower)).sorted().toList();
    }
}
