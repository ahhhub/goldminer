A mining mini-game plugin for Purpur 1.21.11, where players mine in an independent mine world to earn coins and experience, upgrade pickaxes, purchase items, and form teams.

* * *

## Table of Contents

+   [Dependencies](#dependencies)
+   [Installation & Setup](#installation--setup)
+   [Command Reference](#command-reference)
    +   [Player Commands](#player-commands)
    +   [Admin Commands](#admin-commands)
+   [PlaceholderAPI](#placeholderapi)
+   [Configuration Files](#configuration-files)
    +   [config.yml](#configyml)
    +   [lang.yml](#langyml)
    +   [layers.yml](#layersyml)
    +   [loot.yml](#lootyml)
    +   [shop.yml](#shopyml)
+   [Feature Details](#feature-details)
    +   [Mine System](#mine-system)
    +   [Pickaxe Upgrades](#pickaxe-upgrades)
    +   [Crit System](#crit-system)
    +   [Potion Shop](#potion-shop)
    +   [Crit Rate / Crit Multiplier Shop](#crit-rate--crit-multiplier-shop)
    +   [Level Purchase](#level-purchase)
    +   [Chain Trial Card](#chain-trial-card)
    +   [General Chain](#general-chain)
    +   [Team System](#team-system)
    +   [Currency Exchange](#currency-exchange)
+   [Permission Nodes](#permission-nodes)
+   [FAQ](#faq)

* * *

## Dependencies

| Plugin | Required | Description |
| --- | --- | --- |
| **Multiverse-Core** | Yes | World management |
| **Vault** | Yes | Economy system integration |
| **PlaceholderAPI** | No | Placeholder support (recommended) |

* * *

## Installation & Setup

1.  Ensure the required dependencies are installed
2.  Place `GoldMiner.jar` into the server's `plugins/` directory
3.  Restart the server or run `/plugman load GoldMiner`
4.  The plugin will generate configuration files under `plugins/GoldMiner/`:
    +   `config.yml` - Main configuration
    +   `lang.yml` - Language/messages
    +   `layers.yml` - Layered mine definitions (layers/ores/caves)
    +   `loot.yml` - Chest loot (experience bottles / level-up balls)
    +   `shop.yml` - Shop pricing
5.  Modify the configuration files as needed, then run `/goldminer reload` to reload

* * *

## Command Reference

### Player Commands

| Command | Description |
| --- | --- |
| `/goldminer join` | Join the mine world and start mining |
| `/goldminer shop` | Open the mine shop GUI |
| `/goldminer info` | View miner info (level/coins/crit rate, etc.) |
| `/goldminer suit` | Toggle equipment display/hide |
| `/goldminer buy lv <amount>` | Precisely purchase a specified number of levels |
| `/goldminer buy lv <amount> confirm` | Confirm the level purchase |
| `/goldminer team create` | Create a team |
| `/goldminer team join <team name>` | Apply to join a team |
| `/goldminer team accept [player name]` | Accept a join application |
| `/goldminer team leave` | Leave the team (experience/level cleared) |
| `/goldminer team list` | View the team list |
| `/goldminer top` | View the leaderboard |
| `/goldminer exchange [amount]` | Exchange mine coins for main world currency |
| `/goldminer help` | View help |

### Admin Commands

| Command | Description |
| --- | --- |
| `/goldminer reload` | Reload configuration and refresh all layers |
| `/goldminer reload <layer>` | Refresh only the specified layer (stone/calcite/.../bedrock) |
| `/goldminer reload pool` | Force a full refresh of the mine (including caves and chests) |
| `/goldminer reload info` | Refresh player data and mine world info |
| `/goldminer shop set <key> <price>` | Hot-modify shop prices (takes effect immediately) |
| `/goldminer set exp|lv <player> <amount>` | Set player experience/level |
| `/goldminer add exp|lv <player> <amount>` | Add player experience/level |
| `/goldminer remove exp|lv <player> <amount>` | Remove player experience/level |

* * *

## PlaceholderAPI

| Placeholder | Return Value | Description |
| --- | --- | --- |
| `%goldminer_reload_time%` | Integer | Mine air-ratio check interval (seconds) |
| `%goldminer_user_level%` | Integer | Player's current level |
| `%goldminer_user_money%` | Integer | Player's coins |
| `%goldminer_crit_hit_rate%` | Percentage string | Crit rate (e.g., "5.0") |
| `%goldminer_crit_magnification%` | Multiplier string | Crit multiplier (e.g., "2.5") |
| `%goldminer_crit_time%` | Integer | Remaining time of bonus crit multiplier (seconds) |
| `%goldminer_interlocking_type%` | String | Current chain type ("None"/Plane X/Plane Z/Radius/View Direction) |
| `%goldminer_interlocking_time%` | Integer | Remaining time of the chain trial card (seconds) |
| `%goldminer_nomal_interlocking_time%` | Integer | Remaining time of general chain (seconds) |

* * *

## Configuration Files

### config.yml

The main configuration file, controlling mine parameters, pickaxe enchant limits, potion effects, the crit system, and more.

```
# Storage settings
storage:
  type: sqlite              # sqlite or mysql
  mysql:
    host: localhost
    port: 3306
    database: goldminer
    username: root
    password: password

# Mine settings
mine:
  world-name: "goldminer_mine"  # Mine world name
  border-size: 2000             # World border
  center-size: 100              # Side length of the mine core area (length x width)
  check-interval: 10            # Air-ratio check interval (seconds)
  air-threshold: 95.0           # Auto full-refresh when air ratio reaches this percentage
  refresh-batch-size: 20000     # Blocks processed per tick (batched refresh/scan)

# Crit system
crit-system:
  default-crit-rate: 0.005         # Initial crit rate (0.5%)
  default-crit-magnification: 0.5  # Initial crit multiplier
  bonus-crit-mag-duration: 1800    # Duration of bonus crit multiplier (seconds)
  overflow-exp-multiplier: 500.0   # Conversion multiplier for overflow crit rate into experience

# Stepping glass
glass-block:
  material: GLASS          # Glass block type
  name: "&fStepping Glass &7(Infinite Use)"
  lore: "&7A glass block that can be placed infinitely"

# Pickaxe enchant limits (by pickaxe level)
pickaxe-enchant-limits:
  default: {efficiency: 5, fortune: 3, unbreaking: 3}
  iron: {efficiency: 10, ...}
  diamond: {efficiency: 30, fortune: 10, ...}
  netherite: {efficiency: 255, fortune: 15, ...}
```

### lang.yml

The language/message file; all displayed text can be configured here:

```
# Mining messages
mining:
  coin-earned: "&6+{coin} Coins &7| &a+{exp} Exp"
  level-up: "&aCongratulations! Your miner level has increased to &e{level} &a!"
  pickaxe-upgrade: "&aYour pickaxe has been upgraded to &e{pickaxe}&a!"
  enchant-upgrade: "&aYour pickaxe enchantment has been improved!"

# GUI buttons
gui:
  button:
    return-spawn: {name: "&aReturn to Spawn", ...}
    create-team: {name: "&bCreate Team", ...}
```

### layers.yml

The layered mine definition file. From top to bottom, the mine consists of: Stone Layer → Calcite Layer → Granite Layer → Deepslate Layer → Netherrack Layer → Basalt Layer → Blackstone Layer → End Stone Layer, with 1 layer of bedrock at the very bottom.

```
global:
  base-weight-percent: 95.0   # Percentage of base blocks in overall generation probability
  ore-weight-percent: 5.0     # Percentage of ores/special blocks
  inherit-decay: 0.05         # Decay coefficient for ores inherited from the layer above

bedrock:
  height: 1                   # Bedrock layer thickness
  block: BEDROCK

caves:
  enabled: true
  count-per-10000: 2          # Number of caves per 10000 block volume
  chest-chance: 0.3           # Chance of a chest appearing in each cave

layers:
  stone:                      # Layer name (usable in /goldminer reload)
    display: "&7Stone Layer"
    height: 16
    base-blocks:
      STONE: {weight: 80.0, coin: 1, exp: 1}
    ores:
      COAL_ORE: {weight: 40.0, coin: 10, exp: 5}
      IRON_ORE: {weight: 25.0, coin: 15, exp: 8}
```

+   `weight`: Relative weight within its pool (higher = more common)
+   `coin` / `exp`: Mining rewards
+   `inherit: false`: The ore only spawns in this layer and is not inherited to lower layers
+   Ores from the layer above are automatically inherited to lower layers (weight × inherit-decay per layer deeper)

### loot.yml

Chest loot definitions (experience bottles and level-up balls):

```
exp-bottles:
  exp-target: mine            # mine = miner experience / vanilla = vanilla exp bar
  stack-min: 1                # Stack size range per slot
  stack-max: 16
  types:
    small: {name: "&aExperience Bottle", weight: 60.0, exp: 500, lore: [...]}
    medium: {name: "&bExperience Bottle", weight: 30.0, exp: 2500, lore: [...]}
    large: {name: "&dExperience Bottle", weight: 10.0, exp: 10000, lore: [...]}

level-balls:
  level-target: mine          # mine = miner level / vanilla = vanilla level
  types:
    small: {name: "&eLevel-Up Ball", weight: 60.0, levels: 1, lore: [...]}

chest-loot:
  min-items: 2
  max-items: 10
  exp-bottle-chance: 0.85     # Experience bottle chance (high probability)
  level-ball-chance: 0.10     # Level-up ball chance (very low probability)
```

+   Items are distinguished by special NBT; items of the same type can be stacked and stored in chests for long-term use, and used by right-clicking
+   `weight`: Relative probability of drawing that type from a chest

### shop.yml

The shop pricing file; all prices and messages can be configured:

```
# Potion shop
potion:
  available-durations: [30, 60, 300, 600, 1800, 3600]  # Available durations (seconds)
  max-level: 30
  haste:
    base-price: 30
    level-multiplier: 0.5
    duration-multiplier: 0.3

# Crit rate shop
crit-rate:
  base-price: 300
  tiers:
    0.5pct: 1.0
    1pct: 3.0
    ...

# Chain trial card
# Price formula: base price × tier multiplier^(range-1)
chain-card:
  duration-seconds: 30
  plane_x: {base-price: 8000, tier-multiplier: 3.0}
  plane_z: {base-price: 8000, tier-multiplier: 3.0}
  radius:  {base-price: 15000, tier-multiplier: 3.5}
  ray:     {base-price: 6000,  tier-multiplier: 2.5}

# General chain
global-chain:
  price: 100
  duration-seconds: 10800    # 3 hours

# Shop icons
shop-icons:
  chain-card-plane-x: OAK_PLANKS
  chain-card-plane-z: OAK_PLANKS
  chain-card-radius: STONE
  chain-card-ray: ARROW
```

* * *

## Feature Details

### Mine System

+   An independent shared mine world (`goldminer_mine`)
+   The mine is a layered cube; from top to bottom: Stone Layer → Calcite Layer → Granite Layer → Deepslate Layer → Netherrack Layer → Basalt Layer → Blackstone Layer → End Stone Layer, with 1 layer of bedrock at the very bottom
+   Each layer consists of base blocks (default 90%) and ores (default 10%); ores from the layer above are inherited to lower layers with decay
+   Caves are randomly generated inside the mine; some caves spawn chests (experience bottles / level-up balls)
+   No more scheduled refresh: the mine automatically fully refreshes when air blocks account for 95% (adjustable) of the mine's total volume
+   Players spawn on a safe platform at the top of the mine; equipment is automatically protected

**Entering & Exiting**:

+   Run `/goldminer join` → teleport to the mine → receive a wooden pickaxe + menu star + infinite stepping glass
+   Exit the mine world (teleport back to the main world) → mine tracking is automatically cleared → you can `join` again

**Manual Refresh**:

+   `/goldminer reload` → Reload configuration and refresh all layers
+   `/goldminer reload <layer>` → Refresh only the specified layer (e.g., stone, deepslate, netherrack, bedrock)
+   `/goldminer reload pool` → Force a full refresh (including caves and chests)

### Pickaxe Upgrades

Players mine to earn experience → level up progressively → pickaxes upgrade automatically:

| Pickaxe | Upgrade Requirement | Enchant Limits |
| --- | --- | --- |
| Wooden Pickaxe | Initial | Efficiency 5 / Fortune 3 / Unbreaking 3 |
| Stone Pickaxe | Lv.2 | Efficiency 5 / Fortune 3 / Unbreaking 3 |
| Copper Pickaxe | Lv.22 | Efficiency 5 / Fortune 3 / Unbreaking 3 |
| Golden Pickaxe | Lv.42 | Efficiency 5 / Fortune 3 / Unbreaking 3 |
| Iron Pickaxe | Lv.72 | Efficiency 10 / Fortune 3 / Unbreaking 3 |
| Diamond Pickaxe | Lv.102 | Efficiency 30 / Fortune 10 / Unbreaking 5 |
| Netherite Pickaxe | Lv.132 | Efficiency 255 / Fortune 15 / Unbreaking 10 |

+   Each level improves enchantments progressively; once maxed, the pickaxe advances to the next tier
+   On advancement, one maxed enchantment is randomly inherited
+   Equipment automatically changes with pickaxe level (toggle visibility with `/goldminer suit`)

### Crit System

+   **Crit Rate**: Independently determined when mining, initially 0.5%, can be purchased up to 100%
+   **Crit Multiplier**: The bonus multiplier gained when a crit triggers, initially 0.5x
+   Crit effect: `Base coins + Base coins × Crit multiplier` (rounded)
+   During chain mining, each block has an independent crit check; the Title displays the number of crit blocks and bonus coins

**PAPI Placeholders**: `%goldminer_crit_hit_rate%` / `%goldminer_crit_magnification%`

### Potion Shop

In the mine shop → Purchase Boosts → Potion Effects:

| Potion | Level Range | Duration Options |
| --- | --- | --- |
| Haste | 1~30 | 30 seconds~1 hour |
| Speed | 1~30 | Same as above |
| Luck | 1~30 | Same as above |

+   Purchasing overwrites the current effect of the same type
+   Price formula: `Base price × (1 + Level × Level multiplier) × (1 + Duration exponent × Duration multiplier)`
+   All parameters can be adjusted in `shop.yml`

### Crit Rate / Crit Multiplier Shop

+   **Crit Rate**: Permanent increase, options: +0.5% / 1% / 5% / 10% / 50%
+   **Crit Multiplier**: 30-minute temporary increase, options: 1~20x, repeated purchases stack duration and multiplier
+   After crit rate reaches 100%, the overflow portion purchased is converted into experience at 500%

### Level Purchase

+   Presets: Buy 1 level / 5 levels / 10 levels
+   Precise: `/goldminer buy lv <amount> confirm`
+   Price formula: `(Total experience required from current level → target level) × Experience unit price coefficient`
+   The formula is displayed publicly in the GUI

### Chain Trial Card

In the mine shop → Chain Trial Card (effective within the mine world, 30 seconds):

| Type | Description | Price Formula |
| --- | --- | --- |
| Plane X-axis chain | Expands along the X-axis, up to 15 blocks | 8000 × 3.0^(N-1) |
| Plane Z-axis chain | Expands along the Z-axis, up to 15 blocks | 8000 × 3.0^(N-1) |
| Radius range chain | Spherical range, up to radius 15 | 15000 × 3.5^(N-1) |
| View direction chain | In front of the line of sight, up to 15 blocks + 15 height | 6000 × 2.5^(N-1) |

+   Click to enter the adjustment interface: `◀ Range -` / `N blocks` / `▶ Range +`
+   The view direction chain additionally has height adjustment (`◀ Height -` / `N blocks` / `▶ Height +`)
+   When adjusting, the interface refreshes in place without moving the cursor
+   **Same type**: Stacks range + height + duration
+   **Different type**: Overwrites the old effect without refunding coins

### General Chain

In the mine shop → General Chain (effective across the entire map outside the mine world):

+   **Price**: 100 coins
+   **Duration**: 3 hours (stackable)
+   **Range**: 9×9×3 blocks of the same type
+   Automatically chains adjacent blocks of the same type when mining ores/wood
+   Chain drops are affected by the player's tool enchantments (Fortune, etc.)
+   Does not take effect inside the mine world (no interference with each other)

### Team System

+   `/goldminer team create` creates a team (enter the name in chat)
+   `/goldminer team join <team name>` applies to join
+   Team leader `/goldminer team accept [player name]` accepts applications
+   Leaving a team → experience/level/pickaxe cleared (coins retained)
+   Maximum members: 10

### Currency Exchange

+   Mine coins can be exchanged for main world currency (requires Vault)
+   Exchange rate: `1 mine coin = X main world currency` (adjustable in config.yml)
+   The GUI provides quick exchanges of 10/100/1000, and also supports entering a custom amount in chat

* * *

## Permission Nodes

| Permission | Description | Default |
| --- | --- | --- |
| `goldminer.join` | Join the mine | true |
| `goldminer.suit` | Toggle equipment display | true |
| `goldminer.team.create` | Create a team | true |
| `goldminer.team.join` | Join a team | true |
| `goldminer.team.accept` | Accept join applications | true |
| `goldminer.team.leave` | Leave a team | true |
| `goldminer.team.list` | View the team list | true |
| `goldminer.top` | View the leaderboard | true |
| `goldminer.exchange` | Currency exchange | true |
| `goldminer.admin` | Administrator permission | op |

* * *

## FAQ

**Q: I can't see any ores after joining the mine?**  
A: The mine is a 101×101×100 cube (length and width determined by `mine.center-size`, height determined by each layer's height in `layers.yml`), from y=0 to y=100. Please confirm that your position is within the mine area.

**Q: The mine doesn't refresh automatically?**  
A: The mine no longer refreshes on a timer. The plugin scans the air ratio every `mine.check-interval` seconds, and automatically performs a full refresh when air blocks account for `mine.air-threshold`% of the mine's total volume. You can also run `/goldminer reload` (all layers) or `/goldminer reload <layer>` (single layer) to refresh manually.

**Q: The pickaxe doesn't change after upgrading?**  
A: The pickaxe is in the first slot of the hotbar and is automatically replaced after upgrading. If an old pickaxe remains, simply run `/goldminer join` again.

**Q: Can I still chain after the chain trial card expires?**  
A: No. The chain trial card is only effective for 30 seconds after purchase and automatically expires afterward.

**Q: Does the general chain take effect inside the mine world?**  
A: No. Inside the mine world, please purchase a chain trial card. The general chain only takes effect in worlds outside the mine.

**Q: How do I modify shop prices?**  
A: Method 1: Directly edit `shop.yml` and then `/goldminer reload`. Method 2: `/goldminer shop set <key> <price>` takes effect immediately.

**Q: PAPI placeholders are not displaying?**  
A: Make sure the PlaceholderAPI plugin is installed. When it is not installed, placeholders are silently ignored and do not affect the plugin's operation.

**Q: The mine doesn't refresh after a server restart?**  
A: The plugin will automatically rebuild the mine block tracking list, and normal operation resumes after the first refresh.

* * *

**Author**: 未定awa  
**Version**: 2.1.3  
**Compatibility**: Purpur 1.21.11
