package fish.payara.starter.resources;

import static fish.payara.starter.resources.ApplicationConfiguration.ARTIFACT_ID;
import static fish.payara.starter.resources.ApplicationConfiguration.GROUP_ID;
import static fish.payara.starter.resources.ApplicationConfiguration.JAKARTA_EE_VERSION;
import static fish.payara.starter.resources.ApplicationConfiguration.JAVA_VERSION;
import static fish.payara.starter.resources.ApplicationConfiguration.PACKAGE;
import static fish.payara.starter.resources.ApplicationConfiguration.PAYARA_VERSION;
import static fish.payara.starter.resources.ApplicationConfiguration.PLATFORM;
import static fish.payara.starter.resources.ApplicationConfiguration.PROFILE;
import static fish.payara.starter.resources.ApplicationConfiguration.VERSION;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.maven.cli.MavenCli;

@Path("starter")
public class ApplicationGeneratorResource {

    private static final Logger LOGGER = Logger.getLogger(ApplicationGeneratorResource.class.getName());
    private static final String WORKING_DIR_PREFIX = "payara-starter-";
    private static final String ARCHETYPE_GROUP_ID = "fish.payara.starter";
    private static final String ARCHETYPE_ARTIFACT_ID = "payara-starter-archetype";
    private static final String ARCHETYPE_VERSION = "LATEST";
    private static final String MAVEN_ARCHETYPE_CMD = "archetype:generate";
    private static final String ZIP_EXTENSION = ".zip";

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response generate(ApplicationConfiguration appProperties) {
        File applicationDir = null;
        try {
            File workingDirectory = Files.createTempDirectory(WORKING_DIR_PREFIX).toFile();
            workingDirectory.deleteOnExit();
            LOGGER.log(Level.INFO, "Executing Maven Archetype from working directory: {0}", new Object[]{workingDirectory.getAbsolutePath()});
            Properties properties = buildMavenProperties(appProperties);
            invokeMavenArchetype(ARCHETYPE_GROUP_ID, ARCHETYPE_ARTIFACT_ID, ARCHETYPE_VERSION,
                    properties, workingDirectory);

            LOGGER.info("Creating a compressed application bundle.");
            applicationDir = new File(workingDirectory, appProperties.getArtifactId());
            File zipFile = zipDirectory(applicationDir, workingDirectory);
            return buildResponse(zipFile, appProperties.getArtifactId());
        } catch (IOException ie) {
            throw new RuntimeException("Failed to generate application.", ie);
        } finally {
            if (applicationDir != null) {
                deleteDirectory(applicationDir);
            }
        }
    }

    // Utility method to delete a directory and its contents
    private void deleteDirectory(File directory) {
        if (directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    deleteDirectory(file);
                }
            }
        }
        if(!directory.delete()) {
            LOGGER.log(Level.WARNING, "Failed to delete directory: {0}", directory);
        }
    }

    private Properties buildMavenProperties(ApplicationConfiguration appProperties) {
        Properties properties = new Properties();
        properties.put(GROUP_ID, appProperties.getGroupId());
        properties.put(ARTIFACT_ID, appProperties.getArtifactId());
        properties.put(VERSION, appProperties.getVersion());
        properties.put(PACKAGE, appProperties.getPackageName());
        properties.put(JAKARTA_EE_VERSION, appProperties.getJakartaEEVersion());
        properties.put(PROFILE, appProperties.getProfile());
        properties.put(JAVA_VERSION, appProperties.getJavaVersion());
        properties.put(PLATFORM, appProperties.getPlatform());
        properties.put(PAYARA_VERSION, appProperties.getPayaraVersion());
        return properties;
    }

    public void invokeMavenArchetype(String archetypeGroupId, String archetypeArtifactId,
            String archetypeVersion, Properties properties, File workingDirectory) {
        System.setProperty(MavenCli.MULTIMODULE_PROJECT_DIRECTORY, workingDirectory.getAbsolutePath());

        List<String> options = new LinkedList<>();
        options.addAll(Arrays.asList(new String[]{MAVEN_ARCHETYPE_CMD, "-DinteractiveMode=false",
            "-DaskForDefaultPropertyValues=false", "-DarchetypeGroupId=" + archetypeGroupId,
            "-DarchetypeArtifactId=" + archetypeArtifactId, "-DarchetypeVersion=" + archetypeVersion}));
        properties.forEach((k, v) -> options.add("-D" + k + "=" + v));

        int result = new MavenCli().doMain(options.toArray(String[]::new), workingDirectory.getAbsolutePath(),
                System.out, System.err);

        if (result != 0) {
            throw new RuntimeException("Failed to invoke Maven Archetype.");
        }
    }

    private File zipDirectory(File directory, File destinaton) throws IOException {
        File zipFile = new File(destinaton, directory.getName() + ZIP_EXTENSION);
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            zipFile(directory, directory.getName(), zos);
        }
        return zipFile;
    }

    private void zipFile(File fileToZip, String fileName, ZipOutputStream zipOut) throws IOException {
        if (fileToZip.isDirectory()) {
            if (fileName.endsWith("/")) {
                zipOut.putNextEntry(new ZipEntry(fileName));
                zipOut.closeEntry();
            } else {
                zipOut.putNextEntry(new ZipEntry(fileName + "/"));
                zipOut.closeEntry();
            }

            File[] children = fileToZip.listFiles();
            for (File childFile : children) {
                zipFile(childFile, fileName + "/" + childFile.getName(), zipOut);
            }
        } else {
            try (FileInputStream fis = new FileInputStream(fileToZip)) {
                ZipEntry zipEntry = new ZipEntry(fileName);
                zipOut.putNextEntry(zipEntry);

                byte[] bytes = new byte[1024];
                int length;
                while ((length = fis.read(bytes)) >= 0) {
                    zipOut.write(bytes, 0, length);
                }
            }
        }
    }

    private Response buildResponse(File zipFile, String artifactId) throws FileNotFoundException {
        StreamingOutput streamingOutput = output -> {
            try (FileInputStream fis = new FileInputStream(zipFile)) {
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    output.write(buffer, 0, bytesRead);
                }
            } catch (IOException e) {
                throw new RuntimeException("Failed to stream zip file.", e);
            } finally {
                zipFile.delete();
                zipFile.getParentFile().delete();
            }
        };

        return Response.ok(streamingOutput)
                .header("Content-Disposition", "attachment; filename=\"" + artifactId + ".zip\"")
                .header("Content-Type", "application/octet-stream")
                .build();
    }

}