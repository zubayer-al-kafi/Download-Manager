chrome.storage.local.get("latestDownload", (result) => {

    const download = result.latestDownload;

    if (!download) {
        return;
    }

    document.getElementById("message").textContent =
        "Latest download:";

    document.getElementById("downloadId").textContent =
        download.id;

    document.getElementById("downloadUrl").textContent =
        download.url;

    document.getElementById("downloadFilename").textContent =
        download.filename;
});