package lgka.api

import java.io.File
import java.nio.file.Files

object Fixtures {
    fun text(name: String): String =
        checkNotNull(Fixtures::class.java.getResourceAsStream("/api/$name")) { "missing fixture $name" }
            .bufferedReader().use { it.readText() }

    fun tempDir(prefix: String = "lgka-store"): File = Files.createTempDirectory(prefix).toFile().also { it.deleteOnExit() }
}
