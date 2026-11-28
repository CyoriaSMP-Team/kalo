plugins {
    id("paper-conventions")
}

// Kalo answering the part of the CraftEngine API (net.momirealms.craftengine.bukkit.api) that uses
// only plain Bukkit types. Embedded in the Kalo jar; CraftEngine is a stand-in for Kalo (it defines
// the items, blocks and furniture other plugins consume), so Kalo impersonates it. Plain Java.
//
// The rest of that API traffics in CraftEngine's own types (Key, BukkitItemDefinition,
// ImmutableBlockState, BukkitFurniture, CompoundTag...), which other plugins then call methods on;
// serving those would mean reimplementing CraftEngine's core, so it is deliberately not attempted.
dependencies {
    compileOnly(project(":api"))
    compileOnly(project(":compat-core"))
    testImplementation(project(":api"))
    testImplementation(project(":compat-core"))
}
