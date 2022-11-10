package com.nasller.codeglance

import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener
import com.nasller.codeglance.config.SettingsChangeListener

class CodeGlancePlugin : ProjectManagerListener {
    override fun projectOpened(project: Project) {
        project.messageBus.connect().let{
            val injector = EditorPanelInjector(project)
            it.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, injector)
            it.subscribe(SettingsChangeListener.TOPIC, injector)
            it.subscribe(LafManagerListener.TOPIC, injector)
        }
    }
}