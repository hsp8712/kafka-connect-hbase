package io.svectors.hbase.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.sink.SinkRecord;

import java.io.IOException;
import java.nio.charset.Charset;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;


public class HotelLogParser implements EventParser {

    private static Charset CHARSET = Charset.forName("UTF-8");

    private static DateTimeFormatter DATE_FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS").withZone(ZoneOffset.UTC);

    private ObjectMapper objectMapper = new ObjectMapper();

    {
        objectMapper.setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE);
    }

    @Override
    public Map<String, byte[]> parseKey(SinkRecord sr) throws EventParsingException {
        return null;
    }

    @Override
    public Map<String, byte[]> parseValue(SinkRecord sr) throws EventParsingException {

        if (sr.valueSchema() == Schema.OPTIONAL_BYTES_SCHEMA) {
            if (sr.value() instanceof byte[]) {
                byte[] value = (byte[]) sr.value();
                try {

                    Map<String, byte[]> values = new HashMap<>();
                    HotelLog hotelLog = toHotelLog(value);

                    values.put("rowkey", buildRowKey(hotelLog, sr.kafkaPartition(), sr.kafkaOffset()));
                    values.put("value", buildValue(hotelLog));

                    return values;
                } catch (IOException e) {
                    throw new EventParsingException(e);
                }

            } else {
                throw new EventParsingException("Value type must be byte array.");
            }

        } else {
            throw new EventParsingException("Schema type must be bytes.");
        }

    }

    public byte[] buildRowKey(HotelLog hotelLog, int kafkaPartition, long kafkaOffset) {

        String message = hotelLog.getMessage();
        String[] array = message.split("\\|");
        String hotel = array[0].trim();
        String timestamp = hotelLog.getTimestamp();

        long epochMilli = LocalDateTime.parse(timestamp, DATE_FORMATTER)
                .toInstant(ZoneOffset.UTC).toEpochMilli();


        byte[] salt = Bytes.toBytes((short)(hotel.hashCode() | 0xffff));
        byte[] hotelBytes = hotel.getBytes(CHARSET);
        byte[] timeBytes = Bytes.toBytes(epochMilli);
        byte[] partitionBytes = Bytes.toBytes((short)(kafkaPartition | 0xffff));
        byte[] offsetBytes = Bytes.toBytes((short)(kafkaOffset | 0xffff));

        byte[] seg1 = Bytes.add(salt, hotelBytes, new byte[] { 0x00 });
        byte[] seg2 = Bytes.add(timeBytes, partitionBytes, offsetBytes);

        return Bytes.add(seg1, seg2);
    }

    public byte[] buildValue(HotelLog hotelLog) {
        String value = hotelLog.getIp() + "|" + hotelLog.getHost() + "|" + hotelLog.getMessage();
        return value.getBytes(CHARSET);
    }

    public HotelLog toHotelLog(byte[] value) throws IOException {
        return objectMapper.readValue(value, HotelLog.class);
    }

}
