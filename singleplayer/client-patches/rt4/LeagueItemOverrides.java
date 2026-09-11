package rt4;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/** Synthesizes League-only item definitions while reusing stock cache models. */
public final class LeagueItemOverrides {
    public static final int VOIDWALKER = 65000;
    public static final int DISK_OF_MEMORIES = 65001;

    private LeagueItemOverrides() {}

    public static boolean isCustom(int id) {
        return id == VOIDWALKER || id == DISK_OF_MEMORIES;
    }

    public static ObjType create(int id) {
        if (!isCustom(id)) return null;

        int donorId = id == VOIDWALKER ? 14534 : 981;
        ObjType donor = ObjTypeList.get(donorId);
        ObjType custom = new ObjType();
        copyInstanceFields(donor, custom);

        custom.id = id;
        custom.name = JagString.of(id == VOIDWALKER ? "Voidwalker" : "Disk of Memories");
        custom.iops = new JagString[]{
                JagString.of(id == VOIDWALKER ? "Teleport" : "Recall"),
                null,
                null,
                null,
                null
        };
        custom.ops = new JagString[]{null, null, LocalizedText.TAKE, null, null};
        custom.cost = 0;
        custom.team = 0;
        custom.members = false;
        custom.stockMarket = false;
        custom.certtemplate = -1;
        custom.certlink = -1;
        custom.lentTemplate = -1;
        custom.lentLink = -1;
        custom.countobj = null;
        custom.countco = null;
        return custom;
    }

    private static void copyInstanceFields(ObjType from, ObjType to) {
        try {
            for (Field field : ObjType.class.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                field.setAccessible(true);
                field.set(to, field.get(from));
            }
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Unable to clone League item visual definition", failure);
        }
    }
}
