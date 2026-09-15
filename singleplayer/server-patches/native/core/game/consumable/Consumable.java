package core.game.consumable;

import content.data.consumables.Consumables;
import core.api.Container;
import core.game.node.entity.player.Player;
import core.game.node.item.Item;
import core.game.world.update.flag.context.Animation;
import core.local.LeagueRuntime;
import core.local.league.DemonicPactsRelics;

import static core.api.ContentAPIKt.*;

/** Represents any item that has a consumption option such as Eat or Drink. */
public abstract class Consumable {
    protected final int[] ids;
    protected final ConsumableEffect effect;
    protected final String[] messages;
    protected Animation animation = null;

    public Consumable(final int[] ids, final ConsumableEffect effect, final String... messages) {
        this.ids = ids;
        this.effect = effect;
        this.messages = messages;
    }

    public Consumable(final int[] ids, final ConsumableEffect effect, final Animation animation, final String... messages) {
        this.ids = ids;
        this.effect = effect;
        this.animation = animation;
        this.messages = messages;
    }

    public void consume(final Item item, final Player player) {
        executeConsumptionActions(player);
        final int nextItemId = getNextItemId(item.getId());

        // Demonic Pacts: Eternal Sustenance preserves food while retaining the
        // normal eating animation, healing/effect and messages. Drinks still
        // consume doses normally.
        final boolean preserveFood = this instanceof Food
                && LeagueRuntime.hasRelic(player, DemonicPactsRelics.ETERNAL_SUSTENANCE);
        if (!preserveFood) {
            if (ids.length == 1) {
                replaceSlot(player, item.getSlot(), new Item(item.getId(), item.getAmount() - 1), item, Container.INVENTORY);
            } else {
                replaceSlot(player, item.getSlot(), new Item(nextItemId, 1), item, Container.INVENTORY);
            }
        }

        final int initialLifePoints = player.getSkills().getLifepoints();
        Consumables.getConsumableById(item.getId()).getConsumable().effect.activate(player);
        sendMessages(player, initialLifePoints, item, messages);
    }

    protected void sendMessages(final Player player, final int initialLifePoints, final Item item, String[] messages) {
        if (messages.length == 0) {
            sendDefaultMessages(player, item);
            sendHealingMessage(player, initialLifePoints);
        } else {
            sendCustomMessages(player, messages);
        }
    }

    protected void sendHealingMessage(final Player player, final int initialLifePoints) {
        if (player.getSkills().getLifepoints() > initialLifePoints) {
            player.getPacketDispatch().sendMessage("It heals some health.");
        }
    }

    protected void sendCustomMessages(final Player player, final String[] messages) {
        for (String message : messages) player.getPacketDispatch().sendMessage(message);
    }

    protected abstract void sendDefaultMessages(final Player player, final Item item);
    protected abstract void executeConsumptionActions(Player player);

    protected int getNextItemId(final int currentConsumableId) {
        for (int i = 0; i < ids.length; i++) {
            if (ids[i] == currentConsumableId && i != ids.length - 1) return ids[i + 1];
        }
        return -1;
    }

    public String getFormattedName(Item item) {
        return item.getName().replace("(4)", "").replace("(3)", "").replace("(2)", "").replace("(1)", "").trim().toLowerCase();
    }

    public int getHealthEffectValue(Player player) { return effect.getHealthEffectValue(player); }
    public ConsumableEffect getEffect() { return effect; }
    public int[] getIds() { return ids; }
}
