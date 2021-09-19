# Java-bytecode-differential

 I wasn't able find any competent disassemblers and reassemblers that were useable from java code as a library.
 Since I always wanted to do patches in an easily human-readable way, I also added it to the things it can do.
 This application can be run as a standalone application via CLI (although right now only to patch things)
 or as a library.

## Using the disassembler from code.

 The simplest way to disassemble a class is to convert that class to an Objectweb ASM ClassNode
 and call the DeltaGenerator#generateBytecode(ClassNode) method. Alternatively you can do it yourself
 with the BytecodeGeneratorVisitor class.

## Using the reassembler from code

 The easiest way is to invoke the cosntructor of the SLAssembler class. Why the constructor? Because
 I didn't want to use static and I haven't thought it out all the way when I started creating it.

## Drawbacks

 It uses a custom format to assemble the code into and disassembles the methods and fields via Recaf and stitched together
 via an in-house application, though reassembly is done
 manually. Because two different applicaions handle method/field assembly/disassembly there are following limitations:

 - Local variables could be remapped in a sub-optimal fashion. This can be negated by using the SKIP_DEBUG flag
   in your ClassReader. This is done by default in CLI mode.
 - Literal strings could have escapes stripped when they shouldn't be or escapeed when not needed. However for
   normal classes, this is not an issue and is only potent rubbish data are stored as literal strings.
 - Annotations are not supported
 - Attributes are not supported
 - Larger jar size due to shadowing recaf
