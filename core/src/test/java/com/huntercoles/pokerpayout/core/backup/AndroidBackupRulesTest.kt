package com.huntercoles.pokerpayout.core.backup

import org.junit.jupiter.api.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * PP-137: Android's own backup (the Google cloud copy and a move to a new phone, Android 12 and up:
 * app/src/main/res/xml/data_extraction_rules.xml; Android 11 and older: backup_rules.xml) takes every
 * file the in-app backup takes, and none of the [BackupCatalog.PHONE_FILES]. The rules are read as
 * Android reads them: a SharedPreferences file is taken when an `<include>` covers it (or there is
 * none) and no `<exclude>` does; a path of "." is the whole domain, a file's path its name with
 * ".xml". [BackupCoverageTest] makes sure every file the app opens is in the catalog.
 */
class AndroidBackupRulesTest {

    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").isFile }

    private val rulesDir = File(root, "app/src/main/res/xml")

    /** One set of rules: a cloud backup, a device transfer, or the whole of the older format. */
    private class Rules(val name: String, val includes: List<Rule>, val excludes: List<Rule>) {
        fun takesPrefs(file: String): Boolean {
            val covers = { rule: Rule -> rule.domain == SHAREDPREF && (rule.path == "." || rule.path == "$file.xml") }
            return (includes.isEmpty() || includes.any(covers)) && excludes.none(covers)
        }
    }

    private data class Rule(val domain: String, val path: String)

    private fun read(file: String): Element {
        val source = File(rulesDir, file)
        assertTrue(source.isFile, "missing $source")
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(source).documentElement
    }

    private fun Element.rules(name: String): Rules = Rules(name, entries("include"), entries("exclude"))

    private fun Element.entries(tag: String): List<Rule> {
        val nodes = getElementsByTagName(tag)
        return (0 until nodes.length).map { index ->
            val rule = nodes.item(index) as Element
            Rule(rule.getAttribute("domain"), rule.getAttribute("path"))
        }
    }

    /** The cloud backup and the device transfer (Android 12 and up), and the older format's rules. */
    private fun everyRuleSet(): List<Rules> {
        val modern = read(DATA_EXTRACTION_RULES)
        assertEquals("data-extraction-rules", modern.tagName)
        val sections = listOf("cloud-backup", "device-transfer").map { tag ->
            val found = modern.getElementsByTagName(tag)
            assertEquals(1, found.length, "$DATA_EXTRACTION_RULES has one <$tag>")
            (found.item(0) as Element).rules("$DATA_EXTRACTION_RULES <$tag>")
        }
        val legacy = read(BACKUP_RULES)
        assertEquals("full-backup-content", legacy.tagName)
        return sections + legacy.rules(BACKUP_RULES)
    }

    @Test
    fun `what's about this phone alone is left out of the cloud copy and a move to a new phone`() {
        assertTrue(BackupCatalog.PHONE_FILES.isNotEmpty())
        everyRuleSet().forEach { rules ->
            BackupCatalog.PHONE_FILES.forEach { file ->
                assertFalse(rules.takesPrefs(file), "${rules.name} takes $file, which is about this phone alone")
            }
        }
    }

    @Test
    fun `the owner's files go with the owner to a new phone`() {
        assertTrue(BackupCatalog.OWNER_FILES.isNotEmpty())
        everyRuleSet().forEach { rules ->
            BackupCatalog.OWNER_FILES.forEach { file -> assertTrue(rules.takesPrefs(file), "${rules.name} leaves out $file") }
        }
    }

    @Test
    fun `everything the in-app backup takes, Android's backup takes too`() {
        everyRuleSet().forEach { rules ->
            BackupCatalog.FILES.forEach { file -> assertTrue(rules.takesPrefs(file), "${rules.name} leaves out $file") }
        }
    }

    @Test
    fun `the cloud copy is made only when it is encrypted`() {
        val cloud = read(DATA_EXTRACTION_RULES).getElementsByTagName("cloud-backup").item(0) as Element
        assertEquals("true", cloud.getAttribute("disableIfNoEncryptionCapabilities"))
    }

    @Test
    fun `the app's manifest uses these rules`() {
        val manifest = File(root, "app/src/main/AndroidManifest.xml").readText()
        listOf(
            "android:allowBackup=\"true\"",
            "android:dataExtractionRules=\"@xml/${DATA_EXTRACTION_RULES.removeSuffix(".xml")}\"",
            "android:fullBackupContent=\"@xml/${BACKUP_RULES.removeSuffix(".xml")}\"",
        ).forEach { attribute -> assertTrue(attribute in manifest, "the manifest has no $attribute") }
    }

    private companion object {
        const val DATA_EXTRACTION_RULES = "data_extraction_rules.xml"
        const val BACKUP_RULES = "backup_rules.xml"
        const val SHAREDPREF = "sharedpref"
    }
}
