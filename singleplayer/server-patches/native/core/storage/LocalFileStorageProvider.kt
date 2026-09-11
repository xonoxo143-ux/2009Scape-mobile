package core.storage

import core.auth.UserAccountInfo
import core.game.system.communication.CommunicationInfo
import core.game.world.repository.Repository
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.sql.Timestamp
import java.util.Properties

/**
 * Persistent account metadata for the one local Android world.
 *
 * PlayerSaver remains authoritative for gameplay/character state. This provider
 * replaces the hosted deployment's MySQL account table for the small amount of
 * metadata that historically lived outside the player JSON: friends/ignores,
 * clan settings, rights, credits, login timestamps and related account fields.
 *
 * One properties file is used per account so payload/world updates never need a
 * schema migration service. Files live under data/serverstore, which the Android
 * payload installer already treats as persistent user data.
 */
class LocalFileStorageProvider(private val root: File) : AccountStorageProvider {
    init {
        if (!root.exists() && !root.mkdirs()) {
            throw IllegalStateException("Unable to create local account store: $root")
        }
    }

    @Synchronized
    override fun checkUsernameTaken(username: String): Boolean =
        accountFile(username).isFile

    @Synchronized
    override fun getAccountInfo(username: String): UserAccountInfo {
        val canonical = canonical(username)
        val file = accountFile(canonical)
        if (!file.isFile) {
            return UserAccountInfo.createDefault().also {
                it.username = canonical
                it.uid = canonical.hashCode()
                it.setInitialReferenceValues()
            }
        }

        val properties = Properties()
        FileInputStream(file).use { properties.load(it) }
        return decode(properties, canonical)
    }

    @Synchronized
    override fun store(info: UserAccountInfo) {
        normalize(info)
        persist(info)
    }

    @Synchronized
    override fun update(info: UserAccountInfo) {
        normalize(info)
        persist(info)
    }

    @Synchronized
    override fun remove(info: UserAccountInfo) {
        val file = accountFile(info.username)
        if (file.exists() && !file.delete()) {
            throw IllegalStateException("Unable to remove local account metadata: $file")
        }
    }

    /**
     * There is one authoritative in-process world. "Online" therefore means a
     * matching entity exists in Repository, not that a management server says a
     * username is present on some remote world.
     */
    @Synchronized
    override fun getOnlineFriends(username: String): List<String> {
        val info = getAccountInfo(username)
        return CommunicationInfo.parseContacts(info.contacts)
            .keys
            .filter { Repository.getPlayerByName(it) != null }
    }

    /** Retained only for API compatibility; local login does no IP policing. */
    @Synchronized
    override fun getUsernamesWithIP(ip: String): List<String> {
        if (ip.isBlank()) return emptyList()
        val files = root.listFiles { file -> file.isFile && file.name.endsWith(".properties") }
            ?: return emptyList()
        val result = ArrayList<String>()
        for (file in files) {
            val properties = Properties()
            try {
                FileInputStream(file).use { properties.load(it) }
                val last = properties.getProperty("lastUsedIp", "")
                val first = properties.getProperty("ip", "")
                if (ip == last || ip == first) {
                    val username = properties.getProperty("username", "")
                    if (username.isNotBlank()) result.add(username)
                }
            } catch (_: Exception) {
                // A damaged unrelated account must not prevent the local profile
                // from loading; that account will surface its own error if used.
            }
        }
        return result
    }

    private fun normalize(info: UserAccountInfo) {
        info.username = canonical(info.username)
        if (info.uid == 0) info.uid = info.username.hashCode()
    }

    private fun persist(info: UserAccountInfo) {
        val file = accountFile(info.username)
        val temp = File(file.parentFile, file.name + ".tmp")
        val properties = encode(info)
        FileOutputStream(temp).use { output ->
            properties.store(output, "2009Scape local single-player account metadata")
            output.fd.sync()
        }
        try {
            Files.move(
                temp.toPath(),
                file.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
        } catch (_: Exception) {
            Files.move(
                temp.toPath(),
                file.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
        }
        info.setInitialReferenceValues()
    }

    private fun encode(info: UserAccountInfo): Properties = Properties().also { p ->
        p.setProperty("username", info.username)
        p.setProperty("password", info.password)
        p.setProperty("uid", info.uid.toString())
        p.setProperty("rights", info.rights.toString())
        p.setProperty("credits", info.credits.toString())
        p.setProperty("ip", info.ip)
        p.setProperty("lastUsedIp", info.lastUsedIp)
        p.setProperty("muteEndTime", info.muteEndTime.toString())
        p.setProperty("banEndTime", info.banEndTime.toString())
        p.setProperty("contacts", info.contacts)
        p.setProperty("blocked", info.blocked)
        p.setProperty("clanName", info.clanName)
        p.setProperty("currentClan", info.currentClan)
        p.setProperty("clanReqs", info.clanReqs)
        p.setProperty("timePlayed", info.timePlayed.toString())
        p.setProperty("lastLogin", info.lastLogin.toString())
        p.setProperty("online", info.online.toString())
        p.setProperty("joinDate", info.joinDate.time.toString())
    }

    private fun decode(p: Properties, fallbackUsername: String): UserAccountInfo {
        val now = System.currentTimeMillis()
        return UserAccountInfo(
            username = canonical(p.getProperty("username", fallbackUsername)),
            password = p.getProperty("password", ""),
            uid = p.getProperty("uid")?.toIntOrNull() ?: fallbackUsername.hashCode(),
            rights = p.getProperty("rights")?.toIntOrNull() ?: 0,
            credits = p.getProperty("credits")?.toIntOrNull() ?: 0,
            ip = p.getProperty("ip", ""),
            lastUsedIp = p.getProperty("lastUsedIp", ""),
            muteEndTime = p.getProperty("muteEndTime")?.toLongOrNull() ?: 0L,
            banEndTime = p.getProperty("banEndTime")?.toLongOrNull() ?: 0L,
            contacts = p.getProperty("contacts", ""),
            blocked = p.getProperty("blocked", ""),
            clanName = p.getProperty("clanName", ""),
            currentClan = p.getProperty("currentClan", ""),
            clanReqs = p.getProperty("clanReqs", "1,0,8,9"),
            timePlayed = p.getProperty("timePlayed")?.toLongOrNull() ?: 0L,
            lastLogin = p.getProperty("lastLogin")?.toLongOrNull() ?: 0L,
            online = p.getProperty("online")?.toBoolean() ?: false,
            joinDate = Timestamp(p.getProperty("joinDate")?.toLongOrNull() ?: now)
        ).also { it.setInitialReferenceValues() }
    }

    private fun accountFile(username: String): File =
        File(root, canonical(username).replace(Regex("[^a-z0-9 _-]"), "_") + ".properties")

    private fun canonical(username: String): String = username.trim().lowercase()
}
