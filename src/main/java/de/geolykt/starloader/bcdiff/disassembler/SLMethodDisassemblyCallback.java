package de.geolykt.starloader.bcdiff.disassembler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodNode;

import de.geolykt.starloader.bcdiff.disassembler.MethodDisassembler.MethodDisassemblyCallback;

class SLMethodDisassemblyCallback implements MethodDisassemblyCallback {
    private MethodNode node;
    private List<String> output;
    private StringBuilder sharedBuilder;

    public void setup(MethodNode node, List<String> output, StringBuilder sharedBuilder) {
        this.node = node;
        this.output = output;
        this.sharedBuilder = sharedBuilder;
    }

    private String createString(int counter) {
        if (counter > 25) {
            if (counter > 675) {
                int first = counter / 676;
                int second = (counter % 676) / 26;
                int third = counter % 26;
                return String.valueOf(new char[] {(char) (('A' - 1) + first), (char) ('A' + second), (char) ('A' + third)});
            } else {
                int first = counter / 26;
                int second = counter % 26;
                return String.valueOf(new char[] {(char) (('A' - 1) + first), (char) ('A' + second)});
            }
        } else {
            return String.valueOf((char) ('A' + counter));
        }
    }

    @Override
    public void run(String disassembledText) {
        String[] lines = disassembledText.split("\\n");
        output.add(".METHOD");

        Map<LabelNode, String> labelLookup = new HashMap<>();
        int currentIndex = 0;
        String lastNodeName = null;
        for (AbstractInsnNode insn : node.instructions) {
            if (insn instanceof LabelNode) {
                lastNodeName = createString(currentIndex++);
                labelLookup.put((LabelNode) insn, lastNodeName);
            }
        }

        if (node.localVariables != null) {
            for (LocalVariableNode lvn : node.localVariables) {
                sharedBuilder.setLength(0);
                String start = labelLookup.get(lvn.start);
                String end = labelLookup.get(lvn.end);
                sharedBuilder.append(".METHODLVT ");
                sharedBuilder.append(lvn.name);
                sharedBuilder.append(' ');
                sharedBuilder.append(lvn.desc);
                sharedBuilder.append(' ');
                sharedBuilder.append(lvn.index);
                sharedBuilder.append(' ');
                sharedBuilder.append(start);
                sharedBuilder.append(' ');
                if (end == null) {
                    end = lastNodeName;
                }
                sharedBuilder.append(end);
                output.add(sharedBuilder.toString());
            }
        }

        for (String line : lines) {
            sharedBuilder.setLength(0);
            sharedBuilder.append("    ");
            sharedBuilder.append(line);
            output.add(sharedBuilder.toString());
        }
        output.add(".END");
    }
}
