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
        relicStatus = "";
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

            if (activeTab == TAB_RELICS
                    && inside(x, y, layout.contentX, layout.contentY, layout.contentW, layout.contentH)) {
                handleRelicTap(x, y, layout);
            }
            return true;
        }

        if (type == MobileGestureBridge.DRAG_BEGIN) {
            draggingContent = inside(x, y, layout.contentX, layout.contentY, layout.contentW, layout.contentH);
            dragTab = draggingContent ? activeTab : -1;
            return true;
        }
        if (type == MobileGestureBridge.DRAG_MOVE) {
            if (draggingContent && dragTab == activeTab) scrollActiveTab(-value2, layout);
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
        Layout layout = layout();
        if (layout.width <= 0 || layout.height <= 0) return;

        if (!announcedDraw) {
            announcedDraw = true;
            System.out.println("SINGLEPLAYER_LEAGUE_UI: FULLSCREEN " + layout.width + "x" + layout.height);
        }

        // This is now a real screen, not a modal over the world.
        API.FillRect(0, 0, layout.width, layout.height, COLOR_BG, 0);
        API.DrawRect(0, 0, layout.width, layout.height, COLOR_FRAME);
        API.FillRect(1, 1, layout.width - 2, 31, COLOR_HEADER, 0);
        textLarge("LEAGUE", 14, 22, COLOR_GOLD);

        API.FillRect(layout.closeX, layout.closeY, layout.closeW, layout.closeH, 0x6f2e21, 0);
        API.DrawRect(layout.closeX, layout.closeY, layout.closeW, layout.closeH, 0xb78c62);
        textLarge("X", layout.closeX + 10, layout.closeY + 19, 0xffffff);

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
            API.FillRect(tx, layout.tabsY, layout.tabW, layout.tabH,
                    i == activeTab ? COLOR_TAB_ACTIVE : COLOR_TAB, 0);
            API.DrawRect(tx, layout.tabsY, layout.tabW, layout.tabH, COLOR_FRAME);
            int textWidth = Fonts.p12Full.getStringWidth(JagString.of(names[i]));
            textLarge(names[i], tx + Math.max(8, (layout.tabW - textWidth) / 2),
                    layout.tabsY + 21, i == activeTab ? COLOR_GOLD : COLOR_TEXT);
        }
    }

    private static void drawSummary(Layout layout) {
        int points = LocalLeagueBridge.points();
        int tasks = LocalLeagueBridge.completedTasks().size();
        int relics = LocalLeagueBridge.unlockedRelics().size();
        API.FillRect(layout.summaryX, layout.summaryY, layout.summaryW, layout.summaryH, 0x1d1914, 0);
        API.DrawRect(layout.summaryX, layout.summaryY, layout.summaryW, layout.summaryH, 0x5d4f39);
        textLarge("League points: " + points, layout.summaryX + 10, layout.summaryY + 20, COLOR_GOLD);
        textSmall("Tasks: " + tasks + "    Relics: " + relics,
                layout.summaryX + Math.max(190, layout.summaryW / 2), layout.summaryY + 19, COLOR_MUTED);
    }

    private static void drawTasks(Layout layout) {
        API.FillRect(layout.contentX, layout.contentY, layout.contentW, layout.contentH, COLOR_PANEL, 0);
        API.DrawRect(layout.contentX, layout.contentY, layout.contentW, layout.contentH, 0x5d4f39);

        List<String> tasks = sorted(LocalLeagueBridge.completedTasks());
        textLarge("Completed tasks (" + tasks.size() + ")", layout.contentX + 12, layout.contentY + 23, COLOR_GOLD);
        int rowsTop = layout.contentY + 34;
        int rowsH = layout.contentH - 42;
        int rowH = 34;
        int maxScroll = Math.max(0, tasks.size() * rowH - rowsH);
        tasksScroll = clamp(tasksScroll, 0, maxScroll);

        if (tasks.isEmpty()) {
            textLarge("No League tasks completed yet.", layout.contentX + 18, rowsTop + 38, COLOR_TEXT);
            textSmall("Completed tasks will appear here as the task system is populated.",
                    layout.contentX + 18, rowsTop + 61, COLOR_MUTED);
        } else {
            for (int i = 0; i < tasks.size(); i++) {
                int ry = rowsTop + i * rowH - tasksScroll;
                if (ry < rowsTop || ry + rowH > rowsTop + rowsH) continue;
                API.FillRect(layout.contentX + 8, ry, layout.contentW - 22, rowH - 3,
                        (i & 1) == 0 ? COLOR_PANEL_ALT : 0x30281f, 0);
                API.DrawRect(layout.contentX + 8, ry, layout.contentW - 22, rowH - 3, 0x4d4332);
                textLarge("✓", layout.contentX + 16, ry + 22, COLOR_GREEN);
                textLarge(pretty(tasks.get(i)), layout.contentX + 40, ry + 22, COLOR_TEXT);
            }
        }
        drawScrollbar(layout.contentX + layout.contentW - 10, rowsTop, 6, rowsH, tasksScroll, maxScroll);
    }

    private static void drawRelics(Layout layout) {
        API.FillRect(layout.contentX, layout.contentY, layout.contentW, layout.contentH, COLOR_PANEL, 0);
        API.DrawRect(layout.contentX, layout.contentY, layout.contentW, layout.contentH, 0x5d4f39);
        textLarge("Relic choices", layout.contentX + 12, layout.contentY + 23, COLOR_GOLD);

        if (!relicStatus.isEmpty() && System.currentTimeMillis() < relicStatusUntil) {
            int statusWidth = Fonts.p11Full.getStringWidth(JagString.of(relicStatus));
            textSmall(relicStatus,
                    layout.contentX + Math.max(160, layout.contentW - statusWidth - 20),
                    layout.contentY + 22, COLOR_MUTED);
        } else if (!relicStatus.isEmpty()) {
            relicStatus = "";
        }

        List<LocalLeagueBridge.RelicDefinition> definitions = LocalLeagueBridge.relicDefinitions();
        Set<String> selected = LocalLeagueBridge.unlockedRelics();
        int rowsTop = layout.contentY + 34;
        int rowsH = layout.contentH - 42;
        int columns = relicColumns(layout);
        int maxScroll = Math.max(0, relicContentHeight(definitions, columns) - rowsH);
        relicsScroll = clamp(relicsScroll, 0, maxScroll);

        if (definitions.isEmpty()) {
            textLarge("No relic definitions are available.", layout.contentX + 18, rowsTop + 38, COLOR_TEXT);
            return;
        }

        int cursor = rowsTop - relicsScroll;
        int index = 0;
        while (index < definitions.size()) {
            int tier = definitions.get(index).tier;
            int tierEnd = index;
            while (tierEnd < definitions.size() && definitions.get(tierEnd).tier == tier) tierEnd++;

            if (cursor + RELIC_HEADER_H >= rowsTop && cursor <= rowsTop + rowsH) {
                textLarge(tierLabel(tier), layout.contentX + 12, cursor + 18, COLOR_GOLD);
            }
            cursor += RELIC_HEADER_H;

            int count = tierEnd - index;
            int rows = (count + columns - 1) / columns;
            for (int row = 0; row < rows; row++) {
                for (int column = 0; column < columns; column++) {
                    int relicIndex = index + row * columns + column;
                    if (relicIndex >= tierEnd) break;
                    drawRelicCard(layout, definitions.get(relicIndex), definitions, selected,
                            relicCardX(layout, columns, column), cursor,
                            relicCardWidth(layout, columns), rowsTop, rowsH);
                }
                cursor += RELIC_CARD_H + RELIC_GAP;
            }
            index = tierEnd;
        }

        drawScrollbar(layout.contentX + layout.contentW - 10, rowsTop, 6, rowsH, relicsScroll, maxScroll);
    }

    private static void drawRelicCard(
            Layout layout,
            LocalLeagueBridge.RelicDefinition relic,
            List<LocalLeagueBridge.RelicDefinition> definitions,
            Set<String> selected,
            int cardX,
            int cardY,
            int cardW,
            int clipTop,
            int clipHeight) {
        if (cardY < clipTop || cardY + RELIC_CARD_H > clipTop + clipHeight) return;

        boolean isSelected = selected.contains(relic.id);
        String tierChoice = selectedRelicInTier(relic.tier, definitions, selected);
        boolean locked = tierChoice != null && !isSelected;
        API.FillRect(cardX, cardY, cardW, RELIC_CARD_H - 3,
                isSelected ? 0x3b3a22 : COLOR_PANEL_ALT, 0);
        API.DrawRect(cardX, cardY, cardW, RELIC_CARD_H - 3,
                isSelected ? COLOR_GREEN : locked ? 0x554c3d : 0x6f5e41);

        String state = isSelected ? "SELECTED" : locked ? "LOCKED" : "Tap to choose";
        int stateColor = isSelected ? COLOR_GREEN : locked ? COLOR_MUTED : COLOR_GOLD;
        int stateWidth = Fonts.p11Full.getStringWidth(JagString.of(state));
        textLarge(fit(relic.name, Math.max(110, cardW - stateWidth - 34)), cardX + 10, cardY + 20, COLOR_TEXT);
        textSmall(state, cardX + cardW - stateWidth - 10, cardY + 19, stateColor);

        String[] description = wrapSmall(relic.description, cardW - 20, 2);
        if (description.length > 0) textSmall(description[0], cardX + 10, cardY + 42, COLOR_MUTED);
        if (description.length > 1) textSmall(description[1], cardX + 10, cardY + 58, COLOR_MUTED);
    }

    private static void handleRelicTap(int x, int y, Layout layout) {
        List<LocalLeagueBridge.RelicDefinition> definitions = LocalLeagueBridge.relicDefinitions();
        if (definitions.isEmpty()) return;

        int rowsTop = layout.contentY + 34;
        int rowsH = layout.contentH - 42;
        if (!inside(x, y, layout.contentX, rowsTop, layout.contentW - 10, rowsH)) return;

        int columns = relicColumns(layout);
        int cursor = rowsTop - relicsScroll;
        int index = 0;
        while (index < definitions.size()) {
            int tier = definitions.get(index).tier;
            int tierEnd = index;
            while (tierEnd < definitions.size() && definitions.get(tierEnd).tier == tier) tierEnd++;
            cursor += RELIC_HEADER_H;

            int count = tierEnd - index;
            int rows = (count + columns - 1) / columns;
            for (int row = 0; row < rows; row++) {
                for (int column = 0; column < columns; column++) {
                    int relicIndex = index + row * columns + column;
                    if (relicIndex >= tierEnd) break;
                    int cardX = relicCardX(layout, columns, column);
                    int cardW = relicCardWidth(layout, columns);
                    if (cursor >= rowsTop && cursor + RELIC_CARD_H <= rowsTop + rowsH
                            && inside(x, y, cardX, cursor, cardW, RELIC_CARD_H - 3)) {
                        chooseRelic(definitions.get(relicIndex), definitions);
                        return;
                    }
                }
                cursor += RELIC_CARD_H + RELIC_GAP;
            }
            index = tierEnd;
        }
    }

    private static void chooseRelic(
            LocalLeagueBridge.RelicDefinition relic,
            List<LocalLeagueBridge.RelicDefinition> definitions) {
        Set<String> selected = LocalLeagueBridge.unlockedRelics();
        if (selected.contains(relic.id)) {
            setRelicStatus(relic.name + " is already selected.");
            return;
        }

        String selectedId = selectedRelicInTier(relic.tier, definitions, selected);
        if (selectedId != null) {
            String selectedName = selectedId;
            for (LocalLeagueBridge.RelicDefinition candidate : definitions) {
                if (candidate.id.equals(selectedId)) {
                    selectedName = candidate.name;
                    break;
                }
            }
            setRelicStatus("Tier " + relic.tier + " is locked by " + selectedName + ".");
            return;
        }

        if (LocalLeagueBridge.selectRelic(relic.id)) setRelicStatus("Selecting " + relic.name + "...");
        else setRelicStatus("Could not queue that relic selection.");
    }

    private static void drawBlessings(Layout layout) {
        API.FillRect(layout.contentX, layout.contentY, layout.contentW, layout.contentH, COLOR_PANEL, 0);
        API.DrawRect(layout.contentX, layout.contentY, layout.contentW, layout.contentH, 0x5d4f39);
        textLarge("Blessings", layout.contentX + 14, layout.contentY + 24, COLOR_GOLD);
        textLarge("Blessing state is not wired yet.", layout.contentX + 20, layout.contentY + 70, COLOR_TEXT);
        textSmall("This full-screen page is reserved for the authoritative blessing runtime.",
                layout.contentX + 20, layout.contentY + 96 - blessingsScroll, COLOR_MUTED);
    }

    private static int relicColumns(Layout layout) {
        return layout.contentW >= 780 ? 2 : 1;
    }

    private static int relicCardWidth(Layout layout, int columns) {
        int usable = layout.contentW - 26 - (columns - 1) * GRID_GAP;
        return usable / columns;
    }

    private static int relicCardX(Layout layout, int columns, int column) {
        return layout.contentX + 8 + column * (relicCardWidth(layout, columns) + GRID_GAP);
    }

    private static int relicContentHeight(List<LocalLeagueBridge.RelicDefinition> definitions, int columns) {
        int height = 0;
        int index = 0;
        while (index < definitions.size()) {
            int tier = definitions.get(index).tier;
            int end = index;
            while (end < definitions.size() && definitions.get(end).tier == tier) end++;
            int count = end - index;
            height += RELIC_HEADER_H;
            height += ((count + columns - 1) / columns) * (RELIC_CARD_H + RELIC_GAP);
            index = end;
        }
        return height;
    }

    private static String selectedRelicInTier(
            int tier,
            List<LocalLeagueBridge.RelicDefinition> definitions,
            Set<String> selected) {
        for (LocalLeagueBridge.RelicDefinition relic : definitions) {
            if (relic.tier == tier && selected.contains(relic.id)) return relic.id;
        }
        return null;
    }

    private static String tierLabel(int tier) {
        switch (tier) {
            case 1: return "Tier 1 - Gathering";
            case 2: return "Tier 2 - Production";
            case 3: return "Tier 3 - Combat / Utility";
            case 4: return "Tier 4 - Utility";
            default: return "Tier " + tier;
        }
    }

    private static void setRelicStatus(String value) {
        relicStatus = value == null ? "" : value;
        relicStatusUntil = System.currentTimeMillis() + 2500L;
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

    private static void scrollActiveTab(int delta, Layout layout) {
        if (delta == 0) return;
        if (activeTab == TAB_TASKS) {
            int rowsH = layout.contentH - 42;
            int maxScroll = Math.max(0, LocalLeagueBridge.completedTasks().size() * 34 - rowsH);
            tasksScroll = clamp(tasksScroll + delta, 0, maxScroll);
        } else if (activeTab == TAB_RELICS) {
            int rowsH = layout.contentH - 42;
            int maxScroll = Math.max(0,
                    relicContentHeight(LocalLeagueBridge.relicDefinitions(), relicColumns(layout)) - rowsH);
            relicsScroll = clamp(relicsScroll + delta, 0, maxScroll);
        } else {
            blessingsScroll = clamp(blessingsScroll + delta, 0, 90);
        }
    }

    /** Fill the entire logical RT4 canvas; startup/display geometry is untouched. */
    private static Layout layout() {
        int canvasW = GameShell.canvasWidth;
        int canvasH = GameShell.canvasHeight;
        Layout l = new Layout();
        l.x = 0;
        l.y = 0;
        l.width = canvasW;
        l.height = canvasH;
        l.closeW = 31;
        l.closeH = 25;
        l.closeX = Math.max(0, canvasW - l.closeW - 7);
        l.closeY = 4;

        l.tabW = Math.max(120, Math.min(240, (canvasW - 40) / 3));
        int tabsTotal = l.tabW * 3 + 8;
        l.tabsX = Math.max(8, (canvasW - tabsTotal) / 2);
        l.tabsY = 35;
        l.tabH = 29;

        l.summaryX = 8;
        l.summaryY = 69;
        l.summaryW = Math.max(1, canvasW - 16);
        l.summaryH = 29;

        l.contentX = 8;
        l.contentY = 104;
        l.contentW = Math.max(1, canvasW - 16);
        l.contentH = Math.max(1, canvasH - 112);
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
            if (c == ' ') {
                upper = true;
                out.append(c);
            } else if (upper) {
                out.append(Character.toUpperCase(c));
                upper = false;
            } else out.append(c);
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

    private static String[] wrapSmall(String value, int maxWidth, int maxLines) {
        if (value == null || value.trim().isEmpty() || maxLines <= 0) return new String[0];
        String[] words = value.trim().split("\\s+");
        ArrayList<String> lines = new ArrayList<String>();
        StringBuilder current = new StringBuilder();
        int index = 0;
        while (index < words.length && lines.size() < maxLines) {
            String candidate = current.length() == 0 ? words[index] : current + " " + words[index];
            if (Fonts.p11Full.getStringWidth(JagString.of(candidate)) <= maxWidth) {
                current.setLength(0);
                current.append(candidate);
                index++;
            } else if (current.length() > 0) {
                lines.add(current.toString());
                current.setLength(0);
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
        return rx >= 0 && ry >= 0 && rw > 0 && rh > 0
                && x >= rx && y >= ry && x < rx + rw && y < ry + rh;
    }

    private static int clamp(int value, int min, int max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }

    private static final class Layout {
        int x, y, width, height;
        int closeX, closeY, closeW, closeH;
        int tabsX, tabsY, tabW, tabH;
        int summaryX, summaryY, summaryW, summaryH;
        int contentX, contentY, contentW, contentH;
    }
}
