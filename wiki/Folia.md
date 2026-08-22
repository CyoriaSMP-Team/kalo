# ⚡ Folia Support

Kalo fully supports Folia servers.

## What is Folia?

Folia is Paper's fork with region-based multithreading. It allows servers to handle more players and entities by processing different regions in parallel.

## Compatibility

Kalo is verified on Folia 26.2. All features work:

- ✅ Custom items
- ✅ Custom blocks
- ✅ Furniture
- ✅ Armor
- ✅ Recipes
- ✅ Bedrock support

## Setup

No special setup required. Just install Kalo like on Paper:

```bash
cp Kalo-*.jar /path/to/folia/plugins/
```

## Testing

Run Folia test server:

```bash
./gradlew runFolia
```

## Notes

- Region-based scheduling is handled automatically
- Block state allocation is thread-safe
- Pack generation runs asynchronously

---

## See Also

- [[Getting-Started]] — Installation guide
- [[Bedrock]] — Bedrock support

---

## 📜 Footer

**Kalo** — Open Custom Content Engine for Minecraft

[GitHub](https://github.com/CyoriaSMP-Team/kalo) • [Issues](https://github.com/CyoriaSMP-Team/kalo/issues) • [Discord](https://discord.gg/kalo) (coming soon)

> **Version:** 0.1.0 • **License:** MIT • **Java:** 21+ / 25

*Build once. Play everywhere.*
