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
package io.bazel.kotlin.builder.tasks

import com.google.protobuf.util.JsonFormat
import io.bazel.kotlin.builder.utils.ArgMap
import io.bazel.kotlin.builder.utils.partitionSources
import io.bazel.kotlin.model.KotlinModel.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskBuilder @Inject internal constructor() {
    companion object {
        @JvmStatic
        private val jsonTypeRegistry = JsonFormat.TypeRegistry.newBuilder()
            .add(getDescriptor().messageTypes).build()

        @JvmStatic
        private val jsonFormat: JsonFormat.Parser = JsonFormat.parser().usingTypeRegistry(jsonTypeRegistry)
    }

    /**
     * Declares the flags used by the java builder.
     */
    private enum class JavaBuilderFlags(val flag: String) {
        TARGET_LABEL("--target_label"),
        CLASSPATH("--classpath"),
        JAVAC_OPTS("--javacopts"),
        DEPENDENCIES("--dependencies"),
        DIRECT_DEPENDENCIES("--direct_dependencies"),
        DIRECT_DEPENDENCY("--direct_dependency"),
        INDIRECT_DEPENDENCY("--indirect_dependency"),
        STRICT_JAVA_DEPS("--strict_java_deps"),
        OUTPUT_DEPS_PROTO("--output_deps_proto"),
        DEPS_ARTIFACTS("--deps_artifacts"),
        REDUCE_CLASSPATH("--reduce_classpath"),
        SOURCEGEN_DIR("--sourcegendir"),
        GENERATED_SOURCES_OUTPUT("--generated_sources_output"),
        OUTPUT_MANIFEST_PROTO("--output_manifest_proto"),
        SOURCES("--sources"),
        SOURCE_ROOTS("--source_roots"),
        SOURCE_JARS("--source_jars"),
        SOURCE_PATH("--sourcepath"),
        BOOT_CLASSPATH("--bootclasspath"),
        PROCESS_PATH("--processorpath"),
        PROCESSORS("--processors"),
        EXT_CLASSPATH("--extclasspath"),
        EXT_DIR("--extdir"),
        OUTPUT("--output"),
        NATIVE_HEADER_OUTPUT("--native_header_output"),
        CLASSDIR("--classdir"),
        TEMPDIR("--tempdir"),
        GENDIR("--gendir"),
        POST_PROCESSOR("--post_processor"),
        COMPRESS_JAR("--compress_jar"),
        RULE_KIND("--rule_kind"),
        TEST_ONLY("--testonly")
    }

    fun infoFromInput(argMap: ArgMap): CompilationTaskInfo =
        with(CompilationTaskInfo.newBuilder()) {
            label = argMap.mandatorySingle(io.bazel.kotlin.builder.tasks.TaskBuilder.JavaBuilderFlags.TARGET_LABEL.flag)
            argMap.mandatorySingle(io.bazel.kotlin.builder.tasks.TaskBuilder.JavaBuilderFlags.RULE_KIND.flag).split("_").also {
                kotlin.check(it.size == 3 && it[0] == "kt")
                platform = kotlin.checkNotNull(Platform.valueOf(it[1].toUpperCase())) {
                    "unrecognized platform ${it[1]}"
                }
                ruleKind = kotlin.checkNotNull(RuleKind.valueOf(it[2].toUpperCase())) {
                    "unrecognized rule kind ${it[2]}"
                }
            }
            moduleName = argMap.mandatorySingle("--kotlin_module_name").also {
                kotlin.check(it.isNotBlank()) { "--kotlin_module_name should not be blank" }
            }
            passthroughFlags = argMap.optionalSingle("--kotlin_passthrough_flags")
            argMap.optional("--kotlin_friend_paths")?.also { addAllFriendPaths(it) }
            toolchainInfoBuilder.commonBuilder.apiVersion = argMap.mandatorySingle("--kotlin_api_version")
            toolchainInfoBuilder.commonBuilder.languageVersion = argMap.mandatorySingle("--kotlin_language_version")
            build()
        }


    fun buildJvm(info: CompilationTaskInfo, argMap: ArgMap): JvmCompilationTask =
        JvmCompilationTask.newBuilder().let { root ->
            root.info = info

            with(root.outputsBuilder) {
                jar = argMap.mandatorySingle(JavaBuilderFlags.OUTPUT.flag)
                jdeps = argMap.mandatorySingle("--output_jdeps")
                srcjar = argMap.mandatorySingle("--kotlin_output_srcjar")
            }

            with(root.directoriesBuilder) {
                classes = argMap.mandatorySingle(JavaBuilderFlags.CLASSDIR.flag)
                generatedClasses = argMap.mandatorySingle("--kotlin_generated_classdir")
                temp = argMap.mandatorySingle(JavaBuilderFlags.TEMPDIR.flag)
                generatedSources = argMap.mandatorySingle(JavaBuilderFlags.SOURCEGEN_DIR.flag)
            }

            with(root.inputsBuilder) {
                addAllClasspath(argMap.mandatory(JavaBuilderFlags.CLASSPATH.flag))
                putAllIndirectDependencies(argMap.labelDepMap(JavaBuilderFlags.DIRECT_DEPENDENCY.flag))
                putAllIndirectDependencies(argMap.labelDepMap(JavaBuilderFlags.INDIRECT_DEPENDENCY.flag))

                argMap.optional(JavaBuilderFlags.SOURCES.flag)?.iterator()?.partitionSources(
                    { addKotlinSources(it) },
                    { addJavaSources(it) }
                )
                argMap.optional(JavaBuilderFlags.SOURCE_JARS.flag)?.also {
                    addAllSourceJars(it)
                }
            }

            with(root.infoBuilder) {
                toolchainInfoBuilder.jvmBuilder.jvmTarget = argMap.mandatorySingle("--kotlin_jvm_target")

                argMap.optionalSingle("--kt-plugins")?.let { input ->
                    plugins = CompilerPlugins.newBuilder().let {
                        jsonFormat.merge(input, it)
                        it.build()
                    }
                }
            }

            root.build()
        }
}

