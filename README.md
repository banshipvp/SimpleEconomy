# SimpleEconomy Plugin - Complete Guide

## Overview

**SimpleEconomy** provides XP bottle conversion with **rank-based exhaustion** using LuckPerms. Players can convert their experience points into portable bottles that can be traded, sold, or used later.

## Features

✅ **XP Bottle Conversion** - `/xpbottle <amount>` to convert XP
✅ **No Limits** - Convert any amount of XP (no minimum/maximum)
✅ **Rank-Based Exhaustion** - Different ranks have different cooldown periods
✅ **LuckPerms Integration** - Automatically reads player ranks
✅ **Tradeable Bottles** - Bottles are regular items that can be dropped/traded
✅ **Full Consumption** - Right-click bottles to restore XP

## Default Exhaustion Times

| Rank | Exhaustion | Can Withdraw |
|------|-----------|--------------|
| owner | 0 min | Unlimited |
| admin | 0 min | Unlimited |
| mod | 1 min | Every 1 minute |
| legend | 2 min | Every 2 minutes |
| elite | 3 min | Every 3 minutes |
| vip | 5 min | Every 5 minutes |
| default | 10 min | Every 10 minutes |

## Commands

### Player Commands

**`/xpbottle <amount>`** - Convert XP to a bottle
- Converts your current XP to a portable bottle
- Subject to exhaustion cooldown based on rank
- If inventory is full, bottle drops at your feet
- Shows exhaustion status

Example:
```
/xpbottle 30000     # Convert 30,000 XP to bottle
/xpbottle 100000    # Convert 100,000 XP (unlimited)
```

### Admin Commands

*(To be added if needed)*

## Using XP Bottles

1. **Get a bottle**: Run `/xpbottle <amount>`
2. **See exhaustion**: Run `/xpbottle` with no args to check status
3. **Consume bottle**: Right-click the bottle in your inventory to restore the XP
4. **Trade bottle**: Drop bottle and other players can pick it up

## Configuration

Edit `config.yml` to adjust exhaustion times:

```yaml
xp-bottle:
  rank-exhaustion:
    owner: 0
    admin: 0
    mod: 1
    legend: 2
    elite: 3
    vip: 5
    default: 10
```

## How It Works

1. **Rank Detection** - Plugin reads player's primary LuckPerms group
2. **Cooldown Check** - Checks if player has exhaustion active
3. **XP Validation** - Verifies player has enough XP
4. **Conversion** - Removes XP, creates bottle, applies exhaustion
5. **Delivery** - Gives bottle to player or drops it

## Integration

SimpleEconomy can be integrated with other plugins:

```java
// Get the manager
SimpleEconomyPlugin economy = (SimpleEconomyPlugin) Bukkit.getPluginManager().getPlugin("SimpleEconomy");
XPBottleManager xpManager = economy.getXPBottleManager();

// Programmatically add XP bottles to players
xpManager.withdrawXP(player, 50000);

// Check exhaustion
if (xpManager.isExhausted(player)) {
    int remainingMins = xpManager.getRemainingExhaustionMinutes(player);
}
```

## Support

- **Requires**: LuckPerms
- **Minecraft Version**: 1.20.1+
- **Java Version**: 17+

## Troubleshooting

**Q: "LuckPerms is not loaded"**
- Make sure LuckPerms is installed and enabled before SimpleEconomy

**Q: Player can withdraw unlimited XP (no exhaustion)**
- Check LuckPerms is installed and player has a rank assigned

**Q: XP bottle doesn't work when clicked**
- Make sure you're right-clicking the bottle
- Bottles must have the display name "§b§lXP Bottle"

## Files

- `SimpleEconomyPlugin.java` - Main plugin class
- `XPBottleManager.java` - XP bottle logic and exhaustion
- `XPBottleCommand.java` - /xpbottle command
- `XPBottleListener.java` - Bottle consumption listener
- `config.yml` - Configuration file
