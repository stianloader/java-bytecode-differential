package de.geolykt.starloader.bcdiff.disassembler;

import org.objectweb.asm.tree.FieldNode;

import me.coley.recaf.parse.bytecode.Disassembler;

public class FieldDisassembler extends FieldNode {

    @FunctionalInterface
    public static interface FieldDisassemblyCallback {
        public void run(String disassembledText);
    }

    protected FieldDisassemblyCallback callback;

    public FieldDisassembler(int api, int access, String name, String descriptor, String signature, Object value,
            FieldDisassemblyCallback callback) {
        super(api, access, name, descriptor, signature, value);
        this.callback = callback;
    }

    protected void disassemble() {
        callback.run(new Disassembler().disassemble(this));
    }

    @Override
    public void visitEnd() {
        super.visitEnd();
        disassemble();
    }
}
