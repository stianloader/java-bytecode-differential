package de.geolykt.starloader.bcdiff.assembler;

import java.util.HashMap;
import java.util.Map;

import org.objectweb.asm.tree.LabelNode;

public class LabelTable {

    protected final Map<String, LabelNode> labels = new HashMap<>();

    public LabelTable() {
    }

    public LabelNode getLabel(String name) {
        LabelNode label = labels.get(name);
        if (label == null) {
            label = new LabelNode();
            label.getLabel().info = label;
            labels.put(name, label);
        }
        return label;
    }
}
