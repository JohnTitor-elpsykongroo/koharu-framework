package com.johntitor.koharu.jdbc.with.tx;

import com.johntitor.koharu.annotation.ComponentScan;
import com.johntitor.koharu.annotation.Configuration;
import com.johntitor.koharu.annotation.Import;
import com.johntitor.koharu.jdbc.JdbcConfiguration;

@ComponentScan
@Configuration
@Import(JdbcConfiguration.class)
public class JdbcWithTxApplication {

}
