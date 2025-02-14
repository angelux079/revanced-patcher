package net.revanced.patcher

import net.revanced.patcher.patch.Patch
import net.revanced.patcher.patch.PatchResultSuccess
import net.revanced.patcher.signature.Signature
import net.revanced.patcher.util.ExtraTypes
import net.revanced.patcher.util.TestUtil
import net.revanced.patcher.writer.ASMWriter.insertAt
import net.revanced.patcher.writer.ASMWriter.setAt
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.Type
import org.objectweb.asm.tree.*
import java.io.PrintStream
import kotlin.test.Test

internal class PatcherTest {
    companion object {
        val testSigs: Array<Signature> = arrayOf(
            // Java:
            // public static void main(String[] args) {
            //     System.out.println("Hello, world!");
            // }
            // Bytecode:
            // public static main(java.lang.String[] arg0) { // Method signature: ([Ljava/lang/String;)V
            //     getstatic java/lang/System.out:java.io.PrintStream
            //     ldc "Hello, world!" (java.lang.String)
            //     invokevirtual java/io/PrintStream.println(Ljava/lang/String;)V
            //     return
            // }
            Signature(
                "mainMethod",
                Type.VOID_TYPE,
                ACC_PUBLIC or ACC_STATIC,
                arrayOf(ExtraTypes.ArrayAny),
                arrayOf(
                    LDC,
                    INVOKEVIRTUAL
                )
            )
        )
    }

    @Test
    fun testPatcher() {
        val testData = PatcherTest::class.java.getResourceAsStream("/test1.jar")!!
        val patcher = Patcher(testData, testSigs)

        patcher.addPatches(
            Patch ("TestPatch") {
                // Get the method from the resolver cache
                val mainMethod = patcher.cache.methods["mainMethod"]
                // Get the instruction list
                val instructions = mainMethod.method.instructions!!

                // Let's modify it, so it prints "Hello, ReVanced! Editing bytecode."
                // Get the start index of our opcode pattern.
                // This will be the index of the LDC instruction.
                val startIndex = mainMethod.scanData.startIndex
                TestUtil.assertNodeEqual(LdcInsnNode("Hello, world!"), instructions[startIndex]!!)
                // Create a new LDC node and replace the LDC instruction.
                val stringNode = LdcInsnNode("Hello, ReVanced! Editing bytecode.")
                instructions.setAt(startIndex, stringNode)

                // Now lets print our string twice!
                // Insert our instructions after the second instruction by our pattern.
                // This will place our instructions after the original INVOKEVIRTUAL call.
                // You could also copy the instructions from the list and then modify the LDC instruction again,
                // but this is to show a more advanced example of writing bytecode using the patcher and ASM.
                instructions.insertAt(
                    startIndex + 1,
                    FieldInsnNode(
                        GETSTATIC,
                        Type.getInternalName(System::class.java), // "java/io/System"
                        "out",
                        Type.getInternalName(PrintStream::class.java) // "java.io.PrintStream"
                    ),
                    LdcInsnNode("Hello, ReVanced! Adding bytecode."),
                    MethodInsnNode(
                        INVOKEVIRTUAL,
                        Type.getInternalName(PrintStream::class.java), // "java/io/PrintStream"
                        "println",
                        Type.getMethodDescriptor(
                            Type.VOID_TYPE,
                            Type.getType(String::class.java)
                        ) // "(Ljava/lang/String;)V"
                    )
                )

                // Our code now looks like this:
                // public static main(java.lang.String[] arg0) { // Method signature: ([Ljava/lang/String;)V
                //     getstatic java/lang/System.out:java.io.PrintStream
                //     ldc "Hello, ReVanced! Editing bytecode." (java.lang.String) // We overwrote this instruction.
                //     invokevirtual java/io/PrintStream.println(Ljava/lang/String;)V
                //     getstatic java/lang/System.out:java.io.PrintStream // This instruction and the 2 instructions below are written manually.
                //     ldc "Hello, ReVanced! Adding bytecode." (java.lang.String)
                //     invokevirtual java/io/PrintStream.println(Ljava/lang/String;)V
                //     return
                // }

                // Finally, tell the patcher that this patch was a success.
                // You can also return PatchResultError with a message.
                // If an exception is thrown inside this function,
                // a PatchResultError will be returned with the error message.
                PatchResultSuccess()
            }
        )

        // Apply all patches loaded in the patcher
        val result = patcher.applyPatches()
        // You can check if an error occurred
        for ((s, r) in result) {
            if (r.isFailure) {
                throw Exception("Patch $s failed", r.exceptionOrNull()!!)
            }
        }

        // TODO Doesn't work, needs to be fixed.
        //val out = ByteArrayOutputStream()
        //patcher.saveTo(out)
        //assertTrue(
        //    // 8 is a random value, it's just weird if it's any lower than that
        //    out.size() > 8,
        //    "Output must be at least 8 bytes"
        //)
        //
        //out.close()
        testData.close()
    }

    // TODO Doesn't work, needs to be fixed.
    //@Test
    //fun `test patcher with no changes`() {
    //    val testData = PatcherTest::class.java.getResourceAsStream("/test1.jar")!!
    //    val available = testData.available()
    //    val patcher = Patcher(testData, testSigs)
    //
    //    val out = ByteArrayOutputStream()
    //    patcher.saveTo(out)
    //    assertEquals(available, out.size())
    //
    //    out.close()
    //    testData.close()
    //}
}