package it.sephiroth.android.app.appunti.io

import timber.log.Timber
import java.io.File
import java.net.URI

class RelativePath(val baseDir: File, paths: Array<String>) {

    init {
        Timber.i("RelativePath(${baseDir.absolutePath}, ${paths.toList()}")
    }

    constructor(base: File, path: String) : this(base, arrayOf(path))

    constructor(base: RelativePath, vararg paths: String) : this(
        base.baseDir,
        base.pathArray.toMutableList().also {
            it.addAll(paths)
        }.toTypedArray()
    )

    private val pathArray: List<String> = paths.toList()

    val file = File(baseDir, paths.joinToString(File.separator))
    val path = pathArray.joinToString(File.separator)

    val absolutePath: String = file.absolutePath
    val name: String = file.name
    val extension: String = file.extension

    fun toURI(): URI? = file.toURI()
    fun exists(): Boolean = file.exists()

    override fun toString(): String {
        return "RelativePath($baseDir, $path, ${file.absolutePath})"
    }


}