# Loot System

The Loot System is responsible for filling containers (Chests, Barrels, etc.) within the arena with randomized loot when the game enters the **READY** phase.

## Configuration (`config.yml`)

The loot system uses a configurable scan radius to find containers around the arena center.

```yaml
loot:
  # Scan radius around arena center (X/Z)
  scan-radius: 50
  # Vertical scan range (Y)
  scan-y-range: 30
```

## Loot Tables (`loot.yml`)

Loot tables are defined per container type in `loot.yml`.
Supported types:
- `chest`
- `copper-chest` (1.21+)
- `trapped-chest`
- `barrel`
- `shelf` (Chiseled Bookshelf, max 3 items)

Each entry has:
- `item`: Material name (or custom ID)
- `weight`: Probability weight (default 10)
- `amount`: Fixed number or range (e.g., "1-3")
- `custom`: true/false (for custom items like FIENDFINDER)

## Logic & Persistence

1. **Trigger**: Loot is generated during the `StartupRunner` sequence (after `onAllPlayersArrived`).
2. **Delay**: Generation is delayed by **20 ticks (1 second)** to ensure chunks and schematic updates are fully processed.
3. **Double Chests**: The system checks if an inventory is already non-empty to prevent double-filling or clearing shared inventories of Double Chests.
4. **Persistence**: `container.update(true, false)` is used to strictly enforce saving the inventory to the block state.
5. **Verification**: After filling, the system re-reads the inventory from the live block to verify items are present and logs a detailed contents list to the console.

## Testing

You can verify loot generation manually using the command:

### `/test-loot`
*Permission: `hungergames.gamemaster`*

Triggers the loot generation process immediately using the configured settings.
Check the server conosle for detailed logs about:
- Number of containers found
- Location of filled containers
- Exact contents verified in each container

**Example Log Output:**
```
[HungerGames] [DEBUG] Filled CHEST at X:-100 Y:64 Z:100 with 5 items
[HungerGames] [DEBUG] Verified contents: [STONE_SWORDx1, APPLEx2, ARROWx5]
```
