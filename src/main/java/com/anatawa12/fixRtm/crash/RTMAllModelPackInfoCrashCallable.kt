/// Copyright (c) 2021 anatawa12 and other contributors
/// This file is part of fixRTM, released under GNU LGPL v3 with few exceptions
/// See LICENSE at https://github.com/fixrtm/fixRTM for more details

package com.anatawa12.fixRtm.crash

import com.anatawa12.fixRtm.utils.Base64OutputStream
import com.anatawa12.fixRtm.utils.ToStringOutputStream
import com.google.gson.GsonBuilder
import jp.ngt.ngtlib.util.NGTUtil
import jp.ngt.rtm.modelpack.ModelPackManager
import net.minecraftforge.fml.common.ICrashCallable
import java.util.zip.GZIPOutputStream
import kotlin.concurrent.withLock

object RTMAllModelPackInfoCrashCallable : ICrashCallable {
    override fun call(): String = ModelPackManager.INSTANCE.modelSetMapLock.readLock().withLock {
        buildString {
            val initialized = "Initialized ${ModelPackManager.INSTANCE.allModelSetMap.values.sumOf { it.size }} models"
            val using = when (NGTUtil.isSMP()) {
                true -> ", Using ${ModelPackManager.INSTANCE.smpModelSetMap.values.sumOf { it.size }} models"
                else -> ""
            }

            append(
                """
                    ${initialized}${using}

                    The data below included the data about all models. the data is pom-like(but returned per 128 chars)
                    and body is gzipped json. If you want not to include this data to crash-report,
                    you can disable from 'better_rtm.addModelPackInformationInAllCrashReports' in config/fix-rtm.cfg
                    This data can be decoded at https://fixrtm.github.io/crash_report_model_info_parser.html
                    If you want to know which file the model is included in, You can analyze with it.

                    -----BEGIN REAL TRAIN MOD MODEL PACK INFORMATION-----

                """.trimIndent()
            )

            val map = printModelStates()
            val json = gson.toJson(listOf(1, map))

            GZIPOutputStream(
                Base64OutputStream(
                    ToStringOutputStream(this), addPadding = true,
                    addNewLinePer64 = true, addNewLineAtEnd = true
                )
            ).writer().use { it.write(json) }

            append("-----END REAL TRAIN MOD MODEL PACK INFORMATION-----\n")
        }
    }

    override fun getLabel(): String = "RTM Model Status"

    private val gson = GsonBuilder().create()

    private fun printModelStates(): Map<String, Any> {
        val map = mutableMapOf<String, List<List<Any>>>()

        val smp = NGTUtil.isSMP()

        val allModels = collectAllModels(smp)
        for ((sourcePackPath, models) in allModels.groupBy { it.sourcePackPath }) {
            map[sourcePackPath] = models.map { model ->
                listOf(
                    model.state.toString(),
                    model.id,
                    model.sourceFile,
                    model.isIncludedInSMP,
                )
            }
        }
        return map
    }
    /*
    the json formant
    [version, value]
    // v1.0 format
    [1,Map<model_pack:String,List<ModelPack>>]
    ModelPack=[state:string,id:string,sourceName:string,includedInSMP:boolean]
     */
}
