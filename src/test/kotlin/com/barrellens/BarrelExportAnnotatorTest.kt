package com.barrellens

import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class BarrelExportAnnotatorTest : BasePlatformTestCase() {

    fun `test public declaration gets a gutter icon with a barrel path tooltip`() {
        myFixture.addFileToProject("index.ts", "export * from './foo'")
        myFixture.configureByText("foo.ts", "export function <caret>foo() {}")

        myFixture.doHighlighting()

        val gutters = myFixture.findGuttersAtCaret()
        assertTrue(gutters.any { it.tooltipText?.contains("index.ts") == true })
    }

    fun `test internal declaration gets no gutter icon`() {
        myFixture.configureByText("foo.ts", "export function <caret>foo() {}")

        myFixture.doHighlighting()

        val gutters = myFixture.findGuttersAtCaret()
        assertTrue(gutters.none { it.tooltipText?.contains("Public API") == true })
    }

    fun `test exported arrow function const re-exported by name gets a gutter icon`() {
        myFixture.addFileToProject("index.ts", "export { Foo } from './foo'")
        myFixture.configureByText("foo.ts", "export const <caret>Foo = () => {}")

        myFixture.doHighlighting()

        val gutters = myFixture.findGuttersAtCaret()
        assertTrue(gutters.any { it.tooltipText?.contains("index.ts") == true })
    }

    fun `test type re-exported via type-only named export gets a gutter icon`() {
        myFixture.addFileToProject("index.ts", "export type { Foo } from './types'")
        myFixture.configureByText("types.ts", "export type <caret>Foo = { id: string }")

        myFixture.doHighlighting()

        val gutters = myFixture.findGuttersAtCaret()
        assertTrue(gutters.any { it.tooltipText?.contains("index.ts") == true })
    }

    fun `test interface re-exported via type-only named export gets a gutter icon`() {
        myFixture.addFileToProject("index.ts", "export type { Foo } from './types'")
        myFixture.configureByText("types.ts", "export interface <caret>Foo { id: string }")

        myFixture.doHighlighting()

        val gutters = myFixture.findGuttersAtCaret()
        assertTrue(gutters.any { it.tooltipText?.contains("index.ts") == true })
    }

    fun `test clicking the gutter icon navigates to the barrel re-export`() {
        val barrelFile = myFixture.addFileToProject("index.ts", "export { foo } from './foo'")
        myFixture.configureByText("foo.ts", "export function <caret>foo() {}")

        myFixture.doHighlighting()

        val renderer = myFixture.findGuttersAtCaret().filterIsInstance<GutterIconRenderer>().first()
        assertTrue(renderer.isNavigateAction)
        val clickAction = renderer.clickAction!!

        clickAction.actionPerformed(TestActionEvent.createTestEvent())

        assertEquals(barrelFile.virtualFile, FileEditorManager.getInstance(project).selectedFiles.first())
    }
}
