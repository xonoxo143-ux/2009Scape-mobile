package core.game.node.item
import core.game.node.Node
class Item(val id:Int, val amount:Int=1): Node() {
    val name:String get() = names[id] ?: "item $id"
    companion object {
        val names = mutableMapOf<Int,String>()
    }
}
