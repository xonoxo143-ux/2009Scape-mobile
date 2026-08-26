package core.control

import core.ServerConstants
import core.api.addItem
import core.game.node.entity.player.Player
import core.game.node.entity.player.info.login.PlayerParser
import core.game.system.task.Pulse
import core.game.world.GameWorld
import core.game.world.map.Location
import core.game.world.repository.Repository
import org.json.simple.JSONObject
import org.json.simple.parser.JSONParser
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

object LocalControlServer {
    private const val DEFAULT_PORT = 43600
    private val revision = AtomicLong(0)
    @Volatile private var running = false
    @Volatile private var serverSocket: ServerSocket? = null

    @JvmStatic
    fun start(port: Int = DEFAULT_PORT) {
        if (running) return
        running = true
        thread(name = "singleplayer-control-api", isDaemon = true) {
            try {
                val socket = ServerSocket()
                socket.reuseAddress = true
                socket.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), port))
                serverSocket = socket
                while (running) {
                    try {
                        handle(socket.accept())
                    } catch (_: Throwable) {
                        if (!running) break
                    }
                }
            } finally {
                running = false
                try { serverSocket?.close() } catch (_: Throwable) { }
                serverSocket = null
            }
        }
    }

    private fun handle(socket: Socket) {
        socket.use { client ->
            client.soTimeout = 5000
            val line = client.getInputStream().bufferedReader().readLine() ?: return
            val response = try {
                process(line)
            } catch (t: Throwable) {
                json(false, "error" to (t.message ?: t.javaClass.simpleName))
            }
            client.getOutputStream().bufferedWriter().use { out ->
                out.write(response)
                out.newLine()
                out.flush()
            }
        }
    }

    private fun process(line: String): String {
        val request = JSONParser().parse(line) as? JSONObject
            ?: return json(false, "error" to "invalid request")
        val op = request["op"]?.toString() ?: return json(false, "error" to "missing op")

        if (op == "ping") {
            return json(true,
                "service" to "2009scape-singleplayer-control",
                "revision" to revision.get())
        }

        val result = AtomicReference<String>()
        val latch = CountDownLatch(1)
        GameWorld.Pulser.submit(object : Pulse(0) {
            override fun pulse(): Boolean {
                try {
                    result.set(processOnGameThread(op, request))
                } catch (t: Throwable) {
                    result.set(json(false, "error" to (t.message ?: t.javaClass.simpleName)))
                } finally {
                    latch.countDown()
                }
                return true
            }
        })

        if (!latch.await(4, TimeUnit.SECONDS)) {
            return json(false, "error" to "game thread timeout")
        }
        return result.get() ?: json(false, "error" to "empty response")
    }

    private fun processOnGameThread(op: String, request: JSONObject): String {
        return when (op) {
            "status" -> json(true,
                "players" to Repository.playerNames.size,
                "revision" to revision.get())
            "get_player" -> {
                val player = localPlayer() ?: return json(false, "error" to "no local player logged in")
                json(true,
                    "name" to player.name,
                    "x" to player.location.x,
                    "y" to player.location.y,
                    "z" to player.location.z,
                    "revision" to revision.get())
            }
            "give_item" -> {
                val player = requirePlayer()
                val id = number(request, "id")
                val amount = number(request, "amount", 1)
                val success = addItem(player, id, amount)
                if (success) revision.incrementAndGet()
                json(success,
                    "id" to id,
                    "amount" to amount,
                    "revision" to revision.get())
            }
            "teleport" -> {
                val player = requirePlayer()
                val x = number(request, "x")
                val y = number(request, "y")
                val z = number(request, "z", 0)
                player.teleport(Location.create(x, y, z))
                revision.incrementAndGet()
                json(true,
                    "x" to x,
                    "y" to y,
                    "z" to z,
                    "revision" to revision.get())
            }
            "save" -> {
                val player = requirePlayer()
                PlayerParser.saveImmediately(player)
                json(true, "name" to player.name, "revision" to revision.get())
            }
            "snapshot" -> {
                val player = requirePlayer()
                PlayerParser.saveImmediately(player)
                val save = File(ServerConstants.PLAYER_SAVE_PATH + player.name + ".json")
                if (!save.exists()) return json(false, "error" to "save file missing")
                val dir = File(ServerConstants.DATA_PATH, "snapshots")
                dir.mkdirs()
                val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                val target = File(dir, stamp + "-" + player.name + ".json")
                Files.copy(save.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
                json(true,
                    "snapshot" to target.name,
                    "revision" to revision.get())
            }
            else -> json(false, "error" to "unknown op: $op")
        }
    }

    private fun localPlayer(): Player? {
        return Repository.playerNames.values.firstOrNull { !it.isArtificial }
            ?: Repository.playerNames.values.firstOrNull()
    }

    private fun requirePlayer(): Player {
        return localPlayer() ?: throw IllegalStateException("no local player logged in")
    }

    private fun number(request: JSONObject, key: String, default: Int? = null): Int {
        val value = request[key]
        if (value == null) {
            return default ?: throw IllegalArgumentException("missing $key")
        }
        return when (value) {
            is Number -> value.toInt()
            else -> value.toString().toInt()
        }
    }

    private fun json(ok: Boolean, vararg fields: Pair<String, Any?>): String {
        val obj = JSONObject()
        obj["ok"] = ok
        fields.forEach { obj[it.first] = it.second }
        return obj.toJSONString()
    }
}
