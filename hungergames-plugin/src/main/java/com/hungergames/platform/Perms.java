package com.hungergames.platform;

import org.bukkit.permissions.Permissible;

/** Zentrale Permission-Konstanten des Plugins. */
public final class Perms {

    public static final String ADMIN = "hungergames.admin";
    public static final String GAMEMASTER = "hungergames.gamemaster";
    public static final String PROTECTION_BYPASS = "hungergames.protection.bypass";

    private Perms() {
    }

    public static boolean isAdmin(Permissible who) {
        return who.hasPermission(ADMIN);
    }

    public static boolean isGamemaster(Permissible who) {
        return who.hasPermission(GAMEMASTER);
    }

    /** Darf die Admin-GUI öffnen (Admin ODER Spielmacher). */
    public static boolean canUseAdminGui(Permissible who) {
        return isAdmin(who) || isGamemaster(who);
    }
}
