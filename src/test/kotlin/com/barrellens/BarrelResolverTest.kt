package com.barrellens

import com.intellij.lang.javascript.psi.JSFunction
import com.intellij.lang.javascript.psi.JSVariable
import com.intellij.lang.javascript.psi.ecma6.TypeScriptInterface
import com.intellij.lang.javascript.psi.ecma6.TypeScriptTypeAlias
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class BarrelResolverTest : BasePlatformTestCase() {

    fun `test declaration with no barrel anywhere is internal`() {
        myFixture.configureByText("foo.ts", "export function foo() {}")

        val declaration = PsiTreeUtil.findChildOfType(myFixture.file, JSFunction::class.java)!!

        assertEquals(BarrelExportStatus.Internal, BarrelResolver.resolve(declaration))
    }

    fun `test declaration re-exported via star export is public`() {
        myFixture.addFileToProject("index.ts", "export * from './foo'")
        myFixture.configureByText("foo.ts", "export function foo() {}")

        val declaration = PsiTreeUtil.findChildOfType(myFixture.file, JSFunction::class.java)!!

        val status = BarrelResolver.resolve(declaration)

        assertTrue(status is BarrelExportStatus.Public)
        assertEquals("foo", (status as BarrelExportStatus.Public).exportedName)
    }

    fun `test only the named re-exported declaration is public`() {
        myFixture.addFileToProject("index.ts", "export { foo } from './foo'")
        myFixture.configureByText("foo.ts", "export function foo() {}\nexport function bar() {}")

        val functions = PsiTreeUtil.findChildrenOfType(myFixture.file, JSFunction::class.java).toList()
        val foo = functions.single { it.name == "foo" }
        val bar = functions.single { it.name == "bar" }

        assertTrue(BarrelResolver.resolve(foo) is BarrelExportStatus.Public)
        assertEquals(BarrelExportStatus.Internal, BarrelResolver.resolve(bar))
    }

    fun `test aliased re-export is public under the exported alias name`() {
        myFixture.addFileToProject("index.ts", "export { foo as renamedForConsumers } from './foo'")
        myFixture.configureByText("foo.ts", "export function foo() {}")

        val declaration = PsiTreeUtil.findChildOfType(myFixture.file, JSFunction::class.java)!!

        val status = BarrelResolver.resolve(declaration)

        assertTrue(status is BarrelExportStatus.Public)
        assertEquals("renamedForConsumers", (status as BarrelExportStatus.Public).exportedName)
    }

    fun `test declaration is public via an ancestor directory's barrel, not just a sibling one`() {
        // No index.ts inside lib/ itself - only the project-root barrel two levels up re-exports
        // through the nested relative path. Forces walking past the immediate directory.
        myFixture.addFileToProject("index.ts", "export * from './lib/foo'")
        val fooFile = myFixture.addFileToProject("lib/foo.ts", "export function foo() {}")

        val declaration = PsiTreeUtil.findChildOfType(fooFile, JSFunction::class.java)!!

        assertTrue(BarrelResolver.resolve(declaration) is BarrelExportStatus.Public)
    }

    fun `test result flips to internal when the barrel is edited to drop the re-export`() {
        val barrelFile = myFixture.addFileToProject("index.ts", "export * from './foo'")
        val fooFile = myFixture.addFileToProject("foo.ts", "export function foo() {}")
        val declaration = PsiTreeUtil.findChildOfType(fooFile, JSFunction::class.java)!!

        assertTrue(BarrelResolver.resolve(declaration) is BarrelExportStatus.Public)

        WriteCommandAction.runWriteCommandAction(project) {
            barrelFile.viewProvider.document!!.setText("// nothing exported here anymore")
        }
        PsiDocumentManager.getInstance(project).commitAllDocuments()

        assertEquals(BarrelExportStatus.Internal, BarrelResolver.resolve(declaration))
    }

    fun `test exported arrow function const re-exported by name is public`() {
        myFixture.addFileToProject("index.ts", "export { Foo } from './foo'")
        myFixture.configureByText("foo.ts", "export const Foo = () => {}")

        val declaration = PsiTreeUtil.findChildOfType(myFixture.file, JSVariable::class.java)!!

        assertTrue(BarrelResolver.resolve(declaration) is BarrelExportStatus.Public)
    }

    fun `test type re-exported via type-only named export is public`() {
        myFixture.addFileToProject("index.ts", "export type { Foo } from './types'")
        myFixture.configureByText("types.ts", "export type Foo = { id: string }")

        val declaration = PsiTreeUtil.findChildOfType(myFixture.file, TypeScriptTypeAlias::class.java)!!

        assertTrue(BarrelResolver.resolve(declaration) is BarrelExportStatus.Public)
    }

    fun `test interface re-exported via type-only named export is public`() {
        myFixture.addFileToProject("index.ts", "export type { Foo } from './types'")
        myFixture.configureByText("types.ts", "export interface Foo { id: string }")

        val declaration = PsiTreeUtil.findChildOfType(myFixture.file, TypeScriptInterface::class.java)!!

        assertTrue(BarrelResolver.resolve(declaration) is BarrelExportStatus.Public)
    }

    fun `test editing an unrelated file does not invalidate the cached status`() {
        myFixture.addFileToProject("index.ts", "export * from './foo'")
        val fooFile = myFixture.addFileToProject("foo.ts", "export function foo() {}")
        val unrelatedFile = myFixture.addFileToProject("unrelated.ts", "export function unrelated() {}")
        val declaration = PsiTreeUtil.findChildOfType(fooFile, JSFunction::class.java)!!

        val first = BarrelResolver.resolve(declaration)

        WriteCommandAction.runWriteCommandAction(project) {
            unrelatedFile.viewProvider.document!!.insertString(0, "// touched\n")
        }
        PsiDocumentManager.getInstance(project).commitAllDocuments()

        val second = BarrelResolver.resolve(declaration)

        assertSame("editing an unrelated file should not force recomputation", first, second)
    }
}
