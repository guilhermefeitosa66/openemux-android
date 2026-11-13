package com.openemux.mobile.data

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** The emulator downloads without a gate (issue 922): what is known about them without touching the network. */
class SetupTest {
    private lateinit var root: File
    private lateinit var paths: Paths

    @Before
    fun setUp() {
        root = Files.createTempDirectory("setup").toFile()
        paths = Paths(File(root, "external"), File(root, "internal"))
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun setup() = Setup(paths, listOf("x86_64"))
    private val allCores = Console.entries.flatMap { it.cores }.distinctBy { it.id }

    @Test
    fun aFreshInstallIsIdleWithItsCoresUncounted_thenCountedOnRefresh() {
        val s = setup()
        assertEquals(Setup.State.Idle(-1), s.state.value)
        s.refresh()
        assertEquals(Setup.State.Idle(allCores.size), s.state.value)
        assertEquals(allCores.size, s.missingCores())
        assertFalse(s.infoInstalled())
    }

    @Test
    fun anInstalledCoreCountsAsPresentOnlyWithContent() {
        val s = setup()
        val core = Console.FC.cores.first()
        assertFalse(s.coreInstalled(core))
        File(paths.cores, core.fileName).apply { parentFile!!.mkdirs(); writeText("") }
        assertFalse(s.coreInstalled(core))
        File(paths.cores, core.fileName).writeText("so")
        assertTrue(s.coreInstalled(core))
        s.refresh()
        assertEquals(Setup.State.Idle(allCores.size - 1), s.state.value)
    }

    @Test
    fun ensureCoreNeedsNoNetworkWhenEverythingIsThere() = runBlocking {
        val s = setup()
        val core = Console.FC.cores.first()
        File(paths.cores, core.fileName).apply { parentFile!!.mkdirs(); writeText("so") }
        File(paths.internal, "setup/info.ok").apply { parentFile!!.mkdirs(); writeText("") }
        var reported = 0
        s.ensureCore(core) { _, _ -> reported++ }
        assertEquals(0, reported)
        assertTrue(s.infoInstalled())
    }

    @Test
    fun theStampFromAFullDownloadMeansDone() {
        File(paths.internal, "setup.done").apply { parentFile!!.mkdirs(); writeText("1/x86_64") }
        assertEquals(Setup.State.Done, setup().state.value)
        File(paths.internal, "setup.done").writeText("1/arm64-v8a")
        assertEquals(Setup.State.Idle(-1), setup().state.value)
    }

    @Test
    fun anUnsupportedDeviceFailsTheLaunchWithWords() = runBlocking {
        val s = Setup(paths, listOf("armeabi"))
        val core = Console.FC.cores.first()
        val error = runCatching { s.ensureCore(core) { _, _ -> } }.exceptionOrNull()
        assertTrue(error is Setup.SetupException)
        assertEquals(core.name, (error as Setup.SetupException).item)
        assertEquals("armeabi", error.detail)
    }
}
