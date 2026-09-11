#!/usr/bin/env python3
"""Generate diagnostic bootstrap/login sources without permanently forking gameplay code."""
from __future__ import annotations

import argparse
from pathlib import Path


def replace_exact(source: str, old: str, new: str, label: str) -> str:
    count = source.count(old)
    if count != 1:
        raise SystemExit(f"{label}: anchor count was {count}, expected 1")
    return source.replace(old, new, 1)


def prepare_bootstrap(repo: Path, output: Path) -> None:
    path = repo / "singleplayer/inprocess/InProcessBootstrap.java"
    source = path.read_text()

    source = replace_exact(
        source,
        "    public static void markClientReady() {\n"
        "        LocalGameRuntime.get().markClientReady();\n"
        "    }\n",
        "    public static void markClientReady() {\n"
        "        LocalGameRuntime.get().markClientReady();\n"
        "        SinglePlayerDebug.log(\"BOOT\", \"RT4 reported client ready\");\n"
        "        SinglePlayerDebug.stopWatchdog();\n"
        "    }\n",
        "markClientReady",
    )

    source = replace_exact(
        source,
        "    public static void main(String[] args) throws Throwable {\n"
        "        System.setProperty(\"singleplayer\", \"true\");\n",
        "    public static void main(String[] args) throws Throwable {\n"
        "        SinglePlayerDebug.install();\n"
        "        SinglePlayerDebug.startWatchdog();\n"
        "        SinglePlayerDebug.log(\"BOOT\", \"InProcessBootstrap.main entered\");\n"
        "        System.setProperty(\"singleplayer\", \"true\");\n",
        "bootstrap main entry",
    )

    source = replace_exact(
        source,
        "            setStage(\"Preparing local data...\");\n"
        "            verifySQLite();\n"
        "            System.out.println(\"SINGLEPLAYER_E2E: SQLITE_READY\");\n",
        "            setStage(\"Preparing local data...\");\n"
        "            long sqliteStarted = System.nanoTime();\n"
        "            SinglePlayerDebug.log(\"BOOT\", \"SQLite probe begin\");\n"
        "            verifySQLite();\n"
        "            SinglePlayerDebug.logDuration(\"BOOT\", \"SQLite probe\", sqliteStarted);\n"
        "            System.out.println(\"SINGLEPLAYER_E2E: SQLITE_READY\");\n",
        "SQLite timing",
    )

    source = replace_exact(
        source,
        "            setStage(\"Loading world...\");\n"
        "            invokeMain(\"core.Server\", new String[]{\"worldprops/local.conf\"});\n"
        "            runtime.markWorldReady();\n",
        "            setStage(\"Loading world...\");\n"
        "            long worldStarted = System.nanoTime();\n"
        "            SinglePlayerDebug.log(\"BOOT\", \"core.Server.main begin\");\n"
        "            invokeMain(\"core.Server\", new String[]{\"worldprops/local.conf\"});\n"
        "            SinglePlayerDebug.logDuration(\"BOOT\", \"core.Server.main\", worldStarted);\n"
        "            runtime.markWorldReady();\n",
        "world timing",
    )

    source = replace_exact(
        source,
        "            setStage(\"Starting game...\");\n"
        "            invokeMain(\"rt4.client\", new String[]{\"1\", \"live\", \"english\", \"game0\"});\n",
        "            setStage(\"Starting game...\");\n"
        "            long clientStarted = System.nanoTime();\n"
        "            SinglePlayerDebug.log(\"BOOT\", \"rt4.client.main begin\");\n"
        "            invokeMain(\"rt4.client\", new String[]{\"1\", \"live\", \"english\", \"game0\"});\n"
        "            SinglePlayerDebug.logDuration(\"BOOT\", \"rt4.client.main returned\", clientStarted);\n",
        "client timing",
    )

    source = replace_exact(
        source,
        "        } catch (Throwable failure) {\n"
        "            runtime.fail(failure);\n"
        "            throw failure;\n"
        "        }\n",
        "        } catch (Throwable failure) {\n"
        "            runtime.fail(failure);\n"
        "            SinglePlayerDebug.failure(\"BOOT\", failure);\n"
        "            throw failure;\n"
        "        }\n",
        "bootstrap failure",
    )

    source = replace_exact(
        source,
        "    private static void setStage(String value) {\n"
        "        String home = System.getProperty(\"clientHomeOverride\", \"\").trim();\n",
        "    private static void setStage(String value) {\n"
        "        SinglePlayerDebug.stage(value);\n"
        "        String home = System.getProperty(\"clientHomeOverride\", \"\").trim();\n",
        "stage trace",
    )

    destination = output / "singleplayer/InProcessBootstrap.java"
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(source)


def prepare_login(repo: Path, output: Path) -> None:
    path = repo / "singleplayer/client-patches/rt4/LocalLoginBridge.java"
    source = path.read_text()

    source = replace_exact(
        source,
        "    private static long retryAfterMs;\n",
        "    private static long retryAfterMs;\n"
        "    private static long lastDebugHeartbeatMs;\n",
        "login heartbeat field",
    )

    source = replace_exact(
        source,
        "        if (!Boolean.getBoolean(\"singleplayer\")) return;\n\n"
        "        if (client.gameState == 30) {\n",
        "        if (!Boolean.getBoolean(\"singleplayer\")) return;\n"
        "        debugHeartbeat();\n\n"
        "        if (client.gameState == 30) {\n",
        "login heartbeat call",
    )

    source = replace_exact(
        source,
        "            if (!readyAnnounced) {\n"
        "                readyAnnounced = true;\n",
        "            if (!readyAnnounced) {\n"
        "                singleplayer.SinglePlayerDebug.log(\"LOGIN\",\n"
        "                        \"RT4 reached gameState=30; exposing game immediately\");\n"
        "                readyAnnounced = true;\n",
        "game ready trace",
    )

    source = replace_exact(
        source,
        "            state = WAIT_RESPONSE;\n"
        "            System.out.println(\"SINGLEPLAYER_LOCAL_LOGIN: REQUESTED username=\" + normalized);\n",
        "            state = WAIT_RESPONSE;\n"
        "            debugTransition(\"WAIT_RESPONSE\");\n"
        "            System.out.println(\"SINGLEPLAYER_LOCAL_LOGIN: REQUESTED username=\" + normalized);\n",
        "WAIT_RESPONSE transition",
    )

    source = replace_exact(
        source,
        "                state = WAIT_HEADER;\n",
        "                state = WAIT_HEADER;\n"
        "                debugTransition(\"WAIT_HEADER\");\n",
        "WAIT_HEADER transition",
    )

    source = replace_exact(
        source,
        "                state = WAIT_REBUILD;\n",
        "                state = WAIT_REBUILD;\n"
        "                debugTransition(\"WAIT_REBUILD\");\n",
        "WAIT_REBUILD transition",
    )

    source = replace_exact(
        source,
        "                state = COMPLETE;\n"
        "                writeStage(\"Loading map...\");\n",
        "                state = COMPLETE;\n"
        "                debugTransition(\"COMPLETE\");\n"
        "                writeStage(\"Loading map...\");\n",
        "COMPLETE transition",
    )

    source = replace_exact(
        source,
        "        } catch (Throwable failure) {\n"
        "            fail(-4, failure.getClass().getSimpleName() + \": \" + failure.getMessage());\n"
        "            return false;\n"
        "        }\n"
        "    }\n\n"
        "    /** Advance the retained successful-login state from the local byte stream. */\n",
        "        } catch (Throwable failure) {\n"
        "            singleplayer.SinglePlayerDebug.failure(\"LOGIN_BEGIN\", failure);\n"
        "            fail(-4, failure.getClass().getSimpleName() + \": \" + failure.getMessage());\n"
        "            return false;\n"
        "        }\n"
        "    }\n\n"
        "    /** Advance the retained successful-login state from the local byte stream. */\n",
        "login begin failure",
    )

    source = replace_exact(
        source,
        "        } catch (Throwable failure) {\n"
        "            fail(-4, failure.getClass().getSimpleName() + \": \" + failure.getMessage());\n"
        "        }\n"
        "        return false;\n"
        "    }\n\n"
        "    public static synchronized boolean isInProgress() {\n",
        "        } catch (Throwable failure) {\n"
        "            singleplayer.SinglePlayerDebug.failure(\"LOGIN_POLL\", failure);\n"
        "            fail(-4, failure.getClass().getSimpleName() + \": \" + failure.getMessage());\n"
        "        }\n"
        "        return false;\n"
        "    }\n\n"
        "    public static synchronized boolean isInProgress() {\n",
        "login poll failure",
    )

    source = replace_exact(
        source,
        "        state = IDLE;\n"
        "        leagueAttached = false;\n",
        "        state = IDLE;\n"
        "        debugTransition(\"IDLE\");\n"
        "        leagueAttached = false;\n",
        "IDLE transition",
    )

    source = replace_exact(
        source,
        "    private static Method resolveBeginLocalLogin() throws Exception {\n",
        "    private static void debugHeartbeat() {\n"
        "        long now = System.currentTimeMillis();\n"
        "        if (now - lastDebugHeartbeatMs < 2000L) return;\n"
        "        lastDebugHeartbeatMs = now;\n"
        "        int queued = -1;\n"
        "        try {\n"
        "            queued = LocalPresentationBridge.availableServerBytes();\n"
        "        } catch (Throwable ignored) {\n"
        "        }\n"
        "        singleplayer.SinglePlayerDebug.log(\"LOGIN_HEARTBEAT\",\n"
        "                \"gameState=\" + client.gameState\n"
        "                        + \" localState=\" + stateName(state)\n"
        "                        + \" createStep=\" + CreateManager.step\n"
        "                        + \" worldListStep=\" + WorldList.step\n"
        "                        + \" queuedServerBytes=\" + queued\n"
        "                        + \" protocolOpcode=\" + Protocol.opcode\n"
        "                        + \" protocolLength=\" + Protocol.length\n"
        "                        + \" leagueAttached=\" + leagueAttached);\n"
        "    }\n\n"
        "    private static void debugTransition(String next) {\n"
        "        singleplayer.SinglePlayerDebug.log(\"LOGIN_STATE\", \"-> \" + next);\n"
        "    }\n\n"
        "    private static String stateName(int value) {\n"
        "        switch (value) {\n"
        "            case IDLE: return \"IDLE\";\n"
        "            case WAIT_RESPONSE: return \"WAIT_RESPONSE\";\n"
        "            case WAIT_HEADER: return \"WAIT_HEADER\";\n"
        "            case WAIT_REBUILD: return \"WAIT_REBUILD\";\n"
        "            case COMPLETE: return \"COMPLETE\";\n"
        "            case FAILED: return \"FAILED\";\n"
        "            default: return \"UNKNOWN(\" + value + \")\";\n"
        "        }\n"
        "    }\n\n"
        "    private static Method resolveBeginLocalLogin() throws Exception {\n",
        "login diagnostic helpers",
    )

    source = replace_exact(
        source,
        "        state = FAILED;\n"
        "        leagueAttached = false;\n"
        "        failureStage = \"Local login failed\";\n",
        "        state = FAILED;\n"
        "        debugTransition(\"FAILED\");\n"
        "        singleplayer.SinglePlayerDebug.log(\"LOGIN_FAIL\", reason);\n"
        "        leagueAttached = false;\n"
        "        failureStage = \"Local login failed\";\n",
        "FAILED transition",
    )

    destination = output / "rt4/LocalLoginBridge.java"
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(source)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo-root", type=Path, default=Path("."))
    parser.add_argument("--output-root", type=Path, required=True)
    args = parser.parse_args()
    repo = args.repo_root.resolve()
    output = args.output_root.resolve()
    prepare_bootstrap(repo, output)
    prepare_login(repo, output)
    print(f"generated startup diagnostics in {output}")


if __name__ == "__main__":
    main()
