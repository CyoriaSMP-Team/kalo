# 🚀 Getting Started

## Requirements

- Java 21+ to run the Java 21-bytecode jar on Paper 1.21.4; Minecraft 26.x itself requires Java 25
- Paper or Folia 1.21.4 or later compiled against the 1.21.4 API; runtime verification currently covers 26.2 and 1.21.11

## Installation

### 1. Download Kalo

Download the latest JAR from [GitHub Releases](https://github.com/CyoriaSMP-Team/kalo/releases).

### 2. Install Plugin

Copy `Kalo-*.jar` to your server's `plugins/` folder:

```bash
cp Kalo-*.jar /path/to/server/plugins/
```

### 3. Start Server

Start or restart your server. Kalo will create its configuration files:

```
plugins/Kalo/
├── config.yml
├── packs/
│   └── example/
│       ├── pack.yml
│       ├── configs/
│       └── assets/
└── generated.zip
```

### 4. Create Your First Content Pack

See [[First-Pack]] for a step-by-step guide.

### 5. Reload

```bash
/kalo reload
```

Your custom item is now in-game!

---

## Next Steps

- [[First-Pack]] — Create your first content pack
- [[Items]] — Learn about custom items
- [[Furniture]] — Create rotatable furniture
- [[Configuration]] — All config options

---

## 📜 Footer

**Kalo** — Open Custom Content Engine for Minecraft

[GitHub](https://github.com/CyoriaSMP-Team/kalo) • [Issues](https://github.com/CyoriaSMP-Team/kalo/issues) • [Discord](https://discord.gg/kalo) (coming soon)

> **Version:** 0.2.1-prealpha.16 • **License:** MIT • **Java:** 21 bytecode / 25 for 26.x runtime

*Build once. Play everywhere.*
