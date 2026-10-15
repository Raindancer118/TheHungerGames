# Schematic Area Protection

This feature protects the central area (schematic/lobby) from modification by players, while allowing specific interactions.

## Overview
By default, the vanilla Minecraft spawn protection blocks all interactions in the spawn radius. The HungerGames plugin disables this vanilla protection and replaces it with a custom listener that allows detailed control.

## Configuration
All settings are located in `config.yml` under the `protection` section.

```yaml
protection:
  # Radius around arena center where protection is active
  schematic-radius: 20
  
  # Permission to bypass protection (default: gamemaster)
  bypass-permission: gamemaster
  
  # List of blocks that normal players can interact with (open/use)
  allowed-interactions:
    - CHEST
    - TRAPPED_CHEST
    - ENDER_CHEST
    - BARREL
    - SHULKER_BOX
```

## Behavior
- **Block Breaking:** Blocked within `schematic-radius`.
- **Block Placing:** Blocked within `schematic-radius`.
- **Interactions:** 
    - Right-clicking blocks in the allowed list (e.g., Chests) is **ALLOWED**.
    - Other interactions (e.g., trampled crops, buttons if not listed) are **BLOCKED**.
- **Admin Bypass:** Players with the `bypass-permission` (default `HungerGames.gamemaster`) can build and break freely.

## Portal Logic
The portal protection logic has been inverted to support the schematic area.

- **Overworld:** Portals are ONLY allowed **inside** the `nether-allow-radius` (default: 20).
    - Portals **outside** this radius are blocked to prevent players from escaping map boundaries or bypassing game logic.
- **Nether:** Portals work everywhere in the Nether without restriction.

### Config
```yaml
portals:
  block-nether-portals: true
  nether-allow-radius: 20
```
