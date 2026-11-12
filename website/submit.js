(() => {
  const openButton = document.querySelector("#open-submit");
  const dialog = document.querySelector("#submit-dialog");
  const closeButton = document.querySelector("#submit-close");
  const form = document.querySelector("#submit-form");
  const fileInput = document.querySelector("#apk-file");
  const fileName = document.querySelector("#apk-file-name");
  const status = document.querySelector("#submit-status");
  const MAX_FILE_BYTES = 25 * 1000 * 1000;

  if (!openButton || !dialog || !form) return;

  const showDialog = () => {
    status.textContent = "";
    if (typeof dialog.showModal === "function") dialog.showModal();
    else dialog.setAttribute("open", "");
  };

  openButton.addEventListener("click", (event) => {
    event.preventDefault();
    showDialog();
  });
  closeButton.addEventListener("click", () => dialog.close());
  dialog.addEventListener("click", (event) => {
    if (event.target === dialog) dialog.close();
  });

  if (new URLSearchParams(window.location.search).get("submit") === "1") showDialog();

  fileInput.addEventListener("change", () => {
    const file = fileInput.files && fileInput.files[0];
    fileName.textContent = file
      ? file.name + " · " + (file.size / (1024 * 1024)).toFixed(1) + " MB"
      : "尚未选择 APK 文件";
  });

  function block(key, value) {
    return "<!-- FREESTORE:" + key + ":START -->\n" + value +
      "\n<!-- FREESTORE:" + key + ":END -->";
  }

  form.addEventListener("submit", (event) => {
    event.preventDefault();
    status.textContent = "";

    const data = new FormData(form);
    const name = String(data.get("name") || "").trim();
    const version = String(data.get("version") || "").trim();
    const packageName = String(data.get("package") || "").trim();
    const category = String(data.get("category") || "").trim();
    const description = String(data.get("description") || "").trim();
    const projectUrl = String(data.get("project-url") || "").trim() || "无";
    const file = fileInput.files && fileInput.files[0];

    if (!file || !file.name.toLowerCase().endsWith(".apk")) {
      status.textContent = "请选择一个 .apk 文件。";
      return;
    }
    if (file.size <= 0 || file.size > MAX_FILE_BYTES) {
      status.textContent = "APK 必须大于 0，且不能超过 25 MB。";
      return;
    }
    if (!/^[0-9A-Za-z][0-9A-Za-z.+_-]{0,63}$/.test(version)) {
      status.textContent = "版本号格式不正确，例如 1.0.0。";
      return;
    }
    if (!/^[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z][A-Za-z0-9_]*){1,8}$/.test(packageName)) {
      status.textContent = "Android 包名格式不正确，例如 com.example.app。";
      return;
    }
    if (!name || name.length > 80 || name.includes("\n") || !description || description.length > 1000) {
      status.textContent = "请填写应用名称和简介；名称最多 80 字，简介最多 1000 字。";
      return;
    }
    if (projectUrl !== "无") {
      try {
        const parsed = new URL(projectUrl);
        if (parsed.protocol !== "https:") throw new Error("https required");
      } catch {
        status.textContent = "项目主页必须是有效的 HTTPS 地址。";
        return;
      }
    }

    const payload = [
      "<!-- FreeStore community APK submission v1 -->",
      "### 应用名称",
      block("name", name),
      "### 应用版本",
      block("version", version),
      "### Android 包名",
      block("package", packageName),
      "### 分类",
      block("category", category),
      "### 应用简介",
      block("description", description.replace(/<!--/g, "&lt;!--").replace(/-->/g, "--&gt;")),
      "### 项目主页（可选）",
      block("project_url", projectUrl),
      "### APK 附件",
      "请附加刚刚下载的 freestore-apk-submission.zip。它的内容是原始 APK，请勿解压或再次压缩。",
      ""
    ].join("\n");

    // APK is already a ZIP container. Only the downloaded copy's name changes.
    const zipBlob = new Blob([file], { type: "application/zip" });
    const objectUrl = URL.createObjectURL(zipBlob);
    const download = document.createElement("a");
    download.href = objectUrl;
    download.download = "freestore-apk-submission.zip";
    document.body.append(download);
    download.click();
    download.remove();
    window.setTimeout(() => URL.revokeObjectURL(objectUrl), 60_000);

    const issueUrl = new URL("https://github.com/sxsbsxs/FreeStore/issues/new");
    issueUrl.searchParams.set("title", "[FreeStore APK] " + name + " " + version);
    issueUrl.searchParams.set("body", payload);

    status.textContent = "APK 副本已下载。请在新打开的 GitHub 投稿页面中，点击附件按钮，选择 freestore-apk-submission.zip，然后提交 Issue。需要登录 GitHub。";
    const issueTab = window.open(issueUrl.toString(), "_blank");
    if (issueTab) {
      issueTab.opener = null;
      issueTab.focus();
    } else {
      const fallback = document.createElement("a");
      fallback.href = issueUrl.toString();
      fallback.target = "_blank";
      fallback.rel = "noopener noreferrer";
      fallback.className = "submit-fallback";
      fallback.textContent = "弹窗被浏览器拦截，点击这里继续 GitHub 投稿";
      status.append(document.createElement("br"), fallback);
    }
  });
})();
