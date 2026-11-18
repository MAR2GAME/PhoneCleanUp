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
        maven { url = uri("https://www.jitpack.io") }
        maven { url = uri("https://maven.singular.net/") }
        maven { url = uri("https://cboost.jfrog.io/artifactory/chartboost-ads/") }
        maven { url = uri("https://artifact.bytedance.com/repository/pangle") }
        maven { url = uri("https://android-sdk.is.com/") }
        maven { url = uri("https://dl-maven-android.mintegral.com/repository/mbridge_android_sdk_oversea") }
        maven { url = uri("https://artifact.bytedance.com/repository/pangle/") }

    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    val mar2PackagesUser = sequenceOf(
        providers.gradleProperty("gpr.user").orNull,
        providers.environmentVariable("GITHUB_ACTOR").orNull,
    ).mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }.firstOrNull()
    val mar2PackagesToken = sequenceOf(
        providers.gradleProperty("gpr.key").orNull,
        providers.environmentVariable("GITHUB_TOKEN").orNull,
        providers.environmentVariable("GH_TOKEN").orNull,
    ).mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }.firstOrNull()
    repositories {
        google()
        mavenCentral()
        maven {
            name = "Mar2SdkGitHubPackages"
            url = uri("https://maven.pkg.github.com/mar2game/mar2sdk")
            credentials {
                username = mar2PackagesUser ?: "x-access-token"
                password = mar2PackagesToken ?: throw GradleException(
                    "Mar2SDK GitHub Packages credentials are missing. Set gpr.user/gpr.key in " +
                        "the user Gradle properties file or GITHUB_ACTOR/GITHUB_TOKEN (or GH_TOKEN). " +
                        "The token needs read:packages and access to MAR2GAME/Mar2SDK."
                )
            }
            content {
                includeGroup("com.mar2game.mar2sdk")
            }
        }
        maven { url = uri("https://www.jitpack.io") }
        maven { url = uri("https://repository.liferay.com/nexus/content/repositories/public/") }
        maven { url = uri("https://maven.singular.net/") }
        maven { url = uri("https://cboost.jfrog.io/artifactory/chartboost-ads/") }
        maven { url = uri("https://artifact.bytedance.com/repository/pangle") }
        maven { url = uri("https://android-sdk.is.com/") }
        maven { url = uri("https://dl-maven-android.mintegral.com/repository/mbridge_android_sdk_oversea") }
        maven { url = uri("https://artifact.bytedance.com/repository/pangle/") }
        maven { url = uri("https://artifacts.applovin.com/android") }
        maven {
            url = uri("https://jfrog.anythinktech.com/artifactory/overseas_sdk")
            content { includeGroup("com.anythink.sdk") }
        }
    }
}
rootProject.name = "PhoneCleanUp"
include(":app")
 
