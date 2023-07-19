
import com.intellij.codeHighlighting.RainbowHighlighter
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightVisitor
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiEditorUtil
import com.nasller.codeglance.config.CodeGlanceConfigService

/**
 * Avoid report errors.
 * This isn't the error made by this plugin. It's the error of SDK.
 */
abstract class MyRainbowVisitor : HighlightVisitor {
	private var myHolder: HighlightInfoHolder? = null

	override fun suitableForFile(file: PsiFile): Boolean {
		val editor = PsiEditorUtil.findEditor(file) ?: return false
		val config = CodeGlanceConfigService.getConfig()
		val fileExtension = config.enableMarker && (file.fileType.defaultExtension.isBlank() || config.disableLanguageSuffix
			.split(",").toSet().contains(file.fileType.defaultExtension).not())
		val editorKind = config.editorKinds.contains(editor.editorKind) &&
				editor.editorKind != EditorKind.CONSOLE && editor.editorKind != EditorKind.UNTYPED
		return fileExtension && editorKind
	}

	override fun analyze(file: PsiFile, updateWholeFile: Boolean, holder: HighlightInfoHolder, action: Runnable): Boolean {
		myHolder = holder
		try {
			action.run()
		} finally {
			myHolder = null
		}
		return true
	}

	protected fun addInfo(highlightInfo: HighlightInfo?) {
		myHolder!!.add(highlightInfo)
	}

	protected fun getInfo(start: Int, end: Int, colorKey: TextAttributesKey): HighlightInfo? {
		return HighlightInfo
			.newHighlightInfo(RainbowHighlighter.RAINBOW_ELEMENT)
			.textAttributes(colorKey)
			.range(start,end).create()
	}
}