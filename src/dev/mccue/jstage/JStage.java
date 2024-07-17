package dev.mccue.jstage;

import picocli.CommandLine;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.Supplier;
import java.util.spi.ToolProvider;

@CommandLine.Command(name = "jstage", description = "Stage artifacts for publishing to a maven repository.")
public final class JStage implements Callable<Integer> {
    @CommandLine.Option(names = {"--output"}, required = true)
    public File output;
    @CommandLine.Option(names = {"--pom"}, required = true)
    public File pom;
    @CommandLine.Option(names = {"--jar"}, required = true)
    public File jar;
    @CommandLine.Option(names = {"--sources"})
    public File sources;

    @CommandLine.Option(
            names = {"--documentation"},
            description = "The documentation for the artifact either in a jar or a directory."
    )
    public File documentation;

    PrintStream out;
    PrintStream err;

    JStage(PrintStream out, PrintStream err) {
        this.out = out;
        this.err = err;
    }

    public static void main(String[] args) {
        System.exit(
                new CommandLine(new JStage(System.out, System.err))
                        .execute(args)
        );
    }

    @Override
    public Integer call() throws Exception {
        if (!pom.isFile()) {
            err.println("pom must be a file");
            return 1;
        }
        var document = DocumentBuilderFactory.newDefaultInstance()
                .newDocumentBuilder()
                .parse(pom);
        var rootElement = document.getDocumentElement();


        String group = null;
        String artifact = null;
        String version = null;

        var rootElementChildren = rootElement.getChildNodes();
        for (int i = 0; i < rootElementChildren.getLength(); i++) {
            var projectElementChild = rootElementChildren.item(i);
            if (projectElementChild.getNodeName().equals("groupId")) {
                if (group != null) {
                    err.println("<groupId> specified more than once in pom");
                    return 1;
                }
                group = projectElementChild.getTextContent();
            }

            if (projectElementChild.getNodeName().equals("artifactId")) {
                if (artifact != null) {
                    err.println("<artifactId> specified more than once in pom");
                    return 1;
                }
                artifact = projectElementChild.getTextContent();
            }

            if (projectElementChild.getNodeName().equals("version")) {
                if (version != null) {
                    err.println("<version> specified more than once in pom");
                    return 1;
                }
                version = projectElementChild.getTextContent();
            }
        }

        if (group == null) {
            err.println("<groupId> not specified in pom");
            return 1;
        }

        if (artifact == null) {
            err.println("<artifactId> not specified in pom");
            return 1;
        }

        if (version == null) {
            err.println("<version> not specified in pom");
            return 1;
        }

        if (version.contains("${")) {
            err.println("<version> appears to be using a property substitution. This is not supported.");
            return 1;
        }

        group = group.strip();
        artifact = artifact.strip();
        version = version.strip();

        var basePathParts = new ArrayList<>(
                Arrays.asList(group.split("\\."))
        );
        basePathParts.add(artifact);
        basePathParts.add(version);
        Path basePath = Path.of(
                output.toPath().toString(),
                basePathParts.toArray(String[]::new)
        );

        Supplier<ToolProvider> jarTool = () -> ToolProvider.findFirst("jar")
                .orElseThrow();

        try {
            Path unstagedSourcesPath = null;
            if (sources != null) {
                if (Files.isDirectory(sources.toPath())) {
                    var temp = Files.createTempFile(artifact, "sources");
                    int jarExitCode = jarTool.get()
                            .run(out, err, new String[]{
                                    "--create",
                                    "--file", temp.toString(),
                                    "-C", sources.toPath().toString(), "."
                            });
                    if (jarExitCode != 0) {
                        err.println("error running jar command for sources");
                        return jarExitCode;
                    }
                    unstagedSourcesPath = temp;
                } else {
                    unstagedSourcesPath = sources.toPath();
                }
            }


            Path unstagedDocumentationPath = null;
            if (documentation != null) {
                if (Files.isDirectory(documentation.toPath())) {
                    var temp = Files.createTempFile(artifact, "javadoc");
                    int jarExitCode = jarTool.get()
                            .run(out, err, new String[]{
                                    "--create",
                                    "--file", temp.toString(),
                                    "-C", documentation.toPath().toString(), "."
                            });
                    if (jarExitCode != 0) {
                        err.println("error running jar command for documentation");
                        return jarExitCode;
                    }
                    unstagedDocumentationPath = temp;
                } else {
                    unstagedDocumentationPath = documentation.toPath();
                }
            }

            Path pomPath = Path.of(basePath.toString(), artifact + "-" + version + ".pom");
            Path jarPath = Path.of(basePath.toString(), artifact + "-" + version + ".jar");

            Files.createDirectories(pomPath.getParent());
            Files.copy(pom.toPath(), pomPath, StandardCopyOption.REPLACE_EXISTING);
            Files.copy(jar.toPath(), jarPath, StandardCopyOption.REPLACE_EXISTING);

            if (unstagedSourcesPath != null) {
                Path sourcesPath = Path.of(basePath.toString(), artifact + "-" + version + "-sources.jar");
                Files.copy(unstagedSourcesPath, sourcesPath, StandardCopyOption.REPLACE_EXISTING);
            }
            if (unstagedDocumentationPath != null) {
                Path documentationPath = Path.of(basePath.toString(), artifact + "-" + version + "-javadoc.jar");
                Files.copy(unstagedDocumentationPath, documentationPath, StandardCopyOption.REPLACE_EXISTING);
            }

            var moduleRef = ModuleFinder.of(jar.toPath()).findAll()
                    .stream()
                    .findFirst()
                    .orElse(null);
            if (moduleRef == null) {
                err.println("jar is not recognizable as a module");
                return 1;
            }

            if (moduleRef.descriptor().isAutomatic()) {
                err.println("warning: staging an automatic module");
            }
            else {
                var moduleDescriptorVersion = moduleRef.descriptor().version().orElse(null);
                if (!Objects.equals(moduleDescriptorVersion, ModuleDescriptor.Version.parse(version))) {
                    err.println("module version does not match version specified in pom. pom has " + version +
                                (moduleDescriptorVersion == null ? ". module has no version specified. Use --module-version with javac."
                                        : ". module has " + moduleDescriptorVersion));
                    return 1;
                }
            }
        } catch (FileNotFoundException f) {
            err.println("file not found: " + f.getMessage());
            return 1;
        }

        return 0;
    }
}
