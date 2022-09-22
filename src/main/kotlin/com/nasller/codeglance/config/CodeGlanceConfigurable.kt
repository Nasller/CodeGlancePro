package com.nasller.codeglance.config

import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.tabs.ColorButtonBase
import com.nasller.codeglance.config.CodeGlanceConfigService.Companion.ConfigInstance
import com.nasller.codeglance.config.enums.MouseJumpEnum
import com.nasller.codeglance.panel.GlancePanel
import com.nasller.codeglance.ui.ColorButton
import com.nasller.codeglance.util.message
import java.awt.Color
import java.awt.event.InputEvent
import java.awt.event.MouseWheelEvent
import javax.swing.DefaultComboBoxModel
import javax.swing.JComboBox
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel
import kotlin.math.max
import kotlin.math.min

class CodeGlanceConfigurable : BoundSearchableConfigurable("CodeGlance Pro","com.nasller.CodeGlancePro"){
	private val config = ConfigInstance.state

	override fun createPanel(): DialogPanel {
		return panel {
			group(message("settings.general")) {
				twoColumnsRow(
					{ checkBox(message("settings.disabled"))
						.bindSelected(config::disabled) { config.disabled = it } },
					{ checkBox(message("settings.hide.original.scrollbar"))
						.bindSelected(config::hideOriginalScrollBar) { config.hideOriginalScrollBar = it } }
				).bottomGap(BottomGap.SMALL)
				val scrollListener: (e: MouseWheelEvent) -> Unit = {
					val comboBox = it.source as JComboBox<*>
					comboBox.setSelectedIndex(max(0, min(comboBox.selectedIndex + it.wheelRotation, comboBox.itemCount - 1)))
				}
				twoColumnsRow(
					{
						comboBox(DefaultComboBoxModel(arrayOf(1, 2, 3, 4)))
							.label(message("settings.pixels"))
							.bindItem(config::pixelsPerLine.toNullableProperty())
							.accessibleName(message("settings.pixels"))
							.applyToComponent { addMouseWheelListener(scrollListener) }
					},
					{
						val items = arrayOf(message("settings.alignment.right"), message("settings.alignment.left"))
						comboBox(DefaultComboBoxModel(items))
							.label(message("settings.alignment"))
							.bindItem({ if (config.isRightAligned) items[0] else items[1] },
								{ config.isRightAligned = it == items[0] })
							.accessibleName(message("settings.alignment"))
							.applyToComponent { addMouseWheelListener(scrollListener) }
					}
				).bottomGap(BottomGap.SMALL)
				twoColumnsRow(
					{
						comboBox(DefaultComboBoxModel(MouseJumpEnum.values().map { it.getMessage() }.toTypedArray()))
							.label(message("settings.jump"))
							.bindItem({ config.jumpOnMouseDown.getMessage() },{ config.jumpOnMouseDown = MouseJumpEnum.findMouseJumpEnum(it)})
							.accessibleName(message("settings.jump"))
							.applyToComponent { addMouseWheelListener(scrollListener) }
					},
					{
						val items = arrayOf("Clean", "Accurate")
						comboBox(DefaultComboBoxModel(items))
							.label(message("settings.render"))
							.bindItem({ if (config.clean) items[0] else items[1] },
								{ config.clean = it == items[0] })
							.accessibleName(message("settings.render"))
							.applyToComponent { addMouseWheelListener(scrollListener) }
					}
				).bottomGap(BottomGap.SMALL)
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
				twoColumnsRow(
					{
						spinner(GlancePanel.minWidth..GlancePanel.maxWidth, 5)
							.label(message("settings.width"))
							.bindIntValue(config::width)
							.accessibleName(message("settings.width"))
							.applyToComponent { addMouseWheelListener(numberScrollListener) }
						checkBox(message("settings.width.lock"))
							.bindSelected(config::locked) { config.locked = it }
					},
					{
						spinner(1..Int.MAX_VALUE, 10)
							.label(message("settings.max.line"))
							.bindIntValue(config::maxLinesCount)
							.accessibleName(message("settings.max.line"))
							.applyToComponent { addMouseWheelListener(numberScrollListener) }
					}
				).bottomGap(BottomGap.SMALL)
				twoColumnsRow(
					{
						cell(ColorButton(config.viewportColor!!, Color.WHITE))
							.label(message("settings.viewport.color"))
							.accessibleName(message("settings.viewport.color"))
							.bind({ it.text }, { p: ColorButtonBase, v: String ->
								p.setColor(Color.decode("#$v"))
								p.text = v
							}, MutableProperty({ config.viewportColor!! }, { config.viewportColor = it }))
					},
					{
						spinner(2000..Int.MAX_VALUE, 100)
							.label(message("settings.more.than.line.delay"))
							.bindIntValue(config::moreThanLineDelay)
							.accessibleName(message("settings.more.than.line.delay"))
							.applyToComponent { addMouseWheelListener(numberScrollListener) }
					}
				).bottomGap(BottomGap.SMALL)
				twoColumnsRow(
					{
						cell(ColorButton(config.viewportBorderColor!!, Color.WHITE))
							.label(message("settings.viewport.border.color"))
							.accessibleName(message("settings.viewport.border.color"))
							.bind({ it.text }, { p: ColorButtonBase, v: String ->
								p.setColor(Color.decode("#$v"))
								p.text = v
							}, MutableProperty({ config.viewportBorderColor!! }, { config.viewportBorderColor = it }))
					},
					{
						comboBox(DefaultComboBoxModel(arrayOf(0, 1, 2, 3, 4)))
							.label(message("settings.viewport.border.thickness"))
							.bindItem(config::viewportBorderThickness.toNullableProperty())
							.accessibleName(message("settings.viewport.border.thickness"))
							.applyToComponent { addMouseWheelListener(scrollListener) }
					}
				)
			}
		}
	}

	override fun apply() {
		super.apply()
		if((!config.isRightAligned || config.disabled) && config.hoveringToShowScrollBar) config.hoveringToShowScrollBar = false
		invokeLater{ SettingsChangePublisher.onGlobalChanged() }
	}
}