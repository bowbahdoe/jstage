import dev.mccue.jstage.JStage;

import java.util.spi.ToolProvider;

module dev.mccue.jstage {
    requires java.xml;
    exports dev.mccue.jstage;

    provides ToolProvider with JStage;
}