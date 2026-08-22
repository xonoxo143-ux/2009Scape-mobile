package core.game.node.entity.player.link.prayer

import core.game.node.entity.skill.SkillBonus

class PrayerType(val bonuses: Array<SkillBonus>)

class Prayer {
    val active = mutableListOf<PrayerType>()
}
