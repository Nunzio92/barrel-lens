package com.barrellens

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.lang.javascript.psi.JSFunction
import com.intellij.lang.javascript.psi.JSVariable
import com.intellij.lang.javascript.psi.ecma6.TypeScriptInterface
import com.intellij.lang.javascript.psi.ecma6.TypeScriptTypeAlias
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.util.IconLoader
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.util.PsiTreeUtil
import javax.swing.Icon

/**
 * Decorates top-level exported declarations with a gutter icon when they are reachable from a
 * barrel (index.ts/index.tsx) file, i.e. part of the module's public API by convention.
 *
 * Covers plain function declarations (`export function foo() {}`) and const/let bindings
 * (`export const Foo = () => {}`) - the latter is how most React components and hooks are
 * actually declared, so it's not an edge case.
 */
class BarrelExportAnnotator : Annotator {

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        val declaration = topLevelDeclarationOrNull(element) ?: return
        val nameIdentifier = declaration.nameIdentifier ?: return

        val status = BarrelResolver.resolve(declaration)
        if (status !is BarrelExportStatus.Public) return

        holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
            .range(nameIdentifier)
            .gutterIconRenderer(PublicApiGutterIconRenderer(status))
            .create()
    }

    private fun topLevelDeclarationOrNull(element: PsiElement): PsiNameIdentifierOwner? {
        val candidate = when (element) {
            is JSFunction -> element
            is JSVariable -> element
            is TypeScriptTypeAlias -> element
            is TypeScriptInterface -> element
            else -> return null
        }
        return candidate.takeIf { isTopLevel(it) }
    }

    private fun isTopLevel(element: PsiElement): Boolean =
        PsiTreeUtil.getParentOfType(element, JSFunction::class.java, true) == null
}

internal class PublicApiGutterIconRenderer(private val status: BarrelExportStatus.Public) : GutterIconRenderer() {

    override fun getIcon(): Icon = ICON

    override fun getTooltipText(): String =
        "Public API — exported via ${status.barrelFilePath} as \"${status.exportedName}\". Click to jump to the barrel."

    override fun isNavigateAction(): Boolean = true

    override fun getClickAction(): AnAction = object : AnAction() {
        override fun actionPerformed(e: AnActionEvent) {
            val target = status.navigationTarget
            val virtualFile = target.containingFile?.virtualFile ?: return
            OpenFileDescriptor(target.project, virtualFile, target.textOffset).navigate(true)
        }
    }

    // Deliberately compares only the stable, string-based fields of `status` - not the whole
    // data class, since `status.navigationTarget` is a PsiElement that gets recreated on every
    // reparse. Comparing it would make two logically-identical results never equal(), preventing
    // the platform from reusing the existing gutter marker and forcing an unnecessary redraw on
    // every highlighting pass.
    override fun equals(other: Any?): Boolean =
        other is PublicApiGutterIconRenderer &&
            other.status.barrelFilePath == status.barrelFilePath &&
            other.status.exportedName == status.exportedName

    override fun hashCode(): Int = status.barrelFilePath.hashCode() * 31 + status.exportedName.hashCode()

    private companion object {
        val ICON: Icon = IconLoader.getIcon("/icons/barrelPublic.svg", PublicApiGutterIconRenderer::class.java)
    }
}
