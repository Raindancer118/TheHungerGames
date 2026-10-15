package com.hungergames.platform.gui.admin;

import com.hungergames.GameState;
import com.hungergames.HungerGamesPlugin;
import com.hungergames.api.model.GamePhase;
import com.hungergames.platform.Perms;
import com.hungergames.platform.gui.Items;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Gibt Admins (bzw. Spielmachern) im Kreativ-Modus automatisch zwei Hotbar-Items:
 * einen Kompass für das Gesamtmenü ({@code /hg admin} → {@link AdminMainMenu}) und
 * ein Schnellmenü für die Spielsteuerung ({@link GameControlMenu}).
 *
 * <p>Die Items werden per PDC erkannt (nie am Material allein), beim Betreten des
 * Kreativ-Modus vergeben, beim Verlassen wieder eingesammelt und lassen sich nicht
 * fallen lassen. Rechtsklick öffnet exakt dieselben Menüs, die auch die Commands
 * bzw. {@code /hg admin} öffnen — damit lösen die Buttons dieselben Flows aus.</p>
 */
public final class AdminHotbarService implements Listener {

    private static final NamespacedKey ITEM_KEY =
            new NamespacedKey(JavaPlugin.getPlugin(HungerGamesPlugin.class), "admin_hotbar_item");
    private static final String MAIN = "main";
    private static final String QUICK = "quick";
    private static final String START = "start";

    private static final int SLOT_QUICK = 7;
    private static final int SLOT_MAIN = 8;
    private static final int SLOT_START = 4;

    private final HungerGamesPlugin plugin;
    private final AdminServices services;

    public AdminHotbarService(HungerGamesPlugin plugin, AdminServices services) {
        this.plugin = plugin;
        this.services = services;
    }

    /** Vergibt/entfernt die Items bei bereits online Spielern (z. B. /hg-reload). */
    public void syncOnlinePlayers() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            apply(player);
        }
    }

    // === Vergabe / Entzug ===

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        apply(event.getPlayer());
    }

    @EventHandler
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        Player player = event.getPlayer();
        // Der Modus ist erst nach dem Event gesetzt — einen Tick später auswerten.
        plugin.getServer().getScheduler().runTask(plugin, () -> apply(player));
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        // Nach dem Teleport auswerten (u. a. Tp in die Glaslobby bei /init).
        plugin.getServer().getScheduler().runTask(plugin, () -> apply(player));
    }

    @EventHandler
    public void onPhaseChange(com.hungergames.api.event.GamePhaseChangeEvent event) {
        // Bei READY das Start-Item an mitspielende Admins geben, bei RUNNING wieder weg.
        plugin.getServer().getScheduler().runTask(plugin, this::syncOnlinePlayers);
    }

    private void apply(Player player) {
        removeItems(player);
        PlayerInventory inv = player.getInventory();
        if (shouldHave(player)) {
            inv.setItem(SLOT_QUICK, quickItem());
            inv.setItem(SLOT_MAIN, mainItem());
        }
        if (shouldHaveStartItem(player)) {
            inv.setItem(SLOT_START, startItem());
        }
    }

    /**
     * Ein mitspielender Admin, der auf seiner Plattform am Füllhorn steht und
     * starten kann (Phase READY), bekommt ein Start-Item — bequem starten ohne
     * Menü. Verschwindet automatisch, sobald das Spiel läuft.
     */
    private boolean shouldHaveStartItem(Player player) {
        return Perms.canUseAdminGui(player)
                && services.gameControl.canStart()
                && services.session.participants().isAlive(player.getUniqueId());
    }

    /** Admins/Spielmacher im Kreativ-Modus ODER in der Glaslobby bekommen die Items. */
    private boolean shouldHave(Player player) {
        if (!Perms.canUseAdminGui(player)) {
            return false;
        }
        return player.getGameMode() == GameMode.CREATIVE || isInLobby(player.getLocation());
    }

    private boolean isInLobby(Location loc) {
        if (services.session.phase() != GamePhase.LOBBY) {
            return false;
        }
        Location lobby = GameState.getInstance().getLobbyCenter();
        if (lobby == null || lobby.getWorld() == null || loc.getWorld() == null
                || !lobby.getWorld().equals(loc.getWorld())) {
            return false;
        }
        double halfX = services.config.get(services.settings.lobbyWidth) / 2.0 + 3;
        double halfZ = services.config.get(services.settings.lobbyDepth) / 2.0 + 3;
        double windowY = services.config.get(services.settings.lobbyHeight) + 8;
        return Math.abs(loc.getX() - lobby.getX()) <= halfX
                && Math.abs(loc.getZ() - lobby.getZ()) <= halfZ
                && Math.abs(loc.getY() - lobby.getY()) <= windowY;
    }

    private void removeItems(Player player) {
        PlayerInventory inv = player.getInventory();
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length; i++) {
            if (tagOf(contents[i]) != null) {
                inv.setItem(i, null);
            }
        }
    }

    // === Interaktion ===

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        String tag = tagOf(event.getItem());
        if (tag == null) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!Perms.canUseAdminGui(player)) {
            removeItems(player);
            return;
        }
        if (MAIN.equals(tag)) {
            // Identisch zu /hg admin
            new AdminMainMenu(player, services).open();
        } else if (QUICK.equals(tag)) {
            new GameControlMenu(player, services).open();
        } else if (START.equals(tag)) {
            // Exakt derselbe Flow wie der Start-Button im Menü / /start.
            if (services.gameControl.canStart()) {
                services.gameControl.start(player);
            } else {
                player.sendMessage(com.hungergames.platform.Messages.error(
                        "Jetzt nicht startbar (Phase " + services.session.phase() + ")."));
            }
            // Item verschwindet nach dem Start ohnehin über den Phasenwechsel.
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (tagOf(event.getItemDrop().getItemStack()) != null) {
            event.setCancelled(true);
        }
    }

    // === Item-Bau & Erkennung ===

    private static String tagOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        return pdc.get(ITEM_KEY, PersistentDataType.STRING);
    }

    private ItemStack mainItem() {
        ItemStack item = Items.of(Material.COMPASS,
                Component.text("Admin-Menü", NamedTextColor.AQUA),
                Component.text("Rechtsklick: Gesamtübersicht", NamedTextColor.GRAY),
                Component.text("(wie /hg admin)", NamedTextColor.DARK_GRAY));
        return tagged(item, MAIN);
    }

    private ItemStack quickItem() {
        ItemStack item = Items.of(Material.NETHER_STAR,
                Component.text("Schnellmenü", NamedTextColor.GOLD),
                Component.text("Rechtsklick: Spielsteuerung", NamedTextColor.GRAY),
                Component.text("Init · Startsequenz · Start", NamedTextColor.DARK_GRAY));
        return tagged(item, QUICK);
    }

    private ItemStack startItem() {
        ItemStack item = Items.of(Material.LIME_CONCRETE,
                Component.text("Spiel starten", NamedTextColor.GREEN),
                Component.text("Rechtsklick: Countdown & Start", NamedTextColor.GRAY),
                Component.text("(wie /start)", NamedTextColor.DARK_GRAY));
        return tagged(item, START);
    }

    private static ItemStack tagged(ItemStack item, String value) {
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(ITEM_KEY, PersistentDataType.STRING, value);
        item.setItemMeta(meta);
        return item;
    }
}
