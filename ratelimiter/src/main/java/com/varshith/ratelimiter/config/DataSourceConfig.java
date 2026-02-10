package com.varshith.ratelimiter.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.net.URI;

@Configuration
public class DataSourceConfig {

    @Value("${spring.datasource.url}")
    private String url;

    @Value("${spring.datasource.username:}")
    private String username;

    @Value("${spring.datasource.password:}")
    private String password;

    @Value("${spring.datasource.driver-class-name:org.postgresql.Driver}")
    private String driverClassName;

    @Bean
    public DataSource dataSource() {
        String normalizedUrl = normalizeJdbcUrl(url);
        UserInfo creds = extractUserInfo(normalizedUrl);
        String sanitizedUrl = stripUserInfo(normalizedUrl);

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(sanitizedUrl);
        config.setDriverClassName(driverClassName);

        String user = username;
        String pass = password;

        if ((user == null || user.isBlank()) && creds.username != null) {
            user = creds.username;
        }
        if ((pass == null || pass.isBlank()) && creds.password != null) {
            pass = creds.password;
        }

        if (user != null && !user.isBlank()) {
            config.setUsername(user);
        }
        if (pass != null && !pass.isBlank()) {
            config.setPassword(pass);
        }

        return new HikariDataSource(config);
    }

    private String normalizeJdbcUrl(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("spring.datasource.url is required");
        }
        String url = raw.trim();
        if ((url.startsWith("'") && url.endsWith("'")) || (url.startsWith("\"") && url.endsWith("\""))) {
            url = url.substring(1, url.length() - 1);
        }
        if (url.startsWith("postgresql://")) {
            url = "jdbc:" + url;
        }
        return url;
    }

    private UserInfo extractUserInfo(String jdbcUrl) {
        try {
            String noJdbc = jdbcUrl.startsWith("jdbc:") ? jdbcUrl.substring(5) : jdbcUrl;
            URI uri = new URI(noJdbc);
            String userInfo = uri.getUserInfo();
            if (userInfo == null) {
                return new UserInfo(null, null);
            }
            String[] parts = userInfo.split(":", 2);
            String user = parts.length > 0 ? parts[0] : null;
            String pass = parts.length > 1 ? parts[1] : null;
            return new UserInfo(user, pass);
        } catch (Exception e) {
            return new UserInfo(null, null);
        }
    }

    private String stripUserInfo(String jdbcUrl) {
        try {
            String noJdbc = jdbcUrl.startsWith("jdbc:") ? jdbcUrl.substring(5) : jdbcUrl;
            URI uri = new URI(noJdbc);
            if (uri.getUserInfo() == null) {
                return jdbcUrl;
            }
            URI sanitized = new URI(
                    uri.getScheme(),
                    null,
                    uri.getHost(),
                    uri.getPort(),
                    uri.getPath(),
                    uri.getQuery(),
                    uri.getFragment()
            );
            return "jdbc:" + sanitized.toString();
        } catch (Exception e) {
            return jdbcUrl;
        }
    }

    private static class UserInfo {
        final String username;
        final String password;

        UserInfo(String username, String password) {
            this.username = username;
            this.password = password;
        }
    }
}
