package com.hungergames.stagerunner;

import com.hungergames.api.model.GamePhase;
import com.hungergames.GameState;
import com.hungergames.loot.LootManager;
import com.hungergames.HungerGamesPlugin;
import com.hungergames.player.WhitelistManager;
import com.hungergames.utils.ConfigManager;
import com.hungergames.utils.Sender;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.Lightable;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

/**
 * Handles the startup/engaging sequence.
 * Teleports players to underground positions, activates lights,
 * and levitates players through tubes to their platforms.
 */
public class StartupRunner {

    /** Weltzeit (Sonnenuntergang) der Startsequenz — /start setzt sie erneut. */
    public static final long STARTUP_WORLD_TIME = 22500;

    private final ConfigManager config = ConfigManager.getInstance();
    private final HungerGamesPlugin plugin = JavaPlugin.getPlugin(HungerGamesPlugin.class);

    private UUID adminUuid;
    private int playersArrived = 0;
    private int totalPlayers = 0;

    // Stores original block types for platform centers
    private final Map<Location, Material> originalPlatformBlocks = new HashMap<>();

    // Standpositionen, um die unterirdisch Barrier-Ringe gelegt wurden
    private final List<Location> undergroundRingPositions = new ArrayList<>();

    /**
     * Executes the startup sequence.
     * 
     * @param adminUuid UUID of the admin who triggered the sequence
     */
    public void execute(UUID adminUuid) {
        this.adminUuid = adminUuid;

        Player admin = Bukkit.getPlayer(adminUuid);
        if (admin == null) {
            Sender.error("Admin player not found!");
            return;
        }

        GameState gameState = GameState.getInstance();
        if (!gameState.tryTransition(GamePhase.STARTUP)) {
            admin.sendMessage("§cStartsequenz abgebrochen: Phasenwechsel nach STARTUP nicht möglich"
                    + " (aktuelle Phase: " + gameState.getCurrentPhase() + ").");
            return;
        }

        Sender.log("Starting engaging sequence...");

        // Step 1: Get online players from whitelist
        List<Player> onlinePlayers = getOnlineWhitelistedPlayers();
        totalPlayers = onlinePlayers.size();

        if (totalPlayers == 0) {
            // Reine Test-Simulation: keine echten Spieler online, aber Mannequin-
            // Tribute registriert. Ohne Röhren-/Levitationssequenz direkt nach
            // READY überführen; die Attrappen werden per Phasen-Hook auf die
            // Plattformen gesetzt (MannequinSimService).
            if (gameState.registeredTributeCount() >= 2) {
                admin.sendMessage("§7Keine echten Spieler online — Test-Simulation: Tribute werden "
                        + "direkt platziert (keine Röhrensequenz).");
                onAllPlayersArrived(admin);
                return;
            }
            admin.sendMessage("§cNo whitelisted players are online!");
            gameState.setCurrentPhase(GamePhase.LOBBY);
            return;
        }

        admin.sendMessage("§7Found §e" + totalPlayers + "§7 players online");

        // Step 2: Sort players by team (partners next to each other)
        List<Player> sortedPlayers = sortPlayersByTeam(onlinePlayers);

        // Step 3: Get platform positions
        List<Location> platformPositions = gameState.getPlatformPositions();
        if (platformPositions.isEmpty()) {
            admin.sendMessage("§cNo platform positions found! Did /init complete successfully?");
            gameState.setCurrentPhase(GamePhase.LOBBY);
            return;
        }

        // Step 4: Teleport players to underground positions
        admin.sendMessage("§7Teleporting players to underground positions...");
        teleportPlayersToUnderground(sortedPlayers, platformPositions);

        // Ambiance im Warteraum (z. B. der Countdown-Song aus hgpack) — läuft für
        // alle gleichzeitig an, sobald sie unten stehen, nicht erst beim Abschuss.
        plugin.soundService().playTo(sortedPlayers, plugin.settings().soundStartupUnderground);

        // Step 5: Activate redstone lamps (dramatic effect)
        admin.sendMessage("§7Activating lights...");
        int lampDelay = config.getStartupLampDelay();
        int levitationStartDelay = config.getStartupLevitationStartDelay();

        activateLamps(GameState.getInstance().getArenaCenter());

        // Step 5b: Clear all dropped items in the arena
        clearDroppedItems();
        admin.sendMessage("§7Cleared dropped items from the arena");

        // Step 6: Start levitation sequence after delay
        new BukkitRunnable() {
            @Override
            public void run() {
                admin.sendMessage("§7Starting levitation sequence...");
                startLevitationSequence(sortedPlayers, platformPositions, admin);
            }
        }.runTaskLater(plugin, levitationStartDelay);
    }

    /**
     * Clears all dropped items (entities) from the arena world.
     * Called before levitation to ensure a clean game start.
     */
    private void clearDroppedItems() {
        Location arenaCenter = GameState.getInstance().getArenaCenter();
        if (arenaCenter == null || arenaCenter.getWorld() == null) {
            return;
        }

        World world = arenaCenter.getWorld();
        int removedCount = 0;

        // Remove all item entities from the world
        for (org.bukkit.entity.Entity entity : world.getEntities()) {
            if (entity instanceof org.bukkit.entity.Item) {
                entity.remove();
                removedCount++;
            }
        }

        if (removedCount > 0) {
            Sender.log("Cleared " + removedCount + " dropped items from the arena");
        }
    }

    /**
     * Gets all online players who are on the whitelist.
     */
    private List<Player> getOnlineWhitelistedPlayers() {
        List<Player> players = new ArrayList<>();
        WhitelistManager whitelist = WhitelistManager.getInstance();

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (whitelist.isWhitelisted(player.getName())) {
                players.add(player);
            }
        }

        return players;
    }

    /**
     * Sorts players so that team partners are adjacent.
     */
    private List<Player> sortPlayersByTeam(List<Player> players) {
        WhitelistManager whitelist = WhitelistManager.getInstance();
        List<String> sortedNames = whitelist.getPlayersSortedByTeam();

        List<Player> sorted = new ArrayList<>();
        for (String name : sortedNames) {
            for (Player player : players) {
                if (player.getName().equalsIgnoreCase(name)) {
                    sorted.add(player);
                    break;
                }
            }
        }

        // Add any players not in whitelist at the end
        for (Player player : players) {
            if (!sorted.contains(player)) {
                sorted.add(player);
            }
        }

        return sorted;
    }

    /**
     * Teleports players to positions below their assigned tubes.
     */
    private void teleportPlayersToUnderground(List<Player> players, List<Location> platformPositions) {
        List<Location> spawnPositions = GameState.getInstance().getUndergroundSpawnPositions();

        if (spawnPositions.isEmpty()) {
            Sender.error("No underground spawn positions found! Using fallback offset.");
            // Fallback (should not happen if Preflight ran correctly)
            Location arenaCenter = GameState.getInstance().getArenaCenter();
            int undergroundOffset = 15; // Default fallback

            for (int i = 0; i < players.size() && i < platformPositions.size(); i++) {
                Player player = players.get(i);
                Location platformPos = platformPositions.get(i);
                Location undergroundPos = platformPos.clone();
                undergroundPos.setY(arenaCenter.getY() - undergroundOffset);
                undergroundPos.add(0.5, 0, 0.5);
                teleportPlayer(player, undergroundPos, arenaCenter);
                placeBarrierRing(undergroundPos);
                undergroundRingPositions.add(undergroundPos);
            }
            return;
        }

        Location arenaCenter = GameState.getInstance().getArenaCenter();

        // Use pre-calculated spawn positions
        // Since players are sorted by team and positions are in circle order,
        // assigning i-th player to i-th position keeps teams together.
        for (int i = 0; i < players.size() && i < spawnPositions.size(); i++) {
            Player player = players.get(i);
            Location spawnPos = spawnPositions.get(i);

            // Spawn pos is already centered on the floor
            teleportPlayer(player, spawnPos, arenaCenter);

            // Barrier-Ring auf Kopfhöhe statt Bewegungs-Freeze: Weglaufen ist
            // blockiert, Levitation durch die freie Mitte bleibt möglich.
            placeBarrierRing(spawnPos);
            undergroundRingPositions.add(spawnPos);

            Sender.debug("Teleported " + player.getName() + " to underground position " + i);
        }
    }

    private void teleportPlayer(Player player, Location target, Location lookAt) {
        // Face toward center
        Location actualTarget = target.clone();
        if (lookAt != null) {
            actualTarget.setDirection(lookAt.toVector().subtract(actualTarget.toVector()));
        }

        player.teleport(actualTarget);
        player.setGameMode(GameMode.ADVENTURE);

        // Apply saturation to prevent hunger
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.SATURATION,
                Integer.MAX_VALUE,
                0,
                false, false, true));

        // Prevent jumping to aid in freezing sensation
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.JUMP_BOOST,
                Integer.MAX_VALUE,
                -128, // Negative prevents jumping
                false, false, false));
    }

    /**
     * Starts the levitation sequence - one player at a time.
     * Sets the world time to 22500 (sunset) for dramatic effect.
     */
    private void startLevitationSequence(List<Player> players, List<Location> platformPositions, Player admin) {
        int playerDelay = config.getStartupPlayerLevitationDelay();
        int levitationAmplifier = config.getStartupLevitationAmplifier();

        // Set world time to sunset for dramatic levitation sequence
        Location arenaCenter = GameState.getInstance().getArenaCenter();
        if (arenaCenter != null && arenaCenter.getWorld() != null) {
            arenaCenter.getWorld().setTime(STARTUP_WORLD_TIME);
            Sender.log("Set world time to " + STARTUP_WORLD_TIME + " for levitation sequence");
        }

        playersArrived = 0;
        originalPlatformBlocks.clear();

        for (int i = 0; i < players.size() && i < platformPositions.size(); i++) {
            final int index = i;
            final Player player = players.get(i);
            final Location platformPos = platformPositions.get(i);

            // Delay each player's levitation start
            new BukkitRunnable() {
                @Override
                public void run() {
                    // Remove platform center to allow passage (saves original block)
                    removePlatformCenter(platformPos);

                    // Apply levitation effect
                    player.addPotionEffect(new PotionEffect(
                            PotionEffectType.LEVITATION,
                            20 * 30, // 30 seconds max (will be removed when arrived)
                            levitationAmplifier,
                            false, false, true));

                    // Konfigurierbarer Abschuss-Sound + Partikel (leer = aus)
                    plugin.soundService().playTo(player, plugin.settings().soundStartupLaunch);
                    plugin.effectService().playAt(player.getLocation(), plugin.settings().effectStartupLaunch);

                    Sender.debug("Started levitation for " + player.getName());

                    // Monitor player's arrival (will restore original block)
                    monitorPlayerArrival(player, platformPos, admin);
                }
            }.runTaskLater(plugin, (long) i * playerDelay);
        }
    }

    /**
     * Removes the center block of a platform to allow player passage.
     */
    private void removePlatformCenter(Location platformPos) {
        World world = platformPos.getWorld();
        if (world == null)
            return;

        // Save original block type before removing
        Location centerBlock = platformPos.clone();
        Material originalBlock = world.getBlockAt(centerBlock).getType();
        originalPlatformBlocks.put(platformPos, originalBlock);

        // Remove center block
        world.getBlockAt(centerBlock).setType(Material.AIR);

        Sender.debug("Removed platform center at " + formatLocation(centerBlock) + " (was: " + originalBlock + ")");
    }

    /**
     * Seals the platform center after player arrival.
     */
    private void sealPlatformCenter(Location platformPos) {
        World world = platformPos.getWorld();
        if (world == null)
            return;

        // Restore original block type
        Material originalBlock = originalPlatformBlocks.getOrDefault(platformPos, Material.STONE);
        Location centerBlock = platformPos.clone();
        world.getBlockAt(centerBlock).setType(originalBlock);

        Sender.debug("Sealed platform center at " + formatLocation(centerBlock) + " (restored: " + originalBlock + ")");
    }

    /**
     * Monitors a player's vertical position and triggers platform sealing when they
     * arrive.
     */
    private void monitorPlayerArrival(Player player, Location platformPos, Player admin) {
        double targetY = platformPos.getY() + 1.5; // Slightly above platform

        new BukkitRunnable() {
            int ticksWaited = 0;
            final int maxTicks = 20 * 30; // 30 second timeout

            @Override
            public void run() {
                // Disconnect während der Röhrensequenz: Plattform versiegeln
                // und als angekommen zählen — sonst bleibt STARTUP für immer
                // stehen, weil onAllPlayersArrived nie erreicht wird.
                if (!player.isOnline()) {
                    Sender.warning(player.getName() + " hat während der Startsequenz die "
                            + "Verbindung verloren — Plattform wird versiegelt.");
                    sealPlatformCenter(platformPos);
                    playersArrived++;
                    if (playersArrived >= totalPlayers) {
                        onAllPlayersArrived(admin);
                    }
                    this.cancel();
                    return;
                }

                // Freeze horizontal movement (Velocity Cancellation)
                org.bukkit.util.Vector velocity = player.getVelocity();
                player.setVelocity(new org.bukkit.util.Vector(0, velocity.getY(), 0));

                ticksWaited++; // Increment by 1 since we run every tick

                // Check if player reached platform height
                if (player.getLocation().getY() >= targetY) {
                    // Seal platform (restores original block)
                    sealPlatformCenter(platformPos);

                    // Remove levitation
                    player.removePotionEffect(PotionEffectType.LEVITATION);

                    // Konfigurierbarer Ankunfts-Sound + Partikel (leer = aus)
                    plugin.soundService().playTo(player, plugin.settings().soundStartupArrive);
                    plugin.effectService().playAt(player.getLocation(), plugin.settings().effectStartupArrive);

                    // Teleport to exact platform center (block center at X.5, Z.5)
                    Location exactPos = new Location(
                            platformPos.getWorld(),
                            platformPos.getBlockX() + 0.5,
                            platformPos.getBlockY() + 1,
                            platformPos.getBlockZ() + 0.5,
                            player.getLocation().getYaw(),
                            player.getLocation().getPitch());
                    player.teleport(exactPos);

                    // Sofort Barrier-Ring auf Kopfhöhe setzen, damit der Tribut
                    // nicht wegläuft, während andere noch in den Röhren sind.
                    placeBarrierRing(exactPos);

                    playersArrived++;
                    Sender.debug(
                            player.getName() + " arrived at platform (" + playersArrived + "/" + totalPlayers + ")");

                    // Check if all players have arrived
                    if (playersArrived >= totalPlayers) {
                        onAllPlayersArrived(admin);
                    }

                    this.cancel();
                    return;
                }

                // Timeout check
                if (ticksWaited >= maxTicks) {
                    Sender.warning("Timeout waiting for " + player.getName() + " to reach platform");
                    player.removePotionEffect(PotionEffectType.LEVITATION);
                    sealPlatformCenter(platformPos);
                    playersArrived++;

                    if (playersArrived >= totalPlayers) {
                        onAllPlayersArrived(admin);
                    }

                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L); // Check every tick for smooth freezing
    }

    /**
     * Called when all players have arrived at their platforms.
     * Triggers READY phase: freeze players, clear air, fill loot, remove lobby.
     */
    private void onAllPlayersArrived(Player admin) {
        GameState gameState = GameState.getInstance();
        gameState.setCurrentPhase(GamePhase.READY);

        Sender.log("All players have arrived at their platforms!");
        Sender.log("Executing READY phase preparations...");

        // Step 1: Clear 3x3 air above each platform
        clearAirAbovePlatforms();
        Sender.log("Cleared 3x3 air above all platforms");

        // Step 1b: Unterirdische Barrier-Ringe entfernen — alle Tribute sind oben
        for (Location ringPos : undergroundRingPositions) {
            removeBarrierRing(ringPos);
        }
        undergroundRingPositions.clear();

        // Step 1c: Barrier-Ringe auf Kopfhöhe um jede Plattform: Tribute können
        // bis zum Countdown-Ende nicht weglaufen, ohne festgehalten zu wirken.
        // StartRunner entfernt die Ringe beim Release.
        for (Location platformPos : GameState.getInstance().getPlatformPositions()) {
            placeBarrierRing(platformPos.clone().add(0, 1, 0));
        }
        Sender.log("Placed barrier rings around all platforms");

        // Step 2: Remove slowness from players — Barrier-Ringe halten die
        // Tribute auf den Plattformen, kein Bewegungs-Listener mehr nötig.
        removeSlownessFromPlayers();
        Sender.log("Removed slowness from players (barrier rings restrict movement)");

        // Step 3: Fill arena containers with loot
        // DELAYED to ensure chunks and tile entities are fully loaded/initialized
        // after teleport and schematic operations. Use 20 ticks (1 second).
        Bukkit.getScheduler().runTaskLater(JavaPlugin.getPlugin(HungerGamesPlugin.class), () -> {
            new LootManager().fillArenaContainers();
            Sender.log("Filled arena containers with loot");

            // Broadcast only AFTER loot is filled (approximate timing)
            // But we can keep the rest of the flow here as strictly sequential isn't
            // critical for lobby removal
        }, 20L);

        // Step 4: Remove the lobby
        removeLobby();
        Sender.log("Removed lobby structure");

        // Notify admin
        if (admin != null && admin.isOnline()) {
            admin.sendMessage("");
            admin.sendMessage("§a§l✓ ENGAGING SEQUENCE COMPLETE!");
            admin.sendMessage("§7All §e" + totalPlayers + "§7 players are on their platforms.");
            admin.sendMessage("§a✓ 3x3 air cleared above platforms");
            admin.sendMessage("§a✓ Movement restricted by listener");
            admin.sendMessage("§a✓ Arena loot generated");
            admin.sendMessage("§a✓ Lobby removed");
            admin.sendMessage("§7Game is now in §eREADY§7 state.");
            admin.sendMessage("§7Use §e/start§7 to begin the countdown.");
            admin.sendMessage("");

            admin.playSound(admin.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        }

        // Broadcast to all players
        Bukkit.broadcastMessage("§a§lAll tributes are in position!");
        Bukkit.broadcastMessage("§7The games will begin shortly...");
    }

    /**
     * Clears a 3x3 area of air one block above each platform.
     * This ensures players have space and removes any barriers.
     */
    private void clearAirAbovePlatforms() {
        List<Location> platformPositions = GameState.getInstance().getPlatformPositions();

        for (Location platformPos : platformPositions) {
            World world = platformPos.getWorld();
            if (world == null)
                continue;

            // Platform center is at platformPos, players stand 1 block above
            // Clear 3x3 area starting 2 blocks above platform (above player head level)
            int centerX = platformPos.getBlockX();
            int baseY = platformPos.getBlockY() + 2; // 1 above platform + 1 more for clearance
            int centerZ = platformPos.getBlockZ();

            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    Block block = world.getBlockAt(centerX + x, baseY, centerZ + z);
                    if (block.getType() != Material.AIR) {
                        block.setType(Material.AIR);
                    }
                }
            }

            // Barrier-Reste über der Spawn-Plattform entfernen (z. B. der alte
            // Käfig aus platformbarrier.schem bei wiederhergestellten Arenen) —
            // die Barrier-Ringe werden danach gezielt neu gesetzt.
            for (int y = 1; y <= 5; y++) {
                for (int x = -1; x <= 1; x++) {
                    for (int z = -1; z <= 1; z++) {
                        Block block = world.getBlockAt(centerX + x,
                                platformPos.getBlockY() + y, centerZ + z);
                        if (block.getType() == Material.BARRIER) {
                            block.setType(Material.AIR);
                        }
                    }
                }
            }
        }
    }

    /**
     * Removes slowness effect from all whitelisted players.
     * Movement is restricted by the barrier rings around the platforms.
     *
     * We keep the negative JUMP_BOOST effect to prevent players from jumping
     * on their platforms during the countdown.
     */
    private void removeSlownessFromPlayers() {
        List<Player> onlinePlayers = getOnlineWhitelistedPlayers();

        for (Player player : onlinePlayers) {
            // Remove slowness - barrier rings handle movement restriction
            // This provides smoother visuals and less "stuck" feeling for players
            player.removePotionEffect(PotionEffectType.SLOWNESS);

            // Keep JUMP_BOOST negative to prevent jumping during countdown
            player.addPotionEffect(new PotionEffect(
                    PotionEffectType.JUMP_BOOST,
                    Integer.MAX_VALUE,
                    -128, // Negative value prevents jumping completely
                    false, false, false));
        }
    }

    /**
     * Removes the lobby structure.
     */
    private void removeLobby() {
        Location lobbyCenter = GameState.getInstance().getLobbyCenter();
        if (lobbyCenter == null) {
            Sender.debug("No lobby center stored, skipping lobby removal");
            return;
        }

        World world = lobbyCenter.getWorld();
        if (world == null)
            return;

        int width = config.getLobbyWidth();
        int depth = config.getLobbyDepth();
        int height = config.getLobbyHeight();
        int heightOffset = config.getLobbyHeightOffset();

        Location arenaCenter = GameState.getInstance().getArenaCenter();
        if (arenaCenter == null)
            return;

        int baseX = arenaCenter.getBlockX() - width / 2;
        int baseY = arenaCenter.getBlockY() + heightOffset;
        int baseZ = arenaCenter.getBlockZ() - depth / 2;

        // Clear entire lobby structure (floor, walls, ceiling)
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                for (int y = 0; y <= height + 2; y++) { // +2 to include ceiling
                    world.getBlockAt(baseX + x, baseY + y, baseZ + z).setType(Material.AIR);
                }
            }
        }

        Sender.debug("Removed lobby at Y=" + baseY);
    }

    /**
     * Activates redstone lamps in the underground room's ceiling in rings expanding from the center.
     */
    private void activateLamps(Location arenaCenter) {
        if (arenaCenter == null || arenaCenter.getWorld() == null) {
            return;
        }

        World world = arenaCenter.getWorld();
        GameState gameState = GameState.getInstance();
        List<Location> platformPositions = gameState.getPlatformPositions();

        if (platformPositions.isEmpty()) {
            return;
        }

        // Calculate platform radius
        Location firstPlatform = platformPositions.get(0);
        double dx = firstPlatform.getX() - arenaCenter.getX();
        double dz = firstPlatform.getZ() - arenaCenter.getZ();
        double platformRadius = Math.sqrt(dx * dx + dz * dz);

        // Underground room ceiling Y coordinate
        int floorY = gameState.getUndergroundFloorY();
        int ceilingY = floorY + config.getUndergroundRoomHeight() + 1;

        // Underground room radius
        int roomRadius = (int) Math.ceil(platformRadius) + config.getUndergroundRoomExtraRadius();

        // Find all redstone lamps in the ceiling and group by distance from center
        TreeMap<Integer, List<Block>> lampRings = new TreeMap<>();
        int centerX = arenaCenter.getBlockX();
        int centerZ = arenaCenter.getBlockZ();

        for (int x = -roomRadius; x <= roomRadius; x++) {
            for (int z = -roomRadius; z <= roomRadius; z++) {
                double distance = Math.sqrt(x * x + z * z);
                if (distance <= roomRadius) {
                    Block block = world.getBlockAt(centerX + x, ceilingY, centerZ + z);
                    if (block.getType() == Material.REDSTONE_LAMP) {
                        int distanceInt = (int) Math.round(distance);
                        lampRings.computeIfAbsent(distanceInt, k -> new ArrayList<>()).add(block);
                    }
                }
            }
        }

        if (lampRings.isEmpty()) {
            return;
        }

        // Activate lamps ring by ring
        int lampDelay = config.getStartupLampDelay();
        int delay = 0;

        for (Map.Entry<Integer, List<Block>> entry : lampRings.entrySet()) {
            List<Block> ringLamps = entry.getValue();

            new BukkitRunnable() {
                @Override
                public void run() {
                    for (Block block : ringLamps) {
                        if (block.getType() == Material.REDSTONE_LAMP && block.getBlockData() instanceof Lightable lightable) {
                            lightable.setLit(true);
                            block.setBlockData(lightable, false);
                        }
                    }

                    // Konfigurierbarer Lampen-Sound am Zentrum (leer = aus)
                    Location soundLoc = new Location(world, centerX, ceilingY, centerZ);
                    plugin.soundService().playAt(soundLoc, plugin.settings().soundStartupLamp);
                }
            }.runTaskLater(plugin, delay);

            delay += lampDelay;
        }
    }

    private String formatLocation(Location loc) {
        return String.format("X:%d Y:%d Z:%d", loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    /**
     * Legt einen Barrier-Ring auf Kopfhöhe (Standposition +1) um die acht
     * Nachbarblöcke. Ersetzt nur Luft, damit Röhrenglas o. Ä. erhalten bleibt.
     * Die Mitte bleibt frei — Levitation nach oben funktioniert weiterhin.
     */
    public static void placeBarrierRing(Location standPos) {
        World world = standPos.getWorld();
        if (world == null) {
            return;
        }
        int headY = standPos.getBlockY() + 1;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                Block block = world.getBlockAt(standPos.getBlockX() + dx, headY, standPos.getBlockZ() + dz);
                if (block.getType() == Material.AIR) {
                    block.setType(Material.BARRIER);
                }
            }
        }
    }

    /**
     * Entfernt einen zuvor gelegten Barrier-Ring wieder (nur Barrier-Blöcke,
     * andere Blöcke bleiben unangetastet).
     */
    public static void removeBarrierRing(Location standPos) {
        World world = standPos.getWorld();
        if (world == null) {
            return;
        }
        int headY = standPos.getBlockY() + 1;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                Block block = world.getBlockAt(standPos.getBlockX() + dx, headY, standPos.getBlockZ() + dz);
                if (block.getType() == Material.BARRIER) {
                    block.setType(Material.AIR);
                }
            }
        }
    }
}
