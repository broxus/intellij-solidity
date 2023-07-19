package com.broxus.solidity.ide.inspections

import com.broxus.solidity.ide.hints.NO_VALIDATION_TAG
import com.broxus.solidity.ide.hints.comments
import com.broxus.solidity.lang.core.SolidityTokenTypes
import com.broxus.solidity.lang.psi.*
import com.broxus.solidity.lang.resolve.SolResolver
import com.broxus.solidity.lang.types.SolUnknown
import com.broxus.solidity.lang.types.getSolType
import com.broxus.solidity.lang.types.type
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.parents

class ValidateFunctionArgumentsInspection : LocalInspectionTool() {
  override fun getDisplayName(): String = ""

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    return object : SolVisitor() {
      override fun visitFunctionCallArguments(element: SolFunctionCallArguments) {
        val args = element.expressionList
        fun PsiElement.getDefs(): List<SolFunctionDefElement>? {
          return ((parent.parent?.children?.firstOrNull() as? SolMemberAccessExpression)?.let {
            SolResolver.resolveMemberAccess(it)
          } ?: (parent?.parent as? SolFunctionCallExpression)?.let {
            SolResolver.resolveVarLiteralReference(it)
          })?.filterIsInstance<SolFunctionDefElement>()
        }
        if (args.firstOrNull() !is SolMapExpression) {
          element.getDefs()?.let {
            val funDefs = it.filterNot { it.comments().any { it.elementType == SolidityTokenTypes.NAT_SPEC_TAG && it.text == NO_VALIDATION_TAG } }
            if (funDefs.isNotEmpty()) {
              var wrongNumberOfArgs = ""
              var wrongTypes = ""
              var wrongElement = element as SolElement
              if (funDefs.none { ref ->
                  val expArgs = ref.parameters.size
                  val actArgs = args.size
                  if (actArgs != expArgs) {
                    wrongNumberOfArgs = "Expected $expArgs argument${if (expArgs > 1) "s" else ""}, but got $actArgs"
                    false
                  } else {
                    args.withIndex().all { argtype ->
                      ref.parameters.getOrNull(argtype.index)?.let {
                        val expType = getSolType(it.typeName)
                        val actType = argtype.value.type
                        expType == SolUnknown || actType == SolUnknown || expType.isAssignableFrom(actType).also {
                          if (!it) {
                            wrongTypes = "Argument of type '$actType' is not assignable to parameter of type '${expType}'"
                            wrongElement = argtype.value
                          }
                        }
                      } == true
                    }
                  }
                }) {
                holder.registerProblem(
                    wrongElement.parents(true).first { it.textLength > 0 }, wrongTypes.takeIf { it.isNotEmpty() }
                  ?: wrongNumberOfArgs)
              }
            }
          }
        }
      }

    }
  }
}
