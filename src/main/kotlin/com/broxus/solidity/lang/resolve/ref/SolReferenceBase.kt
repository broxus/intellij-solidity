package com.broxus.solidity.lang.resolve.ref

import com.broxus.solidity.lang.core.SolidityTokenTypes.IDENTIFIER
import com.broxus.solidity.lang.psi.SolElement
import com.broxus.solidity.lang.psi.SolPsiFactory
import com.broxus.solidity.lang.psi.SolReferenceElement
import com.broxus.solidity.lang.psi.elementType
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementResolveResult
import com.intellij.psi.PsiPolyVariantReferenceBase
import com.intellij.psi.impl.source.resolve.ResolveCache

abstract class SolReferenceBase<T : SolReferenceElement>(element: T) : PsiPolyVariantReferenceBase<T>(element), SolReference {
  override fun calculateDefaultRangeInElement() = TextRange(0, element.textRange.length)

  override fun getVariants(): Array<out Any> = emptyArray()

  final override fun multiResolve(incompleteCode: Boolean) = ResolveCache.getInstance(element.project)
    .resolveWithCaching(this, { r, _ ->
      r.multiResolve().map(::PsiElementResolveResult).toTypedArray()
    }, true, false)

  override fun multiResolve(): Collection<PsiElement> = singleResolve()?.let { listOf(it) } ?: emptyList()

  open fun singleResolve(): PsiElement? = null

  override fun handleElementRename(newName: String): PsiElement {
    doRename(element.referenceNameElement, newName)
    return element
  }

  override fun resolve(): SolElement? = super.resolve() as? SolElement?

  protected open fun doRename(identifier: PsiElement, newName: String) {
    check(identifier.elementType == IDENTIFIER)
    identifier.replace(SolPsiFactory(identifier.project).createIdentifier(newName.replace(".tsol", "")))
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false
    other as SolReferenceBase<*>
    return element == other.element
  }

  override fun hashCode() = element.hashCode()
}
