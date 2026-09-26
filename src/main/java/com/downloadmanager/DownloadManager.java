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

        executorService =
                Executors.newFixedThreadPool(
                        MAX_DOWNLOADS
                );
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

        executorService.shutdown();
    }
}