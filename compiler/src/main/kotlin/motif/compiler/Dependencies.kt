/*
 * Copyright (c) 2018-2019 Uber Technologies, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package motif.compiler

import com.squareup.javapoet.ClassName
import com.squareup.javapoet.MethodSpec
import com.squareup.javapoet.TypeName
import com.squareup.javapoet.TypeSpec
import motif.ast.IrClass
import motif.ast.IrMethod
import motif.core.ResolvedGraph
import motif.models.*
import java.util.*
import javax.lang.model.element.Modifier

class Dependencies private constructor(
        val spec: TypeSpec,
        val typeName: TypeName,
        private val methodSpecs: SortedMap<Type, Method>) {

    private val methodList = methodSpecs.values.toList()

    fun getMethodSpec(type: Type): MethodSpec? {
        return methodSpecs[type]?.methodSpec
    }

    fun isEmpty(): Boolean {
        return methodSpecs.isEmpty()
    }

    fun types(): List<Type> {
        return methodSpecs.keys.toList()
    }

    fun getMethods(): List<Method> {
        return methodList
    }

    class Method(val type: Type, val methodSpec: MethodSpec)

    data class Requester(val callerQualifiedName: String, val callerMethodName: String)
    data class TypeAndRequesters(val type: Type, val requesters: List<Requester>)

    companion object {

        fun create(
                graph: ResolvedGraph,
                scope: Scope,
                scopeImplTypeName: ClassName): Dependencies {
            val sinks = graph.getUnsatisfied(scope)
            val nameScope = NameScope()
            val typeName = scopeImplTypeName.nestedClass("Dependencies")

            val methods: SortedMap<Type, Method> = getRequestersByType(sinks)
                    .map { typeAndRequesters ->
                        val methodSpec = methodSpec(nameScope, typeAndRequesters.type)
                                .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                                .addJavadoc(getJavadocForType(typeAndRequesters.requesters))
                                .build()
                        Method(typeAndRequesters.type, methodSpec)
                    }
                    .associateBy { it.type }
                    .toSortedMap()

            val typeSpec = TypeSpec.interfaceBuilder(typeName)
                    .addModifiers(Modifier.PUBLIC)
                    .addMethods(methods.values.map { it.methodSpec })
                    .build()

            return Dependencies(typeSpec, typeName, methods)
        }

        private fun getRequestersByType(sinks: Iterable<Sink>): Iterable<TypeAndRequesters> {
            return sinks
                    .groupBy { it.type }
                    .mapValues { sinksByType ->
                        val requesters = sinksByType.value.map { sink ->
                            when (sink) {
                                is FactoryMethodSink -> Requester(
                                        sink.parameter.owner.qualifiedName,
                                        inspectAndReturnMethodName(sink.parameter.owner, sink.parameter.method))
                                is AccessMethodSink -> Requester(
                                        sink.accessMethod.scope.qualifiedName,
                                        sink.accessMethod.method.name)
                            }
                        }
                        TypeAndRequesters(sinksByType.key, requesters)
                    }
                    .values
        }

        private fun getJavadocForType(requesters: List<Requester>): String {
            val requestersHtmlItems = requesters.joinToString(separator = "\n") { requester ->
                "<li>{@link ${requester.callerQualifiedName}#${requester.callerMethodName}}</li>"
            }
            return "<ul>Requested from:\n$requestersHtmlItems\n</ul>"
        }

        private fun inspectAndReturnMethodName(owner: IrClass, method: IrMethod): String {
            if (!"<init>".contentEquals(method.name)) {
                return method.name
            }

            // we have a constructor
            val paramList = method.parameters.joinToString(separator = ",") { parameter -> parameter.type.qualifiedName }
            return "${owner.simpleName}($paramList)"
        }
    }
}
