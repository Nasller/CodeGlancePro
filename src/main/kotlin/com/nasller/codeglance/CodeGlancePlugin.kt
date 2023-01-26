package com.nasller.codeglance

import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectCloseListener
import com.intellij.openapi.startup.ProjectPostStartupActivity
import com.intellij.openapi.util.Disposer
import com.jetbrains.rd.util.concurrentMapOf
import com.nasller.codeglance.config.SettingsChangeListener

class CodeGlancePlugin : ProjectCloseListener, ProjectPostStartupActivity {
    private val projectMap = concurrentMapOf<Project,EditorPanelInjector>()

    override suspend fun execute(project: Project) {
        val injector = EditorPanelInjector(project)
        project.messageBus.connect().subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, injector)
        ApplicationManager.getApplication().messageBus.connect(injector).apply {
            subscribe(LafManagerListener.TOPIC, injector)
            subscribe(SettingsChangeListener.TOPIC, injector)
        }
        projectMap[project] = injector
    }

    override fun projectClosing(project: Project) {
        projectMap.remove(project)?.apply { Disposer.dispose(this) }
    }
}