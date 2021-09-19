package de.geolykt.starloader.bcdiff.assembler;

import de.geolykt.starloader.bcdiff.LookupUtils;

public class DefHeader {
    public static class MethodDefHeader extends DefHeader {

        private static String genDesc(MethodParameter[] params, String returnType) {
            StringBuilder builder = new StringBuilder();
            builder.append('(');
            for (MethodParameter param : params) {
                builder.append(param.getDesc());
            }
            builder.append(')');
            builder.append(returnType);
            return builder.toString();
        }
        private final MethodParameter[] params;

        private final String returnType;

        public MethodDefHeader(int access, String name, MethodParameter[] params, String returnType) {
            super(access, name, genDesc(params, returnType));
            this.params = params;
            this.returnType = returnType;
        }

        public MethodParameter[] getParams() {
            return params;
        }

        public String getReturnType() {
            return returnType;
        }
    }
    public static class MethodParameter {
        private final String desc;
        private final String name;

        public MethodParameter(String desc, String name) {
            this.desc = desc;
            this.name = name;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (obj instanceof MethodParameter) {
                MethodParameter other = (MethodParameter) obj;
                if (this.name == null) {
                    return other.name == null && other.desc.equals(this.desc);
                } else {
                    return this.name.equals(other.name) && other.desc.equals(this.desc);
                }
            } else {
                return false;
            }
        }

        public String getDesc() {
            return desc;
        }

        public String getName() {
            return name;
        }

        @Override
        public int hashCode() {
            if (name == null) {
                return desc.hashCode();
            }
            return desc.hashCode() ^ name.hashCode();
        }
    }
    public static DefHeader parseFieldDef(String def) {
        String[] definitionTokens = def.split(" ");
        if (!definitionTokens[0].equals("DEFINE")) {
            throw new IllegalStateException("First line does not start with DEFINE");
        }
        int access;
        if (definitionTokens[1].length() == 0) {
            access = 0; // no access flags
        } else {
            int endOfFlags = definitionTokens.length - 2;
            access = 0;
            for (int i = 1; i < endOfFlags; i++) {
                access |= LookupUtils.getFieldAccessFlag(definitionTokens[i]);
            }
        }
        String desc = definitionTokens[definitionTokens.length - 2];
        String name = definitionTokens[definitionTokens.length - 1];
        return new DefHeader(access, name, desc);
    }

    public static MethodDefHeader parseMethodDef(String def) {
        String[] definitionTokens = def.split(" ", 2);
        if (!definitionTokens[0].equals("DEFINE")) {
            throw new IllegalStateException("First line does not start with DEFINE");
        }
        def = definitionTokens[1];
        int descStart = def.indexOf('(');
        String[] accessFlagsAndName = def.substring(0, descStart).split(" ");
        String name = accessFlagsAndName[accessFlagsAndName.length - 1];

        int access;
        if (accessFlagsAndName[0].length() == 0) {
            access = 0; // no access flags
        } else {
            access = 0;
            int endOfFlags = accessFlagsAndName.length - 1;
            for (int i = 0; i < endOfFlags; i++) {
                access |= LookupUtils.getMethodAccessFlag(accessFlagsAndName[i]);
            }
        }
        int retStart = def.lastIndexOf(')');
        String[] rawParams = def.substring(descStart + 1, retStart).split(",");
        MethodParameter[] params = null;
        if (rawParams.length == 1 && rawParams[0].length() == 0) {
            params = new MethodParameter[0];
        } else {
            params = new MethodParameter[rawParams.length];
            for (int i = 0; i < rawParams.length; i++) {
                String[] raw = rawParams[i].trim().split(" ");
                if (raw.length != 2) {
                    throw new IllegalStateException("Parameter has too many tokens.");
                }
                params[i] = new MethodParameter(raw[0], raw[1]);
            }
        }
        return new MethodDefHeader(access, name, params, def.substring(retStart + 1));
    }

    private final int access;

    private final String name;

    private final String desc;

    public DefHeader(int access, String name, String desc) {
        this.access = access;
        this.name = name;
        this.desc = desc;
    }

    public int getAccess() {
        return access;
    }

    public String getDesc() {
        return desc;
    }

    public String getName() {
        return name;
    }
}
