import content.global.leagues.core.*
import kotlin.system.measureTimeMillis

fun main() {
    val tasks = (1..2500).map { i ->
        LeagueTaskDefinition("stress-$i", "Stress $i", 1, CountTrigger(LeagueSignalKind.RESOURCE, "resource-${i % 100}", (i % 7 + 1).toLong()))
    }
    val base = GrandLeagueBootstrapContent.create()
    val content = base.copy(tasks = tasks)
    val session = GrandLeagueSession(content)
    session.enable(reset = true)
    val ms = measureTimeMillis {
        repeat(250_000) { i -> session.signal(LeagueSignal(LeagueSignalKind.RESOURCE, "resource-${i % 100}")) }
    }
    check(session.profile.completedTasks.size == 2500)
    check(session.profile.points == 2500)
    check(session.profile.progress["count:RESOURCE:resource-0"] == 2500L)
    println("GRAND LEAGUE STRESS PASS: 2500 tasks / 250000 signals in ${ms}ms")
}
