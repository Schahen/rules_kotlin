load("@io_bazel_rules_kotlin//kotlin:kotlin.bzl", "kt_jvm_library")


java_import(
    name = "android-sdk",
    jars = ["@androidsdk//:platforms/android-27/android.jar"],
    neverlink = 1
)

android_library(
  name = "activities_base",
  custom_package = "com.google.bazel.example.android.activities",
  manifest = "src/main/java/com/google/bazel/example/android/activities/AndroidManifest.xml",
  resource_files = glob(["src/main/java/com/google/bazel/example/android/activities/res/**"]),
)


kt_jvm_library(
    name = "activities_kt",
    srcs = ["src/main/java/com/google/bazel/example/android/activities/MainActivity.kt"],
    deps = [
        "android-sdk",
        ":activities_base",

   ]
)

android_binary(
    name = "android",
    custom_package = "com.google.bazel.example.android",
    manifest = "src/main/java/com/google/bazel/example/android/AndroidManifest.xml",
    resource_files = glob(["src/main/java/com/google/bazel/example/android/res/**"]),
    deps = [
      ":activities_base",
      ":activities_kt"
    ],
)