package com.broxus.solidity.lang.resolve

import com.broxus.solidity.lang.core.SolidityFile
import com.broxus.solidity.lang.psi.*
import com.broxus.solidity.lang.psi.impl.SolNewExpressionElement
import com.broxus.solidity.lang.psi.impl.getAliases
import com.broxus.solidity.lang.psi.parentOfType
import com.broxus.solidity.lang.resolve.ref.SolFunctionCallReference
import com.broxus.solidity.lang.stubs.SolGotoClassIndex
import com.broxus.solidity.lang.stubs.SolModifierIndex
import com.broxus.solidity.lang.types.*
import com.broxus.solidity.nullIfError
import com.broxus.solidity.wrap
import com.intellij.openapi.util.RecursionManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.util.*

object SolResolver {
  fun resolveTypeNameUsingImports(element: PsiElement): Set<SolNamedElement> =
    CachedValuesManager.getCachedValue(element) {
      val result = if (element is SolFunctionCallElement) {
        resolveError(element) +
          resolveEvent(element) +
          resolveContract(element) +
          resolveEnum(element) +
          resolveUserDefinedValueType(element)
      } else {
        resolveContract(element) +
          resolveEnum(element) +
          resolveStruct(element) +
          resolveUserDefinedValueType(element) +
          resolveBuiltinValueType(element)
      }
      CachedValueProvider.Result.create(result, PsiModificationTracker.MODIFICATION_COUNT)
    }


  /**
   * @param withAliases aliases are not recursive, so count them only at the first level of recursion
   */
  private fun <T: SolNamedElement>resolveUsingImports(
    target: Class<T>,
    element: PsiElement,
    file: PsiFile,
    withAliases: Boolean,
  ): Set<T> =
    RecursionManager.doPreventingRecursion(ResolveUsingImportsKey(element.nameOrText, file), true) {
      if (element is SolUserDefinedTypeName && element.findIdentifiers().size > 1) {
        emptySet()
      } else {
        val inFile = file.children
          .filterIsInstance(target)
          .filter { it.name == element.nameOrText }

        val resolvedViaAlias = when (withAliases) {
          true -> file.children
            .filterIsInstance<SolImportDirective>()
            .mapNotNull { directive ->
              directive.importAliasedPairList
                .firstOrNull { aliasPair -> aliasPair.importAlias?.name == element.nameOrText }
                ?.let { aliasPair ->
                  directive.importPath?.reference?.resolve()?.let { resolvedFile ->
                    aliasPair.userDefinedTypeName to resolvedFile
                  }
                }
            }.flatMap { (alias, resolvedFile) ->
              resolveUsingImports(target, alias, resolvedFile.containingFile, false)
            }
          else -> emptyList()
        }

        val insideImportDirective = element.findParentOfType<SolImportDirective>() != null

        val imported = file.children
          .filterIsInstance<SolImportDirective>()
          .filter { insideImportDirective || it.importAliasedPairList.let { it.isEmpty() || it.any {it.importAlias?.name == element.nameOrText } } }
          .mapNotNull { nullIfError { it.importPath?.reference?.resolve()?.containingFile } }
          .flatMap { resolveUsingImports(target, element, it, false) }

        (inFile + resolvedViaAlias + imported).toSet()
      }
    } ?: emptySet()

  private fun resolveContract(element: PsiElement): Set<SolContractDefinition> =
    resolveUsingImports(SolContractDefinition::class.java, element, element.containingFile, true)
  private fun resolveEnum(element: PsiElement): Set<SolNamedElement> =
    resolveInnerType<SolEnumDefinition>(element) { it.enumDefinitionList } + resolveUsingImports(SolEnumDefinition::class.java, element, element.containingFile, true)

  private fun resolveStruct(element: PsiElement): Set<SolNamedElement> =
    resolveInnerType<SolStructDefinition>(element) { it.structDefinitionList } + resolveUsingImports(SolStructDefinition::class.java, element, element.containingFile, true)

  private fun resolveUserDefinedValueType(element: PsiElement): Set<SolNamedElement> =
    resolveInnerType<SolUserDefinedValueTypeDefinition>(
      element,
      { it.userDefinedValueTypeDefinitionList }) + resolveUsingImports(SolUserDefinedValueTypeDefinition::class.java, element, element.containingFile, true)

  private fun resolveBuiltinValueType(element: PsiElement): Set<SolNamedElement> {
    val id = (element as? SolUserDefinedTypeNameElement)?.findIdentifiers()?.firstOrNull() ?:
        (element as? SolVarLiteral)?.identifier ?: return emptySet()

    return setOf(SolInternalTypeFactory.of(element.project).builtinByName(id.nameOrText ?: return emptySet()) ?: return emptySet())
  }


  private fun resolveEvent(element: PsiElement): Set<SolNamedElement> =
    resolveInnerType<SolEventDefinition>(element) { it.eventDefinitionList }

  private fun resolveError(element: PsiElement): Set<SolNamedElement> =
    resolveInnerType<SolErrorDefinition>(element) { it.errorDefinitionList } + resolveUsingImports(SolErrorDefinition::class.java, element, element.containingFile, true)

  private inline fun <reified T : SolNamedElement> resolveInFile(element: PsiElement) : Set<T> {
    return element.parentOfType<SolidityFile>()
      ?.children
      ?.filterIsInstance<T>()
      ?.filter { it.name == element.text }
      ?.toSet() ?: emptySet()
  }

  private fun <T : SolNamedElement> resolveInnerType(
    element: PsiElement,
    f: (SolContractDefinition) -> List<T>
  ): Set<T> {
    val inheritanceSpecifier = element.parentOfType<SolInheritanceSpecifier>()
    return if (inheritanceSpecifier != null) {
      emptySet()
    } else {
      val names = if (element is SolUserDefinedTypeNameElement) {
        element.findIdentifiers()
      } else {
        element.wrap()
      }
      when {
        names.size > 2 -> emptySet()
        names.size > 1 -> resolveTypeNameUsingImports(names[0])
          .filterIsInstance<SolContractDefinition>()
          .firstOrNull()
          ?.let { resolveInnerType(it, names[1].nameOrText!!, f) }
          ?: emptySet()

        else -> element.parentOfType<SolContractDefinition>()
          ?.let {
            names[0].nameOrText?.let { nameOrText ->
              resolveInnerType(it, nameOrText, f)
            }
          }
          ?: emptySet()
      }
    }
  }

  private val PsiElement.nameOrText
    get() = if (this is PsiNamedElement) {
      this.name
    } else {
      this.text
    }

  private fun <T : SolNamedElement> resolveInnerType(
    contract: SolContractDefinition,
    name: String,
    f: (SolContractDefinition) -> List<T>
  ): Set<T> {
    val supers = contract.collectSupers
      .mapNotNull { it.reference?.resolve() }.filterIsInstance<SolContractDefinition>() + contract
    return supers.flatMap(f)
      .filter { it.name == name }
      .toSet()
  }

  fun resolveTypeName(element: SolReferenceElement): Collection<SolNamedElement> = StubIndex.getElements(
    SolGotoClassIndex.KEY,
    element.referenceName,
    element.project,
    null,
    SolNamedElement::class.java
  )

  fun resolveModifier(modifier: SolModifierInvocationElement): List<SolModifierDefinition> = StubIndex.getElements(
    SolModifierIndex.KEY,
    modifier.firstChild.text,
    modifier.project,
    null,
    SolNamedElement::class.java
  ).filterIsInstance<SolModifierDefinition>()
    .toList()

  fun resolveVarLiteralReference(element: SolNamedElement): List<SolNamedElement> {
    return when {
        element.parent?.parent is SolFunctionCallExpression -> {
          val functionCall = element.findParentOrNull<SolFunctionCallElement>()!!
          val resolved = functionCall.reference?.multiResolve() ?: emptyList()
          if (resolved.isNotEmpty()) {
            resolved.filterIsInstance<SolNamedElement>()
          } else {
            resolveVarLiteral(element)
          }
        }
        element.parent is SolModifierInvocation -> {
          (element.parent as SolModifierInvocation).reference?.multiResolve()?.filterIsInstance<SolNamedElement>() ?: emptyList()
        }
        else -> {
          resolveVarLiteral(element)
            .findBest {
              when (it) {
                is SolStateVariableDeclaration -> 0
                else -> Int.MAX_VALUE
              }
            }
        }
    }
  }

  private fun <T : Any> List<T>.findBest(priorities: (T) -> Int): List<T> {
    return this
      .groupBy { priorities(it) }
      .minByOrNull { it.key }
      ?.value
      ?: emptyList()
  }

  fun resolveVarLiteral(element: SolNamedElement): List<SolNamedElement> {
    return when (element.name) {
      "this" -> element.findContract()
        .wrap()
      "super" -> element.findContract()
        ?.supers
        ?.flatMap { resolveTypeNameUsingImports(it) }
        ?: emptyList()
      else -> lexicalDeclarations(element)
        .filter { it.name == element.name }
        .distinct()
        .toList()
    }
  }

  fun resolveMemberAccess(element: SolMemberAccessExpression): List<SolMember> {
    if (element.parent is SolFunctionCallExpression) {
      val functionCall = element.findParentOrNull<SolFunctionCallElement>()!!
      val resolved = (functionCall.reference as SolFunctionCallReference)
        .resolveFunctionCallAndFilter()
        .filterIsInstance<SolMember>()
      if (resolved.isNotEmpty()) {
        return resolved
      }
    }
    return when (val memberName = element.identifier?.text) {
      null -> emptyList()
      else -> element.expression.getMembers()
        .filter { it.getName() == memberName }
    }
  }

  fun resolveContractMembers(contract: SolContractDefinition, skipThis: Boolean = false): List<SolMember> {
    val members = if (!skipThis)
      contract.stateVariableDeclarationList as List<SolMember> + contract.functionDefinitionList  +
        contract.structDefinitionList.map { SolStructConstructor(it) }
    else
      emptyList()
    return members + contract.supers
      .map { resolveTypeName(it).firstOrNull() }
      .filterIsInstance<SolContractDefinition>()
      .flatMap { resolveContractMembers(it) }
  }

  fun lexicalDeclarations(place: PsiElement, stop: (PsiElement) -> Boolean = { false }): Sequence<SolNamedElement> {
    val globalType = SolInternalTypeFactory.of(place.project).globalType
    return lexicalDeclarations(globalType.ref, place) + lexicalDeclRec(place, stop).distinct() + place.getAliases() + resolveTypeNameUsingImports(place)
  }

  private fun lexicalDeclRec(place: PsiElement, stop: (PsiElement) -> Boolean): Sequence<SolNamedElement> {
    return place.ancestors
      .drop(1) // current element might not be a SolElement
      .takeWhileInclusive { it is SolElement && !stop(it) }
      .flatMap { lexicalDeclarations(it, place) }
  }

  private fun lexicalDeclarations(scope: PsiElement, place: PsiElement): Sequence<SolNamedElement> {
    return when (scope) {
      is SolVariableDeclaration -> {
        scope.declarationList?.declarationItemList?.filterIsInstance<SolNamedElement>()?.asSequence()
          ?: scope.typedDeclarationList?.typedDeclarationItemList?.filterIsInstance<SolNamedElement>()?.asSequence()
          ?: sequenceOf(scope)
      }
      is SolVariableDefinition -> lexicalDeclarations(scope.firstChild, place)

      is SolStateVariableDeclaration -> sequenceOf(scope)
      is SolContractDefinition -> {
        val childrenScope = sequenceOf(
          scope.stateVariableDeclarationList as List<PsiElement>,
          scope.enumDefinitionList,
          scope.structDefinitionList
        ).flatten()
          .map { lexicalDeclarations(it, place) }
          .flatten() + scope.structDefinitionList + scope.eventDefinitionList + scope.errorDefinitionList
        val extendsScope = scope.supers.asSequence()
          .map { resolveTypeName(it).firstOrNull() }
          .filterNotNull()
          .map { lexicalDeclarations(it, place) }
          .flatten()
        childrenScope + extendsScope + scope.functionDefinitionList
      }
      is SolFunctionDefinition -> {
        scope.parameters.asSequence() +
          (scope.returns?.parameterDefList?.asSequence() ?: emptySequence())
      }
      is SolConstructorDefinition -> {
        scope.parameterList?.parameterDefList?.asSequence() ?: emptySequence()
      }
      is SolModifierDefinition -> {
        scope.parameterList?.parameterDefList?.asSequence() ?: emptySequence()
      }
      is SolEnumDefinition -> sequenceOf(scope)
      is SolForStatement -> if (PsiTreeUtil.isAncestor(scope, place, false)) scope.children.firstOrNull()?.let { lexicalDeclarations(it, place) } ?: emptySequence() else emptySequence()

      is SolStatement -> {
        scope.children.asSequence()
          .map { lexicalDeclarations(it, place) }
          .flatten()
      }

      is SolBlock -> {
        scope.statementList.asSequence()
          .map { lexicalDeclarations(it, place) }
          .flatten()
      }

      is SolidityFile -> {
        RecursionManager.doPreventingRecursion(scope.name, true) {
          val contracts = scope.children.asSequence()
            .filterIsInstance<SolContractDefinition>()

          val constantVariables = scope.children.asSequence()
            .filterIsInstance<SolConstantVariable>()

          val freeFunctions = scope.children.asSequence()
            .filterIsInstance<SolFunctionDefinition>()

          // NOTE: Imports are intentionally resolved eagerly rather than lazily to ensure that
          // cyclic imports don't cause infinite recursion.
          val imports = scope.children.asSequence().filterIsInstance<SolImportDirective>()
            .mapNotNull { if (it.importAliasedPairList.let { it.isEmpty() || it.any {it.importAlias?.name == place.nameOrText } }) {
              nullIfError { it.importPath?.reference?.resolve()?.containingFile }
              } else null }
            .mapNotNull { lexicalDeclarations(it, place) }
            .flatten()
            .toList()
            .asSequence()
          imports + contracts + constantVariables + freeFunctions
        } ?: emptySequence()
      }

      is SolTupleStatement -> {
        scope.variableDeclaration?.let {
          val declarationList = it.declarationList
          val typedDeclarationList = it.typedDeclarationList
          when {
            declarationList != null -> declarationList.declarationItemList.asSequence()
            typedDeclarationList != null -> typedDeclarationList.typedDeclarationItemList.asSequence()
            else -> sequenceOf(it)
          }
        } ?: emptySequence()
      }

      else -> emptySequence()
    }
  }

  fun resolveNewExpression(parentNew: SolNewExpressionElement): Collection<PsiElement> {
    return parentNew.reference.multiResolve()
  }
}

data class ResolveUsingImportsKey(val name: String?, val file: PsiFile)

private fun <T> Sequence<T>.takeWhileInclusive(pred: (T) -> Boolean): Sequence<T> {
  var shouldContinue = true
  return takeWhile {
    val result = shouldContinue
    shouldContinue = pred(it)
    result
  }
}

fun SolCallable.canBeApplied(arguments: SolFunctionCallArguments): Boolean {
  val callArgumentTypes = arguments.expressionList.map { it.type }
  val parameters = parseParameters()
    .map { it.second }
  if (parameters.size != callArgumentTypes.size)
    return false
  return !parameters.zip(callArgumentTypes)
    .any { (paramType, argumentType) ->
      paramType != SolUnknown && !paramType.isAssignableFrom(argumentType)
    }
}
