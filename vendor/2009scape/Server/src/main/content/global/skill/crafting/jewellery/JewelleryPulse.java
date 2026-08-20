package content.global.skill.crafting.jewellery;

import content.global.leagues.GrandLeagueManager;
import content.global.leagues.core.LeagueOutputKind;
import content.global.leagues.core.LeagueOutputPlan;
import content.global.leagues.core.LeagueResolvedOutput;
import core.game.event.ResourceProducedEvent;
import core.game.node.entity.skill.SkillPulse;
import core.game.node.entity.skill.Skills;
import core.game.node.entity.player.Player;
import core.game.node.item.Item;
import core.game.world.update.flag.context.Animation;
import org.rs09.consts.Sounds;

import static core.api.ContentAPIKt.*;

/**
 * Represents the pulse used to craft jewllery.
 * @author 'Vexia
 */
public final class JewelleryPulse extends SkillPulse<Item> {

	/**
	 * Represents the animation to use.
	 */
	private static final Animation ANIMATION = new Animation(3243);

	/**
	 * Represents the data of jewellery.
	 */
	private JewelleryCrafting.JewelleryItem type;

	/**
	 * Represents the amount to make.
	 */
	private int amount;

	/**
	 * Represents the ticks.
	 */
	private int ticks;

	/**
	 * Constructs a new {@code CraftJewellery.java} {@code Object}.
	 * @param player the player.
	 * @param node the node.
	 */
	public JewelleryPulse(Player player, Item node, JewelleryCrafting.JewelleryItem data, int amount) {
		super(player, node);
		this.type = data;
		this.amount = amount;
	}

	@Override
	public boolean checkRequirements() {
		if (player.getSkills().getLevel(Skills.CRAFTING) < type.getLevel()) {
			return false;
		}
		return true;
	}

	@Override
	public void animate() {
		if (ticks % 5 == 0) {
			player.animate(ANIMATION);
			playAudio(player, Sounds.FURNACE_2725);
		}
	}

	@Override
	public boolean reward() {
		LeagueOutputPlan plan = GrandLeagueManager.outputPlan(player, 1, LeagueOutputKind.PRODUCTION);
		if (plan.getInstantBatch()) {
			while (amount > 0 && checkRequirements()) {
				if (!craftOne()) break;
				amount--;
			}
			return true;
		}
		if (++ticks % 5 != 0) return false;
		if (craftOne()) amount--;
		return amount < 1;
	}

	private boolean craftOne() {
		if (!player.getInventory().remove(getItems())) return false;
		final Item item = new Item(type.getSendItem());
		LeagueResolvedOutput output = GrandLeagueManager.resolveOutput(player, 1, LeagueOutputKind.PRODUCTION);
		player.getInventory().add(new Item(item.getId(), output.getBaseAmount()));
		GrandLeagueManager.deliverBonusOutput(player, item.getId(), output);
		player.dispatch(new ResourceProducedEvent(item.getId(), output.getAmount(), player, JewelleryCrafting.GOLD_BAR));
		player.getSkills().addExperience(Skills.CRAFTING, type.getExperience() * output.getExperienceUnits(), true);
		return true;
	}

	/**
	 * Gets the items to remove.
	 * @return the items.
	 */
	private Item[] getItems() {
		Item items[] = new Item[type.getItems().length];
		int index = 0;
		for (int i = 0; i < type.getItems().length; i++) {
			items[index] = new Item(type.getItems()[i], 1);
			index++;
		}
		return items;
	}

}