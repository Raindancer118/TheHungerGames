package com.hungergames.platform;

import com.hungergames.api.service.TeamOperationResult;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/** Zentrale Nutzer-Nachrichten (Adventure, deutsch). */
public final class Messages {

    public static final Component PREFIX =
            Component.text("[HG] ", NamedTextColor.GOLD);

    private Messages() {
    }

    public static Component ok(String text) {
        return PREFIX.append(Component.text(text, NamedTextColor.GREEN));
    }

    public static Component error(String text) {
        return PREFIX.append(Component.text(text, NamedTextColor.RED));
    }

    public static Component info(String text) {
        return PREFIX.append(Component.text(text, NamedTextColor.GRAY));
    }

    /** @return deutschsprachige Begründung für ein Team-Operationsergebnis. */
    public static String describe(TeamOperationResult result) {
        return switch (result) {
            case SUCCESS -> "Erledigt.";
            case TEAM_NOT_FOUND -> "Dieses Team existiert nicht.";
            case NAME_TAKEN -> "Der Teamname ist bereits vergeben.";
            case COLOR_TAKEN -> "Diese Farbe ist bereits vergeben.";
            case TEAM_FULL -> "Das Team ist voll.";
            case PHASE_LOCKED -> "Teams sind in dieser Phase gesperrt.";
            case PLAYER_NOT_WHITELISTED -> "Der Spieler steht nicht auf der Turnier-Whitelist.";
            case ALREADY_MEMBER -> "Bereits Mitglied dieses Teams.";
            case NOT_A_MEMBER -> "Kein Mitglied eines Teams.";
            case SWITCHING_DISABLED -> "Teamwechsel ist deaktiviert.";
            case TEAM_LIMIT_REACHED -> "Die maximale Teamanzahl ist erreicht.";
            case TEAMS_DISABLED -> "Teams sind deaktiviert — jeder spielt für sich (Solo-Modus).";
        };
    }
}
