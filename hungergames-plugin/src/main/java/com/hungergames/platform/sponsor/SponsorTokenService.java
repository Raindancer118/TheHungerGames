package com.hungergames.platform.sponsor;

import com.hungergames.HungerGamesPlugin;
import com.hungergames.api.event.GamePhaseChangeEvent;
import com.hungergames.api.model.GamePhase;
import com.hungergames.game.GameSession;
import com.hungergames.game.config.ConfigurationService;
import com.hungergames.game.sponsor.TokenSchedule;
import com.hungergames.platform.VirtualTime;
import com.hungergames.platform.announce.AnnouncementService;
import com.hungergames.platform.announce.AnnouncementService.Style;
import com.hungergames.platform.config.HgSettings;
import com.hungergames.platform.log.RoundLogService;
import com.hungergames.platform.persistence.RuntimeStateStore;
import com.hungergames.platform.sound.SoundService;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Sponsor Tokens: physische Items (Default Nether Star), die lebende Tribute
 * zeitbasiert fürs Überleben erhalten und an Sponsor-Beacons ausgeben können.
 *
 * <p>Echtheit wird ausschließlich über einen PersistentDataContainer-Key
 * geprüft — normale Nether Stars zählen nie als Sponsor Tokens. Erhaltene
 * Wellen und verdiente Tokens pro Spieler werden persistiert: Rejoin holt
 * fehlende Wellen nach, ein Neustart vergibt nichts doppelt.</p>
 */
public final class SponsorTokenService implements Listener {

    private static final NamespacedKey TOKEN_KEY =
            new NamespacedKey(JavaPlugin.getPlugin(HungerGamesPlugin.class), "sponsor_token");

    private final Plugin plugin;
    private final GameSession session;
    private final ConfigurationService config;
    private final HgSettings settings;
    private final VirtualTime virtualTime;
    private final SoundService sounds;
    private final AnnouncementService announcements;
    private final RoundLogService roundLog;
    private final RuntimeStateStore runtimeStore;

    /** uuid → {erhaltene Wellen, verdiente Tokens gesamt}. */
    private final Map<java.util.UUID, int[]> perPlayer = new HashMap<>();
    private BukkitTask timerTask;

    public SponsorTokenService(Plugin plugin, GameSession session, ConfigurationService config,
                               HgSettings settings, VirtualTime virtualTime, SoundService sounds,
                               AnnouncementService announcements, RoundLogService roundLog,
                               RuntimeStateStore runtimeStore) {
        this.plugin = plugin;
        this.session = session;
        this.config = config;
        this.settings = settings;
        this.virtualTime = virtualTime;
        this.sounds = sounds;
        this.announcements = announcements;
        this.roundLog = roundLog;
        this.runtimeStore = runtimeStore;
    }

    /** Startet den Vergabe-Tick und lädt den persistierten Zustand. */
    public void start() {
        perPlayer.putAll(runtimeStore.loadSponsorTokenState());
        timerTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40L, 40L);
    }

    public void stop() {
        if (timerTask != null) {
            timerTask.cancel();
            timerTask = null;
        }
    }

    public boolean tokensEnabled() {
        return config.get(settings.sponsorsEnabled) && config.get(settings.sponsorTokensEnabled);
    }

    // ==================== Token-Item ====================

    /** Erstellt einen Stapel echter Sponsor Tokens (PDC-markiert). */
    public ItemStack createTokens(int amount) {
        Material material = Material.matchMaterial(config.get(settings.sponsorTokenMaterial));
        if (material == null) {
            material = Material.NETHER_STAR;
        }
        ItemStack item = new ItemStack(material, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(LegacyComponentSerializer.legacySection()
                .deserialize(config.get(settings.sponsorTokenDisplayName))
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        meta.lore(config.get(settings.sponsorTokenLore).stream()
                .map(line -> LegacyComponentSerializer.legacySection().deserialize(line)
                        .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false))
                .toList());
        meta.getPersistentDataContainer().set(TOKEN_KEY, PersistentDataType.BOOLEAN, true);
        item.setItemMeta(meta);
        return item;
    }

    /** Nur der PDC-Key zählt — Material und Name sind fälschbar. */
    public static boolean isToken(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer()
                .has(TOKEN_KEY, PersistentDataType.BOOLEAN);
    }

    /** Anzahl echter Sponsor Tokens im Inventar. */
    public int countTokens(Player player) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (isToken(item)) {
                count += item.getAmount();
            }
        }
        return count;
    }

    /**
     * Entfernt Tokens aus dem Inventar.
     *
     * @return {@code false}, wenn nicht genug vorhanden waren (nichts entfernt)
     */
    public boolean removeTokens(Player player, int amount) {
        if (countTokens(player) < amount) {
            return false;
        }
        int remaining = amount;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack item = contents[i];
            if (!isToken(item)) {
                continue;
            }
            int take = Math.min(remaining, item.getAmount());
            remaining -= take;
            if (take >= item.getAmount()) {
                player.getInventory().setItem(i, null);
            } else {
                item.setAmount(item.getAmount() - take);
            }
        }
        return true;
    }

    /** Gibt Tokens ins Inventar; bei vollem Inventar droppen sie am Spieler. */
    public void giveTokens(Player player, int amount) {
        var leftover = player.getInventory().addItem(createTokens(amount));
        for (ItemStack rest : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), rest);
        }
    }

    /** Manuelle Vergabe (Admin/GM-Override) — zählt nicht als verdiente Tokens. */
    public void giveManually(String actor, Player target, int amount) {
        giveTokens(target, amount);
        roundLog.log("SPONSOR", actor + " gibt " + target.getName() + " " + amount + " Token");
    }

    /** Entfernt alle Sponsor Tokens aus dem Inventar eines Spielers. */
    public int clearTokens(Player player) {
        int removed = 0;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (isToken(contents[i])) {
                removed += contents[i].getAmount();
                player.getInventory().setItem(i, null);
            }
        }
        return removed;
    }

    /** Verdiente Tokens eines Spielers in dieser Runde. */
    public int earnedTotal(java.util.UUID uuid) {
        int[] state = perPlayer.get(uuid);
        return state != null ? state[1] : 0;
    }

    // ==================== Vergabe-Tick ====================

    private void tick() {
        if (session.phase() != GamePhase.RUNNING || !tokensEnabled()) {
            return;
        }
        Duration elapsed = virtualTime.elapsed();
        int due = TokenSchedule.dueWaves(elapsed,
                config.get(settings.sponsorTokenFirstAfter),
                config.get(settings.sponsorTokenInterval));
        if (due <= 0) {
            return;
        }
        int amountPerWave = config.get(settings.sponsorTokenAmount);
        int maxPerPlayer = config.get(settings.sponsorTokenMaxPerPlayer);
        boolean onlyAlive = config.get(settings.sponsorTokenOnlyAlive);
        boolean changed = false;

        for (var participant : session.participants().all()) {
            if (onlyAlive && !participant.isAlive()) {
                continue;
            }
            Player player = Bukkit.getPlayer(participant.uuid());
            if (player == null) {
                continue; // offline — Wellen werden beim Rejoin nachgeholt
            }
            int[] state = perPlayer.computeIfAbsent(participant.uuid(), k -> new int[]{0, 0});
            int pending = TokenSchedule.pendingTokens(due, state[0], amountPerWave,
                    state[1], maxPerPlayer);
            state[0] = due;
            if (pending <= 0) {
                changed = true;
                continue;
            }
            state[1] += pending;
            changed = true;
            giveTokens(player, pending);
            roundLog.log("SPONSOR", participant.lastKnownName() + " erhält " + pending
                    + " Token (gesamt " + state[1] + ")");
            if (config.get(settings.sponsorTokenAnnouncePersonal)) {
                announcements.sendTo(player, settings.textSponsorTokenEarned,
                        Map.of("amount", String.valueOf(pending)), Style.CHAT, Style.ACTIONBAR);
                sounds.playTo(player, settings.soundSponsorTokenEarned);
            }
            if (config.get(settings.sponsorTokenBroadcastMilestones) && state[1] % 5 == 0) {
                Bukkit.broadcast(com.hungergames.platform.Messages.info(
                        participant.lastKnownName() + " hat bereits " + state[1]
                                + " Sponsor Tokens gesammelt."));
            }
        }
        if (changed) {
            runtimeStore.saveSponsorTokenState(perPlayer);
        }
    }

    // ==================== Tod / Rundenreset ====================

    /** Tokens beim Tod: clear-on-elimination hat Vorrang vor drop-on-death. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        if (!session.isWhitelisted(event.getPlayer().getUniqueId())) {
            return;
        }
        boolean clear = config.get(settings.sponsorTokenClearOnElimination);
        boolean drop = config.get(settings.sponsorTokenDropOnDeath);
        if (clear || !drop) {
            event.getDrops().removeIf(SponsorTokenService::isToken);
        }
    }

    @EventHandler
    public void onPhaseChange(GamePhaseChangeEvent event) {
        if (event.getNewPhase() == GamePhase.NOT_INITIALIZED
                && config.get(settings.sponsorTokenClearOnRoundReset)) {
            perPlayer.clear();
            for (Player player : Bukkit.getOnlinePlayers()) {
                clearTokens(player);
            }
            roundLog.log("SPONSOR", "Token-Zähler und Inventar-Tokens zurückgesetzt (Rundenreset)");
        }
    }

    /**
     * Nachzügler-Bereinigung: Offline eliminierte Spieler (clear-on-elimination)
     * und Tokens aus der Vorrunde (clear-on-round-reset) beim Join aufräumen —
     * beides konnte im Offline-Inventar überleben.
     */
    @EventHandler
    public void onJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        Player player = event.getPlayer();
        boolean eliminatedClear = config.get(settings.sponsorTokenClearOnElimination)
                && session.isWhitelisted(player.getUniqueId())
                && !session.participants().isAlive(player.getUniqueId());
        boolean preGameClear = config.get(settings.sponsorTokenClearOnRoundReset)
                && session.phase().isPreGame();
        if (!eliminatedClear && !preGameClear) {
            return;
        }
        int removed = clearTokens(player);
        if (removed > 0) {
            roundLog.log("SPONSOR", removed + " Token bei " + player.getName()
                    + " beim Join entfernt (" + (eliminatedClear ? "eliminiert" : "Vorrunde") + ")");
        }
    }
}
