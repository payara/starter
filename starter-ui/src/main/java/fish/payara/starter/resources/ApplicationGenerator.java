/*
 *
 * Copyright (c) 2023-2024 Payara Foundation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://github.com/payara/Payara/blob/master/LICENSE.txt
 * See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * The Payara Foundation designates this particular file as subject to the "Classpath"
 * exception as provided by the Payara Foundation in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */
package fish.payara.starter.resources;

import fish.payara.starter.LangChainChatService;
import fish.payara.starter.application.domain.ERModel;
import fish.payara.starter.application.generator.CRUDAppGenerator;
import fish.payara.starter.application.generator.ERDiagramParser;
import static fish.payara.starter.resources.ApplicationConfiguration.ADD_PAYARA_API;
import static fish.payara.starter.resources.ApplicationConfiguration.ARTIFACT_ID;
import static fish.payara.starter.resources.ApplicationConfiguration.AUTH;
import static fish.payara.starter.resources.ApplicationConfiguration.BUILD;
import static fish.payara.starter.resources.ApplicationConfiguration.DOCKER;
import static fish.payara.starter.resources.ApplicationConfiguration.GROUP_ID;
import static fish.payara.starter.resources.ApplicationConfiguration.INCLUDE_TESTS;
import static fish.payara.starter.resources.ApplicationConfiguration.JAKARTA_EE_VERSION;
import static fish.payara.starter.resources.ApplicationConfiguration.JAVA_VERSION;
import static fish.payara.starter.resources.ApplicationConfiguration.MP_CONFIG;
import static fish.payara.starter.resources.ApplicationConfiguration.MP_FAULT_TOLERANCE;
import static fish.payara.starter.resources.ApplicationConfiguration.MP_METRICS;
import static fish.payara.starter.resources.ApplicationConfiguration.MP_OPEN_API;
import static fish.payara.starter.resources.ApplicationConfiguration.PACKAGE;
import static fish.payara.starter.resources.ApplicationConfiguration.PAYARA_CLOUD;
import static fish.payara.starter.resources.ApplicationConfiguration.PAYARA_VERSION;
import static fish.payara.starter.resources.ApplicationConfiguration.PLATFORM;
import static fish.payara.starter.resources.ApplicationConfiguration.PROFILE;
import static fish.payara.starter.resources.ApplicationConfiguration.REST_SUBPACKAGE;
import static fish.payara.starter.resources.ApplicationConfiguration.VERSION;
import jakarta.annotation.Resource;
import jakarta.enterprise.concurrent.ManagedExecutorDefinition;
import jakarta.enterprise.concurrent.ManagedExecutorService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.maven.cli.MavenCli;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 *
 * @author Gaurav Gupta
 */
@ManagedExecutorDefinition(
        name = "java:comp/DefaultManagedExecutorService",
        maxAsync = 5
)
@ApplicationScoped
public class ApplicationGenerator {

    private static final Logger LOGGER = Logger.getLogger(ApplicationGenerator.class.getName());
    private static final String WORKING_DIR_PREFIX = "payara-starter-";
    private static final String ARCHETYPE_GROUP_ID = "fish.payara.starter";
    private static final String ARCHETYPE_ARTIFACT_ID = "payara-starter-archetype";
    private static final String ARCHETYPE_VERSION = "1.0-SNAPSHOT";
    private static final String MAVEN_ARCHETYPE_CMD = "org.apache.maven.plugins:maven-archetype-plugin:3.2.1:generate";// "archetype:generate";
    private static final String ZIP_EXTENSION = ".zip";

    @Resource(name = "java:comp/DefaultManagedExecutorService")
    private ManagedExecutorService executorService;

    @Inject
    private LangChainChatService langChainChatService;

    public Future<File> generate(ApplicationConfiguration appProperties) {
        return executorService.submit(() -> {
            File applicationDir = null;
            try {
                File workingDirectory = Files.createTempDirectory(WORKING_DIR_PREFIX).toFile();
                workingDirectory.deleteOnExit();
                LOGGER.log(Level.INFO, "Executing Maven Archetype from working directory: {0}", new Object[]{workingDirectory.getAbsolutePath()});
                Properties properties = buildMavenProperties(appProperties);
                ERModel erModel = null;
                if (appProperties.getErDiagram() != null) {
                    ERDiagramParser parser = new ERDiagramParser();
                    erModel = parser.parse(appProperties.getErDiagram());
                    if (erModel != null && !erModel.getEntities().isEmpty()) {
                        properties.put(ApplicationConfiguration.ER_DIAGRAM, true);
                        properties.put(ApplicationConfiguration.ER_DIAGRAM_NAME, appProperties.getErDiagramName());
                    }
                }
                invokeMavenArchetype(ARCHETYPE_GROUP_ID, ARCHETYPE_ARTIFACT_ID, ARCHETYPE_VERSION,
                        properties, workingDirectory);

                LOGGER.info("Creating a compressed application bundle.");
                applicationDir = new File(workingDirectory, appProperties.getArtifactId());
                if (erModel != null && !erModel.getEntities().isEmpty()) {
                    Jsonb jsonb = JsonbBuilder.create();
                    String jsonString = jsonb.toJson(erModel);
                    LOGGER.log(Level.INFO, "Generating web components info from AI \n{0}", jsonString);
                    String outputJson = langChainChatService.addFronEndDetailsToERDiagram(jsonString);
                    if (outputJson != null && !outputJson.isEmpty()) {
                        String updatedJson = outputJson.strip().replaceAll("^```json|```$", "");
                        erModel = jsonb.fromJson(updatedJson, ERModel.class);
                    }
                    erModel.setImportPrefix("8".equals(appProperties.getJakartaEEVersion()) ? "javax" : "jakarta");
                    CRUDAppGenerator generator = new CRUDAppGenerator(erModel,
                            appProperties.getPackageName(),
                            appProperties.getJpaSubpackage(),
                            appProperties.getRepositorySubpackage(),
                            appProperties.getRestSubpackage()
                    );
                    try {
                        generator.generate(applicationDir,
                                appProperties.isGenerateJPA(),
                                appProperties.isGenerateRepository(),
                                appProperties.isGenerateRest(),
                                appProperties.isGenerateWeb());
                    } catch (Exception e) {
                        LOGGER.log(Level.SEVERE, "Error generating application: " + e.getMessage(), e);
                    }
                    Path diagramPath = Paths.get(applicationDir.getAbsolutePath(), appProperties.getErDiagramName().trim().replaceAll("\\s+", "_") + ".mmd");
                    Files.write(diagramPath, appProperties.getErDiagram().getBytes());
                }
                return zipDirectory(applicationDir, workingDirectory);
            } catch (Exception ie) {
                throw new RuntimeException("Failed to generate application.", ie);
            } finally {
                if (applicationDir != null) {
                    deleteDirectory(applicationDir);
                }
            }
        });
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
        if (!directory.delete()) {
            LOGGER.log(Level.WARNING, "Failed to delete directory: {0}", directory);
        }
    }

    private Properties buildMavenProperties(ApplicationConfiguration appProperties) {
        Properties properties = new Properties();
        properties.put(BUILD, appProperties.getBuild());
        properties.put(GROUP_ID, appProperties.getGroupId());
        properties.put(ARTIFACT_ID, appProperties.getArtifactId());
        properties.put(VERSION, appProperties.getVersion());
        properties.put(PACKAGE, appProperties.getPackageName());
        properties.put(JAKARTA_EE_VERSION, appProperties.getJakartaEEVersion());
        properties.put(PROFILE, appProperties.getProfile());
        properties.put(JAVA_VERSION, appProperties.getJavaVersion());
        properties.put(PLATFORM, appProperties.getPlatform());
        properties.put(PAYARA_VERSION, appProperties.getPayaraVersion());
        properties.put(INCLUDE_TESTS, appProperties.isIncludeTests());
        properties.put(ADD_PAYARA_API, appProperties.isAddPayaraApi());
        properties.put(DOCKER, appProperties.isDocker());
        properties.put(PAYARA_CLOUD, appProperties.isCloud());
        properties.put(MP_CONFIG, appProperties.isMpConfig());
        properties.put(MP_OPEN_API, appProperties.isMpOpenAPI());
        properties.put(MP_FAULT_TOLERANCE, appProperties.isMpFaultTolerance());
        properties.put(MP_METRICS, appProperties.isMpMetrics());
        properties.put(AUTH, appProperties.getAuth());
        properties.put(REST_SUBPACKAGE, appProperties.getRestSubpackage());
        return properties;
    }

    private void invokeMavenArchetype(String archetypeGroupId, String archetypeArtifactId,
            String archetypeVersion, Properties properties, File workingDirectory) {
        System.setProperty(MavenCli.MULTIMODULE_PROJECT_DIRECTORY, workingDirectory.getAbsolutePath());

        List<String> options = new LinkedList<>();
        options.addAll(Arrays.asList(new String[]{MAVEN_ARCHETYPE_CMD, "-DinteractiveMode=false",
            "-DaskForDefaultPropertyValues=false", "-DarchetypeGroupId=" + archetypeGroupId,
            "-DarchetypeArtifactId=" + archetypeArtifactId, "-DarchetypeVersion=" + archetypeVersion}));
        properties.forEach((k, v) -> options.add("-D" + k + "=" + (v.toString().contains(" ") ? "'" + v + "'" : v)));

        LOGGER.log(Level.INFO, "Executing Maven Archetype {0} ", new Object[]{options.toString()});

        int result = new MavenCli().doMain(options.toArray(String[]::new), workingDirectory.getAbsolutePath(),
                System.out, System.err);

        if (result != 0) {
            throw new RuntimeException("Failed to invoke Maven Archetype.");
        }
    }

    private File zipDirectory(File directory, File destinatonDir) throws IOException {
        Set<String> executables = Set.of("/mvnw", "/gradlew");
        Path directoryPath = directory.toPath();
        File zipFile = new File(destinatonDir, directory.getName() + ZIP_EXTENSION);
        Map<String, String> zipProperties = Map.of("create", "true");

        // Create a ZIP file
        try (FileSystem zipFs = FileSystems.newFileSystem(URI.create("jar:" + zipFile.toPath().toUri()), zipProperties)) {
            // Walk through the directory tree and copy each file into the ZIP filesystem
            Files.walkFileTree(directoryPath, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Path destFile = zipFs.getPath("/").resolve(directoryPath.relativize(file).toString());
                    Files.createDirectories(destFile.getParent());
                    Files.copy(file, destFile, StandardCopyOption.REPLACE_EXISTING);
                    if (executables.contains(destFile.toString())) {
                        Set<PosixFilePermission> perms;
                        if (System.getProperty("os.name").startsWith("Windows")) {
                            // use typical setup, r/w for owner and group, r for others
                            perms = new HashSet<>(Set.of(
                                    PosixFilePermission.OWNER_READ,
                                    PosixFilePermission.OWNER_WRITE,
                                    PosixFilePermission.GROUP_READ,
                                    PosixFilePermission.GROUP_WRITE,
                                    PosixFilePermission.OTHERS_READ
                            ));
                        } else {
                            // some of the files should be executable on Unix systems
                            perms = new HashSet<>(Files.getPosixFilePermissions(file));
                        }
                        perms.add(PosixFilePermission.OWNER_EXECUTE); // existing permissions + executable for user
                        Files.setAttribute(destFile, "zip:permissions", perms);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    Path dirInZip = zipFs.getPath("/").resolve(directoryPath.relativize(dir).toString());
                    Files.createDirectories(dirInZip);
                    return FileVisitResult.CONTINUE;
                }
            });
        }
        return zipFile;
    }
}
