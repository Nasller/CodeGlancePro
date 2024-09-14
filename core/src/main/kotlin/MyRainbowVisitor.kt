import com.intellij.codeHighlighting.RainbowHighlighter
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightVisitor
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNameIdentifierOwner
import com.nasller.codeglance.config.CodeGlanceConfigService
import com.nasller.codeglance.util.Util

var MARK_REGEX = CodeGlanceConfigService.Config.markRegex.run {
	if(isNotBlank()) Regex(this) else null
}

/**
 * Avoid report errors.
 * This isn't the error made by this plugin. It's the error of SDK.
 */
abstract class MyRainbowVisitor : HighlightVisitor {
	private var myHolder: HighlightInfoHolder? = null

	abstract fun suitableForFile(extension: String): Boolean

	override fun suitableForFile(file: PsiFile): Boolean {
		val config = CodeGlanceConfigService.Config
		val extension = file.fileType.defaultExtension
		return config.enableMarker && (extension.isBlank() || suitableForFile(extension) &&
				config.disableLanguageSuffix.split(",").toSet().contains(extension).not())
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

	protected fun visitPsiNameIdentifier(element: PsiNameIdentifierOwner) {
		val psiIdentifier = element.nameIdentifier ?: return
		visitText(psiIdentifier.text, psiIdentifier.textRange, Util.MARK_CLASS_ATTRIBUTES)
	}

	protected fun visitText(text: String, textRange: TextRange, textAttributesKey: TextAttributesKey) {
		if (text.isNotBlank()) {
			addInfo(getInfo(textRange.startOffset, textRange.endOffset, Util.MARK_CLASS_ATTRIBUTES))
		}
	}
}