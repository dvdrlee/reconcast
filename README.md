# ReconCast

Mac application that allows you to screencast your Recon HUD to your Mac.

**[Download ReconCast](https://github.com/dvdrlee/reconcast/releases)**

Build Instructions
--------

Build Jar

    gradlew jar

Run the Jar

    java -XstartOnFirstThread -jar build/libs/ReconCast.jar

Create Mac App Package

    gradlew createApp

Create dmg

    gradlew createDmg
    
Forked from https://code.google.com/p/android-projector/
with ADB code borrowed from https://github.com/romannurik/AndroidDesignPreview
