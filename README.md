# ReMinions

A high-performance Paper minion plugin built for Minecraft 1.21.11+.

## Features

- Multiple minion types: farming, mining, combat, lumberjack, fisherman
- Modifier system: compactor, auto-sell, orb of expansion, enchanted lava bucket
- Skin system with per-minion cosmetics
- SQLite / MySQL / Redis database backends
- PlaceholderAPI support (`%reminions_*%`)
- Soft integrations: Vault, LuckPerms, AuraSkills, EcoSkills, MMOItems, ItemsAdder, CraftEngine

## Requirements

- Paper 1.21.11+
- Java 21

## Build

```bash
./gradlew :plugin:shadowJar
# Output: plugin/build/libs/ReMinions-2.0.7.jar
```

## Layout

```
.
├── plugin/                  Main plugin sources (Java 21)
│   ├── src/main/java/       dev.yanianz.reminions package
│   ├── src/main/resources/  plugin.yml + config + data files
│   └── src/stubs/java/      Compile-only stubs for old BeeMinions import migration
├── nms/
│   └── v1_21_11/            NMS adapter for 1.21.11 (paperweight Mojang-mapped)
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

## Commands

| Command | Alias | Description |
|---------|-------|-------------|
| `/reminions` | `/minions` | Main command |

## License

PolyForm Perimeter License 1.0.0 — see [LICENSE](LICENSE).

Copyright YanIanZ 2026
