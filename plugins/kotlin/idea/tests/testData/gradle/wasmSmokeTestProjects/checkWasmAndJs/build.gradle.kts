plugins {
    kotlin("multiplatform") version "{{kotlin_plugin_version}}"
}

group = "me.user"
version = "1.0-SNAPSHOT"

repositories {
    { { kts_kotlin_plugin_repositories } }
}

kotlin {
    js {
        binaries.executable()
        browser {}
    }
    wasm {
        binaries.executable()
        browser {}
    }

    sourceSets {
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }

        val wasmJsTest by getting {
            dependencies {
                implementation(kotlin("test-wasm-js"))
                implementation(kotlin("test-annotations-wasm-js"))
            }
        }
        val jsTest by getting {
            dependencies {
                implementation(kotlin("test-js"))
                implementation(kotlin("test-annotations-js"))
            }
        }

    }

}
