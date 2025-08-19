package com.itranswarp.imported;

import com.johntitor.koharu.annotation.Bean;
import com.johntitor.koharu.annotation.Configuration;

import java.time.ZonedDateTime;

@Configuration
public class ZonedDateConfiguration {

    @Bean
    ZonedDateTime startZonedDateTime() {
        return ZonedDateTime.now();
    }
}
