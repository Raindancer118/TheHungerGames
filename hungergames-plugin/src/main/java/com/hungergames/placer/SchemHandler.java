package com.hungergames.placer;

/*
This class handles loading and placing of schematics into the game. Its being called mainly by PrepRunner class.
 */

import com.hungergames.HungerGamesPlugin;
import com.hungergames.utils.Sender;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.session.ClipboardHolder;
import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class SchemHandler {

    private final HungerGamesPlugin plugin = JavaPlugin.getPlugin(HungerGamesPlugin.class);

    /**
     * Gets the height (Y dimension) of a schematic.
     * 
     * @param schematicName Name of the schematic file
     * @return Height in blocks, or -1 if schematic couldn't be loaded
     */
    public int getSchematicHeight(String schematicName) {
        File schematicFile = getSafeSchematicFile(schematicName);
        if (schematicFile == null) {
            Sender.error("Invalid schematic name: " + schematicName);
            return -1;
        }

        if (!schematicFile.exists()) {
            try {
                plugin.saveResource("schem/" + schematicName, false);
            } catch (IllegalArgumentException e) {
                Sender.error("Could not find schematic resource: " + schematicName);
                return -1;
            }
        }

        ClipboardFormat format = ClipboardFormats.findByFile(schematicFile);
        if (format == null) {
            Sender.error("Unknown schematic format for file: " + schematicName);
            return -1;
        }

        try (ClipboardReader reader = format.getReader(new FileInputStream(schematicFile))) {
            Clipboard clipboard = reader.read();
            BlockVector3 dimensions = clipboard.getDimensions();
            return dimensions.getY();
        } catch (IOException e) {
            Sender.error("Failed to read schematic " + schematicName + ": " + e.getMessage());
            return -1;
        }
    }

    public void pasteSchematic(String schematicName, Location location) {
        Sender.log("Attempting to load schematic file: " + schematicName + ".");
        File schematicFile = getSafeSchematicFile(schematicName);
        if (schematicFile == null) {
            Sender.error("Invalid schematic name: " + schematicName);
            return;
        }

        if (!schematicFile.exists()) {
            try {
                plugin.saveResource("schem/" + schematicName, false);
                Sender.debug("Found schematic file. Attempting to load into clipboard.");
            } catch (IllegalArgumentException e) {
                Sender.error("Could not find schematic resource: " + schematicName);
                return;
            }
        }

        ClipboardFormat format = ClipboardFormats.findByFile(schematicFile);
        if (format == null) {
            Sender.error("Unknown schematic format for file: " + schematicName);
            return;
        }

        try (ClipboardReader reader = format.getReader(new FileInputStream(schematicFile))) {
            Clipboard clipboard = reader.read();

            try (EditSession editSession = WorldEdit.getInstance()
                    .newEditSession(BukkitAdapter.adapt(location.getWorld()))) {
                Operation operation = new ClipboardHolder(clipboard)
                        .createPaste(editSession)
                        .to(BlockVector3.at(location.getX(), location.getY(), location.getZ()))
                        .ignoreAirBlocks(false)
                        .build();
                Operations.complete(operation);
                Sender.log("Pasted schematic: " + schematicName);
            }
        } catch (IOException e) {
            Sender.error("Failed to paste schematic " + schematicName + ": " + e.getMessage(), e);
        } catch (Exception e) {
            Sender.error("WorldEdit error pasting " + schematicName + ": " + e.getMessage(), e);
        }
    }

    /**
     * Validates the schematic name and returns a File object within the schem
     * directory.
     * Prevents path traversal attacks.
     *
     * @param schematicName The name of the schematic file
     * @return The File object, or null if the path is invalid or unsafe
     */
    private File getSafeSchematicFile(String schematicName) {
        if (schematicName == null || schematicName.isEmpty()) {
            return null;
        }

        // Forbid directory navigation
        if (schematicName.contains("..") || schematicName.contains("/") || schematicName.contains("\\")) {
            return null;
        }

        File dataFolder = plugin.getDataFolder();
        File schemDir = new File(dataFolder, "schem");
        File schematicFile = new File(schemDir, schematicName);

        try {
            String canonicalPath = schematicFile.getCanonicalPath();
            String canonicalSchemDir = schemDir.getCanonicalPath();

            if (!canonicalPath.startsWith(canonicalSchemDir + File.separator)) {
                return null;
            }
        } catch (IOException e) {
            return null;
        }

        return schematicFile;
    }
}
