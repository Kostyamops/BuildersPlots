package com.kostyamops.buildersplots.data

import java.io.File
import java.io.IOException
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes

object FileUtils {

    fun moveWorldFolder(source: File, destination: File) {
        if (destination.exists()) {
            deleteDirectory(destination)
        }

        try {
            Files.move(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } catch (e: Exception) {
            copyDirectory(source, destination)
            deleteDirectory(source)
        }
    }

    fun copyDirectory(source: File, destination: File) {
        if (!destination.exists()) {
            destination.mkdirs()
        }

        val sourcePath = source.toPath()
        val destinationPath = destination.toPath()

        Files.walkFileTree(sourcePath, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                val targetDir = destinationPath.resolve(sourcePath.relativize(dir))
                try {
                    Files.createDirectories(targetDir)
                } catch (e: Exception) {
                    return FileVisitResult.SKIP_SUBTREE
                }
                return FileVisitResult.CONTINUE
            }

            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                val targetFile = destinationPath.resolve(sourcePath.relativize(file))
                try {
                    Files.copy(file, targetFile, StandardCopyOption.REPLACE_EXISTING)
                } catch (e: Exception) {
                    // Silently continue
                }
                return FileVisitResult.CONTINUE
            }
        })
    }

    fun deleteDirectory(directory: File) {
        try {
            Files.walkFileTree(directory.toPath(), object : SimpleFileVisitor<Path>() {
                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    Files.delete(file)
                    return FileVisitResult.CONTINUE
                }

                override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                    Files.delete(dir)
                    return FileVisitResult.CONTINUE
                }
            })
        } catch (e: Exception) {
            // Handle silently
        }
    }

    fun forceDeleteFile(file: File) {
        try {
            val tempFile = File(file.parentFile, "temp_delete_${System.currentTimeMillis()}_${file.name}")
            if (file.renameTo(tempFile)) {
                tempFile.deleteOnExit()

                System.gc()
                Thread.sleep(100)

                tempFile.delete()
            }
        } catch (e: Exception) {
            // Silently fail
        }
    }

    fun deleteDirectoryCompletely(directory: File, logWarning: (String, Map<String, String>) -> Unit) {
        if (!directory.exists()) return

        try {
            val regionFolders = arrayOf(
                File(directory, "region"),
                File(directory, "DIM1/region"),
                File(directory, "DIM-1/region")
            )

            for (regionFolder in regionFolders) {
                if (regionFolder.exists() && regionFolder.isDirectory) {
                    regionFolder.listFiles()?.forEach { file ->
                        if (file.name.endsWith(".mca")) {
                            if (!file.delete()) {
                                file.deleteOnExit()
                            }
                        }
                    }
                }
            }

            Files.walkFileTree(directory.toPath(), object : SimpleFileVisitor<Path>() {
                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    try {
                        Files.delete(file)
                    } catch (e: Exception) {
                        file.toFile().deleteOnExit()
                    }
                    return FileVisitResult.CONTINUE
                }

                override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                    try {
                        Files.delete(dir)
                    } catch (e: Exception) {
                        dir.toFile().deleteOnExit()
                    }
                    return FileVisitResult.CONTINUE
                }
            })
        } catch (e: Exception) {
            logWarning("plotmanager.directory.delete.error",
                mapOf("%error%" to e.message.toString()))
        }
    }
}