package com.hungergames.stagerunner;

/*
This class starts up when the server starts the plugin. It searches for a good spot for the game and places the lobby, as well as the middle of the game.
It correctly sets worldborder and center of the world, as well as player spawnpoint in the lobby.
 */

import com.hungergames.api.model.GamePhase;
import com.hungergames.GameState;
import com.hungergames.placer.SchemHandler;
import com.hungergames.player.WhitelistManager;
import com.hungergames.utils.ConfigManager;
import com.hungergames.utils.Sender;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PreflightRunner {

    private final SchemHandler schemHandler = new SchemHandler();
    private final ConfigManager config = ConfigManager.getInstance();

    // Stores the last calculated platform radius for use in underground room
    // generation
    private double lastCalculatedRadius = 0;

    /**
     * Main setup method. Called after player count is received via chat.
     * 
     * @param invokerUuid UUID of the admin who initiated setup
     * @param playerCount Number of players participating
     */
    public void setup(UUID invokerUuid, int playerCount) {
        if (invokerUuid == null) {
            Sender.error("Invoker UUID is null!");
            return;
        }

        Player player = Bukkit.getPlayer(invokerUuid);
        if (player == null) {
            Sender.error("Invoking player is not online: " + invokerUuid);
            return;
        }

        World world = player.getWorld();
        if (world == null) {
            Sender.error("Could not get player's world!");
            return;
        }

        // Get arena center from GameState (set by InitCommand)
        Location gameCenter = GameState.getInstance().getArenaCenter();
        if (gameCenter == null) {
            gameCenter = player.getLocation();
            GameState.getInstance().setArenaCenter(gameCenter);
        }

        Sender.log("Starting arena setup at " + formatLocation(gameCenter) + " for " + playerCount + " players");

        // Update game state
        GameState.getInstance().setCurrentPhase(GamePhase.PREFLIGHT);
        GameState.getInstance().setPlayerCount(playerCount);

        // Step 1: Calculate platform positions first (needed for terrain radius)
        Sender.log("Step 1: Calculating platform positions...");
        List<Location> platformPositions = calculatePlatformPositions(gameCenter, playerCount);
        GameState.getInstance().setPlatformPositions(platformPositions);
        player.sendMessage("§a✓ Calculated " + platformPositions.size() + " platform positions");

        // Step 2: Prepare terrain - flatten ground and set to grass
        Sender.log("Step 2: Preparing terrain...");
        int terrainRadius = (int) Math.ceil(lastCalculatedRadius) + config.getUndergroundRoomExtraRadius();
        prepareTerrain(gameCenter, terrainRadius, world);
        player.sendMessage("§a✓ Terrain prepared (radius: " + terrainRadius + " blocks)");

        // Step 3: Generate underground room FIRST (filled with air)
        // Tubes will be placed on top, automatically cutting through correctly
        Sender.log("Step 3: Generating underground room...");
        Location undergroundCenter = generateUndergroundRoom(gameCenter, lastCalculatedRadius, playerCount);
        GameState.getInstance().setUndergroundRoomCenter(undergroundCenter);
        player.sendMessage("§a✓ Underground room generated at Y=" + (int) undergroundCenter.getY());

        // Step 4: Place middle schematic
        Sender.log("Step 4: Placing middle schematic...");
        schemHandler.pasteSchematic("middle.schem", gameCenter);
        player.sendMessage("§a✓ Middle schematic placed");

        // Step 5: Place platforms and tubes (after room, so tubes override air)
        Sender.log("Step 5: Placing platforms and tubes...");
        placePlatformsAndTubes(platformPositions);
        player.sendMessage("§a✓ Platforms and tubes placed");

        // Step 6: Generate lobby
        Sender.log("Step 6: Generating lobby...");
        Location lobbyCenter = generateLobby(gameCenter, playerCount);
        GameState.getInstance().setLobbyCenter(lobbyCenter);
        player.sendMessage("§a✓ Lobby generated at Y=" + (int) lobbyCenter.getY());

        // Step 7: Configure world settings
        Sender.log("Step 7: Configuring world settings...");
        configureWorldSettings(world, gameCenter);
        player.sendMessage("§a✓ World settings configured");

        // Step 8: Apply whitelist
        Sender.log("Step 8: Applying whitelist...");
        WhitelistManager.getInstance().applyToServerWhitelist();
        player.sendMessage("§a✓ Whitelist applied");

        // Step 9: Teleport admin to lobby
        Sender.log("Step 9: Teleporting admin to lobby...");
        player.teleport(lobbyCenter);
        player.setGameMode(org.bukkit.GameMode.ADVENTURE);
        player.sendMessage("§a✓ Teleported to lobby");

        // Complete — Arena steht, ab jetzt gilt die Lobby-Phase (Teamwahl offen)
        if (!GameState.getInstance().tryTransition(GamePhase.LOBBY)) {
            Sender.error("Phasenwechsel PREFLIGHT -> LOBBY fehlgeschlagen!");
            player.sendMessage("§cInterner Fehler beim Phasenwechsel — bitte /fliptable erwägen.");
            return;
        }
        Sender.log("Arena setup complete!");
        player.sendMessage("");
        player.sendMessage("§a§l✓ ARENA SETUP COMPLETE!");
        player.sendMessage("§7Arena center: §e" + formatLocation(gameCenter));
        player.sendMessage("§7Lobby center: §e" + formatLocation(lobbyCenter));
        player.sendMessage("§7Platforms: §e" + platformPositions.size());
        player.sendMessage("§7Player count: §e" + playerCount);
        player.sendMessage("");
        player.sendMessage("§7Players can now join and will spawn in the lobby.");
        player.sendMessage("§7Use §e/test-start§7 to enter spectator mode for testing.");
    }

    /**
     * Calculates positions for platforms in a circle around the center.
     * Ensures minimum gap between platforms as configured.
     */
    private List<Location> calculatePlatformPositions(Location center, int playerCount) {
        List<Location> positions = new ArrayList<>();

        int platformWidth = config.getPlatformWidth();
        int minGap = config.getPlatformMinGap();

        // Calculate required arc length per platform
        double arcLengthPerPlayer = platformWidth + minGap;

        // Calculate circumference and radius
        double circumference = playerCount * arcLengthPerPlayer;
        double radius = circumference / (2 * Math.PI);

        // Ensure minimum radius for small player counts
        radius = Math.max(radius, 10.0);

        // Store radius for underground room generation
        this.lastCalculatedRadius = radius;

        Sender.debug("Platform circle radius: " + String.format("%.2f", radius) + " blocks");

        // Calculate positions
        for (int i = 0; i < playerCount; i++) {
            double angle = (2 * Math.PI * i) / playerCount;
            double x = center.getX() + radius * Math.cos(angle);
            double z = center.getZ() + radius * Math.sin(angle);

            Location platformPos = new Location(
                    center.getWorld(),
                    x,
                    center.getY(),
                    z,
                    (float) Math.toDegrees(angle) + 90, // Face inward toward center
                    0);

            positions.add(platformPos);
        }

        return positions;
    }

    /**
     * Places platform schematics and tubes at the given positions.
     */
    private void placePlatformsAndTubes(List<Location> positions) {
        int tubeDepth = config.getTubeDepth();

        for (int i = 0; i < positions.size(); i++) {
            Location platformPos = positions.get(i);

            // Place tube directly under platform (schematic extends downward from this
            // point)
            // Stays at ground level as requested
            Location tubePos = platformPos.clone();
            schemHandler.pasteSchematic("tube.schem", tubePos);

            // Place platform - 1 block UP. Deliberately WITHOUT the barrier cage
            // (platformbarrier.schem): movement is restricted by the barrier
            // rings from the StartupRunner; leftover cage blocks above the
            // spawn platforms are not wanted.
            Location raisedPlatformPos = platformPos.clone().add(0, 1, 0);
            schemHandler.pasteSchematic("platform.schem", raisedPlatformPos);

            if ((i + 1) % 10 == 0) {
                Sender.debug("Placed " + (i + 1) + "/" + positions.size() + " platforms");
            }
        }

        Sender.log("Finished placing " + positions.size() + " platforms with tubes");
    }

    /**
     * Prepares the terrain for the arena.
     * Flattens the ground to the arena center height and sets surface to grass.
     * Clears everything above the ground within the radius.
     * Smoothly blends edges with surrounding terrain.
     * 
     * @param center Center of the arena
     * @param radius Radius to flatten
     * @param world  World to modify
     */
    private void prepareTerrain(Location center, int radius, World world) {
        int centerX = center.getBlockX();
        int centerY = center.getBlockY();
        int centerZ = center.getBlockZ();

        // Edge blend distance (blocks to smooth transition)
        int blendDistance = 5;
        int totalRadius = radius + blendDistance;

        Sender.debug("Preparing terrain: center=" + formatLocation(center) +
                ", radius=" + radius + ", blendDistance=" + blendDistance);

        int blocksModified = 0;

        for (int x = -totalRadius; x <= totalRadius; x++) {
            for (int z = -totalRadius; z <= totalRadius; z++) {
                double distance = Math.sqrt(x * x + z * z);

                // Skip if outside total radius
                if (distance > totalRadius)
                    continue;

                int blockX = centerX + x;
                int blockZ = centerZ + z;

                // Calculate target Y based on distance (smooth edge blending)
                int targetY;
                if (distance <= radius) {
                    // Inside main arena - flat at center height - 1 (ground level)
                    targetY = centerY - 1;
                } else {
                    // In blend zone - gradually transition to natural terrain
                    double blendFactor = (distance - radius) / blendDistance;
                    int naturalY = getHighestNaturalBlock(world, blockX, blockZ);
                    targetY = (int) Math.round(centerY - 1 + (naturalY - (centerY - 1)) * blendFactor);
                }

                // Set ground block (grass block)
                world.getBlockAt(blockX, targetY, blockZ).setType(Material.GRASS_BLOCK, false);

                // Set dirt below grass (2-3 layers)
                for (int depth = 1; depth <= 3; depth++) {
                    world.getBlockAt(blockX, targetY - depth, blockZ).setType(Material.DIRT, false);
                }

                // Clear everything above the ground up to a reasonable height
                for (int y = targetY + 1; y <= targetY + 50; y++) {
                    Material current = world.getBlockAt(blockX, y, blockZ).getType();
                    if (current != Material.AIR && current != Material.BARRIER) {
                        world.getBlockAt(blockX, y, blockZ).setType(Material.AIR, false);
                    }
                }

                blocksModified++;
            }
        }

        Sender.log("Terrain prepared: " + blocksModified + " columns modified");
    }

    /**
     * Gets the highest natural (non-air) block Y coordinate at a position.
     */
    private int getHighestNaturalBlock(World world, int x, int z) {
        return world.getHighestBlockYAt(x, z);
    }

    /**
     * Generates the glass lobby box above the arena center.
     * Returns the center location inside the lobby.
     */
    private Location generateLobby(Location arenaCenter, int playerCount) {
        World world = arenaCenter.getWorld();

        int heightOffset = config.getLobbyHeightOffset();
        int width = config.getLobbyWidth();
        int depth = config.getLobbyDepth();
        int height = config.getLobbyHeight();
        Material blockType = config.getLobbyBlockType();

        // Calculate lobby base position (centered above arena)
        int baseX = arenaCenter.getBlockX() - width / 2;
        int baseY = arenaCenter.getBlockY() + heightOffset;
        int baseZ = arenaCenter.getBlockZ() - depth / 2;

        Sender.debug("Generating lobby at Y=" + baseY + " with dimensions " + width + "x" + depth + "x" + height);

        // Build floor
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                world.getBlockAt(baseX + x, baseY, baseZ + z).setType(blockType, false);
            }
        }

        // Build walls
        for (int y = 1; y <= height; y++) {
            for (int x = 0; x < width; x++) {
                // Front and back walls
                world.getBlockAt(baseX + x, baseY + y, baseZ).setType(blockType, false);
                world.getBlockAt(baseX + x, baseY + y, baseZ + depth - 1).setType(blockType, false);
            }
            for (int z = 0; z < depth; z++) {
                // Left and right walls
                world.getBlockAt(baseX, baseY + y, baseZ + z).setType(blockType, false);
                world.getBlockAt(baseX + width - 1, baseY + y, baseZ + z).setType(blockType, false);
            }
        }

        // Build ceiling
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                world.getBlockAt(baseX + x, baseY + height + 1, baseZ + z).setType(blockType, false);
            }
        }

        // Clear interior (in case there was anything there)
        for (int x = 1; x < width - 1; x++) {
            for (int z = 1; z < depth - 1; z++) {
                for (int y = 1; y <= height; y++) {
                    world.getBlockAt(baseX + x, baseY + y, baseZ + z).setType(Material.AIR, false);
                }
            }
        }

        // Calculate center of lobby interior
        Location lobbyCenter = new Location(
                world,
                baseX + width / 2.0,
                baseY + 1.5, // Slightly above floor
                baseZ + depth / 2.0);

        // Set world spawn ON TOP of the glass box (not inside): joining players
        // land on the roof; tributes are teleported inside by the LobbyBoxService.
        Location roofSpawn = new Location(
                world,
                baseX + width / 2.0,
                baseY + height + 2.0, // Standing on the ceiling layer
                baseZ + depth / 2.0);
        world.setSpawnLocation(roofSpawn);

        Sender.log("Lobby generated successfully");
        return lobbyCenter;
    }

    /**
     * Generates the underground circular quartz room below the arena.
     * The room is deep enough that tube tips are fully inside.
     * The ceiling contains redstone lamps.
     * 
     * @param arenaCenter    Center of the arena (at ground level)
     * @param platformRadius Radius of the platform circle
     * @param playerCount    Number of players
     * @return Center location of the underground room
     */
    private Location generateUndergroundRoom(Location arenaCenter, double platformRadius, int playerCount) {
        World world = arenaCenter.getWorld();

        // Get actual tube schematic height
        int tubeHeight = schemHandler.getSchematicHeight("tube.schem");
        if (tubeHeight <= 0) {
            Sender.warning("Could not read tube schematic height, using config value");
            tubeHeight = config.getTubeDepth();
        }

        int roomHeight = config.getUndergroundRoomHeight();
        int extraRadius = config.getUndergroundRoomExtraRadius();
        int tubeProtrusion = 3; // How many blocks the tube should protrude into the room

        // Calculate room dimensions
        // The room radius should be larger than the platform radius to encompass all
        // tubes
        int roomRadius = (int) Math.ceil(platformRadius) + extraRadius;

        // Calculate room Y position
        // Platforms are at arenaCenter.Y (raisedPlatformPos is now just clone)
        // Tube schematic starts there and goes down by tubeHeight
        // Tube bottom is at: arenaCenter.Y - tubeHeight
        int tubeBottomY = arenaCenter.getBlockY() - tubeHeight;
        // Room ceiling should be tubeProtrusion blocks ABOVE tubeBottom so tubes stick
        // into room
        int ceilingY = tubeBottomY + tubeProtrusion;
        int floorY = ceilingY - roomHeight - 1; // -1 for ceiling block itself

        int centerX = arenaCenter.getBlockX();
        int centerZ = arenaCenter.getBlockZ();

        Sender.debug("Generating underground room: radius=" + roomRadius +
                ", height=" + roomHeight +
                ", tubeHeight=" + tubeHeight +
                ", tubeBottomY=" + tubeBottomY +
                ", tubeProtrusion=" + tubeProtrusion +
                ", Y range=" + floorY + " to " + ceilingY);

        // Build the circular room
        // Interior is filled with AIR - tubes will be placed afterwards and override
        // correctly
        for (int y = floorY; y <= ceilingY; y++) {
            for (int x = -roomRadius - 1; x <= roomRadius + 1; x++) {
                for (int z = -roomRadius - 1; z <= roomRadius + 1; z++) {
                    double distance = Math.sqrt(x * x + z * z);
                    int blockX = centerX + x;
                    int blockZ = centerZ + z;

                    // Floor
                    if (y == floorY) {
                        if (distance <= roomRadius) {
                            world.getBlockAt(blockX, y, blockZ).setType(Material.QUARTZ_BLOCK, false);
                        }
                    }
                    // Ceiling with redstone lamps
                    else if (y == ceilingY) {
                        if (distance <= roomRadius) {
                            // Place redstone lamps in a pattern (every 3 blocks for aesthetic)
                            if ((x + z) % 3 == 0 && distance > 2) {
                                world.getBlockAt(blockX, y, blockZ).setType(Material.REDSTONE_LAMP, false);
                            } else {
                                world.getBlockAt(blockX, y, blockZ).setType(Material.QUARTZ_BLOCK, false);
                            }
                        }
                    }
                    // Walls and interior
                    else {
                        // Outer wall (circular)
                        if (distance >= roomRadius && distance < roomRadius + 1) {
                            world.getBlockAt(blockX, y, blockZ).setType(Material.QUARTZ_BLOCK, false);
                        }
                        // Interior - fill with air
                        else if (distance < roomRadius) {
                            world.getBlockAt(blockX, y, blockZ).setType(Material.AIR, false);
                        }
                    }
                }
            }
        }

        // Store floor Y for spawn calculations
        GameState.getInstance().setUndergroundFloorY(floorY);

        // Calculate underground spawn positions - one per platform, on the floor
        List<Location> undergroundSpawnPositions = new ArrayList<>();
        for (Location platformPos : GameState.getInstance().getPlatformPositions()) {
            // Center the spawn position within the block (+0.5 on X and Z)
            // This ensures players spawn in the exact center of the tube
            // and don't hit the glass edges during levitation
            Location spawnPos = new Location(
                    world,
                    platformPos.getBlockX() + 0.5, // Centered X
                    floorY + 1.0, // On the floor (1 block above floor block)
                    platformPos.getBlockZ() + 0.5, // Centered Z
                    platformPos.getYaw(), // Face same direction as platform
                    0);
            undergroundSpawnPositions.add(spawnPos);
        }
        GameState.getInstance().setUndergroundSpawnPositions(undergroundSpawnPositions);

        // Calculate room center (for backwards compatibility)
        Location roomCenter = new Location(
                world,
                centerX + 0.5,
                floorY + 1.0,
                centerZ + 0.5);

        Sender.log("Underground room generated at Y=" + floorY + " to " + ceilingY +
                " with radius " + roomRadius + ", tube bottom Y=" + tubeBottomY +
                ", " + undergroundSpawnPositions.size() + " spawn positions");

        return roomCenter;
    }

    /**
     * Configures world settings: worldborder, difficulty, spawn.
     */
    private void configureWorldSettings(World world, Location center) {
        int borderSize = config.getWorldborderSize();

        // Set worldborder for Overworld
        WorldBorder border = world.getWorldBorder();
        border.setCenter(center);
        border.setSize(borderSize);
        Sender.log("Set overworld worldborder to " + borderSize + " blocks");

        // Set worldborder for Nether
        World nether = Bukkit.getWorld("world_nether");
        if (nether != null) {
            WorldBorder netherBorder = nether.getWorldBorder();
            // Nether is 1:8 scale, but we set same border size as requested
            netherBorder.setCenter(center.getX() / 8, center.getZ() / 8);
            netherBorder.setSize(borderSize);
            Sender.log("Set nether worldborder to " + borderSize + " blocks");
        } else {
            Sender.warning("Nether world not found, skipping nether worldborder");
        }

        // Set difficulty
        world.setDifficulty(config.getPreflightDifficulty());
        Sender.log("Set difficulty to " + config.getPreflightDifficulty().name());

        // Set world spawn to arena center (will be overridden by lobby later)
        world.setSpawnLocation(center);
    }

    /**
     * Formats a location for display.
     */
    private String formatLocation(Location loc) {
        return String.format("X:%d Y:%d Z:%d", loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }
}
