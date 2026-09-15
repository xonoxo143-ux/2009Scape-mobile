import core.cache.Cache;
import core.cache.CacheFileManager;
import rt4.Buffer;
import rt4.Component;

import java.util.Arrays;

/** One-shot cache diagnostic: decode every component in resizable root 746. */
public final class DumpInterface746 {
    private static String array(Object[] value) {
        return value == null ? "null" : Arrays.deepToString(value);
    }

    public static void main(String[] args) throws Throwable {
        if (args.length != 1) {
            throw new IllegalArgumentException("usage: DumpInterface746 <cache-dir>");
        }
        Cache.init(args[0]);
        CacheFileManager interfaces = Cache.getIndexes()[3];
        int[] fileIds = interfaces.getFileIds(746);
        System.out.println("INTERFACE 746 FILES " + Arrays.toString(fileIds));
        for (int child : fileIds) {
            byte[] data = interfaces.getFileData(746, child);
            if (data == null || data.length == 0) continue;

            Component component = new Component();
            component.id = (746 << 16) | child;
            Buffer buffer = new Buffer(data);
            if (data[0] == (byte) 0xFF) component.decodeIf3(buffer);
            else component.decodeIf1(buffer);

            System.out.println(
                    "child=" + child
                            + " if3=" + component.if3
                            + " type=" + component.type
                            + " buttonType=" + component.buttonType
                            + " clientCode=" + component.clientCode
                            + " base=" + component.baseX + "," + component.baseY
                            + " size=" + component.baseWidth + "x" + component.baseHeight
                            + " overlayer=" + component.overlayer
                            + " ops=" + (component.ops == null ? "null" : Arrays.toString(component.ops))
                            + " onOptionClick=" + array(component.onOptionClick));
        }
    }
}
