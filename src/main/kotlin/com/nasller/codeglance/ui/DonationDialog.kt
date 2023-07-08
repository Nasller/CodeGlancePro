package com.nasller.codeglance.ui

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI.Borders
import com.nasller.codeglance.util.CodeGlanceIcons
import com.nasller.codeglance.util.localMessage
import net.miginfocom.layout.CC
import net.miginfocom.swing.MigLayout
import javax.swing.*

class DonationDialog : DialogWrapper(null) {
	init {
		title = localMessage("donate.title")
		isResizable = false
		setOKButtonText(localMessage("donate.thanks"))
		init()
	}

	override fun createCenterPanel(): JComponent = JPanel(VerticalLayout(JBUIScale.scale(10),SwingConstants.CENTER)).apply {
		border = Borders.empty(16)
		background = UIManager.getColor("TextArea.background")
		add(Messages.configureMessagePaneUi(JTextPane(), localMessage("donate.contribution"))
			.apply { background = null })
		add(createDonatePanel())
	}

	private fun createDonatePanel(): JPanel {
		return NonOpaquePanel(MigLayout()).apply {
			add(LinkLabel(null, CodeGlanceIcons.loadRoundImageIcon("/image/paypal.png")) { _: LinkLabel<Any?>, _: Any? ->
				BrowserUtil.browse("https://www.paypal.com/paypalme/Nasller")
			}, CC().alignX("center").spanX(2).wrap())
			val weChatPayLabel = JBLabel(localMessage("donate.wechat")).apply {
				horizontalTextPosition = SwingConstants.CENTER
				verticalTextPosition = SwingConstants.BOTTOM
				icon = CodeGlanceIcons.scaleIcon(CodeGlanceIcons.loadImageIcon("/image/wechat_pay.png"), 0.5f)
			}
			add(weChatPayLabel)
			val aliPayLabel = JBLabel(localMessage("donate.alipay")).apply {
				horizontalTextPosition = SwingConstants.CENTER
				verticalTextPosition = SwingConstants.BOTTOM
				icon =  CodeGlanceIcons.scaleIcon(CodeGlanceIcons.loadImageIcon("/image/ali_pay.png"), 0.5f)
			}
			add(aliPayLabel, CC().wrap())
		}
	}

	override fun getStyle(): DialogStyle = DialogStyle.COMPACT

	override fun createActions(): Array<Action> = arrayOf(okAction)
}