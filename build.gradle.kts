// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
}

// Kotlin 2.2.x では WasmNodeJsEnvSpec が各プロジェクト(:app 含む)に個別登録され、
// webpack タスクは :app 側の spec を参照する。rootProject だけ設定しても効かないため
// 全プロジェクトで download=false / システムの node を使うよう設定する。
allprojects {
    plugins.withType<org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsPlugin> {
        extensions.configure<org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsEnvSpec> {
            download.set(false)
            command.set("node")
        }
    }
}
rootProject.plugins.withType<org.jetbrains.kotlin.gradle.targets.wasm.yarn.WasmYarnPlugin> {
    rootProject.extensions.configure<org.jetbrains.kotlin.gradle.targets.wasm.yarn.WasmYarnRootEnvSpec> {
        download.set(false)
        command.set("yarn")
    }
}
