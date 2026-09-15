package core.local.ui

import core.api.TickListener
import core.game.node.entity.player.Player
import core.game.node.entity.player.link.quest.QuestRepository
import core.game.node.entity.skill.Skills
import core.game.world.repository.Repository
import org.json.simple.JSONArray
import org.json.simple.JSONObject
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Read-only semantic state boundary for future Android-native game UI.
 *
 * The authoritative state remains the retained 2009Scape Player/world. A fresh
 * immutable JSON string is produced on the world tick and published atomically,
 * so an Android/JNI consumer never walks mutable Container/Skills/Quest state on
 * its own thread. Nothing in this API exposes RT4 component ids, packet opcodes,
 * varps, client scripts or presentation codecs.
 */
object LocalPlayerUiState {
    private const val SCHEMA_VERSION = 2L

    private val publishedSequence = AtomicLong(0L)
    private val publishedJson = AtomicReference(emptySnapshot())
    @Volatile private var publishedUsername: String? = null

    @JvmStatic
    fun sequence(): Long = publishedSequence.get()

    @JvmStatic
    fun snapshotJson(): String = publishedJson.get()

    @JvmStatic
    fun schemaVersion(): Long = SCHEMA_VERSION

    internal fun publish(player: Player) {
        val root = JSONObject()
        root["schema"] = SCHEMA_VERSION
        root["username"] = player.username
        root["skills"] = skills(player)
        root["inventory"] = items(player.inventory.toArray(), false)
        root["equipment"] = items(player.equipment.toArray(), true)
        root["quests"] = quests(player)
        root["questPoints"] = player.questRepository.points.toLong()

        // Build the complete immutable document on the world thread and only
        // advance the sequence when semantic UI-visible state actually changed.
        // This keeps Android from rebuilding open menus every 600 ms.
        val next = root.toJSONString()
        publishedUsername = player.username
        if (publishedJson.get() == next) return
        publishedJson.set(next)
        publishedSequence.incrementAndGet()
    }

    internal fun clearIfPlayerChanged(username: String?) {
        if (publishedUsername == null || publishedUsername == username) return
        publishedUsername = null
        val empty = emptySnapshot()
        if (publishedJson.get() != empty) {
            publishedJson.set(empty)
            publishedSequence.incrementAndGet()
        }
    }

    private fun skills(player: Player): JSONArray {
        val result = JSONArray()
        val skills = player.skills
        for (id in 0 until Skills.NUM_SKILLS) {
            val entry = JSONObject()
            entry["id"] = id.toLong()
            entry["name"] = Skills.SKILL_NAME[id]
            entry["level"] = skills.getLevel(id, true).toLong()
            entry["baseLevel"] = skills.getStaticLevel(id).toLong()
            entry["xp"] = skills.getExperience(id)
            result.add(entry)
        }
        return result
    }

    private fun items(source: Array<core.game.node.item.Item?>, equipped: Boolean): JSONArray {
        val result = JSONArray()
        for (slot in source.indices) {
            val item = source[slot] ?: continue
            val entry = JSONObject()
            entry["slot"] = slot.toLong()
            entry["id"] = item.id.toLong()
            entry["amount"] = item.amount.toLong()
            entry["name"] = item.name
            entry["examine"] = item.definition.examine ?: ""

            val actions = JSONArray()
            if (equipped) {
                actions.add("Unequip")
                actions.add("Operate")
            } else {
                item.definition.options
                    .filterNotNull()
                    .filter { it.isNotBlank() }
                    .forEach { actions.add(it) }
            }
            entry["actions"] = actions
            result.add(entry)
        }
        return result
    }

    private fun quests(player: Player): JSONArray {
        val result = JSONArray()
        for (quest in QuestRepository.getQuests().values.sortedBy { it.index }) {
            val stage = player.questRepository.getStage(quest)
            val entry = JSONObject()
            entry["id"] = quest.index.toLong()
            entry["name"] = quest.quest.toString()
            entry["stage"] = stage.toLong()
            entry["points"] = quest.questPoints.toLong()
            entry["started"] = stage > 0
            entry["complete"] = stage >= 100
            result.add(entry)
        }
        return result
    }

    private fun emptySnapshot(): String {
        val root = JSONObject()
        root["schema"] = SCHEMA_VERSION
        root["username"] = null
        root["skills"] = JSONArray()
        root["inventory"] = JSONArray()
        root["equipment"] = JSONArray()
        root["quests"] = JSONArray()
        root["questPoints"] = 0L
        return root.toJSONString()
    }
}

/**
 * Publishes the local human's UI snapshot on the authoritative world thread.
 * The 600 ms world cadence is deliberate: UI state follows game-state commits
 * rather than creating a second high-frequency simulation on Android.
 */
class LocalPlayerUiStatePublisher : TickListener {
    override fun tick() {
        if (!java.lang.Boolean.getBoolean("singleplayer")) return

        val player = Repository.players.firstOrNull {
            !it.isArtificial && it.getAttribute("logged-in-fully", false)
        }
        if (player == null) {
            LocalPlayerUiState.clearIfPlayerChanged(null)
            return
        }
        LocalPlayerUiState.publish(player)
    }
}
