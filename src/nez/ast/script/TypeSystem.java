package nez.ast.script;

import java.lang.reflect.Method;

import nez.util.UList;

public class TypeSystem {
	UList<Class<?>> classList = new UList<Class<?>>(new Class<?>[4]);

	public TypeSystem() {
		add(DynamicOperator.class);
		add(StaticOperator.class);
	}

	void add(Class<?> c) {
		classList.add(c);
	}

	Method findMethod(String name, Class<?>... args) {
		for (int i = classList.size() - 1; i >= 0; i--) {
			Class<?> c = classList.ArrayValues[i];
			for (Method m : c.getMethods()) {
				if (!name.equals(m.getName())) {
					continue;
				}
				if (acceptArguments(true, m, args)) {
					return m;
				}
			}
		}
		return null;
	}

	boolean acceptArguments(boolean autoBoxing, Method m, Class<?>... args) {
		Class<?>[] p = m.getParameterTypes();
		if (args.length != p.length) {
			return false;
		}
		for (int j = 0; j < args.length; j++) {
			if (!accept(autoBoxing, p[j], args[j])) {
				return false;
			}
		}
		return true;
	}

	boolean accept(boolean autoBoxing, Class<?> p, Class<?> a) {
		if (a == null) {
			return true;
		}
		if (autoBoxing) {
			if (p == int.class && a == Integer.class) {
				return true;
			}
		}
		System.out.printf("%s %s %s\n", p, a, p.isAssignableFrom(a));
		if (p.isAssignableFrom(a)) {
			return true;
		}
		return false;
	}

}