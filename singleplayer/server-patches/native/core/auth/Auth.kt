package core.auth

import core.ServerConstants
import core.storage.AccountStorageProvider
import core.storage.InMemoryStorageProvider
import core.storage.LocalFileStorageProvider
import core.storage.SQLStorageProvider
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Select account storage/authentication for the retained world.
 *
 * Hosted 2009Scape can still use its original in-memory/MySQL choices. The
 * Android single-player runtime instead persists account metadata beside the
 * ordinary world save, without requiring a database server or network service.
 */
object Auth {
    lateinit var authenticator: AuthProvider<*>
    lateinit var storageProvider: AccountStorageProvider

    fun configure() {
        storageProvider = when {
            java.lang.Boolean.getBoolean("singleplayer") -> {
                val root = localAccountRoot()
                migrateBrokenServerStoreAccountDirectory(root)
                LocalFileStorageProvider(root)
            }
            ServerConstants.PERSIST_ACCOUNTS -> SQLStorageProvider()
            else -> InMemoryStorageProvider()
        }

        authenticator = if (ServerConstants.USE_AUTH)
            ProductionAuthenticator().also { it.configureFor(storageProvider) }
        else
            DevelopmentAuthenticator().also { it.configureFor(storageProvider) }

        if (java.lang.Boolean.getBoolean("singleplayer")) {
            println("SINGLEPLAYER_ACCOUNT_STORE: LOCAL_FILE root=" +
                localAccountRoot().absolutePath)
        }
    }

    /**
     * ServerStore owns data/serverstore and assumes every direct child is a JSON
     * file. Account metadata therefore lives beside that namespace rather than
     * beneath it. Payload updates preserve the local world data tree.
     */
    private fun localAccountRoot(): File {
        val data = ServerConstants.DATA_PATH ?: "data"
        return File(data, "localaccounts")
    }

    /**
     * One broken single-player payload briefly used data/serverstore/accounts as
     * the account root. ServerStore.parse() attempts FileReader on every child of
     * serverstore, so that directory prevents world startup. Move it out of the
     * ServerStore namespace before ServerStore is parsed. The migration preserves
     * any metadata that may already have been written instead of deleting it.
     */
    private fun migrateBrokenServerStoreAccountDirectory(target: File) {
        val store = File(ServerConstants.STORE_PATH ?: "data/serverstore")
        val legacy = File(store, "accounts")
        if (!legacy.isDirectory) return

        if (!target.exists()) {
            target.parentFile?.mkdirs()
            try {
                Files.move(
                    legacy.toPath(),
                    target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE
                )
            } catch (_: Exception) {
                Files.move(legacy.toPath(), target.toPath())
            }
            println(
                "SINGLEPLAYER_ACCOUNT_STORE: MIGRATED_LEGACY_DIRECTORY from=" +
                    legacy.absolutePath + " to=" + target.absolutePath
            )
            return
        }

        if (!target.isDirectory) {
            throw IllegalStateException(
                "Local account metadata target is not a directory: $target"
            )
        }

        val children = legacy.listFiles() ?: emptyArray()
        for (child in children) {
            val destination = File(target, child.name)
            if (destination.exists()) {
                throw IllegalStateException(
                    "Refusing to overwrite local account metadata during migration: $destination"
                )
            }
            Files.move(child.toPath(), destination.toPath())
        }
        if (!legacy.delete()) {
            throw IllegalStateException(
                "Unable to remove obsolete ServerStore account directory: $legacy"
            )
        }
        println(
            "SINGLEPLAYER_ACCOUNT_STORE: MIGRATED_LEGACY_DIRECTORY from=" +
                legacy.absolutePath + " to=" + target.absolutePath
        )
    }
}
