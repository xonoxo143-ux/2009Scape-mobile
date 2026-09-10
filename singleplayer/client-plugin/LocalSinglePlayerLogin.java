package LocalSinglePlayerLogin;

import plugin.Plugin;
import plugin.annotations.PluginMeta;

/**
 * Transitional compatibility shell.
 *
 * Socketless local login is now driven directly by rt4.NanoTimer ->
 * rt4.LocalLoginBridge. Keep this empty plugin only until the packaging workflow
 * is cleaned up; it no longer owns login state, profile loading, or lifecycle.
 */
@PluginMeta(
        author = "2009Scape Mobile Single Player",
        description = "Retired compatibility shell; local login is runtime-owned.",
        version = 3.0
)
public class plugin extends Plugin {
    @Override
    public void Update() {
        // Intentionally empty.
    }
}
