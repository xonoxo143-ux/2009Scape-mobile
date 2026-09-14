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
 * The world remains authoritative for progress and relic definitions. This UI
 * renders those definitions and queues selection through LocalLeagueBridge.
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

    private static final int RELIC_HEADER_H = 23;
    private static final int RELIC_CARD_H = 68;
    private static final int RELIC_GAP = 4;

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

            if (activeTab == TAB_RELICS
                    && inside(x, y, layout.contentX, layout.contentY, layout.contentW, layout.contentH)) {
                handleRelicTap(x, y, layout);
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

        API.FillRect(0, 0, GameShell.canvasWidth, GameShell.canvasHeight, COLOR_DIM, 68);

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
            int textX = tx + Math.max(8, (layout.tabW - Fonts.p12Full.getStringWidth(JagString.of(names[i]))) / 2);
            textLarge(names[i], textX, layout.tabsY + 20, i == activeTab ? COLOR_GOLD : COLOR_TEXT);
        }
    }

    private static void drawSummary(Layout layout) {
        int points = LocalLeagueBridge.points();
        int tasks = LocalLeagueBridge.completedTasks().size();
        int relics = LocalLeagueBridge.unlockedRelics().size();

        API.FillRect(layout.summaryX, layout.summaryY, layout.summaryW, layout.summaryH, 0x1d1914, 0);
        API.DrawRect(layout.summaryX, layout.summaryY, layout.summaryW, layout.summaryH, 0x5d4f39);
        textLarge("League points: " + points, layout.summaryX + 10, layout.summaryY + 20, COLOR_GOLD);
        int statsX = layout.summaryX + Math.min(190, Math.max(165, layout.summaryW / 2));
        textSmall(
                "Tasks: " + tasks + "    Relics: " + relics,
                statsX,
                layout.summaryY + 19,
                COLOR_MUTED);
    }

    private static void drawTasks(Layout layout) {
        int sidebarW = Math.min(150, Math.max(128, layout.contentW / 3));
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
        textSmall("Drag to scroll.", layout.contentX + 10, layout.contentY + layout.contentH - 18, COLOR_MUTED);

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
            textSmall("Completed tasks will appear here.", listX + 16, rowsTop + 56, COLOR_MUTED);
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
        API.FillRect(layout.contentX, layout.contentY, layout.contentW, layout.contentH, COLOR_PANEL, 0);
        API.DrawRect(layout.contentX, layout.contentY, layout.contentW, layout.contentH, 0x5d4f39);
        textLarge("Relic choices", layout.contentX + 10, layout.contentY + 21, COLOR_GOLD);

        if (!relicStatus.isEmpty() && System.currentTimeMillis() < relicStatusUntil) {
            int statusWidth = Fonts.p11Full.getStringWidth(JagString.of(relicStatus));
            textSmall(relicStatus, layout.contentX + Math.max(140, layout.contentW - statusWidth - 18),
                    layout.contentY + 20, COLOR_MUTED);
        } else if (!relicStatus.isEmpty()) {
            relicStatus = "";
        }

        List<LocalLeagueBridge.RelicDefinition> definitions = LocalLeagueBridge.relicDefinitions();
        Set<String> selected = LocalLeagueBridge.unlockedRelics();
        int rowsTop = layout.contentY + 30;
        int rowsH = layout.contentH - 38;
        int maxScroll = Math.max(0, relicContentHeight(definitions) - rowsH);
        relicsScroll = clamp(relicsScroll, 0, maxScroll);

        if (definitions.isEmpty()) {
            textLarge("No relic definitions are available.", layout.contentX + 16, rowsTop + 34, COLOR_TEXT);
            textSmall("The world is still loading or the League registry is unavailable.",
                    layout.contentX + 16, rowsTop + 56, COLOR_MUTED);
            return;
        }

        int cursor = rowsTop - relicsScroll;
        int lastTier = -1;
        for (LocalLeagueBridge.RelicDefinition relic : definitions) {
            if (relic.tier != lastTier) {
                if (cursor + RELIC_HEADER_H >= rowsTop && cursor <= rowsTop + rowsH) {
                    textLarge(tierLabel(relic.tier), layout.contentX + 10, cursor + 17, COLOR_GOLD);
                }
                cursor += RELIC_HEADER_H;
                lastTier = relic.tier;
            }

            int cardY = cursor;
            if (cardY + RELIC_CARD_H >= rowsTop && cardY <= rowsTop + rowsH) {
                drawRelicCard(layout, relic, definitions, selected, cardY, rowsTop, rowsH);
            }
            cursor += RELIC_CARD_H + RELIC_GAP;
        }

        drawScrollbar(layout.contentX + layout.contentW - 10, rowsTop, 6, rowsH, relicsScroll, maxScroll);
    }

    private static void drawRelicCard(
            Layout layout,
            LocalLeagueBridge.RelicDefinition relic,
            List<LocalLeagueBridge.RelicDefinition> definitions,
            Set<String> selected,
            int cardY,
            int clipTop,
            int clipHeight) {
        if (cardY < clipTop || cardY + RELIC_CARD_H > clipTop + clipHeight) return;

        int cardX = layout.contentX + 8;
        int cardW = layout.contentW - 22;
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
        textLarge(fit(relic.name, Math.max(110, cardW - stateWidth - 48)), cardX + 10, cardY + 20, COLOR_TEXT);
        textSmall(state, cardX + cardW - stateWidth - 10, cardY + 19, stateColor);

        String[] description = wrapSmall(relic.description, cardW - 20, 2);
        if (description.length > 0) textSmall(description[0], cardX + 10, cardY + 40, COLOR_MUTED);
        if (description.length > 1) textSmall(description[1], cardX + 10, cardY + 55, COLOR_MUTED);
    }

    private static void handleRelicTap(int x, int y, Layout layout) {
        List<LocalLeagueBridge.RelicDefinition> definitions = LocalLeagueBridge.relicDefinitions();
        if (definitions.isEmpty()) return;

        int rowsTop = layout.contentY + 30;
        int rowsH = layout.contentH - 38;
        if (!inside(x, y, layout.contentX, rowsTop, layout.contentW - 10, rowsH)) return;

        int cursor = rowsTop - relicsScroll;
        int lastTier = -1;
        for (LocalLeagueBridge.RelicDefinition relic : definitions) {
            if (relic.tier != lastTier) {
                cursor += RELIC_HEADER_H;
                lastTier = relic.tier;
            }
            int cardY = cursor;
            if (cardY >= rowsTop && cardY + RELIC_CARD_H <= rowsTop + rowsH
                    && inside(x, y, layout.contentX + 8, cardY, layout.contentW - 22, RELIC_CARD_H - 3)) {
                chooseRelic(relic, definitions);
                return;
            }
            cursor += RELIC_CARD_H + RELIC_GAP;
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

        if (LocalLeagueBridge.selectRelic(relic.id)) {
            setRelicStatus("Selecting " + relic.name + "...");
        } else {
            setRelicStatus("Could not queue that relic selection.");
        }
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

    private static int relicContentHeight(List<LocalLeagueBridge.RelicDefinition> definitions) {
        int height = 0;
        int lastTier = -1;
        for (LocalLeagueBridge.RelicDefinition relic : definitions) {
            if (relic.tier != lastTier) {
                height += RELIC_HEADER_H;
                lastTier = relic.tier;
            }
            height += RELIC_CARD_H + RELIC_GAP;
        }
        return height;
    }

    private static String tierLabel(int tier) {
        switch (tier) {
            case 1:
                return "Tier 1 - Gathering";
            case 2:
                return "Tier 2 - Production";
            case 3:
                return "Tier 3 - Combat / Utility";
            case 4:
                return "Tier 4 - Utility";
            default:
                return "Tier " + tier;
        }
    }

    private static void setRelicStatus(String value) {
        relicStatus = value == null ? "" : value;
        relicStatusUntil = System.currentTimeMillis() + 2500L;
    }

    private static void drawBlessings(Layout layout) {
        API.FillRect(layout.contentX, layout.contentY, layout.contentW, layout.contentH, COLOR_PANEL, 0);
        API.DrawRect(layout.contentX, layout.contentY, layout.contentW, layout.contentH, 0x5d4f39);
        textLarge("Blessings", layout.contentX + 12, layout.contentY + 22, COLOR_GOLD);
        textLarge("Blessing state is not wired yet.", layout.contentX + 18, layout.contentY + 62, COLOR_TEXT);
        textSmall("This page is touch-modal and ready for authoritative choices, progress and resets.",
                layout.contentX + 18, layout.contentY + 88, COLOR_MUTED);
        textSmall("Nothing fake is stored in the client while that runtime seam is unfinished.",
                layout.contentX + 18, layout.contentY + 105, COLOR_MUTED);

        int sampleY = layout.contentY + 145 - blessingsScroll;
        API.FillRect(layout.contentX + 18, sampleY, layout.contentW - 36, 54, COLOR_PANEL_ALT, 0);
        API.DrawRect(layout.contentX + 18, sampleY, layout.contentW - 36, 54, 0x5d4f39);
        textLarge("No blessing choices installed yet.", layout.contentX + 30, sampleY + 23, COLOR_TEXT);
        textSmall("Awaiting authoritative world state.", layout.contentX + 30, sampleY + 42, COLOR_MUTED);
    }

    private static void drawStaticSelector(int x, int y, int width, String value) {
        API.FillRect(x, y, width, 25, 0x211c16, 0);
        API.DrawRect(x, y, width, 25, 0x66563d);
        textLarge(value, x + 8, y + 18, COLOR_TEXT);
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
            int rowsH = layout.contentH - 39;
            int max = Math.max(0, LocalLeagueBridge.completedTasks().size() * 32 - rowsH);
            tasksScroll = clamp(tasksScroll + delta, 0, max);
        } else if (activeTab == TAB_RELICS) {
            int rowsH = layout.contentH - 38;
            int max = Math.max(0, relicContentHeight(LocalLeagueBridge.relicDefinitions()) - rowsH);
            relicsScroll = clamp(relicsScroll + delta, 0, max);
        } else {
            blessingsScroll = clamp(blessingsScroll + delta, 0, 90);
        }
    }

    private static Layout layout() {
        int canvasW = GameShell.canvasWidth;
        int canvasH = GameShell.canvasHeight;

        int width = clamp(canvasW * 58 / 100, 500, 650);
        int height = clamp(canvasH * 68 / 100, 320, 370);
        width = Math.min(width, Math.max(420, canvasW - 32));
        height = Math.min(height, Math.max(300, canvasH - 28));
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
        l.tabW = Math.min(140, Math.max(104, (width - 32) / 3));
        int tabsTotal = l.tabW * 3 + 8;
        l.tabsX = x + Math.max(12, (width - tabsTotal) / 2);
        l.tabsY = y + 34;
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
            if (Fonts.p12Full.getStringWidth(JagString.of(candidate)) <= maxWidth) return candidate;
        }
        return suffix;
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
                continue;
            }
            if (current.length() > 0) {
                lines.add(current.toString());
                current.setLength(0);
            } else {
                lines.add(words[index]);
                index++;
            }
        }
        if (current.length() > 0 && lines.size() < maxLines) lines.add(current.toString());
        if (index < words.length && !lines.isEmpty()) {
            int last = lines.size() - 1;
            String line = lines.get(last);
            while (!line.isEmpty()
                    && Fonts.p11Full.getStringWidth(JagString.of(line + "...")) > maxWidth) {
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
