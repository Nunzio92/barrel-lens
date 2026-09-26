package com.barrellens

import com.intellij.lang.ecmascript6.psi.ES6ExportDeclaration
import com.intellij.lang.ecmascript6.resolve.ES6PsiUtil
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.PsiTreeUtil

/**
 * Resolves whether a declaration is reachable from a barrel (index.ts/index.tsx) file,
 * i.e. is part of a module's public API by convention.
 */
object BarrelResolver {

    private val BARREL_FILE_NAMES = setOf("index.ts", "index.tsx")

    fun resolve(declaration: PsiNamedElement): BarrelExportStatus {
        return CachedValuesManager.getCachedValue(declaration) {
            // Dependencies are scoped to only the declaration's own file plus whichever barrel
            // files were actually inspected while resolving it - editing an unrelated file
            // anywhere else in the project must not force this to recompute.
            val visitedBarrels = mutableListOf<PsiFile>()
            val status = computeStatus(declaration, visitedBarrels)
            val declarationFile = declaration.containingFile

            val dependencies: Array<Any> = if (declarationFile != null) {
                (listOf(declarationFile) + visitedBarrels).toTypedArray()
            } else {
                arrayOf(PsiModificationTracker.MODIFICATION_COUNT)
            }

            CachedValueProvider.Result.create(status, *dependencies)
        }
    }

    private fun computeStatus(declaration: PsiNamedElement, visitedBarrels: MutableList<PsiFile>): BarrelExportStatus {
        val declarationFile = declaration.containingFile ?: return BarrelExportStatus.Internal
        var directory: PsiDirectory? = declarationFile.containingDirectory
        while (directory != null) {
            val barrelFile = directory.files.firstOrNull { it.name in BARREL_FILE_NAMES && it != declarationFile }
            if (barrelFile != null) {
                visitedBarrels += barrelFile
                checkBarrel(barrelFile, declaration, declarationFile)?.let { return it }
            }
            directory = directory.parentDirectory
        }
        return BarrelExportStatus.Internal
    }

    private fun checkBarrel(barrelFile: PsiFile, declaration: PsiNamedElement, targetFile: PsiFile): BarrelExportStatus? {
        val exportDeclarations = PsiTreeUtil.findChildrenOfType(barrelFile, ES6ExportDeclaration::class.java)

        for (exportDeclaration in exportDeclarations) {
            val fromClause = exportDeclaration.fromClause ?: continue
            val resolvedFiles = ES6PsiUtil.getFromClauseResolvedReferences(fromClause).filterIsInstance<PsiFile>()
            if (targetFile !in resolvedFiles) continue

            if (exportDeclaration.isExportAll) {
                return BarrelExportStatus.Public(barrelPath(barrelFile), declaration.name.orEmpty(), exportDeclaration)
            }

            for (specifier in exportDeclaration.exportSpecifiers) {
                val resolvesToDeclaration = ES6PsiUtil.resolveSymbolForSpecifier(specifier)
                    .any { it.element == declaration }
                if (resolvesToDeclaration) {
                    val exportedName = specifier.alias?.name ?: declaration.name.orEmpty()
                    return BarrelExportStatus.Public(barrelPath(barrelFile), exportedName, specifier)
                }
            }
        }

        return null
    }

    private fun barrelPath(barrelFile: PsiFile): String = barrelFile.virtualFile?.path ?: barrelFile.name
}
