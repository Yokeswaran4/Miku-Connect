const menuToggle = document.querySelector(".menu-toggle");
const siteNav = document.querySelector("#site-nav");

menuToggle.addEventListener("click", () => {
  const isExpanded = menuToggle.getAttribute("aria-expanded") === "true";
  menuToggle.setAttribute("aria-expanded", String(!isExpanded));
  siteNav.classList.toggle("is-open", !isExpanded);
});

siteNav.addEventListener("click", (event) => {
  if (event.target.closest("a")) {
    menuToggle.setAttribute("aria-expanded", "false");
    siteNav.classList.remove("is-open");
  }
});

document.querySelector("#year").textContent = new Date().getFullYear();

const releaseUrl = "https://github.com/Yokeswaran4/Y-Connect/releases/latest";
const downloadPatterns = {
  android: /^Y-Connect.*\.apk$/i,
  linux: /^Y-Connect.*\.appimage$/i,
  windows: /^Y-Connect.*setup.*\.exe$/i,
};

fetch("https://api.github.com/repos/Yokeswaran4/Y-Connect/releases/latest", {
  headers: { Accept: "application/vnd.github+json" },
})
  .then((response) => {
    if (!response.ok) throw new Error(`Release lookup failed: ${response.status}`);
    return response.json();
  })
  .then((release) => {
    for (const [platform, pattern] of Object.entries(downloadPatterns)) {
      const asset = release.assets?.find(({ name }) => pattern.test(name));
      const link = document.querySelector(`[data-download="${platform}"]`);
      if (asset && link) link.href = asset.browser_download_url;
    }
  })
  .catch(() => {
    for (const link of document.querySelectorAll("[data-download]")) {
      link.href = releaseUrl;
    }
  });
