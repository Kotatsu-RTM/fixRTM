/// Copyright (c) 2020 anatawa12 and other contributors
/// This file is part of fixRTM, released under GNU LGPL v3 with few exceptions
/// See LICENSE at https://github.com/fixrtm/fixRTM for more details

import com.anatawa12.fixrtm.gradle.classHierarchy.ClassHierarchy
import com.anatawa12.fixrtm.gradle.classHierarchy.HClass
import com.anatawa12.fixrtm.gradle.walkBottomUp
import org.gradle.api.DefaultTask
import org.gradle.api.file.FileTree
import org.gradle.api.tasks.*
import org.gradle.internal.impldep.org.eclipse.jgit.lib.ObjectChecker.type
import java.io.File
import java.util.function.Predicate

open class PrintFieldStructure : DefaultTask() {
    @InputFiles
    var files: FileTree = project.files().asFileTree

    @OutputDirectory
    var outTo: File? = null

    @Input
    var superClass: String? = null

    @Input
    var exclusion: MutableSet<String> = mutableSetOf()

    @Internal
    var condition: Predicate<String> = Predicate { true }

    init {
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun run() {
        val classes = ClassHierarchy().apply { load(files) }
        val outTo = outTo ?: error("outTo not inited")
        val superClass = superClass ?: error("superClass not inited")

        outTo.deleteRecursively()

        val rootClass = classes.getByInternalName(superClass.replace('.', '/'))
        val printClasses = rootClass.getAllClass().sortedBy { it.internalName }

        for (hClass in printClasses) {
            val file = buildString {
                if (!condition.test(hClass.internalName) || !hClass.isLoaded) {
                    append("not loaded class\n")
                    return@buildString
                }

                append("super: ${hClass.parentClass?.internalName}")

                hClass.fields.filter { !it.isStatic }.sortedBy { it.name }.forEach {
                    append(
                        """

                            name: ${it.name}
                            type: ${it.type}

                        """.trimIndent()
                    )
                }
            }

            outTo.resolve("${hClass.internalName}.txt")
                .apply { parentFile.mkdirs() }
                .writeText(file)
        }
    }

    fun exclude(name: String) {
        exclusion.add(name)
    }

    private fun HClass.getAllClass(): Set<HClass> {
        val runed = exclusion.map { it.replace('.', '/') }.toMutableSet()
        runed.add("java/lang/Object")
        return walkBottomUp {
            sequence {
                if (internalName in runed) return@sequence
                runed += internalName
                if (!condition.test(internalName)) return@sequence
                yieldAll(childClasses)
                if (isLoaded) {
                    yieldAll(fields.asSequence()
                        .filter { !it.isStatic }
                        .map { it.type.substring(it.type.indexOfFirst { it != '[' }) }
                        .filter { it[0] == 'L' }
                        .map { it.substring(1, it.length - 1) }
                        .map { loader.getByInternalName(it) }
                        .toSet())
                }
            }

        }.toSet()
    }
}
