package content.global.skill.crafting.armour;

import content.global.leagues.GrandLeagueManager;
import content.global.leagues.core.LeagueOutputKind;
import content.global.leagues.core.LeagueOutputPlan;
import content.global.leagues.core.LeagueResolvedOutput;
import core.game.event.ResourceProducedEvent;
import core.cache.def.impl.ItemDefinition;
import core.game.node.entity.skill.SkillPulse;
import core.game.node.entity.skill.Skills;
import content.global.skill.crafting.armour.LeatherCrafting.DragonHide;
import core.game.node.entity.player.Player;
import core.game.node.item.Item;
import core.game.world.update.flag.context.Animation;
import core.tools.StringUtils;

/**
 * Represents a pulse used to craft dragon armour.
 * @author 'Vexia
 */
public final class DragonCraftPulse extends SkillPulse<Item> {

	/**
	 * Represents the animation to use.
	 */
	private static final Animation ANIMATION = Animation.create(1249);

	/**
	 * Represents the dragon hide type.
	 */
	private DragonHide hide;

	/**
	 * Represents the amount to make.
	 */
	private int amount;

	/**
	 * Represents the ticks passed.
	 */
	private int ticks;

	/**
	 * Constructs a new {@code DragonCraftPulse} {@code Object}.
	 * @param player the player.
	 * @param node the node.
	 * @param amount the amount.
	 */
	public DragonCraftPulse(Player player, Item node, DragonHide hide, int amount) {
		super(player, node);
		this.hide = hide;
		this.amount = amount;
	}

	@Override
	public boolean checkRequirements() {
		if (player.getSkills().getLevel(Skills.CRAFTING) < hide.getLevel()) {
			player.getDialogueInterpreter().sendDialogue("You need a crafting level of " + hide.getLevel() + " to make " + ItemDefinition.forId(hide.getProduct()).getName() + ".");
			amount = 0;
			return false;
		}
		if (!player.getInventory().contains(LeatherCrafting.NEEDLE, 1)) {
			player.getDialogueInterpreter().sendDialogue("You need a needle to make this.");
			amount = 0;
			return false;
		}
		if (!player.getInventory().containsItem(LeatherCrafting.THREAD)) {
			player.getDialogueInterpreter().sendDialogue("You need thread to make this.");
			amount = 0;
			return false;
		}
		if (!player.getInventory().contains(hide.getLeather(), hide.getAmount())) {
			player.getDialogueInterpreter().sendDialogue("You need " + hide.getAmount() + " " + ItemDefinition.forId(hide.getLeather()).getName().toLowerCase() + " to make this.");
			amount = 0;
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
		if (!player.getInventory().remove(new Item(hide.getLeather(), hide.getAmount()))) return false;
		if (hide.name().contains("VAMBS")) {
			player.getPacketDispatch().sendMessage("You make a pair of " + ItemDefinition.forId(hide.getProduct()).getName().toLowerCase() + "'s.");
		} else {
			player.getPacketDispatch().sendMessage("You make " + (StringUtils.isPlusN(ItemDefinition.forId(hide.getProduct()).getName().toLowerCase()) ? "an" : "a") + " " + ItemDefinition.forId(hide.getProduct()).getName().toLowerCase() + ".");
		}
		LeagueResolvedOutput output = GrandLeagueManager.resolveOutput(player, 1, LeagueOutputKind.PRODUCTION);
		player.getInventory().add(new Item(hide.getProduct(), output.getBaseAmount()));
		GrandLeagueManager.deliverBonusOutput(player, hide.getProduct(), output);
		player.dispatch(new ResourceProducedEvent(hide.getProduct(), output.getAmount(), player, hide.getLeather()));
		player.getSkills().addExperience(Skills.CRAFTING, hide.getExperience() * output.getExperienceUnits(), true);
		LeatherCrafting.decayThread(player);
		return true;
	}

	@Override
	public void message(int type) {

	}
}
