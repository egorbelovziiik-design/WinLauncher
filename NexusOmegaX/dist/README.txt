NEXUS CLIENT OMEGA X - build output folder
==========================================

NexusInjector.exe          Windows launcher / installer (built, ready to run).

NexusOmegaX-1.16.5.jar     Fabric mod jar for Minecraft 1.16.5
NexusOmegaX-1.21.4.jar     Fabric mod jar for Minecraft 1.21.4
NexusOmegaX-1.21.11.jar    Fabric mod jar for Minecraft 1.21.11

The three jars are produced by "gradle build" inside each adapter - Fabric Loom
needs internet access to download Minecraft and the mappings, which the build
sandbox did not have. Run from the project root:

    ./build-all.sh

or build one version:

    cd adapters/nexus1214 && gradle build

then copy build/libs/NexusOmegaX-*.jar into this folder (build-all.sh does it
automatically) or leave it next to NexusInjector.exe - the launcher finds it
either way. No jar is fabricated for a version that cannot be built: if the jar
is missing, VERIFY INSTALL reports "client not installed" for that version.
