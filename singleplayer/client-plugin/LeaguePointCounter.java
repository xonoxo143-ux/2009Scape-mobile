package LoginTimer;

import plugin.Plugin;
import plugin.annotations.PluginMeta;
import rt4.Component;
import rt4.JagString;
import rt4.LocalLeagueBridge;
import rt4.LocalLeagueUiBridge;

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
        version = 1.2
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
        // The stock LoginTimer plugin also keeps a reference to this component
        // from ComponentDraw and mutates its text from Draw. Doing the mutation
        // here is important because the normal interface renderer may rewrite the
        // stock "Report Abuse" text during the component draw itself.
        if (reportButton != null && reportButton.id == REPORT_ABUSE_COMPONENT) {
            int points = LocalLeagueBridge.points();
            if (points != lastPoints) {
                lastPoints = points;
                lastText = JagString.of(Integer.toString(points));
            }
            reportButton.text = lastText;
        }

        LocalLeagueUiBridge.draw();
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
