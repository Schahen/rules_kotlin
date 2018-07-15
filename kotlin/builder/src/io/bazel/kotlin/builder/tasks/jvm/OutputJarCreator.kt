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
package io.bazel.kotlin.builder.tasks.jvm

import io.bazel.kotlin.model.KotlinModel
import io.bazel.kotlin.builder.utils.bazelRuleKind
import io.bazel.kotlin.builder.utils.jars.JarCreator
import java.nio.file.Paths
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class OutputJarCreator @Inject constructor() {
    fun createOutputJar(command: KotlinModel.JvmCompilationTask) {
        JarCreator(
            path = Paths.get(command.outputs.jar),
            normalize = true,
            verbose = false
        ).also {
            it.addDirectory(Paths.get(command.directories.classes))
            it.addDirectory(Paths.get(command.directories.generatedClasses))
            it.setJarOwner(command.info.label, command.info.bazelRuleKind)
            it.execute()
        }
    }
}
