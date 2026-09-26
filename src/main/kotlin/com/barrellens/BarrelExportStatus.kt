package com.barrellens

import com.intellij.psi.PsiElement

sealed class BarrelExportStatus {
    object Internal : BarrelExportStatus()

    /**
     * [navigationTarget] is the specific element in the barrel that makes the declaration public -
     * the `export * from '...'` declaration itself for star exports, or the individual
     * `ES6ExportSpecifier` for named/aliased/type-only re-exports - so clicking the gutter icon can
     * jump straight to it rather than just opening the barrel file at line 1.
     */
    data class Public(val barrelFilePath: String, val exportedName: String, val navigationTarget: PsiElement) :
        BarrelExportStatus()
}
