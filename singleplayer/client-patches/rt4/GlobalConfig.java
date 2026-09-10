package rt4;

import org.openrs2.deob.annotation.OriginalMember;
import singleplayer.InProcessBootstrap;

import java.math.BigInteger;

/** RT4 configuration with world-scale values owned by the local runtime. */
public class GlobalConfig {
    @OriginalMember(owner = "client!um", name = "V", descriptor = "Ljava/math/BigInteger;")
    public static final BigInteger RSA_MODULUS = new BigInteger(
            "96982303379631821170939875058071478695026608406924780574168393250855797534862289546229721580153879336741968220328805101128831071152160922518190059946555203865621183480223212969502122536662721687753974815205744569357388338433981424032996046420057284324856368815997832596174397728134370577184183004453899764051");

    @OriginalMember(owner = "client!gm", name = "X", descriptor = "Ljava/math/BigInteger;")
    public static final BigInteger RSA_EXPONENT = new BigInteger("65537");

    public static String EXTENDED_CONFIG_PATH = "config.json";
    public static String DEFAULT_HOSTNAME = "play.2009scape.org";
    public static int DEFAULT_PORT = 43594;
    public static int ALTERNATE_PORT = 43593;
    public static boolean SELECT_DEFAULT_WORLD = true;
    public static boolean LOGIN_USE_STRINGS = true;
    public static boolean LOGIN_EXTRA_INFO = true;
    public static boolean LOGIN_FAKE_IDX28 = true;
    public static boolean USE_ISAAC = false;

    // One authority owns this value. Legacy RT4 calculations derive from it.
    public static int TILE_DISTANCE = InProcessBootstrap.getViewDistance();
    public static int VIEW_DISTANCE = TILE_DISTANCE * 128;
    public static float VIEW_FADE_DISTANCE =
            ((float) TILE_DISTANCE / 28.0f) * 256.0f;

    public static boolean USE_SHIFT_CLICK = true;
    public static boolean USE_TWEENING = true;
    public static boolean BILINEAR_MINIMAP = true;
    public static boolean MOUSEWHEEL_ZOOM = true;
    public static int JS5_RESPONSE_TIMEOUT = 5000;
    public static int AUDIO_SAMPLE_RATE = 22050;
}
