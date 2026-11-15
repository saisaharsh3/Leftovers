// Leftovers for desktop: a build of its own, apart from the phone app's, so the Android and F-Droid builds never
// see it. It reads the phone app's shared code (budget maths, backups, imports) straight from ../app.
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
    versionCatalogs {
        create("libs") { from(files("../gradle/libs.versions.toml")) }
    }
}
rootProject.name = "LeftoversDesktop"
