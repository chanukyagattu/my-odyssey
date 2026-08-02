plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    jvmToolchain(17)

    jvm()
    iosArm64()
    iosSimulatorArm64()
    iosX64()

    sourceSets {
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
