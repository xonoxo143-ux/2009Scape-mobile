package content.global.leagues

import core.game.node.entity.player.Player

object GrandLeagueGuardianController {
    fun sync(player: Player) {
        if (GrandLeagueManager.guardianModifiers(player).enabled) {
            player.attrs["guardian-enabled"] = true
        } else {
            player.attrs.remove("guardian-enabled")
        }
    }

    fun clear(player: Player) {
        player.attrs.remove("guardian-enabled")
    }
}
