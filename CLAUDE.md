# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

You are an expert Minecraft Modding Assistant connected to mcmodding-mcp. DO NOT rely on your internal knowledge for modding APIs (Fabric/NeoForge) as they change frequently. ALWAYS use the available tools:

    search_fabric_docs and get_example for documentation and code patterns
    search_mappings and get_class_details for Minecraft internals and method signatures
    search_mod_examples for battle-tested implementations from popular mods

Prioritize working code examples over theoretical explanations. When dealing with Minecraft internals, use the mappings tools to get accurate parameter names and Javadocs. If the user specifies a Minecraft version, ensure all retrieved information matches that version.

BearMetalCarts is a Fabric mod for Minecraft (targeting Minecraft 26.2 / Fabric Loader 0.19.3 / Java 25) that adds
tiered minecart variants (copper, gold, netherite) craftable from vanilla minecarts, each with a different max
speed. Mod ID is `bearmetalcarts`.

## Commands

Build tooling is Gradle via Fabric Loom. Always use the wrapper.

- `./gradlew build` — compile and build the mod jar
- `./gradlew runClient` — launch a dev Minecraft client with the mod loaded (uses the `run/` directory as the game dir)
- `./gradlew runServer` — launch a dev Minecraft server with the mod loaded
- `./gradlew runDatagen` — regenerate the files under `src/main/generated/` (recipes, advancements, lang) from the
  data providers in `src/client/java/bearmetalcarts/client/`

There is no test suite (`src/test` does not exist).

`src/main/generated/` is checked into version control. After changing anything in `BearMetalCartsRecipeProvider` or
`BearMetalCartsEnglishTranslationProvider`, re-run `./gradlew runDatagen` and commit the resulting diff rather than
hand-editing the generated JSON.

## Architecture

### Source set split

Fabric Loom's `splitEnvironmentSourceSets()` is enabled, so code is split across two source sets declared in
`build.gradle`:

- `src/main/java` — logic that must run on both client and server (registration, mixins, attribute/component
  definitions). Entrypoint: `bearmetalcarts.BearMetalCarts` (`ModInitializer`).
- `src/client/java` — client-only code, including all datagen providers. Entrypoints:
  `bearmetalcarts.client.BearMetalCartsClient` (`ClientModInitializer`) and
  `bearmetalcarts.client.BearMetalCartsDataGenerator` (`DataGeneratorEntrypoint`).

Both are wired together as one logical mod via the `loom.mods { "bearmetalcarts" { ... } }` block.

### Core mechanism: a custom attribute on minecarts

Vanilla `AbstractMinecart` has no `AttributeMap` (that's a `LivingEntity` concept), so the mod bolts one on via
mixins rather than subclassing minecart entities:

- `ModAttributes` registers a custom `minecart_speed` `RangedAttribute` (default 0.4, range 0–1024, synced to
  client).
- `AttributeHolderMinecart` is an interface (`bearmetalcarts$getAttributeMap()`) that mixed-in minecarts implement,
  giving other code a typed way to reach the injected attribute map without casting to the mixin class directly.
- `AbstractMinecartAttributeMixin` (mixes into `AbstractMinecart`) lazily creates the `AttributeMap`, overrides
  `getMaxSpeed` to return the attribute's value, and persists the attribute map through
  `addAdditionalSaveData`/`readAdditionalSaveData` (NBT key `BearMetalCartsAttributes`).
- `AttributeCommandMixin` `@Overwrite`s several private static helpers in vanilla's `/attribute` command
  (`AttributeCommand`) so they resolve `AttributeHolderMinecart` targets in addition to `LivingEntity`. This is a
  single choke-point widening (`getAttributeInstance`) that every other command branch depends on — see the
  javadoc on that class before changing it, since `@Overwrite` on vanilla code is a maintenance-sensitive mixin
  and needs to stay in sync with `AttributeCommand`'s actual private method signatures.

### Setting per-item speed via a data component

- `ModComponents` registers a persistent `DataComponentType<Double>` (`minecart_speed`) that can be attached to a
  `MinecartItem` `ItemStack`.
- `MinecartItemMixin` hooks `MinecartItem#useOn` to read that component off the placed item's stack and, if
  present, set it as the *base value* of the newly spawned entity's `minecart_speed` attribute instance (via
  `AttributeHolderMinecart`). This is how a crafted "gold minecart" item ends up as a faster entity in the world.

### Push-chain collision solver

`PushChainSolver` replaces vanilla cart-on-cart collisions with a pairwise inelastic solve in *speeds along each
cart's own heading* (`s = (m1*s1 + m2*s2)/(m1+m2)`; scalar, not vector — componentwise averaging on curves smears
speed across axes and causes juttery cornering), run once per cart per server tick from
`NewMinecartBehaviorPushChainMixin` (head of `NewMinecartBehavior.moveAlongTrack`, so it's implicitly gated
behind `minecart_improvements` like the rest of the physics). Each moving cart walks its own rail path a bounded
`lookaheadBlocks(speed)` ahead (following rail exits, stopping where connectivity breaks, e.g. entering the back
of a curve) and solves against the nearest cart it is about to touch — with distance measured *along the walked
path* and the front cart's speed/launch direction signed against the path's direction at its block, never
center-to-center geometry, which lies around hairpins (parallel legs a block apart in space, several apart on the
track) and used to nudge the leading cart backwards there.

The shared speed is
capped at the front cart's own `getMaxSpeed` (widened in the accesswidener, together with the `MinecartFurnace`
override — an AW'd base method with a still-protected override fails class-loading), which is what stops a fast
furnace cart from perpetually ramming a slower cart it can't actually push past that cart's cap. No chain state
is stored — trains are emergent from the rule re-firing every tick: pairs inside `HOLD_DISTANCE` re-solve every
closing tick, so a pressing furnace cart's engine thrust (re-applied per tick by `MinecartFurnaceEngineMixin`)
flows into the coupled pair scaled by mass.

**Keeping coupled carts from interpenetrating.** Everything here was found by measurement, and every wrong turn
came from mixing pre-movement and post-movement positions — the solve runs at the *head* of a cart's
`moveAlongTrack`, so at that moment the rear cart has definitely not moved yet while the front cart may already
have, since entity tick order is arbitrary. Three fixes, in the order they mattered:

1. **The velocity solve was never firing for a pushing furnace at all.** Its trigger predicted contact with the
   pair's *closing rate*, but mid-tick `distance` is inflated by exactly one tick of the front cart's travel when
   that cart has already moved (measured: a pair at a true 1.12 reported 1.329 = 1.12 + the front cart's 0.209,
   and `fires=false` every single tick). The train was therefore never velocity-coupled — it was only being
   dragged along by the positional correction. The trigger now predicts with `distance - s1`, the rear cart's own
   imminent movement, which is the honest worst case and does not depend on tick order.
2. **The standoff must be restored after movement, never inside a cart's own tick.** Correcting at the head of a
   tick sets the gap and then lets that cart travel a full `s1` through it, leaving the player-visible gap at
   `HOLD_DISTANCE - s1` — measured to the digit as a rock-steady 0.7132 for a furnace running 0.4068 (1.12 -
   0.4068), permanently a quarter-block inside the cart ahead. Which cart of the pair got moved was irrelevant.
   `enforceSpacing` now drains a per-tick queue from `END_SERVER_TICK`, where positions are final.
3. **`enforceSpacing` settles back-to-front.** A correction moves the pair's *front* cart, which is the *rear* of
   the next pair up the train, so front-first ordering leaves every pair but the last one broken (measured: only
   the last-corrected pair read 1.12, the rest sagged to ~1.0). Sorting by front-cart position ascending means
   each correction is computed from an already-final rear cart and the chain settles in one pass.

`markCoupledCap` additionally feeds the furnace engine the speed the solve agreed on, plus one tick of wind-up
scaled by the engine's share of coupled mass, rather than the cart-ahead's *max* speed. A max-based cap was a
no-op whenever a cart travels below its max (a blocked netherite cart crawling at 0.39 may still legally do 1.2),
so it only ever worked when max happened to equal actual — i.e. all-vanilla carts. The wind-up term is
load-bearing: `shared` is always below the rear cart's speed while closing, so a flat `shared` cap is a ratchet
that walks a train's speed monotonically *down* (measured: free-track train decaying 0.13 → 0.07 instead of
climbing to 0.6). `AbstractMinecartPushMixin` cancels vanilla's `pushOtherMinecart` *and*
the hard cart-cart entity collision for on-rails carts: the hard collision must stay off because a cart whose
move is fully blocked by another's box gets its velocity zeroed by `stepAlongTrack`'s pinned rule (entity tick
order is arbitrary, so a settling chain's rear cart randomly hit this and stopped dead). The *same* mixin also
cancels collision against a coupled cart's **passenger** (`bearmetalcarts$isRidingCoupledCart`) — `AbstractBoat
.canVehicleCollide` (which `AbstractMinecart.canCollideWith` delegates to) treats any `isPushable()` entity as
solid regardless of whether it's riding something, so a mounted player/mannequin was a physical obstacle to an
*approaching* cart even with cart-cart collision already disabled. This was the actual cause of the "pushing an
occupied cart bunches and lurches" bug: the approaching cart's move got blocked by the rider's own hitbox,
hitting the same `stepAlongTrack` zero-velocity clamp, independent of anything the solver does — only visible
with a passenger aboard because an empty cart has no rider hitbox to hit. Diagnosed by instrumenting
`PushChainSolver.tick`/`solve` with temporary debug logging (tick, target, distance, closing) to catch a cart's
velocity hard-zeroing mid-tick, then ruling out the geometric nudge via an A/B (still reproduced with the nudge
no-op'd), then finding `AbstractBoat.canVehicleCollide`'s `entity.isPushable()` clause in vanilla source. Masses
come from the `mass` key in the `BearMetalCarts` compound (stamped by tiered items, `/bmc <sel> mass`),
defaulting by cart type in `BearMetalCartsData` (furnace 12 > chest 8 > hopper 6 > other 4, tier
multipliers 1.25/1.5/2).

### Tiered recipes (datagen)

`BearMetalCartsRecipeProvider.minecartSpeeds` is the single source of truth for the three tiers (copper/gold/
netherite): crafting material, speed value, material cost, name key, and a fallback display-name formatter. For
every vanilla item whose id contains `minecart` (chest minecart, hopper minecart, etc., but not
`minecart_command`... actually excludes ids containing `command`), the provider generates one shapeless recipe per
tier that takes the vanilla cart + N of the tier material and outputs an `ItemStackTemplate` with the
`minecart_speed` component and a custom display name set via `DataComponentPatch`. `BearMetalCartsEnglishTranslationProvider`
walks the same `minecartSpeeds` list to generate matching lang entries (key pattern
`bearmetalcarts.carts.<tier>.<vanilla_cart_id>`).

Both providers are registered in `BearMetalCartsDataGenerator` and only run under `runDatagen`.
