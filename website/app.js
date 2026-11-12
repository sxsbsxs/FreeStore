(() => {
  const grid = document.querySelector("#app-grid");
  const search = document.querySelector("#search-input");
  const categoriesNode = document.querySelector("#category-list");
  const countNode = document.querySelector("#catalog-count");
  const sortNode = document.querySelector("#sort-select");
  const dialog = document.querySelector("#app-dialog");
  const dialogContent = document.querySelector("#dialog-content");
  const closeDialog = document.querySelector("#dialog-close");

  let apps = [];
  let activeCategory = "全部";

  const safeUrl = (value) => {
    try {
      const url = new URL(value);
      return ["https:", "http:"].includes(url.protocol) ? url.href : "";
    } catch {
      return "";
    }
  };

  const formatSize = (size) => {
    const bytes = Number(size || 0);
    if (!bytes) return "大小未知";
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(0) + " KB";
    return (bytes / (1024 * 1024)).toFixed(1) + " MB";
  };

  const formatDate = (value) => {
    if (!value) return "日期未知";
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? "日期未知" :
      new Intl.DateTimeFormat("zh-CN", { year: "numeric", month: "short", day: "numeric" }).format(date);
  };

  function makeIcon(app, className = "app-icon") {
    const holder = document.createElement("div");
    holder.className = className;
    const url = safeUrl(app.icon_url);
    if (url) {
      const image = document.createElement("img");
      image.src = url;
      image.alt = "";
      image.loading = "lazy";
      image.referrerPolicy = "no-referrer";
      image.className = "app-icon";
      image.onerror = () => { image.replaceWith(document.createTextNode((app.name || "A").slice(0, 1).toUpperCase())); };
      return image;
    }
    holder.textContent = (app.name || "A").slice(0, 1).toUpperCase();
    return holder;
  }

  function makeCard(app) {
    const card = document.createElement("article");
    card.className = "app-card";
    card.tabIndex = 0;
    card.setAttribute("role", "button");
    card.setAttribute("aria-label", "查看 " + app.name);

    const top = document.createElement("div");
    top.className = "app-card-top";
    top.append(makeIcon(app));

    const identity = document.createElement("div");
    identity.style.minWidth = "0";
    const name = document.createElement("div");
    name.className = "app-name";
    name.textContent = app.name || "未命名应用";
    const category = document.createElement("div");
    category.className = "app-category";
    category.textContent = app.category || "其他";
    identity.append(name, category);
    top.append(identity);

    if (app.featured) {
      const featured = document.createElement("span");
      featured.className = "featured-tag";
      featured.textContent = "精选";
      top.append(featured);
    }

    const description = document.createElement("p");
    description.className = "app-description";
    description.textContent = app.description || "暂无应用介绍";

    const meta = document.createElement("div");
    meta.className = "app-meta";
    const version = document.createElement("span");
    version.textContent = app.version || "未知版本";
    const size = document.createElement("span");
    size.textContent = formatSize(app.size_bytes);
    meta.append(version, size);

    card.append(top, description, meta);
    const open = () => showDetails(app);
    card.addEventListener("click", open);
    card.addEventListener("keydown", (event) => {
      if (event.key === "Enter" || event.key === " ") {
        event.preventDefault();
        open();
      }
    });
    return card;
  }

  function showDetails(app) {
    dialogContent.replaceChildren();
    const head = document.createElement("div");
    head.className = "dialog-head";
    head.append(makeIcon(app));
    const identity = document.createElement("div");
    const title = document.createElement("h3");
    title.textContent = app.name || "未命名应用";
    const subtitle = document.createElement("div");
    subtitle.className = "dialog-sub";
    subtitle.textContent = [app.category, app.version, formatSize(app.size_bytes)].filter(Boolean).join(" · ");
    identity.append(title, subtitle);
    head.append(identity);

    const description = document.createElement("p");
    description.className = "dialog-description";
    description.textContent = app.description || "暂无应用介绍";

    const source = document.createElement("p");
    source.className = "dialog-sub";
    source.textContent = "GitHub 来源：" + (app.repository || "未知");
    const updated = document.createElement("p");
    updated.className = "dialog-sub";
    updated.textContent = "最近发布：" + formatDate(app.updated_at);

    const actions = document.createElement("div");
    actions.className = "dialog-actions";
    const downloadUrl = safeUrl(app.download_url);
    if (downloadUrl) {
      const download = document.createElement("a");
      download.className = "action-button";
      download.href = downloadUrl;
      download.rel = "noopener noreferrer";
      download.textContent = "下载 APK ↗";
      actions.append(download);
    }
    const repoUrl = safeUrl(app.repository_url);
    if (repoUrl) {
      const repository = document.createElement("a");
      repository.className = "action-button secondary";
      repository.href = repoUrl;
      repository.target = "_blank";
      repository.rel = "noopener noreferrer";
      repository.textContent = "查看项目";
      actions.append(repository);
    }

    dialogContent.append(head, description, source, updated);
    if (app.changelog) {
      const logTitle = document.createElement("p");
      logTitle.className = "dialog-sub";
      logTitle.textContent = "版本更新日志";
      const log = document.createElement("div");
      log.className = "dialog-changelog";
      log.textContent = app.changelog;
      dialogContent.append(logTitle, log);
    }
    dialogContent.append(actions);
    if (typeof dialog.showModal === "function") dialog.showModal();
    else dialog.setAttribute("open", "");
  }

  function renderCategories() {
    const categories = ["全部", ...new Set(apps.map((app) => app.category).filter(Boolean))].sort((a, b) => {
      if (a === "全部") return -1;
      if (b === "全部") return 1;
      return a.localeCompare(b, "zh-CN");
    });
    categoriesNode.replaceChildren();
    categories.forEach((category) => {
      const button = document.createElement("button");
      button.className = "category-chip" + (category === activeCategory ? " active" : "");
      button.type = "button";
      button.textContent = category;
      button.addEventListener("click", () => {
        activeCategory = category;
        renderCategories();
        renderApps();
      });
      categoriesNode.append(button);
    });
  }

  function renderApps() {
    const keyword = search.value.trim().toLocaleLowerCase();
    const filtered = apps.filter((app) => {
      const content = [app.name, app.description, app.repository, app.category].join(" ").toLocaleLowerCase();
      return (activeCategory === "全部" || app.category === activeCategory) && content.includes(keyword);
    });

    const sort = sortNode.value;
    filtered.sort((a, b) => {
      if (sort === "name") return (a.name || "").localeCompare(b.name || "", "zh-CN");
      if (sort === "recent") return new Date(b.updated_at || 0) - new Date(a.updated_at || 0);
      if (sort === "downloads") return Number(b.download_count || 0) - Number(a.download_count || 0);
      return Number(Boolean(b.featured)) - Number(Boolean(a.featured)) ||
        (a.name || "").localeCompare(b.name || "", "zh-CN");
    });

    countNode.textContent = filtered.length + " 个应用";
    grid.replaceChildren();
    if (!filtered.length) {
      const empty = document.createElement("div");
      empty.className = "empty-state";
      const heading = document.createElement("strong");
      heading.textContent = apps.length ? "没有找到匹配的应用" : "应用市场刚刚准备好";
      const message = document.createElement("p");
      message.textContent = apps.length
        ? "换个关键词试试，或者选择其他分类。"
        : "目前还没有收录应用。维护者将 GitHub 仓库加入 catalog/sources.json，并在该仓库的 Release 中上传 APK 后，应用会自动出现在这里。";
      empty.append(heading, message);
      grid.append(empty);
      return;
    }
    filtered.forEach((app) => grid.append(makeCard(app)));
  }

  search.addEventListener("input", renderApps);
  sortNode.addEventListener("change", renderApps);
  closeDialog.addEventListener("click", () => dialog.close());
  dialog.addEventListener("click", (event) => {
    if (event.target === dialog) dialog.close();
  });
  document.addEventListener("keydown", (event) => {
    if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === "k") {
      event.preventDefault();
      search.focus();
    }
  });

  fetch("./catalog/apps.json", { cache: "no-store" })
    .then((response) => {
      if (!response.ok) throw new Error("HTTP " + response.status);
      return response.json();
    })
    .then((catalog) => {
      if (!catalog || catalog.schema_version !== 1 || !Array.isArray(catalog.apps)) {
        throw new Error("应用目录格式不正确");
      }
      apps = catalog.apps.filter((app) => app && app.name && safeUrl(app.download_url));
      renderCategories();
      renderApps();
    })
    .catch((error) => {
      countNode.textContent = "目录暂不可用";
      grid.innerHTML = '<div class="empty-state"><strong>无法加载应用目录</strong><p>请检查网络，稍后刷新页面重试。\n' +
        String(error.message).replace(/[&<>"']/g, "") + "</p></div>";
    });
})();
