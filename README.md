# Xenomorph (Minecraft 1.20.1 / Forge 47.3)

18 castes + Facehugger / Chestburster / Egg, all procedural (no external deps, no geo files).

- **Stance**: Drone, Warrior, Praetorian, Spitter, Ravager, Lurker, Sentinel, Carrier, Predalien, Deacon, Boiler, Protomorph, Stalker switch biped⇄quadruped live (quad when sprinting at range, biped in melee). Queen, Neomorph, Newborn are biped-only; Runner (dog xeno) and Crusher are quadruped-only.
- **Attacks** (per caste, see `Caste.java`): claw swipes, double claw, tail stab, tail sweep, inner-jaw bite, grab→lift→jaw execution→throw, pounce, acid spit, screech, charge, queen tail impale, slam, egg laying.
- Acid blood (retaliation + armor corrosion + death splash), facehugger → Embryo effect → chestburster → drone/runner.
- Animation: `XenoModel.pose()` (2-bone IK gait, keyframed attacks). Everything spawns from creative tab "Xenomorph".

Dev: JDK17, `./gradlew build`, `./gradlew runGameTestServer` (smoke test of every attack).
