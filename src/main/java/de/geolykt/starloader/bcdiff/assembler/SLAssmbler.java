package de.geolykt.starloader.bcdiff.assembler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.MultiANewArrayInsnNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import de.geolykt.starloader.bcdiff.assembler.DefHeader.MethodDefHeader;

public class SLAssmbler {

    private static final char[] ESCAPED_CHARACTERS = new char[256];

    static {
        for (char c = 0; c < 256; c++) {
            ESCAPED_CHARACTERS[c] = 0;
        }
        ESCAPED_CHARACTERS['b'] = '\b';
        ESCAPED_CHARACTERS['t'] = '\t';
        ESCAPED_CHARACTERS['n'] = '\n';
        ESCAPED_CHARACTERS['f'] = '\f';
        ESCAPED_CHARACTERS['r'] = '\r';
        ESCAPED_CHARACTERS['"'] = '\"';
        ESCAPED_CHARACTERS['\''] = '\'';
        ESCAPED_CHARACTERS['\\'] = '\\';
    }

    private final StringBuilder sharedBuilder = new StringBuilder();

    public SLAssmbler(String[] bytecode, ClassNode output) {

        List<String> recafCode = new ArrayList<>();
        List<String> methodLVT = new ArrayList<>();
        boolean parseMethod = false;
        boolean parseField = false;

        List<MethodNode> methods = new ArrayList<>();
        List<FieldNode> fields = new ArrayList<>();

        for (int index = 0; index < bytecode.length; index++) {
            String line = bytecode[index];
            if (line.charAt(0) == ' ') {
                recafCode.add(line.substring(4));
                continue;
            }
            String[] tokens = line.split(" ");
            switch (tokens[0]) {
            case ".VERSION":
                output.version = Integer.parseInt(tokens[1]);
                break;
            case ".ACCESS":
                output.access = Integer.parseInt(tokens[1].substring(2), 16);
                break;
            case ".NAME":
                output.name = tokens[1];
                break;
            case ".SIGNATURE":
                if (tokens[1].equals("null")) {
                    tokens[1] = null;
                }
                output.signature = tokens[1];
                break;
            case ".SUPER":
                if (tokens[1].equals("null")) {
                    tokens[1] = null;
                }
                output.superName = tokens[1];
                break;
            case ".IMPLEMENTS": {
                String[] interfaces = new String[tokens.length - 1];
                System.arraycopy(tokens, 1, interfaces, 0, interfaces.length);
                output.interfaces = Arrays.asList(interfaces);
                break;
            }
            case ".SOURCE":
                if (tokens[1].equals("null")) {
                    tokens[1] = null;
                }
                if (tokens[2].equals("null")) {
                    tokens[2] = null;
                }
                output.sourceFile = tokens[1];
                output.sourceDebug = tokens[2];
                break;
            case ".INNERCLASS":
                if (tokens[3].equals("null")) {
                    tokens[3] = null;
                }
                if (tokens[4].equals("null")) {
                    tokens[4] = null;
                }
                output.visitInnerClass(tokens[2], tokens[4], tokens[3], Integer.parseInt(tokens[1], 16));
                break;
            case ".OUTERCLASS":
                if (tokens[2].equals("null")) {
                    tokens[2] = null;
                }
                if (tokens[3].equals("null")) {
                    tokens[3] = null;
                }
                output.visitOuterClass(tokens[1], tokens[2], tokens[3]);
                break;
            case ".METHOD":
                parseMethod = true;
                break;
            case ".FIELD":
                parseField = true;
                break;
            case ".END":
                if (parseField) {
                    parseField = false;
                    fields.add(parseField(recafCode));
                    recafCode.clear();
                } else if (parseMethod) {
                    parseMethod = false;
                    methods.add(parseMethod(recafCode, methodLVT));
                    methodLVT.clear();
                    recafCode.clear();
                } else {
                    throw new IllegalStateException(".END token reached but there is nothing to end.");
                }
                break;
            case ".METHODLVT":
                methodLVT.add(line.substring(11));
                break;
            default:
                if (tokens[0].charAt(0) == '.') {
                    throw new IllegalStateException("Unknown token: " + tokens[0] + ". Token found in line " + index + " (" + line + ")");
                }
            }
        }
        output.fields = fields;
        output.methods = methods;
    }

    protected FieldNode parseField(List<String> recafCode) {
        String signature = null;
        Object value = null;

        DefHeader header = DefHeader.parseFieldDef(recafCode.get(0));
        String name = header.getName();
        String desc = header.getDesc();
        int access = header.getAccess();

        for (int i = 1; i < recafCode.size(); i++) {
            String line = recafCode.get(i);
            String[] tokens = line.split(" ", 2);
            if (tokens[0].equals("VALUE")) {
                int lastChar = tokens[1].length() - 1;
                if (tokens[1].charAt(0) == '\"') {
                    value = removeEscapes(tokens[1].substring(1, lastChar));
                } else if (tokens[1].codePointAt(lastChar) == 'L') {
                    value = Long.parseLong(tokens[1].substring(0, lastChar));
                } else if (tokens[1].codePointAt(lastChar) == 'F') {
                    value = Float.parseFloat(tokens[1].substring(0, lastChar));
                } else if (tokens[1].codePointAt(lastChar) == 'D') {
                    value = Double.parseDouble(tokens[1].substring(0, lastChar));
                } else {
                    value = Integer.parseInt(tokens[1]);
                }
            } else if (tokens[0].equals("SIGNATURE")) {
                signature = tokens[1];
            } else {
                throw new IllegalArgumentException("Line " + i + " (" + line + ") has an invalid or unimplemented token.");
            }
        }
        return new FieldNode(access, name, desc, signature, value);
    }
    protected Handle parseHandle(String input) {
        // remove the "handle[" prefix and the ']' suffix.
        input = input.substring(7, input.length() - 1);
        String[] tokens = input.split(" ");
        int tag = -1;
        switch (tokens[0]) {
        case "H_GETFIELD":
            tag = Opcodes.H_GETFIELD;
            break;
        case "H_GETSTATIC":
            tag = Opcodes.H_GETSTATIC;
            break;
        case "H_PUTFIELD":
            tag = Opcodes.H_PUTFIELD;
            break;
        case "H_PUTSTATIC":
            tag = Opcodes.H_PUTSTATIC;
            break;
        case "H_INVOKEVIRTUAL":
            tag = Opcodes.H_INVOKEVIRTUAL;
            break;
        case "H_INVOKESTATIC":
            tag = Opcodes.H_INVOKESTATIC;
            break;
        case "H_INVOKEINTERFACE":
            tag = Opcodes.H_INVOKEINTERFACE;
            break;
        case "H_INVOKESPECIAL":
            tag = Opcodes.H_INVOKESPECIAL;
            break;
        case "H_NEWINVOKESPECIAL":
            tag = Opcodes.H_NEWINVOKESPECIAL;
            break;
        default:
            throw new IllegalArgumentException("Unknown tag: " + tokens[0]);
        }
        String[] split = tokens[1].split("\\.", 2);
        int descStart = split[1].indexOf('(');
        return new Handle(tag, split[0], split[1].substring(0, descStart), split[1].substring(descStart), tag == Opcodes.H_INVOKEINTERFACE);
    }

    protected void parseInvokedynamic(String hmeta, String params, InsnList instructionList) {
        String[] tokens = params.split(" ", 4);
        String name = tokens[0];
        String desc = tokens[1];
        String boostrapHandle = tokens[2];
        String[] bsmArgsRaw = tokens[3].substring(5, tokens[3].length() - 1).split(", ");
        if (boostrapHandle.equals("${H_META}")) {
            boostrapHandle = hmeta;
        }
        Handle bsmHandle = parseHandle(boostrapHandle);
        int argC = bsmArgsRaw.length;
        Object[] bsmArgs = new Object[argC];
        for (int i = 0; i < argC; i++) {
            String raw = bsmArgsRaw[i];
            if (raw.codePointAt(0) == 'h') {
                // Handle
                bsmArgs[i] = parseHandle(raw);
            } else {
                // Type
                bsmArgs[i] = Type.getMethodType(raw);
            }
        }
        instructionList.add(new InvokeDynamicInsnNode(name, desc, bsmHandle, bsmArgs));
    }

    protected void parseLookupSwitch(String params, LabelTable labels, InsnList instructionList) {
        int first = params.indexOf('[');
        int last = params.lastIndexOf('[');
        int endFirst = params.indexOf(']');
        LabelNode defaultLabel = labels.getLabel(params.substring(last + 1, params.length() - 1));
        String[] entries = params.substring(first + 1, endFirst).split(", ");
        int entryNum = entries.length;
        int[] keys = new int[entryNum];
        LabelNode[] values = new LabelNode[entryNum];
        for (int i = 0; i < entryNum; i++) {
            String[] entry = entries[i].split("=");
            keys[i] = Integer.valueOf(entry[0]);
            values[i] = labels.getLabel(entry[1]);
        }
        instructionList.add(new LookupSwitchInsnNode(defaultLabel, keys, values));
    }

    protected MethodNode parseMethod(List<String> code, List<String> methodLVT) {
        //System.out.println(String.format("%05d:    %s", 0, code.get(0)));
        MethodDefHeader header = DefHeader.parseMethodDef(code.get(0));
        String signature = null;
        List<String> exceptions = null;
        int linesOfCode = code.size();

        LabelTable labels = new LabelTable();
        LocalNameTable locals = new LocalNameTable(header);
        InsnList instructionList = new InsnList();
        List<TryCatchBlockNode> tryCatchBlocks = new ArrayList<>();
        String hmeta = null;

        List<LocalVariableNode> lvt = new ArrayList<>();
        for (String entry : methodLVT) {
            String[] tokens = entry.split(" ");
            int index = Integer.parseInt(tokens[2]);
            LabelNode start = labels.getLabel(tokens[3]);
            LabelNode end = labels.getLabel(tokens[4]);
            // Also add the signature
            lvt.add(new LocalVariableNode(tokens[0], tokens[1], null, start, end, index));
            locals.pushName(index, tokens[0]);
        }

        for (int i = 1; i < linesOfCode; i++) {
            String line = code.get(i);
            //System.out.println(String.format("%05d:    %s", i, line));
            String[] tokens = line.split(" ");
            String instruction = tokens[0];
            switch (instruction) {
            case "SIGNATURE":
                if (tokens.length != 2) {
                    throw new IllegalStateException("Signature contains whitespace character");
                }
                signature = tokens[1];
                break;
            case "ALIAS":
                if (tokens[1].equals("H_META")) {
                    hmeta = line.split(" ", 3)[2];
                    hmeta = hmeta.substring(1, hmeta.length() - 1);
                } else {
                    throw new IllegalStateException("Line " + i + " (" + line + ") declares an alias, even though aliases are only partially implemented.");
                }
                break;
            case "THROWS":
                if (exceptions == null) {
                    exceptions = new ArrayList<>();
                }
                if (tokens.length != 2) {
                    throw new IllegalStateException("Too many tokens.");
                }
                exceptions.add(tokens[1]);
                break;
            // xStore
            case "ASTORE":
                pushVarInsn(Opcodes.ASTORE, instructionList, tokens[1], locals);
                break;
            case "ISTORE":
                pushVarInsn(Opcodes.ISTORE, instructionList, tokens[1], locals);
                break;
            case "LSTORE":
                pushVarInsn(Opcodes.LSTORE, instructionList, tokens[1], locals);
                break;
            case "FSTORE":
                pushVarInsn(Opcodes.FSTORE, instructionList, tokens[1], locals);
                break;
            case "DSTORE":
                pushVarInsn(Opcodes.DSTORE, instructionList, tokens[1], locals);
                break;
            // xLoad
            case "ALOAD":
                pushVarInsn(Opcodes.ALOAD, instructionList, tokens[1], locals);
                break;
            case "ILOAD":
                pushVarInsn(Opcodes.ILOAD, instructionList, tokens[1], locals);
                break;
            case "LLOAD":
                pushVarInsn(Opcodes.LLOAD, instructionList, tokens[1], locals);
                break;
            case "FLOAD":
                pushVarInsn(Opcodes.FLOAD, instructionList, tokens[1], locals);
                break;
            case "DLOAD":
                pushVarInsn(Opcodes.DLOAD, instructionList, tokens[1], locals);
                break;
            case "LINE":
                instructionList.add(new LineNumberNode(Integer.parseInt(tokens[2]), labels.getLabel(tokens[1])));
                break;
            case "RETURN":
                instructionList.add(new InsnNode(Opcodes.RETURN));
                break;
            case "ARETURN":
                instructionList.add(new InsnNode(Opcodes.ARETURN));
                break;
            case "IRETURN":
                instructionList.add(new InsnNode(Opcodes.IRETURN));
                break;
            case "LRETURN":
                instructionList.add(new InsnNode(Opcodes.LRETURN));
                break;
            case "FRETURN":
                instructionList.add(new InsnNode(Opcodes.FRETURN));
                break;
            case "DRETURN":
                instructionList.add(new InsnNode(Opcodes.DRETURN));
                break;
            case "ATHROW":
                instructionList.add(new InsnNode(Opcodes.ATHROW));
                break;
            case "MONITOREXIT":
                instructionList.add(new InsnNode(Opcodes.MONITOREXIT));
                break;
            case "MONITORENTER":
                instructionList.add(new InsnNode(Opcodes.MONITORENTER));
                break;
            case "ICONST_M1":
                instructionList.add(new InsnNode(Opcodes.ICONST_M1));
                break;
            case "ICONST_0":
                instructionList.add(new InsnNode(Opcodes.ICONST_0));
                break;
            case "ICONST_1":
                instructionList.add(new InsnNode(Opcodes.ICONST_1));
                break;
            case "ICONST_2":
                instructionList.add(new InsnNode(Opcodes.ICONST_2));
                break;
            case "ICONST_3":
                instructionList.add(new InsnNode(Opcodes.ICONST_3));
                break;
            case "ICONST_4":
                instructionList.add(new InsnNode(Opcodes.ICONST_4));
                break;
            case "ICONST_5":
                instructionList.add(new InsnNode(Opcodes.ICONST_5));
                break;
            case "FCONST_0":
                instructionList.add(new InsnNode(Opcodes.FCONST_0));
                break;
            case "FCONST_1":
                instructionList.add(new InsnNode(Opcodes.FCONST_1));
                break;
            case "FCONST_2":
                instructionList.add(new InsnNode(Opcodes.FCONST_2));
                break;
            case "DCONST_0":
                instructionList.add(new InsnNode(Opcodes.DCONST_0));
                break;
            case "DCONST_1":
                instructionList.add(new InsnNode(Opcodes.DCONST_1));
                break;
            case "LCONST_0":
                instructionList.add(new InsnNode(Opcodes.LCONST_0));
                break;
            case "LCONST_1":
                instructionList.add(new InsnNode(Opcodes.LCONST_1));
                break;
            case "ACONST_NULL":
                instructionList.add(new InsnNode(Opcodes.ACONST_NULL));
                break;
            case "DUP":
                instructionList.add(new InsnNode(Opcodes.DUP));
                break;
            case "DUP_X1":
                instructionList.add(new InsnNode(Opcodes.DUP_X1));
                break;
            case "DUP_X2":
                instructionList.add(new InsnNode(Opcodes.DUP_X2));
                break;
            case "DUP2":
                instructionList.add(new InsnNode(Opcodes.DUP2));
                break;
            case "DUP2_X1":
                instructionList.add(new InsnNode(Opcodes.DUP2_X1));
                break;
            case "DUP2_X2":
                instructionList.add(new InsnNode(Opcodes.DUP2_X2));
                break;
            case "POP":
                instructionList.add(new InsnNode(Opcodes.POP));
                break;
            case "POP2":
                instructionList.add(new InsnNode(Opcodes.POP2));
                break;
            case "AASTORE":
                instructionList.add(new InsnNode(Opcodes.AASTORE));
                break;
            case "BASTORE":
                instructionList.add(new InsnNode(Opcodes.BASTORE));
                break;
            case "CASTORE":
                instructionList.add(new InsnNode(Opcodes.CASTORE));
                break;
            case "DASTORE":
                instructionList.add(new InsnNode(Opcodes.DASTORE));
                break;
            case "IASTORE":
                instructionList.add(new InsnNode(Opcodes.IASTORE));
                break;
            case "FASTORE":
                instructionList.add(new InsnNode(Opcodes.FASTORE));
                break;
            case "LASTORE":
                instructionList.add(new InsnNode(Opcodes.LASTORE));
                break;
            case "SASTORE":
                instructionList.add(new InsnNode(Opcodes.SASTORE));
                break;
            case "AALOAD":
                instructionList.add(new InsnNode(Opcodes.AALOAD));
                break;
            case "BALOAD":
                instructionList.add(new InsnNode(Opcodes.BALOAD));
                break;
            case "CALOAD":
                instructionList.add(new InsnNode(Opcodes.CALOAD));
                break;
            case "DALOAD":
                instructionList.add(new InsnNode(Opcodes.DALOAD));
                break;
            case "IALOAD":
                instructionList.add(new InsnNode(Opcodes.IALOAD));
                break;
            case "FALOAD":
                instructionList.add(new InsnNode(Opcodes.FALOAD));
                break;
            case "LALOAD":
                instructionList.add(new InsnNode(Opcodes.LALOAD));
                break;
            case "SALOAD":
                instructionList.add(new InsnNode(Opcodes.SALOAD));
                break;
            case "I2C":
                instructionList.add(new InsnNode(Opcodes.I2C));
                break;
            case "I2S":
                instructionList.add(new InsnNode(Opcodes.I2S));
                break;
            case "I2B":
                instructionList.add(new InsnNode(Opcodes.I2B));
                break;
            case "I2L":
                instructionList.add(new InsnNode(Opcodes.I2L));
                break;
            case "I2F":
                instructionList.add(new InsnNode(Opcodes.I2F));
                break;
            case "I2D":
                instructionList.add(new InsnNode(Opcodes.I2D));
                break;
            case "L2I":
                instructionList.add(new InsnNode(Opcodes.L2I));
                break;
            case "L2F":
                instructionList.add(new InsnNode(Opcodes.L2F));
                break;
            case "L2D":
                instructionList.add(new InsnNode(Opcodes.L2D));
                break;
            case "F2I":
                instructionList.add(new InsnNode(Opcodes.F2I));
                break;
            case "F2L":
                instructionList.add(new InsnNode(Opcodes.F2L));
                break;
            case "F2D":
                instructionList.add(new InsnNode(Opcodes.F2D));
                break;
            case "D2I":
                instructionList.add(new InsnNode(Opcodes.D2I));
                break;
            case "D2L":
                instructionList.add(new InsnNode(Opcodes.D2L));
                break;
            case "D2F":
                instructionList.add(new InsnNode(Opcodes.D2F));
                break;
            case "ARRAYLENGTH":
                instructionList.add(new InsnNode(Opcodes.ARRAYLENGTH));
                break;
            case "ISUB":
                instructionList.add(new InsnNode(Opcodes.ISUB));
                break;
            case "LSUB":
                instructionList.add(new InsnNode(Opcodes.LSUB));
                break;
            case "DSUB":
                instructionList.add(new InsnNode(Opcodes.DSUB));
                break;
            case "FSUB":
                instructionList.add(new InsnNode(Opcodes.FSUB));
                break;
            case "IMUL":
                instructionList.add(new InsnNode(Opcodes.IMUL));
                break;
            case "LMUL":
                instructionList.add(new InsnNode(Opcodes.LMUL));
                break;
            case "FMUL":
                instructionList.add(new InsnNode(Opcodes.FMUL));
                break;
            case "DMUL":
                instructionList.add(new InsnNode(Opcodes.DMUL));
                break;
            case "IADD":
                instructionList.add(new InsnNode(Opcodes.IADD));
                break;
            case "LADD":
                instructionList.add(new InsnNode(Opcodes.LADD));
                break;
            case "DADD":
                instructionList.add(new InsnNode(Opcodes.DADD));
                break;
            case "FADD":
                instructionList.add(new InsnNode(Opcodes.FADD));
                break;
            case "IDIV":
                instructionList.add(new InsnNode(Opcodes.IDIV));
                break;
            case "LDIV":
                instructionList.add(new InsnNode(Opcodes.LDIV));
                break;
            case "DDIV":
                instructionList.add(new InsnNode(Opcodes.DDIV));
                break;
            case "FDIV":
                instructionList.add(new InsnNode(Opcodes.FDIV));
                break;
            case "INEG":
                instructionList.add(new InsnNode(Opcodes.INEG));
                break;
            case "LNEG":
                instructionList.add(new InsnNode(Opcodes.LNEG));
                break;
            case "DNEG":
                instructionList.add(new InsnNode(Opcodes.DNEG));
                break;
            case "FNEG":
                instructionList.add(new InsnNode(Opcodes.FNEG));
                break;
            case "IREM":
                instructionList.add(new InsnNode(Opcodes.IREM));
                break;
            case "LREM":
                instructionList.add(new InsnNode(Opcodes.LREM));
                break;
            case "DREM":
                instructionList.add(new InsnNode(Opcodes.DREM));
                break;
            case "FREM":
                instructionList.add(new InsnNode(Opcodes.FREM));
                break;
            case "ISHR":
                instructionList.add(new InsnNode(Opcodes.ISHR)); // shift right
                break;
            case "LSHR":
                instructionList.add(new InsnNode(Opcodes.LSHR));
                break;
            case "IUSHR":
                instructionList.add(new InsnNode(Opcodes.IUSHR)); // unsigned shift right
                break;
            case "LUSHR":
                instructionList.add(new InsnNode(Opcodes.LUSHR));
                break;
            case "ISHL":
                instructionList.add(new InsnNode(Opcodes.ISHL)); // shift left
                break;
            case "LSHL":
                instructionList.add(new InsnNode(Opcodes.LSHL));
                break;
            case "IAND":
                instructionList.add(new InsnNode(Opcodes.IAND));
                break;
            case "LAND":
                instructionList.add(new InsnNode(Opcodes.LAND));
                break;
            case "IOR":
                instructionList.add(new InsnNode(Opcodes.IOR));
                break;
            case "LOR":
                instructionList.add(new InsnNode(Opcodes.LOR));
                break;
            case "IXOR":
                instructionList.add(new InsnNode(Opcodes.IXOR));
                break;
            case "LXOR":
                instructionList.add(new InsnNode(Opcodes.LXOR));
                break;
            case "DCMPL":
                instructionList.add(new InsnNode(Opcodes.DCMPL));
                break;
            case "DCMPG":
                instructionList.add(new InsnNode(Opcodes.DCMPG));
                break;
            case "FCMPL":
                instructionList.add(new InsnNode(Opcodes.FCMPL));
                break;
            case "FCMPG":
                instructionList.add(new InsnNode(Opcodes.FCMPG));
                break;
            case "LCMP":
                instructionList.add(new InsnNode(Opcodes.LCMP));
                break;
            case "IINC":
                instructionList.add(new IincInsnNode(locals.getVarIndex(tokens[1], Opcodes.IINC), Integer.valueOf(tokens[2])));
                break;
            case "GOTO":
                pushJumpInsn(Opcodes.GOTO, tokens[1], labels, instructionList);
                break;
            case "IF_ACMPEQ":
                pushJumpInsn(Opcodes.IF_ACMPEQ, tokens[1], labels, instructionList);
                break;
            case "IF_ACMPNE":
                pushJumpInsn(Opcodes.IF_ACMPNE, tokens[1], labels, instructionList);
                break;
            case "IF_ICMPEQ":
                pushJumpInsn(Opcodes.IF_ICMPEQ, tokens[1], labels, instructionList);
                break;
            case "IF_ICMPGE":
                pushJumpInsn(Opcodes.IF_ICMPGE, tokens[1], labels, instructionList);
                break;
            case "IF_ICMPGT":
                pushJumpInsn(Opcodes.IF_ICMPGT, tokens[1], labels, instructionList);
                break;
            case "IF_ICMPLE":
                pushJumpInsn(Opcodes.IF_ICMPLE, tokens[1], labels, instructionList);
                break;
            case "IF_ICMPLT":
                pushJumpInsn(Opcodes.IF_ICMPLT, tokens[1], labels, instructionList);
                break;
            case "IF_ICMPNE":
                pushJumpInsn(Opcodes.IF_ICMPNE, tokens[1], labels, instructionList);
                break;
            case "IFEQ":
                pushJumpInsn(Opcodes.IFEQ, tokens[1], labels, instructionList);
                break;
            case "IFGE":
                pushJumpInsn(Opcodes.IFGE, tokens[1], labels, instructionList);
                break;
            case "IFGT":
                pushJumpInsn(Opcodes.IFGT, tokens[1], labels, instructionList);
                break;
            case "IFLE":
                pushJumpInsn(Opcodes.IFLE, tokens[1], labels, instructionList);
                break;
            case "IFLT":
                pushJumpInsn(Opcodes.IFLT, tokens[1], labels, instructionList);
                break;
            case "IFNE":
                pushJumpInsn(Opcodes.IFNE, tokens[1], labels, instructionList);
                break;
            case "IFNONNULL":
                pushJumpInsn(Opcodes.IFNONNULL, tokens[1], labels, instructionList);
                break;
            case "IFNULL":
                pushJumpInsn(Opcodes.IFNULL, tokens[1], labels, instructionList);
                break;
            case "LOOKUPSWITCH":
                parseLookupSwitch(line.split(" ", 2)[1], labels, instructionList);
                break;
            case "TABLESWITCH":
                parseTableSwitch(line.split(" ", 2)[1], labels, instructionList);
                break;
            case "CHECKCAST":
                instructionList.add(new TypeInsnNode(Opcodes.CHECKCAST, tokens[1]));
                break;
            case "INSTANCEOF":
                instructionList.add(new TypeInsnNode(Opcodes.INSTANCEOF, tokens[1]));
                break;
            case "NEW":
                instructionList.add(new TypeInsnNode(Opcodes.NEW, tokens[1]));
                break;
            case "ANEWARRAY":
                instructionList.add(new TypeInsnNode(Opcodes.ANEWARRAY, tokens[1]));
                break;
            case "MULTIANEWARRAY":
                instructionList.add(new MultiANewArrayInsnNode(tokens[1], Integer.valueOf(tokens[2])));
                break;
            case "GETFIELD": {
                String[] spl = tokens[1].split("\\.");
                instructionList.add(new FieldInsnNode(Opcodes.GETFIELD, spl[0], spl[1], tokens[2]));
                break;
            }
            case "PUTFIELD": {
                String[] spl = tokens[1].split("\\.");
                instructionList.add(new FieldInsnNode(Opcodes.PUTFIELD, spl[0], spl[1], tokens[2]));
                break;
            }
            case "GETSTATIC": {
                String[] spl = tokens[1].split("\\.");
                instructionList.add(new FieldInsnNode(Opcodes.GETSTATIC, spl[0], spl[1], tokens[2]));
                break;
            }
            case "PUTSTATIC": {
                String[] spl = tokens[1].split("\\.");
                instructionList.add(new FieldInsnNode(Opcodes.PUTSTATIC, spl[0], spl[1], tokens[2]));
                break;
            }
            case "LDC": {
                String rawValue = line.split(" ", 2)[1];
                Object value;
                int lastChar = rawValue.length() - 1;
                if (rawValue.charAt(0) == '\"') {
                    value = removeEscapes(rawValue.substring(1, lastChar));
                } else if (rawValue.charAt(0) == '[') {
                    value = org.objectweb.asm.Type.getObjectType(rawValue);
                } else if (rawValue.codePointAt(lastChar) == 'L') {
                    value = Long.parseLong(rawValue.substring(0, lastChar));
                } else if (rawValue.codePointAt(lastChar) == 'F') {
                    value = Float.parseFloat(rawValue.substring(0, lastChar));
                } else if (rawValue.codePointAt(lastChar) == 'D') {
                    value = Double.parseDouble(rawValue.substring(0, lastChar));
                } else if (rawValue.codePointAt(lastChar) == ';') {
                    value = org.objectweb.asm.Type.getObjectType(rawValue.substring(1, rawValue.length() - 1));
                } else if (rawValue.equals("Infinity")) {
                    value = Double.POSITIVE_INFINITY;
                } else if (rawValue.equals("-Infinity")) {
                    value = Double.NEGATIVE_INFINITY;
                } else if (rawValue.equals("NaN")) {
                    value = Double.NaN;
                } else {
                    value = Integer.parseInt(rawValue);
                }
                instructionList.add(new LdcInsnNode(value));
                break;
            }
            case "SIPUSH":
                instructionList.add(new IntInsnNode(Opcodes.SIPUSH, Integer.parseInt(tokens[1])));
                break;
            case "BIPUSH":
                instructionList.add(new IntInsnNode(Opcodes.BIPUSH, Integer.parseInt(tokens[1])));
                break;
            case "NEWARRAY": {
                int type = -1;
                // Apparently these magic values are hardcoded without much more info or other documentation
                switch (tokens[1].codePointAt(0)) {
                case 'B':
                    type = 8;
                    break;
                case 'C':
                    type = 5;
                    break;
                case 'D':
                    type = 7;
                    break;
                case 'F':
                    type = 6;
                    break;
                case 'I':
                    type = 10;
                    break;
                case 'J':
                    type = 11;
                    break;
                case 'S':
                    type = 9;
                    break;
                case 'Z':
                    type = 4;
                    break;
                default:
                    throw new IllegalStateException(tokens[1] + " is not a valid descriptor.");
                }
                instructionList.add(new IntInsnNode(Opcodes.NEWARRAY, type));
                break;
            }
            case "INVOKEVIRTUAL":
                parseMethodInsn(Opcodes.INVOKEVIRTUAL, line.split(" ", 2)[1], instructionList);
                break;
            case "INVOKESTATIC":
                parseMethodInsn(Opcodes.INVOKESTATIC, line.split(" ", 2)[1], instructionList);
                break;
            case "INVOKESPECIAL":
                parseMethodInsn(Opcodes.INVOKESPECIAL, line.split(" ", 2)[1], instructionList);
                break;
            case "INVOKEINTERFACE":
                parseMethodInsn(Opcodes.INVOKEINTERFACE, line.split(" ", 2)[1], instructionList);
                break;
            case "INVOKEDYNAMIC":
                parseInvokedynamic(hmeta, line.split(" ", 2)[1], instructionList);
                break;
            case "TRY": {
                LabelNode start = labels.getLabel(tokens[1]);
                LabelNode end = labels.getLabel(tokens[2]);
                LabelNode handler = labels.getLabel(tokens[4]);
                String type = tokens[3].substring(6, tokens[3].length() - 1); // get rid of the "catch(" prefix
                TryCatchBlockNode block = new TryCatchBlockNode(start, end, handler, type);
                tryCatchBlocks.add(block);
                break;
            }
            default:
                if (tokens.length == 1) {
                    if (tokens[0].indexOf(':') == tokens[0].length() - 1) {
                        pushLabel(tokens[0], labels, instructionList);
                        break;
                    }
                }
                throw new IllegalStateException("Unknown instruction (" + tokens[0] + ") at line " + i + ": " + line);
            }
        }
        String[] exceptionsArr;
        if (exceptions == null) {
            exceptionsArr = new String[0];
        } else {
            exceptionsArr = exceptions.toArray(new String[0]);
        }

        MethodNode method = new MethodNode(header.getAccess(), header.getName(), header.getDesc(), signature, exceptionsArr);
        method.instructions = instructionList;
        method.tryCatchBlocks = tryCatchBlocks;
        method.localVariables = lvt;
        return method;
    }

    protected void parseMethodInsn(int opcode, String params, InsnList instructionList) {
        String[] split = params.split("\\.", 2);
        int firstBracket = split[1].indexOf('(');
        String name = split[1].substring(0, firstBracket);
        String desc = split[1].substring(firstBracket);
        instructionList.add(new MethodInsnNode(opcode, split[0], name, desc));
    }

    protected void parseTableSwitch(String params, LabelTable labels, InsnList instructionList) {
        int first = params.indexOf('[');
        int last = params.lastIndexOf('[');
        int endFirst = params.indexOf(']');
        LabelNode defaultLabel = labels.getLabel(params.substring(last + 1, params.length() - 1));
        String[] range = params.substring(first + 1, endFirst).split(":");
        int min = Integer.valueOf(range[0]);
        int max = Integer.valueOf(range[1]);
        first = params.indexOf('[', first + 1);
        endFirst = params.indexOf(']', first);
        String[] values = params.substring(first + 1, endFirst).split(", ");
        int valuesNum = values.length;
        LabelNode[] jumpLabels = new LabelNode[valuesNum];
        for (int i = 0; i < valuesNum; i++) {
            jumpLabels[i] = labels.getLabel(values[i]);
        }
        instructionList.add(new TableSwitchInsnNode(min, max, defaultLabel, jumpLabels));
}

    protected void pushJumpInsn(int opcode, String labelname, LabelTable labels, InsnList instructionList) {
        instructionList.add(new JumpInsnNode(opcode, labels.getLabel(labelname)));
    }

    protected void pushLabel(String name, LabelTable labels, InsnList instructions) {
        // Note: name contains the colon of the label declaration, which needs to be removed
        int len = name.length();
        instructions.add(labels.getLabel(name.substring(0, len - 1)));
    }

    protected void pushVarInsn(int opcode, InsnList list, String var, LocalNameTable lnt) {
        list.add(new VarInsnNode(opcode, lnt.getVarIndex(var, opcode)));
    }

    protected String removeEscapes(String input) {
        char[] cstr = input.toCharArray();
        sharedBuilder.setLength(0);
        int len = cstr.length;
        boolean hadSlash = false;
        for (int i = 0; i < len; i++) {
            char c = cstr[i];
            if (hadSlash) {
                char newval = ESCAPED_CHARACTERS[cstr[i]];
                if (newval == 0) {
                    if (cstr[i] == 'u') {
                        int codepoint = Integer.parseInt("" + cstr[++i] + cstr[++i] + cstr[++i] + cstr[++i], 16);
                        sharedBuilder.appendCodePoint(codepoint);
                    } else {
                        sharedBuilder.append('\\');
                        sharedBuilder.append(cstr[i]);
                    }
                } else {
                    sharedBuilder.append(newval);
                }
                hadSlash = false;
            } else {
                if (c == '\\') {
                    hadSlash = true;
                } else {
                    sharedBuilder.append(cstr[i]);
                }
            }
        }
        return sharedBuilder.toString();
    }
}
