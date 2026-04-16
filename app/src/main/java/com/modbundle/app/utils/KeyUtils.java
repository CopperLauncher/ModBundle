package com.modbundle.app.utils;

public class KeyUtils {
    // XOR key - "ModBundle" in bytes
    private static final byte[] XOR = {0x4D, 0x6F, 0x64, 0x56, 0x61, 0x75, 0x6C, 0x74};

    public static String decode(byte[] obfuscated) {
        char[] result = new char[obfuscated.length];
        for (int i = 0; i < obfuscated.length; i++) {
            result[i] = (char)(obfuscated[i] ^ XOR[i % XOR.length]);
        }
        return new String(result);
    }
}
