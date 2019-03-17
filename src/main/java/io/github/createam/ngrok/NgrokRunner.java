package io.github.createam.ngrok;

import io.github.createam.ngrok.data.Tunnel;
import io.github.createam.ngrok.exception.NgrokDownloadException;
import io.github.createam.ngrok.exception.NgrokStartupException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class NgrokRunner {

    private static final Logger log = LoggerFactory.getLogger(NgrokRunner.class);

    @Value("${server.port:8080}")
    private String port;

    @Value("${ngrok.directory:}")
    private String ngrokDirectory;

    @Value("${ngrok.waitForStartup.millis:3000}")
    private long waitForStartupMillis;

    @Autowired
    private NgrokApiClient ngrokApiClient;

    @Autowired
    private NgrokDownloader ngrokDownloader;

    @EventListener(ApplicationReadyEvent.class)
    public void run() throws NgrokDownloadException, NgrokStartupException, IOException {
        if (ngrokIsNotRunning()) {

            if (needToDownloadNgrok()) {

                String downloadedFilePath = ngrokDownloader.downloadNgrokTo(getNgrokDirectoryOrDefault());

                FileExtractUtils.extractArchive(downloadedFilePath, getNgrokDirectoryOrDefault());

                deleteArchive(downloadedFilePath);
            }

            startupNgrok();
        }

        logTunnelsDetails();
    }

    private void deleteArchive(String downloadedFilePath) throws IOException {
        Files.delete(Paths.get(downloadedFilePath));
    }

    private boolean needToDownloadNgrok() {
        return !Files.isExecutable(Paths.get(getNgrokExecutablePath()));
    }

    private boolean ngrokIsNotRunning() {
        return !ngrokApiClient.isResponding();
    }

    private void logTunnelsDetails() {
        List<Tunnel> tunnels = ngrokApiClient.fetchTunnels();

        tunnels.forEach(t -> log.info("Remote url ({}) -> {}", t.getProto(), t.getPublicUrl()));
    }

    private String getNgrokExecutablePath() {
        String executable = SystemUtils.IS_OS_WINDOWS ? "ngrok.exe" : "ngrok";

        return getNgrokDirectoryOrDefault() + File.separator + executable;
    }

    private void startupNgrok() {
        log.info("Starting ngrok...");

        try {
            Runtime.getRuntime().exec(getNgrokExecutablePath() + " http " + port);

            Thread.sleep(waitForStartupMillis);
        } catch (IOException | InterruptedException e) {
            log.error("Failed to run ngrok!", e);

            throw new NgrokStartupException("Failed to startup ngrok!", e);
        }
    }

    private String getNgrokDirectoryOrDefault() {

        return StringUtils.isNotBlank(ngrokDirectory) ? ngrokDirectory : getDefaultNgrokDirectory();
    }

    private String getDefaultNgrokDirectory() {
        return FileUtils.getUserDirectory().getPath()
                .concat(File.separator)
                .concat(".ngrok2")
                .concat(File.separator);
    }
}