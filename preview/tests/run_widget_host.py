"""Compile and exercise the actual Android-independent production policies."""
import pathlib,subprocess,urllib.request,hashlib,tempfile
root=pathlib.Path(__file__).resolve().parents[2]
with tempfile.TemporaryDirectory(prefix='nova-widget-host-') as temp:
 temp=pathlib.Path(temp);jar=temp/'json.jar'
 with urllib.request.urlopen('https://repo.maven.apache.org/maven2/org/json/json/20240303/json-20240303.jar',timeout=40) as r:raw=r.read()
 assert hashlib.sha256(raw).hexdigest()=='3cf6cd6892e32e2b4c1c39e0f52f5248a2f5b37646fdfbb79a66b46b618414ed'
 jar.write_bytes(raw);production=root/'preview/app/src/main/java/uk/co/traynor/novaos/preview';tests=root/'preview/tests'
 subprocess.run(['java','com.sun.tools.javac.Main','-cp',str(jar),'-d',str(temp)]+[str(p) for p in production.glob('*.java')]+[str(p) for p in tests.glob('*Checks.java')],check=True)
 for name in ('PreviewRulesChecks','AppModeChecks','ShellRulesChecks','MindRulesChecks','WidgetRulesChecks','BackupCodecChecks','TransactionChecks','MediaContinuityRulesChecks','PolishRulesChecks'):
  subprocess.run(['java','-cp',str(temp)+':'+str(jar),name],check=True)
