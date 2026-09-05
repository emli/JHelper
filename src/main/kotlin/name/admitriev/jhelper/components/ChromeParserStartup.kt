package name.admitriev.jhelper.components

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/**
 * Starts the [ChromeParser] listener when a project opens.
 *
 * This replaces `ProjectComponent.projectOpened`, which the platform removed. `ProjectActivity` is a
 * Kotlin `suspend` interface and cannot practically be implemented from Java, so this one class is
 * Kotlin while the rest of the plugin stays Java.
 */
internal class ChromeParserStartup : ProjectActivity {
	override suspend fun execute(project: Project) {
		project.getService(ChromeParser::class.java).start()
	}
}
