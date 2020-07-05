package baaahs.sim

import baaahs.io.Fs

class MergedFs(val baseFs: Fs, val overlayFs: Fs) : Fs {
    override fun listFiles(parent: Fs.File): List<Fs.File> {
        return (baseFs.listFiles(parent) + overlayFs.listFiles(parent))
            .distinct()
            .map { Fs.File(this, it.fullPath, it.isDirectory) }
    }

    override fun loadFile(file: Fs.File): String? {
        return overlayFs.loadFile(file) ?: baseFs.loadFile(file)
    }

    override fun saveFile(file: Fs.File, content: ByteArray, allowOverwrite: Boolean) {
        baseFs.saveFile(file, content, allowOverwrite)
    }

    override fun saveFile(file: Fs.File, content: String, allowOverwrite: Boolean) {
        baseFs.saveFile(file, content, allowOverwrite)
    }

    override fun exists(file: Fs.File): Boolean {
        return baseFs.exists(file) || overlayFs.exists(file)
    }
}