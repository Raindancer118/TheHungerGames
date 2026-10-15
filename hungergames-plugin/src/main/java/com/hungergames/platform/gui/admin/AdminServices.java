package com.hungergames.platform.gui.admin;

import com.hungergames.game.GameSession;
import com.hungergames.game.TeamRules;
import com.hungergames.game.config.ConfigurationService;
import com.hungergames.platform.NameResolver;
import com.hungergames.platform.VirtualTime;
import com.hungergames.platform.announce.AnnouncementService;
import com.hungergames.platform.border.BorderService;
import com.hungergames.platform.config.HgSettings;
import com.hungergames.platform.game.GameControlService;
import com.hungergames.platform.gui.AnvilInput;
import com.hungergames.platform.gui.config.ConfigCategoryMenu;
import com.hungergames.platform.log.RoundLogService;
import com.hungergames.platform.sound.SoundService;
import com.hungergames.platform.spectator.SpectatorService;
import org.bukkit.plugin.Plugin;

import java.util.function.Supplier;

/**
 * Bündel aller Services, die die Admin-GUIs brauchen — statt zwölf
 * Konstruktor-Parametern pro Menü. Wird beim Plugin-Start einmal befüllt;
 * Features späterer Ausbaustufen hängen sich hier an.
 */
public final class AdminServices {

    public final Plugin plugin;
    public final GameSession session;
    public final ConfigurationService config;
    public final HgSettings settings;
    public final AnvilInput anvilInput;
    public final SpectatorService spectator;
    public final BorderService border;
    public final NameResolver nameResolver;
    public final Supplier<TeamRules> teamRules;
    public final ConfigCategoryMenu.ChangeInterceptor borderGuard;
    public final GameControlService gameControl;
    public final SoundService sounds;
    public final AnnouncementService announcements;
    public final RoundLogService roundLog;
    public final VirtualTime virtualTime;

    /** Wird nach der Konstruktion gesetzt (Ausbaustufe Spielmacher). */
    public com.hungergames.platform.gamemaster.GamemasterService gamemaster;
    /** Wird nach der Konstruktion gesetzt (Deop/Re-Op mitspielender Admins). */
    public com.hungergames.platform.game.OpTrackerService opTracker;
    /** Wird nach der Konstruktion gesetzt (Deathmatch). */
    public com.hungergames.platform.deathmatch.DeathmatchService deathmatch;
    /** Wird nach der Konstruktion gesetzt (Supply Drops). */
    public com.hungergames.platform.supplydrop.SupplyDropService supplyDrops;
    /** Wird nach der Konstruktion gesetzt (Sponsor Tokens). */
    public com.hungergames.platform.sponsor.SponsorTokenService sponsorTokens;
    /** Wird nach der Konstruktion gesetzt (Sponsor Beacons). */
    public com.hungergames.platform.sponsor.SponsorBeaconService sponsorBeacons;
    /** Wird nach der Konstruktion gesetzt (LootTable-Editor). */
    public com.hungergames.platform.loot.LootTableRepository lootTables;
    /** Wird nach der Konstruktion gesetzt (Preflight-Check). */
    public com.hungergames.platform.game.PreflightCheckService preflight;
    /** Wird nach der Konstruktion gesetzt (Mannequin-Test-Simulation). */
    public com.hungergames.platform.sim.MannequinSimService mannequinSim;
    /** Wird nach der Konstruktion gesetzt (visuelle Effekte / Partikel). */
    public com.hungergames.platform.effect.EffectService effects;
    /** Wird nach der Konstruktion gesetzt (Monster-Wellen). */
    public com.hungergames.platform.event.MonsterWaveService monsterWaves;

    public AdminServices(Plugin plugin, GameSession session, ConfigurationService config,
                         HgSettings settings, AnvilInput anvilInput, SpectatorService spectator,
                         BorderService border, NameResolver nameResolver,
                         Supplier<TeamRules> teamRules,
                         ConfigCategoryMenu.ChangeInterceptor borderGuard,
                         GameControlService gameControl, SoundService sounds,
                         AnnouncementService announcements, RoundLogService roundLog,
                         VirtualTime virtualTime) {
        this.plugin = plugin;
        this.session = session;
        this.config = config;
        this.settings = settings;
        this.anvilInput = anvilInput;
        this.spectator = spectator;
        this.border = border;
        this.nameResolver = nameResolver;
        this.teamRules = teamRules;
        this.borderGuard = borderGuard;
        this.gameControl = gameControl;
        this.sounds = sounds;
        this.announcements = announcements;
        this.roundLog = roundLog;
        this.virtualTime = virtualTime;
    }
}
