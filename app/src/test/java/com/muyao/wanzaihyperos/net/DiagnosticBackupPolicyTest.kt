package com.muyao.wanzaihyperos.net

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test
import org.w3c.dom.Element

class DiagnosticBackupPolicyTest {
    private val domains = setOf("root", "file", "database", "sharedpref", "external",
        "device_root", "device_file", "device_database", "device_sharedpref")
    private val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }

    @Test fun systemBackupAndBothCloudAndDeviceTransferExcludeAllPrivateDomains() {
        val manifest = factory.newDocumentBuilder().parse(File("src/main/AndroidManifest.xml"))
        val application = manifest.getElementsByTagName("application").item(0) as Element
        val android = "http://schemas.android.com/apk/res/android"
        assertEquals("false", application.getAttributeNS(android, "allowBackup"))
        assertEquals("@xml/backup_rules", application.getAttributeNS(android, "fullBackupContent"))
        assertEquals("@xml/data_extraction_rules", application.getAttributeNS(android, "dataExtractionRules"))

        val legacy = factory.newDocumentBuilder().parse(File("src/main/res/xml/backup_rules.xml"))
        assertEquals("full-backup-content", legacy.documentElement.tagName)
        verifyAllExcluded(legacy.documentElement)
        val modern = factory.newDocumentBuilder().parse(File("src/main/res/xml/data_extraction_rules.xml"))
        assertEquals("data-extraction-rules", modern.documentElement.tagName)
        listOf("cloud-backup", "device-transfer").forEach { name ->
            val containers = modern.getElementsByTagName(name)
            assertEquals(name, 1, containers.length)
            verifyAllExcluded(containers.item(0) as Element)
        }
    }

    @Test fun manualPreferenceExportKeepsItsExplicitNonPrivateWhitelist() {
        val source = File("src/main/java/com/muyao/wanzaihyperos/data/PrefsBackup.kt").readText()
        val whitelist = source.substringAfter("private val labels = linkedMapOf(").substringBefore(")")
        val keys = Regex("\"([^\"]+)\"\\s+to").findAll(whitelist).map { it.groupValues[1] }.toSet()
        assertEquals(setOf("theme", "background", "glass", "container_style", "home_density",
            "home_modules", "apps_web_collapsed", "timetable_grid", "wallpaper_preset", "wallpaper_dim",
            "wallpaper_soft", "glass_opacity", "reduce_motion", "reduce_transparency", "high_contrast"), keys)
        assertTrue(keys.none { it.contains("token") || it.contains("password") || it.contains("path") })
    }

    private fun verifyAllExcluded(container: Element) {
        assertEquals(0, container.getElementsByTagName("include").length)
        val excludes = container.getElementsByTagName("exclude")
        val actual = (0 until excludes.length).map { index ->
            val entry = excludes.item(index) as Element
            assertEquals(".", entry.getAttribute("path"))
            entry.getAttribute("domain")
        }
        assertEquals(domains, actual.toSet())
        assertEquals(domains.size, actual.size)
    }
}
