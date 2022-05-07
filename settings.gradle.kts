rootProject.name = "label-print-agent"

gradle.projectsLoaded {
    allprojects {
        extra["kotlinVersion"] = "1.6.20"
        extra["coroutinesVersion"] = "1.6.1"
        extra["okHttpVersion"] = "4.9.3"
        extra["okioVersion"] = "2.2.2"
        extra["cliktVersion"] = "2.8.0"
        extra["gsonVersion"] = "2.9.0"
        extra["logbackVersion"] = "1.2.11"
        extra["kotlinLoggingVersion"] = "2.1.21"
        extra["slf4jVersion"] = "1.7.36"
        extra["koinVersion"] = "3.1.6"
        extra["hopliteVersion"] = "1.4.16"
    }
}