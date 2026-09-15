package rt4;

import plugin.api.API;
import singleplayer.MobileGestureBridge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/** Full-canvas touch UI for the authoritative single-player League runtime. */
public final class LocalLeagueUiBridge {
    public static final int TAB_TASKS = 0;
    public static final int TAB_RELICS = 1;
    public static final int TAB_BLESSINGS = 2;

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
    private static final int COLOR_RED = 0x7a3528;
    private static final int COLOR_SCROLL_TRACK = 0x17130f;
    private static final int COLOR_SCROLL_THUMB = 0x9b8457;

    private static final int RELIC_HEADER_H = 25;
    private static final int RELIC_CARD_H = 72;
    private static final int RELIC_GAP = 7;
    private static final int GRID_GAP = 8;

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
    private static String relicStatus = "";
    private static long relicStatusUntil;
    private static long resetConfirmUntil;
    private static boolean announcedDraw;

    private LocalLeagueUiBridge() {}

    public static boolean isOpen() { return open; }
    public static int activeTab() { return activeTab; }

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
        System.out.println("SINGLEPLAYER_LEAGUE_UI: OPEN");
    }

    public static void close() {
        if (!open) return;
        open = false;
        draggingContent = false;
        dragTab = -1;
        resetConfirmUntil = 0L;
        announcedDraw = false;
        System.out.println("SINGLEPLAYER_LEAGUE_UI: CLOSE");
    }

    /** League owns every touch while visible, preventing world input beneath it. */
    public static boolean handleGesture(int type, int x, int y, int value1, int value2) {
        if (!open) {
            if ((type == MobileGestureBridge.TAP || type == MobileGestureBridge.LONG_PRESS)
                    && inside(x, y, launcherX, launcherY, launcherWidth, launcherHeight)) {
                open();
                return true;
            }
            return false;
        }

        Layout l = layout();
        if (type == MobileGestureBridge.TAP || type == MobileGestureBridge.LONG_PRESS) {
            if (inside(x, y, l.closeX, l.closeY, l.closeW, l.closeH)) {
                close();
                return true;
            }
            for (int tab = 0; tab < 3; tab++) {
                int tx = l.tabsX + tab * (l.tabW + 4);
                if (inside(x, y, tx, l.tabsY, l.tabW, l.tabH)) {
                    activeTab = tab;
                    draggingContent = false;
                    dragTab = -1;
                    resetConfirmUntil = 0L;
                    return true;
                }
            }
            if (activeTab == TAB_RELICS && inside(x, y, l.resetX, l.resetY, l.resetW, l.resetH)) {
                handleResetTap();
                return true;
            }
            if (activeTab == TAB_RELICS
                    && inside(x, y, l.contentX, l.contentY, l.contentW, l.contentH)) {
                handleRelicTap(x, y, l);
            }
            return true;
        }

        if (type == MobileGestureBridge.DRAG_BEGIN) {
            draggingContent = inside(x, y, l.contentX, l.contentY, l.contentW, l.contentH);
            dragTab = draggingContent ? activeTab : -1;
            return true;
        }
        if (type == MobileGestureBridge.DRAG_MOVE) {
            if (draggingContent && dragTab == activeTab) scrollActiveTab(-value2, l);
            return true;
        }
        if (type == MobileGestureBridge.DRAG_END || type == MobileGestureBridge.CANCEL) {
            draggingContent = false;
            dragTab = -1;
            return true;
        }
        return true;
    }

    public static void draw() {
        if (!open) return;
        Layout l = layout();
        if (!announcedDraw) {
            announcedDraw = true;
            System.out.println("SINGLEPLAYER_LEAGUE_UI: FULLSCREEN_DEMONIC_PACTS " + l.width + "x" + l.height);
        }

        API.FillRect(0, 0, l.width, l.height, COLOR_BG, 0);
        API.DrawRect(0, 0, l.width, l.height, COLOR_FRAME);
        API.FillRect(1, 1, l.width - 2, 31, COLOR_HEADER, 0);
        textLarge("LEAGUE - DEMONIC PACTS", 14, 22, COLOR_GOLD);

        API.FillRect(l.closeX, l.closeY, l.closeW, l.closeH, COLOR_RED, 0);
        API.DrawRect(l.closeX, l.closeY, l.closeW, l.closeH, 0xb78c62);
        textLarge("X", l.closeX + 10, l.closeY + 19, 0xffffff);

        drawTabs(l);
        drawSummary(l);
        if (activeTab == TAB_RELICS) drawRelics(l);
        else if (activeTab == TAB_BLESSINGS) drawBlessings(l);
        else drawTasks(l);
    }

    private static void drawTabs(Layout l) {
        String[] names = {"Tasks", "Relics", "Blessings"};
        for (int i = 0; i < names.length; i++) {
            int x = l.tabsX + i * (l.tabW + 4);
            API.FillRect(x, l.tabsY, l.tabW, l.tabH, i == activeTab ? COLOR_TAB_ACTIVE : COLOR_TAB, 0);
            API.DrawRect(x, l.tabsY, l.tabW, l.tabH, COLOR_FRAME);
            int tw = Fonts.p12Full.getStringWidth(JagString.of(names[i]));
            textLarge(names[i], x + Math.max(8, (l.tabW - tw) / 2), l.tabsY + 21,
                    i == activeTab ? COLOR_GOLD : COLOR_TEXT);
        }
    }

    private static void drawSummary(Layout l) {
        int points = LocalLeagueBridge.points();
        int tasks = LocalLeagueBridge.completedTasks().size();
        int relics = LocalLeagueBridge.unlockedRelics().size();
        API.FillRect(l.summaryX, l.summaryY, l.summaryW, l.summaryH, 0x1d1914, 0);
        API.DrawRect(l.summaryX, l.summaryY, l.summaryW, l.summaryH, 0x5d4f39);
        textLarge("League points: " + points, l.summaryX + 10, l.summaryY + 20, COLOR_GOLD);
        textSmall("Tasks: " + tasks + "    Relics: " + relics,
                l.summaryX + Math.max(190, l.summaryW / 2), l.summaryY + 19, COLOR_MUTED);
    }

    private static void drawTasks(Layout l) {
        panel(l);
        List<String> tasks = sorted(LocalLeagueBridge.completedTasks());
        textLarge("Completed tasks (" + tasks.size() + ")", l.contentX + 12, l.contentY + 23, COLOR_GOLD);
        int top = l.contentY + 34;
        int h = l.contentH - 42;
        int rowH = 34;
        int max = Math.max(0, tasks.size() * rowH - h);
        tasksScroll = clamp(tasksScroll, 0, max);
        if (tasks.isEmpty()) {
            textLarge("No League tasks completed yet.", l.contentX + 18, top + 38, COLOR_TEXT);
            textSmall("Completed tasks will appear here as the task system is populated.",
                    l.contentX + 18, top + 61, COLOR_MUTED);
        } else {
            for (int i = 0; i < tasks.size(); i++) {
                int y = top + i * rowH - tasksScroll;
                if (y < top || y + rowH > top + h) continue;
                API.FillRect(l.contentX + 8, y, l.contentW - 22, rowH - 3,
                        (i & 1) == 0 ? COLOR_PANEL_ALT : 0x30281f, 0);
                API.DrawRect(l.contentX + 8, y, l.contentW - 22, rowH - 3, 0x4d4332);
                textLarge("✓", l.contentX + 16, y + 22, COLOR_GREEN);
                textLarge(pretty(tasks.get(i)), l.contentX + 40, y + 22, COLOR_TEXT);
            }
        }
        drawScrollbar(l.contentX + l.contentW - 10, top, 6, h, tasksScroll, max);
    }

    private static void drawRelics(Layout l) {
        panel(l);
        textLarge("Relic choices", l.contentX + 12, l.contentY + 23, COLOR_GOLD);

        boolean confirming = System.currentTimeMillis() < resetConfirmUntil;
        API.FillRect(l.resetX, l.resetY, l.resetW, l.resetH, confirming ? 0x9a3d2d : COLOR_RED, 0);
        API.DrawRect(l.resetX, l.resetY, l.resetW, l.resetH, 0xb78c62);
        String resetText = confirming ? "CONFIRM RESET" : "Reset Relics";
        int resetTw = Fonts.p11Full.getStringWidth(JagString.of(resetText));
        textSmall(resetText, l.resetX + Math.max(5, (l.resetW - resetTw) / 2), l.resetY + 17, 0xffffff);

        if (!relicStatus.isEmpty() && System.currentTimeMillis() < relicStatusUntil) {
            textSmall(fitSmall(relicStatus, Math.max(160, l.contentW - l.resetW - 190)),
                    l.contentX + 150, l.contentY + 22, COLOR_MUTED);
        }

        List<LocalLeagueBridge.RelicDefinition> defs = LocalLeagueBridge.relicDefinitions();
        Set<String> selected = LocalLeagueBridge.unlockedRelics();
        int top = l.contentY + 36;
        int h = l.contentH - 44;
        int columns = relicColumns(l);
        int max = Math.max(0, relicContentHeight(defs, columns) - h);
        relicsScroll = clamp(relicsScroll, 0, max);

        if (defs.isEmpty()) {
            textLarge("No relic definitions are available.", l.contentX + 18, top + 38, COLOR_TEXT);
            return;
        }

        int cursor = top - relicsScroll;
        int index = 0;
        while (index < defs.size()) {
            int tier = defs.get(index).tier;
            int end = index;
            while (end < defs.size() && defs.get(end).tier == tier) end++;
            if (cursor + RELIC_HEADER_H >= top && cursor <= top + h) {
                textLarge(tierLabel(tier), l.contentX + 12, cursor + 18, COLOR_GOLD);
            }
            cursor += RELIC_HEADER_H;
            int count = end - index;
            int rows = (count + columns - 1) / columns;
            for (int row = 0; row < rows; row++) {
                for (int col = 0; col < columns; col++) {
                    int relicIndex = index + row * columns + col;
                    if (relicIndex >= end) break;
                    drawRelicCard(l, defs.get(relicIndex), selected,
                            relicCardX(l, columns, col), cursor, relicCardWidth(l, columns), top, h);
                }
                cursor += RELIC_CARD_H + RELIC_GAP;
            }
            index = end;
        }
        drawScrollbar(l.contentX + l.contentW - 10, top, 6, h, relicsScroll, max);
    }

    private static void drawRelicCard(Layout l, LocalLeagueBridge.RelicDefinition relic,
                                      Set<String> selected, int x, int y, int w,
                                      int clipTop, int clipH) {
        if (y < clipTop || y + RELIC_CARD_H > clipTop + clipH) return;
        boolean chosen = selected.contains(relic.id);
        boolean selectable = !chosen && LocalLeagueBridge.canSelectRelic(relic.id);
        String state = chosen ? "SELECTED" : selectable ? "Tap to choose" : "LOCKED";
        int stateColor = chosen ? COLOR_GREEN : selectable ? COLOR_GOLD : COLOR_MUTED;
        API.FillRect(x, y, w, RELIC_CARD_H - 3, chosen ? 0x3b3a22 : COLOR_PANEL_ALT, 0);
        API.DrawRect(x, y, w, RELIC_CARD_H - 3, chosen ? COLOR_GREEN : selectable ? 0x6f5e41 : 0x554c3d);

        int sw = Fonts.p11Full.getStringWidth(JagString.of(state));
        textLarge(fit(relic.name, Math.max(105, w - sw - 34)), x + 10, y + 20, COLOR_TEXT);
        textSmall(state, x + w - sw - 10, y + 19, stateColor);
        String[] desc = wrapSmall(relic.description, w - 20, 2);
        if (desc.length > 0) textSmall(desc[0], x + 10, y + 42, COLOR_MUTED);
        if (desc.length > 1) textSmall(desc[1], x + 10, y + 58, COLOR_MUTED);
    }

    private static void handleRelicTap(int x, int y, Layout l) {
        List<LocalLeagueBridge.RelicDefinition> defs = LocalLeagueBridge.relicDefinitions();
        int top = l.contentY + 36;
        int h = l.contentH - 44;
        if (!inside(x, y, l.contentX, top, l.contentW - 10, h)) return;
        int columns = relicColumns(l);
        int cursor = top - relicsScroll;
        int index = 0;
        while (index < defs.size()) {
            int tier = defs.get(index).tier;
            int end = index;
            while (end < defs.size() && defs.get(end).tier == tier) end++;
            cursor += RELIC_HEADER_H;
            int count = end - index;
            int rows = (count + columns - 1) / columns;
            for (int row = 0; row < rows; row++) {
                for (int col = 0; col < columns; col++) {
                    int relicIndex = index + row * columns + col;
                    if (relicIndex >= end) break;
                    int cardX = relicCardX(l, columns, col);
                    int cardW = relicCardWidth(l, columns);
                    if (cursor >= top && cursor + RELIC_CARD_H <= top + h
                            && inside(x, y, cardX, cursor, cardW, RELIC_CARD_H - 3)) {
                        chooseRelic(defs.get(relicIndex));
                        return;
                    }
                }
                cursor += RELIC_CARD_H + RELIC_GAP;
            }
            index = end;
        }
    }

    private static void chooseRelic(LocalLeagueBridge.RelicDefinition relic) {
        Set<String> selected = LocalLeagueBridge.unlockedRelics();
        if (selected.contains(relic.id)) {
            setRelicStatus(relic.name + " is already selected.");
            return;
        }
        if (!LocalLeagueBridge.canSelectRelic(relic.id)) {
            setRelicStatus("That relic choice is currently locked.");
            return;
        }
        if (LocalLeagueBridge.selectRelic(relic.id)) setRelicStatus("Selecting " + relic.name + "...");
        else setRelicStatus("Could not queue that relic selection.");
    }

    private static void handleResetTap() {
        long now = System.currentTimeMillis();
        if (now < resetConfirmUntil) {
            resetConfirmUntil = 0L;
            if (LocalLeagueBridge.resetRelics()) {
                relicsScroll = 0;
                setRelicStatus("Resetting all relic selections...");
            } else {
                setRelicStatus("Could not queue relic reset.");
            }
        } else {
            resetConfirmUntil = now + 3000L;
            setRelicStatus("Tap Reset Relics again to confirm.");
        }
    }

    private static void drawBlessings(Layout l) {
        panel(l);
        textLarge("Blessings", l.contentX + 14, l.contentY + 24, COLOR_GOLD);
        textLarge("Blessing state is not wired yet.", l.contentX + 20, l.contentY + 70, COLOR_TEXT);
        textSmall("This page remains reserved for the authoritative blessing runtime.",
                l.contentX + 20, l.contentY + 96 - blessingsScroll, COLOR_MUTED);
    }

    private static void panel(Layout l) {
        API.FillRect(l.contentX, l.contentY, l.contentW, l.contentH, COLOR_PANEL, 0);
        API.DrawRect(l.contentX, l.contentY, l.contentW, l.contentH, 0x5d4f39);
    }

    private static int relicColumns(Layout l) { return l.contentW >= 780 ? 2 : 1; }
    private static int relicCardWidth(Layout l, int columns) {
        return (l.contentW - 26 - (columns - 1) * GRID_GAP) / columns;
    }
    private static int relicCardX(Layout l, int columns, int column) {
        return l.contentX + 8 + column * (relicCardWidth(l, columns) + GRID_GAP);
    }

    private static int relicContentHeight(List<LocalLeagueBridge.RelicDefinition> defs, int columns) {
        int height = 0;
        int index = 0;
        while (index < defs.size()) {
            int tier = defs.get(index).tier;
            int end = index;
            while (end < defs.size() && defs.get(end).tier == tier) end++;
            height += RELIC_HEADER_H;
            height += ((end - index + columns - 1) / columns) * (RELIC_CARD_H + RELIC_GAP);
            index = end;
        }
        return height;
    }

    private static String tierLabel(int tier) {
        switch (tier) {
            case 1: return "Tier 1 - 0 points";
            case 2: return "Tier 2 - 600 points";
            case 3: return "Tier 3 - 1,200 points";
            case 4: return "Tier 4 - 2,600 points";
            case 5: return "Tier 5 - 5,200 points";
            case 6: return "Tier 6 - 8,500 points";
            case 7: return "Tier 7 - 16,500 points";
            case 8: return "Tier 8 - 28,000 points";
            default: return "Tier " + tier;
        }
    }

    private static void setRelicStatus(String value) {
        relicStatus = value == null ? "" : value;
        relicStatusUntil = System.currentTimeMillis() + 3000L;
    }

    private static void drawScrollbar(int x, int y, int width, int height, int scroll, int maxScroll) {
        API.FillRect(x, y, width, height, COLOR_SCROLL_TRACK, 0);
        if (maxScroll <= 0) {
            API.FillRect(x, y, width, height, COLOR_SCROLL_THUMB, 0);
            return;
        }
        int thumbH = Math.max(24, height * height / (height + maxScroll));
        int thumbY = y + (height - thumbH) * scroll / maxScroll;
        API.FillRect(x, thumbY, width, thumbH, COLOR_SCROLL_THUMB, 0);
    }

    private static void scrollActiveTab(int delta, Layout l) {
        if (delta == 0) return;
        if (activeTab == TAB_TASKS) {
            int h = l.contentH - 42;
            tasksScroll = clamp(tasksScroll + delta, 0,
                    Math.max(0, LocalLeagueBridge.completedTasks().size() * 34 - h));
        } else if (activeTab == TAB_RELICS) {
            int h = l.contentH - 44;
            relicsScroll = clamp(relicsScroll + delta, 0,
                    Math.max(0, relicContentHeight(LocalLeagueBridge.relicDefinitions(), relicColumns(l)) - h));
        } else {
            blessingsScroll = clamp(blessingsScroll + delta, 0, 90);
        }
    }

    private static Layout layout() {
        int w = GameShell.canvasWidth;
        int h = GameShell.canvasHeight;
        Layout l = new Layout();
        l.width = w;
        l.height = h;
        l.closeW = 31; l.closeH = 25;
        l.closeX = Math.max(0, w - l.closeW - 7); l.closeY = 4;
        l.tabW = Math.max(120, Math.min(240, (w - 40) / 3));
        int tabsTotal = l.tabW * 3 + 8;
        l.tabsX = Math.max(8, (w - tabsTotal) / 2); l.tabsY = 35; l.tabH = 29;
        l.summaryX = 8; l.summaryY = 69; l.summaryW = Math.max(1, w - 16); l.summaryH = 29;
        l.contentX = 8; l.contentY = 104; l.contentW = Math.max(1, w - 16); l.contentH = Math.max(1, h - 112);
        l.resetW = 112; l.resetH = 25;
        l.resetX = l.contentX + l.contentW - l.resetW - 14; l.resetY = l.contentY + 4;
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
        StringBuilder out = new StringBuilder(raw.length());
        boolean upper = true;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == ' ') { upper = true; out.append(c); }
            else if (upper) { out.append(Character.toUpperCase(c)); upper = false; }
            else out.append(c);
        }
        return out.toString();
    }

    private static String fit(String value, int maxWidth) {
        if (value == null) return "";
        if (Fonts.p12Full.getStringWidth(JagString.of(value)) <= maxWidth) return value;
        for (int end = value.length() - 1; end > 0; end--) {
            String candidate = value.substring(0, end) + "...";
            if (Fonts.p12Full.getStringWidth(JagString.of(candidate)) <= maxWidth) return candidate;
        }
        return "...";
    }

    private static String fitSmall(String value, int maxWidth) {
        if (value == null) return "";
        if (Fonts.p11Full.getStringWidth(JagString.of(value)) <= maxWidth) return value;
        for (int end = value.length() - 1; end > 0; end--) {
            String candidate = value.substring(0, end) + "...";
            if (Fonts.p11Full.getStringWidth(JagString.of(candidate)) <= maxWidth) return candidate;
        }
        return "...";
    }

    private static String[] wrapSmall(String value, int maxWidth, int maxLines) {
        if (value == null || value.trim().isEmpty() || maxLines <= 0) return new String[0];
        String[] words = value.trim().split("\\s+");
        ArrayList<String> lines = new ArrayList<String>();
        StringBuilder current = new StringBuilder();
        int index = 0;
        while (index < words.length && lines.size() < maxLines) {
            String candidate = current.length() == 0 ? words[index] : current + " " + words[index];
            if (Fonts.p11Full.getStringWidth(JagString.of(candidate)) <= maxWidth) {
                current.setLength(0); current.append(candidate); index++;
            } else if (current.length() > 0) {
                lines.add(current.toString()); current.setLength(0);
            } else {
                lines.add(words[index++]);
            }
        }
        if (current.length() > 0 && lines.size() < maxLines) lines.add(current.toString());
        if (index < words.length && !lines.isEmpty()) {
            int last = lines.size() - 1;
            String line = lines.get(last);
            while (!line.isEmpty() && Fonts.p11Full.getStringWidth(JagString.of(line + "...")) > maxWidth) {
                line = line.substring(0, line.length() - 1);
            }
            lines.set(last, line + "...");
        }
        return lines.toArray(new String[lines.size()]);
    }

    private static void textLarge(String value, int x, int y, int color) {
        Fonts.p12Full.renderLeft(JagString.of(value), x, y, color, -1);
    }
    private static void textSmall(String value, int x, int y, int color) {
        Fonts.p11Full.renderLeft(JagString.of(value), x, y, color, -1);
    }
    private static boolean inside(int x, int y, int rx, int ry, int rw, int rh) {
        return rx >= 0 && ry >= 0 && rw > 0 && rh > 0 && x >= rx && y >= ry && x < rx + rw && y < ry + rh;
    }
    private static int clamp(int value, int min, int max) {
        return value < min ? min : Math.min(value, max);
    }

    private static final class Layout {
        int width, height;
        int closeX, closeY, closeW, closeH;
        int tabsX, tabsY, tabW, tabH;
        int summaryX, summaryY, summaryW, summaryH;
        int contentX, contentY, contentW, contentH;
        int resetX, resetY, resetW, resetH;
    }
}
