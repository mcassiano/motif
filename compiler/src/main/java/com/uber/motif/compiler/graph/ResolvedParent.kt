package com.uber.motif.compiler.graph

import com.squareup.javapoet.ClassName
import com.uber.motif.compiler.asDeclaredType
import com.uber.motif.compiler.codegen.className
import com.uber.motif.compiler.innerInterfaces
import com.uber.motif.compiler.methods
import com.uber.motif.compiler.model.Dependency
import com.uber.motif.compiler.model.ParentInterface
import com.uber.motif.compiler.model.ParentInterfaceMethod
import com.uber.motif.compiler.names.Names
import com.uber.motif.compiler.names.UniqueNameSet
import com.uber.motif.compiler.simpleName
import javax.annotation.processing.ProcessingEnvironment
import javax.lang.model.element.TypeElement
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.ExecutableType

class ResolvedParent(
        val className: ClassName,
        val methods: List<ParentInterfaceMethod>) {

    companion object {

        fun fromCalculated(
                scopeType: TypeElement,
                externalDependencies: Set<Dependency>,
                transitiveDependencies: Set<Dependency>): ResolvedParent {
            val methodNames = UniqueNameSet()
            val methods: List<ParentInterfaceMethod> = externalDependencies.map { dependency ->
                val transitive = dependency in transitiveDependencies
                ParentInterfaceMethod(methodNames.unique(dependency.preferredName), transitive, dependency)
            }
            val className = ClassNames.generatedParentInterface(scopeType)
            return ResolvedParent(className, methods)
        }

        fun fromExplicit(parentInterface: ParentInterface): ResolvedParent {
            return ResolvedParent(parentInterface.type.className, parentInterface.methods)
        }

        /**
         * Return the ParentInterface for this scope if the implementation is already generated. Otherwise, return null.
         */
        fun fromGenerated(env: ProcessingEnvironment, scopeElement: TypeElement): ResolvedParent? {
            val scopeType = scopeElement.asDeclaredType()
            val scopeImplType = findScopeImpl(env, scopeType) ?: return null
            val parentInterfaceType: DeclaredType = findParentInterface(scopeType, scopeImplType)
            val methods = parentInterfaceType.methods(env).map {
                val methodType: ExecutableType = env.typeUtils.asMemberOf(parentInterfaceType, it) as ExecutableType
                ParentInterfaceMethod.fromMethod(parentInterfaceType, it, methodType)
            }
            return ResolvedParent(parentInterfaceType.className, methods)
        }

        private fun findParentInterface(scopeType: DeclaredType, scopeImplType: DeclaredType): DeclaredType {
            scopeImplType.getParentInterface()?.let { return it }
            // It's possible that the child scope defines the parent interface explicitly so check for that case as well.
            scopeType.getParentInterface()?.let { return it }

            throw RuntimeException("Could not find generated ScopeImpl.Parent class for: $scopeType")
        }

        private fun findScopeImpl(env: ProcessingEnvironment, scopeType: DeclaredType): DeclaredType? {
            val scopeImplName = ClassNames.scopeImpl(scopeType)
            return env.elementUtils.getTypeElement(scopeImplName.qualifiedName())?.asDeclaredType()
        }

        private fun DeclaredType.getParentInterface(): DeclaredType? {
            return innerInterfaces().find { it.simpleName == Names.PARENT_INTERFACE_NAME }
        }
    }
}