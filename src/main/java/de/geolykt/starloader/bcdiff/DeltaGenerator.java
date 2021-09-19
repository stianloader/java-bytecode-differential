package de.geolykt.starloader.bcdiff;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

import com.github.difflib.DiffUtils;
import com.github.difflib.UnifiedDiffUtils;
import com.github.difflib.patch.Patch;
import com.github.difflib.patch.PatchFailedException;

import de.geolykt.starloader.bcdiff.assembler.SLAssmbler;
import de.geolykt.starloader.bcdiff.disassembler.BytecodeGeneratorVisitor;

/**
 * Entrypoint class for bytecode delta generation.
 */
public class DeltaGenerator {

    public static void printHelp() {
        System.out.println("=== Java-bytecode-differential help ===");
        System.out.println(" generate <originalJar> <revisedJar> <ctx>: prints a UnifiedDiff between the bytecode of the two jars to console. The amount of context line is given with ctx.");
        System.out.println(" apply    <originalJar> <patch> <outputJar>: Patches a jar with a patch created with the generate command.");
    }

    public static void main(String[] args) {
        if (args.length == 0 || args[0].equals("help")) {
            printHelp();
            System.exit(0);
        }
        if (args[0].equals("generate")) {
            if (!(args.length == 4 || args.length == 5)) {
                System.err.println("Invalid argument count. The argument count MUST be 4 or 5 for the generate command.");
                printHelp();
                System.exit(1);
            }
            String[] packageFilter = new String[0];
            if (args.length > 4) {
                packageFilter = args[4].split(",");
            }
            try (JarFile original = new JarFile(args[1])) {
                try (JarFile revised = new JarFile(args[2])) {
                    new DeltaGenerator().generatePatch(original, revised, Integer.valueOf(args[3]), packageFilter).forEach(System.out::println);;
                }
            } catch (Throwable e) {
                System.err.println("Unable to perform action (broken jars?)");
                e.printStackTrace();
                System.exit(1);
            }
        } else if (args[0].equals("apply")) {
            if (args.length != 4) {
                System.err.println("Invalid argument count. The argument count MUST be 4 for the apply command.");
                printHelp();
                System.exit(1);
            }
            try (JarFile original = new JarFile(args[1])) {
                List<String> patch = new ArrayList<>();
                try (BufferedReader br = new BufferedReader(new FileReader(args[2]))) {
                    for (String read = br.readLine(); read != null; read = br.readLine()) {
                        patch.add(read);
                    }
                }
                new DeltaGenerator().applyPatch(original, new File(args[3]), patch);
            } catch (Throwable e) {
                System.err.println("Unable to perform action (broken jars?)");
                e.printStackTrace();
                System.exit(1);
            }
        } else {
            System.err.println("Unknown command: " + args[0]);
            printHelp();
            System.exit(1);
        }
    }

    private static final Method READ_ALL_BYTES;

    static {
        Method temp = null;
        try {
            temp = InputStream.class.getMethod("readAllBytes");
        } catch (NoSuchMethodException ignored) { }
        READ_ALL_BYTES = temp;
    }

    private static final byte[] TEMPORARY_CACHE = new byte[4096];

    // TODO Make it work with concurrency
    public synchronized byte[] readAllBytes(InputStream is) throws IOException {
        if (READ_ALL_BYTES != null) {
            try {
                return (byte[]) READ_ALL_BYTES.invoke(is);
            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                throw new IOException(e);
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        while (true) {
            int len = is.read(TEMPORARY_CACHE);
            if (len != 4096) {
                if (len != -1) {
                    out.write(TEMPORARY_CACHE, 0, len);
                }
                break;
            } else {
                out.write(TEMPORARY_CACHE);
            }
        }
        return out.toByteArray();
    }

    public void applyPatch(JarFile original, File revised, List<String> fullPatch) throws IOException, PatchFailedException {
        try (JarOutputStream os = new JarOutputStream(new FileOutputStream(revised))) {
            Map<String, ClassNode> originalNodes = new HashMap<>();
            Map<String, ClassNode> allNodes = new HashMap<>();
            Map<String, byte[]> resources = new HashMap<>();

            Enumeration<JarEntry> entries = original.entries();
            while (entries.hasMoreElements()) {
                JarEntry next = entries.nextElement();
                try (InputStream isTest = original.getInputStream(next)) {
                    if (next.getName().endsWith(".jnilib")) {
                        resources.put(next.getName(), readAllBytes(isTest));
                        isTest.close();
                        continue; // apparently these files also start with the 0xCAFEBABE prefix.
                    }
                    boolean validClassFile = true;
                    if (isTest.read() != 0xCA || isTest.read() != 0xFE || isTest.read() != 0xBA || isTest.read() != 0xBE) {
                        validClassFile = false;
                    }
                    isTest.close();
                    if (validClassFile) {
                        // InflaterInputStream sadly does not support mark/reset, so we have to do it the dirty way.
                        try (InputStream is = original.getInputStream(next)) {
                            ClassReader cr = new ClassReader(is);
                            ClassNode result = new ClassNode();
                            cr.accept(result, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                            originalNodes.put(next.getName(), result);
                            allNodes.put(result.name, result);
                        }
                    }
                    try (InputStream is = original.getInputStream(next)) {
                        resources.put(next.getName(), readAllBytes(is));
                    }
                }
            }

            List<List<String>> patches = new ArrayList<>();
            List<String> currPatch = new ArrayList<>();
            for (String ln : fullPatch) {
                if (ln.charAt(0) == '-' && ln.charAt(1) == '-' && ln.charAt(2) == '-') {
                    if (!currPatch.isEmpty()) {
                        patches.add(currPatch);
                        currPatch = new ArrayList<>();
                    }
                    currPatch.add(ln);
                } else if (ln.charAt(0) != '#') {
                    currPatch.add(ln);
                }
            }
            if (!currPatch.isEmpty()) {
                patches.add(currPatch);
            }

            for (List<String> patchLines : patches) {
                String originalName = patchLines.get(0).substring(4);
                String revisedName = patchLines.get(1).substring(4);
                Patch<String> diffUtilsPatch = UnifiedDiffUtils.parseUnifiedDiff(patchLines);

                if (originalName.equals("/dev/null")) {
                    // completely new file
                    List<String> newBytecode = DiffUtils.patch(Collections.emptyList(), diffUtilsPatch);
                    ClassNode out = new ClassNode();
                    new SLAssmbler(newBytecode.toArray(new String[0]), out);
                    ClassWriter cw = new FrameComputingClasswriter(0, allNodes);
                    out.accept(cw);
                    resources.put(revisedName, cw.toByteArray());
                } else if (revisedName.equals("/dev/null")) {
                    // deleted file
                    originalNodes.remove(originalName);
                } else if (!originalName.equals(revisedName)){
                    // renamed file
                    throw new UnsupportedOperationException("Renaming classes not yet implemented");
                } else {
                    // basic patch
                    ClassNode node = originalNodes.remove(originalName);
                    List<String> newBytecode = DiffUtils.patch(generateBytecode(node), diffUtilsPatch);
                    ClassNode out = new ClassNode();
                    new SLAssmbler(newBytecode.toArray(new String[0]), out);
                    ClassWriter cw = new FrameComputingClasswriter(0, allNodes);
                    out.accept(cw);
                    resources.put(revisedName, cw.toByteArray());
                }
            }

            for (Map.Entry<String, byte[]> data : resources.entrySet()) {
                os.putNextEntry(new JarEntry(data.getKey()));
                os.write(data.getValue());
                os.closeEntry();
            }
//            patch.forEach(System.out::println);
        }
    }

    public Map<String, ClassNode> mapNodes(JarFile input) throws IOException {
        Enumeration<JarEntry> entries = input.entries();
        Map<String, ClassNode> out = new LinkedHashMap<>();

        while (entries.hasMoreElements()) {
            JarEntry next = entries.nextElement();
            try (InputStream isTest = input.getInputStream(next)) {
                if (next.getName().endsWith(".jnilib")) {
                    continue; // apparently these files also start with the 0xCAFEBABE prefix.
                }
                if (isTest.read() != 0xCA || isTest.read() != 0xFE || isTest.read() != 0xBA || isTest.read() != 0xBE) {
                    isTest.close();
                    continue;
                }
                isTest.close();
                // InflaterInputStream sadly does not support mark/reset, so we have to do it the dirty way.
                try (InputStream is = input.getInputStream(next)) {
                    ClassReader cr = new ClassReader(is);
                    ClassNode result = new ClassNode();
                    cr.accept(result, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                    out.put(next.getName(), result);
                }
            }
        }

        return out;
    }

    public List<String> generatePatch(JarFile original, JarFile revised, int context, String[] packageWhitelist) throws IOException {
        Map<String, ClassNode> originalNodes = mapNodes(original);
        Map<String, ClassNode> revisedNodes = mapNodes(revised);
        List<String> output = new ArrayList<>();

        for (Map.Entry<String, ClassNode> onode : originalNodes.entrySet()) {
            if (packageWhitelist.length != 0) {
                boolean allow = false;
                for (String filter : packageWhitelist) {
                    if (onode.getKey().startsWith(filter)) {
                        allow = true;
                        break;
                    }
                }
                if (!allow) {
                    continue;
                }
            }
            List<String> originalBytecode = generateBytecode(onode.getValue());
            ClassNode rNode = revisedNodes.remove(onode.getKey());
            if (rNode == null) {
                Patch<String> patch = DiffUtils.diff(originalBytecode, Collections.emptyList());
                output.addAll(UnifiedDiffUtils.generateUnifiedDiff(onode.getKey(), "/dev/null", originalBytecode, patch, context));
            } else {
                output.addAll(generateUnifiedDiff(onode.getValue(), onode.getKey(), rNode, onode.getKey(), context));
            }
        }

        for (Map.Entry<String, ClassNode> surplusRevisedNode : revisedNodes.entrySet()) {
            if (packageWhitelist.length != 0) {
                boolean allow = false;
                for (String filter : packageWhitelist) {
                    if (surplusRevisedNode.getKey().startsWith(filter)) {
                        allow = true;
                        break;
                    }
                }
                if (!allow) {
                    continue;
                }
            }
            List<String> revisedBytecode = generateBytecode(surplusRevisedNode.getValue());
            Patch<String> patch = DiffUtils.diff(Collections.emptyList(), revisedBytecode);
            output.addAll(UnifiedDiffUtils.generateUnifiedDiff("/dev/null", surplusRevisedNode.getKey(), Collections.emptyList(), patch, context));
        }
        return output;
    }

    public List<String> generateUnifiedDiff(ClassNode nodeA, String nameA, ClassNode nodeB, String nameB, int contextLines) {
        List<String> nodeABytecode = generateBytecode(nodeA);
        Patch<String> diff = DiffUtils.diff(nodeABytecode, generateBytecode(nodeB));
        return UnifiedDiffUtils.generateUnifiedDiff(nameA, nameB, nodeABytecode, diff, contextLines);
    }

    public Patch<String> generatePatch(ClassNode nodeA, ClassNode nodeB) {
        return DiffUtils.diff(generateBytecode(nodeA), generateBytecode(nodeB));
    }

    public List<String> generateBytecode(ClassNode node) {
        List<String> output = new ArrayList<>();
        ClassVisitor visitor = new BytecodeGeneratorVisitor(output);
        node.accept(visitor);
        return output;
    }
}
