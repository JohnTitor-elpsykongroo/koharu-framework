package com.itranswarp.scan.primary;

import com.johntitor.koharu.annotation.Bean;
import com.johntitor.koharu.annotation.Configuration;
import com.johntitor.koharu.annotation.Primary;

@Configuration
public class PrimaryConfiguration {

    @Primary
    @Bean
    DogBean husky() {
        return new DogBean("Husky");
    }

    @Bean
    DogBean teddy() {
        return new DogBean("Teddy");
    }
}
