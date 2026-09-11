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
        version = 1.1
)
public final class plugin extends Plugin {
    private static final int REPORT_ABUSE_COMPONENT = 49217565; // 751:29

    private int lastPoints = Integer.MIN_VALUE;
    private JagString lastText = JagString.of("0");

    @Override
    public void Init() {
        lastPoints = Integer.MIN_VALUE;
        lastText = JagString.of("0");
    }

    @Override
    public void Draw(long timeDelta) {
        LocalLeagueUiBridge.draw();
    }

    @Override
    public void ComponentDraw(int componentIndex, Component component, int screenX, int screenY) {
        if (component == null || component.id != REPORT_ABUSE_COMPONENT) return;

        LocalLeagueUiBridge.noteLauncherButton(
                screenX,
                screenY,
                component.width,
                component.height);

        int points = LocalLeagueBridge.points();
        if (points != lastPoints) {
            lastPoints = points;
            lastText = JagString.of(Integer.toString(points));
        }
        component.text = lastText;
    }
}
