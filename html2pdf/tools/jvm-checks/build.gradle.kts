plugins {
    kotlin("jvm") version "1.9.22"
}

repositories { mavenCentral() }

/**
 * The archiver's REAL sources — not copies. Everything that is plain logic
 * (scanning, page extraction, EPUB / RAG / PDF-book building) is compiled
 * straight out of the Android module and exercised here on the JVM, so these
 * checks cannot drift away from the app they are supposed to guard.
 *
 * `src/stubs/kotlin` supplies just enough of the platform to make that
 * possible: android.net.Uri, a Context, the WebView cookie jar, an in-memory
 * Http, and aliases from pdfbox-android's package to the identical Apache
 * PDFBox API.
 */
val appSources = file("../../android/lj2pdf/app/src/main/java")

kotlin {
    sourceSets["main"].kotlin.apply {
        setSrcDirs(listOf(file("src/stubs/kotlin"), appSources))
        // Left out: files that genuinely need the Android framework (UI,
        // services, WebView rendering, SAF) and the four the stubs stand in for
        // (ConvertBus, Http, Logx, Translator).
        exclude(
            "com/drmd/lj2pdf/App.kt",
            "com/drmd/lj2pdf/ConvertBus.kt",
            "com/drmd/lj2pdf/ConvertService.kt",
            "com/drmd/lj2pdf/FacebookLoginActivity.kt",
            "com/drmd/lj2pdf/Http.kt",
            "com/drmd/lj2pdf/Logx.kt",
            "com/drmd/lj2pdf/MainActivity.kt",
            "com/drmd/lj2pdf/PdfRenderer2.kt",
            "com/drmd/lj2pdf/SiteProfile.kt",
            "com/drmd/lj2pdf/TelegramImporter.kt",
            "com/drmd/lj2pdf/Translator.kt",
            "com/drmd/lj2pdf/WebViewPdfRenderer.kt"
        )
    }
}

dependencies {
    // Same libraries the app uses, in their JVM packaging.
    implementation("org.jsoup:jsoup:1.17.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.json:json:20240303")
    implementation("org.apache.pdfbox:pdfbox:2.0.31")
    testImplementation(kotlin("test"))
}

// Same bytecode level as the Android module, whatever JDK runs Gradle.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = "17"
}

tasks.test {
    useJUnitPlatform()
    testLogging { events("passed", "failed", "skipped") }
}
