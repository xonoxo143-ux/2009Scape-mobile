package content.global.skill.crafting.armour;

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

/**
 * Represents the pulse used to craft snake skin.
 * @author 'Vexia
 */
public final class SnakeSkinPulse extends SkillPulse<Item> {

	/**
	 * Represents the animation to use.
	 */
	private static final Animation ANIMATION = Animation.create(1249);

	/**
	 * The snake skin.
	 */
	private final SnakeSkin skin;

	/**
	 * Represents the amount to make.
	 */
	private int amount;

	/**
	 * Represents the ticks passed.
	 */
	private int ticks;

	/**
	 * Constructs a new {@code HardCraftPulse} {@code Object}.
	 * @param player the player.
	 * @param node the node.
	 * @param amount the amount.
	 */
	public SnakeSkinPulse(Player player, Item node, int amount, SnakeSkin skin) {
		super(player, node);
		this.amount = amount;
		this.skin = skin;
	}

	@Override
	public boolean checkRequirements() {
		if (player.getSkills().getLevel(Skills.CRAFTING) < skin.getLevel()) {
			player.getDialogueInterpreter().sendDialogue("You need a crafting level of " + skin.getLevel() + " to make this.");
			return false;
		}
		if (!player.getInventory().contains(LeatherCrafting.NEEDLE, 1)) {
			return false;
		}
		if (!player.getInventory().containsItem(LeatherCrafting.THREAD)) {
			player.getDialogueInterpreter().sendDialogue("You need thread to make this.");
			return false;
		}
		if (!player.getInventory().contains(6289, skin.getRequiredAmount())) {
			player.getDialogueInterpreter().sendDialogue("You need " + skin.getRequiredAmount() + " snakeskins in order to do this.");
			return false;
		}
		player.getInterfaceManager().close();
		return true;
	}

	@Override
	public void animate() {
		if (ticks % 5 == 0) {
			player.animate(ANIMATION);
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
		if (!player.getInventory().remove(new Item(6289, skin.getRequiredAmount()))) return false;
		Item item = skin.getProduct();
		LeagueResolvedOutput output = GrandLeagueManager.resolveOutput(player, 1, LeagueOutputKind.PRODUCTION);
		player.getInventory().add(new Item(item.getId(), output.getBaseAmount()));
		GrandLeagueManager.deliverBonusOutput(player, item.getId(), output);
		player.dispatch(new ResourceProducedEvent(item.getId(), output.getAmount(), player, 6289));
		player.getSkills().addExperience(Skills.CRAFTING, skin.getExperience() * output.getExperienceUnits(), true);
		LeatherCrafting.decayThread(player);
		return true;
	}

}
