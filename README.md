# Xenomorph (Minecraft 1.20.1 / Forge 47.3)

85 xenomorph castes (+ Predator, Engineer, facehuggers, chestbursters, eggs). Procedural models/textures, no external deps.

## Play
- Creative tab **Xenomorph**: spawn eggs, resin, tools. Build: JDK 17, `./gradlew build` (or `gradlew.bat`). Smoke tests: `./gradlew runGameTestServer`.
- **Hives** generate underground in new overworld chunks (resin cave, eggs, queen, brood, cocooned hosts). Kinds: classic, pathogen, protomorph, neomorph, red, K-Series, clone. Find them: `/xenomorph hive [list|tp]` (op) or the **Hive Locator**. **Motion Tracker** pings xenos within 48 blocks.
- **Factions** fight each other (classic, red, pathogen, neomorph, protomorph, deacon, clone, K-Series, white hybrid, rogue king, Predator/Engineer) and every other hostile mob.
- **Life cycle**: egg → facehugger → embryo → chestburster; the form depends on the host species (DNA reflex). Cut embryos out with the **Surgical Kit**.
- **Gear**: Pulse Rifle, Flamethrower, Acid-Neutralizing Suit (resin), Acid Gland / Royal Jelly drops. Xenos burn (except fire-resistant castes).
- **Behaviour**: unique attacks per caste, weight/ferocity, wall climbing, ceiling ambush, melting corpses, boss bars for giants.
- Config: `config/xenomorph-common.toml` (hive rarity, acid armor damage, ceiling ambush, damage multiplier).

Source notes and gaps: `docs/RESEARCH.md`.
