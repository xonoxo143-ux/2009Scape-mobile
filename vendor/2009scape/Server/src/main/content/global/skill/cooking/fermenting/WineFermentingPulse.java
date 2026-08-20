package content.global.skill.cooking.fermenting;

import core.game.event.ResourceProducedEvent;
import core.game.node.entity.skill.Skills;
import core.game.node.entity.player.Player;
import core.game.node.item.Item;
import core.game.system.task.Pulse;
import core.tools.RandomFunction;

/**
 * Represents a pulse used to ferment wine.
 * @author 'Vexia
 * @date 22/12/2013
 */
public final class WineFermentingPulse extends Pulse {

	/**
	 * Represents the player instance.
	 */
	private final Player player;

	/**
	 * Represents a counter used to time when to ferment.
	 */
	private int count;

	/**
	 * Constructs a new {@code WineFermentingPulse} {@Code Object}
	 * @param delay the delay.
	 */
	public WineFermentingPulse(int delay, final Player player) {
		super(delay);
		this.player = player;
	}

	@Override
	public boolean pulse() {
		if (count++ >= 16) {
			int rand = RandomFunction.random(1, 3);
			if (rand == 1) {
				if (replaceOne(1991)) {
					player.dispatch(new ResourceProducedEvent(1991, 1, player, 1995));
				}
				return true;
			}

			if (replaceOne(1993)) {
				player.getSkills().addExperience(Skills.COOKING, 200, true);
				player.dispatch(new ResourceProducedEvent(1993, 1, player, 1995));
			}
			return true;
		}
		count++;
		return false;
	}

	/** Replace one unfermented wine regardless of which bank tab is active. */
	private boolean replaceOne(int productId) {
		Item unfermented = new Item(1995, 1);
		Item product = new Item(productId, 1);
		if (player.getInventory().contains(1995, 1)) {
			player.getInventory().replace(product, player.getInventory().getSlot(unfermented));
			return true;
		}
		if (player.getBank().contains(1995, 1)) {
			player.getBank().replace(product, player.getBank().getSlot(unfermented));
			return true;
		}
		if (player.getBankPrimary().contains(1995, 1)) {
			player.getBankPrimary().replace(product, player.getBankPrimary().getSlot(unfermented));
			return true;
		}
		if (player.getBankSecondary().contains(1995, 1)) {
			player.getBankSecondary().replace(product, player.getBankSecondary().getSlot(unfermented));
			return true;
		}
		return false;
	}

	/**
	 * Gets the player.
	 * @return the player.
	 */
	public Player getPlayer() {
		return player;
	}

}
