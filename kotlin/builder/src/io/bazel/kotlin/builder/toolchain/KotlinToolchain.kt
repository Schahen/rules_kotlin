/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.bazel.kotlin.builder.toolchain

import io.bazel.kotlin.builder.utils.resolveVerified
import org.jetbrains.kotlin.preloading.ClassPreloadingUtils
import org.jetbrains.kotlin.preloading.Preloader
import java.io.File
import java.io.PrintStream
import java.io.PrintWriter
import java.nio.file.Path
import java.nio.file.Paths
import javax.inject.Inject
import javax.inject.Singleton

class KotlinToolchain private constructor(
    internal val javaHome: Path,
    val kotlinHome: Path,
    val classLoader: ClassLoader,
    val kotlinStandardLibraries: List<String> = listOf(
        "kotlin-stdlib.jar",
        "kotlin-stdlib-jdk7.jar",
        "kotlin-stdlib-jdk8.jar"
    ),
    val kapt3Plugin: KotlinToolchain.CompilerPlugin = KotlinToolchain.CompilerPlugin(
        kotlinHome.resolveVerified("lib", "kotlin-annotation-processing.jar").absolutePath,
        "org.jetbrains.kotlin.kapt3"
    )
) {

    companion object {
        internal val NO_ARGS = arrayOf<Any>()

        private val isJdk9OrNewer = !System.getProperty("java.version").startsWith("1.")
        private val javaRunfiles get() = Paths.get(System.getenv("JAVA_RUNFILES"))

        private fun createClassLoader(javaHome: Path, kotlinHome: Path): ClassLoader {
            val preloadJars = mutableListOf<File>().also {
                it += kotlinHome.resolveVerified("lib", "kotlin-compiler.jar")
                it += javaRunfiles.resolveVerified("io_bazel_rules_kotlin", "kotlin", "builder", "compiler_lib.jar")
                if (!isJdk9OrNewer) {
                    it += javaHome.resolveVerified("lib", "tools.jar")
                }
            }
            return ClassPreloadingUtils.preloadClasses(
                preloadJars,
                Preloader.DEFAULT_CLASS_NUMBER_ESTIMATE,
                ClassLoader.getSystemClassLoader(),
                null
            )
        }

        @JvmStatic
        fun createToolchain(): KotlinToolchain {
            val kotlinHome = Paths.get("external", "com_github_jetbrains_kotlin")
            val javaHome = Paths.get(System.getProperty("java.home")).let {
                it.takeIf { !it.endsWith(Paths.get("jre")) } ?: it.parent
            }
            return KotlinToolchain(
                javaHome,
                kotlinHome,
                createClassLoader(javaHome, kotlinHome)
            )
        }

//        @JvmStatic
//        fun createToolchainModule(outputProvider: Provider<PrintStream>): Module {
//            val toolchain = createToolchain()
//            return object : AbstractModule() {
//                override fun configure() {
//                    bind(PrintStream::class.java).toProvider(outputProvider)
//                    bind(KotlinToolchain::class.java).toInstance(toolchain)
//                    install(KotlinToolchainModule)
//                }
//            }
//        }
    }

    data class CompilerPlugin(val jarPath: String, val id: String)

    @Singleton
    class JavacInvoker @Inject constructor(toolchain: KotlinToolchain) {
        private val c = toolchain.classLoader.loadClass("com.sun.tools.javac.Main")
        private val m = c.getMethod("compile", Array<String>::class.java)
        private val mPw = c.getMethod("compile", Array<String>::class.java, PrintWriter::class.java)
        fun compile(args: Array<String>) = m.invoke(c, args) as Int
        fun compile(args: Array<String>, out: PrintWriter) = mPw.invoke(c, args, out) as Int
    }

    @Singleton
    class JDepsInvoker @Inject constructor(toolchain: KotlinToolchain) {
        private val clazz = toolchain.classLoader.loadClass("com.sun.tools.jdeps.Main")
        private val method = clazz.getMethod("run", Array<String>::class.java, PrintWriter::class.java)
        fun run(args: Array<String>, out: PrintWriter): Int = method.invoke(clazz, args, out) as Int
    }

    @Singleton
    class KotlincInvoker @Inject constructor(toolchain: KotlinToolchain) {
        private val compilerClass = toolchain.classLoader.loadClass("io.bazel.kotlin.compiler.BazelK2JVMCompiler")
        private val exitCodeClass = toolchain.classLoader.loadClass("org.jetbrains.kotlin.cli.common.ExitCode")

        private val compiler = compilerClass.getConstructor().newInstance()
        private val execMethod = compilerClass.getMethod("exec", PrintStream::class.java, Array<String>::class.java)
        private val getCodeMethod = exitCodeClass.getMethod("getCode")

        fun compile(args: Array<String>, out: PrintStream): Int {
            val exitCodeInstance = execMethod.invoke(compiler, out, args)
            return getCodeMethod.invoke(exitCodeInstance, *NO_ARGS) as Int
        }
    }
}


