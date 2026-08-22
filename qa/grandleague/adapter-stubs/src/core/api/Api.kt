package core.api
import core.game.event.*
import core.game.node.entity.player.Player
import core.game.node.item.Item
import org.json.simple.JSONObject
interface LoginListener { fun login(player:Player) }
interface PersistPlayer { fun savePlayer(player:Player, save:JSONObject); fun parsePlayer(player:Player, data:JSONObject) }
object Event {
    @JvmStatic val ResourceProduced = ResourceProducedEvent::class.java
    @JvmStatic val NPCKilled = NPCKillEvent::class.java
    @JvmStatic val XpGained = XPGainEvent::class.java
    @JvmStatic val QuestCompleted = QuestCompleteEvent::class.java
}
fun setAttribute(player:Player,key:String,value:Any) { player.attrs[if(key.startsWith("/save:")) key.removePrefix("/save:") else key]=value }
@Suppress("UNCHECKED_CAST")
fun <T> getAttribute(player:Player,key:String,default:T):T = player.attrs[key] as? T ?: default

enum class Container { INVENTORY, BANK, EQUIPMENT, BoB }
fun addItem(player:Player,id:Int,amount:Int=1,container:Container=Container.INVENTORY):Boolean {
    val target = if (container == Container.BANK) player.bankItems else player.inventoryItems
    target[id] = (target[id] ?: 0) + amount
    return true
}
fun addItemOrDrop(player:Player,id:Int,amount:Int=1) { addItem(player,id,amount,Container.INVENTORY) }
fun <T> removeItem(player:Player,item:T,container:Container=Container.INVENTORY):Boolean {
    val parsed = when(item) { is Item -> item; is Int -> Item(item); else -> return false }
    val target = if (container == Container.BANK) player.bankItems else player.inventoryItems
    val have = target[parsed.id] ?: 0
    if (have < parsed.amount) return false
    val left = have - parsed.amount
    if (left == 0) target.remove(parsed.id) else target[parsed.id] = left
    return true
}
