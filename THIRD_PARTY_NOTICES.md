# Third-party notices

Leftovers bundles the following assets. Library dependencies (AndroidX, Kotlin, Haze) are
fetched by Gradle and keep their own licences (Apache 2.0). JUnit (EPL 1.0) and org.json (public domain) are used
only to run the tests and are not part of the app.

## Manrope typeface

`app/src/main/res/font/manrope.ttf`, from [googlefonts/manrope](https://github.com/googlefonts/manrope).
Copyright 2018 The Manrope Project Authors. Licensed under the SIL Open Font License 1.1;
the full text is in [third_party/manrope/OFL.txt](third_party/manrope/OFL.txt).

## Jakarta Mail (Eclipse Angus Mail)

Used for email payment detection (IMAP over TLS): `org.eclipse.angus:angus-mail`, `jakarta.mail-api`,
`angus-activation` and `jakarta.activation-api`, fetched by Gradle. Copyright the Eclipse Foundation and
contributors. Licensed under the Eclipse Distribution License 1.0 (BSD-3-Clause), the Eclipse Public License 2.0,
or GPL 2.0 with the Classpath Exception; Leftovers uses them under the BSD-3-Clause terms. See
[eclipse-ee4j/angus-mail](https://github.com/eclipse-ee4j/angus-mail). GreenMail (Apache 2.0) is used only in tests.

## Lucide icons

Icon outlines in `app/src/main/java/com/leftovers/app/ui/icons/Lucide.kt` were converted from
[Lucide](https://lucide.dev). The app icon (`app/src/main/res/drawable/ic_launcher_foreground.xml`, and
`docs/icon.svg` in the README) is Lucide's "wallet" icon on a plain background. Licensed under the ISC License; the full text is in
[third_party/lucide/LICENSE](third_party/lucide/LICENSE).
