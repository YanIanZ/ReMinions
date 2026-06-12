package org.dev.yanianz.reminions;

import org.dev.yanianz.reminions.data.minion.template.MinionTemplate;

public final class BeeMinions {
    public static BeeMinionsAPI getAPI() { return null; }

    public interface BeeMinionsAPI {
        MinionTemplate getMinionTemplate(String id);
    }
}
