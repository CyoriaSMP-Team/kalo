/**
 * Keep the Kalo website in sync with the latest published stable GitHub release.
 * The public GitHub API is the source of truth; version.json is an offline fallback.
 * No deploy or manual version bump is needed for the visible version badge.
 */
(() => {
  "use strict";

  const GITHUB_RELEASE = "https://api.github.com/repos/CyoriaSMP-Team/kalo/releases/latest";
  const REFRESH_MS = 15 * 60 * 1000;
  const versionText = document.getElementById("version-text");
  const badge = document.getElementById("version-badge");
  const docsBadges = [...document.querySelectorAll("[data-kalo-version]")];
  const fallbackHolder = document.querySelector("[data-release-fallback]");
  if (!versionText && !docsBadges.length) return;

  const fallbackPath = fallbackHolder?.getAttribute("data-release-fallback") || "version.json";
  const validTag = /^v[0-9]+\.[0-9]+\.[0-9]+(?:-[0-9A-Za-z.-]+)?$/;

  function render(tag, isFallback) {
    if (!validTag.test(tag)) throw new Error("Invalid release tag");
    if (versionText) {
      versionText.textContent = isFallback
        ? `${tag} — last known release`
        : `${tag} — Now Available`;
    }
    if (badge) badge.style.display = "inline-flex";
    for (const element of docsBadges) {
      element.textContent = isFallback ? `${tag} (cached)` : tag;
    }
  }

  async function refreshRelease() {
    try {
      const response = await fetch(GITHUB_RELEASE, {
        headers: { Accept: "application/vnd.github+json" },
        cache: "no-cache"
      });
      if (!response.ok) throw new Error(`GitHub returned ${response.status}`);
      const release = await response.json();
      if (release.draft || release.prerelease || typeof release.tag_name !== "string") {
        throw new Error("Not a published stable release");
      }
      render(release.tag_name, false);
    } catch (error) {
      // GitHub can be rate-limited or offline: retain a usable, labelled fallback.
      try {
        const response = await fetch(fallbackPath, { cache: "no-cache" });
        if (!response.ok) throw new Error("Fallback unavailable");
        const data = await response.json();
        render(data.name, true);
      } catch {
        if (versionText) versionText.textContent = "See latest releases on GitHub";
        for (const element of docsBadges) element.textContent = "See releases";
      }
    }
  }

  refreshRelease();
  setInterval(refreshRelease, REFRESH_MS);
})();
