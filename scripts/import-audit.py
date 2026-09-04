"""Stand-in for the compiler this sandbox cannot run: every capitalised type used in real
code must be imported, declared in the file, in the same package, in java.lang, or written
out fully qualified. Catches exactly the class of mistake that cost a CI round trip."""
import os, re, sys, glob

SRC = ['src/main/java', 'src/test/java']
JAVA_LANG = set("""String Object Integer Long Double Float Boolean Byte Short Character Math System
Exception RuntimeException IllegalArgumentException IllegalStateException Override Deprecated
SuppressWarnings FunctionalInterface Thread Runnable Comparable Iterable Number Class Enum Record
StringBuilder CharSequence Void Error Throwable NullPointerException UnsupportedOperationException
SafeVarargs NumberFormatException ArithmeticException ClassCastException ClassLoader
Module Package StackTraceElement InterruptedException ReflectiveOperationException
IndexOutOfBoundsException ArrayIndexOutOfBoundsException StringIndexOutOfBoundsException
Iterable Cloneable AutoCloseable Process ProcessBuilder ThreadLocal StrictMath""".split())

# Nested types inherited from a superclass (Block.Settings, Item.Settings) resolve without an
# import and cannot be seen from this file alone. Named rather than silently allowed.
INHERITED_NESTED = {'Settings'}

def package_types(pkg_dir):
    return {os.path.basename(p)[:-5] for p in glob.glob(os.path.join(pkg_dir, '*.java'))}

def strip_comments(text):
    text = re.sub(r'/\*.*?\*/', '', text, flags=re.S)
    text = re.sub(r'//[^\n]*', '', text)
    text = re.sub(r'"(\\.|[^"\\])*"', '""', text)
    return text

problems = []
files = sys.argv[1:]
for path in files:
    if not path.endswith('.java') or not os.path.exists(path): continue
    raw = open(path, encoding='utf-8').read()
    body = strip_comments(raw)
    imports = set()
    for m in re.finditer(r'^import\s+(?:static\s+)?([\w.]+);', raw, re.M):
        imports.add(m.group(1).rsplit('.', 1)[-1])
    same_pkg = package_types(os.path.dirname(path))
    declared = set(re.findall(r'\b(?:class|interface|enum|record)\s+(\w+)', body))
    known = imports | same_pkg | declared | JAVA_LANG | INHERITED_NESTED

    # candidate type references: a capitalised word not preceded by '.' (so Foo.Bar only checks Foo)
    for m in re.finditer(r'(?<![\w.$])([A-Z]\w+)', body):
        name = m.group(1)
        if name in known: continue
        # generic type variables and constants
        if len(name) == 1 or name.isupper(): continue
        problems.append(f"{path}: {name}")

seen = set()
for p in problems:
    if p in seen: continue
    seen.add(p)
    print("  UNRESOLVED:", p)
print(f"checked {len([f for f in files if f.endswith('.java')])} files, {len(seen)} unresolved names")
