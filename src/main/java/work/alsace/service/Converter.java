package work.alsace.service;

import se.llbit.nbt.*;

import java.io.*;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * 该类提供将 .litematic 文件转换为 WorldEdit 兼容的 .schem 文件的功能。
 */
public class Converter {

    /**
     * 将 Litematica (.litematic) 文件转换为 WorldEdit (.schem) 文件。
     *
     * @param inputFile  输入的 Litematica 文件
     * @param outputDir  输出的目录，用于存储转换后的文件
     * @return 转换后生成的 .schem 文件列表（通常只包含一个文件）
     * @throws IOException 如果在读取或写入文件时发生错误
     */
    public static List<File> litematicToWorldEdit(File inputFile, File outputDir) throws IOException {
        File tempFile = new File("lite2edit_" + Thread.currentThread().getId() + ".tmp");
        DataInputStream inStream = new DataInputStream(new GZIPInputStream(Files.newInputStream(inputFile.toPath())));
        Tag litematica = CompoundTag.read(inStream).get("");
        inStream.close();
        int dataVersion = litematica.get("MinecraftDataVersion").intValue();

        List<File> files = new ArrayList<>();
        CompoundTag regions = litematica.get("Regions").asCompound();
        for (NamedTag regionTag : regions) {
            CompoundTag region = regionTag.asCompound();
            ListTag palette = region.get("BlockStatePalette").asList();
            int bitsPerBlock = Math.max(2, Integer.SIZE - Integer.numberOfLeadingZeros(palette.size() - 1));

            // Litematica dimensions can be negative.
            Tag size = region.get("Size");
            int x = size.get("x").intValue();
            int y = size.get("y").intValue();
            int z = size.get("z").intValue();

            // get offset
            Tag position = region.get("Position");
            int offsetx = position.get("x").intValue() + (x < 0 ? x+1 : 0);
            int offsety = position.get("y").intValue() + (y < 0 ? y+1 : 0);
            int offsetz = position.get("z").intValue() + (z < 0 ? z+1 : 0);

            // convert blocks
            // use a temporary file to avoid OutOfMemoryErrors for large schematics
            BufferedOutputStream fout = new BufferedOutputStream(new FileOutputStream(tempFile));
            int numBlocks = Math.abs(x * y * z);
            long bitmask, bits = 0;
            int i = 0, bitCount = 0;
            for (long num : region.get("BlockStates").longArray()) {
                int remainingBits = bitCount + 64;
                if (bitCount != 0) {
                    bitmask = (1L << (bitsPerBlock - bitCount)) - 1;
                    long newBits = (num & bitmask) << bitCount;
                    bits = bits | newBits;
                    num = num >>> (bitsPerBlock - bitCount);
                    remainingBits -= bitsPerBlock;
                    writeBlock(fout, (short) bits);
                    i++;
                }

                bitmask = (1L << bitsPerBlock) - 1;
                while (remainingBits >= bitsPerBlock) {
                    bits = num & bitmask;
                    num = num >>> bitsPerBlock;
                    remainingBits -= bitsPerBlock;
                    if (i >= numBlocks)
                        break;
                    writeBlock(fout, (short) bits);
                    i++;
                }
                bits = num;
                bitCount = remainingBits;
            }
            fout.flush();
            fout.close();

            i = 0;
            String[] blockPalette = new String[palette.size()];
            for (SpecificTag blockState : palette) {
                String name = blockState.get("Name").stringValue();
                CompoundTag properties = blockState.get("Properties").asCompound();
                if (!properties.isEmpty()) {
                    List<String> propertyNames = new ArrayList<>();
                    for (NamedTag property : properties) {
                        propertyNames.add(property.name() + "=" + property.unpack().stringValue());
                    }
                    name += "[" + String.join(",", propertyNames) + "]";
                }
                blockPalette[i++] = name;
            }

            //
            // Convert to WorldEdit format now
            //

            // read block data
            byte[] weBlocks = Files.readAllBytes(tempFile.toPath());

            // Convert palette
            CompoundTag wePalette = new CompoundTag();
            for (i = 0; i < blockPalette.length; ++i) {
                wePalette.add(blockPalette[i], new IntTag(i));
            }

            // Copy tile entity data
            List<CompoundTag> weTileEntities = new ArrayList<>();
            List<String> skip = Arrays.asList("x", "y", "z", "id");
            for (SpecificTag tileEntity : region.get("TileEntities").asList()) {
                CompoundTag liteTileEntity = tileEntity.asCompound();
                CompoundTag weTileEntity = new CompoundTag();

                // Litematica uses integer "x", "y", and "z" tags
                // WorldEdit uses one integer array "Pos" tag
                int tx = liteTileEntity.get("x").intValue();
                int ty = liteTileEntity.get("y").intValue();
                int tz = liteTileEntity.get("z").intValue();
                weTileEntity.add("Pos", new IntArrayTag(new int[] {tx, ty, tz}));

                // Litematica uses a lowercase "id"
                // WorldEdit uses a capitalized "Id"
                String tid = liteTileEntity.get("id").stringValue();
                weTileEntity.add("Id", new StringTag(tid));

                for (NamedTag tileEntityTag : liteTileEntity) {
                    String name = tileEntityTag.name();
                    if (!skip.contains(name))
                        weTileEntity.add(tileEntityTag);
                }
                weTileEntities.add(weTileEntity);
            }

            // metadata
            CompoundTag metadata = new CompoundTag();
            metadata.add("WEOffsetX", new IntTag(offsetx));
            metadata.add("WEOffsetY", new IntTag(offsety));
            metadata.add("WEOffsetZ", new IntTag(offsetz));

            CompoundTag worldEdit = new CompoundTag();
            worldEdit.add(new NamedTag("Metadata", metadata));
            worldEdit.add(new NamedTag("Palette", wePalette));
            worldEdit.add(new NamedTag("BlockEntities", new ListTag(Tag.TAG_COMPOUND, weTileEntities)));
            worldEdit.add(new NamedTag("DataVersion", new IntTag(dataVersion)));
            worldEdit.add(new NamedTag("Height", new ShortTag((short) Math.abs(y))));
            worldEdit.add(new NamedTag("Length", new ShortTag((short) Math.abs(z))));
            worldEdit.add(new NamedTag("PaletteMax", new IntTag(wePalette.size())));
            worldEdit.add(new NamedTag("Version", new IntTag(2)));
            worldEdit.add(new NamedTag("Width", new ShortTag((short) Math.abs(x))));
            worldEdit.add(new NamedTag("BlockData", new ByteArrayTag(weBlocks)));
            worldEdit.add(new NamedTag("Offset", new IntArrayTag(new int[3])));

            CompoundTag worldEditRoot = new CompoundTag();
            worldEditRoot.add("Schematic", worldEdit);

            // determine outputFileName
            String outputFileName = inputFile.getName();
            if (outputFileName.contains(".")) {
                outputFileName = outputFileName.substring(0, outputFileName.lastIndexOf('.'));
            }
            if (regions.size() > 1) {
                outputFileName += "-" + regionTag.name();
            }
            outputFileName = outputFileName.replaceAll("[^\\w-]+", "_") + getRandomString(6) + ".schem";

            // make sure directory exists, and write to the provided path
            Files.createDirectories(outputDir.toPath());
            File outputFile = new File(outputDir + "/" + outputFileName);
            DataOutputStream outStream = new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(outputFile.toPath())));
            worldEditRoot.write(outStream);
            outStream.close();
            files.add(outputFile);
        }

        tempFile.delete();
        return files;
    }

    private static void writeBlock(BufferedOutputStream fout, short block) throws IOException {
        if (block > 127) {
            fout.write((byte) (block | 128));
            fout.write((byte) (block / 128));
        }
        else {
            fout.write((byte) block);
        }
    }

    private static String getRandomString(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        Random random = new SecureRandom();
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
}