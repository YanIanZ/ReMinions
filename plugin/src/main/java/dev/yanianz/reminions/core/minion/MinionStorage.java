package dev.yanianz.reminions.core.minion;

import java.util.UUID;
import dev.yanianz.reminions.utils.Location3f;

public record MinionStorage(String name, UUID owner, MinionInventory inventory, Location3f location) {
}
