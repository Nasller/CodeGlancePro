package com.nasller.codeglance.agent;

import com.intellij.openapi.editor.impl.EditorImpl;
import net.bytebuddy.implementation.bind.annotation.This;

import javax.swing.*;
import java.awt.*;

public class Interceptor {
    public static int intercept(@This EditorImpl editor) {
        JComponent component = editor.getComponent();
        LayoutManager layoutManager = component.getLayout();
        if(layoutManager instanceof BorderLayout layout){
            Component layoutComponent = layout.getLayoutComponent(BorderLayout.LINE_END);
            if(layoutComponent != null){
                return component.getWidth() - layoutComponent.getWidth();
            }
        }
        return component.getWidth();
    }
}