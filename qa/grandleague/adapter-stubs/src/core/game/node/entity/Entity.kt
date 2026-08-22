package core.game.node.entity
import core.game.node.Node
import core.game.event.*
open class Entity: Node() {
    private val hooks = mutableMapOf<Class<*>, MutableList<EventHook<out Event>>>()
    fun <T: Event> hook(type: Class<T>, hook: EventHook<T>) { hooks.getOrPut(type){ mutableListOf() }.add(hook) }
    @Suppress("UNCHECKED_CAST")
    fun <T: Event> dispatch(event:T) { hooks[event.javaClass].orEmpty().forEach { (it as EventHook<T>).process(this,event) } }
}
