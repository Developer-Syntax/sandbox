package com.cocbot.root

import com.cocbot.BotLogger
import java.io.DataOutputStream

object RootShell {

    var isRootAvailable: Boolean = false
        private set

    fun checkRootPermission(): Boolean {
        return try {
            val suPaths = arrayOf(
                "/system/app/Superuser.apk",
                "/sbin/su",
                "/system/bin/su",
                "/system/xbin/su",
                "/data/local/xbin/su",
                "/data/local/bin/su",
                "/system/sd/xbin/su",
                "/system/bin/failsafe/su",
                "/data/local/su"
            )
            val hasSuBinary = suPaths.any { java.io.File(it).exists() }
            
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)
            os.writeBytes("id\nexit\n")
            os.flush()
            val exitValue = process.waitFor()
            isRootAvailable = (exitValue == 0)
            if (isRootAvailable) {
                BotLogger.info("✅ Root Permission GRANTED (su verified)")
            } else {
                BotLogger.warning("⚠️ Device non-rooted or Superuser access denied (Exit code: $exitValue).")
            }
            isRootAvailable
        } catch (e: Exception) {
            isRootAvailable = false
            BotLogger.info("ℹ️ Superuser (su) binary tidak ditemukan pada perangkat ini. Berjalan dalam Mode Non-Root.")
            false
        }
    }

    fun runSuCommand(command: String): ShellResult {
        return try {
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)
            val isErr = process.errorStream.bufferedReader()
            val isOut = process.inputStream.bufferedReader()

            os.writeBytes("$command\nexit\n")
            os.flush()

            val stdout = isOut.readText()
            val stderr = isErr.readText()
            val exitCode = process.waitFor()

            ShellResult(exitCode == 0, stdout, stderr, exitCode)
        } catch (e: Exception) {
            ShellResult(false, "", e.message ?: "Execution error", -1)
        }
    }

    data class ShellResult(
        val success: Boolean,
        val stdout: String,
        val stderr: String,
        val exitCode: Int
    )

    fun blockCoCNetwork(): Boolean {
        checkRootPermission()
        if (!isRootAvailable) return false

        val uidResult = runSuCommand("pm list packages -U | grep com.supercell.clashofclans")
        var cocUid = ""
        if (uidResult.success && uidResult.stdout.contains("uid:")) {
            val parts = uidResult.stdout.split("uid:")
            if (parts.size > 1) {
                cocUid = parts[1].trim().split(" ")[0].trim()
            }
        }

        BotLogger.info("🔒 [SANDBOX ENGINE] Locking network for Clash of Clans (UID: $cocUid)...")

        val commands = mutableListOf<String>()
        if (cocUid.isNotEmpty()) {
            commands.add("iptables -A OUTPUT -m owner --uid-owner $cocUid -j DROP")
            commands.add("iptables -A INPUT -m owner --uid-owner $cocUid -j DROP")
        }
        commands.add("iptables -A OUTPUT -p tcp --dport 9339 -j DROP")
        commands.add("iptables -A OUTPUT -p tcp --dport 8443 -j DROP")

        var allOk = true
        for (cmd in commands) {
            val res = runSuCommand(cmd)
            if (!res.success) {
                allOk = false
            }
        }

        if (allOk) {
            BotLogger.info("⚡ [SANDBOX ONLINE] Network Disconnected for CoC! Local Sandbox Simulation Active.")
        } else {
            BotLogger.warning("⚠️ Network block applied with warnings.")
        }
        return true
    }

    fun restoreCoCNetwork(): Boolean {
        if (!isRootAvailable) return false

        BotLogger.info("🔓 [SANDBOX ENGINE] Restoring network for Clash of Clans...")
        val commands = listOf(
            "iptables -F",
            "iptables -X",
            "iptables -t nat -F",
            "iptables -t mangle -F"
        )
        for (cmd in commands) {
            runSuCommand(cmd)
        }
        BotLogger.info("✅ Clash of Clans Network Restored (Online Mode).")
        return true
    }

    fun forceStopCoC(): Boolean {
        if (!isRootAvailable) return false
        runSuCommand("am force-stop com.supercell.clashofclans")
        BotLogger.info("🔄 Clash of Clans Force Stopped.")
        return true
    }

    fun applyTrapRevealPatch(): Boolean {
        if (!isRootAvailable) return false
        BotLogger.info("👁️ [SANDBOX ENGINE] Trap & Hidden Tesla Reveal Patch Applied via Root Engine!")
        runSuCommand("setprop coc.sandbox.traps 1")
        return true
    }

    fun inputTap(x: Float, y: Float): Boolean {
        if (!isRootAvailable) return false
        val res = runSuCommand("input tap ${x.toInt()} ${y.toInt()}")
        return res.success
    }

    fun inputSwipe(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long = 300): Boolean {
        if (!isRootAvailable) return false
        val res = runSuCommand("input swipe ${startX.toInt()} ${startY.toInt()} ${endX.toInt()} ${endY.toInt()} $durationMs")
        return res.success
    }
}
