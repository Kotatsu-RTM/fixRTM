/// Copyright (c) 2021 anatawa12 and other contributors
/// This file is part of fixRTM, released under GNU LGPL v3 with few exceptions
/// See LICENSE at https://github.com/fixrtm/fixRTM for more details

package com.anatawa12.fixRtm.asm.hooking

import com.anatawa12.fixRtm.trimOneLine
import net.minecraft.launchwrapper.IClassTransformer
import org.objectweb.asm.*

class HookingTransformer : IClassTransformer {
    override fun transform(name: String?, transformedName: String?, basicClass: ByteArray?): ByteArray? {
        if (basicClass == null) return null
        val cw = ClassWriter(0)
        var cv: ClassVisitor = cw
        cv = MethodVisitingClassVisitor(
            cv, listOfNotNull(
                ::NewEntityTrackerVisitor.takeUnless { name == "com.anatawa12.fixRtm.rtm.entity.vehicle.VehicleTrackerEntryKt" },
            )
        )
        if (name == "net.minecraftforge.fml.common.FMLContainer") cv = FMLContainerClassVisitor(cv)
        if (name == "net.minecraftforge.fml.common.FMLModContainer") cv = FMLModContainerClassVisitor(cv)
        if (name == "net.minecraftforge.fml.client.FMLClientHandler") cv = FMLClientHandlerClassVisitor(cv)
        ClassReader(basicClass).accept(cv, 0)
        return cw.toByteArray()
    }

    class NewEntityTrackerVisitor(mv: MethodVisitor) : MethodVisitor(Opcodes.ASM5, mv) {
        var afterNew = false

        override fun visitTypeInsn(opcode: Int, type: String?) {
            if (opcode == Opcodes.NEW && type == "net/minecraft/entity/EntityTrackerEntry")
                afterNew = true
            super.visitTypeInsn(opcode, type)
        }

        override fun visitMethodInsn(opcode: Int, owner: String?, name: String?, desc: String?, itf: Boolean) {
            if (afterNew
                && opcode == Opcodes.INVOKESPECIAL
                && owner == "net/minecraft/entity/EntityTrackerEntry"
                && name == "<init>"
                && desc == "(Lnet/minecraft/entity/Entity;IIIZ)V"
            ) {
                afterNew = false
                super.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    "com/anatawa12/fixRtm/rtm/entity/vehicle/VehicleTrackerEntryKt",
                    "newEntityTrackerEntry",
                    "(Lnet/minecraft/entity/Entity;IIIZ)Lnet/minecraft/entity/EntityTrackerEntry;",
                    false
                )
                super.visitInsn(Opcodes.SWAP)
                super.visitInsn(Opcodes.POP)
                super.visitInsn(Opcodes.SWAP)
                super.visitInsn(Opcodes.POP)
                return
            }
            super.visitMethodInsn(opcode, owner, name, desc, itf)
        }
    }

    class MethodVisitingClassVisitor(
        cv: ClassVisitor,
        val factories: List<(mv: MethodVisitor) -> MethodVisitor?>,
    ) : ClassVisitor(Opcodes.ASM5, cv) {
        override fun visitMethod(
            access: Int,
            name: String?,
            desc: String?,
            signature: String?,
            exceptions: Array<out String>?,
        ): MethodVisitor? {
            var mv = super.visitMethod(access, name, desc, signature, exceptions) ?: return null
            for (factory in factories) {
                mv = factory(mv) ?: mv
            }
            return mv
        }
    }

    class FMLContainerClassVisitor(cv: ClassVisitor) : ClassVisitor(Opcodes.ASM5, cv) {
        override fun visitMethod(
            access: Int,
            name: String?,
            desc: String?,
            signature: String?,
            exceptions: Array<out String>?
        ): MethodVisitor? = super.visitMethod(access, name, desc, signature, exceptions).let {
            if (
                name == "readData" && desc == """
                        (L
                        net/minecraft/world/storage/SaveHandler;L
                        net/minecraft/world/storage/WorldInfo;L
                        java/util/Map;L
                        net/minecraft/nbt/NBTTagCompound;)V
                    """.trimOneLine()
            ) FMLContainerReadDataMethodVisitor(it) else it
        }
    }

    class FMLContainerReadDataMethodVisitor(mv: MethodVisitor) : MethodVisitor(Opcodes.ASM5, mv) {
        override fun visitCode() {
            super.visitCode()
            super.visitMethodInsn(
                Opcodes.INVOKESTATIC, "com/anatawa12/fixRtm/FixHooks",
                "onFMLReadData", "()V", false
            )
        }
    }

    class FMLModContainerClassVisitor(cv: ClassVisitor) : ClassVisitor(Opcodes.ASM5, cv) {
        override fun visitMethod(
                access: Int,
                name: String?,
                desc: String?,
                signature: String?,
                exceptions: Array<out String>?
        ): MethodVisitor? {
            var mv = super.visitMethod(access, name, desc, signature, exceptions) ?: return null
            if (name == "constructMod" && desc == "(L" +
                    "net/minecraftforge/fml/common/event/FMLConstructionEvent;)V") {
                mv = FMLModContainerConstructModMethodVisitor(mv)
            }
            return mv
        }
    }

    class FMLModContainerConstructModMethodVisitor(mv: MethodVisitor) : MethodVisitor(Opcodes.ASM5, mv) {
        override fun visitMethodInsn(opcode: Int, owner: String?, name: String?, desc: String?, itf: Boolean) {
            super.visitMethodInsn(opcode, owner, name, desc, itf)
            if (opcode == Opcodes.INVOKEVIRTUAL &&
                    owner == "net/minecraftforge/fml/common/ModClassLoader" &&
                    name == "clearNegativeCacheFor" &&
                    desc == "(Ljava/util/Set;)V") {
                super.visitVarInsn(Opcodes.ALOAD, 0)
                super.visitVarInsn(Opcodes.ALOAD, 1)
                super.visitMethodInsn(Opcodes.INVOKESTATIC, "com/anatawa12/fixRtm/versionCheck/VersionCheck",
                        "onConstructMod", "(L" +
                        "net/minecraftforge/fml/common/FMLModContainer;L" +
                        "net/minecraftforge/fml/common/event/FMLConstructionEvent;)V", false)
            }
        }
    }


    class FMLClientHandlerClassVisitor(cv: ClassVisitor) : ClassVisitor(Opcodes.ASM5, cv) {
        override fun visitMethod(
                access: Int,
                name: String?,
                desc: String?,
                signature: String?,
                exceptions: Array<out String>?
        ): MethodVisitor? {
            var mv = super.visitMethod(access, name, desc, signature, exceptions) ?: return null
            if (name == "beginMinecraftLoading" && desc == "(L" +
                    "net/minecraft/client/Minecraft;L" +
                    "java/util/List;Lnet/minecraft/client/resources/IReloadableResourceManager;L" +
                    "net/minecraft/client/resources/data/MetadataSerializer;)V") {
                mv = FMLClientHandlerBeginMinecraftLoadingMethodVisitor(mv)
            }
            return mv
        }
    }

    class FMLClientHandlerBeginMinecraftLoadingMethodVisitor(mv: MethodVisitor) : MethodVisitor(Opcodes.ASM5, mv) {
        override fun visitTryCatchBlock(start: Label?, end: Label?, handler: Label?, type: String?) {
            super.visitTryCatchBlock(start, end, handler, type)
            if (type == "net/minecraftforge/fml/common/WrongMinecraftVersionException") {
                super.visitTryCatchBlock(start, end, handler,
                        "com/anatawa12/fixRtm/versionCheck/RtmOrNgtLibVersionMismatchException")
            }
        }
    }
}
