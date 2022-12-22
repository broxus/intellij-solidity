package com.broxus.solidity.ide.navigation

import com.intellij.codeInsight.navigation.GotoImplementationHandler
import com.intellij.codeInsight.navigation.GotoTargetHandler.GotoData
import com.intellij.openapi.ui.GenericListComponentUpdater
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.ui.components.JBList
import com.intellij.ui.popup.AbstractPopup
import com.intellij.ui.popup.ComponentPopupBuilderImpl
import com.intellij.util.ui.UIUtil
import com.broxus.solidity.lang.psi.SolContractDefinition
import com.broxus.solidity.utils.SolTestBase
import org.intellij.lang.annotations.Language
import org.junit.Assert

class GoToImplementationTest : SolTestBase() {

  fun testFindImplementations() = testImplementations("""
      contract A/*caret*/ { }
      contract B is A { }
  """, setOf("B"))

  fun testFindMultipleImplementations() = testImplementations("""
      contract A/*caret*/ { }
      contract B is A { }
      contract C is B { }
  """, setOf("B", "C"))

  private fun testImplementations(@Language("T-Sol") code: String, options: Set<String>) {
    InlineFile(code).withCaret()
    val handler = GotoImplementationHandler()

    val data: GotoData? = handler.getSourceAndTargetElements(myFixture.editor, myFixture.file)
    if (data == null) {
      throw RuntimeException("Can't find implementations")
    }
    drainGoToDataEvents(data)
    Assert.assertEquals(options, data.targets.map { (it as SolContractDefinition).name }.toSet())
  }

  private fun drainGoToDataEvents(data: GotoData) {
    if (data.listUpdaterTask != null) {
      val list = JBList<String>()
      val popup = ComponentPopupBuilderImpl(list, null).createPopup()
      data.listUpdaterTask.init(popup as AbstractPopup, object: GenericListComponentUpdater<PsiElement> {
        override fun replaceModel(data: MutableList<out PsiElement>) {
        }

        override fun paintBusy(paintBusy: Boolean) {
        }
      }, Ref())
      data.listUpdaterTask.queue()

      try {
        while (!data.listUpdaterTask.isFinished) {
          UIUtil.dispatchAllInvocationEvents()
        }
      } finally {
        Disposer.dispose(popup)
      }
    }
  }
}
