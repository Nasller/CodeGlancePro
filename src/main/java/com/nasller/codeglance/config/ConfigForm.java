package com.nasller.codeglance.config;

import com.intellij.ui.JBColor;
import com.nasller.codeglance.CodeGlanceBundleKt;
import com.nasller.codeglance.panel.AbstractGlancePanel;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.event.InputEvent;
import java.awt.event.MouseWheelListener;
import java.util.regex.Pattern;

@SuppressWarnings("unchecked")
public class ConfigForm {
    private JCheckBox disabled;
    private JCheckBox locked;
    private JComboBox alignment;
    private JComboBox jumpToPosition;
    private JComboBox pixelsPerLine;
    private JComboBox renderStyle;
    private JPanel rootPanel;
    private JSpinner maxLinesCount;
    private JSpinner width;
    private JTextField viewportColor;
    private JCheckBox hideOriginalScrollBar;

    public ConfigForm() {
        jumpToPosition.setModel(new DefaultComboBoxModel<>(new String[]{CodeGlanceBundleKt.message("settings.jump.down"), CodeGlanceBundleKt.message("settings.jump.up")}));
        alignment.setModel(new DefaultComboBoxModel<>(new String[]{CodeGlanceBundleKt.message("settings.alignment.right"), CodeGlanceBundleKt.message("settings.alignment.left")}));
        pixelsPerLine.setModel(new DefaultComboBoxModel<>(new Integer[]{1, 2, 3, 4}));
        viewportColor.setInputVerifier(new InputVerifier() {
            private final Pattern pattern = Pattern.compile("[a-fA-F\\d]{6}");
            private final Border defaultBorder = viewportColor.getBorder();
            private final Border invalidBorder = BorderFactory.createLineBorder(JBColor.RED);

            @Override
            public boolean verify(JComponent input) {
                boolean valid = pattern.matcher(viewportColor.getText()).matches();
                if (!valid){
                    viewportColor.setBorder(invalidBorder);
                } else{
                    viewportColor.setBorder(defaultBorder);
                }
                return valid;
            }
        });

        width.setModel(new SpinnerNumberModel(110, AbstractGlancePanel.minWidth, AbstractGlancePanel.maxWidth, 5));
        maxLinesCount.setModel(new SpinnerNumberModel(1, 0, Integer.MAX_VALUE, 10));

        // Spinner scroll support
        MouseWheelListener scrollListener = e -> {
            JSpinner spinner = (JSpinner) e.getSource();
            SpinnerNumberModel model = (SpinnerNumberModel) spinner.getModel();

            int step = model.getStepSize().intValue();
            switch (e.getModifiersEx() & (InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK)) {
                case InputEvent.CTRL_DOWN_MASK:
                    step *= 2;
                    break;
                case InputEvent.SHIFT_DOWN_MASK:
                    step /= 2;
                    break;
                case InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK:
                    step = 1;
                    break;
            }

            int newValue = (int) spinner.getValue() + (step * -e.getWheelRotation());
            newValue = Math.min(Math.max(newValue, (Integer) model.getMinimum()), (Integer) model.getMaximum());
            spinner.setValue(newValue);
        };
        width.addMouseWheelListener(scrollListener);
        maxLinesCount.addMouseWheelListener(scrollListener);

        // ComboBox scroll support
        scrollListener = e -> {
            JComboBox comboBox = (JComboBox) e.getSource();
            // Bounded Scroll
            comboBox.setSelectedIndex(Math.max(0, Math.min(comboBox.getSelectedIndex() + e.getWheelRotation(), comboBox.getItemCount() - 1)));
            // Cyclic Scroll
            // comboBox.setSelectedIndex(Math.abs((comboBox.getSelectedIndex() + e.getWheelRotation()) % comboBox.getItemCount()));
        };
        alignment.addMouseWheelListener(scrollListener);
        jumpToPosition.addMouseWheelListener(scrollListener);
        pixelsPerLine.addMouseWheelListener(scrollListener);
        renderStyle.addMouseWheelListener(scrollListener);
    }

    public JPanel getRoot() {
        return rootPanel;
    }

    public int getPixelsPerLine() {
        return (int) pixelsPerLine.getSelectedItem();
    }

    public void setPixelsPerLine(int pixelsPerLine) {
        this.pixelsPerLine.setSelectedIndex(pixelsPerLine - 1);
    }

    public boolean isDisabled() {
        return disabled.getModel().isSelected();
    }

    public void setDisabled(boolean isDisabled) {
        disabled.getModel().setSelected(isDisabled);
    }

    public boolean isHideOriginalScrollBar() {
        return hideOriginalScrollBar.getModel().isSelected();
    }

    public void setHideOriginalScrollBar(boolean isDisabled) {
        hideOriginalScrollBar.getModel().setSelected(isDisabled);
    }

    public boolean isLocked() {
        return locked.getModel().isSelected();
    }

    public void setLocked(boolean isLocked) {
        locked.getModel().setSelected(isLocked);
    }

    public boolean jumpOnMouseDown() {
        return jumpToPosition.getSelectedIndex() == 0;
    }

    public void setJumpOnMouseDown(boolean jump) {
        jumpToPosition.setSelectedIndex(jump ? 0 : 1);
    }

    public String getViewportColor() {
        return viewportColor.getText();
    }

    public void setViewportColor(String color) {
        viewportColor.setText(color);
    }

    public boolean getCleanStyle() {
        return renderStyle.getSelectedIndex() == 0;
    }

    public void setCleanStyle(boolean isClean) {
        renderStyle.setSelectedIndex(isClean ? 0 : 1);
    }

    public boolean isRightAligned() {
        return alignment.getSelectedIndex() == 0;
    }

    public void setRightAligned(boolean isRightAligned) {
        alignment.setSelectedIndex(isRightAligned ? 0 : 1);
    }

    public int getWidth() {
        return (int) width.getValue();
    }

    public void setWidth(int width) {
        this.width.setValue(width);
    }

    public int getMaxLinesCount() {
        return (int) maxLinesCount.getValue();
    }

    public void setMaxLinesCount(int maxLinesCount) {
        this.maxLinesCount.setValue(maxLinesCount);
    }
}