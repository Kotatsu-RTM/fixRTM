package com.anatawa12.fixRtm.rtm.entity.train.util

import jp.ngt.ngtlib.io.ScriptUtil
import jp.ngt.rtm.entity.train.EntityTrainBase
import jp.ngt.rtm.entity.train.util.EnumNotch
import jp.ngt.rtm.modelpack.cfg.TrainConfig
import kotlin.math.abs

fun getAcceleration(notch: Int, prevSpeed: Float, config: TrainConfig, train: EntityTrainBase): Float {
    val scriptEngine = train.resourceState.resourceSet.serverSE

    return when {
        notch > 0 -> when {
            abs(prevSpeed) >= config.maxSpeed[notch - 1] -> 0.0F
            config.useVariableAcceleration -> {
                ScriptUtil
                    .doScriptIgnoreError(scriptEngine, "getAcceleration", train, prevSpeed)
                    .toString()
                    .toFloatOrNull() ?: 0.0F
            }
            else -> config.accelerateion
        }
        notch < 0 -> {
            val deceleration = if (config.useVariableDeceleration) {
                ScriptUtil
                    .doScriptIgnoreError(scriptEngine, "getDeceleration", train, prevSpeed)
                    .toString()
                    .toFloatOrNull() ?: 0.0F
            } else {
                (EnumNotch.values().firstOrNull { it.id == notch } ?: EnumNotch.inertia).acceleration
            }

            deceleration.coerceAtLeast(-abs(prevSpeed))
        }
        else -> 0.0F
    }
}
