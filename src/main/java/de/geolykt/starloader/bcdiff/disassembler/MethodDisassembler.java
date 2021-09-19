package de.geolykt.starloader.bcdiff.disassembler;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.tree.MethodNode;

import me.coley.recaf.parse.bytecode.Disassembler;

/**
 * A method visitor that hijacks Recaf to disassemble the method into plaintext
 * once {@link MethodVisitor#visitEnd()} is called.
 */
public class MethodDisassembler extends MethodNode {

    @FunctionalInterface
    public static interface MethodDisassemblyCallback {
        public void run(String disassembledText);
    }

    protected MethodDisassemblyCallback callback;

    public MethodDisassembler(int api, int access, String name, String descriptor, String signature,  String[] exceptions,
            MethodDisassemblyCallback callback) {
        super(api, access, name, descriptor, signature, exceptions);
        this.callback = callback;
    }

    protected MethodDisassembler(int api, MethodDisassemblyCallback callback) {
        super(api);
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
