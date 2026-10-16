package com.tgnguyen.layoutlybe.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.time.Instant;
import java.nio.charset.StandardCharsets;

@Service @Transactional
public class AiConversationService {
 private final JdbcTemplate jdbc;
 private final ObjectMapper mapper;
 public AiConversationService(JdbcTemplate jdbc,ObjectMapper mapper) { this.jdbc=jdbc; this.mapper=mapper; }
 public UUID userMessage(String snapshotId,String prompt,String framework) {
  UUID conversation=UUID.nameUUIDFromBytes(("layoutly:"+snapshotId).getBytes(StandardCharsets.UTF_8));
  jdbc.update("INSERT INTO ai_conversations(id,snapshot_id,created_at) VALUES (?,?,?) ON CONFLICT(id) DO NOTHING",
   conversation,snapshotId,java.sql.Timestamp.from(Instant.now()));
  message(conversation,"user",Map.of("message",prompt,"framework",framework==null?"REACT":framework));
  return conversation;
 }
 public void message(UUID conversation,String role,Object content) {
  try {
   jdbc.update("INSERT INTO ai_messages(id,conversation_id,role,content,created_at) VALUES (?,?,?,?::jsonb,?)",
    UUID.randomUUID(),conversation,role,mapper.writeValueAsString(content),java.sql.Timestamp.from(Instant.now()));
  } catch(com.fasterxml.jackson.core.JsonProcessingException ex) { throw new IllegalStateException("Cannot serialize AI message.",ex); }
 }
 @Transactional(readOnly=true)
 public List<Map<String,Object>> history(String snapshotId) {
  return jdbc.query("SELECT m.id,m.role,m.content,m.created_at FROM ai_messages m JOIN ai_conversations c ON c.id=m.conversation_id WHERE c.snapshot_id=? ORDER BY m.created_at,m.id",
   (rs,index)->{
    Map<String,Object> row=new LinkedHashMap<>(); row.put("id",rs.getObject("id")); row.put("role",rs.getString("role"));
    try { row.put("content",mapper.readTree(rs.getString("content"))); }
    catch(Exception ex) { throw new IllegalStateException(ex); }
    row.put("createdAt",rs.getTimestamp("created_at").toInstant()); return row;
   },snapshotId);
 }
}
