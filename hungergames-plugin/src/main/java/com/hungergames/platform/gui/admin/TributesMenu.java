package com.hungergames.platform.gui.admin;

import com.hungergames.api.model.Participant;
import com.hungergames.platform.Messages;
import com.hungergames.platform.gui.Items;
import com.hungergames.platform.gui.Menu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Tribut-Verwaltung (Admin): Whitelist pflegen, Wiederbeleben, Übersicht.
 * Linksklick belebt eliminierte Tribute wieder, Shift-Rechtsklick entfernt
 * einen Spieler (mit Bestätigung) aus dem Turnier.
 */
public final class TributesMenu extends Menu {

    private static final int PAGE_SIZE = 45;

    private final AdminServices services;
    private final Runnable onBack;
    private int page;

    public TributesMenu(Player viewer, AdminServices services, Runnable onBack) {
        super(viewer, 6, Component.text("Spieler & Tribute"));
        this.services = services;
        this.onBack = onBack;
    }

    @Override
    protected void build() {
        List<Participant> all = new ArrayList<>(services.session.participants().all());
        all.sort(Comparator.comparing((Participant p) -> !p.isAlive())
                .thenComparing(p -> p.lastKnownName().toLowerCase(java.util.Locale.ROOT)));

        int totalPages = Math.max(1, (all.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.min(page, totalPages - 1);

        int start = page * PAGE_SIZE;
        for (int i = start; i < Math.min(start + PAGE_SIZE, all.size()); i++) {
            addParticipant(i - start, all.get(i));
        }

        if (page > 0) {
            set(45, Items.simple(Material.ARROW, "Vorherige Seite"),
                    click -> {
                        page--;
                        refresh();
                    });
        }
        if (page < totalPages - 1) {
            set(53, Items.simple(Material.ARROW, "Nächste Seite"),
                    click -> {
                        page++;
                        refresh();
                    });
        }

        set(47, Items.simple(Material.EMERALD, "Spieler registrieren",
                        "Fügt einen Spieler als Tribut hinzu"),
                click -> askForName());
        set(49, Items.back(), click -> onBack.run());
    }

    private void addParticipant(int slot, Participant participant) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        meta.setOwningPlayer(Bukkit.getOfflinePlayer(participant.uuid()));
        meta.displayName(Component.text(participant.lastKnownName(),
                        participant.isAlive() ? NamedTextColor.GREEN : NamedTextColor.RED)
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(participant.isAlive() ? "Lebend" : "Eliminiert",
                participant.isAlive() ? NamedTextColor.GREEN : NamedTextColor.RED));
        participant.teamId().ifPresent(teamId ->
                services.session.teamRegistry().team(teamId).ifPresent(team ->
                        lore.add(Component.text("Team: " + team.name(), team.color().namedTextColor()))));
        boolean online = Bukkit.getPlayer(participant.uuid()) != null;
        lore.add(Component.text(online ? "Online" : "Offline", NamedTextColor.DARK_GRAY));
        lore.add(Component.empty());
        if (!participant.isAlive()) {
            lore.add(Component.text("Linksklick: wiederbeleben", NamedTextColor.AQUA));
        }
        lore.add(Component.text("Shift-Rechtsklick: aus Turnier entfernen", NamedTextColor.AQUA));
        meta.lore(lore.stream()
                .map(line -> line.decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false))
                .toList());
        head.setItemMeta(meta);

        set(slot, head, click -> {
            if (click.isShiftClick() && click.isRightClick()) {
                new ConfirmMenu(viewer, participant.lastKnownName() + " entfernen",
                        List.of("Entfernt den Spieler aus Turnier-Whitelist",
                                "und seinem Team."),
                        () -> {
                            services.session.whitelistRemove(participant.uuid());
                            services.roundLog.log("ADMIN", viewer.getName() + " entfernt "
                                    + participant.lastKnownName() + " aus dem Turnier");
                            viewer.sendMessage(Messages.ok(participant.lastKnownName()
                                    + " aus dem Turnier entfernt."));
                            open();
                        }, this::open).open();
            } else if (click.isLeftClick() && !participant.isAlive()) {
                revive(participant);
            }
        });
    }

    private void revive(Participant participant) {
        if (!services.session.revive(participant.uuid())) {
            viewer.sendMessage(Messages.error(participant.lastKnownName() + " ist nicht eliminiert."));
            return;
        }
        services.roundLog.log("ADMIN", viewer.getName() + " belebt "
                + participant.lastKnownName() + " wieder");
        viewer.sendMessage(Messages.ok(participant.lastKnownName() + " ist zurück im Spiel."));
        Player player = Bukkit.getPlayer(participant.uuid());
        if (player != null) {
            player.setGameMode(GameMode.SURVIVAL);
            player.sendMessage(Messages.ok("Das Kapitol gewährt dir eine zweite Chance!"));
        }
        refresh();
    }

    private void askForName() {
        services.anvilInput.open(viewer, Component.text("Spielername"), "",
                input -> {
                    String name = input.trim();
                    if (name.isEmpty()) {
                        open();
                        return;
                    }
                    services.nameResolver.resolve(name).whenComplete((resolved, error) ->
                            Bukkit.getScheduler().runTask(services.plugin, () -> {
                                if (resolved == null) {
                                    viewer.sendMessage(Messages.error(
                                            "Kein Account namens \"" + name + "\" gefunden."));
                                } else if (services.session.whitelistAdd(resolved.uuid(), resolved.name())) {
                                    services.roundLog.log("ADMIN", viewer.getName()
                                            + " registriert " + resolved.name() + " als Tribut");
                                    viewer.sendMessage(Messages.ok(resolved.name()
                                            + " als Tribut registriert."));
                                } else {
                                    viewer.sendMessage(Messages.info(resolved.name()
                                            + " ist bereits registriert."));
                                }
                                open();
                            }));
                },
                this::open);
    }
}
