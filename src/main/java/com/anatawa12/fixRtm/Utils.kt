/// Copyright (c) 2020 anatawa12 and other contributors
/// This file is part of fixRTM, released under GNU LGPL v3 with few exceptions
/// See LICENSE at https://github.com/fixrtm/fixRTM for more details

package com.anatawa12.fixRtm

import com.anatawa12.fixRtm.gui.GuiId
import com.anatawa12.fixRtm.utils.ArrayPool
import com.anatawa12.fixRtm.utils.closeScope
import com.anatawa12.fixRtm.utils.sortedWalk
import com.google.common.collect.Iterators
import jp.ngt.rtm.RTMItem
import jp.ngt.rtm.block.tileentity.TileEntityMachineBase
import jp.ngt.rtm.item.ItemInstalledObject
import jp.ngt.rtm.modelpack.ResourceType
import jp.ngt.rtm.modelpack.cfg.ResourceConfig
import net.minecraft.crash.CrashReportCategory
import net.minecraft.entity.Entity
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraft.item.ItemStack
import net.minecraft.util.math.RayTraceResult
import net.minecraft.util.math.Vec3i
import net.minecraft.world.World
import net.minecraftforge.fml.common.Loader
import net.minecraftforge.fml.common.network.handshake.NetworkDispatcher
import java.io.*
import java.nio.charset.Charset
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger


fun getThreadGroup() = System.getSecurityManager()?.threadGroup ?: Thread.currentThread().threadGroup!!

fun threadFactoryWithPrefix(prefix: String, group: ThreadGroup = getThreadGroup()) = object : ThreadFactory {
    private val threadNumber = AtomicInteger(1)

    override fun newThread(r: Runnable?): Thread {
        val t = Thread(group, r, "$prefix-${threadNumber.getAndIncrement()}", 0)
        if (t.isDaemon) t.isDaemon = false
        if (t.priority != Thread.NORM_PRIORITY) t.priority = Thread.NORM_PRIORITY
        return t
    }
}

fun <E> List<E?>.isAllNotNull(): Boolean = all { it != null }
fun <E> Array<E?>.isAllNotNull(): Boolean = all { it != null }

val minecraftDir = Loader.instance().configDir.parentFile!!
val fixCacheDir = minecraftDir.resolve("fixrtm-cache")
val MS932 = Charset.forName("MS932")
val fixRTMCommonExecutor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors(),
    threadFactoryWithPrefix("fixrtm-common-executor"))

fun File.directoryDigestBaseStream() = SequenceInputStream(
    Iterators.asEnumeration(
        this.sortedWalk()
            .flatMap {
                sequenceOf(
                    it.toRelativeString(this).byteInputStream(),
                    it.inputStream().buffered()
                )
            }
            .iterator()
    )
)

fun DataOutput.writeUTFNullable(string: String?) = closeScope {
    if (string == null) return writeShort(0xFFFF)

    string.map {
            when {
                it.code in 0x0001..0x007F -> 1
                it.code <= 0x07FF -> 2
                else -> 3
            }
        }
        .sum()
        .let { if (it >= 0xFFFF) throw UTFDataFormatException("encoded string too long: $it bytes") }

    val bytes = mutableListOf<Byte>()

    string.map { it.code }.forEach {
        when {
            it in 0x0001..0x007F -> bytes += it.toByte()
            it > 0x07FF -> {
                bytes += (0xE0 or (it shr 12 and 0x0F)).toByte()
                bytes += (0x80 or (it shr 6 and 0x3F)).toByte()
                bytes += (0x80 or (it shr 0 and 0x3F)).toByte()
            }
            else -> {
                bytes += (0xC0 or (it shr 6 and 0x1F)).toByte()
                bytes += (0x80 or (it shr 0 and 0x3F)).toByte()
            }
        }
    }

    write(bytes.toByteArray())
}

fun DataInput.readUTFNullable(): String? = closeScope {
    val length = readUnsignedShort()
    if (length == 0xFFFF) return null
    val bytes = ArrayPool.bytePool.request(length).closer().array
    val chars = ArrayPool.charPool.request(length).closer().array

    readFully(bytes)
    var byteI = 0
    var charI = 0

    while (byteI < length) {
        val c = bytes.get(byteI).toInt() and 0xff
        when (c shr 4) {
            0, 1, 2, 3, 4, 5, 6, 7 -> {
                /* 0xxxxxxx*/byteI++
                chars[charI++] = c.toChar()
            }
            12, 13 -> {
                /* 110x xxxx   10xx xxxx*/
                byteI += 2
                if (byteI > length) throw UTFDataFormatException("malformed input: partial character at end")
                val char2 = bytes[byteI - 1].toInt()
                if (char2 and 0xC0 != 0x80) throw UTFDataFormatException("malformed input around byte $byteI")
                chars[charI++] = (c and 0x1F shl 6)
                    .or(char2 and 0x3F)
                    .toChar()
            }
            14 -> {
                /* 1110 xxxx  10xx xxxx  10xx xxxx */
                byteI += 3
                if (byteI > length) throw UTFDataFormatException("malformed input: partial character at end")
                val char2 = bytes[byteI - 2].toInt()
                val char3 = bytes[byteI - 1].toInt()

                if (char2 and 0xC0 != 0x80 || char3 and 0xC0 != 0x80)
                    throw UTFDataFormatException("malformed input around byte ${byteI - 1}")

                chars[charI++] = (c and 0x0F shl 12)
                    .or(char2 and 0x3F shl 6)
                    .or(char3 and 0x3F shl 0)
                    .toChar()
            }
            else -> throw UTFDataFormatException("malformed input around byte $byteI")
        }
    }

    return String(chars, 0, charI)
}

fun File.mkParent(): File = apply { parentFile.mkdirs() }

val EntityPlayerMP.modList get() = NetworkDispatcher.get(this.connection.netManager)?.modList ?: emptyMap()
val EntityPlayerMP.hasFixRTM get() = server.isSinglePlayer || modList.containsKey(FixRtm.MODID)

@Suppress("unused")
fun Entity.rayTraceBothSide(blockReachDistance: Double, partialTicks: Float): RayTraceResult? {
    val eyePosition = getPositionEyes(partialTicks)
    val lookDir = getLook(partialTicks)
    val endPosition = eyePosition
        .add(lookDir.x * blockReachDistance, lookDir.y * blockReachDistance, lookDir.z * blockReachDistance)
    return world.rayTraceBlocks(eyePosition, endPosition,
        false, false, true)
}

@Suppress("EXTENSION_SHADOWED_BY_MEMBER", "unused")
fun <T> T.addEntityCrashInfoAboutModelSet(
    category: CrashReportCategory,
    configGetter: T.() -> ResourceConfig?,
) = try {
    val cfg = configGetter()
    if (cfg == null) {
        category.addCrashSection("Parent Train ModelSet Name", "no modelpack detected")
    } else {
        category.addCrashSection("Parent Train ModelSet Name", cfg.name)
        category.addCrashSection("Parent Train ModelSet Source JSON Path", cfg.file ?: "no source")
    }
} catch (t: Throwable) {
    category.addCrashSectionThrowable("Error Getting ModelSet", t)
}

fun arrayOfItemStack(size: Int) = Array(size) { ItemStack.EMPTY }

private const val START = 0
private const val KEY = 1
private const val STR_BS = 2
private const val STR_BS_U0 = 3 // \u|0000
private const val STR_BS_U1 = 4 // \u0|000
private const val STR_BS_U2 = 5 // \u00|00
private const val STR_BS_U3 = 6 // \u000|0
private const val STR = 7

fun joinLinesForJsonReading(lines: List<String>): String = buildString {
    var stat = START
    var shouldAddNewLine = false
    for (line in lines) {
        for (c in line) {
            val preStat = stat
            when (stat) {
                START -> {
                    if (c in '0'..'9' || c in 'a'..'z' || c in 'A'..'Z' || c == '+' || c == '-' || c == '.')
                        stat = KEY
                    else if (c == '"')
                        stat = STR
                }
                KEY -> {
                    if (c in '0'..'9' || c in 'a'..'z' || c in 'A'..'Z' || c == '+' || c == '-' || c == '.')
                        stat = KEY
                    else
                        stat = START
                }
                STR -> {
                    when (c) {
                        '\\' -> stat = STR_BS
                        '"' -> stat = START
                    }
                }
                STR_BS -> {
                    when (c) {
                        'u' -> stat = STR_BS_U0
                        else -> stat = STR
                    }
                }
                STR_BS_U0, STR_BS_U1, STR_BS_U2, STR_BS_U3 -> {
                    stat++
                }
            }
            if (shouldAddNewLine && (preStat == START || stat == START)) append('\n')
            shouldAddNewLine = false
            append(c)
        }
        shouldAddNewLine = true
    }
}

fun ItemStack.isItemOf(machine: TileEntityMachineBase): Boolean {
    if (this.item !== RTMItem.installedObject) return false
    val type = ItemInstalledObject.IstlObjType.getType(this.itemDamage)
    return type.type == machine.subType
}

fun ItemStack.isItemOf(istlType: ItemInstalledObject.IstlObjType): Boolean {
    if (this.item !== RTMItem.installedObject) return false
    val type = ItemInstalledObject.IstlObjType.getType(this.itemDamage)
    return type == istlType
}

fun ItemStack.isItemOf(resourceType: ResourceType<*, *>): Boolean {
    if (this.item !== RTMItem.installedObject) return false
    val type = ItemInstalledObject.IstlObjType.getType(this.itemDamage).type
    return type == resourceType
}

fun EntityPlayer.openGui(fixGuiId: GuiId, world: World, pos: Vec3i) {
    openGui(FixRtm, fixGuiId.ordinal, world, pos.x, pos.y, pos.z)
}

fun EntityPlayer.openGui(fixGuiId: GuiId, world: World, x: Int, y: Int, z: Int) {
    openGui(FixRtm, fixGuiId.ordinal, world, x, y, z)
}

fun String.trimOneLine() = trimIndent().replace(Regex("""\r\n|\n|\r"""), " ")
