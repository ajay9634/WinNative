package com.winlator.cmod.steam.utils

import com.winlator.cmod.core.FileUtils
import com.winlator.cmod.core.TarCompressorUtils
import com.winlator.cmod.xenvironment.ImageFs
import com.winlator.cmod.xenvironment.components.GuestProgramLauncherComponent
import timber.log.Timber
import java.io.File
import java.nio.file.Files
import java.util.zip.CRC32
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

const val NULL_CHAR = '\u0000'

class SteamTokenLogin(
    private val steamId: String,
    private val login: String,
    private val token: String,
    private val imageFs: ImageFs,
    private val guestProgramLauncherComponent: GuestProgramLauncherComponent? = null,
) {
    fun setupSteamFiles() {
        phase1SteamConfig()
    }

    private fun hdr(): String {
        val crc = CRC32()
        crc.update(login.toByteArray())
        return "${crc.value.toString(16)}1"
    }

    private fun obfuscateToken(value: String, mtbf: Long): String {
        return SteamTokenHelper.obfuscate(value.toByteArray(), mtbf)
    }

    private fun createConfigVdf(): String {
        val hdr = hdr()
        val minMTBF = 1000000000L
        val maxMTBF = 2000000000L
        var mtbf = kotlin.random.Random.nextLong(minMTBF, maxMTBF)
        var encoded = ""

        do {
            try {
                encoded = obfuscateToken("$token$NULL_CHAR", mtbf)
            } catch (_: Exception) {
                mtbf = kotlin.random.Random.nextLong(minMTBF, maxMTBF)
            }
        } while (encoded == "")

        Timber.d("SteamTokenLogin: MTBF=$mtbf")

        return "\"InstallConfigStore\"\n" +
                "{\n" +
                "\t\"Software\"\n" +
                "\t{\n" +
                "\t\t\"Valve\"\n" +
                "\t\t{\n" +
                "\t\t\t\"Steam\"\n" +
                "\t\t\t{\n" +
                "\t\t\t\t\"MTBF\"\t\t\"$mtbf\"\n" +
                "\t\t\t\t\"ConnectCache\"\n" +
                "\t\t\t\t{\n" +
                "\t\t\t\t\t\"$hdr\"\t\t\"$encoded$NULL_CHAR\"\n" +
                "\t\t\t\t}\n" +
                "\t\t\t\t\"Accounts\"\n" +
                "\t\t\t\t{\n" +
                "\t\t\t\t\t\"$login\"\n" +
                "\t\t\t\t\t{\n" +
                "\t\t\t\t\t\t\"SteamID\"\t\t\"$steamId\"\n" +
                "\t\t\t\t\t}\n" +
                "\t\t\t\t}\n" +
                "\t\t\t}\n" +
                "\t\t}\n" +
                "\t}\n" +
                "}\n"
    }

    /**
     * Phase 1 Steam Config - Write config.vdf with obfuscated refresh token
     */
    fun phase1SteamConfig() {
        try {
            val steamConfigDir = File(imageFs.wineprefix, "drive_c/Program Files (x86)/Steam/config").toPath()
            Files.createDirectories(steamConfigDir)

            val configVdfPath = steamConfigDir.resolve("config.vdf")

            var shouldWriteConfig = true

            if (Files.exists(configVdfPath)) {
                val vdfContent = FileUtils.readString(configVdfPath.toFile()) ?: ""
                if (vdfContent.contains("ConnectCache") && vdfContent.contains("MTBF")) {
                    // Config already has tokens, skip unless token is empty/changed
                    if (token.isNotEmpty()) {
                        // Always refresh to ensure latest token
                        shouldWriteConfig = true
                    } else {
                        shouldWriteConfig = false
                    }
                }
            }

            if (shouldWriteConfig && token.isNotEmpty()) {
                Timber.d("SteamTokenLogin: Writing config.vdf with ConnectCache")
                Files.write(configVdfPath, createConfigVdf().toByteArray())

                // Set permissions
                FileUtils.chmod(configVdfPath.toFile(), 505)

                // Remove local.vdf if it exists (force phase 1 usage)
                val localSteamDir = File(imageFs.wineprefix, "drive_c/users/${ImageFs.USER}/AppData/Local/Steam").toPath()
                localSteamDir.createDirectories()
                if (localSteamDir.resolve("local.vdf").exists()) {
                    Files.delete(localSteamDir.resolve("local.vdf"))
                }
            } else {
                Timber.d("SteamTokenLogin: Skipping config.vdf (no token or already configured)")
            }
        } catch (e: Exception) {
            Timber.w(e, "SteamTokenLogin: Failed to write config.vdf")
        }
    }
}
