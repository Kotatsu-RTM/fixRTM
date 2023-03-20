package com.anatawa12.fixRtm.rtm.modelpack.init

import com.anatawa12.fixRtm.rtm.modelpack.ModelState
import jp.ngt.ngtlib.io.NGTFileLoader
import jp.ngt.rtm.RTMResource
import jp.ngt.rtm.block.tt.TimeTableManager
import jp.ngt.rtm.modelpack.ModelPackException
import jp.ngt.rtm.modelpack.ModelPackManager
import jp.ngt.rtm.modelpack.cfg.RRSConfig
import jp.ngt.rtm.modelpack.init.ProgressStateHolder
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.SimpleTexture
import net.minecraft.crash.CrashReport
import net.minecraftforge.fml.common.ProgressManager
import org.apache.logging.log4j.LogManager

object ClientModelPackLoader {
    private val logger = LogManager.getLogger("fixRTM/ClientModelPackLoader")

    fun load() {
        loadModels()
        constructModels()
        cleanup()
    }

    private fun loadModels() {
        run {
            val progress = ProgressManager.push("Load files", 1)
            progress.step("")
            logger.debug("Load files")

            val files = NGTFileLoader.findFile {
                val path = it.absolutePath
                val name = it.name
                !path.contains("block") && !path.contains("item") && !name.contains(".json")
            }.also { ModelPackManager.INSTANCE.fileCache.addAll(it) }

            logger.debug("Found ${files.size} files")
            ProgressManager.pop(progress)
        }

        run {
            val progress = ProgressManager.push(ProgressStateHolder.ProgressState.SEARCHING_MODEL.message, 1)
            progress.step("")
            logger.debug("Start searching jsons")

            val files = NGTFileLoader.findFile {
                val path = it.absolutePath
                val name = it.name
                !path.contains("block") && !path.contains("item") && name.endsWith(".json")
            }

            logger.debug("Found ${files.size} jsons")
            ProgressManager.pop(progress)

            files
        }.run {
            var i = 0
            val progress = ProgressManager.push(ProgressStateHolder.ProgressState.LOADING_MODEL.message, size)
            logger.debug("Start registering jsons")

            forEach {
                progress.step(it.name)

                if (!it.name.contains("_")) return@forEach
                val type = ModelPackManager.INSTANCE.getType(it.name.split("_").first()) ?: return@forEach

                try {
                    ModelPackManager.INSTANCE.registerResourceSet(type, it)
                } catch (exception: ModelPackException) {
                    throw exception
                } catch (throwable: Throwable) {
                    throw ModelPackException("Can't load model", it.absolutePath, throwable)
                }

                ++i
            }

            logger.debug("Registered $i jsons")
            ProgressManager.pop(progress)
        }

        run {
            val progress = ProgressManager.push(ProgressStateHolder.ProgressState.SEARCHING_RRS.message, 1)
            progress.step("")
            logger.debug("Start searching rail load signs")

            val files = NGTFileLoader.findFile { it.name.startsWith("rrs_") && it.name.endsWith(".png") }

            logger.debug("Found ${files.size} rail load signs")
            ProgressManager.pop(progress)

            files
        }.run {
            val progress = ProgressManager.push(ProgressStateHolder.ProgressState.LOADING_MODEL.message, size)
            logger.debug("Start registering rail load signs")

            forEach {
                progress.step(it.name)

                ModelPackManager.INSTANCE
                    .registerResourceSet(RTMResource.RRS, RRSConfig(it.name).apply { file = it }, "")

                //Framerate drops when load at runtime, so we'll load it beforehand.
                ModelPackManager.INSTANCE.getResource(RRSConfig.fixName(it.name)).let { signLocation ->
                    Minecraft.getMinecraft().textureManager.loadTexture(
                        signLocation,
                        SimpleTexture(signLocation)
                    )
                }
            }

            logger.debug("Registered rail load signs")
            ProgressManager.pop(progress)
        }

        TimeTableManager.INSTANCE.load()
        ModelPackManager.INSTANCE.modelLoaded = true
    }

    private fun constructModels() {
        val unConstructSets = ModelPackManager.INSTANCE.unconstructSets
        var i = 0
        val progress = ProgressManager.push("Construct Model", unConstructSets.size)

        unConstructSets.forEach {
            try {
                progress.step(it.config.name)

                it.constructOnClient()
                it.finishConstruct()
                it.state = ModelState.CONSTRUCTED

                logger.trace("Model ${it.config.name} was constructed (${++i} / ${unConstructSets.size})")
            } catch (throwable: Throwable) {
                val sourceFile = it.config.file?.let { file -> "source file: $file" } ?: "unknown source file"
                val wrappedException =
                    ModelConstructingException("constructing resource: ${it.config.name} ($sourceFile)", throwable)

                CrashReport
                    .makeCrashReport(wrappedException, "Constructing RTM ModelPack")
                    .apply {
                        makeCategory("Initialization")
                        Minecraft.getMinecraft().addGraphicsAndWorldToCrashReport(this)
                    }
                    .let { report -> Minecraft.getMinecraft().displayCrashReport(report) }
            }
        }

        ProgressManager.pop(progress)
    }

    private fun cleanup() {
        ModelPackManager.INSTANCE.unconstructSets.clear()
        ModelPackManager.INSTANCE.clearCache()
    }
}
