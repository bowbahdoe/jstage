import dev.mccue.jstage.JStage;

import java.util.spi.ToolProvider;

module dev.mccue.jstage {
    requires info.picocli;
    requires java.xml;
    exports dev.mccue.jstage;

    opens dev.mccue.jstage to info.picocli;
    provides ToolProvider with JStage;
}