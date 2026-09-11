package core.auth

import core.ServerConstants
import core.storage.AccountStorageProvider
import core.storage.InMemoryStorageProvider
import core.storage.LocalFileStorageProvider
import core.storage.SQLStorageProvider
import java.io.File

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
                val base = ServerConstants.STORE_PATH ?: "data/serverstore"
                LocalFileStorageProvider(File(base, "accounts"))
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
                File(ServerConstants.STORE_PATH ?: "data/serverstore", "accounts").absolutePath)
        }
    }
}
