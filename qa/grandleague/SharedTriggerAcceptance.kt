import content.global.leagues.core.*

fun main() {
    val base = GrandLeagueBootstrapContent.create()
    val tasks = listOf(
        LeagueTaskDefinition("one-log", "One", 1, CountTrigger(LeagueSignalKind.RESOURCE, "log", 1)),
        LeagueTaskDefinition("ten-logs", "Ten", 10, CountTrigger(LeagueSignalKind.RESOURCE, "log", 10)),
        LeagueTaskDefinition("hundred-logs", "Hundred", 100, CountTrigger(LeagueSignalKind.RESOURCE, "log", 100))
    )
    val session = GrandLeagueSession(base.copy(tasks = tasks))
    session.enable(reset = true)
    session.signal(LeagueSignal(LeagueSignalKind.RESOURCE, "log"))
    check(session.profile.completedTasks == setOf("one-log"))
    check(session.profile.progress["count:RESOURCE:log"] == 1L)
    repeat(8) { session.signal(LeagueSignal(LeagueSignalKind.RESOURCE, "log")) }
    check("ten-logs" !in session.profile.completedTasks)
    session.signal(LeagueSignal(LeagueSignalKind.RESOURCE, "log"))
    check("ten-logs" in session.profile.completedTasks)
    check(session.profile.progress["count:RESOURCE:log"] == 10L)
    check("hundred-logs" !in session.profile.completedTasks)
    println("GRAND LEAGUE SHARED-TRIGGER PASS")
}
