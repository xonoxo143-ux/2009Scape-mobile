package net.kdt.pojavlaunch;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;

/**
 * First Android-owned game UI surface.
 *
 * This is intentionally an overlay: the proven RT4 logical framebuffer and
 * startup geometry are never resized. When closed, only one small launcher
 * button intercepts touch. When open, the full overlay owns touch and the world
 * remains visible behind the dimmed mobile panel.
 */
final class LocalGameNativeUiController implements AutoCloseable {
    private enum Tab { SKILLS, INVENTORY, EQUIPMENT, QUESTS }

    private static final int PANEL_BG = Color.rgb(44, 36, 24);
    private static final int CELL_BG = Color.rgb(67, 55, 37);
    private static final int CELL_BG_ALT = Color.rgb(78, 64, 43);
    private static final int TEXT = Color.rgb(239, 226, 190);
    private static final int MUTED = Color.rgb(190, 169, 125);
    private static final int COMPLETE = Color.rgb(143, 205, 126);
    private static final int STARTED = Color.rgb(231, 199, 105);

    private final Activity activity;
    private final FrameLayout host;
    private final LocalGameUiBridge bridge;
    private final Button launcher;
    private final FrameLayout overlay;
    private final LinearLayout panel;
    private final LinearLayout content;
    private final TextView title;

    private JSONObject state;
    private Tab tab = Tab.SKILLS;
    private boolean open;

    LocalGameNativeUiController(Activity activity) {
        this.activity = activity;
        this.host = activity.findViewById(android.R.id.content);
        this.launcher = buildLauncher();
        this.overlay = buildOverlay();
        this.panel = buildPanel();
        this.title = buildTitle();
        this.content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(12), dp(8), dp(12), dp(18));

        attachPanelContents();
        host.addView(launcher);
        host.addView(overlay);
        bridge = new LocalGameUiBridge(this::onState);
    }

    boolean isOpen() {
        return open;
    }

    void closePanel() {
        open = false;
        overlay.setVisibility(View.GONE);
    }

    private Button buildLauncher() {
        Button button = new Button(activity);
        button.setText("☰");
        button.setTextSize(22f);
        button.setTextColor(TEXT);
        button.setAllCaps(false);
        button.setContentDescription("Open game menu");
        button.setBackground(roundRect(Color.argb(230, 54, 44, 29), 12));
        button.setPadding(0, 0, 0, 0);
        button.setVisibility(View.GONE);
        button.setOnClickListener(v -> openPanel());

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(52), dp(52));
        lp.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
        lp.leftMargin = dp(10);
        return withLayout(button, lp);
    }

    private FrameLayout buildOverlay() {
        FrameLayout frame = new FrameLayout(activity);
        frame.setBackgroundColor(Color.argb(145, 0, 0, 0));
        frame.setVisibility(View.GONE);
        frame.setClickable(true);
        frame.setFocusable(true);
        frame.setOnClickListener(v -> closePanel());
        frame.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        return frame;
    }

    private LinearLayout buildPanel() {
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackground(roundRect(PANEL_BG, 0));
        layout.setClickable(true);
        layout.setOnClickListener(v -> { });
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                dp(650), ViewGroup.LayoutParams.MATCH_PARENT);
        lp.gravity = Gravity.END;
        overlay.addView(layout, lp);
        return layout;
    }

    private TextView buildTitle() {
        TextView text = new TextView(activity);
        text.setText("Game");
        text.setTextColor(TEXT);
        text.setTextSize(20f);
        text.setGravity(Gravity.CENTER_VERTICAL);
        text.setPadding(dp(14), 0, dp(8), 0);
        return text;
    }

    private void attachPanelContents() {
        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, dp(6), dp(6), dp(4));
        header.addView(title, new LinearLayout.LayoutParams(
                0, dp(48), 1f));

        Button close = smallButton("×");
        close.setContentDescription("Close game menu");
        close.setOnClickListener(v -> closePanel());
        header.addView(close, new LinearLayout.LayoutParams(dp(48), dp(44)));
        panel.addView(header);

        LinearLayout tabs = new LinearLayout(activity);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setPadding(dp(6), 0, dp(6), dp(4));
        addTab(tabs, "Skills", Tab.SKILLS);
        addTab(tabs, "Inventory", Tab.INVENTORY);
        addTab(tabs, "Equipment", Tab.EQUIPMENT);
        addTab(tabs, "Quests", Tab.QUESTS);
        panel.addView(tabs, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        panel.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
    }

    private void addTab(LinearLayout tabs, String label, Tab destination) {
        Button button = smallButton(label);
        button.setTextSize(13f);
        button.setOnClickListener(v -> {
            tab = destination;
            render();
        });
        tabs.addView(button, new LinearLayout.LayoutParams(0, dp(44), 1f));
    }

    private void onState(JSONObject next) {
        state = next;
        String username = next.optString("username", "");
        if (!username.isEmpty() && !"null".equals(username)) {
            launcher.setVisibility(View.VISIBLE);
            title.setText(username);
        }
        if (open) render();
    }

    private void openPanel() {
        open = true;
        overlay.setVisibility(View.VISIBLE);
        render();
    }

    private void render() {
        content.removeAllViews();
        if (state == null || state.isNull("username")) {
            TextView waiting = text("Waiting for game state…", 16f, MUTED);
            waiting.setPadding(dp(8), dp(24), dp(8), dp(24));
            content.addView(waiting);
            return;
        }
        switch (tab) {
            case SKILLS:
                renderSkills(state.optJSONArray("skills"));
                break;
            case INVENTORY:
                renderItems(state.optJSONArray("inventory"), false);
                break;
            case EQUIPMENT:
                renderItems(state.optJSONArray("equipment"), true);
                break;
            case QUESTS:
                renderQuests(state.optJSONArray("quests"));
                break;
        }
    }

    private void renderSkills(JSONArray skills) {
        if (skills == null) return;
        GridLayout grid = new GridLayout(activity);
        grid.setColumnCount(3);
        grid.setUseDefaultMargins(false);
        for (int i = 0; i < skills.length(); i++) {
            JSONObject skill = skills.optJSONObject(i);
            if (skill == null) continue;
            int level = skill.optInt("level");
            int base = skill.optInt("baseLevel");
            double xp = skill.optDouble("xp");
            String value = skill.optString("name") + "\n"
                    + (level == base ? Integer.toString(base) : level + "/" + base)
                    + "   " + formatXp(xp) + " xp";
            TextView cell = cell(value, i % 2 == 0 ? CELL_BG : CELL_BG_ALT);
            GridLayout.LayoutParams lp = gridParams(i, 3);
            grid.addView(cell, lp);
        }
        content.addView(grid);
    }

    private void renderItems(JSONArray items, boolean equipment) {
        if (items == null || items.length() == 0) {
            content.addView(text(equipment ? "Nothing equipped." : "Inventory is empty.", 16f, MUTED));
            return;
        }
        GridLayout grid = new GridLayout(activity);
        grid.setColumnCount(equipment ? 3 : 4);
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) continue;
            int amount = item.optInt("amount", 1);
            String label = item.optString("name", "Item");
            if (amount > 1) label += "\n×" + amount;
            TextView cell = cell(label, i % 2 == 0 ? CELL_BG : CELL_BG_ALT);
            cell.setGravity(Gravity.CENTER);
            cell.setMinHeight(dp(74));
            cell.setClickable(true);
            cell.setFocusable(true);
            cell.setOnClickListener(v -> showItemMenu(cell, item, equipment));
            grid.addView(cell, gridParams(i, equipment ? 3 : 4));
        }
        content.addView(grid);
    }

    private void showItemMenu(View anchor, JSONObject item, boolean equipment) {
        PopupMenu menu = new PopupMenu(activity, anchor);
        JSONArray actions = item.optJSONArray("actions");
        if (actions != null) {
            for (int i = 0; i < actions.length(); i++) {
                String action = actions.optString(i, "").trim();
                if (!action.isEmpty()) menu.getMenu().add(action);
            }
        }
        menu.getMenu().add("Examine");
        int slot = item.optInt("slot", -1);
        menu.setOnMenuItemClickListener(menuItem -> {
            String action = String.valueOf(menuItem.getTitle());
            if ("Examine".equalsIgnoreCase(action)) {
                String examine = item.optString("examine", "Nothing interesting happens.");
                Toast.makeText(activity, examine, Toast.LENGTH_LONG).show();
                return true;
            }
            if (equipment && "Unequip".equalsIgnoreCase(action)) {
                bridge.unequipEquipmentSlot(slot);
            } else if (equipment) {
                bridge.equipmentAction(slot, action);
            } else if (isWearAction(action)) {
                bridge.equipInventorySlot(slot);
            } else {
                bridge.inventoryAction(slot, action);
            }
            return true;
        });
        menu.show();
    }

    private void renderQuests(JSONArray quests) {
        if (quests == null) return;
        int points = state.optInt("questPoints", 0);
        TextView total = text("Quest points: " + points, 15f, MUTED);
        total.setPadding(dp(6), dp(2), dp(6), dp(8));
        content.addView(total);
        for (int i = 0; i < quests.length(); i++) {
            JSONObject quest = quests.optJSONObject(i);
            if (quest == null) continue;
            boolean complete = quest.optBoolean("complete");
            boolean started = quest.optBoolean("started");
            String suffix = complete ? "  ✓" : started ? "  •" : "";
            int color = complete ? COMPLETE : started ? STARTED : TEXT;
            TextView row = text(quest.optString("name", "Quest") + suffix, 15f, color);
            row.setPadding(dp(10), dp(9), dp(10), dp(9));
            content.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }

    private TextView cell(String value, int background) {
        TextView text = text(value, 13f, TEXT);
        text.setBackground(roundRect(background, 8));
        text.setPadding(dp(9), dp(8), dp(9), dp(8));
        return text;
    }

    private GridLayout.LayoutParams gridParams(int index, int columns) {
        GridLayout.Spec row = GridLayout.spec(index / columns);
        GridLayout.Spec column = GridLayout.spec(index % columns, 1f);
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams(row, column);
        lp.width = 0;
        lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        lp.setMargins(dp(3), dp(3), dp(3), dp(3));
        return lp;
    }

    private Button smallButton(String label) {
        Button button = new Button(activity);
        button.setText(label);
        button.setTextColor(TEXT);
        button.setTextSize(15f);
        button.setAllCaps(false);
        button.setBackground(roundRect(CELL_BG, 8));
        button.setPadding(dp(5), 0, dp(5), 0);
        return button;
    }

    private TextView text(String value, float size, int color) {
        TextView text = new TextView(activity);
        text.setText(value);
        text.setTextColor(color);
        text.setTextSize(size);
        return text;
    }

    private GradientDrawable roundRect(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private static boolean isWearAction(String action) {
        return "equip".equalsIgnoreCase(action)
                || "wear".equalsIgnoreCase(action)
                || "wield".equalsIgnoreCase(action);
    }

    private static String formatXp(double xp) {
        if (xp >= 1_000_000.0) return String.format(Locale.US, "%.1fm", xp / 1_000_000.0);
        if (xp >= 1_000.0) return String.format(Locale.US, "%.1fk", xp / 1_000.0);
        return Integer.toString((int) xp);
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private static <T extends View> T withLayout(T view, ViewGroup.LayoutParams layout) {
        view.setLayoutParams(layout);
        return view;
    }

    @Override
    public void close() {
        bridge.close();
        host.removeView(overlay);
        host.removeView(launcher);
    }
}
