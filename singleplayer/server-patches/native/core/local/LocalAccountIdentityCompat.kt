package core.local

import core.auth.UserAccountInfo

/**
 * Preserve the retained server's canonical account-name form for the one local
 * human player. Repository.getPlayerByName() lowercases names and converts spaces
 * to underscores before lookup, and Repository.addPlayer() stores player.name as
 * the map key. A mixed-case local name such as "Player" therefore became
 * unreachable from every direct in-process command even though the entity was
 * alive in Repository.players.
 *
 * Revision-530 presentation remains unchanged: RT4 decodes Base37 names and
 * applies toTitleCase(), so the visible player name still renders normally.
 */
fun UserAccountInfo.setUsername(value: String) {
    username = value.trim().lowercase().replace(' ', '_')
}
