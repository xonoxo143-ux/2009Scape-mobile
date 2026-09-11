package core.local

import core.auth.UserAccountInfo

/**
 * Kotlin exposes UserAccountInfo.username as a mutable property rather than a
 * source-callable setUsername method. Keep the local-login overlay explicit and
 * small while preserving the Java-style call site used by LocalMigrationProbe.
 */
fun UserAccountInfo.setUsername(value: String) {
    username = value
}
