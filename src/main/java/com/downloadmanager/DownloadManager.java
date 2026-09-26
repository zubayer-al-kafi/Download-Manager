package com.downloadmanager;

import com.downloadmanager.controller.DownloadItemController;
import com.downloadmanager.interfaces.DownloadEngine;

import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DownloadManager implements DownloadEngine {

    private static final int MAX_DOWNLOADS = 3;

    private final ExecutorService executorService;

    public DownloadManager() {
        // Advanced Concurrency: Use a ThreadFactory to create Daemon threads
        // This ensures background downloads don't keep the app alive as a ghost
        // process if the user forcefully closes the main window.
        executorService = Executors.newFixedThreadPool(MAX_DOWNLOADS, runnable -> {
            Thread thread = new Thread(runnable);
            thread.setDaemon(true);
            thread.setName("Download-Worker-Thread");
            return thread;
        });
    }

    @Override
    public void startDownload(
            DownloadItemController controller,
            String fileName,
            String url,
            Path downloadFolder) {

        executorService.submit(() -> {
            controller.download(
                    fileName,
                    url,
                    downloadFolder
            );
        });
    }

    public void shutdown() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdownNow(); // Forcefully stops active threads on exit
        }
    }
}