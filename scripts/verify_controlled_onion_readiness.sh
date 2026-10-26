#!/usr/bin/env bash
set -euo pipefail
scratch_dir=$(mktemp -d)
trap 'rm -rf "$scratch_dir"' EXIT
policy=app/src/androidTest/java/uk/co/traynor/privategallery/core/browser/hidden/ControlledOnionReadinessPolicy.java
cat > "$scratch_dir/ReadinessChecks.java" <<'JAVA'
import uk.co.traynor.privategallery.core.browser.hidden.ControlledOnionReadinessPolicy;
public class ReadinessChecks {
 static int checks;
 static void denied(Runnable r) {try {r.run();throw new IllegalStateException("admitted invalid readiness");}catch(AssertionError expected){checks++;}}
 static void same(boolean b){if(!b)throw new AssertionError();checks++;}
 public static void main(String[] args){
  var p=new ControlledOnionReadinessPolicy("unique-fixture");
  same(p.observe(true,1000,null,null,200,"unique-fixture".getBytes())==ControlledOnionReadinessPolicy.Result.READY);
  same(p.observe(true,30000,"transport","timeout",503,new byte[0])==ControlledOnionReadinessPolicy.Result.WAIT);
  same(p.observe(true,60000,null,null,200,"unique-fixture".getBytes())==ControlledOnionReadinessPolicy.Result.READY);
  denied(()->p.observe(true,1,null,null,200,"wrong".getBytes()));
  denied(()->p.observe(true,1,null,null,201,"unique-fixture".getBytes()));
  denied(()->p.observe(true,1,"transport",null,200,"unique-fixture".getBytes()));
  denied(()->p.observe(true,1,"transport","DNS",503,new byte[0]));
  denied(()->p.observe(true,1,"policy",null,451,new byte[0]));
  denied(()->p.observe(false,1,null,null,200,"unique-fixture".getBytes()));
  denied(()->p.observe(true,90000,"transport","timeout",503,new byte[0]));
  denied(()->p.observe(true,90001,null,null,200,"unique-fixture".getBytes()));
  denied(()->p.observe(true,-1,null,null,200,"unique-fixture".getBytes()));
  same(p.observe(true,90000,null,null,200,"unique-fixture".getBytes())==ControlledOnionReadinessPolicy.Result.READY);
  System.out.println(checks+" controlled onion readiness policy checks PASS");
 }
}
JAVA
java com.sun.tools.javac.Main -d "$scratch_dir" "$policy" "$scratch_dir/ReadinessChecks.java"
java -cp "$scratch_dir" ReadinessChecks
