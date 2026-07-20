# BearMetalCarts

A Fabric mod for Minecraft that enhances minecarts with tiered variants and improved physics for furnace carts.

## Overview

BearMetalCarts adds three tiered minecart variants to Minecraft, craftable from vanilla minecarts and upgrade materials:

- **Copper Minecart** (0.6 blocks/tick max speed)
- **Gold Minecart** (0.8 blocks/tick max speed)
- **Netherite Minecart** (1.2 blocks/tick max speed)

Each variant can be crafted as a furnace minecart, hopper minecart, chest minecart, TNT minecart, or command minecart.

### Furnace Minecart Engine

The mod introduces realistic physics to furnace minecarts, replacing vanilla's instant acceleration and inability to stop on brake rails:

- **Wind-up acceleration**: Furnace carts ramp up to their max speed over a distance, rather than jumping to speed instantly
- **Braking**: Furnace carts gradually slow down on powered rails with the brake setting, coming to a complete stop instead of settling at equilibrium
- **Fuel efficiency**: Stopped furnace carts no longer burn fuel needlessly, allowing them to idle at stations

### Push-Chain Collisions

The mod replaces vanilla's erratic minecart-on-minecart collisions with a momentum-conserving solver (all carts,
not just furnace carts; requires the same *Minecart Improvements* experiment as the tiered speeds):

- **Inelastic collisions**: When a cart catches up to another on the same track, both speeds are blended toward
  the shared speed `(m₁s₁ + m₂s₂) / (m₁ + m₂)`, each along its own heading — carts never bounce apart, and never
  stop dead on contact. A pushed cart is never driven past its own max speed: a fast furnace cart behind a slower
  cart settles in behind it at the slower cart's pace.
- **Carts keep their spacing**: coupled carts hold a fixed standoff rather than sinking into each other, including
  a furnace cart pushing a train of them at netherite speed. The standoff is restored once per tick after every
  cart has moved, so nothing can eat into it, and a train settles back-to-front in a single pass.
- **Powered pushing**: A fueled furnace cart pressed against carts ahead re-couples with them every tick, so its
  engine's thrust flows into the whole train — divided across the coupled mass, meaning heavier trains wind up
  proportionally slower but still reach the engine's speed.
- **Mass matters**: Default masses are 4 (regular/TNT/spawner), 6 (hopper), 8 (chest), and 12 (furnace); tiered
  carts are heavier by ×1.25/×1.5/×2 for copper/gold/netherite. A furnace cart plows a bare cart along with
  little lost speed; a bare cart rear-ending a furnace cart mostly just stops. Mass is stored per cart as
  `BearMetalCarts.mass` and can be read or written with `/bmc <targets> mass [<value>]`.
- **Emergent trains**: There is no coupling mechanic — a "train" is just adjacent carts continuously agreeing on
  velocity, re-solved pairwise every tick. A shove into a line of carts ripples down it over a few ticks, like
  slack running out of real couplers, and a furnace cart pressed against a line of carts pushes the whole line.
- **Track-aware**: Carts only interact along connected rail (a short lookahead along the path the cart would
  actually take), with separation measured as travel distance along that path — so a cart parked on a diverging
  branch doesn't disturb traffic passing it, and carts on the parallel legs of a hairpin don't couple across the
  gap; trains push cleanly around hairpins.
- **Passengers ride along cleanly**: pushing an occupied cart (a player or a mannequin) no longer causes the train
  to bunch up and lurch — that turned out to be vanilla's own rider collision (a mounted passenger is a solid
  body to *other* entities' movement, cart or not) fighting the solver from underneath; the fix disables it for
  coupled on-rail carts the same way cart-on-cart collision already was.

## Furnace Minecart Specifications

### A note on "max speed"

Each tier sets a *rail speed cap* on the cart (0.6/0.8/1.2 blocks/tick for copper/gold/netherite — see
`BearMetalCartsRecipeProvider.minecartSpeeds`). That's the fastest the cart can go, e.g. boosted along by powered
rails. A furnace cart's own engine, however, is weaker than that: vanilla's `MinecartFurnace.getMaxSpeed` always
halves whatever cap it's given before deciding how hard to push (¼ less severe — ×0.75 instead of ×0.5 — when the
cart is in water), and BearMetalCarts doesn't override that. So the speed the wind-up/braking curves below actually
ramp to and from is **half the tier's rail cap on land**, not the cap itself.

### Tiered Speed and Distance Specs (on land)

| Tier | Rail Speed Cap | Effective Engine Speed | Stopping Distance | Acceleration Distance | Acceleration Time |
|------|-----------------|------------------------|--------------------|------------------------|--------------------|
| Vanilla | 0.4 blocks/tick | 0.2 blocks/tick | 3 blocks | 6 blocks | 60 ticks (3 seconds) |
| Copper | 0.6 blocks/tick | 0.3 blocks/tick | 4.5 blocks | 9 blocks | 60 ticks (3 seconds) |
| Gold | 0.8 blocks/tick | 0.4 blocks/tick | 6 blocks | 12 blocks | 60 ticks (3 seconds) |
| Netherite | 1.2 blocks/tick | 0.6 blocks/tick | 9 blocks | 18 blocks | 60 ticks (3 seconds) |

"Vanilla" here means an un-upgraded furnace minecart, whose rail cap is the `max_minecart_speed` game rule's default
(8, i.e. 0.4 blocks/tick) rather than a BearMetalCarts tier.

### Physics Equations

The distance-from-velocity relationship is consistent across all tiers, scaling linearly with the cart's *effective
engine speed* (rail cap ÷ 2 on land, ÷ 1.333 in water — not the raw tier speed):

**Stopping Distance (Braking):**
```
distance (blocks) = effective engine speed (blocks/tick) × 15
```

For example, a Netherite furnace cart has a 1.2 blocks/tick rail cap but a 0.6 blocks/tick effective engine speed,
so it stops within 0.6 × 15 = 9 blocks.

**Acceleration Distance (Wind-up):**
```
distance (blocks) = effective engine speed (blocks/tick) × 30
```

A Netherite furnace cart needs 0.6 × 30 = 18 blocks to wind up from a standstill to its effective engine speed.

**Braking Curve:**
The braking mechanism uses a geometric decay model where speed is multiplied by a retention factor each tick:
```
speed_retention_per_tick = 1 - (1/15) ≈ 0.9333
```

This means each tick the cart retains about 93.33% of its current speed while braking. The sum of this infinite geometric series equals the stopping distance constant of 15, so any cart can be modeled as:
```
ticks_to_stop ≈ ln(speed / stop_threshold) / ln(retention_factor)
```

## Setup

For development setup instructions, please see the [Fabric Documentation page](https://docs.fabricmc.net/develop/getting-started/creating-a-project#setting-up) related to the IDE that you are using.

## Building and Running

- `./gradlew build` — compile and build the mod jar
- `./gradlew runClient` — launch a dev Minecraft client with the mod loaded
- `./gradlew runServer` — launch a dev Minecraft server with the mod loaded
- `./gradlew runDatagen` — regenerate data files (recipes, advancements, lang) from data providers

## License

This mod is covered under the GPL 3.0 license.
