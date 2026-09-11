package rt4;

import plugin.api.API;
import singleplayer.MobileGestureBridge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Local-only League presentation state.
 *
 * This class deliberately owns presentation state only. Authoritative League
 * progress continues to live in the retained world and is read through
 * LocalLeagueBridge.
 */
public final class LocalLeagueUiBridge {
    public static final int TAB_TASKS = 0;
    public static final int TAB_RELICS = 1;
    public static final int TAB_BLESSINGS = 2;

    private static final int COLOR_DIM = 0x000000;
    private static final int COLOR_FRAME = 0x8c7651;
    private static final int COLOR_BG = 0x241e17;
    private static final int COLOR_HEADER = 0x3b3124;
    private static final int COLOR_TAB = 0x332a20;
    private static final int COLOR_TAB_ACTIVE = 0x6a5737;
    private static final int COLOR_PANEL = 0x2c251c;
    private static final int COLOR_PANEL_ALT = 0x352d22;
    private static final int COLOR_TEXT = 0xe7d7ad;
    private static final int COLOR_MUTED = 0xb2a383;
    private static final int COLOR_GOLD = 0xffd37a;
    private static final int COLOR_GREEN = 0x7bae4d;
    private static final int COLOR_SCROLL_TRACK = 0x17130f;
    private static final int COLOR_SCROLL_THUMB = 0x9b8457;

    private static volatile boolean open;
    private static volatile int activeTab = TAB_TASKS;

    private static volatile int launcherX = -1;
    private static volatile int launcherY = -1;
    private static volatile int launcherWidth;
    private static volatile int launcherHeight;

    private static int tasksScroll;
    private static int relicsScroll;
    private static int blessingsScroll;
    private static boolean draggingContent;
    private static int dragTab = -1;

    private static boolean announcedOpen;
    private static boolean announcedDraw;

    private LocalLeagueUiBridge() {}

    public static boolean isOpen() {
        return open;
    }

    public static int activeTab() {
        return activeTab;
    }

    public static void noteLauncherButton(int x, int y, int width, int height) {
        if (width <= 0 || height <= 0) return;
        launcherX = x;
        launcherY = y;
        launcherWidth = width;
        launcherHeight = height;
    }

    public static void open() {
        open = true;
        draggingContent = false;
        dragTab = -1;
        if (!announcedOpen) {
            announcedOpen = true;
            System.out.println("SINGLEPLAYER_LEAGUE_UI: OPEN");
        }
    }

    public static void close() {
        if (!open) return;
        open = false;
        draggingContent = false;
        dragTab = -1;
        announcedDraw = false;
        System.out.println("SINGLEPLAYER_LEAGUE_UI: CLOSE");
    }

    /**
     * Called from MobileGestureBridge before the normal RT4 touch plugin. When
     * this returns true the gesture belongs to the League UI and must not become
     * a world click/camera drag.
     */
    public static boolean handleGesture(int type, int x, int y, int value1, int value2) {
        if (!open) {
            if ((type == MobileGestureBridge.TAP || type == MobileGestureBridge.LONG_PRESS)
                    && inside(x, y, launcherX, launcherY, launcherWidth, launcherHeight)) {
                open();
                return true;
            }
            return false;
        }

        Layout layout = layout();

        if (type == MobileGestureBridge.TAP || type == MobileGestureBridge.LONG_PRESS) {
            if (inside(x, y, layout.closeX, layout.closeY, layout.closeW, layout.closeH)) {
                close();
                return true;
            }

            for (int tab = 0; tab < 3; tab++) {
                int tx = layout.tabsX + tab * (layout.tabW + 4);
                if (inside(x, y, tx, layout.tabsY, layout.tabW, layout.tabH)) {
                    activeTab = tab;
                    draggingContent = false;
                    dragTab = -1;
                    return true;
                }
            }
            return true;
        }

        if (type == MobileGestureBridge.DRAG_BEGIN) {
            draggingContent = inside(
                    x,
                    y,
                    layout.contentX,
                    layout.contentY,
                    layout.contentW,
                    layout.contentH);
            dragTab = draggingContent ? activeTab : -1;
            return true;
        }

        if (type == MobileGestureBridge.DRAG_MOVE) {
            if (draggingContent && dragTab == activeTab) {
                scrollActiveTab(-value2, layout);
            }
            return true;
        }

        if (type == MobileGestureBridge.DRAG_END || type == MobileGestureBridge.CANCEL) {
            draggingContent = false;
            dragTab = -1;
            return true;
        }

        // Pinch and any future touch gestures are swallowed while the modal is
        // open so they cannot move the camera or alter the game behind it.
        return true;
    }

    public static void draw() {
        if (!open) return;

        Layout layout = layout();
        if (layout.width <= 0 || layout.height <= 0) return;

        if (!announcedDraw) {
            announcedDraw = true;
            System.out.println(
                    "SINGLEPLAYER_LEAGUE_UI: DRAW " + layout.width + "x" + layout.height);
        }

        API.FillRect(0, 0, GameShell.canvasWidth, GameShell.canvasHeight, COLOR_DIM, 150);

        API.FillRect(layout.x, layout.y, layout.width, layout.height, COLOR_BG, 0);
        API.DrawRect(layout.x, layout.y, layout.width, layout.height, COLOR_FRAME);
        API.DrawRect(layout.x + 1, layout.y + 1, layout.width - 2, layout.height - 2, 0x4c402e);

        API.FillRect(layout.x + 2, layout.y + 2, layout.width - 4, 26, COLOR_HEADER, 0);
        textLarge("LEAGUE", layout.x + 12, layout.y + 19, COLOR_GOLD);

        API.FillRect(layout.closeX, layout.closeY, layout.closeW, layout.closeH, 0x6f2e21, 0);
        API.DrawRect(layout.closeX, layout.closeY, layout.closeW, layout.closeH, 0xb78c62);
        textLarge("X", layout.closeX + 7, layout.closeY + 16, 0xffffff);

        drawTabs(layout);
        drawSummary(layout);

        switch (activeTab) {
            case TAB_RELICS:
                drawRelics(layout);
                break;
            case TAB_BLESSINGS:
                drawBlessings(layout);
                break;
            default:
                drawTasks(layout);
                break;
        }
    }

    private static void drawTabs(Layout layout) {
        String[] names = {"Tasks", "Relics", "Blessings"};
        for (int i = 0; i < names.length; i++) {
            int tx = layout.tabsX + i * (layout.tabW + 4);
            API.FillRect(
                    tx,
                    layout.tabsY,
                    layout.tabW,
                    layout.tabH,
                    i == activeTab ? COLOR_TAB_ACTIVE : COLOR_TAB,
                    0);
            API.DrawRect(tx, layout.tabsY, layout.tabW, layout.tabH, COLOR_FRAME);
            textLarge(names[i], tx + 12, layout.tabsY + 20, i == activeTab ? COLOR_GOLD : COLOR_TEXT);
        }
    }

    private static void drawSummary(Layout layout) {
        int points = LocalLeagueBridge.points();
        int tasks = LocalLeagueBridge.completedTasks().size();
        int relics = LocalLeagueBridge.unlockedRelics().size();

        API.FillRect(layout.summaryX, layout.summaryY, layout.summaryW, layout.summaryH, 0x1d1914, 0);
        API.DrawRect(layout.summaryX, layout.summaryY, layout.summaryW, layout.summaryH, 0x5d4f39);
        textLarge("League points: " + points, layout.summaryX + 10, layout.summaryY + 20, COLOR_GOLD);
        textSmall(
                "Completed tasks: " + tasks + "    Unlocked relics: " + relics,
                layout.summaryX + 215,
                layout.summaryY + 19,
                COLOR_MUTED);
    }

    private static void drawTasks(Layout layout) {
        int sidebarW = Math.min(174, layout.contentW / 3);
        int gap = 8;
        int listX = layout.contentX + sidebarW + gap;
        int listW = layout.contentW - sidebarW - gap;

        API.FillRect(layout.contentX, layout.contentY, sidebarW, layout.contentH, COLOR_PANEL, 0);
        API.DrawRect(layout.contentX, layout.contentY, sidebarW, layout.contentH, 0x5d4f39);
        textLarge("Tasks", layout.contentX + 10, layout.contentY + 21, COLOR_GOLD);
        textSmall("View", layout.contentX + 10, layout.contentY + 46, COLOR_MUTED);
        drawStaticSelector(layout.contentX + 10, layout.contentY + 52, sidebarW - 20, "Completed");
        textSmall("Tier", layout.contentX + 10, layout.contentY + 92, COLOR_MUTED);
        drawStaticSelector(layout.contentX + 10, layout.contentY + 98, sidebarW - 20, "All");
        textSmall("Type", layout.contentX + 10, layout.contentY + 138, COLOR_MUTED);
        drawStaticSelector(layout.contentX + 10, layout.contentY + 144, sidebarW - 20, "All");
        textSmall("Drag the list to scroll.", layout.contentX + 10, layout.contentY + layout.contentH - 18, COLOR_MUTED);

        API.FillRect(listX, layout.contentY, listW, layout.contentH, COLOR_PANEL, 0);
        API.DrawRect(listX, layout.contentY, listW, layout.contentH, 0x5d4f39);

        List<String> tasks = sorted(LocalLeagueBridge.completedTasks());
        textLarge("Completed tasks (" + tasks.size() + ")", listX + 10, layout.contentY + 21, COLOR_GOLD);

        int rowsTop = layout.contentY + 31;
        int rowsH = layout.contentH - 39;
        int rowH = 32;
        int maxScroll = Math.max(0, tasks.size() * rowH - rowsH);
        tasksScroll = clamp(tasksScroll, 0, maxScroll);

        if (tasks.isEmpty()) {
            textLarge("No League tasks completed yet.", listX + 16, rowsTop + 34, COLOR_TEXT);
            textSmall(
                    "The task catalogue will populate as the League ruleset is installed.",
                    listX + 16,
                    rowsTop + 56,
                    COLOR_MUTED);
        } else {
            for (int i = 0; i < tasks.size(); i++) {
                int ry = rowsTop + i * rowH - tasksScroll;
                if (ry < rowsTop || ry + rowH > rowsTop + rowsH) continue;
                API.FillRect(listX + 7, ry, listW - 21, rowH - 3,
                        (i & 1) == 0 ? COLOR_PANEL_ALT : 0x30281f, 0);
                API.DrawRect(listX + 7, ry, listW - 21, rowH - 3, 0x4d4332);
                textLarge("✓", listX + 14, ry + 20, COLOR_GREEN);
                textLarge(fit(pretty(tasks.get(i)), listW - 58), listX + 34, ry + 20, COLOR_TEXT);
            }
        }

        drawScrollbar(listX + listW - 10, rowsTop, 6, rowsH, tasksScroll, maxScroll);
    }

    private static void drawRelics(Layout layout) {
        int passiveW = Math.min(205, layout.contentW / 3);
        int gap = 8;
        int leftW = layout.contentW - passiveW - gap;
        int passiveX = layout.contentX + leftW + gap;

        API.FillRect(layout.contentX, layout.contentY, leftW, layout.contentH, COLOR_PANEL, 0);
        API.DrawRect(layout.contentX, layout.contentY, leftW, layout.contentH, 0x5d4f39);
        textLarge("Relics", layout.contentX + 10, layout.contentY + 21, COLOR_GOLD);

        List<String> relics = sorted(LocalLeagueBridge.unlockedRelics());
        int rowsTop = layout.contentY + 31;
        int rowsH = layout.contentH - 39;
        int cardH = 54;
        int maxScroll = Math.max(0, relics.size() * cardH - rowsH);
        relicsScroll = clamp(relicsScroll, 0, maxScroll);

        if (relics.isEmpty()) {
            textLarge("No relics unlocked yet.", layout.contentX + 16, rowsTop + 34, COLOR_TEXT);
            textSmall(
                    "Unlocked relics will appear here as the League rules become active.",
                    layout.contentX + 16,
                    rowsTop + 56,
                    COLOR_MUTED);
        } else {
            for (int i = 0; i < relics.size(); i++) {
                int ry = rowsTop + i * cardH - relicsScroll;
                if (ry < rowsTop || ry + cardH > rowsTop + rowsH) continue;
                API.FillRect(layout.contentX + 8, ry, leftW - 22, cardH - 5, COLOR_PANEL_ALT, 0);
                API.DrawRect(layout.contentX + 8, ry, leftW - 22, cardH - 5, 0x6f5e41);
                textLarge(fit(pretty(relics.get(i)), leftW - 56), layout.contentX + 18, ry + 22, COLOR_GOLD);
                textSmall("Unlocked", layout.contentX + 18, ry + 40, COLOR_GREEN);
            }
        }
        drawScrollbar(layout.contentX + leftW - 10, rowsTop, 6, rowsH, relicsScroll, maxScroll);

        API.FillRect(passiveX, layout.contentY, passiveW, layout.contentH, 0x272119, 0);
        API.DrawRect(passiveX, layout.contentY, passiveW, layout.contentH, 0x5d4f39);
        textLarge("Passive effects", passiveX + 10, layout.contentY + 21, COLOR_GOLD);
        textSmall("Tier-wide bonuses live here.", passiveX + 10, layout.contentY + 49, COLOR_TEXT);
        textSmall("They are kept separate from", passiveX + 10, layout.contentY + 66, COLOR_MUTED);
        textSmall("the selected relic's effects.", passiveX + 10, layout.contentY + 82, COLOR_MUTED);
        textSmall("The ruleset will supply the", passiveX + 10, layout.contentY + 112, COLOR_MUTED);
        textSmall("actual tier effect text.", passiveX + 10, layout.contentY + 128, COLOR_MUTED);
    }

    private static void drawBlessings(Layout layout) {
        API.FillRect(layout.contentX, layout.contentY, layout.contentW, layout.contentH, COLOR_PANEL, 0);
        API.DrawRect(layout.contentX, layout.contentY, layout.contentW, layout.contentH, 0x5d4f39);
        textLarge("Blessings", layout.contentX + 12, layout.contentY + 22, COLOR_GOLD);
        textLarge("Blessing state is the next authoritative runtime seam.",
                layout.contentX + 18, layout.contentY + 62, COLOR_TEXT);
        textSmall(
                "This page is already touch-modal and scroll-ready; choices, task progress and resets",
                layout.contentX + 18,
                layout.contentY + 88,
                COLOR_MUTED);
        textSmall(
                "will be populated from the retained world rather than stored in the client.",
                layout.contentX + 18,
                layout.contentY + 105,
                COLOR_MUTED);

        int sampleY = layout.contentY + 145 - blessingsScroll;
        API.FillRect(layout.contentX + 18, sampleY, layout.contentW - 36, 54, COLOR_PANEL_ALT, 0);
        API.DrawRect(layout.contentX + 18, sampleY, layout.contentW - 36, 54, 0x5d4f39);
        textLarge("No blessing choices have been installed yet.", layout.contentX + 30, sampleY + 23, COLOR_TEXT);
        textSmall("No fake client-side state is being shown.", layout.contentX + 30, sampleY + 42, COLOR_MUTED);
    }

    private static void drawStaticSelector(int x, int y, int width, String value) {
        API.FillRect(x, y, width, 25, 0x211c16, 0);
        API.DrawRect(x, y, width, 25, 0x66563d);
        textLarge(value, x + 8, y + 18, COLOR_TEXT);
    }

    private static void drawScrollbar(
            int x,
            int y,
            int width,
            int height,
            int scroll,
            int maxScroll) {
        API.FillRect(x, y, width, height, COLOR_SCROLL_TRACK, 0);
        if (maxScroll <= 0) {
            API.FillRect(x, y, width, height, COLOR_SCROLL_THUMB, 0);
            return;
        }
        int thumbH = Math.max(24, height * height / (height + maxScroll));
        int thumbY = y + (height - thumbH) * scroll / maxScroll;
        API.FillRect(x, thumbY, width, thumbH, COLOR_SCROLL_THUMB, 0);
    }

    private static void scrollActiveTab(int delta, Layout layout) {
        if (delta == 0) return;
        if (activeTab == TAB_TASKS) {
            int rowsH = layout.contentH - 39;
            int max = Math.max(0, LocalLeagueBridge.completedTasks().size() * 32 - rowsH);
            tasksScroll = clamp(tasksScroll + delta, 0, max);
        } else if (activeTab == TAB_RELICS) {
            int rowsH = layout.contentH - 39;
            int max = Math.max(0, LocalLeagueBridge.unlockedRelics().size() * 54 - rowsH);
            relicsScroll = clamp(relicsScroll + delta, 0, max);
        } else {
            blessingsScroll = clamp(blessingsScroll + delta, 0, 90);
        }
    }

    private static Layout layout() {
        int canvasW = GameShell.canvasWidth;
        int canvasH = GameShell.canvasHeight;

        int width = Math.min(700, Math.max(420, canvasW - 30));
        int height = Math.min(455, Math.max(300, canvasH - 24));
        int x = Math.max(0, (canvasW - width) / 2);
        int y = Math.max(0, (canvasH - height) / 2);

        Layout l = new Layout();
        l.x = x;
        l.y = y;
        l.width = width;
        l.height = height;
        l.closeW = 22;
        l.closeH = 20;
        l.closeX = x + width - l.closeW - 7;
        l.closeY = y + 5;
        l.tabsX = x + 12;
        l.tabsY = y + 34;
        l.tabW = Math.min(116, (width - 40) / 4);
        l.tabH = 27;
        l.summaryX = x + 12;
        l.summaryY = y + 67;
        l.summaryW = width - 24;
        l.summaryH = 30;
        l.contentX = x + 12;
        l.contentY = y + 104;
        l.contentW = width - 24;
        l.contentH = height - 116;
        return l;
    }

    private static List<String> sorted(Set<String> values) {
        if (values == null || values.isEmpty()) return Collections.emptyList();
        ArrayList<String> list = new ArrayList<String>(values);
        Collections.sort(list);
        return list;
    }

    private static String pretty(String id) {
        if (id == null || id.isEmpty()) return "Unknown";
        String raw = id.replace('_', ' ').replace('-', ' ').trim();
        if (raw.isEmpty()) return id;
        StringBuilder out = new StringBuilder(raw.length());
        boolean upper = true;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == ' ') {
                upper = true;
                out.append(c);
            } else if (upper) {
                out.append(Character.toUpperCase(c));
                upper = false;
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static String fit(String value, int maxWidth) {
        if (value == null) return "";
        JagString full = JagString.of(value);
        if (Fonts.p12Full.getStringWidth(full) <= maxWidth) return value;
        String suffix = "...";
        for (int end = value.length() - 1; end > 0; end--) {
            String candidate = value.substring(0, end) + suffix;
            if (Fonts.p12Full.getStringWidth(JagString.of(candidate)) <= maxWidth) {
                return candidate;
            }
        }
        return suffix;
    }

    private static void textLarge(String value, int x, int y, int color) {
        Fonts.p12Full.renderLeft(JagString.of(value), x, y, color, -1);
    }

    private static void textSmall(String value, int x, int y, int color) {
        Fonts.p11Full.renderLeft(JagString.of(value), x, y, color, -1);
    }

    private static boolean inside(int x, int y, int rx, int ry, int rw, int rh) {
        return rx >= 0 && ry >= 0 && rw > 0 && rh > 0
                && x >= rx && y >= ry && x < rx + rw && y < ry + rh;
    }

    private static int clamp(int value, int min, int max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }

    private static final class Layout {
        int x;
        int y;
        int width;
        int height;
        int closeX;
        int closeY;
        int closeW;
        int closeH;
        int tabsX;
        int tabsY;
        int tabW;
        int tabH;
        int summaryX;
        int summaryY;
        int summaryW;
        int summaryH;
        int contentX;
        int contentY;
        int contentW;
        int contentH;
    }
}
