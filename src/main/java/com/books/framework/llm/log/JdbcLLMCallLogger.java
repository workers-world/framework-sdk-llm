package com.books.framework.llm.log;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
public class JdbcLLMCallLogger implements LLMCallLogger {

    private static final DateTimeFormatter TECH_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JdbcLLMCallLogger(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void log(LLMCallLogEntry entry) {
        String now = TECH_FMT.format(LocalDateTime.now());
        jdbcTemplate.update(
                "INSERT INTO llm_call_logs (provider, model_name, request_prompt, response_text, " +
                "parsed_result, tokens_used, cost, response_time_ms, success, error_message, " +
                "caller_info, idempotency_key, metadata_json, tech_create_time, tech_update_time) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                entry.getProvider(), entry.getModelName(), entry.getRequestPrompt(), entry.getResponseText(),
                entry.getParsedResult(), entry.getTokensUsed(), entry.getCost(), entry.getResponseTimeMs(),
                entry.isSuccess() ? 1 : 0, entry.getErrorMessage(),
                entry.getCallerInfo(), entry.getIdempotencyKey(), entry.getMetadataJson(),
                now, now);
    }

    @Override
    public LLMCallLogEntry findByIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isEmpty()) return null;
        String cutoff = TECH_FMT.format(LocalDateTime.now().minusHours(24));
        List<LLMCallLogEntry> list = jdbcTemplate.query(
                "SELECT * FROM llm_call_logs WHERE idempotency_key = ? AND success = 1 " +
                "AND tech_create_time >= ? ORDER BY tech_create_time DESC LIMIT 1",
                new LogRowMapper(), idempotencyKey, cutoff);
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public Map<String, Object> findPage(String caller, String provider, Boolean success, int page, int limit) {
        StringBuilder sql = new StringBuilder("SELECT * FROM llm_call_logs WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (caller != null && !caller.isEmpty()) {
            sql.append(" AND caller_info = ?");
            params.add(caller);
        }
        if (provider != null && !provider.isEmpty()) {
            sql.append(" AND provider = ?");
            params.add(provider);
        }
        if (success != null) {
            sql.append(" AND success = ?");
            params.add(success ? 1 : 0);
        }
        sql.append(" ORDER BY tech_create_time DESC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add((page - 1) * limit);

        List<LLMCallLogEntry> data = jdbcTemplate.query(sql.toString(), new LogRowMapper(), params.toArray());

        StringBuilder countSql = new StringBuilder("SELECT COUNT(*) FROM llm_call_logs WHERE 1=1");
        List<Object> countParams = new ArrayList<>();
        if (caller != null && !caller.isEmpty()) { countSql.append(" AND caller_info = ?"); countParams.add(caller); }
        if (provider != null && !provider.isEmpty()) { countSql.append(" AND provider = ?"); countParams.add(provider); }
        if (success != null) { countSql.append(" AND success = ?"); countParams.add(success ? 1 : 0); }
        Long total = jdbcTemplate.queryForObject(countSql.toString(), Long.class, countParams.toArray());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("data", data);
        result.put("total", total != null ? total : 0L);
        result.put("page", page);
        return result;
    }

    @Override
    public LLMCallLogEntry findById(Long id) {
        List<LLMCallLogEntry> list = jdbcTemplate.query(
                "SELECT * FROM llm_call_logs WHERE id = ?", new LogRowMapper(), id);
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public Map<String, Object> stats(String from, String to) {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalCalls", jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM llm_call_logs WHERE tech_create_time BETWEEN ? AND ?",
                Long.class, from, to));
        stats.put("totalTokens", jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(tokens_used),0) FROM llm_call_logs WHERE tech_create_time BETWEEN ? AND ?",
                Long.class, from, to));
        stats.put("totalCost", jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(cost),0) FROM llm_call_logs WHERE tech_create_time BETWEEN ? AND ?",
                BigDecimal.class, from, to));
        stats.put("successRate", jdbcTemplate.queryForObject(
                "SELECT CASE WHEN COUNT(*)=0 THEN 0 ELSE SUM(success)*100.0/COUNT(*) END FROM llm_call_logs WHERE tech_create_time BETWEEN ? AND ?",
                Double.class, from, to));
        return stats;
    }

    @Override
    public List<Map<String, Object>> healthOverview() {
        String cutoff = TECH_FMT.format(LocalDateTime.now().minusMinutes(5));
        return jdbcTemplate.queryForList(
                "SELECT provider, model_name, success, response_time_ms, tech_create_time " +
                "FROM llm_call_logs WHERE tech_create_time >= ? " +
                "ORDER BY tech_create_time DESC LIMIT 20", cutoff);
    }

    private static class LogRowMapper implements RowMapper<LLMCallLogEntry> {
        @Override
        public LLMCallLogEntry mapRow(ResultSet rs, int rowNum) throws SQLException {
            String techCreateTime = rs.getString("tech_create_time");
            LocalDateTime createdAt = null;
            if (techCreateTime != null && techCreateTime.length() == 14) {
                try {
                    createdAt = LocalDateTime.parse(techCreateTime, TECH_FMT);
                } catch (Exception ignored) {}
            }
            return LLMCallLogEntry.builder()
                    .id(rs.getLong("id"))
                    .provider(rs.getString("provider"))
                    .modelName(rs.getString("model_name"))
                    .requestPrompt(rs.getString("request_prompt"))
                    .responseText(rs.getString("response_text"))
                    .parsedResult(rs.getString("parsed_result"))
                    .tokensUsed(rs.getInt("tokens_used"))
                    .cost(rs.getBigDecimal("cost"))
                    .responseTimeMs(rs.getInt("response_time_ms"))
                    .success(rs.getInt("success") == 1)
                    .errorMessage(rs.getString("error_message"))
                    .callerInfo(rs.getString("caller_info"))
                    .idempotencyKey(rs.getString("idempotency_key"))
                    .metadataJson(rs.getString("metadata_json"))
                    .createdAt(createdAt)
                    .build();
        }
    }
}
