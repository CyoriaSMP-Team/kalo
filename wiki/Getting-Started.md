# 🚀 Getting Started

## Requirements

- Java 21+ (for Paper 1.21.4) or Java 25 (for 26.2)
- Paper or Folia server 1.21.4 or later

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
