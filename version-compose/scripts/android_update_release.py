"""Checks the actual 2.3 release -> 2.4 signed release upgrade, without clearing data."""
from pathlib import Path
import subprocess,time,re,xml.etree.ElementTree as ET
APP='com.pdffacil.pro'
OUT=Path('verification-update');OUT.mkdir(exist_ok=True)
def adb(*args,binary=False):return subprocess.check_output(['adb',*args],text=not binary,timeout=60)
def tree():
 adb('shell','uiautomator','dump','/sdcard/release-ui.xml')
 value=adb('exec-out','cat','/sdcard/release-ui.xml');(OUT/'last-ui.xml').write_text(value)
 return ET.fromstring(value)
def wait(label,seconds=60):
 until=time.monotonic()+seconds
 while time.monotonic()<until:
  try:
   for n in tree().iter('node'):
    if n.attrib.get('text')==label or n.attrib.get('content-desc')==label:return n
  except (subprocess.CalledProcessError,ET.ParseError):pass
  time.sleep(1)
 (OUT/'error.png').write_bytes(adb('exec-out','screencap','-p',binary=True))
 raise AssertionError('No apareció: '+label)
def tap(label):
 values=list(map(int,re.findall(r'\d+',wait(label).attrib['bounds'])))
 adb('shell','input','tap',str((values[0]+values[2])//2),str((values[1]+values[3])//2));time.sleep(1)
def launch():
 adb('shell','am','force-stop',APP);adb('shell','am','start','-W','-n',APP+'/.MainActivity')
adb('wait-for-device')
subprocess.run(['adb','uninstall',APP],stdout=subprocess.PIPE,stderr=subprocess.PIPE)
subprocess.run(['gh','release','download','v2.3-beta','--repo','hsalazar66/pdf-facil-pro','--pattern','PDF-Facil-Pro-2.3-beta.apk','--dir',str(OUT),'--clobber'],check=True,timeout=120)
adb('install','-r',str(OUT/'PDF-Facil-Pro-2.3-beta.apk'))
launch();wait('¿Quieres abrir tus PDF con PDF Fácil Pro?');tap('Ahora no')
tap('Probar edición con un PDF de ejemplo');wait('Informe de trabajo.pdf');tap('Anterior');wait('Informe de trabajo.pdf')
adb('install','-r','app/build/outputs/apk/release/app-release.apk')
launch();wait('¿Quieres abrir tus PDF con PDF Fácil Pro?')
(OUT/'actualizacion-firmada-2.3-a-2.4.png').write_bytes(adb('exec-out','screencap','-p',binary=True))
tap('Ahora no');wait('Informe de trabajo.pdf')
package=adb('shell','dumpsys','package',APP)
assert 'versionCode=7' in package and 'versionName=2.4-beta' in package
(OUT/'RESULTADO.txt').write_text('OK: APK release 2.3 instalada; borrador creado; actualización real con APK release 2.4 sin desinstalar; borrador conservado; oferta de visor tras actualizar; versionCode 7.\n')
print('ACTUALIZACION RELEASE 2.3 -> 2.4 VERIFICADA')
