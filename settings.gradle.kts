pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    // Kotlin/Wasm・Kotlin/JSツールチェインがNode.js配布物解決のために、プロジェクト側で
    // 自動的にリポジトリを追加する(FAIL_ON_PROJECT_REPOSと非互換のため緩和が必要)。
    // 実際のダウンロードはbuild.gradle.ktsでdownload=falseにして無効化し、
    // システムにインストール済みのNode.jsを使う。
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
        // Kotlin/WasmのBinaryenツールはGitHub Releasesでのみ配布されている(Ivyパターン)。
        // PREFER_SETTINGSではプロジェクト側リポジトリが無視されるため、ここで宣言する必要がある。
        ivy {
            name = "binaryenDistributions"
            url = uri("https://github.com/WebAssembly/binaryen/releases/download")
            patternLayout {
                artifact("version_[revision]/binaryen-version_[revision]-[classifier].[ext]")
            }
            metadataSources {
                artifact()
            }
            content {
                includeModule("com.github.webassembly", "binaryen")
            }
        }
    }
}

rootProject.name = "kalender"
include(":app")
include(":server")
