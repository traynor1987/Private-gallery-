pluginManagement {
  repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories { google(); mavenCentral(); maven { url = uri("https://raw.githubusercontent.com/guardianproject/gpmaven/5fdb8dbcfe98d1b7e027e2f99bc086709870b005"); content { includeGroup("info.guardianproject") } } }
}
rootProject.name = "Private Gallery"
include(":app")
