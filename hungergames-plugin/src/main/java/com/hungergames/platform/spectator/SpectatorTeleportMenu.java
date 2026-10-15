package com.hungergames.platform.spectator;

import com.hungergames.game.GameSession;
import com.hungergames.platform.gui.Items;
import com.hungergames.platform.gui.Menu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.UUID;

/**
 * Teleport-Ziele für Spectator: ausschließlich lebende, online befindliche
 * Teilnehmer — eliminierte oder offline Spieler erscheinen nicht.
 */
final class SpectatorTeleportMenu extends Menu {

    private final GameSession session;
    private final SpectatorService spectatorService;

    SpectatorTeleportMenu(Player viewer, GameSession session, SpectatorService spectatorService) {
        super(viewer, 6, Component.text("Lebende Spieler"));
        this.session = session;
        this.spectatorService = spectatorService;
    }

    @Override
    protected void build() {
        int slot = 0;
        for (UUID alive : session.participants().alive()) {
            if (slot >= 53) {
                break;
            }
            Player target = Bukkit.getPlayer(alive);
            if (target == null) {
                continue; // offline Lebende sind kein gültiges Teleportziel
            }

            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setOwningPlayer(target);
            meta.displayName(Component.text(target.getName(), NamedTextColor.GREEN));
            String teamName = session.teamRegistry().teamOf(alive)
                    .map(team -> team.name())
                    .orElse("Kein Team");
            meta.lore(java.util.List.of(
                    Component.text(teamName, NamedTextColor.GRAY),
                    Component.text("Klick: hinspringen", NamedTextColor.YELLOW)));
            head.setItemMeta(meta);

            set(slot++, head, click -> {
                spectatorService.teleportTo(viewer, alive);
                viewer.closeInventory();
            });
        }

        if (slot == 0) {
            set(22, Items.simple(Material.BARRIER, "Keine Ziele",
                    "Kein lebender Teilnehmer ist online."));
        }
        set(53, Items.close(), click -> viewer.closeInventory());
    }
}
