package dev.mccue.jstage;

import picocli.CommandLine;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.Supplier;
import java.util.spi.ToolProvider;

@CommandLine.Command(
        name = "jstage",
        description = "Stage artifacts for publishing to a maven repository."
)
public final class JStage implements Callable<Integer> {
    @CommandLine.Option(names = {"--output"}, required = true)
    public File output;

    @CommandLine.Option(names = {"--pom"}, required = true)
    public File pom;

    @CommandLine.Option(names = {"--artifact"}, required = true)
    public File artifact;

    @CommandLine.Option(names = {"--classifier"})
    public String classifier = "";

    @CommandLine.Option(names = {"--type"})
    public String type = "jar";

    PrintWriter out;
    PrintWriter err;

    JStage(PrintWriter out, PrintWriter err) {
        this.out = out;
        this.err = err;
    }

    JStage(PrintStream out, PrintStream err) {
        this.out = new PrintWriter(out);
        this.err = new PrintWriter(err);
    }

    public static ToolProvider provider() {
        return new JStageToolProvider();
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


        String groupId = null;
        String artifactId = null;
        String version = null;

        var rootElementChildren = rootElement.getChildNodes();
        for (int i = 0; i < rootElementChildren.getLength(); i++) {
            var projectElementChild = rootElementChildren.item(i);
            if (projectElementChild.getNodeName().equals("groupId")) {
                if (groupId != null) {
                    err.println("<groupId> specified more than once in pom");
                    return 1;
                }
                groupId = projectElementChild.getTextContent();
            }

            if (projectElementChild.getNodeName().equals("artifactId")) {
                if (artifactId != null) {
                    err.println("<artifactId> specified more than once in pom");
                    return 1;
                }
                artifactId = projectElementChild.getTextContent();
            }

            if (projectElementChild.getNodeName().equals("version")) {
                if (version != null) {
                    err.println("<version> specified more than once in pom");
                    return 1;
                }
                version = projectElementChild.getTextContent();
            }
        }

        if (groupId == null) {
            err.println("<groupId> not specified in pom");
            return 1;
        }

        if (artifactId == null) {
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

        groupId = groupId.strip();
        artifactId = artifactId.strip();
        version = version.strip();

        var basePathParts = new ArrayList<>(
                Arrays.asList(groupId.split("\\."))
        );
        basePathParts.add(artifactId);
        basePathParts.add(version);
        Path basePath = Path.of(
                output.toPath().toString(),
                basePathParts.toArray(String[]::new)
        );

        Supplier<ToolProvider> jarTool = () -> ToolProvider.findFirst("jar")
                .orElseThrow();

        try {
            Path unstagedPath = artifact.toPath();
            if (List.of("sources", "javadoc").contains(classifier) && "jar".equals(type)) {
                if (Files.isDirectory(artifact.toPath())) {
                    var temp = Files.createTempFile(artifactId, classifier + ".jar");
                    int jarExitCode = jarTool.get()
                            .run(out, err, new String[]{
                                    "--create",
                                    "--file", temp.toString(),
                                    "-C", artifact.toPath().toString(), "."
                            });
                    if (jarExitCode != 0) {
                        err.println("error running jar command for sources");
                        return jarExitCode;
                    }
                    unstagedPath = temp;
                }
            }


            Path pomPath = Path.of(basePath.toString(), artifactId + "-" + version + ".pom");
            Path jarPath = Path.of(basePath.toString(), artifactId + "-" + version + (classifier.isBlank() ? "" : ("-" + classifier)) + "." + type);

            Files.createDirectories(pomPath.getParent());
            Files.copy(pom.toPath(), pomPath, StandardCopyOption.REPLACE_EXISTING);
            Files.copy(unstagedPath, jarPath, StandardCopyOption.REPLACE_EXISTING);

            if ("jar".equals(type) && !List.of("sources", "javadoc").contains(classifier)) {
                var moduleRef = ModuleFinder.of(artifact.toPath()).findAll()
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
            }

        } catch (FileNotFoundException f) {
            err.println("file not found: " + f.getMessage());
            return 1;
        }

        return 0;
    }
}
