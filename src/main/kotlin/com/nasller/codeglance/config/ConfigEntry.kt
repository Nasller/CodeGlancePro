package com.nasller.codeglance.config

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.nasller.codeglance.config.ConfigService.Companion.ConfigInstance
import org.jetbrains.annotations.Nls
import javax.swing.JComponent

class ConfigEntry : Configurable {
    private var form: ConfigForm? = null
    private val config = ConfigInstance.state

    @Nls
    override fun getDisplayName(): String {
        return "CodeGlance Pro"
    }

    override fun getHelpTopic(): String {
        return "Configuration for the CodeGlance minimap"
    }

    override fun createComponent(): JComponent? {
        form = ConfigForm()
        reset()
        return form!!.root
    }

    override fun isModified(): Boolean = form != null && (
        config.disabled != form!!.isDisabled
            || config.pixelsPerLine != form!!.pixelsPerLine
            || config.jumpOnMouseDown != form!!.jumpOnMouseDown()
            || config.width != form!!.width
            || config.locked != form!!.isLocked
            || config.viewportColor != form!!.viewportColor
            || config.minLineCount != form!!.minLinesCount
            || config.minWindowWidth != form!!.minWindowWidth
            || config.clean != form!!.cleanStyle
            || config.isRightAligned != form!!.isRightAligned
            || config.oldGlance != form!!.isOldGlance
    )

    @Throws(ConfigurationException::class)
    override fun apply() {
        if (form == null) return

        config.pixelsPerLine = form!!.pixelsPerLine
        config.disabled = form!!.isDisabled
        config.locked = form!!.isLocked
        config.oldGlance = form!!.isOldGlance
        config.jumpOnMouseDown = form!!.jumpOnMouseDown()
        config.width = form!!.width.coerceAtLeast(50)
        if (form!!.viewportColor.length == 6 && form!!.viewportColor.matches("^[a-fA-F0-9]*$".toRegex())) {
            config.viewportColor = form!!.viewportColor
        } else {
            config.viewportColor = "A0A0A0"
        }
        config.minLineCount = form!!.minLinesCount
        config.minWindowWidth = form!!.minWindowWidth
        config.clean = form!!.cleanStyle
        config.isRightAligned = form!!.isRightAligned
        ApplicationManager.getApplication().invokeLater{
            SettingsChangePublisher.onRefreshChanged(config.disabled,null)
        }
    }

    override fun reset() {
        if (form == null) return

        form!!.pixelsPerLine = config.pixelsPerLine
        form!!.isDisabled = config.disabled
        form!!.isOldGlance = config.oldGlance
        form!!.isLocked= config.locked
        form!!.setJumpOnMouseDown(config.jumpOnMouseDown)
        form!!.viewportColor = config.viewportColor
        form!!.width = config.width
        form!!.minLinesCount = config.minLineCount
        form!!.minWindowWidth = config.minWindowWidth
        form!!.cleanStyle = config.clean
        form!!.isRightAligned = config.isRightAligned
        form!!.isOldGlance = config.oldGlance
    }

    override fun disposeUIResources() {
        form = null
    }
}