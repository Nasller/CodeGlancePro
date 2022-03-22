package com.nasller.codeglance

import com.intellij.openapi.components.NamedComponent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener

class CodeGlancePlugin : ProjectManagerListener,NamedComponent {
    private val logger = Logger.getInstance(javaClass)

    override fun projectOpened(project: Project) {
        project.messageBus.connect().subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER,
            EditorPanelInjector(project)
        )
        logger.debug("CodeGlance initialized")
    }

    override fun getComponentName(): String {
        return "CodeGlancePlugin"
    }
}