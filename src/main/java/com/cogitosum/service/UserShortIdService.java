package com.cogitosum.service;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Service
public class UserShortIdService {

    private final JdbcTemplate jdbcTemplate;

    public UserShortIdService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public String getOrCreate(String email) {
        if (!hasColumn("short_id")) {
            return email;
        }
        List<UserShortId> users = jdbcTemplate.query(
            "SELECT id, full_name, short_id FROM app_users WHERE LOWER(email) = LOWER(?)",
            (rs, rowNum) -> new UserShortId(
                rs.getLong("id"), rs.getString("full_name"), rs.getString("short_id")),
            email);
        if (users.isEmpty()) {
            return email;
        }
        return assign(users.getFirst());
    }

    @Transactional
    public void assignMissingShortIds() {
        if (!hasColumn("short_id")) {
            return;
        }
        List<UserShortId> users = jdbcTemplate.query(
            "SELECT id, full_name, short_id FROM app_users WHERE short_id IS NULL ORDER BY id",
            (rs, rowNum) -> new UserShortId(
                rs.getLong("id"), rs.getString("full_name"), rs.getString("short_id")));
        users.forEach(this::assign);
    }

    private String assign(UserShortId user) {
        if (user.shortId() != null && !user.shortId().isBlank()) {
            return user.shortId();
        }

        String prefix = initialsPrefix(user.fullName());
        for (int suffix = 1; suffix <= 999; suffix++) {
            String candidate = prefix + "%03d".formatted(suffix);
            try {
                int updated = jdbcTemplate.update(
                    "UPDATE app_users SET short_id = ? WHERE id = ? AND short_id IS NULL",
                    candidate, user.id());
                if (updated == 1) {
                    return candidate;
                }
                String assigned = findAssignedId(user.id());
                if (assigned != null) {
                    return assigned;
                }
            } catch (DuplicateKeyException duplicate) {
                String assigned = findAssignedId(user.id());
                if (assigned != null) {
                    return assigned;
                }
            }
        }
        throw new IllegalStateException("No available seven-character user ShortID remains for prefix " + prefix);
    }

    private String findAssignedId(Long userId) {
        return jdbcTemplate.query(
            "SELECT short_id FROM app_users WHERE id = ? AND short_id IS NOT NULL",
            (rs, rowNum) -> rs.getString(1), userId).stream().findFirst().orElse(null);
    }

    private String initialsPrefix(String fullName) {
        String normalizedName = Normalizer.normalize(fullName, Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "")
            .toUpperCase(Locale.ROOT)
            .replaceAll("[^A-Z\\s]", " ")
            .trim();
        List<String> nameParts = normalizedName.isEmpty()
            ? List.of()
            : Arrays.stream(normalizedName.split("\\s+")).toList();

        String firstName = nameParts.isEmpty() ? "U" : nameParts.getFirst();
        String familyName = nameParts.size() < 2
            ? ""
            : String.join("", nameParts.subList(1, nameParts.size()));
        StringBuilder prefix = new StringBuilder(familyName.substring(0, Math.min(3, familyName.length())));
        for (int index = 0; prefix.length() < 3 && index < firstName.length(); index++) {
            prefix.append(firstName.charAt(index));
        }
        while (prefix.length() < 3) {
            prefix.append('U');
        }
        prefix.append(firstName.charAt(0));
        return prefix.toString();
    }

    private boolean hasColumn(String columnName) {
        return jdbcTemplate.execute((ConnectionCallback<Boolean>) connection -> {
            try (var columns = connection.getMetaData().getColumns(
                    connection.getCatalog(), null, "%", "%")) {
                while (columns.next()) {
                    if ("app_users".equalsIgnoreCase(columns.getString("TABLE_NAME"))
                            && columnName.equalsIgnoreCase(columns.getString("COLUMN_NAME"))) {
                        return true;
                    }
                }
            }
            return false;
        });
    }

    private record UserShortId(Long id, String fullName, String shortId) {
    }
}
