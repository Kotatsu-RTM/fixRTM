package com.anatawa12.fixRtm.rtm.entity.train

import jp.ngt.ngtlib.math.NGTMath
import jp.ngt.rtm.entity.train.EntityTrainBase
import jp.ngt.rtm.entity.train.util.EnumNotch
import jp.ngt.rtm.entity.train.util.TrainState

fun EntityTrainBase.updateSpeed() {
    if (!onRail) return

    when {
        notch < 0 -> {
            val pressurePerBrake = notch * -18

            when {
                brakeCount < pressurePerBrake -> {
                    val perTick = if (notch == -8) 4 else 2
                    brakeCount = (brakeCount + perTick).coerceAtMost(pressurePerBrake)
                    if (world.isRemote) brakeAirCount -= perTick
                }
                brakeCount > pressurePerBrake -> {
                    brakeCount = (brakeCount - 2).coerceAtLeast(pressurePerBrake)
                }
            }
        }
        brakeCount > 0 -> brakeCount = (brakeCount - 4).coerceAtLeast(0)
    }

    val isBrakeDisabled = notch >= 0 && brakeCount > 0 && speed <= 0.0F

    if (isControlCar && !world.isRemote && !isBrakeDisabled) {
        val calcAcceleration = EnumNotch.getAcceleration(notch, speed, resourceState.resourceSet.config, this)

        val acceleration = if (getVehicleState(TrainState.TrainStateType.Role) == TrainState.Role_Back.data) {
            if (speed > 0.0F && notch < 0) calcAcceleration else -calcAcceleration
        } else {
            if (speed < 0.0F && notch < 0) -calcAcceleration else calcAcceleration
        }

        val frictionalDeceleration = when {
            notch < 0 -> 0.0F
            rotationPitch == 0.0F -> when {
                speed > 0.0F -> 0.0002F
                speed < 0.0F -> -0.0002F
                else -> 0.0F
            }
            else -> NGTMath.sin(rotationPitch) * (if (trainDirection == 0) 0.0125F else -0.0125F)
        }

        speed += acceleration - frictionalDeceleration
    }
}
