package com.tgnguyen.layoutlybe.security;
import org.springframework.security.crypto.password.*;
import java.security.*;
import java.util.*;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Spring PBKDF2-SHA256, 600,000 rounds. Legacy file hashes remain verifiable. */
public final class PasswordHashing implements PasswordEncoder {
 private final Pbkdf2PasswordEncoder encoder = new Pbkdf2PasswordEncoder("",16,600_000,
   Pbkdf2PasswordEncoder.SecretKeyFactoryAlgorithm.PBKDF2WithHmacSHA256);
 public String encode(CharSequence raw) { return "{pbkdf2}"+encoder.encode(raw); }
 public boolean matches(CharSequence raw,String encoded) {
  if(raw==null||encoded==null) return false;
  try {
   if(encoded.startsWith("{pbkdf2}")) return encoder.matches(raw,encoded.substring(8));
   String[] parts=encoded.split(":");
   if(parts.length!=3) return false;
   int rounds=Integer.parseInt(parts[0]);
   if(rounds<100_000||rounds>2_000_000) return false;
   PBEKeySpec spec=new PBEKeySpec(raw.toString().toCharArray(),Base64.getDecoder().decode(parts[1]),rounds,256);
   try { return MessageDigest.isEqual(Base64.getDecoder().decode(parts[2]),SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded()); }
   finally { spec.clearPassword(); }
  } catch(Exception invalid) { return false; }
 }
 public boolean upgradeEncoding(String hash) { return hash!=null&&!hash.startsWith("{pbkdf2}"); }
}
