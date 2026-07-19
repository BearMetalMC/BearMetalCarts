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
