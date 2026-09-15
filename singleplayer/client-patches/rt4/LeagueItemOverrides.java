package rt4;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/** Synthesizes League-only item definitions while reusing stock cache models. */
public final class LeagueItemOverrides {
    private static final int FIRST_DEMONIC_ITEM = 65010;
    private static final int LAST_DEMONIC_ITEM = 65023;

    // Legacy IDs stay readable so old saves/payloads never crash while migrating.
    public static final int VOIDWALKER = 65000;
    public static final int DISK_OF_MEMORIES = 65001;
    public static final int BANKERS_NOTE = 65002;

    private LeagueItemOverrides() {}

    public static boolean isCustom(int id) {
        return id == VOIDWALKER || id == DISK_OF_MEMORIES || id == BANKERS_NOTE
                || (id >= FIRST_DEMONIC_ITEM && id <= LAST_DEMONIC_ITEM);
    }

    public static ObjType create(int id) {
        if (!isCustom(id)) return null;

        int donorId = 970;
        String name = "League relic item";
        String action = null;
        boolean stackable = false;

        switch (id) {
            case VOIDWALKER: donorId = 14534; name = "Voidwalker"; action = "Teleport"; break;
            case DISK_OF_MEMORIES: donorId = 981; name = "Disk of Memories"; action = "Recall"; break;
            case BANKERS_NOTE: donorId = 970; name = "Banker's Note"; action = "Activate"; break;
            case 65010: donorId = 10012; name = "Knapsack"; break;
            case 65011: donorId = 6106; name = "Searing boots"; break;
            case 65012: donorId = 10012; name = "Forager's pouch"; break;
            case 65013: donorId = 6199; name = "Banker's briefcase"; action = "Activate"; break;
            case 65014: donorId = 14534; name = "Evil eye"; action = "Activate"; break;
            case 65015: donorId = 981; name = "Map of Alacrity"; action = "Activate"; break;
            case 65016: donorId = 970; name = "Transmutation ledger"; action = "Activate"; break;
            case 65017: donorId = 1531; name = "Butler's bell"; action = "Activate"; break;
            case 65018: donorId = 6004; name = "Fairy mushroom"; action = "Activate"; break;
            case 65019: donorId = 4278; name = "Soul shard"; action = "Sacrifice"; stackable = true; break;
            case 65020: donorId = 3842; name = "Arcane grimoire"; action = "Activate"; break;
            case 65021: donorId = 1351; name = "Sage's axe"; break;
            case 65022: donorId = 10150; name = "Minion whistle"; action = "Activate"; break;
            case 65023: donorId = 229; name = "Flask of fervour"; action = "Activate"; break;
            default: break;
        }

        ObjType donor = ObjTypeList.get(donorId);
        ObjType custom = new ObjType();
        copyInstanceFields(donor, custom);

        custom.id = id;
        custom.name = JagString.of(name);
        custom.iops = new JagString[]{action == null ? null : JagString.of(action), null, null, null, null};
        custom.ops = new JagString[]{null, null, LocalizedText.TAKE, null, null};
        custom.cost = 0;
        custom.team = 0;
        custom.members = false;
        custom.stockMarket = false;
        custom.stackable = stackable ? 1 : 0;
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
