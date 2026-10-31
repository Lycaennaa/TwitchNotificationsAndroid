# Third-party notices

This file records the third-party packages declared or resolved for this Android app. Gradle resolves transitive versions from the dependency graph; `just dependencies` shows the release runtime graph and `just test-dependencies` shows the unit-test graph. Recheck this notice when dependency declarations or resolved versions change.

## Runtime and build packages

| Package | Version and use | Origin and repository path | License and upstream notice |
| --- | --- | --- | --- |
| AndroidX WorkManager (`androidx.work:work-runtime`) | 2.10.0; declared in `app/build.gradle.kts`, runtime | [AndroidX source](https://github.com/androidx/androidx), `work/work-runtime` | Apache-2.0; [upstream license](https://www.apache.org/licenses/LICENSE-2.0.txt). Copyright notice: The Android Open Source Project. |
| AndroidX transitive modules | Resolved release components and versions are listed below; all are transitive from WorkManager. | [AndroidX source](https://github.com/androidx/androidx) | Apache-2.0; [full terms](https://www.apache.org/licenses/LICENSE-2.0.txt). Copyright: The Android Open Source Project. |
| Kotlin standard library (`org.jetbrains.kotlin:kotlin-stdlib`, `kotlin-stdlib-common`, `kotlin-stdlib-jdk7`, `kotlin-stdlib-jdk8`) | `stdlib` and `stdlib-common` 2.2.10; JDK7/JDK8 adapters 1.8.22; runtime | [`LICENSE.txt` for Kotlin 2.2.10](https://github.com/JetBrains/kotlin/blob/v2.2.10/license/LICENSE.txt) | Apache-2.0; [full terms](https://www.apache.org/licenses/LICENSE-2.0.txt). Copyright: `Copyright 2000-2020 JetBrains s.r.o. and Kotlin Programming Language`. |
| Kotlin Coroutines (`org.jetbrains.kotlinx:kotlinx-coroutines-android`, `kotlinx-coroutines-core`, `kotlinx-coroutines-core-jvm`) | 1.7.3; transitive runtime | [`LICENSE.txt` for 1.7.3](https://github.com/Kotlin/kotlinx.coroutines/blob/1.7.3/LICENSE.txt) | Apache-2.0; [full terms](https://www.apache.org/licenses/LICENSE-2.0.txt). Copyright: `Copyright 2000-2020 JetBrains s.r.o. and Kotlin Programming Language`. |
| JetBrains annotations (`org.jetbrains:annotations`) | 23.0.0; resolved runtime | [`LICENSE.txt` for 23.0.0](https://github.com/JetBrains/java-annotations/blob/23.0.0/LICENSE.txt) | Apache-2.0; [full terms](https://www.apache.org/licenses/LICENSE-2.0.txt). Copyright: `Copyright 2000-2016 JetBrains s.r.o.` |
| Guava ListenableFuture (`com.google.guava:listenablefuture`) | 1.0; transitive runtime | [Artifact metadata](https://repo.maven.apache.org/maven2/com/google/guava/listenablefuture/1.0/listenablefuture-1.0.pom), [Guava source](https://github.com/google/guava) | Apache-2.0; [full terms](https://www.apache.org/licenses/LICENSE-2.0.txt). Copyright and attribution are given in the upstream package/source notices. |
| Android Gradle Plugin (`com.android.application`) | 9.1.0; build-time plugin declared in root `build.gradle.kts` | [Android tools source](https://android.googlesource.com/platform/tools/base/) | Apache-2.0; [full terms](https://www.apache.org/licenses/LICENSE-2.0.txt). Copyright: The Android Open Source Project. |
| Gradle Wrapper | 9.3.1; build-time distribution configured in `gradle/wrapper/gradle-wrapper.properties`; wrapper files are under `gradle/wrapper/`, `gradlew`, and `gradlew.bat` | [`LICENSE` for Gradle 9.3.1](https://github.com/gradle/gradle/blob/v9.3.1/LICENSE) | Apache-2.0; [full terms](https://www.apache.org/licenses/LICENSE-2.0.txt). Copyright and attribution are given in the upstream distribution. |

## Test-only packages

| Package | Version and use | Origin and repository path | License and upstream notice |
| --- | --- | --- | --- |
| JUnit (`junit:junit`) | 4.13.2; declared in `app/build.gradle.kts`, unit tests only | [`LICENSE-junit.txt` for JUnit 4.13.2](https://github.com/junit-team/junit4/blob/r4.13.2/LICENSE-junit.txt) | Eclipse Public License 1.0; [full terms](https://www.eclipse.org/legal/epl-v10.html). The upstream notice is quoted below. |
| Hamcrest Core (`org.hamcrest:hamcrest-core`) | 1.3; transitive test dependency of JUnit | [`LICENSE` for Hamcrest 1.3](https://github.com/hamcrest/JavaHamcrest/blob/1.3/LICENSE) | New BSD License (BSD-3-Clause); [full terms](https://opensource.org/license/bsd-3-clause/). Copyright: `Copyright 2000-2024, www.hamcrest.org`. |

## Resolved AndroidX transitive versions

The release runtime graph currently resolves these AndroidX artifacts through WorkManager. They use the Apache-2.0 license and AndroidX upstream notice listed in the table above.

- `androidx.annotation:annotation` and `androidx.annotation:annotation-jvm` 1.6.0; `androidx.annotation:annotation-experimental` 1.4.1.
- `androidx.concurrent:concurrent-futures` and `androidx.concurrent:concurrent-futures-ktx` 1.1.0.
- `androidx.core:core` 1.12.0; `androidx.collection:collection` and `androidx.interpolator:interpolator` 1.0.0.
- `androidx.lifecycle:lifecycle-common`, `lifecycle-livedata`, `lifecycle-livedata-core`, `lifecycle-runtime`, and `lifecycle-service` 2.6.2; `androidx.arch.core:core-common` and `core-runtime` 2.2.0.
- `androidx.room:room-common`, `room-ktx`, and `room-runtime` 2.6.1; `androidx.sqlite:sqlite` and `sqlite-framework` 2.4.0.
- `androidx.profileinstaller:profileinstaller` 1.3.0; `androidx.startup:startup-runtime` 1.1.1; `androidx.tracing:tracing` and `tracing-ktx` 1.2.0; `androidx.versionedparcelable:versionedparcelable` 1.1.1.

Use `just dependencies` to inspect the current resolved graph when changing dependencies.

## Verbatim upstream notice excerpts

The following notice text is reproduced from upstream license files. The full license texts are linked in the tables above.

### Apache License 2.0

The Apache-licensed dependencies use this upstream notice:

> Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License.
>
> You may obtain a copy of the License at
> http://www.apache.org/licenses/LICENSE-2.0

Kotlin 2.2.10 and kotlinx.coroutines 1.7.3 also include this copyright notice in their upstream license files:

> Copyright 2000-2020 JetBrains s.r.o. and Kotlin Programming Language

The JetBrains annotations 23.0.0 license file contains:

> Copyright 2000-2016 JetBrains s.r.o.

### JUnit 4.13.2

The upstream `LICENSE-junit.txt` begins:

> THE ACCOMPANYING PROGRAM IS PROVIDED UNDER THE TERMS OF THIS ECLIPSE PUBLIC
> LICENSE ("AGREEMENT"). ANY USE, REPRODUCTION OR DISTRIBUTION OF THE PROGRAM
> CONSTITUTES RECIPIENT'S ACCEPTANCE OF THIS AGREEMENT.

### Hamcrest Core 1.3

The upstream license notice includes:

> Copyright 2000-2024, www.hamcrest.org

The Commons Clause text in the root `LICENSE` explicitly limits that condition to TwitchLiveAlerts-owned material and states third-party material remains under its applicable license. The Apache-2.0, EPL-1.0, and BSD-3-Clause notices above apply to their respective third-party packages; this document does not extend the Commons Clause to them.
