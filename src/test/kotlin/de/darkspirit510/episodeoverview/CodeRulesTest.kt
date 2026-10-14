package de.darkspirit510.episodeoverview

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.provider.KoAnnotationProvider
import com.lemonappdev.konsist.api.verify.assertTrue
import kotlin.test.Test

class CodeRulesTest {

    private val scope = Konsist.scopeFromProject()

    @Test
    fun `every file declares at most one top-level class, interface or object`() {
        scope.files.assertTrue { file ->
            val topLevel = file.classes(includeNested = false, includeLocal = false).size +
                file.interfaces(includeNested = false).size +
                file.objects(includeNested = false).size
            topLevel <= 1
        }
    }

    @Test
    fun `extension functions and properties are opted in with Suppress ExtensionFunction`() {
        scope.functions().filter { it.isExtension }.assertTrue { isOptedIn(it) }
        scope.properties().filter { it.isExtension }.assertTrue { isOptedIn(it) }
    }

    private fun isOptedIn(declaration: KoAnnotationProvider) =
        declaration.annotations.any { it.name == "Suppress" && it.text.contains("\"ExtensionFunction\"") }
}
