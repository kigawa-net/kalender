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
    }
}

rootProject.name = "kalender"
include(":app")
