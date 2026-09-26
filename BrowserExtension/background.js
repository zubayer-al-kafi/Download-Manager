console.log("Download Manager Extension loaded.");

// ============================================================
// NORMAL DOWNLOAD DETECTION
// ============================================================

chrome.downloads.onCreated.addListener(function (downloadItem) {

    console.log("New download detected!");

    console.log("Original URL:", downloadItem.url);
    console.log("Final URL:", downloadItem.finalUrl);
    console.log("Filename:", downloadItem.filename);

    const downloadData = {
        id: downloadItem.id,
        url: downloadItem.finalUrl || downloadItem.url,
        filename: downloadItem.filename,
        type: "normal"
    };

    fetch(
        "http://localhost:8765/download",
        {
            method: "POST",
            headers: {
                "Content-Type": "application/json"
            },
            body: JSON.stringify(downloadData)
        }
    )
        .then(function (response) {
            return response.text().then(function (result) {
                console.log("Java response:", result);

                if (!response.ok) {
                    console.error("Download Manager rejected:", result);
                    return;
                }

                console.log("Download Manager accepted the download.");

                // Show notification
                chrome.notifications.create(
                    "download-" + downloadItem.id,
                    {
                        type: "basic",
                        iconUrl: "icons/icon128.png",
                        title: "Download Manager",
                        message: "Downloading " + downloadItem.filename
                    },
                    function (notificationId) {
                        if (chrome.runtime.lastError) {
                            console.error("Notification error:", chrome.runtime.lastError.message);
                        } else {
                            console.log("Notification shown:", notificationId);
                        }
                    }
                );

                // Cancel Chrome's original download
                chrome.downloads.cancel(
                    downloadItem.id,
                    function () {
                        if (chrome.runtime.lastError) {
                            console.error("Cancel error:", chrome.runtime.lastError.message);
                        } else {
                            console.log("Chrome download cancelled.");
                        }
                    }
                );
            });
        })
        .catch(function (error) {
            console.error("Could not connect to Download Manager:", error);
            // IMPORTANT:
            // Chrome download is NOT cancelled if the connection fails.
        });

    chrome.storage.local.set({
        latestDownload: downloadData
    });
});


// ============================================================
// CREATE CONTEXT MENU
// ============================================================

chrome.runtime.onInstalled.addListener(function () {

    chrome.contextMenus.create({
        id: "download-youtube",
        title: "Download with Download Manager",
        contexts: [
            "page",
            "link",
            "image",
            "video"
        ]
    });

    console.log("Download context menu created.");
});


// ============================================================
// YOUTUBE RIGHT-CLICK
// ============================================================

chrome.contextMenus.onClicked.addListener(function (info, tab) {

    // 1. Check if it's the correct menu item first
    if (info.menuItemId !== "download-youtube") {
        return;
    }

    // 2. Use the specific link the user right-clicked on.
    // If they didn't click a link, fall back to the current page URL.
    const youtubeUrl = info.linkUrl || (tab && tab.url);

    if (!youtubeUrl) {
        console.error("Could not determine YouTube page URL.");
        return;
    }

    console.log("YouTube download requested.");
    console.log("YouTube URL:", youtubeUrl);

    const youtubeData = {

        // id: Date.now(), <-- EITA MUCHE DIN
        id: Math.floor(Math.random() * 1000000), // <-- EI LINE TA DIN

        url: youtubeUrl,

        filename: "YouTube Video",

        type: "youtube"
    };

    fetch(
        "http://localhost:8765/download",
        {
            method: "POST",
            headers: {
                "Content-Type": "application/json"
            },
            body: JSON.stringify(youtubeData)
        }
    )
        .then(function (response) {
            return response.text().then(function (result) {
                console.log("Java response:", result);
                console.log("HTTP status:", response.status);

                if (!response.ok) {
                    console.error("YouTube download rejected.");
                    return;
                }

                console.log("YouTube download accepted.");

                // Notification
                chrome.notifications.create(
                    "youtube-" + youtubeData.id,
                    {
                        type: "basic",
                        iconUrl: "icons/icon128.png",
                        title: "Download Manager",
                        message: "YouTube video added to download queue."
                    },
                    function (notificationId) {
                        if (chrome.runtime.lastError) {
                            console.error("Notification error:", chrome.runtime.lastError.message);
                        } else {
                            console.log("Notification shown:", notificationId);
                        }
                    }
                );
            });
        })
        .catch(function (error) {
            console.error("Could not connect to Download Manager:", error);
        });
});