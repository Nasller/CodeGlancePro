package com.nasller.codeglance.config

import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.tabs.ColorButtonBase
import com.nasller.codeglance.config.CodeGlanceConfigService.Companion.ConfigInstance
import com.nasller.codeglance.config.enums.MouseJumpEnum
import com.nasller.codeglance.panel.GlancePanel
import com.nasller.codeglance.ui.ColorButton
import com.nasller.codeglance.util.message
import java.awt.event.InputEvent
import java.awt.event.MouseWheelEvent
import javax.swing.DefaultComboBoxModel
import javax.swing.JComboBox
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel
import kotlin.math.max
import kotlin.math.min

class CodeGlanceConfigurable : BoundSearchableConfigurable("CodeGlance Pro","com.nasller.CodeGlancePro"){
	override fun createPanel(): DialogPanel {
		val config = ConfigInstance.state
		return panel {
			group(message("settings.general")) {
				val scrollListener: (e: MouseWheelEvent) -> Unit = {
					val comboBox = it.source as JComboBox<*>
					comboBox.setSelectedIndex(max(0, min(comboBox.selectedIndex + it.wheelRotation, comboBox.itemCount - 1)))
				}
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
						.applyToComponent { addMouseWheelListener(scrollListener) }
				}, {
					val items = listOf(message("settings.alignment.right"), message("settings.alignment.left"))
					comboBox(items).label(message("settings.alignment"))
						.bindItem({ if (config.isRightAligned) items[0] else items[1] },
							{ config.isRightAligned = it == items[0] })
						.accessibleName(message("settings.alignment"))
						.applyToComponent { addMouseWheelListener(scrollListener) }
				}).bottomGap(BottomGap.SMALL)
				twoColumnsRow({
					comboBox(MouseJumpEnum.values().map { it.getMessage() }).label(message("settings.jump"))
						.bindItem({ config.jumpOnMouseDown.getMessage() }, { config.jumpOnMouseDown = MouseJumpEnum.findMouseJumpEnum(it) })
						.accessibleName(message("settings.jump"))
						.applyToComponent { addMouseWheelListener(scrollListener) }
				}, {
					val items = arrayOf("Clean", "Accurate")
					comboBox(DefaultComboBoxModel(items)).label(message("settings.render"))
						.bindItem({ if (config.clean) items[0] else items[1] },
							{ config.clean = it == items[0] })
						.accessibleName(message("settings.render"))
						.applyToComponent { addMouseWheelListener(scrollListener) }
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
					spinner(GlancePanel.minWidth..GlancePanel.maxWidth, 5).label(message("settings.width"))
						.bindIntValue(config::width)
						.accessibleName(message("settings.width"))
						.applyToComponent {
							toolTipText = "50 - 250 pixels"
							addMouseWheelListener(numberScrollListener)
						}
					checkBox(message("settings.width.lock"))
						.bindSelected(config::locked) { config.locked = it }
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
					spinner(2000..Int.MAX_VALUE, 100).label(message("settings.more.than.line.delay"))
						.bindIntValue(config::moreThanLineDelay)
						.accessibleName(message("settings.more.than.line.delay"))
						.applyToComponent {
							toolTipText = "2000 - Int.Max lines"
							addMouseWheelListener(numberScrollListener)
						}
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
						.applyToComponent { addMouseWheelListener(scrollListener) }
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
				row {
					textField().label(message("settings.disabled.language"))
						.bindText(config::disableLanguageSuffix)
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
			}
		}
	}

	override fun apply() {
		super.apply()
		val config = ConfigInstance.state
		if((!config.isRightAligned || config.disabled) && config.hoveringToShowScrollBar) config.hoveringToShowScrollBar = false
		invokeLater{ SettingsChangePublisher.onGlobalChanged() }
	}
}