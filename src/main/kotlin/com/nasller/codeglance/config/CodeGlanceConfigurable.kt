package com.nasller.codeglance.config

import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.tabs.ColorButtonBase
import com.intellij.util.ui.JBUI
import com.nasller.codeglance.config.CodeGlanceConfig.Companion.getWidth
import com.nasller.codeglance.config.CodeGlanceConfig.Companion.setWidth
import com.nasller.codeglance.config.enums.ClickTypeEnum
import com.nasller.codeglance.config.enums.MouseJumpEnum
import com.nasller.codeglance.panel.GlancePanel
import com.nasller.codeglance.ui.ColorButton
import com.nasller.codeglance.ui.DonationDialog
import com.nasller.codeglance.util.Util
import com.nasller.codeglance.util.localMessage
import com.nasller.codeglance.util.message
import java.awt.Component
import java.awt.Dimension
import java.awt.event.InputEvent
import java.awt.event.MouseWheelEvent
import javax.swing.*
import kotlin.math.max
import kotlin.math.min

class CodeGlanceConfigurable : BoundSearchableConfigurable(Util.PLUGIN_NAME,"com.nasller.CodeGlancePro"){
	private val editorKinds = mutableSetOf<EditorKind>()
	private val useEmptyMinimap = mutableSetOf<EditorKind>()
	private lateinit var editorKindComboBox: ComboBox<EditorKind>
	private lateinit var emptyMinimapComboBox: ComboBox<EditorKind>

	override fun createPanel(): DialogPanel {
		val config = CodeGlanceConfigService.getConfig()
		return panel {
			group(message("settings.general")) {
				val doubleNumberScrollListener: (e: MouseWheelEvent) -> Unit = {
					val spinner = it.source as JSpinner
					val model = spinner.model as SpinnerNumberModel
					var step = model.stepSize.toDouble()
					when (it.modifiersEx and (InputEvent.CTRL_DOWN_MASK or InputEvent.SHIFT_DOWN_MASK)) {
						InputEvent.CTRL_DOWN_MASK -> step *= 2
						InputEvent.SHIFT_DOWN_MASK -> step /= 2
						InputEvent.CTRL_DOWN_MASK or InputEvent.SHIFT_DOWN_MASK -> step = 0.5
					}
					var newValue: Double = spinner.value as Double + step * -it.wheelRotation
					newValue = min(max(newValue, (model.minimum as Double)), (model.maximum as Double))
					spinner.value = newValue
				}
				twoColumnsRow({
					comboBox(listOf(1, 2, 3, 4)).label(message("settings.pixels"))
						.bindItem(config::pixelsPerLine.toNullableProperty())
						.accessibleName(message("settings.pixels"))
				}, {
					val items = listOf(message("settings.alignment.right"), message("settings.alignment.left"))
					comboBox(items).label(message("settings.alignment"))
						.bindItem({ if (config.isRightAligned) items[0] else items[1] },
							{ config.isRightAligned = it == items[0] })
						.accessibleName(message("settings.alignment"))
				}).bottomGap(BottomGap.SMALL)
				twoColumnsRow({
					comboBox(MouseJumpEnum.entries.map { it.getMessage() }).label(message("settings.jump"))
						.bindItem({ config.jumpOnMouseDown.getMessage() }, { config.jumpOnMouseDown = MouseJumpEnum.findEnum(it) })
						.accessibleName(message("settings.jump"))
				}, {
					val items = arrayOf("Clean", "Accurate")
					comboBox(DefaultComboBoxModel(items)).label(message("settings.render"))
						.bindItem({ if (config.clean) items[0] else items[1] },
							{ config.clean = it == items[0] })
						.accessibleName(message("settings.render"))
				}).bottomGap(BottomGap.SMALL)
				val numberScrollListener: (e: MouseWheelEvent) -> Unit = {
					val spinner = it.source as JSpinner
					val model = spinner.model as SpinnerNumberModel
					var step = model.stepSize.toInt()
					when (it.modifiersEx and (InputEvent.CTRL_DOWN_MASK or InputEvent.SHIFT_DOWN_MASK)) {
						InputEvent.CTRL_DOWN_MASK -> step *= 2
						InputEvent.SHIFT_DOWN_MASK -> step /= 2
						InputEvent.CTRL_DOWN_MASK or InputEvent.SHIFT_DOWN_MASK -> step = 1
					}
					var newValue: Int = spinner.value as Int + step * -it.wheelRotation
					newValue = min(max(newValue, (model.minimum as Int)), (model.maximum as Int))
					spinner.value = newValue
				}
				twoColumnsRow({
					comboBox(ClickTypeEnum.entries.map { it.getMessage() }).label(message("settings.click"))
						.bindItem({ config.clickType.getMessage() }, { config.clickType = ClickTypeEnum.findEnum(it) })
						.accessibleName(message("settings.click"))
				}, {
					spinner(1..Int.MAX_VALUE, 10).label(message("settings.max.line"))
						.bindIntValue(config::maxLinesCount)
						.accessibleName(message("settings.max.line"))
						.applyToComponent {
							toolTipText = "1 - Int.Max lines"
							addMouseWheelListener(numberScrollListener)
						}
				}).bottomGap(BottomGap.SMALL)
				twoColumnsRow({
					cell(ColorButton(config.viewportColor, JBColor.WHITE)).label(message("settings.viewport.color"))
						.accessibleName(message("settings.viewport.color"))
						.bind({ it.text }, { p: ColorButtonBase, v: String ->
							p.setColor(ColorUtil.fromHex(v))
							p.text = v
						}, config::viewportColor.toMutableProperty())
				}, {
					editorKindComboBox = comboBox(EditorKind.entries, EditorKindListCellRenderer(editorKinds))
						.label(message("settings.editor.kind")).applyToComponent {
						isSwingPopup = false
						addActionListener {
							val kind = editorKindComboBox.item ?: return@addActionListener
							if (!editorKinds.remove(kind)) editorKinds.add(kind)
							editorKindComboBox.repaint()
						}
					}.component
				}).bottomGap(BottomGap.SMALL)
				twoColumnsRow({
					cell(ColorButton(config.viewportBorderColor, JBColor.WHITE)).label(message("settings.viewport.border.color"))
						.accessibleName(message("settings.viewport.border.color"))
						.bind({ it.text }, { p: ColorButtonBase, v: String ->
							p.setColor(ColorUtil.fromHex(v))
							p.text = v
						}, config::viewportBorderColor.toMutableProperty())
				}, {
					comboBox(listOf(0, 1, 2, 3, 4)).label(message("settings.viewport.border.thickness"))
						.bindItem(config::viewportBorderThickness.toNullableProperty())
						.accessibleName(message("settings.viewport.border.thickness"))
				}).bottomGap(BottomGap.SMALL)
				twoColumnsRow({
					spinner(0..2000, 50).label(message("popup.hover.minimap.delay"))
						.bindIntValue(config::delayHoveringToShowScrollBar)
						.accessibleName(message("popup.hover.minimap.delay"))
						.applyToComponent {
							toolTipText = "0 - 2000 ms"
							addMouseWheelListener(numberScrollListener)
						}
					@Suppress("DialogTitleCapitalization")
					label("ms").gap(RightGap.SMALL)
				}, {
					spinner(2.0..10.0, 0.1).label(message("settings.markers.scale"))
						.bindValue(getter = { config.markersScaleFactor.toDouble() }, setter = { value: Double -> config.markersScaleFactor = value.toFloat() })
						.accessibleName(message("settings.markers.scale"))
						.applyToComponent {
							toolTipText = "Scale factor for font of markers in minimap[2 - 10]"
							addMouseWheelListener(doubleNumberScrollListener)
						}
				}).bottomGap(BottomGap.SMALL)
				twoColumnsRow({
					textField().label(message("settings.markers.regex")).bindText(config::markRegex).accessibleName(message("settings.markers.regex"))
				},{
					emptyMinimapComboBox = comboBox(EditorKind.entries, EditorKindListCellRenderer(useEmptyMinimap))
						.label(message("settings.use.empty.minimap")).applyToComponent {
						isSwingPopup = false
						addActionListener {
							val kind = emptyMinimapComboBox.item ?: return@addActionListener
							if (!useEmptyMinimap.remove(kind)) useEmptyMinimap.add(kind)
							emptyMinimapComboBox.repaint()
						}
					}.component
				}).bottomGap(BottomGap.SMALL)
				val widthList = EditorKind.entries.chunked(3)
				widthList.forEachIndexed { index, it ->
					row {
						for (kind in it) {
							spinner(GlancePanel.MIN_WIDTH..GlancePanel.MAX_WIDTH, 5).label(kind.getMessageWidth())
								.bindIntValue({ kind.getWidth() }, { kind.setWidth(it) })
								.accessibleName(kind.getMessageWidth())
								.applyToComponent {
									toolTipText = "30 - 250 pixels"
									addMouseWheelListener(numberScrollListener)
								}
						}
						if(widthList.lastIndex == index) {
							checkBox(message("settings.width.lock")).bindSelected(config::locked) { config.locked = it }
						}
					}
				}
				row {
					textField().label(message("settings.disabled.language")).bindText(config::disableLanguageSuffix)
						.accessibleName(message("settings.disabled.language"))
				}
			}
			group(message("settings.option")) {
				threeColumnsRow({
					checkBox(message("settings.disabled"))
						.bindSelected(config::disabled)
				}, {
					checkBox(message("settings.markers.enable"))
						.bindSelected(config::enableMarker)
				}, {
					checkBox(message("settings.hide.original.scrollbar"))
						.bindSelected(config::hideOriginalScrollBar)
				})
				threeColumnsRow({
					checkBox(message("settings.highlight.vcs"))
						.bindSelected(config::showVcsHighlight)
				}, {
					checkBox(message("settings.highlight.filter.markup"))
						.bindSelected(config::showFilterMarkupHighlight)
						.gap(RightGap.SMALL)
					contextHelp(message("settings.highlight.filter.markup.desc"))
				}, {
					checkBox(message("settings.highlight.markup"))
						.bindSelected(config::showMarkupHighlight)
						.gap(RightGap.SMALL)
					contextHelp(message("settings.highlight.markup.desc"))
				})
				threeColumnsRow({
					checkBox(message("settings.highlight.syntax"))
						.bindSelected(config::syntaxHighlight)
						.gap(RightGap.SMALL)
				},{
					checkBox(message("settings.two.sides.diff"))
						.bindSelected(config::diffTwoSide)
						.gap(RightGap.SMALL)
				},{
					checkBox(message("settings.three.sides.diff"))
						.bindSelected(config::diffThreeSide)
						.gap(RightGap.SMALL)
				})
				threeColumnsRow({
					checkBox(message("settings.three.sides.middle.diff"))
						.bindSelected(config::diffThreeSideMiddle)
						.gap(RightGap.SMALL)
				},{
					checkBox(message("settings.experiment.use.fast.minimap.for.main"))
						.bindSelected(config::useFastMinimapForMain)
						.gap(RightGap.SMALL)
				})
			}
			row {
				link(localMessage("donate.title")){
					DonationDialog().show()
				}.applyToComponent { border = JBUI.Borders.emptyTop(20) }
			}
		}
	}

	override fun apply() {
		super.apply()
		CodeGlanceConfigService.getConfig().apply {
			editorKinds = this@CodeGlanceConfigurable.editorKinds
			useEmptyMinimap = this@CodeGlanceConfigurable.useEmptyMinimap
			if((!isRightAligned || disabled) && hoveringToShowScrollBar) hoveringToShowScrollBar = false
			Util.MARK_REGEX = if(markRegex.isNotBlank()) Regex(markRegex) else null
		}
		invokeLater{ SettingsChangePublisher.onGlobalChanged() }
	}

	override fun isModified(): Boolean {
		return super.isModified() || editorKinds != CodeGlanceConfigService.getConfig().editorKinds ||
				useEmptyMinimap != CodeGlanceConfigService.getConfig().useEmptyMinimap
	}

	override fun reset() {
		super.reset()
		val config = CodeGlanceConfigService.getConfig()
		editorKinds.clear()
		editorKinds.addAll(config.editorKinds)
		useEmptyMinimap.clear()
		useEmptyMinimap.addAll(config.useEmptyMinimap)
		editorKindComboBox.repaint()
		emptyMinimapComboBox.repaint()
	}

	private fun EditorKind.getMessageWidth() = when(this){
		EditorKind.UNTYPED -> message("settings.untyped.width")
		EditorKind.CONSOLE -> message("settings.console.width")
		EditorKind.PREVIEW -> message("settings.preview.width")
		EditorKind.DIFF -> message("settings.diff.width")
		else -> message("settings.main.width")
	}

	private inner class EditorKindListCellRenderer<T : Enum<T>>(private val data: MutableSet<T>) : DefaultListCellRenderer() {
		private val container = JPanel(null)
		private val checkBox = JBCheckBox()

		init {
			isOpaque = false
			container.isOpaque = false
			checkBox.isOpaque = false

			container.layout = BoxLayout(container, BoxLayout.X_AXIS)
			container.add(checkBox)
			container.add(this)
			preferredSize = Dimension(100, 0)
		}

		override fun getListCellRendererComponent(list: JList<*>?,
		                                          value: Any?,
		                                          index: Int,
		                                          isSelected: Boolean,
		                                          cellHasFocus: Boolean): Component {
			super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
			if (index == -1) {
				checkBox.isVisible = false
				text = data.minOrNull()?.name?.lowercase()?.replaceFirstChar { it.titlecase() } ?: ""
				return container
			}
			text = value.toString().lowercase().replaceFirstChar { it.titlecase() }
			checkBox.isVisible = true
			checkBox.isSelected = data.contains(value)
			return container
		}
	}
}