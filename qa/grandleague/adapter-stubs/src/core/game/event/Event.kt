package core.game.event
import core.game.node.Node
import core.game.node.entity.npc.NPC
interface Event
interface EventHook<T: Event> { fun process(entity: core.game.node.entity.Entity, event: T) }
enum class ResourceActivity { GENERIC, GATHERING, PRODUCTION }
enum class ResourceSkill { NONE, WOODCUTTING, FISHING, MINING, THIEVING, FARMING, COOKING, CRAFTING, SMITHING, FLETCHING, HERBLORE, CONSTRUCTION, RUNECRAFTING, OTHER }
data class ResourceProducedEvent(
    val itemId:Int,
    val amount:Int,
    val source:Node,
    val original:Int=-1,
    val activity:ResourceActivity=ResourceActivity.GENERIC,
    val skill:ResourceSkill=ResourceSkill.NONE
): Event
data class NPCKillEvent(val npc:NPC): Event
data class XPGainEvent(val skillId:Int,val amount:Double): Event
data class QuestCompleteEvent(val questId:String,val questPoints:Int,val totalQuestPoints:Int): Event
