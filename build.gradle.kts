import com.android.build.gradle.BaseExtension
import com.lagradost.cloudstream3.gradle.CloudstreamExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

buildscript {
    repositories {
        google()
        mavenCentral()
        // Shitpack repo which contains our tools and dependencies
        maven("https://jitpack.io")
    }

    dependencies {
        classpath("com.android.tools.build:gradle:8.7.3")
        // Cloudstream gradle plugin which makes everything work and builds plugins
        classpath("com.github.recloudstream:gradle:master-SNAPSHOT")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.0")
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

fun Project.cloudstream(configuration: CloudstreamExtension.() -> Unit) = extensions.getByName<CloudstreamExtension>("cloudstream").configuration()

fun Project.android(configuration: BaseExtension.() -> Unit) = extensions.getByName<BaseExtension>("android").configuration()

// Library modules that should NOT have the cloudstream plugin applied
val libraryModules = setOf("CommonLib")

subprojects {
    apply(plugin = "com.android.library")
    apply(plugin = "kotlin-android")

    // Only apply cloudstream plugin to actual plugin modules, not library modules
    if (project.name !in libraryModules) {
        apply(plugin = "com.lagradost.cloudstream3.gradle")

        cloudstream {
            // when running through github workflow, GITHUB_REPOSITORY should contain current repository name
            // you can modify it to use other git hosting services, like gitlab
            setRepo(System.getenv("GITHUB_REPOSITORY") ?: "https://github.com/user/repo")
        }
    }

    android {
        namespace = "com.rjbiermann"

        defaultConfig {
            minSdk = 21
            compileSdkVersion(35)
            targetSdk = 35
        }

        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_1_8
            targetCompatibility = JavaVersion.VERSION_1_8
        }

        tasks.withType<KotlinJvmCompile> {
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_1_8) // Required
                freeCompilerArgs.addAll(
                    "-Xno-call-assertions",
                    "-Xno-param-assertions",
                    "-Xno-receiver-assertions"
                )
            }
        }

        // Allow android.util.Log to return defaults instead of throwing in unit tests
        testOptions {
            unitTests.isReturnDefaultValues = true
        }
    }

    dependencies {
        val implementation by configurations
        val testImplementation by configurations

        // Cloudstream stubs only for plugin modules (cloudstream configuration is created by cloudstream plugin)
        if (project.name !in libraryModules) {
            val cloudstream by configurations
            // Stubs for all cloudstream classes
            cloudstream("com.lagradost:cloudstream3:pre-release")
        }

        // These dependencies can include any of those which are added by the app,
        // but you don't need to include any of them if you don't need them.
        // https://github.com/recloudstream/cloudstream/blob/master/app/build.gradle.kts
        implementation(kotlin("stdlib")) // Adds Standard Kotlin Features
        implementation("com.github.Blatzar:NiceHttp:0.4.13") // HTTP Lib
        implementation("org.jsoup:jsoup:1.18.3") // HTML Parser

        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
        implementation("me.xdrop:fuzzywuzzy:1.4.0")

        // Test dependencies
        testImplementation("junit:junit:4.13.2")
        testImplementation("org.jetbrains.kotlin:kotlin-test:2.3.0")
        testImplementation("io.mockk:mockk:1.13.8")
    }

    // Auto-configure CommonLib class bundling for plugins that depend on it
    // This is needed because the cloudstream gradle plugin only DEX-compiles
    // classes from the current module, not from project dependencies
    afterEvaluate {
        if (project.name !in libraryModules) {
            val hasCommonLibDep = configurations.findByName("implementation")
                ?.dependencies?.any {
                    it is org.gradle.api.artifacts.ProjectDependency && it.name == "CommonLib"
                } == true

            if (hasCommonLibDep) {
                tasks.register<Copy>("copyCommonLibClasses") {
                    dependsOn("compileDebugKotlin", ":CommonLib:compileDebugKotlin")
                    from(project(":CommonLib").layout.buildDirectory.dir("tmp/kotlin-classes/debug"))
                    into(layout.buildDirectory.dir("tmp/kotlin-classes/debug"))
                }
                tasks.named("compileDex") {
                    dependsOn("copyCommonLibClasses")
                }
            }
        }
    }
}

task<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}