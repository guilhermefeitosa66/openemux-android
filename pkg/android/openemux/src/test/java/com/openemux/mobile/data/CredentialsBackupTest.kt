package com.openemux.mobile.data

import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The ScreenScraper accounts never leave the device (issue 753). */
class CredentialsBackupTest {
    private fun res(name: String) = listOf(File("src/main/res/xml/$name"), File("pkg/android/openemux/src/main/res/xml/$name")).first { it.isFile }.readText()

    @Test
    fun `no Android backup or device transfer carries the accounts`() {
        val line = """<exclude domain="sharedpref" path="${PrefsAccountStore.PREFS}.xml" />"""
        assertTrue(res("backup_rules.xml").contains(line))
        // Once under cloud-backup and once under device-transfer.
        assertTrue(res("data_extraction_rules.xml").split(line).size == 3)
    }

    @Test
    fun `the app's own backup exports another preferences file`() {
        // BackupManager exports the "openemux" preferences only.
        assertNotEquals("openemux", PrefsAccountStore.PREFS)
    }
}
