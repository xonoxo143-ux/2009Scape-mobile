import core.cache.Cache;
import core.cache.CacheFileManager;

import java.nio.charset.StandardCharsets;

/** Minimal CS2 decoder used for one-shot diagnostics against the historical cache. */
public final class DumpClientScript {
    private static int u1(byte[] b, int p) { return b[p] & 0xff; }
    private static int u2(byte[] b, int p) { return (u1(b,p) << 8) | u1(b,p+1); }
    private static int i4(byte[] b, int p) { return (u1(b,p)<<24)|(u1(b,p+1)<<16)|(u1(b,p+2)<<8)|u1(b,p+3); }
    private static int nul(byte[] b, int p) { while (p < b.length && b[p] != 0) p++; return p; }

    public static void main(String[] args) throws Throwable {
        if (args.length != 2) throw new IllegalArgumentException("usage: DumpClientScript <cache-dir> <script-id>");
        int id = Integer.parseInt(args[1]);
        Cache.init(args[0]);
        CacheFileManager scripts = Cache.getIndexes()[12];
        byte[] b = scripts.getFileData(id, 0);
        if (b == null) throw new IllegalStateException("missing script " + id);

        int trailerLen = u2(b, b.length - 2);
        int trailerPos = b.length - trailerLen - 14;
        int p = trailerPos;
        int instructions = i4(b,p); p += 4;
        int intLocals=u2(b,p); p+=2, stringLocals=u2(b,p); p+=2, intArgs=u2(b,p); p+=2, stringArgs=u2(b,p); p+=2;
        int switches=u1(b,p++);
        System.out.println("script="+id+" bytes="+b.length+" instructions="+instructions+" intLocals="+intLocals+" stringLocals="+stringLocals+" intArgs="+intArgs+" stringArgs="+stringArgs+" switches="+switches+" trailerPos="+trailerPos);

        p = 0;
        int endName = nul(b,p);
        String name = endName == p ? "" : new String(b,p,endName-p, StandardCharsets.ISO_8859_1);
        System.out.println("name=" + name);
        p = endName + 1;
        int pc = 0;
        while (p < trailerPos && pc < instructions) {
            int opcode=u2(b,p); p+=2;
            if (opcode == 3) {
                int e=nul(b,p);
                String s=new String(b,p,e-p,StandardCharsets.ISO_8859_1);
                p=e+1;
                System.out.println(pc+": op="+opcode+" str="+s);
            } else if (opcode >= 100 || opcode == 21 || opcode == 38 || opcode == 39) {
                int operand=u1(b,p++);
                System.out.println(pc+": op="+opcode+" arg="+operand);
            } else {
                int operand=i4(b,p); p+=4;
                System.out.println(pc+": op="+opcode+" arg="+operand);
            }
            pc++;
        }
    }
}
