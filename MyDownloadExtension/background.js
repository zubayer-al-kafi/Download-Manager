// ==========================================
// 1. RIGHT-CLICK MENU (For YouTube & direct links)
// ==========================================
chrome.runtime.onInstalled.addListener(() => {
  chrome.contextMenus.create({
    id: "send-to-java",
    title: "Download with My Manager",
    contexts: ["link", "video", "audio"]
  });
});

chrome.contextMenus.onClicked.addListener((info, tab) => {
  if (info.menuItemId === "send-to-java") {
    sendToJava(info.linkUrl || info.srcUrl || info.pageUrl);
  }
});

// ==========================================
// 2. AUTOMATIC DOWNLOAD INTERCEPTOR (For Buttons)
// ==========================================
chrome.downloads.onCreated.addListener((downloadItem) => {
  
  // Ignore internal browser "blob" files (Java can't download these anyway)
  if (downloadItem.url.startsWith("blob:")) return;

  // Ping the Java app to see if it is currently running
  fetch("http://localhost:8001/download", { method: "OPTIONS" })
    .then(() => {
      // The Java app is ALIVE! 
      
      // 1. Cancel the default Chrome download
      chrome.downloads.cancel(downloadItem.id);
      
      // 2. Send the final, resolved URL to our Java app
      sendToJava(downloadItem.url);
    })
    .catch(() => {
      // The Java app is CLOSED. 
      // Do nothing! Let Chrome download the file normally.
      console.log("Java Download Manager is not running. Using Chrome's default downloader.");
    });
});

// ==========================================
// 3. HELPER FUNCTION
// ==========================================
function sendToJava(urlToDownload) {
  fetch("http://localhost:8001/download", {
    method: "POST",
    body: urlToDownload
  })
  .then(response => console.log("Sent successfully to Java!"))
  .catch(error => console.error("Error sending to Java:", error));
}