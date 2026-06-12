<div align="center">

# ⚙️ ReMinions

**High-performance Paper minion plugin for Minecraft 1.21.11+**

[![License](https://img.shields.io/badge/License-PolyForm%20Perimeter-blue?style=flat-square)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21-orange?style=flat-square&logo=openjdk)](https://adoptium.net)
[![Paper](https://img.shields.io/badge/Paper-1.21.11+-white?style=flat-square&logo=data:image/png;base64,)](https://papermc.io)
[![Version](https://img.shields.io/badge/Version-2.0.7-green?style=flat-square)](https://github.com/YanIanZ/ReMinions/releases)

</div>

---

## Features

- **5 minion categories** — Farming, Mining, Combat, Lumberjack, Fisherman
- **Modifier system** — Compactor, Auto-Sell, Orb of Expansion (x3), Enchanted Lava Bucket
- **Skin system** — per-minion cosmetic skins with level progression
- **Multi-database** — SQLite / MySQL / Redis
- **PlaceholderAPI** — `%reminions_<placeholder>%`
- **Soft integrations** — Vault, LuckPerms, AuraSkills, EcoSkills, MMOItems, ItemsAdder, CraftEngine

---

## Requirements

| Requirement | Version |
|-------------|---------|
| Paper | 1.21.11+ |
| Java | 21+ |

---

## Build

```bash
./gradlew :plugin:shadowJar
```

Output: `plugin/build/libs/ReMinions-2.0.7.jar`

---

## Commands

| Command | Alias | Permission |
|---------|-------|------------|
| `/reminions` | `/minions` | `reminions.admin` |

---

## PlaceholderAPI

| Placeholder | Description |
|-------------|-------------|
| `%reminions_player_current_minions%` | Active minion count |
| `%reminions_player_max_minions%` | Max allowed minions |
| `%reminions_player_unique_minions%` | Total unique minion types |
| `%reminions_player_total_earnings%` | Total earnings |
| `%reminions_player_needed_unique_minions%` | Needed for next reward |
| `%reminions_player_progress_next_reward%` | Progress % to next reward |
| `%reminions_player_minion_index_<n>_<field>%` | Minion at index n (status/id/owner/display) |

---

## Project Structure

```
ReMinions/
├── plugin/
│   ├── src/main/java/dev/yanianz/reminions/   # Plugin source
│   ├── src/main/resources/                    # plugin.yml + configs
│   └── src/stubs/java/                        # Legacy import migration stubs
├── nms/
│   └── v1_21_11/                              # NMS adapter (Mojang-mapped)
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

---

## License

Copyright © 2026 YanIanZ — [PolyForm Perimeter License 1.0.0](LICENSE)

> Use and modify freely. Redistribution as a competing product is prohibited.
