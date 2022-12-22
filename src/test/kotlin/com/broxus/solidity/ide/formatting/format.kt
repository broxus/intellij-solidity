package com.broxus.solidity.ide.formatting

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.codeStyle.CodeStyleManager

import java.io.File

import com.broxus.solidity.utils.SolLightPlatformCodeInsightFixtureTestCase

class SolidityFormattingTest : SolLightPlatformCodeInsightFixtureTestCase() {
  private fun doTest() {
    val inputFile = inputFileName
    val inputText = FileUtil.loadFile(File(testDataPath + inputFile))
    myFixture.configureByText(inputFile, inputText)
    WriteCommandAction.runWriteCommandAction(project) {
      CodeStyleManager.getInstance(project).reformat(myFixture.file as PsiElement)
    }
    myFixture.checkResultByFile(expectedOutputFileName)
  }

  private val inputFileName: String
    get() = this.getTestName(true) + ".tsol"

  private val expectedOutputFileName: String
    get() = this.getTestName(true) + "-after.tsol"

  fun testAssembly() = this.doTest()
  fun testInsideParens() = this.doTest()
  fun testIf() = this.doTest()
  fun testLineComments() = this.doTest()
  fun testContract() = this.doTest()
  fun testVoting() = this.doTest()
  fun testLibrary() = this.doTest()
  fun testIndent() = this.doTest()
  fun testEvents() = this.doTest()
  fun testErrors() = this.doTest()
  fun testEnum() = this.doTest()
  fun testStatementLineBreak() = this.doTest()
  fun testBlankLinesBetweenContracts() = this.doTest()
  fun testBlankLinesBetweenContractParts() = this.doTest()
  fun testSpaceAfterReturns() = this.doTest()
  fun testMultisigWallet() = this.doTest()

  override fun getTestDataPath() = "src/test/resources/fixtures/formatter/"
}
