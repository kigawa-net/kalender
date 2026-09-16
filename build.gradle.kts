// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
}

// このサンドボックス環境ではsettings.gradle.ktsのdependencyResolutionManagementが
// プロジェクト側で追加されたリポジトリ(nodejs.org)からのダウンロードをブロックするため、
// 既にインストール済みのシステムNode.jsを使うよう設定する。
rootProject.plugins.withType<org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsRootPlugin> {
    rootProject.extensions.configure<org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsEnvSpec> {
        download.set(false)
        command.set("node")
    }
}
rootProject.plugins.withType<org.jetbrains.kotlin.gradle.targets.wasm.yarn.WasmYarnPlugin> {
    rootProject.extensions.configure<org.jetbrains.kotlin.gradle.targets.wasm.yarn.WasmYarnRootEnvSpec> {
        download.set(false)
        command.set("yarn")
    }
}