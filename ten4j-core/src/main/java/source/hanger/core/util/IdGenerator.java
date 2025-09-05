package source.hanger.core.util;

import java.nio.ByteBuffer;
import java.util.UUID;
import java.util.Base64;

import com.fasterxml.uuid.Generators;

public class IdGenerator {

    private static final int SHORT_ID_LENGTH = 8;

    /**
     * 生成一个随机的UUID字符串。
     *
     * @return 随机的UUID字符串
     */
    public static String generateUUID() {
        return Generators.randomBasedGenerator().generate().toString();
    }

    /**
     * 生成一个指定长度的短随机ID。
     *
     * @return 短随机ID字符串
     */
    public static String generateShortId() {
        UUID uuid = Generators.timeBasedGenerator().generate(); // 使用时间戳UUID
        ByteBuffer bb = ByteBuffer.wrap(new byte[16]);
        bb.putLong(uuid.getMostSignificantBits());
        bb.putLong(uuid.getLeastSignificantBits());
        String base64Uuid = Base64.getEncoder().withoutPadding().encodeToString(bb.array()); // 使用标准Base64编码，避免连字符'-'
        return base64Uuid.substring(0, Math.min(SHORT_ID_LENGTH, base64Uuid.length()));
    }
}
