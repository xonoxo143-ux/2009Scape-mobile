package core.game.node.entity.player
import core.game.node.entity.Entity
class SkillsStub {
    var totalLevel:Int = 1
    var lifepoints:Int = 100
    var maximumLifepoints:Int = 100
    var prayerPoints:Double = 100.0
    private val xp = DoubleArray(24)
    fun getExperience(skillId:Int):Double = xp[skillId]
    fun setExperience(skillId:Int, value:Double) { xp[skillId] = value }
    fun addExperience(skillId:Int, amount:Double) { xp[skillId] += amount }
    fun heal(amount:Int) { lifepoints = (lifepoints + amount).coerceAtMost(maximumLifepoints) }
    fun incrementPrayerPoints(amount:Double) { prayerPoints = (prayerPoints + amount).coerceAtMost(100.0) }
}
class Player(val username:String="test", val isArtificial:Boolean=false): Entity() {
    val attrs = mutableMapOf<String,Any>()
    val skills = SkillsStub()
    val inventoryItems = mutableMapOf<Int,Int>()
    val bankItems = mutableMapOf<Int,Int>()
}
