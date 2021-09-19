package de.geolykt.starloader.bcdiff;

import java.util.Locale;

import org.objectweb.asm.Opcodes;

public final class LookupUtils {

    public static int getFieldAccessFlag(String modifier) {
        // public, protected, private, static, final, transient & volatile are permitted in source code
        // synthetic & enum are also inserted by the compiler
        switch (modifier.toLowerCase(Locale.US)) {
        case "public":
            return Opcodes.ACC_PUBLIC;
        case "protected":
            return Opcodes.ACC_PROTECTED;
        case "private":
            return Opcodes.ACC_PRIVATE;
        case "static":
            return Opcodes.ACC_STATIC;
        case "final":
            return Opcodes.ACC_FINAL;
        case "transient":
            return Opcodes.ACC_TRANSIENT;
        case "volatile":
            return Opcodes.ACC_VOLATILE;
        case "synthetic":
            return Opcodes.ACC_SYNTHETIC;
        case "enum":
            return Opcodes.ACC_ENUM;
        default:
            throw new IllegalArgumentException("Access modifier " + modifier + " is not a known modifier.");
        }
    }

    public static int getMethodAccessFlag(String modifier) {
        // public, protected, private, abstract, static, final, synchronized, native & strictfp are permitted in source code
        // synthetic, varargs & bridge are also inserted by the compiler
        switch (modifier.toLowerCase(Locale.US)) {
        case "public":
            return Opcodes.ACC_PUBLIC;
        case "protected":
            return Opcodes.ACC_PROTECTED;
        case "private":
            return Opcodes.ACC_PRIVATE;
        case "abstract":
            return Opcodes.ACC_ABSTRACT;
        case "static":
            return Opcodes.ACC_STATIC;
        case "final":
            return Opcodes.ACC_FINAL;
        case "synchronized":
            return Opcodes.ACC_SYNCHRONIZED;
        case "native":
            return Opcodes.ACC_NATIVE;
        case "strictfp":
            return Opcodes.ACC_STRICT;
        case "synthetic":
            return Opcodes.ACC_SYNTHETIC;
        case "bridge":
            return Opcodes.ACC_BRIDGE;
        case "varargs":
            return Opcodes.ACC_VARARGS;
        default:
            throw new IllegalArgumentException("Access modifier " + modifier + " is not a known modifier.");
        }
    }

    private LookupUtils() {}
}
