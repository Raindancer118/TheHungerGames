# Config Reload Command

## Description
The `/config-reload` command allows GameMasters and Admins to reload all plugin configuration files and update the tournament whitelist and teams without restarting the server.

## Features
- Reloads `config.yml` (Game settings)
- Reloads `loot.yml` (Loot tables)
- Reloads `teams.yml` (Team colors)
- Reloads `whitelist.yml` (Players and team assignments)
- **Automatically updates the server whitelist**: Players listed in `whitelist.yml` are added to the Minecraft server whitelist.
- **Automatically refreshes scoreboard teams**: Updates colored nametags and team associations for all online players.

## Usage
`/config-reload`

## Permissions
- `hungergames.admin`
- `gamemaster`
- `op`

## Technical Details
This command orchestrates the reload process by calling the reload methods of the following managers in order:
1. `ConfigManager`
2. `LootConfig`
3. `TeamsManager`
4. `WhitelistManager`

After reloading, it specifically triggers `WhitelistManager.applyToServerWhitelist()` to ensure server access control is up-to-date and `TeamsManager.setupScoreboardTeams()` to refresh visual team indicators.
