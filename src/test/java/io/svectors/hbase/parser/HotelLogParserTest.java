package io.svectors.hbase.parser;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.Charset;

import static org.junit.Assert.*;

public class HotelLogParserTest {

    HotelLogParser parser = new HotelLogParser();

    @Test
    public void toHotelLog() throws IOException {
        String data = "{\"app_name\": \"dwarf4bookingcomplugin\", \"level\": \"DEBUG\", \"logger\": " +
                "\"hotel.AvailLog.IHG.59519\", \"ip\": \"10.0.25.20\", \"host\": \"54.200.250.255:15020\", " +
                "\"thread\": \"task.executor.175\", \"message\": \"59519 | 5951910 | 1004526 | 2020-01-07 | - " +
                "| 2A | Avail | Success | Price:175.00, RUID:UmFuZG9tSVYkc2RlIyh9YS6n4nxMd/8BGfVgZVZNKRcSDcWq" +
                "JHSHJ/IteZfcZL/i2cj3fQgS5sVp1irMCsJUzcGotkOVnBHy\", \"type\": \"hotel_avail\", \"timestamp\": " +
                "\"2019-07-01T04:18:19.324\"}";

        HotelLog hotelLog = parser.toHotelLog(data.getBytes(Charset.forName("UTF-8")));
        assertEquals("dwarf4bookingcomplugin", hotelLog.getAppName());
        assertEquals("2019-07-01T04:18:19.324", hotelLog.getTimestamp());
    }
}