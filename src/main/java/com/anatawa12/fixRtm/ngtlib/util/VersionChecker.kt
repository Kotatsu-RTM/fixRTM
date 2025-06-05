package com.anatawa12.fixRtm.ngtlib.util

import com.anatawa12.fixRtm.FixRtm
import com.anatawa12.fixRtm.Loggers
import jp.ngt.ngtlib.util.PackInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import net.minecraft.client.Minecraft
import net.minecraft.util.text.TextComponentString
import net.minecraft.util.text.TextComponentTranslation
import net.minecraft.util.text.TextFormatting
import net.minecraft.util.text.event.ClickEvent
import net.minecraftforge.fml.common.ProgressManager
import java.io.IOException
import java.net.MalformedURLException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownServiceException
import java.nio.charset.UnsupportedCharsetException

object VersionChecker {
    private const val CONNECTION_TIMEOUT = 5_000
    private const val READ_TIMEOUT = 2_000
    private val USER_AGENT by lazy { "FixRTM/${FixRtm.VERSION}" }

    private val LOGGER = Loggers.getLogger("VersionChecker")
    private val targetPacks = mutableListOf<PackInfo>()
    private val updates = mutableListOf<UpdateInfo>()
    private var alreadyChecked = false

    fun addTargetPack(info: PackInfo) {
        if (alreadyChecked) {
            LOGGER.error("Add target pack after check, skip.")
            return
        }
        targetPacks.add(info)
    }

    fun check() {
        if (alreadyChecked) {
            LOGGER.error("VersionChecker.check called multiple times.")
            return
        }
        alreadyChecked = true

        val progress = ProgressManager.push("Check modelpack updates", 1)
        progress.step("")

        val versions = getVersionTexts()
            .flatMap { it.lines() }
            .map { it.split(":") }
            .filter { it.size == 2 }
            .map { it[0] to it[1] }
            .groupBy { it.first }

        versions
            .filter { it.value.size >= 2 }
            .forEach {
                LOGGER.warn(
                    """
                    Modelpack ${it.key} has multiple versions: ${it.value.map { pair -> pair.second }}
                    Use first one.
                """.trimIndent()
                )
            }

        val packVersionMap = versions
            .mapValues { it.value.first().second }

        targetPacks.forEach {
            if (!packVersionMap.contains(it.name)) {
                return@forEach
            }
            if (packVersionMap[it.name] == it.version) {
                return@forEach
            }

            val url = try {
                URL(it.homepage)
            } catch (e: MalformedURLException) {
                LOGGER.error("Malformed homepage. pack: ${it.name}, url: ${it.homepage}", e)
                null
            }
            val updateInfo = UpdateInfo(it.name, packVersionMap[it.name]!!, url)

            updates.add(updateInfo)
        }

        targetPacks.clear()
        updates.sortBy { it.url != null }

        ProgressManager.pop(progress)
    }

    private fun getVersionTexts(): List<String> {
        val urls = targetPacks
            .filter { !it.updateURL.isNullOrBlank() }
            .mapNotNull {
                try {
                    URL(it.updateURL)
                } catch (e: MalformedURLException) {
                    LOGGER.error("Malformed updateURL. pack: ${it.name}, url: ${it.updateURL}", e)
                    null
                }
            }
            .toSet()

        val versionTexts = urls.map { url ->
            CoroutineScope(Dispatchers.IO).async {
                try {
                    LOGGER.info("Try to get version info from $url")
                    val connection = url.openConnection()
                    connection.connectTimeout = CONNECTION_TIMEOUT
                    connection.readTimeout = READ_TIMEOUT
                    connection.setRequestProperty("User-Agent", USER_AGENT)

                    connection.inputStream.use { inputStream ->
                        val charset = connection.contentEncoding?.let {
                            try {
                                charset(it)
                            } catch (e: UnsupportedCharsetException) {
                                LOGGER.warn("Unknown encoding returned from $url.", e)
                                null
                            }
                        } ?: run {
                            LOGGER.warn("Couldn't detect charset, read $url as UTF-8.")
                            Charsets.UTF_8
                        }

                        return@async String(inputStream.readBytes(), charset)
                    }
                } catch (e: SocketTimeoutException) {
                    LOGGER.error("Connection timeout when connecting to $url.", e)
                } catch (e: UnknownServiceException) {
                    LOGGER.error("UnknownServiceException occurred when connecting to $url.", e)
                } catch (e: IOException) {
                    LOGGER.error("IOException occurred when connecting to $url.", e)
                }

                null
            }
        }

        return runBlocking { versionTexts.awaitAll().filterNotNull() }
    }

    fun notifyUpdates() {
        if (!alreadyChecked) {
            LOGGER.error("Try to notify modelpack update before check.")
            return
        }

        updates
            .forEach {
                val message = TextComponentTranslation("message.version", TextFormatting.AQUA.toString() + it.packName)
                message.appendText(" : ${TextFormatting.GREEN}${it.latestVersion}")
                if (it.url != null) {
                    val link = TextComponentString("  ${TextFormatting.GOLD}${TextFormatting.UNDERLINE}Download here")
                    link.style.clickEvent = ClickEvent(ClickEvent.Action.OPEN_URL, it.url.toString())
                    message.appendSibling(link)
                }

                Minecraft.getMinecraft().ingameGUI.chatGUI.printChatMessage(message)
            }
    }

    data class UpdateInfo(val packName: String, val latestVersion: String, val url: URL?)
}
