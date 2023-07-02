package com.anatawa12.fixRtm.rtm.modelpack.init

import com.anatawa12.fixRtm.rtm.modelpack.ModelState
import jp.ngt.ngtlib.io.NGTFileLoader
import jp.ngt.rtm.RTMResource
import jp.ngt.rtm.block.tt.TimeTableManager
import jp.ngt.rtm.modelpack.ModelPackException
import jp.ngt.rtm.modelpack.ModelPackManager
import jp.ngt.rtm.modelpack.cfg.RRSConfig
import jp.ngt.rtm.modelpack.init.ProgressStateHolder
import jp.ngt.rtm.modelpack.modelset.ModelSetBase
import jp.ngt.rtm.modelpack.modelset.ResourceSet
import kotlinx.coroutines.*
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.SimpleTexture
import net.minecraft.crash.CrashReport
import net.minecraftforge.fml.common.ProgressManager
import net.minecraftforge.fml.common.ProgressManager.ProgressBar
import org.apache.logging.log4j.LogManager
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

object ClientModelPackLoader {
    private val logger = LogManager.getLogger("fixRTM/ClientModelPackLoader")
    private val lock = ReentrantLock()

    fun load() {
        loadModels()
        constructModels()
        loadButtonTextures()
        cleanup()
        ModelPackManager.INSTANCE.setupPacketData()
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
        val size = unConstructSets.size

        runBlocking {
            val asynchronousLoadingModels =
                unConstructSets
                    .filter { !it.config.synchronousLoading }
                    .map {
                        CoroutineScope(Dispatchers.Default).async {
                            construct(progress, it, ++i, size)
                        }
                    }

            unConstructSets
                .filter { it.config.synchronousLoading }
                .forEach {
                    construct(progress, it, ++i, size)
                }

            asynchronousLoadingModels.forEach { it.await() }
        }

        ProgressManager.pop(progress)
    }

    private fun construct(progress: ProgressBar, resourceSet: ResourceSet<*>, index: Int, size: Int) {
        try {
            //progress.step(resourceSet.config.name)
            stepBar(progress, resourceSet.config.name)

            resourceSet.constructOnClient()
            resourceSet.finishConstruct()
            resourceSet.state = ModelState.CONSTRUCTED

            logger.trace("Model ${resourceSet.config.name} was constructed ($index / $size)")
        } catch (throwable: Throwable) {
            val sourceFile =
                resourceSet.config.file?.let { file -> "source file: $file" } ?: "unknown source file"
            val wrappedException =
                ModelConstructingException(
                    "constructing resource: ${resourceSet.config.name} ($sourceFile)",
                    throwable
                )

            CrashReport
                .makeCrashReport(wrappedException, "Constructing RTM ModelPack")
                .apply {
                    makeCategory("Initialization")
                    Minecraft.getMinecraft().addGraphicsAndWorldToCrashReport(this)
                }
                .let { report -> Minecraft.getMinecraft().displayCrashReport(report) }
        }
    }

    private fun stepBar(bar: ProgressBar, message: String) {
        lock.withLock {
            bar.step(message)
        }
    }

    private fun loadButtonTextures() {
        val progress = ProgressManager.push("Load button textures", 1)
        progress.step("")

        ModelPackManager.INSTANCE.allModelSetMap
            .map { it.value }
            .map { it.values }
            .filterIsInstance<ModelSetBase<*>>()
            .forEach {
                val buttonLocation = ModelPackManager.INSTANCE.getResource(it.config.buttonTexture)
                Minecraft.getMinecraft().textureManager.loadTexture(
                    buttonLocation,
                    SimpleTexture(buttonLocation)
                )
            }

        ProgressManager.pop(progress)
    }

    private fun cleanup() {
        ModelPackManager.INSTANCE.unconstructSets.clear()
        ModelPackManager.INSTANCE.clearCache()
    }
}
