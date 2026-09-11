package LoginTimer;

import plugin.Plugin;
import rt4.Component;
import rt4.JagString;
import rt4.LocalLeagueBridge;

/**
 * Reuses the stock Report Abuse button as a live League-point display.
 *
 * The component remains visually native to RT4. League state stays authoritative
 * in the retained world and is exposed read-only through LocalLeagueBridge.
 * Click behavior is intentionally left separate so the future League menu can be
 * wired without coupling presentation text to a client-side state mutation.
 */
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
    public void ComponentDraw(int componentIndex, Component component, int screenX, int screenY) {
        if (component == null || component.id != REPORT_ABUSE_COMPONENT) return;

        int points = LocalLeagueBridge.points();
        if (points != lastPoints) {
            lastPoints = points;
            lastText = JagString.of(Integer.toString(points));
        }
        component.text = lastText;
    }
}
