# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Angus Mail (Jakarta Mail) resolves its IMAP/SMTP/POP3 Session providers by class name
# from META-INF/javamail.providers, META-INF/javamail.default.providers and
# META-INF/services/jakarta.mail.Provider, and resolves MIME content-handlers the same
# way from META-INF/mailcap. Neither jar ships consumer rules for this, so without a
# keep rule R8 shrinking/renaming leaves those string-based lookups pointing at classes
# that no longer exist, and Session.getStore("imap") / getTransport("smtp") throws
# NoSuchProviderException at runtime — this app talks to a real IMAP/SMTP server, so it
# is directly on that path.
-keep class jakarta.mail.** { *; }
-keep class org.eclipse.angus.mail.** { *; }

# Angus Activation (Jakarta Activation) resolves its MailcapRegistryProvider and
# MimeTypeRegistryProvider SPI implementations the same way, via
# META-INF/services/jakarta.activation.spi.*, with no consumer rules of its own.
-keep class jakarta.activation.** { *; }
-keep class org.eclipse.angus.activation.** { *; }

# Below: R8's default AGP config now treats "missing class" as a build-breaking error
# rather than a warning, so these need explicit -dontwarn. Each class is genuinely
# absent from Android's runtime/classpath, and each is on a code path this app never
# exercises — none of these are being made to silently fail; they were already unusable
# on Android before R8 was ever turned on.

# Tink (androidx.security.crypto's backing crypto engine, via EncryptedSharedPreferences
# in CredentialsStore.kt) references error-prone/javax.annotation markers that are
# @Retention(CLASS)/(SOURCE) only — present at Tink's compile time, never at runtime,
# and not shipped on Android's classpath.
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**

# Angus Activation's optional GraalVM native-image integration. This app runs on
# Android's runtime, never as a GraalVM native image, so the feature class is dead code
# here.
-dontwarn org.graalvm.nativeimage.**

# Angus Mail's optional java.awt-based image/gif and image/jpeg content-handlers (already
# commented out of the jar's own META-INF/mailcap: "can't support image types because
# java.awt.Toolkit doesn't work on servers") and its OAuth2/SASL auth path (SmtpMailSender,
# ImapMailFetcher and MailProbe only ever set plain SSL properties — see mail/*.kt — never
# mail.smtp.sasl.enable or an OAuth2 mechanism). Both paths reference java.awt.* /
# javax.security.sasl.* classes that don't exist on Android, so they were already
# unreachable at runtime before R8: nothing here is newly broken by shrinking.
-dontwarn java.awt.**
-dontwarn javax.security.auth.callback.**
-dontwarn javax.security.sasl.**