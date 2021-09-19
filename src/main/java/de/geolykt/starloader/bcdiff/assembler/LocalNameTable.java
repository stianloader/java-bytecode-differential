package de.geolykt.starloader.bcdiff.assembler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.objectweb.asm.Opcodes;

import de.geolykt.starloader.bcdiff.assembler.DefHeader.MethodDefHeader;
import de.geolykt.starloader.bcdiff.assembler.DefHeader.MethodParameter;

public class LocalNameTable {

    private final Map<String, Integer> locals = new HashMap<>();
    private final List<String> localnames = new ArrayList<>();

    public LocalNameTable(MethodDefHeader header) {
        MethodParameter[] methodArgs = header.getParams();
        int parameterCount = methodArgs.length;
        if ((header.getAccess() & Opcodes.ACC_STATIC) == 0) {
            localnames.add(0, "this");
            locals.put("this", 0);
        }
        for (int i = 0; i < parameterCount; i++) {
            MethodParameter param = methodArgs[i];
            String name = param.getName();
            String desc = param.getDesc();
            try {
                int varIndex = Integer.parseInt(name);
                locals.put(name, varIndex);
                localnames.add(varIndex, name);
                if (desc.equals("J") || desc.equals("D")) {
                    // In java, longs and doubles occupy 2 slots in the lvt. How fun.
                    localnames.add(varIndex + 1, null);
                }
            } catch (NumberFormatException expected) {
                int varIndex = localnames.size();
                locals.put(name, varIndex);
                localnames.add(varIndex, name);
                if (desc.equals("J") || desc.equals("D")) {
                    // In java, longs and doubles occupy 2 slots in the lvt. How fun.
                    localnames.add(varIndex + 1, null);
                }
            }
        }
    }

    public int getVarIndex(String var, int opcode) {
        Integer index = locals.get(var);
        if (index == null) {
            try {
                index = Integer.valueOf(var);
                if (index != localnames.size()) {
                    return index;
                }
                localnames.add(index, var);
                locals.put(var, index);
                if (opcode == Opcodes.LLOAD || opcode == Opcodes.DLOAD || opcode == Opcodes.LSTORE || opcode == Opcodes.DSTORE) {
                    localnames.add(index + 1, var);
                }
            } catch (NumberFormatException expected) {
                index = localnames.size();
                locals.put(var, index);
                localnames.add(index, var);
                if (opcode == Opcodes.LLOAD || opcode == Opcodes.DLOAD || opcode == Opcodes.LSTORE || opcode == Opcodes.DSTORE) {
                    localnames.add(index + 1, var);
                }
            }
        }
        return index;
    }

    public void pushName(int index, String name) {
        locals.put(name, index);
        // FIXME dangerous stuff
    }
}
