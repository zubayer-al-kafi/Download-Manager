package com.downloadmanager.interfaces;

import com.downloadmanager.controller.DownloadItemController;
import java.nio.file.Path;

/**
 * Advanced OOP: Interface defining the contract for download engines.
 * Any class handling downloads must implement these core behaviors.
 */
public interface DownloadEngine {

    void startDownload(DownloadItemController controller, String fileName, String url, Path downloadLocation);

}