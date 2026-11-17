"""Builds the page for the Android app so it also runs on older phones (Android 5+):
fonts inlined, the app script compiled to ES5 with TypeScript, small polyfills for newer browser
functions, CSS fallbacks, and a notice if the phone's WebView is too old to run it at all."""
import base64, re, subprocess, sys, tempfile, os
src, fonts, out = sys.argv[1:4]
s = open(src, encoding='utf-8').read()
s, n = re.subn(r'url\(fonts/(Almarai-[A-Za-z]+\.ttf)\)',
               lambda m: 'url(data:font/ttf;base64,' + base64.b64encode(open(f'{fonts}/{m.group(1)}', 'rb').read()).decode() + ')', s)
assert n == 3, n
m = list(re.finditer(r'<script>(.*?)</script>', s, re.S)); assert len(m) == 1
js = m[0].group(1)
with tempfile.TemporaryDirectory() as d:
    open(f'{d}/app.js', 'w', encoding='utf-8').write(js)
    r = subprocess.run(['node', '/opt/npm-tools/node_modules/typescript/bin/tsc', '--allowJs', '--checkJs', 'false', '--ignoreDeprecations', '6.0', '--target', 'ES5', '--lib', 'es2020,dom',
                        '--skipLibCheck', '--noResolve', '--removeComments', '--outDir', f'{d}/o', f'{d}/app.js'],
                       capture_output=True, text=True)
    es5 = open(f'{d}/o/app.js', encoding='utf-8').read()
    errs = [l for l in r.stdout.splitlines() if 'error TS1' in l]   # TS1xxx = syntax errors only
    if errs: print('\n'.join(errs)); sys.exit(1)
for bad in ['=>', '`', '??', '?.', 'const ', 'let ']:
    found = [x.start() for x in re.finditer(re.escape(bad), re.sub(r'"(?:[^"\\]|\\.)*"|\'(?:[^\'\\]|\\.)*\'|/(?![*/])(?:[^/\\\n]|\\.)+/[gimsuy]*', '""', es5))]
    if found: print('still in output:', bad, len(found))
POLY = r'''
(function(){var O=Object,A=Array.prototype,S=String.prototype;
if(!O.assign)O.assign=function(t){for(var i=1;i<arguments.length;i++){var s=arguments[i];if(s)for(var k in s)if(O.prototype.hasOwnProperty.call(s,k))t[k]=s[k]}return t};
if(!O.values)O.values=function(o){return O.keys(o).map(function(k){return o[k]})};
if(!O.entries)O.entries=function(o){return O.keys(o).map(function(k){return[k,o[k]]})};
if(!O.fromEntries)O.fromEntries=function(a){var o={};for(var i=0;i<a.length;i++)o[a[i][0]]=a[i][1];return o};
if(!S.padStart)S.padStart=function(n,c){var s=String(this);c=c===undefined?' ':String(c);while(s.length<n)s=c+s;return s.slice(s.length-Math.max(n,String(this).length))};
if(!S.startsWith)S.startsWith=function(x,p){p=p||0;return this.substr(p,x.length)===x};
if(!S.endsWith)S.endsWith=function(x){return this.slice(-x.length)===x};
if(!S.includes)S.includes=function(x,p){return this.indexOf(x,p)!==-1};
if(!A.includes)A.includes=function(x){for(var i=0;i<this.length;i++)if(this[i]===x||(x!==x&&this[i]!==this[i]))return true;return false};
if(!A.find)A.find=function(f,t){for(var i=0;i<this.length;i++)if(f.call(t,this[i],i,this))return this[i]};
if(!A.findIndex)A.findIndex=function(f,t){for(var i=0;i<this.length;i++)if(f.call(t,this[i],i,this))return i;return -1};
if(!A.fill)A.fill=function(v){for(var i=0;i<this.length;i++)this[i]=v;return this};
if(!Array.from)Array.from=function(a,f){var r=[];for(var i=0;i<a.length;i++)r.push(f?f(a[i],i):a[i]);return r};
if(!Number.isFinite)Number.isFinite=function(v){return typeof v==='number'&&isFinite(v)};
if(!Math.hypot)Math.hypot=function(){var t=0;for(var i=0;i<arguments.length;i++)t+=arguments[i]*arguments[i];return Math.sqrt(t)};
var E=window.Element&&Element.prototype;
if(E&&!E.matches)E.matches=E.webkitMatchesSelector;
if(E&&!E.closest)E.closest=function(sel){var el=this;while(el&&el.nodeType===1){if(el.matches(sel))return el;el=el.parentNode}return null};
if(window.NodeList&&!NodeList.prototype.forEach)NodeList.prototype.forEach=A.forEach;
})();
'''
WATCH = '''<script>window.__yawmiOK=false;setTimeout(function(){if(window.__yawmiOK)return;var b=document.body;if(!b)return;
b.innerHTML='<div style="padding:40px 24px;color:#eef1fa;font-family:sans-serif;line-height:1.9;text-align:center;direction:rtl">'
+'<h2 style="margin-bottom:12px">يومي بحاجة لتحديث صغير</h2>'
+'<p>متصفح النظام على هالتلفون قديم. حدّث «Android System WebView» و«Google Chrome» من Google Play، وبعدها افتح يومي من جديد.</p>'
+'<p style="margin-top:18px"><a style="color:#e2ba6c;font-weight:bold" href="market://details?id=com.google.android.webview">افتح Android System WebView على Google Play</a></p></div>';},7000);</script>'''
CSS_FALLBACK = '''<style>.mbg{top:0;right:0;bottom:0;left:0}#space{top:0;left:0}.party{top:0;right:0;bottom:0;left:0}
.tabs{background:rgba(6,9,20,.94)}</style>'''
s = s[:m[0].start()] + WATCH + '<script>' + POLY + es5 + '</script>' + s[m[0].end():]
i = s.index('<style>'); s = s[:i] + CSS_FALLBACK + s[i:]
open(out, 'w', encoding='utf-8').write(s)
print('ok', len(s) // 1024, 'KB')
