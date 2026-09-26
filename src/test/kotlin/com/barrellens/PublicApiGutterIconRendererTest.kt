package com.barrellens

import com.intellij.lang.javascript.psi.JSFunction
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class PublicApiGutterIconRendererTest : BasePlatformTestCase() {

    fun `test renderers with the same barrel path and exported name are equal despite different PSI instances`() {
        // Two separate fixture files stand in for "the same barrel export, seen across two
        // separate reparses" - the underlying PsiElement instances are never the same object,
        // even when they represent an unchanged declaration.
        val fileA = myFixture.addFileToProject("a.ts", "export function foo() {}")
        val fileB = myFixture.addFileToProject("b.ts", "export function foo() {}")
        val targetA = PsiTreeUtil.findChildOfType(fileA, JSFunction::class.java)!!
        val targetB = PsiTreeUtil.findChildOfType(fileB, JSFunction::class.java)!!
        assertNotSame(targetA, targetB)

        val rendererA = PublicApiGutterIconRenderer(BarrelExportStatus.Public("index.ts", "foo", targetA))
        val rendererB = PublicApiGutterIconRenderer(BarrelExportStatus.Public("index.ts", "foo", targetB))

        assertEquals(rendererA, rendererB)
        assertEquals(rendererA.hashCode(), rendererB.hashCode())
    }

    fun `test renderers with a different exported name are not equal`() {
        val file = myFixture.addFileToProject("a.ts", "export function foo() {}")
        val target = PsiTreeUtil.findChildOfType(file, JSFunction::class.java)!!

        val rendererA = PublicApiGutterIconRenderer(BarrelExportStatus.Public("index.ts", "foo", target))
        val rendererB = PublicApiGutterIconRenderer(BarrelExportStatus.Public("index.ts", "renamed", target))

        assertFalse(rendererA == rendererB)
    }
}
