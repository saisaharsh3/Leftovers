# Room and Compose ship their own consumer rules.

# Jakarta Mail (email payment detection) finds its IMAP store and helpers by class name, from the
# META-INF/javamail.* and META-INF/services files in its jars, so they must keep their names.
-keep class org.eclipse.angus.mail.imap.** { *; }
-keep class org.eclipse.angus.mail.iap.** { *; }
-keep class org.eclipse.angus.mail.handlers.** { *; }
-keep class org.eclipse.angus.mail.util.** { *; }
-keep class org.eclipse.angus.activation.** { *; }
-keep class jakarta.mail.** { *; }
-keep class jakarta.activation.** { *; }
-dontwarn java.awt.**
-dontwarn javax.security.sasl.**
-dontwarn org.graalvm.**
-dontwarn javax.security.auth.callback.**
