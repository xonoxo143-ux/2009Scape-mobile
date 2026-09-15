package LoginTimer;

import plugin.Plugin;
import plugin.annotations.PluginMeta;
import plugin.api.MiniMenuEntry;
import rt4.Component;
import rt4.JagString;
import rt4.LocalLeagueBridge;
import rt4.LocalLeagueUiBridge;
import rt4.LocalizedText;
import rt4.MiniMenu;

/**
 * Reuses the stock Report Abuse button as a live League-point display and
 * launcher for the local League menu.
 *
 * League state remains authoritative in the retained world. This plugin only
 * renders the point total and the RT4-side presentation surface.
 */
@PluginMeta(
        author = "2009Scape Mobile Single Player",
        description = "Displays authoritative League points and the local League menu.",
        version = 1.3
)
public final class plugin extends Plugin {
    private static final int REPORT_ABUSE_COMPONENT = 49217565; // 751:29

    private Component reportButton;
    private int lastPoints = Integer.MIN_VALUE;
    private JagString lastText = JagString.of("0");
    private boolean announcedBinding;

    @Override
    public void Init() {
        reportButton = null;
        lastPoints = Integer.MIN_VALUE;
        lastText = JagString.of("0");
        announcedBinding = false;
    }

    @Override
    public void Draw(long timeDelta) {
        // Keep the League-point launcher text current here. The actual League
        // surface is deliberately NOT drawn from Plugin.Draw(): RT4 invokes
        // that callback from client-code component 1405, before the rest of the
        // normal HUD has finished rendering.
        if (reportButton != null && reportButton.id == REPORT_ABUSE_COMPONENT) {
            int points = LocalLeagueBridge.points();
            if (points != lastPoints) {
                lastPoints = points;
                lastText = JagString.of(Integer.toString(points));
            }
            reportButton.text = lastText;
        }
    }

    @Override
    public void OnMiniMenuCreate(MiniMenuEntry[] currentEntries) {
        if (!LocalLeagueUiBridge.isOpen()) return;

        // LoginManager invokes this only after Cs1ScriptRunner has rendered the
        // complete top-level interface tree (viewport, chat, minimap, inventory,
        // tabs, etc.). Drawing here therefore makes League the final full-screen
        // UI layer instead of allowing retained HUD widgets to punch through it.
        LocalLeagueUiBridge.draw();

        // Prevent the retained hover/minimenu text from being painted over the
        // League layer later in LoginManager.method1841(). League already owns
        // all touch input while open, so only the inert Cancel entry is needed.
        MiniMenu.size = 1;
        MiniMenu.ops[0] = LocalizedText.CANCEL;
        MiniMenu.opBases[0] = JagString.EMPTY;
        MiniMenu.actions[0] = 1005;
    }

    @Override
    public void ComponentDraw(int componentIndex, Component component, int screenX, int screenY) {
        if (component == null || component.id != REPORT_ABUSE_COMPONENT) return;

        reportButton = component;
        LocalLeagueUiBridge.noteLauncherButton(
                screenX,
                screenY,
                component.width,
                component.height);

        if (!announcedBinding) {
            announcedBinding = true;
            System.out.println(
                    "SINGLEPLAYER_LEAGUE_UI: LAUNCHER_BOUND x=" + screenX
                            + " y=" + screenY
                            + " w=" + component.width
                            + " h=" + component.height);
        }
    }

    @Override
    public void ProcessCommand(String commandStr, String[] args) {
        if (commandStr != null && commandStr.equalsIgnoreCase("::league")) {
            LocalLeagueUiBridge.open();
        }
    }
}
