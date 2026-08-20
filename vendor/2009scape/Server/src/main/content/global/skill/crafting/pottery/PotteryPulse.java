package content.global.skill.crafting.pottery;

import content.global.leagues.GrandLeagueManager;
import content.global.leagues.core.LeagueOutputKind;
import content.global.leagues.core.LeagueOutputPlan;
import content.global.leagues.core.LeagueResolvedOutput;
import core.game.event.ResourceProducedEvent;
import core.game.world.map.Location;
import core.game.node.entity.skill.SkillPulse;
import core.game.node.entity.skill.Skills;
import core.game.node.entity.player.Player;
import core.game.node.item.Item;
import core.game.world.update.flag.context.Animation;
import core.tools.StringUtils;

/**
 * Represents the skill pulse of the pottery unfired items.
 * @author 'Vexia
 */
public final class PotteryPulse extends SkillPulse<Item> {

	/**
	 * Represents the animation to use.
	 */
	private static final Animation ANIMATION = Animation.create(896);

	/**
	 * Represnets the soft clay item.
	 */
	private static final Item SOFT_CLAY = new Item(1761);

	/**
	 * Represents the pottery item to make.
	 */
	private final PotteryItem pottery;

	/**
	 * Represents the amount to make.
	 */
	private int amount;

	/**
	 * Represents the ticks passed.
	 */
	private int ticks;

	/**
	 * Constructs a new {@code PotteryPulse} {@code Object}.
	 * @param player the player.
	 * @param node the node.
	 * @param amount the amount.
	 * @param pottery the pottery.
	 */
	public PotteryPulse(Player player, Item node, int amount, final PotteryItem pottery) {
		super(player, node);
		this.pottery = pottery;
		this.amount = amount;
	}

	@Override
	public boolean checkRequirements() {
		if (!player.getInventory().contains(1761, 1)) {
			player.getPacketDispatch().sendMessage("You need soft clay in order to do this.");
			return false;
		}
		if (player.getSkills().getLevel(Skills.CRAFTING) < pottery.getLevel()) {
			player.getPacketDispatch().sendMessage("You need a crafting level of " + pottery.getLevel() + " to make this.");
			return false;
		}
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
		if (!player.getInventory().remove(SOFT_CLAY)) return false;
		if (pottery == PotteryItem.BOWL && player.getLocation().withinDistance(Location.create(3086,3410,0))) {
			player.setAttribute("/save:diary:varrock:spun-bowl", true);
		}
		final Item item = pottery.getUnfinished();
		LeagueResolvedOutput output = GrandLeagueManager.resolveOutput(player, 1, LeagueOutputKind.PRODUCTION);
		player.getInventory().add(new Item(item.getId(), output.getBaseAmount()));
		GrandLeagueManager.deliverBonusOutput(player, item.getId(), output);
		player.dispatch(new ResourceProducedEvent(item.getId(), output.getAmount(), player, SOFT_CLAY.getId()));
		player.getSkills().addExperience(Skills.CRAFTING, pottery.getExp() * output.getExperienceUnits(), true);
		player.getPacketDispatch().sendMessage("You make the soft clay into " + (StringUtils.isPlusN(pottery.getUnfinished().getName()) ? "an" : "a") + " " + pottery.getUnfinished().getName().toLowerCase() + ".");
		return true;
	}

}
