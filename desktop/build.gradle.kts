import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.4.20"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20"
    id("org.jetbrains.compose") version "1.12.2"
}

group = "com.leftovers"
version = "1.0.0"

/**
 * The phone app's code that has nothing of Android in it, used here as it is: the money maths, budgets, forecasts,
 * backups and their merging, imports, and how payments and paybacks are told apart. Copied in at build time, so
 * there's one copy of it in git, and a fix in the phone app is a fix here.
 */
val shared = listOf(
    "data/Models.kt", "data/AccountModels.kt", "data/Buckets.kt", "data/BudgetPlan.kt", "data/DailyBudget.kt",
    "data/DebtModels.kt", "data/DeletedModels.kt", "data/DuplicatePayments.kt", "data/Forecast.kt", "data/NativeParts.kt",
    "data/PlanningModels.kt", "data/SubscriptionMatch.kt", "data/Trends.kt",
    "util/BackupCrypto.kt", "util/BackupMerge.kt", "util/CsvImporter.kt", "util/JsonExport.kt", "util/MerchantMemory.kt",
    "util/MoneyText.kt", "util/Payback.kt", "util/SyncRemoved.kt", "util/CategoryGuess.kt", "util/Formatting.kt",
    "util/SmsParser.kt", "util/BankSenders.kt", "util/BankBalance.kt", "util/EmailParser.kt", "util/AutoAdd.kt",
    "ui/icons/Lucide.kt", "ui/icons/IconBuilder.kt", "ui/icons/CategoryIcons.kt", "util/ProductPrice.kt",
)
val sharedSrc = layout.buildDirectory.dir("shared-src")
val copyShared by tasks.registering(Sync::class) {
    from("../app/src/main/java/com/leftovers/app") { include(shared) }
    into(sharedSrc.map { it.dir("com/leftovers/app") })
}
kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21) }
    sourceSets.main { kotlin.srcDir(sharedSrc) }
}
java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}
tasks.named("compileKotlin") { dependsOn(copyShared) }

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
    // The shared models carry Room's annotations; on the desktop they're just annotations.
    implementation("androidx.room:room-common:${libs.versions.room.get()}")
    implementation(libs.org.json)
    testImplementation(kotlin("test"))
    testImplementation(libs.junit)
}

compose.desktop {
    application {
        mainClass = "com.leftovers.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "Leftovers"
            packageVersion = "1.0.0"
            description = "Know what's left"
            vendor = "Leftovers"
            windows {
                menuGroup = "Leftovers"
                upgradeUuid = "6f2a9c1e-4b7d-4f8a-9e3c-2d5b8a1f7c40"
                shortcut = true
                dirChooser = true
            }
        }
    }
}

/** Draws pages off screen to PNG files, to check the design without opening a window (see Snapshots.kt). */
tasks.register<JavaExec>("snapshots") {
    dependsOn("testClasses")
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.leftovers.desktop.Snapshots")
    args(
        (findProperty("out") ?: "build/snapshots").toString(),
        (findProperty("pages") ?: "").toString(),
        (findProperty("theme") ?: "LIGHT").toString(),
        (findProperty("glass") ?: "false").toString(),
        (findProperty("welcome") ?: "false").toString(),
    )
    jvmArgs("-Djava.awt.headless=true")
}
