/// Copyright (c) 2021 anatawa12 and other contributors
/// This file is part of fixRTM, released under GNU LGPL v3 with few exceptions
/// See LICENSE at https://github.com/fixrtm/fixRTM for more details

package com.anatawa12.fixRtm.crash

import com.anatawa12.fixRtm.rtm.modelpack.ModelState
import com.google.gson.GsonBuilder
import jp.ngt.ngtlib.util.NGTUtil
import jp.ngt.rtm.modelpack.ModelPackManager
import net.minecraftforge.common.util.TextTable
import net.minecraftforge.fml.common.ICrashCallable
import java.util.*
import kotlin.concurrent.withLock

object RTMSmallModelPackInfoCrashCallable : ICrashCallable {
    override fun call(): String = ModelPackManager.INSTANCE.modelSetMapLock.readLock().withLock {
        buildString {
            val initialized = "Initialized ${ModelPackManager.INSTANCE.allModelSetMap.values.sumOf { it.size }} models"
            val using = when (NGTUtil.isSMP()) {
                true -> ", Using ${ModelPackManager.INSTANCE.smpModelSetMap.values.sumOf { it.size }} models"
                else -> ""
            }

            append("${initialized}${using}\n\t")

            ModelState.values().joinTo(this) { "${it.marker} = $it" }
            append(", SMP = SMP included\n")

            val map = collectModelStates()

            val cols = mutableListOf<TextTable.Column>()
            cols.add(TextTable.column("model pack"))
            cols.add(TextTable.column("all"))

            ModelState.values().forEach { cols.add(TextTable.column(it.marker)) }
            if (NGTUtil.isSMP()) cols.add(TextTable.column("SMP"))

            val table = TextTable(cols)
            val valuesArray = arrayOfNulls<Any?>(cols.size)

            for ((packName, packInfo) in map) {
                var i = 0
                valuesArray[i++] = packName
                valuesArray[i++] = packInfo.allModelsCount
                for (state in ModelState.values()) {
                    valuesArray[i++] = packInfo.modelsCountByState[state] ?: 0
                }
                if (NGTUtil.isSMP())
                    valuesArray[i++] = packInfo.smpIncludedCount
                check(i == valuesArray.size)
                table.add(*valuesArray)
            }

            append("\n\n\t")
            table.append(this, "\n\t")
            append("\n")
        }
    }

    override fun getLabel(): String = "RTM Model Status"

    private val gson = GsonBuilder().create()

    private class ModelPackInfo {
        var allModelsCount = 0
        var modelsCountByState = EnumMap<ModelState, Int>(ModelState::class.java)
        var smpIncludedCount = 0

        fun addModel(resourceSetInfo: ResourceSetInfo) {
            allModelsCount++
            modelsCountByState[resourceSetInfo.state] =
                modelsCountByState.getOrDefault(resourceSetInfo.state, 0) + 1
            if (resourceSetInfo.isIncludedInSMP)
                smpIncludedCount++
        }
    }

    private fun collectModelStates(): Map<String, ModelPackInfo> {
        val map = mutableMapOf<String, ModelPackInfo>()

        val smp = NGTUtil.isSMP()

        for (resourceInfo in collectAllModels(smp)) {
            map.getOrPut(resourceInfo.sourcePackPath, ::ModelPackInfo)
                .addModel(resourceInfo)
        }
        return map
    }
}
