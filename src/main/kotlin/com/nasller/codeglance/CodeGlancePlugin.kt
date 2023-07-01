package com.nasller.codeglance

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectCloseListener
import com.intellij.openapi.util.Disposer

class CodeGlancePlugin : ProjectCloseListener {
    override fun projectClosing(project: Project) {
        projectMap.remove(project)?.apply { Disposer.dispose(this) }
    }

    companion object{
        val projectMap = hashMapOf<Project,EditorPanelInjector>()
    }
}