# R8 shrinks and optimizes release builds; the libraries (Compose, kotlinx.serialization) bring their own rules.

# Keep class and method names and line numbers: testers install whatever build the "latest" release
# holds, and that build's mapping file is gone after the next CI run, so crash logs must read as is.
-dontobfuscate
-keepattributes SourceFile,LineNumberTable

# Shizuku starts the shell service by its class name in a process of its own.
-keep class io.github.alstarknight.slidex.ShellService { <init>(); }
