package de.geolykt.starloader.bcdiff.disassembler;

import java.util.List;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.MethodNode;

public class BytecodeGeneratorVisitor extends ClassVisitor {

    protected final List<String> out;
    private final StringBuilder sharedBuilder = new StringBuilder();

    public BytecodeGeneratorVisitor(List<String> out) {
        super(Opcodes.ASM9);
        this.out = out;
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        super.visit(version, access, name, signature, superName, interfaces);
        if (version < 46) {
            out.add(String.format(".VERSION %d", version));
        } else {
            out.add(String.format(".VERSION %d // Java %d", version, version - 44));
        }
        out.add(String.format(".ACCESS 0x%08X", access));

        sharedBuilder.setLength(0);
        sharedBuilder.append(".NAME ");
        sharedBuilder.append(name);
        out.add(sharedBuilder.toString());

        sharedBuilder.setLength(0);
        sharedBuilder.append(".SIGNATURE ");
        sharedBuilder.append(signature);
        out.add(sharedBuilder.toString());

        sharedBuilder.setLength(0);
        sharedBuilder.append(".SUPER ");
        sharedBuilder.append(superName);
        out.add(sharedBuilder.toString());

        if (interfaces.length != 0) {
            sharedBuilder.setLength(0);
            sharedBuilder.append(".IMPLEMENTS");
            for (String str : interfaces) {
                sharedBuilder.append(' ');
                sharedBuilder.append(str);
            }
            out.add(sharedBuilder.toString());
        }
    }

    @Override
    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
        // TODO implement
        return super.visitAnnotation(descriptor, visible);
    }

    @Override
    public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
        return new FieldDisassembler(api, access, name, descriptor, signature, value, (text) -> {
            out.add(".FIELD");
            String[] lines = text.split("\\n");
            for (String line : lines) {
                sharedBuilder.setLength(0);
                sharedBuilder.append("    ");
                sharedBuilder.append(line);
                out.add(sharedBuilder.toString());
            }
            out.add(".END");
        });
    }

    @Override
    public void visitInnerClass(String name, String outerName, String innerName, int access) {
        out.add(String.format(".INNERCLASS %08X %s %s %s", access, name, innerName, outerName));
        super.visitInnerClass(name, outerName, innerName, access);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        SLMethodDisassemblyCallback callback = new SLMethodDisassemblyCallback();
        MethodNode disassembler = new MethodDisassembler(api, access, name, descriptor, signature, exceptions, callback);
        callback.setup(disassembler, out, sharedBuilder);
        return disassembler;
    }

    @Override
    public void visitOuterClass(String owner, String name, String descriptor) {
        out.add(String.format(".OUTERCLASS %s %s %s", owner, name ,descriptor));
        super.visitOuterClass(owner, name, descriptor);
    }

    @Override
    public void visitSource(String source, String debug) {
        out.add(String.format(".SOURCE %s %s", source, debug));
        super.visitSource(source, debug);
    }
}
