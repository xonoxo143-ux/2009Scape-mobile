package core.game.ge

import core.ServerConstants
import core.cache.def.impl.ItemDefinition
import core.integrations.sqlite.SQLiteProvider
import org.json.simple.JSONArray
import org.json.simple.JSONObject
import java.sql.Connection

/**
 * Collection of methods for interacting with the grand exchange databases
 * @author Ceikry
 */
object GEDB {
    lateinit var db: SQLiteProvider

    fun init() {
        init (ServerConstants.GRAND_EXCHANGE_DATA_PATH + "grandexchange.db")
    }

    fun init (path: String) {
        db = SQLiteProvider(path, expectedTables)
        db.initTables { createdTable: String ->
            if (createdTable == "price_index")
                populateInitialPriceIndex()
        }
    }

    @JvmStatic fun run(closure: (conn: Connection) -> Unit) {
        db.run(closure)
    }

    private fun convertJsonArray(arr: JSONArray): String
    {
        val sb = StringBuilder()
        for ((index, data) in arr.withIndex())
        {
            val item = data as JSONObject
            sb.append("$index")
            sb.append(",")
            sb.append(item["id"])
            sb.append(",")
            sb.append(item["amount"])
            if(index + 1 < arr.size)
                sb.append(":")
        }
        return sb.toString()
    }

    var expectedTables = hashMapOf(
        "player_offers" to "CREATE TABLE player_offers(" +
                "uid INTEGER PRIMARY KEY ASC," +
                "player_uid      INTEGER," +
                "item_id         INTEGER," +
                "amount_total    INTEGER," +
                "amount_complete INTEGER," +
                "offered_value   INTEGER," +
                "time_stamp      INTEGER," +
                "offer_state     INTEGER," +
                "is_sale         INTEGER," +
                "withdraw_items  STRING ," +
                "slot_index      INTEGER," +
                "total_coin_xc   INTEGER)",
        "bot_offers" to "CREATE TABLE bot_offers(" +
                "item_id         INTEGER," +
                "amount          INTEGER)",
        "price_index" to "CREATE TABLE price_index(" +
                "item_id         INTEGER," +
                "value           INTEGER," +
                "total_value     INTEGER," +
                "unique_trades   INTEGER," +
                "last_update     INTEGER)"
    )

    private fun populateInitialPriceIndex()
    {
        // Phone storage makes one autocommit/fsync per seed row extremely slow.
        // Preserve the exact same initial values but commit the whole index once.
        run { connection ->
            val previousAutoCommit = connection.autoCommit
            val statement = connection.prepareStatement(PriceIndex.INSERT_QUERY)
            try {
                connection.autoCommit = false
                ItemDefinition.getDefinitions().values.forEach { def ->
                    if(def.isTradeable) {
                        statement.setInt(1, def.id)
                        statement.setInt(2, def.getAlchemyValue(true))
                        statement.setInt(3, def.getAlchemyValue(true))
                        statement.setInt(4, 0)
                        statement.setInt(5, 0)
                        statement.addBatch()
                    }
                }
                statement.executeBatch()
                connection.commit()
                println("SINGLEPLAYER_STARTUP: GE_PRICE_INDEX_BATCHED")
            } catch (t: Throwable) {
                try { connection.rollback() } catch (_: Throwable) { }
                throw t
            } finally {
                try { statement.close() } catch (_: Throwable) { }
                try { connection.autoCommit = previousAutoCommit } catch (_: Throwable) { }
            }
        }
    }
}
