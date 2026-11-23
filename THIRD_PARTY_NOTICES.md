# Third-party notices

IQMemo original source and public documentation are licensed under MIT; third-party components retain their original licenses. The MIT license does not relicense third-party code or service access.

## Redistributed build tooling

`gradlew` and `gradle/wrapper/gradle-wrapper.jar` originate from Gradle and are governed by Apache License 2.0. Preserve the copyright/license header in gradlew and [Gradle's license](licenses/Gradle-LICENSE). Upstream: https://github.com/gradle/gradle/tree/v8.7.0 .

## Dependencies resolved by Gradle

The source distribution does not include their binary implementations. Coordinates and versions are in gradle/libs.versions.toml and app/build.gradle.kts. For binary distribution, review each resolved component, transitive dependency and applicable notices.

| Components | Upstream license / source |
| --- | --- |
| AndroidX, including Room and AndroidX Test | Apache-2.0 · https://android.googlesource.com/platform/frameworks/support/ |
| Material Components Android | Apache-2.0 · https://github.com/material-components/material-components-android |
| Kotlin, Kotlin Coroutines | Apache-2.0 · https://github.com/JetBrains/kotlin · https://github.com/Kotlin/kotlinx.coroutines |
| OkHttp | Apache-2.0 · https://github.com/square/okhttp |
| JUnit 4.13.2 (test only) | EPL-1.0 · https://github.com/junit-team/junit4 |
| org.json:json:20240303 (test only) | JSON License, including its good-not-evil clause · https://github.com/stleary/JSON-java/tree/20240303 |

org.json is a test dependency and is not included as a runtime dependency in the APK. Its JSON License has an additional restriction and is not described here as MIT or Apache-2.0. Android's platform org.json APIs are a separate implementation.

Xfyun IAT has separate service terms. This repository does not include third-party service credentials or an account. Workbench binding and synchronization have been removed from the public edition.
