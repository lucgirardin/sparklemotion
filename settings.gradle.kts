pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://dl.bintray.com/kotlin/kotlin-eap")
    }
}

//pluginManagement {
//    resolutionStrategy {
//        eachPlugin {
//            if (requested.id.id == "kotlin-multiplatform") {
//                useModule("org.jetbrains.kotlin:kotlin-gradle-plugin:${requested.version}")
//            }
//            if (requested.id.id == "kotlinx-serialization") {
//                useModule("org.jetbrains.kotlin:kotlin-serialization:${requested.version}")
//            }
//        }
//    }
//}
rootProject.name = "sparklemotion"

enableFeaturePreview("GRADLE_METADATA")
