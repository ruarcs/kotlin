/*
 * Copyright 2010-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.script.util

import org.jetbrains.kotlin.script.*
import org.jetbrains.kotlin.script.util.resolvers.DirectResolver
import org.jetbrains.kotlin.script.util.resolvers.FlatLibDirectoryResolver
import org.jetbrains.kotlin.script.util.resolvers.MavenResolver
import org.jetbrains.kotlin.script.util.resolvers.Resolver
import java.io.File
import java.lang.Exception
import java.lang.IllegalArgumentException
import java.util.concurrent.Future

open class KotlinAnnotatedScriptDependenciesResolver(val baseClassPath: List<File>, resolvers: Iterable<Resolver>)
    : ScriptDependenciesResolver
{
    private val resolvers: MutableList<Resolver> = resolvers.toMutableList()

    @AcceptedAnnotations(DependsOn::class, Repository::class)
    override fun resolve(script: ScriptContents,
                         environment: Map<String, Any?>?,
                         report: (ScriptDependenciesResolver.ReportSeverity, String, ScriptContents.Position?) -> Unit,
                         previousDependencies: KotlinScriptExternalDependencies?
    ): Future<KotlinScriptExternalDependencies?>
            = (if (previousDependencies != null && resolveFromAnnotations(script).isEmpty()) previousDependencies
                    else
                        object : KotlinScriptExternalDependencies {
                            override val classpath: Iterable<File> = if (resolvers.isEmpty()) baseClassPath  else baseClassPath + resolveFromAnnotations(script)
                            override val imports: Iterable<String> =
                                    previousDependencies?.let { emptyList<String>() } ?: listOf(DependsOn::class.java.`package`.name + ".*")
                        }
                   ).asFuture()

    private fun resolveFromAnnotations(script: ScriptContents): List<File> {
        script.annotations.forEach {
            when (it) {
                is Repository ->
                    when {
                        File(it.value).run { exists() && isDirectory } -> resolvers.add(FlatLibDirectoryResolver(File(it.value)))
                        else -> throw IllegalArgumentException("Illegal argument for Repository annotation: ${it.value}")
                    }
                is DependsOn -> {}
                is InvalidScriptResolverAnnotation -> throw Exception("Invalid annotation ${it.name}", it.error)
                else -> throw Exception("Unknown annotation ${it.javaClass}")
            }
        }
        return script.annotations.filterIsInstance(DependsOn::class.java).flatMap { dep ->
            resolvers.asSequence().mapNotNull { it.tryResolve(dep) }.firstOrNull() ?:
                    throw Exception("Unable to resolve dependency $dep")
        }
    }
}

val defaultScriptBaseClasspath: List<File> by lazy {
    val clp = "${StandardScript::class.qualifiedName?.replace('.', '/')}.class"
    val url = Thread.currentThread().contextClassLoader.getResource(clp)
    url?.toURI()?.path?.removeSuffix(clp)?.let {
        listOf(File(it))
    } ?: emptyList()
}

class DefaultKotlinResolver() :
        KotlinAnnotatedScriptDependenciesResolver(defaultScriptBaseClasspath, arrayListOf())

class DefaultKotlinAnnotatedScriptDependenciesResolver :
        KotlinAnnotatedScriptDependenciesResolver(defaultScriptBaseClasspath, arrayListOf(DirectResolver(), MavenResolver()))
