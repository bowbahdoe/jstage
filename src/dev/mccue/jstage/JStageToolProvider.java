package dev.mccue.jstage;

import picocli.CommandLine;

import java.io.PrintWriter;
import java.util.Optional;
import java.util.spi.ToolProvider;

final class JStageToolProvider implements ToolProvider {
    @Override
    public String name() {
        return JStage.class.getAnnotation(CommandLine.Command.class)
                .name();
    }

    @Override
    public Optional<String> description() {
        return Optional.of(String.join("\n", JStage.class.getAnnotation(CommandLine.Command.class)
                .description()));
    }

    @Override
    public int run(PrintWriter out, PrintWriter err, String... args) {
        return new CommandLine(new JStage(out, err))
                .execute(args);
    }
}
