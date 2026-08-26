package GroundItems

import plugin.Plugin
import plugin.annotations.PluginMeta
import plugin.api.API.*
import plugin.api.FontColor.fromColor
import plugin.api.FontType
import plugin.api.MiniMenuEntry
import plugin.api.MiniMenuType
import plugin.api.TextModifier
import rt4.*
import java.awt.Color
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.text.DecimalFormat
import kotlin.math.roundToInt

@PluginMeta(
    author = "downthecrop",
    description =
        """
        Ground Items Overlay. Just like Runelite!
        cmds ::set(low,med,high,insane,hide), ::(tag,ignore)item ID, ::(reset)groundconfig
        Special thanks to Chisato for the original skeleton.
        """,
    version = 1.0
)
open class plugin : Plugin() {

    private var lowValue = 5000
    private var mediumValue = 20000
    private var highValue = 50000
    private var insaneValue = 100000
    private var hideBelowValue = 0
    private val coindId = 995
    private lateinit var taggedItems: List<Int>
    private lateinit var ignoredItems: List<Int>

    private val gePriceMap = try {
        parseGEPrices(BufferedReader(InputStreamReader(plugin::class.java.getResourceAsStream("item_configs.json"), StandardCharsets.UTF_8)).useLines { it.joinToString("\n") })
    } catch (e: Exception) {
        emptyMap()
    }

    private val colorMap = mapOf(
        "tagged" to "#AA00FF",
        "hidden" to "#808080",
        "lowValue" to "#66B2FF",
        "mediumValue" to "#99FF99",
        "highValue" to "#FF9600",
        "insaneValue" to "#FF66B2",
    )

    private val commandMap = mapOf(
        "::setlow" to "low-value",
        "::setmed" to "medium-value",
        "::sethigh" to "high-value",
        "::setinsane" to "insane-value",
        "::sethide" to "hide-below-value"
    )

    override fun Init() {
        lowValue = GetData("low-value") as? Int ?: 5000
        mediumValue = GetData("medium-value") as? Int ?: 20000
        highValue = GetData("high-value") as? Int ?: 50000
        insaneValue = GetData("insane-value") as? Int ?: 100000
        hideBelowValue = GetData("hide-below-value") as? Int ?: 0
        taggedItems = GetData("ground-item-tags")?.let { it.toString().split(",").mapNotNull { it.toIntOrNull() } } ?: emptyList()
        ignoredItems = GetData("ground-item-ignore")?.let { it.toString().split(",").mapNotNull { it.toIntOrNull() } } ?: emptyList()
        if (gePriceMap.isEmpty()) SendMessage("Ground Items unable to load item_configs.json")
    }

    private fun isTagged(itemId: Int): Boolean {
        return taggedItems.contains(itemId)
    }

    private fun isHidden(itemId: Int): Boolean {
        return ignoredItems.contains(itemId)
    }

    override fun Draw(timeDelta: Long) = renderGroundItemNames()

    override fun ProcessCommand(commandStr: String, args: Array<out String>?) {
        when (commandStr.toLowerCase()) {
            "::resetgroundconfig" -> resetConfig().also { Init() }
            "::groundconfig" -> displayRanges()
            "::ignoreitem" -> {
                args?.get(0)?.toInt()?.let { id ->
                    SendMessage("Ignoring/Unignoring: $id")
                    ignoreItem(id).run()
                }
            }
            "::tagitem" -> {
                args?.get(0)?.toInt()?.let { id ->
                    SendMessage("Tagging/Untagging: $id")
                    tagItem(id).run()
                }
            }
            else -> {
                commandMap[commandStr]?.let { key ->
                    args?.get(0)?.toInt()?.let { valueArg ->
                        if (valueArg >= 0) {
                            StoreData(key, valueArg).also { Init() }
                        }
                    }
                }
            }
        }
    }

    open fun renderGroundItemNames() {
        for (x in 0..103) {
            for (y in 0..103) {
                val objstacknodeLL = SceneGraph.objStacks[Player.plane][x][y]
                var itemCount = 0
                var tempNode = objstacknodeLL?.head() as ObjStackNode?
                while (tempNode != null) {
                    itemCount++
                    tempNode = objstacknodeLL.next() as ObjStackNode?
                }
                var offset = itemCount * 12
                if (objstacknodeLL != null) {
                    var item = objstacknodeLL.head() as ObjStackNode?
                    while (item != null) {
                        val itemDef = ObjTypeList.get(item.value.type)
                        var haValue = if (itemDef.id == coindId) item.value.amount else (itemDef.cost * 0.6 * item.value.amount).roundToInt()
                        val geValue = (gePriceMap[itemDef.id.toString()]?.toInt() ?: 0) * item.value.amount

                        // If the HA value or GE value is below the hide threshold, don't render
                        val highestValue = maxOf(haValue, geValue)
                        if ((highestValue < hideBelowValue || isHidden(itemDef.id)) && !isTagged(itemDef.id)) {
                            item = objstacknodeLL.next() as ObjStackNode?
                            continue
                        }

                        val screenPos: IntArray = CalculateSceneGraphScreenPosition((x shl 7) + 64, (y shl 7) + 64, 64)
                        if (screenPos[0] < 0 || screenPos[1] < 0) {
                            // Item is offscreen
                            item = objstacknodeLL.next() as ObjStackNode?
                            continue
                        }
                        val screenX = screenPos[0]
                        val screenY = screenPos[1]
                        val color = when {
                            isTagged(itemDef.id) -> colorMap["tagged"]
                            highestValue < lowValue -> "#FFFFFF"
                            highestValue < mediumValue -> colorMap["lowValue"]
                            highestValue < highValue -> colorMap["mediumValue"]
                            highestValue < insaneValue -> colorMap["highValue"]
                            else -> colorMap["insaneValue"]
                        } ?: "#FFFFFF"
                        val colorInt = color.drop(1).toInt(16)

                        val formattedHaValue = formatValue(haValue)
                        val formattedGeValue = formatValue(geValue)
                        val amountSuffix = if (item.value.amount > 1) " (${formatValue(item.value.amount)})" else ""
                        val itemNameAndValue = when (formattedGeValue) {
                            "0" -> "${itemDef.name}$amountSuffix (HA: $formattedHaValue gp)"
                            else -> "${itemDef.name}$amountSuffix (GE: $formattedGeValue gp) (HA: $formattedHaValue gp)"
                        }
                        drawTextWithDropShadow(screenX, screenY - offset, colorInt, itemNameAndValue)

                        offset -= 12
                        item = objstacknodeLL.next() as ObjStackNode?
                    }
                }
            }
        }
    }

    override fun OnMiniMenuCreate(currentEntries: Array<out MiniMenuEntry>?) {
        if (currentEntries != null && IsKeyPressed(Keyboard.KEY_CTRL)) {
            for ((index, entry) in currentEntries.withIndex()) {
                if(entry.type == MiniMenuType.OBJ && index == currentEntries.size - 1) {
                    val itemDef = ObjTypeList.get(entry.subjectIndex.toInt())
                    InsertMiniMenuEntry("Ignore/Unignore", itemDef.name.toString(), ignoreItem(itemDef.id))
                    InsertMiniMenuEntry("Tag/Untag", itemDef.name.toString(), tagItem(itemDef.id))
                }
            }
        }
    }

    private fun ignoreItem(itemId: Int): Runnable {
        return Runnable {
            val existingIgnores = ignoredItems.toMutableList()

            if (existingIgnores.contains(itemId)) {
                existingIgnores.remove(itemId)
            } else {
                existingIgnores.add(itemId)
            }

            val updatedIgnores = existingIgnores.joinToString(",")
            StoreData("ground-item-ignore", updatedIgnores).also { Init() }
        }
    }

    private fun tagItem(itemId: Int): Runnable {
        return Runnable {
            val existingTags = taggedItems.toMutableList()

            if (existingTags.contains(itemId)) {
                existingTags.remove(itemId)
            } else {
                existingTags.add(itemId)
            }

            val updatedTags = existingTags.joinToString(",")
            StoreData("ground-item-tags", updatedTags).also { Init() }
        }
    }


    private fun resetConfig() {
        lowValue = 5000
        mediumValue = 20000
        highValue = 50000
        insaneValue = 100000
        hideBelowValue = 0
        StoreData("ground-item-tags","");
        StoreData("ground-item-ignore","");
        StoreData("low-value", lowValue)
        StoreData("medium-value", mediumValue)
        StoreData("high-value", highValue)
        StoreData("insane-value", insaneValue)
        StoreData("hide-below-value", hideBelowValue)
    }

    private fun displayRanges() {
        val low = lowValue
        val medium = mediumValue
        val high = highValue
        val insane = insaneValue
        val hide = hideBelowValue

        SendMessage("== Ground Item Config ==")
        SendMessage("Low: $low")
        SendMessage("Medium: $medium")
        SendMessage("High: $high")
        SendMessage("Insane: $insane")
        SendMessage("Hide Below: $hide")
        SendMessage("-- Ignored Items --")
        for(item in ignoredItems){
            val itemDef = ObjTypeList.get(item)
            SendMessage("Ignored: ${itemDef.name} ${itemDef.id}")
        }
        SendMessage("-- Tagged Items --")
        for(item in taggedItems){
            val itemDef = ObjTypeList.get(item)
            SendMessage("Tagged: ${itemDef.name} ${itemDef.id}")
        }
        SendMessage("cmds ::set(low,med,high,insane,hide), ::(tag,ignore)item ID, ::(reset)groundconfig")
    }

    private fun drawTextWithDropShadow(x: Int, y: Int, color: Int, text: String) {
        DrawText(FontType.SMALL, fromColor(Color(0)), TextModifier.CENTER, text, x + 1, y + 1)
        DrawText(FontType.SMALL, fromColor(Color(color)), TextModifier.CENTER, text, x, y)
    }

    private fun parseGEPrices(json: String): Map<String, String> {
        return json
            .trim()
            .removeSurrounding("[", "]")
            .split("},")
            .associate { obj ->
                val pairs = obj.trim().removeSurrounding("{", "}").split(",")
                val id = pairs.find { it.trim().startsWith("\"id\"") }?.split(":")?.get(1)?.trim()?.trim('\"')
                val grandExchangePrice =
                    pairs.find { it.trim().startsWith("\"grand_exchange_price\"") }?.split(":")?.get(1)?.trim()
                        ?.trim('\"')
                id to grandExchangePrice
            }.filterKeys { it != null }.filterValues { it != null } as Map<String, String>
    }

    private fun formatValue(value: Int): String {
        return when {
            value >= 1_000_000 -> "%.1fM".format(value / 1_000_000.0)
            value >= 10_000 -> "%.1fK".format(value / 1_000.0)
            else -> DecimalFormat("#,###").format(value)
        }
    }
}