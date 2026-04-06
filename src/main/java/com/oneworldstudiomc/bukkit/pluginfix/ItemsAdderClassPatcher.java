package com.oneworldstudiomc.bukkit.pluginfix;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

final class ItemsAdderClassPatcher {

    private static final int GETFIELD = 180;
    private static final int INVOKESTATIC = 184;
    private static final int INVOKEVIRTUAL = 182;

    private ItemsAdderClassPatcher() {
    }

    static byte[] patchYkClass(byte[] basicClass) {
        try {
            ClassFile classFile = ClassFile.parse(basicClass);
            int getPlayerListRef = classFile.findMethodRef(
                    "net/minecraft/server/MinecraftServer",
                    "ah",
                    "()Lnet/minecraft/server/players/PlayerList;"
            );
            int getRegistriesRef = classFile.findMethodRef(
                    "net/minecraft/server/MinecraftServer",
                    "bd",
                    "()Lnet/minecraft/core/LayeredRegistryAccess;"
            );
            int playersFieldRef = classFile.findFieldRef(
                    "net/minecraft/server/players/PlayerList",
                    "l",
                    "Ljava/util/List;"
            );
            int sendPacketRef = classFile.findMethodRef(
                    "net/minecraft/server/network/PlayerConnection",
                    "b",
                    "(Lnet/minecraft/network/protocol/Packet;)V"
            );

            int compatPlayerListRef = classFile.addMethodRef(
                    "com/oneworldstudiomc/bukkit/pluginfix/PluginFixManager",
                    "itemsAdderGetPlayerListCompat",
                    "(Lnet/minecraft/server/MinecraftServer;)Lnet/minecraft/server/players/PlayerList;"
            );
            int compatRegistriesRef = classFile.addMethodRef(
                    "com/oneworldstudiomc/bukkit/pluginfix/PluginFixManager",
                    "itemsAdderGetRegistriesCompat",
                    "(Lnet/minecraft/server/MinecraftServer;)Lnet/minecraft/core/LayeredRegistryAccess;"
            );
            int compatPlayersRef = classFile.addMethodRef(
                    "com/oneworldstudiomc/bukkit/pluginfix/PluginFixManager",
                    "itemsAdderGetPlayersCompat",
                    "(Ljava/lang/Object;)Ljava/util/List;"
            );
            int compatSendPacketRef = classFile.addMethodRef(
                    "com/oneworldstudiomc/bukkit/pluginfix/PluginFixManager",
                    "itemsAdderSendPacketCompat",
                    "(Ljava/lang/Object;Lnet/minecraft/network/protocol/Packet;)V"
            );

            boolean patched = classFile.patchCode(
                    getPlayerListRef,
                    compatPlayerListRef,
                    getRegistriesRef,
                    compatRegistriesRef,
                    playersFieldRef,
                    compatPlayersRef,
                    sendPacketRef,
                    compatSendPacketRef
            );
            return patched ? classFile.write() : basicClass;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to patch ItemsAdder class", exception);
        }
    }

    private static final class ClassFile {

        private final int minorVersion;
        private final int majorVersion;
        private final List<ConstantPoolEntry> constantPool;
        private final int accessFlags;
        private final int thisClass;
        private final int superClass;
        private final int[] interfaces;
        private final List<byte[]> fields;
        private final List<MethodInfo> methods;
        private final List<RawAttribute> attributes;

        private ClassFile(
                int minorVersion,
                int majorVersion,
                List<ConstantPoolEntry> constantPool,
                int accessFlags,
                int thisClass,
                int superClass,
                int[] interfaces,
                List<byte[]> fields,
                List<MethodInfo> methods,
                List<RawAttribute> attributes
        ) {
            this.minorVersion = minorVersion;
            this.majorVersion = majorVersion;
            this.constantPool = constantPool;
            this.accessFlags = accessFlags;
            this.thisClass = thisClass;
            this.superClass = superClass;
            this.interfaces = interfaces;
            this.fields = fields;
            this.methods = methods;
            this.attributes = attributes;
        }

        static ClassFile parse(byte[] bytes) throws IOException {
            ClassReader reader = new ClassReader(bytes);
            int magic = reader.readInt();
            if (magic != 0xCAFEBABE) {
                throw new IOException("Invalid class file magic: " + Integer.toHexString(magic));
            }

            int minorVersion = reader.readUnsignedShort();
            int majorVersion = reader.readUnsignedShort();
            int constantPoolCount = reader.readUnsignedShort();
            List<ConstantPoolEntry> constantPool = new ArrayList<>(constantPoolCount);
            constantPool.add(null);

            for (int index = 1; index < constantPoolCount; index++) {
                int tag = reader.readUnsignedByte();
                ConstantPoolEntry entry = switch (tag) {
                    case 1 -> {
                        byte[] utf8Bytes = reader.readBytes(reader.readUnsignedShort());
                        yield new Utf8Entry(utf8Bytes);
                    }
                    case 7 -> new ClassEntry(reader.readUnsignedShort());
                    case 9, 10, 11 -> new RefEntry(tag, reader.readUnsignedShort(), reader.readUnsignedShort());
                    case 12 -> new NameAndTypeEntry(reader.readUnsignedShort(), reader.readUnsignedShort());
                    case 3, 4 -> new RawEntry(tag, reader.readBytes(4));
                    case 5, 6 -> {
                        ConstantPoolEntry longEntry = new RawEntry(tag, reader.readBytes(8));
                        constantPool.add(longEntry);
                        constantPool.add(null);
                        index++;
                        yield null;
                    }
                    case 8, 16, 19, 20 -> new RawEntry(tag, shortBytes(reader.readUnsignedShort()));
                    case 15 -> new RawEntry(tag, new byte[]{(byte) reader.readUnsignedByte(), (byte) (reader.readUnsignedShort() >>> 8), (byte) reader.peekLastUnsignedShort()});
                    case 17, 18 -> new RawEntry(tag, fourBytes(reader.readUnsignedShort(), reader.readUnsignedShort()));
                    default -> throw new IOException("Unsupported constant pool tag: " + tag);
                };
                if (entry != null) {
                    constantPool.add(entry);
                }
            }

            int accessFlags = reader.readUnsignedShort();
            int thisClass = reader.readUnsignedShort();
            int superClass = reader.readUnsignedShort();

            int[] interfaces = new int[reader.readUnsignedShort()];
            for (int index = 0; index < interfaces.length; index++) {
                interfaces[index] = reader.readUnsignedShort();
            }

            List<byte[]> fields = new ArrayList<>();
            int fieldsCount = reader.readUnsignedShort();
            for (int index = 0; index < fieldsCount; index++) {
                fields.add(reader.readMemberRaw());
            }

            List<MethodInfo> methods = new ArrayList<>();
            int methodsCount = reader.readUnsignedShort();
            for (int index = 0; index < methodsCount; index++) {
                methods.add(reader.readMethod(constantPool));
            }

            List<RawAttribute> attributes = reader.readAttributes(constantPool);
            return new ClassFile(minorVersion, majorVersion, constantPool, accessFlags, thisClass, superClass, interfaces, fields, methods, attributes);
        }

        int findMethodRef(String owner, String name, String desc) {
            return findRef(10, owner, name, desc);
        }

        int findFieldRef(String owner, String name, String desc) {
            return findRef(9, owner, name, desc);
        }

        private int findRef(int tag, String owner, String name, String desc) {
            for (int index = 1; index < constantPool.size(); index++) {
                ConstantPoolEntry entry = constantPool.get(index);
                if (!(entry instanceof RefEntry refEntry) || refEntry.tag != tag) {
                    continue;
                }
                String entryOwner = className(refEntry.classIndex);
                NameAndTypeEntry nameAndType = nameAndType(refEntry.nameAndTypeIndex);
                if (owner.equals(entryOwner) && name.equals(utf8(nameAndType.nameIndex)) && desc.equals(utf8(nameAndType.descriptorIndex))) {
                    return index;
                }
            }
            return -1;
        }

        int addMethodRef(String owner, String name, String desc) {
            int existing = findMethodRef(owner, name, desc);
            if (existing != -1) {
                return existing;
            }
            int classIndex = addClass(owner);
            int nameAndTypeIndex = addNameAndType(name, desc);
            constantPool.add(new RefEntry(10, classIndex, nameAndTypeIndex));
            return constantPool.size() - 1;
        }

        private int addClass(String internalName) {
            for (int index = 1; index < constantPool.size(); index++) {
                ConstantPoolEntry entry = constantPool.get(index);
                if (entry instanceof ClassEntry classEntry && internalName.equals(utf8(classEntry.nameIndex))) {
                    return index;
                }
            }
            int nameIndex = addUtf8(internalName);
            constantPool.add(new ClassEntry(nameIndex));
            return constantPool.size() - 1;
        }

        private int addNameAndType(String name, String desc) {
            for (int index = 1; index < constantPool.size(); index++) {
                ConstantPoolEntry entry = constantPool.get(index);
                if (!(entry instanceof NameAndTypeEntry nameAndTypeEntry)) {
                    continue;
                }
                if (name.equals(utf8(nameAndTypeEntry.nameIndex)) && desc.equals(utf8(nameAndTypeEntry.descriptorIndex))) {
                    return index;
                }
            }
            int nameIndex = addUtf8(name);
            int descIndex = addUtf8(desc);
            constantPool.add(new NameAndTypeEntry(nameIndex, descIndex));
            return constantPool.size() - 1;
        }

        private int addUtf8(String value) {
            for (int index = 1; index < constantPool.size(); index++) {
                ConstantPoolEntry entry = constantPool.get(index);
                if (entry instanceof Utf8Entry utf8Entry && value.equals(utf8Entry.value())) {
                    return index;
                }
            }
            constantPool.add(new Utf8Entry(value.getBytes(StandardCharsets.UTF_8)));
            return constantPool.size() - 1;
        }

        boolean patchCode(
                int getPlayerListRef,
                int compatPlayerListRef,
                int getRegistriesRef,
                int compatRegistriesRef,
                int playersFieldRef,
                int compatPlayersRef,
                int sendPacketRef,
                int compatSendPacketRef
        ) throws IOException {
            boolean patched = false;
            for (MethodInfo method : methods) {
                CodeAttribute codeAttribute = method.findCodeAttribute();
                if (codeAttribute == null) {
                    continue;
                }
                patched |= patchMethodCode(
                        codeAttribute.code,
                        getPlayerListRef,
                        compatPlayerListRef,
                        getRegistriesRef,
                        compatRegistriesRef,
                        playersFieldRef,
                        compatPlayersRef,
                        sendPacketRef,
                        compatSendPacketRef
                );
            }
            return patched;
        }

        byte[] write() throws IOException {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(outputStream);

            output.writeInt(0xCAFEBABE);
            output.writeShort(minorVersion);
            output.writeShort(majorVersion);
            output.writeShort(constantPool.size());
            for (int index = 1; index < constantPool.size(); index++) {
                ConstantPoolEntry entry = constantPool.get(index);
                if (entry == null) {
                    continue;
                }
                output.writeByte(entry.tag());
                entry.write(output);
            }

            output.writeShort(accessFlags);
            output.writeShort(thisClass);
            output.writeShort(superClass);
            output.writeShort(interfaces.length);
            for (int iface : interfaces) {
                output.writeShort(iface);
            }

            output.writeShort(fields.size());
            for (byte[] field : fields) {
                output.write(field);
            }

            output.writeShort(methods.size());
            for (MethodInfo method : methods) {
                method.write(output);
            }

            output.writeShort(attributes.size());
            for (RawAttribute attribute : attributes) {
                attribute.write(output);
            }

            output.flush();
            return outputStream.toByteArray();
        }

        private String className(int classIndex) {
            ConstantPoolEntry entry = constantPool.get(classIndex);
            if (!(entry instanceof ClassEntry classEntry)) {
                throw new IllegalStateException("Expected class entry at index " + classIndex);
            }
            return utf8(classEntry.nameIndex);
        }

        private NameAndTypeEntry nameAndType(int index) {
            ConstantPoolEntry entry = constantPool.get(index);
            if (!(entry instanceof NameAndTypeEntry nameAndTypeEntry)) {
                throw new IllegalStateException("Expected name and type at index " + index);
            }
            return nameAndTypeEntry;
        }

        private String utf8(int index) {
            ConstantPoolEntry entry = constantPool.get(index);
            if (!(entry instanceof Utf8Entry utf8Entry)) {
                throw new IllegalStateException("Expected UTF8 entry at index " + index);
            }
            return utf8Entry.value();
        }
    }

    private static boolean patchMethodCode(
            byte[] code,
            int getPlayerListRef,
            int compatPlayerListRef,
            int getRegistriesRef,
            int compatRegistriesRef,
            int playersFieldRef,
            int compatPlayersRef,
            int sendPacketRef,
            int compatSendPacketRef
    ) throws IOException {
        boolean patched = false;
        for (int offset = 0; offset < code.length; ) {
            int opcode = code[offset] & 0xFF;

            if (opcode == INVOKEVIRTUAL) {
                int refIndex = readUnsignedShort(code, offset + 1);
                if (refIndex == getPlayerListRef && compatPlayerListRef > 0) {
                    code[offset] = (byte) INVOKESTATIC;
                    writeUnsignedShort(code, offset + 1, compatPlayerListRef);
                    patched = true;
                } else if (refIndex == getRegistriesRef && compatRegistriesRef > 0) {
                    code[offset] = (byte) INVOKESTATIC;
                    writeUnsignedShort(code, offset + 1, compatRegistriesRef);
                    patched = true;
                } else if (refIndex == sendPacketRef && compatSendPacketRef > 0) {
                    code[offset] = (byte) INVOKESTATIC;
                    writeUnsignedShort(code, offset + 1, compatSendPacketRef);
                    patched = true;
                }
            } else if (opcode == GETFIELD) {
                int refIndex = readUnsignedShort(code, offset + 1);
                if (refIndex == playersFieldRef && compatPlayersRef > 0) {
                    code[offset] = (byte) INVOKESTATIC;
                    writeUnsignedShort(code, offset + 1, compatPlayersRef);
                    patched = true;
                }
            }

            offset = nextInstructionOffset(code, offset);
        }
        return patched;
    }

    private static int nextInstructionOffset(byte[] code, int offset) throws IOException {
        int opcode = code[offset] & 0xFF;
        return offset + switch (opcode) {
            case 16, 18, 21, 22, 23, 24, 25, 54, 55, 56, 57, 58, 169, 188 -> 2;
            case 17, 19, 20, 132, 153, 154, 155, 156, 157, 158, 159, 160, 161, 162, 163, 164,
                    165, 166, 167, 168, 178, 179, 180, 181, 182, 183, 184, 187, 189, 192, 193,
                    198, 199 -> 3;
            case 185, 186, 197, 200, 201 -> 5;
            case 170 -> {
                int aligned = alignToFour(offset + 1);
                int low = readInt(code, aligned + 4);
                int high = readInt(code, aligned + 8);
                yield (aligned - offset) + 12 + ((high - low + 1) * 4);
            }
            case 171 -> {
                int aligned = alignToFour(offset + 1);
                int pairs = readInt(code, aligned + 4);
                yield (aligned - offset) + 8 + (pairs * 8);
            }
            case 196 -> {
                int widenedOpcode = code[offset + 1] & 0xFF;
                yield widenedOpcode == 132 ? 6 : 4;
            }
            default -> 1;
        };
    }

    private static int alignToFour(int value) {
        int remainder = value & 3;
        return remainder == 0 ? value : value + (4 - remainder);
    }

    private static int readUnsignedShort(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xFF) << 8) | (bytes[offset + 1] & 0xFF);
    }

    private static int readInt(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xFF) << 24)
                | ((bytes[offset + 1] & 0xFF) << 16)
                | ((bytes[offset + 2] & 0xFF) << 8)
                | (bytes[offset + 3] & 0xFF);
    }

    private static void writeUnsignedShort(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) (value >>> 8);
        bytes[offset + 1] = (byte) value;
    }

    private static byte[] shortBytes(int value) {
        return new byte[]{(byte) (value >>> 8), (byte) value};
    }

    private static byte[] fourBytes(int first, int second) {
        return new byte[]{
                (byte) (first >>> 8),
                (byte) first,
                (byte) (second >>> 8),
                (byte) second
        };
    }

    private interface ConstantPoolEntry {
        int tag();

        void write(DataOutputStream output) throws IOException;
    }

    private static final class Utf8Entry implements ConstantPoolEntry {

        private final byte[] bytes;

        private Utf8Entry(byte[] bytes) {
            this.bytes = bytes;
        }

        @Override
        public int tag() {
            return 1;
        }

        String value() {
            return new String(bytes, StandardCharsets.UTF_8);
        }

        @Override
        public void write(DataOutputStream output) throws IOException {
            output.writeShort(bytes.length);
            output.write(bytes);
        }
    }

    private static final class ClassEntry implements ConstantPoolEntry {

        private final int nameIndex;

        private ClassEntry(int nameIndex) {
            this.nameIndex = nameIndex;
        }

        @Override
        public int tag() {
            return 7;
        }

        @Override
        public void write(DataOutputStream output) throws IOException {
            output.writeShort(nameIndex);
        }
    }

    private static final class NameAndTypeEntry implements ConstantPoolEntry {

        private final int nameIndex;
        private final int descriptorIndex;

        private NameAndTypeEntry(int nameIndex, int descriptorIndex) {
            this.nameIndex = nameIndex;
            this.descriptorIndex = descriptorIndex;
        }

        @Override
        public int tag() {
            return 12;
        }

        @Override
        public void write(DataOutputStream output) throws IOException {
            output.writeShort(nameIndex);
            output.writeShort(descriptorIndex);
        }
    }

    private static final class RefEntry implements ConstantPoolEntry {

        private final int tag;
        private final int classIndex;
        private final int nameAndTypeIndex;

        private RefEntry(int tag, int classIndex, int nameAndTypeIndex) {
            this.tag = tag;
            this.classIndex = classIndex;
            this.nameAndTypeIndex = nameAndTypeIndex;
        }

        @Override
        public int tag() {
            return tag;
        }

        @Override
        public void write(DataOutputStream output) throws IOException {
            output.writeShort(classIndex);
            output.writeShort(nameAndTypeIndex);
        }
    }

    private static final class RawEntry implements ConstantPoolEntry {

        private final int tag;
        private final byte[] payload;

        private RawEntry(int tag, byte[] payload) {
            this.tag = tag;
            this.payload = payload;
        }

        @Override
        public int tag() {
            return tag;
        }

        @Override
        public void write(DataOutputStream output) throws IOException {
            output.write(payload);
        }
    }

    private static final class MethodInfo {

        private final int accessFlags;
        private final int nameIndex;
        private final int descriptorIndex;
        private final List<Attribute> attributes;

        private MethodInfo(int accessFlags, int nameIndex, int descriptorIndex, List<Attribute> attributes) {
            this.accessFlags = accessFlags;
            this.nameIndex = nameIndex;
            this.descriptorIndex = descriptorIndex;
            this.attributes = attributes;
        }

        private CodeAttribute findCodeAttribute() {
            for (Attribute attribute : attributes) {
                if (attribute instanceof CodeAttribute codeAttribute) {
                    return codeAttribute;
                }
            }
            return null;
        }

        private void write(DataOutputStream output) throws IOException {
            output.writeShort(accessFlags);
            output.writeShort(nameIndex);
            output.writeShort(descriptorIndex);
            output.writeShort(attributes.size());
            for (Attribute attribute : attributes) {
                attribute.write(output);
            }
        }
    }

    private interface Attribute {
        void write(DataOutputStream output) throws IOException;
    }

    private static final class RawAttribute implements Attribute {

        private final int nameIndex;
        private final byte[] info;

        private RawAttribute(int nameIndex, byte[] info) {
            this.nameIndex = nameIndex;
            this.info = info;
        }

        @Override
        public void write(DataOutputStream output) throws IOException {
            output.writeShort(nameIndex);
            output.writeInt(info.length);
            output.write(info);
        }
    }

    private static final class CodeAttribute implements Attribute {

        private final int nameIndex;
        private final int maxStack;
        private final int maxLocals;
        private final byte[] code;
        private final byte[] exceptionTable;
        private final List<RawAttribute> attributes;

        private CodeAttribute(
                int nameIndex,
                int maxStack,
                int maxLocals,
                byte[] code,
                byte[] exceptionTable,
                List<RawAttribute> attributes
        ) {
            this.nameIndex = nameIndex;
            this.maxStack = maxStack;
            this.maxLocals = maxLocals;
            this.code = code;
            this.exceptionTable = exceptionTable;
            this.attributes = attributes;
        }

        @Override
        public void write(DataOutputStream output) throws IOException {
            ByteArrayOutputStream codeStream = new ByteArrayOutputStream();
            DataOutputStream codeOutput = new DataOutputStream(codeStream);
            codeOutput.writeShort(maxStack);
            codeOutput.writeShort(maxLocals);
            codeOutput.writeInt(code.length);
            codeOutput.write(code);
            codeOutput.writeShort(exceptionTable.length / 8);
            codeOutput.write(exceptionTable);
            codeOutput.writeShort(attributes.size());
            for (RawAttribute attribute : attributes) {
                attribute.write(codeOutput);
            }
            codeOutput.flush();

            byte[] info = codeStream.toByteArray();
            output.writeShort(nameIndex);
            output.writeInt(info.length);
            output.write(info);
        }
    }

    private static final class ClassReader {

        private final byte[] bytes;
        private int position;
        private int lastUnsignedShort;

        private ClassReader(byte[] bytes) {
            this.bytes = bytes;
        }

        private int readUnsignedByte() {
            return bytes[position++] & 0xFF;
        }

        private int readUnsignedShort() {
            lastUnsignedShort = ((bytes[position] & 0xFF) << 8) | (bytes[position + 1] & 0xFF);
            position += 2;
            return lastUnsignedShort;
        }

        private int peekLastUnsignedShort() {
            return lastUnsignedShort;
        }

        private int readInt() {
            int value = ((bytes[position] & 0xFF) << 24)
                    | ((bytes[position + 1] & 0xFF) << 16)
                    | ((bytes[position + 2] & 0xFF) << 8)
                    | (bytes[position + 3] & 0xFF);
            position += 4;
            return value;
        }

        private byte[] readBytes(int length) {
            byte[] out = new byte[length];
            System.arraycopy(bytes, position, out, 0, length);
            position += length;
            return out;
        }

        private byte[] readMemberRaw() {
            int start = position;
            readUnsignedShort();
            readUnsignedShort();
            readUnsignedShort();
            int attributesCount = readUnsignedShort();
            for (int index = 0; index < attributesCount; index++) {
                readUnsignedShort();
                int attributeLength = readInt();
                position += attributeLength;
            }
            int end = position;
            byte[] out = new byte[end - start];
            System.arraycopy(bytes, start, out, 0, out.length);
            return out;
        }

        private MethodInfo readMethod(List<ConstantPoolEntry> constantPool) {
            int accessFlags = readUnsignedShort();
            int nameIndex = readUnsignedShort();
            int descriptorIndex = readUnsignedShort();
            int attributesCount = readUnsignedShort();
            List<Attribute> attributes = new ArrayList<>(attributesCount);
            for (int index = 0; index < attributesCount; index++) {
                int attributeNameIndex = readUnsignedShort();
                String attributeName = utf8(constantPool, attributeNameIndex);
                int attributeLength = readInt();
                if ("Code".equals(attributeName)) {
                    int maxStack = readUnsignedShort();
                    int maxLocals = readUnsignedShort();
                    byte[] code = readBytes(readInt());
                    byte[] exceptionTable = readBytes(readUnsignedShort() * 8);
                    int nestedAttributesCount = readUnsignedShort();
                    List<RawAttribute> nestedAttributes = new ArrayList<>(nestedAttributesCount);
                    for (int nestedIndex = 0; nestedIndex < nestedAttributesCount; nestedIndex++) {
                        int nestedNameIndex = readUnsignedShort();
                        nestedAttributes.add(new RawAttribute(nestedNameIndex, readBytes(readInt())));
                    }
                    attributes.add(new CodeAttribute(attributeNameIndex, maxStack, maxLocals, code, exceptionTable, nestedAttributes));
                    continue;
                }

                attributes.add(new RawAttribute(attributeNameIndex, readBytes(attributeLength)));
            }
            return new MethodInfo(accessFlags, nameIndex, descriptorIndex, attributes);
        }

        private List<RawAttribute> readAttributes(List<ConstantPoolEntry> constantPool) {
            int attributesCount = readUnsignedShort();
            List<RawAttribute> attributes = new ArrayList<>(attributesCount);
            for (int index = 0; index < attributesCount; index++) {
                int attributeNameIndex = readUnsignedShort();
                utf8(constantPool, attributeNameIndex);
                attributes.add(new RawAttribute(attributeNameIndex, readBytes(readInt())));
            }
            return attributes;
        }

        private static String utf8(List<ConstantPoolEntry> constantPool, int index) {
            ConstantPoolEntry entry = constantPool.get(index);
            if (!(entry instanceof Utf8Entry utf8Entry)) {
                throw new IllegalStateException("Expected UTF8 entry at index " + index);
            }
            return utf8Entry.value();
        }
    }
}
