package com.chenyubang.core.data

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** 已安装 ZIP 与 Room 的两步提交：先原子落盘，再导入；导入失败恢复旧 ZIP。 */
data class BootstrapPack(val pack: ValidatedPack, val isMock: Boolean)

class PackInstallStore(
    context: Context,
    private val packsDir: File = File(context.filesDir, "packs"),
    builtinAsset: String = DEFAULT_ASSET,
    private val builtin: () -> ByteArray = {
        context.applicationContext.assets.open(builtinAsset).use { it.readBytes() }
    },
    private val mock: () -> ByteArray = {
        context.applicationContext.assets.open(DEFAULT_MOCK_ASSET).use { it.readBytes() }
    },
) {
    val installed: File get() = File(packsDir, INSTALLED_NAME)

    suspend fun bootstrap(importer: PackImporter): BootstrapPack? =
        withContext(Dispatchers.IO) {
            if (installed.isFile) {
                try {
                    val pack = installed.inputStream().use(importer::validate)
                    importer.import(pack)
                    return@withContext BootstrapPack(pack, isMock = false)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // 损坏的已安装包不覆盖内置包；内置包导入成功后再移除损坏文件。
                }
            }
            try {
                val pack = builtin().inputStream().use(importer::validate)
                importer.import(pack)
                installed.delete()
                BootstrapPack(pack, isMock = false)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                try {
                    val pack = mock().inputStream().use(importer::validate)
                    importer.import(pack)
                    installed.delete()
                    BootstrapPack(pack, isMock = true)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                }
            }
        }

    suspend fun install(
        downloaded: File,
        importer: PackImporter,
        import: suspend (ValidatedPack) -> Unit,
    ): Unit =
        withContext(Dispatchers.IO) {
            require(packsDir.isDirectory || packsDir.mkdirs()) { "无法创建题库目录" }
            val backup = File(packsDir, BACKUP_NAME)
            val backupStaged = File(packsDir, BACKUP_STAGED_NAME)
            backupStaged.delete()
            backup.delete()
            val hadOld = installed.isFile
            if (hadOld) {
                copyAtomically(installed, backupStaged)
                atomicMove(backupStaged, backup)
            }
            atomicMove(downloaded, installed)
            syncFile(installed)
            var imported = false
            try {
                val pack = installed.inputStream().use(importer::validate)
                import(pack)
                imported = true
            } finally {
                if (imported) {
                    backup.delete()
                } else if (hadOld) {
                    atomicMove(backup, installed)
                } else {
                    installed.delete()
                }
                backupStaged.delete()
            }
        }

    private fun copyAtomically(
        source: File,
        staged: File,
    ) {
        FileOutputStream(staged).use { output ->
            source.inputStream().use { it.copyTo(output) }
            output.fd.sync()
        }
    }

    private fun atomicMove(
        source: File,
        target: File,
    ) {
        Files.move(
            source.toPath(),
            target.toPath(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING,
        )
    }

    private fun syncFile(file: File) {
        RandomAccessFile(file, "r").use { it.fd.sync() }
    }

    private companion object {
        const val DEFAULT_ASSET = "pack-v1.0.0.zip"
        const val DEFAULT_MOCK_ASSET = "pack-mock-v0.0.1.zip"
        const val INSTALLED_NAME = "installed-pack.zip"
        const val BACKUP_NAME = "installed-pack.previous.zip"
        const val BACKUP_STAGED_NAME = "installed-pack.previous.tmp"
    }
}
