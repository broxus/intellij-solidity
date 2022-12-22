package com.broxus.solidity.ide.hints

import com.intellij.lang.ExpressionTypeProvider
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.SyntaxTraverser
import com.broxus.solidity.lang.psi.SolExpression
import com.broxus.solidity.lang.types.type

class SolExpressionTypeProvider : ExpressionTypeProvider<SolExpression>() {
  override fun getExpressionsAt(pivot: PsiElement): MutableList<SolExpression> {
    return SyntaxTraverser.psiApi().parents(pivot)
      .filter(SolExpression::class.java)
      .toList()
  }

  override fun getInformationHint(element: SolExpression): String = StringUtil.escapeXmlEntities(element.type.toString())

  override fun getErrorHint() = "Select an expression"
}
